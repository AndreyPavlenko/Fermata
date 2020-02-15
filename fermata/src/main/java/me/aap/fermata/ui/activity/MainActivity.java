package me.aap.fermata.ui.activity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Toast;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.fragment.NavBarMediator;
import me.aap.utils.function.Supplier;
import me.aap.utils.ui.activity.ActivityBase;

public class MainActivity extends ActivityBase implements AppActivity {
	private static MainActivity instance;
	private boolean exitPressed;

	public static MainActivity getInstance() {
		return instance;
	}

	@Override
	protected Supplier<MainActivityDelegate> getConstructor() {
		return MainActivityDelegate::new;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		instance = this;
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		instance = null;
	}

	@Override
	public void onBackPressed() {
		getActivityDelegate().onBackPressed();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		MainActivityDelegate d;

		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_ESCAPE:
				onBackPressed();
				return true;
			case KeyEvent.KEYCODE_M:
				if (event.isShiftPressed()) NavBarMediator.instance.showMenu(getActivityDelegate());
				else getActivityDelegate().getControlPanel().showMenu();
				break;
			case KeyEvent.KEYCODE_P:
				d = getActivityDelegate();
				d.getMediaServiceBinder().onPlayPauseButtonClick();
				if (d.isVideoMode()) d.getControlPanel().onVideoSeek();
				return true;
			case KeyEvent.KEYCODE_S:
				getActivityDelegate().getMediaServiceBinder().getMediaSessionCallback().onStop();
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
	public boolean isCarActivity() {
		return false;
	}

	@Override
	public MainActivityDelegate getActivityDelegate() {
		return (MainActivityDelegate) super.getActivityDelegate();
	}
}
