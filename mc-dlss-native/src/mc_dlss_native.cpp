#include "mc_dlss_native.h"

#ifdef _WIN32
#include "d3d12_adapter_identity.h"
#include "live_d3d12_frame_registry.h"
#include "live_d3d12_interop_registry.h"
#include "live_dlss_registry.h"
#endif

#include <cstring>
#include <array>
#include <algorithm>
#include <string>

namespace {
constexpr const char* kProbeMessage =
    "Native JNI bridge loaded. NVIDIA Streamline/DLSS is not linked yet.";

std::wstring toWideString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::wstring result(reinterpret_cast<const wchar_t*>(chars),
        static_cast<std::size_t>(length));
    env->ReleaseStringChars(value, chars);
    return result;
}

#ifdef _WIN32
bool decodeTemporalConstants(
    JNIEnv* env, jfloatArray constants,
    mc_dlss::LiveDlssConstantsData& decoded) {
    if (constants == nullptr || env->GetArrayLength(constants) != 85) return false;
    std::array<float, 85> values{};
    env->GetFloatArrayRegion(constants, 0, 85, values.data());
    if (env->ExceptionCheck()) return false;
    std::size_t offset = 0;
    auto copyMatrix = [&](std::array<float, 16>& target) {
        std::copy_n(values.data() + offset, target.size(), target.data());
        offset += target.size();
    };
    copyMatrix(decoded.cameraViewToClip);
    copyMatrix(decoded.clipToCameraView);
    copyMatrix(decoded.clipToPrevClip);
    copyMatrix(decoded.prevClipToClip);
    auto copyVector = [&](std::array<float, 3>& target) {
        std::copy_n(values.data() + offset, target.size(), target.data());
        offset += target.size();
    };
    copyVector(decoded.cameraPosition);
    copyVector(decoded.cameraUp);
    copyVector(decoded.cameraRight);
    copyVector(decoded.cameraForward);
    decoded.cameraNear = values[offset++];
    decoded.cameraFar = values[offset++];
    decoded.cameraFov = values[offset++];
    decoded.cameraAspect = values[offset++];
    decoded.jitterX = values[offset++];
    decoded.jitterY = values[offset++];
    decoded.motionScaleX = values[offset++];
    decoded.motionScaleY = values[offset++];
    decoded.reset = values[offset] != 0.0F;
    return true;
}
#endif
}

JNIEXPORT jstring JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeGetVersion(
    JNIEnv* env,
    jclass bridgeClass) {
    (void)bridgeClass;
    return env->NewStringUTF(MC_DLSS_NATIVE_VERSION);
}

JNIEXPORT jstring JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeProbeSystem(
    JNIEnv* env,
    jclass bridgeClass) {
    (void)bridgeClass;
    return env->NewStringUTF(kProbeMessage);
}

JNIEXPORT jstring JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeGetD3D12AdapterLuid(
    JNIEnv* env,
    jclass bridgeClass) {
    (void)bridgeClass;
#ifdef _WIN32
    const std::string luid = mc_dlss::queryHighPerformanceD3D12AdapterLuid();
    return env->NewStringUTF(luid.c_str());
#else
    return env->NewStringUTF("");
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12InteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jint width,
    jint height) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto info = mc_dlss::openLiveD3D12InteropSession(
        static_cast<std::uint32_t>(width), static_cast<std::uint32_t>(height));
    if (!info.available()) {
        return nullptr;
    }
    const jlong values[] = {
        static_cast<jlong>(info.sessionId),
        static_cast<jlong>(info.textureHandle),
        static_cast<jlong>(info.fenceHandle),
    };
    jlongArray result = env->NewLongArray(3);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 3, values);
    }
    return result;
