package me.aap.fermata.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.List;
import java.util.Objects;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.MiscUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.menu.OverlayMenu;

import static me.aap.utils.function.ProgressiveResultConsumer.PROGRESS_DONE;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_DESTROY;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemView extends ConstraintLayout implements OnLongClickListener,
		OnCheckedChangeListener, PreferenceStore.Listener {
	private static final RotateAnimation rotate = new RotateAnimation(0, 360,
			Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
	private static Drawable loadingDrawable;
	@ColorInt
	private static int iconColor;
	private final ColorStateList iconTint;
	private MediaItemWrapper itemWrapper;
	private MediaItemListView listView;

	public MediaItemView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs, R.attr.appMediaItemStyle);
		TypedArray ta = ctx.obtainStyledAttributes(attrs, new int[]{
				R.attr.colorOnSecondary,
				R.attr.elevation
		}, R.attr.appMediaItemStyle, R.style.AppTheme_MediaItemStyle);
		iconColor = ta.getColor(0, 0);
		setElevation(ta.getDimension(1, 0));
		ta.recycle();
		applyLayout(ctx);
		iconTint = getIcon().getImageTintList();
		setLongClickable(true);
		setOnLongClickListener(this);
		getCheckBox().setOnCheckedChangeListener(this);
		setBackgroundResource(R.drawable.media_item_bg);
		setFocusable(true);
		getMainActivity().getPrefs().addBroadcastListener(this);
	}

	private void applyLayout(Context ctx) {
		MainActivityDelegate a = MainActivityDelegate.get(ctx);
		if (a == null) return;

		boolean grid = a.getPrefs().getGridViewPref();
		removeAllViews();

		if (grid) {
			inflate(ctx, R.layout.media_item_grid_layout, this);
		} else {
			inflate(ctx, R.layout.media_item_list_layout, this);
			int iconSize = (int) (getTitle().getTextSize() + getSubtitle().getTextSize() + toPx(ctx, 10));
			ViewGroup.LayoutParams lp = getIcon().getLayoutParams();
			lp.height = iconSize;
			lp.width = iconSize;
		}
	}

	public MediaItemWrapper getItemWrapper() {
		return itemWrapper;
	}

	public Item getItem() {
		return getItemWrapper().getItem();
	}

	public void setItemWrapper(MediaItemWrapper wrapper) {
		itemWrapper = wrapper;
		wrapper.setView(this);
		Item i = wrapper.getItem();

		FutureSupplier<MediaDescriptionCompat> load = i.getMediaDescription().main()
				.addConsumer((result, fail, progress, total) -> {
					if (wrapper != getItemWrapper()) return;

					if (fail != null) {
						Log.e(fail, "Failed to load media description: ", i);
						setDefaults(i, false);
						return;
					}

					CharSequence sub = result.getSubtitle();
					getTitle().setText(MiscUtils.ifNull(result.getTitle(), () -> i.getResource().getName()));
					if (sub != null) getSubtitle().setText(sub);

					if (progress != PROGRESS_DONE) return;

					Uri uri = result.getIconUri();

					if (uri != null) {
						FutureSupplier<Bitmap> loadIcon = i.getLib().getBitmap(uri.toString(), true, true)
								.main().onCompletion((bm, err) -> {
									if (wrapper != getItemWrapper()) return;

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

		if (!load.isDone()) setDefaults(i, true);
	}

	private void setDefaults(Item i, boolean loading) {
		if (i instanceof BrowsableItem) {
			getTitle().setText(((BrowsableItem) i).getName());
		} else {
			getTitle().setText(i.getResource().getName());
		}

		ImageView icon = getIcon();

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
			MainActivityDelegate.get(ctx).addBroadcastListener(MediaItemView::onActivityDestroyEvent, ACTIVITY_DESTROY);
		}

		return loadingDrawable;
	}

	private static void onActivityDestroyEvent(ActivityDelegate a, long e) {
		loadingDrawable = null;
	}

	public void cancelLoading() {
		if (itemWrapper != null) itemWrapper = new MediaItemWrapper(itemWrapper.getItem());
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

	public MaterialCheckBox getCheckBox() {
		return (MaterialCheckBox) getChildAt(3);
	}

	@Override
	public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		getItemWrapper().setSelected(isChecked, false);
	}

	public MediaItemListView getListView() {
		return listView;
	}

	public void setListView(MediaItemListView listView) {
		this.listView = listView;
	}

	@Override
	protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
		super.onVisibilityChanged(changedView, visibility);
		Item item = getItem();

		if ((visibility == VISIBLE) && (item != null)) {
			refreshState();
		}
	}

	public void refresh() {
		MediaItemWrapper w = getItemWrapper();
		setItemWrapper(w);
		refreshState(w.getItem());
	}

	public void refreshState() {
		MediaItemWrapper w = getItemWrapper();
		if (w == null) return;

		Item item = w.getItem();
		if ((item instanceof PlayableItem) && ((PlayableItem) item).isVideo()) setItemWrapper(w);
		refreshState(item);
	}

	private void refreshState(Item item) {
		boolean last = ((item instanceof PlayableItem) && ((PlayableItem) item).isLastPlayed());
		int type = last ? Typeface.BOLD : Typeface.NORMAL;

		TextView t = getTitle();
		t.setTypeface(null, type);
		t.setActivated(last);
		t = getSubtitle();
		t.setTypeface(null, type);
		t.setActivated(last);
		refreshCheckbox();

		if (item.equals(getMainActivity().getCurrentPlayable())) {
			setSelected(true);
			requestFocus();
		} else {
			setSelected(false);
		}
	}

	public void refreshCheckbox() {
		MediaItemWrapper w = getItemWrapper();
		MaterialCheckBox cb = getCheckBox();

		if (!w.isSelectionSupported()) {
			cb.setVisibility(GONE);
			return;
		}

		if (getListView().isSelectionActive()) {
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
		getListView().discardSelection();
		handler.show();
		return true;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (prefs.contains(MainActivityPrefs.GRID_VIEW)) {
			applyLayout(getContext());
		}
	}

	void hideMenu() {
		OverlayMenu menu = getMainActivity().findViewById(R.id.context_menu);
		menu.hide();
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
