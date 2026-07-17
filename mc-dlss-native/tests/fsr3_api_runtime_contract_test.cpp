#include "fsr3_api_runtime.h"

#include <iostream>

int main() {
    auto missing = mc_dlss::Fsr3ApiRuntime::load(L"Z:\\missing\\amd_fidelityfx_loader_dx12.dll");
    if (missing.runtime || missing.message.find("LoadLibraryExW") == std::string::npos) {
        std::cerr << "Missing loader was not rejected: " << missing.message << '\n';
        return 1;
    }

    auto wrong = mc_dlss::Fsr3ApiRuntime::load(L"C:\\Windows\\System32\\kernel32.dll");
    if (wrong.runtime || wrong.message.find("exports") == std::string::npos) {
        std::cerr << "DLL without FidelityFX exports was accepted: " << wrong.message << '\n';
        return 1;
    }

    if (mc_dlss::describeFfxReturnCode(0) != "OK" ||
        mc_dlss::describeFfxReturnCode(4) != "NO_PROVIDER" ||
        mc_dlss::describeFfxReturnCode(999) != "UNKNOWN_999") {
        std::cerr << "FidelityFX return-code mapping is unstable\n";
        return 1;
    }

    wrong.runtime.reset();
    wrong.runtime.reset();
    std::cout << "fsr3_api_runtime_contract_test passed\n";
    return 0;
}
