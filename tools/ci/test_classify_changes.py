import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from classify_changes import LIGHTWEIGHT_DOCS, classify, docs_only


def entry(path, status="M", old="100644", new="100644"):
    return f":{old} {new} {'a' * 40} {'b' * 40} {status}\0{path}\0".encode()


class ChangeClassificationTest(unittest.TestCase):
    def test_allowlisted_prose(self):
        self.assertTrue(docs_only(b"".join(entry(path) for path in LIGHTWEIGHT_DOCS)))

    def test_relevant_and_unknown_paths(self):
        for path in ["README.md", "CONTRIBUTING.md", "AGENTS.md", "ARCHITECTURE.md",
                     "RELEASE_CHECKLIST.md", "docs/HNS_DOUBLES_AUTHORITY.md",
                     "docs/QUICKJS_CALCULATOR_TESTS.md", "native/README.md",
                     "tools/hns-runtime-probe/evidence/README.md", "tools/ci/classify_changes.py",
                     "ci.sh", "app/src/main/a.kt", "app/src/test/a.kt", "native/tests/a.c",
                     "app/src/main/assets/profiles/a.json", "build.gradle.kts",
                     "gradle/libs.versions.toml", ".github/workflows/ci.yml", "new.md",
                     "docs/CI.md\nunknown", "docs/../docs/CI.md"]:
            with self.subTest(path=path):
                self.assertFalse(docs_only(entry(path)))
                self.assertFalse(docs_only(entry("docs/CI.md") + entry(path)))

    def test_empty_malformed_and_nonordinary_changes(self):
        for raw in [b"", b"garbage\0", entry("docs/CI.md")[:-1],
                    entry("docs/CI.md", "A"), entry("docs/CI.md", "D"),
                    entry("docs/CI.md", "T", new="120000"),
                    entry("docs/CI.md", new="100755"),
                    entry("docs/CI.md", "R100") + b"other.md\0"]:
            with self.subTest(raw=raw):
                self.assertFalse(docs_only(raw))

    def test_events_and_missing_metadata_default_full(self):
        for event, base, head in [("push", "a" * 40, "b" * 40),
                                  ("merge_group", "a" * 40, "b" * 40),
                                  ("pull_request", "", "b" * 40),
                                  ("pull_request", "--help", "b" * 40)]:
            self.assertTrue(classify(event, base, head))

    def test_git_error_fails_job(self):
        with patch("classify_changes.subprocess.check_output", side_effect=subprocess.CalledProcessError(1, "git")):
            with self.assertRaises(subprocess.CalledProcessError):
                classify("pull_request", "a" * 40, "b" * 40)

    def test_real_git_diff_and_rename(self):
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.check_output(["git", "-C", directory, *args])
            git("init", "-q")
            git("config", "user.name", "CI Test")
            git("config", "user.email", "ci@example.invalid")
            path = Path(directory, "UI_DESIGN_AUDIT.md")
            path.write_text("before\n")
            git("add", ".")
            git("commit", "-qm", "base")
            path.write_text("after\n")
            self.assertTrue(docs_only(git("diff", "--raw", "-z", "--no-renames", "HEAD")))
            path.rename(Path(directory, "unknown.md"))
            git("add", "-A")
            self.assertFalse(docs_only(git("diff", "--raw", "-z", "--no-renames", "HEAD")))


if __name__ == "__main__":
    unittest.main()
