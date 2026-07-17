#include "gl_d3d12_interop_probe.h"
#include "interop_test_pattern.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <GL/gl.h>
#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <iomanip>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

#ifndef APIENTRYP
#define APIENTRYP APIENTRY*
#endif

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;
using GLuint64 = unsigned long long;

constexpr GLenum kGlRgba8 = 0x8058;
constexpr GLenum kGlHandleTypeD3D12ResourceExt = 38282;
constexpr GLenum kGlLayoutGeneralExt = 38285;
constexpr GLenum kGlHandleTypeD3D12FenceExt = 38292;
constexpr GLenum kGlD3D12FenceValueExt = 38293;

using GlCreateMemoryObjects = void(APIENTRYP)(GLsizei, GLuint*);
using GlDeleteMemoryObjects = void(APIENTRYP)(GLsizei, const GLuint*);
using GlImportMemoryWin32Handle = void(APIENTRYP)(
    GLuint, GLuint64, GLenum, void*);
using GlTexStorageMem2D = void(APIENTRYP)(
    GLenum, GLsizei, GLenum, GLsizei, GLsizei, GLuint, GLuint64);
using GlGenSemaphores = void(APIENTRYP)(GLsizei, GLuint*);
using GlDeleteSemaphores = void(APIENTRYP)(GLsizei, const GLuint*);
using GlImportSemaphoreWin32Handle = void(APIENTRYP)(GLuint, GLenum, void*);
using GlSemaphoreParameterUi64 = void(APIENTRYP)(GLuint, GLenum, const GLuint64*);
using GlSignalSemaphore = void(APIENTRYP)(
    GLuint, GLuint, const GLuint*, GLuint, const GLuint*, const GLenum*);
using GlWaitSemaphore = void(APIENTRYP)(
    GLuint, GLuint, const GLuint*, GLuint, const GLuint*, const GLenum*);

class ScopedHandle {
public:
    ScopedHandle() noexcept = default;
    explicit ScopedHandle(HANDLE handle) noexcept : handle_(handle) {}
    ~ScopedHandle() { reset(); }

    ScopedHandle(const ScopedHandle&) = delete;
    ScopedHandle& operator=(const ScopedHandle&) = delete;

    HANDLE get() const noexcept { return handle_; }
    HANDLE release() noexcept {
        const HANDLE handle = handle_;
        handle_ = nullptr;
        return handle;
    }
    void reset(HANDLE handle = nullptr) noexcept {
        if (handle_ != nullptr && handle_ != INVALID_HANDLE_VALUE) {
            CloseHandle(handle_);
        }
        handle_ = handle;
    }

private:
    HANDLE handle_ = nullptr;
};

void requireSuccess(HRESULT result, const char* operation) {
    if (SUCCEEDED(result)) {
        return;
    }
    std::ostringstream message;
    message << operation << " failed with HRESULT 0x"
            << std::hex << std::uppercase << static_cast<unsigned long>(result);
    throw std::runtime_error(message.str());
}

void requireWin32(bool success, const char* operation) {
    if (success) {
        return;
    }
    std::ostringstream message;
    message << operation << " failed with Win32 error " << GetLastError();
    throw std::runtime_error(message.str());
}

void requireNoGlError(const char* operation) {
    const GLenum error = glGetError();
    if (error == GL_NO_ERROR) {
        return;
    }
    std::ostringstream message;
    message << operation << " failed with OpenGL error 0x"
            << std::hex << std::uppercase << error;
    throw std::runtime_error(message.str());
}

std::string glString(GLenum name) {
    const auto* value = glGetString(name);
    if (value == nullptr) {
        return {};
    }
    return reinterpret_cast<const char*>(value);
}

