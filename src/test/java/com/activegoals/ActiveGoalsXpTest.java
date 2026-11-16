package com.activegoals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveGoalsXpTest
{
    @Test
    void levelTwoRequires83Xp()
    {
        assertEquals(83, ActiveGoalsPlugin.xpForLevel(2));
    }

    @Test
    void level99Requires13034431Xp()
    {
        assertEquals(13034431, ActiveGoalsPlugin.xpForLevel(99));
    }

    @Test
    void zeroXpIsLevelOne()
    {
        assertEquals(1, ActiveGoalsPlugin.virtualLevelForXp(0));
    }

    @Test
    void xpAtLevelTwoIsLevelTwo()
    {
        assertEquals(2, ActiveGoalsPlugin.virtualLevelForXp(83));
    }
    @Test
    void virtualLevelBoundaries()
    {
        assertEquals(104273167, ActiveGoalsPlugin.xpForLevel(120));
        assertEquals(188884740, ActiveGoalsPlugin.xpForLevel(126));
        assertEquals(125, ActiveGoalsPlugin.virtualLevelForXp(188884739));
        assertEquals(126, ActiveGoalsPlugin.virtualLevelForXp(188884740));
        assertEquals(126, ActiveGoalsPlugin.virtualLevelForXp(200000000));
    }
}
