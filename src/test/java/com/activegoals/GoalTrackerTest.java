package com.activegoals;

import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoalTrackerTest
{
    @Test
    void manualCollapseSurvivesRoundTripAndOnlyCompletionReopensIt()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal first = tracker.add(Skill.MINING, 1000, 1200, 1);
        GoalTracker.Goal second = tracker.add(Skill.MINING, 1000, 2000, 1);
        first.manuallyCollapsed = true;
        first.expandedAtNanos = 0;
        String goals = tracker.encode();
        GoalTracker restored = GoalTracker.decode(goals);
        restored.restoreManualCollapses(first.id + ";unknown;invalid");
        GoalTracker.Goal restoredFirst = restored.goals().get(1);
        GoalTracker.Goal restoredSecond = restored.goals().get(0);
        assertEquals(goals, restored.encode());
        assertEquals(first.id, restored.encodeManualCollapses());
        assertFalse(restored.gain(Skill.MINING, 1100, 100, 2));
        assertTrue(restoredFirst.manuallyCollapsed);
        assertEquals(0, restoredFirst.expandedAtNanos);
        assertEquals(100, restoredFirst.sessionXp);
        assertEquals(100, restoredFirst.rateXp);
        assertEquals(100, restoredFirst.samples.getFirst());
        assertFalse(restoredSecond.manuallyCollapsed);
        assertEquals(2, restoredSecond.expandedAtNanos);
        assertTrue(restored.gain(Skill.MINING, 1200, 100, 3));
        assertTrue(restoredFirst.completed);
        assertFalse(restoredFirst.manuallyCollapsed);
        assertEquals(3, restoredFirst.expandedAtNanos);
        assertEquals("", restored.encodeManualCollapses());
    }

    @Test
    void autoCollapseResetsRateAndSamplesButPreservesSessionXp()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.MINING, 1000, 5000, 1);
        tracker.gain(Skill.MINING, 1100, 100, 2);
        goal.expireExpansion(300_000_000_002L, 300);
        assertEquals(100, goal.sessionXp);
        assertEquals(0, goal.rateXp);
        assertEquals(0, goal.ratePeriodStartNanos);
        assertTrue(goal.samples.isEmpty());
        tracker.gain(Skill.MINING, 1150, 50, 300_000_000_003L);
        assertEquals(150, goal.sessionXp);
        assertEquals(50, goal.rateXp);
        assertEquals(50, goal.samples.getFirst());
    }
    @Test
    void decodeKeepsValidGoalsAndPersistsUnreadablePlaceholders()
    {
        String data = "first,MINING,100,2000,0,false;bad;broken,UNKNOWN,0,500,0,false;"
            + "invalid,FISHING,nope,500,0,false;last,FISHING,10,600,0,false";
        GoalTracker restored = GoalTracker.decode(data);
        assertEquals(5, restored.goals().size());
        assertEquals("first", restored.goals().get(0).id);
        assertNull(restored.goals().get(1).skill);
        assertNull(restored.goals().get(2).skill);
        assertNull(restored.goals().get(3).skill);
        assertEquals("last", restored.goals().get(4).id);
        String encoded = restored.encode();
        GoalTracker reloaded = GoalTracker.decode(encoded);
        assertEquals(encoded, reloaded.encode());
        reloaded.remove(reloaded.goals().get(1).id);
        assertEquals(4, GoalTracker.decode(reloaded.encode()).goals().size());
    }

    @Test
    void duplicateIdsAreRepairedWithoutDiscardingGoals()
    {
        GoalTracker tracker = GoalTracker.decode("same,MINING,2000,2000,0,false;same,FISHING,10,600,0,false");
        assertEquals(2, tracker.goals().size());
        assertNotEquals(tracker.goals().get(0).id, tracker.goals().get(1).id);
        tracker.remove(tracker.goals().get(0).id);
        assertEquals(Skill.FISHING, tracker.goals().get(0).skill);
    }
    @Test
    void startingLevelStaysFixedAfterLevelingAndReload()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.MINING, 83, 5000, 1);
        tracker.gain(Skill.MINING, 1000, 917, 2);
        assertEquals(2, goal.startingLevel());
        assertEquals(2, GoalTracker.decode(tracker.encode()).goals().get(0).startingLevel());
    }
    @Test
    void identicalTargetsCreateIndependentGoals()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal first = tracker.add(Skill.MINING, 1000, 2000, 0);
        GoalTracker.Goal second = tracker.add(Skill.MINING, 1500, 2000, 0);
        tracker.gain(Skill.MINING, 1600, 100, 1);
        assertEquals(0.6, first.progress(1600), 0.00001);
        assertEquals(0.2, second.progress(1600), 0.00001);
        assertEquals(100, first.sessionXp());
        assertEquals(100, second.sessionXp());
        tracker.remove(first.id());
        assertEquals(1, tracker.goals().size());
        assertEquals(second.id(), tracker.goals().get(0).id());
    }

    @Test
    void oneCompletedGoalDoesNotStopAnotherGoal()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal shorter = tracker.add(Skill.MINING, 1000, 1100, 0);
        GoalTracker.Goal longer = tracker.add(Skill.MINING, 1000, 1500, 0);
        tracker.gain(Skill.MINING, 1100, 100, 1);
        tracker.gain(Skill.MINING, 1150, 50, 2);
        assertEquals(100, shorter.sessionXp());
        assertEquals(150, longer.sessionXp());
        assertEquals(1.0, shorter.progress(1150));
        assertEquals(0.3, longer.progress(1150), 0.00001);
    }

    @Test
    void duplicateGoalsSurvivePersistenceWithSeparateStartingXp()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal first = tracker.add(Skill.MINING, 1000, 2000, 1);
        GoalTracker.Goal second = tracker.add(Skill.MINING, 1500, 2000, 2);
        GoalTracker restored = GoalTracker.decode(tracker.encode());
        assertEquals(2, restored.goals().size());
        assertEquals(second.id(), restored.goals().get(0).id());
        assertEquals(first.id(), restored.goals().get(1).id());
        assertEquals(0.2, restored.goals().get(0).progress(1600), 0.00001);
        assertEquals(0.6, restored.goals().get(1).progress(1600), 0.00001);
    }

    @Test
    void migratedGoalAlreadyAtTargetDisplaysComplete()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.restore("existing", Skill.MINING, 2000, 2000, 0);
        assertEquals(1.0, goal.progress(2000));
    }
}
