"""Tests for the deterministic agent-configuration validator."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.check_agent_config import validate_repository


class AgentConfigValidatorTest(unittest.TestCase):
    """Checks accepted agent configuration and each guarded failure boundary."""

    def setUp(self) -> None:
        """Create a minimal repository with one valid routed skill."""

        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self._write(
            ".agents/skills/alpha/SKILL.md",
            """\
---
name: alpha
description: >-
  Use for alpha tasks.
---

# Alpha
""",
        )
        self._write(
            "AGENTS.md",
            """\
# Agent instructions

## Skill routing

- [`alpha`](.agents/skills/alpha/SKILL.md)

## Reporting

Report the result.
""",
        )
        self._write(
            "PROJECT.md",
            """\
# Project

Read the [skill routing](AGENTS.md#skill-routing).
""",
        )
        self._write(
            "CLAUDE.md",
            """\
@AGENTS.md

# Claude-specific routing

Load skills directly from `.agents/skills/`.
""",
        )
        self._write(
            ".github/copilot-instructions.md",
            """\
# GitHub Copilot instructions

- Read [agent instructions](../AGENTS.md).
- Read [project context](../PROJECT.md).
- Load every matching [repository skill](../.agents/skills/) through the `AGENTS.md` routing map.
""",
        )

    def tearDown(self) -> None:
        """Remove the temporary repository."""

        self.temporary_directory.cleanup()

    def _write(self, relative_path: str, content: str) -> None:
        """Write one UTF-8 fixture below the temporary repository root."""

        path = self.root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def _messages(self) -> list[str]:
        """Return rendered diagnostics for the current fixture."""

        return [
            diagnostic.render()
            for diagnostic in validate_repository(self.root)
        ]

    def test_accepts_a_complete_configuration(self) -> None:
        """Accept matching frontmatter, routing, entry points, and links."""

        self.assertEqual([], self._messages())

    def test_reports_missing_required_paths(self) -> None:
        """Reject absent agent entry points and the canonical skills directory."""

        for relative_path in (
            "AGENTS.md",
            "PROJECT.md",
            "CLAUDE.md",
            ".github/copilot-instructions.md",
        ):
            with self.subTest(relative_path=relative_path):
                path = self.root / relative_path
                content = path.read_text(encoding="utf-8")
                path.unlink()
                try:
                    self.assertIn(
                        f"{relative_path}:1: required file is missing",
                        self._messages(),
                    )
                finally:
                    self._write(relative_path, content)

        skills_directory = self.root / ".agents/skills"
        unavailable_directory = self.root / ".agents/unavailable-skills"
        skills_directory.rename(unavailable_directory)
        try:
            self.assertIn(
                ".agents/skills:1: required directory is missing",
                self._messages(),
            )
        finally:
            unavailable_directory.rename(skills_directory)

    def test_reports_orphaned_and_unknown_skill_routes(self) -> None:
        """Reject skills absent from the index and routes without directories."""

        self._write(
            ".agents/skills/beta/SKILL.md",
            """\
---
name: beta
description: >-
  Use for beta tasks.
---

# Beta
""",
        )
        agents = (self.root / "AGENTS.md").read_text(encoding="utf-8")
        agents = agents.replace(
            "- [`alpha`](.agents/skills/alpha/SKILL.md)",
            "\n".join(
                (
                    "- [`alpha`](.agents/skills/alpha/SKILL.md)",
                    "- [`gamma`](.agents/skills/gamma/SKILL.md)",
                )
            ),
        )
        self._write("AGENTS.md", agents)

        messages = self._messages()

        self.assertTrue(
            any("skill `beta` exists but is missing" in message for message in messages)
        )
        self.assertTrue(
            any("routing index refers to unknown skill `gamma`" in message for message in messages)
        )

    def test_reports_duplicate_and_mislabeled_skill_routes(self) -> None:
        """Reject duplicate route targets and labels that disagree with directories."""

        agents = (self.root / "AGENTS.md").read_text(encoding="utf-8")
        agents = agents.replace(
            "- [`alpha`](.agents/skills/alpha/SKILL.md)",
            "\n".join(
                (
                    "- [`renamed`](.agents/skills/alpha/SKILL.md)",
                    "- [`alpha`](.agents/skills/alpha/SKILL.md)",
                )
            ),
        )
        self._write("AGENTS.md", agents)

        messages = self._messages()

        self.assertTrue(
            any("route label `renamed` must equal directory `alpha`" in message
                for message in messages)
        )
        self.assertTrue(
            any("skill `alpha` is routed more than once" in message for message in messages)
        )

    def test_reports_invalid_frontmatter_and_entry_points(self) -> None:
        """Reject a mismatched skill name and incomplete Claude or Copilot routes."""

        skill = (self.root / ".agents/skills/alpha/SKILL.md").read_text(encoding="utf-8")
        self._write(
            ".agents/skills/alpha/SKILL.md",
            skill.replace(
                "name: alpha",
                "name: renamed\nname: duplicate\nnot a frontmatter entry",
            ),
        )
        self._write(
            "CLAUDE.md",
            """\
# Claude-specific routing

Read only this file.
""",
        )
        self._write(
            ".github/copilot-instructions.md",
            """\
# GitHub Copilot instructions

- Read [agent instructions](../AGENTS.md).
- Read [project context](../PROJECT.md).
""",
        )

        messages = self._messages()

        self.assertTrue(
            any("frontmatter name `renamed` must equal directory `alpha`" in message
                for message in messages)
        )
        self.assertTrue(
            any("frontmatter key `name` is duplicated" in message for message in messages)
        )
        self.assertTrue(
            any("frontmatter contains an invalid entry" in message for message in messages)
        )
        self.assertTrue(
            any("Claude route must include `@AGENTS.md`" in message for message in messages)
        )
        self.assertTrue(
            any("Claude route must use the canonical" in message for message in messages)
        )
        self.assertTrue(
            any("Copilot route must link to `.agents/skills/`" in message
                for message in messages)
        )

    def test_reports_unsupported_and_misordered_frontmatter(self) -> None:
        """Reject unsupported keys and enforce the canonical key order."""

        skill_file = self.root / ".agents/skills/alpha/SKILL.md"
        valid_skill = skill_file.read_text(encoding="utf-8")

        self._write(
            ".agents/skills/alpha/SKILL.md",
            valid_skill.replace(
                "  Use for alpha tasks.\n---",
                "  Use for alpha tasks.\nowner: platform\n---",
            ),
        )
        self.assertTrue(
            any(
                "frontmatter key `owner` is not supported" in message
                for message in self._messages()
            )
        )

        self._write(
            ".agents/skills/alpha/SKILL.md",
            """\
---
description: >-
  Use for alpha tasks.
name: alpha
---

# Alpha
""",
        )
        messages = self._messages()
        self.assertTrue(
            any("frontmatter name must be the first entry" in message for message in messages)
        )
        self.assertTrue(
            any(
                "frontmatter description must immediately follow the name" in message
                for message in messages
            )
        )

    def test_reports_missing_empty_and_malformed_descriptions(self) -> None:
        """Reject every description form outside the supported non-empty folded scalar."""

        skill_file = self.root / ".agents/skills/alpha/SKILL.md"
        valid_skill = skill_file.read_text(encoding="utf-8")
        valid_description = "description: >-\n  Use for alpha tasks.\n"
        cases = (
            ("", "frontmatter is missing `description`"),
            ("description: >-\n", "frontmatter description must not be empty"),
            (
                'description: "unterminated\n  Use for alpha tasks.\n',
                "frontmatter description must use the folded scalar `>-`",
            ),
            (
                "description: >-\n\tUse for alpha tasks.\n",
                "frontmatter indentation must use spaces",
            ),
            (
                "description: >-\n  First line.\n Second line.\n",
                "frontmatter description lines must use two-space indentation",
            ),
        )

        for replacement, expected_message in cases:
            with self.subTest(replacement=replacement):
                self._write(
                    ".agents/skills/alpha/SKILL.md",
                    valid_skill.replace(valid_description, replacement),
                )

                self.assertTrue(
                    any(expected_message in message for message in self._messages())
                )

    def test_reports_broken_links_anchors_and_markdown_formatting(self) -> None:
        """Reject unresolved destinations, absent anchors, and malformed prose lines."""

        self._write(
            "PROJECT.md",
            "\n".join(
                (
                    "# Project",
                    "",
                    "Read [missing](missing.md).",
                    "Read the [missing section](AGENTS.md#missing-section).",
                    "Read [outside](../outside.md).",
                    "Trailing whitespace. ",
                    "x" * 101,
                    "",
                )
            ),
        )

        messages = self._messages()

        self.assertTrue(
            any("relative link target does not exist" in message for message in messages)
        )
        self.assertTrue(
            any("Markdown anchor does not exist" in message for message in messages)
        )
        self.assertTrue(
            any("relative link escapes the repository" in message for message in messages)
        )
        self.assertTrue(any("line has trailing whitespace" in message for message in messages))
        self.assertTrue(any("line exceeds 100 characters" in message for message in messages))

    def test_validates_claude_command_markdown(self) -> None:
        """Apply link and formatting checks to Claude slash commands."""

        self._write(
            ".claude/commands/proofread.md",
            "\n".join(
                (
                    "# Proofread",
                    "",
                    "Read [missing](missing.md).",
                    "Read the [missing section](#missing-section).",
                    "x" * 101,
                    "",
                )
            ),
        )

        messages = self._messages()

        self.assertTrue(
            any(
                ".claude/commands/proofread.md:3: relative link target does not exist"
                in message
                for message in messages
            )
        )
        self.assertTrue(
            any(
                ".claude/commands/proofread.md:4: Markdown anchor does not exist"
                in message
                for message in messages
            )
        )
        self.assertTrue(
            any(
                ".claude/commands/proofread.md:5: line exceeds 100 characters"
                in message
                for message in messages
            )
        )

    def test_accepts_colliding_duplicate_heading_anchors(self) -> None:
        """Match GitHub suffixes when a heading collides with a generated anchor."""

        self._write(
            "PROJECT.md",
            """\
# Project

## Topic

## Topic

## Topic-1

Read the [third topic](#topic-1-1).
""",
        )

        self.assertEqual([], self._messages())

    def test_ignores_links_and_long_lines_inside_fenced_code(self) -> None:
        """Allow exact machine text inside fenced Markdown while checking prose."""

        project = (self.root / "PROJECT.md").read_text(encoding="utf-8")
        project += (
            "\n`[inline missing](inline-missing.md)`\n"
            "```text\n"
            + "[missing](missing.md)"
            + "x" * 120
            + "\n```\n"
        )
        self._write("PROJECT.md", project)

        self.assertEqual([], self._messages())


if __name__ == "__main__":
    unittest.main()
