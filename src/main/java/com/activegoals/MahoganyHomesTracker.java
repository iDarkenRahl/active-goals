/*
 * Message formats adapted from the Mahogany Homes Plugin Hub plugin.
 * BSD 2-Clause License
 *
 * Copyright (c) 2020, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.activegoals;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Transient, client-thread-confined observations of Mahogany Homes contracts. */
final class MahoganyHomesTracker
{
    private static final int SAMPLE_LIMIT = 20;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    // Message formats were checked against the Mahogany Homes Plugin Hub plugin:
    // https://github.com/TheStonedTurtle/Mahogany-Homes/blob/master/src/main/java/thestonedturtle/mahoganyhomes/MahoganyHomesPlugin.java
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "^(Beginner|Novice|Adept|Expert) Contract: Go see .+\\.$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPLETION = Pattern.compile(
        "^You have completed [0-9,]+ contracts with a total of [0-9,]+ points?\\.$");

    private enum Tier { BEGINNER, NOVICE, ADEPT, EXPERT }

    private final Map<Tier, Deque<Integer>> samples = new EnumMap<>(Tier.class);
    private Tier currentTier;
    private Tier activeTier;
    private int activeStartXp;
    private boolean activeValid;
    private boolean pendingCompletion;
    private Tier pendingTier;
    private int pendingStartXp;
    private boolean pendingValid;
    private long pendingNanos;
    private int knownOutfit = -1;
    private boolean outfitAvailable;
    private boolean estimateActive;
    private int lastCompletionXp;
    private long lastCompletionNanos;

    /** The mask identifies only equipped Carpenter's outfit pieces; -1 means unavailable. */
    boolean observeOutfit(int mask)
    {
        if (mask < 0)
        {
            boolean changed = outfitAvailable;
            outfitAvailable = false;
            activeValid = false;
            pendingValid = false;
            return changed;
        }
        boolean changed = !outfitAvailable || (knownOutfit >= 0 && knownOutfit != mask);
        if (knownOutfit >= 0 && knownOutfit != mask)
        {
            samples.clear();
            activeValid = false;
            pendingValid = false;
            estimateActive = false;
        }
        knownOutfit = mask;
        outfitAvailable = true;
        return changed;
    }

    /** Observe a tag-stripped GAMEMESSAGE after login and profile initialization. */
    boolean observeMessage(String message, int xp, int outfitMask, long nowNanos)
    {
        boolean changed = observeOutfit(outfitMask);
        if (message == null) return changed;
        Matcher assignment = ASSIGNMENT.matcher(message);
        if (assignment.matches())
        {
            Tier next = Tier.valueOf(assignment.group(1).toUpperCase(Locale.ROOT));
            boolean overlap = activeTier != null || pendingCompletion;
            if (pendingCompletion) pendingValid = false;
            currentTier = next;
            activeTier = next;
            activeStartXp = xp;
            // An overlapping assignment is ambiguous; never sample a partial contract.
            activeValid = !overlap && xp >= 0 && outfitAvailable;
            return true;
        }
        if (COMPLETION.matcher(message).matches() && !pendingCompletion && activeTier != null)
        {
            pendingCompletion = true;
            pendingTier = activeTier;
            pendingStartXp = activeStartXp;
            pendingValid = activeValid && outfitAvailable && xp >= activeStartXp;
            pendingNanos = nowNanos;
            activeTier = null;
            activeValid = false;
            return true;
        }
        return changed;
    }

    /** Finish an observed completion on the next game tick, after the completion XP update. */
    boolean onTick(int xp, int outfitMask, long nowNanos)
    {
        boolean changed = observeOutfit(outfitMask);
        if (activeTier != null && xp < activeStartXp) activeValid = false;
        if (!pendingCompletion) return changed;
        if (pendingValid && outfitAvailable && xp > pendingStartXp)
        {
            Deque<Integer> values = samples.computeIfAbsent(pendingTier, ignored -> new ArrayDeque<>());
            values.addLast(xp - pendingStartXp);
            if (values.size() > SAMPLE_LIMIT) values.removeFirst();
        }
        if (xp >= 0 && outfitAvailable)
        {
            lastCompletionXp = xp;
            lastCompletionNanos = pendingNanos;
            estimateActive = true;
        }
        pendingCompletion = false;
        pendingTier = null;
        pendingValid = false;
        return true;
    }

    /** Estimates are fixed at the last completion XP; they do not change during repairs. */
    long estimateHomes(long targetXp, long nowNanos, int timeoutSeconds)
    {
        if (!estimateActive || !outfitAvailable || currentTier == null || timeoutSeconds <= 0) return -1;
        long elapsed = nowNanos - lastCompletionNanos;
        if (elapsed < 0 || elapsed >= timeoutSeconds * NANOS_PER_SECOND)
        {
            estimateActive = false;
            return -1;
        }
        Deque<Integer> values = samples.get(currentTier);
        if (values == null || values.isEmpty()) return -1;
        long total = 0;
        for (int value : values) total += value;
        long remaining = Math.max(0, targetXp - lastCompletionXp);
        return (long) Math.ceil((double) remaining * values.size() / total);
    }

    void reset()
    {
        samples.clear();
        currentTier = null;
        activeTier = null;
        activeValid = false;
        pendingCompletion = false;
        pendingTier = null;
        pendingValid = false;
        knownOutfit = -1;
        outfitAvailable = false;
        estimateActive = false;
        lastCompletionXp = 0;
        lastCompletionNanos = 0;
    }
}
