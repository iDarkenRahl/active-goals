package com.activegoals;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.SkillIconManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MahoganyHomesIntegrationTest
{
    @Test
    void completionCapturesRewardAndEstimateStaysFixedUntilNextCompletion() throws Exception
    {
        Fixture f = new Fixture();
        f.assign();
        assertEquals(-1, f.plugin.homesLeft(12_500));
        f.xp(10_400);
        f.complete();
        // Completion XP can arrive after the completion message and before the next tick.
        f.xp(11_000);
        f.tick();
        assertEquals(2, f.plugin.homesLeft(12_500));

        f.assign();
        f.xp(11_900);
        assertEquals(2, f.plugin.homesLeft(12_500));
        f.complete();
        f.xp(12_000);
        f.tick();
        assertEquals(1, f.plugin.homesLeft(12_500));
    }

    @Test
    void equipmentChangeDiscardsEstimateAndFirstNewOutfitContract() throws Exception
    {
        Fixture f = new Fixture();
        f.assign();
        f.xp(11_000);
        f.complete();
        f.tick();
        assertEquals(2, f.plugin.homesLeft(12_500));

        f.assign();
        Item shirt = mock(Item.class);
        when(shirt.getId()).thenReturn(ItemID.CARPENTERS_SHIRT);
        ItemContainer equipment = mock(ItemContainer.class);
        when(equipment.getItems()).thenReturn(new Item[]{shirt});
        when(f.client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        f.plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.EQUIPMENT.getId(), equipment));
        assertEquals(-1, f.plugin.homesLeft(12_500));
        f.xp(12_000);
        f.complete();
        f.tick();
        assertEquals(-1, f.plugin.homesLeft(13_500));

        f.assign();
        f.xp(13_000);
        f.complete();
        f.tick();
        assertEquals(1, f.plugin.homesLeft(13_500));
    }

    @Test
    void logoutAndProfileChangeClearRecognizedContracts() throws Exception
    {
        Fixture logoutFixture = new Fixture();
        logoutFixture.finishedContract();
        GameStateChanged logout = new GameStateChanged();
        logout.setGameState(GameState.LOGIN_SCREEN);
        logoutFixture.plugin.onGameStateChanged(logout);
        assertEquals(-1, logoutFixture.plugin.homesLeft(12_500));

        Fixture profileFixture = new Fixture();
        profileFixture.finishedContract();
        when(profileFixture.config.getRSProfileKey()).thenReturn("other");
        profileFixture.plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged("first", "other"));
        assertEquals(-1, profileFixture.plugin.homesLeft(12_500));
    }

    @Test
    void changedProfileKeyHidesOldEstimateBeforeProfileEvent() throws Exception
    {
        Fixture f = new Fixture();
        f.finishedContract();
        when(f.config.getRSProfileKey()).thenReturn("other");
        assertEquals(-1, f.plugin.homesLeft(12_500));
    }

    @Test
    void dashboardShowsHomesOrActionsAndCompletedGoalShowsZero() throws Exception
    {
        ActiveGoalsPlugin plugin = mock(ActiveGoalsPlugin.class);
        Client client = mock(Client.class);
        GoalTracker tracker = new GoalTracker();
        GoalTracker.Goal goal = tracker.add(Skill.CONSTRUCTION, 1000, 1201, 1);
        goal.samples.add(100);
        goal.expandedAtNanos = 1;
        when(plugin.individualGoals()).thenReturn(tracker.goals());
        when(plugin.getClient()).thenReturn(client);
        when(plugin.goalDataReady()).thenReturn(true);
        when(plugin.goalClockNanos()).thenReturn(2L);
        when(plugin.getGoalCardExpansionSeconds()).thenReturn(300);
        when(client.getSkillExperience(Skill.CONSTRUCTION)).thenReturn(1000);
        when(plugin.homesLeft(1201)).thenReturn(2L);
        doAnswer(call -> { call.<Runnable>getArgument(0).run(); return null; })
            .when(plugin).runOnClientThread(any(Runnable.class));
        ActiveGoalsPanel panel = new ActiveGoalsPanel(plugin, mock(SkillIconManager.class));
        try
        {
            SwingUtilities.invokeAndWait(panel::start);
            flush();
            SwingUtilities.invokeAndWait(() ->
            {
                assertEquals("2", valueForRow(panel, "Homes Left:"));
            });
            when(plugin.homesLeft(1201)).thenReturn(-1L);
            SwingUtilities.invokeAndWait(panel::requestRefresh);
            flush();
            SwingUtilities.invokeAndWait(() ->
            {
                assertEquals("3", valueForRow(panel, "Actions Left:"));
            });
            when(plugin.homesLeft(1201)).thenReturn(2L);
            when(client.getSkillExperience(Skill.CONSTRUCTION)).thenReturn(1201);
            goal.completed = true;
            SwingUtilities.invokeAndWait(panel::requestRefresh);
            flush();
            SwingUtilities.invokeAndWait(() ->
            {
                assertEquals("0", valueForRow(panel, "Homes Left:"));
            });
        }
        finally { SwingUtilities.invokeAndWait(panel::stop); }
    }

    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }

    private static String valueForRow(Container parent, String label)
    {
        List<String> directLabels = new ArrayList<>();
        for (Component child : parent.getComponents())
        {
            if (child instanceof JLabel) directLabels.add(((JLabel) child).getText());
        }
        if (directLabels.size() == 2 && label.equals(directLabels.get(0))) return directLabels.get(1);
        for (Component child : parent.getComponents())
        {
            if (child instanceof Container)
            {
                String value = valueForRow((Container) child, label);
                if (value != null) return value;
            }
        }
        return null;
    }

    private static final class Fixture
    {
        final ActiveGoalsPlugin plugin = new ActiveGoalsPlugin();
        final ConfigManager config = mock(ConfigManager.class);
        final Client client = mock(Client.class);

        Fixture() throws Exception
        {
            ItemContainer equipment = mock(ItemContainer.class);
            when(equipment.getItems()).thenReturn(new Item[0]);
            when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            when(client.getSkillExperience(Skill.CONSTRUCTION)).thenReturn(10_000);
            when(config.getRSProfileKey()).thenReturn("first");
            set("client", client);
            set("configManager", config);
            set("activeGoalsPanel", mock(ActiveGoalsPanel.class));
            set("loadedRSProfileKey", "first");
            set("individualGoalsLoaded", true);
            @SuppressWarnings("unchecked") Map<Skill, Integer> xp = (Map<Skill, Integer>) field("lastXp").get(plugin);
            xp.put(Skill.CONSTRUCTION, 10_000);
        }

        void assign() { chat("Adept Contract: Go see Ross in East Ardougne."); }
        void complete() { chat("You have completed 12 contracts with a total of 48 points."); }
        void chat(String text)
        {
            ChatMessage event = new ChatMessage();
            event.setType(ChatMessageType.GAMEMESSAGE);
            event.setMessage(text);
            plugin.onChatMessage(event);
        }
        void xp(int xp)
        {
            when(client.getSkillExperience(Skill.CONSTRUCTION)).thenReturn(xp);
            plugin.onStatChanged(new StatChanged(Skill.CONSTRUCTION, xp, 50, 50));
        }
        void tick() { plugin.onGameTick(new GameTick()); }
        void finishedContract()
        {
            assign(); xp(11_000); complete(); tick();
            assertEquals(2, plugin.homesLeft(12_500));
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
