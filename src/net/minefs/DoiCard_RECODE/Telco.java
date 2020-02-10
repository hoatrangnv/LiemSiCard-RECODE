package net.minefs.DoiCard;

public enum Telco {

	MOBIFONE("Mobifone", "VMS"), VIETTEL("Viettel", "VTT"), VINAPHONE("Vinaphone", "VNP"), GATE("Gate", "FPT");

	private String name, id;

	Telco(String name, String id) {
		this.name = name;
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public String getID() {
		return id;
	}
}
