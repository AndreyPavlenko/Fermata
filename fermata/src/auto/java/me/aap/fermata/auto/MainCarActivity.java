package me.aap.fermata.auto;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermata.R;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.AppActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements AppActivity {
	private static MainActivityDelegate delegate;

	static {
		ActivityDelegate.setContextToDelegate(c -> delegate);
	}

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return delegate;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		delegate = ActivityDelegate.create(MainActivityDelegate::new, this);
		delegate.onActivityCreate(savedInstanceState);

		CarUiController ctrl = getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivityDelegate().onActivityResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		MainActivityDelegate a = getActivityDelegate();

		if (a != null) {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			if (b != null) b.getMediaSessionCallback().onPause();
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public View findViewById(int i) {
		return super.findViewById(i);
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return super.getSupportFragmentManager();
	}

	@Override
	public View getCurrentFocus() {
		return null;
	}

	public boolean isCarActivity() {
		return true;
	}


	public void recreate() {
		Utils.showAlert(getContext(), R.string.please_restart_app);
	}

	public void finish() {
		getActivityDelegate().onActivityFinish();
	}

	public void startActivityForResult(BiConsumer<Integer, Intent> resultHandler, Intent intent) {
	}

	public void checkPermissions(String... perms) {
		for (String perm : perms) {
			if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
				Utils.showAlert(getContext(), R.string.use_phone_to_grant_perm);
				return;
			}
		}
	}

	@Override
	public Window getWindow() {
		return c();
	}
}
