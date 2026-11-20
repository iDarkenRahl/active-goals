# Active Goals

Active Goals is a RuneLite sidebar plugin for tracking Old School RuneScape skill goals. Set a target, train as usual, and see your progress and training estimates in one place.

## Features

* Create multiple goals, including separate goals for the same skill.
* Set a target level, including virtual levels up to 126, or an exact XP target up to 200 million.
* See progress, current XP, XP remaining, XP per hour, session XP, estimated actions left, and estimated time to completion.
* View total XP gained and time elapsed in the session overview.
* Edit or remove goals from the sidebar. Goals are saved separately for each character and game-mode profile.
* Automatically expand a goal when you gain XP in its skill, then collapse it after a configurable delay. Manually collapsed goals stay closed until you expand or edit them, or they are completed.
* Move recently trained skills to the top when a new training activity starts.
* Hover over a progress bar to see XP gained toward the goal, or an incomplete level goal's range to see your current XP-earned level. Expanded cards show a Created date.

## Getting started

1. Find and install **Active Goals** through RuneLite's Plugin Hub.
2. Open the **Active Goals** sidebar while logged in.
3. Click **+**, choose a skill, and enter a level or XP target above your current XP.
4. Create the goal and start training. Use the pencil to edit a goal or the trash can to remove it.

Progress starts at your XP when the goal is created. Reaching the target marks the goal complete; completed goals remain visible briefly before being removed.

## Settings and estimates

Use the Active Goals settings in RuneLite's plugin configuration to change the auto-collapse delay, the activity window used for goal card ordering, and the inactivity timeouts for Agility laps and Mahogany Homes contracts. Each defaults to five minutes.

XP per hour and time remaining are estimates based on recent training. **Actions Left** uses recent XP changes as samples, so it may differ from the actual number of actions remaining. Automatic collapse resets these rate and action estimates; goal session XP is preserved. Session XP and logged-in session time are tracked separately for each character and game-mode profile until RuneLite closes or restarts. XP and time while the plugin is disabled are not counted.

After a recognized Agility course lap, **Laps Left** estimates the whole-lap equivalents needed for the remaining XP. For Mahogany Homes, **Homes Left** uses the average XP of up to 20 observed complete contracts of the current tier, updating after each recognized completion. It takes account of the Carpenter's outfit worn during those contracts. The card returns to **Actions Left** when an estimate is unavailable or its inactivity timeout expires. These estimates can differ from actual results; unrelated Construction XP gained during an observed contract can affect the Homes Left average.

If a saved goal cannot be loaded, a **Corrupted Save** card appears in its place. Use its trash button to remove it.

