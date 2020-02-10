package net.minefs.DoiCard.Event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerGetDoneRechargeCardEvent extends Event {

	private int moneyAmount;
	private int receivePoint;
	private Player player;

	public int getMoneyAmount() {
		return moneyAmount;
	}

	public int getReceivePoint() {
		return receivePoint;
	}

	public Player getPlayer() {
		return player;
	}

	public PlayerGetDoneRechargeCardEvent(int moneyAmount, int receivePoint, Player player) {
		this.moneyAmount = moneyAmount;
		this.receivePoint = receivePoint;
		this.player = player;
	}

	private static final HandlerList HANDLERS = new HandlerList();

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
