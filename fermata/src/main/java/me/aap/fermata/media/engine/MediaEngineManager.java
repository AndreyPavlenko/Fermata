package me.aap.fermata.media.engine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.play.core.splitinstall.SplitInstallHelper;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.pref.PreferenceStore;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;

/**
 * @author Andrey Pavlenko
 */
public class MediaEngineManager implements Closeable, PreferenceStore.Listener {
	private static final String MODULE_EXO = "exoplayer";
	private final MediaLib lib;
	private final MediaEngineProvider mediaPlayer;
	private MediaEngineProvider exoPlayer;

	public MediaEngineManager(MediaLib lib) {
		this.lib = lib;
		mediaPlayer = new MediaPlayerEngineProvider();
		mediaPlayer.init(lib.getContext());
		lib.getPrefs().addBroadcastListener(this);
		setExoPlayer(true);
	}

	public MediaEngine createEngine(MediaEngine current, PlayableItem i, Listener listener) {
		if (exoPlayer == null) {
			if (current != null) {
				if (current.getId() == MEDIA_ENG_MP) return current;
				current.close();
			}

			return mediaPlayer.createEngine(listener);
		}

		PlayableItemPrefs pref = i.getPrefs();
		int id = i.isVideo() ? pref.getVideoEnginePref() : pref.getAudioEnginePref();

		if (current != null) {
			if (current.getId() == id) return current;
			current.close();
		}

		return (id == MEDIA_ENG_MP) ? mediaPlayer.createEngine(listener) : exoPlayer.createEngine(listener);
	}

	public MediaEngine createAnotherEngine(@NonNull MediaEngine current, Listener listener) {
		current.close();
		if (exoPlayer == null) return null;
		int id = current.getId();
		return (id == MEDIA_ENG_MP) ? exoPlayer.createEngine(listener) : mediaPlayer.createEngine(listener);
	}

	@Override
	public void close() {
		lib.getPrefs().removeBroadcastListener(this);
	}

	private void setExoPlayer(boolean install) {
		if (lib.getPrefs().getExoEnabledPref()) {
			try {
				exoPlayer = (MediaEngineProvider) Class.forName(
						"me.aap.fermata.engine.exoplayer.ExoPlayerEngineProvider").newInstance();
				exoPlayer.init(lib.getContext());
				return;
			} catch (Throwable ex) {
				Log.e(getClass().getName(), "ExoPlayer not found", ex);
				if (install) installExoPlayer();
			}
		}

		exoPlayer = null;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(MediaLibPrefs.EXO_ENABLED)) {
			if (lib.getPrefs().getExoEnabledPref()) {
				installExoPlayer();
			} else {
				exoPlayer = null;
				Log.i(getClass().getName(), "Uninstalling ExoPlayer");
				SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
				sm.deferredUninstall(Collections.singletonList(MODULE_EXO));
			}
		}
	}

	@SuppressLint("SwitchIntDef")
	private void installExoPlayer() {
		SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
		SplitInstallRequest req = SplitInstallRequest.newBuilder().addModule(MODULE_EXO).build();
		int[] sessionId = new int[1];
		sm.registerListener(st -> {
			if (st.status() == SplitInstallSessionStatus.FAILED) {
				Log.e(getClass().getName(), "Failed to install ExoPlayer: " + st);
				return;
			}

			if (st.sessionId() == sessionId[0]) {
				switch (st.status()) {
					case SplitInstallSessionStatus.INSTALLING:
						Log.i(getClass().getName(), "Installing ExoPlayer");
						break;
					case SplitInstallSessionStatus.INSTALLED:
						Log.i(getClass().getName(), "ExoPlayer installed");

						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							SplitInstallHelper.updateAppInfo(lib.getContext());
							FermataApplication.get().getHandler().post(() -> setExoPlayer(false));
						} else {
							setExoPlayer(false);
						}

						break;
					case SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION:
						try {
							sm.startConfirmationDialogForResult(st, new Activity(), 123);
						} catch (Exception ex) {
							Log.e(getClass().getName(), "Failed to request user confirmation", ex);
						}

						break;
				}
			}
		});

		sm.startInstall(req)
				.addOnSuccessListener(id -> sessionId[0] = id)
				.addOnFailureListener(ex -> {
					Log.e(getClass().getName(), "Failed to install ExoPlayer", ex);
					exoPlayer = null;
				});
	}
}
