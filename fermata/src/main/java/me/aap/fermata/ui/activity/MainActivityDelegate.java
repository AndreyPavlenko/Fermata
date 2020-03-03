package me.aap.fermata.ui.activity;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.fragment.AudioEffectsFragment;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.PlaylistsFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.fragment.VideoFragment;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static me.aap.fermata.media.service.FermataMediaService.DEFAULT_NOTIF_COLOR;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.activity.ActivityListener.SERVICE_BOUND;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends ActivityDelegate implements
		PreferenceStore.Listener, FermataServiceUiBinder.Listener {
	private FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private boolean bind = true;
	private boolean barsHidden;
	private boolean videoMode;

	public MainActivityDelegate() {
		getPrefs().addBroadcastListener(this);
	}

	public static MainActivityDelegate get(Context ctx) {
		return (MainActivityDelegate) ActivityDelegate.get(ctx);
	}

	@Override
	public void onActivityCreate(Bundle savedInstanceState) {
		setTheme();

		if (bind) {
			bind = false;
			TypedArray typedArray = getTheme().obtainStyledAttributes(new int[]{android.R.attr.statusBarColor});
			int notifColor = typedArray.getColor(0, Color.parseColor(DEFAULT_NOTIF_COLOR));
			typedArray.recycle();
			FermataServiceUiBinder.bind(FermataApplication.get(), notifColor, this::onMediaServiceBind);
		} else {
			if (mediaServiceBinder != null) init();
		}
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
	}

	public void onActivityFinish() {
		if (mediaServiceBinder != null) {
			mediaServiceBinder.removeBroadcastListener(this);
			FermataApplication.get().unbindService(mediaServiceBinder);
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();

		if ((mediaServiceBinder != null) && mediaServiceBinder.isConnected()) {
			mediaServiceBinder.getLib().clearCache();
		}

		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction tr = null;

		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if ((f != this) && f.isHidden()) {
				if (tr == null) tr = fm.beginTransaction();
				tr.remove(f);
			}
		}

		if (tr != null) tr.commitAllowingStateLoss();
	}

	@Override
	public AppActivity getAppActivity() {
		return (AppActivity) super.getAppActivity();
	}

	public boolean isCarActivity() {
		return getAppActivity().isCarActivity();
	}

	@NonNull
	public MainActivityPrefs getPrefs() {
		return Prefs.instance;
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return getMediaServiceBinder().getMediaSessionCallback().getPlaybackControlPrefs();
	}

	@SuppressWarnings("deprecation")
	protected void setTheme() {
		switch (getPrefs().getThemePref()) {
			default:
			case MainActivityPrefs.THEME_DARK:
				getAppActivity().setTheme(R.style.AppTheme_Dark);
				break;
			case MainActivityPrefs.THEME_LIGHT:
				getAppActivity().setTheme(R.style.AppTheme_Light);
				break;
			case MainActivityPrefs.THEME_DAY_NIGHT:
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME);
				getAppActivity().setTheme(R.style.AppTheme_DayNight);
				break;
			case MainActivityPrefs.THEME_BLACK:
				getAppActivity().setTheme(R.style.AppTheme_Black);
				break;
		}
	}

	@Override
	public boolean isFullScreen() {
		return videoMode || getPrefs().getFullscreenPref();
	}

	public FermataServiceUiBinder getMediaServiceBinder() {
		return mediaServiceBinder;
	}

	public MediaSessionCallback getMediaSessionCallback() {
		return getMediaServiceBinder().getMediaSessionCallback();
	}

	@NonNull
	public MediaLib getLib() {
		return getMediaServiceBinder().getLib();
	}

	@Nullable
	public PlayableItem getCurrentPlayable() {
		return getMediaServiceBinder().getCurrentItem();
	}

	public NavBarView getNavBar() {
		return navBar;
	}

	public ToolBarView getToolBar() {
		return toolBar;
	}

	public ControlPanelView getControlPanel() {
		return controlPanel;
	}

	public FloatingButton getFloatingButton() {
		return floatingButton;
	}

	public boolean isBarsHidden() {
		return barsHidden;
	}

	public void setBarsHidden(boolean barsHidden) {
		this.barsHidden = barsHidden;
		int visibility = barsHidden ? GONE : VISIBLE;
		getToolBar().setVisibility(visibility);
		getNavBar().setVisibility(visibility);
	}

	public void setVideoMode(boolean videoMode) {
		if (videoMode) {
			this.videoMode = true;
			setSystemUiVisibility();
			getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
			getControlPanel().enableVideoMode();
		} else {
			this.videoMode = false;
			setSystemUiVisibility();
			getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
			getControlPanel().disableVideoMode();
		}
	}

	public boolean isVideoMode() {
		return videoMode;
	}

	public void backToNavFragment() {
		int id = getActiveNavItemId();
		showFragment((id == ID_NULL) ? R.id.nav_folders : id);
	}

	@Override
	protected int getFrameContainerId() {
		return R.id.frame_layout;
	}

	protected ActivityFragment createFragment(int id) {
		switch (id) {
			case R.id.nav_folders:
				return new FoldersFragment();
			case R.id.nav_favorites:
				return new FavoritesFragment();
			case R.id.nav_playlist:
				return new PlaylistsFragment();
			case R.id.nav_settings:
				return new SettingsFragment();
			case R.id.audio_effects:
				return new AudioEffectsFragment();
			case R.id.video:
				return new VideoFragment();
			case R.id.file_picker:
				return new FilePickerFragment();
			default:
				throw new IllegalArgumentException("Invalid fragment id: " + id);
		}
	}

	@Nullable
	public MediaLibFragment getActiveMediaLibFragment() {
		ActivityFragment f = getActiveFragment();
		return (f instanceof MediaLibFragment) ? (MediaLibFragment) f : null;
	}

	@Nullable
	public MediaLibFragment getMediaLibFragment(int id) {
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if (!(f instanceof MediaLibFragment)) continue;
			MediaLibFragment m = (MediaLibFragment) f;
			if (m.getFragmentId() == id) return m;
		}

		return null;
	}

	public boolean hasCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return (pi != null) || (getLib().getPrefs().getLastPlayedItemPref() != null);
	}

	public boolean goToCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();

		if (pi == null) pi = getLib().getLastPlayedItem();
		if (pi == null) return false;

		MediaLib.BrowsableItem root = pi.getRoot();

		if (root instanceof MediaLib.Folders) {
			showFragment(R.id.nav_folders);
		} else if (root instanceof MediaLib.Favorites) {
			showFragment(R.id.nav_favorites);
		} else if (root instanceof MediaLib.Playlists) {
			showFragment(R.id.nav_playlist);
		} else {
			throw new UnsupportedOperationException();
		}

		PlayableItem i = pi;
		FermataApplication.get().getHandler().post(() -> {
			ActivityFragment f = getActiveFragment();
			if (f instanceof MediaLibFragment) ((MediaLibFragment) f).revealItem(i);
		});

		return true;
	}

	@Override
	public OverlayMenu createMenu(View anchor) {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getContextMenu() {
		return findViewById(R.id.context_menu);
	}

	public OverlayMenu getToolBarMenu() {
		return findViewById(R.id.tool_menu);
	}

	public void initPlaylistMenu(OverlayMenu menu) {
		boolean visible = !isCarActivity() || !getMediaServiceBinder().getLib().getPlaylists()
				.getChildren().isEmpty();
		menu.findItem(R.id.playlist_add).setVisible(visible);
	}

	public void createPlaylistMenu(OverlayMenu menu) {
		menu.hide();
		Resources res = getResources();

		if (!isCarActivity()) {
			menu.addItem(R.id.playlist_create, false, res.getDrawable(R.drawable.playlist_add, null),
					res.getString(R.string.playlist_create));
		}

		for (MediaLib.Item pl : getLib().getPlaylists().getChildren()) {
			menu.addItem(R.id.playlist_add_item, false, res.getDrawable(R.drawable.playlist, null),
					pl.getName());
		}
	}

	public void createPlaylist(List<PlayableItem> selection, String initName) {
		UiUtils.queryText(getContext(), R.string.playlist_name, initName, name -> {
			discardSelection();
			if (name == null) return;

			MediaLib.Playlist pl = getLib().getPlaylists().addItem(name);
			if (pl != null) {
				pl.addItems(selection);
				MediaLibFragment f = getMediaLibFragment(R.id.nav_playlist);
				if (f != null) f.getAdapter().reload();
			}
		});
	}

	public void addToPlaylist(String name, List<PlayableItem> selection) {
		discardSelection();
		for (MediaLib.Item pl : getLib().getPlaylists().getChildren()) {
			if (name.equals(pl.getName())) {
				((MediaLib.Playlist) pl).addItems(selection);
				MediaLibFragment f = getMediaLibFragment(R.id.nav_playlist);
				if (f != null) f.getAdapter().reload();
				break;
			}
		}
	}

	public void removeFromPlaylist(MediaLib.Playlist pl, List<PlayableItem> selection) {
		discardSelection();
		pl.removeItems(selection);
		MediaLibFragment f = getMediaLibFragment(R.id.nav_playlist);
		if (f != null) f.getAdapter().reload();
	}

	private void discardSelection() {
		ActivityFragment f = getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).discardSelection();
	}

	public void startActivityForResult(BiConsumer<Integer, Intent> resultHandler, Intent intent) {
		getAppActivity().startActivityForResult(resultHandler, intent);
	}

	@Override
	protected int getExitMsg() {
		return R.string.press_back_again;
	}

	private void init() {
		AppActivity a = getAppActivity();
		a.setContentView(R.layout.main_activity);
		toolBar = a.findViewById(R.id.tool_bar);
		navBar = a.findViewById(R.id.nav_bar);
		controlPanel = a.findViewById(R.id.control_panel);
		floatingButton = a.findViewById(R.id.floating_button);
		controlPanel.bind(getMediaServiceBinder());
	}

	private void onMediaServiceBind(FermataServiceUiBinder b, Throwable err) {
		AppActivity a = getAppActivity();

		if (err == null) {
			mediaServiceBinder = b;
			Context ctx = a.getContext();
			b.getMediaSessionCallback().getSession().setSessionActivity(
					PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()), 0));
			b.getMediaSessionCallback().onPrepare();
			init();
			fireBroadcastEvent(SERVICE_BOUND);

			if (!goToCurrent()) showFragment(R.id.nav_folders);
			a.checkPermissions(getRequiredPermissions());

			FermataApplication.get().getHandler().post(() -> {
				b.addBroadcastListener(this);
				onPlayableChanged(null, b.getCurrentItem());
			});
		} else {
			Log.e(getClass().getName(), err.getMessage(), err);
			new AlertDialog.Builder(a.getContext())
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(android.R.string.dialog_alert_title)
					.setMessage(err.getMessage())
					.setPositiveButton(android.R.string.ok, (d, i) -> a.finish())
					.show();
		}
	}

	private static String[] getRequiredPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.ACCESS_MEDIA_LOCATION};
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.FOREGROUND_SERVICE};
		} else {
			return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(MainActivityPrefs.THEME)) {
			setTheme();
			getAppActivity().recreate();
		} else if (prefs.contains(MainActivityPrefs.FULLSCREEN)) {
			setSystemUiVisibility();
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		if ((newItem != null) && newItem.isVideo()) showFragment(R.id.video);
	}

	private static final class Prefs implements MainActivityPrefs {
		static final Prefs instance = new Prefs();
		private final List<ListenerRef<PreferenceStore.Listener>> listeners = new LinkedList<>();
		private final SharedPreferences prefs = FermataApplication.get().getDefaultSharedPreferences();

		@NonNull
		@Override
		public SharedPreferences getSharedPreferences() {
			return prefs;
		}

		@Override
		public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
			return listeners;
		}
	}
}
