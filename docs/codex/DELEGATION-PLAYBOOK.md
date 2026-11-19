# Delegation Playbook

## When to delegate
Use specialists when domain knowledge or independent review adds value. Keep trivial edits in the coordinator session. Limit concurrent specialists to a small number (start with three), subject to the installed Codex configuration.

When delegation is beneficial, prioritize available named project specialist agents whose established responsibilities match the task; an explicit user request for specialist delegation is not required. Use generic agents only when no suitable configured specialist is available or a generic role is better suited to the assignment. When using a generic agent instead of a suitable configured specialist, report the reason. Report actual roles invoked and their contributions; never represent a generic agent as a configured specialist. Continue to respect applicable higher-priority delegation instructions.

## Task contract
Every delegation must specify: objective; verified context; files in scope; owner; read-only or approved-write authority; forbidden actions; dependencies; expected tests; acceptance criteria; report format; escalation triggers.

## Ownership
Assign one writer per file or isolated worktree. Parallel read-only inspections are fine. Never have reviewer and implementer edit the same files concurrently. The coordinator integrates work after review.

## Routing
Architecture for feature design; RuneLite Specialist for client/API changes; UI Developer for Swing; Data Specialist for saved data; QA for reproducibility/tests; Code Reviewer for independent review; Release Manager for versioning/release readiness.

## Disagreements
Present evidence, alternatives, and impacts. Escalate major tradeoffs to the user. Never let agent consensus bypass approval.

## Completion
Require handoff template, QA evidence where relevant, review findings, and explicit unresolved items.
