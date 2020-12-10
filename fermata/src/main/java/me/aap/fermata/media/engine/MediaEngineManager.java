package me.aap.fermata.media.engine;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.module.DynamicModuleInstaller;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityBase;

import static me.aap.fermata.media.pref.MediaLibPrefs.EXO_ENABLED;
import static me.aap.fermata.media.pref.MediaLibPrefs.VLC_ENABLED;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;
import static me.aap.fermata.media.pref.MediaPrefs.VIDEO_ENGINE;

/**
 * @author Andrey Pavlenko
 */
public class MediaEngineManager implements PreferenceStore.Listener {
	private static final String EXO_PROV_CLASS = "me.aap.fermata.engine.exoplayer.ExoPlayerEngineProvider";
	private static final String VLC_PROV_CLASS = "me.aap.fermata.engine.vlc.VlcEngineProvider";
	private static final String MODULE_EXO = "exoplayer";
	private static final String MODULE_VLC = "vlc";
	final MediaLib lib;
	final MediaPlayerEngineProvider mediaPlayer;
	MediaEngineProvider exoPlayer;
	MediaEngineProvider vlcPlayer;

	public MediaEngineManager(MediaLib lib) {
		MediaLibPrefs prefs = lib.getPrefs();

		if (!prefs.hasPref(EXO_ENABLED) && isProviderAvailable(EXO_PROV_CLASS)) {
			prefs.applyBooleanPref(EXO_ENABLED, true);
		}
		if (!prefs.hasPref(VLC_ENABLED) && isProviderAvailable(VLC_PROV_CLASS)) {
			prefs.applyBooleanPref(VLC_ENABLED, true);
		}
		if (!prefs.hasPref(VIDEO_ENGINE) && isProviderAvailable(VLC_PROV_CLASS)) {
			prefs.setVideoEnginePref(MEDIA_ENG_VLC);
		}

		this.lib = lib;
		mediaPlayer = new MediaPlayerEngineProvider();
		mediaPlayer.init(lib.getContext());
		lib.getPrefs().addBroadcastListener(this);
		setExoPlayer(true);
		setVlcPlayer(true);
	}

	public boolean isExoPlayerSupported() {
		return exoPlayer != null;
	}

	public boolean isVlcPlayerSupported() {
		return vlcPlayer != null;
	}

	public boolean isAdditionalPlayerSupported() {
		return isExoPlayerSupported() || isVlcPlayerSupported();
	}

