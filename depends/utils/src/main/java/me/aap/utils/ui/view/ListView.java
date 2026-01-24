package me.aap.utils.ui.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_DONE;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_UNKNOWN;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.toPx;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

import me.aap.utils.R;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class ListView<I> extends RecyclerView {
	@ColorInt
	private final int textColor;
	@StyleRes
	private final int textAppearance;
	@Nullable
	private final ColorStateList iconTint;
	@DrawableRes
	private final int itemBackground;
	@AttrRes
	private int defStyleAttr;
	private ItemAdapter<I> itemAdapter;
	private ProgressBar progressBar;
	private boolean hideProgressBar;
	private ItemClickListener<I> itemClickListener;
	private ItemsChangeListener<I> itemsChangeListener;
	private Consumer<Throwable> errorHandler;
	private Function<I, Boolean> filter;
	private Function<List<? extends I>, List<? extends I>> sort = this::sort;
	private I parent;
	private List<? extends I> items = Collections.emptyList();
	private FutureSupplier<?> loading = Completed.completedNull();

	public ListView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
		this(context, attrs, defStyleAttr, ID_NULL);
	}

	public ListView(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr, @StyleRes int defStyleRes) {
		super(context, attrs, defStyleAttr);
		this.defStyleAttr = defStyleAttr;

		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ListView, defStyleAttr, defStyleRes);
		iconTint = ta.getColorStateList(R.styleable.ListView_tint);
		itemBackground = ta.getResourceId(R.styleable.ListView_background, ID_NULL);
		textColor = ta.getColor(R.styleable.ListView_android_textColor, Color.BLACK);
		textAppearance = ta.getResourceId(R.styleable.ListView_android_textAppearance,
				com.google.android.material.R.attr.textAppearanceBody1);
		ta.recycle();

		setAdapter(createAdapter());
		addItemDecoration(new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
		setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
	}

	@ColorInt
	public int getTextColor() {
		return textColor;
	}

	@StyleRes
	public int getTextAppearance() {
		return textAppearance;
	}

	@Nullable
	public ColorStateList getIconTint() {
		return iconTint;
	}

	@DrawableRes
	public int getItemBackground() {
		return itemBackground;
	}

	public I getParentItem() {
		return parent;
	}

	public List<? extends I> getItems() {
		return items;
	}

	public void setParentItems() {
		if (parent != null) setItems(itemAdapter.getParent(parent));
	}

	public void setItems(@NonNull String findParent) {
		if (parent != null) setItems(itemAdapter.findParent(parent, findParent));
	}

	public void setItems(@NonNull I parent) {
		cancel();
		loading = itemAdapter.getChildren(parent).main()
				.onSuccess(items -> setItems(parent, items))
				.onFailure(this::onFailure)
				.onCancel(this::onCancel)
				.onProgress(this::onProgress);
		loading();
	}

	public void setItems(FutureSupplier<BiHolder<? extends I, List<? extends I>>> supplier) {
		cancel();
		loading = supplier.main()
				.onSuccess(h -> {
					if (h != null) setItems(h.getValue1(), h.getValue2());
				})
				.onFailure(this::onFailure)
				.onCancel(this::onCancel)
				.onProgress(this::onProgress);
		loading();
	}

	public void setItems(@Nullable I parent, @NonNull List<? extends I> items) {
		onCancel();
		this.parent = parent;

		if ((filter == null) || items.isEmpty()) {
			this.items = sort.apply(items);
		} else {
			List<I> filtered = new ArrayList<>(items.size());
			for (I i : items) {
				if (filter.apply(i)) filtered.add(i);
			}
			this.items = sort.apply(filtered);
		}

		notifyDataSetChanged();
	}

	@SuppressWarnings("unchecked")
	private void notifyDataSetChanged() {
		RecyclerView.Adapter<Holder> a = getAdapter();
		if (a != null) a.notifyDataSetChanged();
		if (itemsChangeListener != null) itemsChangeListener.onListItemsChange(parent, items);
	}

	public void setItemAdapter(ItemAdapter<I> itemAdapter) {
		this.itemAdapter = itemAdapter;
	}

	public void setProgressBar(ProgressBar progressBar, boolean hideProgressBar) {
		this.progressBar = progressBar;
		this.hideProgressBar = hideProgressBar;
	}

	public void setItemClickListener(@Nullable ItemClickListener<I> itemClickListener) {
		this.itemClickListener = itemClickListener;
	}

	public void setItemsChangeListener(@Nullable ItemsChangeListener<I> itemsChangeListener) {
		this.itemsChangeListener = itemsChangeListener;
	}

	public void setErrorHandler(@NonNull Consumer<Throwable> errorHandler) {
		this.errorHandler = errorHandler;
	}

	public void setFilter(@Nullable Function<I, Boolean> filter) {
		this.filter = filter;
	}

	public void setSort(@NonNull Function<List<? extends I>, List<? extends I>> sort) {
		this.sort = sort;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	protected List<? extends I> sort(List<? extends I> list) {
		if (list.isEmpty()) return Collections.emptyList();
		if (!(list.get(0) instanceof Comparable)) return list;
		List l = new ArrayList(list);
		Collections.sort(l);
		return l;
	}

	protected void onItemClick(I item) {
		if ((itemClickListener != null) && (itemClickListener.onListItemClick(item))) return;
		if ((itemAdapter == null) || !itemAdapter.hasChildren(item)) return;
		setItems(item);
	}

	protected boolean onItemLongClick(I item) {
		return (itemClickListener != null) && (itemClickListener.onListItemLongClick(item));
	}

	public void cleanUp() {
		cancel();
		loading = Completed.completedNull();
		parent = null;
		progressBar = null;
		items = Collections.emptyList();
		notifyDataSetChanged();
	}

	protected RecyclerView.Adapter<Holder> createAdapter() {
		return new RecyclerView.Adapter<Holder>() {

			@NonNull
			@Override
			public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				return ListView.this.createViewHolder();
			}

			@Override
			public void onBindViewHolder(@NonNull Holder holder, int position) {
				holder.setItem(getItems().get(position));
			}

			@Override
			public int getItemCount() {
				return getItems().size();
			}
		};
	}

	protected Holder createViewHolder() {
		return new Holder();
	}

	private void cancel() {
		loading.cancel();
		loading = Completed.completedNull();
	}

	private void onCancel() {
		onProgress(null, PROGRESS_DONE, PROGRESS_DONE);
	}

	private void loading() {
		if ((progressBar == null) || loading.isDone()) return;
		onProgress(null, 0, PROGRESS_UNKNOWN);
	}

	private <T> void onProgress(T incomplete, int progress, int total) {
		if (progressBar == null) return;

		if (progress == PROGRESS_DONE) {
			if (hideProgressBar) {
				progressBar.setVisibility(GONE);
			} else {
				progressBar.setVisibility(VISIBLE);
				progressBar.setIndeterminate(false);
				progressBar.setMax(total);
				progressBar.setProgress(total);
			}
		} else {
			if (total != PROGRESS_UNKNOWN) {
				progressBar.setIndeterminate(false);
				progressBar.setMax(total);
				progressBar.setProgress(progress);
			} else {
				progressBar.setIndeterminate(true);
			}
		}
	}

	public void onFailure(Throwable err) {
		onCancel();
		Log.w(err, "Failed to load items");
		if (errorHandler != null) errorHandler.accept(err);
		else Toast.makeText(getContext(), err.toString(), Toast.LENGTH_LONG).show();
	}

	public interface ItemAdapter<I> {

		@Nullable
		Drawable getIcon(I item);

		@NonNull
		CharSequence getText(I item);

		boolean hasChildren(I item);

		FutureSupplier<List<I>> getChildren(I item);

		FutureSupplier<BiHolder<? extends I, List<? extends I>>> getParent(I item);

		FutureSupplier<BiHolder<? extends I, List<? extends I>>> findParent(I current, String find);
	}

	public interface ItemClickListener<I> {

		boolean onListItemClick(I item);

		boolean onListItemLongClick(I item);
	}

	public interface ItemsChangeListener<I> {
		void onListItemsChange(@Nullable I parent, @NonNull List<? extends I> items);
	}

	protected class Holder extends ViewHolder implements OnClickListener, OnLongClickListener {
		private I item;

		public Holder() {
			this(new ScalableTextView(getContext(), null, defStyleAttr));
		}

		public Holder(@NonNull View itemView) {
			super(itemView);
			RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
			itemView.setFocusable(true);
			itemView.setLayoutParams(lp);
			itemView.setOnClickListener(this);
			itemView.setOnLongClickListener(this);
			itemView.setBackgroundResource(getItemBackground());

			if (itemView instanceof TextView) {
				TextView t = (TextView) itemView;
				t.setTextAppearance(getTextAppearance());
				t.setTextColor(getTextColor());
				t.setCompoundDrawableTintList(getIconTint());
				int pad = (int) toPx(getContext(), 5);
				t.setCompoundDrawablePadding(pad);
				t.setPadding(pad, 2 * pad, pad, 2 * pad);
			}
		}

		public I getItem() {
			return item;
		}

		public void setItem(I item) {
			this.item = item;
			if (item != null) initView(item);
		}

		protected void initView(I item) {
			if (!(itemView instanceof TextView) || (itemAdapter == null)) return;
			TextView t = (TextView) itemView;
			t.setText(itemAdapter.getText(item));
			t.setCompoundDrawablesWithIntrinsicBounds(itemAdapter.getIcon(item), null, null, null);
		}

		@Override
		public void onClick(View v) {
			I i = getItem();
			if (i != null) onItemClick(i);
		}

		@Override
		public boolean onLongClick(View v) {
			I i = getItem();
			return (i != null) ? onItemLongClick(i) : false;
		}
	}
}
