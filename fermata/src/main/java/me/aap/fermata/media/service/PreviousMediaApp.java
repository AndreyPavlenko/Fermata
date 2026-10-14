package me.aap.fermata.media.service;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
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
			return;
		}
	}

	// Returns true if the previous app was asked to play
	static boolean resume(Context ctx, MediaLibPrefs prefs) {
		List<MediaController> sessions = getSessions(ctx, prefs);
		if (sessions == null) return false;
		String pkg = prefs.getStringPref(MediaLibPrefs.PREVIOUS_MEDIA_APP);
		if (pkg == null) return false;

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
