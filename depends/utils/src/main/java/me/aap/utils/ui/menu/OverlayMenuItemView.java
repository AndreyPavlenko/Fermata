package me.aap.utils.ui.menu;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.widget.TextViewCompat;

import me.aap.utils.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Function;
import me.aap.utils.ui.menu.OverlayMenu.Builder;
import me.aap.utils.ui.menu.OverlayMenu.SelectionHandler;

/**
 * @author Andrey Pavlenko
 */
public class OverlayMenuItemView extends AppCompatTextView implements OverlayMenuItem, OnClickListener,
		OnLongClickListener, OnCheckedChangeListener {
	OverlayMenuView parent;
	SelectionHandler handler;
	Function<? super Builder, FutureSupplier<Void>> submenuBuilder;
	private Object data;
	private boolean isLongClick;

	OverlayMenuItemView(OverlayMenuView parent, int id, Drawable icon, CharSequence text, float scale) {
		super(parent.getContext(), null, androidx.appcompat.R.attr.popupMenuStyle);
		this.parent = parent;
		setId(id);

		Context ctx = parent.getContext();
		TypedArray ta = ctx.obtainStyledAttributes(null, R.styleable.OverlayMenuItemView,
				androidx.appcompat.R.attr.popupMenuStyle, R.style.Theme_Utils_Base_PopupMenuStyle);
		ColorStateList iconTint = ta.getColorStateList(R.styleable.OverlayMenuItemView_tint);
		int textColor = ta.getColor(R.styleable.OverlayMenuItemView_android_textColor, Color.BLACK);
		int textAppearance = ta.getResourceId(R.styleable.OverlayMenuItemView_android_textAppearance,
				androidx.appcompat.R.attr.textAppearanceListItem);
		int padding = (int) ta.getDimension(R.styleable.OverlayMenuItemView_itemPadding, 5);
		ta.recycle();
		init(icon, iconTint, text, textColor, textAppearance, padding, scale);
	}

	@SuppressLint("InlinedApi")
	public OverlayMenuItemView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs, androidx.appcompat.R.attr.popupMenuStyle);

		TypedArray ta = ctx.obtainStyledAttributes(attrs, R.styleable.OverlayMenuItemView,
				androidx.appcompat.R.attr.popupMenuStyle, R.style.Theme_Utils_Base_PopupMenuStyle);
		ColorStateList iconTint = ta.getColorStateList(R.styleable.OverlayMenuItemView_tint);
		Drawable icon = ta.getDrawable(R.styleable.OverlayMenuItemView_icon);
		CharSequence text = ta.getText(R.styleable.OverlayMenuItemView_text);
		int textColor = ta.getColor(R.styleable.OverlayMenuItemView_android_textColor, Color.BLACK);
		int textAppearance = ta.getResourceId(R.styleable.OverlayMenuItemView_android_textAppearance,
				androidx.appcompat.R.attr.textAppearanceListItem);
		int padding = (int) ta.getDimension(R.styleable.OverlayMenuItemView_itemPadding, 5);
		int submenu = ta.getResourceId(R.styleable.OverlayMenuItemView_submenu, ID_NULL);
		ta.recycle();

		init(icon, iconTint, text, textColor, textAppearance, padding, 1F);

		if (submenu != ID_NULL) {
			setSubmenu(submenu);
		} else if (submenu == R.layout.dynamic) {
			setRightIcon(R.drawable.chevron_right);
		}
	}

	private void init(Drawable icon, ColorStateList iconTint, CharSequence text,
										int textColor, int textAppearance, int padding, float scale) {
		LinearLayoutCompat.LayoutParams lp = new LinearLayoutCompat.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		setLayoutParams(lp);
		setText(text);
		setTextAppearance(textAppearance);
		setTextColor(textColor);
		TextViewCompat.setCompoundDrawableTintList(this, iconTint);

		setCompoundDrawablePadding(padding);
		setPadding(padding, padding, padding, padding);
		setSingleLine(true);
		setVisibility(VISIBLE);
		setTextDirection(TEXT_DIRECTION_LOCALE);
		setTextAlignment(TEXT_ALIGNMENT_VIEW_START);

		setFocusable(true);
		setOnClickListener(this);
		setOnLongClickListener(this);
		setBackgroundResource(R.drawable.focusable_shape_transparent);
		setMovementMethod(new ScrollingMovementMethod());

		if (scale != 1F) {
			TypedArray ta = getContext().obtainStyledAttributes(textAppearance, new int[]{android.R.attr.textSize});
			setTextSize(COMPLEX_UNIT_PX, ta.getDimensionPixelSize(0, 0) * scale);
			ta.recycle();

			if (icon != null) {
				icon.setBounds(0, 0, (int) (icon.getIntrinsicWidth() * scale),
						(int) (icon.getIntrinsicHeight() * scale));
				setCompoundDrawables(icon, null, null, null);
			}
		} else if (icon != null) {
			setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
		}
	}

	public int getItemId() {
		return getId();
	}

	public OverlayMenuView getMenu() {
		return parent;
	}

	@Override
	public boolean isLongClick() {
		return isLongClick;
	}

	public CharSequence getTitle() {
		return getText();
	}

	@Override
	public OverlayMenuItem setTitle(@StringRes int title) {
		setText(title);
		return this;
	}

	public OverlayMenuItem setTitle(CharSequence title) {
		setText(title);
		return this;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getData() {
		return (T) data;
	}

	@Override
	public <T> OverlayMenuItem setData(T data) {
		this.data = data;
		return this;
	}

	@Override
	public OverlayMenuItem setChecked(boolean checked, boolean selectChecked) {
		setRightIcon(checked ? R.drawable.check_box : R.drawable.check_box_blank);
		if (selectChecked && checked && (parent.builder != null)) parent.builder.setSelectedItem(this);
		return this;
	}

	public OverlayMenuItem setVisible(boolean visible) {
		setVisibility(visible ? VISIBLE : GONE);
		return this;
	}

	@Override
	public OverlayMenuItem setMultiLine(boolean multiLine) {
		setSingleLine(!multiLine);
		return this;
	}

	@Override
	public void onClick(View v) {
		getMenu().menuItemSelected(this);
	}

	@Override
	public OverlayMenuItem setHandler(SelectionHandler handler) {
		this.handler = handler;
		return this;
	}

	public OverlayMenuItem setFutureSubmenu(Function<? super Builder, FutureSupplier<Void>> builder) {
		submenuBuilder = builder;
		setRightIcon(R.drawable.chevron_right);
		return this;
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

	private void setRightIcon(@DrawableRes int icon) {
		Drawable[] d = getCompoundDrawables();
		d[2] = (icon == ID_NULL) ? null : AppCompatResources.getDrawable(getContext(), icon);
		setCompoundDrawablesWithIntrinsicBounds(d[0], d[1], d[2], d[3]);
	}
}
