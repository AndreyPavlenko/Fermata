package me.aap.utils.vfs.content;

import static android.content.Context.MODE_PRIVATE;
import static android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME;
import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.io.FileUtils;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.local.LocalFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class ContentFileSystem implements VirtualFileSystem {
	private final Provider provider;
	private final boolean preferFiles;
	private final SharedPreferences uriToPathMap;

	ContentFileSystem(Provider provider, boolean preferFiles) {
		this.provider = provider;
		this.preferFiles = preferFiles;
		uriToPathMap = preferFiles ? App.get().getSharedPreferences("uri_to_path", MODE_PRIVATE) : null;
	}

	@NonNull
	@Override
	public Provider getProvider() {
		return provider;
	}

	@Override
	public FutureSupplier<VirtualFile> getFile(Rid rid) {
		return getResource(rid).map(r -> (r instanceof VirtualFile) ? (VirtualFile) r : null);
	}

	@Override
	public FutureSupplier<VirtualFolder> getFolder(Rid rid) {
		return getResource(rid).map(r -> (r instanceof VirtualFolder) ? (VirtualFolder) r : null);
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		Uri uri = rid.toAndroidUri();

		if (preferFiles) {
			String path = uriToPathMap.getString(uri.toString(), null);

			if (path != null) {
				VirtualResource local = LocalFileSystem.getInstance().getResource(path);
				if (local != null) return completed(local);
			}
		}

		FutureSupplier<VirtualResource> res = App.get().execute(() -> create(uri));
		if (!preferFiles) return res;

		return res.then(r -> (r instanceof ContentFolder) ? ((ContentFolder) r).findAnyFile()
				.then(contentFile -> {
					if (contentFile == null) return res;

					File f = FileUtils.getFileFromUri(contentFile.getRid().toAndroidUri());
					if (f == null) return res;

					VirtualResource dir = res.get(null);
					f = f.getParentFile();

					for (ContentFolder p = contentFile.getParentFolder(); (p != null) && (f != null);
							 p = p.getParentFolder(), f = f.getParentFile()) {
						if (p == dir) {
							String path = f.getAbsolutePath();
							VirtualResource local = LocalFileSystem.getInstance().getResource(path);
							if (local == null) return res;
							uriToPathMap.edit().putString(uri.toString(), path).apply();
							return completed(local);
						}
					}

					return res;
				}) : completed(r));

	}

	private ContentResource create(@NonNull Uri docUri) {
		Context ctx = App.get();
		Uri uri = DocumentsContract.buildDocumentUriUsingTree(docUri,
				DocumentsContract.getTreeDocumentId(docUri));
		String name = null;
		String id = DocumentsContract.getTreeDocumentId(docUri);

		try (Cursor c = ctx.getContentResolver().query(uri, new String[]{COLUMN_DISPLAY_NAME}, null, null, null)) {
			if ((c != null) && c.moveToNext()) name = c.getString(0);
		}

		ContentFolder root = new ContentFolder(null, (name == null) ? uri.getLastPathSegment() : name, id) {
			@NonNull
			@Override
			public Rid getRid() {
				return Rid.create(docUri);
			}

			@NonNull
			@Override
			Uri getRootUri() {
				return docUri;
			}

			@NonNull
			@Override
			ContentFolder getRoot() {
				return this;
			}

			@NonNull
			@Override
			public VirtualFileSystem getVirtualFileSystem() {
				return ContentFileSystem.this;
			}
		};

		if (DocumentsContract.isDocumentUri(ctx, docUri)) {
			id = DocumentsContract.getDocumentId(docUri);
			name = null;

			try (Cursor c = ctx.getContentResolver().query(docUri, new String[]{COLUMN_DISPLAY_NAME}, null, null, null)) {
				if ((c != null) && c.moveToNext()) name = c.getString(0);
			}

			return new ContentFile(root, (name == null) ? uri.getLastPathSegment() : name, id);
		} else {
			return root;
		}
	}

	public static final class Provider implements VirtualFileSystem.Provider {
		public static final Pref<BooleanSupplier> PREFER_FILE_API = Pref.b("PREFER_FILE_API", false);
		private static final Set<String> schemes = Collections.singleton("content");
		private static final Provider instance = new Provider();

		private Provider() {
		}

		public static Provider getInstance() {
			return instance;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return schemes;
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(new ContentFileSystem(this, ps.getBooleanPref(PREFER_FILE_API)));
		}
	}
}
