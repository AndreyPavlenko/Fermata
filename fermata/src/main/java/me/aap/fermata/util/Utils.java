package me.aap.fermata.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;

import androidx.annotation.StringRes;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;
import me.aap.utils.ui.menu.OverlayMenu;

import static android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.UiUtils.toPx;

/**
 * @author Andrey Pavlenko
 */
public class Utils {

	public static Uri getResourceUri(Context ctx, int resourceId) {
		Resources res = ctx.getResources();
		return new Uri.Builder()
				.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
				.authority(res.getResourcePackageName(resourceId))
				.appendPath(res.getResourceTypeName(resourceId))
				.appendPath(res.getResourceEntryName(resourceId))
				.build();
	}

	public static Uri getAudioUri(Uri documentUri) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;

		try {
			Uri uri = MediaStore.getMediaUri(FermataApplication.get(), documentUri);
			if (uri == null) return null;

			try (Cursor c = FermataApplication.get().getContentResolver().query(uri,
					new String[]{MediaStore.MediaColumns._ID}, null, null, null)) {
				if ((c != null) && c.moveToNext()) {
					return ContentUris.withAppendedId(EXTERNAL_CONTENT_URI, c.getLong(0));
				}
			}
		} catch (Exception ex) {
			Log.d(ex, "Failed to get audio Uri for ", documentUri);
		}

		return null;
	}

	public static boolean isVideoFile(String fileName) {
		return (fileName != null) && isVideoMimeType(FileUtils.getMimeType(fileName));
	}

	public static boolean isVideoMimeType(String mime) {
		return (mime != null) && mime.startsWith("video/");
	}

	public static void showAlert(Context ctx, @StringRes int msg) {
		showAlert(ctx, ctx.getString(msg));
	}

	public static void showAlert(Context ctx, String msg) {
		OverlayMenu menu = MainActivityDelegate.get(ctx).getContextMenu();
		LinearLayoutCompat v = new LinearLayoutCompat(ctx);
		v.setOrientation(LinearLayoutCompat.VERTICAL);

		TypedArray ta = ctx.obtainStyledAttributes(null, new int[]{me.aap.utils.R.attr.popupMenuStyle,
						me.aap.utils.R.attr.tint},
				me.aap.utils.R.attr.popupMenuStyle, me.aap.utils.R.style.Theme_Utils_Base_PopupMenuStyle);
		int style = ta.getResourceId(0, me.aap.utils.R.style.Theme_Utils_Base_PopupMenuStyle);
		ColorStateList buttonColor = ta.getColorStateList(1);
		ta.recycle();

		int p1 = toIntPx(ctx, 20);
		int p2 = toIntPx(ctx, 15);
		MaterialTextView text = new MaterialTextView(ctx, null, me.aap.utils.R.attr.popupMenuStyle, style);
		LinearLayoutCompat.LayoutParams lp = new LinearLayoutCompat.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		lp.setMargins(p1, p2, p1, p2);
		text.setLayoutParams(lp);
		text.setText(msg);
		v.addView(text);

		MaterialButton ok = new MaterialButton(ctx, null, me.aap.utils.R.attr.popupMenuStyle);
		p1 = toIntPx(ctx, 15);
		p2 = toIntPx(ctx, 10);
		lp = new LinearLayoutCompat.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		lp.setMargins(p1, p2, p1, p2);
		lp.gravity = Gravity.END;
		ok.setLayoutParams(lp);
		p1 = toIntPx(ctx, 16);
		p2 = toIntPx(ctx, 5);
		ok.setPadding(p1, p2, p1, p2);
		ok.setText(android.R.string.ok);
		ok.setCornerRadius((int) toPx(ctx, 5));
		ok.setStrokeWidth(toIntPx(ctx, 1));
		ok.setStrokeColor(buttonColor);
		ok.setTextColor(buttonColor);
		ok.setBackgroundColor(Color.TRANSPARENT);
		ok.setOnClickListener(b -> menu.hide());
		v.addView(ok);

		menu.show(b -> b.setView(v));
	}
}
