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

    @Range(min = 1, max = 86400)
    @ConfigItem(keyName = "goalCardExpansionSeconds", name = "Seconds", section = COLLAPSE_SECTION,
        description = "Seconds a goal stays expanded after activity.", position = 0)
    default int goalCardExpansionSeconds() { return 300; }
}
