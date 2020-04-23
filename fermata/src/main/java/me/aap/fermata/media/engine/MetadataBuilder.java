package me.aap.fermata.media.engine;

import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;

/**
 * @author Andrey Pavlenko
 */
public class MetadataBuilder {
	private final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
	private String imageUri;

	public String getImageUri() {
		return imageUri;
	}

	public void setImageUri(String imageUri) {
		this.imageUri = imageUri;
		builder.putString(METADATA_KEY_ALBUM_ART_URI, imageUri);
	}

	public void putString(String key, String value) {
		builder.putString(key, value);
	}

	public void putLong(String key, long value) {
		builder.putLong(key, value);
	}

	public void putBitmap(String key, Bitmap value) {
		builder.putBitmap(key, value);
	}

	public MediaMetadataCompat build() {
		return builder.build();
	}
}
