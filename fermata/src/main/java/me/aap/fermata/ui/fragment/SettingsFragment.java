package me.aap.fermata.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.fermata.R;
import me.aap.fermata.media.pref.PlaybackControlPrefs;
import me.aap.fermata.pref.PreferenceViewAdapter;
import me.aap.fermata.pref.PreferenceSet;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;

import static me.aap.fermata.ui.activity.MainActivityListener.Event.FRAGMENT_CONTENT_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public class SettingsFragment extends Fragment implements MainActivityFragment {
	private PreferenceViewAdapter adapter;

	@Override
	public int getFragmentId() {
		return R.id.nav_settings;
	}

	@Override
	public CharSequence getTitle() {
		PreferenceSet set = adapter.getPreferenceSet();
		if (set.getParent() != null) return getResources().getString(set.get().title);
		else return getResources().getString(R.string.settings);
	}

	@SuppressLint("RestrictedApi")
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.pref_list_view, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		if (adapter == null) adapter = createAdapter();

		RecyclerView listView = view.findViewById(R.id.prefs_list_view);
		listView.setHasFixedSize(true);
		listView.setLayoutManager(new LinearLayoutManager(getContext()));
		listView.setAdapter(adapter);
	}

	@Override
	public boolean isRootPage() {
		return adapter.getPreferenceSet().getParent() == null;
	}

	@Override
	public boolean onBackPressed() {
		PreferenceSet p = adapter.getPreferenceSet().getParent();
		if (p == null) return false;
		adapter.setPreferenceSet(p);
		return true;
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}

	private PreferenceViewAdapter createAdapter() {
		int[] timeUnits = new int[]{R.string.time_unit_second, R.string.time_unit_minute,
				R.string.time_unit_percent};
		PreferenceSet set = new PreferenceSet();
		PreferenceSet sub1;
		PreferenceSet sub2;

		sub1 = set.subSet(o -> o.title = R.string.interface_prefs);
		sub1.addListPref(o -> {
			o.store = getMainActivity().getPrefs();
			o.pref = MainActivityPrefs.THEME;
			o.title = R.string.theme;
			o.subtitle = R.string.theme_sub;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.theme_dark, R.string.theme_light, R.string.theme_day_night};
		});
		sub1.addBooleanPref(o -> {
			o.store = getMainActivity().getPrefs();
			o.pref = MainActivityPrefs.HIDE_BARS;
			o.title = R.string.hide_bars;
			o.subtitle = R.string.hide_bars_sub;
		});
		sub1.addBooleanPref(o -> {
			o.store = getMainActivity().getPrefs();
			o.pref = MainActivityPrefs.FULLSCREEN;
			o.title = R.string.fullscreen_mode;
		});

		sub1 = set.subSet(o -> o.title = R.string.playback_prefs);

		sub2 = sub1.subSet(o -> o.title = R.string.rw_ff_click);
		sub2.addIntPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.rw_ff_long_click);
		sub2.addIntPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_LONG_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.RW_FF_LONG_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub2 = sub1.subSet(o -> o.title = R.string.prev_next_long_click);
		sub2.addIntPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PREV_NEXT_LONG_TIME;
			o.title = R.string.time;
			o.seekMax = 60;
		});
		sub2.addListPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PREV_NEXT_LONG_TIME_UNIT;
			o.title = R.string.time_unit;
			o.subtitle = R.string.time_unit_sub;
			o.formatSubtitle = true;
			o.values = timeUnits;
		});

		sub1.addBooleanPref(o -> {
			o.store = getMainActivity().getPlaybackControlPrefs();
			o.pref = PlaybackControlPrefs.PLAY_PAUSE_STOP;
			o.title = R.string.play_pause_stop;
		});

		return new PreferenceViewAdapter(set) {
			@Override
			public void setPreferenceSet(PreferenceSet set) {
				super.setPreferenceSet(set);
				getMainActivity().fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
			}
		};
	}
}
