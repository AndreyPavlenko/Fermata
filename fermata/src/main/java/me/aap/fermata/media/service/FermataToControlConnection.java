package me.aap.fermata.media.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.lib.MediaLib.PlayableItem;

import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_INVALID;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_INVALID;
import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
class FermataToControlConnection extends ControlServiceConnection {
	static final String PKG_ID = "me.aap.fermata.auto.control";

	FermataToControlConnection(FermataMediaService service) {
		super(service);
	}

	public FermataMediaService getService() {
		return (FermataMediaService) service;
	}

	@Override
	public void handleMessage(@NonNull Message msg) {
		Log.d(getClass().getName(), "Message received: " + msg);

		switch (msg.what) {
			case MSG_KEY_EVENT:
				if (getService().callback == null) return;
				getService().callback.onMediaButtonEvent(new KeyEvent(msg.arg1, msg.arg2));
				break;
			case MSG_GET_CHILDREN:
				Bundle b = msg.getData();
				int arg = msg.arg1;
				getService().getLib().getChildren(b.getString(KEY), (r, err) -> {
					Message m = Message.obtain(this, MSG_GET_CHILDREN, arg, 0);
					if (r != null) {
						Bundle d = new Bundle();
						d.putParcelableArray(KEY, r.toArray(new Parcelable[0]));
						m.setData(d);
					} else {
						m.setData(err);
					}
					send(m);
				});
				break;
			case MSG_PLAY:
				b = msg.getData();
				String id = b.getString(KEY);
				if (id == null) getService().callback.onPlay();
				else getService().callback.onPlayFromMediaId(id, null);
				break;
			case MSG_PAUSE:
				getService().callback.onPause();
				break;
			case MSG_STOP:
				getService().callback.onStop();
				break;
			case MSG_PREV:
				getService().callback.onSkipToPrevious();
				break;
			case MSG_NEXT:
				getService().callback.onSkipToNext();
				break;
			case MSG_RW:
				getService().callback.onRewind();
				break;
			case MSG_FF:
				getService().callback.onFastForward();
				break;
			case MSG_SEEK:
				getService().callback.onSeekTo(msg.arg1);
				break;
			case MSG_SKIP_TO_QI:
				getService().callback.onSkipToQueueItem(msg.arg1);
				break;
			case MSG_CUSTOM_ACTION:
				b = msg.getData();
				getService().callback.onCustomAction(requireNonNull(b.getString(KEY)), null);
				break;
			default:
				Log.e(getClass().getName(), "Unknown message received: " + msg.what);
		}
	}

	private void send(Message msg) {
		try {
			if (remoteMessenger != null) {
				remoteMessenger.send(msg);
				return;
			}
		} catch (Exception ex) {
			Log.e(getClass().getName(), "Failed to send message", ex);
		}

		reconnect();
	}

	public void sendPlaybackState(MediaSessionState state) {
		Message m = Message.obtain(this, MSG_SESSION_STATE);
		Bundle b = new Bundle();
		b.putParcelable(KEY, state);
		m.setData(b);
		send(m);
	}

	public void connect() {
		try {
			Intent i = new Intent(ACTION_CONTROL_SERVICE);
			i.setComponent(new ComponentName(PKG_ID, "me.aap.fermata.auto.control.FermataMediaServiceControl"));
			getService().bindService(i, this, Context.BIND_AUTO_CREATE);
		} catch (Exception ex) {
			Log.d(getClass().getName(), "Failed to connect to remote service", ex);
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder remove) {
		super.onServiceConnected(name, remove);
		MediaSessionCallback cb = getService().callback;

		if (cb != null) {
			MediaMetadataCompat meta = null;
			List<MediaSessionCompat.QueueItem> queue = Collections.emptyList();
			PlayableItem i = cb.getCurrentItem();

			if (i != null) {
				meta = cb.getMetadata(i);
				queue = i.getParent().getQueue();
			}

			sendPlaybackState(new MediaSessionState(getService().callback.getPlaybackState(), meta,
					queue, REPEAT_MODE_INVALID, SHUFFLE_MODE_INVALID));
		}
	}
}
