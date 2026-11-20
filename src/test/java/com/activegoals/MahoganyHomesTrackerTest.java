package com.activegoals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MahoganyHomesTrackerTest
{
    private static final long SECOND = 1_000_000_000L;
    private static final String DONE = "You have completed 123 contracts with a total of 456 points.";

    private static void assign(MahoganyHomesTracker tracker, String tier, int xp, int outfit, long now)
    {
        tracker.observeMessage(tier + " Contract: Go see Bob in Varrock.", xp, outfit, now);
    }

    private static void complete(MahoganyHomesTracker tracker, int before, int after, int outfit, long now)
    {
        tracker.observeMessage(DONE, before, outfit, now);
        tracker.onTick(after, outfit, now + 1);
    }

    @Test
    void estimatesFromWholeContractAndFreezesUntilNextCompletion()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        assign(tracker, "Expert", 1000, 0, SECOND);
        assertEquals(-1, tracker.estimateHomes(4000, SECOND, 300));
        // The completion message arrives before the final XP drop.
        complete(tracker, 1500, 2000, 0, 2 * SECOND);
        assertEquals(2, tracker.estimateHomes(4000, 3 * SECOND, 300));
        assign(tracker, "Expert", 2000, 0, 4 * SECOND);
        tracker.onTick(2500, 0, 5 * SECOND);
        assertEquals(2, tracker.estimateHomes(4000, 5 * SECOND, 300));
        // XP can also arrive before the completion message.
        complete(tracker, 3000, 3000, 0, 6 * SECOND);
        assertEquals(1, tracker.estimateHomes(4000, 7 * SECOND, 300));
        assertEquals(2, tracker.estimateHomes(4001, 7 * SECOND, 300));
    }

    @Test
    void keepsTierSamplesSeparateAndIgnoresUnknownContractAtLogin()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        tracker.observeMessage(DONE, 1000, 0, SECOND);
        tracker.onTick(1200, 0, SECOND + 1);
        assertEquals(-1, tracker.estimateHomes(5000, SECOND + 2, 300));
        assign(tracker, "Adept", 1200, 0, 2 * SECOND);
        complete(tracker, 1700, 2200, 0, 3 * SECOND);
        assign(tracker, "Expert", 2200, 0, 4 * SECOND);
        assertEquals(-1, tracker.estimateHomes(5200, 4 * SECOND, 300));
        complete(tracker, 3200, 4200, 0, 5 * SECOND);
        assertEquals(1, tracker.estimateHomes(5200, 5 * SECOND + 1, 300));
        assign(tracker, "Adept", 4200, 0, 6 * SECOND);
        assertEquals(1, tracker.estimateHomes(5200, 6 * SECOND, 300));
    }

    @Test
    void onlyLastTwentyContractsContribute()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        int xp = 0;
        for (int i = 0; i < 21; i++)
        {
            assign(tracker, "Novice", xp, 0, (2L * i + 1) * SECOND);
            int gain = i == 0 ? 10_000 : 100;
            complete(tracker, xp + gain, xp + gain, 0, (2L * i + 2) * SECOND);
            xp += gain;
        }
        assertEquals(10, tracker.estimateHomes(xp + 1000, 43 * SECOND, 300));
    }

    @Test
    void changedOrUnknownOutfitInvalidatesAndDoesNotReuseStaleSamples()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        assign(tracker, "Adept", 1000, 1, SECOND);
        complete(tracker, 1500, 2000, 1, 2 * SECOND);
        assertEquals(2, tracker.estimateHomes(4000, 3 * SECOND, 300));
        assign(tracker, "Adept", 2000, 1, 4 * SECOND);
        tracker.observeOutfit(3);
        complete(tracker, 2500, 3000, 3, 5 * SECOND);
        assertEquals(-1, tracker.estimateHomes(4000, 6 * SECOND, 300));
        assign(tracker, "Adept", 3000, 3, 7 * SECOND);
        complete(tracker, 3500, 4000, 3, 8 * SECOND);
        assertEquals(1, tracker.estimateHomes(5000, 8 * SECOND + 1, 300));
        assign(tracker, "Adept", 4000, 3, 9 * SECOND);
        tracker.observeOutfit(-1);
        assertEquals(-1, tracker.estimateHomes(5000, 9 * SECOND, 300));
        tracker.observeOutfit(3);
        complete(tracker, 4500, 5000, 3, 10 * SECOND);
        // The unknown-equipment contract is discarded; the previous valid sample survives.
        assertEquals(1, tracker.estimateHomes(6000, 10 * SECOND + 1, 300));
    }

    @Test
    void inactivityExpiresEstimateButNewCompletionReactivatesIt()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        assign(tracker, "Beginner", 1000, 0, SECOND);
        complete(tracker, 1500, 2000, 0, 2 * SECOND);
        assertEquals(-1, tracker.estimateHomes(3000, 7 * SECOND, 5));
        assertEquals(-1, tracker.estimateHomes(3000, 7 * SECOND, 300));
        assign(tracker, "Beginner", 2000, 0, 8 * SECOND);
        complete(tracker, 2500, 3000, 0, 9 * SECOND);
        assertEquals(1, tracker.estimateHomes(4000, 9 * SECOND + 1, 5));
        tracker.reset();
        assertEquals(-1, tracker.estimateHomes(4000, 9 * SECOND + 2, 5));
    }

    @Test
    void xpDecreaseDoesNotEnterAverage()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        assign(tracker, "Expert", 2100, 0, 4 * SECOND);
        tracker.onTick(2000, 0, 5 * SECOND);
        complete(tracker, 2500, 3000, 0, 6 * SECOND);
        assertEquals(-1, tracker.estimateHomes(4000, 7 * SECOND, 300));
    }

    @Test
    void overlappingAssignmentsDiscardAmbiguousSamples()
    {
        MahoganyHomesTracker tracker = new MahoganyHomesTracker();
        assign(tracker, "Expert", 1000, 0, SECOND);
        tracker.observeMessage(DONE, 1500, 0, 2 * SECOND);
        // The next contract arrives before the completion tick; the XP boundary is uncertain.
        assign(tracker, "Expert", 1500, 0, 2 * SECOND + 1);
        tracker.onTick(2100, 0, 2 * SECOND + 2);
        assertEquals(-1, tracker.estimateHomes(4000, 3 * SECOND, 300));
        complete(tracker, 2500, 3000, 0, 4 * SECOND);
        assertEquals(-1, tracker.estimateHomes(4000, 5 * SECOND, 300));
        assign(tracker, "Expert", 3000, 0, 6 * SECOND);
        complete(tracker, 3500, 4000, 0, 7 * SECOND);
        assertEquals(1, tracker.estimateHomes(5000, 7 * SECOND + 1, 300));
    }
}
