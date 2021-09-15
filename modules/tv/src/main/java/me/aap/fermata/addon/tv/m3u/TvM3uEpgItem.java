package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.Locale;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemBase;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uEpgItem extends ItemBase implements TvItem, EpgItem {
	public static final String SCHEME = "tvm3ue";
	private final long start;
	private final long end;
	private final String title;
	private final String description;
	private final String icon;
	TvM3uEpgItem next;

	private TvM3uEpgItem(String id, @NonNull TvM3uTrackItem track, long start, long end, String title,
											 String description, String icon) {
		super(id, track, track.getResource());
		this.start = start;
		this.end = end;
		this.title = title;
		this.description = description;
		this.icon = icon;
	}

	public static FutureSupplier<? extends Item> create(@NonNull TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int slash = id.indexOf('/');
		if (slash < 0) return completedNull();

		int dash = id.indexOf('-', slash + 1);
		if (dash < 0) return completedNull();
		long start = Long.parseLong(id.substring(slash + 1, dash));
		long end = Long.parseLong(id.substring(dash + 1));

		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(TvM3uTrackItem.SCHEME);
		tb.append(id, SCHEME.length(), slash);
		String trackId = tb.releaseString();
		FutureSupplier<? extends Item> f = root.getItem(TvM3uTrackItem.SCHEME, trackId);
		return (f == null) ? null : f.then(t -> {
			if (t == null) return completedNull();
			return ((TvM3uTrackItem) t).getEpg().map(l -> {
				for (TvM3uEpgItem e : l) {
					if ((e.start == start) && (e.end == end)) return e;
				}
				return t;
			});
		});
	}

	static TvM3uEpgItem create(@NonNull TvM3uTrackItem track, long start, long end, String title,
														 String description, String icon) {
		String id = SCHEME + track.getId().substring(TvM3uTrackItem.SCHEME.length()) +
				'/' + start + '-' + end;
		DefaultMediaLib lib = (DefaultMediaLib) track.getLib();

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				TvM3uEpgItem e = (TvM3uEpgItem) i;
				if (BuildConfig.D && !track.equals(e.getParent())) throw new AssertionError();
				return e;
			} else {
				return new TvM3uEpgItem(id, track, start, end, title, description, icon);
			}
		}
	}

	@Override
	protected FutureSupplier<String> buildTitle(int seqNum, BrowsableItemPrefs parentPrefs) {
		return completed(title);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		try (SharedTextBuilder b = SharedTextBuilder.get()) {
			if (description != null) b.append(description).append(".\n");
			Calendar c = Calendar.getInstance();
			Locale l = Locale.getDefault();
			c.setTimeInMillis(start);
			b.append(c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, l)).append(' ');
			b.append(c.get(Calendar.DAY_OF_MONTH)).append(", ");
			TextUtils.dateToTimeString(b, start, false);
			b.append(" - ");
			TextUtils.dateToTimeString(b, end, false);
			return completed(b.toString());
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return (icon == null) ? completedNull() : completed(Uri.parse(icon));
	}

	@NonNull
	@Override
	public TvM3uTrackItem getParent() {
		assert super.getParent() != null;
		return (TvM3uTrackItem) super.getParent();
	}

	@Override
	public long getStartTime() {
		return start;
	}

	@Override
	public long getEndTime() {
		return end;
	}

	@Override
	public TvM3uEpgItem getNext() {
		return next;
	}
}
