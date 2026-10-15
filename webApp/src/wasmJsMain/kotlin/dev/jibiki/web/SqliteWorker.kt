package dev.jibiki.web

import org.w3c.dom.Worker

fun createSqliteWorker(): Worker =
    js("""new Worker(new URL("jibiki-sqlite-worker/worker.js", import.meta.url), { type: "module" })""")