#else
    (void)env;
    (void)width;
    (void)height;
    return nullptr;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12InteropReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    return mc_dlss::submitLiveD3D12InteropReadback(
        static_cast<std::uint64_t>(sessionId)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)sessionId;
    return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeVerifyD3D12InteropReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    return mc_dlss::verifyLiveD3D12InteropReadback(
        static_cast<std::uint64_t>(sessionId)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)sessionId;
    return JNI_FALSE;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12InteropReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto fingerprint = mc_dlss::inspectLiveD3D12InteropReadback(
        static_cast<std::uint64_t>(sessionId));
    if (!fingerprint.available) {
        return nullptr;
    }
    const jlong values[] = {
        static_cast<jlong>(fingerprint.hash),
        fingerprint.nonUniform ? 1LL : 0LL,
        static_cast<jlong>(fingerprint.nonBlackPixelCount),
    };
    jlongArray result = env->NewLongArray(3);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 3, values);
    }
    return result;
#else
    (void)env;
    (void)sessionId;
    return nullptr;
#endif
}

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12InteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    mc_dlss::closeLiveD3D12InteropSession(static_cast<std::uint64_t>(sessionId));
#else
    (void)sessionId;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12PersistentInteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jint width,
    jint height) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto info = mc_dlss::openPersistentD3D12InteropSession(
        static_cast<std::uint32_t>(width), static_cast<std::uint32_t>(height));
    if (!info.available()) {
        return nullptr;
    }
    const jlong values[] = {
        static_cast<jlong>(info.sessionId),
        static_cast<jlong>(info.textureHandle),
        static_cast<jlong>(info.fenceHandle),
    };
    jlongArray result = env->NewLongArray(3);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 3, values);
    }
    return result;
#else
    (void)env;
    (void)width;
    (void)height;
    return nullptr;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12PersistentReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong waitValue,
    jlong signalValue) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    return mc_dlss::submitPersistentD3D12InteropReadback(
        static_cast<std::uint64_t>(sessionId),
        static_cast<std::uint64_t>(waitValue),
        static_cast<std::uint64_t>(signalValue)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)sessionId;
    (void)waitValue;
    (void)signalValue;
    return JNI_FALSE;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12PersistentReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong signalValue) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto fingerprint = mc_dlss::inspectPersistentD3D12InteropReadback(
        static_cast<std::uint64_t>(sessionId),
        static_cast<std::uint64_t>(signalValue));
    const jlong values[] = {
        static_cast<jlong>(fingerprint.status),
        static_cast<jlong>(fingerprint.hash),
        fingerprint.nonUniform ? 1LL : 0LL,
        static_cast<jlong>(fingerprint.nonBlackPixelCount),
        static_cast<jlong>(fingerprint.completedFenceValue),
        static_cast<jlong>(fingerprint.lastSubmittedSignal),
    };
    jlongArray result = env->NewLongArray(6);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 6, values);
    }
    return result;
#else
    (void)env;
    (void)sessionId;
    (void)signalValue;
    return nullptr;
#endif
}

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12PersistentInteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    mc_dlss::closeLiveD3D12InteropSession(static_cast<std::uint64_t>(sessionId));
#else
    (void)sessionId;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12PersistentFrameSession(
    JNIEnv* env,
    jclass bridgeClass,
    jint width,
    jint height) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto info = mc_dlss::openPersistentD3D12FrameSession(
        static_cast<std::uint32_t>(width), static_cast<std::uint32_t>(height));
    if (!info.available()) {
        return nullptr;
    }
    const jlong values[] = {
        static_cast<jlong>(info.sessionId),
        static_cast<jlong>(info.colorTextureHandle),
        static_cast<jlong>(info.depthTextureHandle),
        static_cast<jlong>(info.fenceHandle),
    };
    jlongArray result = env->NewLongArray(4);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 4, values);
    }
    return result;
