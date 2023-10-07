package me.aap.fermata.addon.cast;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataMediaServiceAddon;
import me.aap.fermata.addon.FermataToolAddon;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MediaLibFragment;
import me.aap.fermata.ui.fragment.ToolBarMediator;
import me.aap.utils.app.App;
import me.aap.utils.io.IoUtils;
import me.aap.utils.log.Log;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class CastAddon
		implements FermataMediaServiceAddon, FermataToolAddon, SessionManagerListener<CastSession> {
	private static final AddonInfo info = FermataAddon.findAddonInfo(CastAddon.class.getName());
	@Nullable
	private MediaSessionCallback cb;
	@Nullable
	private SessionManager sessionMgr;
	@Nullable
	private MediaRouteButton routeButton;
	@Nullable
	private CastMediaEngineProvider engProvider;

	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.cast_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public void contributeTool(ToolBarMediator m, ToolBarView tb, ActivityFragment f) {
		if (!(f instanceof MediaLibFragment)) return;
		Context ctx = f.requireContext();
		MainActivityDelegate a = MainActivityDelegate.get(ctx);
		if (a.isCarActivity()) return;
		if (cb == null) onServiceCreate(a.getMediaSessionCallback());

		if (routeButton == null) {
			Resources.Theme theme = ctx.getTheme();
			theme.applyStyle(R.style.RouteButtonStyle, true);
			routeButton = new MediaRouteButton(ctx);
			CastButtonFactory.setUpMediaRouteButton(ctx, routeButton);
			routeButton.setAlwaysVisible(true);
		}

		m.addView(tb, routeButton, R.id.cast_button);
	}

	@Override
	public void onServiceCreate(MediaSessionCallback cb) {
		activate(cb);
	}

	@Override
	public void onServiceDestroy(MediaSessionCallback cb) {
		deactivate();
		this.cb = null;
	}

	public void onActivityPause(MainActivityDelegate a) {
		if (!a.isCarActivity()) routeButton = null;
	}

	@Override
	public void onSessionStarted(@NonNull CastSession session, @NonNull String sessionId) {
		Log.d("Cast session started: ", sessionId);
		connected(session);
	}

	@Override
	public void onSessionResumed(@NonNull CastSession session, boolean wasSuspended) {
		Log.d("Cast session resumed: ", wasSuspended);
		connected(session);
	}

	@Override
	public void onSessionSuspended(@NonNull CastSession session, int reason) {
		Log.d("Cast session suspended due to : ", reason);
		disconnected();
	}

	@Override
	public void onSessionStartFailed(@NonNull CastSession session, int error) {
		Log.d("Cast session start failed: ", error);
		disconnected();
	}

	@Override
	public void onSessionResumeFailed(@NonNull CastSession castSession, int error) {
		Log.d("Cast session resume failed: ", error);
		disconnected();
	}

	@Override
	public void onSessionEnded(@NonNull CastSession session, int error) {
		Log.d("Cast session ended: ", error);
		disconnected();
	}

	@Override
	public void onSessionStarting(@NonNull CastSession castSession) {
	}

	@Override
	public void onSessionResuming(@NonNull CastSession session, @NonNull String s) {
	}

	@Override
	public void onSessionEnding(@NonNull CastSession castSession) {
		if ((cb == null) || (engProvider == null)) return;
		cb.removeCustomEngineProvider(engProvider);
	}

	@Override
	public void uninstall() {
		deactivate();
		cb = null;
		routeButton = null;
	}

	private void activate(MediaSessionCallback cb) {
		this.cb = cb;
		CastContext.getSharedInstance(cb.getMediaLib().getContext(), App.get().getExecutor())
				.addOnSuccessListener(ctx -> {
					sessionMgr = ctx.getSessionManager();
					sessionMgr.addSessionManagerListener(this, CastSession.class);
					CastSession cs = sessionMgr.getCurrentCastSession();
					if (cs != null) connected(cs);
				}).addOnFailureListener(Log::e);
	}

	private void deactivate() {
		if (sessionMgr == null) return;
		disconnected();
		sessionMgr.removeSessionManagerListener(this, CastSession.class);
		sessionMgr = null;
	}

	private void connected(CastSession session) {
		if (cb == null) return;
		RemoteMediaClient client = session.getRemoteMediaClient();
		if (client == null) return;
		IoUtils.close(engProvider);
		engProvider = new CastMediaEngineProvider(session, client, cb.getMediaLib());
		cb.setCustomEngineProvider(engProvider);
	}

	private void disconnected() {
		IoUtils.close(engProvider);
		if ((cb == null) || (engProvider == null)) return;
		cb.removeCustomEngineProvider(engProvider);
		this.engProvider = null;
	}
}
