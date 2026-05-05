package me.aap.fermata.addon.poi;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.fragment.FilePickerFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class PoiAddon implements FermataAddon {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(PoiAddon.class.getName());
	private static final Pref<Supplier<String>> POI_DB_URL = Pref.s("POI_DB_URL", "");
	private FutureSupplier<Voyageur> voyageur;

	public PoiAddon() {
		var main = MainActivity.getActiveInstance();
		if (main != null) main.checkPermissions(ACCESS_FINE_LOCATION);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.poi_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	public String getDbUrl() {
		return FermataApplication.get().getPreferenceStore().getStringPref(POI_DB_URL);
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore ps, PreferenceSet set,
																 ChangeableCondition visibility) {
		set.addFilePref(o -> {
			o.store = ps;
			o.pref = POI_DB_URL;
			o.title = R.string.poi_file_or_url;
			o.mode = FilePickerFragment.FILE;
			o.visibility = visibility;
		});
	}

	@Override
	public void start() {
		stop();
		if (!getDbUrl().isEmpty()) {
			voyageur = Voyageur.start(App.get());
		}
	}

	@Override
	public void stop() {
		if (voyageur != null) {
			voyageur.onSuccess(Voyageur::stop);
			voyageur = null;
		}
	}
}