#else
    (void)env;
    (void)width;
    (void)height;
    return nullptr;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12PersistentFrameReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong waitValue,
    jlong signalValue) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    return mc_dlss::submitPersistentD3D12FrameReadback(
        static_cast<std::uint64_t>(sessionId),
        static_cast<std::uint64_t>(waitValue),
        static_cast<std::uint64_t>(signalValue)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)sessionId;
    (void)waitValue;
    (void)signalValue;
    return JNI_FALSE;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12PersistentFrameReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong signalValue) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto frame = mc_dlss::inspectPersistentD3D12FrameReadback(
        static_cast<std::uint64_t>(sessionId),
        static_cast<std::uint64_t>(signalValue));
    std::uint32_t minimumBits = 0;
    std::uint32_t maximumBits = 0;
    std::memcpy(&minimumBits, &frame.depth.minimum, sizeof(minimumBits));
    std::memcpy(&maximumBits, &frame.depth.maximum, sizeof(maximumBits));
    const jlong values[] = {
        static_cast<jlong>(frame.status),
        static_cast<jlong>(frame.color.hash),
        frame.color.nonUniform ? 1LL : 0LL,
        static_cast<jlong>(frame.color.nonBlackPixelCount),
        static_cast<jlong>(frame.depth.hash),
        frame.depth.nonUniform ? 1LL : 0LL,
        static_cast<jlong>(frame.depth.finiteSampleCount),
        static_cast<jlong>(frame.depth.inRangeSampleCount),
        static_cast<jlong>(frame.depth.sceneSampleCount),
        static_cast<jlong>(frame.depth.farSampleCount),
        static_cast<jlong>(minimumBits),
        static_cast<jlong>(maximumBits),
        static_cast<jlong>(frame.completedFenceValue),
        static_cast<jlong>(frame.lastSubmittedSignal),
    };
    jlongArray result = env->NewLongArray(14);
    if (result != nullptr) {
        env->SetLongArrayRegion(result, 0, 14, values);
    }
    return result;
#else
    (void)env;
    (void)sessionId;
    (void)signalValue;
    return nullptr;
#endif
}

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12PersistentFrameSession(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    mc_dlss::closePersistentD3D12FrameSession(
        static_cast<std::uint64_t>(sessionId));
#else
    (void)sessionId;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12PersistentMotionFrameSession(
    JNIEnv* env, jclass bridgeClass, jint width, jint height) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto info = mc_dlss::openPersistentD3D12FrameSession(
        static_cast<std::uint32_t>(width), static_cast<std::uint32_t>(height));
    if (!info.available()) return nullptr;
    const jlong values[] = {
        static_cast<jlong>(info.sessionId), static_cast<jlong>(info.colorTextureHandle),
        static_cast<jlong>(info.depthTextureHandle), static_cast<jlong>(info.motionTextureHandle),
        static_cast<jlong>(info.fenceHandle)};
    jlongArray result = env->NewLongArray(5);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 5, values);
    return result;
#else
    (void)env; (void)width; (void)height; return nullptr;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12PersistentMotionFrameReadback(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jlong waitValue, jlong signalValue) {
    (void)env; (void)bridgeClass;
#ifdef _WIN32
    return mc_dlss::submitPersistentD3D12FrameReadback(
        static_cast<std::uint64_t>(sessionId), static_cast<std::uint64_t>(waitValue),
        static_cast<std::uint64_t>(signalValue)) ? JNI_TRUE : JNI_FALSE;
#else
    (void)sessionId; (void)waitValue; (void)signalValue; return JNI_FALSE;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12PersistentMotionFrameReadback(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jlong signalValue) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto frame = mc_dlss::inspectPersistentD3D12FrameReadback(
        static_cast<std::uint64_t>(sessionId), static_cast<std::uint64_t>(signalValue));
    auto bits = [](float value) { std::uint32_t result = 0; std::memcpy(&result, &value, 4); return result; };
    const jlong values[] = {
        static_cast<jlong>(frame.status), static_cast<jlong>(frame.color.hash),
        frame.color.nonUniform ? 1LL : 0LL, static_cast<jlong>(frame.color.nonBlackPixelCount),
        static_cast<jlong>(frame.depth.hash), frame.depth.nonUniform ? 1LL : 0LL,
        static_cast<jlong>(frame.depth.finiteSampleCount), static_cast<jlong>(frame.depth.inRangeSampleCount),
        static_cast<jlong>(frame.depth.sceneSampleCount), static_cast<jlong>(frame.depth.farSampleCount),
        static_cast<jlong>(bits(frame.depth.minimum)), static_cast<jlong>(bits(frame.depth.maximum)),
        static_cast<jlong>(frame.motion.hash), static_cast<jlong>(frame.motion.finiteVectorCount),
        static_cast<jlong>(frame.motion.nonZeroVectorCount), static_cast<jlong>(frame.motion.outOfBoundsVectorCount),
        static_cast<jlong>(bits(frame.motion.minimumX)), static_cast<jlong>(bits(frame.motion.maximumX)),
        static_cast<jlong>(bits(frame.motion.minimumY)), static_cast<jlong>(bits(frame.motion.maximumY)),
        static_cast<jlong>(bits(frame.motion.maximumMagnitude)),
        static_cast<jlong>(frame.completedFenceValue), static_cast<jlong>(frame.lastSubmittedSignal)};
    jlongArray result = env->NewLongArray(23);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 23, values);
    return result;
