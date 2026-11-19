# Testing and Release Gates

## Test matrix
For each changed behavior, record unit tests, integration tests, in-client manual checks, compatibility/legacy cases, and failure-path checks. Mark each pass/fail/skipped/blocked/not run with evidence.

For build and release assessments, report the requested RuneLite dependency version and the actual resolved client version for `compileClasspath`, `testCompileClasspath`, and `testRuntimeClasspath`; identify version conflicts or differences. Record the Java and Gradle versions used, build and automated test commands and results, and compatibility concerns or unverified runtime behavior. Distinguish local compilation and tests from in-client and Plugin Hub build validation. Dynamic resolution and caches can change the selected version over time; do not assume a previous result still applies.

## Before proposing merge
Inspect diff, ensure no unrelated changes, run relevant tests with the repository Gradle wrapper, obtain independent review for significant changes, document limitations, and check user data compatibility.

## Before release
Confirm user approval; inspect version sources and release strategy; verify clean intended changes, reproducible build, tests, RuneLite Plugin Hub requirements, documentation, changelog, and rollback instructions. Do not assume the Plugin Hub uses the same version semantics as Git tags or GitHub Releases.

## No-go conditions
Unresolved critical defects, unreviewed destructive migrations, unverified build claims, missing required approvals, or unexplained failing tests.

## Publish boundary
No commits, pushes, tags, merges, release creation, or submission without explicit approval for the action.

## RuneLite Plugin Hub changelog checkpoints
- During approved development, the coordinator records verified player-facing changes under the appropriate existing unreleased version, without automatic version bumps. If the next version is unknown, retain a draft or unnumbered Unreleased section pending approval. Preserve published history and existing style.
- Before every Plugin Hub submission, identify the last confirmed published revision and candidate revision; compare their changes and reconcile the changelog. If the baseline cannot be verified, mark it unknown and report the blocker. Verify applicable version declarations, build/test evidence and intended release contents. Present release version and changelog for approval before any remote action.
- Local JAR installation (`installRuneLitePlugin`), developer launch (`run`), GitHub commits/tags/releases, PR submission and acceptance are not by themselves proof of Plugin Hub publication. Keep the entry unreleased until actual availability for players is confirmed by reliable evidence or explicit user confirmation. Record submission/acceptance separately in current state if relevant.
- After confirmed publication, within authorized documentation scope, finalize only the published version entry with the verified publication date (or omit the date and flag it if unknown); preserve all older history. Keep changes not included in the published revision unreleased. If confirmation arrives between sessions, reconcile on the next release-related task; no background monitoring is implied. Never commit/push the finalization or submit a follow-up Hub update without explicit approval.
