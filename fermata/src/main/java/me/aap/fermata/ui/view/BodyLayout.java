package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.constraintlayout.widget.Guideline;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.utils.app.App;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
public class BodyLayout extends ConstraintLayout implements
		SwipeRefreshLayout.OnRefreshListener, SwipeRefreshLayout.OnChildScrollUpCallback,
		View.OnTouchListener, FermataServiceUiBinder.Listener, MainActivityListener {
	private Mode mode;

	public BodyLayout(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		this(ctx, attrs, true);
	}

	@SuppressLint("ClickableViewAccessibility")
	private BodyLayout(@NonNull Context ctx, @Nullable AttributeSet attrs, boolean init) {
		super(ctx, attrs);
		inflate(ctx, isPortrait() ? R.layout.body_layout : R.layout.body_layout_land, this);
		if (!init) return;

		MainActivityDelegate a = getActivity();
		SwipeRefreshLayout srl = getSwipeRefresh();
		srl.setId(R.id.swiperefresh);
		srl.setOnRefreshListener(this);
		srl.setOnChildScrollUpCallback(this);
		getSplitLine().setOnTouchListener(this);
		getSplitHandle().setOnTouchListener(this);
		setSplitPercent(a.getPrefs().getSplitPercent(ctx));
		setMode(Mode.FRAME);

		FermataServiceUiBinder b = a.getMediaServiceBinder();
		b.addBroadcastListener(this);
		a.addBroadcastListener(this, FRAGMENT_CHANGED | ACTIVITY_DESTROY);
		onPlayableChanged(null, b.getCurrentItem());
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
			case FRAME:
				vv.setVisibility(GONE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(VISIBLE);
				lp.guidePercent = isPortrait() ? 0f : 1f;
				a.setVideoMode(false, vv);
				break;
			case VIDEO:
				vv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(GONE);
				getSplitHandle().setVisibility(GONE);
				getSwipeRefresh().setVisibility(GONE);
				lp.guidePercent = isPortrait() ? 1f : 0f;
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				App.get().getHandler().post(vv::requestFocus);
				break;
			case BOTH:
				vv.setVisibility(VISIBLE);
				getSplitLine().setVisibility(VISIBLE);
				getSplitHandle().setVisibility(VISIBLE);
				getSwipeRefresh().setVisibility(VISIBLE);
				lp.guidePercent = a.getPrefs().getSplitPercent(getContext());
				vv.showVideo(true);
				a.setVideoMode(true, vv);
				MediaItemListView.focusActive(vv);
				break;
		}

		gl.setLayoutParams(lp);
		a.fireBroadcastEvent(MODE_CHANGED);
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		layout(newConfig.orientation);
	}

	private void layout(int orientation) {
		if (orientation == Configuration.ORIENTATION_PORTRAIT) layoutPortrait();
		else layoutLandscape();
	}

	private void layoutPortrait() {
		ConstraintSet cs = new ConstraintSet();
		cs.clone(new BodyLayout(getContext(), null, false));
		cs.applyTo(this);
		getSplitHandle().setImageResource(R.drawable.horizontal_split);
		setSplitPercent(getActivity().getPrefs().getSplitPercent(getContext()));
		setMode(getMode());
	}

	private void layoutLandscape() {
		ConstraintSet cs = new ConstraintSet();
		cs.clone(new BodyLayout(getContext(), null, false));
		cs.applyTo(this);
		getSplitHandle().setImageResource(R.drawable.vertical_split);
		setSplitPercent(getActivity().getPrefs().getSplitPercent(getContext()));
		setMode(getMode());
	}

	public VideoView getVideoView() {
		return findViewById(R.id.video_view);
	}

	private SwipeRefreshLayout getSwipeRefresh() {
		return findViewById(R.id.swiperefresh);
	}

	private Guideline getGuideline() {
		return findViewById(R.id.guideline);
	}

	private View getSplitLine() {
		return findViewById(R.id.split_line);
	}

	private AppCompatImageView getSplitHandle() {
		return findViewById(R.id.split_handle);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			a.getMediaServiceBinder().removeBroadcastListener(this);
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

				if ((i != null) && i.isVideo() && !i.isExternal() && (cb.getVideoView() == getVideoView())) {
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
		if (a.getActiveMediaLibFragment() == null) return;

		if ((newItem == null) || !newItem.isVideo() || newItem.isExternal()) {
			setMode(BodyLayout.Mode.FRAME);
		} else {
			if (isFrameMode()) setMode(BodyLayout.Mode.VIDEO);
			else getVideoView().showVideo(false);
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

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				return true;
			case MotionEvent.ACTION_MOVE:
				float percent;
				if (isPortrait()) percent = (event.getRawY()) / getRootView().getHeight();
				else percent = (event.getRawX()) / getRootView().getWidth();

				setSplitPercent(percent);
				getActivity().getPrefs().setSplitPercent(getContext(), percent);
				return true;
			default:
				return false;
		}
	}

	private void setSplitPercent(float percent) {
		Guideline gl = getGuideline();
		ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) gl.getLayoutParams();
		params.guidePercent = Math.max(0.1f, Math.min(0.9f, percent));
		gl.setLayoutParams(params);
	}

	private boolean isPortrait() {
		return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	public enum Mode {
		FRAME, VIDEO, BOTH
	}
}
