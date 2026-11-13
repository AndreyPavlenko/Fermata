package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_FULL_SCREEN;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PROGRESS;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;

import android.content.Context;
import android.os.SystemClock;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.AttributeSet;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONTokener;

import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private static final String CLEAR_HIGHEST_VIDEO_QUALITY_JS =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";
	private YoutubeJsInterface js;
	// Reported by the page: the bare video, which has no YouTube controls, is what's in full screen
	boolean videoFullScreen;
	// The video this page is reopening, set as it was once it starts playing
	@Nullable
	private Video reopening;

	// left: when it stopped playing, as SystemClock.elapsedRealtime(), or now if it's playing
	record Video(String url, long position, boolean playing, long left) {
		Video(String url, long position, boolean playing) {
			this(url, position, playing, SystemClock.elapsedRealtime());
		}
	}

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
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		super.onPreferenceChanged(store, prefs);

		if (getAddon().autoHighestQualityChanged(prefs)) {
			if (getAddon().autoHighestQuality()) setHighestVideoQuality();
			else clearHighestVideoQuality();
		}

		if (YoutubeSponsorBlock.isPreferenceChanged(prefs)) injectSponsorBlock();
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
	public void onActivityEvent(MainActivityDelegate a, long e) {
		// The page is gone with its activity (e.g. Fermata Auto exited from Android Auto), and nothing
		// can continue its playback. Switching to another section only detaches it until it's shown again.
		if ((e == ACTIVITY_DESTROY) && (js != null)) js.pageRemoved();
		super.onActivityEvent(a, e);
	}

	/**
	 * A car screen has no saved state to restore its page from, as a phone screen does, and a car that's
	 * switched off doesn't always close it. When a new one opens while the previous one's page is still
	 * there with a video, take it over, as "Resume last video" would, within its time limit. The previous
	 * page is stopped either way.
	 */
	@Nullable
	static Video takeOverVideo(MediaSessionCallback cb, YoutubeWebView page) {
		if (!(cb.getEngine() instanceof YoutubeMediaEngine eng) || (eng.getWebView() == page)) return null;
		Video v = eng.getWebView().getVideo(cb);
		if (v == null) return null;
		cb.onStop();
		return page.getAddon().isRecentInCar(v.left()) ? v : null;
	}

	// The video playing or paused on this page
	@Nullable
	private Video getVideo(MediaSessionCallback cb) {
		if (!(cb.getEngine() instanceof YoutubeMediaEngine eng) || (eng.getWebView() != this)) return null;
		String url = getUrl();
		if (!YoutubeUrls.isVideoUrl(url)) return null;
		PlaybackStateCompat st = cb.getPlaybackState();
		int state = st.getState();
		boolean playing = (state == PlaybackStateCompat.STATE_PLAYING);
		if (!playing && (state != PlaybackStateCompat.STATE_PAUSED)) return null;
		long now = SystemClock.elapsedRealtime();
		long pos = st.getPosition();
		if (!playing) return new Video(url, pos, false, st.getLastPositionUpdateTime());
		pos += (long) ((now - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
		return new Video(url, pos, true, now);
	}

	/**
	 * Open a video at its position, playing or paused. It's set as it was when it starts playing rather
	 * than after a delay, as the page may take a while to load, e.g. in a car that's just been started.
	 */
	void reopen(Video v) {
		String url = v.url();
		if (YoutubeUrls.isVideoUrl(url)) {
			reopening = v;
			url = YoutubeUrls.withStartTime(url, v.position());
		}
		loadUrl(url);
	}

	// Called when this page's video starts playing
	void videoPlaying() {
		Video v = reopening;
		if (v == null) return;
		reopening = null;
		// Pausing from the page, its pause event then updates the session as any other pause does.
		// YouTube may also start a video that the page opens by itself muted: unmute it, or it would
		// play muted, without taking the audio focus, as soon as it's resumed.
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				(v.playing() ? "" : "if (v != null) v.pause();\n") +
				"var p = document.getElementById('movie_player');\n" +
				"try { if ((p != null) && p.unMute) p.unMute(); } catch (e) {}\n" +
				"if (v != null) v.muted = false;");
	}

	@Override
	protected void urlChanged(String url) {
		if (YoutubeUrls.isVideoUrl(url)) {
			// Another video opened before the reopened one started
			if ((reopening != null) &&
					!YoutubeUrls.videoId(url).equals(YoutubeUrls.videoId(reopening.url()))) reopening = null;
			return;
		}
		// Leaving the video within the page (e.g. YouTube's own back button) leaves it like goBack(),
		// even though YouTube keeps playing it as a muted preview
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if ((cb.getEngine() instanceof YoutubeMediaEngine eng) && (eng.getWebView() == this)) cb.onStop();
	}

	@Override
	protected void pageLoaded(String uri) {
		attachListeners();
		injectSponsorBlock();
		addFocusHighlight();
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

	private void attachListeners() {
		String debug = BuildConfig.D ? JS_EVENT + "(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		loadUrl("javascript:\n" +
				// Muted autoplay previews in the feeds reuse the player, they are not user playback.
				// The video pages are the same as in isVideoUrl(): keep the two in sync.
				"function isPreview(v) {\n" +
				"  return v.muted && !/^\\/(watch|shorts|live|embed)/.test(location.pathname);\n" +
				"}\n" +
				"function videoEvent(v, e) {\n" +
				"  if (isPreview(v)) {\n" +
				"    if (!v.fermataPlaying) return;\n" +
				"    e = " + JS_VIDEO_PAUSED + ";\n" +
				"  }\n" +
				"  v.fermataPlaying = (e === " + JS_VIDEO_PLAYING + ");\n" +
				"  " + JS_EVENT + "(e, (e === " + JS_VIDEO_ENDED + ") ? fermataVideoUrl() : v.currentSrc);\n" +
				"}\n" +
				"function attachVideoListeners(v) {\n" +
				"  if (v.getAttribute('FermataAttached') === 'true') return;\n" +
				"  v.setAttribute('FermataAttached', 'true');\n" +
				"  v.style.objectFit = '" + scale + "';\n" + debug +
				"  if ((v.currentTime > 0) && !v.paused && !v.ended) videoEvent(v, " + JS_VIDEO_PLAYING + ");\n" +
				"  v.addEventListener('playing', function(e) {\n" +
				"    v.fermataStopped = false;\n" +
				"    videoEvent(v, " + JS_VIDEO_PLAYING + ");\n" +
				"  });\n" +
				// Stopping resets the position: not one to resume from
				"  v.addEventListener('pause', function(e) {\n" +
				"    if (!v.muted && !isPreview(v) && !v.fermataStopped) " + JS_EVENT + "(" + JS_VIDEO_PROGRESS +
				", Math.floor(v.currentTime) + '|' + fermataVideoUrl());\n" +
				"    videoEvent(v, " + JS_VIDEO_PAUSED + ");\n" +
				"  });\n" +
				"  v.addEventListener('timeupdate', function(e) {\n" +
				"    if (v.muted || isPreview(v) || v.fermataStopped) return;\n" +
				"    var t = Math.floor(v.currentTime);\n" +
				"    if ((t % 5 != 0) || (t === v.fermataProgress)) return;\n" +
				"    v.fermataProgress = t;\n" +
				"    " + JS_EVENT + "(" + JS_VIDEO_PROGRESS + ", t + '|' + fermataVideoUrl());\n" +
				"  });\n" +
				"  v.addEventListener('ended', function(e) { videoEvent(v, " + JS_VIDEO_ENDED + "); });\n" +
				// A muted preview unmuted in place becomes the user's playback
				"  v.addEventListener('volumechange', function(e) {\n" +
				"    if (!v.paused && !v.fermataPlaying && !isPreview(v)) videoEvent(v, " + JS_VIDEO_PLAYING + ");\n" +
				"  });\n" +
				"}\n" +
				// Search results and the home feed play videos inline, so the page URL is not
				// always the video URL. The player element knows the video id.
				"function fermataVideoUrl() {\n" +
				"  var h = location.href;\n" +
				"  if ((h.indexOf('/watch?') >= 0) || (h.indexOf('/shorts/') >= 0)) return h;\n" +
				"  var p = document.getElementById('movie_player') ||" +
				" document.querySelector('.html5-video-player');\n" +
				"  try {\n" +
				"    var d = p && p.getVideoData && p.getVideoData();\n" +
				"    if (d && d.video_id) return location.origin + '/watch?v=' + d.video_id;\n" +
				"  } catch (e) {}\n" +
				"  try {\n" +
				"    var u = p && p.getVideoUrl && p.getVideoUrl();\n" +
				"    if (u) return u;\n" +
				"  } catch (e) {}\n" +
				"  return '';\n" +
				"}\n" +
				"function findVideo() {\n" +
				"  var video = document.querySelectorAll('video');" +
				"  video.forEach(attachVideoListeners);\n" +
				"   setTimeout(findVideo, 1000);\n" +
				"}\n" +
				"if (!window.__fermataFullScreen) {\n" +
				"  window.__fermataFullScreen = true;\n" +
				"  document.addEventListener('fullscreenchange', function() {\n" +
				"    var e = document.fullscreenElement;\n" +
				"    " + JS_EVENT + "(" + JS_FULL_SCREEN + ", e ? e.tagName : null);\n" +
				"  });\n" +
				"}\n" +
				"findVideo();");
	}

	private void injectSponsorBlock() {
		String script = YoutubeSponsorBlock.getScript(getContext(), getAddon().getPreferenceStore());
		if (!script.isEmpty()) evaluateJavascript(script, result -> configureSponsorBlock());
		else configureSponsorBlock();
	}

	private void configureSponsorBlock() {
		evaluateJavascript("if (window.FermataSponsorBlock) window.FermataSponsorBlock.configure(" +
				YoutubeSponsorBlock.getConfigJson(getAddon().getPreferenceStore()) + ");", null);
	}

	protected boolean requestFullScreen() {
		// With YouTube's controls, its own button lets YouTube switch to its full screen layout. Without the
		// button (e.g. Shorts) the player goes full screen, and without the player the bare video, as without
		// the option. A refused request falls back to the next one. Only the click can't tell, so whether
		// full screen started is checked after a while.
		loadUrl("javascript:(function() {\n" +
				"  var v = document.querySelector('video');\n" +
				"  if (!v) return;\n" +
				"  var p = " + getAddon().youtubePlayerControls() + " && v.closest('#player-container-id');\n" +
				"  var b = p && p.querySelector('button.fullscreen-icon');\n" +
				"  function fs(e) {\n" +
				"    if ('requestFullscreen' in e) return e.requestFullscreen();\n" +
				"    if ('webkitRequestFullscreen' in e) return Promise.resolve(e.webkitRequestFullscreen());\n" +
				"    return Promise.reject('Method requestFullscreen not found in ' + e);\n" +
				"  }\n" +
				"  function video() {\n" +
				"    fs(v).catch(function(err) { " + JS_EVENT + "(" + JS_ERR + ", '' + err); });\n" +
				"  }\n" +
				"  function player() { fs(p).catch(video); }\n" +
				"  if (!p) video();\n" +
				"  else if (!b) player();\n" +
				"  else {\n" +
				"    b.click();\n" +
				"    setTimeout(function() { if (!document.fullscreenElement) player(); }, 1500);\n" +
				"  }\n" +
				"})();");
		return true;
	}

	void play() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.play();");
	}

	void pause() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.pause();");
	}

	void stop() {
		// Not a position to save for "Resume last video": the listeners skip the progress of this reset
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				"if (v != null) { v.fermataStopped = true; v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		prevNext(false);
	}

	void next() {
		prevNext(true);
	}

	private void prevNext(boolean next) {
		evaluateJavascript("""
				(function() {
				  const p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				  const fn = '%s';
				  if (!p || (typeof p[fn] !== 'function')) return false;
				  p[fn]();
				  return true;
				})();
				""".formatted(next ? "nextVideo" : "previousVideo"), r -> {
			if ("true".equals(r)) return;
			// Player API not available, fall back to the page's prev/next buttons
			FermataChromeClient chrome = getWebChromeClient();
			if (chrome == null) return;
			chrome.exitFullScreen().thenRun(() -> evaluateJavascript("""
					setTimeout(() => {
					  const buttons = document.querySelectorAll('button.player-middle-controls-prev-next-button');
					  if (buttons.length > %1$d) buttons[%1$d].click();
					}, 600);
					""".formatted(next ? 1 : 0), null));
		});
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
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
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
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return true;\n" +
				"}\n" +
				"setVideoQuality(" + idx + ", 0, true);");
	}

	void setHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"  var state = window.__fermataQ = { player: null, handler: null, timeout: null, attempts: 0 };\n" +
				"  function getPlayer() {\n" +
				"    return document.querySelector('#movie_player') || document.querySelector('.html5-video-player');\n" +
				"  }\n" +
				"  function applyHighest(p) {\n" +
				"    if (!p || typeof p.getAvailableQualityLevels !== 'function') return false;\n" +
				"    var levels = p.getAvailableQualityLevels();\n" +
				"    if (!levels || levels.length === 0) return false;\n" +
				"    var best = null;\n" +
				"    for (var i = 0; i < levels.length; i++) {\n" +
				"      if (levels[i] !== 'auto') { best = levels[i]; break; }\n" +
				"    }\n" +
				"    if (!best) return false;\n" +
				"    if (p.getPlaybackQuality && p.getPlaybackQuality() === best) return true;\n" +
				"    if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(best, best);\n" +
				"    else if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(best);\n" +
				"    else return false;\n" +
				"    return true;\n" +
				"  }\n" +
				"  function install() {\n" +
				"    var p = getPlayer();\n" +
				"    if (!p || typeof p.addEventListener !== 'function') {\n" +
				"      if (++state.attempts < 50) state.timeout = setTimeout(install, 200);\n" +
				"      return;\n" +
				"    }\n" +
				"    state.player = p;\n" +
				"    state.handler = function(s) {\n" +
				"      if ((s === 1) || (s === 3)) applyHighest(getPlayer() || p);\n" +
				"    };\n" +
				"    p.addEventListener('onStateChange', state.handler);\n" +
				"    applyHighest(p);\n" +
				"  }\n" +
				"  install();\n" +
				"})();");
	}

	void clearHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"})();");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v." + value +
						" : 0})();",
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
		double pos = position / 1000d;
		// Seek through YouTube's player API when available: setting currentTime on the media
		// element directly may be ignored or reverted by the player.
		evaluateJavascript("""
				(function() {
				  const s = %s;
				  const p = document.querySelector('#movie_player') || document.querySelector('.html5-video-player');
				  if (p && (typeof p.seekTo === 'function')) { p.seekTo(s, true); return; }
				  const v = document.querySelector('video');
				  if (v != null) v.currentTime = s;
				})();
				""".formatted(Double.toString(pos)), null);
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v" +
						".playbackRate" +
						" " +
						": 0})();",
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
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.playbackRate =" +
				" " +
				speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		// The page title is updated only some time after a video is opened, the player knows it already
		evaluateJavascript("""
				(function() {
				  var v = document.querySelector('video');
				  var p = v && v.closest('#movie_player');
				  var d = p && p.getVideoData && p.getVideoData();
				  return (d && d.title) || document.title;
				})()""", json -> {
			// evaluateJavascript() returns its result as JSON, so a string comes quoted and escaped
			try {
				Object v = new JSONTokener(json).nextValue();
				p.complete((v instanceof String) ? (String) v : null);
			} catch (JSONException ex) {
				p.complete(json);
			}
		});
		return p;
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		loadUrl("javascript:" +
				"document.querySelectorAll('video')" +
				".forEach(v=> v.style.objectFit = '" + p + "');");
	}
}
