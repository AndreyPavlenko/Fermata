package me.aap.fermata.media.lib;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.util.concurrent.Future;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.concurrent.CompletedFuture;
import me.aap.utils.function.Consumer;
import me.aap.utils.text.TextUtils;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
abstract class PlayableItemBase extends ItemBase implements PlayableItem, PlayableItemPrefs {
	protected MediaMetadataCompat mediaData;
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

	public MediaMetadataCompat getMediaData() {
		if (mediaData == null) {
			mediaData = getMediaMetadataBuilder().build();
		}
		return mediaData;
	}

	@SuppressWarnings("unchecked")
	public Future<Void> getMediaData(Consumer<MediaMetadataCompat> consumer) {
		MediaMetadataCompat meta = mediaData;

		if (meta == null) {
			return (Future<Void>) FermataApplication.get().getExecutor().submit(() -> {
				MediaMetadataCompat m = getMediaMetadataBuilder().build();
				FermataApplication.get().getHandler().post(() -> {
					if (mediaData == null) mediaData = m;
					consumer.accept(mediaData);
				});
			});
		} else {
			consumer.accept(meta);
			return CompletedFuture.nullResult();
		}
	}

	public MediaMetadataCompat.Builder getMediaMetadataBuilder() {
		return new MediaMetadataCompat.Builder();
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
