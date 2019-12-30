package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static me.aap.fermata.ui.activity.MainActivityListener.Event.VIDEO_SURFACE_TOUCH;

/**
 * @author Andrey Pavlenko
 */
public class VideoView extends FrameLayout {

	public VideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setBackgroundColor(Color.BLACK);
		addView(new SurfaceView(getContext()) {
			{
				super.setVisibility(GONE);
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
				setLayoutParams(lp);
			}

			@Override
			public void setVisibility(int visibility) {
				if (visibility == getVisibility()) return;

				super.setVisibility(visibility);
				VideoView.this.setVisibility(visibility);
				MainActivityDelegate a = getActivity();
				Window w = a.getWindow();

				if (visibility == VISIBLE) {
					a.setVideoMode(true);
				} else {
					a.setVideoMode(false);
					w.clearFlags(FLAG_KEEP_SCREEN_ON);
				}
			}
		});

		TextView text = new TextView(context);
		text.setPadding(Utils.toPx(10), Utils.toPx(10), Utils.toPx(10), 0);
		text.setTextSize(20);
		text.setTextColor(Color.WHITE);
		addView(text);
	}

	public SurfaceView getSurface() {
		return (SurfaceView) getChildAt(0);
	}

	public TextView getTitle() {
		return (TextView) getChildAt(1);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent e) {
		return getActivity().interceptTouchEvent(e, this::touchHandler);
	}

	private boolean touchHandler(@NonNull MotionEvent e) {
		if (e.getAction() == MotionEvent.ACTION_DOWN) {
			getActivity().fireBroadcastEvent(VIDEO_SURFACE_TOUCH);
		}
		return true;
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
