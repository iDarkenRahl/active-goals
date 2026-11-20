# Active Goals

Active Goals tracks training progress toward configured Old School RuneScape skill goals.

## Language

**Plugin Release**:
An update of Active Goals published for players, identified by a release version.

**Release Version**:
The semantic version identifying a plugin release, expressed as major, minor, and patch numbers.

**Character**:
An Old School RuneScape character whose goals are separate from those of other characters.

**Game-mode Profile**:
The character and game-mode context in which a separate set of goals is maintained.

**Goal**:
An individual target for one skill, belonging to a character and game-mode profile. Multiple goals may have the same skill and target.
_Avoid_: Active goal

**Goal Target**:
The level or exact XP a character aims to reach in a goal's skill.

**Goal Creation Time**:
The date and time shown as Created on a goal card. It is initially the goal's creation time. Saving a goal with an unknown date or changing its level or XP target (including the target type) sets it to the time of that save; saving an unchanged target preserves a known date. Older goals or unreadable metadata may have an unknown date until saved.

**Goal Starting XP**:
The skill's XP when a goal is created, representing 0% progress toward that goal.

**Goal Starting Level**:
The level derived from a goal's starting XP; it remains fixed as the character gains XP.

**Goal Percentage**:
Progress from a goal's starting XP to its target XP, expressed from 0% to 100%.
_Avoid_: Goal Progress in internal domain terminology; it is acceptable as a player-facing UI label.

**Completed Goal**:
A goal whose target has been reached and which remains visible temporarily during normal gameplay before removal. It does not need to reappear after a plugin restart.

**Session XP**:
The total XP gains observed across skills during the current RuneLite client session for a character and game-mode profile, counted once per gain even when multiple goals track the same skill. It is retained across logout, profile changes, and plugin disablement, and resets when the client closes or restarts. XP gained while the plugin is disabled is excluded.

**Goal Session XP**:
The sum of observed positive XP changes for a goal's skill while that goal is incomplete, accumulated separately for the goal during the current RuneLite client session. It is retained across logout, profile changes, and plugin disablement, and resets when the client closes or restarts. XP gained while the plugin is disabled is excluded. The full gain that completes the goal is included, even when it exceeds the target.

**Session Time**:
The accumulated time spent logged in on a character and game-mode profile during the current RuneLite client session while Active Goals is enabled. It pauses when the character is not logged in or the plugin is disabled and resets when the client closes or restarts.

**Goal XP/hour**:
The rate of XP gained toward a goal during its current rate-tracking period.

**Goal Rate-tracking Period**:
The period used to measure a goal's XP/hour, beginning with an observed XP gain and resetting when the goal card automatically collapses. Time spent logged out or on another character is excluded.

**XP Change**:
An observed change in a skill's whole-number XP total.

**Observed XP Gain**:
An increase in a skill's XP detected by the plugin while it is tracking the character.

**Actions Left**:
An estimate of the number of actions needed to reach a goal, based on recent positive XP changes. Each change is a sample and does not necessarily represent one in-game action.

**Goal Card**:
The dashboard representation of a goal and its progress statistics.

**Goal Card Order**:
The order in which goal cards appear on the dashboard.

**Collapsed Goal Card**:
A goal card showing its compact header, goal endpoints, and progress bar without the expanded statistics.
_Avoid_: Minimized goal

**Manually Collapsed Goal Card**:
A goal card the player has chosen to keep collapsed until they expand it or the goal is edited or completed. Training in its skill does not promote it, but other cards can move above it.

**Expanded Goal Card**:
A goal card showing its target, progress, controls, and detailed statistics.
_Avoid_: Unminimized goal

**Goal Card Expansion Window**:
The configured period during which an automatically managed, incomplete goal card remains expanded after creation, editing, manual expansion, or an observed XP gain. This is distinct from the Training Activity Window.
_Avoid_: Training activity window, timeout

**Training Activity Window**:
The configured period in which successive observed XP gains in a skill count as the same training activity for goal card ordering. Each gain extends the window.
_Avoid_: Goal card expansion window

**Corrupted Save Placeholder**:
A dashboard card indicating that a saved goal could not be loaded. It remains visible until the user deletes it.
