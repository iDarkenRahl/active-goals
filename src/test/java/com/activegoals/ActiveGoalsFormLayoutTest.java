package com.activegoals;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JToolTip;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveGoalsFormLayoutTest
{
    @Test
    void staleCreateFormCannotCreateGoalAfterProfileSwitch() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        when(plugin.individualGoals()).thenReturn(new GoalTracker().goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(plugin.goalActionSession()).thenReturn(1L);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> button(panel, "Add Goal").doClick(0));
            SwingUtilities.invokeAndWait(() -> {
                descendants(panel, JComboBox.class).get(0).setSelectedItem(Skill.MINING);
                descendants(panel, JTextField.class).get(0).setText("20");
                when(plugin.goalActionSession()).thenReturn(2L);
                doThrow(new IllegalStateException("Profile changed.")).when(plugin).requireGoalAction(1L);
                button(panel, "Create Goal").doClick(0);
            });
            SwingUtilities.invokeAndWait(() -> { });
            verify(plugin, never()).addIndividualGoal(any(), anyInt(), anyLong());
            assertFalse(hasLabel(panel, "Profile changed."));
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void staleGoalCardActionsDoNotMutateGoals() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.MINING, 1000, 2000, 1);
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        when(plugin.goalDataReady()).thenReturn(true);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        doThrow(new IllegalStateException("Profile changed.")).when(plugin).requireGoalAction(anyLong());
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> button(panel, "Expand goal").doClick(0));
            SwingUtilities.invokeAndWait(() -> button(panel, "Remove Goal").doClick(0));
            SwingUtilities.invokeAndWait(() -> button(panel, "Remove Goal").doClick(0));
            SwingUtilities.invokeAndWait(() -> { });
            verify(plugin, never()).setIndividualGoalCollapsed(eq(goal.id), anyBoolean());
            verify(plugin, never()).removeIndividualGoal(goal.id);
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void emptyStateTracksReadinessFormsAndGoalRecords() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1500);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(hasLabel(panel, "Log in to add goals."));
                assertFalse(hasEmptyState(panel));
                when(plugin.goalDataReady()).thenReturn(true);
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(hasEmptyState(panel));
                button(panel, "Add Goal").doClick(0);
            });
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(hasEmptyState(panel));
                button(panel, "Cancel").doClick(0);
            });
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(hasEmptyState(panel));
                tracker.add(Skill.MINING, 1000, 2500, 0);
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(hasEmptyState(panel));
                assertEquals(1, descendants(panel, JProgressBar.class).size());
                tracker.goals().get(0).targetXp = 0;
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(hasEmptyState(panel));
                assertTrue(descendants(panel, JProgressBar.class).isEmpty());
                tracker.remove(tracker.goals().get(0).id);
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> assertTrue(hasEmptyState(panel)));
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void corruptedSavePlaceholderIsNotAnEmptyState() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        GoalTracker tracker = GoalTracker.decode("unreadable");
        assertEquals(1, tracker.goals().size());
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.goalDataReady()).thenReturn(true);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(hasEmptyState(panel));
                assertTrue(hasLabel(panel, "Corrupted Save"));
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    private static boolean hasEmptyState(Container panel)
    {
        return hasLabel(panel, "No goals yet. Select + to add one.");
    }

    private static boolean hasLabel(Container panel, String text)
    {
        return descendants(panel, JLabel.class).stream().anyMatch(label -> text.equals(label.getText()));
    }

    @Test
    void hoveredGoalTooltipRebindsToUpdatedBarOnRefresh() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        tracker.add(Skill.MINING, 100_000, 350_000, 0);
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(150_000, 160_000);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        Field tooltipField = ActiveGoalsPanel.class.getDeclaredField("progressTooltip");
        tooltipField.setAccessible(true);
        JToolTip tooltip = (JToolTip) tooltipField.get(panel);
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> {
                JProgressBar original = descendants(panel, JProgressBar.class).get(0);
                original.dispatchEvent(new MouseEvent(original, MouseEvent.MOUSE_ENTERED,
                    0, 0, 1, 1, 0, false));
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> {
                JProgressBar updated = descendants(panel, JProgressBar.class).get(0);
                assertSame(updated, tooltip.getComponent());
                assertEquals("60,000 / 250,000 XP", tooltip.getTipText());
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void progressTooltipUsesGoalXpAndTracksTargetAndCurrentXp() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.MINING, 100_000, 350_000, 0);
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(150_000, 90_000, 400_000);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("50,000 / 250,000 XP", descendants(panel, JProgressBar.class).get(0).getToolTipText());
                goal.targetXp = 300_000;
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("0 / 200,000 XP", descendants(panel, JProgressBar.class).get(0).getToolTipText());
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> assertEquals("200,000 / 200,000 XP",
                descendants(panel, JProgressBar.class).get(0).getToolTipText()));
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void inlineRemovalSurvivesRefreshAndTargetsOnlyTheSelectedGoal() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal other = tracker.add(Skill.MINING, 1000, 2500, 0);
        GoalTracker.Goal selected = tracker.add(Skill.MINING, 1000, 2500, 0);
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1500);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        doAnswer(call -> { tracker.remove(call.getArgument(0)); return null; })
            .when(plugin).removeIndividualGoal(anyString());
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> button(panel, "Remove Goal").doClick(0));
            SwingUtilities.invokeAndWait(() ->
            {
                assertFalse(button(panel, "Add Goal").isEnabled());
                assertTrue(descendants(panel, JButton.class).stream()
                    .filter(b -> "Remove Goal".equals(b.getToolTipText()))
                    .noneMatch(JButton::isEnabled));
                assertFalse(button(panel, "Edit Goal").isEnabled());
                JButton cancel = button(panel, "Cancel");
                assertEquals("Remove Goal", ((JButton) cancel.getParent().getComponent(0)).getText());
                assertSame(cancel, cancel.getParent().getComponent(1));
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() -> button(panel, "Cancel").doClick(0));
            SwingUtilities.invokeAndWait(() -> button(panel, "Remove Goal").doClick(0));
            SwingUtilities.invokeAndWait(() -> descendants(panel, JButton.class).stream()
                .filter(b -> "Remove Goal".equals(b.getText())).findFirst().orElseThrow().doClick(0));
            verify(plugin).removeIndividualGoal(selected.id);
            assertEquals(List.of(other), tracker.goals());
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void pencilOnCollapsedGoalOpensTheSharedEditorForThatGoal() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal finished = tracker.add(Skill.MINING, 1000, 1500, 0);
        finished.completed = true;
        GoalTracker.Goal editable = tracker.add(Skill.MINING, 1000, 2500, 0);
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1500);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));

        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() ->
            {
                List<JButton> buttons = descendants(panel, JButton.class);
                assertEquals(1, buttons.stream().filter(b -> "Edit Goal".equals(b.getToolTipText())).count());
                JButton pencil = button(panel, "Edit Goal");
                Container controls = pencil.getParent();
                assertSame(button(panel, "Remove Goal"), controls.getComponent(1));
                pencil.doClick(0);
            });
            SwingUtilities.invokeAndWait(() ->
            {
                assertFalse(button(panel, "Add Goal").isEnabled());
                assertFalse(button(panel, "Edit Goal").isEnabled());
                assertFalse(button(panel, "Remove Goal").isEnabled());
                JButton cancel = button(panel, "Cancel");
                assertEquals("Save Changes", ((JButton) cancel.getParent().getComponent(0)).getText());
                assertSame(cancel, cancel.getParent().getComponent(1));
                JComboBox<?> mode = descendants(panel, JComboBox.class).get(0);
                JTextField amount = descendants(panel, JTextField.class).get(0);
                assertEquals("Xp", mode.getSelectedItem());
                assertEquals("2,500", amount.getText());
                mode.setSelectedItem("Level");
                amount.setText("20");
                button(panel, "Save Changes").doClick(0);
            });
            verify(plugin).editIndividualGoal(editable.id, 20, ActiveGoalsPlugin.xpForLevel(20));
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void confirmationClosesWhenCompletedGoalDisappears() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.MINING, 1000, 1500, 0);
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1400);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> button(panel, "Remove Goal").doClick(0));
            SwingUtilities.invokeAndWait(() ->
            {
                goal.completed = true;
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(button(panel, "Cancel").isEnabled());
                tracker.remove(goal.id);
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(button(panel, "Add Goal").isEnabled());
                assertTrue(descendants(panel, JButton.class).stream().noneMatch(b -> "Cancel".equals(b.getText())));
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    @Test
    void goalFormIsLeftAlignedAndKeepsControlsAtUsableHeight() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                ActiveGoalsPanel panel = new ActiveGoalsPanel(mock(ActiveGoalsPlugin.class), mock(SkillIconManager.class));
                Class<?> goal = Class.forName(ActiveGoalsPanel.class.getName() + "$GoalSnapshot");
                Method method = ActiveGoalsPanel.class.getDeclaredMethod("createGoalForm", goal, long.class);
                method.setAccessible(true);
                JPanel form = (JPanel) method.invoke(panel, null, 0L);
                assertEquals(Component.LEFT_ALIGNMENT, form.getAlignmentX());
                for (Component child : form.getComponents())
                {
                    if (child instanceof javax.swing.JComboBox || child instanceof JPanel)
                        assertTrue(child.getMaximumSize().height <= child.getPreferredSize().height + 2,
                            "Controls should not stretch vertically");
                }
            }
            catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        });
    }

    @Test
    void refreshKeepsOpenFormAndUpdatesCompletedGoalRate() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.MINING, 1000, 2000, 1);
        goal.completed = true;
        goal.rateXp = 1000;
        goal.ratePeriodStartNanos = 1_000_000_000L;
        goal.completedAtNanos = 121_000_000_000L;
        goal.expandedAtNanos = goal.completedAtNanos;
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(plugin.goalClockNanos()).thenReturn(181_000_000_000L, 241_000_000_000L, 301_000_000_000L);
        when(plugin.getGoalCardExpansionSeconds()).thenReturn(1000);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(2000);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> button(panel, "Add Goal").doClick(0));
            SwingUtilities.invokeAndWait(() ->
            {
                JTextField input = descendants(panel, JTextField.class).get(0);
                input.setText("12345");
                input.setCaretPosition(2);
                panel.requestRefresh();
            });
            SwingUtilities.invokeAndWait(() ->
            {
                JTextField input = descendants(panel, JTextField.class).get(0);
                assertEquals("12345", input.getText());
                assertEquals(2, input.getCaretPosition());
                assertTrue(descendants(panel, javax.swing.JLabel.class).stream()
                    .anyMatch(label -> "30,000".equals(label.getText())));
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }
    private static JButton button(Container parent, String name)
    {
        return descendants(parent, JButton.class).stream()
            .filter(b -> name.equals(b.getToolTipText()) || name.equals(b.getText()))
            .findFirst().orElseThrow();
    }

    private static <T extends Component> List<T> descendants(Container parent, Class<T> type)
    {
        List<T> matches = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (type.isInstance(child)) matches.add(type.cast(child));
            if (child instanceof Container) matches.addAll(descendants((Container) child, type));
        }
        return matches;
    }
}
