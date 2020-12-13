package me.aap.fermata.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.view.GestureListener;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public class FermataNavBarView extends NavBarView implements GestureListener {
	private final GestureDetectorCompat gestureDetector;

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		gestureDetector = new GestureDetectorCompat(context, this);
	}

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		gestureDetector = new GestureDetectorCompat(context, this);
	}

	protected boolean interceptTouchEvent(MotionEvent e) {
		gestureDetector.onTouchEvent(e);
		return super.onTouchEvent(e);
	}

	@Override
	public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeLeft(e1, e2);
	}

	@Override
	public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeRight(e1, e2);
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		return getMainActivity().getControlPanel().onScroll(e1, e2, distanceX, distanceY);
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
