package me.aap.fermata.ui.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;

/**
 * @author Andrey Pavlenko
 */
public class NavBarView extends LinearLayoutCompat implements MainActivityListener,
		OnClickListener, AppMenu.SelectionHandler {
	@ColorInt
	private final int outlineColor;
	@Nullable
	private ViewGroup selectedItem;

	public NavBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		inflate(getContext(), R.layout.nav_bar, this);

		TypedValue typedValue = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.appBarsOutlineColor, typedValue, true);
		outlineColor = typedValue.data;

		MainActivityDelegate a = getActivity();
		a.addBroadcastListener(this, Event.ACTIVITY_FINISH, Event.FRAGMENT_CHANGED);
		if (a.isBarsHidden()) setVisibility(GONE);

		int id = a.getActiveNavItemId();

		for (int count = getChildCount(), i = 0; i < count; i++) {
			View c = getChildAt(i);
			c.setOnClickListener(this);

			if (c.getId() == id) {
				selectedItem = (ViewGroup) c;
				setTextVisibility(selectedItem, VISIBLE);
			}
		}
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		float h = Resources.getSystem().getDisplayMetrics().density;
		Paint p = new Paint();
		p.setColor(outlineColor);
		p.setStrokeWidth(h);
		canvas.drawLine(0, h, getWidth(), h, p);
	}

	@Override
	public void onClick(View v) {
		setSelectedItem((ViewGroup) v, true);
	}

	private void setSelectedItem(ViewGroup item, boolean checkReselected) {
		if (item == null) return;

		if (checkReselected && (item == selectedItem)) {
			itemReselected(item);
		} else if (itemSelected(item)) {
			if (item != selectedItem) {
				setTextVisibility(selectedItem, GONE);
				selectedItem = item;
			}

			getActivity().setActiveNavItemId(item.getId());
			setTextVisibility(item, VISIBLE);
		}
	}

	private boolean itemSelected(ViewGroup item) {
		MainActivityDelegate a = getActivity();
		int id = item.getId();

		if (id == R.id.nav_menu) {
			showMenu();
			return false;
		} else {
			a.showFragment(id);
			return true;
		}
	}

	private void itemReselected(ViewGroup item) {
		int id = item.getId();
		if (id != R.id.nav_menu) {
			MainActivityFragment f = getActivity().getActiveFragment();
			if ((f != null) && (f.getFragmentId() == id)) {
				f.navBarItemReselected(id);
				return;
			}
		}
		itemSelected(item);
	}

	public void showMenu() {
		MainActivityDelegate a = getActivity();
		AppMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.inflate(R.layout.nav_menu);
		menu.findItem(R.id.nav_got_to_current).setVisible(a.hasCurrent());
		if (a.isCarActivity()) menu.findItem(R.id.nav_exit).setVisible(false);

		MainActivityFragment f = a.getActiveFragment();
		if (f != null) f.initNavBarMenu(menu);

		menu.show(this);
	}

	@Override
	public boolean menuItemSelected(AppMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_got_to_current:
				getActivity().goToCurrent();
				return true;
			case R.id.nav_settings:
				getActivity().showFragment(R.id.nav_settings);
				return true;
			case R.id.nav_exit:
				getActivity().finish();
				return true;
		}

		MainActivityFragment f = getActivity().getActiveFragment();
		return (f != null) && f.navBarMenuItemSelected(item);
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private void setTextVisibility(@Nullable ViewGroup item, int visibility) {
		if (item != null) {
			item.getChildAt(1).setVisibility(visibility);
			FermataApplication.get().getHandler().post(this::requestLayout);
		}
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		return getActivity().interceptTouchEvent(e, super::onTouchEvent);
	}

	@Override
	public void onMainActivityEvent(MainActivityDelegate a, Event e) {
		if (!handleActivityFinishEvent(a, e)) {
			if (e == Event.FRAGMENT_CHANGED) {
				setSelectedItem(findViewById(getActivity().getActiveFragmentId()), false);
			}
		}
	}
}
