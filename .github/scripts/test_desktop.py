"""Small fixture-based checks for portable archives and incomplete runtimes."""
import os
import stat
import tarfile
import tempfile
import unittest
import zipfile
from pathlib import Path

from release_assets import APP_NAME, ASSETS, installer_name, portable_name
from stage_desktop import stage


class PortableTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name) / "binaries"
        self.output = Path(self.directory.name) / "release-assets"

    def file(self, path, content=b"fixture"):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def test_windows_zip_contains_exe_app_and_java_without_installer(self):
        app = self.root / "app" / APP_NAME
        self.file(app / f"{APP_NAME}.exe")
        self.file(app / "runtime/bin/server/jvm.dll")
        self.file(app / "app/main.jar")
        self.file(self.root / "msi/versioned-package.msi")
        stage("windows-x64", "msi", self.root, self.output)
        with zipfile.ZipFile(self.output / portable_name("windows-x64")) as archive:
            self.assertIsNone(archive.testzip())
            for name in (f"{APP_NAME}.exe", "runtime/bin/server/jvm.dll", "app/main.jar"):
                self.assertEqual(archive.read(f"{APP_NAME}/{name}"), b"fixture")
            self.assertFalse(any(name.endswith(".msi") for name in archive.namelist()))
        self.assertTrue((self.output / installer_name("windows-x64")).is_file())

    @unittest.skipIf(os.name == "nt", "Unix executable modes and symlinks")
    def test_linux_archive_preserves_executable_and_runtime_symlink(self):
        app = self.root / "app" / APP_NAME
        self.file(app / f"bin/{APP_NAME}").chmod(0o755)
        runtime = app / "lib/runtime/lib/server/jvm-actual.so"
        self.file(runtime)
        (runtime.parent / "libjvm.so").symlink_to(runtime.name)
        self.file(self.root / "deb/versioned-package.deb")
        stage("linux-x64", "deb", self.root, self.output)
        with tarfile.open(self.output / portable_name("linux-x64")) as archive:
            launcher = archive.getmember(f"{APP_NAME}/bin/{APP_NAME}")
            self.assertTrue(launcher.mode & stat.S_IXUSR)
            link = archive.getmember(f"{APP_NAME}/lib/runtime/lib/server/libjvm.so")
            self.assertTrue(link.issym())
            self.assertEqual(link.linkname, runtime.name)

    def test_installer_alone_cannot_be_published_as_portable(self):
        self.file(self.root / "msi/versioned-package.msi")
        self.file(self.root / "app" / APP_NAME / f"{APP_NAME}.exe")
        with self.assertRaisesRegex(RuntimeError, "Incomplete portable app image"):
            stage("windows-x64", "msi", self.root, self.output)
        self.assertFalse(self.output.exists())

    def test_download_names_are_unique_and_start_with_platform(self):
        self.assertEqual(len(ASSETS), 9)
        self.assertEqual(len(set(ASSETS)), len(ASSETS))
        for name in ASSETS:
            self.assertRegex(name, r"^(windows|macos|linux|web)-")
            self.assertIn(APP_NAME, name)


if __name__ == "__main__":
    unittest.main()
