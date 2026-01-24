package me.aap.utils.ui.view;

import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.ArrayRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import me.aap.utils.R;
import me.aap.utils.ui.menu.OverlayMenu;

/**
 * @author Andrey Pavlenko
 */
public interface DialogBuilder {

	Context getContext();

	default DialogBuilder setTitle(@StringRes int title) {
		return setTitle(ID_NULL, title);
	}

	default DialogBuilder setTitle(@DrawableRes int icon, @StringRes int title) {
		Context ctx = getContext();
		Drawable i = (icon != ID_NULL) ? AppCompatResources.getDrawable(ctx, icon) : null;
		CharSequence t = (title != ID_NULL) ? ctx.getString(title) : null;
		return setTitle(i, t);
	}

	default DialogBuilder setTitle(@NonNull CharSequence title) {
		return setTitle(null, title);
	}

	DialogBuilder setTitle(@Nullable Drawable icon, @Nullable CharSequence title);

	default DialogBuilder setMessage(@StringRes int message) {
		return setMessage(getContext().getString(message));
	}

	DialogBuilder setMessage(@NonNull CharSequence message);

	default DialogBuilder setPositiveButton(@StringRes int text,
																					@Nullable DialogInterface.OnClickListener listener) {
		return setPositiveButton(getContext().getString(text), listener);
	}

	DialogBuilder setPositiveButton(@NonNull CharSequence text,
																	@Nullable DialogInterface.OnClickListener listener);

	default DialogBuilder setNegativeButton(@StringRes int text,
																					@Nullable DialogInterface.OnClickListener listener) {
		return setNegativeButton(getContext().getString(text), listener);
	}

	DialogBuilder setNegativeButton(@NonNull CharSequence text,
																	@Nullable DialogInterface.OnClickListener listener);

	default DialogBuilder setNeutralButton(@StringRes int text,
																				 @Nullable DialogInterface.OnClickListener listener) {
		return setNeutralButton(getContext().getString(text), listener);
	}

	DialogBuilder setNeutralButton(@NonNull CharSequence text,
																 @Nullable DialogInterface.OnClickListener listener);

	default DialogBuilder setSingleChoiceItems(@ArrayRes int itemsId, int checkedItem,
																						 @Nullable DialogInterface.OnClickListener listener) {
		return setSingleChoiceItems(getContext().getResources().getTextArray(itemsId), checkedItem, listener);
	}

	DialogBuilder setSingleChoiceItems(@NonNull CharSequence[] items, int checkedItem,
																		 @Nullable DialogInterface.OnClickListener listener);

	DialogBuilder setView(@LayoutRes int layout);

	DialogBuilder setView(View view);

	void show();

	static DialogBuilder create(OverlayMenu menu) {
		return new DialogView.Builder(menu.getContext()) {
			@Override
			public void show() {
				menu.show(b -> b.setView(build(menu::hide)));
			}
		};
	}

	static DialogBuilder create(Context ctx) {
		TypedArray ta = ctx.obtainStyledAttributes(
				new int[]{com.google.android.material.R.attr.materialAlertDialogTheme});
		int theme = ta.getResourceId(0, R.style.Theme_Utils_Base_AlertDialog);
		ta.recycle();

		return new DialogBuilder() {
			private final AlertDialog.Builder b = new MaterialAlertDialogBuilder(ctx, theme);

			@Override
			public Context getContext() {
				return ctx;
			}

			@Override
			public DialogBuilder setTitle(@Nullable Drawable icon, @Nullable CharSequence title) {
				if (icon != null) b.setIcon(icon);
				if (title != null) b.setTitle(title);
				return this;
			}

			@Override
			public DialogBuilder setMessage(@NonNull CharSequence message) {
				b.setMessage(message);
				return this;
			}

			@Override
			public DialogBuilder setPositiveButton(@NonNull CharSequence text, @Nullable DialogInterface.OnClickListener listener) {
				b.setPositiveButton(text, listener);
				return this;
			}

			@Override
			public DialogBuilder setNegativeButton(@NonNull CharSequence text, @Nullable DialogInterface.OnClickListener listener) {
				b.setNegativeButton(text, listener);
				return this;
			}

			@Override
			public DialogBuilder setNeutralButton(@NonNull CharSequence text, @Nullable DialogInterface.OnClickListener listener) {
				b.setNeutralButton(text, listener);
				return this;
			}

			@Override
			public DialogBuilder setSingleChoiceItems(@NonNull CharSequence[] items, int checkedItem, @Nullable DialogInterface.OnClickListener listener) {
				b.setSingleChoiceItems(items, checkedItem, listener);
				return this;
			}

			@Override
			public DialogBuilder setView(int layout) {
				b.setView(layout);
				return this;
			}

			@Override
			public DialogBuilder setView(View view) {
				b.setView(view);
				return this;
			}

			@Override
			public void show() {
				b.show();
			}
		};
	}
}
