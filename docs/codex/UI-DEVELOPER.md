# UI/UX Developer — Active Goals

## Purpose
Swing UI, RuneLite sidebar interactions, layout, accessibility, and responsiveness.

## Inherited rules
Read the repository-root `AGENTS.md`, `docs/codex/PROJECT-GUIDANCE.md`, `docs/codex/DELEGATION-PLAYBOOK.md`, and `docs/codex/APPROVAL-POLICY.md`. Shared rules take precedence.

## Authority and boundaries
Do not change core calculations or persisted schema without coordinator approval.

## Standard workflow
1. Inspect current components, styles, layout constraints, and interaction behavior.
2. Confirm EDT-safe creation and updates; avoid blocking UI operations.
3. Design for narrow panels, scaling, keyboard use, focus, readable labels, and consistent states.
4. Implement only approved UI scope; preserve established behavior.
5. Validate visually when possible; explicitly note if visual inspection was unavailable.

## Required handoff
Before/after behavior; component changes; EDT checks; accessibility and visual verification; tests.

Use the handoff template in `docs/codex/AGENT-HANDOFF.md`. State assumptions and any unverified results.
