#include <android/dlext.h>
#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <string>
#include <unistd.h>

#ifndef DISABLE_LAUNCHER_FIX
#include <dobby.h>
#endif

class DataPaths {
public:
    std::string original_data_path;
    std::string data_path;

    static DataPaths& get_instance() {
        static auto paths_instance = DataPaths();
        return paths_instance;
    }

private:
    DataPaths() : original_data_path(), data_path() {}
};

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_setDataPath(
        JNIEnv *env,
        jobject,
        jstring data_path
) {
    auto is_copy = jboolean();
    auto data_path_str = env->GetStringUTFChars(data_path, &is_copy);

    DataPaths::get_instance().data_path = std::string(data_path_str);

    env->ReleaseStringUTFChars(data_path, data_path_str);
}

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_setOriginalDataPath(
        JNIEnv *env,
        jobject,
        jstring data_path
) {
    auto is_copy = jboolean();
    auto data_path_str = env->GetStringUTFChars(data_path, &is_copy);

    DataPaths::get_instance().original_data_path = std::string(data_path_str);

    env->ReleaseStringUTFChars(data_path, data_path_str);
}

extern "C"
JNIEXPORT jboolean JNICALL Java_com_geode_launcher_LauncherFix_loadLibraryFromOffset(JNIEnv *env, jobject, jstring library_name, jint fd, jlong offset) {
    // loads a given library at an offset and file descriptor
    // assumes we have ownership of the file passed in fd

    auto is_copy = jboolean();
    auto library_cname = env->GetStringUTFChars(library_name, &is_copy);

    android_dlextinfo ext_info{};
    ext_info.flags = ANDROID_DLEXT_USE_LIBRARY_FD | ANDROID_DLEXT_USE_LIBRARY_FD_OFFSET;
    ext_info.library_fd = fd;
    ext_info.library_fd_offset = offset;

    auto handle = android_dlopen_ext(library_cname, RTLD_NOW | RTLD_GLOBAL, &ext_info);
    env->ReleaseStringUTFChars(library_name, library_cname);
    close(fd);

    if (handle == nullptr) {
        auto error = dlerror();
        __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-Fix", "dlopen_ext failed. given: %s\n", error);

        return false;
    }

    // we don't need the library anymore
    dlclose(handle);

    return true;
}

FILE* (*fopen_original)(const char *pathname, const char *mode);
FILE* fopen_hook(const char* pathname, const char* mode) {
    auto data_path = DataPaths::get_instance().data_path;
    auto original_data_path = DataPaths::get_instance().original_data_path;

    if (!data_path.empty() && !original_data_path.empty()) {
        auto path_str = std::string(pathname);
        auto pos = path_str.find(original_data_path);
        if (pos != std::string::npos) {
            path_str.replace(pos, original_data_path.length(), data_path);
        }

        return fopen_original(path_str.c_str(), mode);
    }

    return fopen_original(pathname, mode);
}

[[gnu::constructor]] [[gnu::used]] void setup_hooks() {
    #ifndef DISABLE_LAUNCHER_FIX
    auto fopen_addr = dlsym(RTLD_NEXT, "fopen");

    DobbyHook(
        fopen_addr,
        reinterpret_cast<dobby_dummy_func_t>(&fopen_hook),
        reinterpret_cast<dobby_dummy_func_t*>(&fopen_original)
    );
    #endif
}
