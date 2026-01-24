package me.aap.utils.io;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import me.aap.utils.app.App;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FileUtils {
	private static final int FILE_BUFFER_SIZE = 8192;

	public static File getFileFromUri(Uri fileUri) {
		return getFileFromUri(App.get(), fileUri);
	}

	public static File getFileFromUri(Context ctx, Uri fileUri) {
		try (ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(fileUri, "r")) {
			return (pfd != null) ? getFileFromDescriptor(pfd) : null;
		} catch (Exception ex) {
			Log.d(ex, "Failed to resolve real path: ", fileUri);
		}

		return null;
	}

	public static File getFileFromDescriptor(ParcelFileDescriptor fd) throws Exception {
		String path = Os.readlink("/proc/self/fd/" + fd.getFd());
		File f = new File(path);
		if (f.isFile()) return f;

		if (path.startsWith("/mnt/media_rw/")) {
			f = new File("/storage" + path.substring(13));
			if (f.isFile()) return f;
		}

		return null;
	}

	public static String getFileName(String path) {
		int idx = path.lastIndexOf('/');
		return (idx > 0) ? path.substring(idx + 1) : path;
	}

	public static String getFileExtension(String fileName) {
		return getFileExtension(fileName, null);
	}

	public static String getFileExtension(String fileName, String defaultValue) {
		if (fileName == null) return null;
		int idx = fileName.lastIndexOf('.');
		return ((idx == -1) || (idx == (fileName.length() - 1))) ? defaultValue : fileName.substring(idx + 1);
	}

	public static String getMimeType(String fileName) {
		return getMimeTypeFromExtension(getFileExtension(fileName));
	}

	public static String getMimeTypeFromExtension(String fileExt) {
		return (fileExt == null) ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExt);
	}

	public static ByteBuffer readBytes(File f) throws IOException {
		try (FileInputStream in = new FileInputStream(f); FileChannel c = in.getChannel()) {
			return readBytes(c);
		}
	}

	public static ByteBuffer readBytes(FileChannel c) throws IOException {
		long len = c.size();

		if (len > 0) {
			ByteBuffer bb = ByteBuffer.allocate((int) len);
			long pos = 0;

			for (int i = c.read(bb, pos); bb.hasRemaining() && (i != -1); i = c.read(bb, pos)) {
				pos += i;
			}

			bb.flip();
			return bb;
		} else {
			return IoUtils.emptyByteBuffer();
		}
	}

	public static void transfer(FileChannel source, FileChannel destination) throws IOException {
		transfer(source, destination, 0, source.size());
	}

	public static void transfer(FileChannel source, FileChannel destination,
															long offset, long count) throws IOException {
		do {
			long n = destination.transferFrom(source, offset, Math.min(65536, count));
			offset += n;
			count -= n;
		} while (count > 0);
	}

	public static int copy(String fromFile, String toFile) throws IOException {
		return copy(new File(fromFile), new File(toFile));
	}

	public static int copy(File fromFile, File toFile) throws IOException {
		if (fromFile.isDirectory()) {
			int counter = 1;
			File[] files = fromFile.listFiles();
			mkdirs(toFile);

			if (files != null) {
				for (File f : files) {
					counter += copy(f, new File(toFile, f.getName()));
				}
			}

			return counter;
		}

		if (toFile.isDirectory()) {
			throw new IllegalArgumentException("The destination file is a directory: " + toFile);
		}

		File dir = toFile.getParentFile();
		if (dir != null) mkdirs(dir);

		try (FileInputStream in = new FileInputStream(fromFile);
				 FileOutputStream out = new FileOutputStream(toFile);
				 FileChannel source = in.getChannel();
				 FileChannel destination = out.getChannel()) {
			destination.truncate(0);
			transfer(source, destination);
		}

		return 1;
	}

	public static void copy(InputStream in, File to) throws IOException {
		try (OutputStream out = new FileOutputStream(to)) {
			copy(in, out);
		}
	}

	public static void copy(File from, OutputStream out)
			throws IOException {
		try (InputStream in = new FileInputStream(from)) {
			copy(in, out);
		}
	}

	public static void copy(InputStream in, OutputStream out)
			throws IOException {
		byte[] buf = new byte[FILE_BUFFER_SIZE];

		for (int i = in.read(buf); i != -1; i = in.read(buf)) {
			out.write(buf, 0, i);
		}
	}

	public static int move(File fromFile, File toFile) throws IOException {
		if (fromFile.exists()) {
			if (fromFile.isDirectory()) {
				if (toFile.exists() && !toFile.isDirectory()) {
					throw new IllegalArgumentException("The destination file is not a directory: " + toFile);
				}

				int counter = 0;
				File[] files = fromFile.listFiles();

				if (files != null) {
					for (File f : files) {
						counter += move(f, new File(toFile, f.getName()));
					}
				}

				return fromFile.delete() ? counter + 1 : counter;
			}

			if (toFile.exists() && toFile.isDirectory()) {
				throw new IllegalArgumentException(
						"The destination file is directory: " + toFile);
			}

			File dir = toFile.getParentFile();
			if (dir != null) mkdirs(dir);

			if (!fromFile.renameTo(toFile)) {
				copy(fromFile, toFile);
				delete(fromFile);
			}

			return 1;
		}

		return 0;
	}

	public static int delete(File... files) throws IOException {
		return delete(Arrays.asList(files));
	}

	public static int delete(Iterable<File> files) throws IOException {
		int count = 0;

		for (File f : files) {
			count += delete(f);
		}

		return count;
	}

	public static int delete(File file) throws IOException {
		if (file.isDirectory()) {
			int counter = 0;

			for (int i = 1; i <= 10; i++) {
				File[] files = file.listFiles();

				if (files != null) {
					for (File f : files) {
						counter += delete(f);
					}
				}

				if (!file.delete() && file.exists()) {
					try {
						Thread.sleep(10 * i);
					} catch (InterruptedException ex) {
					}
				} else {
					return counter + 1;
				}
			}

			throw new IOException("Failed to delete directory: " + file);
		}

		if (file.delete() || !file.exists()) {
			return 1;
		} else {
			throw new IOException("Failed to delete file: " + file);
		}
	}

	public static void mkdirs(File dir) throws IOException {
		if (!dir.mkdirs() && !dir.isDirectory()) {
			throw new IOException("Failed to create directory: " + dir);
		}
	}

	public static void mkdirs(File... dirs) throws IOException {
		for (File dir : dirs) {
			mkdirs(dir);
		}
	}
}
