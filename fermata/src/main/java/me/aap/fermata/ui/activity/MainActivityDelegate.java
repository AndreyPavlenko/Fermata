package me.aap.fermata.ui.activity;

import android.Manifest.permission;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.fragment.AudioEffectsFragment;
import me.aap.fermata.ui.fragment.FavoritesFragment;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.NavBarMediator;
import me.aap.fermata.ui.fragment.PlaylistsFragment;
import me.aap.fermata.ui.fragment.SettingsFragment;
import me.aap.fermata.ui.fragment.VideoFragment;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static me.aap.fermata.media.service.FermataMediaService.DEFAULT_NOTIF_COLOR;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.activity.ActivityListener.SERVICE_BOUND;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends ActivityDelegate implements
		PreferenceStore.Listener, FermataServiceUiBinder.Listener {
	private final NavBarMediator navBarMediator = new NavBarMediator();
	private FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private ContentLoadingProgressBar progressBar;
	private FutureSupplier<?> contentLoading;
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
			FermataServiceUiBinder.bind(FermataApplication.get(), notifColor, isCarActivity(),
					this::onMediaServiceBind);
		} else if (mediaServiceBinder != null) {
			onMediaServiceBind(mediaServiceBinder, null);
		} else {
			onMediaServiceBind(mediaServiceBinder, new IllegalStateException("Media service is not bound"));
		}
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
	}

	@Override
	protected void onActivityDestroy() {
		super.onActivityDestroy();

		toolBar = null;
		navBar = null;
		controlPanel = null;
		floatingButton = null;
		progressBar = null;
		contentLoading = null;
		barsHidden = false;
		videoMode = false;
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
	public FermataActivity getAppActivity() {
		return (FermataActivity) super.getAppActivity();
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
	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if (BuildConfig.AUTO && (e.getAction() == MotionEvent.ACTION_DOWN)) {
			FermataActivity a = getAppActivity();

			if (a.isInputActive()) {
				a.stopInput(null);
				return true;
			}
		}

		return super.interceptTouchEvent(e, view);
	}

	@Override
	public boolean isFullScreen() {
		if (videoMode || getPrefs().getFullscreenPref()) {
			if (isCarActivity()) {
				FermataServiceUiBinder b = getMediaServiceBinder();
				return (b == null) || !(b.getMediaSessionCallback().getPlaybackControlPrefs())
						.getVideoAaShowStatusPref();
			} else {
				return true;
			}
		} else {
			return false;
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

	public NavBarView getNavBar() {
		return navBar;
	}

	public NavBarMediator getNavBarMediator() {
		return navBarMediator;
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
		App.get().getHandler().post(() -> {
			this.barsHidden = barsHidden;
			int visibility = barsHidden ? GONE : VISIBLE;
			ToolBarView tb = getToolBar();
			if (tb.getMediator() != ToolBarView.Mediator.Invisible.instance) tb.setVisibility(visibility);
			getNavBar().setVisibility(visibility);
		});
	}

	public void setVideoMode(boolean videoMode, @Nullable VideoView v) {
		ControlPanelView cp = getControlPanel();

		if (videoMode) {
			this.videoMode = true;
			setSystemUiVisibility();
			getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
			cp.enableVideoMode(v);
		} else {
			this.videoMode = false;
			setSystemUiVisibility();
			getWindow().clearFlags(FLAG_KEEP_SCREEN_ON);
			if (cp != null) cp.disableVideoMode();
		}
	}

	public boolean isVideoMode() {
		return videoMode;
	}

	public void setContentLoading(FutureSupplier<?> contentLoading) {
		if (this.contentLoading != null) {
			this.contentLoading.cancel();
			this.contentLoading = null;
		}

		if (contentLoading.isDone()) return;

		this.contentLoading = contentLoading;
		contentLoading.onCompletion((r, f) -> App.get().run(() -> {
			if ((f != null) && !isCancellation(f)) Log.d(f);
			if (this.contentLoading == contentLoading) {
				this.contentLoading = null;
				progressBar.hide();
			}
		}));
		progressBar.show();
	}

	public void backToNavFragment() {
		int id = getActiveNavItemId();
		showFragment((id == ID_NULL) ? R.id.folders_fragment : id);
	}

	@Override
	protected int getFrameContainerId() {
		return R.id.frame_layout;
	}

	@Override
	public <F extends ActivityFragment> F showFragment(int id, Object input) {
		setVideoMode(false, null);
		return super.showFragment(id, input);
	}

	protected ActivityFragment createFragment(int id) {
		switch (id) {
			case R.id.folders_fragment:
				return new FoldersFragment();
			case R.id.favorites_fragment:
				return new FavoritesFragment();
			case R.id.playlists_fragment:
				return new PlaylistsFragment();
			case R.id.settings_fragment:
				return new SettingsFragment();
			case R.id.audio_effects_fragment:
				return new AudioEffectsFragment();
			case R.id.video:
				return new VideoFragment();
			default:
				ActivityFragment f = FermataApplication.get().getAddonManager().createFragment(id);
				return (f != null) ? f : super.createFragment(id);
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

	public FutureSupplier<Boolean> goToCurrent() {
		PlayableItem pi = getMediaServiceBinder().getCurrentItem();
		return (pi == null) ? getLib().getLastPlayedItem().main().map(this::goToItem)
				: completed(goToItem(pi));
	}

	private boolean goToItem(PlayableItem pi) {
		if (pi == null) return false;

		MediaLib.BrowsableItem root = pi.getRoot();

		if (root instanceof MediaLib.Folders) {
			showFragment(R.id.folders_fragment);
		} else if (root instanceof MediaLib.Favorites) {
			showFragment(R.id.favorites_fragment);
		} else if (root instanceof MediaLib.Playlists) {
			showFragment(R.id.playlists_fragment);
		} else {
			Log.d(new UnsupportedOperationException());
		}

		FermataApplication.get().getHandler().post(() -> {
			ActivityFragment f = getActiveFragment();
			if (f instanceof MediaLibFragment) ((MediaLibFragment) f).revealItem(pi);
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

	@Override
	public DialogBuilder createDialogBuilder(Context ctx) {
		return DialogBuilder.create(getContextMenu());
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder, FutureSupplier<List<PlayableItem>> selection) {
		addPlaylistMenu(builder, selection, () -> "");
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder, FutureSupplier<List<PlayableItem>> selection,
															Supplier<? extends CharSequence> initName) {
		builder.addItem(R.id.playlist_add, R.drawable.playlist_add, R.string.playlist_add)
				.setSubmenu(b -> createPlaylistMenu(b, selection, initName));
	}

	private void createPlaylistMenu(OverlayMenu.Builder b, FutureSupplier<List<PlayableItem>> selection,
																	Supplier<? extends CharSequence> initName) {
		List<Item> playlists = getLib().getPlaylists().getUnsortedChildren().getOrThrow();

		b.addItem(R.id.playlist_create, R.drawable.playlist_add, R.string.playlist_create)
				.setHandler(i -> createPlaylist(selection, initName));

		for (int i = 0; i < playlists.size(); i++) {
			Playlist pl = (Playlist) playlists.get(i);
			String name = pl.getName();
			b.addItem(UiUtils.getArrayItemId(i), R.drawable.playlist, name)
					.setHandler(item -> addToPlaylist(name, selection));
		}
	}

	private boolean createPlaylist(FutureSupplier<List<PlayableItem>> selection, Supplier<? extends CharSequence> initName) {
		UiUtils.queryText(getContext(), R.string.playlist_name, initName.get(), name -> {
			discardSelection();
			if (name == null) return;

			Playlist pl = getLib().getPlaylists().addItem(name);
			if (pl != null) {
				selection.main().onSuccess(items -> {
					pl.addItems(items);
					MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
					if (f != null) f.getAdapter().reload();
				});
			}
		});
		return true;
	}

	private boolean addToPlaylist(String name, FutureSupplier<List<PlayableItem>> selection) {
		discardSelection();
		for (Item i : getLib().getPlaylists().getUnsortedChildren().getOrThrow()) {
			Playlist pl = (Playlist) i;

			if (name.equals(pl.getName())) {
				selection.main().onSuccess(items -> {
					pl.addItems(items);
					MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
					if (f != null) f.getAdapter().reload();
				});
				break;
			}
		}
		return true;
	}

	public void removeFromPlaylist(Playlist pl, List<PlayableItem> selection) {
		discardSelection();
		pl.removeItems(selection);
		MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
		if (f != null) f.getAdapter().reload();
	}

	private void discardSelection() {
		ActivityFragment f = getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).discardSelection();
	}

	public FutureSupplier<Intent> startActivityForResult(Intent intent) {
		return getAppActivity().startActivityForResult(intent);
	}

	@Override
	protected int getExitMsg() {
		return R.string.press_back_again;
	}

	private void init() {
		FermataActivity a = getAppActivity();
		MainActivityPrefs prefs = getPrefs();
		@LayoutRes int layout;

		switch (a.isCarActivity() ? prefs.getNavBarPosAAPref() : prefs.getNavBarPosPref()) {
			default:
				layout = R.layout.main_activity;
				break;
			case NavBarView.POSITION_LEFT:
				layout = R.layout.main_activity_left;
				break;
			case NavBarView.POSITION_RIGHT:
				layout = R.layout.main_activity_right;
				break;
		}

		a.setContentView(layout);
		toolBar = a.findViewById(R.id.tool_bar);
		progressBar = a.findViewById(R.id.content_loading_progress);
		navBar = a.findViewById(R.id.nav_bar);
		controlPanel = a.findViewById(R.id.control_panel);
		floatingButton = a.findViewById(R.id.floating_button);
		controlPanel.bind(getMediaServiceBinder());

		SwipeRefreshLayout refresh = a.findViewById(R.id.swiperefresh);
		refresh.setOnRefreshListener(() -> {
			ActivityFragment f = getActiveFragment();
			if (f != null) f.onRefresh(refresh::setRefreshing);
		});
		refresh.setOnChildScrollUpCallback((p, c) -> {
			if (isMenuActive()) return true;
			ActivityFragment f = getActiveFragment();
			return (f != null) && f.canScrollUp();
		});
	}

	private void onMediaServiceBind(FermataServiceUiBinder b, Throwable err) {
		FermataActivity a = getAppActivity();

		if (b != null) {
			mediaServiceBinder = b;
			Context ctx = a.getContext();
			b.getMediaSessionCallback().getSession().setSessionActivity(
					PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()), 0));
			b.getMediaSessionCallback().onPrepare();
			init();

			String[] perms = getRequiredPermissions();
			a.checkPermissions(perms).onCompletion((result, fail) -> {
				if (fail != null) {
					if (!isCarActivity()) Log.e(fail);
				} else {
					Log.i("Requested permissions: " + Arrays.toString(perms) + ". Result: " + Arrays.toString(result));
				}

				fireBroadcastEvent(SERVICE_BOUND);

				FutureSupplier<Boolean> f = goToCurrent().onCompletion((ok, fail1) -> {
					if ((fail1 != null) && !isCancellation(fail1)) {
						Log.e(fail1, "Last played track not found");
					}
				});

				if (!f.isDone() || !f.peek()) {
					showFragment(R.id.folders_fragment);
					setContentLoading(f);
				}

				FermataApplication.get().getHandler().post(() -> {
					b.addBroadcastListener(this);
					onPlayableChanged(null, b.getCurrentItem());
				});
			});
		} else {
			Log.e(err);
			UiUtils.showAlert(getContext(), String.valueOf(err));
		}
	}

	private static String[] getRequiredPermissions() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			return new String[]{permission.READ_EXTERNAL_STORAGE, permission.FOREGROUND_SERVICE,
					permission.ACCESS_MEDIA_LOCATION, permission.USE_FULL_SCREEN_INTENT};
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			return new String[]{permission.READ_EXTERNAL_STORAGE, permission.FOREGROUND_SERVICE};
		} else {
			return new String[]{permission.READ_EXTERNAL_STORAGE};
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(MainActivityPrefs.THEME)) {
			setTheme();
			getAppActivity().recreate();
		} else if (prefs.contains(MainActivityPrefs.NAV_BAR_POS)) {
			getAppActivity().recreate();
		} else if (BuildConfig.AUTO && prefs.contains(MainActivityPrefs.NAV_BAR_POS_AA)) {
			FermataActivity a = getAppActivity();
			if (a.isCarActivity()) a.recreate();
		} else if (prefs.contains(MainActivityPrefs.FULLSCREEN)) {
			setSystemUiVisibility();
		}
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		if ((newItem != null) && !newItem.isExternal() && newItem.isVideo()) showFragment(R.id.video);
	}

	private boolean exitPressed;

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent, IntObjectFunction<KeyEvent, Boolean> next) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_ESCAPE:
				onBackPressed();
				return true;
			case KeyEvent.KEYCODE_M:
			case KeyEvent.KEYCODE_MENU:
				if (keyEvent.isShiftPressed()) {
					getNavBarMediator().showMenu(this);
				} else {
					ControlPanelView cp = getControlPanel();
					if (cp.isActive()) cp.showMenu();
					else getNavBarMediator().showMenu(this);
				}
				break;
			case KeyEvent.KEYCODE_P:
				getMediaServiceBinder().onPlayPauseButtonClick();
				if (isVideoMode()) getControlPanel().onVideoSeek();
				return true;
			case KeyEvent.KEYCODE_S:
			case KeyEvent.KEYCODE_DEL:
				getMediaServiceBinder().getMediaSessionCallback().onStop();
				return true;
			case KeyEvent.KEYCODE_X:
				if (exitPressed) {
					finish();
				} else {
					exitPressed = true;
					Toast.makeText(getContext(), R.string.press_x_again, Toast.LENGTH_SHORT).show();
					FermataApplication.get().getHandler().postDelayed(() -> exitPressed = false, 2000);
				}

				return true;
		}

		return super.onKeyDown(keyCode, keyEvent, next);
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
