// Browser helpers for the Kotlin/Wasm app: zip reading, file picking, storage and tab locking.
import { unzipSync } from "fflate";

// ---- Zip ----------------------------------------------------------------------------------

/** Wraps an ArrayBuffer; entry names are read from the central directory without inflating. */
export function zipOpen(buffer) {
  const data = new Uint8Array(buffer);
  const names = [];
  unzipSync(data, { filter: (file) => { names.push(file.name); return false; } });
  return { data, names: names.filter((n) => !n.endsWith("/")) };
}

export function zipNames(handle) {
  return handle.names;
}

function readEntries(handle, wanted) {
  const set = new Set(wanted);
  return unzipSync(handle.data, { filter: (file) => set.has(file.name) });
}

export function zipReadText(handle, name) {
  const out = readEntries(handle, [name])[name];
  if (!out) throw new Error("Missing file " + name);
  return new TextDecoder("utf-8").decode(out);
}

/** Inflates several entries in one pass; returns an array aligned with `names`. */
export function zipReadMany(handle, names) {
  const out = readEntries(handle, names);
  return names.map((n) => out[n] || new Uint8Array(0));
}

export function zipReadBytes(handle, name) {
  const out = readEntries(handle, [name])[name];
  if (!out) throw new Error("Missing file " + name);
  return out;
}

// ---- Files --------------------------------------------------------------------------------

/** Opens the browser's file chooser; resolves with the chosen File objects ([] if cancelled). */
export function pickFiles(accept) {
  return new Promise((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.multiple = true;
    input.accept = accept;
    input.style.display = "none";
    document.body.appendChild(input);
    const done = (files) => { input.remove(); resolve(files); };
    input.addEventListener("change", () => done(Array.from(input.files || [])));
    input.addEventListener("cancel", () => done([]));
    input.click();
  });
}

export function fileName(file) { return file.name; }

export function fileArrayBuffer(file) { return file.arrayBuffer(); }

// ---- Storage ------------------------------------------------------------------------------

export async function storagePersisted() {
  return !!(navigator.storage && navigator.storage.persisted && (await navigator.storage.persisted()));
}

export async function storagePersist() {
  return !!(navigator.storage && navigator.storage.persist && (await navigator.storage.persist()));
}

/** Resolves to [usage, quota] in bytes, or null. */
export async function storageEstimate() {
  if (!navigator.storage || !navigator.storage.estimate) return null;
  const { usage, quota } = await navigator.storage.estimate();
  return [usage || 0, quota || 0];
}

// ---- Single tab ---------------------------------------------------------------------------

/** Holds a Web Lock for the page's lifetime; resolves false if another tab holds it. */
export function acquireAppLock() {
  return new Promise((resolve) => {
    if (!navigator.locks) { resolve(true); return; }
    navigator.locks.request("jibiki-database", { ifAvailable: true }, (lock) => {
      if (!lock) { resolve(false); return undefined; }
      resolve(true);
      return new Promise(() => {}); // never released
    });
  });
}

// ---- URL & history -------------------------------------------------------------------------

export function readHashQuery() {
  const m = /^#q=(.*)$/.exec(location.hash);
  return m ? decodeURIComponent(m[1]) : "";
}

export function writeHashQuery(q) {
  const hash = q ? "#q=" + encodeURIComponent(q) : "";
  if (location.hash !== hash) history.replaceState(history.state, "", location.pathname + location.search + hash);
}

export function pushHistory(depth) { history.pushState({ jibikiDepth: depth }, "", location.href); }

export function goBack(steps) { history.go(-steps); }

/** Calls back with the depth stored in the history entry we arrived at (1 = the start page). */
export function onPopState(callback) {
  window.addEventListener("popstate", (e) => callback((e.state && e.state.jibikiDepth) || 1));
}

export function reloadPage() { location.reload(); }
