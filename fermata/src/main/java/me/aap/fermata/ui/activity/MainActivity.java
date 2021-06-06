package me.aap.fermata.ui.activity;

import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.activity.SplitCompatActivityBase;

import static me.aap.utils.ui.UiUtils.showAlert;

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
}
