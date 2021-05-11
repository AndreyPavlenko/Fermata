package me.aap.fermata.vfs.m3u;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.collection.LruMap;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.AsyncInputStream;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;
import me.aap.utils.net.http.HttpException;
import me.aap.utils.net.http.HttpStatusCode;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.resource.Rid;
import me.aap.utils.security.SecurityUtils;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.local.LocalFileSystem;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

/**
 * @author Andrey Pavlenko
 */
public class M3uFileSystem implements VirtualFileSystem {
	public static final String SCHEME_M3U = "m3u";
	static final Pref<Supplier<String>> NAME = Pref.s("NAME");
	static final Pref<Supplier<String>> URL = Pref.s("URL");
	static final Pref<BooleanSupplier> VIDEO = Pref.b("VIDEO", false);
	static final Pref<Supplier<String>> AGENT = Pref.s("AGENT");
	static final Pref<Supplier<String>> ETAG = Pref.s("ETAG");
	static final Pref<Supplier<String>> CHARSET = Pref.s("CHARSET", "UTF-8");
	static final Pref<Supplier<String>> ENCODING = Pref.s("ENCODING");
	private static final M3uFileSystem fs = new M3uFileSystem();
	private final Map<Rid, FutureSupplier<VirtualResource>> cache = new LruMap<>(10);

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
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		return load(rid, true);
	}

	public Rid toRid(String name, String url) {
		return toRid(SecurityUtils.sha1String(name, url));
	}

	public Rid toRid(String id) {
		return Rid.create(SCHEME_M3U, null, id, -1, null);
	}

	public String toId(Rid rid) {
		return rid.getHost();
	}

	public FutureSupplier<VirtualResource> reload(Rid rid) {
		return load(rid, false);
	}

	public boolean isVideo(Rid rid) {
		return getM3uPrefs(rid).getBooleanPref(VIDEO);
	}

	public String getUserAgent(Rid rid) {
		return getM3uPrefs(rid).getStringPref(AGENT);
	}

	private static FutureSupplier<VirtualResource> load(Rid rid, boolean useCached) {
		M3uFileSystem fs = getInstance();
		Promise<VirtualResource> p = new Promise<>();

		synchronized (fs.cache) {
			if (useCached) {
				FutureSupplier<VirtualResource> cached = CollectionUtils.putIfAbsent(fs.cache, rid, p);

				if (cached != null) {
					if (cached.isFailed()) fs.cache.put(rid, p);
					else return cached;
				}
			} else {
				fs.cache.put(rid, p);
			}
		}

		SharedPreferenceStore prefs = getM3uPrefs(rid);
		String url = prefs.getStringPref(URL);

		if (url == null) {
			Log.d("Playlist URL not found: ", rid);
			return completedNull();
		}

		String name = prefs.getStringPref(NAME);

		if (name == null) {
			Log.d("Playlist name not found: ", rid);
			return completedNull();
		}

		File cacheFile = getCacheFile(rid);
		VirtualFile local = LocalFileSystem.getInstance().getFile(cacheFile);
		M3uHttpFile m3u = new M3uHttpFile(rid, name, local);

		HttpConnection.connect(o -> {
			o.url(url);
			o.responseTimeout = 10;
			o.userAgent = prefs.getStringPref(AGENT);
			if (cacheFile.isFile()) o.ifNonMatch = prefs.getStringPref(ETAG);
		}, (resp, err) -> {
			if (err != null) {
				if (cacheFile.isFile()) {
					Log.e(err, "Failed to load playlist: ", url, ". Loading from cache: ", cacheFile);
					p.complete(m3u);
				} else {
					p.completeExceptionally(err);
				}

				return failed(err);
			}

			if (resp.getStatusCode() == HttpStatusCode.NOT_MODIFIED) {
				Log.i("Playlist not modified: ", url, ". Loading from cache: ", cacheFile);
				p.complete(m3u);
				return completedVoid();
			}

			CharSequence s = resp.getContentEncoding();

			if (s != null) {
				if (!TextUtils.equals("gzip", s) && !TextUtils.equals("deflate", s)) {
					p.completeExceptionally(new HttpException("Unsupported content encoding: " + s));
					return completedVoid();
				}

				prefs.applyStringPref(ENCODING, s.toString());
			}

			s = resp.getCharset();
			if (s != null) prefs.applyStringPref(CHARSET, s.toString().toUpperCase());

			s = resp.getEtag();
			if (s != null) prefs.applyStringPref(ETAG, s.toString());

			File cacheFileTmp = new File(cacheFile.getAbsolutePath() + ".tmp");
			return resp.writePayload(cacheFileTmp).onCompletion((v, fail) -> {
				if (fail != null) {
					if (cacheFile.isFile()) {
						Log.e(fail, "Failed to load playlist: ", url, ". Loading from cache: ", cacheFile);
						p.complete(m3u);
					} else {
						p.completeExceptionally(fail);
					}

					//noinspection ResultOfMethodCallIgnored
					cacheFileTmp.delete();
				} else if (cacheFileTmp.renameTo(cacheFile)) {
					p.complete(m3u);
				} else {
					Log.e("Failed to rename file ", cacheFileTmp, " to ", cacheFile);
					p.complete(new M3uHttpFile(rid, name, LocalFileSystem.getInstance().getFile(cacheFileTmp)));
				}
			});
		});

		return p;
	}

	static SharedPreferenceStore getM3uPrefs(Rid rid) {
		SharedPreferences prefs = App.get().getSharedPreferences("m3u", Context.MODE_PRIVATE);

		return new SharedPreferenceStore() {
			private Collection<ListenerRef<Listener>> listeners;

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
				return rid + "#" + pref.getName();
			}
		};
	}

	static File getCacheFile(Rid rid) {
		File cache = App.get().getExternalCacheDir();
		File cacheDir = new File(cache, "m3u");
		//noinspection ResultOfMethodCallIgnored
		cacheDir.mkdirs();
		return new File(cacheDir, rid.getHost() + ".m3u");
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

	private static final class M3uHttpFile implements VirtualFile {
		private final Rid rid;
		private final String name;
		private final VirtualFile localFile;

		private M3uHttpFile(Rid rid, String name, VirtualFile localFile) {
			this.rid = rid;
			this.name = name;
			this.localFile = localFile;
		}

		@NonNull
		@Override
		public VirtualFileSystem getVirtualFileSystem() {
			return fs;
		}

		@NonNull
		@Override
		public String getName() {
			return name;
		}

		@NonNull
		@Override
		public Rid getRid() {
			return rid;
		}

		@Nullable
		@Override
		public String getContentEncoding() {
			return getM3uPrefs(rid).getStringPref(ENCODING);
		}

		@Nullable
		@Override
		public String getCharacterEncoding() {
			return getM3uPrefs(rid).getStringPref(CHARSET);
		}

		@Override
		public FutureSupplier<Long> getLength() {
			return localFile.getLength();
		}

		@Override
		public AsyncInputStream getInputStream() throws IOException {
			return localFile.getInputStream();
		}

		@Nullable
		@Override
		public RandomAccessChannel getChannel() {
			return localFile.getChannel();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return rid.equals(((M3uHttpFile) o).rid);
		}

		@Override
		public int hashCode() {
			return rid.hashCode();
		}

		@Override
		public String toString() {
			return "M3uHttpFile{" +
					"rid=" + rid +
					", name='" + name + '\'' +
					", url='" + getM3uPrefs(rid).getStringPref(URL) + '\'' +
					", localFile=" + localFile +
					'}';
		}
	}
}
