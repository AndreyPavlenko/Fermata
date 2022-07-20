package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.M3uGroupItem;
import me.aap.fermata.media.lib.M3uItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uGroupItem extends M3uGroupItem implements TvItem {
	public static final String SCHEME = "tvm3ug";

	protected TvM3uGroupItem(String id, M3uItem parent, String name, int groupId) {
		super(id, parent, name, groupId);
	}

	public static FutureSupplier<TvM3uGroupItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int gstart = id.indexOf(':') + 1;
		int gend = id.indexOf(':', gstart);
		int gid = Integer.parseInt(id.substring(gstart, gend));
		int nstart = id.indexOf(':', gend + 1);
		SharedTextBuilder tb = SharedTextBuilder.get().append(TvM3uItem.SCHEME);
		String name;

		if (nstart > 0) {
			name = id.substring(nstart + 1);
			tb.append(id, gend, nstart);
		} else {
			name = null;
			tb.append(id, gend, id.length());
		}

		FutureSupplier<? extends Item> f = root.getItem(TvM3uItem.SCHEME, tb.releaseString());
		return (f == null) ? completedNull() : f.then(i -> {
			TvM3uItem m3u = (TvM3uItem) i;
			return (m3u != null) ? m3u.getGroup(gid, name) : completedNull();
		});
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		Context ctx = dynCtx(getLib().getContext());
		String t = ctx.getResources().getString(R.string.sub_ch, tracks.size());
		return completed(t);
	}
}
