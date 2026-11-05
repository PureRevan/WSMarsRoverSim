@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package sfg.wseminar.rover

/** Platform hints only select a download; no platform data is sent to a server. */
@JsFun("""() => JSON.stringify({platform: navigator.platform || '', userAgent: navigator.userAgent || '', touch: navigator.maxTouchPoints || 0})""")
external fun browserPlatform(): String

@JsFun("""(callback) => {
    const fallback = {platform: navigator.platform || '', userAgent: navigator.userAgent || '', touch: navigator.maxTouchPoints || 0};
    const data = navigator.userAgentData;
    if (!data || !data.getHighEntropyValues) { callback(JSON.stringify(fallback)); return; }
    data.getHighEntropyValues(['architecture', 'bitness', 'platform']).then(function(info) {
        callback(JSON.stringify(Object.assign({}, fallback, info, {mobile: data.mobile})));
    }).catch(function() { callback(JSON.stringify(fallback)); });
}""")
external fun detectPlatform(callback: (String) -> Unit)
