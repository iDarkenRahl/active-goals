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
* Hover over a progress bar to see XP gained toward the goal. Expanded cards show a Created date.

## Getting started

1. Find and install **Active Goals** through RuneLite's Plugin Hub.
2. Open the **Active Goals** sidebar while logged in.
3. Click **+**, choose a skill, and enter a level or XP target above your current XP.
4. Create the goal and start training. Use the pencil to edit a goal or the trash can to remove it.

Progress starts at your XP when the goal is created. Reaching the target marks the goal complete; completed goals remain visible briefly before being removed.

## Settings and estimates

Use the Active Goals settings in RuneLite's plugin configuration to change the auto-collapse delay and the inactivity window used for goal card ordering. Both default to five minutes.

XP per hour and time remaining are estimates based on recent training. **Actions Left** uses recent XP changes as samples, so it may differ from the actual number of actions remaining. Automatic collapse resets these rate and action estimates; goal session XP is preserved. Goal statistics reset when the plugin or RuneLite restarts.

If a saved goal cannot be loaded, a **Corrupted Save** card appears in its place. Use its trash button to remove it.

