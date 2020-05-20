package me.aap.fermata.addon.web.yt;

import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import me.aap.fermata.addon.web.FermataWebClient;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebClient extends FermataWebClient {

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
		if (!isYoutubeReq(request)) {
			MainActivityDelegate a = MainActivityDelegate.get(view.getContext());

			try {
				WebBrowserFragment f = a.showFragment(me.aap.fermata.R.id.web_browser_fragment);
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}
}
