package net.minefs.DoiCard;

public class RechargeCardStatus {

	private String playerName;
	private int amount;
	private int status;
	
	public RechargeCardStatus(String playerName, int amount, int status) {
		super();
		this.playerName = playerName;
		this.amount = amount;
		this.status = status;
	}
	
	public String getPlayerName() {
		return playerName;
	}
	
	public int getAmount() {
		return amount;
	}
	
	public int getStatus() {
		return status;
	}
	
}
