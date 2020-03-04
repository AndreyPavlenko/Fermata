package me.aap.fermata.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.AudioStreamInfo;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.SubtitleStreamInfo;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.pref.BrowsableItemPrefs;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ImageButton;

/**
 * @author Andrey Pavlenko
 */
public class ControlPanelView extends LinearLayoutCompat implements MainActivityListener,
		PreferenceStore.Listener, OverlayMenu.SelectionHandler {
	private static final byte MASK_VISIBLE = 1;
	private static final byte MASK_VIDEO_MODE = 2;
	private final ImageButton showHideBars;
	private HideTimer hideTimer;
	private byte mask;

	@SuppressLint("PrivateResource")
	public ControlPanelView(Context context, AttributeSet attrs) {
		super(context, attrs, R.attr.appControlPanelStyle);
		setOrientation(VERTICAL);
		inflate(context, R.layout.control_panel_view, this);

		TypedArray ta = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.colorBackground},
				R.attr.appControlPanelStyle, R.style.AppTheme_ControlPanelStyle);
		setBackgroundColor(ta.getColor(0, Color.TRANSPARENT));
		ta.recycle();

		MainActivityDelegate a = getActivity();
		a.addBroadcastListener(this, ACTIVITY_DESTROY);
		a.getPrefs().addBroadcastListener(this);

		ViewGroup g = findViewById(R.id.show_hide_bars);
		showHideBars = (ImageButton) g.getChildAt(0);
		g.setOnClickListener(this::showHideBars);
		showHideBars.setOnClickListener(this::showHideBars);
		g = findViewById(R.id.control_menu_button);
		g.setOnClickListener(this::showMenu);
		g.getChildAt(1).setOnClickListener(this::showMenu);
		setShowHideBarsIcon(a);
	}

	@Nullable
	@Override
	protected Parcelable onSaveInstanceState() {
		Parcelable parentState = super.onSaveInstanceState();
		Bundle b = new Bundle();
		b.putByte("MASK", mask);
		b.putParcelable("PARENT", parentState);
		return b;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable st) {
		if (st instanceof Bundle) {
			Bundle b = (Bundle) st;
			super.onRestoreInstanceState(b.getParcelable("PARENT"));
			mask = b.getByte("MASK");
			if (mask != MASK_VISIBLE) super.setVisibility(GONE);
		}
	}

	public void bind(FermataServiceUiBinder b) {
		b.bindControlPanel(this);
		b.bindPrevButton(findViewById(R.id.control_prev));
		b.bindRwButton(findViewById(R.id.control_rw));
		b.bindPlayPauseButton(findViewById(R.id.control_play_pause));
		b.bindFfButton(findViewById(R.id.control_ff));
		b.bindNextButton(findViewById(R.id.control_next));
		b.bindProgressBar(findViewById(R.id.seek_bar));
		b.bindProgressTime(findViewById(R.id.seek_time));
		b.bindProgressTotal(findViewById(R.id.seek_total));
		b.bound();
	}

	public boolean isActive() {
		return mask != 0;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		if (hideTimer != null) {
			hideTimer = new HideTimer(hideTimer.views);
			FermataApplication.get().getHandler().postDelayed(hideTimer, 5000);
		}
		return getActivity().interceptTouchEvent(e, super::onTouchEvent);
	}

	@Override
	public void setVisibility(int visibility) {
		if (visibility == VISIBLE) {
			if ((mask & MASK_VIDEO_MODE) != 0) return;
			mask |= MASK_VISIBLE;

			MainActivityDelegate a = getActivity();
			super.setVisibility(VISIBLE);

			if (a.getPrefs().getHideBarsPref()) {
				a.setBarsHidden(true);
				setShowHideBarsIcon(a);
			}
		} else {
			MainActivityDelegate a = getActivity();
			mask = 0;
			super.setVisibility(GONE);
			a.getFloatingButton().setVisibility(VISIBLE);

			if (a.isBarsHidden()) {
				a.setBarsHidden(false);
				setShowHideBarsIcon(a);
			}
		}
	}

	public void enableVideoMode() {
		if ((mask & MASK_VISIBLE) == 0) return;

		MainActivityDelegate a = getActivity();
		hideTimer = null;
		mask |= MASK_VIDEO_MODE;
		super.setVisibility(GONE);
		a.getFloatingButton().setVisibility(GONE);
		a.setBarsHidden(true);
		setShowHideBarsIcon(a);
	}

	public void disableVideoMode() {
		MainActivityDelegate a = getActivity();
		hideTimer = null;
		mask &= ~MASK_VIDEO_MODE;
		a.getFloatingButton().setVisibility(VISIBLE);

		if ((mask & MASK_VISIBLE) == 0) {
			super.setVisibility(GONE);
			a.setBarsHidden(false);
		} else {
			super.setVisibility(VISIBLE);
			a.setBarsHidden(a.getPrefs().getHideBarsPref());
		}

		setShowHideBarsIcon(a);
	}

	public void onVideoViewTouch(VideoView view) {
		MainActivityDelegate a = getActivity();
		View title = view.getTitle();
		View fb = a.getFloatingButton();

		if (getVisibility() == VISIBLE) {
			super.setVisibility(GONE);
			title.setVisibility(GONE);
			fb.setVisibility(GONE);
		} else {
			super.setVisibility(VISIBLE);
			title.setVisibility(VISIBLE);
			fb.setVisibility(VISIBLE);
			clearFocus();
			hideTimer = new HideTimer(title, fb);
			FermataApplication.get().getHandler().postDelayed(hideTimer, 5000);
		}
	}

	public void onVideoSeek() {
		MainActivityDelegate a = getActivity();
		VideoView vv = a.getMediaServiceBinder().getMediaSessionCallback().getVideoView();
		if (vv == null) return;

		View title = vv.getTitle();
		View fb = a.getFloatingButton();
		super.setVisibility(VISIBLE);
		title.setVisibility(VISIBLE);
		fb.setVisibility(VISIBLE);
		clearFocus();
		hideTimer = new HideTimer(title, fb);
		FermataApplication.get().getHandler().postDelayed(hideTimer, 3000);
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			a.getMediaServiceBinder().unbind();
			a.getPrefs().removeBroadcastListener(this);
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (mask != MASK_VISIBLE) return;

		if (prefs.contains(MainActivityPrefs.HIDE_BARS)) {
			MainActivityDelegate a = getActivity();
			if (a.getPrefs().getHideBarsPref()) a.setBarsHidden(getVisibility() == VISIBLE);
			else if (a.isBarsHidden()) a.setBarsHidden(false);
			setShowHideBarsIcon(a);
		}
	}

	private void showHideBars(View v) {
		MainActivityDelegate a = getActivity();
		a.setBarsHidden(!a.isBarsHidden());
		setShowHideBarsIcon(a);
	}

	public void showMenu() {
		if (isActive()) showMenu(this);
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
	public boolean menuItemSelected(OverlayMenuItem item) {
		return true;
	}

	private final class MenuHandler extends MediaItemMenuHandler {

		public MenuHandler(OverlayMenu menu, Item item) {
			super(menu, item);
		}

		@Override
		protected void initPlayableMenu(MainActivityDelegate a, OverlayMenu menu, PlayableItem pi,
																		boolean initRepeat) {
			super.initPlayableMenu(a, menu, pi, false);
			BrowsableItemPrefs p = pi.getParent().getPrefs();
			MediaEngine eng = a.getMediaSessionCallback().getEngine();
			if (eng == null) return;

			boolean repeat = pi.isRepeatItemEnabled() || p.getRepeatPref();
			boolean shuffle = p.getShufflePref();
			OverlayMenuItem i;

			if (repeat) i = menu.findItem(R.id.repeat_disable);
			else i = menu.findItem(R.id.repeat_enable);

			i.setVisible(true);
			i.setTitle(getContext().getResources().getString(R.string.repeat));

			menu.findItem(R.id.shuffle).setVisible(!shuffle);
			menu.findItem(R.id.shuffle_disable).setVisible(shuffle);
			menu.findItem(R.id.audio_effects).setVisible(eng.getAudioEffects() != null);
		}

		@Override
		protected void initVideoMenu(OverlayMenu menu, Item item) {
			super.initVideoMenu(menu, item);

			MediaEngine eng = getActivity().getMediaSessionCallback().getEngine();
			if (eng == null) return;
			PlayableItem pi = (PlayableItem) item;

			if (pi.isVideo()) {
				if (eng.isAudioDelaySupported()) menu.findItem(R.id.audio_delay).setVisible(true);
				if (eng.isSubtitleDelaySupported()) menu.findItem(R.id.subtitle_delay).setVisible(true);
				if (eng.getAudioStreamInfo().size() > 1) menu.findItem(R.id.select_audio).setVisible(true);
				if (!eng.getSubtitleStreamInfo().isEmpty())
					menu.findItem(R.id.select_subtitles).setVisible(true);
			}
		}

		@Override
		public boolean menuItemSelected(OverlayMenuItem i) {
			int id = i.getItemId();
			OverlayMenu menu;
			PlayableItem pi;
			MediaEngine eng;

			switch (id) {
				case R.id.audio_effects:
					eng = getActivity().getMediaSessionCallback().getEngine();
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
				case R.id.select_audio:
					eng = getActivity().getMediaSessionCallback().getEngine();
					if (eng == null) return true;
					AudioStreamInfo ai = eng.getCurrentAudioStreamInfo();
					menu = i.getMenu();
					menu.setTitle(R.string.select_audio_stream);
					for (AudioStreamInfo s : eng.getAudioStreamInfo()) {
						menu.addItem(R.id.select_audio_stream, null, s.toString()).setData(s).setChecked(s.equals(ai));
					}
					menu.show(this);
					return true;
				case R.id.select_subtitles:
					eng = getActivity().getMediaSessionCallback().getEngine();
					if (eng == null) return true;
					SubtitleStreamInfo si = eng.getCurrentSubtitleStreamInfo();
					menu = i.getMenu();
					menu.setTitle(R.string.select_subtitles);
					for (SubtitleStreamInfo s : eng.getSubtitleStreamInfo()) {
						menu.addItem(R.id.select_subtitle_stream, null, s.toString()).setData(s).setChecked(s.equals(si));
					}
					menu.show(this);
					return true;
				case R.id.select_audio_stream:
					eng = getActivity().getMediaSessionCallback().getEngine();
					if (eng != null) {
						ai = i.getData();
						pi = (PlayableItem) getItem();

						if (ai.equals(eng.getCurrentAudioStreamInfo())) {
							pi.getPrefs().setAudioIdPref(null);
							eng.setCurrentAudioStream(null);
						} else {
							eng.setCurrentAudioStream(ai);
							pi.getPrefs().setSubIdPref(ai.getId());
						}
					}
					return true;
				case R.id.select_subtitle_stream:
					eng = getActivity().getMediaSessionCallback().getEngine();
					if (eng != null) {
						si = i.getData();
						pi = (PlayableItem) getItem();

						if (si.equals(eng.getCurrentSubtitleStreamInfo())) {
							pi.getPrefs().setSubIdPref(null);
							eng.setCurrentSubtitleStream(null);
						} else {
							eng.setCurrentSubtitleStream(si);
							pi.getPrefs().setSubIdPref(si.getId());
						}
					}
					return true;
				default:
					return super.menuItemSelected(i);
			}
		}
	}

	private final class SpeedMenuHandler implements OverlayMenu.SelectionHandler, OverlayMenu.CloseHandler {
		private PrefStore store;

		void show(OverlayMenu menu, Item item) {
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

			set.addToMenu(menu, true);
			menu.show(this, this);
		}

		@Override
		public boolean menuItemSelected(OverlayMenuItem item) {
			return true;
		}

		@Override
		public void menuClosed(OverlayMenu menu) {
			store.apply();
		}

		private class PrefStore extends BasicPreferenceStore {
			final Pref<BooleanSupplier> TRACK = Pref.b("TRACK", false);
			final Pref<BooleanSupplier> FOLDER = Pref.b("FOLDER", false);
			private final MediaSessionCallback cb = getActivity().getMediaServiceBinder().getMediaSessionCallback();
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

	private final class HideTimer implements Runnable {
		final View[] views;

		HideTimer(View... views) {
			this.views = views;
		}

		@Override
		public void run() {
			if ((hideTimer == this) && ((mask & MASK_VIDEO_MODE) != 0)) {
				ControlPanelView.super.setVisibility(GONE);

				for (View v : views) {
					v.setVisibility(GONE);
				}
			}
		}
	}
}
