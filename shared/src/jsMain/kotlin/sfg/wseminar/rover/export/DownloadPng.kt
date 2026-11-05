package sfg.wseminar.rover.export

internal actual fun downloadPng(base64: String, fileName: String) {
    val download: dynamic = js("""(base64, fileName) => {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const url = URL.createObjectURL(new Blob([bytes], {type: 'image/png'}));
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    try { link.click(); } finally {
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
}""")
    download(base64, fileName)
}
