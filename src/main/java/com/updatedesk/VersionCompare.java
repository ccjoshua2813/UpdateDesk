package com.updatedesk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Comparaciones de versiones de plugins que no dependen de Bukkit. */
final class VersionCompare {
	private static final Pattern BUILD = Pattern.compile("(?:\\bb|-b|\\(b)(\\d{1,7})(?!\\d)");

	private VersionCompare() {}

	/**
	 * ¿La versión instalada es una compilación de desarrollo (dev/SNAPSHOT) que ya iguala o supera a la publicada?
	 * Si ambas indican número de compilación (b141), se comparan esos números; si no, se compara el número de versión.
	 */
	static boolean devAtLeast(String installed, String latest) {
		String low = installed.toLowerCase();
		if (!(low.contains("dev") || low.contains("snapshot"))) return false;
		Integer bi = build(installed), bl = build(latest);
		if (bi != null && bl != null) return bi >= bl;
		if (bl != null) return false;                // la publicada tiene compilación y la instalada no: no se puede asegurar
		int[] a = numbers(installed), b = numbers(latest);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
			if (x != y) return x > y;
		}
		return true;
	}

	private static Integer build(String v) {
		Matcher m = BUILD.matcher(v);
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}

	private static int[] numbers(String v) {
		Matcher m = Pattern.compile("\\d+").matcher(v.split("[-+ (]")[0]);
		List<Integer> l = new ArrayList<>();
		while (m.find() && l.size() < 4) l.add(Integer.parseInt(m.group()));
		return l.stream().mapToInt(Integer::intValue).toArray();
	}

	/**
	 * ¿"candidate" es más nueva que "installed"? Compara el número de versión (2.22.1) y, si coincide, la numeración de desarrollo
	 * (dev+27): una versión estable es posterior a sus compilaciones de desarrollo.
	 */
	static boolean newer(String candidate, String installed) {
		int[] a = numbers(candidate), b = numbers(installed);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
			if (x != y) return x > y;
		}
		boolean devA = candidate.toLowerCase().contains("dev"), devB = installed.toLowerCase().contains("dev");
		if (devA != devB) return !devA;                  // la estable gana a la de desarrollo
		if (!devA) return false;                         // ambas estables e iguales
		return counter(candidate) > counter(installed);
	}

	private static int counter(String v) {
		Matcher m = Pattern.compile("dev\\+(\\d+)").matcher(v);
		return m.find() ? Integer.parseInt(m.group(1)) : 0;
	}
}
