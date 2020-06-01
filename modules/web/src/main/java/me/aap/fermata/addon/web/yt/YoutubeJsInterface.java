package me.aap.fermata.addon.web.yt;

import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeJsInterface extends FermataJsInterface {
	public static final int JS_VIDEO_FOUND = JS_LAST + 1;
	public static final int JS_VIDEO_PLAYING = JS_LAST + 2;
	public static final int JS_VIDEO_PAUSED = JS_LAST + 3;
	public static final int JS_VIDEO_ENDED = JS_LAST + 4;
	private final YoutubeMediaEngine engine;

	public YoutubeJsInterface(FermataWebView webView, YoutubeMediaEngine engine) {
		super(webView);
		this.engine = engine;
	}

	protected void handleEvent(int event, String data) {
		switch (event) {
			case JS_VIDEO_FOUND:
				Log.d("Video found");
				break;
			case JS_VIDEO_PLAYING:
				Log.d("Video playing");
				engine.playing(data);
				break;
			case JS_VIDEO_PAUSED:
				Log.d("Video paused");
				engine.paused();
				break;
			case JS_VIDEO_ENDED:
				Log.d("Video ended");
				engine.ended();
				break;
			default:
				super.handleEvent(event, data);
		}
	}
}
