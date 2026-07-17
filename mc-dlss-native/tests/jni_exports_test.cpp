#ifdef _WIN32
#include <windows.h>

#include <iostream>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2) return 2;
    HMODULE library = LoadLibraryW(argv[1]);
    if (library == nullptr) {
        std::cerr << "Unable to load mc_dlss_native.dll\n";
        return 3;
    }
    const char* required[] = {
        "Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenLiveFsr3Session",
        "Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitLiveFsr3Upscale",
        "Java_dev_mcdlss_core_NativeLibraryBridge_nativePresentLiveFsr3DxgiFrame",
    };
    for (const char* name : required) {
        if (GetProcAddress(library, name) == nullptr) {
            std::cerr << "Missing JNI export: " << name << '\n';
            FreeLibrary(library);
            return 1;
        }
    }
    FreeLibrary(library);
    return 0;
}
#endif
