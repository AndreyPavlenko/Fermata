package me.aap.fermata.media.lib;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.app.App;
import me.aap.utils.concurrent.CompletableFuture;
import me.aap.utils.function.Consumer;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.concurrent.ConcurrentUtils.consumeInMainThread;
import static me.aap.utils.concurrent.ConcurrentUtils.isMainThread;
import static me.aap.utils.concurrent.PriorityThreadPool.MAX_PRIORITY;

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

			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				BrowsableItemPrefs prefs = requireNonNull(getParent()).getPrefs();
				String s;

				if (prefs.getSubtitleNamePref()) {
					s = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
					if ((s != null) && !s.isEmpty()) tb.append(s);
				}

				if (prefs.getSubtitleFileNamePref()) {
					if (tb.length() != 0) tb.append(" - ");
					tb.append(getFile().getName());
				}

				if (prefs.getSubtitleAlbumPref()) {
					s = md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM);
					if ((s != null) && !s.isEmpty()) {
						if (tb.length() != 0) tb.append(" - ");
						tb.append(s);
					}
				}

				if (prefs.getSubtitleArtistPref()) {
					s = md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
					if (s == null) s = md.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST);
					if ((s != null) && !s.isEmpty()) {
						if (tb.length() != 0) tb.append(" - ");
						tb.append(s);
					}
				}

				if (prefs.getSubtitleDurationPref()) {
					long dur = getDuration();
					if (tb.length() != 0) tb.append(" - ");
					TextUtils.timeToString(tb, (int) (dur / 1000));
				}

				subtitle = tb.toString();
			}
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
	public void getMediaData(Consumer<MediaMetadataCompat> consumer) {
		MediaMetadataCompat meta = mediaData;

		if (meta != null) {
			consumeInMainThread(consumer, meta);
			return;
		}

		String id = getOrigId();

		if (!id.equals(getId())) {
			PlayableItem i = (PlayableItem) getLib().getItem(id);

			if (i != null) {
				Consumer<MediaMetadataCompat> c = m -> {
					mediaData = m;
					consumer.accept(m);
				};

				if (consumer.canBlockThread()) {
					CompletableFuture<MediaMetadataCompat> f = new CompletableFuture<>();
					f.addConsumer(c, () -> new MediaMetadataCompat.Builder().build(), App.get().getHandler());
					i.getMediaData(f);
				} else {
					i.getMediaData(c);
				}

				return;
			}
		}

		CompletableFuture<MediaMetadataCompat> load;

		synchronized (this) {
			if (loadMeta == null) loadMeta = load = new CompletableFuture<>();
			else load = loadMeta;
		}

		if (load.setRunning()) {
			if (isMainThread() && !consumer.canBlockThread()) {
				load.addConsumer(consumer, this::incomplete, App.get().getHandler());
				FermataApplication.get().getExecutor().submit(MAX_PRIORITY, () -> loadMeta(load));
			} else {
				consumeInMainThread(consumer, loadMeta(load));
			}
		} else if (load.isDone()) {
			consumeInMainThread(consumer, load.get(this::incomplete));
		} else {
			load.addConsumer(consumer, this::incomplete, App.get().getHandler());
		}
	}

	private MediaMetadataCompat loadMeta(CompletableFuture<MediaMetadataCompat> load) {
		if (!load.isDone()) {
			MediaMetadataCompat meta = getMediaMetadataBuilder().build();

			if (load.complete(meta)) {
				mediaData = meta;
				synchronized (this) {
					if (loadMeta == load) loadMeta = null;
				}
				return meta;
			}
		}

		return load.get(() -> getMediaMetadataBuilder().build());
	}

	private MediaMetadataCompat incomplete() {
		return new MediaMetadataCompat.Builder().build();
	}

	@Override
	Consumer<MediaDescriptionCompat.Builder> buildIncompleteDescription(MediaDescriptionCompat.Builder b) {
		if (mediaData != null) {
			BrowsableItem p = getParent();

			if ((p instanceof BrowsableItemBase) && ((BrowsableItemBase<?>) p).isChildrenLoaded()) {
				buildCompleteDescription(b);
				return null;
			}
		}

		return super.buildIncompleteDescription(b);
	}

	@Override
	void buildCompleteDescription(MediaDescriptionCompat.Builder b) {
		super.buildCompleteDescription(b);
		MediaMetadataCompat meta = getMediaData();
		Bitmap bm = meta.getBitmap(METADATA_KEY_ALBUM_ART);

		if (bm != null) {
			b.setIconBitmap(bm);
			return;
		} else {
			String uri = meta.getString(METADATA_KEY_ALBUM_ART_URI);

			if (uri != null) {
				b.setIconUri(Uri.parse(uri));
				return;
			}
		}

		BrowsableItem p = getParent();
		if (!(p instanceof BrowsableItemBase)) return;
		Uri uri = ((BrowsableItemBase<?>) p).getIconUri();
		if (uri != null) b.setIconUri(uri);
	}

	MediaMetadataCompat.Builder getMediaMetadataBuilder() {
		MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
		setParentData(b);
		return b;
	}

	void setParentData(MediaMetadataCompat.Builder b) {
		BrowsableItem p = getParent();
		if (!(p instanceof BrowsableItemBase)) return;
		Uri uri = ((BrowsableItemBase<?>) p).getIconUri();
		if (uri != null) b.putString(METADATA_KEY_ALBUM_ART_URI, uri.toString().intern());
	}

	public void setMediaData(MediaMetadataCompat mediaData) {
		this.mediaData = mediaData;
		CompletableFuture<MediaMetadataCompat> load = null;

		synchronized (this) {
			if (loadMeta != null) {
				load = loadMeta;
				loadMeta = null;
			}
		}

		if (load != null) load.complete(mediaData);
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
