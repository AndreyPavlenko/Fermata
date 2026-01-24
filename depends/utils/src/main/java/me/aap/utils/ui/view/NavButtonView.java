package me.aap.utils.ui.view;

import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.view.NavBarView.POSITION_BOTTOM;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.android.material.textview.MaterialTextView;

import me.aap.utils.ui.UiUtils;

/**
 * @author Andrey Pavlenko
 */
@SuppressLint("ViewConstructor")
public class NavButtonView extends LinearLayoutCompat {
	private final NavBarView navBar;
	private final int pad;

	public NavButtonView(NavBarView navBar) {
		super(navBar.getContext());
		this.navBar = navBar;
		setClickable(true);
		setFocusable(true);
		setOrientation(VERTICAL);

		Context ctx = navBar.getContext();
		ImageView img = new AppCompatImageView(ctx);
		boolean bottom = (navBar.getPosition() == POSITION_BOTTOM);
		LayoutParams lp = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
		pad = toIntPx(ctx, 2);
		img.setPadding(0, pad, 0, pad);
		lp.weight = 1;
		lp.gravity = Gravity.CENTER;
		img.setLayoutParams(lp);
		img.setAlpha(0.5f);
		ImageViewCompat.setImageTintList(img, ColorStateList.valueOf(navBar.getTint()));
		addView(img);

		if (bottom) {
			TextView t = new MaterialTextView(ctx);
			lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
			lp.gravity = Gravity.CENTER;
			t.setLayoutParams(lp);
			t.setTextAppearance(navBar.textAppearance);
			t.setVisibility(GONE);
			addView(t);
		}
	}

	@Override
	public void setSelected(boolean selected) {
		super.setSelected(selected);
		ImageView i = getIcon();

		if (selected) {
			i.setAlpha(1f);
			i.setPadding(0, 0, 0, 0);
		} else {
			i.setAlpha(0.5f);
			i.setPadding(0, pad, 0, pad);
		}
		TextView t = getText();
		if (t != null) t.setVisibility(selected ? VISIBLE : GONE);
	}

	public void setIcon(Drawable icon) {
		getIcon().setImageDrawable(icon);
	}

	public void setText(CharSequence text) {
		TextView t = getText();
		if (t != null) t.setText(text);
	}

	public void setTextSize(float size) {
		TextView t = getText();
		if (t != null) t.setTextSize(COMPLEX_UNIT_PX, size);
	}

	public NavBarView getNavBar() {
		return navBar;
	}

	public ImageView getIcon() {
		return (ImageView) getChildAt(0);
	}

	@Nullable
	TextView getText() {
		return (getChildCount() == 2) ? (TextView) getChildAt(1) : null;
	}

	public static class Ext extends NavButtonView {
		private final Path path = new Path();
		private final CornerPathEffect corner;
		private boolean hasExt;

		public Ext(NavBarView navBar) {
			super(navBar);
			corner = new CornerPathEffect(toPx(navBar.getContext(), 1));
		}

		public void setHasExt(boolean hasExt) {
			this.hasExt = hasExt;
			invalidate();
		}

		@Override
		protected void onDraw(Canvas canvas) {
			if (!hasExt) return;

			Context ctx = getContext();
			NavBarView nb = getNavBar();
			super.onDraw(canvas);

			float w = toPx(ctx, 2);
			float len = toPx(ctx, 4);
			path.reset();

			if (getText() == null) {
				float y2 = getHeight() - 2 * w;
				float y1 = y2 - len;
				float x2 = getWidth() / 2f;
				float x1 = x2 - len;
				float x3 = x2 + len;
				path.moveTo(x1, y1);
				path.lineTo(x2, y2);
				path.lineTo(x3, y1);
			} else {
				float x2 = getWidth() - 2 * w;
				float x1 = x2 - len;
				float y2 = getHeight() / 2f;
				float y1 = y2 - len;
				float y3 = y2 + len;
				path.moveTo(x1, y1);
				path.lineTo(x2, y2);
				path.lineTo(x1, y3);
			}

			Paint paint = UiUtils.getPaint();
			paint.setPathEffect(corner);
			paint.setStyle(Paint.Style.STROKE);
			paint.setStrokeWidth(w);
			paint.setColor(nb.getTint());
			paint.setAntiAlias(true);
			canvas.drawPath(path, paint);
		}
	}
}
