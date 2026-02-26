#include <dlfcn.h>
#include <jni.h>
#include <fstream>
#include <string>
#include <vector>
#include <cstdint>
#include <android/log.h>
#include <link.h>
#include <span>
#include <unistd.h>
#include <sys/mman.h>

#define LOG_TAG "GeodeLauncher-Fix"

class DataPaths {
public:
    std::string original_data_path{};
    std::string data_path{};
    std::string load_symbols_from{};

    bool patch_exceptions{};

    static DataPaths& get_instance() {
        static auto paths_instance = DataPaths();
        return paths_instance;
    }

private:
    DataPaths() {}
};

struct ElfDynamicState {
    std::uint32_t* hash_table{};
    char* str_table{};
    ElfW(Sym)* sym_table{};

    void* plt_table{};
    std::uint64_t plt_table_size_bytes{};
    bool plt_table_rela{};

    std::uint64_t soname_idx{};

    bool is_empty() const {
        return !hash_table || !str_table || !sym_table || !plt_table;
    }
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

extern "C"
JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_enableExceptionsRenaming(JNIEnv*, jobject) {
    DataPaths::get_instance().patch_exceptions = true;
}

// copied from tuliphook source :)
bool set_mprotect(std::uintptr_t address, size_t size, uint32_t protection) {
    auto const page_size = getpagesize();
    auto const page_mask = page_size - 1;

    auto const ptr = reinterpret_cast<uintptr_t>(address);
    auto const aligned_ptr = ptr & (~page_mask);
    auto const begin_size = ptr - aligned_ptr;
    auto const page_count = (begin_size + size + page_mask) / page_size;
    auto const aligned_size = page_count * page_size;

    auto status = mprotect(reinterpret_cast<void*>(aligned_ptr), aligned_size, protection);

    return status == 0;
}

// i copied this function from the android linker
std::uint32_t elfhash(const char *_name) {
    const auto *name = reinterpret_cast<const unsigned char*>(_name);
    std::uint32_t h = 0, g;

    while (*name) {
        h = (h << 4) + *name++;
        g = h & 0xf0000000;
        h ^= g;
        h ^= g >> 24;
    }

    return h;
}

std::array<std::uint8_t, 3> SYMBOL_PATCH_VALUE{":3"};

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

                __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Disabling symbol at %p (%s)", sym_name, sym_name);

                if (!set_mprotect(reinterpret_cast<std::uintptr_t>(sym_name), SYMBOL_PATCH_VALUE.size(), PROT_READ | PROT_WRITE)) {
                    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "failed to patch symbol: could not set writable flag");
                    return false;
                }

                memcpy(sym_name, SYMBOL_PATCH_VALUE.data(), SYMBOL_PATCH_VALUE.size());

                if (!set_mprotect(reinterpret_cast<std::uintptr_t>(sym_name), SYMBOL_PATCH_VALUE.size(), PROT_READ)) {
                    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "failed to patch symbol: could not remove writable flag");
                    return false;
                }

                return true;
            }
        }
    }

    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "could not find symbol %s to patch (hash: %u)", orig_name, hash);
    return false;
}

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

bool perform_symbol_patches(dl_phdr_info* info, const ElfDynamicState& state) {
    // patch symbol names
    auto so_name = state.str_table + state.soname_idx;
    if (strcmp(so_name, "libcocos2dcpp.so") != 0) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "DT_SONAME gives us %s! that's not good", so_name);
    }

    auto has_error = false;
    for (const auto& symbol : remove_symbols) {
        if (!patch_symbol(state.hash_table, state.str_table, state.sym_table, symbol)) {
            has_error = true;
        }
    }

    return !has_error;
}

#if defined(__LP64__)
#define ELF_R_SYM(i) ELF64_R_SYM(i)
#define ELF_R_TYPE(i) ELF64_R_TYPE(i)
#else
#define ELF_R_SYM(i) ELF32_R_SYM(i)
#define ELF_R_TYPE(i) ELF32_R_TYPE(i)
#endif

template <typename T, typename... Types>
concept is_one_of = (std::same_as<T, Types> || ...);

template <typename T> requires is_one_of<T, ElfW(Rela), ElfW(Rel)>
bool rewrite_symbol_reloc(std::uintptr_t addr, T& r, ElfW(Sym)* sym, void* func) {
    auto type = ELF_R_TYPE(r.r_info);

    // we're only interested in JUMP_SLOT symbols
    if (type != R_AARCH64_JUMP_SLOT && type != R_ARM_JUMP_SLOT) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "asked to patch unknown symbol type: %llu", type);
        return false;
    }

    auto write_addr = reinterpret_cast<std::uintptr_t*>(addr);
    auto val = reinterpret_cast<std::uintptr_t>(func);
    if constexpr (std::is_same_v<T, ElfW(Rela)>) {
        // reloc = S + A
        val += r.r_addend;
    }

    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Patching relocation at vaddr %#lx (offset=%#llx): %#lx => %#lx", addr, r.r_offset, *write_addr, val);

    if (!set_mprotect(addr, sizeof(std::uintptr_t), PROT_WRITE | PROT_READ)) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Failed to patch relocation: failed to add writable flag");
        return false;
    }

    // why use memcpy when you can do this :)
    *write_addr = val;

    if (!set_mprotect(addr, sizeof(std::uintptr_t), PROT_READ)) {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Failed to patch relocation: failed to remove writable flag");
        return false;
    }

    return true;
}

