package me.aap.fermata.auto.control;

import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.media.service.SharedConstants;

import static me.aap.fermata.media.service.ControlServiceConnection.ACTION_CONTROL_SERVICE;

/**
 * @author Andrey Pavlenko
 */
public class FermataMediaServiceControl extends MediaBrowserServiceCompat implements SharedConstants {
	private ControlToFermataConnection connection;

	@Override
	public void onCreate() {
		super.onCreate();
		connection = new ControlToFermataConnection(this);
		connection.connect();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (connection != null) {
			connection.disconnect(true);
			connection = null;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		if (ACTION_CONTROL_SERVICE.equals(intent.getAction())) {
			return connection.getBinder();
		} else {
			return super.onBind(intent);
		}
	}

	@Nullable
	@Override
	public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
		Bundle extras = new Bundle();
		extras.putBoolean(CONTENT_STYLE_SUPPORTED, true);
		extras.putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
		extras.putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE);
		return new BrowserRoot("Root", extras);
	}

	@Override
	public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
		if (connection == null) {
			result.sendResult(Collections.emptyList());
		} else {
			result.detach();
			connection.loadChildren(parentId, result);
		}
	}
}
