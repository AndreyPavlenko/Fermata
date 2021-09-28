package me.aap.fermata.provider;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.util.Base64.URL_SAFE;
import static java.nio.charset.StandardCharsets.US_ASCII;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataContentProvider extends ContentProvider {
	private static final String URI_PREF = "content://" + BuildConfig.APPLICATION_ID + "/image/";

	public static boolean isSupportedFileScheme(String scheme) {
		if (scheme == null) return false;
		switch (scheme) {
			case "http":
			case "https":
			case "file":
			case "content":
				return true;
			default:
				return FermataApplication.get().getVfsManager().isSupportedScheme(scheme);
		}
	}

	public static Uri toContentUri(Uri uri) {
		String enc = Base64.encodeToString(uri.toString().getBytes(US_ASCII), URL_SAFE);
		return Uri.parse(URI_PREF + enc);
	}

	@Nullable
	@Override
	public String[] getStreamTypes(@NonNull Uri uri, @NonNull String mimeTypeFilter) {
		return new String[]{"image/*"};
	}

	@Nullable
	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode)
			throws FileNotFoundException {
		String s = uri.toString();
		if ((s.length() <= URI_PREF.length()))
			throw new FileNotFoundException(uri.toString());
		String imgUri = new String(Base64.decode(s.substring(URI_PREF.length()), URL_SAFE), US_ASCII);

		if (imgUri.startsWith("file:/")) {
			return ParcelFileDescriptor.open(new File(imgUri.substring(6)), MODE_READ_ONLY);
		} else if (imgUri.startsWith("http")) {
			try {
				return ParcelFileDescriptor.open(FermataApplication.get().getBitmapCache()
						.downloadImage(imgUri).get().getLocalFile(), MODE_READ_ONLY);
			} catch (Exception ex) {
				Log.e(ex, "Failed to download image ", uri);
			}
		} else if (imgUri.startsWith("content:/")) {

			return FermataApplication.get().getBitmapCache().openResourceImage(Uri.parse(imgUri));
		}

		throw new FileNotFoundException(uri.toString());
	}

	@Nullable
	@Override
	public AssetFileDescriptor openTypedAssetFile(@NonNull Uri uri, @NonNull String mimeTypeFilter, @Nullable Bundle opts) throws FileNotFoundException {
		return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
											@Nullable String[] selectionArgs, @Nullable String sortOrder) {
		return null;
	}

	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		String path = uri.getPath();
		return (path == null) ? null : FileUtils.getMimeType(path);
	}

	@Nullable
	@Override
	public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
		return uri;
	}

	@Override
	public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
										@Nullable String[] selectionArgs) {
		return 0;
	}
}
