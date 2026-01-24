package me.aap.utils.vfs.content;

import static android.provider.DocumentsContract.buildDocumentUriUsingTree;
import static android.provider.DocumentsContract.getDocumentId;
import static android.provider.DocumentsContract.renameDocument;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.io.AsyncInputStream;
import me.aap.utils.io.AsyncOutputStream;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.log.Log;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
class ContentFile extends ContentResource implements VirtualFile {
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static final AtomicReferenceFieldUpdater<ContentFile, FutureSupplier<Long>> LENGTH =
			(AtomicReferenceFieldUpdater) AtomicReferenceFieldUpdater.newUpdater(ContentFile.class, FutureSupplier.class, "length");
	@Keep
	private volatile FutureSupplier<Long> length;

	public ContentFile(ContentFolder parent, String name, String id) {
		super(parent, name, id);
	}

	@Override
	public FutureSupplier<Long> getLength() {
		FutureSupplier<Long> length = this.length;
		if (length != null) return length;

		Promise<Long> p = new Promise<>();
		if (!LENGTH.compareAndSet(this, null, p)) return LENGTH.get(this);

		App.get().execute(() -> {
			long len = queryLong(getRid().toAndroidUri(), DocumentsContract.Document.COLUMN_SIZE, -1);

			if (len == -1) {
				try (InputStream in = App.get().getContentResolver().openInputStream(getRid().toAndroidUri())) {
					len = IoUtils.skip(in, Long.MAX_VALUE);
				} catch (IOException ex) {
					Log.d(ex);
				}
			}

			p.complete(len);
			this.length = completed(len);
			return len;
		});

		return p;
	}

	@Override
	public AsyncInputStream getInputStream(long offset) throws IOException {
		InputStream in = App.get().getContentResolver().openInputStream(getRid().toAndroidUri());
		if (in == null) throw new IOException("Resource not found: " + this);
		IoUtils.skip(in, offset);
		return AsyncInputStream.from(in, getInputBufferLen());
	}

	@Override
	public AsyncOutputStream getOutputStream() throws IOException {
		OutputStream out = App.get().getContentResolver().openOutputStream(getRid().toAndroidUri());
		if (out == null) throw new IOException("Resource not found: " + this);
		return AsyncOutputStream.from(out, getOutputBufferLen());
	}

	@Nullable
	@Override
	public RandomAccessChannel getChannel(String mode) {
		try {
			ParcelFileDescriptor pfd = App.get().getContentResolver()
					.openFileDescriptor(getRid().toAndroidUri(), mode);
			if (pfd == null) {
				Log.e("Resource not found: ", this);
				return null;
			}
			FileDescriptor fd = pfd.getFileDescriptor();
			FileInputStream in = (mode.contains("r")) ? new FileInputStream(fd) : null;
			FileOutputStream out = (mode.contains("w")) ? new FileOutputStream(fd) : null;
			FileChannel read = (in != null) ? in.getChannel() : null;
			FileChannel write = (out != null) ? out.getChannel() : null;
			return RandomAccessChannel.wrap(read, write, in, out, pfd);
		} catch (Throwable ex) {
			Log.e(ex, "Failed to open file: ", getRid());
			return null;
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		try {
			Uri u = buildDocumentUriUsingTree(getRid().toAndroidUri(), getId());
			return completed(DocumentsContract.deleteDocument(App.get().getContentResolver(), u));
		} catch (Exception ex) {
			return failed(ex);
		}
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFile> rename(CharSequence name) {
		try {
			String n = name.toString();
			Uri u = buildDocumentUriUsingTree(getRid().toAndroidUri(), getId());
			u = renameDocument(App.get().getContentResolver(), u, n);
			if (u != null) return completed(new ContentFile(getParentFolder(), n, getDocumentId(u)));
		} catch (Exception ex) {
			return failed(ex);
		}

		return VirtualFile.super.rename(name);
	}
}
