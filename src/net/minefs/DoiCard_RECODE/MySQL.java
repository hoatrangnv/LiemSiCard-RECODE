package net.minefs.DoiCard;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

public class MySQL {
	private Connection sql;
	private String host, db, user, password;
	private int port;
	private boolean enabled;
	private BukkitRunnable task;

	private static MySQL _instance;

	private final String GET_NAPTHE_STATUS = "select name,menhgia,status from napthe_log where id=?";
	private final String NAPTHE_LOG = "insert into napthe_log(name, uuid, seri, pin, loai, time, menhgia, status, server, pointsnhan, thucnhan)"
			+ " values(?,?,?,?,?,?,?,?,?,?,?)";
	private final String NAPTHE_LOG_SUCCESS = "update napthe_log set status=1, pointsnhan=?, thucnhan=? where id=?";
	private final String NAPTHE_LOG_ERROR_CODE = "update napthe_log set status=? where id=?";
	private final String POINTS_CONSUME_LOG = "insert into points_consume_log(player, current, amount, time, server) values(?,?,?,?,?)";
	private final String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS `napthe_log` (  `id` mediumint(11) NOT NULL AUTO_INCREMENT,  `name` varchar(255) COLLATE utf8_bin NOT NULL DEFAULT '',  `uuid` varchar(100) COLLATE utf8_bin NOT NULL DEFAULT '',  `seri` varchar(255) COLLATE utf8_bin NOT NULL DEFAULT '',  `pin` varchar(255) COLLATE utf8_bin NOT NULL DEFAULT '',  `loai` varchar(255) COLLATE utf8_bin NOT NULL,  `time` int(11) NOT NULL DEFAULT 0,  `menhgia` varchar(10) COLLATE utf8_bin NOT NULL DEFAULT '',  `status` int(2) NOT NULL DEFAULT 0,  `server` varchar(15) COLLATE utf8_bin NOT NULL DEFAULT 'web',  `pointsnhan` int(11) NOT NULL DEFAULT 0,  `thucnhan` int(11) NOT NULL DEFAULT 0,  PRIMARY KEY (`id`)) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_bin";

	public MySQL(String host, int port, String user, String password, String db) {
		this.host = host;
		this.user = user;
		this.password = password;
		this.port = port;
		this.db = db;
		if (_instance != null) {
			_instance.task.cancel();
			_instance.close();
			_instance = null;
		}
		if (open(host, port, db, user, password) && createTable()) {
			task = new BukkitRunnable() {
				@Override
				public void run() {
					keepAlive();
				}
			};
			task.runTaskTimerAsynchronously(DoiCard.getInstance(), 3600, 3600);
			_instance = this;
		}
	}

