package me.aap.fermata.media.lib;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

/**
 * @author Andrey Pavlenko
 */
class M3uGroupItem extends BrowsableItemBase {
	public static final String SCHEME = "m3ug";
	final ArrayList<M3uTrackItem> tracks = new ArrayList<>();
	private final String name;
	private final int groupId;

	M3uGroupItem(String id, M3uItem parent, String name, int groupId) {
		super(id, parent, parent.getFile());
		this.name = name;
		this.groupId = groupId;
	}

	void init() {
		tracks.trimToSize();
	}

	@NonNull
	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uItem.SCHEME).append(id, end, id.length());

		return lib.getItem(tb.releaseString()).then(i -> {
			M3uItem m3u = (M3uItem) i;
			return (m3u != null) ? m3u.getGroup(gid) : completedNull();
		});
	}

	@Override
	@NonNull
	public M3uItem getParent() {
		return (M3uItem) requireNonNull(super.getParent());
	}

	@Override
	public String getName() {
		return name;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return getParent().getIconUri();
	}

	public int getGroupId() {
		return groupId;
	}

	public M3uTrackItem getTrack(int id) {
		for (M3uTrackItem t : tracks) {
			if (t.getTrackNumber() == id) return t;
		}
		return null;
	}

	@Override
	public int getIcon() {
		return R.drawable.m3u;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed((List) tracks);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		String t = getLib().getContext().getResources().getString(R.string.browsable_subtitle,
				tracks.size());
		return completed(t);
	}
}

