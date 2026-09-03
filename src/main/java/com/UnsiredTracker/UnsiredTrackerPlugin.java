package com.UnsiredTracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

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
	private static final String CONFIG_GROUP = "unsiredtracker";

	private static final Set<Integer> ABYSSAL_NEXUS_REGIONS = Set.of(
			11850,
			11851,
			12106,
			12362,
			12363
	);

	private static final Pattern KC_PATTERN =
			Pattern.compile("Your Abyssal Sire kill count is: ([\\d,]+)");

	private static final String UNSIRED_DROP_MESSAGE =
			"<col=ef1020>Untradeable drop: Unsired</col>";

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
	private int lastDry;
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
		saveAllStats();

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
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		if (event.getOverlay() != overlay)
		{
			return;
		}

		String option = event.getEntry().getOption();
		String target = event.getEntry().getTarget();

		if (!option.equals("Reset"))
		{
			return;
		}

		switch (target)
		{
			case "All Stats":
				resetAllStats();
				break;

			case "Last Unsired KC":
				resetLastUnsiredKC();
				break;

			case "Longest Dry":
				resetDryStreak();
				break;

			case "Most Spooned":
				resetBestStreak();
				break;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}

		if (config.applyBaselineKC())
		{
			lastUnsiredKC = config.baselineUnsiredKC();

			recalculateKillsSinceLastUnsired();

			saveAllStats();

			configManager.setConfiguration(
					CONFIG_GROUP,
					"applyBaselineKC",
					false
			);

            /*
            log.debug(
                    "Applied baseline KC {}. Current KC: {}. Current dry: {}",
                    lastUnsiredKC,
                    currentSireKC,
                    killsSinceLastUnsired
            );
            */
		}

        /*
        // CAN USE THIS FOR TESTING PURPOSES
        // Only uncomment this if simulateUnsiredDrop() exists in UnsiredTrackerConfig.
        if (config.simulateUnsiredDrop())
        {
            handleUnsiredDrop();

            configManager.setConfiguration(
                    CONFIG_GROUP,
                    "simulateUnsiredDrop",
                    false
            );

            log.info(
                    "Simulated Unsired drop at KC {}. Last Unsired KC: {}. Current Dry: {}. Last Dry: {}. Longest Dry: {}. Most Spooned: {}",
                    currentSireKC,
                    lastUnsiredKC,
                    killsSinceLastUnsired,
                    lastDry,
                    dryStreak,
                    bestStreak
            );
        }
        */
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();
		String cleanMessage = Text.removeTags(message);

		// Temporary testing logs. Use info, not debug, so they show in IntelliJ.
		//log.info("CHAT RAW: {}", message);
		//log.info("CHAT CLEAN: {}", cleanMessage);

		Matcher matcher = KC_PATTERN.matcher(cleanMessage);

		if (matcher.find())
		{
			currentSireKC = Integer.parseInt(
					matcher.group(1)
							.replace(",", "")
							.trim()
			);

			recalculateKillsSinceLastUnsired();

			saveCurrentStats();

			//log.info("Updated Abyssal Sire KC to {}", currentSireKC);
			return;
		}

		if (message.equals(UNSIRED_DROP_MESSAGE))
		{
			handleUnsiredDrop();
		}
	}

	private void recalculateKillsSinceLastUnsired()
	{
		if (currentSireKC > 0 && lastUnsiredKC > 0 && currentSireKC >= lastUnsiredKC)
		{
			killsSinceLastUnsired = currentSireKC - lastUnsiredKC;
		}
		else
		{
			killsSinceLastUnsired = 0;
		}
	}

	private void handleUnsiredDrop()
	{
		if (currentSireKC <= 0)
		{
			log.debug("Unsired received, but current Sire KC is unknown. Drop ignored.");
			return;
		}

		if (lastUnsiredKC > 0)
		{
			int gap = currentSireKC - lastUnsiredKC;

			if (gap > 0)
			{
				lastDry = gap;

				if (gap > dryStreak)
				{
					dryStreak = gap;
				}

				if (bestStreak == 0 || gap < bestStreak)
				{
					bestStreak = gap;
				}
			}
		}

		lastUnsiredKC = currentSireKC;
		killsSinceLastUnsired = 0;

		saveAllStats();

		//log.debug("Unsired received at KC {}. Last dry: {}", currentSireKC, lastDry);
	}

	private void loadProfileData()
	{
		Integer savedCurrentKC =
				configManager.getRSProfileConfiguration(
						CONFIG_GROUP,
						"currentSireKC",
						Integer.class
				);

		Integer savedKillsSince =
				configManager.getRSProfileConfiguration(
						CONFIG_GROUP,
						"killsSinceLastUnsired",
						Integer.class
				);

		Integer savedLastUnsired =
				configManager.getRSProfileConfiguration(
						CONFIG_GROUP,
						"lastUnsiredKC",
						Integer.class
				);

		Integer savedLastDry =
				configManager.getRSProfileConfiguration(
						CONFIG_GROUP,
						"lastDry",
						Integer.class
				);

		Integer savedDry =
				configManager.getRSProfileConfiguration(
						CONFIG_GROUP,
						"dryStreak",
						Integer.class
				);

		Integer savedBest =
				configManager.getRSProfileConfiguration(
						CONFIG_GROUP,
						"bestStreak",
						Integer.class
				);

		currentSireKC = savedCurrentKC == null ? 0 : savedCurrentKC;
		killsSinceLastUnsired = savedKillsSince == null ? 0 : savedKillsSince;
		lastUnsiredKC = savedLastUnsired == null ? 0 : savedLastUnsired;
		lastDry = savedLastDry == null ? 0 : savedLastDry;
		dryStreak = savedDry == null ? 0 : savedDry;
		bestStreak = savedBest == null ? 0 : savedBest;
	}

	private void saveCurrentStats()
	{
		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"currentSireKC",
				currentSireKC
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"killsSinceLastUnsired",
				killsSinceLastUnsired
		);
	}

	private void saveAllStats()
	{
		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"currentSireKC",
				currentSireKC
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"killsSinceLastUnsired",
				killsSinceLastUnsired
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"lastUnsiredKC",
				lastUnsiredKC
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"lastDry",
				lastDry
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"dryStreak",
				dryStreak
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"bestStreak",
				bestStreak
		);
	}

	public void resetAllStats()
	{
		currentSireKC = 0;
		killsSinceLastUnsired = 0;
		lastUnsiredKC = 0;
		lastDry = 0;
		dryStreak = 0;
		bestStreak = 0;

		saveAllStats();
	}

	public void resetLastUnsiredKC()
	{
		lastUnsiredKC = 0;
		killsSinceLastUnsired = 0;

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"lastUnsiredKC",
				lastUnsiredKC
		);

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"killsSinceLastUnsired",
				killsSinceLastUnsired
		);
	}

	public void resetDryStreak()
	{
		dryStreak = 0;

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"dryStreak",
				dryStreak
		);
	}

	public void resetBestStreak()
	{
		bestStreak = 0;

		configManager.setRSProfileConfiguration(
				CONFIG_GROUP,
				"bestStreak",
				bestStreak
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

	public int getLastDry()
	{
		return lastDry;
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