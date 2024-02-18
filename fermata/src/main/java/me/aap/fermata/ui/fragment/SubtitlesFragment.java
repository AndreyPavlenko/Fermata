package me.aap.fermata.ui.fragment;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.R;
import me.aap.fermata.ui.view.SubtitlesView;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class SubtitlesFragment extends MainActivityFragment {

	@Override
	public int getFragmentId() {
		return R.id.subtitles_fragment;
	}

	@Override
	public CharSequence getTitle() {
		var eng = getActivityDelegate().getMediaSessionCallback().getEngine();

		if (eng != null) {
			var i = eng.getSource();
			if (i != null) {
				var md = i.getMediaDescription().peek();
				try (var tb = SharedTextBuilder.get()) {
					if (md == null) {
						tb.append(i.getName());
					} else {
						tb.append(md.getTitle());
						var dsc = md.getDescription();
						if (dsc != null) tb.append(" (").append(dsc).append(')');
					}
					var si = eng.getCurrentSubtitleStreamInfo();
					if (si != null) tb.append(" (").append(si).append(')');
					return tb.toString();
				}
			}
		}

		return getString(R.string.subtitles);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		return new SubtitlesView(requireContext(), null);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		var lp = view.getLayoutParams();
		lp.width = MATCH_PARENT;
		lp.height = MATCH_PARENT;
		view.setLayoutParams(lp);
	}

	@Override
	public void onStart() {
		super.onStart();
		start();
	}

	@Override
	public void onStop() {
		super.onStop();
		stop();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		if (hidden) stop();
		else start();
	}

	@Override
	public boolean onBackPressed() {
		getActivityDelegate().goToCurrent();
		return true;
	}

	public void restart() {
		stop();
		start();
		getActivityDelegate().fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
	}

	private void start() {
		var a = getActivityDelegate();
		var eng = a.getMediaSessionCallback().getEngine();
		var src = (eng == null) ? null : eng.getSource();

		if (src == null) {
			onBackPressed();
		} else {
			a.keepScreenOn(true);
			eng.getCurrentSubtitles().main().onSuccess(sg -> {
				var v = (SubtitlesView) getView();
				if ((v == null) || (eng.getSource() != src)) return;
				v.setSubtitles(sg);
				eng.addSubtitleConsumer(v);
			});
		}
	}

	private void stop() {
		var a = getActivityDelegate();
		if (!a.isVideoMode()) a.keepScreenOn(false);
		var v = (SubtitlesView) getView();
		if (v == null) return;
		v.setSubtitles(null);
		var eng = a.getMediaSessionCallback().getEngine();
		if (eng != null) eng.removeSubtitleConsumer(v);
	}
}
