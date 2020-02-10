package net.minefs.DoiCard;

public class PlayerData {

	private String name;
	private int amount;

	public PlayerData(String name, int amount) {
		this.name = name;
		this.amount = amount;
	}

	public String getName() {
		return name;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int money) {
		this.amount = money;
	}
}
