package me.aap.fermata.media.lib;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.FoldersPrefs;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualFolder;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.mapToArray;
import static me.aap.utils.security.SecurityUtils.sha1String;

/**
 * @author Andrey Pavlenko
 */
class DefaultFolders extends BrowsableItemBase implements Folders,
		FoldersPrefs {
	static final String ID = "Folders";
	private final DefaultMediaLib lib;
	private final SharedPreferenceStore foldersPrefStore;
	private final FermataVfsManager vfsManager;

	public DefaultFolders(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		SharedPreferences prefs = lib.getContext().getSharedPreferences("folders", Context.MODE_PRIVATE);
		foldersPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
		vfsManager = new FermataVfsManager();
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(R.string.folders));
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
	public FoldersPrefs getPrefs() {
		return this;
	}

	@NonNull
	@Override
	public PreferenceStore getFoldersPreferenceStore() {
		return foldersPrefStore;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return getLib().getBroadcastEventListeners();
	}

	@Override
	public boolean sortChildrenEnabled() {
		return false;
	}

	@Override
	public boolean getTitleSeqNumPref() {
		return false;
	}

	FermataVfsManager getVfsManager() {
		return vfsManager;
	}

	protected FutureSupplier<List<Item>> listChildren() {
		DefaultMediaLib lib = getLib();
		String[] pref = getFoldersPref();
		List<Item> children = new ArrayList<>(pref.length);
		Set<String> names = new HashSet<>((int) (pref.length * 1.5));

		return Async.forEach(u -> vfsManager.getResource(Uri.parse(u)).then(res -> {
			if (!(res instanceof VirtualFolder)) return completedVoid();

			VirtualFolder folder = (VirtualFolder) res;
			String name = folder.getName();
			if (!names.add(name)) name = sha1String(u);

			String id = SharedTextBuilder.get().append(FolderItem.SCHEME).append(':').append(name).releaseString();
			FolderItem i = FolderItem.create(id, this, folder, lib);
			children.add(i);
			return completedVoid();
		}), pref).then(v -> completed(children));
	}

	@Override
	public boolean isFoldersItemId(String id) {
		int idx = id.indexOf(':');
		if (idx == -1) return false;

		switch (id.substring(0, idx)) {
			case FileItem.SCHEME:
			case FolderItem.SCHEME:
			case CueItem.SCHEME:
			case CueTrackItem.SCHEME:
				return true;
			default:
				return false;
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Item> addItem(Uri uri) {
		List<FolderItem> children = list();

		if (CollectionUtils.contains(children, u -> uri.equals(u.getFile().getUri()))) {
			return completedNull();
		}

		List<FolderItem> newChildren = new ArrayList<>(children.size() + 1);
		newChildren.addAll(children);
		return toFolderItem(uri, newChildren).withMainHandler().map(folder -> {
			if (folder == null) return null;

			newChildren.add(folder);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			return folder;
		});
	}

	@Override
	public void removeItem(int idx) {
		List<FolderItem> newChildren = new ArrayList<>(list());
		FolderItem i = newChildren.remove(idx);
		getLib().removeFromCache(i);
		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	@Override
	public void removeItem(Item item) {
		Uri uri = item.getFile().getUri();
		List<FolderItem> newChildren = new ArrayList<>(list());
		if (!CollectionUtils.remove(newChildren, u -> uri.equals(u.getFile().getUri()))) return;

		getLib().removeFromCache(item);
		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	@Override
	public void moveItem(int fromPosition, int toPosition) {
		List<FolderItem> newChildren = new ArrayList<>(list());
		CollectionUtils.move(newChildren, fromPosition, toPosition);
		setNewChildren(newChildren);
		saveChildren(newChildren);
	}

	private void saveChildren(List<? extends Item> children) {
		setFoldersPref(mapToArray(children, i -> i.getFile().getUri().toString(), String[]::new));
	}

	@NonNull
	private FutureSupplier<FolderItem> toFolderItem(Uri u, List<FolderItem> children) {
		return vfsManager.getResource(u).map(res -> {
			if (!(res instanceof VirtualFolder)) return null;

			VirtualFolder folder = (VirtualFolder) res;
			String name = folder.getName();

			for (FolderItem c : children) {
				if (name.equals(c.getFile().getName())) {
					name = sha1String(u.toString());
					break;
				}
			}

			SharedTextBuilder tb = SharedTextBuilder.get();
			tb.append(FolderItem.SCHEME).append(':').append(name);
			return FolderItem.create(tb.releaseString(), this, folder, getLib());
		});
	}


	@SuppressWarnings({"rawtypes", "unchecked"})
	private List<FolderItem> list() {
		return (List) getUnsortedChildren().getOrThrow();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void setNewChildren(List<FolderItem> c) {
		super.setChildren((List) c);
	}
}