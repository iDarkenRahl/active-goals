package com.activegoals;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SkillIconManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoalCreationTimeTest
{
    @Test
    void existingRecordsAndMalformedMetadataRemainReadable()
    {
        String legacy = "a,MINING,1000,2000,0,false;b,FISHING,100,3000,0,false";
        GoalTracker tracker = GoalTracker.decode(legacy);
        tracker.restoreCreationTimes("a,1700000000000;b,not-a-time;missing,1700000000000;bad");
        assertEquals(legacy, tracker.encode());
        assertEquals(1700000000000L, tracker.goals().get(0).createdAtEpochMillis);
        assertNull(tracker.goals().get(1).createdAtEpochMillis);
        GoalTracker noMetadata = GoalTracker.decode(legacy);
        noMetadata.restoreCreationTimes(null);
        assertNull(noMetadata.goals().get(0).createdAtEpochMillis);
        assertNull(noMetadata.goals().get(1).createdAtEpochMillis);
    }

    @Test
    void createdDateTracksTargetChangesAndSurvivesReload() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        Map<String, String> saved = new HashMap<>();
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        ActiveGoalsPlugin plugin = plugin(client, config);
        long before = System.currentTimeMillis();
        plugin.addIndividualGoal(Skill.MINING, 0, 2000);
        long after = System.currentTimeMillis();
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        assertNotNull(goal.createdAtEpochMillis);
        assertTrue(goal.createdAtEpochMillis >= before && goal.createdAtEpochMillis <= after);
        goal.createdAtEpochMillis = 1L;
        String id = goal.id;
        assertEquals(6, saved.get("individualGoals").split(",", -1).length);
        plugin.editIndividualGoal(id, 0, 2000);
        assertEquals(1L, goal.createdAtEpochMillis);
        long editedBefore = System.currentTimeMillis();
        plugin.editIndividualGoal(id, 0, 3000);
        long editedAfter = System.currentTimeMillis();
        long updated = goal.createdAtEpochMillis;
        assertTrue(updated >= editedBefore && updated <= editedAfter);

        ActiveGoalsPlugin restarted = plugin(client, config);
        GoalTracker.Goal restored = restarted.individualGoals().get(0);
        assertEquals(id, restored.id);
        assertEquals(updated, restored.createdAtEpochMillis);
        restarted.removeIndividualGoal(id);
        assertTrue(restarted.individualGoals().isEmpty());
        assertFalse(saved.getOrDefault("goalCreationTimes", "").contains(id));
    }

    @Test
    void savingUnknownDateAndChangingTargetTypeUpdatesDateWithoutResettingProgress() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        Map<String, String> saved = new HashMap<>();
        saved.put("individualGoals", "legacy,MINING,1000,3000,0,false");
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1500);
        ActiveGoalsPlugin plugin = plugin(client, config);
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        goal.sessionXp = 200;
        assertNull(goal.createdAtEpochMillis);
        long before = System.currentTimeMillis();
        plugin.editIndividualGoal(goal.id, 0, 3000);
        long after = System.currentTimeMillis();
        assertTrue(goal.createdAtEpochMillis >= before && goal.createdAtEpochMillis <= after);
        assertEquals(goal.createdAtEpochMillis, plugin(client, config).individualGoals().get(0).createdAtEpochMillis);
        assertEquals(1000, goal.startXp);
        assertEquals(200, goal.sessionXp);
        assertEquals(0.25, goal.progress(1500));

        int level = 20;
        int levelXp = ActiveGoalsPlugin.xpForLevel(level);
        plugin.editIndividualGoal(goal.id, level, levelXp);
        goal.createdAtEpochMillis = 1L;
        before = System.currentTimeMillis();
        plugin.editIndividualGoal(goal.id, 0, levelXp);
        after = System.currentTimeMillis();
        assertTrue(goal.createdAtEpochMillis >= before && goal.createdAtEpochMillis <= after);
        assertEquals(goal.createdAtEpochMillis, plugin(client, config).individualGoals().get(0).createdAtEpochMillis);
    }

    @Test
    void metadataIsIsolatedByProfile() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        AtomicReference<String> profile = new AtomicReference<>("a");
        Map<String, Map<String, String>> saved = new HashMap<>();
        when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.getOrDefault(profile.get(), Map.of()).get(call.getArgument(1)));
        doAnswer(call -> { saved.computeIfAbsent(profile.get(), key -> new HashMap<>())
            .put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        ActiveGoalsPlugin a = plugin(client, config);
        a.addIndividualGoal(Skill.MINING, 0, 2000);
        String id = a.individualGoals().get(0).id;
        long created = a.individualGoals().get(0).createdAtEpochMillis;
        profile.set("b");
        saved.computeIfAbsent("b", key -> new HashMap<>()).put("individualGoals", id + ",MINING,1000,2000,0,false");
        ActiveGoalsPlugin b = plugin(client, config);
        assertNull(b.individualGoals().get(0).createdAtEpochMillis);
        profile.set("a");
        assertEquals(created, plugin(client, config).individualGoals().get(0).createdAtEpochMillis);
    }

    @Test
    void completedGoalKeepsCreationTimeUntilAutomaticRemoval() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        Map<String, String> saved = new HashMap<>();
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        ActiveGoalsPlugin plugin = plugin(client, config);
        plugin.addIndividualGoal(Skill.MINING, 0, 2000);
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        goal.completed = true;
        goal.completedAtNanos = System.nanoTime() - 61_000_000_000L;
        assertTrue(saved.get("goalCreationTimes").contains(goal.id));
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.individualGoals().isEmpty());
        assertFalse(saved.getOrDefault("goalCreationTimes", "").contains(goal.id));
    }

    @Test
    void expandedCardShowsKnownAndUnknownCreationTimes() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal unknown = tracker.add(Skill.MINING, 1000, 2000, 1);
        GoalTracker.Goal known = tracker.add(Skill.MINING, 1000, 3000, 1);
        known.createdAtEpochMillis = 1_700_000_000_000L;
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(plugin.goalClockNanos()).thenReturn(2L);
        when(plugin.getGoalCardExpansionSeconds()).thenReturn(300);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1500);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        String expected = DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(known.createdAtEpochMillis));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(hasLabel(panel, "Created:"));
                assertTrue(hasLabel(panel, expected));
                assertTrue(hasLabel(panel, "Unknown"));
            });
        }
        finally { SwingUtilities.invokeAndWait(panel::stop); }
    }

    private static ActiveGoalsPlugin plugin(Client client, ConfigManager config) throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        return plugin;
    }

    private static void inject(Object target, String name, Object value) throws Exception
    {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static boolean hasLabel(Container container, String text)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText())) return true;
            if (child instanceof Container && hasLabel((Container) child, text)) return true;
        }
        return false;
    }
}
