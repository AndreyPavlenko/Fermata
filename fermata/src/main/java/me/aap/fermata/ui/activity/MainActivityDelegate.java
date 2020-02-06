package me.aap.fermata.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.IdRes;
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
import me.aap.fermata.function.BiConsumer;
import me.aap.fermata.function.Function;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.ui.fragment.AudioEffectsFragment;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.PlaylistsFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.fragment.VideoFragment;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.FloatingButton;
import me.aap.fermata.ui.view.NavBarView;
import me.aap.fermata.ui.view.ToolBarView;
import me.aap.fermata.util.EventBroadcaster;
import me.aap.fermata.util.Utils;

import static android.view.View.GONE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.SYSTEM_UI_FLAG_VISIBLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.service.FermataMediaService.DEFAULT_NOTIF_COLOR;
import static me.aap.fermata.ui.activity.MainActivityListener.Event.ACTIVITY_FINISH;
import static me.aap.fermata.ui.activity.MainActivityListener.Event.FRAGMENT_CHANGED;
import static me.aap.fermata.util.Utils.forEach;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends Fragment implements
		EventBroadcaster<MainActivityListener>, PreferenceStore.Listener,
		FermataServiceUiBinder.Listener {
	private static final int FULLSCREEN_FLAGS = SYSTEM_UI_FLAG_LAYOUT_STABLE |
			SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
			SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
			SYSTEM_UI_FLAG_LOW_PROFILE |
			SYSTEM_UI_FLAG_FULLSCREEN |
			SYSTEM_UI_FLAG_HIDE_NAVIGATION |
			SYSTEM_UI_FLAG_IMMERSIVE |
			SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
	private static AppActivity carActivity;
	private final List<ListenerRef<MainActivityListener>> listeners = new LinkedList<>();
	private final boolean recreate;
	private AppActivity activity;
	private FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private AppMenu activeMenu;
	private boolean bind = true;
	private boolean backPressed;
	private boolean barsHidden;
	private boolean videoMode;
	@SuppressLint("InlinedApi")
	private int activeNavItemId = Resources.ID_NULL;
	@SuppressLint("InlinedApi")
	private int activeFragmentId = Resources.ID_NULL;

	public MainActivityDelegate() {
		recreate = true;
	}

	private MainActivityDelegate(AppActivity activity) {
		recreate = false;
		this.activity = activity;
		getPrefs().addBroadcastListener(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	public static MainActivityDelegate get(Context ctx) {
		if (requireNonNull(ctx) instanceof AppActivity) {
			return ((AppActivity) ctx).getMainActivityDelegate();
		} else {
			return requireNonNull(carActivity).getMainActivityDelegate();
		}
	}

	public static MainActivityDelegate create(AppActivity activity) {
		FragmentManager fm = activity.getSupportFragmentManager();
		MainActivityDelegate delegate = (MainActivityDelegate) fm.findFragmentByTag("MainActivityDelegate");

		if ((delegate == null) || delegate.recreate) {
			delegate = new MainActivityDelegate(activity);
			FragmentTransaction tr = fm.beginTransaction();
			forEach(fm.getFragments(), tr::remove);
			tr.add(delegate, "MainActivityDelegate");
			tr.commit();
		} else {
			delegate.activity = activity;
		}

		if (activity.isCarActivity()) carActivity = activity;
		return delegate;
	}

	public void onActivityCreate() {
		setTheme();

		if (bind) {
			bind = false;
			TypedArray typedArray = activity.getTheme().obtainStyledAttributes(new int[]{R.attr.appNotificationColor});
			int notifColor = typedArray.getColor(0, Color.parseColor(DEFAULT_NOTIF_COLOR));
			typedArray.recycle();
			FermataServiceUiBinder.bind(FermataApplication.get(), notifColor, this::onMediaServiceBind);
		} else {
			if (mediaServiceBinder != null) init();
		}
	}

	public void onActivityResume() {
		setSystemUiVisibility();
	}

	public void onActivityDestroy() {
	}

	public void onActivityFinish() {
		if (mediaServiceBinder != null) {
			mediaServiceBinder.removeBroadcastListener(this);
			FermataApplication.get().unbindService(mediaServiceBinder);
		}
	}

	public void finish() {
		fireBroadcastEvent(ACTIVITY_FINISH);
		getAppActivity().finish();
	}

	public <T extends View> T findViewById(@IdRes int id) {
		return getAppActivity().findViewById(id);
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

	public AppActivity getAppActivity() {
		return activity;
	}

	public View getCurrentFocus() {
		return getAppActivity().getCurrentFocus();
	}

	public boolean isCarActivity() {
		return getAppActivity().isCarActivity();
	}

	@Nullable
	@Override
	public Context getContext() {
		return getAppActivity().getContext();
	}

	@Override
	public Collection<ListenerRef<MainActivityListener>> getBroadcastEventListeners() {
		return listeners;
	}

	public void addBroadcastListener(MainActivityListener listener, MainActivityListener.Event... events) {
		addBroadcastListener(listener, MainActivityListener.mask(events));
	}

	public void fireBroadcastEvent(MainActivityListener.Event e) {
		fireBroadcastEvent(l -> l.onMainActivityEvent(this, e), e.mask());
	}

	public void postBroadcastEvent(MainActivityListener.Event e) {
		postBroadcastEvent(l -> l.onMainActivityEvent(this, e), e.mask());
	}

	@NonNull
	public MainActivityPrefs getPrefs() {
		return Prefs.instance;
	}

	@NonNull
	public PlaybackControlPrefs getPlaybackControlPrefs() {
		return getMediaServiceBinder().getMediaSessionCallback().getPlaybackControlPrefs();
	}

	public Resources.Theme getTheme() {
		return getAppActivity().getTheme();
	}

	public Window getWindow() {
		return getAppActivity().getWindow();
	}

	@SuppressWarnings("deprecation")
	private void setTheme() {
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

	private void setSystemUiVisibility() {
		View decor = getAppActivity().getWindow().getDecorView();

		if (videoMode || getPrefs().getFullscreenPref()) {
			decor.setSystemUiVisibility(FULLSCREEN_FLAGS);
		} else {
			decor.setSystemUiVisibility(SYSTEM_UI_FLAG_VISIBLE);
		}
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

	public int getActiveFragmentId() {
		return activeFragmentId;
	}

	public int getActiveNavItemId() {
		return activeNavItemId;
	}

	public void setActiveNavItemId(int activeNavItemId) {
		this.activeNavItemId = activeNavItemId;
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

	@NonNull
	private FragmentManager getSupportFragmentManager() {
		return getAppActivity().getSupportFragmentManager();
	}

	public void backToNavFragment() {
		int id = getActiveNavItemId();
		showFragment(id == Resources.ID_NULL ? R.id.nav_folders : id);
	}

	public void showFragment(@IdRes int id) {
		if (id == getActiveFragmentId()) return;

		FragmentManager fm = getSupportFragmentManager();
		FragmentTransaction tr = fm.beginTransaction();
		MainActivityFragment active = null;

		for (Fragment f : fm.getFragments()) {
			if (!(f instanceof MainActivityFragment)) continue;

			MainActivityFragment m = (MainActivityFragment) f;

			if (m.getFragmentId() == id) {
				tr.show(f);
				active = m;
			} else {
				tr.hide(f);
				m.discardSelection();
			}
		}

		if (active == null) {
			active = createFragment(id);
			Fragment f = (Fragment) active;
			tr.add(R.id.frame_layout, f);
			tr.show(f);
		}

		activeFragmentId = active.getFragmentId();
		tr.commitAllowingStateLoss();
		postBroadcastEvent(FRAGMENT_CHANGED);
	}

	private MainActivityFragment createFragment(int id) {
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
			default:
				throw new IllegalArgumentException("Invalid fragment id: " + id);
		}
	}

	@Nullable
	public MainActivityFragment getActiveFragment() {
		int id = getActiveFragmentId();
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if (f instanceof MainActivityFragment) {
				MainActivityFragment mf = (MainActivityFragment) f;
				if (mf.getFragmentId() == id) return mf;
			}
		}
		return null;
	}

	@Nullable
	public MediaLibFragment getActiveMediaLibFragment() {
		MainActivityFragment f = getActiveFragment();
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
			MainActivityFragment f = getActiveFragment();
			if (f instanceof MediaLibFragment) ((MediaLibFragment) f).revealItem(i);
		});

		return true;
	}

	public void setActiveMenu(AppMenu menu) {
		this.activeMenu = menu;
	}

	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if ((e.getAction() != MotionEvent.ACTION_DOWN) || (activeMenu == null)) return view.apply(e);
		activeMenu.hide();
		activeMenu = null;
		return true;
	}

	public AppMenu getContextMenu() {
		return (AppMenu) findViewById(R.id.context_menu);
	}

	public AppMenu getToolBarMenu() {
		return (AppMenu) findViewById(R.id.tool_menu);
	}

	public void initPlaylistMenu(AppMenu menu) {
		boolean visible = !isCarActivity() || !getMediaServiceBinder().getLib().getPlaylists()
				.getChildren().isEmpty();
		menu.findItem(R.id.playlist_add).setVisible(visible);
	}

	public void createPlaylistMenu(AppMenu menu) {
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
		Utils.queryText(getContext(), R.string.playlist_name, initName, name -> {
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
		MainActivityFragment f = getActiveFragment();
		if (f != null) f.discardSelection();
	}

	public void onBackPressed() {
		if (backPressed) {
			finish();
			return;
		}

		if (activeMenu != null) {
			activeMenu.hide();
			activeMenu = null;
			return;
		}

		if (getToolBar().onBackPressed()) return;

		MainActivityFragment f = getActiveFragment();

		if (f != null) {
			f.discardSelection();
			if (f.onBackPressed()) return;

			int navId = getActiveNavItemId();

			if ((f.getFragmentId() != navId) && (navId != Resources.ID_NULL)) {
				showFragment(navId);
				return;
			}
		}

		backPressed = true;
		Toast.makeText(getContext(), R.string.press_back_again, Toast.LENGTH_SHORT).show();
		FermataApplication.get().getHandler().postDelayed(() -> backPressed = false, 2000);
	}

	public void startActivityForResult(BiConsumer<Integer, Intent> resultHandler, Intent intent) {
		getAppActivity().startActivityForResult(resultHandler, intent);
	}

	private void init() {
		AppActivity a = getAppActivity();
		a.setContentView(a.getContentView());
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
			fireBroadcastEvent(MainActivityListener.Event.SERVICE_BOUND);

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
