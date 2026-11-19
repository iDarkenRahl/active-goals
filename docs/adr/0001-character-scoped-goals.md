# Store goals per character and game mode

Goals belong to a RuneScape character and game-mode profile rather than a shared RuneLite configuration profile, because characters and separate game modes have different skill XP. World hopping preserves the character's goals, while the auto-collapse setting remains shared. Previously shared goals are not automatically assigned to a character because their owner cannot be established reliably; each character starts with an empty goal list after this change.

## Provenance and current status

The historical text above was recovered from commit `8a1d52879ba78e625ddaede9a3f64643d2538e43`, dated 2026-11-15. That commit date is evidence of the record's existence, not its original decision or approval date; those dates are unknown.

**Status:** Accepted. The user explicitly accepted the recovered core architectural decision as a current project decision on 2026-11-18. This acceptance is separate from the historical record.
