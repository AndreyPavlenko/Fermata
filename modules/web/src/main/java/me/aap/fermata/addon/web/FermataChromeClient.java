package me.aap.fermata.addon.web;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.Manifest;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.view.FloatingButton;

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
	public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
		Context ctx = view.getContext();
		ActivityDelegate.get(ctx).createDialogBuilder(ctx)
				.setTitle(android.R.drawable.ic_dialog_alert, android.R.string.dialog_alert_title)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
				.show();
		return true;
	}

	@Override
	public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
		Context ctx = view.getContext();
		ActivityDelegate.get(ctx).createDialogBuilder(ctx)
				.setMessage(message)
				.setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
				.setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
				.show();
		return true;
	}

	@Override
	public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
		Context ctx = view.getContext();
		ActivityDelegate a = ActivityDelegate.get(ctx);
		EditText text = a.createEditText(ctx);
		text.setSingleLine();
		text.setText(defaultValue);
		a.createDialogBuilder(ctx)
				.setTitle(message).setView(text)
				.setNegativeButton(android.R.string.cancel, (d, i) -> result.cancel())
				.setPositiveButton(android.R.string.ok, (d, i) -> result.confirm(text.getText().toString())).show();
		return true;
	}

	@Override
	public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
		return onJsConfirm(view, url, message, result);
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
		getWebView().setVisibility(GONE);
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
		getWebView().setVisibility(VISIBLE);
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

		if (!web.requestFullScreen()) {
			onShowCustomView(new FrameLayout(web.getContext()), () -> {
			});
		}

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
		if (!isFullScreen() || (event.getAction() != ACTION_DOWN)) return false;

		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		FloatingButton fb = a.getFloatingButton();
		long st = touchStamp = System.currentTimeMillis();

		fb.setVisibility(VISIBLE);
		App.get().getHandler().postDelayed(() -> {
			if (st == touchStamp) fb.setVisibility(GONE);
		}, 3000);

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

	@Override
	public void onPermissionRequest(PermissionRequest request) {
		Log.d("Permissions requested: ", Arrays.toString(request.getResources()));
		Map<String, String> perms = new HashMap<>();
		String[] resources = request.getResources();

		for (String p : resources) {
			switch (p) {
				case PermissionRequest.RESOURCE_AUDIO_CAPTURE:
					perms.put(Manifest.permission.RECORD_AUDIO, p);
					break;
				case PermissionRequest.RESOURCE_VIDEO_CAPTURE:
					perms.put(Manifest.permission.CAMERA, p);
					break;
				case PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID:
					request.grant(resources);
					return;
			}
		}

		if (perms.isEmpty()) {
			Log.d("No permissions granted");
			request.deny();
			return;
		}

		MainActivityDelegate a = MainActivityDelegate.get(getWebView().getContext());

		if (BuildConfig.AUTO && a.isCarActivity()) {
			// Activity.checkPermissions() is not supported by AA
			Log.d("Granted permissions: ", perms.values());
			request.grant(perms.values().toArray(new String[0]));
			return;
		}

		String[] keys = perms.keySet().toArray(new String[0]);
		FutureSupplier<int[]> perm = a.getAppActivity().checkPermissions(keys);
		perm.onCompletion((r, err) -> {
			if (err != null) {
				Log.e(err, "Permission request failed");
				request.deny();
			} else {
				List<String> granted = new ArrayList<>(r.length);

				for (int i = 0; i < r.length; i++) {
					if (r[i] == PERMISSION_GRANTED) granted.add(perms.get(keys[i]));
				}

				if (granted.isEmpty()) {
					Log.d("No permissions granted");
					request.deny();
				} else {
					Log.d("Granted permissions: ", granted);
					request.grant(granted.toArray(new String[0]));
				}
			}
		});
	}

	@Override
	public boolean onConsoleMessage(ConsoleMessage m) {
		Log.d("[JS:", m.lineNumber(), "] ", m.message());
		return true;
	}
}
