package me.aap.fermata.media.lib;

import static me.aap.utils.concurrent.ConcurrentUtils.ensureMainThread;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class ExportedItem extends PlayableItemBase {
	private final PlayableItemBase orig;
	private List<ListenerWrapper> listeners;

	private ExportedItem(PlayableItemBase orig, String exportId, BrowsableItem parent) {
		super(exportId, parent, parent.getResource());
		this.orig = orig;
		setMeta(orig.getMediaData());
	}

	public static ExportedItem create(PlayableItemBase orig, String exportId, BrowsableItem parent) {
		DefaultMediaLib lib = (DefaultMediaLib) parent.getLib();

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(exportId);

			if (i != null) {
				ExportedItem ex = (ExportedItem) i;
				assert parent.equals(ex.getParent());
				return ex;
			} else if (orig instanceof StreamItem) {
				return new ExportedStreamItem(orig, exportId, parent);
			} else {
				return new ExportedItem(orig, exportId, parent);
			}
		}
	}

	@Override
	public boolean addChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		ListenerWrapper w = new ListenerWrapper(l);
		if (!orig.addChangeListener(w)) return false;
		List<ListenerWrapper> listeners = this.listeners;
		if (listeners == null) this.listeners = listeners = new LinkedList<>();
		listeners.add(w);
		orig.addChangeListener(w);
		return true;
	}

	@Override
	public boolean removeChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<ListenerWrapper> listeners = this.listeners;
		if ((listeners == null) || listeners.isEmpty()) return false;

		for (Iterator<ListenerWrapper> it = listeners.iterator(); it.hasNext(); ) {
			ListenerWrapper w = it.next();

			if (w.getListener() == l) {
				it.remove();
				orig.removeChangeListener(w);
				return true;
			}
		}

		return false;
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return orig.getMediaData();
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return orig.buildTitle(seqNum, parentPrefs);
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return orig.buildSubtitle(md, tb);
	}

	@Override
	protected FutureSupplier<Bundle> buildExtras() {
		return orig.buildExtras();
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder meta) {
		return orig.buildMeta(meta);
	}

	@Override
	protected boolean isMediaDataValid(FutureSupplier<MediaMetadataCompat> d) {
		return orig.isMediaDataValid(d);
	}

	@Override
	protected boolean isMediaDescriptionValid(FutureSupplier<MediaDescriptionCompat> d) {
		return orig.isMediaDescriptionValid(d);
	}

	@Override
	public VirtualResource getResource() {
		return orig.getResource();
	}

	@Override
	public boolean isExternal() {
		return (orig != null) && orig.isExternal();
	}

	@Override
	@NonNull
	public PlayableItemPrefs getPrefs() {
		return orig.getPrefs();
	}

	@Override
	public boolean isVideo() {
		return orig.isVideo();
	}

	@Override
	public boolean isSeekable() {
		return orig.isSeekable();
	}

	@Override
	public boolean isStream() {
		return orig.isStream();
	}

	@Override
	@NonNull
	public PlayableItem export(String exportId, BrowsableItem parent) {
		return orig.export(exportId, parent);
	}

	@Override
	public String getOrigId() {
		return orig.getId();
	}

	@Override
	@NonNull
	public Uri getLocation() {
		return orig.getLocation();
	}

	@Override
	public boolean isNetResource() {
		return orig.isNetResource();
	}

	@Override
	@NonNull
	public FutureSupplier<Long> getDuration() {
		return orig.getDuration();
	}

	@Override
	@NonNull
	public FutureSupplier<Void> setDuration(long duration) {
		return orig.setDuration(duration);
	}

	@Override
	public boolean isTimerRequired() {
		return orig.isTimerRequired();
	}

	@NonNull
	@Override
	public String getName() {
		return orig.getName();
	}

	@DrawableRes
	@Override
	public int getIcon() {
		return orig.getIcon();
	}

	@Override
	@NonNull
	public FutureSupplier<Uri> getIconUri() {
		return orig.getIconUri();
	}

	@Override
	public long getOffset() {
		return orig.getOffset();
	}

	@Override
	@Nullable
	public String getUserAgent() {
		return orig.getUserAgent();
	}

	@NonNull
	@Override
	public String toString() {
		return getId();
	}

	@Override
	public int hashCode() {
		return getId().hashCode();
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof Item) {
			return getId().equals(((Item) obj).getId());
		} else {
			return false;
		}
	}

	public PlayableItemBase getOrig() {
		return orig;
	}

	private static final class ExportedStreamItem extends ExportedItem implements StreamItem {

		private ExportedStreamItem(PlayableItemBase orig, String exportId, BrowsableItem parent) {
			super(orig, exportId, parent);
		}

		@NonNull
		@Override
		public StreamItemPrefs getPrefs() {
			return getStream().getPrefs();
		}

		@Override
		public boolean isSeekable(long time) {
			return getStream().isSeekable(time);
		}

		@Nullable
		@Override
		public Uri getLocation(long time, long duration) {
			return getStream().getLocation(time, duration);
		}

		@NonNull
		@Override
		public <E extends MediaLib.EpgItem> FutureSupplier<List<E>> getEpg() {
			return getStream().getEpg();
		}

		private StreamItem getStream() {
			return (StreamItem) getOrig();
		}
	}

	public final class ListenerWrapper implements Item.ChangeListener {
		private final Item.ChangeListener listener;

		public ListenerWrapper(Item.ChangeListener listener) {
			this.listener = listener;
		}

		public ChangeListener getListener() {
			return listener;
		}

		@Override
		public void mediaItemChanged(Item i) {
			reset();
			listener.mediaItemChanged(ExportedItem.this);
		}
	}
}
