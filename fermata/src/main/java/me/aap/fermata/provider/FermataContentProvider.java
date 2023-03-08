package me.aap.fermata.provider;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.util.Base64.URL_SAFE;
import static java.nio.charset.StandardCharsets.US_ASCII;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataContentAddon;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class FermataContentProvider extends ContentProvider {
	private static final String IMG_PREF = "content://" + BuildConfig.APPLICATION_ID + "/image/";
	private static final String ADDON_PREF = "content://" + BuildConfig.APPLICATION_ID + "/addon/";

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

	public static Uri toImgUri(Uri uri) {
		String enc = Base64.encodeToString(uri.toString().getBytes(US_ASCII), URL_SAFE);
		return Uri.parse(IMG_PREF + enc);
	}

	public static Uri toAddonUri(String addon, Uri uri, @Nullable String displayName) {
		String enc = Base64.encodeToString(uri.toString().getBytes(US_ASCII), URL_SAFE);
		String u = ADDON_PREF + addon + '/' + enc;
		if (displayName != null) u = u + '/' + displayName;
		return Uri.parse(u);
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
		UriInfo info = UriInfo.parse(uri);
		if (info == null) throw new FileNotFoundException(uri.toString());
		String pref = info.getPref();

		if (pref.equals(IMG_PREF)) {
			Uri u = info.getUri();
			String s = u.getScheme();
			if (s == null) throw new FileNotFoundException(uri.toString());

			switch (s) {
				case "file":
					return ParcelFileDescriptor.open(new File(u.toString().substring(6)), MODE_READ_ONLY);
				case "http":
				case "https":
					try {
						return ParcelFileDescriptor.open(FermataApplication.get().getBitmapCache()
								.downloadImage(u.toString()).get().getLocalFile(), MODE_READ_ONLY);
					} catch (Exception ex) {
						String msg = "Failed to download image: " + u;
						Log.e(ex, msg);
						throw new FileNotFoundException(msg);
					}
				case "content":
					return FermataApplication.get().getBitmapCache().openResourceImage(u);
			}
		} else if (pref.equals(ADDON_PREF)) {
			return info.getAddon().openFile(info.getUri());
		}

		throw new FileNotFoundException(uri.toString());
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Nullable
	@Override
	public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
											@Nullable String[] selectionArgs, @Nullable String sortOrder) {
		UriInfo info = UriInfo.parse(uri);
		if (info == null) return null;
		MatrixCursor c = new MatrixCursor(new String[]{"_display_name", "mime_type"});
		c.addRow(new Object[]{info.getDisplayName(), info.getType()});
		return c;
	}

	@Nullable
	@Override
	public String getType(@NonNull Uri uri) {
		UriInfo info = UriInfo.parse(uri);
		return (info == null) ? null : info.getType();
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

	private static final class UriInfo {
		private final Uri uri;
		private final String pref;
		private final String displayName;
		private final FermataContentAddon addon;

		private UriInfo(String uri, String pref, String displayName, FermataContentAddon addon) {
			this.uri = Uri.parse(uri);
			this.pref = pref;
			this.displayName = displayName;
			this.addon = addon;
		}

		@Nullable
		static UriInfo parse(Uri uri) {
			String s = uri.toString();

			if (s.startsWith(IMG_PREF)) {
				return new UriInfo(new String(Base64.decode(s.substring(IMG_PREF.length()), URL_SAFE),
						US_ASCII), IMG_PREF, null, null);
			} else if (s.startsWith(ADDON_PREF)) {
				int idx = s.indexOf('/', ADDON_PREF.length());
				if (idx < 0) return null;
				String name = s.substring(ADDON_PREF.length(), idx);
				FermataAddon a = AddonManager.get().getAddon(name);
				if (!(a instanceof FermataContentAddon)) return null;
				int end = s.lastIndexOf('/');

				if (end == idx) {
					return new UriInfo(new String(Base64.decode(s.substring(idx + 1), URL_SAFE), US_ASCII),
							ADDON_PREF, null, (FermataContentAddon) a);
				} else {
					return new UriInfo(new String(Base64.decode(s.substring(idx + 1, end), URL_SAFE),
							US_ASCII), ADDON_PREF, s.substring(end + 1), (FermataContentAddon) a);
				}
			}

			return null;
		}

		public Uri getUri() {
			return uri;
		}

		public String getPref() {
			return pref;
		}

		public String getDisplayName() {
			if (displayName != null) return displayName;
			String u = uri.toString();
			int idx = u.lastIndexOf('/');
			return (idx < 0) ? null : u.substring(idx + 1);
		}

		public String getType() {
			return (addon != null) ? addon.getFileType(uri, getDisplayName())
					: FileUtils.getMimeType(uri.getPath());
		}

		public FermataContentAddon getAddon() {
			return addon;
		}
	}
}
