package me.aap.utils.ui;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Build.VERSION_CODES;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.aap.utils.R;
import me.aap.utils.app.App;
import me.aap.utils.async.Completed;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.ConcurrentUtils;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.Predicate;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceViewAdapter;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.GenericDialogFragment;

/**
 * @author Andrey Pavlenko
 */
public class UiUtils {
	public static final byte ID_NULL = 0;

	public static boolean isVisible(View v) {
		return v.getVisibility() == View.VISIBLE;
	}

	public static float toPx(Context ctx, int dp) {
		return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
	}

	public static int toIntPx(Context ctx, int dp) {
		return (int) toPx(ctx, dp);
	}

	public static int getTextAppearanceSize(Context ctx, @StyleRes int textAppearance) {
		TypedArray ta = ctx.obtainStyledAttributes(textAppearance, new int[]{android.R.attr.textSize});
		int size = ta.getDimensionPixelSize(0, 0);
		ta.recycle();
		return size;
	}

	public static Promise<Void> showAlert(Context ctx, @StringRes int msg) {
		return showAlert(ctx, ctx.getString(msg));
	}

	public static Promise<Void> showAlert(Context ctx, String msg) {
		Promise<Void> p = new Promise<>();
		ActivityDelegate.get(ctx).createDialogBuilder(ctx)
				.setTitle(android.R.drawable.ic_dialog_alert, android.R.string.dialog_alert_title)
				.setMessage(msg)
				.setPositiveButton(android.R.string.ok, (d, i) -> p.complete(null))
				.show();
		App.get().getHandler().submit(() -> {
			ActivityDelegate a = ActivityDelegate.get(ctx);
			View b = a.findViewById(android.R.id.button1);
			if (b != null) {
				b.requestFocus();
				b.setNextFocusUpId(android.R.id.button1);
				b.setNextFocusDownId(android.R.id.button1);
				b.setNextFocusLeftId(android.R.id.button1);
				b.setNextFocusRightId(android.R.id.button1);
			}
		});
		return p;
	}

	public static FutureSupplier<Void> showInfo(Context ctx, @StringRes int msg) {
		return showInfo(ctx, ctx.getString(msg));
	}

	public static FutureSupplier<Void> showInfo(Context ctx, String msg) {
		Promise<Void> p = new Promise<>();
		ActivityDelegate.get(ctx).createDialogBuilder(ctx)
				.setMessage(msg)
				.setPositiveButton(android.R.string.ok, (d, w) -> p.complete(null))
				.show();
		App.get().getHandler().submit(() -> {
			ActivityDelegate a = ActivityDelegate.get(ctx);
			View b = a.findViewById(android.R.id.button1);
			if (b != null) {
				b.requestFocus();
				b.setNextFocusUpId(android.R.id.button1);
				b.setNextFocusDownId(android.R.id.button1);
				b.setNextFocusLeftId(android.R.id.button1);
				b.setNextFocusRightId(android.R.id.button1);
			}
		});
		return p;
	}

	public static FutureSupplier<Void> showQuestion(
			Context ctx, @StringRes int title, @StringRes int msg, @DrawableRes int icon) {
		return showQuestion(ctx, ctx.getString(title), ctx.getString(msg),
				(icon == ID_NULL) ? null : AppCompatResources.getDrawable(ctx, icon));
	}

	public static FutureSupplier<Void> showQuestion(
			Context ctx, CharSequence title, CharSequence msg, @Nullable Drawable icon) {
		Promise<Void> p = new Promise<>();
		ActivityDelegate.get(ctx).createDialogBuilder(ctx)
				.setTitle(icon, title)
				.setMessage(msg)
				.setNegativeButton(android.R.string.cancel, (d, w) -> p.cancel())
				.setPositiveButton(android.R.string.ok, (d, w) -> p.complete(null))
				.show();
		App.get().getHandler().submit(() -> {
			ActivityDelegate a = ActivityDelegate.get(ctx);
			View b = a.findViewById(android.R.id.button1);
			if (b != null) {
				b.requestFocus();
				b.setNextFocusUpId(android.R.id.button1);
				b.setNextFocusDownId(android.R.id.button1);
				b.setNextFocusRightId(android.R.id.button2);
			}
			b = a.findViewById(android.R.id.button2);
			if (b != null) {
				b.setNextFocusUpId(android.R.id.button2);
				b.setNextFocusDownId(android.R.id.button2);
				b.setNextFocusLeftId(android.R.id.button1);
			}
		});
		return p;
	}

