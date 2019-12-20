/*
 * Copyright (c) 2019, dekvall <https://github.com/dekvall>
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
package dekvall.bronzeman;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.Player;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayManager;
import static net.runelite.http.api.RuneLiteAPI.GSON;

@Slf4j
@PluginDescriptor(
	name = "Bronzeman Mode",
	description = "Unlock items as you acquire them (by dekvall)"
)
public class BronzemanModePlugin extends Plugin
{
	static final String CONFIG_GROUP = "bronzemanmode";
	public static final String CONFIG_KEY = "unlockeditems";
	private static final int AMOUNT_OF_TICKS_TO_SHOW_OVERLAY = 8;
	private static final int GE_REGION = 12598;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	@Inject
	private BronzemanModeConfig config;

	@Inject
	private BronzemanModeOverlay overlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	ItemManager itemManager;

	private final Set<Integer> unlockedItems = Sets.newHashSet();
	@Getter(AccessLevel.PACKAGE)
	private List<BufferedImage> recentUnlockedImages;
	@Getter(AccessLevel.PACKAGE)
	private boolean itemsRecentlyUnlocked;
	private int ticksToLastUnlock;
	private boolean inGeRegion;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadUnlockedItems();
		}

		log.info("Bronzeman Mode started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		unlockedItems.clear();
		log.info("Bronzeman Mode stopped!");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			loadUnlockedItems();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getItemContainer() != client.getItemContainer(InventoryID.INVENTORY))
		{
			return;
		}

		Set<Integer> recentUnlocks = Arrays.stream(client.getItemContainer(InventoryID.INVENTORY).getItems())
			.map(Item::getId)
			.map(itemManager::canonicalize)
			.filter(id -> !unlockedItems.contains(id) && id != -1)
			.collect(Collectors.toSet());

		if (recentUnlocks.isEmpty())
		{
			return;
		}

		log.info("Unlocked {} item(s), the id(s) were {}", recentUnlocks.size(), recentUnlocks);
		unlockedItems.addAll(recentUnlocks);
		recentUnlockedImages = recentUnlocks.stream().map(itemManager::getImage).collect(Collectors.toList());
		ticksToLastUnlock = 0;
		itemsRecentlyUnlocked = true;
		saveUnlockedItems();

		if (config.sendNotification())
		{
			notifier.notify("New bronzeman unlock!");
		}
	}

	private void saveUnlockedItems()
	{
		String key = client.getUsername() + "." + CONFIG_KEY;

		if (unlockedItems == null || unlockedItems.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, key);
			return;
		}

		String json = GSON.toJson(unlockedItems);
		configManager.setConfiguration(CONFIG_GROUP, key, json);
	}

	private void loadUnlockedItems()
	{
		String key = client.getUsername() + "." + CONFIG_KEY;

		String json = configManager.getConfiguration(CONFIG_GROUP, key);
		unlockedItems.clear();

		if (!Strings.isNullOrEmpty(json))
		{
			// CHECKSTYLE:OFF
			unlockedItems.addAll(GSON.fromJson(json, new TypeToken<List<Integer>>(){}.getType()));
			// CHECKSTYLE:ON
		}
	}

	@Provides
	BronzemanModeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BronzemanModeConfig.class);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (ticksToLastUnlock > AMOUNT_OF_TICKS_TO_SHOW_OVERLAY)
		{
			itemsRecentlyUnlocked = false;
		}
		ticksToLastUnlock += 1;

		killSearchResults();

		Player localPlayer = client.getLocalPlayer();
		inGeRegion = localPlayer != null && localPlayer.getWorldLocation().getRegionID() == GE_REGION;
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (inGeRegion)
		{
			killSearchResults();
		}
	}

	void killSearchResults()
	{
		Widget grandExchangeSearchResults = client.getWidget(162, 53);

		if (grandExchangeSearchResults == null)
		{
			return;
		}

		Widget[] children = grandExchangeSearchResults.getDynamicChildren();

		if (children == null || children.length < 2)
		{
			return;
		}

		for (int i = 0; i < children.length; i+= 3) {
			if (!unlockedItems.contains(children[i + 2].getItemId()))
			{
				children[i].setHidden(true);
				children[i + 1].setOpacity(60);
				children[i + 2].setOpacity(60);
			}
		}
	}
}
