@AGENTS.md

# Claude-specific routing

- Read `PROJECT.md` for the project map, runtime flow, compatibility constraints, test
  strategy, and trust boundaries.
- Load every matching skill through `.claude/skills/`, which routes to the canonical
  `.agents/skills/` directory; keep skill bodies agent-neutral.
- Treat `AGENTS.md` as the repository-wide policy owner. Do not duplicate its rules here.
- Do not commit, push, publish, or mutate review-thread state unless the user's current request
  explicitly asks for that action.
