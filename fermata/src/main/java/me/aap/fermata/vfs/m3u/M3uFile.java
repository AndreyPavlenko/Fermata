package me.aap.fermata.vfs.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.net.http.HttpFileDownloader.AGENT;
import static me.aap.utils.net.http.HttpFileDownloader.CHARSET;
import static me.aap.utils.net.http.HttpFileDownloader.ENCODING;
import static me.aap.utils.net.http.HttpFileDownloader.MAX_AGE;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;
import static me.aap.utils.text.TextUtils.isInt;
import static me.aap.utils.text.TextUtils.trim;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.AsyncInputStream;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.local.LocalFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class M3uFile implements VirtualFile {
	public static final int M3U_FILE_AGE = 24 * 60 * 60;
	public static final Pref<Supplier<String>> NAME = Pref.s("NAME");
	public static final Pref<Supplier<String>> URL = Pref.s("URL");
	public static final Pref<BooleanSupplier> VIDEO = Pref.b("VIDEO", false);
	private final Rid rid;
	private final PreferenceStore prefs;

	public M3uFile(Rid rid) {
		this.rid = rid;
		prefs = getPrefs(rid);
	}

	public PreferenceStore getPrefs() {
		return prefs;
	}

	@NonNull
	@Override
	public M3uFileSystem getVirtualFileSystem() {
		return M3uFileSystem.getInstance();
	}

	@NonNull
	@Override
	public String getName() {
		String name = getPrefs().getStringPref(NAME);
		return (name != null) ? name : getRid().toString();
	}

	public void setName(String name) {
		getPrefs().applyStringPref(NAME, trim(name));
	}

	public String getUrl() {
		return getPrefs().getStringPref(URL);
	}

	public void setUrl(String url) {
		getPrefs().applyStringPref(URL, trim(url));
	}

	public boolean isVideo() {
		return getPrefs().getBooleanPref(VIDEO);
	}

	public void setVideo(boolean video) {
		getPrefs().applyBooleanPref(VIDEO, video);
	}

	public int getMaxAge() {
		return getPrefs().getIntPref(MAX_AGE);
	}

	public void setMaxAge(int maxAge) {
		getPrefs().applyIntPref(MAX_AGE, maxAge);
	}

	public String getUserAgent() {
		return getPrefs().getStringPref(AGENT);
	}

	public void setUserAgent(String agent) {
		getPrefs().applyStringPref(AGENT, trim(agent));
	}

	public int getResponseTimeout() {
		return getPrefs().getIntPref(RESP_TIMEOUT);
	}

	public void setResponseTimeout(int timeout) {
		getPrefs().applyIntPref(RESP_TIMEOUT, timeout);
	}

	@NonNull
	@Override
	public Rid getRid() {
		return rid;
	}

	@Nullable
	public String getContentEncoding() {
		return getPrefs().getStringPref(ENCODING);
	}

	@Nullable
	public String getCharacterEncoding() {
		return getPrefs().getStringPref(CHARSET);
	}

	@Override
	public FutureSupplier<Long> getLength() {
		return completed(getLocalFile().length());
	}

	@Override
	public AsyncInputStream getInputStream() throws IOException {
		String url = getUrl();

		if (url.startsWith("content://")) {
			return AsyncInputStream.from(App.get().getContentResolver().openInputStream(Uri.parse(url)));
		}

		return LocalFileSystem.getInstance().getFile(getLocalFile()).getInputStream();
	}

	@NonNull
	@Override
	public File getLocalFile() {
		String url = getUrl();
		if ((url != null) && url.startsWith("/")) return new File(url);
		else return getCacheFile();
	}

	@Nullable
	@Override
	public RandomAccessChannel getChannel(String mode) {
		return LocalFileSystem.getInstance().getFile(getLocalFile()).getChannel(mode);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return rid.equals(((M3uFile) o).rid);
	}

	@Override
	public int hashCode() {
		return rid.hashCode();
	}

	@NonNull
	@Override
	public String toString() {
		return getClass().getSimpleName() + " {" + "rid=" + rid + ", name='" + getName() + '\'' +
				", url='" + getUrl() + '\'' + ", localFile=" + getLocalFile() + '}';
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		cleanUp();
		return completed(true);
	}

	protected String getId() {
		return getRid().getHost();
	}

	protected void cleanUp() {
		M3uPrefs m3u = (M3uPrefs) getPrefs();
		SharedPreferences prefs = m3u.getSharedPreferences();
		SharedPreferences.Editor edit = prefs.edit();
		edit.remove(rid.toString());
		for (String k : new ArrayList<>(prefs.getAll().keySet())) {
			if (k.startsWith(m3u.key)) edit.remove(k);
		}
		edit.apply();
		File f = getCacheFile();
		if (f.isFile() && !f.delete()) Log.e("Failed to delete cache file ", f);
	}

	protected File getCacheDir() {
		App app = App.get();
		File cache = app.getExternalCacheDir();
		if (cache == null) cache = app.getCacheDir();
		return new File(cache, getVirtualFileSystem().getScheme());
	}

	private PreferenceStore getPrefs(Rid rid) {
		String name = getVirtualFileSystem().getScheme();
		SharedPreferences prefs = App.get().getSharedPreferences(name, Context.MODE_PRIVATE);
		String host = rid.getHost();
		String key = (isInt(host) ? host : rid.toString()) + '#';
		return new M3uPrefs(key, prefs);
	}

	private File getCacheFile() {
		File cacheDir = getCacheDir();
		//noinspection ResultOfMethodCallIgnored
		cacheDir.mkdirs();
		return new File(cacheDir, getId() + ".m3u");
	}

	protected static class M3uPrefs implements SharedPreferenceStore {
		private final String key;
		private final SharedPreferences prefs;
		private Collection<ListenerRef<Listener>> listeners;

		@SuppressWarnings("CopyConstructorMissesField")
		protected M3uPrefs(M3uPrefs prefs) {
			this(prefs.key, prefs.prefs);
		}

		protected M3uPrefs(String key, SharedPreferences prefs) {
			this.key = key;
			this.prefs = prefs;
		}

		@NonNull
		@Override
		public SharedPreferences getSharedPreferences() {
			return prefs;
		}

		@Override
		public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
			return (listeners != null) ? listeners : (listeners = new LinkedList<>());
		}

		@Override
		public String getPreferenceKey(Pref<?> pref) {
			return key + pref.getName();
		}
	}
}
