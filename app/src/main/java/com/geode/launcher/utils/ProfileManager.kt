package com.geode.launcher.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val FILE_KEY = "GeodeLauncherPreferencesFileKey"
private const val PROFILES_FILE_KEY = "GeodeLauncherProfiles"

private const val PROFILE_LIST_SAVE_KEY = "profiles"
private const val PROFILE_CURRENT_SAVE_KEY = "currentProfile"

@Serializable
data class Profile(val path: String, val name: String)

class ProfileManager {
    private val applicationContext: Context
    private val preferences: SharedPreferences

    companion object {
        private lateinit var managerInstance: ProfileManager

        fun get(context: Context): ProfileManager {
            if (!Companion::managerInstance.isInitialized) {
                val applicationContext = context.applicationContext

                managerInstance = ProfileManager(applicationContext)
            }

            return managerInstance
        }
    }

    constructor(applicationContext: Context) {
        this.applicationContext = applicationContext
        this.preferences = applicationContext.getSharedPreferences(PROFILES_FILE_KEY, Context.MODE_PRIVATE)

        _storedProfiles.value = getProfiles()
    }

    private val _storedProfiles = MutableStateFlow<List<Profile>>(emptyList())
    val storedProfiles = _storedProfiles.asStateFlow()

    fun getCurrentProfile(): String? {
        return preferences.getString(PROFILE_CURRENT_SAVE_KEY, null)
            .takeUnless { it.isNullOrEmpty() }
    }

    fun getCurrentFileKey(): String {
        val currentProfile = getCurrentProfile()
            ?: return FILE_KEY

        return "${FILE_KEY}_${currentProfile}"
    }

    fun getProfile(path: String): Profile? {
        val profilesSet = preferences.getStringSet(PROFILE_LIST_SAVE_KEY, null)
        return profilesSet?.firstNotNullOfOrNull {
            Json.decodeFromString<Profile>(it).takeIf { it.path == path }
        }
    }

    /**
     * Store a profile into the save, removing any duplicate profiles beforehand
     */
    fun storeProfile(profile: Profile) {
        val profilesSet = preferences.getStringSet(PROFILE_LIST_SAVE_KEY, null) ?: emptySet<String>()

        val removedList = profilesSet.filterTo(LinkedHashSet()) {
            val saveProfile = Json.decodeFromString<Profile>(it)
            saveProfile.path != profile.path
        }
        removedList.add(Json.encodeToString(profile))

        preferences.edit {
            putStringSet(PROFILE_LIST_SAVE_KEY, removedList)
        }

        _storedProfiles.value = getProfiles()
    }

    fun deleteProfile(path: String) {
        val profilesSet = preferences.getStringSet(PROFILE_LIST_SAVE_KEY, null) ?: emptySet<String>()

        val removedList = profilesSet.filterTo(LinkedHashSet()) {
            val saveProfile = Json.decodeFromString<Profile>(it)
            saveProfile.path != path
        }

        preferences.edit {
            putStringSet(PROFILE_LIST_SAVE_KEY, removedList)
        }

        _storedProfiles.value = getProfiles()
    }

    fun deleteProfiles(paths: List<String>) {
        val profilesSet = preferences.getStringSet(PROFILE_LIST_SAVE_KEY, null) ?: emptySet<String>()

        val removedList = profilesSet.filterTo(LinkedHashSet()) {
            val saveProfile = Json.decodeFromString<Profile>(it)
            !paths.contains(saveProfile.path)
        }

        preferences.edit(commit = true) {
            putStringSet(PROFILE_LIST_SAVE_KEY, removedList)
        }

        _storedProfiles.value = getProfiles()
    }

    fun setCurrentProfile(profile: String?) {
        preferences.edit(commit = true) {
            if (profile.isNullOrEmpty()) {
                remove(PROFILE_CURRENT_SAVE_KEY)
            } else {
                putString(PROFILE_CURRENT_SAVE_KEY, profile)
            }
        }
    }

    fun getProfiles(): List<Profile> {
        val profilesSet = preferences.getStringSet(PROFILE_LIST_SAVE_KEY, null) ?: emptySet<String>()

        return profilesSet.map {
            Json.decodeFromString<Profile>(it)
        }
    }
}
