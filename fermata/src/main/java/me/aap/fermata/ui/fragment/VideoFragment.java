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
	private boolean serviceListenerRegistered;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return new VideoView(getContext());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		MainActivityDelegate a = getMainActivity();
		if ((a == null) || (a.getMediaServiceBinder() == null)) return;
		a.getMediaServiceBinder().addBroadcastListener(this);
		serviceListenerRegistered = true;
	}

	@Override
	public void onResume() {
		super.onResume();
		VideoView v = (VideoView) getView();
		if (v == null) return;
		MainActivityDelegate a = getMainActivity();
		if (a != null) a.setVideoMode(!isHidden(), v);
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		VideoView v = (VideoView) getView();
		if (v == null) return;

		MainActivityDelegate a = getMainActivity();
		if (a == null) return;

		if (!serviceListenerRegistered && (a.getMediaServiceBinder() != null)) {
			a.getMediaServiceBinder().addBroadcastListener(this);
			serviceListenerRegistered = true;
		}

		if (hidden) {
			a.setVideoMode(false, v);
		} else {
			a.setVideoMode(true, v);
			v.showVideo();
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		serviceListenerRegistered = false;
		MainActivityDelegate a = getMainActivity();
		if (a == null) return;

		a.setVideoMode(false, null);
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		if (b == null) return;

		b.removeBroadcastListener(this);
		b.getLib().getPrefs().removeBroadcastListener(getVideoView());
	}

	@Override
	public int getFragmentId() {
		return R.id.video;
	}

	public VideoView getVideoView() {
		return (VideoView) requireNonNull(getView());
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		if (isHidden() || ((newItem != null) && (newItem.isExternal()))) return;
		MainActivityDelegate a = getMainActivity();
		VideoView v = getVideoView();

		if ((newItem == null) || !newItem.isVideo()) {
			a.setVideoMode(false, v);
			a.backToNavFragment();
		} else {
			v.showVideo();
			a.setVideoMode(true, v);
		}
	}

	@Override
	public CharSequence getTitle() {
		PlayableItem pi = getMainActivity().getMediaServiceBinder().getCurrentItem();
		return (pi == null) ? "" : pi.getMediaDescription().getOrThrow().getTitle();
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
