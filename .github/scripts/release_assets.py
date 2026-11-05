"""Stable, platform-first names shared by packaging and immutable publication."""

APP_NAME = "WSMarsRoverSim"
TARGETS = {
    "windows-x64": ("msi", "zip"),
    "macos-arm64": ("dmg", "zip"),
    "macos-x64": ("dmg", "zip"),
    "linux-x64": ("deb", "tar.gz"),
}


def installer_name(target):
    return f"{target}-{APP_NAME}-installer.{TARGETS[target][0]}"


def portable_name(target):
    return f"{target}-{APP_NAME}-portable.{TARGETS[target][1]}"


WASM_ASSET = f"web-wasm-{APP_NAME}.zip"
ASSETS = tuple(name for target in TARGETS for name in (portable_name(target), installer_name(target))) + (WASM_ASSET,)
