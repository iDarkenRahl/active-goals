package com.activegoals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Skill;

/** In-memory tracking for distinct goals, including identical targets. */
final class GoalTracker
{
    private final List<Goal> goals = new ArrayList<>();
    private final Map<Skill, Long> lastTrainingXpNanos = new EnumMap<>(Skill.class);

    Goal add(Skill skill, int startXp, long targetXp, long now)
    {
        if (skill == null || skill == Skill.OVERALL || startXp < 0 || targetXp <= startXp
            || targetXp > 200_000_000L)
            throw new IllegalArgumentException("Invalid goal");
        if (goals.stream().noneMatch(goal -> goal.skill == skill && !goal.completed))
            lastTrainingXpNanos.remove(skill);
        Goal goal = new Goal(UUID.randomUUID().toString(), skill, startXp, targetXp, 0);
        goal.expandedAtNanos = now;
        goals.add(0, goal);
        return goal;
    }

    List<Goal> goals()
    {
        return Collections.unmodifiableList(goals);
    }

    void restoreSessionStats(GoalTracker previous)
    {
        if (previous == null) return;
        lastTrainingXpNanos.putAll(previous.lastTrainingXpNanos);
        Map<String, Goal> byId = new HashMap<>();
        for (Goal goal : previous.goals) byId.put(goal.id, goal);
        for (Goal goal : goals)
        {
            Goal old = byId.get(goal.id);
            if (old == null || goal.skill == null || old.skill != goal.skill) continue;
            goal.sessionXp = old.sessionXp;
            goal.rateXp = old.rateXp;
            goal.ratePeriodStartNanos = old.ratePeriodStartNanos;
            goal.samples.addAll(old.samples);
            if (!goal.manuallyCollapsed && !goal.completed) goal.expandedAtNanos = old.expandedAtNanos;
            if (goal.completed && old.completed)
            {
                goal.completedAtNanos = old.completedAtNanos;
                goal.expandedAtNanos = old.expandedAtNanos;
            }
        }
    }

    void clear()
    {
        goals.clear();
    }

    String encode()
    {
        StringBuilder data = new StringBuilder();
        for (Goal goal : goals)
        {
            if (data.length() > 0) data.append(';');
            if (goal.skill == null)
            {
                data.append(goal.id).append(",!CORRUPTED");
                continue;
            }
            data.append(goal.id).append(',').append(goal.skill.name()).append(',')
                .append(goal.startXp).append(',').append(goal.targetXp).append(',')
                .append(goal.targetLevel).append(',').append(goal.completed);
        }
        return data.toString();
    }

    String encodeManualCollapses()
    {
        StringBuilder data = new StringBuilder();
        for (Goal goal : goals)
        {
            if (!goal.manuallyCollapsed || goal.skill == null || goal.completed) continue;
            if (data.length() > 0) data.append(';');
            data.append(goal.id);
        }
        return data.toString();
    }

    void restoreManualCollapses(String data)
    {
        Set<String> ids = new HashSet<>();
        if (data != null && !data.isEmpty()) Collections.addAll(ids, data.split(";"));
        for (Goal goal : goals)
        {
            goal.manuallyCollapsed = goal.skill != null && !goal.completed && ids.contains(goal.id);
            if (goal.manuallyCollapsed) goal.expandedAtNanos = 0;
        }
    }

    String encodeCreationTimes()
    {
        StringBuilder data = new StringBuilder();
        for (Goal goal : goals)
        {
            if (goal.skill == null || goal.createdAtEpochMillis == null) continue;
            if (data.length() > 0) data.append(';');
            data.append(goal.id).append(',').append(goal.createdAtEpochMillis);
        }
        return data.toString();
    }

    void restoreCreationTimes(String data)
    {
        Map<String, Long> times = new HashMap<>();
        Set<String> duplicates = new HashSet<>();
        if (data != null && !data.isEmpty())
        {
            for (String record : data.split(";", -1))
            {
                String[] parts = record.split(",", -1);
                if (parts.length != 2 || parts[0].isEmpty()) continue;
                try
                {
                    long time = Long.parseLong(parts[1]);
                    if (times.putIfAbsent(parts[0], time) != null) duplicates.add(parts[0]);
                }
                catch (NumberFormatException ignored) { }
            }
        }
        for (Goal goal : goals)
            goal.createdAtEpochMillis = goal.skill == null || duplicates.contains(goal.id)
                ? null : times.get(goal.id);
    }

