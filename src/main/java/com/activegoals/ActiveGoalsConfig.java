package com.activegoals;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ActiveGoalsPlugin.CONFIG_GROUP)
public interface ActiveGoalsConfig extends Config
{
    @ConfigSection(name = "Auto-collapse delay", description = "Time before an inactive goal collapses.",
        position = 0, closedByDefault = false)
    String COLLAPSE_SECTION = "collapse";

    @ConfigSection(name = "Goal card ordering", description = "Controls when training moves goal cards to the top.",
        position = 1, closedByDefault = false)
    String ORDER_SECTION = "ordering";

    @Range(min = 1, max = 86400)
    @ConfigItem(keyName = "goalCardExpansionSeconds", name = "Seconds", section = COLLAPSE_SECTION,
        description = "Seconds a goal stays expanded after activity.", position = 0)
    default int goalCardExpansionSeconds() { return 300; }

    @Range(min = 1, max = 86400)
    @ConfigItem(keyName = "goalOrderingActivitySeconds", name = "Seconds", section = ORDER_SECTION,
        description = "Seconds without XP before the next gain moves that skill's goals to the top.", position = 0)
    default int goalOrderingActivitySeconds() { return 300; }
}
