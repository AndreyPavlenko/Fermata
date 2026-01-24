package me.aap.utils.ui.activity;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import com.google.android.play.core.splitcompat.SplitCompat;

/**
 * @author Andrey Pavlenko
 */
public abstract class SplitCompatActivityBase extends ActivityBase
		implements FragmentOnAttachListener {

	@Override
	protected void attachBaseContext(Context newBase) {
		super.attachBaseContext(newBase);
		SplitCompat.installActivity(this);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		getSupportFragmentManager().addFragmentOnAttachListener(this);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		getSupportFragmentManager().removeFragmentOnAttachListener(this);
		super.onDestroy();
	}

	@Override
	public void onAttachFragment(@NonNull FragmentManager mgr, @NonNull Fragment f) {
		SplitCompat.install(f.requireContext());
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		SplitCompat.installActivity(this);
		super.onConfigurationChanged(newConfig);
	}
}
