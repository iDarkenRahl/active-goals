# 5. Store manual-collapse choices separately from saved goals

Date: 2026-11-16

## Status

Accepted; implemented in the fixes for manual collapse and goal card ordering.

## Context

Players can create independent goals for the same skill and can manually collapse each card. A manually collapsed card should remain closed through XP gains and restarts until the player expands it, edits the goal, or reaches its target. The existing saved-goal format has six fields per goal; its decoder treats records with an unexpected field count as unreadable.

We needed to remember each choice without making existing saved goals unreadable or conflating separate goals for the same skill.

## Decision

Store the IDs of manually collapsed goals in a separate RuneScape-profile configuration entry. Keep the existing saved-goal record format unchanged. Each goal has its own ID, so the choice applies to one card rather than an entire skill.

Restore choices for valid, incomplete goals when their profile loads. Clear a saved choice when the player expands the card, edits or deletes the goal, or completes it. Ignore and prune entries that no longer correspond to eligible goals.

Card ordering and card expansion are independent: a manually collapsed card can move when a new training activity starts without opening. This decision does not persist the goal's transient session or rate statistics across plugin restarts.

## Consequences

- Existing goal records remain readable without a format migration; older plugin versions can read them while ignoring the separate collapse preference.
- Goal records and collapse preferences are written separately, so a partial write, downgrade, or restore can leave stale preferences. Unknown or ineligible IDs are discarded when preferences are restored.
- Future changes to goal identity, profile loading, deletion, editing, or completion must account for the separate preference. A downgrade followed by an upgrade may also restore an old preference for a goal that survived in the older version.
- The preference survives restarts, while session and rate statistics retain their existing reset behavior.

## Evidence

- [Manual collapse implementation PR](https://github.com/iDarkenRahl/active-goals/pull/10) describes separate storage and compatibility with older versions.
- [GoalTracker](../../src/main/java/com/activegoals/GoalTracker.java) encodes and restores per-goal collapse choices; [ActiveGoalsPlugin](../../src/main/java/com/activegoals/ActiveGoalsPlugin.java) saves and restores them per profile.
- [ActiveGoalsGoalCardExpansionTest](../../src/test/java/com/activegoals/ActiveGoalsGoalCardExpansionTest.java) and [GoalTrackerTest](../../src/test/java/com/activegoals/GoalTrackerTest.java) cover collapse and persistence behavior.