	public static FutureSupplier<String> queryText(Context ctx, @StringRes int title,
																								 @DrawableRes int icon) {
		return queryText(ctx, title, icon, "");
	}

	public static FutureSupplier<String> queryText(Context ctx, @StringRes int title,
																								 @DrawableRes int icon, CharSequence initText) {
		Promise<String> p = new Promise<>();
		ActivityDelegate a = ActivityDelegate.get(ctx);
		EditText text = a.createEditText(ctx);
		text.setSingleLine();
		text.setText(initText);
		text.setId(android.R.id.text1);
		text.setNextFocusUpId(android.R.id.button1);
		text.setNextFocusDownId(android.R.id.button1);
		text.setNextFocusLeftId(android.R.id.text1);
		text.setNextFocusRightId(android.R.id.text1);
		text.setOnKeyListener(UiUtils::dpadFocusHelper);
		a.createDialogBuilder(ctx)
				.setTitle(icon, title).setView(text)
				.setNegativeButton(android.R.string.cancel, (d, i) -> p.cancel())
				.setPositiveButton(android.R.string.ok, (d, i) -> p.complete(text.getText().toString()))
				.show();
		App.get().getHandler().submit(() -> {
			text.requestFocus();
			View b = a.findViewById(android.R.id.button1);
			if (b != null) {
				b.setNextFocusUpId(text.getId());
				b.setNextFocusDownId(text.getId());
				b.setNextFocusRightId(android.R.id.button2);
			}
			b = a.findViewById(android.R.id.button2);
			if (b != null) {
				b.setNextFocusUpId(text.getId());
				b.setNextFocusDownId(text.getId());
				b.setNextFocusLeftId(android.R.id.button1);
			}
		});
		return p;
	}

	public static FutureSupplier<PreferenceStore> queryPrefs(
			Context ctx, @StringRes int title, BiConsumer<PreferenceStore, PreferenceSet> builder,
			@Nullable Predicate<PreferenceStore> validator) {
		return queryPrefs(ctx, ctx.getString(title), builder, validator);
	}

	public static FutureSupplier<PreferenceStore> queryPrefs(
			Context ctx, String title, BiConsumer<PreferenceStore, PreferenceSet> builder,
			@Nullable Predicate<PreferenceStore> validator) {
		ActivityDelegate a = ActivityDelegate.get(ctx);
		int active = a.getActiveFragmentId();
		if (!(a.showFragment(
				me.aap.utils.R.id.generic_dialog_fragment) instanceof GenericDialogFragment f))
			return Completed.cancelled();
		Promise<PreferenceStore> p = new Promise<>();
		PreferenceStore store = new BasicPreferenceStore();
		f.setTitle(title);
		f.setContentProvider(g -> {
			PreferenceSet set = new PreferenceSet();
			RecyclerView v = new RecyclerView(ctx);
			v.setLayoutParams(new RecyclerView.LayoutParams(MATCH_PARENT, MATCH_PARENT));
			v.setHasFixedSize(true);
			v.setLayoutManager(new LinearLayoutManager(g.getContext()));
			v.setAdapter(new PreferenceViewAdapter(set));
			builder.accept(store, set);
			g.addView(v);
		});
		f.setBackHandler(() -> {
			p.cancel();
			return false;
		});
		f.setDialogConsumer(v -> {
			a.showFragment(active);
			if (v) p.complete(store);
			else p.cancel();
		});
		if (validator != null) {
			f.setDialogValidator(() -> validator.test(store));
			store.addBroadcastListener((st, pr) ->
					f.getToolBarMediator().onActivityEvent(a.getToolBar(), a, FRAGMENT_CONTENT_CHANGED));
		}
		return p;
	}

