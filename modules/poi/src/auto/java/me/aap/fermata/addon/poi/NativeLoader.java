package me.aap.fermata.addon.poi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;

/**
 * App native POI database format. The Poi definitions and the locations are described in the
 * following text format:
 *
 * <pre>
 * # Lines starting with # are comments
 *
 * # Optional list of global properties
 * $propName: property value
 * ...
 *
 *
 * # Pois definitions:
 *   %PoiId[: optional list of ancestor ids separated by comma]
 *   [white space] propName: property value
 *   [white space] ...
 *
 * # Poi locations:
 * startLatitude startLongitude endLatitude endLongitude PoiId
 * ...
 * </pre>
 * <p>
 * Each Poi must have at least one property - "msg" - the message to be shown when crossing
 * the poi location. The message can be localized by adding the language code suffix to the
 * property name, for example: "msg_de". The message can also contain placeholders for the
 * properties in the form ${propName}. These placeholders are replaced with the corresponding
 * property values.
 * <p>
 * Below is the list of predefined global properties:
 * <ul>
 *  <li>latitude - Current latitude.</li>
 *  <li>longitude - Current longitude.</li>
 *  <li>azimuth - The direction of movement in degrees, where 0 is north, 90 is east ...</li>
 *  <li>speed - Current speed.</li>
 *  <li>speed_unit - The locale specific speed unit (e.g. km/h or mph).</li>
 * </ul>
 * <p>
 *   Optionally, the Pois may have the following special properties:
 *   <ul>
 *     <li>vmsg - The voice message to be announced when crossing the Poi location. If not
 *     defined, only the visual message ${msg} will be shown.</li>
 *     <li>approach_msg - The message to be shown, when approaching the Poi.</li>
 *     <li>approach_distance - The distance in ${speed_unit} to the Poi location to trigger the
 *     approach message.</li>
 *     <li>approach_vmsg - The voice message to be announced when approaching the Poi.</li>
 *   </ul>
 * </p>
 * <p>
 *   There are also predefined Pois, that can be extended as described above:
 *   <pre>
 *     %SpeedLimit
 *       limit: 30 # the speed limit in ${speed_unit}
 *       msg: Speed limit ${speed_limit} ${speed_unit}
 *       vmsg: Speed limit ${speed_limit} ${speed_unit} exceeded! # Alerted when the limit is exceeded
 *       approach_msg: Approaching area with speed limit ${speed_limit} ${speed_unit}
 *       approach_vmsg: ${approach_msg} # Alerted when approaching limit is exceeded
 *
 *     # Inherits properties from SpeedLimit, but only in the countries,
 *     # where speed cam alerts are not prohibited.
 *     %SpeedCam: SpeedLimit
 *       msg: Camera! Speed limit ${speed_limit} ${speed_unit}
 *       approach_msg: Approaching a speed camera! Speed limit ${speed_limit} ${speed_unit}
 *   </pre>
 * </p>
 * <p>
 * Example:
 * <pre>
 *   # Global properties
 * </pre>
 */
class NativeLoader implements PoiDb.Provider.Loader<InputStream> {
	@Override
	public void load(InputStream is, PoiDb.Builder builder) throws Exception {
//		Map<String, String> globalProps = new HashMap<>();
//		List<PoiLocation> locations = new ArrayList<>();
//		Map<String, Poi> pois = predefined();
//		double latDelta = radiusKm / 111.0;
//		double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)));
//
//
//		try (BufferedReader br = new BufferedReader(is)) {
//			String line;
//			Poi currentPoi = null;
//
//			while ((line = br.readLine()) != null) {
//				int idx = line.lastIndexOf('#');
//				if (idx != -1) { // Remove inline comments
//					line = line.substring(0, idx);
//				}
//
//				line = line.trim();
//				// Skip empty lines and comments
//				if (line.isEmpty() || line.startsWith("#")) {
//					continue;
//				}
//
//				idx = line.indexOf(':');
//
//				// Global property: $propName: value
//				if (line.startsWith("$")) {
//					if (idx != -1) {
//						String propName = line.substring(1, idx).trim();
//						String propValue = line.substring(idx + 1).trim();
//						globalProps.put(propName, propValue);
//					} else {
//						Log.e("Malformed global property: ", line);
//					}
//					continue;
//				}
//
//				// Poi definition: %PoiId[: parent1, parent2, ...]
//				if (line.startsWith("%")) {
//					String id;
//					List<Poi> ancestors = emptyList();
//					if (idx != -1) {
//						id = line.substring(1, idx).trim();
//						String[] aids = line.substring(idx + 1).trim().split(",");
//						if (aids.length != 0) {
//							ancestors = new ArrayList<>(aids.length);
//							for (var aid : aids) {
//								var h = pois.get(aid.trim());
//								if (h == null) {
//									Log.e("Ancestor ", aid, " of ", id, " not found!");
//								} else {
//									ancestors.add(h);
//								}
//							}
//						}
//					} else {
//						id = line.substring(1).trim();
//					}
//					currentPoi = new Poi(ancestors);
//					pois.put(id, currentPoi);
//					continue;
//				}
//
//				// Poi property: propName: value
//				if (idx != -1) {
//					if (currentPoi == null) {
//						Log.e("Property defined outside of poi: ", line);
//						continue;
//					}
//					currentPoi.props.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
//				}
//
//				// Poi location: lat1 lon1 lat2 lon2 PoiId
//				String[] parts = line.split(" ");
//				if (parts.length != 5) {
//					Log.e("Malformed poi location: ", line);
//					continue;
//				}
//
//				try {
//					double startLat = Double.parseDouble(parts[0]);
//					double startLon = Double.parseDouble(parts[1]);
//
//					if (Math.abs(startLat - latitude) > latDelta ||
//							Math.abs(startLon - longitude) > lonDelta) {
//						continue; // Skip locations outside the radius
//					}
//
//					double endLat = Double.parseDouble(parts[2]);
//					double endLon = Double.parseDouble(parts[3]);
//					Poi poi = pois.get(parts[4]);
//					if (poi == null) {
//						Log.e("Poi ", parts[4], " not found for location: ", line);
//					} else {
//						locations.add(new PoiLocation(poi, startLat, startLon, endLat, endLon));
//					}
//				} catch (NumberFormatException err) {
//					Log.e("Malformed poi location: ", line, " - ", err.getMessage());
//				}
//			}
//		}
//
//		return new PoiDb(globalProps, locations, latitude, longitude, radiusKm);
	}

}
