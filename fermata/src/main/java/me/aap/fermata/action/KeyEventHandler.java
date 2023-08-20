package me.aap.fermata.action;

import static android.os.SystemClock.uptimeMillis;
import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_MULTIPLE;
import static android.view.KeyEvent.ACTION_UP;

import android.os.Handler;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.annotation.Nullable;

import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class KeyEventHandler {
	private static final int DBL_CLICK_INTERVAL = 500;
	private static final int LONG_CLICK_INTERVAL = 1000;
	private final Handler handler;
	private final MediaSessionCallback cb;
	@Nullable
	private final MainActivityDelegate activity;
	private Worker worker;

	public KeyEventHandler(MediaSessionCallback cb) {
		this.cb = cb;
		this.activity = null;
		this.handler = cb.getHandler();
	}

	public KeyEventHandler(MainActivityDelegate activity) {
		this.cb = activity.getMediaSessionCallback();
		this.activity = activity;
		this.handler = activity.getHandler();
	}

	public boolean handle(KeyEvent event, IntObjectFunction<KeyEvent, Boolean> defaultHandler) {
		Log.i((activity == null) ? "Media: " : "Activity: ", event);

		if (event.isCanceled()) {
			worker = null;
			return defaultHandler.apply(event.getKeyCode(), event);
		}

		if (worker != null) {
			if (worker.handle(event)) return true;
			worker = null;
			return false;
		}

		var code = event.getKeyCode();
		var k = Key.get(code);
		if (k == null) return defaultHandler.apply(code, event);

		if (!k.isMedia() && (activity != null) && (activity.getCurrentFocus() instanceof EditText)) {
			return defaultHandler.apply(code, event);
		}

		var dblClickHandler = k.getDblClickHandler();
		if (dblClickHandler == null) return defaultHandler.apply(code, event);

		var action = event.getAction();
		if (action == ACTION_MULTIPLE) {
			dblClickHandler.handle(cb, activity, uptimeMillis());
			return true;
		}
		if (action != ACTION_DOWN) return defaultHandler.apply(code, event);

		var clickHandler = k.getClickHandler();
		if (clickHandler == null) return defaultHandler.apply(code, event);
		var longClickHandler = k.getLongClickHandler();
		if (longClickHandler == null) return defaultHandler.apply(code, event);
		worker = new Worker(event, clickHandler, dblClickHandler, longClickHandler);
		return false;
	}

	private final class Worker implements Runnable {
		private final KeyEvent event;
		private final Action.Handler clickHandler;
		private final Action.Handler dblClickHandler;
		private final Action.Handler longClickHandler;
		private final long time;
		private long longClickTime;
		private boolean up;


		Worker(KeyEvent event, Action.Handler clickHandler, Action.Handler dblClickHandler,
					 Action.Handler longClickHandler) {
			this.event = event;
			this.clickHandler = clickHandler;
			this.dblClickHandler = dblClickHandler;
			this.longClickHandler = longClickHandler;
			time = longClickTime = uptimeMillis();
			sched(DBL_CLICK_INTERVAL);
		}

		@Override
		public void run() {
			if (worker != this) return;

			long now = uptimeMillis();
			long diff = now - time;

			if (diff < LONG_CLICK_INTERVAL) {
				if (up) {
					worker = null;
					handle(clickHandler);
				} else {
					sched(LONG_CLICK_INTERVAL - (now - longClickTime));
				}
			} else {
				diff = now - longClickTime;

				if (diff < LONG_CLICK_INTERVAL) {
					sched(LONG_CLICK_INTERVAL - diff);
				} else if (diff > 60000) { // Key UP not received?
					worker = null;
				} else {
					longClickTime = time;
					handle(longClickHandler);
					sched(LONG_CLICK_INTERVAL);
				}
			}
		}

		boolean handle(KeyEvent e) {
			if (e.getKeyCode() != event.getKeyCode()) return false;

			switch (e.getAction()) {
				case ACTION_DOWN -> {
					if (dblClickHandler == clickHandler) {
						longClickTime = uptimeMillis();
						handle(longClickHandler);
					}
					return true;
				}
				case ACTION_UP -> {
					long holdTime = uptimeMillis() - time;

					if (holdTime <= DBL_CLICK_INTERVAL) {
						if (up) {
							worker = null;
							handle(dblClickHandler);
						} else if (dblClickHandler == clickHandler) {
							worker = null;
							handle(clickHandler);
						} else {
							up = true;
						}
					} else if (holdTime >= LONG_CLICK_INTERVAL) {
						worker = null;
					} else {
						worker = null;
						if (longClickTime == time) handle(clickHandler);
					}

					return true;
				}
				case ACTION_MULTIPLE -> {
					worker = null;
					handle(dblClickHandler);
					return true;
				}
			}
			return false;
		}

		private void handle(Action.Handler h) {
			h.handle(cb, activity, time);
		}

		private void sched(long delay) {
			handler.postDelayed(this, delay);
		}
	}
}
