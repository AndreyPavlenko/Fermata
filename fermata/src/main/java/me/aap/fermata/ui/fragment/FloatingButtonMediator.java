package me.aap.fermata.ui.fragment;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.FOCUS_UP;
import static me.aap.fermata.ui.activity.MainActivityPrefs.FB_LONG_PRESS_VOICE_SEARCH;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.ui.UiUtils.isVisible;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.FloatingButton.Mediator.BackMenu;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class FloatingButtonMediator implements BackMenu {
	public static final FloatingButtonMediator instance = new FloatingButtonMediator();

	@Override
	public int getIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		if (a.isVideoMode() || !a.isRootPage()) return getBackIcon();
		if (isAddFolderEnabled(a.getActiveFragment())) return R.drawable.add_folder;
		return getMenuIcon();
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());

		if (a.isVideoMode() || !a.isRootPage()) {
			a.onBackPressed();
		} else {
			ActivityFragment f = a.getActiveFragment();
			if (isAddFolderEnabled(f)) ((FoldersFragment) f).addFolder();
			else showMenu((FloatingButton) v);
		}
	}

	@Override
	public boolean onLongClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		ActivityFragment f = a.getActiveFragment();
		boolean vs = (f instanceof MainActivityFragment)
				&& ((MainActivityFragment) f).isVoiceSearchSupported();

		if (vs && a.getPrefs().getFloatingButtonLongPressPref(a) == FB_LONG_PRESS_VOICE_SEARCH) {
			FutureSupplier<int[]> check = a.isCarActivity()
					? completed(new int[]{PERMISSION_GRANTED})
					: a.getAppActivity().checkPermissions(Manifest.permission.RECORD_AUDIO);
			check.onCompletion((r, err) -> {
				if ((err == null) && (r[0] == PERMISSION_GRANTED)) {
					voiceSearch((MainActivityFragment) f);
					return;
				}
				if (err != null) Log.e(err, "Failed to request RECORD_AUDIO permission");
				UiUtils.showAlert(v.getContext(), R.string.err_no_audio_record_perm);
			});
			return true;
		}

		if (isAddFolderEnabled(f)) ((FoldersFragment) f).addFolderPicker();
		else showMenu((FloatingButton) v);
		return true;
	}

	private void voiceSearch(MainActivityFragment f) {
		Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
		i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
		i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		f.getActivityDelegate().startSpeechRecognizer(i).onSuccess(f::voiceSearch);
	}

	@Nullable
	@Override
	public View focusSearch(FloatingButton fb, int direction) {
		if (direction == FOCUS_RIGHT) {
			Context ctx = fb.getContext();
			NavBarView n = MainActivityDelegate.get(ctx).getNavBar();
			return (isVisible(n) && n.isRight()) ? n.focusSearch() : MediaItemListView.focusSearchLast(ctx, fb);
		} else if (direction == FOCUS_LEFT) {
			return MediaItemListView.focusSearchActive(fb.getContext(), fb);
		} else if (direction == FOCUS_UP) {
			ToolBarView tb = MainActivityDelegate.get(fb.getContext()).getToolBar();
			if (isVisible(tb)) return tb.focusSearch();
		}

		return null;
	}

	private boolean isAddFolderEnabled(ActivityFragment f) {
		return ((f instanceof FoldersFragment) && f.isRootPage());
	}
}