std::string toUtf8(const wchar_t* value) {
    if (value == nullptr || value[0] == L'\0') {
        return {};
    }
    const int required = WideCharToMultiByte(
        CP_UTF8, 0, value, -1, nullptr, 0, nullptr, nullptr);
    if (required <= 1) {
        return {};
    }
    std::string converted(static_cast<std::size_t>(required), '\0');
    requireWin32(
        WideCharToMultiByte(
            CP_UTF8, 0, value, -1, converted.data(), required, nullptr, nullptr) ==
            required,
        "WideCharToMultiByte");
    converted.resize(static_cast<std::size_t>(required - 1));
    return converted;
}

bool hasExtension(const std::string& extensions, const std::string& name) {
    std::size_t offset = 0;
    while ((offset = extensions.find(name, offset)) != std::string::npos) {
        const bool startValid = offset == 0 || extensions[offset - 1] == ' ';
        const std::size_t end = offset + name.size();
        const bool endValid = end == extensions.size() || extensions[end] == ' ';
        if (startValid && endValid) {
            return true;
        }
        offset = end;
    }
    return false;
}

LRESULT CALLBACK hiddenWindowProcedure(
    HWND window, UINT message, WPARAM word, LPARAM longValue) {
    return DefWindowProcW(window, message, word, longValue);
}

class HiddenWglContext {
public:
    HiddenWglContext() {
        try {
            instance_ = GetModuleHandleW(nullptr);
            requireWin32(instance_ != nullptr, "GetModuleHandleW");

            WNDCLASSW windowClass{};
            windowClass.style = CS_OWNDC;
            windowClass.lpfnWndProc = hiddenWindowProcedure;
            windowClass.hInstance = instance_;
            windowClass.lpszClassName = className();
            atom_ = RegisterClassW(&windowClass);
            requireWin32(atom_ != 0, "RegisterClassW");

            window_ = CreateWindowExW(
                0, className(), L"mc-dlss interop probe", WS_OVERLAPPEDWINDOW,
                CW_USEDEFAULT, CW_USEDEFAULT, 64, 64,
                nullptr, nullptr, instance_, nullptr);
            requireWin32(window_ != nullptr, "CreateWindowExW");

            deviceContext_ = GetDC(window_);
            requireWin32(deviceContext_ != nullptr, "GetDC");

            PIXELFORMATDESCRIPTOR descriptor{};
            descriptor.nSize = sizeof(descriptor);
            descriptor.nVersion = 1;
            descriptor.dwFlags =
                PFD_DRAW_TO_WINDOW | PFD_SUPPORT_OPENGL | PFD_DOUBLEBUFFER;
            descriptor.iPixelType = PFD_TYPE_RGBA;
            descriptor.cColorBits = 32;
            descriptor.cAlphaBits = 8;
            descriptor.cDepthBits = 24;
            descriptor.iLayerType = PFD_MAIN_PLANE;

            const int pixelFormat = ChoosePixelFormat(deviceContext_, &descriptor);
            requireWin32(pixelFormat != 0, "ChoosePixelFormat");
            requireWin32(
                SetPixelFormat(deviceContext_, pixelFormat, &descriptor) == TRUE,
                "SetPixelFormat");

            renderingContext_ = wglCreateContext(deviceContext_);
            requireWin32(renderingContext_ != nullptr, "wglCreateContext");
            requireWin32(
                wglMakeCurrent(deviceContext_, renderingContext_) == TRUE,
                "wglMakeCurrent");
        } catch (...) {
            cleanup();
            throw;
        }
    }

    ~HiddenWglContext() { cleanup(); }

    HiddenWglContext(const HiddenWglContext&) = delete;
    HiddenWglContext& operator=(const HiddenWglContext&) = delete;

private:
    static const wchar_t* className() noexcept {
        return L"McDlssGlD3D12InteropProbeWindow";
    }

