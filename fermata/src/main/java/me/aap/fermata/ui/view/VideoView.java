package me.aap.fermata.ui.view;

import static android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY;
import static androidx.core.text.HtmlCompat.fromHtml;
import static me.aap.fermata.media.lib.MediaLib.PlayableItem;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_16_9;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_4_3;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_BEST;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_FILL;
import static me.aap.fermata.media.pref.MediaPrefs.SCALE_ORIGINAL;
import static me.aap.fermata.media.sub.SubGrid.Position.BOTTOM_LEFT;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.circularreveal.CircularRevealFrameLayout;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.media.sub.SubGrid;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.ui.view.NavBarView;

/**
 * @author Andrey Pavlenko
 */
public class VideoView extends FrameLayout
		implements SurfaceHolder.Callback, View.OnLayoutChangeListener, PreferenceStore.Listener,
		MainActivityListener, BiConsumer<SubGrid.Position, Subtitles.Text> {
	private final Set<PreferenceStore.Pref<?>> prefChange = new HashSet<>(
			Arrays.asList(MediaPrefs.VIDEO_SCALE, MediaPrefs.AUDIO_DELAY, MediaPrefs.SUB_DELAY));
	private SubDrawer subDrawer;
	private FutureSupplier<?> createSurface = new Promise<>();

	public VideoView(Context context) {
		this(context, null);
	}

	public VideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
		getActivity().onSuccess(a -> {
			a.addBroadcastListener(this);
			a.getLib().getPrefs().addBroadcastListener(this);
			setClockPos(a.getPrefs().getClockPosPref());
		});
	}

	protected void init(Context context) {
		setBackgroundColor(Color.BLACK);
		addView(new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
				setLayoutParams(lp);
				getHolder().addCallback(VideoView.this);
			}
		});
		addView(new SurfaceView(getContext()) {
			{
				FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
				lp.gravity = Gravity.FILL;
				setLayoutParams(lp);
				setZOrderMediaOverlay(true);
				setZOrderOnTop(true);
				getHolder().setFormat(PixelFormat.TRANSLUCENT);
				getHolder().addCallback(VideoView.this);
			}
		});

		addInfoView(context);
		addOnLayoutChangeListener(this);
		setLayoutParams(new CircularRevealFrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		setFocusable(true);
	}

	protected void addInfoView(Context context) {
		VideoInfoView d = new VideoInfoView(context, null);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
		lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
		d.setLayoutParams(lp);
		addView(d);
	}

	public SurfaceView getVideoSurface() {
		return (SurfaceView) getChildAt(0);
	}

	@Nullable
	public SurfaceView getSubtitleSurface() {
		return (SurfaceView) getChildAt(1);
	}


	public void setClockPos(int pos) {
		int idx = getChildCount() - 1;
		int gravity = Gravity.TOP;

		switch (pos) {
			case MainActivityPrefs.CLOCK_POS_NONE -> {
				if (getChildAt(idx) instanceof TextClock) removeViewAt(idx);
				return;
			}
			case MainActivityPrefs.CLOCK_POS_LEFT -> gravity |= Gravity.START;
			case MainActivityPrefs.CLOCK_POS_RIGHT -> gravity |= Gravity.END;
			case MainActivityPrefs.CLOCK_POS_CENTER -> gravity |= Gravity.CENTER;
		}

		View clock = getChildAt(idx);
		FrameLayout.LayoutParams lp;

		if (clock instanceof TextClock) {
			lp = (FrameLayout.LayoutParams) clock.getLayoutParams();
		} else {
			Context ctx = getContext();
			int m = toIntPx(ctx, 10);
			clock = LayoutInflater.from(ctx).inflate(R.layout.clock_view, this, false);
			lp = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
			lp.setMargins(m, m, m, m);
			addView(clock);
		}

		lp.gravity = gravity;
		clock.setLayoutParams(lp);
	}

	@Nullable
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(2);
	}

	public void showVideo(boolean hideTitle) {
		createSurface.onSuccess(v -> {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaSessionCallback cb = a.getMediaSessionCallback();
			MediaEngine eng = cb.getEngine();
			if (eng != null) setSurfaceSize(eng);
			VideoInfoView info = getVideoInfoView();
			if (hideTitle && (info != null)) info.setVisibility(GONE);
		});
	}

	public void prepareSubDrawer(boolean dbl) {
		MainActivityDelegate a = getActivity().peek();
		if (a == null) return;
		if (dbl) {
			if (subDrawer instanceof DoubleSubDrawer) return;
			subDrawer = new DoubleSubDrawer(a.getPrefs().getTextIconSizePref(a));
		} else {
			if (subDrawer instanceof GridDrawer) return;
			subDrawer = new GridDrawer(a.getPrefs().getTextIconSizePref(a));
		}
	}

	public void releaseSubDrawer() {
		subDrawer = null;
	}

	@Override
	public void accept(SubGrid.Position position, @Nullable Subtitles.Text text) {
		if (subDrawer == null) return;
		if (!subDrawer.setText(position, text)) return;
		createSurface.onSuccess(v -> {
			SurfaceView sv = getSubtitleSurface();
			if (sv == null) return;

			var h = sv.getHolder();
			var c = h.lockCanvas();
			try {
				subDrawer.clr(c);
				subDrawer.draw(c);
			} finally {
				h.unlockCanvasAndPost(c);
			}
		});
	}

	public void setSurfaceSize(MediaEngine eng) {
		if (eng.setSurfaceSize(this)) return;

		PlayableItem item = eng.getSource();
		if (item == null) return;

		SurfaceView surface = getVideoSurface();
		ViewGroup.LayoutParams lp = surface.getLayoutParams();

		float videoWidth = eng.getVideoWidth();
		float videoHeight = eng.getVideoHeight();
		float videoRatio = videoWidth / videoHeight;

		float screenWidth = getWidth();
		float screenHeight = getHeight();

		int width;
		int height;
		int scale = item.getPrefs().getVideoScalePref();

		switch (scale) {
			case SCALE_4_3:
			case SCALE_16_9:
				videoRatio = (scale == SCALE_16_9) ? 16f / 9f : 4f / 3f;
			default:
			case SCALE_BEST:
				float screenRatio = screenWidth / screenHeight;

				if (videoRatio > screenRatio) {
					width = (int) screenWidth;
					height = (int) (screenWidth / videoRatio);
				} else {
					width = (int) (screenHeight * videoRatio);
					height = (int) screenHeight;
				}

				break;
			case SCALE_FILL:
				if (videoWidth > videoHeight) {
					width = (int) screenWidth;
					height = (int) (screenWidth / videoRatio);
				} else {
					width = (int) (screenHeight * videoRatio);
					height = (int) screenHeight;
				}

				break;
			case SCALE_ORIGINAL:
				width = (int) videoWidth;
				height = (int) videoHeight;
				break;
		}

		if ((lp.width != width) || (lp.height != height)) {
			lp.width = width;
			lp.height = height;
			surface.setLayoutParams(lp);
		}
	}

	@Override
	public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
														 int oldTop, int oldRight, int oldBottom) {
		FermataApplication.get().getHandler().post(() -> createSurface.onSuccess(s -> {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
			if (eng == null) return;

			PlayableItem i = eng.getSource();
			if ((i != null) && i.isVideo()) setSurfaceSize(eng);
		}));
	}

	@Override
	public void surfaceCreated(@NonNull SurfaceHolder holder) {
		if (!getVideoSurface().getHolder().getSurface().isValid()) return;
		SurfaceView s = getSubtitleSurface();
		if ((s != null) && !s.getHolder().getSurface().isValid()) return;
		getActivity().onSuccess(
				a -> a.getMediaSessionCallback().addVideoView(this, a.isCarActivity() ? 0 : 1));
		if (createSurface instanceof Promise<?> p) {
			createSurface = completedNull();
			p.complete(null);
		}
	}

	@Override
	public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		createSurface = new Promise<>();
		getActivity().onSuccess(a -> a.getMediaSessionCallback().removeVideoView(this));
	}

	public boolean isSurfaceCreated() {
		return createSurface.isDone();
	}

	public void onSurfaceCreated(Runnable run) {
		createSurface.onSuccess(v -> run.run());
	}

	@Override
	public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(@NonNull MotionEvent e) {
		MainActivityDelegate a = getActivity().peek();
		return (a != null) && a.interceptTouchEvent(e, this::onTouch);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		MainActivityDelegate a;
		FermataServiceUiBinder b;
		ControlPanelView p;

		switch (keyCode) {
			case KEYCODE_ENTER, KEYCODE_DPAD_CENTER -> {
				if ((a = getActivity().peek()) == null) break;
				return a.getControlPanel().onTouch(this);
			}
			case KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT -> {
				if ((a = getActivity().peek()) == null) break;
				p = a.getControlPanel();
				if (!p.isVideoSeekMode() && !a.getBody().isVideoMode()) {
					View v = focusSearch(this, (keyCode == KEYCODE_DPAD_LEFT) ? FOCUS_LEFT : FOCUS_RIGHT);
					if (v != null) {
						v.requestFocus();
						return true;
					} else {
						break;
					}
				}
				b = a.getMediaServiceBinder();
				b.onRwFfButtonClick(keyCode == KEYCODE_DPAD_RIGHT);
				a.getControlPanel().onVideoSeek();
				return true;
			}
			case KEYCODE_DPAD_UP -> {
				if ((a = getActivity().peek()) == null) break;
				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(true);
				a.getControlPanel().onVideoSeek();
				return true;
			}
			case KEYCODE_DPAD_DOWN -> {
				if ((a = getActivity().peek()) == null) break;
				p = a.getControlPanel();
				if (!p.isVideoSeekMode() && isVisible(p)) {
					View v = p.focusSearch();
					if (v != null) {
						v.requestFocus();
						return true;
					} else {
						break;
					}
				}
				b = a.getMediaServiceBinder();
				b.onRwFfButtonLongClick(false);
				a.getControlPanel().onVideoSeek();
				return true;
			}
		}

		return super.onKeyUp(keyCode, event);
	}

	private boolean onTouch(@NonNull MotionEvent e) {
		MainActivityDelegate a = getActivity().peek();
		if (a == null) return false;
		a.getControlPanel().onVideoViewTouch(this, e);
		return true;
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		if (createSurface.isDone() && !Collections.disjoint(prefChange, prefs)) {
			MainActivityDelegate a = getActivity().peek();
			if (a == null) return;
			MediaEngine eng = a.getMediaSessionCallback().getEngine();
			if (eng == null) return;
			PlayableItem i = eng.getSource();
			if ((i == null) || !i.isVideo()) return;

			if (prefs.contains(MediaPrefs.VIDEO_SCALE)) {
				setSurfaceSize(eng);
			} else if (prefs.contains(MediaPrefs.AUDIO_DELAY)) {
				eng.setAudioDelay(i.getPrefs().getAudioDelayPref());
			} else if (prefs.contains(MediaPrefs.SUB_DELAY)) {
				eng.setSubtitleDelay(i.getPrefs().getSubDelayPref());
			}
		}
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) {
			a.getMediaSessionCallback().removeVideoView(this);
			a.getLib().getPrefs().removeBroadcastListener(this);
		}
	}

	@Override
	public View focusSearch(View focused, int direction) {
		MainActivityDelegate a = getActivity().peek();
		if ((a == null) || !a.getBody().isBothMode()) return focused;

		if (direction == FOCUS_LEFT) {
			return MediaItemListView.focusSearchActive(getContext(), focused);
		} else if (direction == FOCUS_RIGHT) {
			NavBarView n = a.getNavBar();
			if (n.isRight()) return n.focusSearch();
		}

		return focused;
	}

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	private static abstract class SubDrawer {
		final float textScale;

		SubDrawer(float textScale) {this.textScale = textScale;}

		abstract boolean setText(SubGrid.Position position, @Nullable Subtitles.Text text);

		abstract void draw(Canvas canvas);

		void clr(Canvas canvas) {
			canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		}

		float textSize(int canvasHeight) {
			return textScale * canvasHeight / 25;
		}

		static TextPaint paint(Paint.Align align) {
			TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
			paint.setColor(Color.WHITE);
			paint.setTypeface(Typeface.DEFAULT);
			paint.setElegantTextHeight(true);
			paint.setTextAlign(align);
			return paint;
		}

		static CharSequence text(String text) {
			var idx = text.indexOf('<');
			if ((idx == -1) || (text.indexOf('>', idx) == -1)) return text;
			return fromHtml(text, FROM_HTML_MODE_LEGACY);
		}

		static StaticLayout layout(CharSequence text, TextPaint paint, int width) {
			return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width).setMaxLines(10)
					.setEllipsize(TextUtils.TruncateAt.END).setIncludePad(true).build();
		}
	}

	private static final class GridDrawer extends SubDrawer {
		private final String[] grid = new String[9];
		private final TextPaint[] paint = new TextPaint[3];
		private final float[] yoff = new float[]{1f, 0.5f, 0.f};


		private GridDrawer(float textScale) {
			super(textScale);
			for (int i = 0; i < 3; i++) {
				paint[i] =
						paint(i == 0 ? Paint.Align.LEFT : i == 1 ? Paint.Align.CENTER : Paint.Align.RIGHT);
			}
		}

		@Override
		public boolean setText(SubGrid.Position position, @Nullable Subtitles.Text text) {
			var t = (text == null) ? null : text.getText();
			int idx = position.ordinal();
			if (Objects.equals(grid[idx], t)) return false;
			grid[idx] = t;
			return true;
		}

		@Override
		public void draw(Canvas canvas) {
			var ch = canvas.getHeight();
			var cw = canvas.getWidth();
			var ts = textSize(ch);
			var x = new int[]{0, cw / 2, cw};
			var y = new int[]{ch, ch / 2, 0};

			for (int i = 0, g = 0; i < 3; i++, g += 3) {
				var l = grid[g] != null;
				var c = grid[g + 1] != null;
				var r = grid[g + 2] != null;
				int[] w = new int[3];

				if (c) {
					if (l) {
						if (r) {
							w[0] = w[1] = w[2] = cw / 3;
						} else {
							w[0] = w[1] = cw / 3;
						}
					} else if (r) {
						w[1] = w[2] = cw / 3;
					} else {
						w[1] = cw;
					}
				} else if (l) {
					if (r) w[0] = w[2] = cw / 2;
					else w[0] = cw;
				} else if (r) {
					w[2] = cw;
				} else {
					continue;
				}

				for (int j = 0; j < 3; j++) {
					if (w[j] == 0) continue;
					var t = text(grid[g + j]);
					paint[j].setTextSize(ts);
					var sl = layout(t, paint[j], w[j]);
					canvas.save();
					canvas.translate(x[j], y[i] - sl.getHeight() * yoff[i]);
					sl.draw(canvas);
					canvas.restore();
				}
			}
		}
	}

	private static final class DoubleSubDrawer extends SubDrawer {
		private final TextPaint paint;
		private String left;
		private String right;
		private boolean clearBoth;

		DoubleSubDrawer(float textScale) {
			super(textScale);
			paint = paint(Paint.Align.CENTER);
		}

		@Override
		boolean setText(SubGrid.Position position, @Nullable Subtitles.Text text) {
			if (text == null) {
				if (clearBoth) {
					clearBoth = false;
					if ((left == null) && (right == null)) return false;
					left = right = null;
				} else if (position == BOTTOM_LEFT) {
					if (left == null) return false;
					left = null;
				} else {
					if (right == null) return false;
					right = null;
				}
				return true;
			}

			var t = text.getText();
			var trans = text.getTranslation();

			if (trans != null) {
				if (Objects.equals(left, t) && Objects.equals(left, trans)) return false;
				left = t;
				right = trans;
				clearBoth = true;
			} else if (position == BOTTOM_LEFT) {
				if (Objects.equals(left, t)) return false;
				left = t;
			} else {
				if (Objects.equals(right, t)) return false;
				right = t;
			}

			return true;
		}

		@Override
		void draw(Canvas canvas) {
			CharSequence t;
			int start;
			int end;

			if (left != null) {
				if (right != null) {
					var l = text(left).toString();
					t = l + '\n' + text(right);
					start = l.length() + 1;
					end = t.length();
				} else {
					t = text(left).toString();
					start = end = 0;
				}
			} else if (right != null) {
				t = text(right).toString();
				start = 0;
				end = t.length();
			} else {
				return;
			}

			if (start != end) {
				var st = new SpannableString(t);
				st.setSpan(new ForegroundColorSpan(Color.RED), start, end, SPAN_EXCLUSIVE_EXCLUSIVE);
				t = st;
			}

			var ch = canvas.getHeight();
			var cw = canvas.getWidth();
			paint.setTextSize(textSize(ch));
			var sl = layout(t, paint, cw);
			canvas.translate(cw / 2f, ch - sl.getHeight());
			sl.draw(canvas);
		}
	}
}
