#!/usr/bin/env python3
"""Validate agent skills, routes, links, anchors, and Markdown formatting."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote, urlsplit


MAX_MARKDOWN_LINE_LENGTH = 100
SKILLS_DIRECTORY = Path(".agents/skills")
CLAUDE_COMMANDS_DIRECTORY = Path(".claude/commands")
AGENT_DOCUMENTS = (
    Path("README.md"),
    Path("AGENTS.md"),
    Path("PROJECT.md"),
    Path("CLAUDE.md"),
    Path(".github/copilot-instructions.md"),
)
SKILL_ROUTE_PATTERN = re.compile(
    r"^\s*-\s+\[`(?P<label>[^`]+)`\]"
    r"\(\.agents/skills/(?P<directory>[^/]+)/SKILL\.md\)\s*$"
)
INLINE_LINK_PATTERN = re.compile(r"!?\[[^\]]*]\((?P<destination>[^)]+)\)")
REFERENCE_LINK_PATTERN = re.compile(r"^\s*\[[^\]]+]:\s*(?P<destination>\S+)")
INLINE_CODE_PATTERN = re.compile(r"`+[^`]*`+")
HEADING_PATTERN = re.compile(r"^(?P<level>#{1,6})\s+(?P<title>.+?)\s*#*\s*$")
FENCE_PATTERN = re.compile(r"^\s*(?P<fence>`{3,}|~{3,})")
FRONTMATTER_KEY_PATTERN = re.compile(
    r"^(?P<key>[A-Za-z][A-Za-z0-9_-]*):(?:\s*(?P<value>.*))?$"
)
SKILL_NAME_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
SUPPORTED_FRONTMATTER_KEYS = frozenset({"name", "description"})


@dataclass(frozen=True, order=True)
class Diagnostic:
    """Describes one validation failure at a repository-relative location."""

    path: str
    line: int
    message: str

    def render(self) -> str:
        """Render the diagnostic in a format recognized by editors and CI logs."""

        return f"{self.path}:{self.line}: {self.message}"


def _relative_path(path: Path, root: Path) -> str:
    """Return a stable POSIX path relative to the repository root."""

    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def _read_lines(path: Path) -> list[str]:
    """Read a UTF-8 text file while preserving whitespace before line endings."""

    return path.read_text(encoding="utf-8").splitlines()


def _markdown_files(root: Path) -> list[Path]:
    """Return every Markdown file governed by the agent-writing policy."""

    files = [root / relative for relative in AGENT_DOCUMENTS]
    agents_directory = root / ".agents"
    if agents_directory.is_dir():
        files.extend(agents_directory.rglob("*.md"))
    claude_commands_directory = root / CLAUDE_COMMANDS_DIRECTORY
    if claude_commands_directory.is_dir():
        files.extend(claude_commands_directory.rglob("*.md"))
    return sorted(set(files))


def _validate_required_paths(root: Path) -> list[Diagnostic]:
    """Check that every required routing document and the skills directory exists."""

    diagnostics: list[Diagnostic] = []
    for relative in AGENT_DOCUMENTS:
        path = root / relative
        if not path.is_file():
            diagnostics.append(Diagnostic(relative.as_posix(), 1, "required file is missing"))

    skills_directory = root / SKILLS_DIRECTORY
    if not skills_directory.is_dir():
        diagnostics.append(
            Diagnostic(SKILLS_DIRECTORY.as_posix(), 1, "required directory is missing")
        )
    return diagnostics


def _frontmatter_fields(
    lines: list[str],
) -> tuple[dict[str, tuple[str, int]], int | None, list[tuple[int, str]]]:
    """Parse the scalar keys from a skill's constrained YAML frontmatter."""

    if not lines or lines[0] != "---":
        return {}, None, []

    try:
        closing_line = lines.index("---", 1)
    except ValueError:
        return {}, None, []

    fields: dict[str, tuple[str, int]] = {}
    issues: list[tuple[int, str]] = []
    for index, line in enumerate(lines[1:closing_line], start=2):
        indentation = line[: len(line) - len(line.lstrip(" \t"))]
        if "\t" in indentation:
            issues.append((index, "frontmatter indentation must use spaces"))
            continue
        match = FRONTMATTER_KEY_PATTERN.match(line)
        if match is None:
            if line and not line[0].isspace():
                issues.append((index, "frontmatter contains an invalid entry"))
            continue
        key = match.group("key")
        value = (match.group("value") or "").strip()
        if key in fields:
            issues.append((index, f"frontmatter key `{key}` is duplicated"))
            continue
        fields[key] = (value, index)
    return fields, closing_line + 1, issues


