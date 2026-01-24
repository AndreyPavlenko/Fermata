package me.aap.utils.vfs.gdrive;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.io.AsyncInputStream.readInputStream;

import androidx.annotation.NonNull;

import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;

import java.io.InputStream;
import java.nio.ByteBuffer;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.AsyncInputStream;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.net.ByteBufferSupplier;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFolder;

/**
 * @author Andrey Pavlenko
 */
class GdriveFile extends GdriveResource implements VirtualFile {
	private FutureSupplier<Long> length;

	GdriveFile(GdriveFileSystem fs, String id, String name) {
		super(fs, id, name);
	}

	GdriveFile(GdriveFileSystem fs, String id, String name, VirtualFolder parent) {
		super(fs, id, name, parent);
	}

	@Override
	public FutureSupplier<Long> getLength() {
		if (length != null) return length;
		return length = fs.useDrive(d -> d.files().get(id).setFields("size").execute().getSize())
				.onSuccess(len -> length = completed(len));
	}

	@Override
	public AsyncInputStream getInputStream(long offset) {
		return new AsyncInputStream() {
			long pos = offset;
			InputStream stream;

			@Override
			public FutureSupplier<ByteBuffer> read(ByteBufferSupplier dst) {
				InputStream in = stream;

				if (in != null) {
					FutureSupplier<ByteBuffer> r = readInputStream(in, dst.getByteBuffer(), getInputBufferLen());

					if (!r.isFailed()) {
						pos += r.getOrThrow().remaining();
						return r;
					}
				}

				return fs.useDrive(d -> {
					Drive.Files.Get get = d.files().get(id);
					get.getMediaHttpDownloader().setDirectDownloadEnabled(true);

					if (pos > 0) {
						HttpHeaders h = new HttpHeaders();
						h.setRange("bytes=" + pos + "-");
						get.setRequestHeaders(h);
					}

					InputStream is = stream = get.executeMediaAsInputStream();
					ByteBuffer b = readInputStream(is, dst.getByteBuffer(), getInputBufferLen()).getOrThrow();
					pos += b.remaining();
					return b;
				});
			}

			@Override
			public void close() {
				IoUtils.close(stream);
			}
		};
	}

	@Override
	public boolean canDelete() {
		return true;
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		return fs.useDrive(d -> {
			try {
				d.files().delete(id).execute();
				return true;
			} catch (Exception ex) {
				Log.e(ex, "Failed to delete file ", getName());
				return false;
			}
		});
	}
}
