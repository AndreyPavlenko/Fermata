package me.aap.fermata.ui.view;

import androidx.annotation.LayoutRes;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;

import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static me.aap.fermata.media.pref.MediaPrefs.VIDEO_SCALE;
import static me.aap.fermata.media.pref.PlayableItemPrefs.BOOKMARKS;
import static me.aap.fermata.ui.fragment.SettingsFragment.addAudioPrefs;
import static me.aap.fermata.ui.fragment.SettingsFragment.addDelayPrefs;
import static me.aap.fermata.ui.fragment.SettingsFragment.addSubtitlePrefs;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemMenuHandler implements OverlayMenu.SelectionHandler {
	private final OverlayMenu menu;
	private final Item item;
	private final MediaItemView view;

	public MediaItemMenuHandler(OverlayMenu menu, MediaItemView view) {
		this.menu = menu;
		this.view = view;
		this.item = view.getItem();
	}

	public MediaItemMenuHandler(OverlayMenu menu, Item item) {
		this.menu = menu;
		this.item = item;
		this.view = null;
	}

	public OverlayMenu getMenu() {
		return menu;
	}

	public Item getItem() {
		return item;
	}

	public void show(@LayoutRes int layout) {
		MainActivityDelegate a = getMainActivity();
		OverlayMenu menu = getMenu();
		menu.inflate(layout);

		if (item instanceof PlayableItem) {
			initPlayableMenu(a, menu, (PlayableItem) item, true);
		} else {
			initBrowsableMenu(a, menu, (BrowsableItem) item);
		}

		if (a.getMediaSessionCallback().getEngineManager().isExternalPlayerSupported()) {
			menu.findItem(R.id.preferred_media_engine).setVisible(true);
		}

		menu.show(this);
	}

	protected void initPlayableMenu(MainActivityDelegate a, OverlayMenu menu, PlayableItem pi,
																	boolean initRepeat) {
		boolean favorite = pi.isFavoriteItem();
		boolean hasBookmarks = pi.getPrefs().hasPref(BOOKMARKS);
		boolean playlist = (pi.getParent() instanceof Playlist);

		menu.findItem(R.id.favorites_add).setVisible(!favorite);
		menu.findItem(R.id.favorites_remove).setVisible(favorite);
		menu.findItem(R.id.create_bookmark).setVisible(!hasBookmarks);
		menu.findItem(R.id.bookmarks).setVisible(hasBookmarks);

		if (initRepeat) {
			boolean repeat = pi.isRepeatItemEnabled();
			menu.findItem(R.id.repeat_enable).setVisible(!repeat);
			menu.findItem(R.id.repeat_disable).setVisible(repeat);
		}

		if (pi.isVideo()) {
			menu.findItem(R.id.video_menu).setVisible(true);
		}

		if (playlist) menu.findItem(R.id.playlist_remove).setVisible(true);
		else a.initPlaylistMenu(menu);
	}

	protected void initBrowsableMenu(MainActivityDelegate a, OverlayMenu menu, BrowsableItem bi) {
		boolean playlist = (bi instanceof Playlist);
		boolean favorite = (bi.getParent() instanceof Favorites);
		boolean hasBookmarks = false;

		for (Item c : bi.getChildren(null)) {
			if ((c instanceof PlayableItem) && ((PlayableItem) c).getPrefs().hasPref(BOOKMARKS)) {
				hasBookmarks = true;
				break;
			}
		}

		menu.findItem(R.id.favorites_add).setVisible(!favorite);
		menu.findItem(R.id.bookmarks).setVisible(hasBookmarks);
		menu.findItem(R.id.video_menu).setVisible(true);
		if (!playlist) a.initPlaylistMenu(menu);
	}

	protected void initVideoMenu(OverlayMenu menu, Item item) {
		menu.findItem(R.id.video_scaling).setVisible(true);

		if ((item instanceof PlayableItem)) {
			PlayableItem pi = (PlayableItem) item;
			if (pi.getPrefs().getWatchedPref()) menu.findItem(R.id.mark_unwatched).setVisible(true);
			else menu.findItem(R.id.mark_watched).setVisible(true);
		}

		if (item.getLib().getPrefs().getVlcEnabledPref()) {
			if ((item instanceof PlayableItem)) {
				menu.findItem(R.id.audio_delay).setVisible(true);

				if (((PlayableItem) item).getPrefs().getSubEnabledPref()) {
					menu.findItem(R.id.subtitle_delay).setVisible(true);
				}
			} else if ((item instanceof BrowsableItem)) {
				menu.findItem(R.id.audio_prefs).setVisible(true);
				menu.findItem(R.id.subtitle_prefs).setVisible(true);
			}
		}
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem i) {
		int id = i.getItemId();
		MediaLibFragment f;
		Item item = getItem();
		OverlayMenu menu;
		List<PlayableItem> items;

		switch (id) {
			case R.id.favorites_add:
				if (item instanceof BrowsableItem) {
					items = ((BrowsableItem) item).getPlayableChildren(true);
					item.getLib().getFavorites().addItems(items);
				} else {
					item.getLib().getFavorites().addItem((PlayableItem) item);
				}

				f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				break;
			case R.id.favorites_remove:
				item.getLib().getFavorites().removeItem((PlayableItem) item);
				f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				break;
			case R.id.playlist_create:
				String initName = "";
				if (item instanceof BrowsableItem) {
					items = ((BrowsableItem) item).getPlayableChildren(true);
					initName = item.getName();
				} else {
					items = Collections.singletonList((PlayableItem) item);
				}
				getMainActivity().createPlaylist(items, initName);
				break;
			case R.id.playlist_add:
				menu = i.getMenu();
				getMainActivity().createPlaylistMenu(menu);
				menu.show(this);
				break;
			case R.id.playlist_add_item:
				items = (item instanceof BrowsableItem) ?
						((BrowsableItem) item).getPlayableChildren(true) :
						Collections.singletonList((PlayableItem) item);
				getMainActivity().addToPlaylist(i.getTitle().toString(), items);
				break;
			case R.id.playlist_remove:
				getMainActivity().removeFromPlaylist(
						(Playlist) requireNonNull(item.getParent()),
						Collections.singletonList((PlayableItem) item));
				break;
			case R.id.create_bookmark:
				showCreateBookmarkMenu(i.getMenu());
				break;
			case R.id.bookmarks:
				menu = i.getMenu();
				menu.setTitle(R.string.bookmarks);
				menu.addItem(R.id.bookmark_remove_all, R.string.remove_all_bookmarks);

				if (item instanceof BrowsableItem) {
					items = ((BrowsableItem) item).getPlayableChildren(true);
				} else {
					items = Collections.singletonList((PlayableItem) item);
					menu.addItem(R.id.create_bookmark, R.string.create_bookmark);
				}

				for (PlayableItem pi : items) {
					for (String b : pi.getPrefs().getBookmarks()) {
						String name = PlayableItemPrefs.bookmarkName(b);
						i = menu.addItem(R.id.bookmark_select, name);
						i.setData(new Bookmark(pi, name, PlayableItemPrefs.bookmarkTime(b)));
					}
				}

				menu.show(this);
				break;
			case R.id.bookmark_select:
				if (i.isLongClick()) {
					Bookmark b = i.getData();
					menu = i.getMenu();
					menu.hide();
					menu.setTitle(i.getTitle());
					i = menu.addItem(R.id.bookmark_remove, R.string.remove_bookmark);
					i.setData(b);
					menu.show(this);
				} else {
					Bookmark b = i.getData();
					getMainActivity().getMediaServiceBinder().playItem(b.item, b.time * 1000);
				}

				break;
			case R.id.bookmark_remove:
				Bookmark b = i.getData();
				b.item.getPrefs().removeBookmark(b.name, b.time);
				break;
			case R.id.bookmark_remove_all:
				menu = i.getMenu();
				menu.setTitle(R.string.bookmarks);
				menu.addItem(R.id.bookmark_remove_all_confirm, R.string.remove_all_bookmarks);
				menu.show(this);
				break;
			case R.id.bookmark_remove_all_confirm:
				items = (item instanceof BrowsableItem) ?
						((BrowsableItem) item).getPlayableChildren(true) :
						Collections.singletonList((PlayableItem) item);

				for (PlayableItem pi : items) {
					pi.getPrefs().removePref(BOOKMARKS);
				}

				break;
			case R.id.repeat_enable:
			case R.id.repeat_disable:
				((PlayableItem) item).setRepeatItemEnabled(id == R.id.repeat_enable);
				break;
			case R.id.mark_watched:
			case R.id.mark_unwatched:
				((PlayableItem) item).getPrefs().setWatchedPref(id == R.id.mark_watched);

				if (view != null) {
					view.refresh();
				} else {
					ActivityFragment mf = getMainActivity().getActiveFragment();
					if (mf instanceof MediaLibFragment) ((MediaLibFragment) mf).getAdapter().refresh();
				}

				break;
			case R.id.preferred_media_engine:
				menu = i.getMenu();
				menu.setTitle(R.string.preferred_media_engine);

				if (item instanceof BrowsableItem) {
					menu.addItem(R.id.preferred_audio_engine, R.string.preferred_audio_engine);
					menu.addItem(R.id.preferred_video_engine, R.string.preferred_video_engine);
				} else {
					createMediaPrefsMenu(item, menu, ((PlayableItem) item).isVideo());
				}

				menu.show(this);
				break;
			case R.id.preferred_audio_engine:
			case R.id.preferred_video_engine:
				menu = i.getMenu();
				menu.setTitle((id == R.id.preferred_audio_engine) ? R.string.preferred_audio_engine
						: R.string.preferred_video_engine);
				createMediaPrefsMenu(item, menu, (id == R.id.preferred_video_engine));
				menu.show(this);
				break;
			case R.id.preferred_audio_engine_default:
			case R.id.preferred_video_engine_default:
				item.getPrefs().removePref((id == R.id.preferred_audio_engine_default)
						? MediaPrefs.AUDIO_ENGINE : MediaPrefs.VIDEO_ENGINE);
				break;
			case R.id.preferred_audio_engine_mp:
				item.getPrefs().setAudioEnginePref(MediaPrefs.MEDIA_ENG_MP);
				break;
			case R.id.preferred_video_engine_mp:
				item.getPrefs().setVideoEnginePref(MediaPrefs.MEDIA_ENG_MP);
				break;
			case R.id.preferred_audio_engine_exo:
				item.getPrefs().setAudioEnginePref(MediaPrefs.MEDIA_ENG_EXO);
				break;
			case R.id.preferred_video_engine_exo:
				item.getPrefs().setVideoEnginePref(MediaPrefs.MEDIA_ENG_EXO);
				break;
			case R.id.preferred_audio_engine_vlc:
				item.getPrefs().setAudioEnginePref(MediaPrefs.MEDIA_ENG_VLC);
				break;
			case R.id.preferred_video_engine_vlc:
				item.getPrefs().setVideoEnginePref(MediaPrefs.MEDIA_ENG_VLC);
				break;
			case R.id.video_menu:
				initVideoMenu(i.getMenu(), item);
				break;
			case R.id.video_scaling:
				int scale = item.getPrefs().hasPref(VIDEO_SCALE, false) ? item.getPrefs().getVideoScalePref() : -1;
				menu = i.getMenu();
				menu.setTitle(R.string.video_scaling);
				menu.addItem(R.id.video_scaling_default, null, R.string.by_default).setChecked(scale == -1);
				menu.addItem(R.id.video_scaling_best, null, R.string.video_scaling_best).setChecked(scale == SCALE_BEST);
				menu.addItem(R.id.video_scaling_fill, null, R.string.video_scaling_fill).setChecked(scale == SCALE_FILL);
				menu.addItem(R.id.video_scaling_orig, null, R.string.video_scaling_orig).setChecked(scale == SCALE_ORIGINAL);
				menu.addItem(R.id.video_scaling_4, null, R.string.video_scaling_4).setChecked(scale == SCALE_4_3);
				menu.addItem(R.id.video_scaling_16, null, R.string.video_scaling_16).setChecked(scale == SCALE_16_9);
				menu.show(this);
				break;
			case R.id.video_scaling_default:
				item.getPrefs().removePref(VIDEO_SCALE);
				break;
			case R.id.video_scaling_best:
				item.getPrefs().setVideoScalePref(SCALE_BEST);
				break;
			case R.id.video_scaling_fill:
				item.getPrefs().setVideoScalePref(SCALE_FILL);
				break;
			case R.id.video_scaling_orig:
				item.getPrefs().setVideoScalePref(SCALE_ORIGINAL);
				break;
			case R.id.video_scaling_4:
				item.getPrefs().setVideoScalePref(SCALE_4_3);
				break;
			case R.id.video_scaling_16:
				item.getPrefs().setVideoScalePref(SCALE_16_9);
				break;
			case R.id.audio_delay:
				PreferenceSet prefSet = new PreferenceSet();
				menu = i.getMenu();
				addDelayPrefs(prefSet, item.getPrefs(), MediaLibPrefs.AUDIO_DELAY, R.string.audio_delay, null);
				prefSet.addToMenu(menu, true);
				menu.show();
				break;
			case R.id.subtitle_delay:
				prefSet = new PreferenceSet();
				menu = i.getMenu();
				addDelayPrefs(prefSet, item.getPrefs(), MediaLibPrefs.SUB_DELAY, R.string.subtitle_delay, null);
				prefSet.addToMenu(menu, true);
				menu.show();
				break;
			case R.id.audio_prefs:
				prefSet = new PreferenceSet();
				menu = i.getMenu();
				addAudioPrefs(prefSet, item.getPrefs(), getMainActivity().isCarActivity());
				menu.setTitle(R.string.audio);
				prefSet.addToMenu(menu, true);
				menu.show();
				break;
			case R.id.subtitle_prefs:
				prefSet = new PreferenceSet();
				menu = i.getMenu();
				addSubtitlePrefs(prefSet, item.getPrefs(), getMainActivity().isCarActivity());
				menu.setTitle(R.string.subtitles);
				prefSet.addToMenu(menu, true);
				menu.show();
				break;
		}

		return true;
	}

	private void createMediaPrefsMenu(Item item, OverlayMenu menu, boolean video) {
		MediaPrefs prefs = item.getPrefs();
		MediaEngineManager mgr = getMainActivity().getMediaSessionCallback().getEngineManager();
		Pref<IntSupplier> p = video ? MediaPrefs.VIDEO_ENGINE.withInheritance(false)
				: MediaPrefs.AUDIO_ENGINE.withInheritance(false);
		int eng = prefs.hasPref(p) ? (video ? prefs.getVideoEnginePref() : prefs.getAudioEnginePref()) : -1;

		OverlayMenuItem i = menu.addItem(video ? R.id.preferred_video_engine_default
				: R.id.preferred_audio_engine_default, null, R.string.by_default);
		i.setChecked(eng == -1);
		i = menu.addItem(video ? R.id.preferred_video_engine_mp
				: R.id.preferred_audio_engine_mp, null, R.string.engine_mp_name);
		i.setChecked(eng == MediaPrefs.MEDIA_ENG_MP);

		if (mgr.isExoPlayerSupported()) {
			i = menu.addItem(video ? R.id.preferred_video_engine_exo
					: R.id.preferred_audio_engine_exo, null, R.string.engine_exo_name);
			i.setChecked(eng == MediaPrefs.MEDIA_ENG_EXO);
		}

		if (mgr.isVlcPlayerSupported()) {
			i = menu.addItem(video ? R.id.preferred_video_engine_vlc
					: R.id.preferred_audio_engine_vlc, null, R.string.engine_vlc_name);
			i.setChecked(eng == MediaPrefs.MEDIA_ENG_VLC);
		}
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getMenu().getContext());
	}

	private void showCreateBookmarkMenu(OverlayMenu menu) {
		MediaEngine eng = getMainActivity().getMediaServiceBinder().getCurrentEngine();
		int pos = ((eng == null) || !item.equals(eng.getSource())) ? 0 : (int) (eng.getPosition() / 1000);
		PreferenceStore store = new BasicPreferenceStore();
		Pref<Supplier<String>> name = Pref.s("name", TextUtils.timeToString(pos));
		Pref<IntSupplier> time = Pref.i("time", pos);

		PreferenceSet set = new PreferenceSet();
		set.addStringPref(o -> {
			o.store = store;
			o.pref = name;
			o.title = R.string.bookmark_name;
		});
		set.addTimePref(o -> {
			o.store = store;
			o.pref = time;
			o.title = R.string.bookmark_time;
			o.editable = false;
			o.ems = 4;
			o.seekMax = (int) (((PlayableItem) item).getDuration() / 1000);
		});

		set.addToMenu(menu, true);
		menu.show(m -> true, m -> {
			PlayableItemPrefs prefs = ((PlayableItem) item).getPrefs();
			prefs.addBookmark(store.getStringPref(name), store.getIntPref(time));
		});
	}

	private static final class Bookmark {
		final PlayableItem item;
		final String name;
		final int time;

		public Bookmark(PlayableItem item, String name, int time) {
			this.item = item;
			this.name = name;
			this.time = time;
		}
	}
}
