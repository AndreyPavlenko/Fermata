package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private YoutubeJsInterface js;

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
		return js = new YoutubeJsInterface(this, new YoutubeMediaEngine(this, a));
	}

	@Override
	public YoutubeAddon getAddon() {
		return (YoutubeAddon) super.getAddon();
	}

	@Override
	public void loadUrl(@NonNull String url) {
		Log.d("Loading URL: " + url);
		super.loadUrl(url);
	}

	@Override
	public void goBack() {
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if (cb.getEngine() instanceof YoutubeMediaEngine) cb.onStop();
		super.goBack();
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
		String debug = BuildConfig.D ? JS_EVENT + "(" + JS_VIDEO_FOUND + ", null);\n" : "";
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
		prevNext(0);
	}

	void next() {
		prevNext(1);
	}

	private void prevNext(int plIdx) {
		FermataChromeClient chrome = getWebChromeClient();
		if (chrome == null) return;

		chrome.exitFullScreen().thenRun(() -> loadUrl("""
			javascript:
			function changeSong() {
				""" + (plIdx == 0 ? """
				var prevSong = document.querySelector('.ytm-playlist-panel-video-renderer-v2--selected').previousSibling;
				if (prevSong !== null) prevSong.getElementsByTagName('a')[0].click();
				""" : """
				var nextSong = document.querySelector('.ytm-playlist-panel-video-renderer-v2--selected').nextSibling;
				if (nextSong !== null) nextSong.getElementsByTagName('a')[0].click();
				""") + """
			}
			
			if (document.getElementsByTagName('ytm-playlist-engagement-panel').length > 0) {
				if (document.body.getAttribute('engagement-panel-open') === null) {
					setTimeout(() => {document.querySelector('[aria-label="Show playlist videos"]').click();}, 600);
					setTimeout(() => { changeSong(); }, 600);
				}
				
				changeSong();
			} else {
				var playerControls = document.getElementsByClassName('player-controls-middle-core-buttons');
				if (playerControls.length > 0) {
				""" + (plIdx == 0 ? """
					playerControls[0].querySelector('[aria-label="Previous video"]').click();
				""" : """
					playerControls[0].querySelector('[aria-label="Next video"]').click();
				""") + """
				}
			}
		"""));
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	FutureSupplier<String> getVideoQualities() {
		Promise<String> p = js.getResultPromise();
		loadUrl("javascript:\n" +
				"function retryGetVideoQualities(attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(getVideoQualities, 100, attempt + 1, openMenu);\n" +
				"  else " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", null);\n" +
				"  return null;\n" +
				"}\n" +
				"function getVideoQualities(attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retryGetVideoQualities(attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var result = '';\n" +
				"  for (let i = 0; i < options.length; i++) {\n" +
				"    if (i != 0) result += ';';\n" +
				"    if (i == select.selectedIndex) result += '*';\n" +
				"    result += options[i].innerText;\n" +
				"  }\n" +
				"  " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", result);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('.c3-material-button-button').click();}, 100);\n" +
				"  return result;\n" +
				"}\n" +
				"getVideoQualities(0, true);");
		return p;
	}
	void setVideoQuality(int idx) {
		loadUrl("javascript:\n" +
				"function retrySetVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(setVideoQuality, 100, idx, attempt + 1, openMenu);\n" +
				"  return false;\n" +
				"}\n" +
				"function setVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retrySetVideoQuality(idx, attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var evt = document.createEvent(\"HTMLEvents\");\n" +
				"  evt.initEvent(\"change\", true, true);\n" +
				"  select.selectedIndex = idx;\n" +
				"  options[idx].selected = true;\n" +
				"  select.dispatchEvent(evt);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('.c3-material-button-button').click();}, 100);\n" +
				"  return true;\n" +
				"}\n" +
				"setVideoQuality(" + idx + ", 0, true);");
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
