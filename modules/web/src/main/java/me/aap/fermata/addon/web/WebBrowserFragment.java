package me.aap.fermata.addon.web;

import static me.aap.fermata.addon.web.FermataWebClient.isYoutubeUri;
import static me.aap.fermata.util.Utils.dynCtx;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.VoiceCommand;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class WebBrowserFragment extends MainActivityFragment
		implements OverlayMenu.SelectionHandler, MainActivityListener {
	private boolean fullScreenOnResume;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.web_browser_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		dynCtx(requireContext());
		return inflater.inflate(R.layout.browser, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		WebBrowserAddon addon = getAddon();
		if (addon == null) return;

		Context ctx = view.getContext();
		FermataWebView webView = view.findViewById(R.id.browserWebView);
		ViewGroup fullScreenView = view.findViewById(R.id.browserFullScreenView);
		FermataWebClient webClient = new FermataWebClient();
		FermataChromeClient chromeClient = new FermataChromeClient(webView, fullScreenView);
		webView.init(addon, webClient, chromeClient);
		webView.loadUrl(addon.getLastUrl());
		MainActivityDelegate.getActivityDelegate(ctx).onSuccess(this::registerListeners);
	}

	@Override
	public void onDestroyView() {
		MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(this::unregisterListeners);
		super.onDestroyView();
	}

	@Override
	public void onRefresh(BooleanConsumer refreshing) {
		FermataWebView v = getWebView();
		if (v != null) {
			FermataWebClient c = v.getWebViewClient();
			if (c != null) {
				c.loading = refreshing;
				v.reload();
			}
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (!BuildConfig.AUTO) return;
		FermataWebView v = getWebView();
		if (v == null) return;
		FermataChromeClient chrome = v.getWebChromeClient();
		if (chrome != null) {
			if (chrome.isFullScreen()) {
				chrome.exitFullScreen();
				fullScreenOnResume = true;
			} else {
				fullScreenOnResume = false;
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!BuildConfig.AUTO || !fullScreenOnResume) return;
		FermataWebView v = getWebView();
		if (v == null) return;
		// Calling here onResume makes the video to not get freezed
		// when you switch to another app and go back to Fermata
		v.onResume();
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			a.post(() -> {
				FermataChromeClient chrome = v.getWebChromeClient();
				if (chrome != null) chrome.enterFullScreen();
			});
		});
	}

	protected void registerListeners(MainActivityDelegate a) {
		a.addBroadcastListener(this, MainActivityListener.ACTIVITY_DESTROY);
	}

	protected void unregisterListeners(MainActivityDelegate a) {
		FermataWebView v = getWebView();
		WebBrowserAddon addon = getAddon();
		a.removeBroadcastListener(this);
		if ((addon != null) && (v != null)) addon.getPreferenceStore().removeBroadcastListener(v);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) unregisterListeners(a);
	}

	@Override
	public void setInput(Object input) {
		loadUrl(input.toString());
	}

	public void loadUrl(String url) {
		if (Uri.parse(url).getScheme() == null) {
			url = getSearchUrl() + url;
		}

		FermataWebView v = getWebView();

		if (v != null) {
			if (!(this instanceof YoutubeFragment) && isYoutubeUri(Uri.parse(url)) &&
					AddonManager.get().hasAddon(me.aap.fermata.R.id.youtube_fragment)) {
				String u = url;
				MainActivityDelegate.getActivityDelegate(requireContext()).onSuccess(a -> {
					YoutubeFragment f = a.showFragment(me.aap.fermata.R.id.youtube_fragment);
					f.loadUrl(u);
				});
			} else {
				v.loadUrl(url);
			}
		} else {
			WebBrowserAddon addon = AddonManager.get().getAddon(WebBrowserAddon.class);
			if (addon != null) addon.setLastUrl(url);
		}
	}

	@Nullable
	public String getUrl() {
		WebView v = getWebView();
		return (v == null) ? null : v.getUrl();
	}

	@Override
	public boolean isRootPage() {
		FermataWebView v = getWebView();
		if ((v == null) || (v.getWebChromeClient() == null)) return true;
		return !v.getWebChromeClient().isFullScreen() && !v.canGoBack();
	}

	@Override
	public boolean onBackPressed() {
		FermataWebView v = getWebView();
		if (v == null) return false;
		FermataChromeClient chrome = v.getWebChromeClient();

		if ((chrome != null) && chrome.isFullScreen()) {
			chrome.exitFullScreen();
			return true;
		}

		if (v.canGoBack()) {
			v.goBack();
			return true;
		}

		return false;
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return WebToolBarMediator.getInstance();
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(WebBrowserAddon.class);
	}

	@Nullable
	protected FermataWebView getWebView() {
		View v = getView();
		return (v != null) ? v.findViewById(R.id.browserWebView) : null;
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder b) {
		WebBrowserAddon a = getAddon();
		FermataWebView v = getWebView();
		if ((a == null) || (v == null)) return;

		Context ctx = dynCtx(requireContext());
		Resources res = ctx.getResources();
		Resources.Theme theme = ctx.getTheme();
		b.addItem(me.aap.fermata.R.id.refresh,
				ResourcesCompat.getDrawable(res, me.aap.fermata.R.drawable.refresh, theme),
				res.getString(me.aap.fermata.R.string.refresh)).setHandler(this);

		if (isDesktopVersionSupported()) {
			b.addItem(R.id.desktop_version,
					ResourcesCompat.getDrawable(res, R.drawable.desktop, theme),
					res.getString(R.string.desktop_version)).setChecked(a.isDesktopVersion()).setHandler(this);
		}

		FermataChromeClient chrome = v.getWebChromeClient();
		if (chrome == null) return;

		if (!chrome.isFullScreen()) {
			if (chrome.canEnterFullScreen()) {
				b.addItem(R.id.fullscreen,
						ResourcesCompat.getDrawable(res, R.drawable.fullscreen, theme),
						res.getString(R.string.full_screen)).setHandler(this);
			}
		} else {
			b.addItem(R.id.fullscreen_exit,
					ResourcesCompat.getDrawable(res, R.drawable.fullscreen_exit, theme),
					res.getString(R.string.full_screen_exit)).setHandler(this);
		}

		b.addItem(me.aap.fermata.R.id.bookmarks,
				ResourcesCompat.getDrawable(res, me.aap.fermata.R.drawable.bookmark_filled, theme),
				res.getText(me.aap.fermata.R.string.bookmarks)).setSubmenu(this::bookmarksMenu);
	}

	protected boolean isDesktopVersionSupported() {
		return true;
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		FermataWebView v = getWebView();
		if (v == null) return false;

		int id = item.getItemId();

		if (id == me.aap.fermata.R.id.refresh) {
			v.reload();
			return true;
		} else if (id == R.id.desktop_version) {
			WebBrowserAddon addon = getAddon();
			if (addon != null) addon.setDesktopVersion(!addon.isDesktopVersion());
			return true;
		} else if (id == R.id.fullscreen || id == R.id.fullscreen_exit) {
			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return false;
			if (id == R.id.fullscreen) chrome.enterFullScreen();
			else chrome.exitFullScreen();
			return true;
		}

		return false;
	}

	public void bookmarksMenu(OverlayMenu.Builder b) {
		WebBrowserAddon a = getAddon();
		if (a == null) return;

		b.addItem(me.aap.fermata.R.id.bookmark_create, me.aap.fermata.R.string.create_bookmark).setSubmenu(this::createBookmark);
		int i = 0;

		for (Map.Entry<String, String> e : a.getBookmarks().entrySet()) {
			b.addItem(UiUtils.getArrayItemId(i++), e.getValue()).setData(e.getKey()).setHandler(this::bookmarkSelected);
		}
	}

	private void createBookmark(OverlayMenu.Builder b) {
		FermataWebView v = getWebView();
		if (v == null) return;
		PreferenceStore store = new BasicPreferenceStore();
		PreferenceStore.Pref<Supplier<String>> name = PreferenceStore.Pref.s("name", v.getTitle());
		PreferenceStore.Pref<Supplier<String>> url = PreferenceStore.Pref.s("url", v.getUrl());

		PreferenceSet set = new PreferenceSet();
		set.addStringPref(o -> {
			o.store = store;
			o.pref = name;
			o.title = me.aap.fermata.R.string.bookmark_name;
		});
		set.addStringPref(o -> {
			o.store = store;
			o.pref = url;
			o.title = R.string.url;
		});

		set.addToMenu(b, true);
		b.setCloseHandlerHandler(m -> {
			WebBrowserAddon a = getAddon();
			if (a != null) a.addBookmark(store.getStringPref(name), store.getStringPref(url));
		});
	}

	private boolean bookmarkSelected(OverlayMenuItem item) {
		if (item.isLongClick()) {
			String url = item.getData();
			item.getMenu().show(b ->
					b.addItem(me.aap.fermata.R.id.bookmark_remove, me.aap.fermata.R.string.remove_bookmark)
							.setHandler(i -> {
								WebBrowserAddon a = getAddon();
								if (a != null) a.removeBookmark(url);
								return true;
							})
			);
		} else {
			loadUrl(item.getData());
		}

		return true;
	}

	@Override
	public boolean isVoiceCommandsSupported() {
		return true;
	}

	@Override
	public void voiceCommand(VoiceCommand cmd) {
		String q = cmd.getQuery();

		if (cmd.isOpen()) {
			WebBrowserAddon a = getAddon();
			if (a != null) {
				for (Map.Entry<String, String> e : a.getBookmarks().entrySet()) {
					if (q.equalsIgnoreCase(e.getValue())) {
						loadUrl(e.getKey());
						return;
					}
				}
			}
		}

		try {
			String u = getSearchUrl() + URLEncoder.encode(q, "UTF-8");
			loadUrl(u);
		} catch (UnsupportedEncodingException ex) {
			Log.e(ex, "Failed to encode query ", q);
		}
	}

	protected String getSearchUrl() {
		return "https://www.google.com/search?q=";
	}
}
