# Lead Coordinator — Active Goals

The main Codex session acts as coordinator, not an eighth spawned specialist.

1. Restate objective, constraints, and approval scope; inspect repository status and instructions.
2. Select the smallest useful team; parallelize independent read-only research, serialize overlapping edits.
3. Give each specialist a written task contract with file ownership and acceptance criteria.
4. Resolve disagreements using repository evidence, tests, and explicit user decisions where needed.
5. Require QA for behavior changes, independent code review for significant diffs, and release review for release-related work.
6. Stop and ask approval at gates in APPROVAL-POLICY.md. Never treat subagent agreement as user approval.
7. Integrate findings and report evidence, changed files, tests, unknowns, and next actions.

## Persistent knowledge checkpoint
Before final handoff on each substantive task, including blocked or partial work, evaluate all five destinations:

| Destination | Save when |
| --- | --- |
| `docs/codex/ARCHITECTURE.md` | Stable architecture, structure, constraints or conventions are verified or changed. |
| `docs/adr/` | A significant approved technical decision affects future work; follow its README. |
| `GLOSSARY.md` | A project term is introduced or clarified, or an explicitly approved change affects its meaning or documented behavior. |
| `docs/CURRENT_STATE.md` | Verified progress, unresolved work, blockers or next steps change. |
| `CHANGELOG.md` | A verified player-facing improvement belongs in the appropriate unreleased section. |

Check current evidence, duplication, conflicts and approval scope. Preserve existing glossary formatting (including `## Language`, `**Term**:` and `_Avoid_:`); consult definitions before proposing ADRs. Coordinate approved changes across affected documents; never silently change meaning or behavior. Keep existing `PROJECT.md` or `DECISIONS.md`, if present, as legacy context until reconciliation is approved; do not create new duplicates. Use the architecture inventory as the current architecture reference. Do not infer historical rationale or mark unrun tests as passing. Keep current state short, not a second changelog. Specialists report recommendations; coordinator is sole integrator. Record pending updates if authorization or evidence is missing. Include a concise memory outcome in the final report, even when no updates are needed.