    void cleanup() noexcept {
        if (renderingContext_ != nullptr) {
            wglMakeCurrent(nullptr, nullptr);
            wglDeleteContext(renderingContext_);
            renderingContext_ = nullptr;
        }
        if (deviceContext_ != nullptr && window_ != nullptr) {
            ReleaseDC(window_, deviceContext_);
            deviceContext_ = nullptr;
        }
        if (window_ != nullptr) {
            DestroyWindow(window_);
            window_ = nullptr;
        }
        if (atom_ != 0 && instance_ != nullptr) {
            UnregisterClassW(className(), instance_);
            atom_ = 0;
        }
    }

    HINSTANCE instance_ = nullptr;
    ATOM atom_ = 0;
    HWND window_ = nullptr;
    HDC deviceContext_ = nullptr;
    HGLRC renderingContext_ = nullptr;
};

bool invalidWglAddress(PROC address) noexcept {
    return address == nullptr ||
        address == reinterpret_cast<PROC>(1) ||
        address == reinterpret_cast<PROC>(2) ||
        address == reinterpret_cast<PROC>(3) ||
        address == reinterpret_cast<PROC>(-1);
}

template <typename Function>
Function loadGlFunction(const char* name) {
    const PROC address = wglGetProcAddress(name);
    if (invalidWglAddress(address)) {
        throw std::runtime_error(std::string("Missing OpenGL entry point: ") + name);
    }
    return reinterpret_cast<Function>(address);
}

struct GlInteropFunctions {
    GlCreateMemoryObjects createMemoryObjects = nullptr;
    GlDeleteMemoryObjects deleteMemoryObjects = nullptr;
    GlImportMemoryWin32Handle importMemoryWin32Handle = nullptr;
    GlTexStorageMem2D texStorageMem2D = nullptr;
    GlGenSemaphores genSemaphores = nullptr;
    GlDeleteSemaphores deleteSemaphores = nullptr;
    GlImportSemaphoreWin32Handle importSemaphoreWin32Handle = nullptr;
    GlSemaphoreParameterUi64 semaphoreParameterUi64 = nullptr;
    GlSignalSemaphore signalSemaphore = nullptr;
    GlWaitSemaphore waitSemaphore = nullptr;

    static GlInteropFunctions load() {
        GlInteropFunctions functions{};
        functions.createMemoryObjects =
            loadGlFunction<GlCreateMemoryObjects>("glCreateMemoryObjectsEXT");
        functions.deleteMemoryObjects =
            loadGlFunction<GlDeleteMemoryObjects>("glDeleteMemoryObjectsEXT");
        functions.importMemoryWin32Handle =
            loadGlFunction<GlImportMemoryWin32Handle>(
                "glImportMemoryWin32HandleEXT");
        functions.texStorageMem2D =
            loadGlFunction<GlTexStorageMem2D>("glTexStorageMem2DEXT");
        functions.genSemaphores =
            loadGlFunction<GlGenSemaphores>("glGenSemaphoresEXT");
        functions.deleteSemaphores =
            loadGlFunction<GlDeleteSemaphores>("glDeleteSemaphoresEXT");
        functions.importSemaphoreWin32Handle =
            loadGlFunction<GlImportSemaphoreWin32Handle>(
                "glImportSemaphoreWin32HandleEXT");
        functions.semaphoreParameterUi64 =
            loadGlFunction<GlSemaphoreParameterUi64>(
                "glSemaphoreParameterui64vEXT");
        functions.signalSemaphore =
            loadGlFunction<GlSignalSemaphore>("glSignalSemaphoreEXT");
        functions.waitSemaphore =
            loadGlFunction<GlWaitSemaphore>("glWaitSemaphoreEXT");
        return functions;
    }
};

class GlSharedObjects {
public:
    explicit GlSharedObjects(const GlInteropFunctions& functions)
        : functions_(functions) {}
    ~GlSharedObjects() {
        if (semaphore_ != 0) {
            functions_.deleteSemaphores(1, &semaphore_);
        }
        if (texture_ != 0) {
            glDeleteTextures(1, &texture_);
        }
        if (memory_ != 0) {
            functions_.deleteMemoryObjects(1, &memory_);
        }
    }

