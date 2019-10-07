package me.aap.fermata.ui.view;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;

import static me.aap.fermata.ui.activity.MainActivityListener.Event.FILTER_CHANGED;

/**
 * @author Andrey Pavlenko
 */
public class ToolBarView extends ConstraintLayout implements TextWatcher, MainActivityListener {
	private Pattern filter;

	public ToolBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		inflate(getContext(), R.layout.tool_bar, this);

		ImageView filerButton = getFilterButton();
		MainActivityDelegate a = getActivity();
		setTitle(a);
		setToolsVisibility(a);
		a.addBroadcastListener(this, Event.ACTIVITY_FINISH, Event.SHOW_BARS, Event.HIDE_BARS,
				Event.FRAGMENT_CHANGED, Event.FRAGMENT_CONTENT_CHANGED);
		if (a.isBarsHidden()) setVisibility(GONE);

		if (getActivity().isCarActivity()) filerButton.setVisibility(GONE);
		else filerButton.setOnClickListener(this::onFilterButtonClick);

		getBackButton().setOnClickListener(this::onBackButtonClick);
		getTitle().setOnClickListener(this::onTitleClick);
		getFilterEdit().addTextChangedListener(this);
		getFilterButton().setOnClickListener(this::onFilterButtonClick);
		getViewButton().setOnClickListener(this::onViewButtonClick);
		getSortButton().setOnClickListener(this::onSortButtonClick);
	}

	public Pattern getFilter() {
		return filter;
	}

	private void setFilter(String text) {
		if ((text == null) || (text = text.trim()).isEmpty()) {
			filter = null;
		} else {
			filter = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE);
			EditText view = getFilterEdit();
			if (view.getVisibility() != VISIBLE) onFilterButtonClick(getFilterButton());
		}

		getActivity().fireBroadcastEvent(FILTER_CHANGED);
	}

	private BackButton getBackButton() {
		return (BackButton) getChildAt(0);
	}

	private TextView getTitle() {
		return (TextView) getChildAt(1);
	}

	private EditText getFilterEdit() {
		return (EditText) getChildAt(2);
	}

	private ImageView getFilterButton() {
		return (ImageView) getChildAt(3);
	}

	private ImageView getViewButton() {
		return (ImageView) getChildAt(4);
	}

	private ImageView getSortButton() {
		return (ImageView) getChildAt(5);
	}

	private void onBackButtonClick(View v) {
		if (getFilterEdit().getVisibility() != GONE) hideFilter();
		else getActivity().onBackPressed();
	}

	private void onTitleClick(View v) {
		if (getBackButton().getVisibility() != GONE) getActivity().onBackPressed();
	}

	private void onFilterButtonClick(View v) {
		getBackButton().filterModeOn();
		getTitle().setVisibility(GONE);
		EditText filter = getFilterEdit();
		filter.setVisibility(VISIBLE);
		filter.requestFocus();
	}

	private void hideFilter() {
		EditText filter = getFilterEdit();

		if (filter.getVisibility() != GONE) {
			filter.setText("");
			filter.clearFocus();
			filter.setVisibility(GONE);
			getBackButton().filterModeOff();
			getTitle().setVisibility(VISIBLE);
		}
	}

	private void onViewButtonClick(View v) {
		hideFilter();
		MainActivityDelegate a = getActivity();
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		AppMenu m = a.getToolBarMenu();
		f.discardSelection();
		m.show(R.layout.view_menu, this::onViewMenuItemClick);
	}

	private boolean onViewMenuItemClick(AppMenuItem item) {
		MainActivityDelegate a = getActivity();
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return false;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
		f.discardSelection();
		AppMenu menu;

		switch (item.getItemId()) {
			case R.id.tool_view_title:
				menu = item.getMenu();
				menu.findItem(R.id.tool_seq_num).setChecked(prefs.getTitleSeqNumPref());
				menu.findItem(R.id.tool_track_name).setChecked(prefs.getTitleNamePref());
				menu.findItem(R.id.tool_file_name).setChecked(prefs.getTitleFileNamePref());
				return true;
			case R.id.tool_seq_num:
				prefs.setTitleSeqNumPref(!prefs.getTitleSeqNumPref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_track_name:
				prefs.setTitleNamePref(!prefs.getTitleNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_file_name:
				prefs.setTitleFileNamePref(!prefs.getTitleFileNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_view_subtitle:
				menu = item.getMenu();
				menu.findItem(R.id.tool_sub_track_name).setChecked(prefs.getSubtitleNamePref());
				menu.findItem(R.id.tool_sub_file_name).setChecked(prefs.getSubtitleFileNamePref());
				menu.findItem(R.id.tool_sub_album).setChecked(prefs.getSubtitleAlbumPref());
				menu.findItem(R.id.tool_sub_artist).setChecked(prefs.getSubtitleArtistPref());
				menu.findItem(R.id.tool_sub_dur).setChecked(prefs.getSubtitleDurationPref());
				return true;
			case R.id.tool_sub_track_name:
				prefs.setSubtitleNamePref(!prefs.getSubtitleNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_file_name:
				prefs.setSubtitleFileNamePref(!prefs.getSubtitleFileNamePref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_album:
				prefs.setSubtitleAlbumPref(!prefs.getSubtitleAlbumPref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_artist:
				prefs.setSubtitleArtistPref(!prefs.getSubtitleArtistPref());
				titlePrefChanged(adapter);
				return true;
			case R.id.tool_sub_dur:
				prefs.setSubtitleDurationPref(!prefs.getSubtitleDurationPref());
				titlePrefChanged(adapter);
				return true;
			default:
				return false;
		}
	}

	private void titlePrefChanged(MediaLibFragment.ListAdapter adapter) {
		adapter.getParent().updateTitles();
		adapter.reload();
	}

	private void onSortButtonClick(View v) {
		hideFilter();
		MainActivityDelegate a = getActivity();
		MediaLibFragment f = a.getActiveMediaLibFragment();
		if (f == null) return;

		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();
		f.discardSelection();

		int sort = prefs.getSortByPref();
		AppMenu menu = a.getToolBarMenu();
		menu.inflate(R.layout.sort_menu);
		menu.findItem(R.id.tool_sort_name).setChecked(sort == BrowsableItemPrefs.SORT_BY_NAME);
		menu.findItem(R.id.tool_sort_file_name).setChecked(sort == BrowsableItemPrefs.SORT_BY_FILE_NAME);
		menu.findItem(R.id.tool_sort_none).setChecked(sort == BrowsableItemPrefs.SORT_BY_NONE);
		menu.show(this::onSortMenuItemClick);
	}

	private boolean onSortMenuItemClick(AppMenuItem item) {
		MainActivityDelegate a = getActivity();
		MainActivityFragment mf = a.getActiveFragment();
		if (!(mf instanceof MediaLibFragment)) return false;

		MediaLibFragment f = (MediaLibFragment) mf;

		f.discardSelection();
		MediaLibFragment.ListAdapter adapter = f.getAdapter();
		BrowsableItemPrefs prefs = adapter.getParent().getPrefs();

		switch (item.getItemId()) {
			case R.id.tool_sort_name:
				prefs.setSortByPref(BrowsableItemPrefs.SORT_BY_NAME);
				sortPrefChanged(adapter);
				return true;
			case R.id.tool_sort_file_name:
				prefs.setSortByPref(BrowsableItemPrefs.SORT_BY_FILE_NAME);
				sortPrefChanged(adapter);
				return true;
			case R.id.tool_sort_none:
				prefs.setSortByPref(BrowsableItemPrefs.SORT_BY_NONE);
				sortPrefChanged(adapter);
				return true;
			default:
				return false;
		}
	}

	private void sortPrefChanged(MediaLibFragment.ListAdapter adapter) {
		adapter.getParent().updateSorting();
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		setFilter(s.toString());
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Override
	public void afterTextChanged(Editable s) {
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		return getActivity().interceptTouchEvent(e, super::onTouchEvent);
	}

	@Override
	public void onMainActivityEvent(MainActivityDelegate a, Event e) {
		if (!handleActivityFinishEvent(a, e)) {
			switch (e) {
				case SHOW_BARS:
				case HIDE_BARS:
					setVisibility((e == Event.SHOW_BARS) ? VISIBLE : GONE);
					break;
				case FRAGMENT_CHANGED:
					setToolsVisibility(a);
				case FRAGMENT_CONTENT_CHANGED:
					setTitle(a);
					break;
			}
		}
	}

	private void setTitle(MainActivityDelegate a) {
		MainActivityFragment f = a.getActiveFragment();


		if (f != null) {
			getTitle().setText(f.getTitle());

			if (f.isRootPage() && (a.getActiveNavItemId() == f.getFragmentId())) {
				getBackButton().setVisibility(GONE);
			} else {
				getBackButton().setVisibility(VISIBLE);
			}
		} else {
			getBackButton().setVisibility(GONE);
		}
	}

	private void setToolsVisibility(MainActivityDelegate a) {
		int visibility = (a.getActiveFragment() instanceof MediaLibFragment) ? VISIBLE : GONE;
		getFilterButton().setVisibility(visibility);
		getViewButton().setVisibility(visibility);
		getSortButton().setVisibility(visibility);
		hideFilter();
	}
}
