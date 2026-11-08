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

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(TowerOfLifeConfig.GROUP)
public interface TowerOfLifeConfig extends Config
{
	String GROUP = "toweroflife";

	@ConfigSection(
		name = "Colors",
		description = "Colors used by the altar overlay",
		position = 10
	)
	String colorSection = "colors";

	@ConfigItem(
		keyName = "showCreatureName",
		name = "Show creature name",
		description = "Show the name of the creature each altar creates",
		position = 1
	)
	default boolean showCreatureName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showItems",
		name = "Show required items",
		description = "List the two items each altar needs and whether they have been placed",
		position = 2
	)
	default boolean showItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showItemIcons",
		name = "Show item icons",
		description = "Draw an icon next to each required item",
		position = 3
	)
	default boolean showItemIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideCompletedItems",
		name = "Hide placed items",
		description = "Only list the items an altar still needs",
		position = 4
	)
	default boolean hideCompletedItems()
	{
		return false;
	}

	@ConfigItem(
		keyName = "highlightAltar",
		name = "Highlight altars",
		description = "Outline each altar in a color that reflects its progress",
		position = 5
	)
	default boolean highlightAltar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "placedColor",
		name = "Placed",
		description = "Color of items already placed on the altar",
		position = 11,
		section = colorSection
	)
	default Color placedColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "inInventoryColor",
		name = "In inventory",
		description = "Color of needed items you are carrying",
		position = 12,
		section = colorSection
	)
	default Color inInventoryColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
		keyName = "missingColor",
		name = "Missing",
		description = "Color of needed items you are not carrying",
		position = 13,
		section = colorSection
	)
	default Color missingColor()
	{
		return Color.RED;
	}

	@ConfigItem(
		keyName = "readyColor",
		name = "Ready to activate",
		description = "Color of an altar that has both items and can be activated",
		position = 14,
		section = colorSection
	)
	default Color readyColor()
	{
		return Color.CYAN;
	}
}