#else
    (void)env; (void)sessionId; (void)signalValue; return nullptr;
#endif
}

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12PersistentMotionFrameSession(
    JNIEnv* env, jclass bridgeClass, jlong sessionId) {
    (void)env; (void)bridgeClass;
#ifdef _WIN32
    mc_dlss::closePersistentD3D12FrameSession(static_cast<std::uint64_t>(sessionId));
#else
    (void)sessionId;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenLiveDlssSession(
    JNIEnv* env, jclass bridgeClass, jint outputWidth, jint outputHeight,
    jstring pluginPath, jstring logPath, jint qualityMode) {
    (void)bridgeClass;
#ifdef _WIN32
    if (qualityMode < 0 || qualityMode > 3) return nullptr;
    const auto info = mc_dlss::openLiveDlssQualitySession(
        static_cast<std::uint32_t>(outputWidth),
        static_cast<std::uint32_t>(outputHeight),
        toWideString(env, pluginPath), toWideString(env, logPath),
        static_cast<mc_dlss::LiveDlssQualityMode>(qualityMode));
    if (!info.available()) return nullptr;
    const jlong values[] = {
        static_cast<jlong>(info.sessionId),
        static_cast<jlong>(info.renderWidth), static_cast<jlong>(info.renderHeight),
        static_cast<jlong>(info.outputWidth), static_cast<jlong>(info.outputHeight),
        static_cast<jlong>(info.slots[0].colorTextureHandle),
        static_cast<jlong>(info.slots[0].depthTextureHandle),
        static_cast<jlong>(info.slots[0].motionTextureHandle),
        static_cast<jlong>(info.slots[0].outputTextureHandle),
        static_cast<jlong>(info.slots[0].fenceHandle),
        static_cast<jlong>(info.slots[1].colorTextureHandle),
        static_cast<jlong>(info.slots[1].depthTextureHandle),
        static_cast<jlong>(info.slots[1].motionTextureHandle),
        static_cast<jlong>(info.slots[1].outputTextureHandle),
        static_cast<jlong>(info.slots[1].fenceHandle)};
    jlongArray result = env->NewLongArray(15);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 15, values);
    return result;
#else
    (void)env; (void)outputWidth; (void)outputHeight;
    (void)pluginPath; (void)logPath; (void)qualityMode; return nullptr;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenLiveFsr3Session(
    JNIEnv* env, jclass bridgeClass, jint outputWidth, jint outputHeight,
    jstring loaderPath, jint qualityMode) {
    (void)bridgeClass;
#if defined(_WIN32) && defined(MC_DLSS_HAS_FIDELITYFX)
    if (qualityMode < 0 || qualityMode > 3) return nullptr;
    const auto info = mc_dlss::openLiveFsr3Session(
        static_cast<std::uint32_t>(outputWidth),
        static_cast<std::uint32_t>(outputHeight),
        toWideString(env, loaderPath),
        static_cast<mc_dlss::LiveDlssQualityMode>(qualityMode));
    if (!info.available()) return nullptr;
    const jlong values[] = {
        static_cast<jlong>(info.sessionId),
        static_cast<jlong>(info.renderWidth), static_cast<jlong>(info.renderHeight),
        static_cast<jlong>(info.outputWidth), static_cast<jlong>(info.outputHeight),
        static_cast<jlong>(info.slots[0].colorTextureHandle),
        static_cast<jlong>(info.slots[0].depthTextureHandle),
        static_cast<jlong>(info.slots[0].motionTextureHandle),
        static_cast<jlong>(info.slots[0].outputTextureHandle),
        static_cast<jlong>(info.slots[0].fenceHandle),
        static_cast<jlong>(info.slots[0].generatedTextureHandle),
        static_cast<jlong>(info.slots[1].colorTextureHandle),
        static_cast<jlong>(info.slots[1].depthTextureHandle),
        static_cast<jlong>(info.slots[1].motionTextureHandle),
        static_cast<jlong>(info.slots[1].outputTextureHandle),
        static_cast<jlong>(info.slots[1].fenceHandle),
        static_cast<jlong>(info.slots[1].generatedTextureHandle)};
    jlongArray result = env->NewLongArray(17);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 17, values);
    return result;
#else
    (void)env; (void)outputWidth; (void)outputHeight;
    (void)loaderPath; (void)qualityMode; return nullptr;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitLiveDlssEvaluation(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex,
    jlong waitValue, jlong signalValue, jfloatArray constants,
    jint executionMode) {
    (void)bridgeClass;
#ifdef _WIN32
    if (constants == nullptr || env->GetArrayLength(constants) != 85
        || slotIndex < 0 || slotIndex > 1
        || executionMode < 0 || executionMode > 1) return JNI_FALSE;
    std::array<float, 85> values{};
    env->GetFloatArrayRegion(constants, 0, 85, values.data());
    if (env->ExceptionCheck()) return JNI_FALSE;
    mc_dlss::LiveDlssConstantsData decoded{};
    std::size_t offset = 0;
    auto copyMatrix = [&](std::array<float, 16>& target) {
        std::copy_n(values.data() + offset, target.size(), target.data());
        offset += target.size();
    };
    copyMatrix(decoded.cameraViewToClip);
    copyMatrix(decoded.clipToCameraView);
    copyMatrix(decoded.clipToPrevClip);
    copyMatrix(decoded.prevClipToClip);
    auto copyVector = [&](std::array<float, 3>& target) {
        std::copy_n(values.data() + offset, target.size(), target.data());
        offset += target.size();
    };
    copyVector(decoded.cameraPosition);
    copyVector(decoded.cameraUp);
    copyVector(decoded.cameraRight);
    copyVector(decoded.cameraForward);
    decoded.cameraNear = values[offset++];
    decoded.cameraFar = values[offset++];
    decoded.cameraFov = values[offset++];
    decoded.cameraAspect = values[offset++];
    decoded.jitterX = values[offset++];
    decoded.jitterY = values[offset++];
    decoded.motionScaleX = values[offset++];
    decoded.motionScaleY = values[offset++];
    decoded.reset = values[offset] != 0.0F;
    return mc_dlss::submitLiveDlssEvaluation(
        static_cast<std::uint64_t>(sessionId), static_cast<std::size_t>(slotIndex),
        static_cast<std::uint64_t>(waitValue),
        static_cast<std::uint64_t>(signalValue), decoded,
        static_cast<mc_dlss::LiveUpscalerExecutionMode>(executionMode))
            ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)sessionId; (void)slotIndex; (void)waitValue;
    (void)signalValue; (void)constants; (void)executionMode; return JNI_FALSE;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitLiveFsr3Upscale(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex,
    jlong waitValue, jlong signalValue, jfloatArray constants,
    jfloat frameTimeMilliseconds, jboolean generateFrame) {
    (void)bridgeClass;
#if defined(_WIN32) && defined(MC_DLSS_HAS_FIDELITYFX)
    if (slotIndex < 0 || slotIndex > 1) return JNI_FALSE;
    mc_dlss::LiveDlssConstantsData decoded{};
    if (!decodeTemporalConstants(env, constants, decoded)) return JNI_FALSE;
    return mc_dlss::submitLiveFsr3Upscale(
        static_cast<std::uint64_t>(sessionId), static_cast<std::size_t>(slotIndex),
        static_cast<std::uint64_t>(waitValue),
        static_cast<std::uint64_t>(signalValue), decoded,
        static_cast<float>(frameTimeMilliseconds),
        generateFrame == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)sessionId; (void)slotIndex; (void)waitValue;
    (void)signalValue; (void)constants; (void)frameTimeMilliseconds;
    (void)generateFrame;
    return JNI_FALSE;
#endif
}

JNIEXPORT jint JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativePresentLiveFsr3DxgiFrame(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jlong windowHandle,
    jint slotIndex, jlong signalValue, jint width, jint height) {
    (void)env; (void)bridgeClass;
#if defined(_WIN32) && defined(MC_DLSS_HAS_FIDELITYFX)
    if (sessionId <= 0 || windowHandle == 0 || slotIndex < 0 || slotIndex > 1 ||
        signalValue <= 0 || width <= 0 || height <= 0) return 0;
    const auto result = mc_dlss::presentLiveFsr3DxgiFrame(
        static_cast<std::uint64_t>(sessionId),
        static_cast<std::uintptr_t>(windowHandle),
        static_cast<std::size_t>(slotIndex),
        static_cast<std::uint64_t>(signalValue),
        static_cast<std::uint32_t>(width),
        static_cast<std::uint32_t>(height));
    return static_cast<jint>(result.state);
#else
    (void)sessionId; (void)windowHandle; (void)slotIndex;
    (void)signalValue; (void)width; (void)height;
    return 0;
#endif
}

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeIsLiveUpscalerSlotReady(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex) {
    (void)env; (void)bridgeClass;
#ifdef _WIN32
    if (sessionId <= 0 || slotIndex < 0 || slotIndex > 1) return JNI_FALSE;
    return mc_dlss::isLiveUpscalerSlotReady(
        static_cast<std::uint64_t>(sessionId), static_cast<std::size_t>(slotIndex))
            ? JNI_TRUE : JNI_FALSE;
#else
    (void)sessionId; (void)slotIndex; return JNI_FALSE;
#endif
}

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectLiveDlssEvaluation(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex,
    jlong signalValue) {
    (void)bridgeClass;
#ifdef _WIN32
    const auto frame = mc_dlss::inspectLiveDlssEvaluation(
        static_cast<std::uint64_t>(sessionId), static_cast<std::size_t>(slotIndex),
        static_cast<std::uint64_t>(signalValue));
    std::uint64_t millisecondsBits = 0;
    std::memcpy(&millisecondsBits, &frame.evaluationMilliseconds,
        sizeof(millisecondsBits));
    const jlong values[] = {
        frame.available ? 1LL : 0LL,
        static_cast<jlong>(frame.stage), static_cast<jlong>(frame.outputHash),
        frame.outputNonUniform ? 1LL : 0LL,
        static_cast<jlong>(frame.outputNonBlackPixelCount),
        static_cast<jlong>(frame.completedFenceValue),
        static_cast<jlong>(frame.lastSubmittedSignal),
        static_cast<jlong>(millisecondsBits),
        frame.diagnosticReadbackRequested ? 1LL : 0LL};
    jlongArray result = env->NewLongArray(9);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 9, values);
    return result;
#else
    (void)env; (void)sessionId; (void)slotIndex; (void)signalValue; return nullptr;
#endif
}

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseLiveDlssSession(
    JNIEnv* env, jclass bridgeClass, jlong sessionId) {
    (void)env; (void)bridgeClass;
#ifdef _WIN32
    mc_dlss::closeLiveDlssSession(static_cast<std::uint64_t>(sessionId));
#else
    (void)sessionId;
#endif
}

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeShutdownLiveDlssProcess(
    JNIEnv* env, jclass bridgeClass) {
    (void)env;
    (void)bridgeClass;
#ifdef _WIN32
    mc_dlss::shutdownLiveDlssProcess();
#endif
}
