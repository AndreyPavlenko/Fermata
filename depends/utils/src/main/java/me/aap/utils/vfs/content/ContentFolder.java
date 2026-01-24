package me.aap.utils.vfs.content;

import static android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME;
import static android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID;
import static android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE;
import static android.provider.DocumentsContract.Document.MIME_TYPE_DIR;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;

import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class ContentFolder extends ContentResource implements VirtualFolder {
	private static final String[] queryFields =
			new String[]{COLUMN_DISPLAY_NAME, COLUMN_DOCUMENT_ID, COLUMN_MIME_TYPE};

	public ContentFolder(ContentFolder parent, String name, String id) {
		super(parent, name, id);
	}

	@Override
	public Filter filterChildren() {
		return new ContentFilter();
	}

	@Override
	public FutureSupplier<List<VirtualResource>> getChildren() {
		return getChildren(null, null);
	}

	private FutureSupplier<List<VirtualResource>> getChildren(@Nullable String selection,
																														@Nullable String[] selectionArgs) {
		return App.get().execute(() -> {
			Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(getRootUri(), getId());

			try (Cursor c = App.get().getContentResolver()
					.query(childrenUri, queryFields, selection, selectionArgs, null)) {
				if ((c != null) && c.moveToNext()) {
					List<VirtualResource> list = new ArrayList<>(c.getCount());

					do {
						String name = c.getString(0);
						String id = c.getString(1);
						String mime = c.getString(2);

						if (isDir(mime)) {
							list.add(new ContentFolder(this, name, id));
						} else {
							list.add(new ContentFile(this, name, id));
						}
					} while (c.moveToNext());

					return list;
				}
			}

			return Collections.emptyList();
		});
	}

	@Override
	public FutureSupplier<VirtualFile> createFile(CharSequence name) {
		return createDocument(name.toString(), ContentFile.class).cast();
	}

	@Override
	public FutureSupplier<VirtualFolder> createFolder(CharSequence name) {
		String n = name.toString();
		if (n.isEmpty()) return completed(this);
		return createDocument(n, ContentFolder.class).cast();
	}

	private <T> FutureSupplier<T> createDocument(String name, Class<T> type) {
		String[] names = name.toString().split("/");
		if (names.length > 1) {
			FutureSupplier<VirtualFolder> f = completed(this);
			for (int i = 0; i < names.length - 1; i++) {
				var n = names[i];
				if (!n.isEmpty()) f = f.then(p -> p.createFolder(n));
			}
			return f.then(p -> ((ContentFolder) p).createDocument(names[names.length - 1], type));
		}

		return getChild(name).map(c -> {
			if (c != null) {
				if (type.isInstance(c)) return type.cast(c);
				else throw new IOException(
						(c instanceof ContentFolder) ? "Folder" : "File" + " exists " + name);
			}

			var cr = App.get().getContentResolver();
			var n = name.toString();
			var u = DocumentsContract.buildDocumentUriUsingTree(getRid().toAndroidUri(), getId());
			var mime = (type == ContentFolder.class) ? MIME_TYPE_DIR : "application/tmp";
			u = DocumentsContract.createDocument(cr, u, mime, n);
			if (u == null) throw new IOException("Failed to create file " + name);
			return type.getConstructor(
					ContentFolder.class, String.class, String.class).newInstance(this, n,
					DocumentsContract.getDocumentId(u));
		});
	}

	@NonNull
	@Override
	public FutureSupplier<Boolean> delete() {
		try {
			Uri u = DocumentsContract.buildDocumentUriUsingTree(getRid().toAndroidUri(), getId());
			return completed(DocumentsContract.deleteDocument(App.get().getContentResolver(), u));
		} catch (Exception ex) {
			return failed(ex);
		}
	}

	FutureSupplier<ContentFile> findAnyFile() {
		return getChildren().then(ls -> {
			if (ls.isEmpty()) return completedNull();

			for (VirtualResource f : ls) {
				if (!f.isFolder()) return completed((ContentFile) f);
			}

			Iterator<VirtualResource> it = ls.iterator();
			return Async.iterate(((ContentFolder) it.next()).findAnyFile(), find -> {
				ContentFile f = find.peek();
				return (f != null) || !it.hasNext() ? null : ((ContentFolder) it.next()).findAnyFile();
			});
		});
	}

	private static boolean isDir(String mime) {
		return MIME_TYPE_DIR.equals(mime) || "directory".equals(mime);
	}

	private final class ContentFilter extends BasicFilter {
		private final StringBuilder selection = new StringBuilder();
		private final List<String> selectionArgs = new ArrayList<>();
		private boolean not = false;

		ContentFilter() {
			super(ContentFolder.this);
		}

		@Override
		public FutureSupplier<List<VirtualResource>> apply() {
			return getChildren(selection.toString(), selectionArgs.toArray(new String[0])).map(
					this::apply);
		}

		@Override
		public Filter starts(String prefix) {
			super.starts(prefix);
			return like("?%", prefix);
		}

		@Override
		public Filter ends(String suffix) {
			super.ends(suffix);
			return like("%?", suffix);
		}

		@Override
		public Filter startsEnds(String prefix, String suffix) {
			super.startsEnds(prefix, suffix);
			return like("?%?", prefix, suffix);
		}

		@Override
		public Filter and() {
			super.and();
			if (selection.length() != 0) selection.append(" AND ");
			return this;
		}

		@Override
		public Filter or() {
			super.or();
			if (selection.length() != 0) selection.append(" OR ");
			return this;
		}

		@Override
		public Filter not() {
			super.not();
			not = true;
			return this;
		}

		private Filter like(String like, String... args) {
			selection.append('?');
			selectionArgs.add(COLUMN_DISPLAY_NAME);
			if (not) {
				not = false;
				selection.append(" NOT LIKE ");
			} else {
				selection.append(" LIKE ");
			}
			selection.append(like);
			Collections.addAll(selectionArgs, args);
			return this;
		}
	}
}
