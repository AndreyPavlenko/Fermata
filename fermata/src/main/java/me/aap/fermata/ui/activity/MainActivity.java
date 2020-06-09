package me.aap.fermata.ui.activity;

import android.os.Bundle;

import me.aap.utils.function.Supplier;
import me.aap.utils.ui.activity.SplitCompatActivityBase;

public class MainActivity extends SplitCompatActivityBase implements FermataActivity {

	@Override
	protected Supplier<MainActivityDelegate> getConstructor() {
		return MainActivityDelegate::new;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onBackPressed() {
		getActivityDelegate().onBackPressed();
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