def _folded_description(
    lines: list[str],
    field_line: int,
    closing_line: int,
) -> tuple[str, list[tuple[int, str]]]:
    """Resolve and validate the canonical two-space-indented description body."""

    content: list[str] = []
    issues: list[tuple[int, str]] = []
    body_lines = lines[field_line : closing_line - 1]
    for line_number, line in enumerate(body_lines, start=field_line + 1):
        if not line:
            continue
        indentation = line[: len(line) - len(line.lstrip(" \t"))]
        if indentation != "  ":
            issues.append(
                (
                    line_number,
                    "frontmatter description lines must use two-space indentation",
                )
            )
            continue
        if line.strip():
            content.append(line.strip())
    return "\n".join(content).strip(), issues


def _skill_directories(root: Path) -> list[Path]:
    """Return immediate, non-hidden directories under the canonical skills root."""

    skills_directory = root / SKILLS_DIRECTORY
    if not skills_directory.is_dir():
        return []
    return sorted(
        path
        for path in skills_directory.iterdir()
        if path.is_dir() and not path.name.startswith(".")
    )


def _validate_skill_frontmatter(root: Path) -> tuple[set[str], list[Diagnostic]]:
    """Validate each skill directory and return the discovered skill names."""

    skill_names: set[str] = set()
    diagnostics: list[Diagnostic] = []

    for directory in _skill_directories(root):
        skill_names.add(directory.name)
        skill_file = directory / "SKILL.md"
        relative = _relative_path(skill_file, root)
        if not skill_file.is_file():
            diagnostics.append(Diagnostic(relative, 1, "skill directory has no SKILL.md"))
            continue

        lines = _read_lines(skill_file)
        fields, closing_line, frontmatter_issues = _frontmatter_fields(lines)
        if closing_line is None:
            diagnostics.append(
                Diagnostic(relative, 1, "frontmatter must start and end with `---`")
            )
            continue
        diagnostics.extend(
            Diagnostic(relative, line, message)
            for line, message in frontmatter_issues
        )
        diagnostics.extend(
            Diagnostic(
                relative,
                line,
                f"frontmatter key `{key}` is not supported",
            )
            for key, (_, line) in fields.items()
            if key not in SUPPORTED_FRONTMATTER_KEYS
        )

        name_field = fields.get("name")
        if name_field is None or not name_field[0]:
            diagnostics.append(Diagnostic(relative, 2, "frontmatter is missing `name`"))
        else:
            name, line = name_field
            if line != 2:
                diagnostics.append(
                    Diagnostic(
                        relative,
                        line,
                        "frontmatter name must be the first entry",
                    )
                )
            if SKILL_NAME_PATTERN.fullmatch(name) is None:
                diagnostics.append(
                    Diagnostic(
                        relative,
                        line,
                        "frontmatter name must be an unquoted lowercase hyphenated value",
                    )
                )
            elif name != directory.name:
                diagnostics.append(
                    Diagnostic(
                        relative,
                        line,
                        f"frontmatter name `{name}` must equal directory `{directory.name}`",
                    )
                )

        description_field = fields.get("description")
        if description_field is None:
            diagnostics.append(
                Diagnostic(relative, 2, "frontmatter is missing `description`")
            )
        else:
            description_style, line = description_field
            if line != 3:
                diagnostics.append(
                    Diagnostic(
                        relative,
                        line,
                        "frontmatter description must immediately follow the name",
                    )
                )
            if description_style != ">-":
                diagnostics.append(
                    Diagnostic(
                        relative,
                        line,
                        "frontmatter description must use the folded scalar `>-`",
                    )
                )
            else:
                description, description_issues = _folded_description(
                    lines,
                    line,
                    closing_line,
                )
                diagnostics.extend(
                    Diagnostic(relative, issue_line, message)
                    for issue_line, message in description_issues
                )
                if not description:
                    diagnostics.append(
                        Diagnostic(
                            relative,
                            line,
                            "frontmatter description must not be empty",
                        )
                    )

    if not skill_names and (root / SKILLS_DIRECTORY).is_dir():
        diagnostics.append(
            Diagnostic(SKILLS_DIRECTORY.as_posix(), 1, "no skill directories were found")
        )
    return skill_names, diagnostics


