package me.aap.fermata.addon.poi;

import static java.util.Collections.binarySearch;
import static java.util.Comparator.comparingDouble;
import static me.aap.fermata.addon.poi.Poi.SpeedLimit.DEFAULT_SPEED_LIMIT;
import static me.aap.fermata.addon.poi.Poi.SpeedLimit.PROP_SPEED_LIMIT;

import android.location.Location;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;

public class PoiDb extends Poi.Type {
	private final ArrayList<Poi> list = new ArrayList<>();
	private final Location location = new Location("");
	private int cursor;

	private PoiDb(Voyageur v) {
		super(new HashMap<>(), v);
		location.setLatitude(v.getLatitude());
		location.setLongitude(v.getLongitude());
		var res = App.get().getResources();
		setProp(PROP_LOCALE, res.getString(R.string.locale));
		setProp(PROP_SPEED_UNIT, res.getString(R.string.speed_unit));
		setProp(PROP_DISTANCE_UNIT, res.getString(R.string.distance_unit));
	}

	public double getLatitude() {
		return location.getLatitude();
	}

	public double getLongitude() {
		return location.getLongitude();
	}

	Poi findNearestTo(Location location, int maxDistance) {
		double latitude = location.getLatitude();
		double longitude = location.getLongitude();
		double latDelta = maxDistance / 111320.;
		double lonDelta = latDelta / Math.cos(Math.toRadians(latitude));
		double minLat = latitude - latDelta;
		double maxLat = latitude + latDelta;
		double minLon = longitude - lonDelta;
		double maxLon = longitude + lonDelta;
		int minDistance = maxDistance;
		Poi nearest = null;

		for (int r = 0; r < 2; r++) {
			int steps = 0;

			for (int i = cursor, n = list.size(); i < n; i++) {
				var poi = list.get(i);
				if (poi.getEndLatitude() > maxLat) break;
				steps++;
				if (poi.getEndLongitude() >= minLon && poi.getEndLongitude() <= maxLon) {
					int dist = poi.distanceFrom(location);
					if (dist < minDistance) {
						minDistance = dist;
						nearest = poi;
						cursor = i;
					}
				}
			}
			for (int i = cursor - 1; i >= 0; i--) {
				var poi = list.get(i);
				if (poi.getEndLatitude() < minLat) break;
				steps++;
				if (poi.getEndLongitude() >= minLon && poi.getEndLongitude() <= maxLon) {
					int dist = poi.distanceFrom(location);
					if (dist < minDistance) {
						minDistance = dist;
						nearest = poi;
						cursor = i;
					}
				}
			}

			if (steps != 0) break;
			updateCursor(location);
		}

		return nearest;
	}

	private void updateCursor(Location location) {
		cursor = binarySearch(list, new Poi(null, location.getLatitude(), location.getLongitude(), 0),
				comparingDouble(Poi::getEndLatitude));
		if (cursor < 0) cursor = -(cursor + 1);
	}

	public int distanceTo(Location location) {
		return (int) location.distanceTo(this.location);
	}

	public static class Builder {
		private final PoiDb db;
		private final Poi.SpeedLimit speedLimit;
		private final Map<Object, Poi.Type> types = new HashMap<>();
		private final int radius;

		public Builder(Voyageur v, int radius) {
			db = new PoiDb(v);
			this.radius = radius;
			speedLimit = new Poi.SpeedLimit(db);
			types.put("SpeedLimit", speedLimit);
		}

		public double getLatitude() {
			return db.location.getLatitude();
		}

		public double getLongitude() {
			return db.location.getLongitude();
		}

		public int getRadius() {
			return radius;
		}

		public Poi.Type createType(Object name, String... ancestorNames) {
			Poi.Type[] ancestors = new Poi.Type[ancestorNames.length];
			for (int i = 0; i < ancestorNames.length; i++) {
				ancestors[i] = types.get(ancestorNames[i]);
				if (ancestors[i] == null) ancestors[i] = new Poi.Type(db);
			}
			Poi.Type type = new Poi.Type(db);
			types.put(name, type);
			return type;
		}

