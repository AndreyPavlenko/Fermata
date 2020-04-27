package me.aap.fermata.vfs.sftp;

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
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.sftp.SftpFileSystem;

import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
public class Provider extends VfsProviderBase {
	private final Pref<Supplier<String>> HOST = Pref.s("HOST");
	private final Pref<IntSupplier> PORT = Pref.i("PORT", 22);
	private final Pref<Supplier<String>> PATH = Pref.s("PATH");
	private final Pref<Supplier<String>> USER = Pref.s("USER");
	private final Pref<Supplier<String>> PASSWD = Pref.s("PASSWD");
	private final Pref<Supplier<String>> KEY = Pref.s("KEY");
	private final Pref<Supplier<String>> KEY_PASSWD = Pref.s("KEY_PASSWD");

	@Override
	public FutureSupplier<VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		return SftpFileSystem.Provider.getInstance().createFileSystem(ps);
	}

	@Override
	protected FutureSupplier<VirtualFolder> addFolder(MainActivityDelegate a, VirtualFileSystem fs) {
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
			o.pref = PATH;
			o.title = me.aap.fermata.R.string.path;
			o.stringHint = "/home/user";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = USER;
			o.stringHint = "root";
			o.title = me.aap.fermata.R.string.username;
		});
		prefs.addFilePref(o -> {
			o.store = ps;
			o.pref = KEY;
			o.title = me.aap.fermata.R.string.private_key;
			o.mode = FilePickerFragment.FILE;
			o.stringHint = "/sdcard/.ssh/id_rsa";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = PASSWD;
			o.title = me.aap.fermata.R.string.password;
			o.stringHint = "secret";
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = KEY_PASSWD;
			o.title = me.aap.fermata.R.string.private_key_pass;
			o.stringHint = "secret";
		});

		return requestPrefs(a, prefs, ps).then(ok -> !ok ? completedNull() : ((SftpFileSystem) fs).addRoot(
				ps.getStringPref(USER),
				ps.getStringPref(HOST),
				ps.getIntPref(PORT),
				ps.getStringPref(PATH),
				ps.getStringPref(PASSWD),
				ps.getStringPref(KEY),
				ps.getStringPref(KEY_PASSWD)));
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualFolder folder) {
		((SftpFileSystem) fs).removeRoot(folder);
		return completedVoid();
	}

	@Override
	protected boolean validate(PreferenceStore ps) {
		return allSet(ps, HOST, USER) && (anySet(ps, KEY, PASSWD));
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
