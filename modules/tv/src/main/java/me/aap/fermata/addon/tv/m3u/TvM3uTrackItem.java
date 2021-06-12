package me.aap.fermata.addon.tv.m3u;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.M3uTrackItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VirtualResource;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.cancelled;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.concurrent.ConcurrentUtils.ensureMainThread;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uTrackItem extends M3uTrackItem implements TvItem, Runnable {
	static final int EPG_ID_UNKNOWN = -1;
	static final int EPG_ID_NOT_FOUND = -2;
	public static final String SCHEME = "tvm3ut";
	private int epgId = EPG_ID_UNKNOWN;
	private long epgStart;
	private long epgStop;
	private String epgTitle;
	private String epgDesc;
	private String epgChIcon;
	private String epgProgIcon;
	private List<Item.ChangeListener> listeners;

	protected TvM3uTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file,
													 String name, String album, String artist, String genre,
													 String logo, String tvgId, String tvgName, long duration, byte type) {
		super(id, parent, trackNumber, file, name, album, artist, genre, logo, tvgId, tvgName,
				duration, (type > 2) ? 2 : type);
	}

	public static FutureSupplier<TvM3uTrackItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		start = end + 1;
		end = id.indexOf(':', start);
		int tid = Integer.parseInt(id.substring(start, end));
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(TvM3uItem.SCHEME).append(id, end, id.length());
		FutureSupplier<? extends Item> f = root.getItem(TvM3uItem.SCHEME, tb.releaseString());
		return (f == null) ? completedNull() : f.then(i -> {
			TvM3uItem m3u = (TvM3uItem) i;
			return (m3u != null) ? m3u.getTrack(gid, tid) : completedNull();
		});
	}

	@Override
	protected TvM3uItem getM3uItem() {
		return (TvM3uItem) super.getM3uItem();
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		return buildMeta(new MetadataBuilder());
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> buildMeta(MetadataBuilder b) {
		FutureSupplier<XmlTv> f = getM3uItem().getXmlTv();
		FutureSupplier<Void> u;

		if (f.isDone()) {
			XmlTv xml = f.peek();
			if ((xml == null) || xml.isClosed()) return completed(build(b));
			u = xml.update(this);
		} else {
			u = f.then(xml -> {
				if ((xml == null) || xml.isClosed()) return cancelled();
				return xml.update(this);
			});
		}

		FutureSupplier<MediaMetadataCompat> m = u.main().map(v -> build(new MetadataBuilder()));
		if (m.isDone()) return m;

		m.onSuccess(meta -> {
			setMeta(completed(meta));
			notifyListeners();
		});

		return completed(build(b));
	}

	private MediaMetadataCompat build(MetadataBuilder b) {
		String logo = getLogo();
		long start = epgStart;
		long stop = epgStop;
		long dur = (stop > start) ? (stop - start) : 0;
		b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, getName());
		b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, dur);
		if (logo != null) b.setImageUri(logo);
		return b.build();
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		String t = epgTitle;
		if (t == null) return "";

		if (requireNonNull(getParent()).getPrefs().getSubtitleDurationPref()) {
			long start = epgStart;
			long stop = epgStop;

			if ((start > 0) && (start < stop)) {
				tb.append(t).append(". ");
				TextUtils.dateToTimeString(tb, start, false);
				tb.append(" - ");
				TextUtils.dateToTimeString(tb, stop, false);
				return tb.toString();
			}
		}

		return t;
	}

	@Override
	protected boolean isMediaDataValid(FutureSupplier<MediaMetadataCompat> d) {
		return (d != null) && (!d.isDone() || (epgStop == 0) || (epgStop > System.currentTimeMillis()));
	}

	@Override
	protected boolean isMediaDescriptionValid(FutureSupplier<MediaDescriptionCompat> d) {
		return (d != null) && (!d.isDone() || (epgStop == 0) || (epgStop > System.currentTimeMillis()));
	}

	@Override
	protected String getLogo() {
		String logo = super.getLogo();

		if (logo == null) {
			TvM3uFile f = getM3uItem().getResource();
			if (f.isPreferEpgLogo()) logo = epgChIcon;

			if (logo == null) {
				logo = f.getLogoUrl();
				if (logo != null) logo = logo + '/' + getName() + ".png";
			}
		}

		return logo;
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@NonNull
	@Override
	public FutureSupplier<Integer> getProgress() {
		return getMediaData().map(d -> {
			long start = epgStart;
			long stop = epgStop;
			if ((start == 0) || (start >= stop)) return 0;
			long dur = stop - start;
			long prog = getM3uItem().getTime() - start;
			return (int) (prog * 100 / dur);
		});
	}

	@Override
	public boolean addChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<Item.ChangeListener> listeners = this.listeners;
		if (listeners == null) this.listeners = listeners = new LinkedList<>();
		else if (listeners.contains(l)) return true;
		listeners.add(l);
		if (BuildConfig.D) ListenerLeakDetector.add(this, l);

		if (listeners.size() == 1) {
			if (getEpgId() >= 0) {
				getM3uItem().addUpdateHandler(this);
			} else {
				getMediaData().main().thenRun(() -> {
					if ((getEpgId() >= 0) && !this.listeners.isEmpty()) {
						getM3uItem().addUpdateHandler(this);
					}
				});
			}
		}

		return true;
	}

	@Override
	public boolean removeChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<Item.ChangeListener> listeners = this.listeners;
		if ((listeners == null) || !listeners.remove(l)) return false;
		if (listeners.isEmpty()) getM3uItem().removeUpdateHandler(this);
		if (BuildConfig.D) ListenerLeakDetector.remove(this, l);
		return true;
	}

	@Override
	public void run() {
		if (!isMediaDataValid(getMediaData())) {
			reset();
			getMediaData();
		} else {
			List<Item.ChangeListener> listeners = this.listeners;

			if ((listeners != null) && !listeners.isEmpty()) {
				for (Item.ChangeListener l : listeners) {
					if (l instanceof PlayableItem.ChangeListener) {
						((PlayableItem.ChangeListener) l).playableItemProgressChanged(this);
					}
				}
			}
		}
	}

	int getEpgId() {
		return epgId;
	}

	long getEpgStart() {
		return epgStart;
	}

	long getEpgStop() {
		return epgStop;
	}

	String getEpgTitle() {
		return epgTitle;
	}

	String getEpgDesc() {
		return epgDesc;
	}

	String getEpgChIcon() {
		return epgChIcon;
	}

	public String getEpgProgIcon() {
		return epgProgIcon;
	}

	void update(int epgId, String epgIcon, long epgStart, long epgStop, String epgTitle,
							String epgDesc, String epgProgIcon, boolean force) {
		App.get().run(() -> {
			this.epgId = epgId;
			this.epgChIcon = epgIcon;
			this.epgStart = epgStart;
			this.epgStop = epgStop;
			this.epgTitle = epgTitle;
			this.epgDesc = epgDesc;
			this.epgProgIcon = epgProgIcon;

			if (force) {
				reset();
				notifyListeners();
			}
		});
	}

	private void notifyListeners() {
		List<Item.ChangeListener> listeners = this.listeners;

		if ((listeners != null) && !listeners.isEmpty()) {
			for (Item.ChangeListener l : listeners) {
				if (l instanceof PlayableItem.ChangeListener) {
					((PlayableItem.ChangeListener) l).playableItemChanged(this);
				} else {
					l.mediaItemChanged(this);
				}
			}
		}
	}
}
