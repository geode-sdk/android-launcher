#pragma once

#define LOG_TAG "GeodeLauncher-Fix"

#include <android/log.h>
#include <format>

namespace log {
    template<typename... Args>
    void log_internal(android_LogPriority prio, std::format_string<Args...> fmt, Args&&... args) {
        std::string str = std::format(fmt, std::forward<Args>(args)...);
        __android_log_write(prio, LOG_TAG, str.c_str());
    }

    template<typename... Args>
    inline void debug(std::format_string<Args...> fmt, Args&&... args) {
        log_internal(ANDROID_LOG_DEBUG, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    inline void info(std::format_string<Args...> fmt, Args&&... args) {
        log_internal(ANDROID_LOG_INFO, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    inline void warn(std::format_string<Args...> fmt, Args&&... args) {
        log_internal(ANDROID_LOG_WARN, fmt, std::forward<Args>(args)...);
    }

    template<typename... Args>
    inline void error(std::format_string<Args...> fmt, Args&&... args) {
        log_internal(ANDROID_LOG_ERROR, fmt, std::forward<Args>(args)...);
    }
}