		public Poi.Type getOrCreateSpeedLimitType(int limit) {
			if (limit == 0 || limit == DEFAULT_SPEED_LIMIT) return speedLimit;
			var type = types.get(limit);
			return type != null ? type : createSpeedLimitType(limit);
		}

		public Poi.Type createSpeedLimitType(int limit) {
			if (limit == 0 || limit == DEFAULT_SPEED_LIMIT) return speedLimit;
			Poi.Type type =
					new Poi.Type(Collections.singletonMap(PROP_SPEED_LIMIT, limit), db, speedLimit);
			types.put(limit, type);
			return type;
		}

		public void addPoi(Poi.Type type, double latitude, double longitude, int radius) {
			db.list.add(new Poi(type, latitude, longitude, radius));
		}

		public void addPoi(Poi.Type type, double latitude, double longitude, float bearing,
											 int length) {
			db.list.add(new Poi(type, latitude, longitude, bearing, length));
		}

		public void addPoi(Poi.Type type, double startLatitude, double startLongitude,
											 double endLatitude, double endLongitude) {
			db.list.add(new Poi(type, startLatitude, startLongitude, endLatitude, endLongitude));
		}


		public void addSpeedLimit(int limit, double latitude, double longitude, int radius) {
			addPoi(getOrCreateSpeedLimitType(limit), latitude, longitude, radius);
		}

		public void addSpeedLimit(int limit, double latitude, double longitude, float bearing,
															int length) {
			addPoi(getOrCreateSpeedLimitType(limit), latitude, longitude, bearing, length);
		}

		public void addSpeedLimit(int limit, double startLatitude, double startLongitude,
															double endLatitude, double endLongitude) {
			addPoi(getOrCreateSpeedLimitType(limit), startLatitude, startLongitude, endLatitude,
					endLongitude);
		}

		public PoiDb build() {
			db.list.trimToSize();
			db.list.sort(comparingDouble(Poi::getEndLatitude));
			db.updateCursor(db.location);
			Log.i("Loaded POI database with ", db.list.size(), " entries at location (", getLatitude(),
					", ", getLongitude(), ") in the radius of ", radius, " km");
			return db;
		}
	}

	public interface Provider {
		FutureSupplier<PoiDb> load(Builder builder);

		default FutureSupplier<PoiDb> loadFor(Voyageur v, int radius) {
			return load(new Builder(v, radius));
		}

		interface Loader<T> {
			void load(T src, Builder builder) throws Exception;
		}

		static Provider create(String url) {
			if (url.startsWith("lufop:")) {
				var key = url.substring(6).trim();
				return key.isEmpty() ? null : new LufopProvider(key);
			} else if (url.startsWith("http://") || url.startsWith("https://")) {
				//TODO: implement
			} else if (url.startsWith("file:/") || url.startsWith("/")) {
				//TODO: implement
			} else if (url.startsWith("content:/")) {
				//TODO: implement
			}
			Log.e("Unsupported POI provider URL: ", url);
			return null;
		}

		class HttpProvider implements Provider {
			private final Function<Builder, String> urlFunc;
			private final Loader<ByteBuffer> loader;

			public HttpProvider(Function<Builder, String> urlFunc, Loader<ByteBuffer> loader) {
				this.urlFunc = urlFunc;
				this.loader = loader;
			}


			public FutureSupplier<PoiDb> load(Builder builder) {
				Log.d(urlFunc.apply(builder));
				Promise<PoiDb> promise = new Promise<>();
				HttpConnection.connect(o -> o.url(urlFunc.apply(builder)), (resp, err) -> {
					if (err != null) {
						promise.completeExceptionally(err);
						return promise;
					}
					return resp.getPayload((p, perr) -> {
						if (perr != null) {
							promise.completeExceptionally(perr);
						} else {
							try {
								loader.load(p, builder);
								promise.complete(builder.build());
							} catch (Exception ex) {
								promise.completeExceptionally(ex);
							}
						}
						return promise;
					});
				});

				return promise;
			}
		}
	}
}