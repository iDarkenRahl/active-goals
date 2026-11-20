package com.activegoals;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActiveGoalsCurrentLevelTest
{
    @Test
    void incompleteLevelGoalShowsCurrentLevelTooltipInAllCardStatesAndRefreshes() throws Exception
    {
        Fixture f = new Fixture(25, 100_000L, 2_411);
        try
        {
            f.start();
            f.assertTooltip("Level: 1 → 25", "Current Level: 15");

            // A new XP snapshot updates the current level, without changing the starting level.
            when(f.client.getSkillExperience(Skill.MINING)).thenReturn(2_746);
            f.refresh();
            f.assertTooltip("Level: 1 → 25", "Current Level: 16");

            f.goal.expandedAtNanos = 0;
            f.refresh();
            f.assertTooltip("Level: 1 → 25", "Current Level: 16");

            SwingUtilities.invokeAndWait(() -> findButton(f.panel, "Remove Goal").doClick());
            f.flush();
            f.assertLabel("Remove this goal?");
            f.assertTooltip("Level: 1 → 25", "Current Level: 16");
        }
        finally { f.stop(); }
    }

    @Test
    void xpTargetCardKeepsItsExistingTextInAllCardStates() throws Exception
    {
        Fixture f = new Fixture(0, 100_000L, 2_411);
        try
        {
            f.start();
            f.assertLabel("Xp Target: 100,000");
            f.assertTooltip("Xp Target: 100,000", null);
            f.assertNoLevelLine();
            f.goal.expandedAtNanos = 0;
            f.refresh();
            f.assertLabel("Xp: 0 → 100,000");
            f.assertTooltip("Xp: 0 → 100,000", null);
            f.assertNoLevelLine();
            SwingUtilities.invokeAndWait(() -> findButton(f.panel, "Remove Goal").doClick());
            f.flush();
            f.assertLabel("Xp Target: 100,000");
            f.assertTooltip("Xp Target: 100,000", null);
            f.assertNoLevelLine();
        }
        finally { f.stop(); }
    }

    @Test
    void levelGoalTooltipShowsVirtualLevelAbove99() throws Exception
    {
        Fixture f = new Fixture(126, 200_000_000L, 188_884_740);
        try
        {
            f.start();
            f.assertTooltip("Level: 1 → 126", "Current Level: 126");
        }
        finally { f.stop(); }
    }

    @Test
    void completedLevelGoalDoesNotShowTooltipEvenWhenXpExceedsTarget() throws Exception
    {
        Fixture f = new Fixture(25, 7_842L, 100_000);
        f.goal.completed = true;
        try
        {
            f.start();
            f.assertTooltip("Level: 1 → 25", null);
            f.goal.expandedAtNanos = 0;
            f.refresh();
            f.assertTooltip("Level: 1 → 25", null);
            SwingUtilities.invokeAndWait(() -> findButton(f.panel, "Remove Goal").doClick());
            f.flush();
            f.assertTooltip("Level: 1 → 25", null);
        }
        finally { f.stop(); }
    }

    @Test
    void hoveringRebindsTooltipToRefreshedLabelAndClearsOnCompletionAndExit() throws Exception
    {
        Fixture f = new Fixture(25, 100_000L, 2_411);
        Field tooltipField = ActiveGoalsPanel.class.getDeclaredField("levelTooltip");
        tooltipField.setAccessible(true);
        Field hoveredField = ActiveGoalsPanel.class.getDeclaredField("hoveredLevelGoalId");
        hoveredField.setAccessible(true);
        JToolTip tooltip = (JToolTip) tooltipField.get(f.panel);
        try
        {
            f.start();
            SwingUtilities.invokeAndWait(() -> {
                JLabel label = findLabel(f.panel, "Level: 1 → 25");
                assertNotNull(label);
                label.dispatchEvent(new MouseEvent(label, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
                when(f.client.getSkillExperience(Skill.MINING)).thenReturn(2_746);
                f.panel.requestRefresh();
            });
            f.flush();
            SwingUtilities.invokeAndWait(() -> {
                JLabel updated = findLabel(f.panel, "Level: 1 → 25");
                assertSame(updated, tooltip.getComponent());
                assertEquals("Current Level: 16", tooltip.getTipText());
                updated.dispatchEvent(new MouseEvent(updated, MouseEvent.MOUSE_EXITED, 0, 0, 1, 1, 0, false));
                try { assertNull(hoveredField.get(f.panel)); }
                catch (IllegalAccessException e) { throw new AssertionError(e); }
                assertFalse(tooltip.isVisible());
                updated.dispatchEvent(new MouseEvent(updated, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false));
            });
            f.goal.completed = true;
            f.refresh();
            f.flush();
            SwingUtilities.invokeAndWait(() -> {
                try { assertNull(hoveredField.get(f.panel)); }
                catch (IllegalAccessException e) { throw new AssertionError(e); }
                assertFalse(tooltip.isVisible());
                assertNull(findLabel(f.panel, "Level: 1 → 25").getToolTipText());
            });
        }
        finally { f.stop(); }
    }

    private static JButton findButton(Container container, String accessibleName)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JButton && accessibleName.equals(child.getAccessibleContext().getAccessibleName()))
                return (JButton) child;
            if (child instanceof Container)
            {
                JButton found = findButton((Container) child, accessibleName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void collectLabels(Container container, List<String> labels)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JLabel) labels.add(((JLabel) child).getText());
            if (child instanceof Container) collectLabels((Container) child, labels);
        }
    }

    private static JLabel findLabel(Container parent, String text)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText())) return (JLabel) child;
            if (child instanceof Container)
            {
                JLabel found = findLabel((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Fixture
    {
        final ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        final Client client = mock(Client.class);
        final GoalTracker.Goal goal;
        final ActiveGoalsPanel panel;

        Fixture(int targetLevel, long targetXp, int currentXp)
        {
            GoalTracker tracker = new GoalTracker();
            goal = tracker.restore("test-goal", Skill.MINING, 0, targetXp, targetLevel);
            goal.expandedAtNanos = 1;
            when(plugin.individualGoals()).thenReturn(tracker.goals());
            when(plugin.getClient()).thenReturn(client);
            when(plugin.goalDataReady()).thenReturn(true);
            when(plugin.goalClockNanos()).thenReturn(2L);
            when(plugin.getGoalCardExpansionSeconds()).thenReturn(300);
            when(client.getSkillExperience(Skill.MINING)).thenReturn(currentXp);
            when(client.getBoostedSkillLevel(Skill.MINING)).thenReturn(99);
            doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
                .when(plugin).runOnClientThread(any(Runnable.class));
            panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        }

        void start() throws Exception { SwingUtilities.invokeAndWait(panel::start); flush(); }
        void stop() throws Exception { SwingUtilities.invokeAndWait(panel::stop); }
        void refresh() throws Exception { SwingUtilities.invokeAndWait(panel::requestRefresh); flush(); }
        void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

        void assertLabel(String expected) throws Exception
        {
            SwingUtilities.invokeAndWait(() ->
            {
                List<String> labels = new ArrayList<>();
                collectLabels(panel, labels);
                assertTrue(labels.contains(expected), "Expected " + expected + " in " + labels);
            });
        }

        void assertNoLevelLine() throws Exception
        {
            SwingUtilities.invokeAndWait(() ->
            {
                List<String> labels = new ArrayList<>();
                collectLabels(panel, labels);
                assertTrue(labels.stream().noneMatch(label -> label != null && label.startsWith("Level:")), labels.toString());
            });
        }

        void assertTooltip(String text, String tooltip) throws Exception
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JLabel label = findLabel(panel, text);
                assertNotNull(label, "Expected label: " + text);
                assertEquals(tooltip, label.getToolTipText());
            });
        }
    }
}
