cmake_minimum_required(VERSION 3.24)

project(mc_dlss_native VERSION 0.1.0 LANGUAGES CXX)

include(CTest)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(JNI REQUIRED)

set(STREAMLINE_ROOT "" CACHE PATH "Path to the NVIDIA Streamline SDK")
set(FIDELITYFX_ROOT "" CACHE PATH "Path to the FidelityFX SDK Kits/FidelityFX directory")
set(MC_DLSS_VERSION "${PROJECT_VERSION}-alpha.1" CACHE STRING
    "Product version embedded in project-authored Windows binaries")

if (WIN32)
    enable_language(RC)

    function(mc_dlss_configure_version_resource target_name original_filename file_description output_variable)
        set(MC_DLSS_RESOURCE_ORIGINAL_FILENAME "${original_filename}")
        set(MC_DLSS_RESOURCE_FILE_DESCRIPTION "${file_description}")
        set(MC_DLSS_RESOURCE_INTERNAL_NAME "${target_name}")
        set(resource_output "${CMAKE_CURRENT_BINARY_DIR}/${target_name}_version.rc")
        configure_file(
            "${CMAKE_CURRENT_SOURCE_DIR}/resources/windows_version.rc.in"
            "${resource_output}"
            @ONLY
        )
        set(${output_variable} "${resource_output}" PARENT_SCOPE)
    endfunction()

    mc_dlss_configure_version_resource(
        mc_dlss_bootstrap
        mc_dlss_bootstrap.dll
        "MC DLSS native bootstrap"
        MC_DLSS_BOOTSTRAP_VERSION_RESOURCE
    )
    mc_dlss_configure_version_resource(
        mc_dlss_native
        mc_dlss_native.dll
        "MC DLSS native rendering bridge"
        MC_DLSS_NATIVE_VERSION_RESOURCE
    )
endif()

add_library(mc_dlss_probe_support STATIC
    include/d3d12_probe_report.h
    include/frame_generation_probe_report.h
    include/gl_d3d12_interop_report.h
    include/streamline_probe_report.h
    src/d3d12_probe_report.cpp
    src/frame_generation_probe_report.cpp
    src/gl_d3d12_interop_report.cpp
    src/streamline_probe_report.cpp
)

add_executable(fsr3_capability_report_test
    tests/fsr3_capability_report_test.cpp
    src/fsr3_capability_report.cpp
)
target_include_directories(fsr3_capability_report_test PRIVATE include)
add_test(NAME fsr3_capability_report COMMAND fsr3_capability_report_test)

add_executable(upscaler_backend_test
    tests/upscaler_backend_test.cpp
    src/upscaler_backend.cpp
)
target_include_directories(upscaler_backend_test PRIVATE include)
add_test(NAME upscaler_backend COMMAND upscaler_backend_test)

add_executable(fsr3_frame_validation_test
    tests/fsr3_frame_validation_test.cpp
    src/fsr3_frame_validation.cpp
)
target_include_directories(fsr3_frame_validation_test PRIVATE include)
add_test(NAME fsr3_frame_validation COMMAND fsr3_frame_validation_test)

add_executable(dxgi_window_present_probe_report_test
    tests/dxgi_window_present_probe_report_test.cpp
    src/dxgi_window_present_probe_report.cpp
)
target_include_directories(dxgi_window_present_probe_report_test PRIVATE include)
add_test(NAME dxgi_window_present_probe_report
    COMMAND dxgi_window_present_probe_report_test
)

target_include_directories(mc_dlss_probe_support PUBLIC include)
target_compile_features(mc_dlss_probe_support PUBLIC cxx_std_17)

add_library(mc_dlss_interop_test_support STATIC
    include/interop_test_pattern.h
    src/interop_test_pattern.cpp
)
target_include_directories(mc_dlss_interop_test_support PUBLIC include)
target_compile_features(mc_dlss_interop_test_support PUBLIC cxx_std_17)

