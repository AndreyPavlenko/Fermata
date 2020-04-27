package me.aap.fermata.media.lib;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VirtualResource;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
abstract class PlayableItemBase extends ItemBase implements PlayableItem, PlayableItemPrefs {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final AtomicReferenceFieldUpdater<PlayableItemBase, FutureSupplier<MediaMetadataCompat>> meta =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(PlayableItemBase.class, FutureSupplier.class, "metaHolder");

	@Keep
	@SuppressWarnings("unused")
	private volatile FutureSupplier<MediaMetadataCompat> metaHolder;

	public PlayableItemBase(String id, @NonNull BrowsableItem parent, @NonNull VirtualResource file) {
		super(id, parent, file);
	}

	@NonNull
	@Override
	public BrowsableItem getParent() {
		return requireNonNull(super.getParent());
	}

	@NonNull
	@Override
	public PlayableItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public FutureSupplier<MediaMetadataCompat> getMediaData() {
		FutureSupplier<MediaMetadataCompat> m = meta.get(this);
		if (m != null) return m;

		Promise<MediaMetadataCompat> load = getLib().newPromise();

		for (; !meta.compareAndSet(this, null, load); m = meta.get(this)) {
			if (m != null) return m;
		}

		loadMeta().thenReplaceOrClear(meta, this, load);
		m = meta.get(this);
		return (m != null) ? m : load;
	}

	@NonNull
	@Override
	public FutureSupplier<Void> setDuration(long duration) {
		return getMediaData().then(md -> {
			MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
			b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);
			setMeta(completed(b.build()));
			return completedVoid();
		});
	}

	@NonNull
	FutureSupplier<MediaMetadataCompat> loadMeta() {
		return getLib().getMetadataRetriever().getMediaMetadata(this).then(this::buildMeta);
	}

	@NonNull
	FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		if (meta.getImageUri() == null) {
			return getParent().getIconUri().then(icon -> {
				if (icon != null) meta.setImageUri(icon.toString());
				return completed(meta.build());
			});
		} else {
			return completed(meta.build());
		}
	}

	void setMeta(MediaMetadataCompat m) {
		setMeta(completed(m));
	}

	void setMeta(FutureSupplier<MediaMetadataCompat> m) {
		meta.set(this, m);
		m.thenReplaceOrClear(meta, this);
	}

	void setMeta(MetadataBuilder mb) {
		FutureSupplier<MediaMetadataCompat> m = meta.get(this);
		if (m != null) return;

		m = buildMeta(mb);

		if (meta.compareAndSet(this, null, m)) m.thenReplace(meta, this);
		else m.cancel();
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return getMediaData().map(md -> {
			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				return buildSubtitle(md, tb);
			}
		});
	}

	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		if (parentPrefs.getTitleNamePref()) {
			return getMediaData().map(md -> {
				try (SharedTextBuilder tb = SharedTextBuilder.get()) {
					if (seqNum != 0) tb.append(seqNum).append(". ");
					tb.append(getTitle(md));
					if (parentPrefs.getTitleFileNamePref()) tb.append(" - ").append(getFile().getName());
					return tb.toString();
				}
			});
		} else {
			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				if (seqNum != 0) tb.append(seqNum).append(". ");
				tb.append(getFile().getName());
				return completed(tb.toString());
			}
		}
	}

	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		BrowsableItemPrefs prefs = requireNonNull(getParent()).getPrefs();
		String s;

		if (prefs.getSubtitleNamePref()) {
			tb.append(getTitle(md));
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
			long dur = md.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
			if (tb.length() != 0) tb.append(" - ");
			TextUtils.timeToString(tb, (int) (dur / 1000));
		}

		return tb.toString();
	}

	private String getTitle(MediaMetadataCompat md) {
		String title = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
		if ((title == null) || (title = title.trim()).isEmpty()) {
			title = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
			return ((title == null) || (title = title.trim()).isEmpty()) ? getFile().getName() : title;
		}
		return title;
	}
}
