"""Package an app image (including Java) and stage the optional native installer."""
import os
import shutil
import subprocess
import tarfile
import zipfile
from pathlib import Path

from release_assets import APP_NAME, TARGETS, installer_name, portable_name


def app_image(root, target):
    app = root / "app" / (f"{APP_NAME}.app" if target.startswith("macos-") else APP_NAME)
    if target.startswith("macos-"):
        launcher = app / "Contents" / "MacOS" / APP_NAME
        runtime = app / "Contents" / "runtime" / "Contents" / "Home" / "lib" / "libjli.dylib"
    elif target.startswith("windows-"):
        launcher = app / f"{APP_NAME}.exe"
        runtime = app / "runtime" / "bin" / "server" / "jvm.dll"
    else:
        launcher = app / "bin" / APP_NAME
        runtime = app / "lib" / "runtime" / "lib" / "server" / "libjvm.so"
    for required in (launcher, runtime):
        if not required.is_file() or required.stat().st_size == 0:
            raise RuntimeError(f"Incomplete portable app image: {required}")
    return app


def archive_app(app, destination, target):
    if target.startswith("macos-"):
        # Preserve the .app's symlinks, executable modes and macOS bundle metadata.
        subprocess.run([
            "ditto", "-c", "-k", "--sequesterRsrc", "--keepParent",
            str(app), str(destination),
        ], check=True)
    elif target.startswith("linux-"):
        with tarfile.open(destination, "w:gz", compresslevel=6) as archive:
            archive.add(app, arcname=app.name)
    else:
        # Windows Explorer can extract this ZIP, including the launcher and bundled runtime.
        with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for path in sorted(app.rglob("*")):
                archive.write(path, path.relative_to(app.parent).as_posix())


def stage(target, extension, root=Path("desktopApp/build/compose/binaries/main"), destination=Path("release-assets")):
    if target not in TARGETS or TARGETS[target][0] != extension:
        raise ValueError("Unknown desktop target or installer extension.")
    installers = list((root / extension).glob(f"*.{extension}"))
    if len(installers) != 1 or installers[0].stat().st_size == 0:
        raise RuntimeError(f"Expected exactly one nonempty {extension} installer, found {len(installers)}.")
    app = app_image(root, target)
    destination.mkdir(parents=True, exist_ok=True)
    portable = destination / portable_name(target)
    archive_app(app, portable, target)
    shutil.copy2(installers[0], destination / installer_name(target))
    print(f"Staged {portable.name} and {installer_name(target)} (Java included).")


if __name__ == "__main__":
    stage(os.environ["DESKTOP_TARGET"], os.environ["PACKAGE_EXTENSION"])
