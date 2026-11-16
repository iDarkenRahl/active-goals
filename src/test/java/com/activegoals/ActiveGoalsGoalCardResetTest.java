package com.activegoals;

import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ActiveGoalsGoalCardResetTest
{
    @SuppressWarnings("unchecked")
    private static Map<Skill, Long> xpMap(ActiveGoalsPlugin plugin, String fieldName) throws Exception
    {
        Field field = ActiveGoalsPlugin.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<Skill, Long>) field.get(plugin);
    }

    @Test
    void resetSessionOverviewDoesNotAffectGoalCards() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        xpMap(plugin, "sessionOverviewXp").put(Skill.WOODCUTTING, 1200L);
        xpMap(plugin, "goalCardXp").put(Skill.WOODCUTTING, 900L);
        xpMap(plugin, "goalRateXp").put(Skill.WOODCUTTING, 900L);
        plugin.clearSessionOverviewXp();
        assertEquals(0L, xpMap(plugin, "sessionOverviewXp").getOrDefault(Skill.WOODCUTTING, 0L));
        assertEquals(900L, plugin.getGoalCardSessionXp(Skill.WOODCUTTING));
        assertEquals(900L, xpMap(plugin, "goalRateXp").get(Skill.WOODCUTTING));
    }

    @Test
    void resetOnlyAffectsGoalCardStatistics() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        xpMap(plugin, "sessionOverviewXp").put(Skill.WOODCUTTING, 1200L);
        xpMap(plugin, "goalCardXp").put(Skill.WOODCUTTING, 900L);
        xpMap(plugin, "goalRateXp").put(Skill.WOODCUTTING, 900L);
        plugin.clearGoalCardXpStats();
        assertEquals(1200L, xpMap(plugin, "sessionOverviewXp").get(Skill.WOODCUTTING));
        assertEquals(0L, plugin.getGoalCardSessionXp(Skill.WOODCUTTING));
        assertEquals(0L, plugin.getXpPerHour(Skill.WOODCUTTING));
    }

    @Test
    void clearActionSamplesDoesNotClearGoalXp() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        xpMap(plugin, "goalCardXp").put(Skill.MINING, 450L);
        Field samplesField = ActiveGoalsPlugin.class.getDeclaredField("recentXpGainSamples");
        samplesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Skill, java.util.Deque<Integer>> samples =
            (Map<Skill, java.util.Deque<Integer>>) samplesField.get(plugin);
        samples.put(Skill.MINING, new java.util.ArrayDeque<>(java.util.Arrays.asList(50, 75)));

        plugin.clearActionSamples();

        assertTrue(samples.isEmpty());
        assertEquals(450L, plugin.getGoalCardSessionXp(Skill.MINING));
    }
}
