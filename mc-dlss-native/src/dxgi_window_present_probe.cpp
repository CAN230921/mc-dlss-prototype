#include "dxgi_window_present_probe.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>

#include <dwmapi.h>

#include <GL/gl.h>
#include <d3d12.h>
#include <dxgi1_6.h>
#include <wrl/client.h>

#include <array>
#include <cmath>
#include <cstdint>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

namespace mc_dlss {
namespace {

using Microsoft::WRL::ComPtr;

constexpr UINT kBufferCount = 2;
constexpr DWORD kFenceTimeoutMilliseconds = 10000;
constexpr ULONGLONG kCompositorReadyTimeoutMilliseconds = 5000;

class ScopedHandle {
public:
    ScopedHandle() noexcept = default;
    explicit ScopedHandle(HANDLE handle) noexcept : handle_(handle) {}
    ~ScopedHandle() { reset(); }

    ScopedHandle(const ScopedHandle&) = delete;
    ScopedHandle& operator=(const ScopedHandle&) = delete;

    HANDLE get() const noexcept { return handle_; }

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
    if (SUCCEEDED(result)) return;
    std::ostringstream message;
    message << operation << " failed with HRESULT 0x"
            << std::hex << std::uppercase
            << static_cast<unsigned long>(result);
    throw std::runtime_error(message.str());
}

void requireExactSuccess(HRESULT result, const char* operation) {
    if (result == S_OK) return;
    std::ostringstream message;
    message << operation << " returned 0x"
            << std::hex << std::uppercase << std::setw(8) << std::setfill('0')
            << static_cast<unsigned long>(result)
            << "; only S_OK is accepted";
    throw std::runtime_error(message.str());
}

void requireWin32(bool success, const char* operation) {
    if (success) return;
    std::ostringstream message;
    message << operation << " failed with Win32 error " << GetLastError();
    throw std::runtime_error(message.str());
}

std::string glString(GLenum name) {
    const auto* value = glGetString(name);
    return value == nullptr ? std::string{} :
        std::string(reinterpret_cast<const char*>(value));
}

std::string toUtf8(const wchar_t* value) {
    if (value == nullptr || value[0] == L'\0') return {};
    const int size = WideCharToMultiByte(
        CP_UTF8, 0, value, -1, nullptr, 0, nullptr, nullptr);
    requireWin32(size > 1, "WideCharToMultiByte(size)");
    std::string result(static_cast<std::size_t>(size), '\0');
    requireWin32(
        WideCharToMultiByte(
            CP_UTF8, 0, value, -1, result.data(), size, nullptr, nullptr) == size,
        "WideCharToMultiByte(convert)");
    result.resize(static_cast<std::size_t>(size - 1));
    return result;
}

LRESULT CALLBACK probeWindowProcedure(
    HWND window, UINT message, WPARAM word, LPARAM longValue) {
    if (message == WM_CLOSE) return 0;
    return DefWindowProcW(window, message, word, longValue);
}

class ProbeWglWindow {
public:
    ProbeWglWindow() {
        try {
            instance_ = GetModuleHandleW(nullptr);
            requireWin32(instance_ != nullptr, "GetModuleHandleW");

            WNDCLASSW windowClass{};
            windowClass.style = CS_OWNDC;
            windowClass.lpfnWndProc = probeWindowProcedure;
            windowClass.hInstance = instance_;
            windowClass.hCursor = LoadCursorW(nullptr, MAKEINTRESOURCEW(32512));
            windowClass.lpszClassName = className();
            atom_ = RegisterClassW(&windowClass);
            requireWin32(atom_ != 0, "RegisterClassW");

            window_ = CreateWindowExW(
                0, className(), L"MC DLSS same-HWND DXGI probe",
                WS_OVERLAPPEDWINDOW,
                CW_USEDEFAULT, CW_USEDEFAULT, 320, 240,
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

    ~ProbeWglWindow() { cleanup(); }

    ProbeWglWindow(const ProbeWglWindow&) = delete;
    ProbeWglWindow& operator=(const ProbeWglWindow&) = delete;

    HWND hwnd() const noexcept { return window_; }
    HDC deviceContext() const noexcept { return deviceContext_; }
    HGLRC renderingContext() const noexcept { return renderingContext_; }

    void show(std::uint32_t width, std::uint32_t height) {
        resize(width, height);
        ShowWindow(window_, SW_SHOWNOACTIVATE);
        requireWin32(
            SetWindowPos(
                window_, HWND_TOP, 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_SHOWWINDOW) == TRUE,
            "SetWindowPos(HWND_TOP)");
        UpdateWindow(window_);
        pumpMessages();
        requireWin32(IsWindowVisible(window_) == TRUE, "IsWindowVisible");
        requireWin32(IsIconic(window_) == FALSE, "IsIconic");
    }

    void resize(std::uint32_t width, std::uint32_t height) {
        RECT bounds{0, 0, static_cast<LONG>(width), static_cast<LONG>(height)};
        requireWin32(
            AdjustWindowRectEx(&bounds, WS_OVERLAPPEDWINDOW, FALSE, 0) == TRUE,
            "AdjustWindowRectEx");
        requireWin32(
            SetWindowPos(
                window_, nullptr, 0, 0,
                bounds.right - bounds.left, bounds.bottom - bounds.top,
                SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE) == TRUE,
            "SetWindowPos");
        pumpMessages();
    }

    void waitForCompositorReady(IDXGISwapChain* swapchain) {
        BOOL compositionEnabled = FALSE;
        requireSuccess(
            DwmIsCompositionEnabled(&compositionEnabled),
            "DwmIsCompositionEnabled");
        if (compositionEnabled == FALSE) {
            throw std::runtime_error("Desktop Window Manager composition is disabled.");
        }
        DWM_TIMING_INFO initialTiming{};
        initialTiming.cbSize = sizeof(initialTiming);
        requireSuccess(
            DwmGetCompositionTimingInfo(nullptr, &initialTiming),
            "DwmGetCompositionTimingInfo(initial)");

        const ULONGLONG deadline =
            GetTickCount64() + kCompositorReadyTimeoutMilliseconds;
        HRESULT lastPresentTest = DXGI_STATUS_OCCLUDED;
        bool clientExposed = false;
        bool compositionAdvanced = false;
        do {
            pumpMessages();
            DWM_TIMING_INFO currentTiming{};
            currentTiming.cbSize = sizeof(currentTiming);
            requireSuccess(
                DwmGetCompositionTimingInfo(nullptr, &currentTiming),
                "DwmGetCompositionTimingInfo(current)");
            compositionAdvanced = compositionAdvanced ||
                currentTiming.cRefresh != initialTiming.cRefresh;
            if (compositionAdvanced && isClientAreaExposed()) {
                clientExposed = true;
                lastPresentTest = swapchain->Present(0, DXGI_PRESENT_TEST);
                if (lastPresentTest == S_OK) return;
                if (lastPresentTest != DXGI_STATUS_OCCLUDED) {
                    requireExactSuccess(
                        lastPresentTest, "IDXGISwapChain::Present(readiness test)");
                }
            }
            Sleep(16);
        } while (GetTickCount64() < deadline);

        std::ostringstream message;
        message << "DXGI compositor readiness timed out; Present(TEST) returned 0x"
                << std::hex << std::uppercase << std::setw(8) << std::setfill('0')
                << static_cast<unsigned long>(lastPresentTest)
                << ", clientExposed=" << std::boolalpha << clientExposed
                << ", compositionAdvanced=" << compositionAdvanced;
        throw std::runtime_error(message.str());
    }

private:
    static const wchar_t* className() noexcept {
        return L"McDlssDxgiWindowPresentProbe";
    }

    void pumpMessages() {
        MSG message{};
        while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }

    bool isClientAreaExposed() const {
        if (IsWindowVisible(window_) == FALSE || IsIconic(window_) != FALSE) {
            return false;
        }
        DWORD cloaked = 0;
        requireSuccess(
            DwmGetWindowAttribute(
                window_, DWMWA_CLOAKED, &cloaked, sizeof(cloaked)),
            "DwmGetWindowAttribute(DWMWA_CLOAKED)");
        if (cloaked != 0) return false;

        RECT client{};
        requireWin32(GetClientRect(window_, &client) == TRUE, "GetClientRect");
        const LONG width = client.right - client.left;
        const LONG height = client.bottom - client.top;
        if (width <= 0 || height <= 0) return false;

        std::array<POINT, 5> samplePoints = {{
            {width / 2, height / 2},
            {width / 4, height / 4},
            {(width * 3) / 4, height / 4},
            {width / 4, (height * 3) / 4},
            {(width * 3) / 4, (height * 3) / 4},
        }};
        for (auto& point : samplePoints) {
            requireWin32(
                ClientToScreen(window_, &point) == TRUE, "ClientToScreen");
            HWND coveringWindow = WindowFromPoint(point);
            if (coveringWindow == nullptr ||
                GetAncestor(coveringWindow, GA_ROOT) != window_) {
                return false;
            }
        }
        return true;
    }

    void cleanup() noexcept {
        if (renderingContext_ != nullptr) {
            if (wglGetCurrentContext() == renderingContext_) {
                wglMakeCurrent(nullptr, nullptr);
            }
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

void waitForQueue(
    ID3D12CommandQueue* queue,
    ID3D12Fence* fence,
    HANDLE completionEvent,
    std::uint64_t& nextFenceValue) {
    const std::uint64_t fenceValue = nextFenceValue++;
    requireSuccess(queue->Signal(fence, fenceValue), "ID3D12CommandQueue::Signal");
    if (fence->GetCompletedValue() >= fenceValue) return;
    requireSuccess(
        fence->SetEventOnCompletion(fenceValue, completionEvent),
        "ID3D12Fence::SetEventOnCompletion");
    const DWORD waitResult = WaitForSingleObject(
        completionEvent, kFenceTimeoutMilliseconds);
    if (waitResult != WAIT_OBJECT_0) {
        if (waitResult == WAIT_TIMEOUT) {
            throw std::runtime_error("Timed out waiting for the D3D12 queue.");
        }
        throw std::runtime_error("D3D12 fence wait failed.");
    }
}

void createBackBuffers(
    ID3D12Device* device,
    IDXGISwapChain4* swapchain,
    ID3D12DescriptorHeap* rtvHeap,
    std::array<ComPtr<ID3D12Resource>, kBufferCount>& backBuffers) {
    const UINT descriptorSize = device->GetDescriptorHandleIncrementSize(
        D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
    D3D12_CPU_DESCRIPTOR_HANDLE descriptor =
        rtvHeap->GetCPUDescriptorHandleForHeapStart();
    for (UINT index = 0; index < kBufferCount; ++index) {
        backBuffers[index].Reset();
        requireSuccess(
            swapchain->GetBuffer(index, IID_PPV_ARGS(&backBuffers[index])),
            "IDXGISwapChain::GetBuffer");
        device->CreateRenderTargetView(backBuffers[index].Get(), nullptr, descriptor);
        descriptor.ptr += descriptorSize;
    }
}

bool renderAndVerify(
    ID3D12Device* device,
    ID3D12CommandQueue* queue,
    ID3D12CommandAllocator* allocator,
    ID3D12GraphicsCommandList* commandList,
    ID3D12DescriptorHeap* rtvHeap,
    IDXGISwapChain4* swapchain,
    const std::array<ComPtr<ID3D12Resource>, kBufferCount>& backBuffers,
    ID3D12Fence* fence,
    HANDLE completionEvent,
    std::uint64_t& nextFenceValue,
    const std::array<float, 4>& color) {
    const UINT bufferIndex = swapchain->GetCurrentBackBufferIndex();
    ID3D12Resource* backBuffer = backBuffers[bufferIndex].Get();
    const D3D12_RESOURCE_DESC textureDescription = backBuffer->GetDesc();

    D3D12_PLACED_SUBRESOURCE_FOOTPRINT footprint{};
    UINT rowCount = 0;
    UINT64 rowSize = 0;
    UINT64 totalBytes = 0;
    device->GetCopyableFootprints(
        &textureDescription, 0, 1, 0,
        &footprint, &rowCount, &rowSize, &totalBytes);
    if (totalBytes == 0 || rowCount != textureDescription.Height ||
        rowSize != textureDescription.Width * 4ULL) {
        throw std::runtime_error("Unexpected swapchain readback footprint.");
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

    requireSuccess(allocator->Reset(), "ID3D12CommandAllocator::Reset");
    requireSuccess(
        commandList->Reset(allocator, nullptr),
        "ID3D12GraphicsCommandList::Reset");

    D3D12_RESOURCE_BARRIER toRenderTarget{};
    toRenderTarget.Type = D3D12_RESOURCE_BARRIER_TYPE_TRANSITION;
    toRenderTarget.Transition.pResource = backBuffer;
    toRenderTarget.Transition.Subresource = D3D12_RESOURCE_BARRIER_ALL_SUBRESOURCES;
    toRenderTarget.Transition.StateBefore = D3D12_RESOURCE_STATE_PRESENT;
    toRenderTarget.Transition.StateAfter = D3D12_RESOURCE_STATE_RENDER_TARGET;
    commandList->ResourceBarrier(1, &toRenderTarget);

    const UINT descriptorSize = device->GetDescriptorHandleIncrementSize(
        D3D12_DESCRIPTOR_HEAP_TYPE_RTV);
    D3D12_CPU_DESCRIPTOR_HANDLE rtv =
        rtvHeap->GetCPUDescriptorHandleForHeapStart();
    rtv.ptr += static_cast<SIZE_T>(bufferIndex) * descriptorSize;
    commandList->ClearRenderTargetView(rtv, color.data(), 0, nullptr);

    D3D12_RESOURCE_BARRIER toCopySource = toRenderTarget;
    toCopySource.Transition.StateBefore = D3D12_RESOURCE_STATE_RENDER_TARGET;
    toCopySource.Transition.StateAfter = D3D12_RESOURCE_STATE_COPY_SOURCE;
    commandList->ResourceBarrier(1, &toCopySource);

    D3D12_TEXTURE_COPY_LOCATION destination{};
    destination.pResource = readback.Get();
    destination.Type = D3D12_TEXTURE_COPY_TYPE_PLACED_FOOTPRINT;
    destination.PlacedFootprint = footprint;
    D3D12_TEXTURE_COPY_LOCATION source{};
    source.pResource = backBuffer;
    source.Type = D3D12_TEXTURE_COPY_TYPE_SUBRESOURCE_INDEX;
    source.SubresourceIndex = 0;
    commandList->CopyTextureRegion(&destination, 0, 0, 0, &source, nullptr);

    D3D12_RESOURCE_BARRIER toPresent = toCopySource;
    toPresent.Transition.StateBefore = D3D12_RESOURCE_STATE_COPY_SOURCE;
    toPresent.Transition.StateAfter = D3D12_RESOURCE_STATE_PRESENT;
    commandList->ResourceBarrier(1, &toPresent);

    requireSuccess(commandList->Close(), "ID3D12GraphicsCommandList::Close");
    ID3D12CommandList* lists[] = {commandList};
    queue->ExecuteCommandLists(1, lists);
    waitForQueue(queue, fence, completionEvent, nextFenceValue);

    void* mapped = nullptr;
    D3D12_RANGE readRange{
        static_cast<SIZE_T>(footprint.Offset),
        static_cast<SIZE_T>(footprint.Offset +
            static_cast<UINT64>(footprint.Footprint.RowPitch) *
            textureDescription.Height)};
    requireSuccess(readback->Map(0, &readRange, &mapped), "ID3D12Resource::Map");
    const auto* bytes = static_cast<const std::uint8_t*>(mapped) + footprint.Offset;
    std::array<std::uint8_t, 4> expected{};
    for (std::size_t channel = 0; channel < expected.size(); ++channel) {
        expected[channel] = static_cast<std::uint8_t>(
            std::lround(color[channel] * 255.0F));
    }

    bool matches = true;
    for (UINT row = 0; row < textureDescription.Height && matches; ++row) {
        const auto* pixel = bytes +
            static_cast<std::size_t>(row) * footprint.Footprint.RowPitch;
        for (UINT64 column = 0; column < textureDescription.Width && matches;
             ++column, pixel += 4) {
            for (std::size_t channel = 0; channel < expected.size(); ++channel) {
                const int difference =
                    static_cast<int>(pixel[channel]) - expected[channel];
                if (difference < -1 || difference > 1) {
                    matches = false;
                    break;
                }
            }
        }
    }
    const D3D12_RANGE writtenRange{0, 0};
    readback->Unmap(0, &writtenRange);
    return matches;
}

}

DxgiWindowPresentProbeSnapshot runDxgiWindowPresentProbe(
    std::uint32_t width,
    std::uint32_t height,
    std::uint32_t resizedWidth,
    std::uint32_t resizedHeight) noexcept {
    DxgiWindowPresentProbeSnapshot snapshot{};
    ComPtr<ID3D12Device> device;
    try {
        if (width == 0 || height == 0 || resizedWidth == 0 || resizedHeight == 0) {
            throw std::invalid_argument("Probe dimensions must be non-zero.");
        }

        ProbeWglWindow window;
        window.show(width, height);
        snapshot.openGlVendor = glString(GL_VENDOR);
        snapshot.openGlRenderer = glString(GL_RENDERER);
        if (snapshot.openGlVendor.empty() || snapshot.openGlRenderer.empty()) {
            throw std::runtime_error("OpenGL identity is unavailable.");
        }
        snapshot.openGlContextCurrent =
            wglGetCurrentContext() == window.renderingContext() &&
            wglGetCurrentDC() == window.deviceContext();
        if (!snapshot.openGlContextCurrent) {
            throw std::runtime_error("The WGL context is not current.");
        }
        glViewport(0, 0, static_cast<GLsizei>(width), static_cast<GLsizei>(height));
        glClearColor(0.0F, 0.0F, 1.0F, 1.0F);
        glClear(GL_COLOR_BUFFER_BIT);
        glFinish();
        if (glGetError() != GL_NO_ERROR) {
            throw std::runtime_error("OpenGL clear failed.");
        }
        requireWin32(::SwapBuffers(window.deviceContext()) == TRUE, "SwapBuffers");
        snapshot.openGlSwapCompleted = true;

        ComPtr<IDXGIFactory6> factory;
        requireSuccess(
            CreateDXGIFactory2(0, IID_PPV_ARGS(&factory)),
            "CreateDXGIFactory2");

        ComPtr<IDXGIAdapter1> adapter;
        DXGI_ADAPTER_DESC1 adapterDescription{};
        for (UINT index = 0;; ++index) {
            ComPtr<IDXGIAdapter1> candidate;
            const HRESULT result = factory->EnumAdapterByGpuPreference(
                index, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
                IID_PPV_ARGS(&candidate));
            if (result == DXGI_ERROR_NOT_FOUND) break;
            requireSuccess(result, "IDXGIFactory6::EnumAdapterByGpuPreference");
            DXGI_ADAPTER_DESC1 description{};
            requireSuccess(candidate->GetDesc1(&description), "IDXGIAdapter1::GetDesc1");
            if ((description.Flags & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) continue;
            ComPtr<ID3D12Device> candidateDevice;
            if (SUCCEEDED(D3D12CreateDevice(
                    candidate.Get(), D3D_FEATURE_LEVEL_12_0,
                    IID_PPV_ARGS(&candidateDevice)))) {
                adapter = candidate;
                adapterDescription = description;
                device = candidateDevice;
                break;
            }
        }
        if (adapter == nullptr || device == nullptr) {
            throw std::runtime_error("No hardware D3D12 feature-level 12.0 adapter is available.");
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
        requireSuccess(commandList->Close(), "ID3D12GraphicsCommandList::Close(initial)");

        DXGI_SWAP_CHAIN_DESC1 swapchainDescription{};
        swapchainDescription.Width = width;
        swapchainDescription.Height = height;
        swapchainDescription.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
        swapchainDescription.SampleDesc.Count = 1;
        swapchainDescription.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
        swapchainDescription.BufferCount = kBufferCount;
        swapchainDescription.Scaling = DXGI_SCALING_STRETCH;
        swapchainDescription.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
        swapchainDescription.AlphaMode = DXGI_ALPHA_MODE_IGNORE;
        ComPtr<IDXGISwapChain1> swapchain1;
        requireSuccess(
            factory->CreateSwapChainForHwnd(
                queue.Get(), window.hwnd(), &swapchainDescription,
                nullptr, nullptr, &swapchain1),
            "IDXGIFactory::CreateSwapChainForHwnd");
        ComPtr<IDXGISwapChain4> swapchain;
        requireSuccess(swapchain1.As(&swapchain), "QueryInterface(IDXGISwapChain4)");
        requireSuccess(
            factory->MakeWindowAssociation(window.hwnd(), DXGI_MWA_NO_ALT_ENTER),
            "IDXGIFactory::MakeWindowAssociation");
        snapshot.dxgiSwapchainCreated = true;
        HWND swapchainWindow = nullptr;
        requireSuccess(swapchain->GetHwnd(&swapchainWindow), "IDXGISwapChain1::GetHwnd");
        snapshot.sameWindowHandle = swapchainWindow == window.hwnd();
        if (!snapshot.sameWindowHandle) {
            throw std::runtime_error("DXGI did not retain the WGL window handle.");
        }
        window.waitForCompositorReady(swapchain.Get());

        D3D12_DESCRIPTOR_HEAP_DESC rtvHeapDescription{};
        rtvHeapDescription.Type = D3D12_DESCRIPTOR_HEAP_TYPE_RTV;
        rtvHeapDescription.NumDescriptors = kBufferCount;
        ComPtr<ID3D12DescriptorHeap> rtvHeap;
        requireSuccess(
            device->CreateDescriptorHeap(
                &rtvHeapDescription, IID_PPV_ARGS(&rtvHeap)),
            "ID3D12Device::CreateDescriptorHeap(RTV)");
        std::array<ComPtr<ID3D12Resource>, kBufferCount> backBuffers;
        createBackBuffers(device.Get(), swapchain.Get(), rtvHeap.Get(), backBuffers);

        ComPtr<ID3D12Fence> fence;
        requireSuccess(
            device->CreateFence(0, D3D12_FENCE_FLAG_NONE, IID_PPV_ARGS(&fence)),
            "ID3D12Device::CreateFence");
        ScopedHandle completionEvent(CreateEventW(nullptr, FALSE, FALSE, nullptr));
        requireWin32(completionEvent.get() != nullptr, "CreateEventW");
        std::uint64_t nextFenceValue = 1;

        snapshot.firstBackBufferMatched = renderAndVerify(
            device.Get(), queue.Get(), allocator.Get(), commandList.Get(),
            rtvHeap.Get(), swapchain.Get(), backBuffers, fence.Get(),
            completionEvent.get(), nextFenceValue,
            {1.0F, 0.0F, 0.0F, 1.0F});
        if (!snapshot.firstBackBufferMatched) {
            throw std::runtime_error("The first DXGI backbuffer was not solid red.");
        }
        requireExactSuccess(swapchain->Present(0, 0), "IDXGISwapChain::Present(first)");
        snapshot.firstPresentSucceeded = true;
        waitForQueue(
            queue.Get(), fence.Get(), completionEvent.get(), nextFenceValue);

        for (auto& backBuffer : backBuffers) backBuffer.Reset();
        window.resize(resizedWidth, resizedHeight);
        requireSuccess(
            swapchain->ResizeBuffers(
                kBufferCount, resizedWidth, resizedHeight,
                DXGI_FORMAT_R8G8B8A8_UNORM, 0),
            "IDXGISwapChain::ResizeBuffers");
        snapshot.resizeSucceeded = true;
        createBackBuffers(device.Get(), swapchain.Get(), rtvHeap.Get(), backBuffers);
        window.waitForCompositorReady(swapchain.Get());

        snapshot.secondBackBufferMatched = renderAndVerify(
            device.Get(), queue.Get(), allocator.Get(), commandList.Get(),
            rtvHeap.Get(), swapchain.Get(), backBuffers, fence.Get(),
            completionEvent.get(), nextFenceValue,
            {0.0F, 1.0F, 0.0F, 1.0F});
        if (!snapshot.secondBackBufferMatched) {
            throw std::runtime_error("The second DXGI backbuffer was not solid green.");
        }
        requireExactSuccess(swapchain->Present(0, 0), "IDXGISwapChain::Present(second)");
        snapshot.secondPresentSucceeded = true;
        waitForQueue(
            queue.Get(), fence.Get(), completionEvent.get(), nextFenceValue);

        snapshot.openGlContextStillCurrent =
            wglGetCurrentContext() == window.renderingContext() &&
            wglGetCurrentDC() == window.deviceContext();
        if (!snapshot.openGlContextStillCurrent) {
            throw std::runtime_error("The WGL context was lost during DXGI presentation.");
        }
        snapshot.deviceRemovedReason = static_cast<std::int64_t>(
            device->GetDeviceRemovedReason());
        if (snapshot.deviceRemovedReason != 0) {
            throw std::runtime_error("The D3D12 device was removed.");
        }
        snapshot.message = "Same-HWND DXGI presentation completed.";
    } catch (const std::exception& error) {
        if (device != nullptr) {
            snapshot.deviceRemovedReason = static_cast<std::int64_t>(
                device->GetDeviceRemovedReason());
        }
        snapshot.message = error.what();
    } catch (...) {
        if (device != nullptr) {
            snapshot.deviceRemovedReason = static_cast<std::int64_t>(
                device->GetDeviceRemovedReason());
        }
        snapshot.message = "Unknown same-HWND DXGI presentation failure.";
    }
    return snapshot;
}

}
