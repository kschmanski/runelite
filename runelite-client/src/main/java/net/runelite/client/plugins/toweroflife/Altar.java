/*
 * Copyright (c) 2026, kschmanski <https://github.com/kschmanski>
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
package net.runelite.client.plugins.toweroflife;

import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * The six Symbol of life altars in the Tower of Life basement (Creature Creation).
 * Each altar needs two specific items before it can be activated to spawn its creature.
 */
@Getter
@RequiredArgsConstructor
enum Altar
{
	NEWTROOST("Newtroost", NpcID.TOL_NEWTROOST, new WorldPoint(3058, 4410, 0),
		ItemID.EYE_OF_NEWT, "Eye of newt", ItemID.FEATHER, "Feather"),
	UNICOW("Unicow", NpcID.TOL_UNICOW, new WorldPoint(3018, 4410, 0),
		ItemID.UNICORN_HORN, "Unicorn horn", ItemID.COW_HIDE, "Cowhide"),
	SPIDINE("Spidine", NpcID.TOL_SPIDINE, new WorldPoint(3043, 4361, 0),
		ItemID.RED_SPIDERS_EGGS, "Red spiders' eggs", ItemID.RAW_SARDINE, "Raw sardine"),
	SWORDCHICK("Swordchick", NpcID.TOL_SWORDCHICK, new WorldPoint(3034, 4361, 0),
		ItemID.RAW_SWORDFISH, "Raw swordfish", ItemID.RAW_CHICKEN, "Raw chicken"),
	JUBSTER("Jubster", NpcID.TOL_JUBSTER, new WorldPoint(3066, 4380, 0),
		ItemID._100_JUBBLY_MEAT_RAW, "Raw jubbly", ItemID.RAW_LOBSTER, "Raw lobster"),
	FROGEEL("Frogeel", NpcID.TOL_FROGEEL, new WorldPoint(3012, 4380, 0),
		ItemID.GIANT_FROG_LEGS, "Giant frog legs", ItemID.RAW_CAVE_EEL, "Raw cave eel");

	/**
	 * How far (in tiles) a Symbol of life object may be from the stored location and
	 * still be matched to that altar. The altars are at least 9 tiles apart.
	 */
	private static final int MATCH_DISTANCE = 4;

	private final String creatureName;
	private final int npcId;
	private final WorldPoint location;
	private final int firstItemId;
	private final String firstItemName;
	private final int secondItemId;
	private final String secondItemName;

	int[] getItemIds()
	{
		return new int[]{firstItemId, secondItemId};
	}

	String getItemName(int itemId)
	{
		return itemId == firstItemId ? firstItemName : secondItemName;
	}

	boolean requires(int itemId)
	{
		return itemId == firstItemId || itemId == secondItemId;
	}

	/**
	 * @return the altar that requires this item, or null if no altar uses it
	 */
	@Nullable
	static Altar forItem(int itemId)
	{
		for (Altar altar : values())
		{
			if (altar.requires(itemId))
			{
				return altar;
			}
		}
		return null;
	}

	/**
	 * @return the altar closest to the given point, or null if none is close enough
	 */
	@Nullable
	static Altar nearest(WorldPoint point)
	{
		Altar best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Altar altar : values())
		{
			if (altar.location.getPlane() != point.getPlane())
			{
				continue;
			}

			int distance = altar.location.distanceTo2D(point);
			if (distance <= MATCH_DISTANCE && distance < bestDistance)
			{
				best = altar;
				bestDistance = distance;
			}
		}
		return best;
	}
}
