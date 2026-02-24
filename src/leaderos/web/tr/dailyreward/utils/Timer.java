package leaderos.web.tr.dailyreward.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import leaderos.web.tr.dailyreward.Main;

public class Timer {

	public static String getToTake() {
		long toNextDay = getNextDayUnix() - (System.currentTimeMillis() / 1000);
		return String.format("%02d" + Manager.getText("Lang", "timeFormat.hour")
						+ " %02d" + Manager.getText("Lang", "timeFormat.minute") + " %02d" + Manager.getText("Lang", "timeFormat.hour"),
				toNextDay / 3600, (toNextDay % 3600) / 60, toNextDay % 60);
	}

	@SuppressWarnings("deprecation")
	public static long getNextDayUnix() {
		Date dt = new Date();
		dt.setHours(0);
		dt.setMinutes(0);
		dt.setSeconds(0);
		Calendar c = Calendar.getInstance();
		c.setTime(dt);
		c.add(Calendar.DATE, 1);
		dt = c.getTime();
		return dt.getTime() / 1000L;
	}

	private static SimpleDateFormat format = new SimpleDateFormat("HH:mm");

	public static boolean hasTime() {
		return Timer.format.format(new Date()).equals("00:00");
	}

	private static Object foliaTask = null;
	private static BukkitRunnable taskid;

	public static void taskAgain() {
		Plugin plugin = Main.instance;

		if (isFolia()) {
			if (foliaTask != null) {
				try {
					Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
					scheduledTaskClass.getMethod("cancel").invoke(foliaTask);
				} catch (Exception e) {
					e.printStackTrace();
				}
				foliaTask = null;
			}

			try {
				Object scheduledTask = plugin.getServer().getClass()
						.getMethod("getGlobalRegionScheduler")
						.invoke(plugin.getServer());

				Class<?> schedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
				Class<?> scheduledTaskClass = Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

				Object task = schedulerClass.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class)
						.invoke(scheduledTask, plugin, (java.util.function.Consumer<Object>) t -> {
							if (Timer.hasTime()) {
								runix();
								try {
									scheduledTaskClass.getMethod("cancel").invoke(t);
								} catch (Exception e) {
									e.printStackTrace();
								}
								foliaTask = null;
							}
						}, 800L, 800L);

				foliaTask = task;

			} catch (Exception e) {
				e.printStackTrace();
			}

		} else {
			if (taskid != null) {
				taskid.cancel();
				taskid = null;
			}

			taskid = new BukkitRunnable() {
				@Override
				public void run() {
					if (Timer.hasTime()) {
						runix();
						cancel();
					}
				}
			};
			taskid.runTaskTimerAsynchronously(plugin, 800L, 800L);
		}
	}

	public static void runix() {
		Plugin plugin = Main.instance;

		if (isFolia()) {
			Bukkit.getOnlinePlayers().forEach(RewardManager::dailyUpdate);

			try {
				Object scheduler = plugin.getServer().getClass()
						.getMethod("getGlobalRegionScheduler")
						.invoke(plugin.getServer());

				Class<?> schedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");

				schedulerClass.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class)
						.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) task -> taskAgain(), 2000L);

			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				for (Player s : Bukkit.getOnlinePlayers()) {
					RewardManager.dailyUpdate(s);
				}
				Bukkit.getScheduler().runTaskLater(plugin, Timer::taskAgain, 2000L);
			});
		}
	}

	private static boolean isFolia() {
		try {
			Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
