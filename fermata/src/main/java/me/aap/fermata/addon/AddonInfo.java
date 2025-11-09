package me.aap.fermata.addon;

import static java.util.Arrays.asList;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import me.aap.utils.collection.CollectionUtils;
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
	public final int order;
	public final boolean hasSettings;
	public String[] depends;
	public final PreferenceStore.Pref<BooleanSupplier> enabledPref;

	public AddonInfo(String moduleName, String className, int addonName, int icon,
									 Integer order, Boolean hasSettings, Boolean enabled, String depends) {
		this.moduleName = moduleName;
		this.className = className;
		this.addonName = addonName;
		this.icon = icon;
		this.order = (order == null) ? 1000 : order;
		this.hasSettings = (hasSettings == null) || hasSettings;
		this.depends = CollectionUtils.filter(asList(depends.split("[, \\[\\]]")), s -> !s.isEmpty())
				.toArray(new String[0]);
		enabledPref = PreferenceStore.Pref.b(className + "_enabled",
				(enabled == null) ? this::isInstalled : () -> enabled);
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
		var cmp = Integer.compare(order, ai.order);
		return cmp == 0 ? moduleName.compareTo(ai.moduleName) : cmp;
	}
}
