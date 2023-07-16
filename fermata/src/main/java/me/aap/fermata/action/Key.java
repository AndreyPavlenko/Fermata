package me.aap.fermata.action;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import android.view.KeyEvent;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public enum Key {
	MEDIA_STOP(KeyEvent.KEYCODE_MEDIA_STOP, Action.STOP),
	MEDIA_PLAY(KeyEvent.KEYCODE_MEDIA_PLAY, Action.PLAY),
	MEDIA_PAUSE(KeyEvent.KEYCODE_MEDIA_PAUSE, Action.PAUSE),
	MEDIA_PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, Action.PLAY_PAUSE),
	MEDIA_PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS, Action.PREV),
	MEDIA_NEXT(KeyEvent.KEYCODE_MEDIA_NEXT, Action.NEXT),
	MEDIA_REWIND(KeyEvent.KEYCODE_MEDIA_REWIND, Action.RW, Action.RW, Action.RW),
	MEDIA_FAST_FORWARD(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, Action.FF, Action.FF, Action.FF),
	VOLUME_UP(KeyEvent.KEYCODE_VOLUME_UP, Action.VOLUME_UP),
	VOLUME_DOWN(KeyEvent.KEYCODE_VOLUME_DOWN, Action.VOLUME_DOWN),
	HEADSETHOOK(KeyEvent.KEYCODE_HEADSETHOOK, Action.PLAY_PAUSE, Action.NEXT,
			Action.ACTIVATE_VOICE_CTRL),
	SEARCH(KeyEvent.KEYCODE_SEARCH, Action.ACTIVATE_VOICE_CTRL),
	BACK(KeyEvent.KEYCODE_BACK, Action.BACK_OR_EXIT),
	ESCAPE(KeyEvent.KEYCODE_ESCAPE, Action.BACK_OR_EXIT),
	DEL(KeyEvent.KEYCODE_DEL, Action.STOP),
	MENU(KeyEvent.KEYCODE_MENU, Action.MENU, Action.CP_MENU, Action.CP_MENU),
	M(KeyEvent.KEYCODE_M, Action.MENU, Action.CP_MENU, Action.CP_MENU),
	P(KeyEvent.KEYCODE_P, Action.PLAY_PAUSE),
	S(KeyEvent.KEYCODE_S, Action.STOP),
	X(KeyEvent.KEYCODE_X, Action.EXIT);

	private static final Map<Integer, Key> keys = new HashMap<>();

	private static final List<Key> all = unmodifiableList(asList(values()));
	private static final PrefsListener listener = new PrefsListener();

	static {
		for (var k : all) keys.put(k.code, k);
		MainActivityPrefs.get().addBroadcastListener(listener);
	}

	private final int code;
	private final boolean media;
	private final PreferenceStore.Pref<IntSupplier> actionPref;
	private final PreferenceStore.Pref<IntSupplier> dblActionPref;
	private final PreferenceStore.Pref<IntSupplier> longActionPref;
	@Nullable
	private Action.Handler clickHandler;
	@Nullable
	private Action.Handler dblClickHandler;
	@Nullable
	private Action.Handler longClickHandler;

	Key(int code, Action action) {
		this(code, action, action, action);
	}

	Key(int code, Action action, Action dblAction, Action longAction) {
		this.code = code;
		var name = name();
		media = name.startsWith("MEDIA_") || name.startsWith("VOLUME_");
		actionPref =
				PreferenceStore.Pref.i("KEY_ACTION_" + name, action.ordinal()).withInheritance(false);
		dblActionPref = PreferenceStore.Pref.i("KEY_ACTION_DBL_" + name, dblAction.ordinal())
				.withInheritance(false);
		longActionPref = PreferenceStore.Pref.i("KEY_ACTION_LONG_" + name, longAction.ordinal())
				.withInheritance(false);
	}

	@Nullable
	public static Key get(int code) {
		return keys.get(code);
	}

	public static List<Key> getAll() {
		return all;
	}

	public static PreferenceStore getPrefs() {
		return MainActivityPrefs.get();
	}

	public PreferenceStore.Pref<IntSupplier> getActionPref() {
		return actionPref;
	}

	public PreferenceStore.Pref<IntSupplier> getDblActionPref() {
		return dblActionPref;
	}

	public PreferenceStore.Pref<IntSupplier> getLongActionPref() {
		return longActionPref;
	}

	@Nullable
	public Action.Handler getClickHandler() {
		if (clickHandler != null) return clickHandler;
		var a = Action.get(getPrefs().getIntPref(actionPref));
		return (a == null) ? null : (clickHandler = a.getHandler());
	}

	@Nullable
	public Action.Handler getDblClickHandler() {
		if (dblClickHandler != null) return dblClickHandler;
		var a = Action.get(getPrefs().getIntPref(dblActionPref));
		return (a == null) ? null : (dblClickHandler = a.getHandler());
	}

	@Nullable
	public Action.Handler getLongClickHandler() {
		if (longClickHandler != null) return longClickHandler;
		var a = Action.get(getPrefs().getIntPref(longActionPref));
		return (a == null) ? null : (longClickHandler = a.getHandler());
	}

	public boolean isMedia() {
		return media;
	}

	public static final class PrefsListener implements PreferenceStore.Listener {

		@Override
		public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
			for (var p : prefs) {
				if (p.getName().startsWith("KEY_ACTION_")) {
					for (var k : Key.all) {
						k.clickHandler = null;
						k.dblClickHandler = null;
						k.longClickHandler = null;
					}
					return;
				}
			}
		}
	}
}
