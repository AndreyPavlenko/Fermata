package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.lib.MediaLib.Playlists;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlaylistPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.SharedTextBuilder;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.collection.CollectionUtils.mapToArray;

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
	protected FutureSupplier<String> buildTitle() {
		return completed(getName());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		int count = getUnsortedChildren().peek(Collections::emptyList).size();
		return completed(getLib().getContext().getResources().getString(R.string.browsable_subtitle, count));
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
	public Playlists getParent() {
		return (Playlists) requireNonNull(super.getParent());
	}

	@NonNull
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

	public FutureSupplier<List<Item>> listChildren() {
		return listChildren(getPlaylistItemsPref());
	}

	@Override
	String getScheme() {
		return getId();
	}

	@Override
	public String toChildItemId(String id) {
		if (isChildItemId(id)) return id;
		SharedTextBuilder tb = SharedTextBuilder.get();
		return tb.append(getScheme()).append(id).releaseString();
	}

	@Override
	void saveChildren(List<PlayableItem> children) {
		setPlaylistItemsPref(mapToArray(children, PlayableItem::getOrigId, String[]::new));
	}
}