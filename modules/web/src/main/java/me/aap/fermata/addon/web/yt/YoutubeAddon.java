package me.aap.fermata.addon.web.yt;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeAddon extends WebBrowserAddon {
	private static final Pref<BooleanSupplier> YT_FORCE_DARK = Pref.b("YT_FORCE_DARK", false);
	private static final Pref<BooleanSupplier> YT_DESKTOP_VERSION = Pref.b("YT_DESKTOP_VERSION", false);
	private static final Pref<Supplier<String>> VIDEO_SCALE = Pref.s("VIDEO_SCALE", VideoScale.CONTAIN::prefName);

	@Override
	public int getNavId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@Nullable
	@Override
	public ActivityFragment createFragment(int id) {
		return (id == me.aap.fermata.R.id.youtube_fragment) ? new YoutubeFragment() : null;
	}

	@Override
	public Pref<BooleanSupplier> getForceDarkPref() {
		return YT_FORCE_DARK;
	}

	@Override
	public Pref<BooleanSupplier> getDesktopVersionPref() {
		return YT_DESKTOP_VERSION;
	}

	VideoScale getScale() {
		switch (getPreferenceStore().getStringPref(VIDEO_SCALE)) {
			case "fill":
				return VideoScale.FILL;
			case "contain":
				return VideoScale.CONTAIN;
			case "cover":
				return VideoScale.COVER;
			default:
				return VideoScale.NONE;
		}
	}

	void setScale(VideoScale scale) {
		getPreferenceStore().applyStringPref(VIDEO_SCALE, scale.prefName());
	}

	enum VideoScale {
		FILL, CONTAIN, COVER, NONE;

		String prefName() {
			return name().toLowerCase();
		}
	}
}
