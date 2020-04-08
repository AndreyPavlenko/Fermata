package me.aap.fermata.media.lib;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.utils.text.SharedTextBuilder;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
class M3uGroupItem extends BrowsableItemBase<M3uTrackItem> {
	public static final String SCHEME = "m3ug";
	final ArrayList<M3uTrackItem> tracks = new ArrayList<>();
	private final String name;
	private final int groupId;
	private String subtitle;

	M3uGroupItem(String id, M3uItem parent, String name, int groupId) {
		super(id, parent, parent.getFile());
		this.name = name;
		this.groupId = groupId;
	}

	void init() {
		tracks.trimToSize();
		subtitle = getLib().getContext().getResources().getString(R.string.browsable_subtitle,
				tracks.size());
	}

	static M3uGroupItem create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);
		int start = id.indexOf(':') + 1;
		int end = id.indexOf(':', start);
		int gid = Integer.parseInt(id.substring(start, end));
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uItem.SCHEME).append(id, end, id.length());
		M3uItem m3u = (M3uItem) lib.getItem(tb.releaseString());
		return (m3u != null) ? m3u.getGroup(gid) : null;
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
	public String getSubtitle() {
		return subtitle;
	}

	@Override
	public Uri getIconUri() {
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

	@Override
	public List<M3uTrackItem> listChildren() {
		return Collections.unmodifiableList(tracks);
	}
}
