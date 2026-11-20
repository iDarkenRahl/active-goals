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
    void orderingWindowUsesItsOwnSettingAndClampsInvalidValues() throws Exception
    {
        ConfigManager config = mock(ConfigManager.class);
        ActiveGoalsPlugin plugin = pluginWithConfig(config);
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "goalCardExpansionSeconds")).thenReturn("30");
        assertEquals(300, plugin.getGoalOrderingActivitySeconds());
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "goalOrderingActivitySeconds")).thenReturn("45", "0", "999999");
        assertEquals(45, plugin.getGoalOrderingActivitySeconds());
        assertEquals(1, plugin.getGoalOrderingActivitySeconds());
        assertEquals(86400, plugin.getGoalOrderingActivitySeconds());
        assertEquals(30, plugin.getGoalCardExpansionSeconds());
    }

    @Test
    void agilityTimeoutDefaultsToFiveMinutesAndClampsInvalidValues() throws Exception
    {
        ConfigManager config = mock(ConfigManager.class);
        ActiveGoalsPlugin plugin = pluginWithConfig(config);
        assertEquals(300, plugin.getAgilityLapTimeoutSeconds());
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "agilityLapTimeoutSeconds"))
            .thenReturn("45", "0", "999999", "invalid");
        assertEquals(45, plugin.getAgilityLapTimeoutSeconds());
        assertEquals(1, plugin.getAgilityLapTimeoutSeconds());
        assertEquals(86400, plugin.getAgilityLapTimeoutSeconds());
        assertEquals(300, plugin.getAgilityLapTimeoutSeconds());
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
