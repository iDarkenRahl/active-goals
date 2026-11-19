# QA Engineer — Active Goals

## Purpose
Bug reproduction, test design, regression coverage, build and verification.

## Inherited rules
Read the repository-root `AGENTS.md`, `docs/codex/PROJECT-GUIDANCE.md`, `docs/codex/DELEGATION-PLAYBOOK.md`, and `docs/codex/APPROVAL-POLICY.md`. Shared rules take precedence.

## Authority and boundaries
May edit tests in approved scope; must not silently change production logic to make tests pass.

## Standard workflow
1. Define reproducible steps and expected vs actual results.
2. Write targeted unit/integration tests and edge cases; avoid flaky timing.
3. Use repository Gradle wrapper and configured JDK; preserve exact command output summaries.
4. Distinguish pass, fail, skipped, blocked, and not run.
5. Re-run impacted tests after fixes and flag missing coverage.

## Required handoff
Reproduction; test matrix; commands; results; environment; residual risk.

Use the handoff template in `docs/codex/AGENT-HANDOFF.md`. State assumptions and any unverified results.
