package me.aap.fermata.addon;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;

import me.aap.utils.app.App;
import me.aap.utils.io.FileUtils;

/**
 * @author Andrey Pavlenko
 */
public interface FermataContentAddon extends FermataAddon {
	@Nullable
	default ParcelFileDescriptor openFile(Uri uri) throws FileNotFoundException {
		String s = uri.getScheme();
		if (s == null) throw new FileNotFoundException(uri.toString());
		if (s.equals("file")) {
			return ParcelFileDescriptor.open(new File(uri.toString().substring(6)), MODE_READ_ONLY);
		} else if (s.equals("content")) {
			return App.get().getContentResolver().openFileDescriptor(uri, "r");
		}
		throw new FileNotFoundException(uri.toString());
	}

	@Nullable
	default String getFileType(Uri uri, String displayName) {
		if (displayName == null) displayName = uri.getPath();
		return FileUtils.getMimeType(displayName);
	}
}
