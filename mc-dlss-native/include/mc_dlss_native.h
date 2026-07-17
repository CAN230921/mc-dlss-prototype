#pragma once

#include <jni.h>

extern "C" {

JNIEXPORT jstring JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeGetVersion(
    JNIEnv* env,
    jclass bridgeClass);

JNIEXPORT jstring JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeProbeSystem(
    JNIEnv* env,
    jclass bridgeClass);

JNIEXPORT jstring JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeGetD3D12AdapterLuid(
    JNIEnv* env,
    jclass bridgeClass);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12InteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jint width,
    jint height);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12InteropReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeVerifyD3D12InteropReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12InteropReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId);

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12InteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12PersistentInteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jint width,
    jint height);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12PersistentReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong waitValue,
    jlong signalValue);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12PersistentReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong signalValue);

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12PersistentInteropSession(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12PersistentFrameSession(
    JNIEnv* env,
    jclass bridgeClass,
    jint width,
    jint height);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12PersistentFrameReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong waitValue,
    jlong signalValue);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12PersistentFrameReadback(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId,
    jlong signalValue);

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12PersistentFrameSession(
    JNIEnv* env,
    jclass bridgeClass,
    jlong sessionId);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenD3D12PersistentMotionFrameSession(
    JNIEnv* env, jclass bridgeClass, jint width, jint height);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitD3D12PersistentMotionFrameReadback(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jlong waitValue, jlong signalValue);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectD3D12PersistentMotionFrameReadback(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jlong signalValue);

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseD3D12PersistentMotionFrameSession(
    JNIEnv* env, jclass bridgeClass, jlong sessionId);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenLiveDlssSession(
    JNIEnv* env, jclass bridgeClass, jint outputWidth, jint outputHeight,
    jstring pluginPath, jstring logPath, jint qualityMode);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeOpenLiveFsr3Session(
    JNIEnv* env, jclass bridgeClass, jint outputWidth, jint outputHeight,
    jstring loaderPath, jint qualityMode);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitLiveDlssEvaluation(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex,
    jlong waitValue, jlong signalValue, jfloatArray constants,
    jint executionMode);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeSubmitLiveFsr3Upscale(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex,
    jlong waitValue, jlong signalValue, jfloatArray constants,
    jfloat frameTimeMilliseconds, jboolean generateFrame);

JNIEXPORT jint JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativePresentLiveFsr3DxgiFrame(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jlong windowHandle,
    jint slotIndex, jlong signalValue, jint width, jint height);

JNIEXPORT jboolean JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeIsLiveUpscalerSlotReady(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex);

JNIEXPORT jlongArray JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeInspectLiveDlssEvaluation(
    JNIEnv* env, jclass bridgeClass, jlong sessionId, jint slotIndex,
    jlong signalValue);

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeCloseLiveDlssSession(
    JNIEnv* env, jclass bridgeClass, jlong sessionId);

JNIEXPORT void JNICALL Java_dev_mcdlss_core_NativeLibraryBridge_nativeShutdownLiveDlssProcess(
    JNIEnv* env, jclass bridgeClass);

}
