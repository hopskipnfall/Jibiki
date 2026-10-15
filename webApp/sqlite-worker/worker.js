// SQLite web worker implementing the androidx.sqlite WebWorkerSQLiteDriver protocol.
//
// Requests arrive as {id, data: {cmd, ...}} and are answered with {id, data} or {id, error}.
// Databases live in OPFS through the "opfs-sahpool" VFS, which works in Firefox and Chromium
// without COOP/COEP headers. When OPFS is unavailable (e.g. Firefox private windows) databases
// fall back to memory and the "open" result carries inMemory: true.
import sqlite3InitModule from "@sqlite.org/sqlite-wasm";

let sqlite3 = null;
let pool = null;
let inMemory = false;

const ready = (async () => {
  sqlite3 = await sqlite3InitModule();
  // Access handles are exclusive: right after a reload the previous page's worker may still
  // hold them, so retry for a few seconds before giving up on persistence.
  for (let attempt = 0; ; attempt++) {
    try {
      pool = await sqlite3.installOpfsSAHPoolVfs({ name: "jibiki", initialCapacity: 8 });
      break;
    } catch (e) {
      if (attempt >= 8 || typeof navigator.storage?.getDirectory !== "function") {
        console.warn("jibiki: OPFS unavailable, using in-memory database", e);
        inMemory = true;
        break;
      }
      await new Promise((r) => setTimeout(r, 250 * (attempt + 1)));
    }
  }
})();

const databases = new Map();
const statements = new Map();
let nextDatabaseId = 1;
let nextStatementId = 1;

self.onmessage = async (event) => {
  const { id, data } = event.data;
  let response;
  try {
    await ready;
    response = { id, data: handle(data) };
  } catch (e) {
    response = { id, error: String((e && e.message) || e) };
  }
  // "close" is fire-and-forget on the Kotlin side, but answering is harmless.
  self.postMessage(response);
};

function handle(request) {
  switch (request.cmd) {
    case "open": return open(request.fileName);
    case "prepare": return prepare(request.databaseId, request.sql);
    case "step": return step(request.statementId, request.bindings);
    case "close": return close(request.statementId, request.databaseId);
    default: throw new Error("Unknown command: " + request.cmd);
  }
}

function open(fileName) {
  const db = inMemory
    ? new sqlite3.oo1.DB(":memory:", "c")
    : new pool.OpfsSAHPoolDb("/" + fileName.replace(/^\/+/, ""));
  const databaseId = nextDatabaseId++;
  databases.set(databaseId, db);
  return { databaseId, inMemory };
}

function prepare(databaseId, sql) {
  const db = databases.get(databaseId);
  if (!db) throw new Error("No database " + databaseId);
  const stmt = db.prepare(sql);
  const statementId = nextStatementId++;
  statements.set(statementId, stmt);
  return {
    statementId,
    parameterCount: stmt.parameterCount,
    columnNames: stmt.columnCount > 0 ? stmt.getColumnNames() : [],
  };
}

function step(statementId, bindings) {
  const stmt = statements.get(statementId);
  if (!stmt) throw new Error("No statement " + statementId);
  stmt.reset(true);
  if (bindings && bindings.length > 0) {
    // Holes in the array (unbound params) become null.
    const values = Array.from(bindings, (v) => (v === undefined ? null : v));
    stmt.bind(values);
  }
  const rows = [];
  let columnTypes = [];
  const columnCount = stmt.columnCount;
  while (stmt.step()) {
    if (rows.length === 0) {
      columnTypes = new Array(columnCount);
      for (let i = 0; i < columnCount; i++) {
        columnTypes[i] = sqlite3.capi.sqlite3_column_type(stmt.pointer, i);
      }
    }
    const row = new Array(columnCount);
    for (let i = 0; i < columnCount; i++) {
      let value = stmt.get(i);
      if (typeof value === "bigint") value = Number(value);
      row[i] = value;
    }
    rows.push(row);
  }
  return { rows, columnTypes };
}

function close(statementId, databaseId) {
  if (statementId != null) {
    const stmt = statements.get(statementId);
    if (stmt) {
      stmt.finalize();
      statements.delete(statementId);
    }
  }
  if (databaseId != null) {
    const db = databases.get(databaseId);
    if (db) {
      db.close();
      databases.delete(databaseId);
    }
  }
  return {};
}
