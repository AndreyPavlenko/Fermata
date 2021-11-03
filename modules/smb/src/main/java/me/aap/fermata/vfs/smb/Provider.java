package me.aap.fermata.vfs.smb;

import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

import android.content.Context;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.vfs.VfsProviderBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.smb.SmbFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class Provider extends VfsProviderBase {
	private final Pref<Supplier<String>> HOST = Pref.s("HOST");
	private final Pref<IntSupplier> PORT = Pref.i("PORT", 445);
	private final Pref<Supplier<String>> SHARE = Pref.s("SHARE");
	private final Pref<Supplier<String>> DOMAIN = Pref.s("DOMAIN");
	private final Pref<Supplier<String>> USER = Pref.s("USER");
	private final Pref<Supplier<String>> PASSWD = Pref.s("PASSWD");

	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		return SmbFileSystem.Provider.getInstance().createFileSystem(ps);
	}

	@Override
	protected FutureSupplier<? extends VirtualFolder> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceStore ps = PrefsHolder.instance;

		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = HOST;
			o.title = me.aap.fermata.R.string.host;
			o.stringHint = "localhost";
		});
		prefs.addIntPref(o -> {
			o.store = ps;
			o.pref = PORT;
			o.title = me.aap.fermata.R.string.port;
			o.showProgress = false;
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = SHARE;
			o.title = me.aap.fermata.R.string.share_name;
			o.stringHint = "share";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = DOMAIN;
			o.title = me.aap.fermata.R.string.domain;
			o.stringHint = "WORKGROUP";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = USER;
			o.title = me.aap.fermata.R.string.username;
			o.stringHint = "Guest";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = PASSWD;
			o.title = me.aap.fermata.R.string.password;
			o.stringHint = "secret";
		});

		return requestPrefs(a, prefs, ps).thenRun(ps::removeBroadcastListeners).then(ok -> {
			if (!ok) return completedNull();

			String user = ps.getStringPref(USER);

			if (user != null) {
				String domain = ps.getStringPref(DOMAIN);
				if (domain != null) user = domain + ';' + user;
			}

			return ((SmbFileSystem) fs).addRoot(
					user,
					ps.getStringPref(HOST),
					ps.getIntPref(PORT),
					'/' + ps.getStringPref(SHARE),
					ps.getStringPref(PASSWD),
					null, null);
		});
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualResource folder) {
		((SmbFileSystem) fs).removeRoot((VirtualFolder) folder);
		return completedVoid();
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		return allSet(ps, HOST, SHARE);
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
