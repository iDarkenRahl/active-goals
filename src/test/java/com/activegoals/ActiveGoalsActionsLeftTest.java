package com.activegoals;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveGoalsActionsLeftTest
{
    @Test void noSamplesWaitsForXp() { assertEquals(-1, ActiveGoalsPlugin.estimateActionsLeft(1000, Collections.emptyList())); }
    @Test void averagesRecentXpGains() { assertEquals(7, ActiveGoalsPlugin.estimateActionsLeft(1000, Arrays.asList(100, 200))); }
    @Test void completedGoalShowsZero() { assertEquals(0, ActiveGoalsPlugin.estimateActionsLeft(0, Collections.emptyList())); }
    @Test void roundsUpActions() { assertEquals(4, ActiveGoalsPlugin.estimateActionsLeft(301, Arrays.asList(100))); }
    @Test void resetClearsOnlyActionSamples() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Field field = ActiveGoalsPlugin.class.getDeclaredField("recentXpGainSamples");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Skill, Deque<Integer>> samples = (Map<Skill, Deque<Integer>>) field.get(plugin);
        samples.put(Skill.WOODCUTTING, new ArrayDeque<>(Arrays.asList(100, 120)));
        assertEquals(10, plugin.getActionsLeft(Skill.WOODCUTTING, 1000));
        plugin.clearActionSamples();
        assertTrue(samples.isEmpty());
        assertEquals(-1, plugin.getActionsLeft(Skill.WOODCUTTING, 1000));
    }
}
