package me.aap.fermata.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;

import static java.util.Objects.requireNonNull;

/**
 * @author Andrey Pavlenko
 */
public class VideoFragment extends MainActivityFragment implements FermataServiceUiBinder.Listener {

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return new VideoView(getContext());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		getMainActivity().getMediaServiceBinder().addBroadcastListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		getMainActivity().setVideoMode(!isHidden());
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		VideoView v = (VideoView) getView();
		if (v == null) return;

		if (hidden) {
			getMainActivity().setVideoMode(false);
		} else {
			getMainActivity().setVideoMode(true);
			v.showVideo();
		}
	}

	@Override
	public void onDestroyView() {
		MainActivityDelegate a = getMainActivity();
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		a.setVideoMode(false);
		b.removeBroadcastListener(this);
		b.getLib().getPrefs().removeBroadcastListener((VideoView) getView());
		super.onDestroyView();
	}

	@Override
	public int getFragmentId() {
		return R.id.video;
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		if (isHidden()) return;
		MainActivityDelegate a = getMainActivity();
		VideoView v = (VideoView) requireNonNull(getView());

		if ((newItem == null) || !newItem.isVideo()) {
			a.setVideoMode(false);
			a.backToNavFragment();
		} else {
			a.setVideoMode(true);
			v.showVideo();
		}
	}

	@Override
	public CharSequence getTitle() {
		PlayableItem pi = getMainActivity().getMediaServiceBinder().getCurrentItem();
		return (pi == null) ? "" : pi.getTitle();
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
