package me.aap.fermata.action;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.ADJUST_TOGGLE_MUTE;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.os.SystemClock.uptimeMillis;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import android.content.Context;
import android.media.AudioManager;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public enum Action {
	STOP(R.string.action_stop, m(MediaSessionCallback::onStop)),
	PLAY(R.string.action_play, m(MediaSessionCallback::onPlay)),
	PAUSE(R.string.action_pause, m(MediaSessionCallback::onPause)),
	PLAY_PAUSE(R.string.action_play_pause, m(cb -> {
		if (cb.isPlaying()) cb.onPause();
		else cb.onPlay();
	})),
	PREV(R.string.action_prev, m(MediaSessionCallback::onSkipToPrevious)),
	NEXT(R.string.action_next, m(MediaSessionCallback::onSkipToNext)),
	RW(R.string.action_rw, new RwFfHandler(false)),
	FF(R.string.action_ff, new RwFfHandler(true)),
	VOLUME_UP(R.string.action_vol_up, new VolumeHandler(ADJUST_RAISE)),
	VOLUME_DOWN(R.string.action_vol_down, new VolumeHandler(ADJUST_LOWER)),
	VOLUME_MUTE_UNMUTE(R.string.action_vol_mute_unmute, new VolumeHandler(ADJUST_TOGGLE_MUTE)),
	ACTIVATE_VOICE_CTRL(R.string.action_activate_voice_ctrl,
			m(cb -> cb.getAssistant().startVoiceAssistant())),
	MENU(R.string.action_menu, a(a -> a.getNavBarMediator().showMenu(a))),
	CP_MENU(R.string.action_cp_menu, a(a -> {
		var cp = a.getControlPanel();
		if (cp.isActive()) cp.showMenu();
	})),
	BACK_OR_EXIT(R.string.action_back_or_exit, a(ActivityDelegate::onBackPressed)),
	EXIT(R.string.action_exit, a(ActivityDelegate::finish)),
	NONE(R.string.action_none, m(cb -> {})),
	;

	private static final List<Action> all = unmodifiableList(asList(values()));

	@StringRes
	private final int name;
	private final Action.Handler handler;

	Action(int name, Action.Handler handler) {
		this.name = name;
		this.handler = handler;
	}

	@Nullable
	public static Action get(int ordinal) {
		return (ordinal >= 0) && (ordinal < all.size()) ? all.get(ordinal) : null;
	}

	public static List<Action> getAll() {
		return all;
	}

	@StringRes
	public int getName() {
		return name;
	}

	public Handler getHandler() {
		return handler;
	}

	private static Handler m(MediaHandler h) {
		return h;
	}

	private static Handler a(ActivityHandler h) {
		return h;
	}

	public interface Handler {
		void handle(MediaSessionCallback cb, @Nullable MainActivityDelegate a, long timestamp);
	}

	private interface MediaHandler extends Handler {
		void handle(MediaSessionCallback cb);

		@Override
		default void handle(MediaSessionCallback cb, @Nullable MainActivityDelegate a,
												long timestamp) {
			handle(cb);
		}
	}

	private interface ActivityHandler extends Handler {
		void handle(MainActivityDelegate a);

		@Override
		default void handle(MediaSessionCallback cb, @Nullable MainActivityDelegate a,
												long timestamp) {
			if (a != null) handle(a);
		}
	}

	private static final class VolumeHandler implements Handler {
		private final int direction;

		VolumeHandler(int direction) {this.direction = direction;}

		@Override
		public void handle(MediaSessionCallback cb, @Nullable MainActivityDelegate a, long timestamp) {
			var eng = cb.getEngine();
			if ((eng != null) && eng.adjustVolume(direction)) return;
			var ctx = (a == null) ? App.get() : a.getContext();
			var amgr = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
			if (amgr != null) amgr.adjustStreamVolume(STREAM_MUSIC, direction, FLAG_SHOW_UI);
		}
	}

	private static final class RwFfHandler implements Handler {
		private final boolean ff;

		private RwFfHandler(boolean ff) {this.ff = ff;}

		@Override
		public void handle(MediaSessionCallback cb, @Nullable MainActivityDelegate a, long timestamp) {
			cb.rewindFastForward(ff, (int) ((uptimeMillis() - timestamp) / 1000));
		}
	}
}
