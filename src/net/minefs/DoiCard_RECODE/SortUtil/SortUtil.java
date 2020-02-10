package net.minefs.DoiCard.SortUtil;

import java.util.List;

import net.minefs.DoiCard.PlayerData;

public class SortUtil {

	public static void quickSort(List<PlayerData> dataList) {
		quickSort(dataList, 0, dataList.size() - 1);
	}

	public static void quickSort(List<PlayerData> dataList, int bI, int eI) {
		if (eI <= bI)
			return;
		int p = bI; // pivot index
		int i = bI + 1; // i index
		int j = eI; // j index
		int amountP = dataList.get(p).getAmount();
		while (i <= j) {
			int amountI = dataList.get(i).getAmount();
			if (amountI < amountP) {
				while (i <= j) {
					int amountJ = dataList.get(j).getAmount();
					if (amountJ > amountP) {
						swap(dataList, i, j);
						j--;
						break;
					}
					j--;
				}
			}
			i++;
		}
		swap(dataList, j, p);
		quickSort(dataList, bI, j - 1);
		quickSort(dataList, j + 1, eI);
	}

	public static void swap(List<PlayerData> dataList, int a, int b) {
		PlayerData temp = dataList.get(a);
		dataList.set(a, dataList.get(b));
		dataList.set(b, temp);
	}

	public static void bubbleSort(List<PlayerData> list) {
		int j = list.size() - 1;
		for (int i = j - 1; i >= 0; i--) {
			if (list.get(j).getAmount() > list.get(i).getAmount()) {
				swap(list, j, i);
				j = i;
			} else
				break;
		}
	}
}
