@AGENTS.md

# Claude-specific routing

- Read `PROJECT.md` for architecture, compatibility, tests, and trust boundaries.
- Load every matching skill through `.claude/skills/`, which routes to the canonical
  `.agents/skills/` directory; keep skill bodies agent-neutral.
- Treat `AGENTS.md` as the repository-wide policy owner. Do not duplicate its rules here.
