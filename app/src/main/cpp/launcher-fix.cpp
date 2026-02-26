#include <jni.h>
#include <string>

#include "base.h"
#include "log.hpp"

std::string DataPaths::original_data_path{};
std::string DataPaths::data_path{};

bool DataPaths::patch_exceptions{};

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_setDataPath(
        JNIEnv *env,
        jobject,
        jstring data_path
) {
    auto is_copy = jboolean();
    auto data_path_str = env->GetStringUTFChars(data_path, &is_copy);

    DataPaths::data_path = std::string(data_path_str);

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

    DataPaths::original_data_path = std::string(data_path_str);

    env->ReleaseStringUTFChars(data_path, data_path_str);
}

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_enableExceptionsRenaming(JNIEnv*, jobject) {
    DataPaths::patch_exceptions = true;
}

// this should be called after gd is loaded but before geode
extern "C" JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_performPatches(JNIEnv*, jobject) {
    dl_iterate_phdr(on_dl_iterate, nullptr);
}

std::optional<std::string> redirect_path(const char* pathname) {
    std::string_view data_path = DataPaths::data_path;
    std::string_view original_data_path = DataPaths::original_data_path;

    if (data_path.empty() || original_data_path.empty()) [[unlikely]] {
        return std::nullopt;
    }

    // call this a c string optimization
    if (std::strncmp(pathname, original_data_path.data(), original_data_path.size()) == 0) {
        auto remaining_path = (pathname + original_data_path.size());
        auto remaining_len = strlen(remaining_path);

        std::string x;
        x.reserve(data_path.size() + remaining_len);
        x.append(data_path);
        x.append(remaining_path, remaining_len);

        return x;
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
