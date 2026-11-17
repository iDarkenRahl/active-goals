package com.activegoals;

import net.runelite.api.Skill;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoalOrderingTest
{
    @Test
    void activityMovesIncompleteGoalsTogetherAndKeepsOtherCardsInOrder()
    {
        GoalTracker tracker = GoalTracker.decode("a,MINING,0,1000,0,false;done,MINING,0,1000,0,true;"
            + "b,FISHING,0,1000,0,false;bad,!CORRUPTED;c,MINING,0,1000,0,false");
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("a;c;done;b;bad", ids(tracker));
        assertEquals(tracker.encode(), GoalTracker.decode(tracker.encode()).encode());
        tracker.recordTrainingGain(Skill.FISHING, 2, 5);
        assertEquals("b;a;c;done;bad", ids(tracker));
        tracker.recordTrainingGain(Skill.MINING, 3, 5);
        assertEquals("b;a;c;done;bad", ids(tracker));
        tracker.recordTrainingGain(Skill.MINING, 5_000_000_003L, 5);
        assertEquals("a;c;b;done;bad", ids(tracker));
    }

    @Test
    void noGoalDoesNotStartActivityAndResetStartsNewActivity()
    {
        GoalTracker tracker = new GoalTracker();
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        GoalTracker.Goal mining = tracker.add(Skill.MINING, 0, 1000, 2);
        GoalTracker.Goal fishing = tracker.add(Skill.FISHING, 0, 1000, 2);
        tracker.recordTrainingGain(Skill.MINING, 3, 5);
        assertEquals(mining, tracker.goals().get(0));
        tracker.recordTrainingGain(Skill.FISHING, 4, 5);
        assertEquals(fishing, tracker.goals().get(0));
        tracker.clearTrainingActivity();
        tracker.recordTrainingGain(Skill.MINING, 5, 5);
        assertEquals(mining, tracker.goals().get(0));
    }

    @Test
    void completedOrDeletedLastGoalDoesNotKeepActivityForNewGoal()
    {
        GoalTracker tracker = new GoalTracker();
        tracker.add(Skill.MINING, 0, 100, 1);
        tracker.recordTrainingGain(Skill.MINING, 2, 5);
        tracker.gain(Skill.MINING, 100, 100, 2);
        GoalTracker.Goal newMining = tracker.add(Skill.MINING, 100, 200, 3);
        tracker.add(Skill.FISHING, 0, 100, 3);
        tracker.recordTrainingGain(Skill.MINING, 4, 5);
        assertEquals(newMining, tracker.goals().get(0));
        tracker.remove(newMining.id);
        GoalTracker.Goal again = tracker.add(Skill.MINING, 100, 200, 5);
        tracker.add(Skill.FISHING, 0, 200, 5);
        tracker.recordTrainingGain(Skill.MINING, 6, 5);
        assertEquals(again, tracker.goals().get(0));
    }

    @Test
    void completingGoalIsNotMovedWhenAnotherGoalRemains()
    {
        GoalTracker tracker = GoalTracker.decode("other,FISHING,0,1000,0,false;"
            + "short,MINING,0,100,0,false;long,MINING,0,1000,0,false");
        tracker.gain(Skill.MINING, 100, 100, 1);
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("long;other;short", ids(tracker));
    }
    @Test
    void reconciliationCompletingLastGoalDoesNotKeepOldActivity()
    {
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal previous = tracker.add(Skill.MINING, 0, 100, 1);
        tracker.recordTrainingGain(Skill.MINING, 2, 5);
        previous.completed = true; // Login or world-hop reconciliation.
        GoalTracker.Goal next = tracker.add(Skill.MINING, 100, 200, 3);
        tracker.add(Skill.FISHING, 0, 200, 3);
        tracker.recordTrainingGain(Skill.MINING, 4, 5);
        assertEquals(next, tracker.goals().get(0));
    }
    @Test
    void manuallyCollapsedCardMovesWithoutOpeningAndSurvivesReload()
    {
        GoalTracker tracker = GoalTracker.decode("other,FISHING,0,1000,0,false;mine,MINING,0,1000,0,false");
        tracker.restoreManualCollapses("mine");
        assertFalse(tracker.gain(Skill.MINING, 100, 100, 1));
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("mine;other", ids(tracker));
        GoalTracker.Goal mining = tracker.goals().get(0);
        assertTrue(mining.manuallyCollapsed);
        assertEquals(0, mining.expandedAtNanos);
        GoalTracker restored = GoalTracker.decode(tracker.encode());
        restored.restoreManualCollapses(tracker.encodeManualCollapses());
        assertEquals("mine;other", ids(restored));
        assertTrue(restored.goals().get(0).manuallyCollapsed);
    }

    @Test
    void completedCollapsedGoalOpensWhileRemainingGoalMovesAndPersists()
    {
        GoalTracker tracker = GoalTracker.decode("other,FISHING,0,1000,0,false;"
            + "short,MINING,0,100,0,false;long,MINING,0,1000,0,false");
        tracker.restoreManualCollapses("short;long");
        assertTrue(tracker.gain(Skill.MINING, 100, 100, 1));
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("long;other;short", ids(tracker));
        assertEquals("long", tracker.encodeManualCollapses());
        assertTrue(tracker.goals().get(2).completed);
        assertFalse(tracker.goals().get(2).manuallyCollapsed);
        assertEquals(1, tracker.goals().get(2).expandedAtNanos);
        GoalTracker restored = GoalTracker.decode(tracker.encode());
        restored.restoreManualCollapses(tracker.encodeManualCollapses());
        assertEquals("long;other;short", ids(restored));
        assertTrue(restored.goals().get(0).manuallyCollapsed);
    }
    private static String ids(GoalTracker tracker)
    {
        StringBuilder result = new StringBuilder();
        for (GoalTracker.Goal goal : tracker.goals())
        {
            if (result.length() > 0) result.append(';');
            result.append(goal.id);
        }
        return result.toString();
    }
}