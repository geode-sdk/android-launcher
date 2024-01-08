#include <dlfcn.h>
#include <jni.h>
#include <string>

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

std::string redirect_path(const char* pathname) {
    auto data_path = DataPaths::get_instance().data_path;
    auto original_data_path = DataPaths::get_instance().original_data_path;

    if (!data_path.empty() && !original_data_path.empty()) {
        // call this a c string optimization
        if (std::strncmp(pathname, original_data_path.c_str(), original_data_path.size()) == 0) {
            auto path = data_path + (pathname + original_data_path.size());

            return path;
        }
    }

    return pathname;
}

FILE* (*fopen_original)(const char *pathname, const char *mode);
FILE* fopen_hook(const char* pathname, const char* mode) {
    auto path_str = redirect_path(pathname);

    return fopen_original(path_str.c_str(), mode);
}

int (*rename_original)(const char* old_path, const char* new_path);
int rename_hook(const char* old_path, const char* new_path) {
    auto old_path_str = redirect_path(old_path);
    auto new_path_str = redirect_path(new_path);

    return rename_original(old_path_str.c_str(), new_path_str.c_str());
}

[[gnu::constructor]] [[gnu::used]] void setup_hooks() {
    #ifndef DISABLE_LAUNCHER_FIX
    auto fopen_addr = dlsym(RTLD_NEXT, "fopen");
    auto rename_addr = dlsym(RTLD_NEXT, "rename");

    DobbyHook(
        fopen_addr,
        reinterpret_cast<dobby_dummy_func_t>(&fopen_hook),
        reinterpret_cast<dobby_dummy_func_t*>(&fopen_original)
    );

    DobbyHook(
            rename_addr,
            reinterpret_cast<dobby_dummy_func_t>(&rename_hook),
            reinterpret_cast<dobby_dummy_func_t*>(&rename_original)
    );

    #endif
}
