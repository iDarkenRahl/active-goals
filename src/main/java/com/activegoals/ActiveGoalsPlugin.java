package com.activegoals;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;
import com.google.inject.Provides;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Collections;

@PluginDescriptor(
    name = "Active Goals",
    description = "Active Goals: skill goals, Xp rates, session stats and completion estimates.",
    tags = {"skill", "xp", "target", "level", "sidebar"}
)
public class ActiveGoalsPlugin extends Plugin
{
    static final String CONFIG_GROUP = "activegoals";
    static final int MIN_TARGET_LEVEL = 2;
    static final int MAX_TARGET_LEVEL = 126;
    static final int DEFAULT_GOAL_CARD_EXPANSION_SECONDS = 300;
    static final int MIN_GOAL_CARD_EXPANSION_SECONDS = 1;
    static final int MAX_GOAL_CARD_EXPANSION_SECONDS = 86_400;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
    private int xpBaselineTicks;
    private Long trackedAccountHash;
    private final Map<Skill, Long> firstGainNanos = new EnumMap<>(Skill.class);
    private long sessionStartNanos;
    private final Map<Skill, Long> goalRateXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Long> sessionOverviewXp = new EnumMap<>(Skill.class);
    // Goal-card Xp is independent from Session Overview.
    private final Map<Skill, Long> goalCardXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Deque<Integer>> recentXpGainSamples = new EnumMap<>(Skill.class);
    private static final int ACTION_SAMPLE_LIMIT = 20;
    private final Map<Skill, Long> goalCardExpansionStartedNanos = new EnumMap<>(Skill.class);
    private final Map<Skill, Long> completedAtNanos = new EnumMap<>(Skill.class);
    private GoalTracker individualGoals = new GoalTracker();
    private boolean individualGoalsLoaded;
    private String loadedRSProfileKey;
    private volatile long profileSession;
    private final Map<String, GoalTracker> sessionGoals = new HashMap<>();
    private final Map<String, Long> goalsPausedAtNanos = new HashMap<>();

    private void pauseIndividualGoals()
    {
        if (!individualGoalsLoaded || loadedRSProfileKey == null
            || goalsPausedAtNanos.containsKey(loadedRSProfileKey)) return;
        long now = System.nanoTime();
        expireGoalExpansions(now);
        goalsPausedAtNanos.put(loadedRSProfileKey, now);
        sessionGoals.put(loadedRSProfileKey, individualGoals);
    }

    void expireGoalExpansions(long now)
    {
        if (loadedRSProfileKey != null && goalsPausedAtNanos.containsKey(loadedRSProfileKey)) return;
        int seconds = getGoalCardExpansionSeconds();
        for (GoalTracker.Goal goal : individualGoals.goals()) goal.expireExpansion(now, seconds);
    }

    long goalClockNanos()
    {
        Long pausedAt = goalsPausedAtNanos.get(loadedRSProfileKey);
        return pausedAt == null ? System.nanoTime() : pausedAt;
    }

    private void resumeIndividualGoals(long now)
    {
        Long pausedAt = goalsPausedAtNanos.remove(loadedRSProfileKey);
        if (pausedAt == null) return;
        long paused = Math.max(0, now - pausedAt);
        for (GoalTracker.Goal goal : individualGoals.goals())
        {
            if (goal.ratePeriodStartNanos != 0) goal.ratePeriodStartNanos += paused;
            if (goal.expandedAtNanos != 0) goal.expandedAtNanos = now;
            if (goal.completedAtNanos != 0) goal.completedAtNanos += paused;
        }
    }

    List<GoalTracker.Goal> individualGoals()
    {
        return individualGoals.goals();
    }

    boolean goalDataReady()
    {
        String key = configManager.getRSProfileKey();
        return individualGoalsLoaded && key != null && key.equals(loadedRSProfileKey)
            && client.getGameState() == GameState.LOGGED_IN && xpBaselineTicks == 0;
    }

    long goalActionSession() { return profileSession; }

    boolean goalProfileLoading() { return client.getGameState() == GameState.LOGGED_IN && !goalDataReady(); }

    void requireGoalAction(long session)
    {
        if (session != profileSession) throw new IllegalStateException("Profile changed.");
        if (!goalDataReady()) throw new IllegalStateException("Wait for your profile to load, then try again.");
    }

