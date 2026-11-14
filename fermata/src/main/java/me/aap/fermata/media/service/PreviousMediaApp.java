package me.aap.fermata.media.service;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.annotation.Nullable;
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
	// The most recent other session when Fermata asked for the audio focus, and when: the app it's taking
	// over from, which may deactivate its session on losing the focus, before Fermata starts playing
	private static String takingOverFrom;
	private static long takingOverTime;

	private PreviousMediaApp() {
	}

	// Called before Fermata asks for the audio focus to play
	static void beforeTakeOver(Context ctx, MediaLibPrefs prefs) {
		takingOverFrom = getMostRecentOther(ctx, prefs);
		takingOverTime = SystemClock.elapsedRealtime();
	}

	// Called when Fermata starts playing: remember the app it took over from
	static void record(Context ctx, MediaLibPrefs prefs) {
		// Taken before the audio focus request, unless that was too long ago to belong to this playback,
		// e.g. a web video that started playing by itself
		String pkg = (SystemClock.elapsedRealtime() - takingOverTime < 30000L) ? takingOverFrom :
				getMostRecentOther(ctx, prefs);
		takingOverFrom = null;
		takingOverTime = 0L;
		if (pkg == null) return;
		prefs.applyStringPref(MediaLibPrefs.PREVIOUS_MEDIA_APP, pkg);
		tookOver = true;
	}

	@Nullable
	private static String getMostRecentOther(Context ctx, MediaLibPrefs prefs) {
		List<MediaController> sessions = getSessions(ctx, prefs);
		if (sessions == null) return null;
		for (MediaController c : sessions) {
			if (!c.getPackageName().equals(ctx.getPackageName())) return c.getPackageName();
		}
		return null;
	}

	// Called when Fermata's car screen is closed with nothing to resume, while Android Auto is projecting
	static void handBack(Context ctx, MediaLibPrefs prefs) {
		if (!tookOver) return;
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

	// Ask the previous app to play
	static void resume(Context ctx, MediaLibPrefs prefs) {
		List<MediaController> sessions = getSessions(ctx, prefs);
		if (sessions == null) return;
		String pkg = prefs.getStringPref(MediaLibPrefs.PREVIOUS_MEDIA_APP);
		if (pkg == null) return;
		tookOver = false;

		for (MediaController c : sessions) {
			if (!pkg.equals(c.getPackageName())) continue;
			c.getTransportControls().play();
			return;
		}

		// No session left: try its media button receiver
		Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg);
		if (ctx.getPackageManager().queryBroadcastReceivers(i, 0).isEmpty()) return;
		ctx.sendBroadcast(new Intent(i).putExtra(Intent.EXTRA_KEY_EVENT,
				new KeyEvent(ACTION_DOWN, KEYCODE_MEDIA_PLAY)));
		ctx.sendBroadcast(new Intent(i).putExtra(Intent.EXTRA_KEY_EVENT,
				new KeyEvent(ACTION_UP, KEYCODE_MEDIA_PLAY)));
	}

	private static List<MediaController> getSessions(Context ctx, MediaLibPrefs prefs) {
		if (!prefs.getBooleanPref(MediaLibPrefs.RESUME_PREVIOUS_APP)) return null;
		if (!NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.getPackageName())) return null;
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
