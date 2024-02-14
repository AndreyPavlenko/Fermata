package me.aap.fermata.auto;

import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.removeAdditionalInstanceField;
import static de.robv.android.xposed.XposedHelpers.setAdditionalInstanceField;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.InstallSourceInfo;
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
import me.aap.fermata.BuildConfig;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class Xposed implements IXposedHookLoadPackage {
	private static final String AA_PKG = "com.google.android.projection.gearhead";
	private static final String GPLAY_PKG = "com.android.vending";
	private static final String EV_SRV_CON_FIELD = "__fermata_event_dispatcher_service__";

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
		log("Hooking package " + lpparam.packageName);

		if (AA_PKG.equals(lpparam.packageName)) {
			if (VERSION.SDK_INT >= VERSION_CODES.R) {
				findAndHookMethod(InstallSourceInfo.class, "getInitiatingPackageName",
						new XC_MethodHook() {

							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								param.setResult(GPLAY_PKG);
							}
						});
			} else {
				findAndHookMethod("android.content.pm.IPackageManager.Stub.Proxy", lpparam.classLoader,
						"getInstallerPackageName", String.class, new XC_MethodHook() {
							@Override
							protected void afterHookedMethod(MethodHookParam param) {
								param.setResult(GPLAY_PKG);
							}
						});
			}
		} else {
			findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {

				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					var a = (Activity) param.thisObject;
					var con = (EventServiceConnection) getAdditionalInstanceField(a, EV_SRV_CON_FIELD);
					if (con == null) {
						con = new EventServiceConnection(a);
						setAdditionalInstanceField(a, EV_SRV_CON_FIELD, con);
					}
					con.register();
				}
			});
			findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {

				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					var a = param.thisObject;
					var con = (EventServiceConnection) getAdditionalInstanceField(a, EV_SRV_CON_FIELD);
					if (con != null) con.unregister();
				}
			});
			findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {

				@Override
				protected void beforeHookedMethod(MethodHookParam param) {
					var a = param.thisObject;
					var con = (EventServiceConnection) removeAdditionalInstanceField(a, EV_SRV_CON_FIELD);
					if (con != null) con.disconnect();
				}
			});
		}
	}

	private static void debug(Object... msg) {
		if (!BuildConfig.D) return;
		Object message;
		if (msg.length == 1) {
			message = msg[0];
		} else {
			var tb = SharedTextBuilder.get();
			for (var m : msg) tb.append(m);
			message = tb.releaseString();
		}
		Log.d(message);
		log(String.valueOf(message));
	}

	private static void debug(Throwable err, Object... msg) {
		if (!BuildConfig.D) return;
		Object message;
		if (msg.length == 1) {
			message = msg[0];
		} else {
			var tb = SharedTextBuilder.get();
			for (var m : msg) tb.append(m);
			message = tb.releaseString();
		}
		Log.d(err, message);
		log(String.valueOf(message));
	}

	private static class EventServiceConnection extends Handler implements ServiceConnection {
		private final int[] loc = new int[2];
		private final Messenger replyTo = new Messenger(this);
		private final Activity activity;
		private Messenger messenger;
		private int registrationKey;
		private boolean connecting;
		private boolean registered;

		EventServiceConnection(Activity activity) {
			super(Looper.getMainLooper());
			this.activity = activity;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			debug("Connected to ", name);
			connecting = false;
			messenger = new Messenger(service);
			if (registered) register();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			debug("Disconnected from ", name);
			connecting = false;
			messenger = null;
		}

		@Override
		public void onBindingDied(ComponentName name) {
			disconnect();
			connect();
		}

		@Override
		public void handleMessage(@NonNull Message msg) {
			switch (msg.what) {
				case XposedEventDispatcherService.MSG_MOTION_EVENT -> {
					MotionEvent e;
					if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
						e = msg.getData().getParcelable("e", MotionEvent.class);
					} else {
						e = msg.getData().getParcelable("e");
					}
					if (e == null) return;
					debug("Event received: ", msg);
					activity.getWindow().getDecorView().getLocationOnScreen(loc);
					e.setLocation(e.getX() - loc[0], e.getY() - loc[1]);
					activity.dispatchTouchEvent(e);
				}
				case XposedEventDispatcherService.MSG_BACK_EVENT -> activity.onBackPressed();
			}
		}

		void connect() {
			if (connecting) return;
			if (messenger != null) return;
			try {
				connecting = true;
				debug("Connecting to the Fermata event service: ", activity);
				var i = new Intent();
				i.setComponent(new ComponentName(BuildConfig.APPLICATION_ID,
						"me.aap.fermata.auto.XposedEventDispatcherService"));
				activity.bindService(i, this, 0);
			} catch (Exception err) {
				connecting = false;
				debug("Failed to bind XposedEventDispatcherService");
			}
		}

		void disconnect() {
			debug("Disconnecting from the Fermata event service: ", activity);
			activity.unbindService(this);
		}

		void register() {
			registered = true;
			if (messenger == null) {
				connect();
				return;
			}
			try {
				debug("Registering in XposedEventDispatcherService");
				registrationKey = Long.hashCode(SystemClock.uptimeMillis()) ^ hashCode();
				var msg =
						Message.obtain(null, XposedEventDispatcherService.MSG_REGISTER, registrationKey, 0);
				msg.replyTo = this.replyTo;
				messenger.send(msg);
			} catch (RemoteException err) {
				debug(err, "Failed to register activity");
			}
		}

		void unregister() {
			registered = false;
			if (messenger == null) return;
			try {
				debug("Unregistering from XposedEventDispatcherService");
				var msg =
						Message.obtain(null, XposedEventDispatcherService.MSG_UNREGISTER, registrationKey, 0);
				messenger.send(msg);
			} catch (RemoteException err) {
				debug(err, "Failed to unregister activity");
			}
		}
	}
}