    void addIndividualGoal(Skill skill, int targetLevel, long targetXp)
    {
        if (!goalDataReady()) throw new IllegalStateException("Log in before adding a goal.");
        int xp = client.getSkillExperience(skill);
        if (xp < 0 || targetXp <= xp || targetXp > 200_000_000L)
            throw new IllegalArgumentException("Target must be above current Xp.");
        GoalTracker.Goal goal = individualGoals.add(skill, xp, targetXp, System.nanoTime());
        goal.createdAtEpochMillis = System.currentTimeMillis();
        goal.targetLevel = targetLevel;
        saveIndividualGoals();
        saveCreationTimes();
    }

    void saveIndividualGoals()
    {
        configManager.setRSProfileConfiguration(CONFIG_GROUP, "individualGoals", individualGoals.encode());
    }

    private void saveManualCollapses()
    {
        configManager.setRSProfileConfiguration(CONFIG_GROUP, "manuallyCollapsedGoals", individualGoals.encodeManualCollapses());
    }

    private void saveCreationTimes()
    {
        configManager.setRSProfileConfiguration(CONFIG_GROUP, "goalCreationTimes", individualGoals.encodeCreationTimes());
    }

    void setIndividualGoalCollapsed(String id, boolean collapsed)
    {
        if (!goalDataReady() || !Objects.equals(loadedRSProfileKey, configManager.getRSProfileKey())) return;
        for (GoalTracker.Goal goal : individualGoals.goals())
        {
            if (!goal.id.equals(id) || goal.skill == null) continue;
            goal.manuallyCollapsed = collapsed && !goal.completed;
            goal.expandedAtNanos = collapsed ? 0 : System.nanoTime();
            saveManualCollapses();
            return;
        }
    }

    void removeIndividualGoal(String id)
    {
        if (!goalDataReady() || !Objects.equals(loadedRSProfileKey, configManager.getRSProfileKey())) return;
        individualGoals.remove(id);
        saveIndividualGoals();
        saveManualCollapses();
        saveCreationTimes();
    }

    void editIndividualGoal(String id, int targetLevel, long targetXp)
    {
        if (!goalDataReady()) throw new IllegalStateException("Log in before editing a goal.");
        expireGoalExpansions(System.nanoTime());
        if (!Objects.equals(loadedRSProfileKey, configManager.getRSProfileKey()))
            throw new IllegalStateException("This goal is no longer available.");
        for (GoalTracker.Goal goal : individualGoals.goals())
        {
            if (!goal.id.equals(id)) continue;
            int xp = client.getSkillExperience(goal.skill);
            if (goal.completed || xp >= goal.targetXp)
                throw new IllegalStateException("This goal is already complete.");
            if (xp < 0 || targetXp <= xp || targetXp > 200_000_000L)
                throw new IllegalArgumentException("Target must be above current Xp.");
            boolean updateCreated = goal.createdAtEpochMillis == null
                || goal.targetXp != targetXp || goal.targetLevel != targetLevel;
            goal.targetXp = targetXp;
            goal.targetLevel = targetLevel;
            if (updateCreated) goal.createdAtEpochMillis = System.currentTimeMillis();
            boolean wasManuallyCollapsed = goal.manuallyCollapsed;
            goal.manuallyCollapsed = false;
            goal.expandedAtNanos = System.nanoTime();
            saveIndividualGoals();
            if (updateCreated) saveCreationTimes();
            if (wasManuallyCollapsed) saveManualCollapses();
            return;
        }
        throw new IllegalStateException("This goal is no longer available.");
    }
    private static final long COMPLETION_DISPLAY_NANOS = 60L * NANOS_PER_SECOND;

    @Inject private ClientToolbar clientToolbar;
    @Inject private ClientThread clientThread;
    @Inject private ActiveGoalsPanel activeGoalsPanel;
    private NavigationButton navigationButton;

    @Inject
    private Client client;

    @Inject
    private ConfigManager configManager;

    @Provides
    ActiveGoalsConfig provideConfig(ConfigManager manager)
    {
        return manager.getConfig(ActiveGoalsConfig.class);
    }

