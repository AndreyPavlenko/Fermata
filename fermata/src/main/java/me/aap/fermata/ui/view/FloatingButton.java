package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.function.Consumer;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.fragment.FoldersFragment;
import me.aap.fermata.ui.fragment.MainActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class FloatingButton extends FloatingActionButton implements OnClickListener,
		OnLongClickListener, MainActivityListener {
	private Mode mode;

	public FloatingButton(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		MainActivityDelegate a = getActivity();
		setOnClickListener(this);
		setOnLongClickListener(this);
		setMode(a, a.getActiveFragment());
		a.addBroadcastListener(this, Event.ACTIVITY_FINISH, Event.FRAGMENT_CHANGED,
				Event.FRAGMENT_CONTENT_CHANGED, Event.BACK_PRESSED, Event.VIDEO_MODE_ON,
				Event.VIDEO_MODE_OFF);
	}

	@Override
	public void onClick(View v) {
		mode.onClick.accept(this);
	}

	@Override
	public boolean onLongClick(View v) {
		Mode.MENU.onClick.accept(this);
		return true;
	}

	@Override
	public void onMainActivityEvent(MainActivityDelegate a, Event e) {
		if (!handleActivityFinishEvent(a, e)) {
			switch (e) {
				case VIDEO_MODE_ON:
					setVisibility(GONE);
					break;
				case VIDEO_MODE_OFF:
					setVisibility(VISIBLE);
					break;
				default:
					setMode(a, a.getActiveFragment());
			}
		}
	}

	private void setMode(MainActivityDelegate a, MainActivityFragment f) {
		if (f == null) {
			setMode(Mode.MENU);
		} else if (!a.isCarActivity() && (f instanceof FoldersFragment) && f.isRootPage()) {
			setMode(Mode.ADD_FOLDER);
		} else if (!f.isRootPage() || (a.getActiveNavItemId() != f.getFragmentId())) {
			setMode(Mode.BACK);
		} else {
			setMode(Mode.MENU);
		}
	}

	private void setMode(Mode m) {
		if (mode != m) {
			mode = m;
			setImageResource(m.icon);
		}
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private static void onBackClick(FloatingButton b) {
		MainActivityDelegate a = b.getActivity();
		MainActivityFragment f = a.getActiveFragment();
		if (f == null) return;
		a.onBackPressed();
		b.setMode(a, f);
	}

	private static void onAddFolderClick(FloatingButton b) {
		MainActivityFragment f = b.getActivity().getActiveFragment();
		if (f instanceof FoldersFragment) ((FoldersFragment) f).addFolder();
	}

	private static void onMenuClick(FloatingButton b) {
		b.getActivity().getNavBar().showMenu();
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent e) {
		return getActivity().interceptTouchEvent(e, super::onTouchEvent);
	}

	private enum Mode {
		BACK(R.drawable.back, FloatingButton::onBackClick),
		ADD_FOLDER(R.drawable.add_folder, FloatingButton::onAddFolderClick),
		MENU(R.drawable.menu, FloatingButton::onMenuClick);
		@DrawableRes
		final int icon;
		final Consumer<FloatingButton> onClick;

		Mode(int icon, Consumer<FloatingButton> onClick) {
			this.icon = icon;
			this.onClick = onClick;
		}
	}
}
