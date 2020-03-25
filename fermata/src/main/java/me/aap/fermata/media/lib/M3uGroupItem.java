package me.aap.fermata.media.lib;

import android.graphics.Bitmap;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.storage.MediaFile;
import me.aap.utils.function.Consumer;
import me.aap.utils.text.TextUtils;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
class M3uGroupItem extends BrowsableItemBase<M3uTrackItem> {
	public static final String SCHEME = "m3ug";
	private final String name;
	private final int groupId;
	final ArrayList<M3uTrackItem> tracks = new ArrayList<>();
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
		StringBuilder sb = TextUtils.getSharedStringBuilder();
		sb.append(M3uItem.SCHEME).append(id, end, id.length());
		M3uItem m3u = (M3uItem) lib.getItem(sb);
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
	void buildMediaDescription(MediaDescriptionCompat.Builder b, Consumer<Consumer<MediaDescriptionCompat.Builder>> update) {
		M3uItem m3u = getParent();
		super.buildMediaDescription(b, (m3u.cover != null) ? update : null);
	}

	@Override
	void loadMediaDescription(MediaDescriptionCompat.Builder b) {
		super.loadMediaDescription(b);
		M3uItem m3u = getParent();
		if (m3u.cover == null) return;

		MediaFile file = MediaFile.resolve(m3u.cover, getFile().getParent());
		if (file != null) {
			Bitmap bm = getLib().getBitmap(file.getUri().toString());
			if (bm != null) b.setIconBitmap(bm);
		}
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
		return new ArrayList<>(tracks);
	}
}
