package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.pref.BasicPreferenceStore;
import me.aap.fermata.pref.PreferenceSet;
import me.aap.fermata.pref.PreferenceStore;
import me.aap.fermata.pref.PreferenceStore.Pref;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;

/**
 * @author Andrey Pavlenko
 */
public class ControlPanelView extends LinearLayoutCompat implements MainActivityListener,
		PreferenceStore.Listener, AppMenu.SelectionHandler {
	private static final byte STATE_ACTIVE = 1;
	private static final byte STATE_HIDDEN = 2;
	@ColorInt
	private final int outlineColor;
	private final ImageButton showHideBars;
	private byte state;
	private Runnable showHideTimer;

	@SuppressLint("PrivateResource")
	public ControlPanelView(Context context, AttributeSet attrs) {
		super(context, attrs, R.attr.appControlBarStyle);
		setOrientation(VERTICAL);
		inflate(context, R.layout.control_panel_view, this);

		TypedValue typedValue = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.appBarsOutlineColor, typedValue, true);
		outlineColor = typedValue.data;

		MainActivityDelegate a = getActivity();
		a.addBroadcastListener(this, Event.ACTIVITY_FINISH, Event.VIDEO_MODE_ON,
				Event.VIDEO_MODE_OFF, Event.VIDEO_SURFACE_TOUCH);
		a.getPrefs().addBroadcastListener(this);

		showHideBars = findViewById(R.id.show_hode_bars);
		showHideBars.setOnClickListener(this::showHideBars);
		findViewById(R.id.seek_time).setOnClickListener(this::showHideBars);
		findViewById(R.id.seek_menu).setOnClickListener(this::showMenu);
		findViewById(R.id.seek_total).setOnClickListener(this::showMenu);

		setShowHideBarsIcon(a);
		showHide();
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable parentState = super.onSaveInstanceState();
		Bundle b = new Bundle();
		b.putByte("STATE", state);
		b.putParcelable("PARENT", parentState);
		return b;
	}


	@Override
	protected void onRestoreInstanceState(Parcelable st) {
		if (st instanceof Bundle) {
			Bundle b = (Bundle) st;
			this.state = b.getByte("STATE");
			super.onRestoreInstanceState(b.getParcelable("PARENT"));
			if ((state & STATE_ACTIVE) == 0) super.setVisibility(GONE);
			setShowHideBarsIcon(getActivity());
			showHide();
		}
	}

	public void bind(FermataServiceUiBinder b) {
		VideoView surface = getActivity().findViewById(R.id.video);
		b.bindControlPanel(this);
		b.bindPrevButton(findViewById(R.id.control_prev));
		b.bindRwButton(findViewById(R.id.control_rw));
		b.bindPlayPauseButton(findViewById(R.id.control_play_pause));
		b.bindFfButton(findViewById(R.id.control_ff));
		b.bindNextButton(findViewById(R.id.control_next));
		b.bindProgressBar(findViewById(R.id.seek_bar));
		b.bindProgressTime(findViewById(R.id.seek_time));
		b.bindProgressTotal(findViewById(R.id.seek_total));
		b.bindSurface(surface.getSurface());
		b.bound();
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		float outlineWidth = Resources.getSystem().getDisplayMetrics().density;
		Paint p = new Paint();
		p.setColor(outlineColor);
		p.setStrokeWidth(outlineWidth);
		float y = findViewById(R.id.seek_panel).getHeight();
		canvas.drawLine(0, y, getWidth(), y, p);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		return getActivity().interceptTouchEvent(e, super::onTouchEvent);
	}

	@Override
	public void setVisibility(int visibility) {
		if (visibility == VISIBLE) {
			if ((state & STATE_ACTIVE) != 0) return;

			MainActivityDelegate a = getActivity();
			state |= STATE_ACTIVE;
			super.setVisibility(VISIBLE);

			if (a.isVideoMode() || a.getPrefs().getHideBarsPref()) {
				a.setBarsHidden(true);
				setShowHideBarsIcon(a);
			}
		} else {
			if ((state & STATE_ACTIVE) == 0) return;

			MainActivityDelegate a = getActivity();
			state &= ~STATE_ACTIVE;
			super.setVisibility(GONE);

			if (a.isBarsHidden()) {
				a.setBarsHidden(false);
				setShowHideBarsIcon(a);
			}
		}
	}

	private void showHide() {
		showHideTimer = null;
		boolean videoMode = getActivity().isVideoMode();

		if (videoMode) {
			if ((state & STATE_HIDDEN) != 0) {
				state &= ~STATE_HIDDEN;
				super.setVisibility(VISIBLE);
				showHideTimer = new Runnable() {
					@Override
					public void run() {
						if (showHideTimer == this) showHide();
					}
				};
				FermataApplication.get().getHandler().postDelayed(showHideTimer, 5000);
			} else {
				state |= STATE_HIDDEN;
				super.setVisibility(GONE);
			}
		} else if ((state & STATE_HIDDEN) != 0) {
			state &= ~STATE_HIDDEN;
			if ((state & STATE_ACTIVE) != 0) super.setVisibility(VISIBLE);
		}
	}

	@Override
	public void onMainActivityEvent(MainActivityDelegate a, Event e) {
		if (handleActivityFinishEvent(a, e)) {
			a.getMediaServiceBinder().unbind();
			a.getPrefs().removeBroadcastListener(this);
		} else {
			switch (e) {
				case VIDEO_MODE_ON:
					a.setBarsHidden(true);
					setShowHideBarsIcon(a);
					showHide();
					break;
				case VIDEO_MODE_OFF:
					a.setBarsHidden(((state & STATE_ACTIVE) != 0) && a.getPrefs().getHideBarsPref());
					setShowHideBarsIcon(a);
					showHide();
					break;
				case VIDEO_SURFACE_TOUCH:
					showHide();
					break;
			}
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (prefs.contains(MainActivityPrefs.HIDE_BARS)) {
			MainActivityDelegate a = getActivity();

			if (a.getPrefs().getHideBarsPref()) {
				a.setBarsHidden((state & STATE_ACTIVE) != 0);
				setShowHideBarsIcon(a);
			} else if (a.isBarsHidden()) {
				a.setBarsHidden(false);
				setShowHideBarsIcon(a);
			}
		}
	}

	private void showHideBars(View v) {
		MainActivityDelegate a = getActivity();
		a.setBarsHidden(!a.isBarsHidden());
		setShowHideBarsIcon(a);
	}

	private void showMenu(View v) {
		MainActivityDelegate a = getActivity();
		FermataServiceUiBinder b = a.getMediaServiceBinder();
		PlayableItem i = b.getCurrentItem();
		if (i == null) return;

		MenuHandler h = new MenuHandler(a.findViewById(R.id.control_menu), i);
		h.show(R.layout.control_menu);
	}

	private void setShowHideBarsIcon(MainActivityDelegate a) {
		showHideBars.setImageResource(a.isBarsHidden() ? R.drawable.expand : R.drawable.collapse);
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public boolean menuItemSelected(AppMenuItem item) {
		return true;
	}

	private final class MenuHandler extends MediaItemMenuHandler {

		public MenuHandler(AppMenu menu, Item item) {
			super(menu, item);
		}

		@Override
		protected void initPlayableMenu(AppMenu menu, PlayableItem pi, boolean initRepeat) {
			super.initPlayableMenu(menu, pi, false);
			BrowsableItemPrefs p = pi.getParent().getPrefs();
			MediaEngine eng = getActivity().getMediaServiceBinder().getMediaSessionCallback().getEngine();
			boolean repeat = pi.isRepeatItemEnabled() || p.getRepeatPref();
			boolean shuffle = p.getShufflePref();
			AppMenuItem i;

			if (repeat) i = menu.findItem(R.id.repeat_disable);
			else i = menu.findItem(R.id.repeat_enable);

			i.setVisible(true);
			i.setTitle(getContext().getResources().getString(R.string.repeat));

			menu.findItem(R.id.shuffle).setVisible(!shuffle);
			menu.findItem(R.id.shuffle_disable).setVisible(shuffle);
			menu.findItem(R.id.audio_effects).setVisible((eng != null) && (eng.getAudioEffects() != null));
		}

		@Override
		public boolean menuItemSelected(AppMenuItem i) {
			int id = i.getItemId();
			AppMenu menu;
			PlayableItem pi;

			switch (id) {
				case R.id.audio_effects:
					MediaEngine eng = getActivity().getMediaServiceBinder().getMediaSessionCallback().getEngine();
					if ((eng != null) && (eng.getAudioEffects() != null))
						getActivity().showFragment(R.id.audio_effects);
					return true;
				case R.id.speed:
					new SpeedMenuHandler().show(getActivity().getContextMenu(), getItem());
					return true;
				case R.id.repeat_enable:
				case R.id.repeat_disable:
					menu = i.getMenu();
					menu.setTitle(R.string.repeat);
					menu.inflate(R.layout.repeat_menu);
					menu.findItem(R.id.repeat_disable_all).setVisible(id == R.id.repeat_disable);
					menu.show(this);
					return true;
				case R.id.repeat_track:
				case R.id.repeat_folder:
				case R.id.repeat_disable_all:
					pi = (PlayableItem) getItem();
					pi.setRepeatItemEnabled(id == R.id.repeat_track);
					pi.getParent().getPrefs().setRepeatPref(id == R.id.repeat_folder);
					return true;
				case R.id.shuffle:
				case R.id.shuffle_disable:
					pi = (PlayableItem) getItem();
					pi.getParent().getPrefs().setShufflePref(id == R.id.shuffle);
					return true;
				default:
					return super.menuItemSelected(i);
			}
		}
	}

	private final class SpeedMenuHandler implements AppMenu.SelectionHandler, AppMenu.CloseHandler {
		private PrefStore store;

		void show(AppMenu menu, Item item) {
			store = new PrefStore(item);
			PreferenceSet set = new PreferenceSet();

			set.addFloatPref(o -> {
				o.title = R.string.speed;
				o.store = store;
				o.pref = MediaPrefs.SPEED;
				o.scale = 0.1f;
				o.seekMin = 1;
				o.seekMax = 20;
			});
			set.addBooleanPref(o -> {
				o.title = R.string.current_track;
				o.store = store;
				o.pref = store.TRACK;
			});
			set.addBooleanPref(o -> {
				o.title = R.string.current_folder;
				o.store = store;
				o.pref = store.FOLDER;
			});

			View v = menu.inflate(R.layout.pref_list_view);
			RecyclerView prefsView = v.findViewById(R.id.prefs_list_view);
			set.addToView(prefsView);
			prefsView.setMinimumWidth(Resources.getSystem().getDisplayMetrics().widthPixels * 2 / 3);
			menu.show(this, this);
		}

		@Override
		public boolean menuItemSelected(AppMenuItem item) {
			return true;
		}

		@Override
		public void menuClosed(AppMenu menu) {
			store.apply();
		}

		private class PrefStore extends BasicPreferenceStore {
			final Pref<BooleanSupplier> TRACK = Pref.b("TRACK", false);
			final Pref<BooleanSupplier> FOLDER = Pref.b("FOLDER", false);
			private MediaSessionCallback cb = getActivity().getMediaServiceBinder().getMediaSessionCallback();
			private final Item item;

			PrefStore(Item item) {
				this.item = item;
				MediaPrefs prefs = item.getPrefs();
				BrowsableItem p = item.getParent();
				boolean set = false;

				try (PreferenceStore.Edit edit = editPreferenceStore()) {
					if (prefs.hasPref(MediaPrefs.SPEED)) {
						edit.setBooleanPref(TRACK, true);
						edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
						set = true;
					} else {
						edit.setBooleanPref(TRACK, false);
					}

					if (p != null) {
						prefs = p.getPrefs();

						if (prefs.hasPref(MediaPrefs.SPEED)) {
							edit.setBooleanPref(FOLDER, true);

							if (!set) {
								edit.setFloatPref(MediaPrefs.SPEED, prefs.getFloatPref(MediaPrefs.SPEED));
								set = true;
							}
						} else {
							edit.setBooleanPref(FOLDER, false);
						}
					} else {
						edit.setBooleanPref(FOLDER, false);
					}

					if (!set)
						edit.setFloatPref(MediaPrefs.SPEED, cb.getPlaybackControlPrefs().getFloatPref(MediaPrefs.SPEED));
				}
			}

			void apply() {
				BrowsableItem p = item.getParent();
				boolean set = false;

				if (getBooleanPref(TRACK)) {
					item.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
					set = true;
				} else {
					item.getPrefs().removePref(MediaPrefs.SPEED);
				}

				if (p != null) {
					if (getBooleanPref(FOLDER)) {
						p.getPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
						set = true;
					} else {
						p.getPrefs().removePref(MediaPrefs.SPEED);
					}
				}

				if (!set) {
					cb.getPlaybackControlPrefs().applyFloatPref(MediaPrefs.SPEED, getFloatPref(MediaPrefs.SPEED));
				}
			}

			@Override
			public void applyFloatPref(Pref<? extends DoubleSupplier> pref, float value) {
				if (value == 0.0f) value = 0.1f;
				super.applyFloatPref(pref, value);
				if (cb.isPlaying()) cb.onSetPlaybackSpeed(value);
			}
		}
	}
}
