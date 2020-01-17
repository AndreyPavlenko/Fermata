package me.aap.fermata.ui.activity;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import me.aap.fermata.function.BiConsumer;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;

public class MainActivity extends AppCompatActivity implements AppActivity {
	private static final int START_ACTIVITY_REQ = 0;
	private static final int GRANT_PERM_REQ = 1;
	private BiConsumer<Integer, Intent> resultHandler;
	private MainActivityDelegate delegate;
	private boolean exitPressed;

	@Override
	public MainActivityDelegate getMainActivityDelegate() {
		return delegate;
	}

	@Override
	public int getContentView() {
		return R.layout.main_activity;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		delegate = MainActivityDelegate.create(this);
		delegate.onActivityCreate();
	}

	@Override
	protected void onResume() {
		super.onResume();
		getMainActivityDelegate().onActivityResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		getMainActivityDelegate().onActivityDestroy();
	}

	@Override
	public void onBackPressed() {
		getMainActivityDelegate().onBackPressed();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				onBackPressed();
				return true;
			case KeyEvent.KEYCODE_P:
				delegate.getMediaServiceBinder().onPlayPauseButtonClick();
				if (delegate.isVideoMode()) delegate.getControlPanel().onVideoSeek();
				return true;
			case KeyEvent.KEYCODE_S:
				delegate.getMediaServiceBinder().getMediaSessionCallback().onStop();
				return true;
			case KeyEvent.KEYCODE_X:
				if (exitPressed) {
					finish();
				} else {
					exitPressed = true;
					Toast.makeText(getContext(), R.string.press_x_again, Toast.LENGTH_SHORT).show();
					FermataApplication.get().getHandler().postDelayed(() -> exitPressed = false, 2000);
				}

				return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void finish() {
		getMainActivityDelegate().onActivityFinish();
		super.finish();
	}

	@Override
	public boolean isCarActivity() {
		return false;
	}

	public void startActivityForResult(BiConsumer<Integer, Intent> resultHandler, Intent intent) {
		assert this.resultHandler == null;
		this.resultHandler = resultHandler;
		super.startActivityForResult(intent, START_ACTIVITY_REQ, null);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		if ((requestCode == START_ACTIVITY_REQ) && (resultHandler != null)) {
			BiConsumer<Integer, Intent> h = resultHandler;
			resultHandler = null;
			h.accept(resultCode, data);
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

	public void checkPermissions(String... perms) {
		for (String perm : perms) {
			if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, perms, GRANT_PERM_REQ);
				return;
			}
		}
	}
}