	public static boolean dpadFocusHelper(View v, int keyCode, KeyEvent event) {
		if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

		switch (keyCode) {
			case KEYCODE_DPAD_UP:
			case KEYCODE_DPAD_DOWN:
				View next = v.focusSearch(keyCode == KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);

				if (next != null) {
					next.requestFocus();
					return true;
				}
			default:
				return false;
		}
	}

	public static void addToClipboard(Context ctx, CharSequence label, CharSequence text) {
		ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(CLIPBOARD_SERVICE);
		clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
	}

	@IdRes
	public static int getArrayItemId(int idx) {
		return switch (idx) {
			case 0 -> R.id.array_item_id_0;
			case 1 -> R.id.array_item_id_1;
			case 2 -> R.id.array_item_id_2;
			case 3 -> R.id.array_item_id_3;
			case 4 -> R.id.array_item_id_4;
			case 5 -> R.id.array_item_id_5;
			case 6 -> R.id.array_item_id_6;
			case 7 -> R.id.array_item_id_7;
			case 8 -> R.id.array_item_id_8;
			case 9 -> R.id.array_item_id_9;
			default -> R.id.array_item_id_unknown;
		};
	}

	public static Bitmap getBitmap(Drawable d) {
		return getBitmap(d, 0, 0);
	}

	public static Bitmap getBitmap(Drawable d, int width, int height) {
		if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();

		if (d instanceof VectorDrawable) {
			return drawBitmap(d, Color.TRANSPARENT, Color.TRANSPARENT, 0, 0);
		}

		if (SDK_INT < VERSION_CODES.O) return null;
		if (!(d instanceof AdaptiveIconDrawable ad)) return null;

		Drawable bg = ad.getBackground();
		Drawable fg = ad.getForeground();
		LayerDrawable ld = new LayerDrawable(new Drawable[]{bg, fg});
		return drawBitmap(ld, Color.TRANSPARENT, Color.TRANSPARENT, width, height);
	}

	public static Bitmap drawBitmap(Drawable d, @ColorInt int bgColor, @ColorInt int fgColor) {
		return drawBitmap(d, bgColor, fgColor, 0, 0);
	}

	public static Bitmap drawBitmap(Drawable d, @ColorInt int bgColor, @ColorInt int fgColor,
																	int width, int height) {
		return drawBitmap(d, bgColor, fgColor, width, height, 0);
	}

	public static Bitmap drawBitmap(Drawable d, @ColorInt int bgColor, @ColorInt int fgColor,
																	int width, int height, float squeeze) {
		int w = (width != 0) ? width : d.getIntrinsicWidth();
		int h = (height != 0) ? height : d.getIntrinsicHeight();
		Bitmap bm = Bitmap.createBitmap(w, h, ARGB_8888);
		Canvas c = new Canvas(bm);

		if ((squeeze == 0) || (squeeze >= 1)) {
			d.setBounds(0, 0, w, h);
		} else {
			squeeze = 1 - squeeze;
			int dw = (int) (w * squeeze);
			int dh = (int) (h * squeeze);
			d.setBounds(dw, dh, w - dw, h - dh);
		}

		if (bgColor != Color.TRANSPARENT) bm.eraseColor(bgColor);
		if (fgColor != Color.TRANSPARENT) d.setTint(fgColor);
		d.draw(c);
		return bm;
	}

	public static Bitmap resizedBitmap(Bitmap bm, int maxSize) {
		int width = bm.getWidth();
		int height = bm.getHeight();
		if ((width <= maxSize) && (height <= maxSize)) return bm;
		var dims = adjustDims(width, height, maxSize);
		return Bitmap.createScaledBitmap(bm, dims[0], dims[1], true);
	}

	public static int[] adjustDims(int width, int height, int maxSize) {
		if ((width <= maxSize) && (height <= maxSize)) return new int[]{width, height};
		float ratio = (float) width / (float) height;
		if (ratio > 1) {
			width = maxSize;
			height = (int) (width / ratio);
		} else {
			height = maxSize;
			width = (int) (height * ratio);
		}
		return new int[]{width, height};
	}

