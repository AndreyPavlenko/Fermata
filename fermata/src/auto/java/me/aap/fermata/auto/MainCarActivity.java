package me.aap.fermata.auto;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermata.R;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.activity.ActivityDelegate;

import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements FermataActivity {
	private static MainActivityDelegate delegate;
	private CarEditText editText;

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
		if (editText == null) editText = new CarEditText(this, null);
		editText.addTextChangedListener(w);
		a().startInput(editText);
		return editText;
	}

	public void stopInput(TextWatcher w) {
		if (editText != null) {
			editText.removeTextChangedListener(w);
			editText.setOnEditorActionListener(null);
			a().stopInput();
		}
	}

	public boolean isInputActive() {
		return a().isInputActive();
	}

	public EditText createEditText(Context ctx, AttributeSet attrs) {
		CarEditText et = new CarEditText(ctx, attrs);
		et.setOnClickListener(v -> {
			if (!a().isInputActive()) a().startInput(et);
		});
		return et;
	}
}
