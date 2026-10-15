package me.aap.fermata.media.service;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.view.KeyEvent;

import androidx.core.app.NotificationManagerCompat;

import java.util.List;

import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.utils.log.Log;

/**
 * The media app that was playing before Fermata, to resume it when Fermata is asked to play but has
 * nothing to resume (e.g. by Android Auto's autoplay after watching YouTube).
 */
final class PreviousMediaApp {
	// Fermata started playing while the previous app had a session, i.e. it took over from it
	private static boolean tookOver;

	private PreviousMediaApp() {
	}

	static boolean hasAccess(Context ctx) {
		return NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.getPackageName());
	}

	// Called when Fermata's session becomes active: remember the most recent other session
	static void record(Context ctx, MediaLibPrefs prefs) {
		List<MediaController> sessions = getSessions(ctx, prefs);
		if (sessions == null) return;

		for (MediaController c : sessions) {
			String pkg = c.getPackageName();
			if (pkg.equals(ctx.getPackageName())) continue;
			prefs.applyStringPref(MediaLibPrefs.PREVIOUS_MEDIA_APP, pkg);
			tookOver = true;
			return;
		}
	}

	// Called when Fermata's car screen is closed with nothing to resume
	static void handBack(Context ctx, MediaLibPrefs prefs) {
		if (!prefs.getBooleanPref(MediaLibPrefs.RESUME_PREVIOUS_APP_ON_LEAVE)) return;
		if (!tookOver || !isProjecting(ctx)) return;
		List<MediaController> sessions = getSessions(ctx, prefs);
		if (sessions == null) return;
		String pkg = prefs.getStringPref(MediaLibPrefs.PREVIOUS_MEDIA_APP);

		for (MediaController c : sessions) {
			if (!c.getPackageName().equals(pkg)) continue;
			PlaybackState st = c.getPlaybackState();
			if ((st != null) && (st.getState() == PlaybackState.STATE_PLAYING)) {
				tookOver = false;
				return;
			}
		}

		resume(ctx, prefs);
	}

	// Returns true if the previous app was asked to play
	static boolean resume(Context ctx, MediaLibPrefs prefs) {
		List<MediaController> sessions = getSessions(ctx, prefs);
		if (sessions == null) return false;
		String pkg = prefs.getStringPref(MediaLibPrefs.PREVIOUS_MEDIA_APP);
		if (pkg == null) return false;
		tookOver = false;

		for (MediaController c : sessions) {
			if (!pkg.equals(c.getPackageName())) continue;
			c.getTransportControls().play();
			return true;
		}

		// No session left: try its media button receiver
		Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg);
		if (ctx.getPackageManager().queryBroadcastReceivers(i, 0).isEmpty()) return false;
		ctx.sendBroadcast(new Intent(i).putExtra(Intent.EXTRA_KEY_EVENT,
				new KeyEvent(ACTION_DOWN, KEYCODE_MEDIA_PLAY)));
		ctx.sendBroadcast(new Intent(i).putExtra(Intent.EXTRA_KEY_EVENT,
				new KeyEvent(ACTION_UP, KEYCODE_MEDIA_PLAY)));
		return true;
	}

	// Same query as the Car App Library's CarConnection: is Android Auto projecting right now?
	private static boolean isProjecting(Context ctx) {
		try (Cursor c = ctx.getContentResolver().query(Uri.parse("content://androidx.car.app.connection"),
				new String[]{"CarConnectionState"}, null, null, null)) {
			return (c != null) && c.moveToNext() && (c.getInt(0) == 2); // CONNECTION_TYPE_PROJECTION
		} catch (Exception ex) {
			Log.d(ex, "Failed to query the car connection");
			return false;
		}
	}

	private static List<MediaController> getSessions(Context ctx, MediaLibPrefs prefs) {
		if (!prefs.getBooleanPref(MediaLibPrefs.RESUME_PREVIOUS_APP) || !hasAccess(ctx)) return null;
		MediaSessionManager m = ctx.getSystemService(MediaSessionManager.class);
		if (m == null) return null;

		try {
			return m.getActiveSessions(new ComponentName(ctx, MediaSessionListener.class));
		} catch (SecurityException ex) {
			Log.d(ex, "No access to media sessions");
			return null;
		}
	}
}
