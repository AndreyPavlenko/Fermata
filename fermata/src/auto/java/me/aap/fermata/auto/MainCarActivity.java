package me.aap.fermata.auto;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;

import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements FermataActivity {
	static MainActivityDelegate delegate;
	private CarEditText editText;
	private TextWatcher textWatcher;

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
		setIgnoreConfigChanges(0xFFFF);
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
		UiUtils.showAlert(getContext(), R.string.please_restart_app);
	}

	public void finish() {
		getActivityDelegate().onActivityFinish();
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Intent intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		return failed(new UnsupportedOperationException());
	}

	@Override
	public Window getWindow() {
		return c();
	}

	public EditText startInput(TextWatcher w) {
		if (editText == null) editText = new CarEditText(this);
		if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
		editText.addTextChangedListener(w);
		textWatcher = w;
		a().startInput(editText);
		return editText;
	}

	public void stopInput(@Nullable TextWatcher w) {
		if (editText != null) {
			if (w != null) editText.removeTextChangedListener(w);
			if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
			editText.setOnEditorActionListener(null);
		}

		a().stopInput();
	}

	public boolean isInputActive() {
		return a().isInputActive();
	}

	public EditText createEditText(Context ctx) {
		CarEditText et = new CarEditText(ctx);
		et.setOnClickListener(v -> {
			if (!a().isInputActive()) a().startInput(et);
		});
		return et;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		return (delegate != null) ? delegate.onKeyUp(keyCode, keyEvent, super::onKeyUp)
				: super.onKeyUp(keyCode, keyEvent);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		return (delegate != null) ? delegate.onKeyDown(keyCode, keyEvent, super::onKeyDown)
				: super.onKeyDown(keyCode, keyEvent);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		return (delegate != null) ? delegate.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress)
				: super.onKeyLongPress(keyCode, keyEvent);
	}
}
