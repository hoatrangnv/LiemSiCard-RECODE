package net.minefs.DoiCard;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minefs.DoiCard.Event.PlayerGetDoneRechargeCardEvent;

public class NapRequest {

	private static Map<String, NapRequest> _requests;

	public static Map<String, NapRequest> getRequests() {
		if (_requests == null)
			_requests = new ConcurrentHashMap<String, NapRequest>();
		return _requests;
	}

	private Player p;
	private String seri = null, code = null;
	private int amount;
	private Telco telco;

	private static DoiCard dc = DoiCard.getInstance();

	public NapRequest(Player p, Telco telco, int amount) {
		this.p = p;
		this.amount = amount;
		this.telco = telco;
		getRequests().put(p.getName(), this);
		p.sendMessage("§b§lLoại thẻ: §a§l" + telco.getName() + "§b§l, mệnh giá: §a§l" + amount);
		p.sendMessage("§b§lBây giờ hãy nhập số seri vào khung chat");
	}

	public void setSeri(String seri) {
		this.seri = seri;
	}

	public String getSeri() {
		return seri;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public void onChat(String msg) {
		if (seri == null) {
			setSeri(msg);
			p.sendMessage("§b§lSố seri: §a§l" + seri);
			p.sendMessage("§b§lHãy tiếp tục nhập mã thẻ vào khung chat");
			return;
		}
		setCode(msg);
		p.sendMessage("§b§lMã thẻ: §a§l" + code);
		p.sendMessage("§b§lĐang tiến hành nạp thẻ, xin chờ...");
		getRequests().remove(p.getName());
		Map<String, String> mapparams = new HashMap<String, String>();
//        'partner_key' => $partner_key,
//        'partner_id' => $partner_id,
//        'serial' => $serial,
//        'code' => $code,
//        'telco' => $telco,
//        'amount' => $amount,
//        'tranid' => $tranid,
//        'sign' => $sign
		dc.getLogger().info(p.getName() + " tien hanh nap the " + telco.getName() + ", menh gia: " + amount + ", seri: "
				+ seri + ", ma the: " + code);
		MySQL mysql = MySQL.getInstance();
		int time = (int) (System.currentTimeMillis() / 1000);
		int tranid = mysql != null ? mysql.log(time, p, telco.getID(), seri, code, amount, 0, 0) : time;
		String encrypt = dc.getID() + tranid + seri + code + dc.getSecret();
		// $sign = md5($partner_id . $tranid . $serial . $code . $secret);
		String md2;
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(encrypt.getBytes());
			byte[] byteData = md.digest();
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < byteData.length; ++i) {
				sb.append(Integer.toString((byteData[i] & 0xFF) + 256, 16).substring(1));
			}
			md2 = sb.toString();
		} catch (Exception ex) {
			p.sendMessage("§c§lMã hóa dữ liệu thấy bại, vui lòng liên hệ Admin!");
			return;
		}
		String callBackURL = dc.getCallbackURL() + "?data=" + tranid + "." + dc.getSecret();
		mapparams.put("partner_key", dc.getKey());
		mapparams.put("partner_id", dc.getID());
		mapparams.put("serial", seri);
		mapparams.put("code", code);
		mapparams.put("telco", telco.getID());
		mapparams.put("amount", amount + "");
		mapparams.put("tranid", tranid + "");
		mapparams.put("sign", md2);
		mapparams.put("callback", callBackURL);
		String url = "https://santhe247.vn/api/charge_card.html";
		String params = dc.createRequestUrl(mapparams);
		String get = dc.get(url, params);
		if (get == null || get.startsWith("error")) {
			p.sendMessage("§c§lCó lỗi xảy ra, vui lòng thử lại sau.");
			return;
		}
		dc.getLogger().info(get);
		JsonObject root = new JsonParser().parse(get).getAsJsonObject();
		String success = root.get("success").getAsString();// Trả về true hoặc false
		if (success.equalsIgnoreCase("true")) {
			if (root.has("data")) {
				JsonObject data = root.get("data").getAsJsonObject();
				int amount = data.get("amount").getAsInt();
				int pChuaKm = (int) amount / 1000;
				int pts = pChuaKm;
				pts *= dc.getTile();
				NapRequest.givePointAndLogToTop(p, pts, pChuaKm);
				if (mysql != null)
					mysql.logSuccess(tranid, pChuaKm, pts);
			} else {
				dc.addIdToQueue(tranid);
				p.sendMessage("§7§l§m=========================================================");
				p.sendMessage("§7§l╢ -§a§l Thẻ đã nạp thành công và đang chờ duyệt, hãy giữ lại thẻ");
				p.sendMessage("§7§l╢ §a§lvà chờ kết quả.");
				p.sendMessage(
						"§7§l╢ -§a§l Nếu sau 1 phút nữa, bạn không nhận được " + dc.getPointName() + " và thông báo");
				p.sendMessage("§7§l╢ §a§lkết quả nạp thẻ, hãy liên hệ Admin để xử lý!");
				p.sendMessage("§7§l╢ -§a§l Ở ngoài lobby, nhấn Tab để xem thông tin liên hệ Admin!");
				p.sendMessage("§7§l§m=========================================================");
				p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
			}
		} else {
			String error = root.get("error").getAsString();// Nội dung lỗi (Trả về khi kết quả là false)
			int errCode = root.get("code").getAsInt();// Mã lỗi (Trả về khi kết quả là false)
			p.sendMessage("§7§l§m=========================================================");
			p.sendMessage("§c§lCó lỗi xảy ra: " + error + " (mã lỗi " + errCode + ")");
			p.sendMessage("§7§l§m=========================================================");
			p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1f, 1f);
			if (mysql != null)
				mysql.logErrorCode(tranid, errCode);
		}
	}

	public static void givePointAndLogToTop(Player p, int pts, int money) {
		Bukkit.getScheduler().runTask(dc, new Runnable() {

			@Override
			public void run() {
				dc.getPlayerPointsIns().getAPI().give(p.getUniqueId(), pts);
				p.sendMessage("§7§l§m=========================================================");
				p.sendMessage("§a§lNạp thẻ thành công, bạn nhận được " + pts + " " + dc.getPointName());
				p.sendMessage("§7§l§m=========================================================");
				p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
				Bukkit.broadcastMessage("§f§l[§c§lNạp Thẻ§f§l]§r§b Cảm ơn người chơi §e" + p.getName()
						+ "§b đã nạp cho Server " + dc.getNumberFormat().format(money * 1000) + "VNĐ.");
				dc.logTop(p, money);
				dc.getServer().getPluginManager().callEvent(new PlayerGetDoneRechargeCardEvent(money, pts, p));
			}
		});
	}
}
