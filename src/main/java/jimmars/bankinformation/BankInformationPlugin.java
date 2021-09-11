package jimmars.bankinformation;

import com.google.common.base.MoreObjects;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.CONFIG_GROUP;
import static net.runelite.client.plugins.banktags.BankTagsPlugin.TAG_TABS_CONFIG;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Bank Information",
	description = "Shows the value of your bank in the sidebar"
)
@Slf4j
public class BankInformationPlugin extends Plugin
{
	@Inject
	Client client;

	@Inject
	ClientToolbar clientToolbar;

	@Inject
	ItemManager itemManager;

	@Inject
	TagManager tagManager;

	@Inject
	ConfigManager configManager;

	private BankInformationPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception
	{
		panel = new BankInformationPanel(this);

		final BufferedImage icon = ImageUtil.loadImageResource(BankInformationPlugin.class, "panel_icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Bank Information")
			.priority(5)
			.panel(panel)
			.icon(icon)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK.getId())
		{
			return;
		}

		final List<CachedItem> cachedItems = new ArrayList<>(event.getItemContainer().getItems().length);
		final List<String> uniqueTags = new ArrayList<>();

		for (Item item : event.getItemContainer().getItems())
		{
			if (itemManager.canonicalize(item.getId()) != item.getId() || item.getId() == -1)
			{
				continue;
			}
			String itemBankTags = configManager.getConfiguration(CONFIG_GROUP, "item_" + item.getId());

			itemBankTags = itemBankTags != null ? itemBankTags : "";
			Arrays.stream(itemBankTags.split(",")).forEach(tag -> {
				if (!uniqueTags.contains(tag) && !tag.equals(""))
				{
					uniqueTags.add(tag);
				}
			});

			int itemPrice = itemManager.getItemPrice(item.getId());
			ItemComposition itemDefinition = client.getItemDefinition(item.getId());

			cachedItems.add(new CachedItem(item.getId(), item.getQuantity(), itemDefinition.getName(), itemPrice, Arrays.asList(itemBankTags.split(","))));
		}

		SwingUtilities.invokeLater(() -> {
			panel.setItems(cachedItems);
			panel.setTags(uniqueTags);
			panel.populate();
		});
	}
}
