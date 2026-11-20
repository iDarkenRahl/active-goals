package com.activegoals;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ActiveGoalsPlugin.CONFIG_GROUP)
public interface ActiveGoalsConfig extends Config
{
    String VERSION = "1.0.3";

    @ConfigSection(name = "Version " + VERSION, description = "Active Goals release version.",
        position = -1, closedByDefault = true)
    String VERSION_SECTION = "version";

    @ConfigSection(name = "Auto-collapse delay", description = "Time before an inactive goal collapses.",
        position = 0, closedByDefault = false)
    String COLLAPSE_SECTION = "collapse";

    @ConfigSection(name = "Goal card ordering", description = "Controls when training moves goal cards to the top.",
        position = 1, closedByDefault = false)
    String ORDER_SECTION = "ordering";

    @ConfigSection(name = "Agility lap timeout", description = "Time before an inactive course estimate expires.",
        position = 2, closedByDefault = false)
    String AGILITY_SECTION = "agility";

    @Range(min = 1, max = 86400)
    @ConfigItem(keyName = "goalCardExpansionSeconds", name = "Seconds", section = COLLAPSE_SECTION,
        description = "Seconds a goal stays expanded after activity.", position = 0)
    default int goalCardExpansionSeconds() { return 300; }

    @Range(min = 1, max = 86400)
    @ConfigItem(keyName = "goalOrderingActivitySeconds", name = "Seconds", section = ORDER_SECTION,
        description = "Seconds without XP before the next gain moves that skill's goals to the top.", position = 0)
    default int goalOrderingActivitySeconds() { return 300; }

    @Range(min = 1, max = 86400)
    @ConfigItem(keyName = "agilityLapTimeoutSeconds", name = "Seconds", section = AGILITY_SECTION,
        description = "Seconds without a completed lap before returning to Actions Left.", position = 0)
    default int agilityLapTimeoutSeconds() { return 300; }
}
