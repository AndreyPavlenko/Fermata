package me.aap.fermata.addon.web.yt;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import me.aap.fermata.ui.view.VideoView;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

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
		addTitle(context);
	}
}
