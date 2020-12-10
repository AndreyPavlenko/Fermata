package me.aap.fermata.addon.web;

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewFeature;

import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.Completed;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.log.Log;

import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebClient extends WebViewClientCompat {
	BooleanConsumer loading;

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		if (loading != null) {
			loading.accept(true);
		} else {
			MainActivityDelegate.get(view.getContext()).setContentLoading(new Promise<>());
		}
		super.onPageStarted(view, url, favicon);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		FermataWebView v = (FermataWebView) view;
		MainActivityDelegate a = MainActivityDelegate.get(view.getContext());
		a.setContentLoading(Completed.completedVoid());

		if (loading != null) {
			loading.accept(false);
			loading = null;
		}

		super.onPageFinished(view, url);
		((FermataWebView) view).hideKeyboard();
		v.pageLoaded(url);
		a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	@Override
	public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
		if (isYoutubeUri(request.getUrl())) {
			try {
				MainActivityDelegate a = MainActivityDelegate.get(view.getContext());
				YoutubeFragment f = a.showFragment(me.aap.fermata.R.id.youtube_fragment);
				f.loadUrl(request.getUrl().toString());
				return true;
			} catch (IllegalArgumentException ex) {
				Log.d(ex);
			}
		}

		return false;
	}

	public static boolean isYoutubeUri(Uri uri) {
		String host = uri.getHost();
		return ((host != null) && (host.endsWith("youtube.com") || host.equals("youtu.be")));
	}

	@Override
	public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceErrorCompat error) {
		if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) {
			Log.e("Web error received: " + error.getDescription());
		} else {
			Log.e("Web error received");
		}

		super.onReceivedError(view, request, error);
	}
}