FILE* fopen_hook(const char* pathname, const char* mode);
int rename_hook(const char* old_path, const char* new_path);

template <typename T> requires is_one_of<T, ElfW(Rela), ElfW(Rel)>
bool perform_reloc_patches(dl_phdr_info* info, const ElfDynamicState& state) {
    auto table_size = state.plt_table_size_bytes / sizeof(T);

    auto failed = false;

    for (auto& plt : std::span{
        reinterpret_cast<T*>(state.plt_table), table_size
    }) {
        auto sym = ELF_R_SYM(plt.r_info);
        if (sym == 0) continue;

        auto sym_data = state.sym_table + sym;
        auto sym_name = state.str_table + sym_data->st_name;

        auto reloc_addr = info->dlpi_addr + plt.r_offset;

        if (strcmp(sym_name, "fopen") == 0) {
            if (!rewrite_symbol_reloc(reloc_addr, plt, sym_data, reinterpret_cast<void*>(&fopen_hook))) {
                failed = true;
            }
        } else if (strcmp(sym_name, "rename") == 0) {
            if (!rewrite_symbol_reloc(reloc_addr, plt, sym_data, reinterpret_cast<void*>(&rename_hook))) {
                failed = true;
            }
        }
    }

    return !failed;
}

int on_dl_iterate(dl_phdr_info* info, size_t size, void* data) {
    // this is probably going to be gd
    if (info->dlpi_name != nullptr && strstr(info->dlpi_name, "libcocos2dcpp.so") != nullptr) {
        __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "Base address of libcocos2dcpp: %#llx", info->dlpi_addr);

        // step 1: get the dynamic table
        std::uintptr_t dyn_addr = 0u;

        for (auto& phdr : std::span{ info->dlpi_phdr, info->dlpi_phnum}) {
            if (phdr.p_type == PT_DYNAMIC) {
                dyn_addr = info->dlpi_addr + phdr.p_vaddr;
                break;
            }
        }

        if (dyn_addr == 0u) {
            __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "failed to find libcocos dynamic section");
            return 0;
        }

        // step 2: get the symbol table
        auto dyn_entry = reinterpret_cast<ElfW(Dyn)*>(dyn_addr);
        auto dyn_end_reached = false;

        ElfDynamicState state{};

        while (!dyn_end_reached) {
            auto tag = dyn_entry->d_tag;

            switch (dyn_entry->d_tag) {
                case DT_SYMTAB:
                    state.sym_table = reinterpret_cast<ElfW(Sym)*>(info->dlpi_addr + dyn_entry->d_un.d_val);
                    break;
                case DT_STRTAB:
                    state.str_table = reinterpret_cast<char*>(info->dlpi_addr + dyn_entry->d_un.d_val);
                    break;
                case DT_HASH:
                    state.hash_table = reinterpret_cast<std::uint32_t*>(info->dlpi_addr + dyn_entry->d_un.d_val);
                    break;
                case DT_SONAME:
                    state.soname_idx = dyn_entry->d_un.d_val;
                    break;
                case DT_JMPREL:
                    state.plt_table = reinterpret_cast<void*>(info->dlpi_addr + dyn_entry->d_un.d_val);
                    break;
                case DT_PLTREL: {
                    // for Android, arm64 = DT_RELA, arm32 = DT_REL
                    // could just gate this based on architecture but whatever

                    auto val = dyn_entry->d_un.d_val;
                    state.plt_table_rela = val == DT_RELA;
                    if (val != DT_RELA && val != DT_REL) {
                        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "unrecognized PLTREL type %llx", val);
                    }
                    break;
                }
                case DT_PLTRELSZ:
                    state.plt_table_size_bytes = dyn_entry->d_un.d_val;
                    break;
                case DT_NULL:
                    dyn_end_reached = true;
                    break;
            }

            dyn_entry++;
        }

        if (state.is_empty()) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "failed to parse dynamic section (at least one table is null)");
            return -1;
        }

        bool failed = state.plt_table_rela
            ? !perform_reloc_patches<ElfW(Rela)>(info, state)
            : !perform_reloc_patches<ElfW(Rel)>(info, state);

        if (DataPaths::get_instance().patch_exceptions) {
            if (!perform_symbol_patches(info, state)) {
                failed = true;
            }
        }

        if (failed) {
            __android_log_print(
                    ANDROID_LOG_INFO,
                    LOG_TAG,
                    "symbol patch diagnostics\nlibrary path: %s\naddrs: base=%p sym=%p str=%p hash=%p plt=%p",
                    info->dlpi_name, reinterpret_cast<void*>(info->dlpi_addr), state.sym_table, state.str_table, state.hash_table, state.plt_table
            );
        }

        return 1;
    }

    return 0;
}

// this should be called after gd is loaded but before geode
extern "C" JNIEXPORT void JNICALL Java_com_geode_launcher_LauncherFix_performPatches(JNIEnv*, jobject) {
    dl_iterate_phdr(on_dl_iterate, nullptr);
}

std::optional<std::string> redirect_path(const char* pathname) {
    auto& data_path = DataPaths::get_instance().data_path;
    auto& original_data_path = DataPaths::get_instance().original_data_path;

    if (!data_path.empty() && !original_data_path.empty()) {
        // call this a c string optimization
        if (std::strncmp(pathname, original_data_path.c_str(), original_data_path.size()) == 0) {
            auto path = data_path + (pathname + original_data_path.size());

            return path;
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
