package sfg.wseminar.rover

import kotlinx.serialization.json.*
import sfg.wseminar.rover.ui.topbar.DesktopInstaller

internal fun detectDesktopInstaller(json: String): DesktopInstaller? {
    val info = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
    fun value(key: String) = (info[key] as? JsonPrimitive)?.contentOrNull.orEmpty().lowercase()
    val platform = value("platform")
    val agent = value("userAgent")
    val architecture = value("architecture")
    val bitness = value("bitness")
    val mobile = value("mobile") == "true" || "android" in agent || "iphone" in agent || "ipad" in agent ||
        ("mac" in platform && (value("touch").toIntOrNull() ?: 0) > 1)
    if (mobile || "cros" in agent || bitness == "32") return null
    val arm = architecture == "arm" || "aarch64" in agent || "arm64" in agent
    val x64 = architecture == "x86" && bitness == "64" || "x86_64" in agent || "x64" in agent || "win64" in agent || "wow64" in agent
    return when {
        "mac" in platform -> when {
            arm -> DesktopInstaller.MACOS_ARM64
            architecture == "x86" && bitness == "64" -> DesktopInstaller.MACOS_X64
            else -> null // MacIntel / Intel UA is also reported on Apple Silicon.
        }
        "win" in platform && x64 && !arm -> DesktopInstaller.WINDOWS_X64
        "linux" in platform && x64 && !arm -> DesktopInstaller.LINUX_X64
        else -> null
    }
}
