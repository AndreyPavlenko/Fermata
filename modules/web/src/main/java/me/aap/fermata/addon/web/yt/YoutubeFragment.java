package me.aap.fermata.addon.web.yt;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeFragment extends WebBrowserFragment implements
		FermataServiceUiBinder.Listener {
	private static final String DEFAULT_URL = "http://m.youtube.com";
	private String loadUrl = DEFAULT_URL;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.youtube, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		YoutubeAddon addon = AddonManager.get().getAddon(YoutubeAddon.class);
		if (addon == null) return;

		Context ctx = view.getContext();
		MainActivityDelegate a = MainActivityDelegate.get(ctx);
		YoutubeWebView webView = a.findViewById(R.id.ytWebView);
		VideoView videoView = a.findViewById(R.id.ytVideoView);
		YoutubeWebClient webClient = new YoutubeWebClient();
		YoutubeChromeClient chromeClient = new YoutubeChromeClient(webView, videoView);
		a.getMediaServiceBinder().addBroadcastListener(this);
		webView.init(addon, webClient, chromeClient);

		String url = loadUrl;
		loadUrl = DEFAULT_URL;
		webView.loadUrl(url);
	}

	@Override
	public void onPause() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());

		if ((a != null) && !a.isCarActivity()) {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			if ((b != null) && (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem()))) {
				b.getMediaSessionCallback().onStop();
			}
		}

		super.onPause();
	}

	public void loadUrl(String url) {
		FermataWebView v = getWebView();
		if (v != null) v.loadUrl(url);
		else loadUrl = url;
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		if (isHidden()) return;

		if (YoutubeMediaEngine.isYoutubeItem(newItem)) {
			FermataWebView v = getWebView();
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if ((v == null) || (a == null)) return;

			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return;

			chrome.enterFullScreen();
			newItem.getMediaDescription().onSuccess(md -> {
				VideoView videoView = a.findViewById(R.id.ytVideoView);
				videoView.getTitle().setText(md.getTitle());
			});
		} else if (YoutubeMediaEngine.isYoutubeItem(oldItem)) {
			FermataWebView v = getWebView();
			if (v == null) return;
			FermataChromeClient chrome = v.getWebChromeClient();
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if ((a != null) && (chrome != null)) chrome.exitFullScreen();
		}
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarView.Mediator.Invisible.instance;
	}

	@Override
	public boolean canScrollUp() {
		FermataWebView v = getWebView();
		if (v == null) return false;
		return v.getWebChromeClient().isFullScreen() || (v.getScrollY() > 0);
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(YoutubeAddon.class);
	}

	@Nullable
	protected YoutubeWebView getWebView() {

		View v = getView();
		return (v != null) ? v.findViewById(R.id.ytWebView) : null;
	}

	protected boolean isDesktopVersionSupported() {
		return false;
	}
}