def _skill_routing_section(lines: list[str]) -> tuple[int, list[tuple[int, str]]]:
    """Return the `AGENTS.md` skill-routing heading and its content lines."""

    heading_line = 0
    for index, line in enumerate(lines, start=1):
        if line == "## Skill routing":
            heading_line = index
            break
    if heading_line == 0:
        return 0, []

    section: list[tuple[int, str]] = []
    for index, line in enumerate(lines[heading_line:], start=heading_line + 1):
        if line.startswith("## "):
            break
        section.append((index, line))
    return heading_line, section


def _validate_skill_routes(root: Path, skill_names: set[str]) -> list[Diagnostic]:
    """Require `AGENTS.md` to route every discovered skill exactly once."""

    agents_file = root / "AGENTS.md"
    if not agents_file.is_file():
        return []

    lines = _read_lines(agents_file)
    heading_line, section = _skill_routing_section(lines)
    if heading_line == 0:
        return [Diagnostic("AGENTS.md", 1, "`## Skill routing` section is missing")]

    routes: dict[str, int] = {}
    diagnostics: list[Diagnostic] = []
    for line_number, line in section:
        match = SKILL_ROUTE_PATTERN.match(line)
        if match is None:
            continue
        label = match.group("label")
        directory = match.group("directory")
        if label != directory:
            diagnostics.append(
                Diagnostic(
                    "AGENTS.md",
                    line_number,
                    f"route label `{label}` must equal directory `{directory}`",
                )
            )
        if directory in routes:
            diagnostics.append(
                Diagnostic(
                    "AGENTS.md",
                    line_number,
                    f"skill `{directory}` is routed more than once",
                )
            )
        else:
            routes[directory] = line_number

    routed_names = set(routes)
    missing_routes = sorted(skill_names - routed_names)
    unknown_routes = sorted(routed_names - skill_names)
    for name in missing_routes:
        diagnostics.append(
            Diagnostic(
                "AGENTS.md",
                heading_line,
                f"skill `{name}` exists but is missing from the routing index",
            )
        )
    for name in unknown_routes:
        diagnostics.append(
            Diagnostic(
                "AGENTS.md",
                routes[name],
                f"routing index refers to unknown skill `{name}`",
            )
        )
    return diagnostics


def _fence_marker(line: str) -> str | None:
    """Return a Markdown fence marker when the line opens or closes a code block."""

    match = FENCE_PATTERN.match(line)
    if match is None:
        return None
    return match.group("fence")


def _line_is_fence(line: str, open_fence: str | None) -> tuple[bool, str | None]:
    """Track fenced Markdown blocks and identify their delimiter lines."""

    marker = _fence_marker(line)
    if marker is None:
        return False, open_fence
    if open_fence is None:
        return True, marker
    if marker[0] == open_fence[0] and len(marker) >= len(open_fence):
        return True, None
    return False, open_fence


def _validate_markdown_format(root: Path, files: list[Path]) -> list[Diagnostic]:
    """Check trailing whitespace and prose line length in governed Markdown."""

    diagnostics: list[Diagnostic] = []
    for path in files:
        if not path.is_file():
            continue
        relative = _relative_path(path, root)
        open_fence: str | None = None
        for line_number, line in enumerate(_read_lines(path), start=1):
            is_fence, next_fence = _line_is_fence(line, open_fence)
            is_machine_read_line = (
                REFERENCE_LINK_PATTERN.match(line) is not None
                or line.lstrip().startswith("[![")
            )
            if line.endswith((" ", "\t")):
                diagnostics.append(
                    Diagnostic(relative, line_number, "line has trailing whitespace")
                )
            if (
                open_fence is None
                and not is_fence
                and not is_machine_read_line
                and len(line.expandtabs(4)) > MAX_MARKDOWN_LINE_LENGTH
            ):
                diagnostics.append(
                    Diagnostic(
                        relative,
                        line_number,
                        "line exceeds "
                        f"{MAX_MARKDOWN_LINE_LENGTH} characters "
                        f"({len(line.expandtabs(4))})",
                    )
                )
            open_fence = next_fence
    return diagnostics


