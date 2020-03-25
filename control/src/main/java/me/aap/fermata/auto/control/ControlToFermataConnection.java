package me.aap.fermata.auto.control;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.service.ControlServiceConnection;
import me.aap.fermata.media.service.MediaSessionState;
import me.aap.fermata.media.service.SharedConstants;

import static android.support.v4.media.session.PlaybackStateCompat.REPEAT_MODE_INVALID;
import static android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_INVALID;
import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
class ControlToFermataConnection extends ControlServiceConnection implements SharedConstants {
	private final MediaSessionCompat session;
	private int counter;
	private Consumer<ControlToFermataConnection> pendingReq;
	private Consumer<Message> pendingResp;

	ControlToFermataConnection(FermataMediaServiceControl service) {
		super(service);
		session = new MediaSessionCompat(service, "FermataControl");
		service.setSessionToken(session.getSessionToken());
		session.setCallback(new ControlCallback());
		Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON, null, service, MediaButtonReceiver.class);
		session.setMediaButtonReceiver(PendingIntent.getBroadcast(service, 0, i, 0));
	}

	@Override
	public void handleMessage(@NonNull Message msg) {
		Log.d(getClass().getName(), "Message received: " + msg);

		switch (msg.what) {
			case MSG_GET_CHILDREN:
				Consumer<Message> resp = pendingResp;
				if (resp != null) {
					pendingResp = null;
					resp.accept(msg);
				}
				break;
			case MSG_SESSION_STATE:
				if (session == null) return;
				Bundle b = msg.getData();
				b.setClassLoader(MediaSessionState.class.getClassLoader());
				MediaSessionState state = requireNonNull(b.getParcelable(KEY));
				applySessionState(state, session);
				break;
			default:
				Log.e(getClass().getName(), "Unknown message received: " + msg.what);
		}
	}

	void loadChildren(String parentId, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
		if (pendingReq != null) pendingReq.accept(null); // Cancel
		if (pendingResp != null) pendingResp.accept(null); // Cancel

		int stamp = ++counter;

		if (remoteMessenger != null) {
			try {
				Message msg = Message.obtain(this, MSG_GET_CHILDREN, stamp, 0);
				Bundle b = new Bundle();
				b.putString(KEY, parentId);
				msg.setData(b);
				pendingResp = m -> {
					if ((m == null) || (m.arg1 != stamp)) {
						result.sendResult(Collections.emptyList());
					} else {
						Bundle d = m.getData();
						d.setClassLoader(MediaBrowserCompat.MediaItem.class.getClassLoader());
						Parcelable[] a = requireNonNull(d.getParcelableArray(KEY));
						ArrayList<MediaBrowserCompat.MediaItem> items = new ArrayList<>(a.length);
						for (Parcelable p : a) {
							items.add((MediaBrowserCompat.MediaItem) p);
						}
						result.sendResult(items);
					}
				};
				remoteMessenger.send(msg);
				return;
			} catch (Exception ex) {
				Log.e(getClass().getName(), "Failed to send MSG_GET_CHILDREN", ex);
				pendingResp = null;
			}
		}

		pendingReq = c -> {
			if ((c == null) || (c.counter != stamp)) result.sendResult(Collections.emptyList());
			else c.loadChildren(parentId, result);
		};

		reconnect();
	}

	void send(int msg) {
		send(Message.obtain(this, msg));
	}

	void send(int msg, String value, int arg) {
		Message m = Message.obtain(this, msg, arg, 0);
		if (value != null) {
			Bundle b = new Bundle();
			b.putString(KEY, value);
			m.setData(b);
		}
		send(m);
	}

	void send(Message msg) {
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

	public void connect() {
		Intent i = new Intent(ACTION_CONTROL_SERVICE);
		String pkg = BuildConfig.DEBUG ? "me.aap.fermata.auto.debug" : "me.aap.fermata.auto";
		i.setComponent(new ComponentName(pkg, "me.aap.fermata.media.service.FermataMediaService"));
		service.bindService(i, this, Context.BIND_AUTO_CREATE);
	}

	public void disconnect(boolean release) {
		if (release) {
			session.release();

			if (pendingResp != null) {
				pendingResp.accept(null);
				pendingResp = null;
			}
		} else {
			session.setActive(false);
		}

		super.disconnect(release);
	}

	public void onServiceConnected(ComponentName name, IBinder remote) {
		super.onServiceConnected(name, remote);
		session.setActive(true);

		if (pendingReq != null) {
			Consumer<ControlToFermataConnection> req = pendingReq;
			pendingReq = null;
			req.accept(this);
		}
	}

	public void applySessionState(MediaSessionState st, MediaSessionCompat session) {
		if (st.playbackState != null) {
			List<PlaybackStateCompat.CustomAction> actions = st.playbackState.getCustomActions();

			if ((actions != null) && !actions.isEmpty()) {
				List<PlaybackStateCompat.CustomAction> newActions = new ArrayList<>(actions.size());
				for (PlaybackStateCompat.CustomAction a : actions) {
					newActions.add(new PlaybackStateCompat.CustomAction.Builder(a.getAction(), a.getName(), getActionIcon(a)).build());
				}
				actions.clear();
				actions.addAll(newActions);
			}

			session.setPlaybackState(st.playbackState);
		}

		if (st.meta != null) session.setMetadata(st.meta);
		if (st.queue != null) session.setQueue(st.queue.isEmpty() ? null : st.queue);
		if (st.repeat != REPEAT_MODE_INVALID) session.setRepeatMode(st.repeat);
		if (st.shuffle != SHUFFLE_MODE_INVALID) session.setShuffleMode(st.shuffle);
	}

	private static int getActionIcon(PlaybackStateCompat.CustomAction a) {
		switch (a.getAction()) {
			case CUSTOM_ACTION_RW:
				return R.drawable.rw;
			case CUSTOM_ACTION_FF:
				return R.drawable.ff;
			case CUSTOM_ACTION_REPEAT_ENABLE:
				return R.drawable.repeat;
			case CUSTOM_ACTION_REPEAT_DISABLE:
				return R.drawable.repeat_filled;
			case CUSTOM_ACTION_FAVORITES_ADD:
				return R.drawable.favorite;
			case CUSTOM_ACTION_FAVORITES_REMOVE:
				return R.drawable.favorite_filled;
			default:
				return 0;
		}
	}

	private final class ControlCallback extends MediaSessionCompat.Callback {

		@Override
		public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
			Message m = Message.obtain(ControlToFermataConnection.this, MSG_MEDIA_BTN_EVENT);
			Bundle b = new Bundle();
			b.putParcelable(KEY, mediaButtonEvent);
			m.setData(b);
			send(m);
			return true;
		}

		@Override
		public void onPlay() {
			send(MSG_PLAY);
		}

		@Override
		public void onPlayFromMediaId(String mediaId, Bundle extras) {
			send(MSG_PLAY, mediaId, 0);
		}

		@Override
		public void onPause() {
			send(MSG_PAUSE);
		}

		@Override
		public void onStop() {
			send(MSG_STOP);
		}

		@Override
		public void onSkipToPrevious() {
			send(MSG_PREV);
		}

		@Override
		public void onSkipToNext() {
			send(MSG_NEXT);
		}

		@Override
		public void onRewind() {
			send(MSG_RW);
		}

		@Override
		public void onFastForward() {
			send(MSG_FF);
		}

		@Override
		public void onSeekTo(long pos) {
			send(MSG_SEEK, null, (int) pos);
		}

		@Override
		public void onSkipToQueueItem(long id) {
			send(MSG_SKIP_TO_QI, null, (int) id);
		}

		@Override
		public void onCustomAction(String action, Bundle extras) {
			send(MSG_CUSTOM_ACTION, action, 0);
		}
	}

	private interface Consumer<T> {
		void accept(T t);
	}
}