    GLuint& memory() noexcept { return memory_; }
    GLuint& texture() noexcept { return texture_; }
    GLuint& semaphore() noexcept { return semaphore_; }

private:
    const GlInteropFunctions& functions_;
    GLuint memory_ = 0;
    GLuint texture_ = 0;
    GLuint semaphore_ = 0;
};

HANDLE duplicateHandle(HANDLE source) {
    HANDLE duplicate = nullptr;
    requireWin32(
        DuplicateHandle(
            GetCurrentProcess(), source,
            GetCurrentProcess(), &duplicate,
            0, FALSE, DUPLICATE_SAME_ACCESS) == TRUE,
        "DuplicateHandle");
    return duplicate;
}

bool readbackMatches(
    ID3D12Resource* readback,
    const D3D12_PLACED_SUBRESOURCE_FOOTPRINT& footprint,
    const std::vector<std::uint8_t>& expected,
    std::uint32_t width,
    std::uint32_t height) {
    void* mapped = nullptr;
    D3D12_RANGE readRange{0, static_cast<SIZE_T>(footprint.Footprint.RowPitch) * height};
    requireSuccess(readback->Map(0, &readRange, &mapped), "ID3D12Resource::Map");

    const auto* bytes = static_cast<const std::uint8_t*>(mapped) + footprint.Offset;
    const bool matches = interopReadbackMatches(
        bytes, footprint.Footprint.RowPitch, expected, width, height);
    D3D12_RANGE writtenRange{0, 0};
    readback->Unmap(0, &writtenRange);
    return matches;
}

}

