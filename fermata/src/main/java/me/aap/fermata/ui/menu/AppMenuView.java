package me.aap.fermata.ui.menu;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import androidx.annotation.ColorInt;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.textview.MaterialTextView;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.fermata.util.Utils.toPx;

/**
 * @author Andrey Pavlenko
 */
public class AppMenuView extends ScrollView implements AppMenu {
	private SelectionHandler handler;
	private CloseHandler closeHandler;
	private AppMenuItemView selectedItem;
	@ColorInt
	private final int headerColor;
	private final LinearLayoutCompat childGroup;

	public AppMenuView(Context context, AttributeSet attrs) {
		super(context, attrs);

		Resources.Theme theme = MainActivityDelegate.get(context).getTheme();
		TypedArray typedArray = theme.obtainStyledAttributes(new int[]{R.attr.appPopupMenuHeaderColor});
		headerColor = typedArray.getColor(0, Color.TRANSPARENT);
		typedArray.recycle();

		childGroup = new LinearLayoutCompat(getContext());
		childGroup.setOrientation(LinearLayoutCompat.VERTICAL);
		childGroup.setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
		addView(childGroup);
	}

	@Override
	public View inflate(@LayoutRes int content) {
		hide();
		return inflateLayout(content);
	}

	@Override
	public void setView(View view) {
		hide();
		initMenuItems(getChildGroup());
	}

	private View inflateLayout(@LayoutRes int content) {
		ViewGroup g = getChildGroup();
		inflate(getContext(), content, g);
		initMenuItems(g);
		return g;
	}

	private void initMenuItems(ViewGroup g) {
		for (int i = 0, count = g.getChildCount(); i < count; i++) {
			View c = g.getChildAt(i);
			if (c instanceof AppMenuItemView) ((AppMenuItemView) c).parent = this;
		}
	}

	@Override
	public void show(SelectionHandler handler, CloseHandler closeHandler) {
		this.handler = handler;
		this.closeHandler = closeHandler;
		MainActivityDelegate.get(getContext()).setActiveMenu(this);
		setVisibility(VISIBLE);
		ViewGroup g = getChildGroup();

		if (selectedItem != null) {
			selectedItem.setSelected(true);
			selectedItem.requestFocus();
		} else {
			for (int i = 0, count = g.getChildCount(); i < count; i++) {
				View c = g.getChildAt(i);

				if ((c instanceof AppMenuItemView) && (c.getVisibility() == VISIBLE)) {
					c.requestFocus();
					break;
				}
			}
		}
	}

	@Override
	public void hide() {
		if (handler == null) return;

		CloseHandler ch = closeHandler;
		handler = null;
		closeHandler = null;
		selectedItem = null;
		getChildGroup().removeAllViews();
		setVisibility(GONE);
		MainActivityDelegate.get(getContext()).setActiveMenu(null);
		if (ch != null) ch.menuClosed(this);
	}

	@Override
	public AppMenuItem findItem(int id) {
		return super.findViewById(id);
	}

	@Override
	public AppMenuItem addItem(int id, boolean checkable, Drawable icon, CharSequence title) {
		AppMenuItemView i = new AppMenuItemView(this, id, checkable, icon, title);
		getChildGroup().addView(i);
		return i;
	}

	@Override
	public void setSelectedItem(AppMenuItem item) {
		selectedItem = (AppMenuItemView) item;
	}

	@Override
	public void setTitle(@StringRes int title) {
		setTitle(getContext().getResources().getString(title));
	}

	@Override
	public void setTitle(CharSequence title) {
		hide();
		MaterialTextView v = new MaterialTextView(getContext());
		LinearLayoutCompat.LayoutParams p = new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		v.setGravity(Gravity.CENTER);
		v.setText(title);
		v.setLayoutParams(p);
		v.setElevation(toPx(10));
		v.setTranslationZ(toPx(10));
		v.setBackgroundColor(headerColor);
		v.setPadding(toPx(5), toPx(10), toPx(20), toPx(10));
		getChildGroup().addView(v);
	}

	void menuItemSelected(AppMenuItemView item) {
		if (handler == null) return;
		SelectionHandler h = handler;

		if (item.submenu == Resources.ID_NULL) {
			hide();
			h.menuItemSelected(item);
		} else {
			hide();
			setTitle(item.getTitle());
			inflateLayout(item.submenu);
			h.menuItemSelected(item);
			if (handler == null) show(h);
		}
	}

	private ViewGroup getChildGroup() {
		return childGroup;
	}
}
