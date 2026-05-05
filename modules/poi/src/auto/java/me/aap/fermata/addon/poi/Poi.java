package me.aap.fermata.addon.poi;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;
import static me.aap.utils.misc.Assert.assertMainThread;

import android.location.Location;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import me.aap.utils.app.App;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;

public class Poi {
	private static final float MAX_BEARING_DIFF = 30f;
	private static final Location poiStart = new Location("");
	private static final Location poiEnd = new Location("");
	private final Type type;
	private final double endLatitude;
	private final double endLongitude;
	private double startLatitude;
	private double startLongitude;
	private float bearing = Float.NaN;
	private int length = -1;

	public Poi(Type type, double latitude, double longitude, int radius) {
		this(type, latitude, longitude, Float.NaN, radius);
	}

	public Poi(Type type, double latitude, double longitude, float bearing, int length) {
		this(type, Double.NaN, Double.NaN, latitude, longitude);
		this.bearing = bearing;
		this.length = length;
	}

	public Poi(Type type, double startLatitude, double startLongitude, double endLatitude,
						 double endLongitude) {
		this.type = type;
		this.startLatitude = startLatitude;
		this.startLongitude = startLongitude;
		this.endLatitude = endLatitude;
		this.endLongitude = endLongitude;
	}

	public Type getType() {
		return type;
	}

	public double getStartLatitude() {
		return startLatitude;
	}

	public double getStartLongitude() {
		return startLongitude;
	}

	public double getEndLatitude() {
		return endLatitude;
	}

	public double getEndLongitude() {
		return endLongitude;
	}

	public int getLength() {
		assertMainThread();
		if (length != -1) return length;
		poiStart.setLatitude(getStartLatitude());
		poiStart.setLongitude(getStartLongitude());
		poiEnd.setLatitude(getEndLatitude());
		poiEnd.setLongitude(getEndLongitude());
		length = (int) poiStart.distanceTo(poiEnd);
		return length;
	}

	public int distanceFrom(Location location) {
		assertMainThread();
		if (!location.hasBearing()) return Integer.MAX_VALUE;

		poiEnd.setLatitude(endLatitude);
		poiEnd.setLongitude(endLongitude);

		if (!Float.isNaN(bearing)) {
			if (!sameDirection(bearing, location.getBearing())) return Integer.MAX_VALUE;
		} else if (!Double.isNaN(startLatitude)) {
			poiStart.setLatitude(startLatitude);
			poiStart.setLongitude(startLongitude);
			bearing = bearingTo(poiStart, poiEnd);
			if (length == -1) length = (int) poiStart.distanceTo(poiEnd);
			if (!sameDirection(bearing, location.getBearing())) return Integer.MAX_VALUE;
		}

		if (!sameDirection(bearingTo(location, poiEnd), location.getBearing()))
			return Integer.MAX_VALUE;
		return (int) location.distanceTo(poiEnd);
	}

	@NonNull
	@Override
	public String toString() {
		return type.getStrProp(Type.PROP_MSG, "POI") + " at " + endLatitude + "," + endLongitude +
				(length != -1 ? " length: " + length : "") +
				(!Float.isNaN(bearing) ? " bearing: " + bearing : "");
	}

	private static float bearingTo(Location from, Location to) {
		float bearing = from.bearingTo(to);
		return (bearing < 0f) ? bearing + 360f : bearing;
	}

	private boolean canMeet(float bearing1, float bearing2) {
		float diff = Math.abs(bearing1 - bearing2);
		return (diff >= 180f - MAX_BEARING_DIFF) && (diff <= 180f + MAX_BEARING_DIFF);
	}

	private boolean sameDirection(float bearing1, float bearing2) {
		float diff = Math.abs(bearing1 - bearing2);
		return (diff <= MAX_BEARING_DIFF) || (diff >= 360f - MAX_BEARING_DIFF);
	}

	private static double[] calculateCoordinatesAhead(double lat, double lon, double bearing,
																										double distance) {
		bearing = toRadians(bearing);
		distance /= 111320.0;
		lon += distance * sin(bearing) / cos(toRadians(lat));
		lat += distance * cos(bearing);
		return new double[]{lat, lon};
	}

	public static class Type extends Props {
		public static final String PROP_MSG = "msg";
		public static final String PROP_LOCALE = "locale";
		public static final String PROP_SPEED = "speed";
		public static final String PROP_DISTANCE = "distance";
		public static final String PROP_LENGTH = "length";
		public static final String PROP_SPEED_UNIT = "speed_unit";
		public static final String PROP_DISTANCE_UNIT = "distance_unit";

		public Type(Type... ancestors) {super(new HashMap<>(), ancestors);}

		public Type(Map<String, Object> props, Type... ancestors) {
			super(props, ancestors);
		}
	}

	static class SpeedLimit extends Type {
		public static final Integer DEFAULT_SPEED_LIMIT = 50;
		public static final String PROP_SPEED_LIMIT = "speed_limit";
		private final String imsg;
		private final String amsg;
		private final String wmsg;

		SpeedLimit(PoiDb db) {
			super(Collections.emptyMap(), db);
			var res = App.get().getResources();
			this.imsg = res.getString(R.string.speed_limit_i);
			this.amsg = res.getString(R.string.speed_limit_a);
			this.wmsg = res.getString(R.string.speed_limit_w);
		}

		@Nullable
		@Override
		public Object get(String name) {
			if (PROP_MSG.equals(name)) return (Function<Props, String>) this::msg;
			if (PROP_SPEED_LIMIT.equals(name)) return DEFAULT_SPEED_LIMIT;
			return super.get(name);
		}

		private String msg(Props props) {
			int dist = props.getProp(PROP_DISTANCE, Integer.MAX_VALUE);
			if (dist == Integer.MAX_VALUE) return null;
			int len = props.getProp(PROP_LENGTH, Integer.MAX_VALUE);
			if (len == Integer.MAX_VALUE) return null;
			if (dist > len) return amsg;
			int limit = props.getProp(PROP_SPEED_LIMIT, DEFAULT_SPEED_LIMIT);
			return (props.getProp(PROP_SPEED, 50) > limit) ? wmsg : imsg;
		}
	}
}
