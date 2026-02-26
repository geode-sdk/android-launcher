#pragma once

#include <string>
#include <array>

#include <link.h>

class DataPaths {
public:
    std::string original_data_path{};
    std::string data_path{};
    std::string load_symbols_from{};

    bool patch_exceptions{};

    static DataPaths& get_instance();

private:
    DataPaths() {}
};

// this is every function that i thought would be relevant
constexpr std::array remove_symbols{
        "__gxx_personality_v0",
        "__cxa_throw",
        "__cxa_rethrow",
        "__cxa_allocate_exception",
        "__cxa_end_catch",
        "__cxa_begin_catch",
        "__cxa_guard_abort",
        "__cxa_guard_acquire",
        "__cxa_guard_release",
        "__cxa_free_exception"

        // android 10's libc doesn't have these symbols
        // and the ndk is forbidden from exporting them!! so keep it for old mods...
        // "_Unwind_RaiseException",
        // "_Unwind_Resume"
};

FILE* fopen_hook(const char* pathname, const char* mode);
int rename_hook(const char* old_path, const char* new_path);

int on_dl_iterate(dl_phdr_info* info, size_t size, void* data);
