package me.aap.fermata.auto;

import static android.os.SystemClock.uptimeMillis;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static androidx.core.graphics.drawable.IconCompat.createWithResource;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarAppService;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.validation.HostValidator;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.utils.concurrent.HandlerExecutor;
import me.aap.utils.function.Cancellable;
import me.aap.utils.log.Log;

public class MirrorServiceFS extends CarAppService {
	private MirrorDisplay md;
	static SurfaceContainer sc;

	@Override
	public void onCreate() {
		super.onCreate();
		md = MirrorDisplay.get();
	}

	@Override
	public void onDestroy() {
		md.release();
		md = null;
		super.onDestroy();
	}

	@NonNull
	@Override
	public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
		return new Session() {
			@NonNull
			@Override
			public Screen onCreateScreen(@NonNull Intent intent) {
				return new MirrorScreen(getCarContext());
			}

			@Override
			public void onNewIntent(@NonNull Intent intent) {
				super.onNewIntent(intent);
			}
		};
	}

	@NonNull
	@Override
	public HostValidator createHostValidator() {
		return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
	}

	private final class MirrorScreen extends Screen implements SurfaceCallback {
		private final HandlerExecutor handler = FermataApplication.get().getHandler();
		private float scrollStartX;
		private float scrollStartY;
		private float scrollX;
		private float scrollY;
		private long scrollDownTime;
		private long scrollMoveTime;
		private Cancellable scrollUp;

		MirrorScreen(@NonNull CarContext ctx) {
			super(ctx);
			ctx.getCarService(AppManager.class).setSurfaceCallback(this);
			Log.i("MirrorScreen created. API level: ", ctx.getCarAppApiLevel());
		}

		@Override
		public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
			if (sc.getSurface() == null) return;
			MirrorServiceFS.sc = sc;
			md.setSurface(sc);
			scrollStartX = sc.getWidth() / 2f;
			scrollStartY = sc.getHeight() / 2f;
		}

		@Override
		public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
			md.releaseSurface(MirrorServiceFS.sc);
			MirrorServiceFS.sc = null;
		}

		@Override
		public void onClick(float x, float y) {
			if (scrollUp != null) {
				scrollUp.cancel();
				scrollUp = null;
			}
			md().tap(x, y);
		}

		@Override
		public void onScroll(float distanceX, float distanceY) {
			var md = md();
			var time = uptimeMillis();
			scrollMoveTime = time;
			if (scrollUp == null) {
				scrollX = scrollStartX;
				scrollY = scrollStartY;
				if (!md.motionEvent(time, time, ACTION_DOWN, scrollX, scrollY)) return;
				scrollDownTime = time;
				scheduleScrollUp(500);
			}
			scrollX -= distanceX;
			scrollY -= distanceY;
			md.motionEvent(scrollDownTime, time, ACTION_MOVE, scrollX, scrollY);
		}

		private void scheduleScrollUp(long delay) {
			scrollUp = handler.schedule(() -> {
				if (scrollUp == null) return;
				var time = uptimeMillis();
				var end = scrollMoveTime + delay;
				if (end > time) {
					scheduleScrollUp(end - time);
				} else {
					scrollUp = null;
					md().motionEvent(scrollDownTime, time, ACTION_UP, scrollX, scrollY);
				}
			}, delay);
		}

		@Override
		public void onScale(float focusX, float focusY, float scaleFactor) {
			scrollStartX = focusX;
			scrollStartY = focusY;
			md().scale(focusX, focusY, scaleFactor > 1f);
		}

		@NonNull
		@Override
		public Template onGetTemplate() {
			var homeButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.home)).build())
					.setOnClickListener(MirrorActivity::onHomeButtonClick).build();
			var backButton = new Action.Builder().setIcon(
							new CarIcon.Builder(createWithResource(getCarContext(), R.drawable.back)).build())
					.setOnClickListener(MirrorActivity::onBackButtonClick).build();
			return new NavigationTemplate.Builder().setActionStrip(
							new ActionStrip.Builder().addAction(homeButton).addAction(backButton).build())
					.setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build()).build();
		}

		private MirrorDisplay md() {
			if (sc != null) md.setSurface(sc);
			return md;
		}
	}
}
