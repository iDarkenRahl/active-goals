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
    void manuallyCollapsedCardStaysInPlaceAndSurvivesReload()
    {
        GoalTracker tracker = GoalTracker.decode("other,FISHING,0,1000,0,false;mine,MINING,0,1000,0,false");
        tracker.restoreManualCollapses("mine");
        assertFalse(tracker.gain(Skill.MINING, 100, 100, 1));
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("other;mine", ids(tracker));
        GoalTracker.Goal mining = tracker.goals().get(1);
        assertTrue(mining.manuallyCollapsed);
        assertEquals(0, mining.expandedAtNanos);
        GoalTracker restored = GoalTracker.decode(tracker.encode());
        restored.restoreManualCollapses(tracker.encodeManualCollapses());
        assertEquals("other;mine", ids(restored));
        assertTrue(restored.goals().get(1).manuallyCollapsed);
    }

    @Test
    void completedCollapsedGoalOpensWhileRemainingGoalStaysAnchoredAndPersists()
    {
        GoalTracker tracker = GoalTracker.decode("other,FISHING,0,1000,0,false;"
            + "short,MINING,0,100,0,false;long,MINING,0,1000,0,false");
        tracker.restoreManualCollapses("short;long");
        assertTrue(tracker.gain(Skill.MINING, 100, 100, 1));
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("other;short;long", ids(tracker));
        assertEquals("long", tracker.encodeManualCollapses());
        assertTrue(tracker.goals().get(1).completed);
        assertFalse(tracker.goals().get(1).manuallyCollapsed);
        assertEquals(1, tracker.goals().get(1).expandedAtNanos);
        GoalTracker restored = GoalTracker.decode(tracker.encode());
        restored.restoreManualCollapses(tracker.encodeManualCollapses());
        assertEquals("other;short;long", ids(restored));
        assertTrue(restored.goals().get(2).manuallyCollapsed);
    }

    @Test
    void activityReordersOnlyUnanchoredSlotsAndRespectsActivityWindowAfterExpansion()
    {
        GoalTracker tracker = GoalTracker.decode("a,FISHING,0,1000,0,false;"
            + "b,MINING,0,1000,0,false;c,WOODCUTTING,0,1000,0,false;"
            + "d,MINING,0,1000,0,false;e,MINING,0,1000,0,false");
        tracker.restoreManualCollapses("b;d");
        tracker.recordTrainingGain(Skill.MINING, 1, 5);
        assertEquals("e;b;a;d;c", ids(tracker));
        tracker.goals().get(1).manuallyCollapsed = false;
        tracker.recordTrainingGain(Skill.MINING, 2, 5);
        assertEquals("e;b;a;d;c", ids(tracker));
        tracker.recordTrainingGain(Skill.FISHING, 3, 5);
        assertEquals("a;e;b;d;c", ids(tracker));
        tracker.recordTrainingGain(Skill.MINING, 5_000_000_002L, 5);
        assertEquals("e;b;a;d;c", ids(tracker));
        GoalTracker restored = GoalTracker.decode(tracker.encode());
        restored.restoreManualCollapses(tracker.encodeManualCollapses());
        restored.recordTrainingGain(Skill.FISHING, 1, 5);
        assertEquals("a;e;b;d;c", ids(restored));
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