    static GoalTracker decode(String data)
    {
        GoalTracker tracker = new GoalTracker();
        if (data == null || data.isEmpty()) return tracker;
        Set<String> usedIds = new HashSet<>();
        Set<String> reservedIds = new HashSet<>();
        for (String record : data.split(";", -1))
        {
            String[] parts = record.split(",", -1);
            if ((parts.length == 6 || (parts.length == 2 && "!CORRUPTED".equals(parts[1])))
                && !parts[0].isEmpty()) reservedIds.add(parts[0]);
        }
        for (String record : data.split(";", -1))
        {
            String[] parts = record.split(",", -1);
            if (parts.length == 2 && "!CORRUPTED".equals(parts[1]) && !parts[0].isEmpty())
            {
                String id = uniqueId(parts[0], usedIds, reservedIds);
                tracker.restore(id, null, 0, 0, 0);
                continue;
            }
            try
            {
                if (parts.length != 6) throw new IllegalArgumentException();
                Skill skill = Skill.valueOf(parts[1]);
                int start = Integer.parseInt(parts[2]);
                long target = Long.parseLong(parts[3]);
                int level = Integer.parseInt(parts[4]);
                if (parts[0].isEmpty() || skill == Skill.OVERALL || start < 0
                    || target < 1 || target > 200_000_000L || level < 0 || level > 126
                    || (!"true".equals(parts[5]) && !"false".equals(parts[5])))
                    throw new IllegalArgumentException();
                Goal goal = tracker.restore(uniqueId(parts[0], usedIds, reservedIds), skill, start, target, level);
                goal.completed = Boolean.parseBoolean(parts[5]);
            }
            catch (IllegalArgumentException ignored)
            {
                tracker.restore(uniqueId(null, usedIds, reservedIds), null, 0, 0, 0);
            }
        }
        return tracker;
    }

    private static String uniqueId(String preferred, Set<String> used, Set<String> reserved)
    {
        if (preferred != null && used.add(preferred)) return preferred;
        String id;
        do { id = UUID.randomUUID().toString(); }
        while (used.contains(id) || reserved.contains(id));
        used.add(id);
        return id;
    }

    void remove(String id)
    {
        goals.removeIf(goal -> goal.id.equals(id));
        lastTrainingXpNanos.keySet().removeIf(skill -> goals.stream()
            .noneMatch(goal -> goal.skill == skill && !goal.completed));
    }

    void clearTrainingActivity()
    {
        lastTrainingXpNanos.clear();
    }

    void recordTrainingGain(Skill skill, long now, int delaySeconds)
    {
        if (goals.stream().noneMatch(goal -> goal.skill == skill && !goal.completed)) return;
        Long previous = lastTrainingXpNanos.put(skill, now);
        if (previous != null && now - previous < delaySeconds * 1_000_000_000L) return;
        List<Goal> moved = new ArrayList<>();
        List<Goal> remaining = new ArrayList<>();
        for (Goal goal : goals)
        {
            if (goal.manuallyCollapsed) continue;
            if (goal.skill == skill && !goal.completed) moved.add(goal);
            else remaining.add(goal);
        }
        moved.addAll(remaining);
        int next = 0;
        for (int i = 0; i < goals.size(); i++)
            if (!goals.get(i).manuallyCollapsed) goals.set(i, moved.get(next++));
    }

    boolean gain(Skill skill, int currentXp, int gain, long now)
    {
        if (gain <= 0) return false;
        boolean collapseChanged = false;
        for (Goal goal : goals)
            if (goal.skill == skill && !goal.completed)
            {
                goal.sessionXp += gain;
                goal.rateXp += gain;
                if (goal.ratePeriodStartNanos == 0) goal.ratePeriodStartNanos = now;
                if (!goal.manuallyCollapsed) goal.expandedAtNanos = now;
                goal.samples.addLast(gain);
                if (goal.samples.size() > 20) goal.samples.removeFirst();
                if (currentXp >= goal.targetXp)
                {
                    goal.completed = true;
                    goal.completedAtNanos = now;
                    goal.expandedAtNanos = now;
                    collapseChanged |= goal.manuallyCollapsed;
                    goal.manuallyCollapsed = false;
                }
            }
        if (goals.stream().noneMatch(goal -> goal.skill == skill && !goal.completed))
            lastTrainingXpNanos.remove(skill);
        return collapseChanged;
    }

    Goal restore(String id, Skill skill, int startXp, long targetXp, int targetLevel)
    {
        Goal goal = new Goal(id, skill, startXp, targetXp, targetLevel);
        goals.add(goal);
        return goal;
    }

    static final class Goal
    {
        final String id;
        final Skill skill;
        final int startXp;
        long targetXp;
        int targetLevel;
        Long createdAtEpochMillis;
        long sessionXp;
        long rateXp;
        long ratePeriodStartNanos;
        long expandedAtNanos;
        long completedAtNanos;
        boolean completed;
        boolean manuallyCollapsed;
        final Deque<Integer> samples = new ArrayDeque<>();

        void expireExpansion(long now, int seconds)
        {
            if (!completed && expandedAtNanos != 0
                && !ActiveGoalsPlugin.isWithinGoalCardExpansionWindow(expandedAtNanos, now, seconds))
            {
                expandedAtNanos = 0;
                ratePeriodStartNanos = 0;
                rateXp = 0;
                samples.clear();
            }
        }

        private Goal(String id, Skill skill, int startXp, long targetXp, int targetLevel)
        {
            this.id = id;
            this.skill = skill;
            this.startXp = startXp;
            this.targetXp = targetXp;
            this.targetLevel = targetLevel;
        }

        String id() { return id; }
        long sessionXp() { return sessionXp; }
        int startingLevel() { return ActiveGoalsPlugin.virtualLevelForXp(startXp); }
        double progress(int xp)
        {
            if (xp >= targetXp) return 1.0;
            if (targetXp <= startXp) return 0.0;
            return Math.max(0, Math.min(1, (xp - startXp) / (double) (targetXp - startXp)));
        }
    }
}
