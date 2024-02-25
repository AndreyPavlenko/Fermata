package me.aap.fermata.auto;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.FermataApplication;
import me.aap.utils.log.Log;

public class XposedEventDispatcherService extends Service {
	static final int MSG_REGISTER = 0;
	static final int MSG_UNREGISTER = 1;
	static final int MSG_MIRROR_MODE = 2;
	static final int MSG_MOTION_EVENT = 3;
	static final int MSG_BACK_EVENT = 4;
	private static Messenger activityMessenger;
	private static int registrationKey;
	private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(@NonNull Message msg) {
			if (msg.what == MSG_REGISTER) {
				activityMessenger = msg.replyTo;
				registrationKey = msg.arg1;
				Log.i("Activity registered: ", activityMessenger, ", key: ", registrationKey);

				try {
					var mode = FermataApplication.get().getMirroringMode();
					activityMessenger.send(Message.obtain(null, MSG_MIRROR_MODE, mode, 0));
				} catch (RemoteException err) {
					Log.e(err);
				}
			} else if ((msg.what == MSG_UNREGISTER) && (registrationKey == msg.arg1)) {
				activityMessenger = null;
				Log.i("Activity unregistered: ", registrationKey);
			}
		}
	});

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		Log.i("Bound: ", intent);
		return messenger.getBinder();
	}

	static boolean canDispatchEvent() {
		return activityMessenger != null;
	}

	static boolean dispatchBackEvent() {
		if (!canDispatchEvent()) return false;
		try {
			activityMessenger.send(Message.obtain(null, MSG_BACK_EVENT));
			return true;
		} catch (Exception err) {
			Log.d(err, "Failed to send back event to ", activityMessenger);
			activityMessenger = null;
			return false;
		}
	}

	static boolean dispatchEvent(MotionEvent e) {
		if (!canDispatchEvent()) return false;
		try {
			var msg = Message.obtain(null, MSG_MOTION_EVENT);
			var b = new Bundle();
			b.putParcelable("e", e);
			msg.setData(b);
			activityMessenger.send(msg);
			return true;
		} catch (Exception err) {
			Log.d(err, "Failed to send motion event to ", activityMessenger);
			activityMessenger = null;
			return false;
		}
	}
}
