package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlaylistPrefs;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.pref.SharedPreferenceStore;
import me.aap.fermata.util.Utils;

import static me.aap.fermata.util.Utils.mapToArray;

/**
 * @author Andrey Pavlenko
 */
class DefaultPlaylist extends ItemContainer<PlayableItem> implements Playlist, PlaylistPrefs {
	private final int playlistId;
	private final SharedPreferenceStore playlistPrefStore;

	public DefaultPlaylist(String id, BrowsableItem parent, int playlistId) {
		super(id, parent, null);
		this.playlistId = playlistId;
		SharedPreferences prefs = getLib().getContext().getSharedPreferences("playlist_" + playlistId,
				Context.MODE_PRIVATE);
		playlistPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
	}

	@Override
	public String getName() {
		return getPlaylistNamePref();
	}

	public int getPlaylistId() {
		return playlistId;
	}

	@NonNull
	@Override
	public String getSubtitle() {
		return getLib().getContext().getResources().getString(R.string.browsable_subtitle,
				getChildren(null).size());
	}

	@Override
	public BrowsableItemPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public PreferenceStore getPlaylistPreferenceStore() {
		return playlistPrefStore;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}

	public List<PlayableItem> listChildren() {
		return listChildren(getPlaylistItemsPref());
	}

	@Override
	String getScheme() {
		return getId();
	}

	@Override
	public String toChildItemId(String id) {
		if (isChildItemId(id)) return id;
		StringBuilder sb = Utils.getSharedStringBuilder();
		return sb.append(getScheme()).append(id).toString();
	}

	@Override
	void saveChildren(List<PlayableItem> children) {
		setPlaylistItemsPref(mapToArray(children, PlayableItem::getOrigId, String[]::new));
	}
}