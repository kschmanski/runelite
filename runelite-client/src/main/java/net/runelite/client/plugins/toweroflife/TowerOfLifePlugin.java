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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Tower of Life",
	description = "Shows which creature each Symbol of life altar creates and which items it still needs",
	tags = {"creature", "creation", "altar", "symbol", "tol", "overlay", "minigame"}
)
@Slf4j
public class TowerOfLifePlugin extends Plugin
{
	static final Set<Integer> ALTAR_OBJECT_IDS = ImmutableSet.of(
		ObjectID.TOL_LIFESYMBOL_ALTER,
		ObjectID.TOL_LIFESYMBOL_ALTER_ACTIVE
	);

	/**
	 * One bit per item the server tracks as placed on an altar (6 altars x 2 items).
	 * Which bit belongs to which item is not available from the client, so the plugin
	 * learns the mapping as the player places items. See {@link #tryLearnVarbit()}.
	 */
	static final Set<Integer> SYMBOL_VARBITS = ImmutableSet.of(
		VarbitID.TOL_SYMBOL_OBJ1, VarbitID.TOL_SYMBOL_OBJ2, VarbitID.TOL_SYMBOL_OBJ3,
		VarbitID.TOL_SYMBOL_OBJ4, VarbitID.TOL_SYMBOL_OBJ5, VarbitID.TOL_SYMBOL_OBJ6,
		VarbitID.TOL_SYMBOL_OBJ7, VarbitID.TOL_SYMBOL_OBJ8, VarbitID.TOL_SYMBOL_OBJ9,
		VarbitID.TOL_SYMBOL_OBJ10, VarbitID.TOL_SYMBOL_OBJ11, VarbitID.TOL_SYMBOL_OBJ12
	);

	private static final String VARBIT_MAPPING_KEY = "varbitMapping";
	private static final String PLACED_ITEMS_KEY = "placedItems";

	/** Ticks the player has to walk to an altar after using an item on it. */
	private static final int PENDING_TIMEOUT_TICKS = 50;
	/** Ticks allowed between an item leaving the inventory and a symbol varbit being set. */
	private static final int LEARN_WINDOW_TICKS = 2;
	/** How far a newly spawned creature may be from its altar to count as ours. */
	private static final int CREATURE_SPAWN_DISTANCE = 12;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TowerOfLifeOverlay overlay;

	@Getter(AccessLevel.PACKAGE)
	private final Map<Altar, GameObject> altars = new EnumMap<>(Altar.class);

	/** Items we saw the player place, used for items whose varbit is not learned yet. */
	private final Set<Integer> locallyPlaced = new HashSet<>();

	/** Learned symbol varbit id -> item id. */
	private final Map<Integer, Integer> varbitToItem = new HashMap<>();
	private final Map<Integer, Integer> itemToVarbit = new HashMap<>();

	/** Counts of altar items currently in the inventory. */
	private final Map<Integer, Integer> inventoryCounts = new HashMap<>();
	private boolean inventoryKnown;

	private int pendingItem = -1;
	private int pendingItemTick;

	private Altar pendingActivation;
	private int pendingActivationTick;

	private int lastPlacedItem = -1;
	private int lastPlacedTick;
	private int lastSetVarbit = -1;
	private int lastSetVarbitTick;