    @Override
    protected void startUp()
    {
        profileSession++;
        sessionGoals.clear();
        goalsPausedAtNanos.clear();
        sessionStartNanos = 0;
        lastXp.clear();
        xpBaselineTicks = 1;
        trackedAccountHash = null;
        clearGoalCardXpStats();
        clearSessionOverviewXp();
        clearActionSamples();
        goalCardExpansionStartedNanos.clear();
        completedAtNanos.clear();
        individualGoalsLoaded = false;
        navigationButton = NavigationButton.builder()
            .tooltip("Active Goals")
            .icon(createToolbarIcon())
            .priority(6)
            .panel(activeGoalsPanel)
            .build();
        clientToolbar.addNavigation(navigationButton);
        activeGoalsPanel.start();
    }

    @Override
    protected void shutDown()
    {
        profileSession++;
        activeGoalsPanel.stop();
        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (CONFIG_GROUP.equals(event.getGroup()))
        {
            activeGoalsPanel.requestRefresh();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN) pauseIndividualGoals();
        if (state == GameState.LOGIN_SCREEN)
        {
            profileSession++;
            individualGoals.clearTrainingActivity();
            individualGoals = new GoalTracker();
            individualGoalsLoaded = false;
            loadedRSProfileKey = null;
            trackedAccountHash = null;
            lastXp.clear();
            clearGoalCardXpStats();
            clearSessionOverviewXp();
            clearActionSamples();
            goalCardExpansionStartedNanos.clear();
            completedAtNanos.clear();
            sessionStartNanos = 0;
            activeGoalsPanel.clearGoalInteractionState();
        }
        if (state == GameState.LOGGING_IN || state == GameState.HOPPING
            || state == GameState.LOGIN_SCREEN)
        {
            // Allow the login Xp sync to finish before treating updates as gains.
            xpBaselineTicks = 2;
        }
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
    {
        String key = configManager.getRSProfileKey();
        if (loadedRSProfileKey != null && !Objects.equals(key, loadedRSProfileKey))
        {
            profileSession++;
            pauseIndividualGoals();
            individualGoals.clearTrainingActivity();
            individualGoals = new GoalTracker();
            individualGoalsLoaded = false;
            loadedRSProfileKey = null;
            lastXp.clear();
            clearSessionOverviewXp();
            clearGoalCardXpStats();
            clearActionSamples();
            sessionStartNanos = 0;
            xpBaselineTicks = 2;
            activeGoalsPanel.clearGoalInteractionState();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        if (xpBaselineTicks == 0)
        {
            expireGoalExpansions(System.nanoTime());
            boolean removed = false;
            for (GoalTracker.Goal goal : new ArrayList<>(individualGoals.goals()))
            {
                if (goal.completed && System.nanoTime() - goal.completedAtNanos >= COMPLETION_DISPLAY_NANOS)
                {
                    individualGoals.remove(goal.id);
                    removed = true;
                }
            }
            if (removed)
            {
                saveIndividualGoals();
                saveCreationTimes();
                activeGoalsPanel.requestRefresh();
            }
            for (Skill skill : new ArrayList<>(completedAtNanos.keySet()))
            {
                if (System.nanoTime() - completedAtNanos.get(skill) >= COMPLETION_DISPLAY_NANOS)
                {
                    clearTarget(skill);
                    activeGoalsPanel.requestRefresh();
                }
            }
            return;
        }
        if (--xpBaselineTicks != 0) return;

        if (configManager.getRSProfileKey() == null)
        {
            xpBaselineTicks = 1;
            return;
        }

        long accountHash = client.getAccountHash();
        if (trackedAccountHash != null && trackedAccountHash != accountHash)
        {
            pauseIndividualGoals();
            individualGoals.clearTrainingActivity();
            individualGoals = new GoalTracker();
            clearGoalCardXpStats();
            clearSessionOverviewXp();
            clearActionSamples();
            goalCardExpansionStartedNanos.clear();
            completedAtNanos.clear();
            individualGoalsLoaded = false;
            loadedRSProfileKey = null;
            sessionStartNanos = 0;
        }
        trackedAccountHash = accountHash;
        if (sessionStartNanos == 0) sessionStartNanos = System.nanoTime();
        if (!individualGoalsLoaded)
        {
            String saved = configManager.getRSProfileConfiguration(CONFIG_GROUP, "individualGoals");
            GoalTracker cached = sessionGoals.get(configManager.getRSProfileKey());
            individualGoals = cached != null ? cached : GoalTracker.decode(saved);
            if (cached == null)
            {
                String creationTimes = configManager.getRSProfileConfiguration(CONFIG_GROUP, "goalCreationTimes");
                individualGoals.restoreCreationTimes(creationTimes);
                boolean removedCompleted = false;
                for (GoalTracker.Goal goal : new ArrayList<>(individualGoals.goals()))
                    if (goal.completed)
                    {
                        individualGoals.remove(goal.id);
                        removedCompleted = true;
                    }
                if (removedCompleted) saveCreationTimes();
                String collapsed = configManager.getRSProfileConfiguration(CONFIG_GROUP, "manuallyCollapsedGoals");
                individualGoals.restoreManualCollapses(collapsed);
                if (collapsed != null && !collapsed.equals(individualGoals.encodeManualCollapses()))
                    saveManualCollapses();
            }
            individualGoalsLoaded = true;
            loadedRSProfileKey = configManager.getRSProfileKey();
            saveIndividualGoals();
        }
        resumeIndividualGoals(System.nanoTime());
        lastXp.clear();
        for (Skill skill : Skill.values())
        {
            if (skill != Skill.OVERALL)
            {
                int xp = client.getSkillExperience(skill);
                if (xp >= 0)
                {
                    lastXp.put(skill, xp);
                }
            }
        }
        boolean changed = false;
        for (GoalTracker.Goal goal : individualGoals.goals())
        {
            if (goal.skill == null) continue;
            int xp = client.getSkillExperience(goal.skill);
            if (!goal.completed && xp >= goal.targetXp)
            {
                goal.completed = true;
                boolean wasManuallyCollapsed = goal.manuallyCollapsed;
                goal.manuallyCollapsed = false;
                goal.completedAtNanos = System.nanoTime();
                goal.expandedAtNanos = goal.completedAtNanos;
                changed = true;
                if (wasManuallyCollapsed) saveManualCollapses();
            }
        }
        if (changed) saveIndividualGoals();
        activeGoalsPanel.requestRefresh();
    }

    private void loadIndividualGoals()
    {
        String saved = configManager.getConfiguration(CONFIG_GROUP, "individualGoals");
        if (saved != null)
        {
            individualGoals = GoalTracker.decode(saved);
        }
        else
        {
            GoalTracker migrated = new GoalTracker();
            for (Skill skill : getOrderedSkills())
            {
                long target = getTargetXp(skill);
                int current = client.getSkillExperience(skill);
                if (target <= 0 || current < 0) continue;
                int start = getIntSetting("goalStartXp." + skill.name(), current);
                GoalTracker.Goal goal = migrated.restore(java.util.UUID.randomUUID().toString(),
                    skill, start, target, hasCustomXpTarget(skill) ? 0 : getTargetLevel(skill));
                goal.completed = getBooleanSetting("completed." + skill.name(), false);
            }
            individualGoals = migrated;
            configManager.setConfiguration(CONFIG_GROUP, "individualGoals", individualGoals.encode());
        }
        individualGoalsLoaded = true;
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        Skill skill = event.getSkill();
        if (xpBaselineTicks > 0 || skill == Skill.OVERALL) return;
        int xp = event.getXp();
        if (xp < 0) return;
        Integer previous = lastXp.put(skill, xp);
        if (previous == null)
        {
            activeGoalsPanel.requestRefresh();
            return;
        }
        if (xp < previous)
        {
            clearSkillTracking(skill);
            activeGoalsPanel.requestRefresh();
            return;
        }
        if (xp > previous)
        {
            int gain = xp - previous;
            long now = System.nanoTime();
            sessionOverviewXp.merge(skill, (long) gain, Long::sum);
            boolean hasIndividualGoal = individualGoals.goals().stream()
                .anyMatch(goal -> goal.skill == skill && !goal.completed);
            if (hasIndividualGoal)
            {
                expireGoalExpansions(now);
                boolean collapseChanged = individualGoals.gain(skill, xp, gain, now);
                individualGoals.recordTrainingGain(skill, now, getGoalOrderingActivitySeconds());
                saveIndividualGoals();
                if (collapseChanged) saveManualCollapses();
            }
            if (getTargetXp(skill) > 0 || hasIndividualGoal)
            {
                goalCardXp.merge(skill, (long) gain, Long::sum);
                Deque<Integer> samples = recentXpGainSamples.computeIfAbsent(skill, ignored -> new ArrayDeque<>());
                samples.addLast(gain);
                if (samples.size() > ACTION_SAMPLE_LIMIT) samples.removeFirst();
                if (!firstGainNanos.containsKey(skill))
                {
                    firstGainNanos.put(skill, now);
                }
                goalRateXp.merge(skill, (long) gain, Long::sum);
                expandGoalCard(skill);
            }
        }
        checkGoalCompletion(skill, xp);
        // Update the dashboard only after all Xp counters and samples are current.
        activeGoalsPanel.requestRefresh();
    }

    int getIntSetting(String key, int fallback)
    {
        String value = configManager.getConfiguration(CONFIG_GROUP, key);
        if (value == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    int getGoalCardExpansionSeconds()
    {
        int seconds = getIntSetting("goalCardExpansionSeconds", DEFAULT_GOAL_CARD_EXPANSION_SECONDS);
        return Math.max(MIN_GOAL_CARD_EXPANSION_SECONDS,
            Math.min(MAX_GOAL_CARD_EXPANSION_SECONDS, seconds));
    }

    int getGoalOrderingActivitySeconds()
    {
        int seconds = getIntSetting("goalOrderingActivitySeconds", 300);
        return Math.max(1, Math.min(86_400, seconds));
    }

    void setGoalCardExpansionSeconds(int seconds)
    {
        if (seconds < MIN_GOAL_CARD_EXPANSION_SECONDS
            || seconds > MAX_GOAL_CARD_EXPANSION_SECONDS)
        {
            throw new IllegalArgumentException("Expansion window must be 1–86,400 seconds");
        }
        configManager.setConfiguration(CONFIG_GROUP, "goalCardExpansionSeconds", seconds);
    }

    List<Skill> getOrderedSkills()
    {
        List<Skill> ordered = new ArrayList<>();
        String raw = configManager.getConfiguration(CONFIG_GROUP, "goalOrder");
        if (raw != null)
        {
            for (String name : raw.split(","))
            {
                try
                {
                    Skill skill = Skill.valueOf(name);
                    if (skill != Skill.OVERALL && getTargetXp(skill) > 0 && !ordered.contains(skill)) ordered.add(skill);
                }
                catch (IllegalArgumentException ignored) { }
            }
        }
        for (Skill skill : Skill.values())
            if (skill != Skill.OVERALL && getTargetXp(skill) > 0 && !ordered.contains(skill))
                ordered.add(skill);
        return ordered;
    }

    void moveGoal(Skill skill, int direction)
    {
        List<Skill> ordered = getOrderedSkills();
        int index = ordered.indexOf(skill);
        int other = index + direction;
        if (index < 0 || other < 0 || other >= ordered.size()) return;
        Collections.swap(ordered, index, other);
        StringBuilder value = new StringBuilder();
        for (Skill item : ordered)
        {
            if (value.length() > 0) value.append(',');
            value.append(item.name());
        }
        configManager.setConfiguration(CONFIG_GROUP, "goalOrder", value.toString());
    }

    static long estimateActionsLeft(long remainingXp, Iterable<Integer> samples)
    {
        if (remainingXp <= 0) return 0;
        long total = 0;
        int count = 0;
        for (int sample : samples)
        {
            if (sample > 0)
            {
                total += sample;
                count++;
            }
        }
        if (count == 0 || total == 0) return -1;
        return (long) Math.ceil((double) remainingXp * count / total);
    }

    long getActionsLeft(Skill skill, long remainingXp)
    {
        Deque<Integer> samples = recentXpGainSamples.get(skill);
        return estimateActionsLeft(remainingXp, samples == null ? Collections.emptyList() : samples);
    }

    void clearActionSamples()
    {
        recentXpGainSamples.clear();
        for (GoalTracker.Goal goal : individualGoals.goals()) goal.samples.clear();
    }

    long getGoalCardSessionXp(Skill skill)
    {
        return goalCardXp.getOrDefault(skill, 0L);
    }

    /** Clear goal statistics when the plugin starts or the account changes. */
    void clearGoalCardXpStats()
    {
        goalCardXp.clear();
        goalRateXp.clear();
        firstGainNanos.clear();
        for (GoalTracker.Goal goal : individualGoals.goals())
        {
            goal.sessionXp = 0;
            goal.rateXp = 0;
            goal.ratePeriodStartNanos = 0;
        }
    }

    /** Clear session statistics when the plugin starts or the account changes. */
    void clearSessionOverviewXp()
    {
        sessionOverviewXp.clear();
    }

    long getSessionOverviewTotalXp()
    {
        long total = 0;
        for (long xp : sessionOverviewXp.values()) total += xp;
        return total;
    }

    long getXpPerHour(Skill skill)
    {
        Long first = firstGainNanos.get(skill);
        if (first == null) return 0;
        long active = Math.max(0L, System.nanoTime() - first);
        double hours = Math.max(60.0, active / 1_000_000_000.0) / 3600.0;
        return Math.round(goalRateXp.getOrDefault(skill, 0L) / hours);
    }

    long getTargetXp(Skill skill)
    {
        String custom = configManager.getConfiguration(CONFIG_GROUP, "xpTarget." + skill.name());
        if (custom != null)
        {
            try
            {
                long xp = Long.parseLong(custom);
                if (xp > 0 && xp <= 200_000_000L) return xp;
            }
            catch (NumberFormatException ignored) { }
        }
        int level = getTargetLevel(skill);
        return level >= MIN_TARGET_LEVEL ? xpForLevel(level) : -1;
    }

    boolean hasCustomXpTarget(Skill skill)
    {
        return isValidCustomXpTarget(configManager.getConfiguration(CONFIG_GROUP, "xpTarget." + skill.name()));
    }

    static boolean isValidCustomXpTarget(String value)
    {
        if (value == null) return false;
        try
        {
            long xp = Long.parseLong(value);
            return xp >= 1 && xp <= 200_000_000L;
        }
        catch (NumberFormatException ignored)
        {
            return false;
        }
    }

    void setXpTarget(Skill skill, long xp)
    {
        if (xp < 1 || xp > 200_000_000L) throw new IllegalArgumentException("Xp must be 1–200,000,000");
        int current = client.getSkillExperience(skill);
        if (current < 0 || xp <= current || isGoalCompleted(skill))
            throw new IllegalArgumentException("Target must exceed current Xp and the goal must not be completed");
        ensureGoalStartXp(skill, current);
        configManager.setConfiguration(CONFIG_GROUP, "xpTarget." + skill.name(), xp);
        expandGoalCard(skill);
    }

    private void ensureGoalStartXp(Skill skill, int xp)
    {
        String key = "goalStartXp." + skill.name();
        if (configManager.getConfiguration(CONFIG_GROUP, key) == null)
            configManager.setConfiguration(CONFIG_GROUP, key, xp);
    }

    double getGoalProgress(Skill skill, int xp, long target)
    {
        int start = getIntSetting("goalStartXp." + skill.name(), xp);
        return goalProgress(start, xp, target);
    }

    static double goalProgress(long start, long xp, long target)
    {
        if (xp >= target) return 1.0;
        if (target <= start) return 0.0;
        return Math.max(0.0, Math.min(1.0, (xp - start) / (double) (target - start)));
    }

    boolean isGoalCompleted(Skill skill)
    {
        return completedAtNanos.containsKey(skill);
    }

    private void checkGoalCompletion(Skill skill, int xp)
    {
        long target = getTargetXp(skill);
        if (target > 0 && xp >= target && !isGoalCompleted(skill))
        {
            completedAtNanos.put(skill, System.nanoTime());
            configManager.setConfiguration(CONFIG_GROUP, "completed." + skill.name(), true);
            expandGoalCard(skill);
        }
    }

    void clearXpTarget(Skill skill)
    {
        configManager.unsetConfiguration(CONFIG_GROUP, "xpTarget." + skill.name());
    }


    boolean isGoalCollapsed(Skill skill)
    {
        return !isWithinGoalCardExpansionWindow(
            goalCardExpansionStartedNanos.get(skill),
            System.nanoTime(),
            getGoalCardExpansionSeconds());
    }

    void expandGoalCard(Skill skill)
    {
        goalCardExpansionStartedNanos.put(skill, System.nanoTime());
    }

    void collapseGoalCard(Skill skill)
    {
        goalCardExpansionStartedNanos.remove(skill);
    }

    static boolean isWithinGoalCardExpansionWindow(Long startedNanos, long activeNowNanos,
                                                    int expansionSeconds)
    {
        if (startedNanos == null || expansionSeconds < 1) return false;
        long elapsedNanos = Math.max(0L, activeNowNanos - startedNanos);
        return elapsedNanos < expansionSeconds * NANOS_PER_SECOND;
    }

    long getSessionSeconds()
    {
        if (sessionStartNanos == 0) return 0;
        return Math.max(0, (System.nanoTime() - sessionStartNanos) / 1_000_000_000L);
    }

    boolean getBooleanSetting(String key, boolean defaultValue)
    {
        String value = configManager.getConfiguration(CONFIG_GROUP, key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    void showLevelTargetDialog(Skill skill)
    {
        SwingUtilities.invokeLater(() ->
        {
            int oldTarget = getTargetLevel(skill);
            String initial = oldTarget >= MIN_TARGET_LEVEL ? Integer.toString(oldTarget) : "";
            String value = ActiveGoalsDialogs.promptText(
                client.getCanvas(),
                "Enter a target level (2–126) for " + skill.getName() + ":",
                "Set " + skill.getName() + " target",
                initial);

            if (value == null)
            {
                return;
            }

            try
            {
                int level = Integer.parseInt(value.trim());
                if (level < MIN_TARGET_LEVEL || level > MAX_TARGET_LEVEL)
                {
                    JOptionPane.showMessageDialog(client.getCanvas(),
                        "Enter a whole number from 2 to 126.",
                        "Invalid target level", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                runOnClientThread(() ->
                {
                    int current = client.getSkillExperience(skill);
                    if (current < 0 || xpForLevel(level) <= current || isGoalCompleted(skill))
                    {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(client.getCanvas(),
                            "Target must be above your current Xp and the goal must not be completed.",
                            "Invalid target level", JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    ensureGoalStartXp(skill, current);
                    configManager.setConfiguration(CONFIG_GROUP, skill.name(), level);
                    clearXpTarget(skill);
                    expandGoalCard(skill);
                    activeGoalsPanel.requestRefresh();
                });
            }
            catch (NumberFormatException ex)
            {
                JOptionPane.showMessageDialog(client.getCanvas(),
                    "Enter a whole number from 2 to 126.",
                    "Invalid target level", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    void clearTarget(Skill skill)
    {
        configManager.unsetConfiguration(CONFIG_GROUP, skill.name());
        clearXpTarget(skill);
        configManager.unsetConfiguration(CONFIG_GROUP, "goalStartXp." + skill.name());
        configManager.unsetConfiguration(CONFIG_GROUP, "completed." + skill.name());
        completedAtNanos.remove(skill);
        clearSkillTracking(skill);
    }

    private void clearSkillTracking(Skill skill)
    {
        goalCardXp.remove(skill);
        recentXpGainSamples.remove(skill);
        goalRateXp.remove(skill);
        firstGainNanos.remove(skill);
        goalCardExpansionStartedNanos.remove(skill);
    }

    int getTargetLevel(Skill skill)
    {
        String value = configManager.getConfiguration(CONFIG_GROUP, skill.name());
        if (value == null)
        {
            return -1;
        }

        try
        {
            int level = Integer.parseInt(value);
            return level >= MIN_TARGET_LEVEL && level <= MAX_TARGET_LEVEL ? level : -1;
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    static int xpForLevel(int targetLevel)
    {
        if (targetLevel <= 1)
        {
            return 0;
        }

        long points = 0;
        for (int level = 1; level < targetLevel; level++)
        {
            points += (int) Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0));
        }
        return (int) (points / 4);
    }

    /** Virtual level derived from Xp; the OSRS real skill level remains capped at 99. */
    static int virtualLevelForXp(int xp)
    {
        int level = 1;
        while (level < MAX_TARGET_LEVEL && xp >= xpForLevel(level + 1))
        {
            level++;
        }
        return level;
    }

    void runOnClientThread(Runnable action)
    {
        clientThread.invokeLater(action);
    }

    private static BufferedImage createToolbarIcon()
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setColor(new Color(217, 177, 89));
            g.fillRoundRect(3, 3, 18, 18, 5, 5);
            g.setColor(new Color(41, 32, 23));
            g.fillRoundRect(5, 5, 14, 14, 3, 3);
            g.setColor(new Color(236, 207, 134));
            g.drawLine(8, 16, 16, 8);
            g.drawLine(7, 9, 15, 17);
            g.fillRect(13, 6, 5, 3);
        }
        finally { g.dispose(); }
        return image;
    }

    Client getClient()
    {
        return client;
    }
}
