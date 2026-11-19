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
    void externalMetadataWritesAreObservedBeforeDelayedNotifications() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = mockProfileConfig(config);
        saved.put("individualGoals", "keep,MINING,0,3000,0,false;remove,FISHING,0,3000,0,false");
        saved.put("manuallyCollapsedGoals", "");
        saved.put("goalCreationTimes", "keep,100;remove,200");
        when(config.getRSProfileKey()).thenReturn("a");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());

        // Both external writes are visible before their ConfigChanged notifications.
        saved.put("manuallyCollapsedGoals", "keep");
        saved.put("goalCreationTimes", "keep,777;remove,200");
        plugin.removeIndividualGoal("remove");
        assertEquals("keep", saved.get("manuallyCollapsedGoals"));
        assertEquals("keep,777", saved.get("goalCreationTimes"));
        assertTrue(plugin.individualGoals().get(0).manuallyCollapsed);
        assertEquals(777L, plugin.individualGoals().get(0).createdAtEpochMillis);
    }

    @Test
    void externalGoalWriteIsObservedBeforeItsDelayedNotification() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put("individualGoals", "keep,MINING,0,3000,0,false");
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());

        // An external writer has changed the configuration but not posted ConfigChanged yet.
        String externalValue = "new,FISHING,0,4000,0,false;keep,MINING,0,4500,0,false";
        saved.put("individualGoals", externalValue);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1100);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        GoalTracker persisted = GoalTracker.decode(saved.get("individualGoals"));
        assertEquals(2, persisted.goals().size());
        assertTrue(persisted.goals().stream().anyMatch(goal -> goal.id.equals("new")));
        assertEquals(4500, persisted.goals().stream().filter(goal -> goal.id.equals("keep")).findFirst().get().targetXp);
        assertEquals(100, plugin.individualGoals().stream().filter(goal -> goal.id.equals("keep")).findFirst().get().sessionXp);
        net.runelite.client.events.ConfigChanged delayed = new net.runelite.client.events.ConfigChanged();
        delayed.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
        delayed.setProfile("a");
        delayed.setKey("individualGoals");
        delayed.setOldValue("keep,MINING,0,3000,0,false");
        delayed.setNewValue(externalValue);
        plugin.onConfigChanged(delayed);
        plugin.onGameTick(new GameTick());
        assertEquals(2, plugin.individualGoals().size());
        assertEquals(100, plugin.individualGoals().stream().filter(goal -> goal.id.equals("keep")).findFirst().get().sessionXp);
    }

    @Test
    void pendingExternalChangeDoesNotLoseCompletingXp()
        throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put("individualGoals", "cross,MINING,0,3000,0,false;past,MINING,0,3000,0,false");
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());

        saved.put("individualGoals", "cross,MINING,0,1050,0,false;past,MINING,0,900,0,false");
        saved.put("manuallyCollapsedGoals", "cross");
        net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
        event.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
        event.setProfile("a");
        event.setKey("individualGoals");
        plugin.onConfigChanged(event);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1100);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));

        GoalTracker.Goal crossed = plugin.individualGoals().stream().filter(g -> g.id.equals("cross")).findFirst().get();
        GoalTracker.Goal alreadyReached = plugin.individualGoals().stream().filter(g -> g.id.equals("past")).findFirst().get();
        assertTrue(crossed.completed);
        assertFalse(crossed.manuallyCollapsed);
        assertEquals(100, crossed.sessionXp);
        assertTrue(alreadyReached.completed);
        assertEquals(0, alreadyReached.sessionXp);
        assertEquals(100, plugin.getSessionOverviewTotalXp());
        assertEquals("", saved.get("manuallyCollapsedGoals"));
    }

    @Test
    void pendingChangesForOldProfileAreNotReplayedAfterReload() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.concurrent.atomic.AtomicReference<String> profile = new java.util.concurrent.atomic.AtomicReference<>("a");
        java.util.Map<String, java.util.Map<String, String>> saved = new java.util.HashMap<>();
        saved.put("a", new java.util.HashMap<>());
        saved.get("a").put("individualGoals", "goal,MINING,0,3000,0,false");
        saved.get("a").put("manuallyCollapsedGoals", "");
        when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.getOrDefault(profile.get(), java.util.Map.of()).get(call.getArgument(1)));
        doAnswer(call -> { saved.computeIfAbsent(profile.get(), ignored -> new java.util.HashMap<>())
            .put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        saved.get("a").put("manuallyCollapsedGoals", "goal");
        net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
        event.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
        event.setProfile("a");
        event.setKey("manuallyCollapsedGoals");
        event.setOldValue("");
        event.setNewValue("goal");
        plugin.onConfigChanged(event);
        profile.set("b");
        plugin.onRuneScapeProfileChanged(null);
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        profile.set("a");
        plugin.onRuneScapeProfileChanged(null);
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.individualGoals().get(0).manuallyCollapsed);
        plugin.setIndividualGoalCollapsed("goal", false);
        assertEquals("", saved.get("a").get("manuallyCollapsedGoals"));
        assertFalse(plugin.individualGoals().get(0).manuallyCollapsed);
    }

    @Test
    void malformedMetadataDoesNotDiscardLaterNestedChanges() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put("individualGoals", "keep,MINING,0,3000,0,false;remove,FISHING,0,3000,0,false");
        saved.put("goalCreationTimes", "keep,100;keep,200;remove,500");
        java.util.concurrent.atomic.AtomicBoolean nested = new java.util.concurrent.atomic.AtomicBoolean();
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> {
            String key = call.getArgument(1);
            String value = call.getArgument(2);
            saved.put(key, value);
            if ("individualGoals".equals(key) && nested.getAndSet(false))
            {
                String[] versions = {"keep,100;keep,200;remove,500", "keep,300;remove,500", "keep,400;remove,500"};
                for (int i = 1; i < versions.length; i++)
                {
                    net.runelite.client.events.ConfigChanged event = new net.runelite.client.events.ConfigChanged();
                    event.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
                    event.setProfile("a");
                    event.setKey("goalCreationTimes");
                    event.setOldValue(versions[i - 1]);
                    event.setNewValue(versions[i]);
                    saved.put("goalCreationTimes", versions[i]);
                    plugin.onConfigChanged(event);
                }
            }
            return null;
        }).when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        nested.set(true);
        plugin.removeIndividualGoal("remove");
        assertEquals("keep,400", saved.get("goalCreationTimes"));
        plugin.onGameTick(new GameTick());
        assertEquals(400L, plugin.individualGoals().get(0).createdAtEpochMillis);
    }

    @Test
    void nestedMetadataChangesAreMergedWithGoalRemoval() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put("individualGoals", "keep,MINING,0,3000,0,false;remove,FISHING,0,3000,0,false");
        saved.put("manuallyCollapsedGoals", "");
        saved.put("goalCreationTimes", "keep,100;remove,200");
        java.util.concurrent.atomic.AtomicBoolean nested = new java.util.concurrent.atomic.AtomicBoolean();
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> {
            String key = call.getArgument(1);
            String value = call.getArgument(2);
            saved.put(key, value);
            net.runelite.client.events.ConfigChanged own = new net.runelite.client.events.ConfigChanged();
            own.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
            own.setProfile("a");
            own.setKey(key);
            own.setNewValue(value);
            plugin.onConfigChanged(own);
            if ("individualGoals".equals(key) && nested.getAndSet(false))
            {
                net.runelite.client.events.ConfigChanged collapse = new net.runelite.client.events.ConfigChanged();
                collapse.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
                collapse.setProfile("a");
                collapse.setKey("manuallyCollapsedGoals");
                collapse.setOldValue("");
                collapse.setNewValue("keep");
                saved.put("manuallyCollapsedGoals", "keep");
                plugin.onConfigChanged(collapse);
                net.runelite.client.events.ConfigChanged times = new net.runelite.client.events.ConfigChanged();
                times.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
                times.setProfile("a");
                times.setKey("goalCreationTimes");
                times.setOldValue("keep,100;remove,200");
                times.setNewValue("keep,777;remove,200");
                saved.put("goalCreationTimes", "keep,777;remove,200");
                plugin.onConfigChanged(times);
            }
            return null;
        }).when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        nested.set(true);
        plugin.removeIndividualGoal("remove");
        assertEquals("keep,MINING,0,3000,0,false", saved.get("individualGoals"));
        assertEquals("keep", saved.get("manuallyCollapsedGoals"));
        assertEquals("keep,777", saved.get("goalCreationTimes"));
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.individualGoals().get(0).manuallyCollapsed);
        assertEquals(777L, plugin.individualGoals().get(0).createdAtEpochMillis);
    }

    @Test
    void externalActiveProfileChangesAreAppliedBeforeNextXpSave() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        net.runelite.client.callback.ClientThread clientThread = mock(net.runelite.client.callback.ClientThread.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicBoolean nestedUpdate = new java.util.concurrent.atomic.AtomicBoolean();
        saved.put("individualGoals", "keep,MINING,1000,3000,0,false;remove,FISHING,0,3000,0,false");
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> {
            String key = call.getArgument(1);
            String value = call.getArgument(2);
            saved.put(key, value);
            net.runelite.client.events.ConfigChanged internal = new net.runelite.client.events.ConfigChanged();
            internal.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
            internal.setProfile("a");
            internal.setKey(key);
            internal.setNewValue(value);
            plugin.onConfigChanged(internal);
            if ("individualGoals".equals(key) && nestedUpdate.getAndSet(false))
            {
                saved.put("manuallyCollapsedGoals", "keep");
                net.runelite.client.events.ConfigChanged nested = new net.runelite.client.events.ConfigChanged();
                nested.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
                nested.setProfile("a");
                nested.setKey("manuallyCollapsedGoals");
                nested.setNewValue("keep");
                plugin.onConfigChanged(nested);
            }
            return null;
        }).when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "clientThread", clientThread);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        long oldAction = plugin.goalActionSession();
        verify(clientThread, never()).invokeLater(any(Runnable.class));

        String updated = "new,FISHING,0,4000,0,false;keep,MINING,1000,4000,0,false";
        saved.put("individualGoals", updated);
        saved.put("manuallyCollapsedGoals", "keep");
        saved.put("goalCreationTimes", "keep,123456");
        net.runelite.client.events.ConfigChanged external = new net.runelite.client.events.ConfigChanged();
        external.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
        external.setProfile("a");
        external.setKey("individualGoals");
        plugin.onConfigChanged(external);
        // The queued client-thread refresh has not executed yet.
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1150, 9, 9));
        assertEquals(updated, saved.get("individualGoals"));
        assertEquals(2, plugin.individualGoals().size());
        GoalTracker.Goal kept = plugin.individualGoals().stream().filter(g -> g.id.equals("keep")).findFirst().get();
        assertEquals(150, kept.sessionXp);
        assertTrue(kept.manuallyCollapsed);
        assertEquals(123456L, kept.createdAtEpochMillis);
        assertThrows(IllegalStateException.class, () -> plugin.requireGoalAction(oldAction));

        long currentAction = plugin.goalActionSession();
        external.setProfile("other-profile");
        plugin.onConfigChanged(external);
        plugin.requireGoalAction(currentAction);
        saved.put("manuallyCollapsedGoals", "");
        saved.put("goalCreationTimes", "keep,654321");
        external.setProfile("a");
        external.setKey("manuallyCollapsedGoals");
        plugin.onConfigChanged(external);
        assertThrows(IllegalStateException.class, () -> plugin.requireGoalAction(currentAction));
        kept = plugin.individualGoals().stream().filter(g -> g.id.equals("keep")).findFirst().get();
        assertFalse(kept.manuallyCollapsed);
        assertEquals(654321L, kept.createdAtEpochMillis);
        assertEquals(150, kept.sessionXp);

        long beforeNested = plugin.goalActionSession();
        nestedUpdate.set(true);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1200, 9, 9));
        assertThrows(IllegalStateException.class, () -> plugin.requireGoalAction(beforeNested));
        kept = plugin.individualGoals().stream().filter(g -> g.id.equals("keep")).findFirst().get();
        assertTrue(kept.manuallyCollapsed);
        assertEquals(200, kept.sessionXp);
    }

    @Test
    void externallyLoweredTargetCompletesImmediately()
        throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put("individualGoals", "goal,MINING,1000,3000,0,false");
        saved.put("manuallyCollapsedGoals", "goal");
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1100);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "clientThread", mock(net.runelite.client.callback.ClientThread.class));
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        saved.put("individualGoals", "goal,MINING,1000,1050,0,false");
        net.runelite.client.events.ConfigChanged changed = new net.runelite.client.events.ConfigChanged();
        changed.setGroup(ActiveGoalsPlugin.CONFIG_GROUP);
        changed.setProfile("a");
        changed.setKey("individualGoals");
        plugin.onConfigChanged(changed);
        plugin.onGameTick(new GameTick());
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        assertTrue(goal.completed);
        assertFalse(goal.manuallyCollapsed);
        assertNotEquals(0, goal.completedAtNanos);
        assertEquals("goal,MINING,1000,1050,0,true", saved.get("individualGoals"));
        assertEquals("", saved.get("manuallyCollapsedGoals"));
    }

    @Test
    void reloadUsesSavedGoalsAndMetadataWhilePreservingMatchingSessionStats() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        saved.put("individualGoals", "keep,MINING,1000,2000,0,false;remove,FISHING,0,2000,0,false");
        when(config.getRSProfileKey()).thenReturn("a");
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "clientToolbar", mock(net.runelite.client.ui.ClientToolbar.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        plugin.shutDown();

        String updated = "new,FISHING,0,3000,0,false;keep,MINING,1000,3000,0,false";
        saved.put("individualGoals", updated);
        saved.put("manuallyCollapsedGoals", "keep");
        saved.put("goalCreationTimes", "keep,123456");
        plugin.startUp();
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1100);
        plugin.onGameTick(new GameTick());
        assertEquals(updated, saved.get("individualGoals"));
        assertEquals(2, plugin.individualGoals().size());
        GoalTracker.Goal added = plugin.individualGoals().get(0);
        GoalTracker.Goal kept = plugin.individualGoals().get(1);
        assertEquals("new", added.id);
        assertEquals(0, added.sessionXp);
        assertEquals("keep", kept.id);
        assertEquals(3000, kept.targetXp);
        assertEquals(100, kept.sessionXp);
        assertEquals(100, kept.rateXp);
        assertTrue(kept.manuallyCollapsed);
        assertEquals(123456L, kept.createdAtEpochMillis);
        assertEquals(100, plugin.getSessionOverviewTotalXp());

        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        saved.put("individualGoals", "keep,FISHING,0,4000,0,false");
        saved.put("manuallyCollapsedGoals", "");
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(1, plugin.individualGoals().size());
        assertEquals(Skill.FISHING, plugin.individualGoals().get(0).skill);
        assertEquals(0, plugin.individualGoals().get(0).sessionXp);
        assertEquals("keep,FISHING,0,4000,0,false", saved.get("individualGoals"));
    }

    @Test
    void sessionTotalsSurviveLogoutProfileSwitchAndPluginToggle() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        java.util.concurrent.atomic.AtomicReference<String> profile = new java.util.concurrent.atomic.AtomicReference<>("a");
        java.util.Map<String, java.util.Map<String, String>> saved = new java.util.HashMap<>();
        when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.getOrDefault(profile.get(), java.util.Map.of()).get(call.getArgument(1)));
        doAnswer(call -> { saved.computeIfAbsent(profile.get(), ignored -> new java.util.HashMap<>())
            .put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1000);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "clientToolbar", mock(net.runelite.client.ui.ClientToolbar.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        plugin.addIndividualGoal(Skill.MINING, 0, 2000);
        GoalTracker.Goal goal = plugin.individualGoals().get(0);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1100, 9, 9));
        inject(plugin, "sessionStartNanos", System.nanoTime() - 5_000_000_000L);

        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        plugin.onGameStateChanged(logout);
        profile.set("b");
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(0, plugin.getSessionOverviewTotalXp());
        assertEquals(0, plugin.getSessionSeconds());
        assertTrue(plugin.individualGoals().isEmpty());
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1060, 9, 9));
        assertEquals(60, plugin.getSessionOverviewTotalXp());
        plugin.onGameStateChanged(logout);
        profile.set("a");
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1100);
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(100, plugin.getSessionOverviewTotalXp());
        goal = plugin.individualGoals().get(0);
        assertEquals(100, goal.sessionXp);
        assertTrue(plugin.getSessionSeconds() >= 5);

        plugin.shutDown();
        plugin.startUp();
        when(client.getSkillExperience(Skill.MINING)).thenReturn(1500);
        plugin.onGameTick(new GameTick());
        assertEquals(100, plugin.getSessionOverviewTotalXp());
        goal = plugin.individualGoals().get(0);
        assertEquals(100, goal.sessionXp);
        assertTrue(plugin.getSessionSeconds() >= 5);
        plugin.onStatChanged(new StatChanged(Skill.MINING, 1550, 9, 9));
        assertEquals(150, plugin.getSessionOverviewTotalXp());
        assertEquals(150, goal.sessionXp);
        plugin.onGameStateChanged(logout);
        profile.set("b");
        plugin.onGameTick(new GameTick());
        plugin.onGameTick(new GameTick());
        assertEquals(60, plugin.getSessionOverviewTotalXp());

        ActiveGoalsPlugin newClientSession = new ActiveGoalsPlugin();
        inject(newClientSession, "client", client);
        inject(newClientSession, "configManager", config);
        inject(newClientSession, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(newClientSession, "xpBaselineTicks", 1);
        newClientSession.onGameTick(new GameTick());
        assertEquals(0, newClientSession.getSessionOverviewTotalXp());
    }

    @Test
    void sessionTimePausesDuringLoadingAndDisconnect() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        when(config.getRSProfileKey()).thenReturn("a");
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        inject(plugin, "client", client);
        inject(plugin, "configManager", config);
        inject(plugin, "activeGoalsPanel", mock(ActiveGoalsPanel.class));
        inject(plugin, "xpBaselineTicks", 1);
        plugin.onGameTick(new GameTick());
        inject(plugin, "sessionStartNanos", System.nanoTime() - 3_000_000_000L);
        GameStateChanged event = new GameStateChanged();
        event.setGameState(GameState.LOADING);
        plugin.onGameStateChanged(event);
        assertEquals(3, plugin.getSessionSeconds());
        event.setGameState(GameState.CONNECTION_LOST);
        plugin.onGameStateChanged(event);
        assertEquals(3, plugin.getSessionSeconds());
        plugin.onGameTick(new GameTick());
        assertTrue(plugin.getSessionSeconds() >= 3);
    }

    @Test
    void profileChangesInvalidatePendingActionsEvenAfterReturning() throws Exception
    {
        ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        Client client = mock(Client.class);
        ConfigManager config = mock(ConfigManager.class);
        mockProfileConfig(config);
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
        GoalTracker.Goal restored = plugin.individualGoals().get(0);
        assertEquals(original.id, restored.id);
        assertEquals(100, restored.sessionXp);
        assertEquals(100, restored.rateXp);
        assertEquals(100, restored.samples.getFirst());
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
        java.util.Map<String, String> saved = mockProfileConfig(config);
        when(config.getRSProfileKey()).thenReturn("character-a");
        inject(plugin, "loadedRSProfileKey", "character-a");
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal edited = tracker.add(Skill.MINING, 1000, 2000, 1);
        GoalTracker.Goal other = tracker.add(Skill.MINING, 1000, 2000, 2);
        tracker.gain(Skill.MINING, 1250, 250, 3);
        saved.put("individualGoals", tracker.encode());
        inject(plugin, "knownSavedGoals", tracker.encode());
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
        mockProfileConfig(config);
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

    private static java.util.Map<String, String> mockProfileConfig(ConfigManager config)
    {
        java.util.Map<String, String> saved = new java.util.HashMap<>();
        when(config.getRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString()))
            .thenAnswer(call -> saved.get(call.getArgument(1)));
        doAnswer(call -> { saved.put(call.getArgument(1), call.getArgument(2)); return null; })
            .when(config).setRSProfileConfiguration(eq(ActiveGoalsPlugin.CONFIG_GROUP), anyString(), anyString());
        return saved;
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
