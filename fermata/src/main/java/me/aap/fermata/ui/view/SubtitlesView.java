package me.aap.fermata.ui.view;

import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;
import static androidx.core.text.HtmlCompat.fromHtml;
import static java.util.Collections.newSetFromMap;
import static me.aap.fermata.media.sub.SubGrid.Position.BOTTOM_LEFT;
import static me.aap.fermata.ui.activity.MainActivityPrefs.L_SPLIT_PERCENT_SUB;
import static me.aap.fermata.ui.activity.MainActivityPrefs.P_SPLIT_PERCENT_SUB;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import me.aap.fermata.R;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;

/**
 * @author Andrey Pavlenko
 */
public class SubtitlesView extends SplitLayout
		implements BiConsumer<SubGrid.Position, Subtitles.Text> {
	private boolean single;
	private RecyclerView left;
	private RecyclerView right;
	private List<Subtitles> subtitles;

	public SubtitlesView(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
	}

	@Override
	protected int getLayout(boolean portrait) {
		return portrait ? R.layout.subtitles_layout : R.layout.subtitles_layout_land;
	}

	@Override
	protected Pref<DoubleSupplier> getSplitPercentPref(boolean portrait) {
		return portrait ? P_SPLIT_PERCENT_SUB : L_SPLIT_PERCENT_SUB;
	}

	protected Guideline getGuideline() {
		return findViewById(R.id.sub_guideline);
	}

	protected View getSplitLine() {
		return findViewById(R.id.sub_split_line);
	}

	protected AppCompatImageView getSplitHandle() {
		return findViewById(R.id.sub_split_handle);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (subtitles == null) return;
		var leftAdapter = (left == null) ? null : (SubAdapter) left.getAdapter();
		var rightAdapter = (right == null) ? null : (SubAdapter) right.getAdapter();
		var leftActive = (leftAdapter == null) ? -1 : leftAdapter.active;
		var rightActive = (rightAdapter == null) ? -1 : rightAdapter.active;
		left = right = null;
		setSubtitles(subtitles, leftActive, rightActive);
	}

	private RecyclerView getLeftList() {
		if (left == null) {
			left = findViewById(R.id.sub_left);
			left.setLayoutManager(new LinearLayoutManager(getContext()));
		}
		return left;
	}

	private RecyclerView getRightList() {
		if (right == null) {
			right = findViewById(R.id.sub_right);
			right.setLayoutManager(new LinearLayoutManager(getContext()));
		}
		return right;
	}

	public void setSubtitles(@Nullable SubGrid sg) {
		if (sg == null) {
			subtitles = null;
			getLeftList().setAdapter(null);
			getRightList().setAdapter(null);
		} else {
			List<Subtitles> list = new ArrayList<>(2);
			for (var e : sg) {
				if (!e.getValue().isEmpty()) list.add(e.getValue());
			}
			subtitles = list;
			setSubtitles(list, -1, -1);
		}
	}

	private void setSubtitles(List<Subtitles> list, int leftActive, int rightActive) {
		switch (list.size()) {
			case 0:
				list.add(new Subtitles.Builder().build());
			case 1:
				single = true;
				showHideRight(GONE);
				setAdapter(getLeftList(), list.get(0), leftActive);
				getRightList().setAdapter(null);
				break;
			case 2:
				single = false;
				showHideRight(VISIBLE);
				setAdapter(getLeftList(), list.get(0), leftActive);
				setAdapter(getRightList(), list.get(1), rightActive);
				break;
			default:
				throw new UnsupportedOperationException();
		}
	}

	private void setAdapter(RecyclerView v, Subtitles subtitles, int active) {
		var a = new SubAdapter(subtitles, active);
		v.setAdapter(a);

		if (active != -1) {
			v.scrollToPosition(active);
			return;
		}

		var eng = getActivity().getMediaSessionCallback().getEngine();
		if (eng == null) return;

		eng.getPosition().main().onSuccess(time -> {
			var next = subtitles.getNext(time);
			if (next == null) return;
			var idx = next.getIndex();
			a.setActive(idx);
			v.scrollToPosition(idx);
		});
	}

	@Override
	public void accept(SubGrid.Position position, Subtitles.Text text) {
		if (single) showText(getLeftList(), text);
		else if (position == BOTTOM_LEFT) showText(getLeftList(), text);
		else showText(getRightList(), text);
	}

	private void showText(RecyclerView list, Subtitles.Text text) {
		var a = (SubAdapter) list.getAdapter();
		if (a == null) return;

		if (text == null) {
			a.setActive(-1);
		} else {
			a.setActive(text.getIndex());
			list.scrollToPosition(text.getIndex());
		}
	}

	private void showHideRight(int visibility) {
		var gl = getGuideline();
		var lp = (ConstraintLayout.LayoutParams) gl.getLayoutParams();
		var views = new View[]{getRightList(), getSplitLine(), getSplitHandle()};
		for (var v : views) v.setVisibility(visibility);
		if (visibility == GONE) {
			lp.guidePercent = 1f;
		} else {
			lp.guidePercent = getActivity().getPrefs().getFloatPref(getSplitPercentPref(isPortrait()));
		}
		gl.setLayoutParams(lp);
	}

	private final class SubAdapter extends RecyclerView.Adapter<SubAdapter.Holder> {
		private final Set<Holder> holders = newSetFromMap(new WeakHashMap<>());
		private final Subtitles subtitles;
		int active;

		SubAdapter(Subtitles subtitles, int active) {
			this.subtitles = subtitles;
			this.active = active;
		}

		void setActive(int idx) {
			active = idx;
			for (var h : holders) h.itemView.setSelected(h.index == active);
		}

		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			var v = LayoutInflater.from(getContext()).inflate(R.layout.list_item, parent, false);
			var h = new Holder(v);
			holders.add(h);
			return h;
		}

		@Override
		public void onBindViewHolder(@NonNull Holder holder, int position) {
			var i = (TextView) holder.itemView;
			var t = subtitles.get(position);
			holder.index = t.getIndex();
			i.setSelected(holder.index == active);
			i.setText(fromHtml(t.getText(), FROM_HTML_MODE_LEGACY));
		}

		@Override
		public int getItemCount() {
			return subtitles.size();
		}

		private final class Holder extends RecyclerView.ViewHolder implements OnClickListener {
			int index = -1;

			public Holder(@NonNull View v) {
				super(v);
				v.setOnClickListener(this);
			}

			@Override
			public void onClick(View v) {
				if (index == -1) return;
				var eng = getActivity().getMediaSessionCallback().getEngine();
				if (eng != null) eng.setPosition(subtitles.get(index).getTime());
			}
		}
	}
}