def _link_destination(raw_destination: str) -> str:
    """Remove optional Markdown title text and angle brackets from a link target."""

    destination = raw_destination.strip()
    if destination.startswith("<"):
        closing = destination.find(">")
        if closing >= 0:
            return destination[1:closing]
    return destination.split(maxsplit=1)[0]


def _markdown_destinations(lines: list[str]) -> list[tuple[int, str]]:
    """Extract inline and reference-style link targets outside fenced code."""

    destinations: list[tuple[int, str]] = []
    open_fence: str | None = None
    for line_number, line in enumerate(lines, start=1):
        is_fence, next_fence = _line_is_fence(line, open_fence)
        if open_fence is None and not is_fence:
            searchable_line = INLINE_CODE_PATTERN.sub("", line)
            destinations.extend(
                (line_number, _link_destination(match.group("destination")))
                for match in INLINE_LINK_PATTERN.finditer(searchable_line)
            )
            reference_match = REFERENCE_LINK_PATTERN.match(searchable_line)
            if reference_match is not None:
                destinations.append(
                    (
                        line_number,
                        _link_destination(reference_match.group("destination")),
                    )
                )
        open_fence = next_fence
    return destinations


def _github_heading_slug(title: str) -> str:
    """Approximate GitHub's stable heading identifier for repository documentation."""

    title = re.sub(r"!\[([^\]]*)]\([^)]+\)", r"\1", title)
    title = re.sub(r"\[([^\]]+)]\([^)]+\)", r"\1", title)
    title = re.sub(r"`([^`]*)`", r"\1", title)
    title = re.sub(r"<[^>]+>", "", title)
    title = title.strip().lower()
    title = "".join(
        character
        for character in title
        if character.isalnum() or character in {" ", "-", "_"}
    )
    return re.sub(r"\s", "-", title)


def _markdown_anchors(path: Path) -> set[str]:
    """Collect GitHub-style heading anchors from one Markdown file."""

    anchors: set[str] = set()
    open_fence: str | None = None
    for line in _read_lines(path):
        is_fence, next_fence = _line_is_fence(line, open_fence)
        if open_fence is None and not is_fence:
            match = HEADING_PATTERN.match(line)
            if match is not None:
                base = _github_heading_slug(match.group("title"))
                duplicate = 0
                anchor = base
                while anchor in anchors:
                    duplicate += 1
                    anchor = f"{base}-{duplicate}"
                anchors.add(anchor)
        open_fence = next_fence
    return anchors


def _is_external_destination(destination: str) -> bool:
    """Return whether a Markdown destination is outside the repository."""

    parsed = urlsplit(destination)
    return bool(parsed.scheme or parsed.netloc or destination.startswith("/"))


def _validate_markdown_links(root: Path, files: list[Path]) -> list[Diagnostic]:
    """Require every relative Markdown path and heading anchor to resolve."""

    diagnostics: list[Diagnostic] = []
    resolved_root = root.resolve()
    anchor_cache: dict[Path, set[str]] = {}

    for source in files:
        if not source.is_file():
            continue
        relative_source = _relative_path(source, root)
        for line_number, destination in _markdown_destinations(_read_lines(source)):
            if not destination or _is_external_destination(destination):
                continue

            parsed = urlsplit(destination)
            relative_target = unquote(parsed.path)
            fragment = unquote(parsed.fragment)
            target = source if not relative_target else source.parent / relative_target
            resolved_target = target.resolve()
            try:
                resolved_target.relative_to(resolved_root)
            except ValueError:
                diagnostics.append(
                    Diagnostic(
                        relative_source,
                        line_number,
                        f"relative link escapes the repository: `{destination}`",
                    )
                )
                continue

            if not resolved_target.exists():
                diagnostics.append(
                    Diagnostic(
                        relative_source,
                        line_number,
                        f"relative link target does not exist: `{destination}`",
                    )
                )
                continue

            if not fragment:
                continue
            if not resolved_target.is_file() or resolved_target.suffix.lower() != ".md":
                diagnostics.append(
                    Diagnostic(
                        relative_source,
                        line_number,
                        f"anchor target is not a Markdown file: `{destination}`",
                    )
                )
                continue

            if resolved_target not in anchor_cache:
                anchor_cache[resolved_target] = _markdown_anchors(resolved_target)
            if fragment not in anchor_cache[resolved_target]:
                diagnostics.append(
                    Diagnostic(
                        relative_source,
                        line_number,
                        f"Markdown anchor does not exist: `{destination}`",
                    )
                )
    return diagnostics


