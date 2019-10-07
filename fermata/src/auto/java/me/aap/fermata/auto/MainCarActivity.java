package me.aap.fermata.auto;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.google.android.apps.auto.sdk.CarActivity;

import java.util.function.BiConsumer;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.AppActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.menu.AppMenu;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements AppActivity {
	private MainActivityDelegate delegate;

	@Override
	public MainActivityDelegate getMainActivityDelegate() {
		return delegate;
	}

	@Override
	public int getContentView() {
		return R.layout.main_activity;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		delegate = MainActivityDelegate.create(this);
		delegate.onActivityCreate();
	}

	@Override
	public void onResume() {
		super.onResume();
		getMainActivityDelegate().onActivityResume();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getMainActivityDelegate().onActivityDestroy();
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

	public boolean isCarActivity() {
		return true;
	}


	public void recreate() {
		AppMenu menu = getMainActivityDelegate().getContextMenu();
		menu.hide();
		menu.addItem(1, getResources().getString(R.string.please_restart_app));
		menu.show(i -> true);
	}

	public void finish() {
		getMainActivityDelegate().onActivityFinish();
	}

	public void startActivityForResult(BiConsumer<Integer, Intent> resultHandler, Intent intent) {
	}

	public void checkPermissions(String... perms) {
	}

	@Override
	public Window getWindow() {
		return c();
	}
}
