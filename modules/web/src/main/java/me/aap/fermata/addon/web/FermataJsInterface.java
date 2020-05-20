package me.aap.fermata.addon.web;

import android.webkit.JavascriptInterface;

import androidx.annotation.Keep;

import me.aap.utils.app.App;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataJsInterface {
	public static final String NAME = "Fermata";
	public static final String JS_EVENT = "window.Fermata.event";
	public static final int JS_EDIT = 0;
	public static final int JS_ERR = 1;
	protected static final int JS_LAST = 1;
	private final FermataWebView webView;

	public FermataJsInterface(FermataWebView webView) {
		this.webView = webView;
	}

	public FermataWebView getWebView() {
		return webView;
	}

	@Keep
	@SuppressWarnings("unused")
	@JavascriptInterface
	public void event(int event, String data) {
		App.get().run(() -> handleEvent(event, data));
	}

	protected void handleEvent(int event, String data) {
		switch (event) {
			case JS_EDIT:
				Log.d("Edit text event: ", data);
				getWebView().showKeyboard(data);
				break;
			case JS_ERR:
				Log.e("JavaScript error: ", data);
				break;
		}
	}
}
