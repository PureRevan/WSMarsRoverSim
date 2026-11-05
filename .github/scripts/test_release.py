"""Fast, offline checks for the irreversible draft-to-published transition."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import publish_release
from validate_release import version_from_tag


class VersionTests(unittest.TestCase):
    def test_installer_compatible_versions(self):
        self.assertEqual(version_from_tag("v1.0.0"), (1, 0, 0))
        self.assertEqual(version_from_tag("v255.255.65535"), (255, 255, 65535))
        for tag in ("v0.1.0", "v1.02.0", "v1.0.0-beta", "v256.0.0", "v1.256.0", "v1.0.65536", "v1.0"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                version_from_tag(tag)


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        previous_directory = Path.cwd()
        os.chdir(self.directory.name)
        self.addCleanup(os.chdir, previous_directory)
        self.addCleanup(patch.stopall)
        patch.dict(os.environ, {
            "GITHUB_REPOSITORY": "example/fresh-public-repo",
            "RELEASE_TAG": "v1.2.0", "RELEASE_COMMIT": "a" * 40,
            "GITHUB_OUTPUT": str(Path.cwd() / "outputs"),
        }).start()
        Path("release-assets").mkdir()
        for name in publish_release.ASSETS:
            (Path("release-assets") / name).write_bytes(b"build output")
        self.release = None
        self.latest = None
        self.commands = []
        self.incomplete_upload = False
        self.fail_upload = False
        patch.object(publish_release, "api", side_effect=self.api).start()
        patch.object(publish_release.subprocess, "run", side_effect=self.command).start()
        patch.object(
            publish_release,
            "release_by_tag",
            side_effect=lambda repository, tag: self.release,
        ).start()

    def api(self, repository, endpoint):
        self.assertEqual(repository, "example/fresh-public-repo")
        return self.latest if endpoint == "releases/latest" else self.release

    def command(self, args, **kwargs):
        self.commands.append(args)
        self.assertEqual(args[:2], ["gh", "release"])
        self.assertEqual(args[-2:], ["--repo", "example/fresh-public-repo"])
        operation = args[2]
        if operation == "create":
            self.assertIn("--draft", args)
            self.assertNotIn("--generate-notes", args)
            self.release = {"tag_name": "v1.2.0", "draft": True, "assets": []}
        elif operation == "upload":
            self.assertTrue(self.release["draft"], "Published assets must never be overwritten")
            if self.fail_upload:
                raise subprocess.CalledProcessError(1, args)
            paths = [Path(arg) for arg in args[4:] if arg.startswith("release-assets/")]
            self.release["assets"] = [
                {"name": path.name, "state": "uploaded", "size": path.stat().st_size}
                for path in paths
            ]
            if self.incomplete_upload:
                self.release["assets"].pop()
        elif operation == "edit":
            self.assertIn("--draft=false", args)
            notes = Path(args[args.index("--notes-file") + 1]).read_text(encoding="utf-8")
            self.assertNotIn("Full Changelog", notes)
            self.assertNotIn("WSRoverSim", notes)
            for asset in publish_release.ASSETS:
                self.assertIn(f"/releases/download/v1.2.0/{asset}", notes)
            publish_release.validate_assets(self.release)
            self.release["draft"] = False
            if "--latest=true" in args:
                self.latest = self.release
        else:
            self.fail(f"Unexpected command: {args}")

    def test_publish_only_after_complete_upload(self):
        publish_release.main()
        self.assertEqual([args[2] for args in self.commands], ["create", "upload", "edit"])
        self.assertFalse(self.release["draft"])
        self.assertEqual(Path("outputs").read_text().strip(), "deploy_pages=true")
        self.assertEqual(len(Path("release-assets/SHA256SUMS").read_text().splitlines()), len(publish_release.ASSETS))

    def test_published_rerun_never_modifies_assets(self):
        self.test_publish_only_after_complete_upload()
        self.commands.clear()
        publish_release.main()
        self.assertEqual(self.commands, [])

    def test_draft_rerun_resumes_upload(self):
        self.release = {"tag_name": "v1.2.0", "draft": True, "assets": []}
        publish_release.main()
        self.assertEqual([args[2] for args in self.commands], ["upload", "edit"])

    def test_missing_build_stops_before_creating_draft(self):
        (Path("release-assets") / publish_release.ASSETS[0]).unlink()
        with self.assertRaisesRegex(RuntimeError, "Missing build output"):
            publish_release.main()
        self.assertEqual(self.commands, [])

    def test_incomplete_upload_does_not_publish(self):
        self.incomplete_upload = True
        with self.assertRaisesRegex(RuntimeError, "missing a complete asset"):
            publish_release.main()
        self.assertTrue(self.release["draft"])
        self.assertNotIn("edit", [args[2] for args in self.commands])

    def test_failed_upload_does_not_publish(self):
        self.fail_upload = True
        with self.assertRaises(subprocess.CalledProcessError):
            publish_release.main()
        self.assertTrue(self.release["draft"])
        self.assertNotIn("edit", [args[2] for args in self.commands])

    def test_older_version_keeps_latest_and_pages(self):
        self.latest = {"tag_name": "v1.10.0"}
        publish_release.main()
        self.assertIn("--latest=false", self.commands[-1])
        self.assertEqual(self.latest["tag_name"], "v1.10.0")
        self.assertEqual(Path("outputs").read_text().strip(), "deploy_pages=false")


if __name__ == "__main__":
    unittest.main()

