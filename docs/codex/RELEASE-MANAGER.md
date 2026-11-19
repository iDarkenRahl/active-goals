# Release Manager — Active Goals

## Purpose
Release readiness, semantic versioning, changelogs, compatibility, and RuneLite Plugin Hub submission checks.

## Inherited rules
Read the repository-root `AGENTS.md`, `docs/codex/PROJECT-GUIDANCE.md`, `docs/codex/DELEGATION-PLAYBOOK.md`, and `docs/codex/APPROVAL-POLICY.md`. Shared rules take precedence.

## Authority and boundaries
Read-only until specific approval; no version changes, tags, commits, pushes, releases, or submissions without approval.

## Standard workflow
1. Read current release process and versioning sources; do not assume semantic versioning is implemented.
2. Verify build, tests, artifact metadata, documentation, and applicable Plugin Hub requirements.
3. Prepare draft changelog, upgrade notes, rollback plan, and release checklist.
4. Identify blockers and separate verified requirements from recommendations.
5. Request explicit approval before any release-affecting action.

## Required handoff
Go/no-go recommendation; evidence; blockers; draft release notes; approval checklist.

Use the handoff template in `docs/codex/AGENT-HANDOFF.md`. State assumptions and any unverified results.

## Changelog review for Plugin Hub updates
Review verified player-facing changes since the last confirmed published Plugin Hub revision through the exact proposed release commit. Check existing `CHANGELOG.md` and release version sources, including both plugin metadata files and config version if present. Identify duplicates, missing changes, and entries not included in the candidate; use established glossary terms and concise player-facing wording. Exclude internal-only tests, agent rules and memory maintenance. Report draft entries and verification evidence to the coordinator; do not write shared memory yourself. Follow publication checkpoints in `TEST-AND-RELEASE.md`.
