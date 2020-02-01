package me.aap.fermata.media.engine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.play.core.install.InstallException;
import com.google.android.play.core.splitinstall.SplitInstallHelper;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;

import java.io.Closeable;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.function.Consumer;
import me.aap.fermata.media.engine.MediaEngine.Listener;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.ui.activity.MainActivity;

import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_EXO;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_MP;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_VLC;

/**
 * @author Andrey Pavlenko
 */
public class MediaEngineManager implements Closeable, PreferenceStore.Listener {
	private static final String MODULE_EXO = "exoplayer";
	private static final String MODULE_VLC = "vlc";
	private static MediaEngineManager instance;
	private final MediaLib lib;
	private final MediaEngineProvider mediaPlayer;
	private MediaEngineProvider exoPlayer;
	private MediaEngineProvider vlcPlayer;

	public MediaEngineManager(MediaLib lib) {
		this.lib = lib;
		mediaPlayer = new MediaPlayerEngineProvider();
		mediaPlayer.init(lib.getContext());
		lib.getPrefs().addBroadcastListener(this);
		setExoPlayer(true);
		setVlcPlayer(true);
		instance = this;
	}

	public static MediaEngineManager getInstance() {
		return instance;
	}

	public void getMediaMetadata(MediaMetadataCompat.Builder meta, PlayableItem item) {
		if ((vlcPlayer != null) && vlcPlayer.getMediaMetadata(meta, item)) return;
		mediaPlayer.getMediaMetadata(meta, item);
	}

	public boolean isExoPlayerSupported() {
		return exoPlayer != null;
	}

	public boolean isVlcPlayerSupported() {
		return vlcPlayer != null;
	}

	public boolean isExternalPlayerSupported() {
		return isExoPlayerSupported() || isVlcPlayerSupported();
	}

	public MediaEngine createEngine(MediaEngine current, PlayableItem i, Listener listener) {
		if (!isExternalPlayerSupported()) {
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
				if (install) {
					exoPlayer = null;
					installPlayer(MODULE_EXO, this::setExoPlayer, this::installExoPlayerFailed,
							R.string.engine_exo_name);
				}
			}
		}

		exoPlayer = null;
	}

	private void setVlcPlayer(boolean install) {
		if (lib.getPrefs().getVlcEnabledPref()) {
			try {
				vlcPlayer = (MediaEngineProvider) Class.forName(
						"me.aap.fermata.engine.vlc.VlcEngineProvider").newInstance();
				vlcPlayer.init(lib.getContext());
				return;
			} catch (Throwable ex) {
				Log.e(getClass().getName(), "VlcPlayer not found", ex);
				if (install) {
					vlcPlayer = null;
					installPlayer(MODULE_VLC, this::setVlcPlayer, this::installVlcPlayerFailed,
							R.string.engine_vlc_name);
				}
			}
		}

		vlcPlayer = null;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(MediaLibPrefs.EXO_ENABLED)) {
			if (lib.getPrefs().getExoEnabledPref()) {
				exoPlayer = null;
				installPlayer(MODULE_EXO, this::setExoPlayer, this::installExoPlayerFailed,
						R.string.engine_exo_name);
			} else {
				exoPlayer = null;
				Log.i(getClass().getName(), "Uninstalling module " + MODULE_EXO);
				SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
				sm.deferredUninstall(Collections.singletonList(MODULE_EXO)).addOnSuccessListener(
						r -> toast(R.string.engine_uninstalled, R.string.engine_exo_name));
			}
		} else if (prefs.contains(MediaLibPrefs.VLC_ENABLED)) {
			if (lib.getPrefs().getVlcEnabledPref()) {
				vlcPlayer = null;
				installPlayer(MODULE_VLC, this::setVlcPlayer, this::installVlcPlayerFailed,
						R.string.engine_vlc_name);
			} else {
				vlcPlayer = null;
				Log.i(getClass().getName(), "Uninstalling module " + MODULE_VLC);
				SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
				sm.deferredUninstall(Collections.singletonList(MODULE_VLC)).addOnSuccessListener(
						r -> toast(R.string.engine_uninstalled, R.string.engine_vlc_name));
			}
		}
	}

	@SuppressLint("SwitchIntDef")
	private void installPlayer(String module, Consumer<Boolean> onSuccess,
														 Consumer<Exception> onError, @StringRes int engineName) {
		SplitInstallManager sm = SplitInstallManagerFactory.create(lib.getContext());
		SplitInstallRequest req = SplitInstallRequest.newBuilder().addModule(module).build();
		int[] sessionId = new int[1];
		sm.registerListener(
				new SplitInstallStateUpdatedListener() {
					@Override
					public void onStateUpdate(SplitInstallSessionState st) {
						if (st.status() == SplitInstallSessionStatus.FAILED) {
							sm.unregisterListener(this);
							onError.accept(new InstallException(st.errorCode()));
							return;
						}

						if (st.sessionId() == sessionId[0]) {
							switch (st.status()) {
								case SplitInstallSessionStatus.INSTALLING:
									Log.i(getClass().getName(), "Installing module " + module);
									break;
								case SplitInstallSessionStatus.INSTALLED:
									Log.i(getClass().getName(), "Module " + module + " installed");
									sm.unregisterListener(this);
									toast(R.string.engine_installed, engineName);

									if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
										SplitInstallHelper.updateAppInfo(lib.getContext());
										FermataApplication.get().getHandler().post(() -> onSuccess.accept(false));
									} else {
										onSuccess.accept(false);
									}

									break;
								case SplitInstallSessionStatus.CANCELED:
									Log.i(getClass().getName(), "Module " + module + " installation canceled");
									sm.unregisterListener(this);
									break;
								case SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION:
									try {
										sm.startConfirmationDialogForResult(st, MainActivity.getInstance(), 123);
									} catch (Exception ex) {
										Log.e(getClass().getName(), "Failed to request user confirmation", ex);
										sm.unregisterListener(this);
										onError.accept(ex);
									}

									break;
							}
						}
					}
				}
		);

		sm.startInstall(req)
				.addOnSuccessListener(id -> sessionId[0] = id)
				.addOnFailureListener(onError::accept);
	}

	private void toast(@StringRes int msg, @StringRes int arg) {
		Context ctx = lib.getContext();
		Toast.makeText(ctx, ctx.getString(msg, ctx.getString(arg)), Toast.LENGTH_LONG).show();
	}

	private void installExoPlayerFailed(Exception ex) {
		setExoPlayer(false);
		if (exoPlayer == null) {
			Log.e(getClass().getName(), "Failed to install ExoPlayer", ex);
			toast(R.string.engine_install_failed, R.string.engine_exo_name);
		}
	}

	private void installVlcPlayerFailed(Exception ex) {
		setVlcPlayer(false);
		if (vlcPlayer == null) {
			Log.e(getClass().getName(), "Failed to install VlcPlayer", ex);
			toast(R.string.engine_install_failed, R.string.engine_vlc_name);
		}
	}
}
