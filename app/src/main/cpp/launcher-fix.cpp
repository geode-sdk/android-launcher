#include <dlfcn.h>
#include <jni.h>
#include <fstream>
#include <string>
#include <vector>
#include <cstdint>
#include <android/log.h>
#include <link.h>

#ifndef DISABLE_LAUNCHER_FIX

#ifdef USE_TULIPHOOK
#include <tulip/TulipHook.hpp>
#else
#include <dobby.h>
#endif

#endif

class DataPaths {
public:
    std::string original_data_path{};
    std::string data_path{};
    std::string load_symbols_from{};

    static DataPaths& get_instance() {
        static auto paths_instance = DataPaths();
        return paths_instance;
    }

private:
    DataPaths() {}
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

// i copied this function from the android linker
std::uint32_t elfhash(const char *_name) {
    const unsigned char *name = reinterpret_cast<const unsigned char*>(_name);
    std::uint32_t h = 0, g;

    while (*name) {
        h = (h << 4) + *name++;
        g = h & 0xf0000000;
        h ^= g;
        h ^= g >> 24;
    }

    return h;
}

bool patch_symbol(std::uint32_t* hash_table, char* str_table, ElfW(Sym)* sym_table, const char* orig_name) {
    auto hash = elfhash(orig_name);

    auto nbucket = hash_table[0];
    auto nchain = hash_table[1];
    auto bucket = &hash_table[2];
    auto chain = &bucket[nbucket];

    for (auto i = bucket[hash % nbucket]; i != 0; i = chain[i]) {
        auto sym = sym_table + i;
        auto sym_name = str_table + sym->st_name;

        if (strcmp(sym_name, orig_name) == 0) {
            // there's probably no point to checking this, honestly
            if (ELF_ST_BIND(sym->st_info) == STB_GLOBAL && ELF_ST_TYPE(sym->st_info) == STT_FUNC) {
                // we found it! now go rename the symbol
                std::array<std::uint8_t, 3> new_symbol{":3"};

#ifdef USE_TULIPHOOK
                auto res = tulip::hook::writeMemory(sym_name, new_symbol.data(), new_symbol.size());
                if (!res) {
                        __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-fix", "failed to patch symbol: %s", res.unwrapErr().c_str());
                        return false;
                }
#else
                DobbyCodePatch(reinterpret_cast<void*>(sym_name), new_symbol.data(), new_symbol.size());
#endif

                return true;
            }
        }
    }

    __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-fix", "could not find symbol %s to patch (hash: %u)", orig_name, hash);
    return false;
}

std::vector<std::string> get_symbols_listing() {
    auto symbol_path = DataPaths::get_instance().load_symbols_from;
    if (symbol_path.empty()) {
        // this is every function that i thought would be relevant
        return {
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
    }

    std::ifstream symbol_file{symbol_path};
    if (!symbol_file) {
        __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-fix", "failed to read symbol file at %s", symbol_path.c_str());
        return {};
    }

    std::vector<std::string> symbols_list{};
    std::string current_line{};
    while (std::getline(symbol_file, current_line)) {
        if (!current_line.empty()) {
            symbols_list.push_back(current_line);
        }
    }

    return symbols_list;
}

int on_dl_iterate(dl_phdr_info* info, size_t size, void* data) {
    // this is probably going to be gd
    if (info->dlpi_name != nullptr && strstr(info->dlpi_name, "libcocos2dcpp.so") != nullptr) {
        // step 1: get the dynamic table
        std::uintptr_t dyn_addr = 0u;

        for (auto i = 0u; i < info->dlpi_phnum; i++) {
            auto phdr = info->dlpi_phdr + i;

            if (phdr->p_type == PT_DYNAMIC) {
                dyn_addr = info->dlpi_addr + phdr->p_vaddr;
                break;
            }
        }

        if (dyn_addr == 0u) {
            __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-fix", "failed to find libcocos dynamic section");
            return 0;
        }

        // step 2: get the symbol table
        auto dyn_entry = reinterpret_cast<ElfW(Dyn)*>(dyn_addr);
        auto dyn_end_reached = false;

        std::uintptr_t sym_table_addr = 0u;
        std::uintptr_t str_table_addr = 0u;
        std::uintptr_t hash_table_addr = 0u;
        std::uintptr_t soname_idx = 0u;

        while (!dyn_end_reached) {
            auto tag = dyn_entry->d_tag;

            switch (dyn_entry->d_tag) {
                case DT_SYMTAB:
                    sym_table_addr = info->dlpi_addr + dyn_entry->d_un.d_val;
                    break;
                case DT_STRTAB:
                    str_table_addr = info->dlpi_addr + dyn_entry->d_un.d_val;
                    break;
                case DT_HASH:
                    hash_table_addr = info->dlpi_addr + dyn_entry->d_un.d_val;
                    break;
                case DT_SONAME:
                    soname_idx = dyn_entry->d_un.d_val;
                    break;
                case DT_NULL:
                    dyn_end_reached = true;
                    break;
            }

            dyn_entry++;
        }

        if (hash_table_addr == 0u || str_table_addr == 0u || sym_table_addr == 0u) {
            __android_log_print(ANDROID_LOG_ERROR, "GeodeLauncher-fix", "failed to parse dynamic section (at least one table is null)");
            return -1;
        }

        // patch symbol names
        auto hash_table = reinterpret_cast<std::uint32_t*>(hash_table_addr);
        auto str_table = reinterpret_cast<char*>(str_table_addr);
        auto sym_table = reinterpret_cast<ElfW(Sym)*>(sym_table_addr);

        auto so_name = str_table + soname_idx;
        if (strcmp(so_name, "libcocos2dcpp.so") != 0) {
            __android_log_print(ANDROID_LOG_WARN, "GeodeLauncher-fix", "DT_SONAME gives us %s! that's not good", so_name);
        }

        auto has_error = false;
        auto symbols_listing = get_symbols_listing();
        for (const auto& symbol : symbols_listing) {
            if (!patch_symbol(hash_table, str_table, sym_table, symbol.c_str())) {
                has_error = true;
            }
        }

        if (has_error) {
            __android_log_print(
                    ANDROID_LOG_INFO,
                    "GeodeLauncher-fix",
                    "symbol patch diagnostics\nlibrary path: %s\naddrs: base=%p sym=%p str=%p hash=%p",
                    info->dlpi_name, reinterpret_cast<void*>(info->dlpi_addr), sym_table, str_table, hash_table
                );
        }

        return 1;
    }

    return 0;

}

// this should be called after gd is loaded but before geode
extern "C"
JNIEXPORT int JNICALL Java_com_geode_launcher_LauncherFix_performExceptionsRenaming(JNIEnv*, jobject) {
    return dl_iterate_phdr(on_dl_iterate, nullptr);
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
