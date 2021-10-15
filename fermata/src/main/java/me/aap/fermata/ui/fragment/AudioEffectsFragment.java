package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioEffects;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.view.AudioEffectsView;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class AudioEffectsFragment extends MainActivityFragment implements
		MediaSessionCallback.Listener, MainActivityListener {

	@Override
	public int getFragmentId() {
		return R.id.audio_effects_fragment;
	}

	@Override
	public CharSequence getTitle() {
		return getResources().getString(R.string.audio_effects);
	}

	@SuppressLint("RestrictedApi")
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getMainActivity().onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			a.addBroadcastListener(this, ACTIVITY_FINISH | ACTIVITY_DESTROY);
			b.getMediaSessionCallback().addBroadcastListener(this);
		});
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		getMainActivity().onSuccess(this::removeListeners);
	}

	private void removeListeners(MainActivityDelegate a) {
		a.removeBroadcastListener(this);
		a.getMediaSessionCallback().removeBroadcastListener(this);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return new AudioEffectsView(getContext());
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		onHiddenChanged(isHidden());
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		getMainActivity().onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			AudioEffectsView view = getView();
			if (view == null) return;
			view.apply(b.getMediaSessionCallback());
		});
	}

	@Nullable
	@Override
	public AudioEffectsView getView() {
		return (AudioEffectsView) super.getView();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);

		getMainActivity().onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			AudioEffectsView view = getView();
			if (view == null) return;
			MediaSessionCallback cb = b.getMediaSessionCallback();

			if (hidden) {
				view.apply(cb);
				view.cleanup();
				return;
			}

			MediaEngine eng = cb.getEngine();

			if (eng != null) {
				PlayableItem pi = eng.getSource();

				if (pi != null) {
					AudioEffects effects = eng.getAudioEffects();

					if (effects != null) {
						view.init(cb, effects, pi);
						return;
					}
				}
			}

			close(a);
		});
	}

	@Override
	public boolean onBackPressed() {
		getMainActivity().onSuccess(a -> {
			AudioEffectsView view = getView();
			if (view != null) view.apply(a.getMediaServiceBinder().getMediaSessionCallback());
			close(a);
		});
		return true;
	}

	private void applyAndCleanup(MainActivityDelegate a) {
		AudioEffectsView view = getView();

		if (view != null) {
			view.apply(a.getMediaServiceBinder().getMediaSessionCallback());
			view.cleanup();
		}
	}

	private void close(MainActivityDelegate a) {
		AudioEffectsView view = getView();

		if (view != null) {
			view.apply(a.getMediaServiceBinder().getMediaSessionCallback());
			view.cleanup();
		}

		a.backToNavFragment();
	}

	@NonNull
	private FutureSupplier<MainActivityDelegate> getMainActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	@SuppressLint("SwitchIntDef")
	@Override
	public void onPlaybackStateChanged(MediaSessionCallback cb, PlaybackStateCompat state) {
		if (isHidden()) return;

		AudioEffectsView view;

		switch (state.getState()) {
			case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
			case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
			case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
				view = getView();

				if (view != null) {
					view.apply(cb);
					view.cleanup();
				}

				break;
			case PlaybackStateCompat.STATE_STOPPED:
				getMainActivity().onSuccess(this::close);
				break;
			default:
				MediaEngine eng = cb.getEngine();
				PlayableItem pi;
				AudioEffects effects;

				if ((eng == null) || ((pi = eng.getSource()) == null)
						|| ((effects = eng.getAudioEffects()) == null) || ((view = getView()) == null)) {
					getMainActivity().onSuccess(this::close);
				} else if (view.getEffects() != effects) {
					view.cleanup();
					view.init(cb, effects, pi);
				}
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_FINISH) {
			applyAndCleanup(a);
		} else if (e == ACTIVITY_DESTROY) {
			removeListeners(a);
		}
	}
}
