package me.aap.fermata.addon.poi;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;

class LufopProvider extends PoiDb.Provider.HttpProvider {


	public LufopProvider(String key) {
		super(b -> "https://api.lufop.net/api?format=jsonc&nbr=10000&key=" + key +
						"&m=" + b.getRadius() + "&q=" + b.getLatitude() + ',' + b.getLongitude(),
				Loader.asByteBufferLoader());
	}

	private static class Loader implements PoiDb.Provider.Loader<JSONObject> {
		@Override
		public void load(JSONObject json, PoiDb.Builder builder) throws Exception {
			if (!json.has("s") || !json.has("d")) {
				throw new IOException("Missing schema or data in JSON response");
			}

			var schemaArray = json.getJSONArray("s");
			var dataArray = json.getJSONArray("d");

			Map<String, Integer> columnIndex = new HashMap<>();
			for (int i = 0; i < schemaArray.length(); i++) {
				columnIndex.put(schemaArray.getString(i), i);
			}

			Integer latIdx = columnIndex.get("lat");
			Integer lngIdx = columnIndex.get("lng");
			Integer azimutIdx = columnIndex.get("azimut");
			Integer limitIdx = columnIndex.get("vitesse");

			if (latIdx == null || lngIdx == null) {
				throw new IOException("Required columns (lat, lng) not found in schema");
			}

			for (int i = 0; i < dataArray.length(); i++) {
				var row = dataArray.getJSONArray(i);
				Log.d(row);
				double lat = row.getDouble(latIdx);
				double lng = row.getDouble(lngIdx);
				int azimuth = (azimutIdx == null) ? 0 : row.optInt(azimutIdx, 0);
				int limit = (limitIdx == null) ? 50 : row.optInt(limitIdx, 50);
				if (limit <= 0) limit = 50;
				int distance = 500;

				if (azimuth == 0) {
					builder.addSpeedLimit(limit, lat, lng, distance);
				} else {
					float opposite = (azimuth + 180) % 360;
					builder.addSpeedLimit(limit, lat, lng, opposite, distance);
				}
			}
		}

		public static PoiDb.Provider.Loader<String> asStringLoader() {
			return (src, builder) -> {
				JSONObject json = new JSONObject(src);
				new Loader().load(json, builder);
			};
		}

		public static PoiDb.Provider.Loader<ByteBuffer> asByteBufferLoader() {
			return (src, builder) -> asStringLoader().load(TextUtils.toString(src, UTF_8), builder);
		}

		// TODO: Filter out countries where camera POIs are not allowed.
		private static final Set<String> CAMERA_ALLOWED_COUNTRIES =
				Set.of("BE", "CZ", "DK", "FI", "GB", "HR", "HU", "LT", "LU", "NL", "PL", "SE", "RO", "SI",
						"NO", "ES", "IT", "be", "cz", "dk", "fi", "gb", "hr", "hu", "lt", "lu", "nl", "pl",
						"se", "ro", "si", "no", "es", "it");
	}
}
