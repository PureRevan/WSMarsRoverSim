"""Publish atomically with immutable releases: all assets first, publish last.

Reruns can finish a draft or reuse a complete published release. They never
replace an asset in a published release. This script runs only in GitHub Actions.
"""
import hashlib
import json
import os
import subprocess
from pathlib import Path
from release_assets import APP_NAME, ASSETS, WASM_ASSET, installer_name, portable_name
from validate_release import version_from_tag


def release_notes(repository, tag):
    base = f"https://github.com/{repository}/releases/download/{tag}"
    lines = [
        f"{APP_NAME} {tag}", "",
        "### Desktop downloads", "",
        "Portable downloads run without installation and include Java. Extract the **entire archive** before starting the app.", "",
        "| Platform | Portable (recommended) | Installer (optional) |",
        "| --- | --- | --- |",
    ]
    for target, label in (
        ("windows-x64", "Windows · Intel / AMD 64-bit"),
        ("macos-arm64", "macOS · Apple Silicon"),
        ("macos-x64", "macOS · Intel"),
        ("linux-x64", "Linux · Intel / AMD 64-bit"),
    ):
        portable, installer = portable_name(target), installer_name(target)
        lines.append(f"| {label} | [{portable}]({base}/{portable}) | [{installer}]({base}/{installer}) |")
    lines.extend([
        "",
        f"- **Windows:** extract the ZIP and open `{APP_NAME}/{APP_NAME}.exe`. Keep the `app` and `runtime` folders next to it.",
        f"- **macOS:** extract the ZIP and open `{APP_NAME}.app`. Moving it to Applications is optional.",
        f"- **Linux:** extract the tar.gz and run `{APP_NAME}/bin/{APP_NAME}`. The optional `.deb` is for Debian/Ubuntu compatible systems.",
        "",
        "Desktop packages are unsigned; macOS builds are not notarized. Native system libraries are still required on Linux.",
        "",
        f"[{WASM_ASSET}]({base}/{WASM_ASSET}) contains the complete optimized website. "
        f"[SHA256SUMS]({base}/SHA256SUMS) covers all nine downloads.", "",
    ])
    return "\n".join(lines)


def api(repository, endpoint):
    result = subprocess.run(["gh", "api", f"repos/{repository}/{endpoint}"], text=True, capture_output=True)
    if result.returncode:
        if "(HTTP 404)" in result.stderr:
            return None
        raise RuntimeError(result.stderr)
    return json.loads(result.stdout)

def release_by_tag(repository, tag):
    result = subprocess.run(
        [
            "gh", "api",
            f"repos/{repository}/releases?per_page=100",
            "--paginate",
            "--slurp",
        ],
        text=True,
        capture_output=True,
    )
    if result.returncode:
        raise RuntimeError(result.stderr)

    matches = [
        release
        for page in json.loads(result.stdout)
        for release in page
        if release["tag_name"] == tag
    ]

    if len(matches) > 1:
        raise RuntimeError(f"Multiple releases found for {tag}.")

    return matches[0] if matches else None

def validate_assets(release, sizes=None):
    assets = {asset["name"]: asset for asset in release["assets"]}
    for name in (*ASSETS, "SHA256SUMS"):
        asset = assets.get(name)
        if not asset or asset["state"] != "uploaded" or asset["size"] <= 0:
            raise RuntimeError(f"Release is missing a complete asset: {name}")
        if sizes and asset["size"] != sizes[name]:
            raise RuntimeError(f"Uploaded size differs for {name}; leaving the release as a draft.")


def main():
    repository = os.environ["GITHUB_REPOSITORY"]
    tag = os.environ["RELEASE_TAG"]
    version = version_from_tag(tag)
    commit = os.environ["RELEASE_COMMIT"]
    root = Path("release-assets")
    paths = [root / name for name in ASSETS]
    for path in paths:
        if not path.is_file() or path.stat().st_size == 0:
            raise RuntimeError(f"Missing build output: {path}")

    existing = release_by_tag(repository, tag)
    if existing and not existing["draft"]:
        validate_assets(existing)
        print("Release already published; keeping all immutable assets unchanged.")
    else:
        checksums = []
        for path in paths:
            digest = hashlib.sha256()
            with path.open("rb") as file:
                for chunk in iter(lambda: file.read(1024 * 1024), b""):
                    digest.update(chunk)
            checksums.append(f"{digest.hexdigest()}  {path.name}\n")
        checksum_file = root / "SHA256SUMS"
        checksum_file.write_text("".join(checksums), encoding="utf-8")
        paths.append(checksum_file)

        def release(*args):
            subprocess.run(["gh", "release", *args, "--repo", repository], check=True)

        notes = root / "release-notes.md"
        notes.write_text(release_notes(repository, tag), encoding="utf-8")
        if not existing:
            release("create", tag, "--verify-tag", "--target", commit, "--draft",
                    "--title", f"{APP_NAME} {tag}", "--notes-file", str(notes))
        release("upload", tag, *(str(path) for path in paths), "--clobber")
        draft = release_by_tag(repository, tag)
        if not draft or not draft["draft"]:
            raise RuntimeError("Expected a draft release before publishing.")
        validate_assets(draft, {path.name: path.stat().st_size for path in paths})
        latest = api(repository, "releases/latest")
        make_latest = latest is None or version > version_from_tag(latest["tag_name"])
        # Replace draft notes on retries too; GitHub-generated notes append a Full Changelog link.
        release("edit", tag, "--draft=false", f"--latest={'true' if make_latest else 'false'}",
                "--title", f"{APP_NAME} {tag}", "--notes-file", str(notes))

    latest = api(repository, "releases/latest")
    deploy = latest is not None and latest["tag_name"] == tag
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(f"deploy_pages={'true' if deploy else 'false'}\n")
    print("Deploying Pages for Latest." if deploy else "Keeping Pages on the newer release.")


if __name__ == "__main__":
    main()
