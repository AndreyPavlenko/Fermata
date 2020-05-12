package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.FavoritesPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.collection.CollectionUtils.mapToArray;


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

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(R.string.favorites));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
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
	public PreferenceStore getParentPreferenceStore() {
		return getLib();
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
	public FutureSupplier<List<Item>> listChildren() {
		return listChildren(getFavoritesPref());
	}

	@Override
	public boolean isFavoriteItem(PlayableItem i) {
		String id = toChildItemId(i.getOrigId());
		List<Item> list = getUnsortedChildren().peek();
		return (list != null) && CollectionUtils.contains(list, c -> id.equals(c.getId()));
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
