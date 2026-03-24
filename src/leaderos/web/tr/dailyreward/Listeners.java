package leaderos.web.tr.dailyreward;

import leaderos.web.tr.dailyreward.database.DatabaseQueries;
import leaderos.web.tr.dailyreward.utils.*;
import leaderos.web.tr.dailyreward.utils.enums.RequirementType;
import leaderos.web.tr.dailyreward.utils.objects.*;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Listeners implements Listener {

	private final Main plugin;

	public Listeners(Main plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void loginInsert(PlayerJoinEvent e) {
		insertPlayerValues(e.getPlayer());
	}

	public void insertPlayerValues(Player player) {
		SchedulerUtils.runAsync(plugin, () -> {
			DatabaseQueries.registerPlayerToDB(player.getName());
			Cache playerCache = DatabaseQueries.getPlayerInformations(player.getName());

			if (plugin.getConfig().getString("Type").equalsIgnoreCase("streak")) {
				if (!RewardManager.isStreakValid(player.getName(), playerCache)) {
					boolean resetOnComplete = plugin.getConfig().getBoolean("ResetOnComplete", true);
					if (resetOnComplete || playerCache.getStreak() < Main.rewards.size()) {
						DatabaseQueries.resetPlayerStreak(player.getName());
						playerCache.setStreak(0);
					}
				}
			}

			RewardManager.cache.put(player.getName(), playerCache);
			RewardManager.sendNotifier(player);
		});
	}

	@EventHandler
	public void logoutRemover(PlayerQuitEvent e) {
		SchedulerUtils.runAsync(plugin, () -> {
			DatabaseQueries.savePlayerAllCache(e.getPlayer().getName());
		});
	}

	@EventHandler
	public void guiListener(InventoryClickEvent e) {
		if (e.getView().getTitle().contains(Main.color(Manager.getText("Lang", "Gui.name")))) {
			Player player = (Player) e.getWhoClicked();
			e.setCancelled(true);

			if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
			if (e.getClickedInventory() == null || e.getClickedInventory().getType() != InventoryType.CHEST) return;

			if (e.getCurrentItem().getItemMeta() != null && e.getCurrentItem().getItemMeta().hasEnchants()) {
				player.closeInventory();
				Title.sendTitleSeconds(player,
						Manager.getText("Lang", "Titles.DailyReward"),
						Manager.getText("Lang", "RewardMsg.alreadyTook"),
						1, 3, 1);
				return;
			}

			if (e.getSlot() >= 11 && e.getSlot() <= 17) {
				Cache cache = RewardManager.cache.get(player.getName());
				if (cache == null) return;

				int streak = cache.getStreak();
				int itemSlot = e.getSlot() - 11;

				if (itemSlot == streak && RewardManager.canPlayerTakeLoot(player.getName())) {

					SchedulerUtils.runSync(plugin, () -> {
						Date time = new Date();
						SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
						long unix = System.currentTimeMillis() / 1000L;

						cache.setLastTake(formatter.format(time));
						cache.setLastTakeUnix(unix);
						
						boolean resetOnComplete = plugin.getConfig().getBoolean("ResetOnComplete", true);
						int maxStreak = Main.rewards.size() - 1;
						
						if (streak < maxStreak) {
							cache.setStreak(streak + 1);
						} else {
							cache.setStreak(resetOnComplete ? 0 : streak + 1);
						}

						cache.setTotalLoot(cache.getTotalLoot() + 1);

						final String playerName = player.getName();
						SchedulerUtils.runAsync(plugin, () -> {
							DatabaseQueries.savePlayerAllCache(playerName);
						});

						Rewards reward = Main.rewards.get(itemSlot);

						boolean giveReward = true;
						boolean hasRequirement = false;

						if (reward.getRequirements() != null) {
							hasRequirement = true;
							Requirement req = reward.getRequirements();

							if (req.getRequirementType() == RequirementType.PERMISSION) {
								if (!player.hasPermission((String) req.getValue())) {
									giveReward = false;
								}
							} else {
								int playerValue = Integer.parseInt(PlaceholderAPI.setPlaceholders(player, "%" + req.getDataName() + "%"));
								if (playerValue < (int) req.getValue()) {
									giveReward = false;
								}
							}
						}

						player.closeInventory();

						if (hasRequirement && giveReward) {
							for (String s : reward.getRequirements().getRewards()) {
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Main.color(s).replace("%player%", player.getName()));
							}
						} else {
							for (String s : reward.getCommands()) {
								Bukkit.dispatchCommand(Bukkit.getConsoleSender(), Main.color(s).replace("%player%", player.getName()));
							}
						}

						Title.sendTitleSeconds(player,
								Manager.getText("Lang", "Titles.DailyReward"),
								Manager.getText("Lang", "RewardMsg.takingSuccess"),
								1, 3, 1);

						player.playSound(player.getLocation(), Sound.valueOf(plugin.getConfig().getString("rewardSound")), 0.1F, 0.1F);

						if (plugin.getConfig().getBoolean("debug")) {
							Bukkit.getConsoleSender().sendMessage(Main.color("&2GünlükÖdül &b" + player.getName() + " &7adlı üye &e" +
									reward.getDataName() + " &7ödülünü aldı!"));
						}
					});
				} else {
					player.closeInventory();
					Title.sendTitleSeconds(player,
							Manager.getText("Lang", "Titles.DailyReward"),
							Manager.getText("Lang", "RewardMsg.alreadyTook"),
							1, 3, 1);
				}
			}
		}
	}
}