add_library(mc_dlss_depth_readback_support STATIC
    include/depth_readback_fingerprint.h
    src/depth_readback_fingerprint.cpp
)
target_include_directories(mc_dlss_depth_readback_support PUBLIC include)
target_compile_features(mc_dlss_depth_readback_support PUBLIC cxx_std_17)

add_library(mc_dlss_motion_readback_support STATIC
    include/motion_readback_fingerprint.h
    src/motion_readback_fingerprint.cpp
)
target_include_directories(mc_dlss_motion_readback_support PUBLIC include)
target_compile_features(mc_dlss_motion_readback_support PUBLIC cxx_std_17)

if (WIN32)
    add_library(mc_dlss_bootstrap SHARED
        include/native_bootstrap.h
        src/native_bootstrap.cpp
        ${MC_DLSS_BOOTSTRAP_VERSION_RESOURCE}
    )
    target_include_directories(mc_dlss_bootstrap PRIVATE include ${JNI_INCLUDE_DIRS})
    target_compile_options(mc_dlss_bootstrap PRIVATE /EHsc)
    set_property(TARGET mc_dlss_bootstrap PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    add_test(
        NAME native_bootstrap_runtime
        COMMAND ${CMAKE_COMMAND}
            -DBOOTSTRAP_DLL=$<TARGET_FILE:mc_dlss_bootstrap>
            -P ${CMAKE_CURRENT_SOURCE_DIR}/tests/check_bootstrap_runtime.cmake
    )

    add_executable(mc_dlss_native_bootstrap_test
        tests/native_bootstrap_test.cpp
        src/native_bootstrap.cpp
    )
    target_include_directories(mc_dlss_native_bootstrap_test
        PRIVATE include ${JNI_INCLUDE_DIRS}
    )
    add_test(NAME native_bootstrap COMMAND mc_dlss_native_bootstrap_test)

    add_library(mc_dlss_live_interop_support STATIC
        include/live_d3d12_interop_registry.h
        include/live_d3d12_interop_session.h
        src/live_d3d12_interop_registry.cpp
        src/live_d3d12_interop_session.cpp
    )
    target_include_directories(mc_dlss_live_interop_support PUBLIC include)
    target_compile_features(mc_dlss_live_interop_support PUBLIC cxx_std_17)
    target_link_libraries(
        mc_dlss_live_interop_support
        PUBLIC mc_dlss_interop_test_support d3d12 dxgi
    )

    add_library(mc_dlss_live_frame_support STATIC
        include/live_d3d12_frame_registry.h
        include/live_d3d12_frame_session.h
        src/live_d3d12_frame_registry.cpp
        src/live_d3d12_frame_session.cpp
    )

    add_library(mc_dlss_live_dlss_support STATIC
        include/live_dlss_session.h
        include/live_dlss_registry.h
        include/live_streamline_runtime.h
        include/fsr3_dxgi_presenter.h
        src/live_dlss_session.cpp
        src/live_dlss_registry.cpp
        src/live_streamline_runtime.cpp
        src/live_fsr3_session.cpp
        src/fsr3_dxgi_presenter.cpp
        src/fsr3_api_runtime.cpp
        src/fsr3_capability_report.cpp
    )
    target_include_directories(mc_dlss_live_dlss_support PUBLIC include)
    target_compile_features(mc_dlss_live_dlss_support PUBLIC cxx_std_17)
    target_link_libraries(mc_dlss_live_dlss_support PUBLIC d3d12 dxgi)
    target_include_directories(mc_dlss_live_frame_support PUBLIC include)
    target_compile_features(mc_dlss_live_frame_support PUBLIC cxx_std_17)
    target_link_libraries(mc_dlss_live_frame_support
        PUBLIC mc_dlss_live_interop_support mc_dlss_depth_readback_support
            mc_dlss_motion_readback_support
    )

    add_library(mc_dlss_d3d12_probe_support STATIC
        include/d3d12_adapter_identity.h
        include/d3d12_probe_context.h
        src/d3d12_adapter_identity.cpp
        src/d3d12_probe_context.cpp
    )
    target_include_directories(mc_dlss_d3d12_probe_support PUBLIC include)
    target_compile_features(mc_dlss_d3d12_probe_support PUBLIC cxx_std_17)
    target_link_libraries(mc_dlss_d3d12_probe_support PUBLIC mc_dlss_probe_support)

    add_executable(fsr3_api_runtime_contract_test
        tests/fsr3_api_runtime_contract_test.cpp
        src/fsr3_api_runtime.cpp
        src/fsr3_capability_report.cpp
    )
    target_include_directories(fsr3_api_runtime_contract_test PRIVATE include)
    add_test(NAME fsr3_api_runtime_contract COMMAND fsr3_api_runtime_contract_test)

    if (FIDELITYFX_ROOT)
        set(FIDELITYFX_INCLUDE_DIRS
            "${FIDELITYFX_ROOT}/api/include"
            "${FIDELITYFX_ROOT}/upscalers/include"
            "${FIDELITYFX_ROOT}/framegeneration/include"
        )
        set(FIDELITYFX_RUNTIME_DIR "${FIDELITYFX_ROOT}/signedbin")
        set(FIDELITYFX_REQUIRED_FILES
            "${FIDELITYFX_ROOT}/api/include/ffx_api.h"
            "${FIDELITYFX_ROOT}/api/include/dx12/ffx_api_dx12.h"
            "${FIDELITYFX_ROOT}/upscalers/include/ffx_upscale.h"
            "${FIDELITYFX_ROOT}/framegeneration/include/ffx_framegeneration.h"
            "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_loader_dx12.dll"
            "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_upscaler_dx12.dll"
            "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_framegeneration_dx12.dll"
        )
        foreach(FIDELITYFX_REQUIRED_FILE IN LISTS FIDELITYFX_REQUIRED_FILES)
            if (NOT EXISTS "${FIDELITYFX_REQUIRED_FILE}")
                message(FATAL_ERROR "FidelityFX SDK file is missing: ${FIDELITYFX_REQUIRED_FILE}")
            endif()
        endforeach()

        add_executable(mc_dlss_fsr3_capability_probe
            src/fsr3_capability_probe_main.cpp
            src/fsr3_api_runtime.cpp
            src/fsr3_capability_report.cpp
        )
        target_compile_definitions(mc_dlss_fsr3_capability_probe
            PRIVATE MC_DLSS_HAS_FIDELITYFX=1)
        target_include_directories(mc_dlss_fsr3_capability_probe
            PRIVATE include ${FIDELITYFX_INCLUDE_DIRS})
        target_link_libraries(mc_dlss_fsr3_capability_probe
            PRIVATE mc_dlss_d3d12_probe_support d3d12 dxgi)

        target_compile_definitions(mc_dlss_live_dlss_support
            PRIVATE MC_DLSS_HAS_FIDELITYFX=1)
        target_include_directories(mc_dlss_live_dlss_support
            PRIVATE ${FIDELITYFX_INCLUDE_DIRS})

        add_executable(live_fsr3_session_real_test
            tests/live_fsr3_session_real_test.cpp
            src/live_fsr3_session.cpp
            src/fsr3_api_runtime.cpp
            src/fsr3_capability_report.cpp
        )
        target_compile_definitions(live_fsr3_session_real_test
            PRIVATE MC_DLSS_HAS_FIDELITYFX=1)
        target_include_directories(live_fsr3_session_real_test
            PRIVATE include ${FIDELITYFX_INCLUDE_DIRS})
        target_link_libraries(live_fsr3_session_real_test
            PRIVATE mc_dlss_d3d12_probe_support d3d12 dxgi)
        add_test(NAME live_fsr3_session_real
            COMMAND live_fsr3_session_real_test
                "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_loader_dx12.dll")

        add_executable(live_fsr3_upscale_real_test
            tests/live_fsr3_upscale_real_test.cpp
            src/live_fsr3_session.cpp
            src/fsr3_api_runtime.cpp
            src/fsr3_capability_report.cpp
        )
        target_compile_definitions(live_fsr3_upscale_real_test
            PRIVATE MC_DLSS_HAS_FIDELITYFX=1)
        target_include_directories(live_fsr3_upscale_real_test
            PRIVATE include ${FIDELITYFX_INCLUDE_DIRS})
        target_link_libraries(live_fsr3_upscale_real_test
            PRIVATE mc_dlss_d3d12_probe_support d3d12 dxgi)
        add_test(NAME live_fsr3_upscale_real
            COMMAND live_fsr3_upscale_real_test
                "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_loader_dx12.dll")

        add_executable(live_fsr3_frame_generation_real_test
            tests/live_fsr3_frame_generation_real_test.cpp
            src/live_fsr3_session.cpp
            src/fsr3_api_runtime.cpp
            src/fsr3_capability_report.cpp
        )
        target_compile_definitions(live_fsr3_frame_generation_real_test
            PRIVATE MC_DLSS_HAS_FIDELITYFX=1)
        target_include_directories(live_fsr3_frame_generation_real_test
            PRIVATE include ${FIDELITYFX_INCLUDE_DIRS})
        target_link_libraries(live_fsr3_frame_generation_real_test
            PRIVATE mc_dlss_d3d12_probe_support d3d12 dxgi)
        add_test(NAME live_fsr3_frame_generation_real
            COMMAND live_fsr3_frame_generation_real_test
                "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_loader_dx12.dll")

        add_executable(live_fsr3_combined_session_real_test
            tests/live_fsr3_combined_session_real_test.cpp)
        target_include_directories(live_fsr3_combined_session_real_test
            PRIVATE include)
        target_link_libraries(live_fsr3_combined_session_real_test
            PRIVATE mc_dlss_live_dlss_support d3d12 dxgi)
        add_test(NAME live_fsr3_combined_session_real
            COMMAND live_fsr3_combined_session_real_test
                "${FIDELITYFX_RUNTIME_DIR}/amd_fidelityfx_loader_dx12.dll")
        foreach(FIDELITYFX_DLL IN ITEMS
            amd_fidelityfx_loader_dx12.dll
            amd_fidelityfx_upscaler_dx12.dll
            amd_fidelityfx_framegeneration_dx12.dll)
            add_custom_command(TARGET mc_dlss_fsr3_capability_probe POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    "${FIDELITYFX_RUNTIME_DIR}/${FIDELITYFX_DLL}"
                    "$<TARGET_FILE_DIR:mc_dlss_fsr3_capability_probe>/${FIDELITYFX_DLL}")
        endforeach()
    endif()
endif()

add_library(mc_dlss_native SHARED
    include/mc_dlss_native.h
    src/mc_dlss_native.cpp
    ${MC_DLSS_NATIVE_VERSION_RESOURCE}
)

if (FIDELITYFX_ROOT)
    target_compile_definitions(mc_dlss_native
        PRIVATE MC_DLSS_HAS_FIDELITYFX=1)
endif()

target_include_directories(mc_dlss_native
    PRIVATE
        include
        ${JNI_INCLUDE_DIRS}
)

target_compile_definitions(mc_dlss_native
    PRIVATE
        MC_DLSS_NATIVE_VERSION="mc-dlss-native/0.1.0"
)

target_link_libraries(mc_dlss_native PRIVATE mc_dlss_probe_support)

if (WIN32)
    target_link_libraries(mc_dlss_native
        PRIVATE
            mc_dlss_d3d12_probe_support
            mc_dlss_live_interop_support
            mc_dlss_live_frame_support
            mc_dlss_live_dlss_support
            d3d12
            dxgi
    )

    add_executable(mc_dlss_jni_exports_test tests/jni_exports_test.cpp)
    add_dependencies(mc_dlss_jni_exports_test mc_dlss_native)
    add_test(NAME jni_exports COMMAND mc_dlss_jni_exports_test
        $<TARGET_FILE:mc_dlss_native>)
endif()

if (WIN32)
    add_executable(mc_dlss_gl_d3d12_interop_probe
        src/gl_d3d12_interop_probe.cpp
        src/gl_d3d12_interop_probe_main.cpp
    )
    target_include_directories(mc_dlss_gl_d3d12_interop_probe PRIVATE include)
    target_link_libraries(mc_dlss_gl_d3d12_interop_probe
        PRIVATE
            mc_dlss_interop_test_support
            mc_dlss_probe_support
            d3d12
            dxgi
            dxguid
            opengl32
            gdi32
            user32
    )

    add_executable(mc_dlss_dxgi_window_present_probe
        src/dxgi_window_present_probe.cpp
        src/dxgi_window_present_probe_main.cpp
        src/dxgi_window_present_probe_report.cpp
    )
    target_include_directories(mc_dlss_dxgi_window_present_probe PRIVATE include)
    target_link_libraries(mc_dlss_dxgi_window_present_probe
        PRIVATE
            d3d12
            dxgi
            dxguid
            dwmapi
            opengl32
            gdi32
            user32
    )

    add_executable(mc_dlss_resource_probe
        src/d3d12_resource_probe.cpp
        src/d3d12_resource_probe_main.cpp
    )
    target_include_directories(mc_dlss_resource_probe PRIVATE include)
    target_link_libraries(mc_dlss_resource_probe
        PRIVATE
            mc_dlss_d3d12_probe_support
            d3d12
            dxgi
            dxguid
    )

    if (STREAMLINE_ROOT)
        set(STREAMLINE_INCLUDE_DIR "${STREAMLINE_ROOT}/include")
        set(STREAMLINE_LIBRARY "${STREAMLINE_ROOT}/lib/x64/sl.interposer.lib")
        set(STREAMLINE_RUNTIME_DIR "${STREAMLINE_ROOT}/bin/x64")
        set(STREAMLINE_REQUIRED_FILES
            "${STREAMLINE_INCLUDE_DIR}/sl.h"
            "${STREAMLINE_LIBRARY}"
            "${STREAMLINE_RUNTIME_DIR}/sl.interposer.dll"
            "${STREAMLINE_RUNTIME_DIR}/sl.common.dll"
            "${STREAMLINE_RUNTIME_DIR}/sl.pcl.dll"
            "${STREAMLINE_RUNTIME_DIR}/sl.dlss.dll"
            "${STREAMLINE_RUNTIME_DIR}/nvngx_dlss.dll"
            "${STREAMLINE_RUNTIME_DIR}/sl.dlss_g.dll"
            "${STREAMLINE_RUNTIME_DIR}/sl.reflex.dll"
            "${STREAMLINE_RUNTIME_DIR}/nvngx_dlssg.dll"
        )
        foreach(STREAMLINE_REQUIRED_FILE IN LISTS STREAMLINE_REQUIRED_FILES)
            if (NOT EXISTS "${STREAMLINE_REQUIRED_FILE}")
                message(FATAL_ERROR "Streamline SDK file is missing: ${STREAMLINE_REQUIRED_FILE}")
            endif()
        endforeach()

        add_executable(mc_dlss_streamline_probe
            src/streamline_dlss_probe.cpp
            src/streamline_dlss_probe_main.cpp
        )

        add_executable(mc_dlss_frame_generation_probe
            src/frame_generation_probe.cpp
            src/frame_generation_probe_main.cpp
        )
        target_include_directories(mc_dlss_frame_generation_probe
            PRIVATE include "${STREAMLINE_INCLUDE_DIR}"
        )
        target_link_libraries(mc_dlss_frame_generation_probe
            PRIVATE mc_dlss_d3d12_probe_support "${STREAMLINE_LIBRARY}" dxguid
        )
        target_include_directories(mc_dlss_streamline_probe
            PRIVATE
                include
                "${STREAMLINE_INCLUDE_DIR}"
        )
        target_link_libraries(mc_dlss_streamline_probe
            PRIVATE
                mc_dlss_d3d12_probe_support
                "${STREAMLINE_LIBRARY}"
                dxguid
        )

        target_compile_definitions(
            mc_dlss_live_dlss_support PRIVATE MC_DLSS_HAS_STREAMLINE=1
        )
        target_include_directories(
            mc_dlss_live_dlss_support PRIVATE "${STREAMLINE_INCLUDE_DIR}"
        )
        target_link_libraries(
            mc_dlss_live_dlss_support PUBLIC "${STREAMLINE_LIBRARY}" dxguid
        )

        add_executable(mc_dlss_live_streamline_runtime_real_test
            tests/live_streamline_runtime_real_test.cpp
        )
        target_link_libraries(
            mc_dlss_live_streamline_runtime_real_test
            PRIVATE mc_dlss_live_dlss_support
        )
        add_test(
            NAME live_streamline_runtime_real
            COMMAND mc_dlss_live_streamline_runtime_real_test
        )

        add_executable(mc_dlss_live_streamline_evaluation_real_test
            tests/live_streamline_evaluation_real_test.cpp
        )
        target_link_libraries(
            mc_dlss_live_streamline_evaluation_real_test
            PRIVATE mc_dlss_live_dlss_support
        )
        add_test(
            NAME live_streamline_evaluation_real
            COMMAND mc_dlss_live_streamline_evaluation_real_test
        )

        foreach(STREAMLINE_RUNTIME_DLL
            sl.interposer.dll
            sl.common.dll
            sl.pcl.dll
            sl.dlss.dll
            nvngx_dlss.dll
            sl.dlss_g.dll
            sl.reflex.dll
            nvngx_dlssg.dll)
            add_custom_command(TARGET mc_dlss_streamline_probe POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    "${STREAMLINE_RUNTIME_DIR}/${STREAMLINE_RUNTIME_DLL}"
                    "$<TARGET_FILE_DIR:mc_dlss_streamline_probe>/${STREAMLINE_RUNTIME_DLL}"
                VERBATIM
            )
            add_custom_command(TARGET mc_dlss_native POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    "${STREAMLINE_RUNTIME_DIR}/${STREAMLINE_RUNTIME_DLL}"
                    "$<TARGET_FILE_DIR:mc_dlss_native>/${STREAMLINE_RUNTIME_DLL}"
                VERBATIM
            )
            add_custom_command(TARGET mc_dlss_frame_generation_probe POST_BUILD
                COMMAND ${CMAKE_COMMAND} -E copy_if_different
                    "${STREAMLINE_RUNTIME_DIR}/${STREAMLINE_RUNTIME_DLL}"
                    "$<TARGET_FILE_DIR:mc_dlss_frame_generation_probe>/${STREAMLINE_RUNTIME_DLL}"
                VERBATIM
            )
        endforeach()
    endif()
endif()

if (MSVC)
    target_compile_options(mc_dlss_probe_support PRIVATE /W4 /permissive-)
    target_compile_options(mc_dlss_interop_test_support PRIVATE /W4 /permissive-)
    if (TARGET mc_dlss_d3d12_probe_support)
        target_compile_options(mc_dlss_d3d12_probe_support PRIVATE /W4 /permissive-)
    endif()
    if (TARGET mc_dlss_live_interop_support)
        target_compile_options(mc_dlss_live_interop_support PRIVATE /W4 /permissive-)
    endif()
    target_compile_options(mc_dlss_native PRIVATE /W4 /permissive-)
    if (TARGET mc_dlss_resource_probe)
        target_compile_options(mc_dlss_resource_probe PRIVATE /W4 /permissive-)
    endif()
    if (TARGET mc_dlss_gl_d3d12_interop_probe)
        target_compile_options(mc_dlss_gl_d3d12_interop_probe PRIVATE /W4 /permissive-)
    endif()
    if (TARGET mc_dlss_dxgi_window_present_probe)
        target_compile_options(mc_dlss_dxgi_window_present_probe PRIVATE /W4 /permissive-)
    endif()
    if (TARGET mc_dlss_streamline_probe)
        target_compile_options(mc_dlss_streamline_probe PRIVATE /W4 /permissive-)
    endif()
else()
    target_compile_options(mc_dlss_probe_support PRIVATE -Wall -Wextra -Wpedantic)
    target_compile_options(mc_dlss_interop_test_support PRIVATE -Wall -Wextra -Wpedantic)
    if (TARGET mc_dlss_d3d12_probe_support)
        target_compile_options(mc_dlss_d3d12_probe_support PRIVATE -Wall -Wextra -Wpedantic)
    endif()
    if (TARGET mc_dlss_live_interop_support)
        target_compile_options(
            mc_dlss_live_interop_support PRIVATE -Wall -Wextra -Wpedantic
        )
    endif()
    target_compile_options(mc_dlss_native PRIVATE -Wall -Wextra -Wpedantic)
    if (TARGET mc_dlss_resource_probe)
        target_compile_options(mc_dlss_resource_probe PRIVATE -Wall -Wextra -Wpedantic)
    endif()
    if (TARGET mc_dlss_gl_d3d12_interop_probe)
        target_compile_options(
            mc_dlss_gl_d3d12_interop_probe
            PRIVATE -Wall -Wextra -Wpedantic
        )
    endif()
    if (TARGET mc_dlss_dxgi_window_present_probe)
        target_compile_options(
            mc_dlss_dxgi_window_present_probe
            PRIVATE -Wall -Wextra -Wpedantic
        )
    endif()
    if (TARGET mc_dlss_streamline_probe)
        target_compile_options(mc_dlss_streamline_probe PRIVATE -Wall -Wextra -Wpedantic)
    endif()
endif()

if (BUILD_TESTING)
    if (WIN32)
        add_executable(mc_dlss_live_dlss_session_test
            tests/live_dlss_session_test.cpp
        )
        target_link_libraries(
            mc_dlss_live_dlss_session_test PRIVATE mc_dlss_live_dlss_support
        )
        add_test(NAME live_dlss_session COMMAND mc_dlss_live_dlss_session_test)

        add_executable(mc_dlss_live_streamline_runtime_contract_test
            tests/live_streamline_runtime_contract_test.cpp
        )
        target_link_libraries(
            mc_dlss_live_streamline_runtime_contract_test
            PRIVATE mc_dlss_live_dlss_support
        )
        add_test(
            NAME live_streamline_runtime_contract
            COMMAND mc_dlss_live_streamline_runtime_contract_test
        )
    endif()

    add_executable(mc_dlss_motion_readback_fingerprint_test
        tests/motion_readback_fingerprint_test.cpp
    )
    target_link_libraries(
        mc_dlss_motion_readback_fingerprint_test
        PRIVATE mc_dlss_motion_readback_support
    )
    add_test(
        NAME motion_readback_fingerprint
        COMMAND mc_dlss_motion_readback_fingerprint_test
    )

    add_executable(mc_dlss_depth_readback_fingerprint_test
        tests/depth_readback_fingerprint_test.cpp
    )
    target_include_directories(
        mc_dlss_depth_readback_fingerprint_test PRIVATE include
    )
    target_link_libraries(
        mc_dlss_depth_readback_fingerprint_test
        PRIVATE mc_dlss_depth_readback_support
    )
    add_test(
        NAME depth_readback_fingerprint
        COMMAND mc_dlss_depth_readback_fingerprint_test
    )

    add_executable(mc_dlss_interop_test_pattern_test
        tests/interop_test_pattern_test.cpp
    )
    target_include_directories(mc_dlss_interop_test_pattern_test PRIVATE include)
    target_link_libraries(
        mc_dlss_interop_test_pattern_test
        PRIVATE mc_dlss_interop_test_support
    )
    add_test(
        NAME interop_test_pattern
        COMMAND mc_dlss_interop_test_pattern_test
    )

    add_executable(mc_dlss_probe_report_test
        tests/d3d12_probe_report_test.cpp
    )
    target_link_libraries(mc_dlss_probe_report_test PRIVATE mc_dlss_probe_support)
    add_test(NAME d3d12_probe_report COMMAND mc_dlss_probe_report_test)

    add_executable(mc_dlss_streamline_probe_report_test
        tests/streamline_probe_report_test.cpp
    )
    target_link_libraries(mc_dlss_streamline_probe_report_test PRIVATE mc_dlss_probe_support)
    add_test(
        NAME streamline_probe_report
        COMMAND mc_dlss_streamline_probe_report_test
    )

    add_executable(mc_dlss_frame_generation_probe_report_test
        tests/frame_generation_probe_report_test.cpp
    )
    target_link_libraries(
        mc_dlss_frame_generation_probe_report_test PRIVATE mc_dlss_probe_support
    )
    add_test(
        NAME frame_generation_probe_report
        COMMAND mc_dlss_frame_generation_probe_report_test
    )

    add_executable(mc_dlss_gl_d3d12_interop_report_test
        tests/gl_d3d12_interop_report_test.cpp
    )
    target_link_libraries(
        mc_dlss_gl_d3d12_interop_report_test
        PRIVATE mc_dlss_probe_support
    )
    add_test(
        NAME gl_d3d12_interop_report
        COMMAND mc_dlss_gl_d3d12_interop_report_test
    )

    if (WIN32)
        add_executable(mc_dlss_live_d3d12_frame_session_test
            tests/live_d3d12_frame_session_test.cpp
        )
        target_include_directories(
            mc_dlss_live_d3d12_frame_session_test PRIVATE include
        )
        target_link_libraries(
            mc_dlss_live_d3d12_frame_session_test
            PRIVATE mc_dlss_live_frame_support
        )
        add_test(
            NAME live_d3d12_frame_session
            COMMAND mc_dlss_live_d3d12_frame_session_test
        )

        add_executable(mc_dlss_live_d3d12_interop_session_test
            tests/live_d3d12_interop_session_test.cpp
        )
        target_include_directories(
            mc_dlss_live_d3d12_interop_session_test PRIVATE include
        )
        target_link_libraries(
            mc_dlss_live_d3d12_interop_session_test
            PRIVATE mc_dlss_live_interop_support
        )
        add_test(
            NAME live_d3d12_interop_session
            COMMAND mc_dlss_live_d3d12_interop_session_test
        )

        add_executable(mc_dlss_d3d12_adapter_identity_test
            tests/d3d12_adapter_identity_test.cpp
        )
        target_link_libraries(
            mc_dlss_d3d12_adapter_identity_test
            PRIVATE mc_dlss_d3d12_probe_support d3d12 dxgi
        )
        add_test(
            NAME d3d12_adapter_identity
            COMMAND mc_dlss_d3d12_adapter_identity_test
        )

        add_test(
            NAME gl_d3d12_interop_probe
            COMMAND mc_dlss_gl_d3d12_interop_probe
                --width 64
                --height 64
                --report ${CMAKE_BINARY_DIR}/gl-d3d12-interop-probe-test.json
        )

        add_test(
            NAME dxgi_window_present_probe_real
            COMMAND mc_dlss_dxgi_window_present_probe
                --width 320
                --height 180
                --resized-width 640
                --resized-height 360
                --report ${CMAKE_BINARY_DIR}/dxgi-window-present-probe-test.json
        )
        set_tests_properties(dxgi_window_present_probe_real PROPERTIES
            LABELS "gpu;windows;dxgi"
        )

        add_test(
            NAME d3d12_resource_probe
            COMMAND mc_dlss_resource_probe
                --input-width 1280
                --input-height 720
                --output-width 1920
                --output-height 1080
                --report ${CMAKE_BINARY_DIR}/d3d12-resource-probe-test.json
        )

        if (TARGET mc_dlss_streamline_probe)
            add_test(
                NAME streamline_dlss_probe
                COMMAND mc_dlss_streamline_probe
                    --plugin-path ${STREAMLINE_RUNTIME_DIR}
                    --log-path ${CMAKE_BINARY_DIR}/streamline-logs
                    --report ${CMAKE_BINARY_DIR}/streamline-dlss-probe-test.json
            )
        endif()
        if (TARGET mc_dlss_frame_generation_probe)
            target_compile_options(
                mc_dlss_frame_generation_probe PRIVATE /W4 /permissive-
            )
        endif()
    endif()
endif()
