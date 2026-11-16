package com.activegoals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveGoalsReliabilityTest
{
    @Test
    void customXpTargetsMustBeValid()
    {
        assertFalse(ActiveGoalsPlugin.isValidCustomXpTarget(null));
        assertFalse(ActiveGoalsPlugin.isValidCustomXpTarget("abc"));
        assertFalse(ActiveGoalsPlugin.isValidCustomXpTarget("0"));
        assertFalse(ActiveGoalsPlugin.isValidCustomXpTarget("200000001"));
        assertTrue(ActiveGoalsPlugin.isValidCustomXpTarget("1"));
        assertTrue(ActiveGoalsPlugin.isValidCustomXpTarget("200000000"));
    }

}
