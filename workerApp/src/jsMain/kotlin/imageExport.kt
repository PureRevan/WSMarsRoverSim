import sfg.wseminar.rover.export.rasterPdf

/** Encoding only: pixels arrive via a transferable buffer, with no SVG parse or font loading. */
fun imageExporter(): dynamic {
    val factory: dynamic = js("""pdf => request => Promise.resolve().then(() => {
        const width = request.width, height = request.height;
        const pixels = new Uint8ClampedArray(request.pixels);
        if (pixels.length !== width * height * 4) throw Error('Invalid raster pixel buffer');
        if (request.format === 'PDF') {
            // Stream RGB rows into deflate instead of retaining a second full-size RGB image.
            let row = 0;
            const stream = new ReadableStream({pull(controller) {
                if (row === height) { controller.close(); return; }
                const rows = Math.min(32, height - row);
                const rgb = new Uint8Array(width * rows * 3);
                let i = row * width * 4;
                for(let j=0;j<rgb.length;) {
                    const alpha = pixels[i+3] / 255;
                    rgb[j++]=Math.round(pixels[i++] * alpha + 255 * (1-alpha));
                    rgb[j++]=Math.round(pixels[i++] * alpha + 255 * (1-alpha));
                    rgb[j++]=Math.round(pixels[i++] * alpha + 255 * (1-alpha)); i++;
                }
                row += rows; controller.enqueue(rgb);
            }}).pipeThrough(new CompressionStream('deflate'));
            return new Response(stream).arrayBuffer().then(bytes => pdf(width, height, new Int8Array(bytes)));
        }
        const canvas = new OffscreenCanvas(width, height);
        const context = canvas.getContext('2d');
        if (!context) throw Error('Image encoder canvas is unavailable');
        context.putImageData(new ImageData(pixels, width, height), 0, 0);
        const type = request.format === 'PNG' ? 'image/png' : 'image/jpeg';
        return canvas.convertToBlob({type, quality:request.quality/100}).then(blob => {
            if (blob.type !== type) throw Error('The browser does not support this image format');
            return blob.arrayBuffer();
        }).then(bytes => new Uint8Array(bytes));
    })""")
    return factory({ w: Int, h: Int, bytes: ByteArray -> rasterPdf(w, h, bytes) })
}
