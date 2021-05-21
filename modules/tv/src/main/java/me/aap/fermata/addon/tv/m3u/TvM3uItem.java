package me.aap.fermata.addon.tv.m3u;

import java.util.HashSet;
import java.util.Set;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.M3uGroupItem;
import me.aap.fermata.media.lib.M3uItem;
import me.aap.fermata.media.lib.M3uTrackItem;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_APPEND;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_DEFAULT;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_SHIFT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.concurrent.ConcurrentUtils.ensureMainThread;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uItem extends M3uItem implements TvItem, Runnable {
	public static final String SCHEME = "tvm3u";
	private String tvgUrl;
	private String catchUpSource;
	private int catchUpType = CATCHUP_TYPE_DEFAULT;
	private int catchUpDays;
	private Set<Runnable> updateHandlers;
	private boolean updaterScheduled;
	private final FutureRef<XmlTv> xmlTv = new FutureRef<XmlTv>() {
		@Override
		protected FutureSupplier<XmlTv> create() {
			return getData().get()
					.then(d -> XmlTv.create(TvM3uItem.this))
					.ifFail(err -> {
						Log.e(err, "Failed to load XMLTV: ", getEpgUrl());
						return null;
					});
		}
	};

	protected TvM3uItem(String id, BrowsableItem parent, TvM3uFile m3uFile) {
		super(id, parent, m3uFile);
		xmlTv.get();
	}

	public static TvM3uItem create(TvRootItem root, TvM3uFile m3u, int srcId) {
		String id = SharedTextBuilder.get().append(SCHEME).append(':').append(srcId).releaseString();
		return create(root, m3u, id);
	}

	public static TvM3uItem create(TvRootItem root, TvM3uFile m3u, String id) {
		DefaultMediaLib lib = root.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);

			if (i != null) {
				TvM3uItem c = (TvM3uItem) i;
				if (BuildConfig.D && !root.equals(c.getParent())) throw new AssertionError();
				if (BuildConfig.D && !m3u.equals(c.getResource())) throw new AssertionError();
				return c;
			} else {
				return new TvM3uItem(id, root, m3u);
			}
		}
	}

	public static FutureSupplier<TvM3uItem> create(TvRootItem root, int srcId, String m3uId) {
		DefaultMediaLib lib = root.getLib();
		String id = SharedTextBuilder.get().append(SCHEME).append(':').append(srcId).releaseString();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) return completed((TvM3uItem) i);
		}

		TvM3uFileSystem fs = TvM3uFileSystem.getInstance();
		Rid rid = fs.toRid(m3uId);
		return fs.getResource(rid).map(m3u -> (m3u != null) ? create(root, m3u, id) : null);
	}

	@Override
	protected String getScheme() {
		return SCHEME;
	}

	@Override
	protected M3uGroupItem createGroup(String idPath, String name, int groupId) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(TvM3uGroupItem.SCHEME).append(':').append(groupId).append(idPath);
		return new TvM3uGroupItem(tb.releaseString(), this, name, groupId);
	}

	@Override
	protected M3uTrackItem createTrack(BrowsableItem parent, int groupNumber, int trackNumber,
																		 String idPath, VirtualResource file, String name, String album,
																		 String artist, String genre, String logo, String tvgId,
																		 long duration, byte type) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(TvM3uTrackItem.SCHEME).append(':').append(groupNumber).append(':')
				.append(trackNumber).append(idPath);
		return new TvM3uTrackItem(tb.releaseString(), parent, trackNumber, file, name, album, artist,
				genre, logo, tvgId, duration, type);
	}

	FutureSupplier<XmlTv> getXmlTv() {
		return xmlTv.get().main();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	public FutureSupplier<TvM3uTrackItem> getTrack(int gid, int tid) {
		return super.getTrack(gid, tid).cast();
	}

	public FutureSupplier<TvM3uGroupItem> getGroup(int id) {
		return super.getGroup(id).cast();
	}

	@Override
	public TvM3uFile getResource() {
		return (TvM3uFile) super.getResource();
	}

	public String getEpgUrl() {
		String url = getResource().getEpgUrl();
		return ((url != null) && !url.isEmpty()) ? url : tvgUrl;
	}

	public String getCatchupQuery() {
		String q = getResource().getCatchupQuery();
		return (q == null) ? catchUpSource : q;
	}

	public int getCatchUpType() {
		int t = getResource().getCatchupType();
		return (t == CATCHUP_TYPE_DEFAULT) ? catchUpType : t;
	}

	public int getCatchUpDays() {
		int d = getResource().getCatchupDays();
		return (d == 0) ? catchUpDays : d;
	}

	protected void setTvgUrl(String url) {
		tvgUrl = url = url.trim();
		TvM3uFile f = getResource();
		if (f.getEpgUrl() == null) f.setEpgUrl(url);
	}

	protected void setCatchup(String catchup) {
		if (catchup != null) {
			switch (catchup) {
				case "default":
					catchUpType = CATCHUP_TYPE_DEFAULT;
					break;
				case "append":
					catchUpType = CATCHUP_TYPE_APPEND;
					break;
				case "shift":
					catchUpType = CATCHUP_TYPE_SHIFT;
					break;
			}
		}
	}

	protected void setCatchupSource(String src) {
		catchUpSource = src;
	}

	protected void setCatchupDays(String days) {
		if (days != null) {
			try {
				catchUpDays = Integer.parseInt(days);
			} catch (NumberFormatException ex) {
				Log.d(ex, "Invalid catchup-days: ", days);
			}
		}
	}

	long getTime() {
		return System.currentTimeMillis() + (long) (60000 * getResource().getEpgShift());
	}

	void addUpdateHandler(Runnable h) {
		ensureMainThread(true);
		Set<Runnable> u = updateHandlers;
		if (u == null) updateHandlers = u = new HashSet<>();
		u.add(h);
		scheduleUpdater();
	}

	void removeUpdateHandler(Runnable h) {
		ensureMainThread(true);
		Set<Runnable> u = updateHandlers;
		if (u != null) u.remove(h);
	}

	private void scheduleUpdater() {
		if (updaterScheduled) return;
		App.get().getHandler().postDelayed(this, 5000);
		updaterScheduled = true;
	}

	@Override
	public void run() {
		updaterScheduled = false;
		Set<Runnable> u = updateHandlers;
		if (!u.isEmpty()) {
			for (Runnable r : u) r.run();
			scheduleUpdater();
		}
	}
}