GlD3D12InteropSnapshot runGlD3D12InteropProbe(
    std::uint32_t width,
    std::uint32_t height) noexcept {
    GlD3D12InteropSnapshot snapshot{};
    try {
        if (width == 0 || height == 0) {
            throw std::invalid_argument("Probe dimensions must be non-zero.");
        }

        HiddenWglContext openGlContext;
        snapshot.openGlVendor = glString(GL_VENDOR);
        snapshot.openGlRenderer = glString(GL_RENDERER);
        snapshot.openGlVersion = glString(GL_VERSION);
        const std::string extensions = glString(GL_EXTENSIONS);
        if (snapshot.openGlVendor.empty() || snapshot.openGlRenderer.empty() ||
            snapshot.openGlVersion.empty() || extensions.empty()) {
            throw std::runtime_error("OpenGL context identity or extensions are unavailable.");
        }

        snapshot.memoryObjectExtension =
            hasExtension(extensions, "GL_EXT_memory_object");
        snapshot.memoryObjectWin32Extension =
            hasExtension(extensions, "GL_EXT_memory_object_win32");
        snapshot.semaphoreExtension =
            hasExtension(extensions, "GL_EXT_semaphore");
        snapshot.semaphoreWin32Extension =
            hasExtension(extensions, "GL_EXT_semaphore_win32");
        if (!snapshot.memoryObjectExtension ||
            !snapshot.memoryObjectWin32Extension ||
            !snapshot.semaphoreExtension ||
            !snapshot.semaphoreWin32Extension) {
            throw std::runtime_error(
                "The OpenGL driver is missing a required Win32 interop extension.");
        }

        const GlInteropFunctions glFunctions = GlInteropFunctions::load();
        snapshot.entryPointsLoaded = true;

        ComPtr<IDXGIFactory6> factory;
        requireSuccess(
            CreateDXGIFactory2(0, IID_PPV_ARGS(&factory)),
            "CreateDXGIFactory2");

        ComPtr<IDXGIAdapter1> adapter;
        DXGI_ADAPTER_DESC1 adapterDescription{};
        ComPtr<ID3D12Device> device;
        for (UINT index = 0;; ++index) {
            ComPtr<IDXGIAdapter1> candidate;
            const HRESULT result = factory->EnumAdapterByGpuPreference(
                index, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
                IID_PPV_ARGS(&candidate));
            if (result == DXGI_ERROR_NOT_FOUND) {
                break;
            }
            requireSuccess(result, "IDXGIFactory6::EnumAdapterByGpuPreference");
            DXGI_ADAPTER_DESC1 candidateDescription{};
            requireSuccess(
                candidate->GetDesc1(&candidateDescription),
                "IDXGIAdapter1::GetDesc1");
            if ((candidateDescription.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) {
                continue;
            }
            ComPtr<ID3D12Device> candidateDevice;
            if (SUCCEEDED(D3D12CreateDevice(
                    candidate.Get(), D3D_FEATURE_LEVEL_12_0,
                    IID_PPV_ARGS(&candidateDevice)))) {
                adapter = candidate;
                adapterDescription = candidateDescription;
                device = candidateDevice;
                break;
            }
        }
        if (adapter == nullptr || device == nullptr) {
            throw std::runtime_error("No hardware D3D12 adapter is available.");
        }
        snapshot.adapterName = toUtf8(adapterDescription.Description);

        D3D12_COMMAND_QUEUE_DESC queueDescription{};
        queueDescription.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
        ComPtr<ID3D12CommandQueue> queue;
        requireSuccess(
            device->CreateCommandQueue(&queueDescription, IID_PPV_ARGS(&queue)),
            "ID3D12Device::CreateCommandQueue");
        ComPtr<ID3D12CommandAllocator> allocator;
        requireSuccess(
            device->CreateCommandAllocator(
                D3D12_COMMAND_LIST_TYPE_DIRECT, IID_PPV_ARGS(&allocator)),
            "ID3D12Device::CreateCommandAllocator");
        ComPtr<ID3D12GraphicsCommandList> commandList;
        requireSuccess(
            device->CreateCommandList(
                0, D3D12_COMMAND_LIST_TYPE_DIRECT, allocator.Get(), nullptr,
                IID_PPV_ARGS(&commandList)),
            "ID3D12Device::CreateCommandList");

        D3D12_HEAP_PROPERTIES defaultHeap{};
        defaultHeap.Type = D3D12_HEAP_TYPE_DEFAULT;
        defaultHeap.CreationNodeMask = 1;
        defaultHeap.VisibleNodeMask = 1;
        D3D12_RESOURCE_DESC textureDescription{};
        textureDescription.Dimension = D3D12_RESOURCE_DIMENSION_TEXTURE2D;
        textureDescription.Width = width;
        textureDescription.Height = height;
        textureDescription.DepthOrArraySize = 1;
        textureDescription.MipLevels = 1;
        textureDescription.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
        textureDescription.SampleDesc.Count = 1;
        textureDescription.Layout = D3D12_TEXTURE_LAYOUT_UNKNOWN;

        ComPtr<ID3D12Resource> sharedTexture;
        requireSuccess(
            device->CreateCommittedResource(
                &defaultHeap, D3D12_HEAP_FLAG_SHARED,
                &textureDescription, D3D12_RESOURCE_STATE_COMMON,
                nullptr, IID_PPV_ARGS(&sharedTexture)),
            "ID3D12Device::CreateCommittedResource(shared texture)");
        snapshot.sharedTextureCreated = true;

        ComPtr<ID3D12Fence> sharedFence;
        requireSuccess(
            device->CreateFence(
                0, D3D12_FENCE_FLAG_SHARED, IID_PPV_ARGS(&sharedFence)),
            "ID3D12Device::CreateFence(shared)");
        snapshot.sharedFenceCreated = true;

        HANDLE rawTextureHandle = nullptr;
        requireSuccess(
            device->CreateSharedHandle(
                sharedTexture.Get(), nullptr, GENERIC_ALL, nullptr,
                &rawTextureHandle),
            "ID3D12Device::CreateSharedHandle(texture)");
        ScopedHandle textureHandle(rawTextureHandle);

        HANDLE rawFenceHandle = nullptr;
        requireSuccess(
            device->CreateSharedHandle(
                sharedFence.Get(), nullptr, GENERIC_ALL, nullptr,
                &rawFenceHandle),
            "ID3D12Device::CreateSharedHandle(fence)");
        ScopedHandle fenceHandle(rawFenceHandle);

        ScopedHandle glTextureHandle(duplicateHandle(textureHandle.get()));
        ScopedHandle glFenceHandle(duplicateHandle(fenceHandle.get()));
        GlSharedObjects glObjects(glFunctions);

        glFunctions.createMemoryObjects(1, &glObjects.memory());
        requireNoGlError("glCreateMemoryObjectsEXT");
        glFunctions.importMemoryWin32Handle(
            glObjects.memory(), 0, kGlHandleTypeD3D12ResourceExt,
            glTextureHandle.get());
        requireNoGlError("glImportMemoryWin32HandleEXT");
        snapshot.memoryImported = true;

        glGenTextures(1, &glObjects.texture());
        requireNoGlError("glGenTextures");
        glBindTexture(GL_TEXTURE_2D, glObjects.texture());
        glFunctions.texStorageMem2D(
            GL_TEXTURE_2D, 1, kGlRgba8,
            static_cast<GLsizei>(width), static_cast<GLsizei>(height),
            glObjects.memory(), 0);
        requireNoGlError("glTexStorageMem2DEXT");

        glFunctions.genSemaphores(1, &glObjects.semaphore());
        requireNoGlError("glGenSemaphoresEXT");
        glFunctions.importSemaphoreWin32Handle(
            glObjects.semaphore(), kGlHandleTypeD3D12FenceExt,
            glFenceHandle.get());
        requireNoGlError("glImportSemaphoreWin32HandleEXT");
        snapshot.semaphoreImported = true;

        const auto expectedPixels = makeInteropTestPattern(width, height);
        glBindTexture(GL_TEXTURE_2D, glObjects.texture());
        glTexSubImage2D(
            GL_TEXTURE_2D, 0, 0, 0,
            static_cast<GLsizei>(width), static_cast<GLsizei>(height),
            GL_RGBA, GL_UNSIGNED_BYTE, expectedPixels.data());
        requireNoGlError("glTexSubImage2D");

        const GLuint64 openGlSignalValue = 1;
        const GLenum generalLayout = kGlLayoutGeneralExt;
        glFunctions.semaphoreParameterUi64(
            glObjects.semaphore(), kGlD3D12FenceValueExt,
            &openGlSignalValue);
        glFunctions.signalSemaphore(
            glObjects.semaphore(), 0, nullptr,
            1, &glObjects.texture(), &generalLayout);
        glFlush();
        requireNoGlError("glSignalSemaphoreEXT");
        snapshot.openGlWriteSubmitted = true;

        D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};
        UINT rowCount = 0;
        UINT64 rowSize = 0;
        UINT64 totalBytes = 0;
        device->GetCopyableFootprints(
            &textureDescription, 0, 1, 0,
            &footprint, &rowCount, &rowSize, &totalBytes);
        if (totalBytes == 0 || rowCount != height || rowSize != width * 4ULL) {
            throw std::runtime_error("Unexpected D3D12 readback footprint.");
        }

        D3D12_HEAP_PROPERTIES readbackHeap{};
        readbackHeap.Type = D3D12_HEAP_TYPE_READBACK;
        readbackHeap.CreationNodeMask = 1;
        readbackHeap.VisibleNodeMask = 1;
        D3D12_RESOURCE_DESC readbackDescription{};
        readbackDescription.Dimension = D3D12_RESOURCE_DIMENSION_BUFFER;
        readbackDescription.Width = totalBytes;
        readbackDescription.Height = 1;
        readbackDescription.DepthOrArraySize = 1;
        readbackDescription.MipLevels = 1;
        readbackDescription.SampleDesc.Count = 1;
        readbackDescription.Layout = D3D12_TEXTURE_LAYOUT_ROW_MAJOR;

        ComPtr<ID3D12Resource> readback;
        requireSuccess(
            device->CreateCommittedResource(
                &readbackHeap, D3D12_HEAP_FLAG_NONE,
                &readbackDescription, D3D12_RESOURCE_STATE_COPY_DEST,
                nullptr, IID_PPV_ARGS(&readback)),
            "ID3D12Device::CreateCommittedResource(readback)");

        requireSuccess(
            queue->Wait(sharedFence.Get(), 1),
            "ID3D12CommandQueue::Wait(OpenGL fence value 1)");

        D3D12_RESOURCE_BARRIER toCopySource{};
        toCopySource.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
        toCopySource.Transition.pResource = sharedTexture.Get();
        toCopySource.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
        toCopySource.Transition.StateBefore = D3D12_RESOURCE_STATE_COMMON;
        toCopySource.Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
        commandList->ResourceBarrier(1, &toCopySource);

        D3D12_TEXTURE_COPY_LOCATION destination{};
        destination.pResource = readback.Get();
        destination.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
        destination.PlacedFootprint = footprint;
        D3D12_TEXTURE_COPY_LOCATION source{};
        source.pResource = sharedTexture.Get();
        source.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
        source.SubresourceIndex = 0;
        commandList->CopyTextureRegion(&destination, 0, 0, 0, &source, nullptr);

        D3D12_RESOURCE_BARRIER toCommon = toCopySource;
        toCommon.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_SOURCE;
        toCommon.Transition.StateAfter = D3D12_RESOURCE_STATE_COMMON;
        commandList->ResourceBarrier(1, &toCommon);
        requireSuccess(commandList->Close(), "ID3D12GraphicsCommandList::Close");
        ID3D12CommandList* commandLists[] = {commandList.Get()};
        queue->ExecuteCommandLists(1, commandLists);
        requireSuccess(
            queue->Signal(sharedFence.Get(), 2),
            "ID3D12CommandQueue::Signal(fence value 2)");

        const GLuint64 d3d12SignalValue = 2;
        glFunctions.semaphoreParameterUi64(
            glObjects.semaphore(), kGlD3D12FenceValueExt,
            &d3d12SignalValue);
        glFunctions.waitSemaphore(
            glObjects.semaphore(), 0, nullptr,
            1, &glObjects.texture(), &generalLayout);
        glFinish();
        requireNoGlError("glWaitSemaphoreEXT");
        snapshot.openGlWaitCompleted = true;

        ScopedHandle completionEvent(CreateEventW(nullptr, FALSE, FALSE, nullptr));
        requireWin32(completionEvent.get() != nullptr, "CreateEventW");
        if (sharedFence->GetCompletedValue() < 2) {
            requireSuccess(
                sharedFence->SetEventOnCompletion(2, completionEvent.get()),
                "ID3D12Fence::SetEventOnCompletion");
            if (WaitForSingleObject(completionEvent.get(), 10000) != WAIT_OBJECT_0) {
                throw std::runtime_error(
                    "Timed out waiting for D3D12 fence value 2.");
            }
        }
        snapshot.d3d12WaitCompleted = sharedFence->GetCompletedValue() >= 2;
        snapshot.d3d12SignalCompleted = snapshot.d3d12WaitCompleted;
        snapshot.readbackMatched = readbackMatches(
            readback.Get(), footprint, expectedPixels, width, height);
        if (!snapshot.readbackMatched) {
            throw std::runtime_error(
                "D3D12 readback did not match the OpenGL pixel pattern.");
        }

        snapshot.message = "OpenGL-D3D12 interop completed.";
    } catch (const std::exception& error) {
        snapshot.message = error.what();
    } catch (...) {
        snapshot.message = "Unknown OpenGL-D3D12 interop failure.";
    }
    return snapshot;
}

}
