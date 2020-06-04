package me.aap.fermata.ui.view;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.pref.PreferenceStore;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemListView extends RecyclerView implements PreferenceStore.Listener {
	private boolean isSelectionActive;

	public MediaItemListView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		configure(ctx.getResources().getConfiguration());
		MainActivityDelegate.get(ctx).getPrefs().addBroadcastListener(this);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		configure(newConfig);
	}

	private void configure(Configuration cfg) {
		Context ctx = getContext();
		MainActivityDelegate a = MainActivityDelegate.get(ctx);
		if (a == null) return;

		boolean grid = a.getPrefs().getGridViewPref();

		if (grid) {
			int span = Math.max(cfg.screenWidthDp / 128, 2);
			setLayoutManager(new GridLayoutManager(ctx, span));
		} else {
			setLayoutManager(new LinearLayoutManager(ctx));
		}
	}

	@NonNull
	@Override
	public MediaItemListViewAdapter getAdapter() {
		return (MediaItemListViewAdapter) requireNonNull(super.getAdapter());
	}

	public boolean isSelectionActive() {
		return isSelectionActive;
	}

	public void select(boolean select) {
		if (!select && !isSelectionActive) return;

		boolean selectAll = isSelectionActive;
		isSelectionActive = select;

		for (MediaItemWrapper w : getAdapter().getList()) {
			if (selectAll) w.setSelected(select, true);
			else w.refreshViewCheckbox();
		}
	}

	public void discardSelection() {
		select(false);
	}

	public void refresh() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refresh();
		}
	}

	public void refreshState() {
		for (int childCount = getChildCount(), i = 0; i < childCount; ++i) {
			MediaItemViewHolder h = (MediaItemViewHolder) getChildViewHolder(getChildAt(i));
			h.getItemView().refreshState();
		}
	}

	@Override
	public void smoothScrollToPosition(int position) {
		scrollToPosition(position);
	}

	@Override
	public void scrollToPosition(int position) {
		super.scrollToPosition(position);
		List<MediaItemWrapper> list = getAdapter().getList();
		if ((position < 0) || (position >= list.size())) return;
		View v = list.get(position).getView();
		if (v != null) v.requestFocus();
	}

	@Override
	public View focusSearch(View focused, int direction) {
		if ((direction == FOCUS_UP) && (focused instanceof MediaItemView)) {
			List<MediaItemWrapper> list = getAdapter().getList();

			if ((list != null) && !list.isEmpty()) {
				MediaItemView i = (MediaItemView) focused;

				if (list.get(0) == i.getItemWrapper()) {
					View v = MainActivityDelegate.get(getContext()).getToolBar()
							.findViewById(R.id.tool_bar_back_button);
					if (v.getVisibility() == VISIBLE) return v;
				}
			}
		}

		return super.focusSearch(focused, direction);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(MainActivityPrefs.GRID_VIEW)) {
			configure(getContext().getResources().getConfiguration());
		}
	}
}
