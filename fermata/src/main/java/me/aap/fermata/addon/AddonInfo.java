package me.aap.fermata.addon;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class AddonInfo {
	public final String moduleName;
	public final String className;
	@StringRes
	public final int addonName;
	@DrawableRes
	public final int icon;
	public final PreferenceStore.Pref<BooleanSupplier> enabledPref;

	public AddonInfo(String moduleName, String className, int addonName, int icon) {
		this.moduleName = moduleName;
		this.className = className;
		this.addonName = addonName;
		this.icon = icon;
		enabledPref = PreferenceStore.Pref.b(className + "_enabled", this::isInstalled);
	}

	public boolean isInstalled() {
		try {
			Class.forName(className);
			return true;
		} catch (Exception ignore) {
			return false;
		}
	}
}
