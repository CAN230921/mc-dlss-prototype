#include "native_bootstrap.h"

#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <jni.h>

#include <mutex>
#include <vector>

namespace mc_dlss {
namespace {

std::mutex bootstrapMutex;
std::wstring retainedDirectory;
DLL_DIRECTORY_COOKIE retainedCookie = nullptr;

bool isAbsoluteWindowsPath(const std::wstring& path) noexcept {
    const bool driveAbsolute = path.size() >= 3
        && ((path[0] >= L'A' && path[0] <= L'Z')
            || (path[0] >= L'a' && path[0] <= L'z'))
        && path[1] == L':' && (path[2] == L'\\' || path[2] == L'/');
    const bool uncAbsolute = path.size() >= 3
        && (path[0] == L'\\' || path[0] == L'/')
        && (path[1] == L'\\' || path[1] == L'/');
    return driveAbsolute || uncAbsolute;
}

std::wstring fullPath(const std::wstring& path) {
    const DWORD required = GetFullPathNameW(path.c_str(), 0, nullptr, nullptr);
    if (required == 0) return {};
    std::vector<wchar_t> buffer(static_cast<std::size_t>(required));
    const DWORD written = GetFullPathNameW(
        path.c_str(), required, buffer.data(), nullptr);
    if (written == 0 || written >= required) return {};
    return std::wstring(buffer.data(), static_cast<std::size_t>(written));
}

std::string windowsError(const char* operation) {
    return std::string(operation) + " failed with Windows error "
        + std::to_string(GetLastError());
}

}

std::string prepareNativeBundleDirectory(const std::wstring& directory) noexcept {
    try {
        if (directory.empty()) return "Native bundle directory is empty";
        if (!isAbsoluteWindowsPath(directory)) {
            return "Native bundle directory must be absolute";
        }
        const std::wstring canonical = fullPath(directory);
        if (canonical.empty()) return windowsError("GetFullPathNameW");
        const DWORD attributes = GetFileAttributesW(canonical.c_str());
        if (attributes == INVALID_FILE_ATTRIBUTES
                || (attributes & FILE_ATTRIBUTE_DIRECTORY) == 0) {
            return "Native bundle directory does not exist";
        }
        std::lock_guard<std::mutex> lock(bootstrapMutex);
        if (!retainedDirectory.empty()) {
            return _wcsicmp(retainedDirectory.c_str(), canonical.c_str()) == 0
                ? std::string{} : "A different native bundle directory is already active";
        }
        if (!SetDefaultDllDirectories(
                LOAD_LIBRARY_SEARCH_DEFAULT_DIRS | LOAD_LIBRARY_SEARCH_USER_DIRS)) {
            return windowsError("SetDefaultDllDirectories");
        }
        DLL_DIRECTORY_COOKIE cookie = AddDllDirectory(canonical.c_str());
        if (cookie == nullptr) return windowsError("AddDllDirectory");
        retainedCookie = cookie;
        retainedDirectory = canonical;
        return {};
    } catch (const std::exception& error) {
        return error.what();
    } catch (...) {
        return "Unknown native bootstrap failure";
    }
}

}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mcdlss_core_NativeBundleBootstrap_nativePrepareDirectory(
    JNIEnv* environment, jclass, jstring directory) {
    if (environment == nullptr || directory == nullptr) {
        return environment == nullptr ? nullptr
            : environment->NewStringUTF("Native bundle directory is required");
    }
    const jchar* characters = environment->GetStringChars(directory, nullptr);
    if (characters == nullptr) return nullptr;
    const jsize length = environment->GetStringLength(directory);
    std::wstring value(
        reinterpret_cast<const wchar_t*>(characters), static_cast<std::size_t>(length));
    environment->ReleaseStringChars(directory, characters);
    const std::string result = mc_dlss::prepareNativeBundleDirectory(value);
    return environment->NewStringUTF(result.c_str());
}
