package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FavoritesPrefs;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.pref.SharedPreferenceStore;
import me.aap.fermata.util.Utils;

import static me.aap.fermata.util.Utils.getResourceUri;
import static me.aap.fermata.util.Utils.mapToArray;


/**
 * @author Andrey Pavlenko
 */
class DefaultFavorites extends ItemContainer<PlayableItem> implements Favorites, FavoritesPrefs {
	public static final String ID = "Favorites";
	public static final String SCHEME = "favorite";
	private final DefaultMediaLib lib;
	private final SharedPreferenceStore favoritesPrefStore;

	public DefaultFavorites(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		SharedPreferences prefs = lib.getContext().getSharedPreferences("favorites", Context.MODE_PRIVATE);
		favoritesPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
	}

	@NonNull
	@Override
	public String getTitle() {
		return getLib().getContext().getString(R.string.favorites);
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

	@NonNull
	@Override
	public PreferenceStore getFavoritesPreferenceStore() {
		return favoritesPrefStore;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}

	@Override
	public MediaDescriptionCompat.Builder getMediaDescriptionBuilder() {
		return super.getMediaDescriptionBuilder()
				.setIconUri(getResourceUri(getLib().getContext(), R.drawable.favorite_filled));
	}

	@Override
	public List<PlayableItem> listChildren() {
		return listChildren(getFavoritesPref());
	}

	@Override
	public boolean isFavoriteItem(PlayableItem i) {
		String id = toChildItemId(i.getOrigId());
		return Utils.contains(getChildren(null), c -> id.equals(c.getId()));
	}

	@Override
	public boolean isFavoriteItemId(String id) {
		return isChildItemId(id);
	}

	@Override
	String getScheme() {
		return SCHEME;
	}

	@Override
	void saveChildren(List<PlayableItem> children) {
		setFavoritesPref(mapToArray(children, PlayableItem::getOrigId, String[]::new));
	}
}
