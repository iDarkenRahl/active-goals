package com.activegoals;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgilityIntegrationTest
{
    @Test
    void completedLapXpIsRecognizedAndLogoutClearsIt() throws Exception
    {
        Fixture f = new Fixture();
        f.lap();
        assertEquals(120, f.plugin.agilityXpPerLap());
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        f.plugin.onGameStateChanged(logout);
        assertEquals(-1, f.plugin.agilityXpPerLap());
    }

    @Test
    void profileChangeClearsRecognizedLap() throws Exception
    {
        Fixture f = new Fixture();
        f.lap();
        assertEquals(120, f.plugin.agilityXpPerLap());
        when(f.config.getRSProfileKey()).thenReturn("other");
        f.plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("first", "other"));
        assertEquals(-1, f.plugin.agilityXpPerLap());
    }

    @Test
    void changedProfileKeyHidesOldLapBeforeProfileEvent() throws Exception
    {
        Fixture f = new Fixture();
        f.lap();
        assertEquals(120, f.plugin.agilityXpPerLap());
        when(f.config.getRSProfileKey()).thenReturn("other");
        assertEquals(-1, f.plugin.agilityXpPerLap());
    }

    @Test
    void dashboardSwitchesBetweenRoundedLapsAndActionSamples() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.AGILITY, 1000, 1201, 1);
        goal.samples.add(100);
        goal.expandedAtNanos = 1;
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(plugin.goalClockNanos()).thenReturn(2L);
        when(plugin.getGoalCardExpansionSeconds()).thenReturn(300);
        when(client.getSkillExperience(Skill.AGILITY)).thenReturn(1000);
        when(plugin.agilityXpPerLap()).thenReturn(120.0);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(labels(panel).contains("Laps Left:"));
                assertTrue(labels(panel).contains("2"));
            });
            when(plugin.agilityXpPerLap()).thenReturn(-1.0);
            SwingUtilities.invokeAndWait(panel::requestRefresh);
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(labels(panel).contains("Actions Left:"));
                assertTrue(labels(panel).contains("3"));
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(panel::stop);
        }
    }

    private static List<String> labels(Container parent)
    {
        List<String> result = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (child instanceof JLabel) result.add(((JLabel) child).getText());
            if (child instanceof Container) result.addAll(labels((Container) child));
        }
        return result;
    }

    private static final class Fixture
    {
        final ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        final ConfigManager config = mock(ConfigManager.class);
        final Client client = mock(Client.class);

        Fixture() throws Exception
        {
            Player player = mock(Player.class);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(3103, 3261, 0));
            when(client.getLocalPlayer()).thenReturn(player);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(config.getRSProfileKey()).thenReturn("first");
            set("client", client);
            set("configManager", config);
            set("activeGoalsPanel", mock(ActiveGoalsPanel.class));
            set("loadedRSProfileKey", "first");
            set("individualGoalsLoaded", true);
            @SuppressWarnings("unchecked")
            Map<Skill, Integer> xp = (Map<Skill, Integer>) field("lastXp").get(plugin);
            xp.put(Skill.AGILITY, 1000);
        }

        void lap()
        {
            plugin.onStatChanged(new StatChanged(Skill.AGILITY, 1120, 10, 10));
        }

        void set(String name, Object value) throws Exception { field(name).set(plugin, value); }
        Field field(String name) throws Exception
        {
            Field field = ActiveGoalsPlugin.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
