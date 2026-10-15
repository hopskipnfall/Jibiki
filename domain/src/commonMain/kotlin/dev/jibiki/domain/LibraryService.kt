package dev.jibiki.domain

import dev.jibiki.db.Database
import dev.jibiki.db.allDictionaries
import dev.jibiki.db.assignToProfile
import dev.jibiki.db.deleteDictionary
import dev.jibiki.db.media
import dev.jibiki.db.profileEntries
import dev.jibiki.db.profiles
import dev.jibiki.db.removeFromProfile
import dev.jibiki.db.reorderProfile
import dev.jibiki.db.setProfileEnabled
import dev.jibiki.db.setSetting
import dev.jibiki.db.setting
import dev.jibiki.db.strokeCounts
import dev.jibiki.model.DictionaryInfo
import dev.jibiki.model.Profile
import dev.jibiki.model.ProfileEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dictionary management, profiles, settings and media — everything except import and search. */
class LibraryService(private val db: Database) {
    private val _version = MutableStateFlow(0)

    /** Increments whenever dictionaries or profiles change, so screens can reload. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun notifyChanged() {
        _version.value++
    }

    suspend fun dictionaries(): List<DictionaryInfo> = db.read { allDictionaries() }

    suspend fun delete(dictId: Long, onProgress: (Long) -> Unit = {}) {
        db.deleteDictionary(dictId, onProgress)
        notifyChanged()
    }

    suspend fun profiles(): List<Profile> = db.read { profiles() }

    suspend fun entries(profileId: Long): List<ProfileEntry> = db.read { profileEntries(profileId) }

    suspend fun setEnabled(profileId: Long, dictId: Long, enabled: Boolean) {
        db.write { setProfileEnabled(profileId, dictId, enabled) }
        notifyChanged()
    }

    suspend fun setMember(profileId: Long, dictId: Long, member: Boolean) {
        db.write { if (member) assignToProfile(profileId, dictId) else removeFromProfile(profileId, dictId) }
        notifyChanged()
    }

    /** Moves a dictionary one step up (earlier = higher priority) or down within a profile. */
    suspend fun move(profileId: Long, dictId: Long, up: Boolean) {
        db.write {
            val ids = profileEntries(profileId).map { it.dict.id }.toMutableList()
            val i = ids.indexOf(dictId)
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in ids.indices) return@write
            ids[i] = ids[j].also { ids[j] = ids[i] }
            reorderProfile(profileId, ids)
        }
        notifyChanged()
    }

    /** Stroke counts for kanji, from installed kanji dictionaries (missing when unknown). */
    suspend fun strokeCounts(chars: Collection<String>): Map<String, Int> =
        chars.chunked(900).fold(emptyMap()) { acc, chunk -> acc + db.read { strokeCounts(chunk) } }

    suspend fun media(dictId: Long, path: String): ByteArray? = db.read { media(dictId, path) }

    suspend fun setting(key: String): String? = db.read { setting(key) }

    suspend fun setSetting(key: String, value: String?) = db.write { setSetting(key, value) }
}
