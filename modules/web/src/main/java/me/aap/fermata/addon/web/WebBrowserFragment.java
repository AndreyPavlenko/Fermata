package me.aap.fermata.addon.web;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.yt.YoutubeFragment;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

import static me.aap.fermata.addon.web.FermataWebClient.isYoutubeUri;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class WebBrowserFragment extends MainActivityFragment implements OverlayMenu.SelectionHandler {

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.web_browser_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
	}

	@Override
	public void setInput(Object input) {
		loadUrl(input.toString());
	}

	public void loadUrl(String url) {
		if (Uri.parse(url).getScheme() == null) {
			url = "http://" + url;
		}

		FermataWebView v = getWebView();

		if (v != null) {
			if (!(this instanceof YoutubeFragment) && isYoutubeUri(Uri.parse(url))) {
				MainActivityDelegate a = MainActivityDelegate.get(getContext());
				YoutubeFragment f = a.showFragment(me.aap.fermata.R.id.youtube_fragment);
				f.loadUrl(url);
			} else {
				v.loadUrl(url);
			}
		} else {
			WebBrowserAddon addon = AddonManager.get().getAddon(WebBrowserAddon.class);
			if (addon != null) addon.setLastUrl(url);
		}
	}

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
		FermataWebView v = getWebView();
		if (v == null) return;

		b.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
				me.aap.fermata.R.string.refresh).setHandler(this);

		if (v.canGoForward()) {
			b.addItem(R.id.browser_forward, R.drawable.forward, R.string.go_forward).setHandler(this);
		}

		FermataChromeClient chrome = v.getWebChromeClient();
		if (chrome == null) return;

		if (!chrome.isFullScreen()) {
			if (chrome.canEnterFullScreen()) {
				b.addItem(R.id.fullscreen, R.drawable.fullscreen, R.string.full_screen).setHandler(this);
			}
		} else {
			b.addItem(R.id.fullscreen_exit, R.drawable.fullscreen_exit, R.string.full_screen_exit).setHandler(this);
		}

		b.addItem(me.aap.fermata.R.id.bookmarks, me.aap.fermata.R.drawable.bookmark_filled,
				me.aap.fermata.R.string.bookmarks).setSubmenu(this::bookmarksMenu);
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		FermataWebView v = getWebView();
		if (v == null) return false;

		int id = item.getItemId();

		switch (id) {
			case me.aap.fermata.R.id.refresh:
				v.reload();
				return true;
			case R.id.browser_forward:
				v.goForward();
				return true;
			case R.id.fullscreen:
			case R.id.fullscreen_exit:
				FermataChromeClient chrome = v.getWebChromeClient();
				if (chrome == null) return false;
				if (id == R.id.fullscreen) chrome.enterFullScreen();
				else chrome.exitFullScreen();
				return true;
		}

		return false;
	}

	private void bookmarksMenu(OverlayMenu.Builder b) {
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
}
