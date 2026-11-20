/*
 * Course values and completion points adapted from RuneLite's Courses.java.
 * Copyright (c) 2018, Seth <http://github.com/sethtroll>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.activegoals;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/** Client-thread confined, ephemeral state for the most recently completed course lap. */
final class AgilityCourseTracker
{
    private enum Course
    {
        GNOME(110.5, p(2484, 3437, 0), p(2487, 3437, 0)),
        SHAYZIEN_BASIC(153.5, p(1554, 3640, 0)), DRAYNOR(120, p(3103, 3261, 0)),
        AL_KHARID(216, p(3299, 3194, 0)), PYRAMID(722, p(3364, 2830, 0)),
        VARROCK(270, p(3236, 3417, 0)), PENGUIN(540, p(2652, 4039, 1)),
        BARBARIAN(153.2, p(2543, 3553, 0)), CANIFIS(240, p(3510, 3485, 0)),
        APE_ATOLL(580, p(2770, 2747, 0)), SHAYZIEN_ADVANCED(507.5, p(1522, 3625, 0)),
        FALADOR(586, p(3029, 3332, 0), p(3029, 3333, 0), p(3029, 3334, 0), p(3029, 3335, 0)),
        WYRM_BASIC(601.6), WILDERNESS(571.4, p(2993, 3933, 0), p(2994, 3933, 0), p(2995, 3933, 0)),
        WEREWOLF(730, p(3528, 9873, 0)), SEERS(570, p(2704, 3464, 0)), WYRM_ADVANCED(1053.6),
        POLLNIVNEACH(890, p(3363, 2998, 0)), RELLEKKA(780, p(2653, 3676, 0)),
        PRIFDDINAS(1337, p(3240, 6109, 0)), ARDOUGNE(889, p(2668, 3297, 0));

        final double xp;
        final WorldPoint[] endpoints;

        Course(double xp, WorldPoint... endpoints)
        {
            this.xp = xp;
            this.endpoints = endpoints;
        }
    }

    private Course course;
    private long lastLapNanos;

    private static WorldPoint p(int x, int y, int plane)
    {
        return new WorldPoint(x, y, plane);
    }

    void observeXp(WorldPoint point, long nowNanos)
    {
        if (point == null) return;
        for (Course candidate : Course.values())
        {
            for (WorldPoint endpoint : candidate.endpoints)
            {
                if (endpoint.equals(point))
                {
                    setCourse(candidate, nowNanos);
                    return;
                }
            }
        }
    }

    void observeVarbit(int id, int value, long nowNanos)
    {
        if (value != 6) return;
        if (id == VarbitID.VARLAMORE_WYRM_AGILITY_BASIC_PROGRESS) setCourse(Course.WYRM_BASIC, nowNanos);
        else if (id == VarbitID.VARLAMORE_WYRM_AGILITY_ADVANCED_PROGRESS) setCourse(Course.WYRM_ADVANCED, nowNanos);
    }

    private void setCourse(Course course, long nowNanos)
    {
        this.course = course;
        lastLapNanos = nowNanos;
    }

    double xpPerLap(Client client, long nowNanos, int timeoutSeconds)
    {
        if (course == null) return -1;
        if (timeoutSeconds <= 0 || nowNanos - lastLapNanos < 0
            || nowNanos - lastLapNanos >= timeoutSeconds * 1_000_000_000L)
        {
            reset();
            return -1;
        }
        switch (course)
        {
            case PYRAMID: return course.xp + Math.min(300 + 8 * client.getRealSkillLevel(Skill.AGILITY), 1000);
            case POLLNIVNEACH: return client.getVarbitValue(VarbitID.DESERT_DIARY_HARD_COMPLETE) == 1 ? 1016 : course.xp;
            case RELLEKKA: return client.getVarbitValue(VarbitID.FREMENNIK_DIARY_HARD_COMPLETE) == 1 ? 920 : course.xp;
            default: return course.xp;
        }
    }

    void reset()
    {
        course = null;
        lastLapNanos = 0;
    }
}
