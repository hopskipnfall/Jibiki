package dev.jibiki.db

import dev.jibiki.model.DictStatus
import dev.jibiki.model.Profile
import dev.jibiki.model.ProfileEntry

suspend fun Sql.profiles(): List<Profile> =
    query("SELECT id, name, sortOrder FROM profile ORDER BY sortOrder") { Profile(it.getLong(0), it.getText(1), it.getLong(2).toInt()) }

suspend fun Sql.profileEntries(profileId: Long): List<ProfileEntry> {
    val byId = allDictionaries().associateBy { it.id }
    return query(
        "SELECT dictId, priority, enabled FROM profile_dictionary WHERE profileId = ? ORDER BY priority",
        profileId,
    ) { Triple(it.getLong(0), it.getLong(1).toInt(), it.getLong(2) != 0L) }
        .mapNotNull { (dictId, priority, enabled) -> byId[dictId]?.let { ProfileEntry(it, priority, enabled) } }
}

suspend fun Sql.assignToProfile(profileId: Long, dictId: Long) {
    val next = long("SELECT coalesce(max(priority), -1) + 1 FROM profile_dictionary WHERE profileId = ?", profileId)
    exec("INSERT OR IGNORE INTO profile_dictionary(profileId, dictId, priority, enabled) VALUES (?,?,?,1)", profileId, dictId, next)
}

suspend fun Sql.removeFromProfile(profileId: Long, dictId: Long) =
    exec("DELETE FROM profile_dictionary WHERE profileId = ? AND dictId = ?", profileId, dictId)

suspend fun Sql.setProfileEnabled(profileId: Long, dictId: Long, enabled: Boolean) =
    exec("UPDATE profile_dictionary SET enabled = ? WHERE profileId = ? AND dictId = ?", enabled, profileId, dictId)

suspend fun Sql.reorderProfile(profileId: Long, dictIdsInOrder: List<Long>) {
    dictIdsInOrder.forEachIndexed { i, id ->
        exec("UPDATE profile_dictionary SET priority = ? WHERE profileId = ? AND dictId = ?", i, profileId, id)
    }
}

/**
 * Ready, enabled dictionaries for a profile, mapped to priority (lower = first).
 * `null` means "All": every ready dictionary enabled in any profile, ordered by profile then priority.
 */
suspend fun Sql.activeDictionaries(profileId: Long?): Map<Long, Int> {
    val ready = DictStatus.READY.name
    val rows = if (profileId != null) {
        query(
            "SELECT pd.dictId, pd.priority FROM profile_dictionary pd JOIN dictionary d ON d.id = pd.dictId " +
                "WHERE pd.profileId = ? AND pd.enabled = 1 AND d.status = ? ORDER BY pd.priority",
            profileId, ready,
        ) { it.getLong(0) to it.getLong(1).toInt() }
    } else {
        query(
            "SELECT pd.dictId FROM profile_dictionary pd JOIN dictionary d ON d.id = pd.dictId JOIN profile p ON p.id = pd.profileId " +
                "WHERE pd.enabled = 1 AND d.status = ? ORDER BY p.sortOrder, pd.priority",
            ready,
        ) { it.getLong(0) }.distinct().mapIndexed { i, id -> id to i }
    }
    return rows.toMap()
}
