"""Accept native-installer-compatible stable tags whose commit belongs to main."""
import os
import re
import subprocess
from pathlib import Path


def version_from_tag(tag):
    match = re.fullmatch(r"v([1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag)
    if not match:
        raise ValueError("Use a stable tag such as v1.0.0 (major version must be at least 1).")
    version = tuple(map(int, match.groups()))
    if version[0] > 255 or version[1] > 255 or version[2] > 65535:
        raise ValueError("Installer versions require major/minor <= 255 and patch <= 65535.")
    return version


if __name__ == "__main__":
    tag = os.environ["RELEASE_TAG"]
    version = version_from_tag(tag)
    commit = subprocess.check_output(["git", "rev-parse", f"refs/tags/{tag}^{{commit}}"], text=True).strip()
    subprocess.run(["git", "merge-base", "--is-ancestor", commit, "refs/remotes/origin/main"], check=True)
    with Path(os.environ["GITHUB_OUTPUT"]).open("a", encoding="utf-8") as output:
        output.write(f"version={'.'.join(map(str, version))}\ncommit={commit}\n")
    print(f"Validated {tag} on main ({commit}).")
