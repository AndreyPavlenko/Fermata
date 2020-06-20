package me.aap.fermata.ui.view;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.async.FutureSupplier;
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
import static me.aap.fermata.media.pref.PlayableItemPrefs.bookmarkTime;
import static me.aap.fermata.ui.fragment.SettingsFragment.addAudioPrefs;
import static me.aap.fermata.ui.fragment.SettingsFragment.addDelayPrefs;
import static me.aap.fermata.ui.fragment.SettingsFragment.addSubtitlePrefs;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;

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

	public void show() {
		menu.showFuture(this::build);
	}

	public FutureSupplier<Void> build(OverlayMenu.Builder builder) {
		MainActivityDelegate a = getMainActivity();
		builder.setSelectionHandler(this);

		if (item instanceof PlayableItem) {
			buildPlayableMenu(a, builder, (PlayableItem) item, true);
			return completedVoid();
		} else {
			return buildBrowsableMenu(a, builder, (BrowsableItem) item);
		}
	}

	protected void buildPlayableMenu(MainActivityDelegate a, OverlayMenu.Builder b, PlayableItem pi,
																	 boolean initRepeat) {
		if (pi.isExternal()) return;

		if (pi.isFavoriteItem()) {
			b.addItem(R.id.favorites_remove, R.drawable.favorite_filled, R.string.favorites_remove);
		} else {
			b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);
		}

		if (pi.getPrefs().hasPref(BOOKMARKS)) {
			b.addItem(R.id.bookmarks, R.drawable.bookmark_filled, R.string.bookmarks).setFutureSubmenu(this::buildBookmarksMenu);
		} else {
			b.addItem(R.id.bookmark_create, R.drawable.bookmark, R.string.create_bookmark).setSubmenu(this::buildCreateBookmarkMenu);
		}

		if (initRepeat) {
			if (pi.isRepeatItemEnabled()) {
				b.addItem(R.id.repeat_disable, R.drawable.repeat_filled, R.string.repeat_disable);
			} else {
				b.addItem(R.id.repeat_enable, R.drawable.repeat, R.string.repeat);
			}
		}

		if (pi.isVideo()) {
			b.addItem(R.id.video, R.drawable.video, R.string.video).setSubmenu(this::buildVideoMenu);
		}

		if ((pi.getParent() instanceof Playlist)) {
			b.addItem(R.id.playlist_remove_item, R.drawable.playlist_remove, R.string.playlist_remove_item);
		} else {
			a.addPlaylistMenu(b, completed(Collections.singletonList(pi)));
		}

		addMediaEngineMenu(a, b);
	}

	private FutureSupplier<Void> buildBrowsableMenu(MainActivityDelegate a, OverlayMenu.Builder b, BrowsableItem bi) {
		return bi.getUnsortedChildren().main().then(children -> {
			boolean hasBookmarks = false;
			BrowsableItem parent = bi.getParent();

			for (Item c : children) {
				if ((c instanceof PlayableItem) && ((PlayableItem) c).getPrefs().hasPref(BOOKMARKS)) {
					hasBookmarks = true;
					break;
				}
			}

			if (!(parent instanceof Favorites)) {
				b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);

				if ((bi instanceof Playlist)) {
					b.addItem(R.id.playlist_remove, R.drawable.playlist_remove, R.string.playlist_remove).setData(bi);
				}
			}

			if (parent instanceof Folders) {
				b.addItem(R.id.folders_remove, R.drawable.remove_folder, R.string.remove_folder);
			}

			if (hasBookmarks) {
				b.addItem(R.id.bookmarks, R.drawable.bookmark_filled, R.string.bookmarks).setFutureSubmenu(this::buildBookmarksMenu);
			}
			if (!(bi instanceof Playlist)) {
				a.addPlaylistMenu(b, bi.getPlayableChildren(true), bi::getName);
			}

			b.addItem(R.id.playback_settings, R.drawable.playback_settings, R.string.playback_settings)
					.setSubmenu(sb -> sb.addItem(R.id.play_next, R.string.play_next_on_completion)
							.setChecked(bi.getPrefs().getPlayNextPref()).setHandler(this));

			addMediaEngineMenu(a, b);
			return completedVoid();
		});
	}

	private void addMediaEngineMenu(MainActivityDelegate a, OverlayMenu.Builder builder) {
		if (a.getMediaSessionCallback().getEngineManager().isAdditionalPlayerSupported()) {
			builder.addItem(R.id.preferred_media_engine, R.drawable.media_engine,
					R.string.preferred_media_engine).setSubmenu(this::buildMediaEngineMenu);
		}
	}

	protected void buildMediaEngineMenu(OverlayMenu.Builder b) {
		b.setSelectionHandler(this);

		if (item instanceof BrowsableItem) {
			b.addItem(R.id.preferred_audio_engine, R.string.preferred_audio_engine).setSubmenu(this::buildAudioEngMenu);
			b.addItem(R.id.preferred_video_engine, R.string.preferred_video_engine).setSubmenu(this::buildVideoEngMenu);
		} else {
			buildMediaEngMenu(b, ((PlayableItem) item).isVideo());
		}
	}

	private void buildAudioEngMenu(OverlayMenu.Builder b) {
		buildMediaEngMenu(b, false);
	}

	private void buildVideoEngMenu(OverlayMenu.Builder b) {
		buildMediaEngMenu(b, true);
	}

	private void buildMediaEngMenu(OverlayMenu.Builder b, boolean video) {
		MediaPrefs prefs = item.getPrefs();
		MediaEngineManager mgr = getMainActivity().getMediaSessionCallback().getEngineManager();
		Pref<IntSupplier> p = video ? MediaPrefs.VIDEO_ENGINE.withInheritance(false)
				: MediaPrefs.AUDIO_ENGINE.withInheritance(false);
		int eng = prefs.hasPref(p) ? (video ? prefs.getVideoEnginePref() : prefs.getAudioEnginePref()) : -1;
		b.setSelectionHandler(this);

		OverlayMenuItem i = b.addItem(video ? R.id.preferred_video_engine_default
				: R.id.preferred_audio_engine_default, null, R.string.by_default);
		i.setChecked(eng == -1, true);
		i = b.addItem(video ? R.id.preferred_video_engine_mp
				: R.id.preferred_audio_engine_mp, null, R.string.engine_mp_name);
		i.setChecked(eng == MediaPrefs.MEDIA_ENG_MP, true);

		if (mgr.isExoPlayerSupported()) {
			i = b.addItem(video ? R.id.preferred_video_engine_exo
					: R.id.preferred_audio_engine_exo, null, R.string.engine_exo_name);
			i.setChecked(eng == MediaPrefs.MEDIA_ENG_EXO, true);
		}

		if (mgr.isVlcPlayerSupported()) {
			i = b.addItem(video ? R.id.preferred_video_engine_vlc
					: R.id.preferred_audio_engine_vlc, null, R.string.engine_vlc_name);
			i.setChecked(eng == MediaPrefs.MEDIA_ENG_VLC, true);
		}
	}

	protected void buildVideoMenu(OverlayMenu.Builder b) {
		b.setSelectionHandler(this);

		if ((item instanceof PlayableItem)) {
			PlayableItem pi = (PlayableItem) item;

			if (pi.getPrefs().getWatchedPref()) {
				b.addItem(R.id.mark_unwatched, R.string.mark_unwatched);
			} else {
				b.addItem(R.id.mark_watched, R.string.mark_watched);
			}
		}

		b.addItem(R.id.video_scaling, R.string.video_scaling).setSubmenu(this::buildVideoScalingMenu);

		if (item.getLib().getPrefs().getVlcEnabledPref()) {
			if ((item instanceof PlayableItem)) {
				b.addItem(R.id.audio_delay, R.string.audio_delay).setSubmenu(this::buildAudioDelayMenu);

				if (((PlayableItem) item).getPrefs().getSubEnabledPref()) {
					b.addItem(R.id.subtitle_delay, R.string.subtitle_delay).setSubmenu(this::buildSubtitleDelayMenu);
				}
			} else if ((item instanceof BrowsableItem)) {
				b.addItem(R.id.audio_prefs, R.string.audio).setSubmenu(this::buildAudioPrefsMenu);
				b.addItem(R.id.subtitle_prefs, R.string.subtitles).setSubmenu(this::buildSubtitlePrefsMenu);
			}
		}
	}

	private FutureSupplier<Void> buildBookmarksMenu(OverlayMenu.Builder b) {
		b.setSelectionHandler(this);
		b.addItem(R.id.bookmark_remove_all, R.string.remove_all_bookmarks).setSubmenu(s -> {
			s.setSelectionHandler(this);
			s.setTitle(R.string.bookmarks);
			s.addItem(R.id.bookmark_remove_all_confirm, R.string.remove_all_bookmarks);
		});

		if (item instanceof BrowsableItem) {
			return ((BrowsableItem) item).getPlayableChildren(true).main().then(items -> {
				addBookmarks(b, items);
				return completedVoid();
			});
		} else {
			addBookmarks(b, Collections.singletonList((PlayableItem) item));
			b.addItem(R.id.bookmark_create, R.string.create_bookmark).setSubmenu(this::buildCreateBookmarkMenu);
			return completedVoid();
		}
	}

	private void addBookmarks(OverlayMenu.Builder b, List<PlayableItem> items) {
		for (PlayableItem pi : items) {
			for (String bm : pi.getPrefs().getBookmarks()) {
				String name = PlayableItemPrefs.bookmarkName(bm);
				b.addItem(R.id.bookmark_select, name).setData(new Bookmark(pi, name, bookmarkTime(bm)))
						.setHandler(this::bookmarkSelected);
			}
		}
	}

	private boolean bookmarkSelected(OverlayMenuItem i) {
		if (i.isLongClick()) {
			Bookmark bm = i.getData();
			i.getMenu().show(b -> {
				b.setTitle(i.getTitle());
				b.addItem(R.id.bookmark_remove, R.string.remove_bookmark)
						.setHandler(mi -> {
							((PlayableItem) item).getPrefs().removeBookmark(bm.name, bm.time);
							return true;
						});
			});
		} else {
			Bookmark bm = i.getData();
			getMainActivity().getMediaServiceBinder().playItem(bm.item, bm.time * 1000);
		}

		return true;
	}

	private void buildVideoScalingMenu(OverlayMenu.Builder b) {
		int scale = item.getPrefs().hasPref(VIDEO_SCALE, false) ? item.getPrefs().getVideoScalePref() : -1;
		b.addItem(R.id.video_scaling_default, null, R.string.by_default).setChecked(scale == -1, true);
		b.addItem(R.id.video_scaling_best, null, R.string.video_scaling_best).setChecked(scale == SCALE_BEST, true);
		b.addItem(R.id.video_scaling_fill, null, R.string.video_scaling_fill).setChecked(scale == SCALE_FILL, true);
		b.addItem(R.id.video_scaling_orig, null, R.string.video_scaling_orig).setChecked(scale == SCALE_ORIGINAL, true);
		b.addItem(R.id.video_scaling_4, null, R.string.video_scaling_4).setChecked(scale == SCALE_4_3, true);
		b.addItem(R.id.video_scaling_16, null, R.string.video_scaling_16).setChecked(scale == SCALE_16_9, true);
		b.setSelectionHandler(this);
	}

	private void buildAudioDelayMenu(OverlayMenu.Builder b) {
		PreferenceSet prefSet = new PreferenceSet();
		addDelayPrefs(prefSet, item.getPrefs(), MediaLibPrefs.AUDIO_DELAY, R.string.audio_delay, null);
		prefSet.addToMenu(b, true);
	}

	private void buildSubtitleDelayMenu(OverlayMenu.Builder b) {
		PreferenceSet prefSet = new PreferenceSet();
		addDelayPrefs(prefSet, item.getPrefs(), MediaLibPrefs.SUB_DELAY, R.string.subtitle_delay, null);
		prefSet.addToMenu(b, true);
	}

	private void buildAudioPrefsMenu(OverlayMenu.Builder b) {
		PreferenceSet prefSet = new PreferenceSet();
		addAudioPrefs(prefSet, item.getPrefs(), getMainActivity().isCarActivity());
		b.setTitle(R.string.audio);
		prefSet.addToMenu(b, true);
	}

	private void buildSubtitlePrefsMenu(OverlayMenu.Builder b) {
		PreferenceSet prefSet = new PreferenceSet();
		addSubtitlePrefs(prefSet, item.getPrefs(), getMainActivity().isCarActivity());
		b.setTitle(R.string.subtitles);
		prefSet.addToMenu(b, true);
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem i) {
		int id = i.getItemId();
		MediaLibFragment f;
		Item item = getItem();

		switch (id) {
			case R.id.folders_remove:
				Folders folders = item.getLib().getFolders();
				folders.removeItem(item);
				break;
			case R.id.favorites_add:
				if (item instanceof BrowsableItem) {
					((BrowsableItem) item).getPlayableChildren(true).main().onSuccess(list -> {
						item.getLib().getFavorites().addItems(list);
						MediaLibFragment mf = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
						if (mf != null) mf.reload();
					});
				} else {
					item.getLib().getFavorites().addItem((PlayableItem) item);
					f = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
					if (f != null) f.reload();
				}

				break;
			case R.id.favorites_remove:
				item.getLib().getFavorites().removeItem((PlayableItem) item);
				f = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
				if (f != null) f.reload();
				break;
			case R.id.playlist_remove_item:
				getMainActivity().removeFromPlaylist(
						(Playlist) requireNonNull(item.getParent()),
						Collections.singletonList((PlayableItem) item));
				break;
			case R.id.playlist_remove:
				Playlist p = i.getData();
				p.getParent().removeItems(Collections.singletonList(p));
				break;
			case R.id.bookmark_remove_all_confirm:
				if ((item instanceof BrowsableItem)) {
					((BrowsableItem) item).getPlayableChildren(true).main().onSuccess(list -> {
						for (PlayableItem pi : list) {
							pi.getPrefs().removePref(BOOKMARKS);
						}
					});
				} else {
					((PlayableItem) item).getPrefs().removePref(BOOKMARKS);
				}

				break;
			case R.id.repeat_enable:
			case R.id.repeat_disable:
				((PlayableItem) item).setRepeatItemEnabled(id == R.id.repeat_enable);
				break;
			case R.id.play_next:
				BrowsableItemPrefs brPrefs = ((BrowsableItem) item).getPrefs();
				brPrefs.setPlayNextPref(!brPrefs.getPlayNextPref());
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
		}

		return true;
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getMenu().getContext());
	}

	private void buildCreateBookmarkMenu(OverlayMenu.Builder b) {
		MediaEngine eng = getMainActivity().getMediaServiceBinder().getCurrentEngine();

		if ((eng != null) && item.equals(eng.getSource())) {
			eng.getPosition().onSuccess(pos -> buildCreateBookmarkMenu(b, (int) (pos / 1000)));
		} else {
			buildCreateBookmarkMenu(b, 0);
		}
	}

	private void buildCreateBookmarkMenu(OverlayMenu.Builder b, int pos) {
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
			o.seekMax = (int) (((PlayableItem) item).getDuration().get(() -> 0L) / 1000);
		});

		set.addToMenu(b, true);
		b.setCloseHandlerHandler(m -> {
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
