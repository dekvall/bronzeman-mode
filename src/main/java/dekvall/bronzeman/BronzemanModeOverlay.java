package dekvall.bronzeman;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class BronzemanModeOverlay extends Overlay
{
	private final BronzemanModePlugin plugin;
	private final PanelComponent panelComponent = new PanelComponent();

	@Inject
	public BronzemanModeOverlay(BronzemanModePlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_CENTER);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		if (!plugin.isItemsRecentlyUnlocked() || plugin.getRecentUnlockedImages().isEmpty())
		{
			return null;
		}

		panelComponent.setOrientation(ComponentOrientation.HORIZONTAL);
		panelComponent.setWrapping(5);

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Unlocked:")
			.color(Color.GREEN)
			.build());

		for (BufferedImage image : plugin.getRecentUnlockedImages())
		{
			panelComponent.getChildren().add(new ImageComponent(image));
		}
		return panelComponent.render(graphics);
	}
}
