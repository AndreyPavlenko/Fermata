package me.aap.fermata.vfs.m3u;

import static me.aap.fermata.util.Utils.createDownloader;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class M3uFileSystem implements VirtualFileSystem {
	public static final String SCHEME_M3U = "m3u";
	private static final M3uFileSystem fs = new M3uFileSystem();

	public static M3uFileSystem getInstance() {
		return fs;
	}

	@NonNull
	@Override
	public Provider getProvider() {
		return Provider.getInstance();
	}

	@NonNull
	@Override
	public FutureSupplier<? extends M3uFile> getResource(Rid rid) {
		return load(createM3uFile(rid));
	}

	public String getScheme() {
		return SCHEME_M3U;
	}

	public Rid toRid(String id) {
		return Rid.create(getScheme(), null, id, -1, null);
	}

	public String toId(Rid rid) {
		return rid.getHost();
	}

	protected synchronized M3uFile newFile() {
		String name = getScheme();
		SharedPreferences prefs = App.get().getSharedPreferences(name, Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = prefs.edit();
		int id = prefs.getInt("ID_COUNTER", 0) + 1;
		Rid rid = Rid.create(name, null, String.valueOf(id), -1, null);
		edit.putInt("ID_COUNTER", id);
		edit.apply();
		return createM3uFile(rid);
	}

	protected M3uFile createM3uFile(Rid rid) {
		return new M3uFile(rid);
	}

	protected FutureSupplier<M3uFile> load(M3uFile file) {
		Promise<M3uFile> p = new Promise<>();
		String url = file.getUrl();

		if (url == null) {
			Log.d("Not an m3u file: ", file);
			return completedNull();
		}

		if (url.startsWith("/") || url.startsWith("content://")) {
			p.complete(file);
			return p;
		}

		File cacheFile = file.getLocalFile();
		Context ctx = App.get();
		HttpFileDownloader d = createDownloader(ctx,url);
		d.setReturnExistingOnFail(true);
		d.download(url, cacheFile, file.getPrefs()).onCompletion((f, err) -> {
			if (err == null) {
				p.complete(file);
			} else {
				p.completeExceptionally(err);
			}
		});

		return p;
	}

	public static final class Provider implements VirtualFileSystem.Provider {
		private static final Provider instance = new Provider();

		public static Provider getInstance() {
			return instance;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return Collections.singleton(SCHEME_M3U);
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(fs);
		}
	}
}
