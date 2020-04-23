package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.PlaylistPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.SharedTextBuilder;

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
		buildDescription(getUnsortedChildren().getOrThrow().size());
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
		buildDescription(children.size());
	}

	private void buildDescription(int count) {
		MediaDescriptionCompat.Builder dsc = new MediaDescriptionCompat.Builder();
		dsc.setTitle(getName());
		dsc.setSubtitle(getLib().getContext().getResources().getString(R.string.browsable_subtitle, count));
		setMediaDescription(dsc.build());
	}
}