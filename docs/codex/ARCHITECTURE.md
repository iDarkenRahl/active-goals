# Active Goals — Architecture Inventory

Verified against the local checkout on 2026-11-18; this describes code and configuration, not a runtime or API-contract audit.

## Build and dependencies
- `build.gradle` uses the Java plugin, Java compilation release 11, RuneLite client `latest.release`, Lombok 1.18.30, JUnit Jupiter 5.11.4 and Mockito 5.15.2. Dynamic RuneLite resolution is intentional and follows the [official example plugin](https://github.com/runelite/example-plugin/blob/master/build.gradle); the [Plugin Hub guidance](https://github.com/runelite/plugin-hub/blob/master/README.md) also recommends `latest.release`. The wrapper specifies Gradle 9.8.0 (`gradle/wrapper/gradle-wrapper.properties`). Record the version actually resolved for each assessment in current state.
- `settings.gradle` names the project `ActiveGoals`. Release version declarations are maintained in both `runelite-plugin.properties` files and `ActiveGoalsConfig.VERSION`; `ActiveGoalsVersionTest` checks their consistency. See [current state](../CURRENT_STATE.md) and the [changelog](../../CHANGELOG.md) for changing release and publication information.

## Entry points and plugin lifecycle
- `src/main/java/com/activegoals/ActiveGoalsPlugin.java` is the RuneLite plugin. Startup resets XP baselines, adds a navigation button and starts the panel; shutdown pauses session tracking, stops the panel and removes the button. It subscribes to config, game state, RuneScape profile, game tick and stat changes.

## Domain logic and event flow
- `GoalTracker.java` owns individual goal records, encoding/decoding, metadata, activity and card state. `ActiveGoalsPlugin.java` manages profile loading, XP baselines, events, tracking and persistence. `GLOSSARY.md` defines the project language.
- Relevant decisions: [character- and game-mode-scoped goals](../adr/0001-character-scoped-goals.md) and [independent goal records](../adr/0002-independent-goals.md).
- Login/profile changes reset or reload tracking context; stat changes feed XP tracking and game ticks handle timed state. See the event handlers in `ActiveGoalsPlugin.java` for exact conditions.
- Session overview XP, logged-in time and individual goal tracking are kept in plugin-instance memory by profile and account hash. Profile return or plugin re-enablement reloads saved goal definitions and metadata and transfers session statistics to matching goal IDs and skills; no session statistics are written to configuration. In-client plugin-instance reuse remains unverified for the resolved RuneLite version.
- Goal actions are tied to the profile session shown by the sidebar. A logout or profile-key change invalidates queued actions, including when the original profile is later reloaded; mutations also require a matching, loaded profile key. The saved-goal format is unchanged.

## UI components and threading
- `ActiveGoalsPanel.java` implements the Swing sidebar, snapshots and refreshes; `ActiveGoalsDialogs.java` supplies dialogs, and `ActiveGoalsConfig.java` declares settings. Panel refresh requests use the client thread for snapshots and Swing EDT dispatch for UI updates (`ActiveGoalsPanel.java`, `ActiveGoalsPlugin.runOnClientThread`). This inventory does not certify every thread path.

## Configuration, storage, backup, migrations
- The `activegoals` configuration group stores profile-scoped `individualGoals`, `goalCreationTimes`, and `manuallyCollapsedGoals` via RuneLite `ConfigManager` (`ActiveGoalsPlugin.java`). Goal serialization and malformed-record handling are in `GoalTracker.java`; manual-collapse choices and creation times are stored separately ([ADR 0005](../adr/0005-store-manual-collapse-separately.md), [ADR 0006](../adr/0006-store-goal-creation-times-separately.md)).
- Intentional corrupted-save behavior: unreadable goal records are replaced by corrupted-save placeholders and the original unreadable serialized contents are not retained when the profile is saved. The user confirmed this behavior is intended; do not report it as a defect in future reviews. The placeholder remains until removed by the player.
- The plugin also contains global settings and legacy goal access/migration code. A source search found no call site for the private `loadIndividualGoals` legacy loader; this alone does not establish compatibility of every legacy path. No dedicated backup/export implementation was identified in the source inventory; RuneLite's own backup behavior is unverified.

## Tests, CI, release process
- `src/test/java/com/activegoals/` contains JUnit tests for tracking, serialization, ordering, creation times, settings, version consistency and UI-related behavior. `build.gradle` configures JUnit Platform, a local sideload installation task and a developer launch task. These tasks were not run during inventory.
- The repository contains an issue-labeling GitHub Actions workflow; no build or test CI workflow was identified in the local workflow inventory. External CI is unverified. See [testing and release gates](TEST-AND-RELEASE.md) and [current state](../CURRENT_STATE.md) for release process and status.

## Known risks and unknowns
- `latest.release` can resolve differently over time, and local compilation and tests do not establish in-client or Plugin Hub build compatibility. Separate profile writes can leave stale metadata (ADR 0005); RuneLite's backup behavior and external CI remain unverified. Current validation and release uncertainties belong in [current state](../CURRENT_STATE.md).

## Maintenance
This is the canonical stable architecture reference for v1.4. Update it from verified local source, build and test evidence; mark unavailable facts explicitly. Do not claim tests were run unless executed. Keep historical rationale in ADRs, current work in `docs/CURRENT_STATE.md`, and terminology in `GLOSSARY.md`. If an older `docs/PROJECT.md` exists, reconcile verified knowledge without deleting or overwriting historical content.
