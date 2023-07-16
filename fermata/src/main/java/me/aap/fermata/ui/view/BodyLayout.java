package me.aap.fermata.ui.view;

import static me.aap.fermata.R.id.subtitles_fragment;
import static me.aap.fermata.ui.activity.MainActivityPrefs.L_SPLIT_PERCENT;
import static me.aap.fermata.ui.activity.MainActivityPrefs.P_SPLIT_PERCENT;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Guideline;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.SubtitlesFragment;
import me.aap.utils.app.App;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class BodyLayout extends SplitLayout
		implements SwipeRefreshLayout.OnRefreshListener, SwipeRefreshLayout.OnChildScrollUpCallback,
		MainActivityListener, FermataServiceUiBinder.Listener, MediaSessionCallback.Listener {
	private Mode mode;

	public BodyLayout(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);

		SwipeRefreshLayout srl = getSwipeRefresh();
		srl.setId(R.id.swiperefresh);
		srl.setOnRefreshListener(this);
		srl.setOnChildScrollUpCallback(this);
		setMode(Mode.FRAME);

		MainActivityDelegate.getActivityDelegate(ctx).onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			b.addBroadcastListener(this);
			a.addBroadcastListener(this, FRAGMENT_CHANGED | ACTIVITY_DESTROY);
			b.getMediaSessionCallback().addBroadcastListener(this);
			onPlayableChanged(null, b.getCurrentItem());
		});
	}

	@Override
	protected int getLayout(boolean portrait) {
		return portrait ? R.layout.body_layout : R.layout.body_layout_land;
	}

	@Override
	protected Pref<DoubleSupplier> getSplitPercentPref(boolean portrait) {
		return portrait ? P_SPLIT_PERCENT : L_SPLIT_PERCENT;
	}

	public Mode getMode() {
		return mode;
	}

	public boolean isFrameMode() {
		return getMode() == Mode.FRAME;
	}

	public boolean isVideoMode() {
		return getMode() == Mode.VIDEO;
	}

	public boolean isBothMode() {
		return getMode() == Mode.BOTH;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
		Guideline gl = getGuideline();
		ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) gl.getLayoutParams();
		MainActivityDelegate a = getActivity();
		VideoView vv = getVideoView();

		switch (mode) {
			case FRAME -> {
				vv.setVisibility(GONE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(VISIBLE);
				lp.guidePercent = isPortrait() ? 0f : 1f;
				a.setVideoMode(false, vv);
			}
			case VIDEO -> {
				vv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(GONE);
				lp.guidePercent = isPortrait() ? 1f : 0f;
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				App.get().getHandler().post(vv::requestFocus);
			}
			case BOTH -> {
				vv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(VISIBLE);
				getSplitHandle().setVisibility(VISIBLE);
				getSwipeRefresh().setVisibility(VISIBLE);
				lp.guidePercent = a.getPrefs().getFloatPref(getSplitPercentPref(isPortrait()));
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				MediaItemListView.focusActive(getContext(), vv);
			}
		}

		gl.setLayoutParams(lp);
		a.fireBroadcastEvent(MODE_CHANGED);
	}

	public VideoView getVideoView() {
		return findViewById(R.id.video_view);
	}

	private SwipeRefreshLayout getSwipeRefresh() {
		return findViewById(R.id.swiperefresh);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setMode(getMode());
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			b.removeBroadcastListener(this);
			b.getMediaSessionCallback().removeBroadcastListener(this);
		} else if (e == FRAGMENT_CHANGED) {
			if (a.getActiveMediaLibFragment() == null) {
				setMode(Mode.FRAME);
			} else {
				MediaSessionCallback cb = a.getMediaSessionCallback();
				MediaEngine eng = cb.getEngine();

				if (eng == null) {
					setMode(Mode.FRAME);
					return;
				}

				MediaLib.PlayableItem i = eng.getSource();

				if ((i != null) && i.isVideo() && eng.isSplitModeSupported() &&
						(cb.getVideoView() == getVideoView())) {
					setMode(Mode.BOTH);
				} else {
					setMode(Mode.FRAME);
				}
			}
		}
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		MainActivityDelegate a = getActivity();
		var f = a.getActiveFragment();
		if (f instanceof SubtitlesFragment) a.goToCurrent();
		else if ((f != null) && !(f instanceof MediaLibFragment)) return;
		MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();

		if ((newItem == null) || !newItem.isVideo() || (eng == null) || !eng.isSplitModeSupported()) {
			setMode(Mode.FRAME);
		} else {
			if (!eng.isVideoModeRequired()) setMode(Mode.FRAME);
			else if (isFrameMode()) setMode(Mode.VIDEO);
			else getVideoView().showVideo(false);
		}

		if ((eng != null) && (newItem != null) && !newItem.isVideo() && (getMode() == Mode.FRAME)) {
			eng.selectSubtitleStream();
		}
	}

	@Override
	public void onSubtitleStreamChanged(MediaSessionCallback cb, @Nullable SubtitleStreamInfo info) {
		if (getMode() != Mode.FRAME) return;
		var i = cb.getCurrentItem();
		if ((i == null) || i.isVideo()) return;
		var a = getActivity();
		var f = a.getActiveFragment();
		if (info == null) {
			if (f instanceof SubtitlesFragment) a.goToCurrent();
		} else if (f instanceof SubtitlesFragment) {
			((SubtitlesFragment) f).restart();
		} else {
			a.post(() -> a.showFragment(subtitles_fragment));
		}
	}

	@Override
	public void onPlaybackError(String message) {
		Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
	}

	@Override
	public void onRefresh() {
		ActivityFragment f = getActivity().getActiveFragment();
		if (f != null) f.onRefresh(getSwipeRefresh()::setRefreshing);
	}

	@Override
	public boolean canChildScrollUp(@NonNull SwipeRefreshLayout parent, @Nullable View child) {
		MainActivityDelegate a = getActivity();
		if (a.isMenuActive()) return true;
		ActivityFragment f = a.getActiveFragment();
		return (f != null) && f.canScrollUp();
	}

	public enum Mode {
		FRAME, VIDEO, BOTH
	}
}
