#include <swappy/swappyGL.h>
#include <swappy/swappyGL_extra.h>
#include <android/native_window_jni.h>
#include <memory>

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_initFramePacing(
        JNIEnv *env,
        jobject,
        jobject activity
) {
    SwappyGL_init(env, activity);
}

struct NativeWindowDeleter {
    void operator()(ANativeWindow* p) const {
        if (p != nullptr) ANativeWindow_release(p);
    }
};

std::unique_ptr<ANativeWindow, NativeWindowDeleter> native_window = nullptr;

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_destroyFramePacing(JNIEnv*, jobject) {
    SwappyGL_destroy();
}

extern "C"
JNIEXPORT jboolean JNICALL Java_com_geode_launcher_LauncherFix_swapFrame(
        JNIEnv*,
        jobject,
        jlong display,
        jlong surface
) {
    return SwappyGL_swap(
            reinterpret_cast<EGLDisplay>(display),
            reinterpret_cast<EGLSurface>(surface)
    );
}

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_setSurface(
        JNIEnv* env,
        jobject,
        jobject surface
) {
    if (surface == nullptr) {
        native_window.reset();
        SwappyGL_setWindow(nullptr);
        return;
    }

    native_window.reset(ANativeWindow_fromSurface(env, surface));
    SwappyGL_setWindow(native_window.get());
}

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_setSwapInterval(
        JNIEnv* env,
        jobject,
        jlong interval_ns
) {
    SwappyGL_setAutoSwapInterval(false);
    SwappyGL_setSwapIntervalNS(interval_ns);
}