	private synchronized boolean open(String host, int port, String db, String user, String pwd) {
		DoiCard.getInstance().getLogger().info("Mo ket noi MySQL...");
		enabled = false;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			sql = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db, user, pwd);
			enabled = true;
			if (task != null)
				task.cancel();
			task = new BukkitRunnable() {
				@Override
				public void run() {
					keepAlive();
				}
			};
		} catch (Exception e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE, "Khong the mo ket noi MySQL", e);
			return false;
		}
		return true;
	}

	public synchronized void close() {
		DoiCard.getInstance().getLogger().info("Dong ket noi MySQL...");
		try {
			if (task != null)
				task.cancel();
			task = null;
			sql.close();
			_instance = null;
		} catch (SQLException e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE, "Khong the dong ket noi MySQL", e);
		}
	}

	public void restart() {
		close();
		open(host, port, db, user, password);
	}

	protected void cleanup(ResultSet result, Statement s) {
		if (result != null) {
			try {
				result.close();
			} catch (SQLException e) {
				DoiCard.getInstance().getLogger().log(Level.SEVERE, "SQLException on cleanup", e);
			}
		}
		if (s != null) {
			try {
				s.close();
			} catch (SQLException e) {
				DoiCard.getInstance().getLogger().log(Level.SEVERE, "SQLException on cleanup", e);
			}
		}
	}

	public static MySQL getInstance() {
		return _instance;
	}

	public synchronized int log(int time, OfflinePlayer player, String cardtype, String seri, String code, int menhgia,
			int pointnhan, int thucnhan) {
		return log(time, player, cardtype, seri, code, menhgia, pointnhan, thucnhan, false);
	}

	public synchronized int log(int time, OfflinePlayer player, String cardtype, String seri, String code, int menhgia,
			int pointnhan, int thucnhan, boolean isSuccess) {
		if (!enabled)
			return -1;
		ResultSet r = null;
		PreparedStatement ps = null;
		try {
			if (sql.isClosed())
				restart();
			ps = sql.prepareStatement(NAPTHE_LOG, Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, player.getName());
			ps.setString(2, player.getUniqueId().toString());
			ps.setString(3, seri);
			ps.setString(4, code);
			ps.setString(5, cardtype);
			ps.setInt(6, time);
			ps.setInt(7, menhgia);
			ps.setBoolean(8, isSuccess);
			ps.setString(9, DoiCard.getInstance().getServerName());
			ps.setInt(10, pointnhan);
			ps.setInt(11, thucnhan);
			ps.executeUpdate();
			r = ps.getGeneratedKeys();
			while (r.next())
				return r.getInt(1);
		} catch (SQLException exc) {
			DoiCard.getInstance().getLogger().warning("Loi xay ra khi log");
//			restart();
//			Bukkit.getScheduler().runTaskLaterAsynchronously(DoiCard.getInstance(),
//					() -> log(player, cardtype, seri, code, menhgia, server, success, pointnhan, thucnhan), 20);
		} finally {
			cleanup(r, ps);
		}
		return -1;
	}

	public synchronized int log(String playerName, String cardtype, String seri, String code, int menhgia,
			int pointnhan, int thucnhan, boolean isSuccess) {
		if (!enabled)
			return -1;
		ResultSet r = null;
		PreparedStatement ps = null;
		try {
			if (sql.isClosed())
				restart();
			ps = sql.prepareStatement(NAPTHE_LOG, Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, playerName);
			ps.setString(2, "-1");
			ps.setString(3, seri);
			ps.setString(4, code);
			ps.setString(5, cardtype);
			ps.setInt(6, (int) (System.currentTimeMillis() / 1000));
			ps.setInt(7, menhgia);
			ps.setBoolean(8, isSuccess);
			ps.setString(9, DoiCard.getInstance().getServerName());
			ps.setInt(10, pointnhan);
			ps.setInt(11, thucnhan);
			ps.executeUpdate();
			r = ps.getGeneratedKeys();
			while (r.next())
				return r.getInt(1);
		} catch (SQLException exc) {
			DoiCard.getInstance().getLogger().warning("Loi xay ra khi log");
//			restart();
//			Bukkit.getScheduler().runTaskLaterAsynchronously(DoiCard.getInstance(),
//					() -> log(player, cardtype, seri, code, menhgia, server, success, pointnhan, thucnhan), 20);
		} finally {
			cleanup(r, ps);
		}
		return -1;
	}

	public synchronized void log(String name, int current, int consumed) {
		if (!enabled) {
			DoiCard.getInstance().getLogger()
					.warning(name + " consumed " + consumed + " points but MySQL logging is disabled");
			return;
		}
		PreparedStatement ps = null;
		try {
			ps = sql.prepareStatement(POINTS_CONSUME_LOG);
			ps.setString(1, name);
			ps.setInt(2, current);
			ps.setInt(3, consumed);
			ps.setLong(4, System.currentTimeMillis());
			ps.setString(5, DoiCard.getInstance().getServerName());
			ps.executeUpdate();
		} catch (Exception e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE,
					name + " consumed " + consumed + " points but MySQL not working", e);
		} finally {
			cleanup(null, ps);
		}
	}

	public synchronized void keepAlive() {
		if (!enabled)
			return;
		Statement s = null;
		ResultSet r = null;
		try {
			s = sql.createStatement();
			r = s.executeQuery("select time from napthe_log where 1=0");
		} catch (Exception e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE, "Khong the chay lenh keepalive", e);
		} finally {
			cleanup(r, s);
		}
	}

	public synchronized void logSuccess(int id, int pointsnhan, int thucnhan) {
		PreparedStatement s = null;
		try {
			s = sql.prepareStatement(NAPTHE_LOG_SUCCESS);
			s.setInt(1, pointsnhan);
			s.setInt(2, thucnhan);
			s.setInt(3, id);
			s.executeUpdate();
		} catch (Exception e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE, "Khong the cap nhat trang thai success cho log #" + id,
					e);
		} finally {
			cleanup(null, s);
		}
	}

	public synchronized boolean createTable() {
		PreparedStatement s = null;
		try {
			s = sql.prepareStatement(TABLE_CREATE);
			s.executeUpdate();
		} catch (Exception e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE, "Khong the tao bang", e);
			return false;
		} finally {
			cleanup(null, s);
		}
		return true;
	}

	public synchronized RechargeCardStatus getLogStatus(int tranid) {
		PreparedStatement s = null;
		ResultSet r = null;
		try {
			s = sql.prepareStatement(GET_NAPTHE_STATUS);
			s.setInt(1, tranid);
			r = s.executeQuery();
			while (r.next()) {
				int amount = (int) (r.getInt("menhgia") / 1000);
				RechargeCardStatus statusObj = new RechargeCardStatus(r.getString("name"), amount, r.getInt("status"));
				return statusObj;
			}
		} catch (SQLException e) {
			DoiCard.getInstance().getLogger().warning("Xay ra loi khi lay du lieu tu CSDL.");
			DoiCard.getInstance().getLogger().warning(e.getMessage() + " - " + e.getSQLState());
		} finally {
			cleanup(r, s);
		}
		return null;
	}

	public synchronized void logErrorCode(int tranid, int errCode) {
		PreparedStatement s = null;
		try {
			s = sql.prepareStatement(NAPTHE_LOG_ERROR_CODE);
			s.setInt(1, errCode);
			s.setInt(2, tranid);
			s.executeUpdate();
		} catch (Exception e) {
			DoiCard.getInstance().getLogger().log(Level.SEVERE,
					"Khong the cap nhat trang thai success cho log #" + tranid, e);
		} finally {
			cleanup(null, s);
		}
	}
}
