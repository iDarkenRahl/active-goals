package com.activegoals;

import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActiveGoalsConfigTest
{
    @Test
    void nativeSettingsUseExistingKeysAndDelayBounds() throws Exception
    {
        assertEquals("activegoals", ActiveGoalsConfig.class.getAnnotation(ConfigGroup.class).value());
        Set<String> keys = new HashSet<>();
        for (java.lang.reflect.Method method : ActiveGoalsConfig.class.getDeclaredMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item != null) keys.add(item.keyName());
        }
        assertEquals(Set.of("goalCardExpansionSeconds", "goalOrderingActivitySeconds"), keys);
        assertEquals(ActiveGoalsConfig.COLLAPSE_SECTION,
            ActiveGoalsConfig.class.getMethod("goalCardExpansionSeconds").getAnnotation(ConfigItem.class).section());
        Range range = ActiveGoalsConfig.class.getMethod("goalCardExpansionSeconds").getAnnotation(Range.class);
        assertEquals(1, range.min());
        assertEquals(86400, range.max());
        assertEquals(ActiveGoalsConfig.ORDER_SECTION,
            ActiveGoalsConfig.class.getMethod("goalOrderingActivitySeconds").getAnnotation(ConfigItem.class).section());
        Range orderingRange = ActiveGoalsConfig.class.getMethod("goalOrderingActivitySeconds").getAnnotation(Range.class);
        assertEquals(1, orderingRange.min());
        assertEquals(86400, orderingRange.max());
    }
}
