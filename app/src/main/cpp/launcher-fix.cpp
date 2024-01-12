#include <dlfcn.h>
#include <jni.h>
#include <string>
#include <android/log.h>

#ifndef DISABLE_LAUNCHER_FIX

#ifdef USE_TULIPHOOK
#include <tulip/TulipHook.hpp>
#else
#include <dobby.h>
#endif

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

bool hook_function(void* addr, auto* hook, auto** orig) {
#ifdef USE_TULIPHOOK
    using namespace tulip::hook;

    HandlerMetadata metadata = {
        std::make_shared<PlatformConvention>(),
        AbstractFunction::from(hook)
    };

    auto handler_result = createHandler(addr, metadata);
    if (!handler_result) {
        __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-Fix",
            "failed to create handler: %s\n", handler_result.unwrapErr().c_str());
        return false;
    }

    auto handler = *handler_result;

    HookMetadata hook_metadata = {0};

    createHook(handler, reinterpret_cast<void*>(hook), hook_metadata);

    WrapperMetadata wrapper_metadata = {
            std::make_unique<PlatformConvention>(),
            AbstractFunction::from(hook)
    };

    auto wrapper = createWrapper(addr, std::move(wrapper_metadata));
    if (!wrapper) {
        __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-Fix",
            "failed to create wrapper: %s\n", wrapper.unwrapErr().c_str());
        return false;
    }

    *reinterpret_cast<void**>(orig) = *wrapper;

    return true;
#else
    DobbyHook(
        addr,
        reinterpret_cast<dobby_dummy_func_t>(hook),
        reinterpret_cast<dobby_dummy_func_t*>(orig)
    );

    return true;
#endif
}

[[gnu::constructor]] [[gnu::used]] void setup_hooks() {
    #ifndef DISABLE_LAUNCHER_FIX
    auto fopen_addr = dlsym(RTLD_NEXT, "fopen");
    hook_function(fopen_addr, &fopen_hook, &fopen_original);

    auto rename_addr = dlsym(RTLD_NEXT, "rename");
    hook_function(rename_addr, &rename_hook, &rename_original);
    #endif
}
