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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ColorUtil;

class TowerOfLifeOverlay extends Overlay
{
	private static final int TEXT_Z_OFFSET = 180;
	private static final int ICON_SIZE = 20;
	private static final int ICON_GAP = 3;
	private static final int LINE_GAP = 2;

	private final Client client;
	private final TowerOfLifePlugin plugin;
	private final TowerOfLifeConfig config;
	private final ItemManager itemManager;

	@Value
	private static class Line
	{
		BufferedImage icon;
		String text;
		Color color;
	}

	@Inject
	private TowerOfLifeOverlay(Client client, TowerOfLifePlugin plugin, TowerOfLifeConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int plane = client.getTopLevelWorldView().getPlane();
		for (Map.Entry<Altar, GameObject> entry : plugin.getAltars().entrySet())
		{
			GameObject object = entry.getValue();
			if (object.getPlane() == plane)
			{
				renderAltar(graphics, entry.getKey(), object);
			}
		}
		return null;
	}

	private void renderAltar(Graphics2D graphics, Altar altar, GameObject object)
	{
		boolean ready = plugin.isReady(altar);

		if (config.highlightAltar())
		{
			Shape hull = object.getConvexHull();
			if (hull != null)
			{
				Color color = ready ? config.readyColor() : progressColor(altar);
				OverlayUtil.renderPolygon(graphics, hull, color, ColorUtil.colorWithAlpha(color, 30), graphics.getStroke());
			}
		}

		List<Line> lines = buildLines(altar, ready);
		if (lines.isEmpty())
		{
			return;
		}

		Point anchor = object.getCanvasLocation(TEXT_Z_OFFSET);
		if (anchor == null)
		{
			return;
		}

		FontMetrics metrics = graphics.getFontMetrics();
		int lineHeight = Math.max(metrics.getHeight(), lines.stream().anyMatch(l -> l.icon != null) ? ICON_SIZE : 0) + LINE_GAP;
		int y = anchor.getY() - (lineHeight * lines.size()) / 2;

		for (Line line : lines)
		{
			int iconWidth = line.icon != null ? ICON_SIZE + ICON_GAP : 0;
			int width = iconWidth + metrics.stringWidth(line.text);
			int x = anchor.getX() - width / 2;

			if (line.icon != null)
			{
				graphics.drawImage(line.icon, x, y + (lineHeight - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE, null);
			}

			int textY = y + (lineHeight + metrics.getAscent() - metrics.getDescent()) / 2;
			OverlayUtil.renderTextLocation(graphics, new Point(x + iconWidth, textY), line.text, line.color);
			y += lineHeight;
		}
	}

	private List<Line> buildLines(Altar altar, boolean ready)
	{
		List<Line> lines = new ArrayList<>(3);

		if (config.showCreatureName())
		{
			lines.add(new Line(null,
				ready ? altar.getCreatureName() + " - ready to activate" : altar.getCreatureName(),
				ready ? config.readyColor() : Color.WHITE));
		}

		if (config.showItems())
		{
			for (int itemId : altar.getItemIds())
			{
				boolean placed = plugin.isPlaced(itemId);
				if (placed && config.hideCompletedItems())
				{
					continue;
				}

				String status;
				Color color;
				if (placed)
				{
					status = "placed";
					color = config.placedColor();
				}
				else if (plugin.isInInventory(itemId))
				{
					status = "in inventory";
					color = config.inInventoryColor();
				}
				else
				{
					status = "needed";
					color = config.missingColor();
				}

				BufferedImage icon = config.showItemIcons() ? itemManager.getImage(itemId) : null;
				lines.add(new Line(icon, altar.getItemName(itemId) + " (" + status + ")", color));
			}
		}

		return lines;
	}

	/**
	 * Outline color for an altar that is not ready yet: yellow if every missing item
	 * is being carried, otherwise red.
	 */
	private Color progressColor(Altar altar)
	{
		for (int itemId : altar.getItemIds())
		{
			if (!plugin.isPlaced(itemId) && !plugin.isInInventory(itemId))
			{
				return config.missingColor();
			}
		}
		return config.inInventoryColor();
	}
}
