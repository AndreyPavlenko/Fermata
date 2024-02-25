package me.aap.fermata.auto;

import static android.content.Context.POWER_SERVICE;
import static android.content.Context.WINDOW_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.SystemClock.uptimeMillis;
import static android.provider.Settings.System.ACCELEROMETER_ROTATION;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS;
import static android.provider.Settings.System.USER_ROTATION;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static me.aap.utils.function.ResultConsumer.Cancel.isCancellation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.car.app.SurfaceContainer;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import java.lang.ref.WeakReference;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.ReschedulableTask;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;

public class MirrorDisplay {
	private static final int OVERLAY_FLAGS =
			FLAG_NOT_FOCUSABLE | FLAG_KEEP_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_TURN_SCREEN_ON |
					FLAG_SHOW_WHEN_LOCKED | FLAG_NOT_TOUCHABLE | FLAG_WATCH_OUTSIDE_TOUCH;
	private static WeakReference<MirrorDisplay> ref;
	private final int[] loc = new int[2];
	private final Display defaultDisplay;
	private final float scaleDiff;
	private final AudioFocusRequestCompat audioFocusReq;
	private WakeLock wakeLock;
	private static int accel = -1;
	private int brightness = -1;
	private int refCounter;
	private FutureSupplier<Session> session = Completed.cancelled();
	private SurfaceContainer sc;
	private Overlay overlay;
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
		audioFocusReq =
				new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).setAudioAttributes(
								new AudioAttributesCompat.Builder().setUsage(AudioAttributesCompat.USAGE_MEDIA)
										.setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build())
						.setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(focusChange -> {}).build();
	}

	public static MirrorDisplay get() {
		MirrorDisplay md;
		if ((ref == null) || ((md = ref.get()) == null)) {
			ref = new WeakReference<>(md = new MirrorDisplay());
		}
		md.refCounter++;
		return md;
	}

	public static void close() {
		MirrorDisplay md;
		if ((ref == null) || ((md = ref.get()) == null)) return;
		var sc = md.sc;
		md.cleanUp();
		if (sc != null) drawMsg(sc, R.string.app_name);
	}

	public void release() {
		if (--refCounter == 0) cleanUp();
	}

	public void setSurface(@NonNull SurfaceContainer sc) {
		var oldSc = this.sc;
		if (oldSc == sc) return;
		this.sc = sc;
		lMetrics = pMetrics = null;
		if (session.isDoneNotFailed()) {
			try {
				var vd = this.session.getOrThrow().vd;
				vd.setSurface(sc.getSurface());
				vd.resize(sc.getWidth(), sc.getHeight(), sc.getDpi());
				started();
			} catch (Throwable err) {
				Log.d(err);
				noSession();
				createSession();
			}
		} else {
			createSession();
		}
		if (oldSc != null) drawMsg(oldSc, R.string.app_name);
	}

	public void releaseSurface(@NonNull SurfaceContainer sc) {
		var oldSc = this.sc;
		if (oldSc != sc) return;
		this.sc = null;
		lMetrics = pMetrics = null;
		if (session.isDoneNotFailed()) session.getOrThrow().vd.setSurface(null);
		drawMsg(oldSc, R.string.app_name);
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

	private long downTime;

	public boolean motionEvent(MotionEvent e) {
		EventDispatcher d;
		var action = e.getAction();
		if (action == MotionEvent.ACTION_DOWN) downTime = uptimeMillis();
		var cnt = e.getPointerCount();
		if (cnt == 1) {
			d = translate(e.getX(), e.getY());
			if (d == null) return false;
			e = MotionEvent.obtain(downTime, uptimeMillis(), e.getAction(), dx, dy, 0);
			e.setSource(InputDevice.SOURCE_TOUCHSCREEN);
		} else {
			d = dispatcher();
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
			e = MotionEvent.obtain(downTime, uptimeMillis(), action, cnt, props, coords,
					e.getMetaState(),
					e.getButtonState(), e.getXPrecision(), e.getYPrecision(), e.getDeviceId(),
					e.getEdgeFlags(), InputDevice.SOURCE_TOUCHSCREEN, e.getFlags());
		}
		return d.motionEvent(e);
	}

	public boolean motionEvent(long downTime, long eventTime, int action, float x, float y) {
		var d = translate(x, y);
		return (d != null) && d.motionEvent(downTime, eventTime, action, dx, dy);
	}

	public static void disableAccelRotation() {
		var app = FermataApplication.get();
		app.getHandler().postDelayed(() -> disableAccelRotation(app), 3000);
	}

	private static void disableAccelRotation(Context ctx) {
		var a = EventDispatcher.get().getActivity();
		var land = FermataApplication.get().isMirroringLandscape();
		if (a != null) {
			a.setRequestedOrientation(
					land ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE : SCREEN_ORIENTATION_SENSOR_PORTRAIT);
		}

		try {
			var cr = ctx.getContentResolver();
			if (accel == -1) {
				var v = Settings.System.getInt(cr, ACCELEROMETER_ROTATION, -1);
				if (v != -1) accel = v;
			}
			Settings.System.putInt(cr, ACCELEROMETER_ROTATION, 0);
			Settings.System.putInt(cr, USER_ROTATION, land ? ROTATION_90 : ROTATION_0);
		} catch (Exception err) {
			Log.e(err);
		}
	}

	private static void restoreAccelRotation(Context ctx) {
		var a = EventDispatcher.get().getActivity();
		if (a != null) a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		if (accel == -1) return;
		try {
			Settings.System.putInt(ctx.getContentResolver(), ACCELEROMETER_ROTATION, accel);
		} catch (Exception err) {
			Log.e(err);
		}
		accel = -1;
	}

	private void dimScreen(Context ctx) {
		if (brightness == -1) {
			var br = Settings.System.getInt(ctx.getContentResolver(), SCREEN_BRIGHTNESS, -1);
			brightness = (br > 0) ? br : 200;
		}
		setBrightness(ctx, 1);
		if (!Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) setBrightness(ctx, 0);
	}

	private void restoreBrightness(Context ctx) {
		if (brightness > 0) setBrightness(ctx, brightness);
	}

	@Override
	protected void finalize() {
		if ((ref == null) || (ref.get() == null) || (ref.get() == this)) {
			cleanUp();
		}
	}

	private void started() {
		if (sc == null) return;
		var app = FermataApplication.get();
		var mode = sc.getWidth() > sc.getHeight() ? 1 : 2;

		if (app.isMirroringMode()) {
			app.setMirroringMode(mode);
			return;
		}

		dimScreen(app);
		disableAccelRotation(app);

		var pmg = (PowerManager) app.getSystemService(POWER_SERVICE);
		if (pmg != null) {
			//noinspection deprecation
			wakeLock = pmg.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP,
					"Fermata:ScreenLock");
			if (wakeLock != null) wakeLock.acquire(24 * 3600000);
		}

		if ((overlay == null) && (SDK_INT >= VERSION_CODES.O)) {
			try {
				var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
				var lp =
						new WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE_APPLICATION_OVERLAY,
								OVERLAY_FLAGS, PixelFormat.TRANSPARENT);
				var overlay = new Overlay(app);
				wm.addView(overlay, lp);
				this.overlay = overlay;
			} catch (Exception err) {
				Log.e(err, "Failed to add overlay");
			}
		}

		try {
			app.startService(new Intent(app, XposedEventDispatcherService.class));
		} catch (Exception err) {
			Log.e(err, "Failed to start XposedEventDispatcherService");
		}

		var amgr = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if (amgr != null) AudioManagerCompat.requestAudioFocus(amgr, audioFocusReq);
		setMirroringMode(app, mode);
	}

	private void cleanUp() {
		noSession();
		sc = null;
		lMetrics = pMetrics = null;
		var app = FermataApplication.get();
		if (overlay != null) {
			overlay.dimAndRotate.cancel();
			var wm = (WindowManager) app.getSystemService(WINDOW_SERVICE);
			wm.removeView(overlay);
			overlay = null;
		}
		if (wakeLock != null) {
			wakeLock.release();
			wakeLock = null;
		}
		setMirroringMode(app, 0);
		restoreBrightness(app);
		restoreAccelRotation(app);
		ProjectionService.stop();

		try {
			app.stopService(new Intent(app, XposedEventDispatcherService.class));
		} catch (Exception err) {
			Log.d(err, "Failed to stop XposedEventDispatcherService");
		}

		var amgr = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
		if (amgr != null) AudioManagerCompat.abandonAudioFocusRequest(amgr, audioFocusReq);
	}

	private void noSession() {
		if (session.isDoneNotFailed()) session.getOrThrow().close();
		else session.cancel();
		session = Completed.cancelled();
	}

	private void createSession() {
		if (!session.isDone()) return;
		var p = new Promise<Session>();
		session = p;
		createSession(p);
		p.onSuccess(s -> {
			Log.i("Session created: ", s);
			session = Completed.completed(s);
			started();
		});
		FermataApplication.get().getHandler().schedule(() -> {
			if (!session.isDone()) drawMsg(R.string.unlock_phone_and_grant);
		}, 500);
	}

	private void createSession(Promise<Session> p) {
		if (session != p) return;
		if (sc == null) noSession();
		if (p.isDone()) return;
		ProjectionService.start().onCompletion((mp, err) -> {
			if (session != p) return;
			if (sc == null) noSession();
			if (p.isDone()) return;
			if (err != null) {
				if (isCancellation(err)) {
					drawMsg(R.string.screen_capture_rejected);
					cleanUp();
					return;
				}
				Log.e(err, "Failed to create media projection");
				retryCreateSession(p);
			} else if (mp == null) {
				Log.e("Failed to create media projection");
				retryCreateSession(p);
			} else {
				try {
					p.complete(new Session(mp, this));
				} catch (Exception ex) {
					Log.e(ex, "Failed to create media projection");
					retryCreateSession(p);
				}
			}
		});
	}

	private void retryCreateSession(Promise<Session> p) {
		FermataApplication.get().getHandler().schedule(() -> {
			if (p.isDone()) return;
			Log.i("Retrying to create media projection");
			createSession(p);
		}, 3000);
	}

	private void drawMsg(@StringRes int msg) {
		if (sc != null) drawMsg(sc, msg);
	}

	private static void drawMsg(SurfaceContainer sc, @StringRes int msg) {
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

	private EventDispatcher dispatcher() {
		var d = EventDispatcher.get();
		Log.d(d);
		return d;
	}

	@Nullable
	private EventDispatcher translate(float x, float y) {
		var d = dispatcher();
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
	private Metrics metrics(@Nullable AppCompatActivity a) {
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
			if (!session.isDoneNotFailed()) return null;
			final Point size = new Point();
			defaultDisplay.getRealSize(size);
			m = new Metrics(size.x, size.y, sc.getWidth(), sc.getHeight());
			if (landscape) lMetrics = m;
			else pMetrics = m;
		}
		return m;
	}

	private void setMirroringMode(FermataApplication app, int mode) {
		app.setMirroringMode(mode);
		var intent = new Intent(app, MainActivity.class);
		intent.setAction(MainActivityDelegate.INTENT_ACTION_FINISH);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		app.startActivity(intent);

		intent = new Intent(app, LauncherActivity.class);
		intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
		if (mode == 0) intent.setAction(MainActivityDelegate.INTENT_ACTION_FINISH);
		app.startActivity(intent);
	}

	private static void setBrightness(Context ctx, int br) {
		try {
			Settings.System.putInt(ctx.getContentResolver(), SCREEN_BRIGHTNESS, br);
		} catch (SecurityException ex) {
			Log.e(ex, "Failed to change SCREEN_BRIGHTNESS");
		}
	}

	private static final class Session extends MediaProjection.Callback {
		final MediaProjection mp;
		final VirtualDisplay vd;
		final WeakReference<MirrorDisplay> mdRef;

		Session(MediaProjection mp, MirrorDisplay md) {
			this.mp = mp;
			this.mdRef = new WeakReference<>(md);
			var sc = md.sc;
			var app = FermataApplication.get();
			var name = app.getString(R.string.mirror_service_name);
			mp.registerCallback(this, app.getHandler());
			vd = mp.createVirtualDisplay(name, sc.getWidth(), sc.getHeight(), sc.getDpi(),
					DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY, sc.getSurface(), null, null);
			if (vd == null) throw new RuntimeException("Failed to create VirtualDisplay");
			Log.i("VirtualDisplay created");
		}

		@Override
		public void onStop() {
			close();
			var md = mdRef.get();
			if (md == null) return;
			Log.i("Media projection stopped");
			md.cleanUp();
		}

		void close() {
			mp.unregisterCallback(this);
			vd.release();
			mp.stop();
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

	private final class Overlay extends FrameLayout {
		final ReschedulableTask dimAndRotate = new ReschedulableTask() {
			@Override
			protected void perform() {
				var ctx = getContext();
				dimScreen(ctx);
				disableAccelRotation(ctx);
			}
		};

		public Overlay(@NonNull Context context) {
			super(context);
		}

		@SuppressLint("ClickableViewAccessibility")
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER) return false;
			Log.d("Temporary restoring brightness and rotation due to event ", event);
			dimAndRotate.schedule(30000);
			var ctx = getContext();
			restoreBrightness(ctx);
			restoreAccelRotation(ctx);
			return false;
		}
	}
}
