@file:JsModule("jibiki-web")

package dev.jibiki.web

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.w3c.files.File
import kotlin.js.Promise

external fun zipOpen(buffer: ArrayBuffer): JsAny
external fun zipNames(handle: JsAny): JsArray<JsString>
external fun zipReadText(handle: JsAny, name: String): String
external fun zipReadBytes(handle: JsAny, name: String): Uint8Array
external fun zipReadMany(handle: JsAny, names: JsArray<JsString>): JsArray<Uint8Array>

external fun pickFiles(accept: String): Promise<JsArray<File>>
external fun fileName(file: File): String
external fun fileArrayBuffer(file: File): Promise<ArrayBuffer>

external fun storagePersisted(): Promise<JsBoolean>
external fun storagePersist(): Promise<JsBoolean>
external fun storageEstimate(): Promise<JsArray<JsNumber>?>

external fun acquireAppLock(): Promise<JsBoolean>

external fun readHashQuery(): String
external fun writeHashQuery(q: String)
external fun pushHistory(depth: Int)
external fun goBack(steps: Int)
external fun onPopState(callback: (Int) -> Unit)
external fun reloadPage()
