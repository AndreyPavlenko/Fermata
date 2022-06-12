package me.aap.fermata.addon;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class AddonInfo implements Comparable<AddonInfo> {
	public final String moduleName;
	public final String className;
	@StringRes
	public final int addonName;
	@DrawableRes
	public final int icon;
	public final PreferenceStore.Pref<BooleanSupplier> enabledPref;
	public final int order;

	public AddonInfo(String moduleName, String className, int addonName, int icon,
									 Integer order, Boolean enabled) {
		this.moduleName = moduleName;
		this.className = className;
		this.addonName = addonName;
		this.icon = icon;
		this.order = (order == null) ? 0 : order;
		enabledPref = PreferenceStore.Pref.b(className + "_enabled",
				(enabled == null) ? this::isInstalled : () -> enabled);
	}

	public String getModuleName() {
		return moduleName;
	}

	public boolean isInstalled() {
		try {
			Class.forName(className);
			return true;
		} catch (Exception ignore) {
			return false;
		}
	}

	@Override
	public int compareTo(AddonInfo ai) {
		return Integer.compare(order, ai.order);
	}
}
