package com.activegoals;

import java.lang.reflect.Field;
import net.runelite.api.Skill;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveGoalsGoalCardExpansionTest
{
    @Test
    void expansionWindowUsesIndependentPerSkillActivity()
        throws Exception
    {
        ActiveGoalsPlugin plugin = pluginWithConfig(mock(ConfigManager.class));

        assertTrue(plugin.isGoalCollapsed(Skill.MINING));
        assertTrue(plugin.isGoalCollapsed(Skill.FISHING));

        plugin.expandGoalCard(Skill.MINING);
        assertFalse(plugin.isGoalCollapsed(Skill.MINING));
        assertTrue(plugin.isGoalCollapsed(Skill.FISHING));

        plugin.collapseGoalCard(Skill.MINING);
        assertTrue(plugin.isGoalCollapsed(Skill.MINING));
    }

    @Test
    void windowExpiresAtConfiguredBoundary()
    {
        long second = 1_000_000_000L;
        assertTrue(ActiveGoalsPlugin.isWithinGoalCardExpansionWindow(10L * second,
            309L * second, 300));
        assertFalse(ActiveGoalsPlugin.isWithinGoalCardExpansionWindow(10L * second,
            310L * second, 300));
        assertTrue(ActiveGoalsPlugin.isWithinGoalCardExpansionWindow(10L * second,
            310L * second, 600));
        assertFalse(ActiveGoalsPlugin.isWithinGoalCardExpansionWindow(null,
            10L * second, 300));
    }

    @Test
    void expansionSettingDefaultsToFiveMinutesAndValidatesRange()
        throws Exception
    {
        ConfigManager config = mock(ConfigManager.class);
        ActiveGoalsPlugin plugin = pluginWithConfig(config);

        assertEquals(300, plugin.getGoalCardExpansionSeconds());
        plugin.setGoalCardExpansionSeconds(600);
        verify(config).setConfiguration(ActiveGoalsPlugin.CONFIG_GROUP,
            "goalCardExpansionSeconds", 600);
        assertThrows(IllegalArgumentException.class,
            () -> plugin.setGoalCardExpansionSeconds(0));
        assertThrows(IllegalArgumentException.class,
            () -> plugin.setGoalCardExpansionSeconds(86_401));
    }

    @Test
    void settingAnXpTargetStartsTheNewGoalGracePeriod()
        throws Exception
    {
        ActiveGoalsPlugin plugin = pluginWithConfig(mock(ConfigManager.class));

        Field client = ActiveGoalsPlugin.class.getDeclaredField("client");
        client.setAccessible(true);
        client.set(plugin, mock(Client.class));
        plugin.setXpTarget(Skill.AGILITY, 100_000L);

        assertFalse(plugin.isGoalCollapsed(Skill.AGILITY));
    }

    private static ActiveGoalsPlugin pluginWithConfig(ConfigManager config) throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Field field = ActiveGoalsPlugin.class.getDeclaredField("configManager");
        field.setAccessible(true);
        field.set(plugin, config);
        return plugin;
    }
}
