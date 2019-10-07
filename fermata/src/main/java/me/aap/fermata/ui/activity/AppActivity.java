package me.aap.fermata.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.view.View;
import android.view.Window;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import java.util.function.BiConsumer;

/**
 * @author Andrey Pavlenko
 */
public interface AppActivity {

	MainActivityDelegate getMainActivityDelegate();

	Resources.Theme getTheme();

	void setTheme(int resid);

	@LayoutRes
	int getContentView();

	<T extends View> T findViewById(@IdRes int id);

	void setContentView(@LayoutRes int layoutResID);

	@NonNull
	FragmentManager getSupportFragmentManager();

	boolean isCarActivity();

	void recreate();

	void finish();

	void startActivityForResult(BiConsumer<Integer, Intent> resultHandler, Intent intent);

	void checkPermissions(String... perms);

	default Context getContext() {
		return (Context) this;
	}

	Window getWindow();
}
