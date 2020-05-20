package me.aap.fermata.addon.web;

import android.Manifest;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.view.FloatingButton;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public class FermataChromeClient extends WebChromeClient {
	private final FermataWebView web;
	private final ViewGroup fullScreenView;
	private View customView;
	private CustomViewCallback customViewCallback;
	private Promise<Void> fullScreenReq;
	private long touchStamp;

	public FermataChromeClient(FermataWebView web, ViewGroup fullScreenView) {
		this.web = web;
		this.fullScreenView = fullScreenView;
	}

	public FermataWebView getWebView() {
		return web;
	}

	@Override
	public void onShowCustomView(View view, CustomViewCallback callback) {
		if (view instanceof ViewGroup) {
			ViewGroup g = (ViewGroup) view;
			View focus = g.getFocusedChild();

			if (focus != null) {
				focus.setOnTouchListener(this::onTouchEvent);
			} else {
				for (int i = 0, n = g.getChildCount(); i < n; i++) {
					g.getChildAt(i).setOnTouchListener(this::onTouchEvent);
				}
			}
		}

		customView = view;
		customViewCallback = callback;
		addCustomView(view);
		MainActivityDelegate a = MainActivityDelegate.get(view.getContext());
		setFullScreen(a, true);

		if (fullScreenReq != null) {
			Promise<Void> req = fullScreenReq;
			fullScreenReq = null;
			req.complete(null);
		}

		a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	@Override
	public void onHideCustomView() {
		if (customViewCallback == null) return;
		touchStamp = 0;
		MainActivityDelegate a = MainActivityDelegate.get(customView.getContext());
		removeCustomView(customView);
		setFullScreen(a, false);
		customViewCallback.onCustomViewHidden();
		customView = null;
		customViewCallback = null;

		if (fullScreenReq != null) {
			Promise<Void> req = fullScreenReq;
			fullScreenReq = null;
			req.complete(null);
		}

		a.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	public boolean isFullScreen() {
		return customView != null;
	}

	public ViewGroup getFullScreenView() {
		return fullScreenView;
	}

	protected void addCustomView(View view) {
		ViewGroup fs = getFullScreenView();
		fs.addView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		fs.setVisibility(VISIBLE);
	}

	protected void removeCustomView(View view) {
		ViewGroup fs = getFullScreenView();
		fs.removeView(view);
		fs.setVisibility(GONE);
	}

	protected void setFullScreen(MainActivityDelegate a, boolean fullScreen) {
		a.setVideoMode(fullScreen, null);
		a.getFloatingButton().setVisibility(fullScreen ? GONE : VISIBLE);
	}

	public boolean canEnterFullScreen() {
		return true;
	}

	@SuppressWarnings("UnusedReturnValue")
	public FutureSupplier<Void> enterFullScreen() {
		if (isFullScreen()) return completedVoid();

		Promise<Void> req;

		if (fullScreenReq != null) {
			req = fullScreenReq;
			fullScreenReq = null;
			req.cancel();
		}

		fullScreenReq = req = new Promise<>();
		web.requestFullScreen();
		return req;
	}

	public FutureSupplier<Void> exitFullScreen() {
		if (!isFullScreen()) return completedVoid();
		Promise<Void> req;

		if (fullScreenReq != null) {
			req = fullScreenReq;
			fullScreenReq = null;
			req.cancel();
		}

		fullScreenReq = req = new Promise<>();
		onHideCustomView();
		return req;
	}

	protected boolean onTouchEvent(View v, MotionEvent event) {
		if (!isFullScreen()) return false;

		int action = event.getAction();
		if ((action != ACTION_UP) && (action != ACTION_DOWN)) return false;

		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		FloatingButton fb = a.getFloatingButton();
		long st = touchStamp = System.currentTimeMillis();

		if (action == ACTION_DOWN) {
			fb.setVisibility(GONE);
		} else {
			fb.setVisibility(VISIBLE);
			App.get().getHandler().postDelayed(() -> {
				if (st == touchStamp) fb.setVisibility(GONE);
			}, 3000);
		}

		return false;
	}

	@Override
	public void onGeolocationPermissionsHidePrompt() {
		onGeolocationPermissionsShowPrompt(null, null);
	}

	@Override
	public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
		FutureSupplier<int[]> perm = ActivityDelegate.get(getWebView().getContext()).getAppActivity()
				.checkPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
		if (cb != null) {
			perm.onCompletion((p, f) -> {
				if (f != null) {
					Log.e(f);
					cb.invoke(origin, false, false);
					return;
				}

				boolean ok = (p.length == 2) && ((p[0] == PERMISSION_GRANTED) || (p[1] == PERMISSION_GRANTED));
				cb.invoke(origin, ok, true);
			});
		}
	}
}
