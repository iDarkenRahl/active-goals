package com.activegoals;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgilityCourseTrackerTest
{
    @Test
    void recognizesLapAndExpiresFromLastCompletion()
    {
        Client client = mock(Client.class);
        AgilityCourseTracker tracker = new AgilityCourseTracker();
        tracker.observeXp(new WorldPoint(3103, 3261, 0), 1_000_000_000L);
        assertEquals(120, tracker.xpPerLap(client, 2_000_000_000L, 5));
        tracker.observeXp(new WorldPoint(3100, 3261, 0), 3_000_000_000L);
        assertEquals(-1, tracker.xpPerLap(client, 6_000_000_000L, 5));
        assertEquals(-1, tracker.xpPerLap(client, 6_000_000_000L, 300));
        tracker.observeXp(new WorldPoint(3103, 3261, 0), 7_000_000_000L);
        assertEquals(120, tracker.xpPerLap(client, 11_999_999_999L, 5));
        tracker.reset();
        assertEquals(-1, tracker.xpPerLap(client, 12_000_000_000L, 5));
    }

    @Test
    void usesDiaryAndPyramidBonuses()
    {
        Client client = mock(Client.class);
        AgilityCourseTracker tracker = new AgilityCourseTracker();
        tracker.observeXp(new WorldPoint(3363, 2998, 0), 1);
        assertEquals(890, tracker.xpPerLap(client, 2, 300));
        when(client.getVarbitValue(VarbitID.DESERT_DIARY_HARD_COMPLETE)).thenReturn(1);
        assertEquals(1016, tracker.xpPerLap(client, 2, 300));
        tracker.observeXp(new WorldPoint(3364, 2830, 0), 3);
        when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(50);
        assertEquals(1422, tracker.xpPerLap(client, 4, 300));
        when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(99);
        assertEquals(1722, tracker.xpPerLap(client, 4, 300));
    }

    @Test
    void recognizesWyrmCompletionVarbits()
    {
        Client client = mock(Client.class);
        AgilityCourseTracker tracker = new AgilityCourseTracker();
        tracker.observeVarbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS, 5, 1);
        assertEquals(-1, tracker.xpPerLap(client, 2, 300));
        tracker.observeVarbit(VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS, 6, 3);
        assertEquals(601.6, tracker.xpPerLap(client, 4, 300));
        tracker.observeVarbit(VarbitID.VARLAMORE_WYRM_AGILITY_ADVANCED_PROGRESS, 6, 5);
        assertEquals(1053.6, tracker.xpPerLap(client, 6, 300));
    }
}
