# ADR-0006: Store goal creation times separately

- **Status:** Accepted
- **Date:** 2026-11-18 (acceptance of this record; not the historical implementation date)
- **Context:** Goal creation times were added after the existing saved-goal format, whose decoder expects six fields per goal. Older goals can lack creation-time metadata.
- **Decision:** Store creation timestamps by goal ID in a separate RuneScape-profile configuration entry, leaving the saved-goal record format unchanged. Show an unknown creation time when the metadata is unavailable or unreadable.
- **Rationale:** Keeping creation times separate preserves compatibility with older plugin versions, as documented in the implementation pull request. The historical evaluation of alternatives was not recovered.
- **Consequences:** Existing goal records do not require a format migration. Missing, duplicate, or unreadable metadata can leave the creation time unknown; separate writes can leave goal records and metadata out of sync if a write is interrupted.
- **Evidence / Approval:** The user approved this ADR on 2026-11-18. The behavior was implemented earlier in [PR #13](https://github.com/iDarkenRahl/active-goals/pull/13); its merge date is not the date of this ADR's acceptance. See [GoalTracker](../../src/main/java/com/activegoals/GoalTracker.java), [ActiveGoalsPlugin](../../src/main/java/com/activegoals/ActiveGoalsPlugin.java), and [GoalCreationTimeTest](../../src/test/java/com/activegoals/GoalCreationTimeTest.java).
- **Affected glossary terms / requirements:** Goal; Goal Creation Time.
- **Related ADRs:** [ADR 0005: Store manual-collapse choices separately from saved goals](0005-store-manual-collapse-separately.md).

## Later behavior clarification

The user approved updating the displayed Created date on a successful save when its date was unknown or its level or XP target or target type changed. An unchanged known date is preserved. This changes the displayed date's meaning, not the storage decision above: the same separate per-goal metadata entry remains in use, and the goal's starting XP and session statistics are preserved.
