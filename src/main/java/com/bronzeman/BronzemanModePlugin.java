package com.bronzeman;

import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ChatMessageType;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Bronzeman Mode"
)
public class BronzemanModePlugin extends Plugin
{
	private static final int AMOUNT_OF_TICKS_TO_SHOW_OVERLAY = 8;
	private static final String FILENAME = "bronzeman-mode-unlocks.txt";
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
	ItemManager itemManager;

	private final Set<Integer> unlockedItems = Sets.newHashSet();
	@Getter(AccessLevel.PACKAGE)
	private List<BufferedImage> recentUnlockedImages;
	@Getter(AccessLevel.PACKAGE)
	private boolean itemsRecentlyUnlocked;
	private int ticksToLastUnlock;

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

	private void loadUnlockedItems()
	{
		unlockedItems.clear();
		try
		{
			File saveFile = getSaveFile();
			BufferedReader r = new BufferedReader(new FileReader(saveFile));
			String l;
			while ((l = r.readLine()) != null)
			{
				unlockedItems.add(Integer.parseInt(l));
			}
			r.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private File getSaveFile() throws IOException
	{
		File saveFolder = new File(RuneLite.PROFILES_DIR, client.getUsername());
		if (!saveFolder.exists())
		{
			saveFolder.mkdirs();
		}

		File saveFile = new File(saveFolder, FILENAME);
		if (!saveFile.exists())
		{
			saveFile.createNewFile();

		}
		return saveFile;
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
			.filter(id -> !unlockedItems.contains(id) && id != -1)
			.collect(Collectors.toSet());

		if (recentUnlocks.isEmpty())
		{
			return;
		}
		log.info("Unlocked {} items, the ids were {}", recentUnlocks.size(), recentUnlocks);
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
		File saveFile;
		try
		{
			saveFile = getSaveFile();
			PrintWriter w = new PrintWriter(saveFile);
			for (int itemId : unlockedItems)
			{
				w.println(itemId);
			}
			w.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
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
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		Widget grandExchangeSearchResults = client.getWidget(162, 53);

		if (grandExchangeSearchResults == null)
		{
			return;
		}

		Widget[] children = grandExchangeSearchResults.getChildren();

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
