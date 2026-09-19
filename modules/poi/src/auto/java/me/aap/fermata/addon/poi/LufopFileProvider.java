package me.aap.fermata.addon.poi;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.location.Location;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

class LufopFileProvider implements PoiDb.Provider {
	private static final int POI_DETECT_RADIUS = 500;
	private final File dir;
	private final Map<String, Poi.Type> typeCache = new HashMap<>();

	LufopFileProvider(File dir) {
		this.dir = dir;
	}

	@Override
	public FutureSupplier<PoiDb> load(PoiDb.Builder builder) {
		return App.get().execute(() -> {
			loadDir(builder);
			return builder.build();
		});
	}

	private void loadDir(PoiDb.Builder builder) throws IOException {
		File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".asc"));
		if (files == null) throw new IOException("Failed to list POI files in " + dir);

		Location center = new Location("");
		center.setLatitude(builder.getLatitude());
		center.setLongitude(builder.getLongitude());
		int radiusMeters = builder.getRadius() * 1000;
		Location poi = new Location("");
		int total = 0;

		for (File f : files) total += loadFile(f, builder, center, poi, radiusMeters);
		Log.i("Loaded ", total, " camera(s) from ", files.length, " Lufop file(s) in ", dir);
	}

	private int loadFile(File f, PoiDb.Builder builder, Location center, Location poi,
											 int radiusMeters) {
		int count = 0;

		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(new FileInputStream(f), UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty()) continue;
				if (parseLine(line, builder, center, poi, radiusMeters)) count++;
			}
		} catch (IOException ex) {
			Log.e(ex, "Failed to read POI file: ", f);
		}

		return count;
	}

	private boolean parseLine(String line, PoiDb.Builder builder, Location center, Location poi,
														 int radiusMeters) {
		int c1 = line.indexOf(',');
		if (c1 < 0) return false;
		int c2 = line.indexOf(',', c1 + 1);
		if (c2 < 0) return false;

		try {
			double lng = Double.parseDouble(line.substring(0, c1).trim());
			double lat = Double.parseDouble(line.substring(c1 + 1, c2).trim());
			String desc = unquote(line.substring(c2 + 1).trim());

			poi.setLatitude(lat);
			poi.setLongitude(lng);
			if (center.distanceTo(poi) > radiusMeters) return false;

			int limit = extractSpeedLimit(desc);
			if (limit > 0) {
				builder.addSpeedLimit(limit, lat, lng, POI_DETECT_RADIUS);
			} else {
//				builder.addPoi(getOrCreateType(builder, desc), lat, lng, POI_DETECT_RADIUS);
			}
			return true;
		} catch (NumberFormatException ex) {
			Log.d("Failed to parse POI line: ", line);
			return false;
		}
	}

	private Poi.Type getOrCreateType(PoiDb.Builder builder, String desc) {
		return typeCache.computeIfAbsent(desc, d -> {
			var type = builder.createType(d);
			type.setProp(Poi.Type.PROP_MSG, d);
			return type;
		});
	}

	private static String unquote(String s) {
		if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	private static int extractSpeedLimit(String desc) {
		int i = desc.length();
		while (i > 0 && Character.isDigit(desc.charAt(i - 1))) i--;
		if (i == desc.length()) return -1;
		try {
			return Integer.parseInt(desc.substring(i));
		} catch (NumberFormatException ex) {
			return -1;
		}
	}
}

