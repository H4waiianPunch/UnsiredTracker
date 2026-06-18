package com.UnsiredTracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
		name = "Unsired Tracker"
)
public class UnsiredTrackerPlugin extends Plugin
{
	private static final Set<Integer> ABYSSAL_NEXUS_REGIONS = Set.of(
			11850,
			11851,
			12106,
			12362,
			12363
	);


	private static final Pattern KC_PATTERN =
			Pattern.compile("Your Abyssal Sire kill count is: (\\d+)");

	private static final String UNSIRED_DROP_MESSAGE =
			"Untradeable drop: Unsired";

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private UnsiredTrackerOverlay overlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	private UnsiredTrackerConfig config;

	private boolean overlayAdded = false;

	private int currentSireKC;
	private int killsSinceLastUnsired;

	private int lastUnsiredKC;
	private int dryStreak;
	private int bestStreak;

	@Override
	protected void startUp()
	{
		loadProfileData();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayAdded = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadProfileData();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();

		if (player == null)
		{
			return;
		}

		WorldPoint location = player.getWorldLocation();

		if (location == null)
		{
			return;
		}

		boolean validRegion =
				ABYSSAL_NEXUS_REGIONS.contains(location.getRegionID());

		if (validRegion && !overlayAdded)
		{
			overlayManager.add(overlay);
			overlayAdded = true;
		}
		else if (!validRegion && overlayAdded)
		{
			overlayManager.remove(overlay);
			overlayAdded = false;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();

		Matcher matcher = KC_PATTERN.matcher(message);

		if (matcher.find())
		{
			currentSireKC = Integer.parseInt(matcher.group(1));

			if (lastUnsiredKC > 0)
			{
				killsSinceLastUnsired =
						currentSireKC - lastUnsiredKC;
			}

			saveCurrentStats();
			return;
		}

		if (message.equals(UNSIRED_DROP_MESSAGE))
		{
			handleUnsiredDrop();
		}
	}

	private void handleUnsiredDrop()
	{
		if (lastUnsiredKC > 0)
		{
			int gap = currentSireKC - lastUnsiredKC;

			if (gap > dryStreak)
			{
				dryStreak = gap;

				configManager.setRSProfileConfiguration(
						"unsiredtracker",
						"dryStreak",
						dryStreak
				);
			}

			if (bestStreak == 0 || gap < bestStreak)
			{
				bestStreak = gap;

				configManager.setRSProfileConfiguration(
						"unsiredtracker",
						"bestStreak",
						bestStreak
				);
			}
		}

		lastUnsiredKC = currentSireKC;
		killsSinceLastUnsired = 0;

		configManager.setRSProfileConfiguration(
				"unsiredtracker",
				"lastUnsiredKC",
				lastUnsiredKC
		);

		configManager.setRSProfileConfiguration(
				"unsiredtracker",
				"killsSinceLastUnsired",
				killsSinceLastUnsired
		);

		log.debug("Unsired received at KC {}", currentSireKC);
	}

	private void loadProfileData()
	{
		Integer savedCurrentKC =
				configManager.getRSProfileConfiguration(
						"unsiredtracker",
						"currentSireKC",
						Integer.class
				);

		Integer savedKillsSince =
				configManager.getRSProfileConfiguration(
						"unsiredtracker",
						"killsSinceLastUnsired",
						Integer.class
				);

		Integer savedLastUnsired =
				configManager.getRSProfileConfiguration(
						"unsiredtracker",
						"lastUnsiredKC",
						Integer.class
				);

		Integer savedDry =
				configManager.getRSProfileConfiguration(
						"unsiredtracker",
						"dryStreak",
						Integer.class
				);

		Integer savedBest =
				configManager.getRSProfileConfiguration(
						"unsiredtracker",
						"bestStreak",
						Integer.class
				);

		currentSireKC = savedCurrentKC == null ? 0 : savedCurrentKC;
		killsSinceLastUnsired = savedKillsSince == null ? 0 : savedKillsSince;
		lastUnsiredKC = savedLastUnsired == null ? 0 : savedLastUnsired;
		dryStreak = savedDry == null ? 0 : savedDry;
		bestStreak = savedBest == null ? 0 : savedBest;

		if (lastUnsiredKC == 0 && config.startingUnsiredKC() > 0)
		{
			lastUnsiredKC = config.startingUnsiredKC();

			configManager.setRSProfileConfiguration(
					"unsiredtracker",
					"lastUnsiredKC",
					lastUnsiredKC
			);
		}
	}

	private void saveCurrentStats()
	{
		configManager.setRSProfileConfiguration(
				"unsiredtracker",
				"currentSireKC",
				currentSireKC
		);

		configManager.setRSProfileConfiguration(
				"unsiredtracker",
				"killsSinceLastUnsired",
				killsSinceLastUnsired
		);
	}

	@Provides
	UnsiredTrackerConfig provideConfig()
	{
		return configManager.getConfig(
				UnsiredTrackerConfig.class
		);
	}

	public UnsiredTrackerConfig getConfig()
	{
		return config;
	}

	public int getCurrentSireKC()
	{
		return currentSireKC;
	}

	public int getLastUnsiredKC()
	{
		return lastUnsiredKC;
	}

	public int getKillsSinceLastUnsired()
	{
		return killsSinceLastUnsired;
	}

	public int getDryStreak()
	{
		return dryStreak;
	}

	public int getBestStreak()
	{
		return bestStreak;
	}
}
