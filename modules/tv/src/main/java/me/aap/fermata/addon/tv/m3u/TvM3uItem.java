package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_APPEND;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_AUTO;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_DEFAULT;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_FLUSSONIC;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_SHIFT;
import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.Nullable;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.M3uGroupItem;
import me.aap.fermata.media.lib.M3uItem;
import me.aap.fermata.media.lib.M3uTrackItem;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uItem extends M3uItem implements TvItem {
	public static final String SCHEME = "tvm3u";
	private String tvgUrl;
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
		tb.append(TvM3uGroupItem.SCHEME).append(':').append(groupId).append(idPath).append(':').append(name);
		return new TvM3uGroupItem(tb.releaseString(), this, name, groupId);
	}

	@Override
	protected M3uTrackItem createTrack(BrowsableItem parent, int groupNumber, int trackNumber,
																		 String idPath, VirtualResource file, String name, String album,
																		 String artist, String genre, String logo, String tvgId,
																		 String tvgName, long duration, byte type,
																		 String catchup, String catchupDays, String catchupSource) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(TvM3uTrackItem.SCHEME).append(':').append(groupNumber).append(':')
				.append(trackNumber).append(idPath).append(':').append(file.getRid());
		int ct = CATCHUP_TYPE_AUTO;
		int cd = -1;

		if (catchup != null) {
			switch (catchup) {
				case "default":
					ct = CATCHUP_TYPE_DEFAULT;
					break;
				case "append":
					ct = CATCHUP_TYPE_APPEND;
					break;
				case "shift":
					ct = CATCHUP_TYPE_SHIFT;
					break;
				case "flussonic":
					ct = CATCHUP_TYPE_FLUSSONIC;
					break;
			}
		}
		if (catchupDays != null) {
			try {
				cd = Integer.parseInt(catchupDays);
			} catch (NumberFormatException ex) {
				Log.e(ex, "Invalid catchup days: ", catchupDays);
			}
		}

		return new TvM3uTrackItem(tb.releaseString(), parent, trackNumber, file, name, album, artist,
				genre, logo, tvgId, tvgName, duration, type, ct, cd, catchupSource);
	}

	@Override
	protected String createSubtitle(int gr, int ch) {
		Context ctx = dynCtx(getLib().getContext());
		Resources res = ctx.getResources();
		if (ch != 0) {
			if (gr == 0) return res.getString(R.string.sub_ch, ch);
		} else if (gr != 0) {
			return res.getString(R.string.sub_gr, gr);
		}
		return res.getString(R.string.sub_ch_gr, ch, gr);
	}

	FutureSupplier<XmlTv> getXmlTv() {
		return xmlTv.get().main();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	public FutureSupplier<TvM3uTrackItem> getTrack(int gid, int tid, @Nullable String uri) {
		return super.getTrack(gid, tid, uri).cast();
	}

	public FutureSupplier<TvM3uGroupItem> getGroup(int id, @Nullable String name) {
		return super.getGroup(id, name).cast();
	}

	@Override
	public TvM3uFile getResource() {
		return (TvM3uFile) super.getResource();
	}

	public String getEpgUrl() {
		String url = getResource().getEpgUrl();
		return ((url != null) && !url.isEmpty()) ? url : tvgUrl;
	}

	@Override
	protected void setTvgUrl(String url) {
		if (url == null) return;
		tvgUrl = url = url.trim();
		TvM3uFile f = getResource();
		if (f.getEpgUrl() == null) f.setEpgUrl(url);
	}
}
