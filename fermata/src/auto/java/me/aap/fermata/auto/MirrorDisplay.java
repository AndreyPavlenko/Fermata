package me.aap.fermata.auto;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.car.app.SurfaceContainer;

import java.lang.ref.WeakReference;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

public class MirrorDisplay {
	private static WeakReference<MirrorDisplay> ref;
	private final int[] loc = new int[2];
	private final Display defaultDisplay;
	private final float scaleDiff;
	private WakeLock wakeLock;
	private int brightness = -1;
	private int refCounter;
	private FutureSupplier<VirtualDisplay> vd = Completed.cancelled();
	private SurfaceContainer sc;
	private Metrics lMetrics;
	private Metrics pMetrics;
	private float dx;
	private float dy;

	private MirrorDisplay() {
		var ctx = FermataApplication.get();
		var vm = (WindowManager) ctx.getSystemService(WINDOW_SERVICE);
		defaultDisplay = vm.getDefaultDisplay();
		var size = new Point();
		defaultDisplay.getRealSize(size);
		scaleDiff = Math.max(UiUtils.toPx(ctx, 20), Math.min(size.x, size.y) / 20f);
	}

	public static MirrorDisplay get() {
		MirrorDisplay vd;
		if ((ref == null) || ((vd = ref.get()) == null))
			ref = new WeakReference<>(vd = new MirrorDisplay());
		vd.refCounter++;
		return vd;
	}

	private void started() {
		if (sc == null) return;
		var app = FermataApplication.get();
		var mode = sc.getWidth() > sc.getHeight() ? 1 : 2;

		if (app.isMirroringMode()) {
			app.setMirroringMode(mode);
			return;
		}

		var br = Settings.System.getInt(app.getContentResolver(), SCREEN_BRIGHTNESS, -1);
		if (br > 0) {
			brightness = br;
			setBrightness(app, 0);
		}

		var pmg = (PowerManager) app.getSystemService(POWER_SERVICE);
		if (pmg != null) {
			//noinspection deprecation
			wakeLock = pmg.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "Fermata:ScreenLock");
			if (wakeLock != null) wakeLock.acquire(24 * 3600000);
		}