	public static Paint getPaint() {
		ConcurrentUtils.ensureMainThread(true);
		Paint p = PaintHolder.paint;
		p.reset();
		return p;
	}

	@NonNull
	public static int[] getDrawableColor(Context ctx, @DrawableRes int id, int defaultColor) {
		var d = ContextCompat.getDrawable(ctx, id);
		if (d instanceof ColorDrawable c) {
			return new int[]{c.getColor()};
		} else if ((SDK_INT >= VERSION_CODES.N) && (d instanceof GradientDrawable g)) {
			var colors = g.getColors();
			return (colors == null) ? new int[]{defaultColor} : colors;
		} else {
			var bm = getBitmap(d);
			if (bm == null) return new int[]{defaultColor};
			int x = bm.getWidth();
			int y = bm.getHeight() / 2;
			return new int[]{bm.getPixel(0, y), bm.getPixel(x / 2, y), bm.getPixel(x - 1, y)};
		}
	}

	public static void drawGroupOutline(Canvas canvas, ViewGroup group, View label,
																			@ColorInt int backgroundColor,
																			@ColorInt int strokeColor, float strokeWidth,
																			float cornerRadius) {
		float w = group.getWidth();
		float h = group.getHeight();
		float x1 = label.getX();
		float x2 = x1 + label.getWidth();
		float y = label.getY() + label.getHeight() / 2f;
		float sw = strokeWidth / 2;

		Paint paint = getPaint();
		paint.setAntiAlias(true);
		paint.setStrokeWidth(strokeWidth);

		if (backgroundColor != Color.TRANSPARENT) {
			paint.setColor(backgroundColor);
			paint.setStyle(Paint.Style.FILL);
			canvas.drawRoundRect(0, h, w, y - sw, cornerRadius, cornerRadius, paint);
		}

		w -= sw;
		h -= sw;

		Path p = new Path();
		p.moveTo(x2, y);
		p.lineTo(w, y);
		p.lineTo(w, h);
		p.lineTo(sw, h);
		p.lineTo(sw, y);
		p.lineTo(x1, y);

		paint.setPathEffect(new CornerPathEffect(cornerRadius));
		paint.setStyle(Paint.Style.STROKE);
		paint.setColor(strokeColor);
		canvas.drawPath(p, paint);
	}

	public static void drawGroupOutline(Canvas canvas, ViewGroup group, View label1, View label2,
																			@ColorInt int backgroundColor,
																			@ColorInt int strokeColor, float strokeWidth,
																			float cornerRadius) {

		float w = group.getWidth();
		float h = group.getHeight();
		float x11 = label1.getX();
		float x12 = x11 + label1.getWidth();
		float x21 = label2.getX();
		float x22 = x21 + label2.getWidth();
		float y = label1.getY() + label1.getHeight() / 2f;
		float sw = strokeWidth / 2;

		Paint paint = getPaint();
		paint.setAntiAlias(true);
		paint.setStrokeWidth(strokeWidth);

		if (backgroundColor != Color.TRANSPARENT) {
			paint.setColor(backgroundColor);
			paint.setStyle(Paint.Style.FILL);
			canvas.drawRoundRect(0, h, w, y - sw, cornerRadius, cornerRadius, paint);
		}

		w -= sw;
		h -= sw;

		Path p = new Path();
		p.moveTo(x12, y);
		p.lineTo(x21, y);
		p.moveTo(x22, y);
		p.lineTo(w, y);
		p.lineTo(w, h);
		p.lineTo(sw, h);
		p.lineTo(sw, y);
		p.lineTo(x11, y);

		paint.setPathEffect(new CornerPathEffect(cornerRadius));
		paint.setStyle(Paint.Style.STROKE);
		paint.setColor(strokeColor);
		canvas.drawPath(p, paint);
	}

	private static final class PaintHolder {
		static final Paint paint = new Paint();
	}
}
