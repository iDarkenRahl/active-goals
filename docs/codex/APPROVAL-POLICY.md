# Supervised Approval Policy

## Read-only without prior approval
Repository inspection, planning, documentation research, diff review, and non-destructive test planning.

## Within an explicitly approved implementation task
Focused code/test/documentation edits and local builds/tests. Stay within named scope; pause if scope expands or an irreversible action appears.

## Always request explicit approval first
- Architectural redesign or changes to externally observable behavior outside scope.
- Persistent-format changes, migrations, data deletion, or destructive reset operations.
- Java/Gradle/dependency upgrades or external tool installation.
- Commit, push, merge, tag, publish, deploy, release, or any remote write (including PR/issue edits).
- Credential access, privileged commands, broad filesystem operations, or bypassing sandbox limits.

## Operational limits
Instructions are behavioral guardrails, not enforceable access controls. Configure sandbox, approval prompts, GitHub branch protections, and minimal credentials separately. If a tool grants broader rights than intended, do not use those rights without approval.

## Approval request format
Describe the exact action, why needed, affected files/data/remotes, risks, rollback, and alternatives. Wait for explicit approval. Silence is not approval.
