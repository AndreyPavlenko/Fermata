package me.aap.fermata.addon.tv;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.concurrent.CancellationException;

import me.aap.fermata.addon.tv.m3u.TvM3uFile;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystem;
import me.aap.fermata.addon.tv.m3u.TvM3uFileSystemProvider;
import me.aap.fermata.addon.tv.m3u.TvM3uItem;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.view.MediaItemMenuHandler;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public class TvFragment extends MediaLibFragment {

	@Override
	protected ListAdapter createAdapter(FermataServiceUiBinder b) {
		return new TvAdapter(getMainActivity(), getRootItem());
	}

	@Override
	public CharSequence getFragmentTitle() {
		return getResources().getString(me.aap.fermata.R.string.addon_name_tv);
	}

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.tv_fragment;
	}

	@Override
	public FloatingButton.Mediator getFloatingButtonMediator() {
		return TvFloatingButtonMediator.instance;
	}

	public void navBarItemReselected(int itemId) {
		getAdapter().setParent(getRootItem());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) return;

		TvAdapter a = getAdapter();
		if (a != null) a.animateAddButton(a.getParent());
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
		getMainActivity().getFloatingButton().clearAnimation();
	}

	public void addSource() {
		TvM3uFileSystemProvider prov = new TvM3uFileSystemProvider();
		prov.select(getMainActivity(), Collections.singletonList(TvM3uFileSystem.getInstance()))
				.main()
				.onFailure(this::failedToAddSource)
				.onSuccess(this::addM3uSource);
	}

	public TvRootItem getRootItem() {
		return TvAddon.getRootItem((DefaultMediaLib) getMainActivity().getLib());
	}

	@Override
	public void contributeToContextMenu(OverlayMenu.Builder b, MediaItemMenuHandler h) {
		if (!(h.getItem() instanceof TvM3uItem)) return;
		b.addItem(me.aap.fermata.R.id.edit, me.aap.fermata.R.drawable.edit,
				me.aap.fermata.R.string.edit)
				.setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		b.addItem(me.aap.fermata.R.id.delete, me.aap.fermata.R.drawable.delete,
				me.aap.fermata.R.string.delete)
				.setData(h.getItem())
				.setHandler(this::contextMenuItemSelected);
		super.contributeToContextMenu(b, h);
	}

	private boolean contextMenuItemSelected(OverlayMenuItem item) {
		int id = item.getItemId();
		if (id == me.aap.fermata.R.id.edit) {
			TvM3uItem i = item.getData();
			new TvM3uFileSystemProvider().edit(getMainActivity(), i.getResource()).onCompletion((ok, err) -> {
				if ((err != null) && !(err instanceof CancellationException)) {
					Log.e(err, "Failed to edit TV source ", i);
					UiUtils.showAlert(getContext(), err.getLocalizedMessage());
				}
				getMainActivity().showFragment(getFragmentId());
				if ((ok != null) && ok) i.refresh().thenRun(this::refresh);
			});
		} else if (id == me.aap.fermata.R.id.delete) {
			TvRootItem root = getRootItem();
			root.removeItem(item.getData()).onSuccess(v -> getAdapter().setParent(root));
		}
		return true;
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		super.contributeToNavBarMenu(builder);
		if (isRootItem()) return;
		TvAdapter a = getAdapter();

		if (a.getListView().isSelectionActive() && a.hasSelectable() && a.hasSelected()) {
			OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
			b.addItem(me.aap.fermata.R.id.favorites_add, me.aap.fermata.R.drawable.favorite,
					me.aap.fermata.R.string.favorites_add);
			getMainActivity().addPlaylistMenu(b, completed(a.getSelectedItems()));
		}
	}

	@Override
	protected boolean isSupportedItem(Item i) {
		return getRootItem().isChildItemId(i.getId());
	}

	@Override
	protected boolean isRefreshSupported() {
		return true;
	}

	private void addM3uSource(TvM3uFile m3u) {
		MainActivityDelegate a = getMainActivity();
		if (m3u != null) getRootItem().addSource(m3u);
		getAdapter().setParent(getRootItem());
		a.showFragment(getFragmentId());
	}

	private void failedToAddSource(Throwable ex) {
		getMainActivity().showFragment(me.aap.fermata.R.id.tv_fragment);
		if (isCancellation(ex)) return;

		App.get().getHandler().post(() -> {
			String msg = ex.getLocalizedMessage();
			UiUtils.showAlert(getContext(), getString(R.string.err_failed_to_add_tv_source,
					(msg != null) ? msg : ex.toString()));
		});
	}

	private boolean isRootItem() {
		BrowsableItem p = getAdapter().getParent();
		return (p == null) || (p instanceof TvRootItem);
	}

	private class TvAdapter extends ListAdapter {

		TvAdapter(MainActivityDelegate activity, BrowsableItem parent) {
			super(activity, parent);
			animateAddButton(parent);
		}

		@Override
		public FutureSupplier<?> setParent(BrowsableItem parent, boolean userAction) {
			return super.setParent(parent, userAction).onSuccess(v -> animateAddButton(parent));
		}

		public boolean isLongPressDragEnabled() {
			return isRootItem();
		}

		@Override
		protected void onItemDismiss(int position) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof TvRootItem) ((TvRootItem) i).removeItem(position);
			super.onItemDismiss(position);
		}

		@Override
		protected boolean onItemMove(int fromPosition, int toPosition) {
			BrowsableItem i = getAdapter().getParent();
			if (i instanceof MediaLib.Folders) ((MediaLib.Folders) i).moveItem(fromPosition, toPosition);
			return super.onItemMove(fromPosition, toPosition);
		}

		private void animateAddButton(BrowsableItem parent) {
			if (!(parent instanceof TvRootItem)) return;

			parent.getUnsortedChildren().onSuccess(c -> {
				if (!c.isEmpty()) return;

				FloatingButton fb = getMainActivity().getFloatingButton();
				fb.requestFocus();
				Animation shake = AnimationUtils.loadAnimation(getContext(), me.aap.utils.R.anim.shake_y_20);
				fb.startAnimation(shake);
			});
		}
	}
}
