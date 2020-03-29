package me.aap.fermata.media.lib;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.app.App;
import me.aap.utils.concurrent.CompletableFuture;
import me.aap.utils.concurrent.CompletedFuture;
import me.aap.utils.concurrent.ConcurrentUtils;
import me.aap.utils.concurrent.FutureSupplier;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Consumer;
import me.aap.utils.text.TextUtils;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;

/**
 * @author Andrey Pavlenko
 */
abstract class PlayableItemBase extends ItemBase implements PlayableItem, PlayableItemPrefs {
	protected volatile MediaMetadataCompat mediaData;
	private CompletableFuture<MediaMetadataCompat> loadMeta;
	protected String subtitle;

	public PlayableItemBase(String id, @NonNull BrowsableItem parent, @NonNull MediaFile file) {
		super(id, parent, file);
	}

	@NonNull
	@Override
	public String getSubtitle() {
		if (subtitle == null) {
			MediaMetadataCompat md = getMediaData();
			StringBuilder sb = TextUtils.getSharedStringBuilder();
			BrowsableItemPrefs prefs = requireNonNull(getParent()).getPrefs();
			String s;

			if (prefs.getSubtitleNamePref()) {
				s = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
				if ((s != null) && !s.isEmpty()) sb.append(s);
			}

			if (prefs.getSubtitleFileNamePref()) {
				if (sb.length() != 0) sb.append(" - ");
				sb.append(getFile().getName());
			}

			if (prefs.getSubtitleAlbumPref()) {
				s = md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
				if ((s != null) && !s.isEmpty()) {
					if (sb.length() != 0) sb.append(" - ");
					sb.append(s);
				}
			}

			if (prefs.getSubtitleArtistPref()) {
				s = md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
				if (s == null) s = md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
				if ((s != null) && !s.isEmpty()) {
					if (sb.length() != 0) sb.append(" - ");
					sb.append(s);
				}
			}

			if (prefs.getSubtitleDurationPref()) {
				long dur = getDuration();
				if (sb.length() != 0) sb.append(" - ");
				TextUtils.timeToString(sb, (int) (dur / 1000));
			}

			subtitle = sb.toString();
		}

		return subtitle;
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		assert super.getParent() != null;
		return super.getParent();
	}

	@Override
	public PlayableItemPrefs getPrefs() {
		return this;
	}

	@Override
	public MediaMetadataCompat getMediaData() {
		MediaMetadataCompat meta = mediaData;
		return (meta != null) ? meta : PlayableItem.super.getMediaData();
	}

	@Override
	public FutureSupplier<MediaMetadataCompat> getMediaData(@Nullable Consumer<MediaMetadataCompat> completionCallback) {
		MediaMetadataCompat meta = mediaData;

		if (meta != null) {
			consumeInMainThread(completionCallback, meta);
			return new CompletedFuture<>(meta);
		}

		String id = getOrigId();

		if (!id.equals(getId())) {
			PlayableItem i = (PlayableItem) getLib().getItem(id);

			if (i != null) {
				FutureSupplier<MediaMetadataCompat> f = i.getMediaData(completionCallback);
				f.addConsumer(m -> {
					if (mediaData == null) mediaData = m;
				});
				return f;
			}
		}

		boolean isNew;
		CompletableFuture<MediaMetadataCompat> load;

		synchronized (this) {
			if (loadMeta == null) {
				isNew = true;
				loadMeta = load = new CompletableFuture<>();
			} else {
				isNew = false;
				load = loadMeta;
			}
		}

		load.addConsumer(completionCallback, () -> new MediaMetadataCompat.Builder().build(),
				App.get().getHandler());

		if (isMainThread()) {
			if (isNew) App.get().getExecutor().submit(() -> loadMeta(load));
		} else {
			loadMeta(load);
		}

		CompletableFuture<MediaMetadataCompat> f = new CompletableFuture<>();
		load.addConsumer((BiConsumer<MediaMetadataCompat, Throwable>) f);
		return f;
	}

	private void loadMeta(CompletableFuture<MediaMetadataCompat> load) {
		if (load.isDone()) return;

		MediaMetadataCompat meta = getMediaMetadataBuilder().build();

		if (load.complete(meta)) {
			mediaData = meta;
			synchronized (this) {
				if (loadMeta == load) loadMeta = null;
			}
		}
	}

	MediaMetadataCompat.Builder getMediaMetadataBuilder() {
		ConcurrentUtils.ensureNotMainThread(true);
		MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
		Bitmap bm = getParent().getMediaDescription().getIconBitmap();
		if (bm != null) b.putBitmap(METADATA_KEY_ALBUM_ART, bm);
		return b;
	}

	@Override
	public boolean isMediaDataLoaded() {
		return mediaData != null;
	}

	@Override
	public void clearCache() {
		super.clearCache();
		subtitle = null;
	}

	@Override
	public void updateTitles() {
		super.updateTitles();
		subtitle = null;
	}
}
