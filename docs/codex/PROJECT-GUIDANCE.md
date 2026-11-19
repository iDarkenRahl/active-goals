# Active Goals — Detailed Project Guidance

## Discover before designing
Inventory Java packages, Gradle wrapper, dependency declarations, entry points, plugin descriptors, UI components, event subscribers, configuration, persistence, tests, CI, and release workflow. Record verified facts in ARCHITECTURE.md; mark unknowns explicitly. Do not import assumptions from older project names.

## Engineering standards
Respect configured JDK/Gradle versions and RuneLite API. Prefer small cohesive classes and single-responsibility changes. Preserve public behavior, configuration keys, saved data, and import/export compatibility unless explicitly authorized. Avoid speculative dependencies and broad formatting changes.

## RuneLite integration
Review plugin lifecycle, registration/unregistration, event subscriptions, client-thread constraints, thread-safe state access, and cleanup on shutdown. Avoid expensive per-tick operations and blocking the client thread. Verify actual API contracts from source or authoritative documentation.

## Swing UI
Perform UI mutations on the Event Dispatch Thread. Consider narrow sidebar widths, text scaling, contrast, keyboard/focus behavior, disabled/loading states, and consistent layouts. Do not claim visual validation without an actual run or screenshot review.

## State and persistence
Document stored keys, data formats, defaults, migrations, failure handling, backup round trips, and legacy compatibility. Treat corrupted or missing values defensively. Require migration/rollback planning before schema changes.

## Tests and diagnostics
Use the repository Gradle wrapper. Prioritize deterministic unit tests for calculations and serialization, integration tests for lifecycle/state where feasible, and manual in-client validation for UI behavior. Capture exact commands, JDK, failures, skipped tests, and untested paths. Never suppress failures.

## Security and performance
Avoid secrets in logs or code. Review untrusted imports and resource usage. Measure before optimizing; state performance baseline and limitations. Preserve user data.

## Documentation
Update relevant README, changelog, architecture notes, and tests for approved changes. Documentation must match verified implementation, not aspirations.

## Knowledge references
Use `ARCHITECTURE.md` (in this directory) for stable internal architecture, `../CURRENT_STATE.md` for current progress, `../adr/` for significant technical decisions, and root `GLOSSARY.md` for terminology and documented behavior. Root `README.md` remains player documentation; root `CHANGELOG.md` remains player-facing release history. Consult relevant records before changes, flag contradictions, and preserve existing glossary format. Report knowledge impact through `AGENT-HANDOFF.md`; the coordinator applies authorized updates using `LEAD-COORDINATOR.md`.
