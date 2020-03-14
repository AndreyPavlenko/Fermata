package me.aap.fermata.media.service;

import android.app.Service;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

import androidx.annotation.CallSuper;

/**
 * @author Andrey Pavlenko
 */
public abstract class ControlServiceConnection extends Handler implements ServiceConnection {
	public static final String ACTION_CONTROL_SERVICE = "me.aap.fermata.action.ControlService";
	protected static final byte MSG_KEY_EVENT = 0;
	protected static final byte MSG_GET_CHILDREN = 1;
	protected static final byte MSG_SESSION_STATE = 2;
	protected static final byte MSG_PLAY = 3;
	protected static final byte MSG_PAUSE = 4;
	protected static final byte MSG_STOP = 5;
	protected static final byte MSG_PREV = 6;
	protected static final byte MSG_NEXT = 7;
	protected static final byte MSG_RW = 8;
	protected static final byte MSG_FF = 9;
	protected static final byte MSG_SEEK = 10;
	protected static final byte MSG_SKIP_TO_QI = 11;
	protected static final byte MSG_CUSTOM_ACTION = 12;
	protected static final String KEY = "k";
	protected final Service service;
	protected final Messenger localMessenger = new Messenger(this);
	protected Messenger remoteMessenger;

	public ControlServiceConnection(Service service) {
		this.service = service;
	}

	public abstract void connect();

	@CallSuper
	public void disconnect(boolean release) {
		if (remoteMessenger != null) {
			remoteMessenger = null;
			service.unbindService(this);
		}
	}

	public void reconnect() {
		disconnect(false);
		connect();
	}

	public IBinder getBinder() {
		return localMessenger.getBinder();
	}

	@Override
	@CallSuper
	public void onServiceConnected(ComponentName name, IBinder remove) {
		Log.i(getClass().getName(), "Connected to remote service: " + name);
		remoteMessenger = new Messenger(remove);
	}

	@Override
	@CallSuper
	public void onServiceDisconnected(ComponentName name) {
		if (remoteMessenger != null) disconnect(false);
	}
}