	public MediaEngine createEngine(MediaEngine current, PlayableItem i, Listener listener) {
		if (!isAdditionalPlayerSupported()) {
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

		switch (id) {
			case MEDIA_ENG_EXO:
				if (exoPlayer != null) return exoPlayer.createEngine(listener);
			case MEDIA_ENG_VLC:
				if (vlcPlayer != null) return vlcPlayer.createEngine(listener);
			default:
				return mediaPlayer.createEngine(listener);
		}
	}

	public MediaEngine createAnotherEngine(@NonNull MediaEngine current, Listener listener) {
		int id = current.getId();
		current.close();

		if ((vlcPlayer != null) && (id != MEDIA_ENG_VLC)) {
			return vlcPlayer.createEngine(listener);
		}
		if ((exoPlayer != null) && (id != MEDIA_ENG_EXO)) {
			return exoPlayer.createEngine(listener);
		}
		if (id != MEDIA_ENG_MP) {
			return mediaPlayer.createEngine(listener);
		}

		return null;
	}

	private boolean isProviderAvailable(String providerClass) {
		try {
			Class.forName(providerClass).newInstance();
			return true;
		} catch (Throwable ex) {
			return false;
		}
	}

	private void setExoPlayer(boolean install) {
		if (lib.getPrefs().getExoEnabledPref()) {
			try {
				exoPlayer = (MediaEngineProvider) Class.forName(EXO_PROV_CLASS).newInstance();
				exoPlayer.init(lib.getContext());
				return;
			} catch (Throwable ex) {
				Log.e(ex, "ExoPlayer not found");
				if (install) {
					exoPlayer = null;
					FutureSupplier<Void> i = installPlayer(MODULE_EXO, R.string.engine_exo_name);
					i.main().onSuccess(v -> setExoPlayer(false)).onFailure(this::installExoFailed);
				}
			}
		}

		exoPlayer = null;
	}

	private void setVlcPlayer(boolean install) {
		if (lib.getPrefs().getVlcEnabledPref()) {
			try {
				vlcPlayer = (MediaEngineProvider) Class.forName(VLC_PROV_CLASS).newInstance();
				vlcPlayer.init(lib.getContext());
				return;
			} catch (Throwable ex) {
				Log.e(ex, "VlcPlayer not found");
				if (install) {
					vlcPlayer = null;
					FutureSupplier<Void> i = installPlayer(MODULE_VLC, R.string.engine_vlc_name);
					i.main().onSuccess(v -> setVlcPlayer(false)).onFailure(this::installVlcFailed);
				}
			}
		}

		vlcPlayer = null;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(EXO_ENABLED)) {
			if (lib.getPrefs().getExoEnabledPref()) {
				exoPlayer = null;
				FutureSupplier<Void> i = installPlayer(MODULE_EXO, R.string.engine_exo_name);
				i.main().onSuccess(v -> setExoPlayer(false)).onFailure(this::installExoFailed);
			} else {
				exoPlayer = null;
				Log.i("Uninstalling module ", MODULE_EXO);
				SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
				sm.deferredUninstall(Collections.singletonList(MODULE_EXO)).addOnSuccessListener(
						r -> toast(R.string.engine_uninstalled, R.string.engine_exo_name));
			}
		} else if (prefs.contains(MediaLibPrefs.VLC_ENABLED)) {
			if (lib.getPrefs().getVlcEnabledPref()) {
				vlcPlayer = null;
				FutureSupplier<Void> i = installPlayer(MODULE_VLC, R.string.engine_vlc_name);
				i.main().onSuccess(v -> setVlcPlayer(false)).onFailure(this::installVlcFailed);
			} else {
				vlcPlayer = null;
				Log.i("Uninstalling module ", MODULE_VLC);
				SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
				sm.deferredUninstall(Collections.singletonList(MODULE_VLC)).addOnSuccessListener(
						r -> toast(R.string.engine_uninstalled, R.string.engine_vlc_name));
			}
		}
	}

	private FutureSupplier<Void> installPlayer(String module, @StringRes int engineName) {
		Context ctx = lib.getContext();
		String name = ctx.getString(engineName);
		String channelId = "fermata.engine.install";
		String title = ctx.getString(R.string.module_installation, name);
		String installing = ctx.getString(R.string.installing, name);
		FutureSupplier<MainActivity> getActivity = ActivityBase.create(ctx, channelId,
				title, R.drawable.ic_notification, title, null, MainActivity.class);

		return getActivity.then(a -> {
			DynamicModuleInstaller i = new DynamicModuleInstaller(a);
			i.setSmallIcon(R.drawable.ic_notification);
			i.setTitle(title);
			i.setNotificationChannel(channelId, installing);
			i.setPendingMessage(ctx.getString(R.string.install_pending, name));
			i.setDownloadingMessage(ctx.getString(R.string.downloading, name));
			i.setInstallingMessage(ctx.getString(R.string.installing, name));
			return i.install(module);
		});
	}

	private void toast(@StringRes int msg, @StringRes int arg) {
		Context ctx = lib.getContext();
		Toast.makeText(ctx, ctx.getString(msg, ctx.getString(arg)), Toast.LENGTH_LONG).show();
	}

	private void installExoFailed(Throwable ex) {
		setExoPlayer(false);
		if (exoPlayer == null) {
			Log.e(ex, "Failed to install ExoPlayer");
			toast(R.string.err_failed_install_module, R.string.engine_exo_name);
		}
	}

	private void installVlcFailed(Throwable ex) {
		setVlcPlayer(false);
		if (vlcPlayer == null) {
			Log.e(ex, "Failed to install VlcPlayer");
			toast(R.string.err_failed_install_module, R.string.engine_vlc_name);
		}
	}
}
