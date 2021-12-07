package me.aap.fermata.ui.view;

import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.fermata.media.lib.MediaLib.PlayableItem;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static me.aap.utils.ui.UiUtils.isVisible;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.circularreveal.CircularRevealFrameLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public class VideoView extends FrameLayout implements SurfaceHolder.Callback,
		View.OnLayoutChangeListener, PreferenceStore.Listener, MainActivityListener {
	private final Set<PreferenceStore.Pref<?>> prefChange = new HashSet<>(Arrays.asList(
			MediaPrefs.VIDEO_SCALE, MediaPrefs.AUDIO_DELAY, MediaPrefs.SUB_DELAY
	));
	private boolean surfaceCreated;

	public VideoView(Context context) {
		this(context, null);
	}

	public VideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
		getActivity().onSuccess(a -> {
			a.addBroadcastListener(this);
			a.getLib().getPrefs().addBroadcastListener(this);
			showClock(a.getPrefs().getShowClockPref());
		});
	}

	protected void init(Context context) {
		setBackgroundColor(Color.BLACK);
		addView(new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
				setLayoutParams(lp);
				getHolder().addCallback(VideoView.this);
			}
		});
		addView(new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
				setLayoutParams(lp);
				setZOrderMediaOverlay(true);
				getHolder().setFormat(PixelFormat.TRANSLUCENT);
				getHolder().addCallback(VideoView.this);
			}
		});

		addInfoView(context);
		addOnLayoutChangeListener(this);
		setLayoutParams(new CircularRevealFrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		setFocusable(true);
	}

	protected void addInfoView(Context context) {
		VideInfoView d = new VideInfoView(context, null);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		d.setLayoutParams(lp);
		addView(d);
	}

	protected void addClockView(Context context) {
		inflate(context, R.layout.clock_view, this);
	}

	public SurfaceView getVideoSurface() {
		return (SurfaceView) getChildAt(0);
	}

	@Nullable
	public SurfaceView getSubtitleSurface() {
		return (SurfaceView) getChildAt(1);
	}


	public void showClock(boolean show) {
		int idx = getChildCount() - 1;
		boolean visible = (getChildAt(idx) instanceof TextClock);
		if (show == visible) return;
		if (show) addClockView(getContext());
		else removeViewAt(idx);
	}

	@Nullable
	public VideInfoView getVideoInfoView() {
		return (VideInfoView) getChildAt(2);
	}

	public void showVideo(boolean hideTitle) {
		if (surfaceCreated) {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaSessionCallback cb = a.getMediaSessionCallback();
			MediaEngine eng = cb.getEngine();
			if (eng == null) return;

			PlayableItem i = eng.getSource();
			if ((i == null) || !i.isVideo()) return;

			setSurfaceSize(eng);
			cb.addVideoView(this, a.isCarActivity() ? 0 : 1);

			VideInfoView info = getVideoInfoView();
			if (hideTitle && (info != null)) info.setVisibility(GONE);
		}
	}

	public void setSurfaceSize(MediaEngine eng) {
		if (eng.setSurfaceSize(this)) return;

		PlayableItem item = eng.getSource();
		if (item == null) return;

		SurfaceView surface = getVideoSurface();
		ViewGroup.LayoutParams lp = surface.getLayoutParams();

		float videoWidth = eng.getVideoWidth();
		float videoHeight = eng.getVideoHeight();
		float videoRatio = videoWidth / videoHeight;

		float screenWidth = getWidth();
		float screenHeight = getHeight();

		int width;
		int height;
		int scale = item.getPrefs().getVideoScalePref();

		switch (scale) {
			case SCALE_4_3:
			case SCALE_16_9:
				videoRatio = (scale == SCALE_16_9) ? 16f / 9f : 4f / 3f;
			default:
			case SCALE_BEST:
				float screenRatio = screenWidth / screenHeight;

				if (videoRatio > screenRatio) {
					width = (int) screenWidth;
					height = (int) (screenWidth / videoRatio);
				} else {
					width = (int) (screenHeight * videoRatio);
					height = (int) screenHeight;
				}

				break;
			case SCALE_FILL:
				if (videoWidth > videoHeight) {
					width = (int) screenWidth;
					height = (int) (screenWidth / videoRatio);
				} else {
					width = (int) (screenHeight * videoRatio);
					height = (int) screenHeight;
				}

				break;
			case SCALE_ORIGINAL:
				width = (int) videoWidth;
				height = (int) videoHeight;
				break;
		}

		if ((lp.width != width) || (lp.height != height)) {
			lp.width = width;
			lp.height = height;
			surface.setLayoutParams(lp);
		}

		if ((surface = getSubtitleSurface()) != null) {
			lp = surface.getLayoutParams();

			if ((lp.width != width) || (lp.height != height)) {
				lp.width = width;
				lp.height = height;
				surface.setLayoutParams(lp);
			}
		}
	}

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
														 int oldTop, int oldRight, int oldBottom) {
		FermataApplication.get().getHandler().post(() -> {
			if (!surfaceCreated) return;

			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
			if (eng == null) return;

			PlayableItem i = eng.getSource();
			if ((i != null) && i.isVideo()) setSurfaceSize(eng);
		});
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder holder) {
		if (!getVideoSurface().getHolder().getSurface().isValid()) return;
		SurfaceView s = getSubtitleSurface();
		if ((s != null) && !s.getHolder().getSurface().isValid()) return;
		surfaceCreated = true;
		showVideo(true);
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		surfaceCreated = false;
		getActivity().onSuccess(a -> a.getMediaSessionCallback().removeVideoView(this));
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent e) {
		MainActivityDelegate a = getActivity().peek();
		return (a != null) && a.interceptTouchEvent(e, this::onTouch);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		MainActivityDelegate a;
		FermataServiceUiBinder b;
		ControlPanelView p;

		switch (keyCode) {
			case KEYCODE_ENTER:
			case KEYCODE_DPAD_CENTER:
				if ((a = getActivity().peek()) == null) break;
				return a.getControlPanel().onTouch(this);
			case KEYCODE_DPAD_LEFT:
			case KEYCODE_DPAD_RIGHT:
				if ((a = getActivity().peek()) == null) break;
				p = a.getControlPanel();

				if (!p.isVideoSeekMode() && !a.getBody().isVideoMode()) {
					View v = focusSearch(this, (keyCode == KEYCODE_DPAD_LEFT) ? FOCUS_LEFT : FOCUS_RIGHT);
					if (v != null) {
						v.requestFocus();
						return true;
					} else {
						break;
					}
				}

				b = a.getMediaServiceBinder();
				b.onRwFfButtonClick(keyCode == KEYCODE_DPAD_RIGHT);
				a.getControlPanel().onVideoSeek();
				return true;
			case KEYCODE_DPAD_UP:
				if ((a = getActivity().peek()) == null) break;
				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(true);
				a.getControlPanel().onVideoSeek();
				return true;
			case KEYCODE_DPAD_DOWN:
				if ((a = getActivity().peek()) == null) break;
				p = a.getControlPanel();

				if (!p.isVideoSeekMode() && isVisible(p)) {
					View v = p.focusSearch();
					if (v != null) {
						v.requestFocus();
						return true;
					} else {
						break;
					}
				}

				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(false);
				a.getControlPanel().onVideoSeek();
				return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	private boolean onTouch(@NonNull MotionEvent e) {
		MainActivityDelegate a = getActivity().peek();
		if (a == null) return false;
		a.getControlPanel().onVideoViewTouch(this, e);
		return true;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (surfaceCreated && !Collections.disjoint(prefChange, prefs)) {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaEngine eng = a.getMediaSessionCallback().getEngine();
			if (eng == null) return;
			PlayableItem i = eng.getSource();
			if ((i == null) || !i.isVideo()) return;

			if (prefs.contains(MediaPrefs.VIDEO_SCALE)) {
				setSurfaceSize(eng);
			} else if (prefs.contains(MediaPrefs.AUDIO_DELAY)) {
				eng.setAudioDelay(i.getPrefs().getAudioDelayPref());
			} else if (prefs.contains(MediaPrefs.SUB_DELAY)) {
				eng.setSubtitleDelay(i.getPrefs().getSubDelayPref());
			}
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			a.getMediaSessionCallback().removeVideoView(this);
			a.getLib().getPrefs().removeBroadcastListener(this);
		}
	}

	@Override
	public View focusSearch(View focused, int direction) {
		MainActivityDelegate a = getActivity().peek();
		if ((a == null) || !a.getBody().isBothMode()) return focused;

		if (direction == FOCUS_LEFT) {
			return MediaItemListView.focusSearchActive(getContext(), focused);
		} else if (direction == FOCUS_RIGHT) {
			NavBarView n = a.getNavBar();
			if (n.isRight()) return n.focusSearch();
		}

		return focused;
	}

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}
}
