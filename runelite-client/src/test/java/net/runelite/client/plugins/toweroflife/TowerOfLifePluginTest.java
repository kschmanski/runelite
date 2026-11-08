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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TowerOfLifePluginTest
{
	@Test
	public void everyItemBelongsToExactlyOneAltar()
	{
		Set<Integer> seen = new HashSet<>();
		for (Altar altar : Altar.values())
		{
			for (int itemId : altar.getItemIds())
			{
				assertTrue("duplicate item " + itemId, seen.add(itemId));
				assertEquals(altar, Altar.forItem(itemId));
			}
		}
		assertEquals(12, seen.size());
		assertNull(Altar.forItem(ItemID.COINS));
	}

	@Test
	public void nearestMatchesAltarTiles()
	{
		for (Altar altar : Altar.values())
		{
			WorldPoint center = altar.getLocation().dx(1).dy(1);
			assertEquals(altar, Altar.nearest(center));
		}
		assertNull(Altar.nearest(new WorldPoint(3040, 4385, 0)));
		assertNull(Altar.nearest(new WorldPoint(3058, 4410, 1)));
	}

	@Test
	public void mappingRoundTrip()
	{
		Map<Integer, Integer> mapping = ImmutableMap.of(
			VarbitID.TOL_SYMBOL_OBJ2, ItemID.FEATHER,
			VarbitID.TOL_SYMBOL_OBJ1, ItemID.EYE_OF_NEWT);
		String formatted = TowerOfLifePlugin.formatMapping(mapping);
		assertEquals(VarbitID.TOL_SYMBOL_OBJ1 + ":" + ItemID.EYE_OF_NEWT + "," + VarbitID.TOL_SYMBOL_OBJ2 + ":" + ItemID.FEATHER, formatted);
		assertEquals(mapping, TowerOfLifePlugin.parseMapping(formatted));
	}

	@Test
	public void parseMappingRejectsBadEntries()
	{
		String value = "garbage,1:2," + VarbitID.TOL_SYMBOL_OBJ1 + ":" + ItemID.COINS
			+ "," + VarbitID.TOL_SYMBOL_OBJ3 + ":" + ItemID.FEATHER
			+ "," + VarbitID.TOL_SYMBOL_OBJ4 + ":" + ItemID.FEATHER;
		assertEquals(ImmutableMap.of(VarbitID.TOL_SYMBOL_OBJ3, ItemID.FEATHER), TowerOfLifePlugin.parseMapping(value));
		assertTrue(TowerOfLifePlugin.parseMapping(null).isEmpty());
	}

	@Test
	public void parseItemsKeepsOnlyAltarItems()
	{
		assertEquals(ImmutableSet.of(ItemID.FEATHER, ItemID.RAW_LOBSTER),
			TowerOfLifePlugin.parseItems(ItemID.FEATHER + ", x," + ItemID.COINS + "," + ItemID.RAW_LOBSTER));
	}
}
