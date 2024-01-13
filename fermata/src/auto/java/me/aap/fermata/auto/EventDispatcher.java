package me.aap.fermata.auto;

import static android.os.SystemClock.uptimeMillis;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.obtain;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Nullable;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;

abstract class EventDispatcher {

	public static void useSu(Su su) {
		SuDispatcher.instance = completed(new SuDispatcher(su));
	}

	@Nullable
	static EventDispatcher get() {
		var ad = ActivityDispatcher.get();
		return (ad.getActivity() == null) ? XposedDispatcher.get() : ad;
	}

	public abstract boolean back();

	public abstract boolean motionEvent(MotionEvent e);

	public abstract boolean motionEvent(long downTime, long eventTime, int action, float x, float y);

	public boolean tap(float x, float y) {
		var time = uptimeMillis();
		return motionEvent(time, time, ACTION_DOWN, x, y) &&
				motionEvent(time, time + 10, ACTION_UP, x, y);
	}

	public boolean scale(float x, float y, float diff) {
		return AccessibilityDispatcher.get().scale(x, y, diff);
	}

	@Nullable
	MainActivity getActivity() {
		return null;
	}

	private static final class XposedDispatcher extends EventDispatcher {
		static final XposedDispatcher instance = new XposedDispatcher();

		static XposedDispatcher get() {
			return instance;
		}

		@Override
		public boolean back() {
			return XposedEventDispatcherService.dispatchBackEvent() ||
					AccessibilityDispatcher.get().back();
		}

		@Override
		public boolean tap(float x, float y) {
			return (XposedEventDispatcherService.canDispatchEvent() && super.tap(x, y)) ||
					AccessibilityDispatcher.get().tap(x, y);
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			return XposedEventDispatcherService.dispatchEvent(e) ||
					AccessibilityDispatcher.get().motionEvent(e);
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			if (XposedEventDispatcherService.canDispatchEvent()) {
				if (XposedEventDispatcherService.dispatchEvent(
						obtain(downTime, eventTime, action, x, y, 0))) return true;
			}
			return AccessibilityDispatcher.get().motionEvent(downTime, eventTime, action, x, y);
		}
	}

	private static final class ActivityDispatcher extends EventDispatcher {
		private static final ActivityDispatcher instance = new ActivityDispatcher();
		private InputMethodManager imm;

		static ActivityDispatcher get() {
			return instance;
		}

		@Override
		public boolean back() {
			var a = getActivity();
			if (a == null) return AccessibilityDispatcher.get().back();
			a.onBackPressed();
			return true;
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			var a = getActivity();
			if (a == null) return AccessibilityDispatcher.get().motionEvent(e);
			a.dispatchTouchEvent(e);
			return true;
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			var a = getActivity();
			if (a == null)
				return AccessibilityDispatcher.get().motionEvent(downTime, eventTime, action, x, y);
			var e = obtain(downTime, eventTime, action, x, y, 0);
			a.dispatchTouchEvent(e);
			e.recycle();
			return true;
		}

		@Override
		MainActivity getActivity() {
			var a = MainActivity.getActiveInstance();
			if (a == null) return null;
			if (imm == null) {
				imm = (InputMethodManager) FermataApplication.get()
						.getSystemService(Context.INPUT_METHOD_SERVICE);
			}
			return ((imm != null) && imm.isAcceptingText()) ? null : a;
		}
	}

	private static final class AccessibilityDispatcher extends EventDispatcher {
		private static final AccessibilityDispatcher instance = new AccessibilityDispatcher();

		static AccessibilityDispatcher get() {
			return instance;
		}

		@Override
		public boolean back() {
			var d = SuDispatcher.get();
			return (d != null) && d.back();
		}

		@Override
		public boolean tap(float x, float y) {
			if (AccessibilityEventDispatcherService.dispatchTap(x, y)) return true;
			var d = SuDispatcher.get();
			return (d != null) && d.tap(x, y);
		}

		@Override
		public boolean scale(float x, float y, float diff) {
			return AccessibilityEventDispatcherService.dispatchScale(x, y, diff);
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			if (AccessibilityEventDispatcherService.dispatchMotionEvent(e)) return true;
			var d = SuDispatcher.get();
			return (d != null) && d.motionEvent(e);
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			if (AccessibilityEventDispatcherService.dispatchMotionEvent(downTime, eventTime, action, x,
					y)) return true;
			var d = SuDispatcher.get();
			return (d != null) && d.motionEvent(downTime, eventTime, action, x, y);
		}
	}

	private static final class SuDispatcher extends EventDispatcher {
		static FutureSupplier<SuDispatcher> instance = completedNull();
		private final StringBuilder sb = new StringBuilder();
		private final Su su;

		SuDispatcher(Su su) {this.su = su;}

		static SuDispatcher get() {
			return instance.peek();
		}

		@Override
		public boolean back() {
			sb.setLength(0);
			sb.append("cmd input keyevent ").append(KeyEvent.KEYCODE_BACK);
			su.exec(sb.toString());
			return true;
		}

		@Override
		public boolean tap(float x, float y) {
			sb.setLength(0);
			sb.append("cmd input tap ").append((int) x).append(' ').append((int) y);
			su.exec(sb.toString());
			return true;
		}

		@Override
		public boolean motionEvent(MotionEvent e) {
			return motionEvent(e.getDownTime(), e.getEventTime(), e.getAction(), e.getX(), e.getY());
		}

		@Override
		public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
			String a;
			switch (action) {
				case ACTION_DOWN -> a = "DOWN ";
				case ACTION_UP -> a = "UP ";
				case ACTION_MOVE -> a = "MOVE ";
				default -> {
					Log.d("Unable to send event with action ", action);
					return false;
				}
			}
			sb.setLength(0);
			sb.append("cmd input motionevent ").append(a);
			sb.append((int) x).append(' ').append((int) y);
			su.exec(sb.toString());
			return true;
		}
	}
}