def _resolved_link_targets(path: Path) -> set[Path]:
    """Resolve every repository-local Markdown destination in one file."""

    targets: set[Path] = set()
    for _, destination in _markdown_destinations(_read_lines(path)):
        if not destination or _is_external_destination(destination):
            continue
        parsed = urlsplit(destination)
        relative_target = unquote(parsed.path)
        if relative_target:
            targets.add((path.parent / relative_target).resolve())
    return targets


def _validate_agent_entry_points(root: Path) -> list[Diagnostic]:
    """Require Claude and Copilot to route through the canonical skill directory."""

    diagnostics: list[Diagnostic] = []
    claude_file = root / "CLAUDE.md"
    if claude_file.is_file():
        text = claude_file.read_text(encoding="utf-8")
        if "@AGENTS.md" not in text:
            diagnostics.append(
                Diagnostic("CLAUDE.md", 1, "Claude route must include `@AGENTS.md`")
            )
        if ".agents/skills/" not in text:
            diagnostics.append(
                Diagnostic(
                    "CLAUDE.md",
                    1,
                    "Claude route must use the canonical `.agents/skills/` directory",
                )
            )

    copilot_file = root / ".github/copilot-instructions.md"
    if copilot_file.is_file():
        copilot_lines = _read_lines(copilot_file)
        targets = _resolved_link_targets(copilot_file)
        required_targets = {
            (root / "AGENTS.md").resolve(): "AGENTS.md",
            (root / "PROJECT.md").resolve(): "PROJECT.md",
            (root / SKILLS_DIRECTORY).resolve(): ".agents/skills/",
        }
        for target, label in required_targets.items():
            if target not in targets:
                diagnostics.append(
                    Diagnostic(
                        ".github/copilot-instructions.md",
                        1,
                        f"Copilot route must link to `{label}`",
                    )
                )
        skill_route_lines = [
            line
            for line in copilot_lines
            if "../.agents/skills/" in line
        ]
        if not any("AGENTS.md" in line and "routing" in line for line in skill_route_lines):
            diagnostics.append(
                Diagnostic(
                    ".github/copilot-instructions.md",
                    1,
                    "Copilot must load skills through the `AGENTS.md` routing map",
                )
            )
    return diagnostics


def validate_repository(root: Path) -> list[Diagnostic]:
    """Validate the complete agent configuration below a repository root."""

    root = root.resolve()
    diagnostics = _validate_required_paths(root)
    skill_names, frontmatter_diagnostics = _validate_skill_frontmatter(root)
    diagnostics.extend(frontmatter_diagnostics)
    diagnostics.extend(_validate_skill_routes(root, skill_names))
    diagnostics.extend(_validate_agent_entry_points(root))

    markdown_files = _markdown_files(root)
    diagnostics.extend(_validate_markdown_format(root, markdown_files))
    diagnostics.extend(_validate_markdown_links(root, markdown_files))
    return sorted(set(diagnostics))


def _parse_arguments() -> argparse.Namespace:
    """Parse command-line arguments."""

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="repository root to validate",
    )
    return parser.parse_args()


def main() -> int:
    """Run validation and return a process-compatible exit code."""

    arguments = _parse_arguments()
    diagnostics = validate_repository(arguments.root)
    if diagnostics:
        print("Agent configuration validation failed:", file=sys.stderr)
        for diagnostic in diagnostics:
            print(diagnostic.render(), file=sys.stderr)
        print(
            "\nRun `python3 scripts/check_agent_config.py` locally after fixing the errors.",
            file=sys.stderr,
        )
        return 1

    print("Agent configuration is valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
