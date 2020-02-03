package me.aap.fermata.ui.menu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;

import me.aap.fermata.R;

import static me.aap.fermata.util.Utils.toPx;

/**
 * @author Andrey Pavlenko
 */
public class AppMenuItemView extends LinearLayoutCompat implements AppMenuItem, OnClickListener,
		OnLongClickListener, OnCheckedChangeListener {
	@SuppressLint("InlinedApi")
	@LayoutRes
	int submenu = Resources.ID_NULL;
	AppMenuView parent;
	private Object data;
	private boolean isLongClick;

	AppMenuItemView(AppMenuView parent, int id, boolean checkable, Drawable icon, CharSequence title) {
		super(parent.getContext());
		this.parent = parent;
		setId(id);
		init(parent.getContext(), checkable, icon, title);
	}

	@SuppressLint("InlinedApi")
	public AppMenuItemView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		TypedArray ta = ctx.obtainStyledAttributes(attrs, R.styleable.AppMenuItemView);
		boolean checkable = ta.getBoolean(R.styleable.AppMenuItemView_checkable, false);
		Drawable icon = ta.getDrawable(R.styleable.AppMenuItemView_icon);
		CharSequence title = ta.getText(R.styleable.AppMenuItemView_text);
		submenu = ta.getResourceId(R.styleable.AppMenuItemView_submenu, Resources.ID_NULL);
		init(ctx, checkable, icon, title);
		ta.recycle();
	}

	private void init(Context ctx, boolean checkable, Drawable icon, CharSequence title) {
		setOrientation(HORIZONTAL);

		if (submenu != Resources.ID_NULL) {
			inflate(ctx, R.layout.submenu_item, this);
		} else if (checkable) {
			inflate(ctx, R.layout.checkable_menu_item, this);
			CheckBox cb = getCheckBox();
			cb.setOnCheckedChangeListener(this);
			cb.setGravity(Gravity.CENTER_VERTICAL);
			cb.setLayoutDirection(LAYOUT_DIRECTION_LTR);
		} else {
			inflate(ctx, R.layout.menu_item, this);
		}

		if (icon == null) getIconView().setVisibility(GONE);
		else getIconView().setImageDrawable(icon);

		getTitleView().setText(title);

		setFocusable(true);
		setOnClickListener(this);
		setOnLongClickListener(this);
		setBackgroundResource(R.drawable.focusable);
		setPadding(toPx(5), toPx(10), toPx(20), toPx(10));
	}

	public int getItemId() {
		return getId();
	}

	public AppMenuView getMenu() {
		return parent;
	}

	@Override
	public boolean isLongClick() {
		return isLongClick;
	}

	public CharSequence getTitle() {
		return getTitleView().getText();
	}

	@Override
	public AppMenuItem setTitle(@StringRes int title) {
		getTitleView().setText(title);
		return this;
	}

	public AppMenuItem setTitle(CharSequence title) {
		getTitleView().setText(title);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData() {
		return (T) data;
	}

	@Override
	public <T> AppMenuItem setData(T data) {
		this.data = data;
		return this;
	}

	@Override
	public AppMenuItem setChecked(boolean checked) {
		getCheckBox().setChecked(checked);
		return this;
	}

	public AppMenuItem setVisible(boolean visible) {
		setVisibility(visible ? VISIBLE : GONE);
		return this;
	}

	@Override
	public void onClick(View v) {
		getMenu().menuItemSelected(this);
	}

	@Override
	public boolean onLongClick(View v) {
		isLongClick = true;
		getMenu().menuItemSelected(this);
		isLongClick = false;
		return true;
	}

	@Override
	public void onCheckedChanged(CompoundButton v, boolean isChecked) {
		onClick(v);
	}

	private ImageView getIconView() {
		return (ImageView) getChildAt(0);
	}

	private TextView getTitleView() {
		return (TextView) getChildAt(1);
	}

	private CheckBox getCheckBox() {
		return (CheckBox) getChildAt(2);
	}
}
