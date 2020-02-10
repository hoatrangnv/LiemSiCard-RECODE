package net.minefs.DoiCard;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.gmail.filoghost.holographicdisplays.api.Hologram;
import com.gmail.filoghost.holographicdisplays.api.HologramsAPI;

import loc4atnt.xlibs.config.SimpleConfig;
import loc4atnt.xlibs.config.SimpleConfigManager;
import net.minefs.DoiCard.Gui.TelcoSelector;
import net.minefs.DoiCard.SortUtil.SortUtil;
import net.minefs.GuiAPI.GuiInventory;

public class DoiCard extends JavaPlugin {

	private String server, id, key, secret, user, host, db, pass, cbUrl, pName;
	private int port;
	private static DoiCard _instance;
	private PlayerPoints pp;
	private double tile;
	private Location topHoloLoca;
	private SimpleConfig topLog, cachePtLog, cardQueueLog;
	private List<PlayerData> topList = new ArrayList<PlayerData>();
	private NumberFormat nF;
	private MySQL mysql;
	private Hologram holo;
	private BukkitTask waitingToDisplayHoloTask, checkQueueCardTask;
	private Runnable checkQueueCardRunnable;
	private List<Integer> cardQueueList = new ArrayList<Integer>();
	private HashMap<Integer, String> statusMessMap = new HashMap<Integer, String>();

	@Override
	public void onEnable() {
		Locale localeEN = new Locale("en", "EN");
		nF = NumberFormat.getInstance(localeEN);
		pp = (PlayerPoints) getServer().getPluginManager().getPlugin("PlayerPoints");
		_instance = this;
		saveDefaultConfig();
		try {
			loadConfig();
			new MySQL(host, port, user, pass, db);
			mysql = MySQL.getInstance();
			Bukkit.getServer().getPluginManager().registerEvents(new Listener() {
				@EventHandler
				public void onClick(InventoryClickEvent e) {
					Inventory inv = e.getClickedInventory();
					if (inv != null && inv.getHolder() != null && inv.getHolder() instanceof GuiInventory)
						((GuiInventory) inv.getHolder()).onClick(e);
				}

				@EventHandler(priority = EventPriority.HIGHEST)
				public void onChat(AsyncPlayerChatEvent e) {
					NapRequest request = NapRequest.getRequests().get(e.getPlayer().getName());
					if (request != null) {
						e.setCancelled(true);
						request.onChat(e.getMessage());
					}
				}

				@EventHandler
				public void onJoin(PlayerJoinEvent e) {
					Player p = e.getPlayer();
					String name = p.getName();
					int pts = cachePtLog.getInt(name + ".pts", 0);
					int money = cachePtLog.getInt(name + ".money", 0);
					if (pts > 0) {
						NapRequest.givePointAndLogToTop(p, pts, money);
						cachePtLog.removeKey(name);
						cachePtLog.saveConfig();
					}
				}
			}, this);
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, e.getMessage(), e);
			getServer().getPluginManager().disablePlugin(this);
		}

		SimpleConfigManager cfgMger = new SimpleConfigManager(this);
		topLog = cfgMger.getNewConfig("top.yml");
		cachePtLog = cfgMger.getNewConfig("cache_pt.yml");
		cardQueueLog = cfgMger.getNewConfig("card_queue.yml");

