package me.aap.utils.ui.activity;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE;
import static android.view.View.SYSTEM_UI_FLAG_VISIBLE;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_DESTROY;
import static me.aap.utils.ui.activity.ActivityListener.ACTIVITY_FINISH;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import javax.annotation.Nonnull;

import me.aap.utils.R;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.function.Function;
import me.aap.utils.function.IntObjectFunction;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.fragment.GenericDialogFragment;
import me.aap.utils.ui.fragment.GenericFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuView;
import me.aap.utils.ui.view.DialogBuilder;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public abstract class ActivityDelegate implements EventBroadcaster<ActivityListener>,
		Thread.UncaughtExceptionHandler {
	public static final int FULLSCREEN_FLAGS = SYSTEM_UI_FLAG_LAYOUT_STABLE |
			SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
			SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
			SYSTEM_UI_FLAG_LOW_PROFILE |
			SYSTEM_UI_FLAG_FULLSCREEN |
			SYSTEM_UI_FLAG_HIDE_NAVIGATION |
			SYSTEM_UI_FLAG_IMMERSIVE |
			SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
	private static Function<Context, ActivityDelegate> contextToDelegate;
	private final Collection<ListenerRef<ActivityListener>> listeners = new LinkedList<>();
	@Nonnull
	private final AppActivity activity;
	private OverlayMenu activeMenu;
	private boolean fullScreen;
	private boolean backPressed;
	private boolean recreating;
	private int activeFragmentId = ID_NULL;
	private int activeNavItemId = ID_NULL;

	public ActivityDelegate(@Nonnull AppActivity activity) {
		this.activity = activity;
	}

	@IdRes
	protected abstract int getFrameContainerId();

	@StringRes
	protected abstract int getExitMsg();

	@Nullable
	public static Function<Context, ActivityDelegate> getContextToDelegate() {
		return contextToDelegate;
	}

	public static void setContextToDelegate(Function<Context, ActivityDelegate> contextToDelegate) {
		ActivityDelegate.contextToDelegate = contextToDelegate;
	}

	@NonNull
	public static ActivityDelegate get(Context ctx) {
		return getActivityDelegate(ctx).getOrThrow();
	}

	@NonNull
	public static FutureSupplier<? extends ActivityDelegate> getActivityDelegate(Context ctx) {
		if (ctx instanceof AppActivity) {
			return ((AppActivity) ctx).getActivityDelegate();
		} else if (ctx instanceof ContextWrapper) {
			do {
				ctx = ((ContextWrapper) ctx).getBaseContext();
				if (ctx instanceof AppActivity) return ((AppActivity) ctx).getActivityDelegate();
			} while (ctx instanceof ContextWrapper);
		}

		Function<Context, ActivityDelegate> f = contextToDelegate;

		if (f != null) {
			ActivityDelegate d = f.apply(ctx);
			if (d != null) return completed(d);
		}

		IllegalArgumentException ex = new IllegalArgumentException("Unsupported context: " + ctx);
		if (f != null) Log.e(ex, "Activity delegate not found. contextToDelegate = ", f);
		return failed(ex);
	}

	@Nonnull
	public AppActivity getAppActivity() {
		return activity;
	}

	@Nonnull
	public Context getContext() {
		return getAppActivity().getContext();
	}

	protected void onActivityCreate(@Nullable Bundle savedInstanceState) {
		Log.d("onActivityCreate");
		setUncaughtExceptionHandler();
	}

	protected void setUncaughtExceptionHandler() {
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	protected void onActivityStart() {
		Log.d("onActivityStart");
	}

	protected void onActivityResume() {
		Log.d("onActivityResume");
		setSystemUiVisibility();
	}

	protected void onActivityNewIntent(Intent intent) {
		Log.d("onActivityNewIntent");
	}

	protected void onActivityPause() {
		Log.d("onActivityPause");
	}

	@SuppressWarnings("unused")
	protected void onActivitySaveInstanceState(@NonNull Bundle outState) {
		Log.d("onActivitySaveInstanceState");
	}

	protected void onActivityStop() {
		Log.d("onActivityStop");
		if (getAppActivity().isFinishing() || recreating) {
			FragmentManager fm = getSupportFragmentManager();
			FragmentTransaction tr = fm.beginTransaction();
			for (Fragment f : new ArrayList<>(fm.getFragments())) {
				Log.d("Destroying fragment ", f);
				tr.remove(f);
			}
			tr.commitAllowingStateLoss();
		}
	}

	protected void onActivityDestroy() {
		Log.d("onActivityDestroy");
		fireBroadcastEvent(ACTIVITY_DESTROY);
		activeMenu = null;
		fullScreen = false;
		backPressed = false;
		activeFragmentId = ID_NULL;
		activeNavItemId = ID_NULL;
		removeListeners(this);
	}

	protected static <T> void removeListeners(EventBroadcaster<T> b) {
		b.removeBroadcastListeners(l -> {
			Log.d(new IllegalStateException(), "Listener ", l, " has not been removed from ", b);
			return true;
		});
	}

	protected void onActivityFinish() {
		Log.d("onActivityFinish");
	}

	public void finish() {
		fireBroadcastEvent(ACTIVITY_FINISH);
		getAppActivity().finish();
	}

	public void recreate() {
		Log.d("Recreating");
		recreating = true;
		getAppActivity().recreate();
	}

	public boolean isRecreating() {
		return recreating;
	}

	@Override
	public void uncaughtException(@NonNull Thread t, @NonNull Throwable err) {
		Log.e(err, "Uncaught exception in thread ", t);
		sendCrashReport(err);
	}

	protected FutureSupplier<Void> sendCrashReport(Throwable err) {
		App app = App.get();
		String email = app.getCrashReportEmail();
		if (email == null) return completedVoid();
		CharSequence appName = getString(App.get().getApplicationInfo().labelRes);
		String subj = appName + " crash report";
		String body = TextUtils.toString(err);
		String uri = "mailto:" + email + "?subject=" + Uri.encode(subj) + "&body=" + Uri.encode(body);
		Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse(uri));
		Context ctx = getContext();
		if (i.resolveActivity(ctx.getPackageManager()) == null) return completedVoid();

		Promise<Void> p = new Promise<>();
		Thread t = new Thread() {
			@Override
			public void run() {
				Looper.prepare();
				new MaterialAlertDialogBuilder(ctx).setTitle(appName)
						.setMessage(appName + " has been crashed.\nSend crash report?")
						.setNegativeButton(android.R.string.cancel, (d, w) -> p.cancel())
						.setPositiveButton(android.R.string.ok, (d, w) -> p.complete(null)).setCancelable(false)
						.show();
				Looper.loop();
			}
		};

		t.start();
		p.onSuccess(v -> {
			File logFile = app.getLogFile();
			if (logFile != null)
				i.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + logFile.getAbsolutePath()));
			i.putExtra(Intent.EXTRA_SUBJECT, subj);
			i.putExtra(Intent.EXTRA_TEXT, body);
			startActivity(Intent.createChooser(i, "Send crash report"));
		}).thenRun(() -> {
			finish();
			Looper l = Looper.myLooper();
			if (l != null) new Handler(l).post(l::quitSafely);
		});

		return p;
	}

	public Theme getTheme() {
		return getAppActivity().getTheme();
	}

	public Window getWindow() {
		return getAppActivity().getWindow();
	}

	public View getCurrentFocus() {
		return getAppActivity().getCurrentFocus();
	}

	public Intent getIntent() {
		return getAppActivity().getIntent();
	}

	public <T extends View> T findViewById(@IdRes int id) {
		return getAppActivity().findViewById(id);
	}

	public EditText createEditText() {
		return createEditText(getContext());
	}

	public EditText createEditText(Context ctx) {
		return getAppActivity().createEditText(ctx);
	}

	public DialogBuilder createDialogBuilder() {
		return createDialogBuilder(getContext());
	}

	public DialogBuilder createDialogBuilder(Context ctx) {
		return getAppActivity().createDialogBuilder(ctx);
	}

	@NonNull
	public FragmentManager getSupportFragmentManager() {
		return getAppActivity().getSupportFragmentManager();
	}

	public ToolBarView getToolBar() {
		return null;
	}

	public float getToolBarSize() {
		return 1F;
	}

	public NavBarView getNavBar() {
		return null;
	}

	public float getNavBarSize() {
		return 1F;
	}

	public float getTextIconSize() {
		return 1F;
	}

	public int getActiveFragmentId() {
		return activeFragmentId;
	}

	@Nullable
	public ActivityFragment getActiveFragment() {
		int id = getActiveFragmentId();
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			if (f instanceof ActivityFragment) {
				ActivityFragment af = (ActivityFragment) f;
				if (af.getFragmentId() == id) return af;
			}
		}
		return null;
	}

	@Nullable
	public ActivityFragment showFragment(@IdRes int id) {
		return showFragment(id, null);
	}

	@Nullable
	public ActivityFragment showFragment(@IdRes int id, Object input) {
		int activeId = getActiveFragmentId();

		if (id == activeId) {
			var f = getActiveFragment();
			if (input != null) f.setInput(input);
			return f;
		}

		var fm = getSupportFragmentManager();
		var tr = fm.beginTransaction();
		ActivityFragment switchingFrom = null;
		ActivityFragment switchingTo = null;

		for (var f : fm.getFragments()) {
			if (!(f instanceof ActivityFragment af)) continue;
			int afid = af.getFragmentId();
			if (afid == id) {
				tr.show(f);
				switchingTo = af;
			} else if (afid == activeId) {
				switchingFrom = af;
				tr.hide(f);
			} else {
				tr.hide(f);
			}
		}

		if (switchingTo == null) {
			switchingTo = createFragment(id);
			tr.add(getFrameContainerId(), switchingTo);
			tr.show(switchingTo);
		}

		activeFragmentId = switchingTo.getFragmentId();
		if (switchingFrom != null) switchingFrom.switchingTo(switchingTo);
		switchingTo.switchingFrom(switchingFrom);
		if (input != null) switchingTo.setInput(input);
		try {
			tr.commitNow();
			postBroadcastEvent(FRAGMENT_CHANGED);
			return switchingTo;
		} catch (IllegalStateException err) {
			activeFragmentId = ID_NULL;
			Log.d(err);
			return null;
		}
	}

	protected ActivityFragment createFragment(int id) {
		if (id == R.id.file_picker) return new FilePickerFragment();
		else if (id == R.id.generic_fragment) return new GenericFragment();
		else if (id == R.id.generic_dialog_fragment) return new GenericDialogFragment();
		else throw new IllegalArgumentException("Invalid fragment id: " + id);
	}

	public int getActiveNavItemId() {
		return activeNavItemId;
	}

	public void setActiveNavItemId(int activeNavItemId) {
		this.activeNavItemId = activeNavItemId;
	}

	public boolean isRootPage() {
		ActivityFragment f = getActiveFragment();
		return (f != null) && f.isRootPage() && (getActiveNavItemId() == f.getFragmentId());
	}

	public void onBackPressed() {
		if (backPressed) {
			finish();
			return;
		}

		if (activeMenu != null) {
			if (activeMenu.back()) return;
			else if (hideActiveMenu()) return;
		}

		ToolBarView tb = getToolBar();
		if ((tb != null) && tb.onBackPressed()) return;

		ActivityFragment f = getActiveFragment();

		if (f != null) {
			if (f.onBackPressed()) return;

			int navId = getActiveNavItemId();

			if ((f.getFragmentId() != navId) && (navId != ID_NULL)) {
				showFragment(navId);
				return;
			}
		}

		if (exitOnBackPressed()) {
			backPressed = true;
			Toast.makeText(getContext(), getExitMsg(), Toast.LENGTH_SHORT).show();
			App.get().getHandler().postDelayed(() -> backPressed = false, 2000);
		}
	}

	protected boolean exitOnBackPressed() {
		return true;
	}

	@Override
	public Collection<ListenerRef<ActivityListener>> getBroadcastEventListeners() {
		return listeners;
	}

	public void fireBroadcastEvent(long event) {
		fireBroadcastEvent(l -> l.onActivityEvent(this, event), event);
	}

	public void postBroadcastEvent(long event) {
		postBroadcastEvent(l -> l.onActivityEvent(this, event), event);
	}

	public OverlayMenu createMenu(View anchor) {
		return new OverlayMenuView(getAppActivity().getContext(), null);
	}

	public void setActiveMenu(@Nullable OverlayMenu menu) {
		hideActiveMenu();
		this.activeMenu = menu;
	}

	public boolean hideActiveMenu() {
		if (activeMenu == null) return false;
		activeMenu.hide();
		activeMenu = null;
		return true;
	}

	@Nullable
	public OverlayMenu getActiveMenu() {
		return activeMenu;
	}

	public boolean isMenuActive() {
		return getActiveMenu() != null;
	}

	public boolean interceptTouchEvent(MotionEvent e, Function<MotionEvent, Boolean> view) {
		if ((e.getAction() != MotionEvent.ACTION_DOWN) || (activeMenu == null)) return view.apply(e);
		activeMenu.hide();
		activeMenu = null;
		return true;
	}

	public void setFullScreen(boolean fullScreen) {
		AppActivity a = getAppActivity();
		this.fullScreen = fullScreen;
		View decor = a.getWindow().getDecorView();
		decor.setSystemUiVisibility(fullScreen ? FULLSCREEN_FLAGS : SYSTEM_UI_FLAG_VISIBLE);
	}

	public boolean isFullScreen() {
		return fullScreen;
	}

	protected void setTheme() {
	}

	protected void setSystemUiVisibility() {
		setFullScreen(isFullScreen());
	}

	public boolean onKeyUp(int keyCode, KeyEvent keyEvent,
												 IntObjectFunction<KeyEvent, Boolean> next) {
		return next.apply(keyCode, keyEvent);
	}

	public boolean onKeyDown(int keyCode, KeyEvent keyEvent,
													 IntObjectFunction<KeyEvent, Boolean> next) {
		return next.apply(keyCode, keyEvent);
	}

	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent,
																IntObjectFunction<KeyEvent, Boolean> next) {
		return next.apply(keyCode, keyEvent);
	}

	public void startActivity(Intent intent) {
		startActivity(intent, null);
	}

	public void startActivity(Intent intent, @Nullable Bundle options) {
		getAppActivity().startActivity(intent, options);
	}

	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return getAppActivity().startActivityForResult(intent);
	}

	@NonNull
	public Resources getResources() {
		return getContext().getResources();
	}

	@NonNull
	public final String getString(@StringRes int resId) {
		return getResources().getString(resId);
	}

	@NonNull
	public final String getString(@StringRes int resId, @Nullable Object... formatArgs) {
		return getResources().getString(resId, formatArgs);
	}
}