	@Provides
	TowerOfLifeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TowerOfLifeConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		loadVarbitMapping();
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadPlacedItems();
				updateInventory(client.getItemContainer(InventoryID.INV));
				scanScene();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		altars.clear();
		locallyPlaced.clear();
		varbitToItem.clear();
		itemToVarbit.clear();
		inventoryCounts.clear();
		inventoryKnown = false;
		pendingItem = -1;
		pendingActivation = null;
		lastPlacedItem = -1;
		lastSetVarbit = -1;
	}

	/**
	 * @return whether the item has been placed on its altar
	 */
	boolean isPlaced(int itemId)
	{
		Integer varbit = itemToVarbit.get(itemId);
		if (varbit != null)
		{
			return client.getVarbitValue(varbit) == 1;
		}
		return locallyPlaced.contains(itemId);
	}

	boolean isInInventory(int itemId)
	{
		return inventoryCounts.getOrDefault(itemId, 0) > 0;
	}

	boolean isReady(Altar altar)
	{
		return isPlaced(altar.getFirstItemId()) && isPlaced(altar.getSecondItemId());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				altars.clear();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				altars.clear();
				inventoryCounts.clear();
				inventoryKnown = false;
				pendingItem = -1;
				pendingActivation = null;
				break;
			case LOGGED_IN:
				// The RuneScape profile (used for per-account state) is only known once logged in
				loadPlacedItems();
				break;
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		addAltar(event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		GameObject object = event.getGameObject();
		if (ALTAR_OBJECT_IDS.contains(object.getId()))
		{
			altars.values().remove(object);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!ALTAR_OBJECT_IDS.contains(event.getId()))
		{
			return;
		}

		if (event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT)
		{
			// "Use <item> -> Symbol of life"
			Widget selected = client.getSelectedWidget();
			if (selected != null && Altar.forItem(selected.getItemId()) != null)
			{
				pendingItem = selected.getItemId();
				pendingItemTick = client.getTickCount();
			}
		}
		else if ("Activate".equals(Text.removeTags(event.getMenuOption())))
		{
			WorldView wv = client.getTopLevelWorldView();
			WorldPoint point = WorldPoint.fromScene(wv, event.getParam0(), event.getParam1(), wv.getPlane());
			Altar altar = Altar.nearest(point);
			if (altar != null)
			{
				pendingActivation = altar;
				pendingActivationTick = client.getTickCount();
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.INV)
		{
			updateInventory(event.getItemContainer());
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		if (!SYMBOL_VARBITS.contains(varbit))
		{
			return;
		}

		if (event.getValue() == 1)
		{
			lastSetVarbit = varbit;
			lastSetVarbitTick = client.getTickCount();
			tryLearnVarbit();
		}
		else
		{
			// The server cleared the item (e.g. the altar was activated)
			Integer item = varbitToItem.get(varbit);
			if (item != null && locallyPlaced.remove(item))
			{
				savePlacedItems();
			}
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		if (pendingActivation == null)
		{
			return;
		}

		if (client.getTickCount() - pendingActivationTick > PENDING_TIMEOUT_TICKS)
		{
			pendingActivation = null;
			return;
		}

		NPC npc = event.getNpc();
		if (npc.getId() == pendingActivation.getNpcId()
			&& npc.getWorldLocation().distanceTo2D(pendingActivation.getLocation()) <= CREATURE_SPAWN_DISTANCE)
		{
			// Our creature appeared, so the altar consumed both items
			for (int itemId : pendingActivation.getItemIds())
			{
				locallyPlaced.remove(itemId);
			}
			savePlacedItems();
			pendingActivation = null;
		}
	}

	private void addAltar(GameObject object)
	{
		if (!ALTAR_OBJECT_IDS.contains(object.getId()))
		{
			return;
		}

		Altar altar = Altar.nearest(object.getWorldLocation());
		if (altar != null)
		{
			altars.put(altar, object);
		}
		else
		{
			log.debug("Symbol of life at unknown location {}", object.getWorldLocation());
		}
	}

	/**
	 * Picks up altars that were already loaded when the plugin started.
	 */
	private void scanScene()
	{
		WorldView wv = client.getTopLevelWorldView();
		Scene scene = wv.getScene();
		Tile[][] tiles = scene.getTiles()[wv.getPlane()];
		for (Tile[] column : tiles)
		{
			for (Tile tile : column)
			{
				if (tile == null)
				{
					continue;
				}

				for (GameObject object : tile.getGameObjects())
				{
					if (object != null)
					{
						addAltar(object);
					}
				}
			}
		}
	}

	private void updateInventory(ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		if (container != null)
		{
			for (Item item : container.getItems())
			{
				if (Altar.forItem(item.getId()) != null)
				{
					counts.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
		}

		if (inventoryKnown && pendingItem != -1)
		{
			if (client.getTickCount() - pendingItemTick > PENDING_TIMEOUT_TICKS)
			{
				pendingItem = -1;
			}
			else if (counts.getOrDefault(pendingItem, 0) < inventoryCounts.getOrDefault(pendingItem, 0))
			{
				onItemPlaced(pendingItem);
			}
		}

		inventoryCounts.clear();
		inventoryCounts.putAll(counts);
		inventoryKnown = true;
	}

	private void onItemPlaced(int itemId)
	{
		log.debug("Placed {} on the {} altar", itemId, Altar.forItem(itemId));
		pendingItem = -1;
		locallyPlaced.add(itemId);
		savePlacedItems();

		lastPlacedItem = itemId;
		lastPlacedTick = client.getTickCount();
		tryLearnVarbit();
	}

	/**
	 * When an item leaves the inventory onto an altar and a symbol varbit is set at
	 * nearly the same time, remember that the varbit tracks that item. After that the
	 * plugin reads the server's state directly, so it also survives client restarts.
	 */
	private void tryLearnVarbit()
	{
		if (lastPlacedItem == -1 || lastSetVarbit == -1
			|| Math.abs(lastPlacedTick - lastSetVarbitTick) > LEARN_WINDOW_TICKS)
		{
			return;
		}

		int item = lastPlacedItem;
		int varbit = lastSetVarbit;
		lastPlacedItem = -1;
		lastSetVarbit = -1;

		if (varbitToItem.containsKey(varbit) || itemToVarbit.containsKey(item))
		{
			return;
		}

		log.debug("Learned symbol varbit {} -> item {}", varbit, item);
		varbitToItem.put(varbit, item);
		itemToVarbit.put(item, varbit);
		configManager.setConfiguration(TowerOfLifeConfig.GROUP, VARBIT_MAPPING_KEY, formatMapping(varbitToItem));
	}

	private void loadVarbitMapping()
	{
		varbitToItem.clear();
		itemToVarbit.clear();
		varbitToItem.putAll(parseMapping(configManager.getConfiguration(TowerOfLifeConfig.GROUP, VARBIT_MAPPING_KEY)));
		varbitToItem.forEach((varbit, item) -> itemToVarbit.put(item, varbit));
	}

	private void loadPlacedItems()
	{
		locallyPlaced.clear();
		locallyPlaced.addAll(parseItems(configManager.getRSProfileConfiguration(TowerOfLifeConfig.GROUP, PLACED_ITEMS_KEY)));
	}

	private void savePlacedItems()
	{
		if (locallyPlaced.isEmpty())
		{
			configManager.unsetRSProfileConfiguration(TowerOfLifeConfig.GROUP, PLACED_ITEMS_KEY);
		}
		else
		{
			configManager.setRSProfileConfiguration(TowerOfLifeConfig.GROUP, PLACED_ITEMS_KEY, Joiner.on(',').join(locallyPlaced));
		}
	}

	/**
	 * Parses "varbit:item,varbit:item" keeping only entries that refer to a symbol
	 * varbit and an altar item.
	 */
	static Map<Integer, Integer> parseMapping(String value)
	{
		if (value == null || value.isEmpty())
		{
			return Collections.emptyMap();
		}

		Map<Integer, Integer> mapping = new HashMap<>();
		Set<Integer> seenItems = new HashSet<>();
		for (String entry : Splitter.on(',').trimResults().omitEmptyStrings().split(value))
		{
			int sep = entry.indexOf(':');
			if (sep < 0)
			{
				continue;
			}

			try
			{
				int varbit = Integer.parseInt(entry.substring(0, sep).trim());
				int item = Integer.parseInt(entry.substring(sep + 1).trim());
				if (SYMBOL_VARBITS.contains(varbit) && Altar.forItem(item) != null
					&& !mapping.containsKey(varbit) && seenItems.add(item))
				{
					mapping.put(varbit, item);
				}
			}
			catch (NumberFormatException ex)
			{
				log.debug("Ignoring bad varbit mapping entry {}", entry);
			}
		}
		return mapping;
	}

	static String formatMapping(Map<Integer, Integer> mapping)
	{
		return mapping.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(e -> e.getKey() + ":" + e.getValue())
			.collect(Collectors.joining(","));
	}

	static Set<Integer> parseItems(String value)
	{
		Set<Integer> items = new HashSet<>();
		if (value == null)
		{
			return items;
		}

		for (String part : Splitter.on(',').trimResults().omitEmptyStrings().split(value))
		{
			try
			{
				int item = Integer.parseInt(part);
				if (Altar.forItem(item) != null)
				{
					items.add(item);
				}
			}
			catch (NumberFormatException ex)
			{
				log.debug("Ignoring bad placed item {}", part);
			}
		}
		return items;
	}
}
