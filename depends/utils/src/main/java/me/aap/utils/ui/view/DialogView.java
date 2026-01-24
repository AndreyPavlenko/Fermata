package me.aap.utils.ui.view;

import static me.aap.utils.R.styleable.DialogView_listLayout;
import static me.aap.utils.R.styleable.DialogView_singleChoiceItemLayout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.R;

/**
 * @author Andrey Pavlenko
 */
@SuppressLint("ViewConstructor")
public class DialogView extends FrameLayout implements DialogInterface {
	private Runnable dismiss;

	private DialogView(Context context, int layout) {
		super(context);
		inflate(context, layout, this);
		findViewById(R.id.topPanel).setVisibility(GONE);
		findViewById(R.id.contentPanel).setVisibility(GONE);
		findViewById(R.id.customPanel).setVisibility(GONE);
		View b = findViewById(R.id.buttonPanel);
		b.setVisibility(GONE);
		b.findViewById(android.R.id.button1).setVisibility(GONE);
		b.findViewById(android.R.id.button2).setVisibility(GONE);
		b.findViewById(android.R.id.button3).setVisibility(GONE);
	}

	@Override
	public void cancel() {
		dismiss();
	}

	@Override
	public void dismiss() {
		if (dismiss != null) {
			dismiss.run();
			dismiss = null;
		}
	}

	@Override
	protected void onWindowVisibilityChanged(int visibility) {
		super.onWindowVisibilityChanged(visibility);
		if (visibility != VISIBLE) return;

		View v = findViewById(android.R.id.button1);
		if (!isFocusable(v)) v = findFocusable(this);
		if (v != null) v.post(v::requestFocus);
	}

	private static View findFocusable(ViewGroup parent) {
		for (int i = 0, n = parent.getChildCount(); i < n; i++) {
			View v = parent.getChildAt(i);
			if (v.getVisibility() != VISIBLE) continue;
			if (isFocusable(v)) return v;

			if (v instanceof ViewGroup) {
				v = findFocusable((ViewGroup) v);
				if (v != null) return v;
			}
		}

		return null;
	}

	private static boolean isFocusable(View v) {
		return (v.getVisibility() == VISIBLE) && v.isFocusable() && (v instanceof TextView);
	}

	public static abstract class Builder implements DialogBuilder {
		private final DialogView dialog;

		public Builder(Context context) {
			TypedArray ta = context.obtainStyledAttributes(new int[]{R.attr.materialAlertDialogTheme});
			int theme = ta.getResourceId(0, me.aap.utils.R.style.Theme_Utils_Base_AlertDialog);
			ta.recycle();
			Context ctx = new ContextThemeWrapper(context, theme);
			ta = ctx.obtainStyledAttributes(null,
					new int[]{android.R.attr.layout},
					androidx.appcompat.R.attr.alertDialogStyle,
					me.aap.utils.R.style.Theme_Utils_Base_AlertDialog_Style);
			@SuppressLint("PrivateResource")
			int layout = ta.getResourceId(0, R.layout.mtrl_alert_dialog);
			ta.recycle();
			dialog = new DialogView(ctx, layout);
		}

		@Override
		public Context getContext() {
			return dialog.getContext();
		}

		@Override
		public DialogBuilder setTitle(@Nullable Drawable icon, @Nullable CharSequence title) {
			View t = dialog.findViewById(R.id.topPanel);
			t.setVisibility(VISIBLE);
			t.findViewById(R.id.titleDividerNoCustom).setVisibility(VISIBLE);

			if (icon != null) {
				ImageView img = t.findViewById(android.R.id.icon);
				img.setImageDrawable(icon);
			}

			if (title != null) {
				TextView text = t.findViewById(R.id.alertTitle);
				text.setText(title);
			}

			return this;
		}

		@Override
		public DialogBuilder setMessage(@NonNull CharSequence message) {
			View c = dialog.findViewById(R.id.contentPanel);
			c.setVisibility(VISIBLE);
			TextView text = c.findViewById(android.R.id.message);
			text.setText(message);
			return this;
		}

		@Override
		public DialogBuilder setPositiveButton(@NonNull CharSequence text, @Nullable DialogInterface.OnClickListener listener) {
			return setButton(android.R.id.button1, BUTTON_POSITIVE, text, listener);
		}

		@Override
		public DialogBuilder setNegativeButton(@NonNull CharSequence text, @Nullable DialogInterface.OnClickListener listener) {
			return setButton(android.R.id.button2, BUTTON_NEGATIVE, text, listener);
		}

