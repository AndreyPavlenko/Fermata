package me.aap.fermata.ui.activity;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.view.InputDevice.SOURCE_CLASS_POINTER;
import static android.view.MotionEvent.ACTION_SCROLL;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.media.AudioManager;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.activity.SplitCompatActivityBase;

public class MainActivity extends SplitCompatActivityBase
		implements FermataActivity, AddonManager.Listener {
	private static FermataMediaServiceConnection service;

	@Override
	protected FutureSupplier<MainActivityDelegate> createDelegate(AppActivity a) {
		FermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			return Completed.completed(new MainActivityDelegate(a, service.createBinder()));
		}

		return FermataMediaServiceConnection.connect(a, false).map(c -> {
			assert service == null;
			service = c;
			return new MainActivityDelegate(a, service.createBinder());
		}).onFailure(err -> showAlert(getContext(), String.valueOf(err)));
	}

	@Override
	public void finish() {
		FermataMediaServiceConnection s = service;
		service = null;
		if (s != null) s.disconnect();
		super.finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this);
		AddonManager.get().addBroadcastListener(this);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		AddonManager.get().removeBroadcastListener(this);
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onBackPressed);
	}

	@Override
	public boolean isCarActivity() {
		return false;
	}

	@SuppressWarnings("unchecked")
	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return (FutureSupplier<MainActivityDelegate>) super.getActivityDelegate();
	}


	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		SplitCompat.installActivity(this);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (((event.getSource() & SOURCE_CLASS_POINTER) != 0) && (event.getAction() == ACTION_SCROLL)) {
			AudioManager amgr = (AudioManager) getContext().getSystemService(AUDIO_SERVICE);
			if (amgr == null) return false;
			float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
			amgr.adjustStreamVolume(STREAM_MUSIC, (v > 0) ? ADJUST_RAISE : ADJUST_LOWER, FLAG_SHOW_UI);
			return true;
		}

		return super.onGenericMotionEvent(event);
	}
}
