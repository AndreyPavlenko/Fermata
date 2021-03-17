package me.aap.fermata.addon.web.yt;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.CookieManager;

import me.aap.fermata.addon.web.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {

	public YoutubeWebView(Context context) {
		super(context);
	}

	public YoutubeWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public YoutubeWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected FermataJsInterface createJsInterface() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		return new YoutubeJsInterface(this, new YoutubeMediaEngine(this, a));
	}

	@Override
	public YoutubeAddon getAddon() {
		return (YoutubeAddon) super.getAddon();
	}

	@Override
	protected void pageLoaded(String uri) {
		attachListeners();
		CookieManager.getInstance().flush();
	}

	protected void submitForm() {
		if (!me.aap.fermata.BuildConfig.AUTO) return;
		loadUrl("javascript:\n" +
				"var e = new KeyboardEvent('keydown',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);\n" +
				"e = new KeyboardEvent('keyup',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);");
	}

	void attachListeners() {
		String debug = BuildConfig.DEBUG ? JS_EVENT + "(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		loadUrl("javascript:\n" +
				"function attachVideoListeners(v) {\n" +
				"  if (v.getAttribute('FermataAttached') === 'true') return;\n" +
				"  v.setAttribute('FermataAttached', 'true');\n" +
				"  v.setAttribute('style', 'object-fit:" + scale + "');\n" + debug +
				"  if ((v.currentTime > 0) && !v.paused && !v.ended) " + JS_EVENT + "(" + JS_VIDEO_PLAYING + ", v.currentSrc);\n" +
				"  v.addEventListener('playing', function(e) {" + JS_EVENT + "(" + JS_VIDEO_PLAYING + ", v.currentSrc);});\n" +
				"  v.addEventListener('pause', function(e) {" + JS_EVENT + "(" + JS_VIDEO_PAUSED + ", v.currentSrc);});\n" +
				"  v.addEventListener('ended', function(e) {" + JS_EVENT + "(" + JS_VIDEO_ENDED + ", null);});\n" +
				"}\n" +
				"function findVideo() {\n" +
				"  var video = document.querySelectorAll('video');" +
				"  video.forEach(attachVideoListeners);\n" +
				"   setTimeout(findVideo, 1000);\n" +
				"}\n" +
				"findVideo();");
	}

	protected boolean requestFullScreen() {
		loadUrl("javascript: var v = document.querySelector('video');\n" +
				"if ('webkitRequestFullscreen' in v) v.webkitRequestFullscreen();\n" +
				"else if ('requestFullscreen' in v) v.requestFullscreen();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Method requestFullscreen not found in ' + v);");
		return true;
	}

	void play() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.play();");
	}

	void pause() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.pause();");
	}

	void stop() {
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				"if (v != null) { v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		FermataChromeClient chrome = getWebChromeClient();
		if (chrome == null) return;

		chrome.exitFullScreen().thenRun(() -> loadUrl("javascript:\n" +
				"var c = document.getElementsByClassName('player-controls-middle center');\n" +
				"if (c.length != 0) c = c[0].querySelectorAll('button');\n" +
				"if (c.length != 0) c[0].click();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Button not found: player-controls-middle center');"));
	}

	void next() {
		FermataChromeClient chrome = getWebChromeClient();
		if (chrome == null) return;

		chrome.exitFullScreen().thenRun(() -> loadUrl("javascript:\n" +
				"var c = document.getElementsByClassName('player-controls-middle center');\n" +
				"if (c.length != 0) c = c[0].querySelectorAll('button');\n" +
				"if (c.length >= 5) c[4].click();  \n" +
				"else {\n" +
				"  c = document.getElementsByClassName('ytp-upnext-autoplay-icon');\n" +
				"  if (c.length != 0) c[0].click();\n" +
				"  else " + JS_EVENT + "(" + JS_ERR + ", 'Button not found: player-controls-middle center');\n" +
				"}"));
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v." + value + " : 0})();",
				v -> {
					try {
						p.complete((long) (Double.parseDouble(v) * 1000));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(0L);
					}
				});
		return p;
	}

	void setPosition(long position) {
		double pos = position / 1000f;
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.currentTime = " + pos + ";");
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v.playbackRate : 0})();",
				v -> {
					try {
						p.complete(Float.parseFloat(v));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(1f);
					}
				});
		return p;
	}

	void setSpeed(float speed) {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.playbackRate = " + speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		evaluateJavascript("document.title", p::complete);
		return p;
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		loadUrl("javascript:" +
				"document.querySelectorAll('video')" +
				".forEach(v=> v.setAttribute('style', 'object-fit:" + p + "'));");
	}
}
