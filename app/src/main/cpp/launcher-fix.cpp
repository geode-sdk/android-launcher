#include <jni.h>
#include <string>

#include "base.h"
#include "log.hpp"

DataPaths& DataPaths::get_instance() {
    static auto paths_instance = DataPaths();
    return paths_instance;
}

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
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_enableCustomSymbolList(
    JNIEnv* env,
    jobject,
    jstring symbol_path
) {
    auto is_copy = jboolean();
    auto symbol_path_str = env->GetStringUTFChars(symbol_path, &is_copy);

    DataPaths::get_instance().load_symbols_from = std::string(symbol_path_str);

    env->ReleaseStringUTFChars(symbol_path, symbol_path_str);
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
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_enableExceptionsRenaming(JNIEnv*, jobject) {
    DataPaths::get_instance().patch_exceptions = true;
}

// this should be called after gd is loaded but before geode
extern "C" JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_performPatches(JNIEnv*, jobject) {
    dl_iterate_phdr(on_dl_iterate, nullptr);
}

std::optional<std::string> redirect_path(const char* pathname) {
    std::string_view data_path = DataPaths::get_instance().data_path;
    std::string_view original_data_path = DataPaths::get_instance().original_data_path;

    if (!data_path.empty() && !original_data_path.empty()) {
        // call this a c string optimization
        if (std::strncmp(pathname, original_data_path.data(), original_data_path.size()) == 0) {
            return std::format("{}{}", data_path, (pathname + original_data_path.size()));
        }
    }

    return std::nullopt;
}

FILE* fopen_hook(const char* pathname, const char* mode) {
    auto path_str = redirect_path(pathname);
    auto redirect = path_str.has_value() ? path_str->c_str() : pathname;

    return fopen(redirect, mode);
}

int rename_hook(const char* old_path, const char* new_path) {
    auto old_path_str = redirect_path(old_path);
    auto old_redirect = old_path_str ? old_path_str->c_str() : old_path;

    auto new_path_str = redirect_path(new_path);
    auto new_redirect = new_path_str ? new_path_str->c_str() : new_path;

    return rename(old_redirect, new_redirect);
}