		@Override
		public DialogBuilder setNeutralButton(@NonNull CharSequence text, @Nullable DialogInterface.OnClickListener listener) {
			return setButton(android.R.id.button3, BUTTON_NEUTRAL, text, listener);
		}

		private DialogBuilder setButton(@IdRes int id, int which, @NonNull CharSequence text,
																		@Nullable DialogInterface.OnClickListener listener) {
			View p = dialog.findViewById(R.id.buttonPanel);
			p.setVisibility(VISIBLE);
			Button b = p.findViewById(id);
			b.setText(text);
			b.setVisibility(VISIBLE);
			b.setOnClickListener(v -> {
				dialog.dismiss();
				if (listener != null) listener.onClick(dialog, which);
			});
			return this;
		}

		@Override
		public DialogBuilder setSingleChoiceItems(@NonNull CharSequence[] items, int checkedItem,
																							@Nullable DialogInterface.OnClickListener listener) {
			Context ctx = getContext();
			TypedArray a = ctx.obtainStyledAttributes(null, me.aap.utils.R.styleable.DialogView,
					androidx.appcompat.R.attr.alertDialogStyle,
					me.aap.utils.R.style.Theme_Utils_Base_AlertDialog_Style);
			int layout = a.getResourceId(DialogView_listLayout, 0);
			int itemLayout = a.getResourceId(DialogView_singleChoiceItemLayout,
					R.layout.mtrl_alert_select_dialog_singlechoice);
			a.recycle();

			ListView list = (ListView) LayoutInflater.from(getContext()).inflate(layout, null);
			ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(ctx, itemLayout, android.R.id.text1, items);
			list.setAdapter(adapter);
			list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
			list.setOnItemClickListener((parent, view, position, id) -> {
				if (listener != null) listener.onClick(dialog, position);
				if (dialog.findViewById(R.id.buttonPanel).getVisibility() != VISIBLE) dialog.dismiss();
			});

			if (checkedItem > -1) {
				list.setItemChecked(checkedItem, true);
				list.setSelection(checkedItem);
			}

			ViewGroup p = dialog.findViewById(R.id.contentPanel);
			p.setVisibility(VISIBLE);

			View scroll = p.findViewById(R.id.scrollView);
			p = (ViewGroup) scroll.getParent();
			int idx = p.indexOfChild(scroll);
			p.removeViewAt(idx);

			DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
			int w = 0;
			int h = 0;
			int wMax = dm.widthPixels;
			int hMax = dm.heightPixels * 2 / 3;
			int wScroll = list.getVerticalScrollbarWidth();

			for (int i = 0; i < items.length; i++) {
				TextView item = (TextView) adapter.getView(i, null, list);
				item.measure(wMax, hMax);
				w = Math.max(w, item.getMeasuredWidth() + item.getPaddingLeft() + item.getPaddingRight()
						+ item.getCompoundDrawablePadding() + wScroll);
				h += item.getMeasuredHeight();
			}

			h = Math.min(h + list.getDividerHeight() * (items.length - 1), hMax);
			p.addView(list, idx, new ViewGroup.LayoutParams(Math.min(w, wMax), h));

			return this;
		}

		@Override
		public DialogBuilder setView(@LayoutRes int layout) {
			View p = dialog.findViewById(R.id.customPanel);
			p.setVisibility(VISIBLE);
			LayoutInflater.from(getContext()).inflate(layout, p.findViewById(R.id.custom));
			return this;
		}

		@Override
		public DialogBuilder setView(View view) {
			View p = dialog.findViewById(R.id.customPanel);
			p.setVisibility(VISIBLE);
			ViewGroup g = p.findViewById(R.id.custom);
			g.addView(view);
			return this;
		}

		public DialogView build(Runnable dismiss) {
			if (dialog.findViewById(R.id.contentPanel).getVisibility() == VISIBLE) {
				if (dialog.findViewById(R.id.topPanel).getVisibility() != VISIBLE) {
					dialog.findViewById(R.id.textSpacerNoTitle).setVisibility(VISIBLE);
				}
				if (dialog.findViewById(R.id.buttonPanel).getVisibility() != VISIBLE) {
					dialog.findViewById(R.id.textSpacerNoButtons).setVisibility(VISIBLE);
				}
			}

			dialog.dismiss = dismiss;
			return dialog;
		}
	}
}