		startFermata(app, mode);
	}

	public void release() {
		if (--refCounter == 0) cleanUp();
	}

	public void setSurface(@NonNull SurfaceContainer sc) {
		if (this.sc == sc) return;
		this.sc = sc;
		lMetrics = pMetrics = null;
		if (vd.isDoneNotFailed()) {
			try {
				var vd = this.vd.getOrThrow();
				vd.setSurface(sc.getSurface());
				vd.resize(sc.getWidth(), sc.getHeight(), sc.getDpi());
				started();
			} catch (Throwable err) {
				Log.d(err);
				noVd();
				createDisplay();
			}
		} else {
			createDisplay();
		}
	}

	public void releaseSurface(@NonNull SurfaceContainer sc) {
		if (this.sc != sc) return;
		this.sc = null;
		lMetrics = pMetrics = null;
		if (vd.isDoneNotFailed()) vd.getOrThrow().setSurface(null);
	}

	public void tap(float x, float y) {
		var d = translate(x, y);
		if (d != null) d.tap(dx, dy);
	}

	public void scale(float x, float y, boolean zoomIn) {
		var d = translate(x, y);
		Log.d(d);
		if (d != null) d.scale(dx, dy, zoomIn ? scaleDiff : -scaleDiff);
	}

	public boolean motionEvent(MotionEvent e) {
		var x = e.getX();
		var y = e.getY();
		var d = translate(x, y);
		if (d == null) return false;
		var cnt = e.getPointerCount();

		if (cnt == 1) {
			e.setLocation(dx, dy);
		} else {
			var a = d.getActivity();
			var m = metrics(a);
			if (m == null) return false;
			if (a != null) a.getWindow().getDecorView().getLocationOnScreen(loc);
			var props = new MotionEvent.PointerProperties[cnt];
			var coords = new MotionEvent.PointerCoords[cnt];
			for (int i = 0; i < cnt; i++) {
				props[i] = new MotionEvent.PointerProperties();
				e.getPointerProperties(i, props[i]);
				var c = coords[i] = new MotionEvent.PointerCoords();
				e.getPointerCoords(i, c);
				if (a != null) {
					c.x = (c.x - m.x) * m.scale - loc[0];
					c.y = (c.y - m.y) * m.scale - loc[1];
				} else {
					c.x = (c.x - m.x) * m.scale;
					c.y = (c.y - m.y) * m.scale;
				}
			}
			e = MotionEvent.obtain(e.getDownTime(), e.getEventTime(), e.getAction(), cnt, props, coords,
					e.getMetaState(), e.getButtonState(), e.getXPrecision(), e.getYPrecision(),
					e.getDeviceId(), e.getEdgeFlags(), e.getSource(), e.getFlags());
		}

		return d.motionEvent(e);
	}

	public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
		var d = translate(x, y);
		return (d != null) && d.motionEvent(downTime, eventTime, action, dx, dy);
	}

	@Override
	protected void finalize() {
		cleanUp();
	}

	private void cleanUp() {
		sc = null;
		lMetrics = pMetrics = null;
		if (vd.isDoneNotFailed()) vd.getOrThrow().release();
		else vd.cancel();
		noVd();
		if ((ref == null) || (ref.get() != this)) return;
		ref = null;
		var app = FermataApplication.get();
		startFermata(app, 0);
		if (brightness > 0) setBrightness(app, brightness);
		if (wakeLock != null) wakeLock.release();
		ProjectionService.stop();
	}

	private void noVd() {
		this.vd.cancel();
		this.vd = Completed.cancelled();
	}

	private void createDisplay() {
		if (!vd.isDone()) return;
		Log.i("Creating VirtualDisplay");
		var p = new Promise<VirtualDisplay>();
		vd = p;
		createDisplay(p);
		p.onSuccess(vd -> {
			Log.i("VirtualDisplay created: ", vd);
			this.vd = Completed.completed(vd);
			started();
		});
		FermataApplication.get().getHandler().schedule(() -> {
			if (!vd.isDone()) drawMsg(R.string.unlock_phone_and_grant);
		}, 500);
	}

	private void createDisplay(Promise<VirtualDisplay> p) {
		if (vd != p) return;
		if (sc == null) noVd();
		if (p.isDone()) return;
		ProjectionService.start().onCompletion((mp, err) -> {
			if (vd != p) return;
			if (sc == null) noVd();
			if (p.isDone()) return;
			if (err != null) {
				if (isCancellation(err)) {
					drawMsg(R.string.screen_capture_rejected);
					noVd();
					return;
				}
				Log.e(err, "Failed to create MediaProjection");
				retryCreateDisplay(p);
			} else if (mp == null) {
				Log.e("Failed to create MediaProjection");
				retryCreateDisplay(p);
			} else {
				try {
					mp.registerCallback(new MediaProjection.Callback() {
						@Override
						public void onStop() {
							mp.unregisterCallback(this);
						}
					}, FermataApplication.get().getHandler());
					var name = FermataApplication.get().getString(R.string.mirror_service_name);
					var vd = mp.createVirtualDisplay(name, sc.getWidth(), sc.getHeight(), sc.getDpi(),
							DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY, sc.getSurface(), null, null);
					if (vd == null) {
						Log.e("Failed to create VirtualDisplay");
						retryCreateDisplay(p);
					} else {
						p.complete(vd);
					}
				} catch (Exception ex) {
					Log.e(ex, "Failed to create VirtualDisplay");
					retryCreateDisplay(p);
				}
			}
		});
	}

	private void retryCreateDisplay(Promise<VirtualDisplay> p) {
		FermataApplication.get().getHandler().schedule(() -> {
			if (p.isDone()) return;
			Log.i("Retrying to create VirtualDisplay");
			createDisplay(p);
		}, 3000);
	}

	private void drawMsg(@StringRes int msg) {
		if (sc == null) return;
		try {
			var surface = sc.getSurface();
			if (surface == null) return;
			var c = surface.lockCanvas(null);
			var w = sc.getWidth();
			var h = sc.getHeight();
			TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(Color.WHITE);
			paint.setTypeface(Typeface.DEFAULT);
			paint.setElegantTextHeight(true);
			paint.setTextAlign(Paint.Align.CENTER);
			paint.setTextSize(h / 10f);
			var text = FermataApplication.get().getString(msg);
			var sl = StaticLayout.Builder.obtain(text, 0, text.length(), paint, w).setMaxLines(5)
					.setEllipsize(TextUtils.TruncateAt.END).setIncludePad(true).build();
			c.drawColor(Color.BLACK);
			c.translate(w / 2f, h / 2f - sl.getHeight() / 2f);
			sl.draw(c);
			surface.unlockCanvasAndPost(c);
		} catch (Exception err) {
			Log.d(err, "Failed to draw message on surface");
		}
	}

	@Nullable
	private EventDispatcher translate(float x, float y) {
		var d = EventDispatcher.get();
		if (d == null) return null;
		var a = d.getActivity();
		var m = metrics(a);
		if (m == null) return null;
		if (a != null) {
			a.getWindow().getDecorView().getLocationOnScreen(loc);
			dx = (x - m.x) * m.scale - loc[0];
			dy = (y - m.y) * m.scale - loc[1];
		} else {
			dx = (x - m.x) * m.scale;
			dy = (y - m.y) * m.scale;
		}
		return d;
	}

	@Nullable
	@SuppressLint("SwitchIntDef")
	private Metrics metrics(@Nullable MainActivity a) {
		if (sc == null) return null;
		boolean landscape;
		if (a != null) {
			landscape = a.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE;
		} else {
			landscape = switch (defaultDisplay.getRotation()) {
				case ROTATION_90, ROTATION_270 -> true;
				default -> false;
			};
		}
		var m = landscape ? lMetrics : pMetrics;
		if (m == null) {
			if (!vd.isDoneNotFailed()) return null;
			final Point size = new Point();
			defaultDisplay.getRealSize(size);
			m = new Metrics(size.x, size.y, sc.getWidth(), sc.getHeight());
			if (landscape) lMetrics = m;
			else pMetrics = m;
		}
		return m;
	}

	private static void startFermata(FermataApplication app, int mirrorMode) {
		app.setMirroringMode(mirrorMode);
		var intent = new Intent(app, MainActivity.class);
		var flags = FLAG_ACTIVITY_NEW_TASK;
		if (mirrorMode == 0) {
			flags |= FLAG_ACTIVITY_CLEAR_TOP;
			intent.setAction(MainActivityDelegate.INTENT_ACTION_FINISH);
		} else {
			flags |= FLAG_ACTIVITY_CLEAR_TASK;
		}
		intent.setFlags(flags);
		app.startActivity(intent);
	}

	private static void setBrightness(Context ctx, int br) {
		try {
			Settings.System.putInt(ctx.getContentResolver(), SCREEN_BRIGHTNESS, br);
		} catch (SecurityException ex) {
			Log.e(ex, "Failed to change SCREEN_BRIGHTNESS");
		}
	}

	private static final class Metrics {
		final float x;
		final float y;
		final float scale;

		Metrics(float dw, float dh, float sw, float sh) {
			var scale = dw / sw;
			var scaleH = dh / scale;
			if (scaleH <= sh) {
				x = 0f;
				y = (sh - scaleH) / 2f;
				assert y >= 0f;
			} else {
				scale = dh / sh;
				y = 0f;
				x = (sw - (dw / scale)) / 2;
				assert x >= 0f;
			}
			this.scale = scale;
		}
	}
}
