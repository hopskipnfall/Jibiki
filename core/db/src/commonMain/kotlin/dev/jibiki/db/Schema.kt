package dev.jibiki.db

internal object Schema {
    const val VERSION = 1

    suspend fun migrate(sql: Sql) {
        val current = sql.long("PRAGMA user_version").toInt()
        if (current >= VERSION) return
        sql.exec("BEGIN IMMEDIATE")
        try {
            if (current < 1) v1.forEach { sql.exec(it) }
            sql.exec("PRAGMA user_version = $VERSION")
            sql.exec("COMMIT")
        } catch (t: Throwable) {
            runCatching { sql.exec("ROLLBACK") }
            throw t
        }
    }

    private val v1 = listOf(
        """CREATE TABLE dictionary(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            revision TEXT NOT NULL,
            author TEXT, url TEXT, description TEXT, attribution TEXT,
            sourceLang TEXT, targetLang TEXT, frequencyMode TEXT,
            kinds TEXT NOT NULL DEFAULT '',
            termCount INTEGER NOT NULL DEFAULT 0,
            kanjiCount INTEGER NOT NULL DEFAULT 0,
            metaCount INTEGER NOT NULL DEFAULT 0,
            mediaBytes INTEGER NOT NULL DEFAULT 0,
            importedAt INTEGER NOT NULL,
            status TEXT NOT NULL
        )""",
        """CREATE TABLE term(
            id INTEGER PRIMARY KEY,
            dictId INTEGER NOT NULL,
            expression TEXT NOT NULL,
            reading TEXT NOT NULL,
            readingKey TEXT NOT NULL,
            defTags TEXT NOT NULL,
            rules TEXT NOT NULL,
            score INTEGER NOT NULL,
            sequence INTEGER NOT NULL,
            termTags TEXT NOT NULL,
            glossaryJson TEXT NOT NULL
        )""",
        "CREATE INDEX term_expression ON term(expression)",
        "CREATE INDEX term_reading ON term(readingKey)",
        "CREATE INDEX term_dict ON term(dictId)",
        "CREATE TABLE term_kanji(kanji TEXT NOT NULL, termId INTEGER NOT NULL, dictId INTEGER NOT NULL)",
        "CREATE INDEX term_kanji_kanji ON term_kanji(kanji)",
        "CREATE INDEX term_kanji_dict ON term_kanji(dictId)",
        """CREATE TABLE term_meta(
            dictId INTEGER NOT NULL, expression TEXT NOT NULL, reading TEXT,
            mode TEXT NOT NULL, dataJson TEXT NOT NULL, freqValue INTEGER
        )""",
        "CREATE INDEX term_meta_expression ON term_meta(expression)",
        "CREATE INDEX term_meta_dict ON term_meta(dictId)",
        """CREATE TABLE kanji(
            dictId INTEGER NOT NULL, character TEXT NOT NULL,
            onyomi TEXT NOT NULL, kunyomi TEXT NOT NULL, tags TEXT NOT NULL,
            meaningsJson TEXT NOT NULL, statsJson TEXT NOT NULL
        )""",
        "CREATE INDEX kanji_character ON kanji(character)",
        "CREATE INDEX kanji_dict ON kanji(dictId)",
        "CREATE TABLE kanji_meta(dictId INTEGER NOT NULL, character TEXT NOT NULL, mode TEXT NOT NULL, dataJson TEXT NOT NULL)",
        "CREATE INDEX kanji_meta_character ON kanji_meta(character)",
        "CREATE INDEX kanji_meta_dict ON kanji_meta(dictId)",
        """CREATE TABLE tag(
            dictId INTEGER NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL,
            sortOrder INTEGER NOT NULL, notes TEXT NOT NULL, score INTEGER NOT NULL
        )""",
        "CREATE INDEX tag_dict ON tag(dictId, name)",
        """CREATE TABLE media(
            dictId INTEGER NOT NULL, path TEXT NOT NULL, mime TEXT NOT NULL,
            bytes BLOB NOT NULL, PRIMARY KEY(dictId, path)
        )""",
        "CREATE TABLE profile(id INTEGER PRIMARY KEY, name TEXT NOT NULL, sortOrder INTEGER NOT NULL)",
        """CREATE TABLE profile_dictionary(
            profileId INTEGER NOT NULL, dictId INTEGER NOT NULL,
            priority INTEGER NOT NULL, enabled INTEGER NOT NULL,
            PRIMARY KEY(profileId, dictId)
        )""",
        "CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        "INSERT INTO profile(id, name, sortOrder) VALUES (1, 'English', 0), (2, '日本語', 1)",
    )
}
