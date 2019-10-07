package me.aap.fermata.storage;

import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;

import static android.provider.DocumentsContract.Document.MIME_TYPE_DIR;


/**
 * @author Andrey Pavlenko
 */
class MediaStoreDir extends MediaStoreFile {
	private static final String[] queryFields = new String[]{
			MediaStore.Files.FileColumns.DISPLAY_NAME,
			MediaStore.Files.FileColumns.DOCUMENT_ID,
			MediaStore.Files.FileColumns.MIME_TYPE,
	};
	private List<MediaFile> children;

	MediaStoreDir(MediaStoreDir parent, String name, String id) {
		super(parent, name, id);
	}

	static MediaStoreDir create(@NonNull Uri rootUri) {
		Uri uri = DocumentsContract.buildDocumentUriUsingTree(rootUri,
				DocumentsContract.getTreeDocumentId(rootUri));
		String name = null;
		String id = DocumentsContract.getTreeDocumentId(rootUri);

		try (Cursor c = FermataApplication.get().getContentResolver().query(uri,
				new String[]{MediaStore.Files.FileColumns.DISPLAY_NAME}, null, null, null)) {
			if ((c != null) && c.moveToNext()) name = c.getString(0);
		}

		return new MediaStoreDir(null, (name == null) ? uri.getLastPathSegment() : name, id) {
			@NonNull
			@Override
			public Uri getUri() {
				return rootUri;
			}

			@NonNull
			@Override
			Uri getRootUri() {
				return rootUri;
			}

			@NonNull
			@Override
			MediaStoreFile getRoot() {
				return this;
			}
		};
	}

	@Override
	public boolean isDirectory() {
		return true;
	}

	@Override
	public List<MediaFile> ls() {
		if (children != null) return children;

		Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(getRootUri(), getId());

		try (Cursor c = FermataApplication.get().getContentResolver().query(childrenUri, queryFields,
				null, null, null)) {
			if ((c != null) && c.moveToNext()) {
				List<MediaFile> children = new ArrayList<>(c.getCount());

				do {
					String name = c.getString(0);
					String id = c.getString(1);
					String mime = c.getString(2);

					if (isDir(mime)) {
						children.add(new MediaStoreDir(this, name, id));
					} else {
						children.add(new MediaStoreFile(this, name, id));
					}
				} while (c.moveToNext());

				return this.children = Collections.unmodifiableList(children);
			}
		}

		return this.children = Collections.emptyList();
	}

	MediaStoreFile findAnyFile() {
		List<MediaFile> ls = ls();

		for (MediaFile f : ls) {
			if (!f.isDirectory()) return (MediaStoreFile) f;
		}
		for (MediaFile f : ls) {
			f = ((MediaStoreDir) f).findAnyFile();
			if (f != null) return (MediaStoreFile) f;
		}

		return null;
	}

	private static boolean isDir(String mime) {
		return MIME_TYPE_DIR.equals(mime);
	}
}
