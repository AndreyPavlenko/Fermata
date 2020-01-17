package me.aap.fermata.media.lib;

import android.content.Context;
import android.support.v4.media.MediaDescriptionCompat;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.MediaLib.Playlists;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlaylistsPrefs;
import me.aap.fermata.util.Utils;

import static me.aap.fermata.util.Utils.getResourceUri;

/**
 * @author Andrey Pavlenko
 */
class DefaultPlaylists extends ItemContainer<Playlist> implements Playlists, PlaylistsPrefs {
	static final String ID = "Playlists";
	static final String SCHEME = "playlist";
	private final DefaultMediaLib lib;

	public DefaultPlaylists(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
	}

	@NonNull
	@Override
	public String getTitle() {
		return getLib().getContext().getString(R.string.playlists);
	}

	@NonNull
	@Override
	public String getSubtitle() {
		return "";
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return lib;
	}

	@Override
	public BrowsableItem getParent() {
		return null;
	}

	@NonNull
	@Override
	public BrowsableItem getRoot() {
		return this;
	}

	@Override
	public BrowsableItemPrefs getPrefs() {
		return this;
	}

	@Override
	public MediaDescriptionCompat.Builder getMediaDescriptionBuilder() {
		return super.getMediaDescriptionBuilder()
				.setIconUri(getResourceUri(getLib().getContext(), R.drawable.playlist));
	}

	public List<Playlist> listChildren() {
		int[] ids = getPlaylistIdsPref();
		List<Playlist> children = new ArrayList<>(ids.length);
		StringBuilder sb = Utils.getSharedStringBuilder();
		sb.append(SCHEME).append(':');
		int len = sb.length();

		for (int id : ids) {
			sb.setLength(len);
			sb.append(id).append(':');
			children.add(new DefaultPlaylist(sb.toString(), this, id));
		}

		return children;
	}

	Item getItem(String id) {
		assert id.startsWith(SCHEME);

		for (Playlist i : getChildren(null)) {
			if (!id.startsWith(i.getId())) continue;
			if (id.equals(i.getId())) return i;

			for (Item c : i.getChildren(null)) {
				if (id.equals(c.getId())) return c;
			}
		}

		return null;
	}

	@Override
	public boolean isPlaylistsItemId(String id) {
		return isChildItemId(id);
	}

	@Override
	public Playlist addItem(CharSequence name) {
		String n = name.toString().trim();

		if (n.isEmpty() || (n.indexOf('/') != -1)) {
			Context ctx = getLib().getContext();
			Toast.makeText(ctx, ctx.getResources().getString(R.string.err_invalid_playlist_name, n),
					Toast.LENGTH_LONG).show();
			return null;
		}

		if (Utils.contains(getChildren(null), c -> n.equals(c.getName()))) {
			Context ctx = getLib().getContext();
			Toast.makeText(ctx, ctx.getResources().getString(R.string.err_playlist_exists, n),
					Toast.LENGTH_LONG).show();
			return null;
		}

		int playlistId = getPlaylistsCounterPref() + 1;
		StringBuilder sb = Utils.getSharedStringBuilder();
		sb.append(SCHEME).append(':').append(playlistId).append(':');
		DefaultPlaylist pl = new DefaultPlaylist(sb.toString(), this, playlistId);
		setPlaylistsCounterPref(playlistId);
		pl.setPlaylistNamePref(n);
		super.addItem(pl);
		return pl;
	}

	@Override
	String getScheme() {
		return SCHEME;
	}

	@Override
	public void addItem(Playlist i) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addItems(List<Playlist> items) {
		throw new UnsupportedOperationException();
	}

	@Override
	void saveChildren(List<Playlist> children) {
		setPlaylistIdsPref(Utils.map(children, (i, t, a) -> a[i] = ((DefaultPlaylist) t).getPlaylistId(), int[]::new));
	}
}