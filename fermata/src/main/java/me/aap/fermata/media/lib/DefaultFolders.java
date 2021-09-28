package me.aap.fermata.media.lib;

import static me.aap.fermata.vfs.m3u.M3uFileSystem.SCHEME_M3U;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.mapToArray;
import static me.aap.utils.security.SecurityUtils.sha1String;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.pref.FoldersPrefs;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.fermata.vfs.m3u.M3uFileSystem;
import me.aap.fermata.vfs.m3u.M3uFileSystemProvider;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
class DefaultFolders extends BrowsableItemBase implements Folders, FoldersPrefs {
	static final String ID = "Folders";
	private final DefaultMediaLib lib;
	private final SharedPreferenceStore foldersPrefStore;
	private final FermataVfsManager vfsManager;

	public DefaultFolders(DefaultMediaLib lib) {
		super(ID, null, null);
		this.lib = lib;
		SharedPreferences prefs = lib.getContext().getSharedPreferences("folders", Context.MODE_PRIVATE);
		foldersPrefStore = SharedPreferenceStore.create(prefs, getLib().getPrefs());
		vfsManager = FermataApplication.get().getVfsManager();
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

		return Async.forEach(rid -> vfsManager.getResource(rid)
				.ifFail(fail -> {
					Log.e(fail, "Failed to load folder ", rid);
					return null;
				})
				.then(r -> {
					if (r == null) return completedVoid();

					Item i = null;

					if (r instanceof VirtualFolder) {
						String name = r.getName();
						if (!names.add(name)) name = sha1String(rid);
						String id = SharedTextBuilder.get().append(FolderItem.SCHEME).append(':').append(name).releaseString();
						i = FolderItem.create(id, this, (VirtualFolder) r, lib);
					} else if (r instanceof VirtualFile) {
						Rid mrid = r.getRid();
						if (SCHEME_M3U.equals(mrid.getScheme())) {
							M3uFileSystem fs = M3uFileSystem.getInstance();
							String id = SharedTextBuilder.get().append(M3uItem.SCHEME).append(':').append(fs.toId(mrid)).releaseString();
							i = M3uItem.create(id, this, (VirtualFile) r, lib);
						}
					}

					if (i != null) children.add(i);
					else Log.e("Unsupported resource: ", r);
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
		return list().then(children -> {
			if (CollectionUtils.contains(children, u -> uri.equals(u.getResource().getRid().toAndroidUri()))) {
				return completedNull();
			}

			List<BrowsableItem> newChildren = new ArrayList<>(children.size() + 1);
			newChildren.addAll(children);
			return toFolderItem(uri, newChildren).main().map(folder -> {
				if (folder == null) return null;

				newChildren.add(folder);
				setNewChildren(newChildren);
				saveChildren(newChildren);
				return folder;
			});
		});
	}

	@Override
	public FutureSupplier<Void> removeItem(int idx) {
		return list().map(list -> {
			List<BrowsableItem> newChildren = new ArrayList<>(list);
			BrowsableItem i = newChildren.remove(idx);
			getLib().removeFromCache(i);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			itemRemoved(i);
			return null;
		});
	}

	@Override
	public FutureSupplier<Void> removeItem(Item item) {
		return list().map(list -> {
			Rid rid = item.getResource().getRid();
			List<BrowsableItem> newChildren = new ArrayList<>(list);
			if (!CollectionUtils.remove(newChildren, u -> rid.equals(u.getResource().getRid())))
				return null;

			getLib().removeFromCache(item);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			itemRemoved(item);
			return null;
		});
	}

	@Override
	public FutureSupplier<Void> moveItem(int fromPosition, int toPosition) {
		return list().map(list -> {
			List<BrowsableItem> newChildren = new ArrayList<>(list);
			CollectionUtils.move(newChildren, fromPosition, toPosition);
			setNewChildren(newChildren);
			saveChildren(newChildren);
			return null;
		});
	}

	private void saveChildren(List<? extends Item> children) {
		setFoldersPref(mapToArray(children, i -> i.getResource().getRid().toString(), String[]::new));
	}

	@NonNull
	private FutureSupplier<BrowsableItem> toFolderItem(Uri u, List<BrowsableItem> children) {
		return vfsManager.getResource(Rid.create(u)).map(r -> {
			if (r == null) return null;

			String name = r.getName();

			for (BrowsableItem c : children) {
				if (name.equals(c.getResource().getName())) {
					name = sha1String(u.toString());
					break;
				}
			}

			if (r instanceof VirtualFolder) {
				SharedTextBuilder tb = SharedTextBuilder.get();
				tb.append(FolderItem.SCHEME).append(':').append(name);
				return FolderItem.create(tb.releaseString(), this, (VirtualFolder) r, getLib());
			} else if (r instanceof VirtualFile) {
				Rid rid = r.getRid();
				if (SCHEME_M3U.equals(rid.getScheme())) {
					M3uFileSystem fs = M3uFileSystem.getInstance();
					String id = SharedTextBuilder.get().append(M3uItem.SCHEME).append(':').append(fs.toId(rid)).releaseString();
					return M3uItem.create(id, this, (VirtualFile) r, lib);
				}
			}

			Log.e("Unsupported resource: " + r);
			return null;
		});
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private FutureSupplier<List<BrowsableItem>> list() {
		return (FutureSupplier) getUnsortedChildren().main();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private void setNewChildren(List<BrowsableItem> c) {
		super.setChildren((List) c);
	}

	private void itemRemoved(Item i) {
		VirtualResource r = i.getResource();
		if (r instanceof M3uFile) M3uFileSystemProvider.removePlaylist((M3uFile) r);
	}
}