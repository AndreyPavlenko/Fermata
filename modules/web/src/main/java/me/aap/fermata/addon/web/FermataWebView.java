package me.aap.fermata.addon.web;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.TextChangedListener;
import me.aap.utils.ui.view.ToolBarView;

import static android.os.Build.VERSION;
import static android.os.Build.VERSION_CODES;
import static androidx.webkit.WebViewFeature.FORCE_DARK;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.web.FermataJsInterface.JS_EDIT;
import static me.aap.fermata.addon.web.FermataJsInterface.JS_EVENT;

/**
 * @author Andrey Pavlenko
 */
public class FermataWebView extends WebView implements TextChangedListener,
		TextView.OnEditorActionListener, PreferenceStore.Listener {
	private final boolean isCar;
	private WebBrowserAddon addon;
	private FermataChromeClient chrome;

	public FermataWebView(Context context) {
		this(context, null);
	}

	public FermataWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(getContext()).isCarActivity();
	}

	public FermataWebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		isCar = BuildConfig.AUTO && MainActivityDelegate.get(getContext()).isCarActivity();
	}

	@SuppressLint("SetJavaScriptEnabled")
	public void init(WebBrowserAddon addon, FermataWebClient webClient, FermataChromeClient chromeClient) {
		this.addon = addon;
		setWebViewClient(webClient);
		setWebChromeClient(chromeClient);
		WebSettings s = getSettings();
		s.setSupportZoom(true);
		s.setBuiltInZoomControls(true);
		s.setDisplayZoomControls(false);
		s.setDatabaseEnabled(true);
		s.setDomStorageEnabled(true);
		s.setAllowFileAccess(true);
		s.setLoadWithOverviewMode(true);
		s.setJavaScriptEnabled(true);
		s.setJavaScriptCanOpenWindowsAutomatically(true);

		addJavascriptInterface(createJsInterface(), FermataJsInterface.NAME);
		CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);

		addon.getPreferenceStore().addBroadcastListener(this);
		setDesktopMode(addon, false);
		setForceDark(addon, false);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		WebBrowserAddon a = getAddon();
		if (a == null) return;

		if (prefs.contains(a.getDesktopVersionPref())) {
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentPref())) {
			UserAgent.ua = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getUserAgentDesktopPref())) {
			UserAgent.uaDesktop = null;
			setDesktopMode(a, true);
		} else if (prefs.contains(a.getForceDarkPref())) {
			setForceDark(addon, true);
		}
	}

	private void setDesktopMode(WebBrowserAddon a, boolean reload) {
		if (getClass() != FermataWebView.class) return;

		WebSettings s = getSettings();
		boolean v = a.getPreferenceStore().getBooleanPref(a.getDesktopVersionPref());
		String ua = v ? UserAgent.getUaDesktop(s, a) : UserAgent.getUa(s, a);
		Log.d("Setting User-Agent to " + ua);
		s.setUserAgentString(ua);
		s.setUseWideViewPort(v);
		if (reload) reload();
	}

	private void setForceDark(WebBrowserAddon a, boolean reload) {
		if (WebViewFeature.isFeatureSupported(FORCE_DARK)) {
			int v = a.isForceDark() ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_AUTO;
			WebSettingsCompat.setForceDark(getSettings(), v);
			if (reload) reload();
		}
	}

	protected FermataJsInterface createJsInterface() {
		return new FermataJsInterface(this);
	}

	protected boolean isCar() {
		return BuildConfig.AUTO && isCar;
	}

	public WebBrowserAddon getAddon() {
		return addon;
	}

	@NonNull
	@Override
	public FermataWebClient getWebViewClient() {
		return (FermataWebClient) super.getWebViewClient();
	}

	public void setWebChromeClient(FermataChromeClient chrome) {
		this.chrome = chrome;
		super.setWebChromeClient(chrome);
	}

	@Nullable
	@Override
	public FermataChromeClient getWebChromeClient() {
		return chrome;
	}

	protected void pageLoaded(String uri) {
		getAddon().setLastUrl(uri);
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		ActivityFragment f = a.getActiveFragment();
		if (f == null) return;

		ToolBarView.Mediator m = f.getToolBarMediator();

		if (m instanceof WebToolBarMediator) {
			WebToolBarMediator wm = (WebToolBarMediator) m;
			ToolBarView tb = a.getToolBar();
			wm.setAddress(tb, uri);
			wm.setButtonsVisibility(tb, canGoBack(), canGoForward());
		}

		CookieManager.getInstance().flush();
	}

	protected boolean requestFullScreen() {
		return false;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isCar()) checkTextInput();
		return super.onTouchEvent(event);
	}

	private void checkTextInput() {
		if (!BuildConfig.AUTO || isKeyboardActive()) return;

		Log.d("checkTextInput");
		loadUrl("javascript:\n" +
				"function checkInput() {\n" +
				"  var e =  document.activeElement;\n" +
				"  if (e == null) return;\n" +
				"  if (e instanceof HTMLInputElement) {\n" +
				"    " + JS_EVENT + '(' + JS_EDIT + ", e.value);\n" +
				"  } else if(e.getAttribute('contenteditable') == 'true') {\n" +
				"    " + JS_EVENT + '(' + JS_EDIT + ", e.innerText);\n" +
				"  }\n" +
				"}\n" +
				"setTimeout(checkInput, 500);");
	}

	private void setTextInput(CharSequence text) {
		if (!BuildConfig.AUTO) return;

		Log.d(text);
		loadUrl("javascript:\n" +
				"var e =  document.activeElement;\n" +
				"var text = '" + text + "';\n" +
				"if (e.isContentEditable) e.innerText = text;\n" +
				"else e.value = text;\n" +
				"e.dispatchEvent(new KeyboardEvent('keydown', { bubbles: true }));\n" +
				"e.dispatchEvent(new KeyboardEvent('keypress', { bubbles: true }));\n" +
				"e.dispatchEvent(new InputEvent('input', { bubbles: true, data: text, inputType: 'insertText' }));\n" +
				"e.dispatchEvent(new KeyboardEvent('keyup', { bubbles: true }));\n" +
				"e.dispatchEvent(new Event('change', { bubbles: true }));"
		);
	}


	protected void submitForm() {
		if (!BuildConfig.AUTO) return;
		loadUrl("javascript:\n" +
				"var ae = document.activeElement;\n" +
				"if (ae.form != null) {\n" +
				"  ae.form.submit();\n" +
				"} else {\n" +
				"  var e = new KeyboardEvent('keydown',\n" +
				"  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"  ae.dispatchEvent(e);\n" +
				"  e = new KeyboardEvent('keyup',\n" +
				"  { code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"  ae.dispatchEvent(e);\n" +
				"}"
		);
	}

	public void showKeyboard(String text) {
		if (!BuildConfig.AUTO) return;

		Context ctx = getContext();
		FermataActivity a = MainActivityDelegate.get(ctx).getAppActivity();
		EditText et = a.startInput(this);
		if (et == null) return;

		if (text != null) {
			et.setText(text);
			et.setSelection(et.getText().length());
		}

		et.setOnEditorActionListener(this);
	}

	public void hideKeyboard() {
		if (!BuildConfig.AUTO) return;

		FermataActivity a = MainActivityDelegate.get(getContext()).getAppActivity();
		a.stopInput(this);
	}

	private boolean isKeyboardActive() {
		if (!BuildConfig.AUTO) return false;

		FermataActivity a = MainActivityDelegate.get(getContext()).getAppActivity();
		return a.isInputActive();
	}

	@Override
	public void afterTextChanged(Editable s) {
		if (BuildConfig.AUTO) setTextInput(s);
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, @Nullable KeyEvent event) {
		if (!BuildConfig.AUTO) return false;

		switch (actionId) {
			case EditorInfo.IME_ACTION_GO:
			case EditorInfo.IME_ACTION_SEARCH:
			case EditorInfo.IME_ACTION_SEND:
			case EditorInfo.IME_ACTION_NEXT:
			case EditorInfo.IME_ACTION_DONE:
				submitForm();
				hideKeyboard();
		}

		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		FermataChromeClient chrome = getWebChromeClient();

		if ((chrome != null) && chrome.isFullScreen()) {
			chrome.onTouchEvent(this, ev);
		} else if (BuildConfig.AUTO) {
			FermataActivity a = MainActivityDelegate.get(getContext()).getAppActivity();

			if (a.isInputActive()) {
				a.stopInput(this);
				return true;
			}
		}

		return super.onInterceptTouchEvent(ev);
	}

	static final class UserAgent {
		private static final Pattern pattern = Pattern.compile(".+ AppleWebKit/(\\S+) .+ Chrome/(\\S+) .+");
		static String ua;
		static String uaDesktop;

		static String getUa(WebSettings s, WebBrowserAddon a) {
			if (ua != null) return ua;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String av;
				if (VERSION.SDK_INT >= VERSION_CODES.R) av = VERSION.RELEASE_OR_CODENAME;
				else av = VERSION.RELEASE;
				String wv = m.group(1);
				String cv = m.group(2);
				UserAgent.ua = a.getPreferenceStore().getStringPref(a.getUserAgentPref()).trim()
						.replace("{ANDROID_VERSION}", av)
						.replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
				if (UserAgent.ua.isEmpty()) UserAgent.ua = ua;
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				UserAgent.ua = ua;
			}

			return UserAgent.ua;
		}

		static String getUaDesktop(WebSettings s, WebBrowserAddon a) {
			if (uaDesktop != null) return uaDesktop;

			String ua = s.getUserAgentString();
			Matcher m = pattern.matcher(ua);

			if (m.matches()) {
				String wv = m.group(1);
				String cv = m.group(2);
				uaDesktop = a.getPreferenceStore().getStringPref(a.getUserAgentDesktopPref())
						.replace("{WEBKIT_VERSION}", requireNonNull(wv))
						.replace("{CHROME_VERSION}", requireNonNull(cv));
			} else {
				Log.w("User-Agent does not match the pattern ", pattern, ": " + ua);
				int i1 = ua.indexOf('(') + 1;
				int i2 = ua.indexOf(')', i1);
				uaDesktop = ua.substring(0, i1) + "X11; Linux x86_64" + ua.substring(i2)
						.replace(" Mobile ", " ")
						.replaceFirst(" Version/\\d+\\.\\d+ ", " ");
			}

			return uaDesktop;
		}
	}
}
