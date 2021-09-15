package me.aap.fermata.addon.tv.m3u;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.cancelled;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.concurrent.ConcurrentUtils.ensureMainThread;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.engine.MetadataBuilder;
import me.aap.fermata.media.lib.M3uTrackItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.StreamItemPrefs;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.ListenerLeakDetector;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uTrackItem extends M3uTrackItem implements StreamItem, StreamItemPrefs, TvItem {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final AtomicReferenceFieldUpdater<TvM3uTrackItem, FutureSupplier<List<TvM3uEpgItem>>> EPG =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(TvM3uTrackItem.class, FutureSupplier.class, "epg");
	static final int EPG_ID_UNKNOWN = -1;
	static final int EPG_ID_NOT_FOUND = -2;
	public static final String SCHEME = "tvm3ut";
	private final int catchUpDays;
	private int epgId = EPG_ID_UNKNOWN;
	private long epgStart;
	private long epgStop;
	private String epgTitle;
	private String epgDesc;
	private String epgChIcon;
	private String epgProgIcon;
	private List<Item.ChangeListener> listeners;
	private volatile FutureSupplier<List<TvM3uEpgItem>> epg;

	protected TvM3uTrackItem(String id, BrowsableItem parent, int trackNumber, VirtualResource file,
													 String name, String album, String artist, String genre,
													 String logo, String tvgId, String tvgName, long duration, byte type,
													 int catchUpDays) {
		super(id, parent, trackNumber, file, name, album, artist, genre, logo, tvgId, tvgName,
				duration, (type > 2) ? 2 : type);
		this.catchUpDays = catchUpDays;
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

	@NonNull
	@Override
	@SuppressWarnings("unchecked")
	public FutureSupplier<List<TvM3uEpgItem>> getEpg() {
		FutureSupplier<List<TvM3uEpgItem>> l = EPG.get(this);
		if (l != null) return l;

		Promise<List<TvM3uEpgItem>> load = new Promise<>();

		for (; !EPG.compareAndSet(this, null, load); l = EPG.get(this)) {
			if (l != null) return l;
		}

		FutureSupplier<XmlTv> f = getM3uItem().getXmlTv();

		if (f.isDone()) {
			XmlTv xml = f.peek();
			if ((xml == null) || xml.isClosed()) return epg = completedEmptyList();
			xml.getEpg(this).thenReplaceOrClear(EPG, this, load);
		} else {
			f.then(xml -> ((xml == null) || xml.isClosed()) ? completedEmptyList() : xml.getEpg(this))
					.thenReplaceOrClear(EPG, this, load);
		}

		l = EPG.get(this);
		return (l != null) ? l : load;
	}

	@NonNull
	@Override
	public StreamItemPrefs getPrefs() {
		return this;
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
		String desc = getEpgDesc();
		String icon = getEpgProgIcon();
		long start = epgStart;
		long stop = epgStop;
		long dur = (start > 0) && (start < stop) ? (stop - start) : 0;
		b.putString(METADATA_KEY_TITLE, getName());
		b.putString(METADATA_KEY_DISPLAY_SUBTITLE, SharedTextBuilder.apply(this::buildSubtitle));
		if (desc != null) b.putString(METADATA_KEY_DISPLAY_DESCRIPTION, desc);
		if (icon != null) b.putString(METADATA_KEY_DISPLAY_ICON_URI, icon);
		if (logo != null) b.setImageUri(logo);
		if (dur > 0) b.putLong(METADATA_KEY_DURATION, dur);
		return b.build();
	}

	@Override
	protected FutureSupplier<Bundle> buildExtras() {
		return getMediaData().map(m -> {
			long start = epgStart;
			long end = epgStop;
			if ((start <= 0) || (end < start)) return null;
			Bundle b = new Bundle();
			b.putLong(STREAM_START_TIME, start);
			b.putLong(STREAM_END_TIME, end);
			return b;
		});
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		String t = md.getString(METADATA_KEY_DISPLAY_SUBTITLE);
		return (t != null) ? t : buildSubtitle(tb);
	}

	private String buildSubtitle(SharedTextBuilder tb) {
		String t = getEpgTitle();
		if (!isNullOrBlank(t)) tb.append(t);

		if (requireNonNull(getParent()).getPrefs().getSubtitleDurationPref()) {
			long start = epgStart;
			long stop = epgStop;

			if ((start > 0) && (start < stop)) {
				if (tb.length() != 0) tb.append(". ");
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
		return validate(d);
	}

	@Override
	protected boolean isMediaDescriptionValid(FutureSupplier<MediaDescriptionCompat> d) {
		return validate(d);
	}

	private boolean validate(FutureSupplier<?> s) {
		return (s != null) && (!s.isDone() || (epgStop == 0) || (epgStop > System.currentTimeMillis()));
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

	@Override
	public boolean addChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<Item.ChangeListener> listeners = this.listeners;
		if (listeners == null) this.listeners = listeners = new LinkedList<>();
		else if (listeners.contains(l)) return true;
		listeners.add(l);
		if (BuildConfig.D) ListenerLeakDetector.add(this, l);
		return true;
	}

	@Override
	public boolean removeChangeListener(Item.ChangeListener l) {
		ensureMainThread(true);
		List<Item.ChangeListener> listeners = this.listeners;
		if ((listeners == null) || !listeners.remove(l)) return false;
		if (BuildConfig.D) ListenerLeakDetector.remove(this, l);
		return true;
	}

	@Override
	public boolean isSeekable() {
		return getCatchUpDays() > 0;
	}

	@Override
	public boolean isSeekable(long time) {
		int cd = getCatchUpDays();
		if (cd < 0) return false;
		long ct = System.currentTimeMillis();
		return (time <= ct) && (time >= (ct - (cd * 60000L * 60L * 24L)));
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

	int getCatchUpDays() {
		return (catchUpDays > 0) ? catchUpDays : getM3uItem().getResource().getCatchupDays();
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

	@Override
	protected void reset() {
		super.reset();
		epg = null;
	}

	private void notifyListeners() {
		List<Item.ChangeListener> listeners = this.listeners;
		if ((listeners != null) && !listeners.isEmpty()) {
			for (Item.ChangeListener l : listeners) l.mediaItemChanged(this);
		}
	}
}
