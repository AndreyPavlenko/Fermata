package me.aap.fermata.auto;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.os.SystemClock.uptimeMillis;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.toPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.car.app.SurfaceContainer;

import com.google.android.apps.auto.sdk.CarActivity;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.concurrent.ReschedulableTask;

/**
 * @author Andrey Pavlenko
 */
public class MirrorActivity extends CarActivity implements SurfaceHolder.Callback {
	private MirrorDisplay md;
	private SurfaceContainer sc;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		md = MirrorDisplay.get();
		MainActivityDelegate.setTheme(this, true);
		var s = new SurfaceView(this);
		var tb = new ToolBar(this);
		var v = new FrameLayout(this) {
			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouchEvent(MotionEvent e) {
				if (sc != null) md.setSurface(sc);
				tb.show();
				return md.motionEvent(e);
			}
		};
		s.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		s.getHolder().addCallback(this);
		v.addView(s);
		v.addView(tb);
		v.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		setContentView(v);
	}

	@Override
	public void onDestroy() {
		md.release();
		md = null;
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (sc != null) md.setSurface(sc);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (MirrorServiceFS.sc != null) md.setSurface(MirrorServiceFS.sc);
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder h) {
		var r = h.getSurfaceFrame();
		sc = new SurfaceContainer(h.getSurface(), r.width(), r.height(),
				getResources().getDisplayMetrics().densityDpi);
		md.setSurface(sc);
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder h, int format, int width, int height) {
		sc = new SurfaceContainer(h.getSurface(), width, height,
				getResources().getDisplayMetrics().densityDpi);
		md.setSurface(sc);
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		if (sc != null) {
			md.releaseSurface(sc);
			sc = null;
		}
	}

	static void onFermataButtonClick(MirrorDisplay md) {
		if (MainActivity.getActiveInstance() == null) startFermata();
		else homeScreen(md);
	}

	static void onBackButtonClick(MirrorDisplay md) {
		var d = EventDispatcher.get();
		if (!d.back()) {
			var vm = (WindowManager) FermataApplication.get().getSystemService(WINDOW_SERVICE);
			var size = new Point();
			vm.getDefaultDisplay().getRealSize(size);
			var y = size.y / 2f;
			var time = uptimeMillis();
			d.motionEvent(time, time, MotionEvent.ACTION_DOWN, size.x, y);
			d.motionEvent(time, time + 10, MotionEvent.ACTION_MOVE, size.x * 0.9f, y);
			d.motionEvent(time, time + 20, MotionEvent.ACTION_MOVE, size.x * 0.8f, y);
			d.motionEvent(time, time + 30, MotionEvent.ACTION_MOVE, size.x * 0.7f, y);
			d.motionEvent(time, time + 40, MotionEvent.ACTION_MOVE, size.x * 0.6f, y);
			d.motionEvent(time, time + 50, MotionEvent.ACTION_UP, size.x * 0.5f, y);
		}
		rotateScreen(md);
	}

	private static void startFermata() {
		var ctx = FermataApplication.get();
		Intent intent = new Intent(ctx, MainActivity.class);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
		ctx.startActivity(intent);
	}

	private static void homeScreen(MirrorDisplay md) {
		if (EventDispatcher.get().home()) {
			var ctx = FermataApplication.get();
			Intent intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_HOME);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP);
			ctx.startActivity(intent);
		}
		rotateScreen(md);
	}

	private static void rotateScreen(MirrorDisplay md) {
		md.disableAccelRotation();
	}

	private final class ToolBar extends LinearLayout {
		private final HandlerExecutor handler = FermataApplication.get().getHandler();
		private final ReschedulableTask delayedHide = new ReschedulableTask() {
			@Override
			protected void perform() {
				setVisibility(GONE);
			}
		};
		private final float moveTolerance;
		private final Animation animation;
		private final AppCompatImageView fermataBtn;
		private final AppCompatImageView homeBtn;
		private final AppCompatImageView backBtn;
		private final float minX;
		private final float maxX;
		private final float minY;
		private final float maxY;
		private final int size;
		private final int size2;
		private final int size3;
		private boolean small;
		private long downTime;
		private float downX, downY;
		private View downButton;
		private boolean moving;


		public ToolBar(Context ctx) {
			super(ctx);
			var pad = toPx(ctx, 5);
			var dm = ctx.getResources().getDisplayMetrics();
			minX = pad;
			maxX = dm.widthPixels - pad;
			minY = pad;
			maxY = dm.heightPixels - pad;
			size = (int) Math.max(Math.min(dm.widthPixels, dm.heightPixels) / 10f, toPx(ctx, 20));
			size2 = size + size;
			size3 = size2 + size;
			setLayoutParams(new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
			setX(dm.widthPixels - size - pad);
			setY(pad);

			var iconPad = size / 4;
			var icons = new int[]{R.drawable.launcher, R.drawable.home_btn, R.drawable.back_btn};
			for (var icon : icons) {
				var v = new AppCompatImageView(ctx);
				addView(v);
				v.setId(icon);
				v.setImageResource(icon);
				v.setPadding(iconPad, iconPad, iconPad, iconPad);
				v.setLayoutParams(new LinearLayout.LayoutParams(size, size));
			}

			moveTolerance = toPx(ctx, 10);
			animation = AnimationUtils.loadAnimation(ctx, me.aap.utils.R.anim.button_press);
			fermataBtn = (AppCompatImageView) getChildAt(0);
			homeBtn = (AppCompatImageView) getChildAt(1);
			backBtn = (AppCompatImageView) getChildAt(2);
			setButtonsVisibility();
			delayedHide.schedule(5000);
		}

		void show() {
			setVisibility(VISIBLE);
			delayedHide.schedule(5000);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent e) {
			show();
			switch (e.getActionMasked()) {
				case MotionEvent.ACTION_DOWN -> {
					downTime = uptimeMillis();
					downX = e.getRawX();
					downY = e.getRawY();
					downButton = getButtonAt(e.getX());
					downButton.startAnimation(animation);
					return true;
				}
				case MotionEvent.ACTION_MOVE -> {
					if ((downButton != null) && ((Math.abs(e.getRawX() - downX) >= moveTolerance) ||
							(Math.abs(e.getRawY() - downY) >= moveTolerance))) {
						downButton.clearAnimation();
					}
					int w = getWidth();
					int h = getHeight();
					var newX = Math.max(minX, Math.min(maxX - w, e.getRawX() - w / 2f));
					var newY = Math.max(minY, Math.min(maxY - h, e.getRawY() - h / 2f));
					animate().x(newX).y(newY).setDuration(0).start();
					moving = true;
					return true;
				}
				case MotionEvent.ACTION_UP -> {
					if (moving) {
						moving = false;
						if ((Math.abs(e.getRawX() - downX) >= moveTolerance) ||
								(Math.abs(e.getRawY() - downY) >= moveTolerance)) {
							return true;
						}
					}
					if ((uptimeMillis() - downTime) < 400) handleClick(e.getX());
					else handleLongClick();
					return true;
				}
				default -> {
					return super.onTouchEvent(e);
				}
			}
		}

		private AppCompatImageView getButtonAt(float x) {
			var idx = (x < size) ? 0 : (x < size2) ? 1 : 2;
			for (int i = 0, n = getChildCount(); i < n; i++) {
				var v = getChildAt(i);
				if ((v.getVisibility() == VISIBLE) && (--idx == -1)) return (AppCompatImageView) v;
			}
			throw new IllegalArgumentException();
		}

		private void handleClick(float x) {
			var v = getButtonAt(x);
			if (v == fermataBtn) startFermata();
			else if (v == homeBtn) homeScreen(md);
			else if (v == backBtn) onBackButtonClick(md);
			handler.postDelayed(this::setButtonsVisibility, 1000);
		}

		private void handleLongClick() {
			small = !small;
			setButtonsVisibility();
		}

		@Override
		public void setVisibility(int visibility) {
			super.setVisibility(visibility);
			setButtonsVisibility();
		}

		private void setButtonsVisibility() {
			if (getVisibility() != VISIBLE) return;
			int width;
			if (MainActivity.getActiveInstance() != null) {
				fermataBtn.setVisibility(GONE);
				if (small) {
					width = size;
					homeBtn.setVisibility(VISIBLE);
					backBtn.setVisibility(GONE);
				} else {
					width = size2;
					homeBtn.setVisibility(VISIBLE);
					backBtn.setVisibility(VISIBLE);
				}
			} else {
				if (small) {
					width = size;
					fermataBtn.setVisibility(GONE);
					homeBtn.setVisibility(GONE);
					backBtn.setVisibility(VISIBLE);
				} else {
					width = size3;
					fermataBtn.setVisibility(VISIBLE);
					homeBtn.setVisibility(VISIBLE);
					backBtn.setVisibility(VISIBLE);
				}
			}

			var x = getX();
			if (((x + width) >= maxX) || ((x + getWidth()) >= maxX)) setX(maxX - width);
		}
	}
}
