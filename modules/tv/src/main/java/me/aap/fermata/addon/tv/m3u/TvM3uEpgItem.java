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
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uEpgItem extends ItemBase implements TvItem, EpgItem {
	public static final String SCHEME = "tvm3ue";
	final long start;
	final long end;
	final String title;
	final String descr;
	final String icon;
	private TvM3uEpgItem prev;
	private TvM3uEpgItem next;

	TvM3uEpgItem(String id, @NonNull TvM3uTrackItem track, long start, long end, String title,
							 String descr, String icon) {
		super(id, track, track.getResource());
		this.start = start;
		this.end = end;
		this.title = title;
		this.descr = descr;
		this.icon = icon;
	}

	TvM3uEpgItem(TvM3uArchiveItem i) {
		this(i.getId(), i.getParent(), i.start, i.end, i.title, i.descr, i.icon);
		set(i);
	}

	public static FutureSupplier<? extends Item> create(@NonNull TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int slash = id.indexOf('/');
		if (slash < 0) return completedNull();
		int dash = id.indexOf('-', slash + 1);
		if (dash < 0) return completedNull();
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(TvM3uTrackItem.SCHEME);
		tb.append(id, SCHEME.length(), slash);
		String trackId = tb.releaseString();
		FutureSupplier<? extends Item> f = root.getItem(TvM3uTrackItem.SCHEME, trackId);
		return (f == null) ? null : f.then(t -> {
			if (t == null) return completedNull();
			return ((TvM3uTrackItem) t).getEpg().map(l -> {
				for (TvM3uEpgItem e : l) {
					if (id.equals(e.getId())) return e;
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
			} else if (track.isArchive(start, end)) {
				return new TvM3uArchiveItem(id, track, start, end, title, description, icon);
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
			if (descr != null) b.append(descr).append(".\n");
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
	public TvM3uEpgItem getPrev() {
		return prev;
	}

	void setPrev(TvM3uEpgItem prev) {
		this.prev = prev;

		if (prev instanceof TvM3uArchiveItem) {
			scheduleReplacement();
		} else if ((prev == null) && (next == null)) {
			scheduleReplacement();
		}
	}

	@Override
	public TvM3uEpgItem getNext() {
		return next;
	}

	public void setNext(TvM3uEpgItem next) {
		this.next = next;

		if (next instanceof TvM3uArchiveItem) {
			next.scheduleReplacement();
		} else if ((next == null) && (prev == null)) {
			scheduleReplacement();
		}
	}

	void scheduleReplacement() {
		long delay = end + 1000 - System.currentTimeMillis();
		if (delay < 0) return;
		App.get().getHandler().postDelayed(() -> {
			TvM3uTrackItem t = getParent();
			if (!t.isArchive(start, end)) return;
			t.replace(this, TvM3uArchiveItem::new);
		}, delay);
	}
}
