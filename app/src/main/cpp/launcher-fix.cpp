#include <dlfcn.h>
#include <string>
#include <dobby.h>
#include <jni.h>

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
JNIEXPORT void JNICALL Java_dev_xyze_geodelauncher_LauncherFix_setDataPath(
        JNIEnv *env,
        jobject,
        jstring data_path
) {
    auto is_copy = jboolean();
    auto data_path_str = env->GetStringUTFChars(data_path, &is_copy);

    DataPaths::get_instance().data_path = std::string(data_path_str);
}

extern "C"
JNIEXPORT void JNICALL Java_dev_xyze_geodelauncher_LauncherFix_setOriginalDataPath(
        JNIEnv *env,
        jobject,
        jstring data_path
) {
    auto is_copy = jboolean();
    auto data_path_str = env->GetStringUTFChars(data_path, &is_copy);

    DataPaths::get_instance().original_data_path = std::string(data_path_str);
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
    auto fopen_addr = dlsym(RTLD_NEXT, "fopen");

    DobbyHook(
        fopen_addr,
        reinterpret_cast<dobby_dummy_func_t>(&fopen_hook),
        reinterpret_cast<dobby_dummy_func_t*>(&fopen_original)
    );
}
