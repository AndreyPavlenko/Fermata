package me.aap.fermata.vfs;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Supplier;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceViewAdapter;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.fragment.GenericDialogFragment;
import me.aap.utils.vfs.VfsException;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.async.Completed.cancelled;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public abstract class VfsProviderBase implements VfsProvider {
	@SuppressWarnings({"FieldCanBeLocal", "unused"})
	private PreferenceStore.Listener prefsListener;

	protected FutureSupplier<VirtualFolder> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
		return completedNull();
	}

	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualFolder folder) {
		return completedVoid();
	}

	@Override
	public FutureSupplier<VirtualFolder> select(MainActivityDelegate a, List<VirtualFileSystem> fs) {
		if (fs.isEmpty()) return Completed.failed(new VfsException("No file system found"));

		VirtualFileSystem f = fs.get(0);
		FutureSupplier<List<VirtualFolder>> getRoots = f.getRoots();
		a.setContentLoading(getRoots);
		return getRoots.then(roots -> {
			if (roots.isEmpty() && addRemoveSupported()) {
				return addFolder(a, f).then(folder -> {
					if (folder != null) {
						return folder.getChildren().withMainHandler().then(c -> pickFolder(a, f, folder, c));
					} else {
						return cancelled();
					}
				});
			} else {
				return pickFolder(a, f, null, roots);
			}
		});
	}

	protected FutureSupplier<VirtualFolder> pickFolder(
			MainActivityDelegate a, VirtualFileSystem fs, VirtualResource parent,
			List<? extends VirtualResource> children) {
		Promise<VirtualFolder> p = new Promise<>();
		FilePickerFragment f = a.showFragment(R.id.file_picker);
		f.setMode(FilePickerFragment.FOLDER);
		f.setResources(parent, children);
		f.setFileConsumer(r -> p.complete((VirtualFolder) r));

		if (addRemoveSupported()) {
			f.setCreateFolder(new FilePickerFragment.CreateFolder() {
				@Override
				public int getIcon() {
					return R.drawable.add_folder;
				}

				@Override
				public boolean isAvailable(VirtualResource parent, List<? extends VirtualResource> children) {
					return parent == null;
				}

				@Override
				public FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>>
				create(VirtualResource parent, List<? extends VirtualResource> children) {
					return addFolder(a, fs).then(f -> fs.getRoots().map(roots -> new BiHolder<>(null, roots)));
				}
			});

			f.setOnLongClick(item -> {
				a.getContextMenu().show(b -> {
					b.addItem(R.id.folders_remove, R.drawable.remove_folder, R.string.remove_folder);
					b.setSelectionHandler(i -> {
						if (i.getItemId() != R.id.folders_remove) return false;
						removeFolder(a, fs, (VirtualFolder) item).thenRun(() -> f.setFileSystem(fs));
						return true;
					});
				});

				return true;
			});
		}

		return p;
	}

	protected FutureSupplier<Boolean> requestPrefs(
			MainActivityDelegate a, PreferenceSet prefs, PreferenceStore ps) {
		GenericDialogFragment f = a.showFragment(me.aap.utils.R.id.generic_dialog_fragment);
		f.setTitle(a.getString(R.string.add_folder));
		f.setContentProvider(g -> {
			RecyclerView v = new RecyclerView(g.getContext());
			v.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			v.setHasFixedSize(true);
			v.setLayoutManager(new LinearLayoutManager(g.getContext()));
			v.setAdapter(new PreferenceViewAdapter(prefs));
			g.addView(v);
		});
		f.setDialogValidator(() -> validate(ps));

		ps.addBroadcastListener(prefsListener = (s, p) ->
				f.getToolBarMediator().onActivityEvent(a.getToolBar(), a, FRAGMENT_CONTENT_CHANGED));

		Promise<Boolean> p = new Promise<>();
		f.setDialogConsumer(p::complete);
		return p;
	}

	protected boolean validate(PreferenceStore ps) {
		return true;
	}

	protected boolean addRemoveSupported() {
		return true;
	}

	@SuppressWarnings("unchecked")
	protected boolean allSet(PreferenceStore ps, PreferenceStore.Pref<Supplier<String>>... prefs) {
		for (PreferenceStore.Pref<Supplier<String>> p : prefs) {
			String v = ps.getStringPref(p);
			if ((v == null) || v.trim().isEmpty()) return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	protected boolean anySet(PreferenceStore ps, PreferenceStore.Pref<Supplier<String>>... prefs) {
		for (PreferenceStore.Pref<Supplier<String>> p : prefs) {
			String v = ps.getStringPref(p);
			if ((v != null) && !v.trim().isEmpty()) return true;
		}
		return false;
	}
}
