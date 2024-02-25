package me.aap.fermata.auto;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.SYSTEM_UI_FLAG_VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.removeAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;
import static me.aap.fermata.BuildConfig.APPLICATION_ID;
import static me.aap.fermata.auto.LauncherActivity.INTENT_EXTRA_MODE;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.InstallSourceInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * @author Andrey Pavlenko
 */
public class Xposed implements IXposedHookLoadPackage {
	private static final String AA_PKG = "com.google.android.projection.gearhead";
	private static final String GPLAY_PKG = "com.android.vending";
	private static final String EV_SRV_CON_FIELD = "__fermata_event_dispatcher_service__";
	private static final String PKG_MGR_CLASS = "android.content.pm.IPackageManager.Stub.Proxy";
	private static final String DISPATCHER_CLASS_NAME =
			"me.aap.fermata.auto.XposedEventDispatcherService";

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
		var pkgName = lpparam.packageName;
		log("[Fermata] Hooking into package " + pkgName);

		if (AA_PKG.equals(pkgName) || APPLICATION_ID.equals(pkgName)) {
			if (VERSION.SDK_INT >= VERSION_CODES.R) {
				findAndHookMethod(InstallSourceInfo.class, "getInitiatingPackageName",
						new XC_MethodHook() {

							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								param.setResult(GPLAY_PKG);
							}
						});
			} else {
				findAndHookMethod(PKG_MGR_CLASS, lpparam.classLoader, "getInstallerPackageName",
						String.class, new XC_MethodHook() {
							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								param.setResult(GPLAY_PKG);
							}
						});
			}
			return;
		}

		findAndHookMethod(Activity.class, "onStart", new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				var a = (Activity) param.thisObject;
				debug("onStart: ", a);
				register(a);
				handleIntent(a, a.getIntent());
			}
		});
		findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				var a = (Activity) param.thisObject;
				debug("onResume: ", a);
				register(a);
			}
		});
		findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				var a = param.thisObject;
				debug("onPause: ", a);
				var con = (EventServiceConnection) getAdditionalInstanceField(a, EV_SRV_CON_FIELD);
				if (con != null) con.unregister();
			}
		});
		findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {

			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				var a = param.thisObject;
				debug("onDestroy: ", a);
				var con = (EventServiceConnection) removeAdditionalInstanceField(a, EV_SRV_CON_FIELD);
				if (con != null) con.disconnect();
			}
		});
		findAndHookMethod(Activity.class, "onConfigurationChanged", Configuration.class,
				new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						var a = param.thisObject;
						debug("onConfigurationChanged: ", a);
						var con = (EventServiceConnection) getAdditionalInstanceField(a, EV_SRV_CON_FIELD);
						if ((con != null) && (con.mirroringMode != 0)) con.configureActivity();
					}
				});
		findAndHookMethod(Activity.class, "onNewIntent", Intent.class, new XC_MethodHook() {

			@Override
			protected void afterHookedMethod(MethodHookParam param) {
				var intent = (Intent) param.args[0];
				if (!intent.hasExtra(INTENT_EXTRA_MODE)) return;
				var a = (Activity) param.thisObject;
				debug("onNewIntent: ", a);
				handleIntent(a, intent);
			}
		});
	}

	private static void register(Activity a) {
		var con = (EventServiceConnection) getAdditionalInstanceField(a, EV_SRV_CON_FIELD);
		if (con == null) {
			con = new EventServiceConnection(a);
			setAdditionalInstanceField(a, EV_SRV_CON_FIELD, con);
		}
		con.register();
	}

	private static void handleIntent(Activity a, Intent intent) {
		if (intent == null) return;
		var con = (EventServiceConnection) getAdditionalInstanceField(a, EV_SRV_CON_FIELD);
		if (con != null) {
			con.mirroringMode = intent.getIntExtra(INTENT_EXTRA_MODE, 0);
			con.configureActivity();
		}
	}

	private static void debug(Object... msg) {
		Object message;
		if (msg.length == 1) {
			message = msg[0];
		} else {
			var sb = new StringBuilder("[Fermata] ");
			for (var m : msg) sb.append(m);
			message = sb;
		}
		log(String.valueOf(message));
	}

	private static class EventServiceConnection extends Handler implements ServiceConnection {
		private static final int FULLSCREEN_FLAGS =
				SYSTEM_UI_FLAG_LAYOUT_STABLE | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
						SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | SYSTEM_UI_FLAG_LOW_PROFILE |
						SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_HIDE_NAVIGATION | SYSTEM_UI_FLAG_IMMERSIVE |
						SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		private static final int SCREEN_ON_FLAGS =
				FLAG_KEEP_SCREEN_ON | FLAG_TURN_SCREEN_ON | FLAG_DISMISS_KEYGUARD | FLAG_SHOW_WHEN_LOCKED;
		private final int[] loc = new int[2];
		private final Messenger replyTo = new Messenger(this);
		private final Activity activity;
		private Messenger messenger;
		private int registrationKey;
		private int mirroringMode;
		private boolean connecting;
		private boolean registered;

		EventServiceConnection(Activity activity) {
			super(Looper.getMainLooper());
			this.activity = activity;
			if (VERSION.SDK_INT >= VERSION_CODES.Q) {
				activity.setInheritShowWhenLocked(true);
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			debug("Connected: ", activity);
			connecting = false;
			messenger = new Messenger(service);
			if (registered) register();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			debug("Disconnected: ", activity);
			connecting = false;
			messenger = null;
			restore();
		}

		@Override
		public void onBindingDied(ComponentName name) {
			debug("Connection died: ", activity);
			connecting = false;
			messenger = null;
			restore();
			connect();
		}

		@Override
		public void handleMessage(@NonNull Message msg) {
			switch (msg.what) {
				case XposedEventDispatcherService.MSG_MIRROR_MODE -> {
					mirroringMode = msg.arg1;
					configureActivity();
				}
				case XposedEventDispatcherService.MSG_MOTION_EVENT -> {
					MotionEvent e;
					if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
						e = msg.getData().getParcelable("e", MotionEvent.class);
					} else {
						e = msg.getData().getParcelable("e");
					}
					if (e == null) return;
					activity.getWindow().getDecorView().getLocationOnScreen(loc);
					e.setLocation(e.getX() - loc[0], e.getY() - loc[1]);
					activity.dispatchTouchEvent(e);
				}
				case XposedEventDispatcherService.MSG_BACK_EVENT -> activity.onBackPressed();
			}
		}

		void connect() {
			if (connecting) {
				debug("Waiting for connection: ", activity);
				return;
			}
			if (messenger != null) {
				debug("Already connected: ", activity);
				return;
			}
			try {
				connecting = true;
				debug("Connecting: ", activity);
				var i = new Intent();
				i.setComponent(new ComponentName(APPLICATION_ID, DISPATCHER_CLASS_NAME));
				if (!activity.bindService(i, this, 0)) {
					debug("Unable to establish connection: ", activity);
					connecting = false;
					activity.unbindService(this);
				}
			} catch (Exception err) {
				connecting = false;
				debug("Connection attempt failed: ", activity);
			}
		}

		void disconnect() {
			try {
				debug("Disconnecting: ", activity);
				restore();
				activity.unbindService(this);
			} catch (Exception ignore) {}
		}

		void register() {
			if (isWindowMode()) return;
			registered = true;
			if (messenger == null) {
				connect();
				return;
			}
			try {
				debug("Registering: ", activity);
				registrationKey = Long.hashCode(SystemClock.uptimeMillis()) ^ hashCode();
				var msg =
						Message.obtain(null, XposedEventDispatcherService.MSG_REGISTER, registrationKey, 0);
				msg.replyTo = this.replyTo;
				messenger.send(msg);
			} catch (RemoteException err) {
				debug(err, "Failed to register: ", activity);
			}
		}

		void unregister() {
			registered = false;
			restore();
			if (messenger == null) return;
			try {
				debug("Unregistering: ", activity);
				var msg =
						Message.obtain(null, XposedEventDispatcherService.MSG_UNREGISTER, registrationKey, 0);
				messenger.send(msg);
			} catch (RemoteException err) {
				debug(err, "Failed to unregister: ", activity);
			}
		}

		private void restore() {
			mirroringMode = 0;
			configureActivity();
		}

		void configureActivity() {
			if (isWindowMode()) {
				unregister();
				return;
			}
			var w = activity.getWindow();
			var decor = w.getDecorView();

			if (mirroringMode == 0) {
				debug("Disabling mirroring mode: ", activity);
				w.clearFlags(SCREEN_ON_FLAGS);
				decor.setSystemUiVisibility(SYSTEM_UI_FLAG_VISIBLE);
				activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			} else {
				debug("Enabling mirroring mode: ", activity);
				w.addFlags(SCREEN_ON_FLAGS);
				decor.setSystemUiVisibility(FULLSCREEN_FLAGS);
				activity.setRequestedOrientation(
						(mirroringMode == 1) ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
								SCREEN_ORIENTATION_SENSOR_PORTRAIT);
			}
		}

		private boolean isWindowMode() {
			return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) &&
					(activity.isInMultiWindowMode() || activity.isInPictureInPictureMode());
		}
	}
}
