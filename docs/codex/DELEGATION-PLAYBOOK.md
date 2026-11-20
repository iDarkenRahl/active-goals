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

### Routing checkpoint
Before substantive work, the coordinator identifies the workstreams that benefit from delegation and matches each to the named specialist responsibilities above. Check which roles are available and whether delegation is permitted. Assign matching work to an available named specialist, including bounded implementation or investigation; a small file scope alone is not a reason to use a generic worker. Keep trivial work in the coordinator session and respect task dependencies and concurrency limits.

Use a generic agent only when no available named specialist fits, or when the generic role is demonstrably better suited to the particular assignment. Record the reason and report it to the user. If delegation or a relevant role is unavailable or prohibited, state that limitation and perform the work that is authorized and feasible; never present another role as the missing specialist.

In the final handoff, list each actual role used and its contribution. When a relevant specialist was not used because of an availability or delegation restriction, or a generic agent was substituted for a matching specialist, explain why. Do not count the coordinator as a spawned specialist.

## Disagreements
Present evidence, alternatives, and impacts. Escalate major tradeoffs to the user. Never let agent consensus bypass approval.

## Completion
Require handoff template, QA evidence where relevant, review findings, and explicit unresolved items.
