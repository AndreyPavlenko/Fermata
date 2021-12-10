package me.aap.fermata.media.service;

import static me.aap.fermata.media.service.FermataMediaService.ACTION_CAR_MEDIA_SERVICE;
import static me.aap.fermata.media.service.FermataMediaService.ACTION_MEDIA_SERVICE;
import static me.aap.fermata.media.service.FermataMediaService.DEFAULT_NOTIF_COLOR;
import static me.aap.fermata.media.service.FermataMediaService.INTENT_ATTR_NOTIF_COLOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.IBinder;

import androidx.annotation.Nullable;

import me.aap.fermata.FermataApplication;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.AppActivity;

/**
 * @author Andrey Pavlenko
 */
public class FermataMediaServiceConnection implements ServiceConnection {
	private Promise<FermataMediaServiceConnection> promise;
	private FermataMediaService.ServiceBinder binder;

	public static FutureSupplier<FermataMediaServiceConnection> connect(@Nullable AppActivity a, boolean isAuto) {
		int notifColor = Color.parseColor(DEFAULT_NOTIF_COLOR);

		if (a != null) {
			TypedArray typedArray = a.getTheme().obtainStyledAttributes(new int[]{android.R.attr.statusBarColor});
			notifColor = typedArray.getColor(0, notifColor);
			typedArray.recycle();
		}

		Context ctx = FermataApplication.get();
		FermataMediaServiceConnection con = new FermataMediaServiceConnection();
		Promise<FermataMediaServiceConnection> p = con.promise = new Promise<>();
		Intent i = new Intent(ctx, FermataMediaService.class);
		i.setAction(isAuto ? ACTION_CAR_MEDIA_SERVICE : ACTION_MEDIA_SERVICE);
		i.putExtra(INTENT_ATTR_NOTIF_COLOR, notifColor);
		Log.d("Binding service to context ", ctx);

		if (!ctx.bindService(i, con, Context.BIND_AUTO_CREATE)) {
			Exception ex = new IllegalStateException("Failed to bind to FermataMediaService");
			Log.e(ex, "Service connection failed");
			p.completeExceptionally(ex);
		}

		return p;
	}

	public FermataServiceUiBinder createBinder() {
		return new FermataServiceUiBinder(this);
	}

	public MediaSessionCallback getMediaSessionCallback() {
		FermataMediaService.ServiceBinder b = binder;
		return (b == null) ? null : b.getMediaSessionCallback();
	}

	public boolean isConnected() {
		FermataMediaService.ServiceBinder b = binder;
		return (b != null) && b.isBinderAlive();
	}

	public void disconnect() {
		if (!isConnected()) return;
		Log.d("Unbinding service from context ", FermataApplication.get());
		FermataApplication.get().unbindService(this);
		disconnected();
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		Promise<FermataMediaServiceConnection> p = promise;
		promise = null;
		binder = (FermataMediaService.ServiceBinder) service;
		p.complete(this);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		Log.d("Service disconnected");
		disconnected();
	}

	private void disconnected() {
		FermataMediaService.ServiceBinder b = binder;
		if (b == null) return;
		Promise<FermataMediaServiceConnection> p = promise;
		binder = null;
		promise = null;
		if (p != null)
			p.completeExceptionally(new IllegalStateException("FermataMediaService disconnected"));
	}
}
