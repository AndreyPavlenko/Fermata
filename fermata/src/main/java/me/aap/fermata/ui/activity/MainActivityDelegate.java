package me.aap.fermata.ui.activity;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static me.aap.fermata.BuildConfig.AUTO;
import static me.aap.fermata.ui.activity.MainActivityPrefs.BRIGHTNESS;
import static me.aap.fermata.ui.activity.MainActivityPrefs.CHANGE_BRIGHTNESS;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.Manifest.permission;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.MediaLibAddon;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.DefaultMediaLib;
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
import me.aap.fermata.ui.view.BodyLayout;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.function.Cancellable;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class MainActivityDelegate extends ActivityDelegate implements PreferenceStore.Listener {
	private final HandlerExecutor handler = new HandlerExecutor(App.get().getHandler().getLooper());
	private final NavBarMediator navBarMediator = new NavBarMediator();
	private final FermataServiceUiBinder mediaServiceBinder;
	private ToolBarView toolBar;
	private NavBarView navBar;
	private BodyLayout body;
	private ControlPanelView controlPanel;
	private FloatingButton floatingButton;
	private ContentLoadingProgressBar progressBar;
	private FutureSupplier<?> contentLoading;
	private boolean barsHidden;
	private boolean videoMode;
	private boolean recreating;
	private boolean exitPressed;
	private int brightness = 255;

	public MainActivityDelegate(AppActivity activity, FermataServiceUiBinder binder) {
		super(activity);
		mediaServiceBinder = binder;
	}

	@NonNull
	public static MainActivityDelegate get(Context ctx) {
		return (MainActivityDelegate) ActivityDelegate.get(ctx);
	}

	@Override
	public void onActivityCreate(@Nullable Bundle state) {
		super.onActivityCreate(state);
		getPrefs().addBroadcastListener(this);
		int navId;
		int fragmentId;

		if (state != null) {
			navId = state.getInt("navId", ID_NULL);
			fragmentId = state.getInt("fragmentId", ID_NULL);
		} else {
			navId = ID_NULL;
			fragmentId = ID_NULL;
		}

		AppActivity a = getAppActivity();
		FermataServiceUiBinder b = getMediaServiceBinder();
		Context ctx = a.getContext();
		b.getMediaSessionCallback().getSession().setSessionActivity(
				PendingIntent.getActivity(ctx, 0, new Intent(ctx, a.getClass()), FLAG_IMMUTABLE));
		if (b.getCurrentItem() == null) b.getMediaSessionCallback().onPrepare();
		init();

		String[] perms = getRequiredPermissions();
		a.checkPermissions(perms).onCompletion((result, fail) -> {
			if (fail != null) {
				if (!isCarActivity()) Log.e(fail);
			} else {
				Log.d("Requested permissions: ", Arrays.toString(perms), ". Result: " + Arrays.toString(result));
			}

			if (fragmentId != ID_NULL) {
				setActiveNavItemId(navId);
				showFragment(fragmentId);
				return;
			}

			String showAddon = getPrefs().getShowAddonOnStartPref();

			if (showAddon != null) {
				FermataAddon addon = AddonManager.get().getAddon(showAddon);

				if (addon != null) {
					showFragment(addon.getFragmentId());
					checkUpdates();
					return;
				}
			}

			FutureSupplier<Boolean> f = goToCurrent().onCompletion((ok, fail1) -> {
				if ((fail1 != null) && !isCancellation(fail1)) {
					Log.e(fail1, "Last played track not found");
				}
				checkUpdates();
			});

			if (!f.isDone() || f.isFailed() || !Boolean.TRUE.equals(f.peek())) {
				showFragment(R.id.folders_fragment);
				setContentLoading(f);
			}
		});
	}

	private void checkUpdates() {
		if (!AUTO || !getPrefs().getCheckUpdatesPref()) return;
		FermataActivity a = getAppActivity();
		if (a instanceof MainActivity) ((MainActivity) a).checkUpdates();
	}

	@Override
	protected void setUncaughtExceptionHandler() {
		if (!AUTO || getAppActivity().isCarActivity()) return;
		super.setUncaughtExceptionHandler();
	}

	@Override
	protected void onActivitySaveInstanceState(@NonNull Bundle outState) {
		super.onActivitySaveInstanceState(outState);
		if (recreating) {
			outState.putInt("navId", getActiveNavItemId());
			outState.putInt("fragmentId", getActiveFragmentId());
		}
	}

	public void recreate() {
		App.get().getHandler().post(() -> {
			Log.d("Recreating");
			recreating = true;
			getAppActivity().recreate();
		});
	}

	@Override
	public void onActivityResume() {
		super.onActivityResume();
	}

	@Override
	public void onActivityDestroy() {
		super.onActivityDestroy();
		handler.close();
		getPrefs().removeBroadcastListener(this);

		if (me.aap.utils.BuildConfig.D) {
			boolean leaks = ListenerLeakDetector.hasLeaks((b, l) -> {
				if (l instanceof FermataAddon) return false;
				if ((l instanceof DefaultMediaLib) && (b instanceof DefaultMediaLib)) return false;
				if ((l instanceof MediaEngineManager) && (b instanceof DefaultMediaLib)) return false;
				return (!(l instanceof AddonManager)) || (b != FermataApplication.get().getPreferenceStore());
			});
			if (leaks) throw new IllegalStateException("Listener leaks detected!");
		}
	}

	public void onActivityFinish() {
		super.onActivityFinish();
	}

	@NonNull
	@Override
	public FermataActivity getAppActivity() {
		return (FermataActivity) super.getAppActivity();
	}

	public boolean isCarActivity() {
		return AUTO && getAppActivity().isCarActivity();
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
	public static void setTheme(FermataActivity a) {
		switch (Prefs.instance.getThemePref(a.isCarActivity())) {
			default:
			case MainActivityPrefs.THEME_DARK:
				a.setTheme(R.style.AppTheme_Dark);
				break;
			case MainActivityPrefs.THEME_LIGHT:
				a.setTheme(R.style.AppTheme_Light);
				break;
			case MainActivityPrefs.THEME_DAY_NIGHT:
				AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_TIME);
				a.setTheme(R.style.AppTheme_DayNight);
				break;
			case MainActivityPrefs.THEME_BLACK:
				a.setTheme(R.style.AppTheme_Black);
				break;
		}
	}

	@Override
	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if (AUTO && (e.getAction() == MotionEvent.ACTION_DOWN)) {
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
		if (videoMode || getPrefs().getFullscreenPref(this)) {
			if (isCarActivity()) {
				FermataServiceUiBinder b = getMediaServiceBinder();
				return !b.getMediaSessionCallback().getPlaybackControlPrefs().getVideoAaShowStatusPref();
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	public boolean isGridView() {
		ActivityFragment f = getActiveFragment();

		if ((f instanceof MediaLibFragment) && ((MediaLibFragment) f).isGreedSupported()) {
			return getPrefs().getGridViewPref(this);
		} else {
			return false;
		}
	}

	@NonNull
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

	public ToolBarView getToolBar() {
		return toolBar;
	}

	@Override
	public float getToolBarSize() {
		return getPrefs().getToolBarSizePref(this);
	}

	public NavBarView getNavBar() {
		return navBar;
	}

	@Override
	public float getNavBarSize() {
		return getPrefs().getNavBarSizePref(this);
	}

	public BodyLayout getBody() {
		return body;
	}

	public NavBarMediator getNavBarMediator() {
		return navBarMediator;
	}


	public ControlPanelView getControlPanel() {
		return controlPanel;
	}

	public FloatingButton getFloatingButton() {
		return floatingButton;
	}

	@Override
	public float getTextIconSize() {
		return getPrefs().getTextIconSizePref(this);
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
		if (videoMode == this.videoMode) return;
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

		MainActivityPrefs p = getPrefs();

		if (p.getChangeBrightnessPref()) {
			if (videoMode) {
				brightness = getBrightness();
				setBrightness(p.getBrightnessPref());
			} else {
				setBrightness(brightness);
			}
		}
		if (p.getLandscapeVideoPref()) {
			if (videoMode) {
				getAppActivity().setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
			} else {
				getAppActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			}
		}

		fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	public int getBrightness() {
		return Settings.System.getInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, 255);
	}

	public void setBrightness(int br) {
		Settings.System.putInt(getContext().getContentResolver(), SCREEN_BRIGHTNESS, br);
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
		BodyLayout b = getBody();
		if (b.isVideoMode()) b.setMode(BodyLayout.Mode.BOTH);
		return super.showFragment(id, input);
	}

	protected ActivityFragment createFragment(int id) {
		if (id == R.id.folders_fragment) {
			return new FoldersFragment();
		} else if (id == R.id.favorites_fragment) {
			return new FavoritesFragment();
		} else if (id == R.id.playlists_fragment) {
			return new PlaylistsFragment();
		} else if (id == R.id.settings_fragment) {
			return new SettingsFragment();
		} else if (id == R.id.audio_effects_fragment) {
			return new AudioEffectsFragment();
		}
		ActivityFragment f = FermataApplication.get().getAddonManager().createFragment(id);
		return (f != null) ? f : super.createFragment(id);
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
			MediaLibAddon a = AddonManager.get().getMediaLibAddon(root);
			if (a != null) showFragment(a.getAddonId());
			else Log.d("Unsupported item: ", pi);
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
		addPlaylistMenu(builder, () -> selection, () -> "");
	}

	public void addPlaylistMenu(OverlayMenu.Builder builder,
															Supplier<FutureSupplier<List<PlayableItem>>> selection,
															Supplier<? extends CharSequence> initName) {
		builder.addItem(R.id.playlist_add, R.drawable.playlist_add, R.string.playlist_add)
				.setSubmenu(b -> createPlaylistMenu(b, selection, initName));
	}

	private void createPlaylistMenu(OverlayMenu.Builder b,
																	Supplier<FutureSupplier<List<PlayableItem>>> selection,
																	Supplier<? extends CharSequence> initName) {
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
			b.addItem(R.id.playlist_create, R.drawable.playlist_add, R.string.playlist_create)
					.setHandler(i -> createPlaylist(selection.get(), initName));

			for (int i = 0; i < playlists.size(); i++) {
				Playlist pl = (Playlist) playlists.get(i);
				String name = pl.getName();
				b.addItem(UiUtils.getArrayItemId(i), R.drawable.playlist, name)
						.setHandler(item -> addToPlaylist(name, selection.get()));
			}
		});
	}

	private boolean createPlaylist(FutureSupplier<List<PlayableItem>> selection, Supplier<? extends CharSequence> initName) {
		UiUtils.queryText(getContext(), R.string.playlist_name, initName.get()).onSuccess(name -> {
			discardSelection();
			if (name == null) return;

			getLib().getPlaylists().addItem(name)
					.onFailure(err -> showAlert(getContext(), err.getMessage()))
					.then(pl -> selection.main().then(items -> pl.addItems(items)
							.onFailure(err -> showAlert(getContext(), err.getMessage()))
							.thenRun(() -> {
								MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
								if (f != null) f.getAdapter().reload();
							}))
					);
		});
		return true;
	}

	private boolean addToPlaylist(String name, FutureSupplier<List<PlayableItem>> selection) {
		discardSelection();
		getLib().getPlaylists().getUnsortedChildren().main().onSuccess(playlists -> {
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
		});
		return true;
	}

	public void removeFromPlaylist(Playlist pl, List<PlayableItem> selection) {
		discardSelection();
		pl.removeItems(selection)
				.onFailure(err -> showAlert(getContext(), err.getMessage()))
				.thenRun(() -> {
					MediaLibFragment f = getMediaLibFragment(R.id.playlists_fragment);
					if (f != null) f.getAdapter().reload();
				});
	}

	private void discardSelection() {
		ActivityFragment f = getActiveFragment();
		if (f instanceof MainActivityFragment) ((MainActivityFragment) f).discardSelection();
	}

	@Override
	protected int getExitMsg() {
		return R.string.press_back_again;
	}

	private void init() {
		FermataActivity a = getAppActivity();
		a.setContentView(getLayout());
		toolBar = a.findViewById(R.id.tool_bar);
		progressBar = a.findViewById(R.id.content_loading_progress);
		navBar = a.findViewById(R.id.nav_bar);
		body = a.findViewById(R.id.body_layout);
		controlPanel = a.findViewById(R.id.control_panel);
		floatingButton = a.findViewById(R.id.floating_button);
		controlPanel.bind(getMediaServiceBinder());
	}

	@LayoutRes
	private int getLayout() {
		MainActivityPrefs prefs = getPrefs();

		switch (prefs.getNavBarPosPref(this)) {
			default:
				return R.layout.main_activity;
			case NavBarView.POSITION_LEFT:
				return R.layout.main_activity_left;
			case NavBarView.POSITION_RIGHT:
				return R.layout.main_activity_right;
		}
	}

	private static String[] getRequiredPermissions() {
		if (VERSION.SDK_INT >= VERSION_CODES.Q) {
			return new String[]{permission.READ_EXTERNAL_STORAGE, permission.FOREGROUND_SERVICE,
					permission.ACCESS_MEDIA_LOCATION, permission.USE_FULL_SCREEN_INTENT};
		} else if (VERSION.SDK_INT >= VERSION_CODES.P) {
			return new String[]{permission.READ_EXTERNAL_STORAGE, permission.FOREGROUND_SERVICE};
		} else {
			return new String[]{permission.READ_EXTERNAL_STORAGE};
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (MainActivityPrefs.hasThemePref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasNavBarPosPref(this, prefs)) {
			recreate();
		} else if (MainActivityPrefs.hasNavBarSizePref(this, prefs)) {
			if (navBar != null) navBar.setSize(getPrefs().getNavBarSizePref(this));
		} else if (MainActivityPrefs.hasToolBarSizePref(this, prefs)) {
			if (toolBar != null) toolBar.setSize(getPrefs().getToolBarSizePref(this));
		} else if (MainActivityPrefs.hasFullscreenPref(this, prefs)) {
			setSystemUiVisibility();
		} else if (prefs.contains(CHANGE_BRIGHTNESS)) {
			if (getPrefs().getChangeBrightnessPref()) {
				if (!Settings.System.canWrite(getContext())) {
					Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
					i.setData(Uri.parse("package:" + getContext().getPackageName()));
					startActivity(i);
				}
			}
		} else if (prefs.contains(BRIGHTNESS)) {
			if (isVideoMode()) setBrightness(getPrefs().getBrightnessPref());
		}
	}

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

	public Cancellable postDelayed(Runnable task, long delay) {
		return handler.schedule(task, delay);
	}

	static final class Prefs implements MainActivityPrefs {
		static final Prefs instance = new Prefs();
		private final List<ListenerRef<PreferenceStore.Listener>> listeners = new LinkedList<>();
		private final SharedPreferences prefs = FermataApplication.get().getDefaultSharedPreferences();

		private Prefs() {
			// Rename old prefs
			Pref<IntSupplier> oldTheme = Pref.i("THEME", THEME_DARK);
			Pref<DoubleSupplier> oldScale = Pref.f("MEDIA_ITEM_SCALE", 1f);
			int theme = getIntPref(oldTheme);
			float scale = getFloatPref(oldScale);

			if ((theme != THEME_DARK) || (scale != 1f)) {
				try (PreferenceStore.Edit e = editPreferenceStore()) {
					if (theme != THEME_DARK) {
						e.setIntPref(THEME_MAIN, theme);
						if (AUTO) e.setIntPref(THEME_AA, theme);
						e.removePref(oldTheme);
					}
					if (scale != 1f) {
						e.setFloatPref(TEXT_ICON_SIZE, scale);
						if (AUTO) e.setFloatPref(TEXT_ICON_SIZE_AA, scale);
						e.removePref(oldScale);
					}
				}
			}
		}

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
