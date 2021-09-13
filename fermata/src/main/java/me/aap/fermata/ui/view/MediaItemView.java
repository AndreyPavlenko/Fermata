package me.aap.fermata.ui.view;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;
import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_DONE;
import static me.aap.utils.misc.MiscUtils.ifNull;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_DESTROY;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.EpgItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.pref.PlayableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemView extends ConstraintLayout implements OnLongClickListener,
		OnCheckedChangeListener, Item.ChangeListener {
	private static final RotateAnimation rotate = new RotateAnimation(0, 360,
			Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
	private static Drawable loadingDrawable;
	@ColorInt
	private static int iconColor;
	private final ColorStateList iconTint;
	@Nullable
	private MediaItemViewHolder holder;
	private ProgressUpdater progressUpdater;
	@ColorInt
	private final int mediaItemMarkColor;
	private VectorDrawableCompat watchedVideoDrawable;
	private VectorDrawableCompat watchingVideoDrawable;

	public MediaItemView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs, R.attr.appMediaItemStyle);
		TypedArray ta = ctx.obtainStyledAttributes(attrs, new int[]{
				R.attr.colorOnSecondary,
				R.attr.elevation,
				R.attr.mediaItemMarkColor
		}, R.attr.appMediaItemStyle, R.style.AppTheme_MediaItemStyle);
		iconColor = ta.getColor(0, 0);
		setElevation(ta.getDimension(1, 0));
		mediaItemMarkColor = ta.getColor(2, 0);
		ta.recycle();
		applyLayout(ctx, getMainActivity());
		iconTint = getIcon().getImageTintList();
		setLongClickable(true);
		setOnLongClickListener(this);
		getCheckBox().setOnCheckedChangeListener(this);
		setBackgroundResource(R.drawable.media_item_bg);
		setFocusable(true);
	}

	public void applyLayout(Context ctx, MainActivityDelegate a) {
		boolean grid = a.isGridView();
		float scale = a.getPrefs().getMediaItemScalePref();
		removeAllViews();

		if (grid) {
			inflate(ctx, R.layout.media_item_grid_layout, this);
			setTextSize(ctx, scale);
		} else {
			inflate(ctx, R.layout.media_item_list_layout, this);
			setTextSize(ctx, scale);
			int iconSize = (int) (getTitle().getTextSize() + getSubtitle().getTextSize() + toPx(ctx, 10));
			ViewGroup.LayoutParams lp = getIcon().getLayoutParams();
			lp.height = iconSize;
			lp.width = iconSize;
		}
	}

	private void setTextSize(Context ctx, float scale) {
		TypedArray ta = ctx.obtainStyledAttributes(null, new int[]{
				R.attr.textAppearanceListItem,
				R.attr.textAppearanceCaption
		}, R.attr.appMediaItemStyle, R.style.AppTheme_MediaItemStyle);
		int titleTextAppearance = ta.getResourceId(0, 0);
		int subtitleTextAppearance = ta.getResourceId(1, 0);
		ta.recycle();

		ta = ctx.obtainStyledAttributes(titleTextAppearance, new int[]{android.R.attr.textSize});
		int titleTextSize = ta.getDimensionPixelSize(0, 0);
		ta.recycle();
		ta = ctx.obtainStyledAttributes(subtitleTextAppearance, new int[]{android.R.attr.textSize});
		int subtitleTextSize = ta.getDimensionPixelSize(0, 0);
		ta.recycle();

		getTitle().setTextSize(COMPLEX_UNIT_PX, titleTextSize * scale);
		getSubtitle().setTextSize(COMPLEX_UNIT_PX, subtitleTextSize * scale);
	}

	public void setHolder(@Nullable MediaItemViewHolder holder) {
		this.holder = holder;
	}

	@Nullable
	public MediaItemViewHolder getHolder() {
		return holder;
	}

	@Nullable
	public MediaItemListView getListView() {
		MediaItemViewHolder h = getHolder();
		return (h != null) ? h.getListView() : null;
	}

	@Nullable
	public MediaItemWrapper getItemWrapper() {
		MediaItemViewHolder h = getHolder();
		return (h != null) ? h.getItemWrapper() : null;
	}

	@Nullable
	public Item getItem() {
		MediaItemWrapper w = getItemWrapper();
		return (w != null) ? w.getItem() : null;
	}

	public void rebind(@Nullable MediaItemWrapper oldItem, @Nullable MediaItemWrapper newItem) {
		if (oldItem == newItem) return;
		if (oldItem != null) oldItem.getItem().removeChangeListener(this);
		if (newItem == null) return;
		boolean hasListener = newItem.getItem().addChangeListener(this);
		load(newItem, !hasListener).onCompletion((r, err) -> {
			if (getItemWrapper() != newItem) return;
			if (err != null) Log.e(err, "Failed to load media description: ", newItem);
		});
	}

	@Override
	public void mediaItemChanged(Item i) {
		MediaItemWrapper w = getItemWrapper();
		if ((w != null) && (w.getItem() == i)) load(w, false);
	}

	private FutureSupplier<MediaDescriptionCompat> load(MediaItemWrapper w, boolean showLoading) {
		Item i = w.getItem();

		if (i instanceof EpgItem) {
			EpgItem e = (EpgItem) i;
			setProgress(i, e.getStartTime(), e.getEndTime());
		} else {
			setProgress(i, 0, 0);
		}

		FutureSupplier<MediaDescriptionCompat> load = i.getMediaDescription().main()
				.addConsumer((result, fail, progress, total) -> {
					if (getItemWrapper() != w) return;

					if (fail != null) {
						Log.e(fail, "Failed to load media description: ", i);
						setDefaults(i, showLoading);
						return;
					}

					CharSequence sub = result.getSubtitle();
					getTitle().setText(ifNull(result.getTitle(), i::getName));
					getSubtitle().setText(sub);

					if ((progress >= 3) && (i instanceof StreamItem)) {
						Bundle b = result.getExtras();
						if (b != null) setProgress(i, b.getLong(STREAM_START_TIME), b.getLong(STREAM_END_TIME));
					}

					if (progress != PROGRESS_DONE) return;

					Uri uri = result.getIconUri();

					if (uri != null) {
						FutureSupplier<Bitmap> loadIcon = i.getLib().getBitmap(uri.toString(), true, true)
								.main().onCompletion((bm, err) -> {
									if (getItemWrapper() != w) return;

									ImageView icon = getIcon();
									icon.clearAnimation();

									if (bm != null) {
										icon.setImageTintList(null);
										icon.setImageBitmap(bm);
									} else {
										icon.setImageTintList(iconTint);
										icon.setImageResource(i.getIcon());
									}
								});

						if (!loadIcon.isDone()) {
							ImageView icon = getIcon();
							icon.clearAnimation();
							icon.setImageTintList(iconTint);
							icon.setImageResource(i.getIcon());
						}
					} else {
						ImageView icon = getIcon();
						icon.clearAnimation();
						icon.setImageTintList(iconTint);
						icon.setImageResource(i.getIcon());
					}
				});

		if (!load.isDone()) setDefaults(i, showLoading);
		return load;
	}

	private void setProgress(Item i, long start, long end) {
		LinearProgressIndicator p = getProgress();

		if (progressUpdater != null) {
			progressUpdater.cancel();
			progressUpdater = null;
		}

		if ((start > 0) && (end > start)) {
			progressUpdater = new ProgressUpdater(i, p, start, end);
			progressUpdater.run();
			p.setVisibility(VISIBLE);
		} else {
			p.setVisibility(GONE);
		}
	}

	private void setDefaults(Item i, boolean loading) {
		ImageView icon = getIcon();
		getTitle().setText(i.getName());

		if (loading) {
			rotate.setDuration(1000);
			rotate.setRepeatCount(Animation.INFINITE);
			icon.setImageDrawable(getLoadingDrawable(getContext()));
			icon.startAnimation(rotate);
			getSubtitle().setText(R.string.loading);
		} else {
			icon.clearAnimation();
			icon.setImageTintList(iconTint);
			icon.setImageResource(i.getIcon());
			getSubtitle().setText("");
		}
	}

	private static Drawable getLoadingDrawable(Context ctx) {
		if (loadingDrawable == null) {
			loadingDrawable = ContextCompat.getDrawable(ctx, R.drawable.loading);
			Objects.requireNonNull(loadingDrawable).setTint(iconColor);
			MainActivityDelegate.get(ctx)
					.addBroadcastListener(MediaItemView::onActivityDestroyEvent, ACTIVITY_DESTROY);
		}

		return loadingDrawable;
	}

	private static void onActivityDestroyEvent(ActivityDelegate a, long e) {
		loadingDrawable = null;
		a.removeBroadcastListener(MediaItemView::onActivityDestroyEvent);
	}

	public ImageView getIcon() {
		return (ImageView) getChildAt(0);
	}

	public TextView getTitle() {
		return (TextView) getChildAt(1);
	}

	public TextView getSubtitle() {
		return (TextView) getChildAt(2);
	}

	public LinearProgressIndicator getProgress() {
		return (LinearProgressIndicator) getChildAt(3);
	}

	public MaterialCheckBox getCheckBox() {
		return (MaterialCheckBox) getChildAt(4);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		MediaItemWrapper w = getItemWrapper();
		if (w != null) w.setSelected(isChecked, false);
	}

	@Override
	protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		Item item = getItem();
		if ((visibility == VISIBLE) && (item != null)) refreshState();
	}

	@Override
	public void onDrawForeground(Canvas canvas) {
		super.onDrawForeground(canvas);
		Item item = getItem();
		if (!(item instanceof PlayableItem) || (item instanceof StreamItem)) return;

		PlayableItem p = (PlayableItem) item;
		if (!p.isVideo()) return;

		PlayableItemPrefs prefs = p.getPrefs();
		VectorDrawableCompat d;

		if (prefs.getWatchedPref()) {
			d = watchedVideoDrawable;
			if (d == null) {
				d = watchedVideoDrawable = VectorDrawableCompat.create(getResources(), R.drawable.done, null);
				if (d == null) return;
				d.setTint(mediaItemMarkColor);
			}
		} else if (prefs.getPositionPref() > 0) {
			d = watchingVideoDrawable;
			if (d == null) {
				d = watchingVideoDrawable = VectorDrawableCompat.create(getResources(), R.drawable.watching, null);
				if (d == null) return;
				d.setTint(mediaItemMarkColor);
			}
		} else {
			return;
		}

		ImageView i = getIcon();
		int l = i.getLeft();
		int t = i.getTop();
		int r = i.getRight();
		int b = i.getBottom();
		d.setBounds(l + (r - l) / 3, t + (b - t) / 3, r, b);
		d.draw(canvas);
	}

	public void refresh() {
		MediaItemWrapper w = getItemWrapper();
		if (w == null) return;
		rebind(null, w);
		refreshState(w.getItem());
	}

	public void refreshState() {
		MediaItemWrapper w = getItemWrapper();
		if (w == null) return;
		Item item = w.getItem();
		if ((item instanceof PlayableItem) && ((PlayableItem) item).isVideo()) rebind(null, w);
		refreshState(item);
	}

	private void refreshState(Item item) {
		MainActivityDelegate a = getMainActivity();
		refreshCheckbox();

		if (item instanceof EpgItem) {
			EpgItem e = (EpgItem) item;
			long time = System.currentTimeMillis();
			setSelected(false);

			if ((e.getStartTime() <= time) && (e.getEndTime() > time)) {
				setState(Typeface.BOLD, false);
				a.postDelayed(() -> {
					if (getItem() != e) return;
					refreshState(e);
					EpgItem next = e.getNext();

					if (next != null) {
						ActivityFragment f = a.getActiveFragment();
						if (f instanceof MediaLibFragment) {
							MediaItemView focus = ((MediaLibFragment) f).getListView().focusTo(next);
							if (focus != null) focus.refreshState();
						}
					}
				}, e.getEndTime() - time);

				return;
			}
		}

		if (item.equals(a.getCurrentPlayable())) {
			setSelected(true);
			setState(Typeface.BOLD, true);
		} else {
			boolean last = ((item instanceof PlayableItem) && ((PlayableItem) item).isLastPlayed());
			int type = last ? Typeface.BOLD : Typeface.NORMAL;
			setSelected(false);
			setState(type, last);
		}
	}

	private void setState(int surfaceType, boolean activated) {
		TextView t = getTitle();
		t.setTypeface(null, surfaceType);
		t.setActivated(activated);
		t = getSubtitle();
		t.setTypeface(null, surfaceType);
		t.setActivated(activated);
	}

	public void refreshCheckbox() {
		MediaItemWrapper w = getItemWrapper();
		if (w == null) return;
		MaterialCheckBox cb = getCheckBox();

		if (!w.isSelectionSupported()) {
			cb.setVisibility(GONE);
			return;
		}

		MediaItemListView l = getListView();

		if ((l != null) && l.isSelectionActive()) {
			cb.setVisibility(VISIBLE);
			cb.setChecked(w.isSelected());
		} else {
			cb.setVisibility(GONE);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		MainActivityDelegate a = getMainActivity();
		OverlayMenu menu = a.getContextMenu();
		MediaItemMenuHandler handler = new MediaItemMenuHandler(menu, this);
		MediaItemListView l = getListView();
		if (l != null) getListView().discardSelection();
		handler.show();
		return true;
	}


	@Override
	protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
		super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
		if (!BuildConfig.AUTO || !gainFocus) return;

		// The next item in the list must always be visible for rotary input scrolling
		MediaItemListView lv = getListView();
		if (lv == null) return;
		int idx = lv.indexOfChild(this);

		if (idx == 0) {
			List<MediaItemWrapper> l = lv.getAdapter().getList();
			idx = l.indexOf(getItemWrapper());
			if (idx == 0) return;
			idx--;
		} else if (idx == (lv.getChildCount() - 1)) {
			List<MediaItemWrapper> l = lv.getAdapter().getList();
			idx = l.indexOf(getItemWrapper());
			if (idx == (l.size() - 1)) return;
			idx++;
		} else {
			return;
		}

		lv.scrollToPosition(idx, false);
	}

	void hideMenu() {
		OverlayMenu menu = getMainActivity().findViewById(R.id.context_menu);
		menu.hide();
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private final class ProgressUpdater implements Runnable, Cancellable {
		private final Item item;
		private final LinearProgressIndicator progress;
		private final long start;
		private final long end;
		private Cancellable scheduled;

		private ProgressUpdater(Item item, LinearProgressIndicator progress, long start, long end) {
			this.item = item;
			this.progress = progress;
			this.start = start;
			this.end = end;
		}

		@Override
		public void run() {
			if (!isValid()) return;
			long time = System.currentTimeMillis();

			if (time >= end) {
				progressUpdater = null;
				progress.setProgressCompat(100, true);
				update();
			} else if (start > time) {
				progress.setProgressCompat(0, true);
				runAt(time, start);
			} else {
				long dur = end - start;
				int prog = (int) ((time - start) * 100 / dur);
				progress.setProgressCompat(prog, true);

				if (prog >= 99) {
					runAt(time, end);
				} else {
					long upTime = start + (prog + 1) * dur / 100;
					runAt(time, Math.min(upTime, end));
				}
			}
		}

		@Override
		public boolean cancel() {
			return (scheduled != null) && scheduled.cancel();
		}

		private boolean isValid() {
			return (progressUpdater == this) && (item == getItem()) && (item != null);
		}

		private void runAt(long curTime, long atTime) {
			if (scheduled != null) scheduled.cancel();
			scheduled = getMainActivity().postDelayed(this, atTime - curTime);
		}

		private void update() {
			item.getMediaItemDescription().main().onSuccess(d -> {
				if (!isValid()) return;
				Bundle b = d.getExtras();
				if (b != null) {
					long start = b.getLong(STREAM_START_TIME);
					long end = b.getLong(STREAM_END_TIME);
					if ((start == this.start) && (end == this.end)) return;
				}
				refresh();
			});
		}
	}
}
