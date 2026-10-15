package dev.jibiki.domain

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.jibiki.db.Database
import dev.jibiki.yomitan.JavaZipArchive
import dev.jibiki.yomitan.ZipArchive
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

suspend fun testDb(): Database = Database.open(BundledSQLiteDriver(), ":memory:")

fun zipOf(entries: Map<String, Any>): ZipArchive {
    val file = File.createTempFile("jibiki-fixture", ".zip")
    file.deleteOnExit()
    ZipOutputStream(file.outputStream()).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(if (content is ByteArray) content else content.toString().encodeToByteArray())
            zip.closeEntry()
        }
    }
    return JavaZipArchive(file)
}

/** Invented entries only — no real dictionary content. */
object TestDictionaries {
    fun english(title: String = "Test EN", revision: String = "1") = zipOf(
        mapOf(
            "index.json" to """{"title":"$title","revision":"$revision","format":3,"sourceLanguage":"ja","targetLanguage":"en"}""",
            "tag_bank_1.json" to """[["v1","partOfSpeech",0,"Ichidan verb",0],["P","popular",0,"common word",10]]""",
            "term_bank_1.json" to """[
              ["食べる","たべる","v1 P","v1",100,["to eat"],1,"P"],
              ["食べ物","たべもの","n","",10,["food"],2,""],
              ["食","しょく","n","",5,["meal"],3,""],
              ["高い","たかい","adj-i","adj-i",50,["tall","expensive"],4,""],
              ["行く","いく","v5","v5",60,["to go"],5,""],
              ["勉強","べんきょう","n vs","vs",40,["study"],7,""],
              ["Tシャツ","ティーシャツ","n","",1,["T-shirt"],8,""],
              ["ねこ","","n","",1,["cat (kana entry)"],9,""]
            ]""",
            "term_bank_2.json" to """[
              ["食堂","しょくどう","n","",1,[{"type":"structured-content","content":{"tag":"span","content":"dining hall"}}],6,""],
              "malformed"
            ]""",
            "img/a.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        ),
    )

    fun japanese() = zipOf(
        mapOf(
            "index.json" to """{"title":"テスト国語","revision":"1","format":3}""",
            "term_bank_1.json" to """[["食べる","たべる","","v1",0,["食物を口に入れて、かんで飲みこむ。"],1,""]]""",
        ),
    )

    fun frequency() = zipOf(
        mapOf(
            "index.json" to """{"title":"Test Freq","revision":"1","format":3}""",
            "term_meta_bank_1.json" to """[
              ["食べ物","freq",{"reading":"たべもの","frequency":{"value":50,"displayValue":"50"}}],
              ["食べる","freq",{"reading":"たべる","frequency":900}],
              ["食堂","freq",3000]
            ]""",
        ),
    )

    fun kanji() = zipOf(
        mapOf(
            "index.json" to """{"title":"Test Kanji","revision":"1","format":3}""",
            "kanji_bank_1.json" to """[["食","ショク","た.べる","",["eat","food"],{"strokes":"9","grade":"2"}]]""",
            "kanji_meta_bank_1.json" to """[["食","freq",320]]""",
        ),
    )
}
