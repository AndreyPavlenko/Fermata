package me.aap.fermata.vfs.m3u;

import android.content.Context;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.vfs.VfsProviderBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.fermata.vfs.m3u.M3uFileSystem.CHARSET;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.ENCODING;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.ETAG;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.NAME;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.URL;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.VIDEO;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.getCacheFile;
import static me.aap.fermata.vfs.m3u.M3uFileSystem.getM3uPrefs;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
public class M3uFileSystemProvider extends VfsProviderBase {

	@Override
	public FutureSupplier<? extends VirtualFileSystem> createFileSystem(
			Context ctx, Supplier<FutureSupplier<? extends AppActivity>> activitySupplier, PreferenceStore ps) {
		return M3uFileSystem.Provider.getInstance().createFileSystem(ps);
	}

	@Override
	public FutureSupplier<? extends VirtualResource> select(MainActivityDelegate a, List<? extends VirtualFileSystem> fs) {
		PreferenceSet prefs = new PreferenceSet();
		PreferenceStore ps = PrefsHolder.instance;
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = NAME;
			o.title = R.string.m3u_playlist_name;
		});
		prefs.addStringPref(o -> {
			o.store = ps;
			o.pref = URL;
			o.title = R.string.m3u_playlist_url;
			o.stringHint = "http://example.com/playlist.m3u";
		});
		prefs.addBooleanPref(o -> {
			o.store = ps;
			o.pref = VIDEO;
			o.title = R.string.m3u_playlist_video;
		});

		return requestPrefs(a, prefs, ps).then(ok -> {
			if (!ok) return completedNull();
			return load(ps.getStringPref(NAME), ps.getStringPref(URL), ps.getBooleanPref(VIDEO));
		});
	}

	@Override
	protected FutureSupplier<Void> removeFolder(MainActivityDelegate a, VirtualFileSystem fs, VirtualResource folder) {
		removePlaylist(folder.getRid());
		return completedVoid();
	}

	@Override
	@SuppressWarnings("unchecked")
	protected boolean validate(PreferenceStore ps) {
		if(!allSet(ps, NAME, URL)) return false;
		String u = ps.getStringPref(URL);
		return u.startsWith("http://") || u.startsWith("https://");
	}

	@Override
	protected boolean addRemoveSupported() {
		return false;
	}

	private static FutureSupplier<VirtualResource> load(String name, String url, boolean video) {
		M3uFileSystem fs = M3uFileSystem.getInstance();
		Rid rid = fs.toRid(name, url);
		SharedPreferenceStore prefs = getM3uPrefs(rid);

		try (PreferenceStore.Edit e = prefs.editPreferenceStore(true)) {
			e.setStringPref(NAME, name);
			e.setStringPref(URL, url);
			e.setBooleanPref(VIDEO, video);
		}

		return fs.getResource(rid).onCompletion((r, err) -> {
			if (r == null) {
				if (err != null) Log.e(err, "Failed to load playlist: ", url);
				else Log.e("Playlist URL not found", url);
				removePlaylist(rid);
			}
		});
	}

	private static void removePlaylist(Rid rid) {
		Log.d("Removing playlist ", rid);
		SharedPreferenceStore prefs = getM3uPrefs(rid);

		try (PreferenceStore.Edit e = prefs.editPreferenceStore(true)) {
			e.removePref(NAME);
			e.removePref(URL);
			e.removePref(VIDEO);
			e.removePref(ETAG);
			e.removePref(CHARSET);
			e.removePref(ENCODING);
		}

		//noinspection ResultOfMethodCallIgnored
		getCacheFile(rid).delete();
	}

	private static final class PrefsHolder extends BasicPreferenceStore {
		static final PrefsHolder instance = new PrefsHolder();
	}
}
