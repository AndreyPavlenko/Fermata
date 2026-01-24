package me.aap.utils.net.http;

import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.net.http.HttpHeader.USER_AGENT;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class HttpFileDownloader {
	public static final Pref<Supplier<String>> AGENT = Pref.s("AGENT", USER_AGENT::getDefaultValue);
	public static final Pref<Supplier<String>> ETAG = Pref.s("ETAG");
	public static final Pref<Supplier<String>> CHARSET = Pref.s("CHARSET", "UTF-8");
	public static final Pref<Supplier<String>> ENCODING = Pref.s("ENCODING");
	public static final Pref<IntSupplier> RESP_TIMEOUT = Pref.i("RESP_TIMEOUT", 10);
	public static final Pref<LongSupplier> TIMESTAMP = Pref.l("TIMESTAMP", 0);
	public static final Pref<IntSupplier> MAX_AGE = Pref.i("MAX_AGE", 0);
	private StatusListener statusListener;
	private boolean returnExistingOnFail;

	public void setStatusListener(StatusListener statusListener) {
		this.statusListener = statusListener;
	}

	public void setReturnExistingOnFail(boolean returnExistingOnFail) {
		this.returnExistingOnFail = returnExistingOnFail;
	}

	public FutureSupplier<Status> download(String src, File dst) {
		return download(src, dst, new BasicPreferenceStore());
	}

	public FutureSupplier<Status> download(String src, File dst, PreferenceStore prefs) {
		try {
			return download(new URL(src), dst, prefs);
		} catch (MalformedURLException ex) {
			return failed(ex);
		}
	}

	public FutureSupplier<Status> download(URL src, File dst, PreferenceStore prefs) {
		boolean exist = dst.isFile();
		Promise<Status> p = new Promise<>();
		StatusListener listener = statusListener;
		Log.d("Downloading ", src, " to ", dst);

		if (exist) {
			long stamp = prefs.getLongPref(TIMESTAMP);
			int age = prefs.getIntPref(MAX_AGE);

			if ((stamp + (age * 1000L)) > System.currentTimeMillis()) {
				DownloadStatus status = new DownloadStatus(src, dst, dst.length());
				status.setEtag(prefs.getStringPref(ETAG));
				status.setCharset(prefs.getStringPref(CHARSET));
				status.setEncoding(prefs.getStringPref(ENCODING));
				Log.d("File age is less than ", age, ". Returning existing file: ", dst);
				if (listener != null) listener.onSuccess(status);
				p.complete(status);
				return p;
			}
		}

		if (!exist) {
			File dir = dst.getParentFile();
			if (dir == null) return failed(new IOException("Unable to create file: " + dst));
			try {
				FileUtils.mkdirs(dir);
			} catch (IOException ex) {
				return failed(ex);
			}
		}

		var o = new HttpConnection.Opts();
		o.url = src;
		o.responseTimeout = prefs.getIntPref(RESP_TIMEOUT);
		o.userAgent = prefs.getStringPref(AGENT);
		if (exist) o.ifNonMatch = prefs.getStringPref(ETAG);
		HttpConnection.connect(o, (resp, err) -> {
			if (err != null) {
				completeExceptionally(p, err, new DownloadStatus(src, dst, 0), listener);
				return p;
			}

			var enc = resp.getContentEncoding();
			if (enc == null) {
				var path = o.url.getPath();
				if ((path != null) && (path.endsWith(".gzip") || path.endsWith(".gz"))) enc = "gzip";
			}
			var status = new DownloadStatus(src, dst, resp.getContentLength());
			status.setEtag(resp.getEtag());
			status.setCharset(resp.getCharset());
			status.setEncoding(enc);
			Log.d("Response received:\n", resp);

			if (resp.getStatusCode() == HttpStatusCode.NOT_MODIFIED) {
				Log.d("File not modified: ", src, ". Returning existing file: ", dst);
				if (listener != null) listener.onSuccess(status);
				p.complete(status);
				return completedVoid();
			}

			File tmp = null;
			try {
				tmp = File.createTempFile(dst.getName(), ".incomplete", dst.getParentFile());
			} catch (IOException ex) {
				Log.e(ex, "Failed to create temporary file");
			}

			File incomplete = (tmp == null) ? new File(dst.getAbsolutePath() + ".incomplete") : tmp;
			return writePayload(resp, incomplete, status, listener).onCompletion((v, fail) -> {
				if (fail != null) {
					completeExceptionally(p, fail, status, listener);
					//noinspection ResultOfMethodCallIgnored
					incomplete.delete();
				} else if (incomplete.renameTo(dst)) {
					if (status.getContentEncoding() == null) {
						try (var in = new FileInputStream(dst)) {
							if ((in.read() == 0x1F) && (in.read() == 0x8B)) status.setEncoding("gzip");
						} catch (IOException ex) {
							Log.e(ex, "Failed to read file: ", dst);
						}
					}
					try (PreferenceStore.Edit edit = prefs.editPreferenceStore()) {
						edit.setStringPref(ETAG, status.getEtag());
						edit.setStringPref(CHARSET, status.getCharacterEncoding());
						edit.setStringPref(ENCODING, status.getContentEncoding());
						edit.setLongPref(TIMESTAMP, System.currentTimeMillis());
					}

					Log.d("Downloaded ", src, " to ", dst);
					if (listener != null) listener.onSuccess(status);
					p.complete(status);
				} else {
					completeExceptionally(p, new IOException("Failed to rename file " + incomplete + " to " + dst),
							status, listener);
					//noinspection ResultOfMethodCallIgnored
					incomplete.delete();
				}
			});
		});

		return p;
	}

	private void completeExceptionally(Promise<Status> p, Throwable err, DownloadStatus status, StatusListener listener) {
		Log.e("Failed to download ", status.getUrl(), " to ", status.getLocalFile());

		if (returnExistingOnFail && status.getLocalFile().isFile()) {
			Log.e(err, "Failed to download: ", status.getUrl(), ". Returning existing file: ",
					status.getLocalFile());
			status.failure = err;
			if (listener != null) listener.onSuccess(status);
			p.complete(status);
		} else {
			if (listener != null) listener.onFailure(status);
			p.completeExceptionally(err);
		}
	}

	private FutureSupplier<?> writePayload(HttpResponse resp, File dst, DownloadStatus status, StatusListener listener) {
		try {
			OutputStream out = new OutputStream() {
				final OutputStream fos = new FileOutputStream(dst);

				@Override
				public void write(int b) throws IOException {
					fos.write(b);
					status.bytesDownloaded += 1;
					if (listener != null) listener.onProgress(status);
				}

				@Override
				public void write(byte[] b, int off, int len) throws IOException {
					fos.write(b, off, len);
					status.bytesDownloaded += len;
					if (listener != null) listener.onProgress(status);
				}

				@Override
				public void flush() throws IOException {
					fos.flush();
				}

				@Override
				public void close() throws IOException {
					fos.close();
				}
			};
			return resp.writePayload(out).thenRun(() -> IoUtils.close(out));
		} catch (FileNotFoundException ex) {
			return failed(ex);
		}
	}

	public interface Status extends VirtualFile.Info {

		URL getUrl();

		String getEtag();

		Throwable getFailure();

		long bytesDownloaded();

		default InputStream getFileStream(boolean decode) throws IOException {
			InputStream in = new FileInputStream(getLocalFile());

			if (decode) {
				String enc = getContentEncoding();

				if (enc != null) {
					if ("gzip".equals(enc)) {
						return new GZIPInputStream(in);
					} else if ("deflate".equals(enc)) {
						return new InflaterInputStream(in);
					} else {
						throw new IOException("Unsupported encoding: " + enc);
					}
				}
			}

			return in;
		}
	}

	public interface StatusListener {

		void onProgress(Status status);

		void onSuccess(Status status);

		void onFailure(Status status);
	}

	private static final class DownloadStatus implements Status {
		private final URL url;
		private final File file;
		private final long len;
		String etag;
		String charset;
		String encoding;
		long bytesDownloaded;
		Throwable failure;

		public DownloadStatus(URL url, File file, long len) {
			this.url = url;
			this.file = file;
			this.len = len;
		}

		@NonNull
		@Override
		public URL getUrl() {
			return url;
		}

		@NonNull
		@Override
		public File getLocalFile() {
			return file;
		}

		@Override
		public long getLength() {
			return len;
		}

		@Override
		public long bytesDownloaded() {
			return bytesDownloaded;
		}

		@Override
		public String getEtag() {
			return etag;
		}

		public void setEtag(CharSequence etag) {
			if (etag != null) this.etag = etag.toString();
		}

		@Override
		public String getCharacterEncoding() {
			return charset;
		}

		public void setCharset(CharSequence charset) {
			if (charset != null) this.charset = charset.toString().toUpperCase();
		}

		@Override
		public String getContentEncoding() {
			return encoding;
		}

		public void setEncoding(CharSequence encoding) {
			if (encoding != null) this.encoding = encoding.toString();
		}

		@Override
		public Throwable getFailure() {
			return failure;
		}

		@NonNull
		@Override
		public String toString() {
			return "DownloadStatus {" +
					"\n  source=" + url +
					"\n  destination=" + file +
					"\n  length=" + len +
					"\n  etag='" + etag + '\'' +
					"\n  charset='" + charset + '\'' +
					"\n  encoding='" + encoding + '\'' +
					"\n  bytesDownloaded=" + bytesDownloaded +
					"\n  failure=" + failure +
					"\n}";
		}
	}
}
