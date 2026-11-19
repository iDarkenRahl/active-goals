package com.activegoals;

import java.lang.reflect.Field;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ActiveGoalsTrackingTest
{
    @Test
    void profileChangesInvalidatePendingActionsEvenAfterReturning() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.concurrent.atomic.AtomicReference<String> profile = new java.util.concurrent.atomic.AtomicReference<>("a");
        when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        long originalSession = plugin.goalActionSession();
        plugin.requireGoalAction(originalSession);

        profile.set(null);
        assertFalse(plugin.goalDataReady());
        assertThrows(IllegalStateException.class, () -> plugin.addIndividualGoal(Skill.MINING, 0, 2000));
        plugin.onRuneScapeProfileChanged(null);
        assertThrows(IllegalStateException.class, () -> plugin.requireGoalAction(originalSession));
        profile.set("a");
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.goalDataReady());
        assertThrows(IllegalStateException.class, () -> plugin.requireGoalAction(originalSession));
        plugin.requireGoalAction(plugin.goalActionSession());
        assertTrue(plugin.individualGoals().isEmpty());
    }

    @Test
    void savedManualCollapseRestoresAndExpansionEditAndDeletionClearIt() throws Exception
    {
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);

        ActiveGoalsPlugin first = new ActiveGoalsPlugin();
        inject(first, "client", client);
        inject(first, "configManager", config);
        inject(first, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(first, "xpBaselineTicks", 1);
        first.onGameTick(new GameTick());
        first.addIndividualGoal(Skill.MINING, 0, 2000);
        String id = first.individualGoals().get(0).id;
        first.setIndividualGoalCollapsed(id, true);
        assertEquals(id, saved.get("manuallyCollapsedGoals"));

        ActiveGoalsPlugin restarted = new ActiveGoalsPlugin();
        inject(restarted, "client", client);
        inject(restarted, "configManager", config);
        inject(restarted, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(restarted, "xpBaselineTicks", 1);
        restarted.onGameTick(new GameTick());
        GoalTracker.Goal goal = restarted.individualGoals().get(0);
        assertTrue(goal.manuallyCollapsed);
        restarted.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        assertEquals(0, goal.expandedAtNanos);
        assertEquals(100, goal.sessionXp);
        restarted.setIndividualGoalCollapsed(id, false);
        assertFalse(goal.manuallyCollapsed);
        assertNotEquals(0, goal.expandedAtNanos);
        assertEquals("", saved.get("manuallyCollapsedGoals"));

        restarted.setIndividualGoalCollapsed(id, true);
        restarted.editIndividualGoal(id, 0, 3000);
        assertFalse(goal.manuallyCollapsed);
        assertNotEquals(0, goal.expandedAtNanos);
        assertEquals("", saved.get("manuallyCollapsedGoals"));
        restarted.setIndividualGoalCollapsed(id, true);
        restarted.removeIndividualGoal(id);
        assertEquals("", saved.get("manuallyCollapsedGoals"));
    }

    @Test
    void loginCompletionClearsSavedManualCollapse() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(config.getRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "individualGoals"))
            .thenReturn("id,MINING,1000,1200,0,false");
        when(config.getRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "manuallyCollapsedGoals"))
            .thenReturn("id");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1200);
        plugin.onGameTick(new GameTick());
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        assertTrue(goal.completed);
        assertFalse(goal.manuallyCollapsed);
        assertNotEquals(0, goal.expandedAtNanos);
        verify(config).setRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "manuallyCollapsedGoals", "");
    }

    @Test
    void xpCompletionClearsSavedManualCollapse() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        when(config.getRSProfileKey()).thenReturn("character-a");
        when(config.getRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "individualGoals"))
            .thenReturn("id,MINING,1000,1200,0,false");
        when(config.getRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "manuallyCollapsedGoals"))
            .thenReturn("id");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        plugin.onGameTick(new GameTick());
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1200, 9, 9));
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        assertTrue(goal.completed);
        assertFalse(goal.manuallyCollapsed);
        assertNotEquals(0, goal.expandedAtNanos);
        verify(config).setRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "manuallyCollapsedGoals", "");
    }

    @Test
    void eachCharacterRestoresOnlyTheirOwnGoals() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicReference<String> profile = new java.util.concurrent.atomic.AtomicReference<>("a");
        when(config.getRSProfileKey()).thenAnswer(invocation -> profile.get());
        when(config.getRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "individualGoals"))
            .thenAnswer(invocation -> saved.get(profile.get()));
        doAnswer(invocation -> { saved.put(profile.get(), invocation.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), eq("individualGoals"), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        plugin.addIndividualGoal(Skill.MINING, 0, 2000);
        GoalTracker.Goal original = plugin.individualGoals().get(0);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        profile.set("b");
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.individualGoals().isEmpty());
        plugin.addIndividualGoal(Skill.MINING, 0, 3000);
        plugin.onGameStateChanged(logout);
        profile.set("a");
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(1, plugin.individualGoals().size());
        assertEquals(2000, plugin.individualGoals().get(0).targetXp);
        assertSame(original, plugin.individualGoals().get(0));
        assertEquals(100, original.sessionXp);
        assertEquals(100, original.rateXp);
        assertEquals(100, original.samples.getFirst());
    }
    @Test
    void logoutClearsDisplayedGoalsAndSessionButWorldHopDoesNot() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "loadedRSProfileKey", "character-a");
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "individualGoalsLoaded", true);
        plugin.addIndividualGoal(Skill.MINING, 0, 2000);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.HOPPING);
        plugin.onGameStateChanged(event);
        assertEquals(1, plugin.individualGoals().size());
        event.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(event);
        assertTrue(plugin.individualGoals().isEmpty());
        assertEquals(0, plugin.getSessionOverviewTotalXp());
        assertEquals(0, plugin.getSessionSeconds());
    }
    @Test
    void sessionOverviewCountsSkillsWithoutGoalsAndSurvivesGoalRemoval() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        @SuppressWarnings("unchecked")
        java.util.Map<Skill, Integer> baseline = (java.util.Map<Skill, Integer>) field(plugin, "lastXp").get(plugin);
        baseline.put(Skill.MINING, 1000);
        baseline.put(Skill.FISHING, 2000);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1050, 9, 9));
        plugin.onStatChanged(new StatChanged(Skill.FISHING, 2030, 9, 9));
        assertEquals(80, plugin.getSessionOverviewTotalXp());
        plugin.clearTarget(Skill.MINING);
        assertEquals(80, plugin.getSessionOverviewTotalXp());
        plugin.clearSessionOverviewXp();
        assertEquals(0, plugin.getSessionOverviewTotalXp());
    }
    @Test
    void progressUsesGoalStartingXp()
    {
        assertEquals(0.0, ActiveGoalsPlugin.goalProgress(1_000, 1_000, 2_000));
        assertEquals(0.5, ActiveGoalsPlugin.goalProgress(1_000, 1_500, 2_000));
        assertEquals(1.0, ActiveGoalsPlugin.goalProgress(1_000, 2_100, 2_000));
        assertEquals(0.0, ActiveGoalsPlugin.goalProgress(1_000, 900, 2_000));
    }
    @Test
    void seedsXpAfterLoginAndCountsOnlyTrainingEvents() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        Skill skill = Skill.MINING;
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, skill.name())).thenReturn("85");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(skill)).thenReturn(1_000);

        GameStateChanged login = new GameStateChanged();
        login.setGameState(GameState.LOGGING_IN);
        plugin.onGameStateChanged(login);
        plugin.onStatChanged(new StatChanged(skill, 1_000, 9, 9));
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(0, plugin.getGoalCardSessionXp(skill));
        assertTrue(plugin.isGoalCollapsed(skill));

        plugin.onStatChanged(new StatChanged(skill, 1_040, 9, 9));
        assertEquals(40, plugin.getGoalCardSessionXp(skill));
        assertFalse(plugin.isGoalCollapsed(skill));

        when(client.getSkillExperience(skill)).thenReturn(10_000);
        plugin.onGameStateChanged(login);
        plugin.onStatChanged(new StatChanged(skill, 10_000, 27, 27));
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(40, plugin.getGoalCardSessionXp(skill));
        plugin.onStatChanged(new StatChanged(skill, 10_060, 27, 27));
        assertEquals(100, plugin.getGoalCardSessionXp(skill));

        when(client.getAccountHash()).thenReturn(123L);
        when(client.getSkillExperience(skill)).thenReturn(20_000);
        plugin.onGameStateChanged(login);
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(0, plugin.getGoalCardSessionXp(skill));
        assertEquals(0, plugin.getSessionOverviewTotalXp());
        assertEquals(-1, plugin.getActionsLeft(skill, 500));
        assertTrue(plugin.isGoalCollapsed(skill));

        plugin.onStatChanged(new StatChanged(skill, 20_025, 33, 33));
        assertEquals(25, plugin.getGoalCardSessionXp(skill));
        assertEquals(20, plugin.getActionsLeft(skill, 500));
        assertFalse(plugin.isGoalCollapsed(skill));
    }

    @Test
    void xpExpandsACollapsedGoalAndIsRecorded() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        ConfigManager config = mock(ConfigManager.class);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        Skill skill = Skill.MINING;
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, skill.name())).thenReturn("85");
        inject(plugin, "xpBaselineTicks", 0);
        @SuppressWarnings("unchecked")
        java.util.Map<Skill, Integer> lastXp =
            (java.util.Map<Skill, Integer>) field(plugin, "lastXp").get(plugin);
        lastXp.put(skill, 1_000);

        plugin.onStatChanged(new StatChanged(skill, 1_025, 9, 9));

        assertFalse(plugin.isGoalCollapsed(skill));
        assertEquals(25, plugin.getGoalCardSessionXp(skill));
    }

    @Test
    void completedGoalIsExpandedThenRemovedAfterSixtySeconds() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "xpTarget.MINING")).thenReturn("1025");
        @SuppressWarnings("unchecked")
        java.util.Map<Skill, Integer> xp = (java.util.Map<Skill, Integer>) field(plugin, "lastXp").get(plugin);
        xp.put(Skill.MINING, 1000);

        plugin.onStatChanged(new StatChanged(Skill.MINING, 1025, 9, 9));
        assertTrue(plugin.isGoalCompleted(Skill.MINING));
        assertFalse(plugin.isGoalCollapsed(Skill.MINING));
        verify(config).setConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "completed.MINING", true);

        @SuppressWarnings("unchecked")
        java.util.Map<Skill, Long> completed = (java.util.Map<Skill, Long>) field(plugin, "completedAtNanos").get(plugin);
        completed.put(Skill.MINING, System.nanoTime() - 61_000_000_000L);
        plugin.onGameTick(new GameTick());
        assertFalse(plugin.isGoalCompleted(Skill.MINING));
        verify(config).unsetConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "xpTarget.MINING");
        verify(config).unsetConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "goalStartXp.MINING");
    }

    @Test
    void xpTargetMustExceedCurrentXp() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        inject(plugin, "client", client);
        inject(plugin, "configManager", mock(ConfigManager.class));
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        assertThrows(IllegalArgumentException.class, () -> plugin.setXpTarget(Skill.MINING, 1000));
        assertThrows(IllegalArgumentException.class, () -> plugin.setXpTarget(Skill.MINING, 999));
    }

    @Test
    void editingChangesOnlyTheSelectedGoalsTargetAndReopensItsCard() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "loadedRSProfileKey", "character-a");
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal edited = tracker.add(Skill.MINING, 1000, 2000, 1);
        GoalTracker.Goal other = tracker.add(Skill.MINING, 1000, 2000, 2);
        tracker.gain(Skill.MINING, 1250, 250, 3);
        edited.expandedAtNanos = 0;
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "individualGoals", tracker);
        inject(plugin, "individualGoalsLoaded", true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1250);

        assertThrows(IllegalArgumentException.class, () -> plugin.editIndividualGoal(edited.id, 0, 1250));
        int targetLevel = 20;
        int levelXp = ActiveGoalsPlugin.xpForLevel(targetLevel);
        plugin.editIndividualGoal(edited.id, targetLevel, levelXp);
        assertEquals(targetLevel, edited.targetLevel);
        assertEquals(levelXp, edited.targetXp);
        verify(config).setRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "individualGoals", tracker.encode());

        plugin.editIndividualGoal(edited.id, 0, 1800);
        assertEquals(0, edited.targetLevel);
        assertEquals(1800, edited.targetXp);
        assertEquals(2000, other.targetXp);
        assertEquals(1000, edited.startXp);
        assertEquals(250, edited.sessionXp());
        assertEquals(250, edited.samples.getFirst());
        assertNotEquals(0, edited.expandedAtNanos);
        assertEquals(java.util.List.of(other, edited), plugin.individualGoals());

        when(client.getSkillExperience(Skill.MINING)).thenReturn(1800);
        assertThrows(IllegalStateException.class, () -> plugin.editIndividualGoal(edited.id, 0, 3000));
        assertEquals(1800, edited.targetXp);
    }

    @Test
    void separateGoalsForSameSkillTrackOneXpEventIndependently() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "loadedRSProfileKey", "character-a");
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "individualGoalsLoaded", true);
        @SuppressWarnings("unchecked")
        java.util.Map<Skill, Integer> baseline = (java.util.Map<Skill, Integer>) field(plugin, "lastXp").get(plugin);
        baseline.put(Skill.MINING, 1000);
        plugin.addIndividualGoal(Skill.MINING, 0, 1100);
        plugin.addIndividualGoal(Skill.MINING, 0, 1500);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        assertEquals(2, plugin.individualGoals().size());
        assertFalse(plugin.individualGoals().get(0).completed);
        assertTrue(plugin.individualGoals().get(1).completed);
        assertEquals(100, plugin.individualGoals().get(0).sessionXp());
        assertEquals(100, plugin.individualGoals().get(1).sessionXp());
        assertEquals(100, plugin.getSessionOverviewTotalXp());
    }

    @Test
    void loginIgnoresOldSharedGoals() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        when(client.getSkillExperience(Skill.FISHING)).thenReturn(2000);
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "xpTarget.MINING")).thenReturn("3000");
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "xpTarget.FISHING")).thenReturn("2000");
        when(config.getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "completed.FISHING")).thenReturn("true");
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.individualGoals().isEmpty());
        verify(config, never()).getConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "individualGoals");
        verify(config).setRSProfileConfiguration(ActiveGoalsPlugin.CONFIG_GROUP, "individualGoals", "");
    }

    private static void inject(ActiveGoalsPlugin plugin, String name, Object value) throws Exception
    {
        Field field = field(plugin, name);
        field.set(plugin, value);
    }

    private static Field field(ActiveGoalsPlugin plugin, String name) throws Exception
    {
        Field field = plugin.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
