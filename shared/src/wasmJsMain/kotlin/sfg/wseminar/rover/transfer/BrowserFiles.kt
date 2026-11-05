package sfg.wseminar.rover.transfer

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(callback) => {
 const registry = window.__roverFiles || (window.__roverFiles = {next: 1, entries: {}});
 const id = registry.next++; const input = document.createElement('input');
 input.type = 'file'; input.accept = '.rover,.json,application/json,application/octet-stream'; input.style.display = 'none'; document.body.appendChild(input);
 let reader; let active = true;
 const clean = () => { active = false; input.remove(); if (reader && reader.readyState === 1) reader.abort(); delete registry.entries[id]; };
 registry.entries[id] = clean;
 const done = (name, data, error) => { if (active) { clean(); callback(name, data, error); } };
 input.oncancel = () => done('', '', '');
 input.onchange = () => {
  const file = input.files[0]; if (!file) { done('', '', ''); return; }
  reader = new FileReader(); reader.onerror = () => done('', '', 'Could not read file.');
  reader.onload = () => done(file.name, reader.result.split(',')[1], ''); reader.readAsDataURL(file);
 }; input.click(); return id;
}""")
private external fun browserPickJs(callback: (String, String, String) -> Unit): Int
internal actual fun browserPick(callback: (String, String, String) -> Unit): Int = browserPickJs(callback)
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(callback) => {
 const registry = window.__roverFiles || (window.__roverFiles = {next: 1, entries: {}});
 const id = registry.next++; let active = true; const readers = new Set();
 const over = e => { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; };
 const drop = e => { e.preventDefault(); const files = e.dataTransfer.files;
  if (files.length !== 1) { callback('', '', 'Drop one Rover bundle at a time.'); return; }
  const file = files[0];
  const reader = new FileReader(); readers.add(reader);
  reader.onerror = () => { readers.delete(reader); if (active) callback('', '', 'Could not read file.'); };
  reader.onload = () => { readers.delete(reader); if (active) callback(file.name, reader.result.split(',')[1], ''); }; reader.readAsDataURL(file);
 };
 document.addEventListener('dragover', over); document.addEventListener('drop', drop);
 registry.entries[id] = () => { active = false; for (const r of readers) r.abort(); document.removeEventListener('dragover', over); document.removeEventListener('drop', drop); delete registry.entries[id]; };
 return id;
}""")
private external fun browserDropJs(callback: (String, String, String) -> Unit): Int
internal actual fun browserDrop(callback: (String, String, String) -> Unit): Int = browserDropJs(callback)
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(id) => { const r = window.__roverFiles; if (r && r.entries[id]) r.entries[id](); }""")
private external fun browserDisposeJs(id: Int): Unit
internal actual fun browserDispose(id: Int): Unit = browserDisposeJs(id)
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun("""(bytes, name, mime) => {
 const url = URL.createObjectURL(new Blob([bytes], {type: mime})); const link = document.createElement('a');
 link.href = url; link.download = name; document.body.appendChild(link);
 try { link.click(); } finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 60000); }
}""")
private external fun browserSaveJs(bytes: kotlin.js.JsAny, name: String, mime: String): Unit
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal actual suspend fun browserSave(bytes: ByteArray, name: String, mime: String) = browserSaveJs(sfg.wseminar.rover.interop.copyToBrowser(bytes), name, mime)
