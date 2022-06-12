package me.aap.fermata.addon.felex.view;

import static me.aap.fermata.addon.felex.FelexAddon.CACHE_FOLDER;
import static me.aap.fermata.addon.felex.FelexAddon.DICT_FOLDER;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.felex.R;
import me.aap.fermata.addon.felex.dict.Dict;
import me.aap.fermata.addon.felex.tutor.DictTutor;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class FelexFragment extends MainActivityFragment implements
		MainActivityListener, PreferenceStore.Listener, ToolBarView.Listener {
	private DictTutor tutor;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.felex_fragment;
	}

	@Override
	public boolean onBackPressed() {
		return view().onBackPressed();
	}

	@Override
	public boolean isRootPage() {
		return view().isRoot();
	}

	@Override
	public CharSequence getTitle() {
		return view().getTitle();
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarMediator.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		activity().onSuccess(a -> {
			a.addBroadcastListener(this);
			a.getPrefs().addBroadcastListener(this);
			a.getToolBar().addBroadcastListener(this);
			FermataApplication.get().getPreferenceStore().addBroadcastListener(this);
		});
		return inflater.inflate(R.layout.felex_list, container, false);
	}

	@Override
	public void onDestroyView() {
		view().close();
		activity().onSuccess(this::cleanUp);
		super.onDestroyView();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		closeTutor();
		super.onHiddenChanged(hidden);
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder b) {
		FutureSupplier<List<Dict>> f = view().getDictMgr().getDictionaries();
		if (!f.isDone() || f.peek(Collections::emptyList).isEmpty()) return;
		b.addItem(R.id.start_tutor, me.aap.fermata.R.drawable.record_voice,
				R.string.start_tutor).setSubmenu(sb -> {
			sb.setSelectionHandler(this::navBarMenuItemSelected);
			sb.addItem(R.id.start_tutor_dir, R.string.start_tutor_dir);
			sb.addItem(R.id.start_tutor_rev, R.string.start_tutor_rev);
			sb.addItem(R.id.start_tutor_mix, R.string.start_tutor_mix);
			sb.addItem(R.id.start_tutor_listen, R.string.start_tutor_listen);
		});
		super.contributeToNavBarMenu(b);
	}

	protected boolean navBarMenuItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == R.id.start_tutor_dir) {
			startTutor(DictTutor.MODE_DIRECT);
		} else if (item.getItemId() == R.id.start_tutor_rev) {
			startTutor(DictTutor.MODE_REVERSE);
		} else if (item.getItemId() == R.id.start_tutor_mix) {
			startTutor(DictTutor.MODE_MIXED);
		} else if (item.getItemId() == R.id.start_tutor_listen) {
			startTutor(DictTutor.MODE_LISTENING);
		}
		return true;
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) cleanUp(a);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		activity().onSuccess(a -> {
			if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) {
				View v = getView();
				if (v instanceof FelexListView) ((FelexListView) v).scale(a.getTextIconSize());
			} else if (prefs.contains(DICT_FOLDER) || prefs.contains(CACHE_FOLDER)) {
				closeTutor();
				view().onFolderChanged();
			}
		});
	}

	@Override
	public void onToolBarEvent(ToolBarView tb, byte event) {
		if (event == FILTER_CHANGED) {
			view().setFilter(tb.getFilter().getText().toString());
		}
	}

	@NonNull
	private FelexListView view() {
		return (FelexListView) requireView();
	}

	private void startTutor(byte mode) {
		closeTutor();
		activity().onSuccess(a -> {
			FelexListView v = view();
			Dict d = v.getCurrentDict();
			FutureSupplier<DictTutor> f = (d != null) ? DictTutor.create(a, d, mode)
					: v.getDictMgr().getDictionaries().then(list ->
					list.isEmpty() ? completedNull() : DictTutor.create(a, list.get(0), mode).map(t -> t));
			f.onCompletion((t, err) -> {
				closeTutor();
				if (err != null) {
					showAlert(requireContext(), err.toString());
					return;
				}
				if (t == null) return;
				tutor = t;
				t.start();
			});
		});
	}

	private void closeTutor() {
		if (tutor != null) {
			tutor.close();
			tutor = null;
		}
	}

	private void cleanUp(MainActivityDelegate a) {
		closeTutor();
		a.removeBroadcastListener(this);
		a.getPrefs().removeBroadcastListener(this);
		a.getToolBar().removeBroadcastListener(this);
		FermataApplication.get().getPreferenceStore().removeBroadcastListener(this);
	}

	private FutureSupplier<MainActivityDelegate> activity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	private static final class ToolBarMediator implements ToolBarView.Mediator.BackTitleFilter {
		static final ToolBarView.Mediator instance = new ToolBarMediator();
	}
}
