package me.aap.utils.ui.view;

import android.view.GestureDetector;
import android.view.MotionEvent;

/**
 * @author Andrey Pavlenko
 */
public interface GestureListener extends GestureDetector.OnGestureListener,
		GestureDetector.OnDoubleTapListener, GestureDetector.OnContextClickListener {

	@Override
	default boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		int x = Math.abs((int) velocityX);
		int y = Math.abs((int) velocityY);
		int threshold = getFlingThreshold();

		if ((x < threshold) && (y < threshold)) {
			return false;
		} else if (x > y) {
			return (velocityX < 0) ? onSwipeLeft(e1, e2) : onSwipeRight(e1, e2);
		} else {
			return (velocityY < 0) ? onSwipeUp(e1, e2) : onSwipeDown(e1, e2);
		}
	}

	default boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		return false;
	}

	default boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		return false;
	}

	default boolean onSwipeUp(MotionEvent e1, MotionEvent e2) {
		return false;
	}

	default boolean onSwipeDown(MotionEvent e1, MotionEvent e2) {
		return false;
	}

	default int getFlingThreshold() {
		return 5000;
	}

	@Override
	default boolean onDown(MotionEvent e) {
		return true;
	}

	@Override
	default void onShowPress(MotionEvent e) {
	}

	@Override
	default boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	@Override
	default boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		return false;
	}

	@Override
	default void onLongPress(MotionEvent e) {
	}

	@Override
	default boolean onContextClick(MotionEvent e) {
		return false;
	}

	@Override
	default boolean onSingleTapConfirmed(MotionEvent e) {
		return false;
	}

	@Override
	default boolean onDoubleTap(MotionEvent e) {
		return false;
	}

	@Override
	default boolean onDoubleTapEvent(MotionEvent e) {
		return false;
	}
}
