package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.circularreveal.CircularRevealFrameLayout;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.fermata.media.lib.MediaLib.PlayableItem;

/**
 * @author Andrey Pavlenko
 */
public class VideoView extends FrameLayout implements SurfaceHolder.Callback,
		View.OnLayoutChangeListener {
	private boolean surfaceCreated;

	public VideoView(Context context) {
		this(context, null);
		setLayoutParams(new CircularRevealFrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
	}

	public VideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setBackgroundColor(Color.BLACK);
		addView(new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
				setLayoutParams(lp);
				getHolder().addCallback(VideoView.this);
			}
		});

		TextView text = new TextView(context);
		text.setPadding(Utils.toPx(10), Utils.toPx(10), Utils.toPx(10), 0);
		text.setTextSize(20);
		text.setTextColor(Color.WHITE);
		text.setVisibility(GONE);
		addView(text);
		addOnLayoutChangeListener(this);
		setFocusable(true);
	}

	public SurfaceView getSurface() {
		return (SurfaceView) getChildAt(0);
	}

	public TextView getTitle() {
		return (TextView) getChildAt(1);
	}

	public void showVideo() {
		if (surfaceCreated) {
			MainActivityDelegate a = getActivity();
			MediaSessionCallback cb = a.getMediaServiceBinder().getMediaSessionCallback();
			MediaEngine eng = cb.getCurrentEngine();
			if (eng == null) return;

			PlayableItem i = eng.getSource();
			if ((i == null) || !i.isVideo()) return;

			setSurfaceSize(eng);
			cb.addVideoView(this, a.isCarActivity() ? 0 : 1);
			TextView title = getTitle();
			title.setText(i.getTitle());
			title.setVisibility(GONE);
		}
	}

	public void setSurfaceSize(MediaEngine eng) {
		SurfaceView surface = getSurface();
		ViewGroup.LayoutParams lp = surface.getLayoutParams();

		float videoWidth = eng.getVideoWidth();
		float videoHeight = eng.getVideoHeight();
		float videoRatio = videoWidth / videoHeight;

		float screenWidth = getWidth();
		float screenHeight = getHeight();
		float screenRatio = screenWidth / screenHeight;

		int width;
		int height;

		if (videoRatio > screenRatio) {
			width = (int) screenWidth;
			height = (int) (screenWidth / videoRatio);
		} else {
			width = (int) (screenHeight * videoRatio);
			height = (int) screenHeight;
		}

		if ((lp.width != width) || (lp.height != height)) {
			lp.width = width;
			lp.height = height;
			surface.setLayoutParams(lp);
		}
	}

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
														 int oldTop, int oldRight, int oldBottom) {
		FermataApplication.get().getHandler().post(() -> {
			if (!surfaceCreated) return;

			MediaEngine eng = getActivity().getMediaServiceBinder().getCurrentEngine();
			if (eng == null) return;

			PlayableItem i = eng.getSource();
			if ((i != null) && i.isVideo()) setSurfaceSize(eng);
		});
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		surfaceCreated = true;
		showVideo();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		getActivity().getMediaServiceBinder().getMediaSessionCallback().removeVideoView(this);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent e) {
		return getActivity().interceptTouchEvent(e, this::onTouch);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		MainActivityDelegate a;
		FermataServiceUiBinder b;

		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				a = getActivity();
				b = a.getMediaServiceBinder();
				b.onRwFfButtonClick(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
				a.getControlPanel().onVideoSeek();
				return true;
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
				a = getActivity();
				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(keyCode == KeyEvent.KEYCODE_DPAD_UP);
				a.getControlPanel().onVideoSeek();
				return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	private boolean onTouch(@NonNull MotionEvent e) {
		if (e.getAction() == MotionEvent.ACTION_DOWN) {
			getActivity().getControlPanel().onVideoViewTouch(this);
		}
		return true;
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
