
package dekvall.bronzeman;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BronzemanModePlugin.CONFIG_GROUP)
public interface BronzemanModeConfig extends Config
{
	@ConfigItem(
		keyName = "notify",
		name = "Notify on unlock",
		description = "Send a notification when a new item is unlocked"
	)
	default boolean sendNotification()
	{
		return false;
	}
}