		getTop();
		waitingToDisplayHoloTask = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {

			@Override
			public void run() {
				if (getServer().getPluginManager().getPlugin("HolographicDisplays") != null) {
					displayHoloTop();
					Bukkit.getLogger().info("Đã hiển thị Holo Top 7 Donate");
					waitingToDisplayHoloTask.cancel();
				}
			}
		}, 60, 60);

		getCardQueue();
		checkQueueCardRunnable = new Runnable() {

			@Override
			public void run() {
				synchronized (mysql) {
					if (mysql == null)
						return;
					for (int i = 0; i < cardQueueList.size(); i++) {
						int tranid = cardQueueList.get(i).intValue();
						RechargeCardStatus statusObj = mysql.getLogStatus(tranid);
						if (statusObj != null) {
							int status = statusObj.getStatus();
							if (status > 0) {
								Player p = Bukkit.getPlayer(statusObj.getPlayerName());
								if (status == 1) {
									int amount = statusObj.getAmount();
									int pts = (int) (amount * getTile());
									mysql.logSuccess(tranid, amount, pts);
									if (p != null) {
										NapRequest.givePointAndLogToTop(p, pts, amount);
									} else {
										cacheGivePoint(statusObj.getPlayerName(), pts, amount);
									}
								} else {
									if (p != null) {
										String mess = getStatusMessage(status);
										p.sendMessage(
												"§7§l§m=========================================================");
										p.sendMessage("§f§l[§c§lNạp Thẻ§f§l]§r §c§lCó lỗi xảy ra: " + mess + " (mã lỗi "
												+ status + ")");
										if (status == 101 || status == 102 || status == 103) {
											p.sendMessage("§e§lLiên hệ Admin để xử lý!");
										}
										p.sendMessage(
												"§7§l§m=========================================================");
										p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 1f);
									}
								}
								cardQueueList.remove(i);
								saveCardQueueToConfig();
								i--;
							}
						}
					}
				}
			}
		};
		checkQueueCardTask = getServer().getScheduler().runTaskTimerAsynchronously(this, checkQueueCardRunnable, 20 * 5,
				20 * 5);
	}

	private void saveCardQueueToConfig() {
		cardQueueLog.set("queue_id", cardQueueList);
		cardQueueLog.saveConfig();
	}

	@SuppressWarnings("unchecked")
	private void getCardQueue() {
		cardQueueList.clear();
		if (cardQueueLog.contains("queue_id"))
			cardQueueList.addAll((List<Integer>) cardQueueLog.getList("queue_id"));
	}

	private void displayHoloTop() {
		if (topHoloLoca == null)
			return;
		if (holo != null)
			holo.delete();
		holo = HologramsAPI.createHologram(this, this.topHoloLoca);
		holo.appendTextLine("§c§lTop 7 người chơi nạp thẻ nhiều nhất");
		for (int i = 0; i < topList.size(); i++) {
			PlayerData pD = topList.get(i);
			String colorName = (i < 3) ? ". §e" : ". §a";
			String line = "§f" + (i + 1) + colorName + pD.getName() + "§f: §6" + nF.format(pD.getAmount() * 1000)
					+ "§eVNĐ";
			holo.appendTextLine(line);
		}
	}

	private void getTop() {
		topList.clear();
		if (topLog.contains("top7")) {
			for (String name : topLog.getConfigurationSection("top7").getKeys(false)) {
				int amount = topLog.getInt("top7." + name);
				PlayerData data = new PlayerData(name, amount);
				topList.add(data);
			}
			SortUtil.quickSort(topList);
		}
	}

	@Override
	public void onDisable() {
		if (holo != null)
			holo.delete();
		if (waitingToDisplayHoloTask != null)
			waitingToDisplayHoloTask.cancel();
		MySQL sql = MySQL.getInstance();
		if (sql != null) {
			MySQL.getInstance().close();
		}

		if (checkQueueCardTask != null)
			checkQueueCardTask.cancel();
		getLogger().info("Dong inventory...");
		Bukkit.getOnlinePlayers().forEach(p -> {
			InventoryView view = p.getOpenInventory();
			if (view == null)
				return;
			Inventory inv = view.getTopInventory();
			if (inv != null && inv.getHolder() != null && inv.getHolder() instanceof GuiInventory)
				p.closeInventory();
		});
	}

	@SuppressWarnings("rawtypes")
	public String createRequestUrl(Map<String, String> map) {
		String url_params = "";
		for (Map.Entry entry : map.entrySet()) {
			if (url_params == "")
				url_params += entry.getKey() + "=" + entry.getValue();
			else
				url_params += "&" + entry.getKey() + "=" + entry.getValue();
		}
		return url_params;
	}

	public String get(String urlz, String info) {
		String stuff = "";
		try {
			byte[] postData = info.getBytes(StandardCharsets.UTF_8);
			int postDataLength = postData.length;
			URL url = new URL(urlz);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			conn.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 5.1) AppleWebKit/535.11 (KHTML, like Gecko) Chrome/17.0.963.56 Safari/535.11");
			conn.setRequestProperty("charset", "utf-8");
			conn.setRequestProperty("Content-Length", Integer.toString(postDataLength));
			conn.setUseCaches(false);
			try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
				wr.write(postData);
			}
			InputStream inputStr = conn.getInputStream();
			InputStreamReader inputStreamReader = new InputStreamReader(inputStr);
			BufferedReader in = new BufferedReader(inputStreamReader);
			stuff = in.readLine();
			if (conn.getResponseCode() != 200) {
				return "error " + conn.getResponseCode();
			}
			return stuff;
		} catch (IOException e) {
			e.printStackTrace();
			return stuff;
		}
	}

	public void loadConfig() {
		cbUrl = getConfig().getString("callback_url");
		pName = getConfig().getString("point_name");
		server = getConfig().getString("server");
		id = getConfig().getString("id");
		key = getConfig().getString("key");
		secret = getConfig().getString("secret");
		tile = getConfig().getDouble("tile");
		user = getConfig().getString("mysql.user");
		host = getConfig().getString("mysql.host");
		db = getConfig().getString("mysql.database");
		pass = getConfig().getString("mysql.password");
		port = getConfig().getInt("mysql.port");
		topHoloLoca = (Location) getConfig().get("topHoloLoca");
		if (getConfig().contains("status")) {
			statusMessMap.clear();
			for (String k : getConfig().getConfigurationSection("status").getKeys(false)) {
				int code = Integer.parseInt(k);
				String mess = getConfig().getString("status." + k);
				statusMessMap.put(code, mess);
			}
		}
	}

	public String getPointName() {
		return pName;
	}

	public String getCallbackURL() {
		return cbUrl;
	}

	public String getStatusMessage(int code) {
		return statusMessMap.getOrDefault(code, "Lỗi không xác định");
	}

	public NumberFormat getNumberFormat() {
		return nF;
	}

	public Location getTopHoloLoca() {
		return topHoloLoca;
	}

	public String getServerName() {
		return server;
	}

	public static DoiCard getInstance() {
		return _instance;
	}

	public String getID() {
		return id;
	}

	public String getKey() {
		return key;
	}

	public String getSecret() {
		return secret;
	}

	public double getTile() {
		return tile;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (args.length == 1) {
			if (args[0].equalsIgnoreCase("top")) {
				sender.sendMessage("§c§lTop 7 người chơi nạp thẻ nhiều nhất:");
				for (int i = 0; i < topList.size(); i++) {
					PlayerData pD = topList.get(i);
					String mess = "§f" + (i + 1) + ". §a" + pD.getName() + "§f: §e" + nF.format(pD.getAmount());
					sender.sendMessage(mess);
				}
				return true;
			}
		}
		if (sender instanceof Player) {
			if (args.length == 0) {
				((Player) sender).openInventory(new TelcoSelector().getInventory());
				return true;
			} else if (!((Player) sender).hasPermission("napthe.*")) {
				sender.sendMessage("§c/napthe để nạp thẻ!");
				return true;
			}
		}
		if (args.length == 1) {
			if (args[0].equalsIgnoreCase("reload")) {
				reloadConfig();
				topLog.reloadConfig();
				cachePtLog.reloadConfig();
				cardQueueLog.reloadConfig();
				getCardQueue();
				loadConfig();
				getTop();
				displayHoloTop();
				if (checkQueueCardTask != null)
					checkQueueCardTask.cancel();
				synchronized (mysql) {
					if (mysql != null) {
						new MySQL(host, port, user, pass, db);
						mysql = MySQL.getInstance();
					}
					checkQueueCardTask = getServer().getScheduler().runTaskTimerAsynchronously(DoiCard.this,
							checkQueueCardRunnable, 20 * 5, 20 * 5);
					sender.sendMessage("§aLiemSiCard: Đã nạp lại liêm sỉ.");
				}
				return true;
			} else if (args[0].equalsIgnoreCase("settoploca")) {
				if (sender instanceof Player) {
					this.topHoloLoca = ((Player) sender).getLocation().clone();
					getConfig().set("topHoloLoca", topHoloLoca);
					saveConfig();
					displayHoloTop();
					sender.sendMessage("§aĐã chỉnh vị trí hiển thị top nạp thẻ!");
				} else {
					sender.sendMessage("§cLệnh này chỉ dùng trong game!");
				}
				return true;
			}
		} else if (args.length == 3) {
			if (args[0].equalsIgnoreCase("naptc")) {
				try {
					int amount = Integer.parseInt(args[2]);
					int pts = (int) (amount * getTile());
					Player p = Bukkit.getPlayer(args[1]);
					if (p != null) {
						NapRequest.givePointAndLogToTop(p, pts, amount);
						if (mysql != null) {
							mysql.log((int) (System.currentTimeMillis() / 1000), p, "THU_CONG", "-1", "-1",
									amount * 1000, amount, pts, true);
						}
					} else {
						cacheGivePoint(args[1], pts, amount);
						if (mysql != null) {
							mysql.log(args[1], "THU_CONG", "-1", "-1", amount * 1000, amount, pts, true);
						}
						sender.sendMessage("§cNgười chơi không online!");
					}
					sender.sendMessage("§aĐã nạp thủ công thành công!");
				} catch (Exception ex) {
					sender.sendMessage("§cĐã có lỗi! (Số tiền nạp không hợp lệ)");
				}
				return true;
			}
//		} else if (args.length == 4) {
//			if (args[0].equalsIgnoreCase("xong")) {
//				try {
//					int tranid = Integer.parseInt(args[2]);
//					int amount = Integer.parseInt(args[3]);
//					int pts = (int) (amount * getTile());
//					if (mysql != null) {
//						cardQueueList.remove(tranid);
//						saveCardQueueToConfig();
//						mysql.logSuccess(tranid, amount, pts);
//						Player p = Bukkit.getPlayer(args[1]);
//						if (p != null) {
//							NapRequest.givePointAndLogToTop(p, pts, amount);
//						} else {
//							cacheGivePoint(args[1], pts, amount);
//							sender.sendMessage("§cNgười chơi không online!");
//						}
//					} else
//						sender.sendMessage("§cMySQL không hoạt động!");
//					sender.sendMessage("§aĐã nạp thủ công thành công!");
//				} catch (Exception ex) {
//					sender.sendMessage("§cĐã có lỗi! (ID hoặc số tiền không hợp lệ)");
//				}
//				return true;
//			}
		}
		sender.sendMessage("§a/napthe");
		sender.sendMessage("§a/napthe reload");
		sender.sendMessage("§a/napthe top");
		sender.sendMessage("§a/napthe settoploca");
		sender.sendMessage(
				"§a/napthe naptc <tên> <số tiền>(Đơn vị: nghìn VNĐ): Nạp thủ công (Không nạp card trong Server)");
//		sender.sendMessage(
//				"§a/napthe xong <tên> <id> <số tiền>(Đơn vị: nghìn VNĐ): Duyệt thẻ đã gửi thành công nhưng không trả kết quả ngay!");
		return true;
	}

	private void cacheGivePoint(String name, int pts, int money) {
		int lastPts = cachePtLog.getInt(name + ".pts", 0);
		int lastMoney = cachePtLog.getInt(name + ".money", 0);
		lastPts += pts;
		lastMoney += money;
		cachePtLog.set(name + ".pts", lastPts);
		cachePtLog.set(name + ".money", lastMoney);
		cachePtLog.saveConfig();
	}

	public PlayerPoints getPlayerPointsIns() {
		return pp;
	}

	public void logTop(Player p, int money) {
		String name = p.getName();
		int newMoney = topLog.getInt("log." + name, 0) + money;
		topLog.set("log." + name, newMoney);
		int size = topList.size();
		boolean isTopHasChange = false;
		boolean isHasPlayerInTop = setMoneyToPlayerInTop(p, newMoney);
		if (isHasPlayerInTop) {
			isTopHasChange = true;
			SortUtil.quickSort(topList);
		} else {
			PlayerData pD = new PlayerData(name, newMoney);
			if (size < 7) {
				topList.add(pD);
				isTopHasChange = true;
				SortUtil.bubbleSort(topList);
			} else {
				int smallestAmount = topList.get(size - 1).getAmount();
				if (newMoney > smallestAmount) {
					topList.set(size - 1, pD);
					isTopHasChange = true;
					SortUtil.bubbleSort(topList);
				}
			}
		}
		if (isTopHasChange) {
			displayHoloTop();
			topLog.removeKey("top7");
			topList.forEach(d -> {
				topLog.set("top7." + d.getName(), d.getAmount());
			});
		}
		topLog.saveConfig();
	}

	private boolean setMoneyToPlayerInTop(Player p, int newMoney) {
		String pName = p.getName();
		for (PlayerData d : topList) {
			if (pName.equals(d.getName())) {
				d.setAmount(newMoney);
				return true;
			}
		}
		return false;
	}

	public int getMoney(Player p) {
		return topLog.getInt("log." + p.getName(), 0);
	}

	public void addIdToQueue(int tranid) {
		cardQueueList.add(tranid);
		saveCardQueueToConfig();
	}
}
