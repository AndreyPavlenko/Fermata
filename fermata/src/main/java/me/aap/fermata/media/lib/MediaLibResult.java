package me.aap.fermata.media.lib;

import android.os.Bundle;

import androidx.media.MediaBrowserServiceCompat;

/**
 * @author Andrey Pavlenko
 */
public interface MediaLibResult<T> {

	void sendResult(T result, Bundle error);

	default void detach() {
	}

	class Wrapper<T> implements MediaLibResult<T> {
		final MediaBrowserServiceCompat.Result<T> result;

		Wrapper(MediaBrowserServiceCompat.Result<T> result) {
			this.result = result;
		}

		@Override
		public void sendResult(T r, Bundle error) {
			if (error == null) result.sendResult(r);
			else result.sendError(error);
		}

		@Override
		public void detach() {
			result.detach();
		}
	}
}
