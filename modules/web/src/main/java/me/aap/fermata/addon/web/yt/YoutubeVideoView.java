package me.aap.fermata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoInfoView;
import me.aap.fermata.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeVideoView extends VideoView {

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		addView(new FrameLayout(context), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
		addBrightnessOverlay(context);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(1);
	}

	@Nullable
	@Override
	public SurfaceView getSubtitleSurface() {
		return null;
	}

	@Override
	public void setSoftwareBrightness(int brightness) {
		super.setSoftwareBrightness(brightness);
		View overlay = MainActivityDelegate.get(getContext()).findViewById(R.id.ytBrightnessOverlay);
		if (overlay == null) return;
		int value = Math.max(0, Math.min(255, brightness));
		float alpha = (255 - value) / 255f;
		overlay.setAlpha(alpha);
		overlay.setVisibility(alpha == 0f ? View.GONE : View.VISIBLE);
	}
}
