package me.aap.fermata.ui.view;

import android.content.res.Resources;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;
import me.aap.fermata.function.IntSupplier;
import me.aap.fermata.function.Supplier;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Favorites;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.Playlist;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.pref.BasicPreferenceStore;
import me.aap.fermata.pref.PreferenceSet;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.pref.PreferenceStore.Pref;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;
import me.aap.fermata.util.Utils;

import static java.util.Objects.requireNonNull;
import static me.aap.fermata.media.pref.PlayableItemPrefs.BOOKMARKS;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemMenuHandler implements AppMenu.SelectionHandler {
	private final AppMenu menu;
	private final Item item;

	public MediaItemMenuHandler(AppMenu menu, Item item) {
		this.menu = menu;
		this.item = item;
	}

	public AppMenu getMenu() {
		return menu;
	}

	public Item getItem() {
		return item;
	}

	public void show(@LayoutRes int layout) {
		AppMenu menu = getMenu();
		menu.inflate(layout);

		if (item instanceof PlayableItem) {
			initPlayableMenu(menu, (PlayableItem) item, true);
		} else {
			initBrowsableMenu(menu, (BrowsableItem) item);
		}

		menu.show(this);
	}

	protected void initPlayableMenu(AppMenu menu, PlayableItem pi, boolean initRepeat) {
		MainActivityDelegate a = getMainActivity();
		boolean favorite = pi.isFavoriteItem();
		boolean hasBookmarks = pi.getPrefs().hasPref(BOOKMARKS);
		boolean playlist = (pi.getParent() instanceof Playlist);

		menu.findItem(R.id.favorites_add).setVisible(!favorite);
		menu.findItem(R.id.favorites_remove).setVisible(favorite);
		menu.findItem(R.id.create_bookmark).setVisible(!hasBookmarks);
		menu.findItem(R.id.bookmarks).setVisible(hasBookmarks);

		if (initRepeat) {
			boolean repeat = pi.isRepeatItemEnabled();
			menu.findItem(R.id.repeat_enable).setVisible(!repeat);
			menu.findItem(R.id.repeat_disable).setVisible(repeat);
		}

		if (playlist) menu.findItem(R.id.playlist_remove).setVisible(true);
		else a.initPlaylistMenu(menu);
	}

	protected void initBrowsableMenu(AppMenu menu, BrowsableItem bi) {
		MainActivityDelegate a = getMainActivity();
		boolean playlist = (bi instanceof Playlist);
		boolean favorite = (bi.getParent() instanceof Favorites);
		boolean hasBookmarks = false;

		for (Item c : bi.getChildren(null)) {
			if ((c instanceof PlayableItem) && ((PlayableItem) c).getPrefs().hasPref(BOOKMARKS)) {
				hasBookmarks = true;
				break;
			}
		}

		menu.findItem(R.id.favorites_add).setVisible(!favorite);
		menu.findItem(R.id.bookmarks).setVisible(hasBookmarks);
		if (!playlist) a.initPlaylistMenu(menu);
	}

	@Override
	public boolean menuItemSelected(AppMenuItem i) {
		int id = i.getItemId();
		MediaLibFragment f;
		Item item = getItem();
		AppMenu menu;
		List<PlayableItem> items;

		switch (id) {
			case R.id.favorites_add:
				if (item instanceof BrowsableItem) {
					items = ((BrowsableItem) item).getPlayableChildren(true);
					item.getLib().getFavorites().addItems(items);
				} else {
					item.getLib().getFavorites().addItem((PlayableItem) item);
				}

				f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				break;
			case R.id.favorites_remove:
				item.getLib().getFavorites().removeItem((PlayableItem) item);
				f = getMainActivity().getMediaLibFragment(R.id.nav_favorites);
				if (f != null) f.reload();
				break;
			case R.id.playlist_create:
				String initName = "";
				if (item instanceof BrowsableItem) {
					items = ((BrowsableItem) item).getPlayableChildren(true);
					initName = item.getName();
				} else {
					items = Collections.singletonList((PlayableItem) item);
				}
				getMainActivity().createPlaylist(items, initName);
				break;
			case R.id.playlist_add:
				menu = i.getMenu();
				getMainActivity().createPlaylistMenu(menu);
				menu.show(this);
				break;
			case R.id.playlist_add_item:
				items = (item instanceof BrowsableItem) ?
						((BrowsableItem) item).getPlayableChildren(true) :
						Collections.singletonList((PlayableItem) item);
				getMainActivity().addToPlaylist(i.getTitle().toString(), items);
				break;
			case R.id.playlist_remove:
				getMainActivity().removeFromPlaylist(
						(Playlist) requireNonNull(item.getParent()),
						Collections.singletonList((PlayableItem) item));
				break;
			case R.id.create_bookmark:
				showCreateBookmarkMenu(i.getMenu());
				break;
			case R.id.bookmarks:
				menu = i.getMenu();
				menu.setTitle(R.string.bookmarks);
				menu.addItem(R.id.bookmark_remove_all, R.string.remove_all_bookmarks);

				if (item instanceof BrowsableItem) {
					items = ((BrowsableItem) item).getPlayableChildren(true);
				} else {
					items = Collections.singletonList((PlayableItem) item);
					menu.addItem(R.id.create_bookmark, R.string.create_bookmark);
				}

				for (PlayableItem pi : items) {
					for (String b : pi.getPrefs().getBookmarks()) {
						String name = PlayableItemPrefs.bookmarkName(b);
						i = menu.addItem(R.id.bookmark_select, name);
						i.setData(new Bookmark(pi, name, PlayableItemPrefs.bookmarkTime(b)));
					}
				}

				menu.show(this);
				break;
			case R.id.bookmark_select:
				if (i.isLongClick()) {
					Bookmark b = i.getData();
					menu = i.getMenu();
					menu.hide();
					menu.setTitle(i.getTitle());
					i = menu.addItem(R.id.bookmark_remove, R.string.remove_bookmark);
					i.setData(b);
					menu.show(this);
				} else {
					Bookmark b = i.getData();
					getMainActivity().getMediaServiceBinder().playItem(b.item, b.time * 1000);
				}

				break;
			case R.id.bookmark_remove:
				Bookmark b = i.getData();
				b.item.getPrefs().removeBookmark(b.name, b.time);
				break;
			case R.id.bookmark_remove_all:
				menu = i.getMenu();
				menu.setTitle(R.string.bookmarks);
				menu.addItem(R.id.bookmark_remove_all_confirm, R.string.remove_all_bookmarks);
				menu.show(this);
				break;
			case R.id.bookmark_remove_all_confirm:
				items = (item instanceof BrowsableItem) ?
						((BrowsableItem) item).getPlayableChildren(true) :
						Collections.singletonList((PlayableItem) item);

				for (PlayableItem pi : items) {
					pi.getPrefs().removePref(BOOKMARKS);
				}

				break;
			case R.id.repeat_enable:
			case R.id.repeat_disable:
				((PlayableItem) item).setRepeatItemEnabled(id == R.id.repeat_enable);
				break;
		}

		return true;
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getMenu().getContext());
	}

	private void showCreateBookmarkMenu(AppMenu menu) {
		MediaEngine eng = getMainActivity().getMediaServiceBinder().getCurrentEngine();
		int pos = ((eng == null) || !item.equals(eng.getSource())) ? 0 : (int) (eng.getPosition() / 1000);
		PreferenceStore store = new BasicPreferenceStore();
		Pref<Supplier<String>> name = Pref.s("name", Utils.timeToString(pos));
		Pref<IntSupplier> time = Pref.i("time", pos);

		PreferenceSet set = new PreferenceSet();
		set.addStringPref(o -> {
			o.store = store;
			o.pref = name;
			o.title = R.string.bookmark_name;
		});
		set.addTimePref(o -> {
			o.store = store;
			o.pref = time;
			o.title = R.string.bookmark_time;
			o.editable = false;
			o.ems = 4;
			o.seekMax = (int) (((PlayableItem) item).getDuration() / 1000);
		});

		View v = menu.inflate(R.layout.pref_list_view);
		RecyclerView prefsView = v.findViewById(R.id.prefs_list_view);
		set.addToView(prefsView);
		prefsView.setMinimumWidth(Resources.getSystem().getDisplayMetrics().widthPixels * 2 / 3);
		menu.show(m -> true, m -> {
			PlayableItemPrefs prefs = ((PlayableItem) item).getPrefs();
			prefs.addBookmark(store.getStringPref(name), store.getIntPref(time));
		});
	}

	private static final class Bookmark {
		final PlayableItem item;
		final String name;
		final int time;

		public Bookmark(PlayableItem item, String name, int time) {
			this.item = item;
			this.name = name;
			this.time = time;
		}
	}
}
