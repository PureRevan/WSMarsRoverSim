package sfg.wseminar.rover.transfer

internal actual fun browserPick(callback: (String, String, String) -> Unit): Int {
    val call: dynamic = js("""(function(callback) {
 const registry = window.__roverFiles || (window.__roverFiles = {next: 1, entries: {}});
 const id = registry.next++; const input = document.createElement('input');
 input.type = 'file'; input.accept = '.rover,.json,application/json,application/octet-stream'; input.style.display = 'none'; document.body.appendChild(input);
 let reader; let active = true;
 const clean = function() { active = false; input.remove(); if (reader && reader.readyState === 1) reader.abort(); delete registry.entries[id]; };
 registry.entries[id] = clean;
 const done = function(name, data, error) { if (active) { clean(); callback(name, data, error); } };
 input.oncancel = function() { done('', '', ''); };
 input.onchange = function() {
  const file = input.files[0]; if (!file) { done('', '', ''); return; }
  reader = new FileReader(); reader.onerror = function() { done('', '', 'Could not read file.'); };
  reader.onload = function() { done(file.name, reader.result.split(',')[1], ''); }; reader.readAsDataURL(file);
 }; input.click(); return id;
})""")
    return call(callback)
}
internal actual fun browserDrop(callback: (String, String, String) -> Unit): Int {
    val call: dynamic = js("""(function(callback) {
 const registry = window.__roverFiles || (window.__roverFiles = {next: 1, entries: {}});
 const id = registry.next++; let active = true; const readers = new Set();
 const over = function(e) { e.preventDefault(); e.dataTransfer.dropEffect = 'copy'; };
 const drop = function(e) { e.preventDefault(); const files = e.dataTransfer.files;
  if (files.length !== 1) { callback('', '', 'Drop one Rover bundle at a time.'); return; }
  const file = files[0];
  const reader = new FileReader(); readers.add(reader);
  reader.onerror = function() { readers.delete(reader); if (active) callback('', '', 'Could not read file.'); };
  reader.onload = function() { readers.delete(reader); if (active) callback(file.name, reader.result.split(',')[1], ''); }; reader.readAsDataURL(file);
 };
 document.addEventListener('dragover', over); document.addEventListener('drop', drop);
 registry.entries[id] = function() { active = false; readers.forEach(function(r) { r.abort(); }); document.removeEventListener('dragover', over); document.removeEventListener('drop', drop); delete registry.entries[id]; };
 return id;
})""")
    return call(callback)
}
internal actual fun browserDispose(id: Int): Unit {
    val call: dynamic = js("""(function(id) { const r = window.__roverFiles; if (r && r.entries[id]) r.entries[id](); })""")
    call(id)
}
internal actual suspend fun browserSave(bytes: ByteArray, name: String, mime: String) {
    val call: dynamic = js("""(function(bytes, name, mime) {
 const url = URL.createObjectURL(new Blob([bytes], {type: mime})); const link = document.createElement('a');
 link.href = url; link.download = name; document.body.appendChild(link);
 try { link.click(); } finally { link.remove(); setTimeout(function() { URL.revokeObjectURL(url); }, 60000); }
})""")
    call(bytes, name, mime)
}
