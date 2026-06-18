package com.UnsiredTracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("unsiredtracker")
public interface UnsiredTrackerConfig extends Config
{
	@ConfigSection(
			name = "Display Settings",
			description = "Choose which stats are shown on the overlay.",
			position = 0
	)
	String displaySection = "displaySection";

	@ConfigSection(
			name = "Setup Settings",
			description = "Configure baseline values.",
			position = 1
	)
	String setupSection = "setupSection";

	@ConfigItem(
			keyName = "currentKC",
			name = "Current KC",
			description = "Show current Abyssal Sire kill count.",
			section = displaySection
	)
	default boolean currentKC()
	{
		return true;
	}

	@ConfigItem(
			keyName = "lastUnsiredKC",
			name = "Last Unsired KC",
			description = "Show the kill count of your most recent Unsired.",
			section = displaySection
	)
	default boolean lastUnsiredKC()
	{
		return true;
	}

	@ConfigItem(
			keyName = "killsSinceLastUnsired",
			name = "Current Dry",
			description = "Show kills since your last Unsired.",
			section = displaySection
	)
	default boolean killsSinceLastUnsired()
	{
		return true;
	}

	@ConfigItem(
			keyName = "dryStreak",
			name = "Longest Dry",
			description = "Highest number of kills between Unsired drops.",
			section = displaySection
	)
	default boolean dryStreak()
	{
		return true;
	}

	@ConfigItem(
			keyName = "bestStreak",
			name = "Most Spooned",
			description = "Lowest number of kills between Unsired drops.",
			section = displaySection
	)
	default boolean bestStreak()
	{
		return true;
	}

	@ConfigItem(
			keyName = "baselineUnsiredKC",
			name = "Baseline Unsired KC",
			description = "Used to establish your last known Unsired KC.",
			section = setupSection
	)
	default int baselineUnsiredKC()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "applyBaselineKC",
			name = "Apply Baseline KC",
			description = "Apply the Baseline Unsired KC value.",
			section = setupSection
	)
	default boolean applyBaselineKC()
	{
		return false;
	}
}