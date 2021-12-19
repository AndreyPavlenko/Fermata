package me.aap.fermata.auto;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView.OnEditorActionListener;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.google.android.apps.auto.sdk.CarActivity;
import com.google.android.apps.auto.sdk.CarUiController;

import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class MainCarActivity extends CarActivity implements FermataActivity {
	static FermataMediaServiceConnection service;
	@SuppressWarnings("unchecked")
	@NonNull
	private FutureSupplier<MainActivityDelegate> delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	private CarEditText editText;
	private TextWatcher textWatcher;
	public static Boolean keyboardButtonPressed = false;

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegate;
	}

	@Override
	protected void attachBaseContext(Context base) {
		MainActivityDelegate.attachBaseContext(base);
		super.attachBaseContext(base);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this);
		super.onCreate(savedInstanceState);
		setIgnoreConfigChanges(0xFFFFFFFF);
		CarUiController ctrl = getCarUiController();
		ctrl.getStatusBarController().hideAppHeader();
		ctrl.getMenuController().hideMenuButton();
		FermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			onCreate(savedInstanceState, s);
		} else {
			delegate = FermataMediaServiceConnection.connect(this, true).main()
					.onFailure(err -> showAlert(getContext(), String.valueOf(err)))
					.map(c -> {
						service = c;
						return onCreate(savedInstanceState, c);
					});
		}
	}

	private MainActivityDelegate onCreate(Bundle state, FermataMediaServiceConnection s) {
		MainActivityDelegate d = new MainActivityDelegate(this, s.createBinder());
		ActivityDelegate.setContextToDelegate(ctx -> d);
		delegate = completed(d);
		d.onActivityCreate(state);
		return d;
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityResume);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void onDestroy() {
		super.onDestroy();
		getActivityDelegate()
				.onSuccess(MainActivityDelegate::onActivityDestroy)
				.thenRun(() -> ActivityDelegate.setContextToDelegate(null));
		delegate = (FutureSupplier<MainActivityDelegate>) NO_DELEGATE;
	}

	@Override
	public void onConfigurationChanged(Configuration configuration) {
		Log.i("Configuration changed: ", configuration);
		super.onConfigurationChanged(configuration);
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

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	public void recreate() {
	}

	public void finish() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onActivityFinish);
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	public FutureSupplier<int[]> checkPermissions(String... perms) {
		return failed(new UnsupportedOperationException());
	}

	@Override
	public Window getWindow() {
		return c();
	}

	@Override
	public EditText startInput(TextWatcher w) {
		if (editText == null) editText = new CarEditText(this);
		if (textWatcher != null) editText.removeTextChangedListener(textWatcher);
		editText.addTextChangedListener(w);
		textWatcher = w;
		getActivityDelegate().onSuccess(a -> {
			if (a.getPrefs().getVoiceControlEnabledPref()) {
				a.startSpeechRecognizer(true).onCompletion((q, err) -> {
					stopInput();
					if ((q != null) && !q.isEmpty()) {
						editText.setText(q.get(0));
						w.afterTextChanged(editText.getText());
					} else if (keyboardButtonPressed) {
						textWatcher = w;
						editText.removeTextChangedListener(w);
						editText.addTextChangedListener(w);
						if (w instanceof OnEditorActionListener)
							editText.setOnEditorActionListener((OnEditorActionListener) w);
						a().startInput(editText);
						setKeyboardButtonPressed(false);
					} else {
						stopInput();
					}
				});
			} else {
				a().startInput(editText);
			}
		});
		return editText;
	}

	public static void setKeyboardButtonPressed(Boolean value) {
		keyboardButtonPressed = value;
	}

	public void stopInput() {
		if (editText != null) {
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
	public boolean setTextInput(String text) {
		if ((editText == null) || !isInputActive()) return false;
		editText.setText(text);
		stopInput();
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyUp(keyCode, keyEvent, super::onKeyUp)
				: super.onKeyUp(keyCode, keyEvent);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyDown(keyCode, keyEvent, super::onKeyDown)
				: super.onKeyDown(keyCode, keyEvent);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent keyEvent) {
		MainActivityDelegate d = delegate.peek();
		return (d != null) ? d.onKeyLongPress(keyCode, keyEvent, super::onKeyLongPress)
				: super.onKeyLongPress(keyCode, keyEvent);
	}
}
