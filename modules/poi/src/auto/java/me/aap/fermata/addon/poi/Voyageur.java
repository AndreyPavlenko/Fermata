package me.aap.fermata.addon.poi;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.ResultConsumer;
import me.aap.utils.log.Log;
import me.aap.utils.voice.TextToSpeech;

public class Voyageur extends Poi.Type implements LocationListener {
	private final Map<String, Long> messages = new HashMap<>();
	private TextToSpeech tts;
	private Bundle ttsParams;
	private LocationManager locationManager;
	private FutureSupplier<PoiDb> loadDb = completedNull();
	private FutureSupplier<?> speak = completedVoid();
	private Location location = new Location("");
	private Poi activePoi;
	private int distanceToPoi = -1;

	private Voyageur(TextToSpeech tts, LocationManager locationManager) {
		this.tts = tts;
		this.locationManager = locationManager;
		ttsParams = new Bundle();
		ttsParams.putInt(KEY_PARAM_STREAM, AudioManager.STREAM_ALARM);
		setProp(PROP_SPEED, this::getSpeed);
		setProp(PROP_DISTANCE, this::distanceToPoi);
		setProp(PROP_LENGTH, this::poiLength);
	}

	public static FutureSupplier<Voyageur> start(Context ctx) {
		Log.i("Starting voyage...");
		var main = MainActivity.getActiveInstance();
		var permCheck = (main == null) ? completed(true) : main.checkPermissions(ACCESS_FINE_LOCATION);
		return permCheck.then(r -> TextToSpeech.create(ctx, Locale.ENGLISH)).map(tts -> {
			if (ActivityCompat.checkSelfPermission(ctx, ACCESS_FINE_LOCATION)
					!= PackageManager.PERMISSION_GRANTED) {
				throw new SecurityException("Location permission not granted");
			}

			var mgr = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
			if (mgr == null) {
				throw new RuntimeException("LocationManager not available");
			}

			var v = new Voyageur(tts, mgr);
			mgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 50, v);
			return v;
		}).main().onFailure(err -> {
			Log.e(err);
			var a = MainActivity.getActiveInstance();
			if (a != null) {
				showAlert(a.getContext(), me.aap.fermata.R.string.err_failed_start_service, err);
			}
		});
	}

	public void stop() {
		Log.i("Stopping voyage...");
		if (locationManager != null) {
			locationManager.removeUpdates(this);
			locationManager = null;
		}
		if (tts != null) {
			tts.stop();
			tts = null;
		}
		loadDb.cancel();
		loadDb = completedNull();
	}

	public boolean isRunning() {
		return locationManager != null;
	}

	public Location getLocation() {
		return location;
	}

	public int getSpeed() {
		return (int) (location.getSpeed() * 3.6);
	}

	public double getLatitude() {
		return location.getLatitude();
	}

	public double getLongitude() {
		return location.getLongitude();
	}

	public int distanceToPoi() {
		if (activePoi == null) return Integer.MAX_VALUE;
		if (distanceToPoi == -1) distanceToPoi = activePoi.distanceFrom(getLocation());
		return distanceToPoi;
	}

	public int poiLength() {
		return activePoi != null ? activePoi.getLength() : Integer.MAX_VALUE;
	}

	@Override
	public void onLocationChanged(Location location) {
		this.location = location;
		int maxDistance = 1000;

		if (activePoi != null) {
			distanceToPoi = activePoi.distanceFrom(location);
			if (distanceToPoi != Integer.MAX_VALUE && distanceToPoi > 0 && distanceToPoi <= maxDistance) {
				update();
				return;
			}
			activePoi = null;
			distanceToPoi = -1;
			messages.clear();
		}

		var db = this.loadDb.peek();
		if (db == null) {
			loadDb();
			return;
		}

		var nearest = db.findNearestTo(location, 1000);
		if (nearest != null) {
			activePoi = nearest;
			Log.d("Active POI: ", activePoi, ". Distance ", nearest.distanceFrom(location));
			update();
		} else {
			Log.d("No POI found within 1 km from the current location (", getLatitude(), ", ",
					getLongitude(), ").");
		}

		var dist = db.distanceTo(location) / 1000;
		if (dist >= 80) {
			Log.d("The POI database has been loaded ", dist, " km away from the current location (",
					getLatitude(), ", ", getLongitude(), ") at (", db.getLatitude(), ", ",
					db.getLongitude(), ").");
			loadDb();
		}
	}

	private void loadDb() {
		if (!loadDb.isDone() || !isRunning()) return;

		PoiAddon addon = FermataApplication.get().getAddonManager().getAddon(PoiAddon.class);
		if (addon == null) {
			stop();
			return;
		}
		var prov = PoiDb.Provider.create(addon.getDbUrl());
		if (prov == null) {
			stop();
			return;
		}

		Log.i("Loading POI database...");
		loadDb = prov.loadFor(this, 100).main().onSuccess(ldb -> {
			if (!isRunning()) return;
			loadDb = completed(ldb);
			onLocationChanged(location);
		}).onFailure(err -> {
			if (ResultConsumer.Cancel.isCancellation(err)) return;
			Log.e(err, "Failed to load POI database. Retrying in 30 seconds ...");
			var p = new Promise<PoiDb>();
			loadDb = p;
			App.get().getHandler().postDelayed(() -> {
				if (p.isDone()) return;
				p.complete(null);
				loadDb();
			}, 30000);
		});
	}

	@Override
	public void onLocationChanged(@NonNull List<Location> locations) {
		onLocationChanged(locations.get(locations.size() - 1));
	}

	private void update() {
		if (activePoi == null) return;

		var type = activePoi.getType();
		var msg = type.getProp(Poi.Type.PROP_MSG, "");
		if (msg.isEmpty()) {
			Log.d("Empty message for POI type: ", type);
			return;
		}
		var stamp = messages.get(msg);
		// Do not repeat non-urgent messages
		if (stamp != null && !msg.endsWith("!") && !msg.startsWith("!")) {
			Log.d("Message already announced: ", msg);
			return;
		}
		long now = System.currentTimeMillis();
		if (stamp != null && now - stamp < 10000) return;
		messages.put(msg, now);

		var locale = type.getStrProp(Poi.Type.PROP_LOCALE, Locale.getDefault().getLanguage());
		try {
			tts.setLanguage(Locale.forLanguageTag(locale));
		} catch (Exception err) {
			Log.e(err, "Failed to set TTS language to ", locale);
		}
		msg = type.substitute(msg);
		Log.i(msg);
		speak.cancel();
		speak = tts.speak(msg, null, ttsParams);
	}
}
