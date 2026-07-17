if (NOT DEFINED BOOTSTRAP_DLL OR NOT EXISTS "${BOOTSTRAP_DLL}")
    message(FATAL_ERROR "Bootstrap DLL is unavailable: ${BOOTSTRAP_DLL}")
endif()

file(STRINGS "${BOOTSTRAP_DLL}" IMPORTED_RUNTIME_NAMES
    REGEX "(MSVCP|VCRUNTIME|msvcp|vcruntime)[0-9_]*\\.dll")
if (IMPORTED_RUNTIME_NAMES)
    message(FATAL_ERROR
        "Bootstrap must not depend on a machine-installed C++ runtime: "
        "${IMPORTED_RUNTIME_NAMES}")
endif()
