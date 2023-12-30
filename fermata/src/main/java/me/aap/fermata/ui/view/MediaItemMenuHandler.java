package me.aap.fermata.ui.view;

import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_AUTO;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_DECODING;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_DISABLED;
import static me.aap.fermata.media.pref.MediaPrefs.HW_ACCEL_FULL;
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

import android.content.Context;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.MediaEngineManager;
import me.aap.fermata.media.lib.FileItem;
import me.aap.fermata.media.lib.FolderItem;
import me.aap.fermata.media.lib.M3uItem;
import me.aap.fermata.media.lib.MediaLib.ArchiveItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaLibPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemMenuHandler implements OverlayMenu.SelectionHandler {
	private final OverlayMenu menu;
	private final Item item;
	@Nullable
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

	@Nullable
	private MediaEngine getEngine() {
		return getMainActivity().getMediaServiceBinder().getCurrentEngine();
	}

	private FutureSupplier<Void> build(OverlayMenu.Builder builder) {
		FutureSupplier<Void> r;
		MainActivityDelegate a = getMainActivity();

		if (canDelete()) builder.addItem(R.id.delete, R.drawable.delete, R.string.delete);

		if (item instanceof PlayableItem) {
			buildPlayableMenu(a, builder, (PlayableItem) item, true);
			r = completedVoid();
		} else if (item instanceof BrowsableItem) {
			r = buildBrowsableMenu(a, builder, (BrowsableItem) item);
		} else {
			r = completedVoid();
		}

		return r.onSuccess(v -> {
			MediaLibFragment f = a.getActiveMediaLibFragment();
			if (f != null) f.contributeToContextMenu(builder, this);
			builder.setSelectionHandler(this);
		});
	}

	private boolean canDelete() {
		if (view == null) return false;
		BrowsableItem p = item.getParent();
		if ((p == null) || (p.getParent() == null)) return false;
		return (
				((item instanceof FileItem) || (item instanceof FolderItem) || (item instanceof M3uItem)) &&
						item.getResource().canDelete());
	}

	protected void buildPlayableMenu(MainActivityDelegate a, OverlayMenu.Builder b, PlayableItem pi,
																	 boolean initRepeat) {
		if (!pi.isExternal()) {
			if (initRepeat) {
				if (pi.isRepeatItemEnabled()) {
					b.addItem(R.id.repeat_disable, R.drawable.repeat_filled, R.string.repeat_disable);
				} else {
					b.addItem(R.id.repeat_enable, R.drawable.repeat, R.string.repeat);
				}
			}

			if (pi instanceof StreamItem) {
				b.addItem(R.id.programme_guide, R.drawable.epg, R.string.programme_guide);
			}

			if (pi.isFavoriteItem()) {
				b.addItem(R.id.favorites_remove, R.drawable.favorite_filled, R.string.favorites_remove);
			} else {
				b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);
			}

			if ((pi.getParent() instanceof Playlist)) {
				b.addItem(R.id.playlist_remove_item, R.drawable.playlist_remove,
						R.string.playlist_remove_item);
			} else {
				a.addPlaylistMenu(b, completed(Collections.singletonList(pi)));
			}

			if (!(item instanceof StreamItem) && !(item instanceof ArchiveItem)) {
				if (pi.getPrefs().hasPref(BOOKMARKS)) {
					b.addItem(R.id.bookmarks, R.drawable.bookmark_filled, R.string.bookmarks)
							.setFutureSubmenu(this::buildBookmarksMenu);
				} else {
					b.addItem(R.id.bookmark_create, R.drawable.bookmark, R.string.create_bookmark)
							.setSubmenu(this::buildCreateBookmarkMenu);
				}
			}
		}

		if (pi.isVideo() && addVideoMenu()) {
			b.addItem(R.id.video, R.drawable.video, R.string.video).setSubmenu(this::buildVideoMenu);
		}

		if (addSubtitlesMenu()) {
			b.addItem(R.id.subtitle_prefs, R.drawable.subtitles, R.string.subtitles)
					.setSubmenu(this::buildSubtitlesMenu);
		}

		if (addMediaEngMenu()) {
			b.addItem(R.id.preferred_media_engine, R.drawable.media_engine,
					R.string.preferred_media_engine).setSubmenu(this::buildVideoEngMenu);
		}
	}

	protected boolean addVideoMenu() {
		return true;
	}

	protected void buildVideoMenu(OverlayMenu.Builder b) {
		b.setSelectionHandler(this);

		if (!item.isExternal()) {
			PlayableItem pi = (PlayableItem) item;
			if (pi.getPrefs().getWatchedPref()) {
				b.addItem(R.id.mark_unwatched, R.string.mark_unwatched);
			} else {
				b.addItem(R.id.mark_watched, R.string.mark_watched);
			}
		}

		if (addAudioMenu()) {
			b.addItem(R.id.audio_prefs, R.string.audio).setSubmenu(this::buildAudioMenu);
		}

		b.addItem(R.id.video_scaling, R.string.video_scaling).setSubmenu(this::buildVideoScalingMenu);

		if (getMainActivity().getMediaSessionCallback().getEngineManager().isVlcPlayerSupported()) {
			b.addItem(R.id.video_scaling, R.string.hw_accel).setSubmenu(this::buildHwAccelMenu);
		}

		if (!item.isExternal()) {
			b.addItem(R.id.watched_threshold, R.string.watched_threshold)
					.setSubmenu(this::buildWatchedThresholdMenu);
		}
	}

	protected boolean addAudioMenu() {
		return item.getLib().getPrefs().getVlcEnabledPref();
	}

	protected void buildAudioMenu(OverlayMenu.Builder b) {
		if (!getMainActivity().getMediaSessionCallback().getEngineManager().isVlcPlayerSupported())
			return;
		PreferenceSet prefSet = new PreferenceSet();
		addDelayPrefs(prefSet, item.getPrefs(), MediaLibPrefs.AUDIO_DELAY, R.string.audio_delay, null);
		prefSet.addToMenu(b, true);
	}

	protected boolean addSubtitlesMenu() {
		return (item != null) && item.getPrefs().getSubEnabledPref();
	}

	protected void buildSubtitlesMenu(OverlayMenu.Builder b) {
		PreferenceSet prefSet = new PreferenceSet();
		addDelayPrefs(prefSet, item.getPrefs(), MediaLibPrefs.SUB_DELAY, R.string.subtitle_delay,
				null);
		prefSet.addToMenu(b, true);
	}

	protected boolean addMediaEngMenu() {
		return (view != null) && getMainActivity().getMediaSessionCallback().getEngineManager()
				.isAdditionalPlayerSupported();
	}

	private FutureSupplier<Void> buildBrowsableMenu(MainActivityDelegate a, OverlayMenu.Builder b,
																									BrowsableItem bi) {
		return bi.getUnsortedChildren().main().then(children -> {
			var hasAudio = false;
			var hasVideo = false;
			var hasWatched = false;
			var hasBookmarks = false;
			var parent = bi.getParent();

			for (var c : children) {
				if (c instanceof PlayableItem pi) {
					var prefs = pi.getPrefs();
					if (pi.isVideo()) {
						hasVideo = true;
						if (prefs.getWatchedPref()) hasWatched = true;
					} else {
						hasAudio = true;
					}
					if (prefs.hasPref(BOOKMARKS)) hasBookmarks = true;
					if (hasAudio && hasVideo && hasWatched && hasBookmarks) break;
				}
			}

			if (!(parent instanceof Favorites)) {
				b.addItem(R.id.favorites_add, R.drawable.favorite, R.string.favorites_add);

				if ((bi instanceof Playlist)) {
					b.addItem(R.id.playlist_remove, R.drawable.playlist_remove, R.string.playlist_remove)
							.setData(bi);
				}
			}

			if (parent instanceof Folders) {
				b.addItem(R.id.folders_remove, R.drawable.remove_folder, R.string.remove_folder);
			}

			if (hasBookmarks) {
				b.addItem(R.id.bookmarks, R.drawable.bookmark_filled, R.string.bookmarks)
						.setFutureSubmenu(this::buildBookmarksMenu);
			}
			if (!(bi instanceof Playlist)) {
				a.addPlaylistMenu(b, () -> bi.getPlayableChildren(true), bi::getName);
			}

			if (hasVideo) {
				var addUnwatched = hasWatched;
				b.addItem(R.id.video, R.drawable.video, R.string.video).setSubmenu(sb -> {
					sb.setSelectionHandler(this);
					sb.addItem(R.id.mark_watched, R.string.mark_watched);
					if (addUnwatched) sb.addItem(R.id.mark_unwatched, R.string.mark_unwatched);
					sb.addItem(R.id.audio_prefs, R.string.audio).setSubmenu(this::buildAudioPrefsMenu);
					sb.addItem(R.id.video_scaling, R.string.video_scaling)
							.setSubmenu(this::buildVideoScalingMenu);

					if (a.getMediaSessionCallback().getEngineManager().isVlcPlayerSupported()) {
						sb.addItem(R.id.video_scaling, R.string.hw_accel)
								.setSubmenu(this::buildHwAccelMenu);
					}

					sb.addItem(R.id.watched_threshold, R.string.watched_threshold)
							.setSubmenu(this::buildWatchedThresholdMenu);
				});
			}

			if (hasVideo || hasAudio) {
				b.addItem(R.id.subtitle_prefs, R.drawable.subtitles, R.string.subtitles)
						.setSubmenu(this::buildSubtitlePrefsMenu);
			}

			b.addItem(R.id.playback_settings, R.drawable.playback_settings, R.string.playback_settings)
					.setSubmenu(sb -> {
						sb.setSelectionHandler(this);
						sb.addItem(R.id.watched_threshold, R.string.watched_threshold)
								.setSubmenu(this::buildWatchedThresholdMenu);
						sb.addItem(R.id.play_next, R.string.play_next_on_completion)
								.setChecked(bi.getPrefs().getPlayNextPref());
					});

			if ((hasVideo || hasAudio) && addMediaEngMenu()) {
				if (hasAudio && hasVideo) {
					b.addItem(R.id.preferred_media_engine, R.drawable.media_engine,
							R.string.preferred_media_engine).setSubmenu(sb -> {
						sb.setSelectionHandler(this);
						sb.addItem(R.id.preferred_audio_engine, R.string.preferred_audio_engine)
								.setSubmenu(this::buildAudioEngMenu);
						sb.addItem(R.id.preferred_video_engine, R.string.preferred_video_engine)
								.setSubmenu(this::buildVideoEngMenu);
					});
				} else if (hasAudio) {
					b.addItem(R.id.preferred_audio_engine, R.drawable.media_engine,
							R.string.preferred_audio_engine).setSubmenu(this::buildAudioEngMenu);
				} else {
					b.addItem(R.id.preferred_video_engine, R.drawable.media_engine,
							R.string.preferred_video_engine).setSubmenu(this::buildVideoEngMenu);
				}
			}

			return completedVoid();
		});
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
		Pref<IntSupplier> p = video ? MediaPrefs.VIDEO_ENGINE.withInheritance(false) :
				MediaPrefs.AUDIO_ENGINE.withInheritance(false);
		int eng =
				prefs.hasPref(p) ? (video ? prefs.getVideoEnginePref() : prefs.getAudioEnginePref()) : -1;
		b.setSelectionHandler(this);

		OverlayMenuItem i =
				b.addItem(video ? R.id.preferred_video_engine_default :
								R.id.preferred_audio_engine_default,
						null, R.string.by_default);
		i.setChecked(eng == -1, true);
		i = b.addItem(video ? R.id.preferred_video_engine_mp : R.id.preferred_audio_engine_mp, null,
				R.string.engine_mp_name);
		i.setChecked(eng == MediaPrefs.MEDIA_ENG_MP, true);

		if (mgr.isExoPlayerSupported()) {
			i = b.addItem(video ? R.id.preferred_video_engine_exo : R.id.preferred_audio_engine_exo,
					null,
					R.string.engine_exo_name);
			i.setChecked(eng == MediaPrefs.MEDIA_ENG_EXO, true);
		}

		if (mgr.isVlcPlayerSupported()) {
			i = b.addItem(video ? R.id.preferred_video_engine_vlc : R.id.preferred_audio_engine_vlc,
					null,
					R.string.engine_vlc_name);
			i.setChecked(eng == MediaPrefs.MEDIA_ENG_VLC, true);
		}
	}

	private FutureSupplier<Void> buildBookmarksMenu(OverlayMenu.Builder b) {
		b.setSelectionHandler(this);
		b.addItem(R.id.bookmark_remove_all, R.string.remove_all_bookmarks).setSubmenu(s -> {
			s.setSelectionHandler(this);
			s.setTitle(R.string.bookmarks);
			s.addItem(R.id.bookmark_remove_all_confirm, R.string.remove_all_bookmarks);
		});

		if (item instanceof PlayableItem) {
			addBookmarks(b, Collections.singletonList((PlayableItem) item));
			b.addItem(R.id.bookmark_create, R.string.create_bookmark)
					.setSubmenu(this::buildCreateBookmarkMenu);
			return completedVoid();
		} else if (item instanceof BrowsableItem) {
			return ((BrowsableItem) item).getPlayableChildren(true).main().then(items -> {
				addBookmarks(b, items);
				return completedVoid();
			});
		} else {
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
				b.addItem(R.id.bookmark_remove, R.string.remove_bookmark).setHandler(mi -> {
					((PlayableItem) item).getPrefs().removeBookmark(bm.name, bm.time);
					return true;
				});
			});
		} else {
			Bookmark bm = i.getData();
			getMainActivity().getMediaServiceBinder().playItem(bm.item, bm.time * 1000L);
		}

		return true;
	}

	private void buildVideoScalingMenu(OverlayMenu.Builder b) {
		int scale =
				item.getPrefs().hasPref(VIDEO_SCALE, false) ? item.getPrefs().getVideoScalePref() : -1;
		b.addItem(R.id.video_scaling_default, null, R.string.by_default).setChecked(scale == -1, true);
		b.addItem(R.id.video_scaling_best, null, R.string.video_scaling_best)
				.setChecked(scale == SCALE_BEST, true);
		b.addItem(R.id.video_scaling_fill, null, R.string.video_scaling_fill)
				.setChecked(scale == SCALE_FILL, true);
		b.addItem(R.id.video_scaling_orig, null, R.string.video_scaling_orig)
				.setChecked(scale == SCALE_ORIGINAL, true);
		b.addItem(R.id.video_scaling_4, null, R.string.video_scaling_4)
				.setChecked(scale == SCALE_4_3, true);
		b.addItem(R.id.video_scaling_16, null, R.string.video_scaling_16)
				.setChecked(scale == SCALE_16_9, true);
		b.setSelectionHandler(this);
	}

	private void buildHwAccelMenu(OverlayMenu.Builder b) {
		int accel = item.getPrefs().getHwAccelPref();
		b.addItem(R.id.hw_accel_auto, null, R.string.hw_accel_auto)
				.setChecked(accel == HW_ACCEL_AUTO, true);
		b.addItem(R.id.hw_accel_full, null, R.string.hw_accel_full)
				.setChecked(accel == HW_ACCEL_FULL, true);
		b.addItem(R.id.hw_accel_decoding, null, R.string.hw_accel_decoding)
				.setChecked(accel == HW_ACCEL_DECODING, true);
		b.addItem(R.id.hw_accel_disabled, null, R.string.hw_accel_disabled)
				.setChecked(accel == HW_ACCEL_DISABLED, true);
		b.setSelectionHandler(this);
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

	private void buildWatchedThresholdMenu(OverlayMenu.Builder b) {
		PreferenceSet prefSet = new PreferenceSet();
		prefSet.addIntPref(o -> {
			o.store = item.getPrefs();
			o.pref = MediaPrefs.WATCHED_THRESHOLD;
			o.title = R.string.watched_threshold;
			o.subtitle = R.string.watched_threshold_sub;
			o.seekMin = 0;
			o.seekMax = 100;
			o.seekScale = 5;
		});
		prefSet.addToMenu(b, true);
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem i) {
		int id = i.getItemId();
		MediaLibFragment f;
		Item item = getItem();

		if (id == R.id.folders_remove) {
			Folders folders = item.getLib().getFolders();
			folders.removeItem(item);
		} else if (id == R.id.programme_guide) {
			ActivityFragment af = getMainActivity().getActiveFragment();
			if (af instanceof MediaLibFragment)
				((MediaLibFragment) af).getAdapter().openEpg((StreamItem) item);
		} else if (id == R.id.favorites_add) {
			if (item instanceof PlayableItem) {
				item.getLib().getFavorites().addItem((PlayableItem) item);
				f = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
				if (f != null) f.reload();
			} else if (item instanceof BrowsableItem) {
				((BrowsableItem) item).getPlayableChildren(true).main().onSuccess(list -> {
					item.getLib().getFavorites().addItems(list);
					MediaLibFragment mf = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
					if (mf != null) mf.reload();
				});
			}
		} else if (id == R.id.favorites_remove) {
			item.getLib().getFavorites().removeItem((PlayableItem) item);
			f = getMainActivity().getMediaLibFragment(R.id.favorites_fragment);
			if (f != null) f.reload();
		} else if (id == R.id.playlist_remove_item) {
			getMainActivity().removeFromPlaylist((Playlist) requireNonNull(item.getParent()),
					Collections.singletonList((PlayableItem) item));
		} else if (id == R.id.playlist_remove) {
			Playlist p = i.getData();
			p.getParent().removeItems(Collections.singletonList(p));
		} else if (id == R.id.bookmark_remove_all_confirm) {
			if ((item instanceof PlayableItem)) {
				((PlayableItem) item).getPrefs().removePref(BOOKMARKS);
			} else if ((item instanceof BrowsableItem)) {
				((BrowsableItem) item).getPlayableChildren(true).main().onSuccess(list -> {
					for (PlayableItem pi : list) {
						pi.getPrefs().removePref(BOOKMARKS);
					}
				});
			}
		} else if (id == R.id.repeat_enable || id == R.id.repeat_disable) {
			((PlayableItem) item).setRepeatItemEnabled(id == R.id.repeat_enable);
		} else if (id == R.id.play_next) {
			BrowsableItemPrefs brPrefs = ((BrowsableItem) item).getPrefs();
			brPrefs.setPlayNextPref(!brPrefs.getPlayNextPref());
		} else if (id == R.id.mark_watched || id == R.id.mark_unwatched) {
			boolean watched = id == R.id.mark_watched;

			if (item instanceof BrowsableItem br) {
				br.getPlayableChildren(false).onSuccess(list -> {
					for (var pi : list) pi.getPrefs().setWatchedPref(watched);
				});
			} else if (item instanceof PlayableItem pi) {
				pi.getPrefs().setWatchedPref(watched);
			}

			if (view != null) {
				view.refresh();
			} else {
				ActivityFragment mf = getMainActivity().getActiveFragment();
				if (mf instanceof MediaLibFragment) ((MediaLibFragment) mf).getAdapter().refresh();
			}
		} else if (id == R.id.preferred_audio_engine_default ||
				id == R.id.preferred_video_engine_default) {
			item.getPrefs().removePref(
					(id == R.id.preferred_audio_engine_default) ? MediaPrefs.AUDIO_ENGINE :
							MediaPrefs.VIDEO_ENGINE);
		} else if (id == R.id.preferred_audio_engine_mp) {
			item.getPrefs().setAudioEnginePref(MediaPrefs.MEDIA_ENG_MP);
		} else if (id == R.id.preferred_video_engine_mp) {
			item.getPrefs().setVideoEnginePref(MediaPrefs.MEDIA_ENG_MP);
		} else if (id == R.id.preferred_audio_engine_exo) {
			item.getPrefs().setAudioEnginePref(MediaPrefs.MEDIA_ENG_EXO);
		} else if (id == R.id.preferred_video_engine_exo) {
			item.getPrefs().setVideoEnginePref(MediaPrefs.MEDIA_ENG_EXO);
		} else if (id == R.id.preferred_audio_engine_vlc) {
			item.getPrefs().setAudioEnginePref(MediaPrefs.MEDIA_ENG_VLC);
		} else if (id == R.id.preferred_video_engine_vlc) {
			item.getPrefs().setVideoEnginePref(MediaPrefs.MEDIA_ENG_VLC);
		} else if (id == R.id.video_scaling_default) {
			item.getPrefs().removePref(VIDEO_SCALE);
		} else if (id == R.id.video_scaling_best) {
			item.getPrefs().setVideoScalePref(SCALE_BEST);
		} else if (id == R.id.video_scaling_fill) {
			item.getPrefs().setVideoScalePref(SCALE_FILL);
		} else if (id == R.id.video_scaling_orig) {
			item.getPrefs().setVideoScalePref(SCALE_ORIGINAL);
		} else if (id == R.id.video_scaling_4) {
			item.getPrefs().setVideoScalePref(SCALE_4_3);
		} else if (id == R.id.video_scaling_16) {
			item.getPrefs().setVideoScalePref(SCALE_16_9);
		} else if (id == R.id.hw_accel_auto) {
			item.getPrefs().setHwAccelPref(HW_ACCEL_AUTO);
		} else if (id == R.id.hw_accel_full) {
			item.getPrefs().setHwAccelPref(HW_ACCEL_FULL);
		} else if (id == R.id.hw_accel_decoding) {
			item.getPrefs().setHwAccelPref(HW_ACCEL_DECODING);
		} else if (id == R.id.hw_accel_disabled) {
			item.getPrefs().setHwAccelPref(HW_ACCEL_DISABLED);
		} else if (id == R.id.delete) {
			UiUtils.showQuestion(getContext(), R.string.delete_file_title, R.string.delete_file_question,
					R.drawable.delete).onSuccess(v -> {
				VirtualResource res = item.getResource();
				res.delete().main().onCompletion((deleted, err) -> {
					if ((err == null) && deleted) {
						MainActivityDelegate a = getMainActivity();
						MediaEngine eng = getEngine();
						if ((eng != null) && item.equals(eng.getSource()))
							a.getMediaSessionCallback().onSkipToNext();
						ActivityFragment mf = a.getActiveFragment();
						if (mf instanceof MediaLibFragment) ((MediaLibFragment) mf).refresh();
						return;
					}
					if (err != null) Log.e(err, "Failed to delete file " + res);
					Context ctx = getContext();
					UiUtils.showAlert(ctx, ctx.getString(R.string.delete_file_failed, res.getName()));
				});
			});
		}

		return true;
	}

	private Context getContext() {
		return getMenu().getContext();
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getMenu().getContext());
	}

	private void buildCreateBookmarkMenu(OverlayMenu.Builder b) {
		MediaEngine eng = getEngine();

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
