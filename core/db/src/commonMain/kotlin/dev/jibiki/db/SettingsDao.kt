package dev.jibiki.db

suspend fun Sql.setting(key: String): String? =
    query("SELECT value FROM settings WHERE key = ?", key) { it.getText(0) }.firstOrNull()

suspend fun Sql.setSetting(key: String, value: String?) {
    if (value == null) exec("DELETE FROM settings WHERE key = ?", key)
    else exec("INSERT INTO settings(key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value)
}
