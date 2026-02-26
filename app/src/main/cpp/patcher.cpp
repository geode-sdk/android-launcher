#include <cstdint>
#include <span>

#include <link.h>
#include <unistd.h>
#include <sys/mman.h>

#include "log.hpp"
#include "base.h"

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

// copied from tuliphook source :)
// tuliphook sets WRX perms, but we write to pages with W^X protection
bool set_mprotect(std::uintptr_t address, size_t size, int protection) {
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
constexpr std::uint32_t elfhash(std::string_view name) {
    std::uint32_t h = 0, g;

    for (const auto c : name) {
        h = (h << 4) + static_cast<unsigned char>(c);
        g = h & 0xf0000000;
        h ^= g;
        h ^= g >> 24;
    }

    return h;
}

constexpr std::array<const std::uint8_t, 3> SYMBOL_PATCH_VALUE{":3"};

bool patch_symbol(std::uint32_t* hash_table, char* str_table, ElfW(Sym)* sym_table, const std::string_view orig_name) {
    auto hash = elfhash(orig_name);

    auto nbucket = hash_table[0];
    auto nchain = hash_table[1];
    auto bucket = &hash_table[2];
    auto chain = &bucket[nbucket];

    for (auto i = bucket[hash % nbucket]; i != 0; i = chain[i]) {
        auto sym = sym_table + i;
        auto sym_name = str_table + sym->st_name;

        if (orig_name == sym_name) {
            // there's probably no point to checking this, honestly
            if (ELF_ST_BIND(sym->st_info) == STB_GLOBAL && ELF_ST_TYPE(sym->st_info) == STT_FUNC) {
                // we found it! now go rename the symbol

                log::debug("Disabling symbol at {} ({})", reinterpret_cast<void*>(sym_name), sym_name);

                if (!set_mprotect(reinterpret_cast<std::uintptr_t>(sym_name), SYMBOL_PATCH_VALUE.size(), PROT_READ | PROT_WRITE)) {
                    log::warn("failed to patch symbol: could not set writable flag");
                    return false;
                }

                memcpy(sym_name, SYMBOL_PATCH_VALUE.data(), SYMBOL_PATCH_VALUE.size());

                if (!set_mprotect(reinterpret_cast<std::uintptr_t>(sym_name), SYMBOL_PATCH_VALUE.size(), PROT_READ)) {
                    log::warn("failed to patch symbol: could not remove writable flag");
                    return false;
                }

                return true;
            }
        }
    }

    log::warn("could not find symbol {} to patch (hash: {})", orig_name, hash);

    return false;
}


bool perform_symbol_patches(dl_phdr_info* info, const ElfDynamicState& state) {
    // patch symbol names
    auto has_error = false;
    for (const auto symbol : remove_symbols) {
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
        log::warn("asked to patch unknown symbol type: {:#x}", type);
        return false;
    }

    auto write_addr = reinterpret_cast<std::uintptr_t*>(addr);
    auto val = reinterpret_cast<std::uintptr_t>(func);
    if constexpr (std::is_same_v<T, ElfW(Rela)>) {
        // reloc = S + A
        val += r.r_addend;
    }

    log::debug("Patching relocation at vaddr {:#x} (offset={:#x}): {:#x} => {:#x}", addr, r.r_offset, *write_addr, val);

    if (!set_mprotect(addr, sizeof(std::uintptr_t), PROT_WRITE | PROT_READ)) {
        log::warn("Failed to patch relocation: failed to add writable flag");
        return false;
    }

    // why use memcpy when you can do this :)
    *write_addr = val;

    if (!set_mprotect(addr, sizeof(std::uintptr_t), PROT_READ)) {
        log::warn("Failed to patch relocation: failed to remove writable flag");
        return false;
    }

    return true;
}


template <typename T> requires is_one_of<T, ElfW(Rela), ElfW(Rel)>
bool perform_reloc_patches(dl_phdr_info* info, const ElfDynamicState& state) {
    auto table_size = state.plt_table_size_bytes / sizeof(T);

    auto failed = false;

    for (auto& plt : std::span{
            reinterpret_cast<T*>(state.plt_table),
            static_cast<std::size_t>(table_size)
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
        log::debug("Base address of libcocos2dcpp: {:#x}", info->dlpi_addr);

        // step 1: get the dynamic table
        std::uintptr_t dyn_addr = 0u;

        for (auto& phdr : std::span{ info->dlpi_phdr, info->dlpi_phnum}) {
            if (phdr.p_type == PT_DYNAMIC) {
                dyn_addr = info->dlpi_addr + phdr.p_vaddr;
                break;
            }
        }

        if (dyn_addr == 0u) {
            log::warn("failed to find libcocos dynamic section");
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
                        log::warn("unrecognized PLTREL type {:#x}", val);
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
            log::error("failed to parse dynamic section (at least one table is null)");
            return -1;
        }

        bool failed = false;

        auto so_name = state.str_table + state.soname_idx;
        if (strcmp(so_name, "libcocos2dcpp.so") != 0) {
            log::warn("DT_SONAME gives us {}! that's not good", so_name);
            failed = true;
        }

        auto reloc_failed = state.plt_table_rela
                            ? !perform_reloc_patches<ElfW(Rela)>(info, state)
                            : !perform_reloc_patches<ElfW(Rel)>(info, state);
        if (reloc_failed) {
            failed = true;
        }

        if (DataPaths::patch_exceptions) {
            if (!perform_symbol_patches(info, state)) {
                failed = true;
            }
        }

        if (failed) {
            log::info(
                    "symbol patch diagnostics\nlibrary path: {}\naddrs: base={:#x} sym={} str={} hash={} plt={}",
                    info->dlpi_name,
                    info->dlpi_addr,
                    reinterpret_cast<void*>(state.sym_table),
                    reinterpret_cast<void*>(state.str_table),
                    reinterpret_cast<void*>(state.hash_table),
                    state.plt_table
            );
        }

        return 1;
    }

    return 0;
}

