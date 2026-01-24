package me.aap.utils.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.appcompat.widget.LinearLayoutCompat;

import me.aap.utils.R;

import static me.aap.utils.ui.UiUtils.drawGroupOutline;

/**
 * @author Andrey Pavlenko
 */
public class OutlinedLinearLayout extends LinearLayoutCompat {
	@ColorInt
	private final int bgColor;
	@ColorInt
	private final int strokeColor;
	private final float strokeWidth;
	private final float cornerRadius;
	private final byte numLabels;

	public OutlinedLinearLayout(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);

		TypedArray ta = ctx.obtainStyledAttributes(attrs, R.styleable.OutlinedLinearLayout);
		bgColor = ta.getColor(R.styleable.OutlinedLinearLayout_backgroundColor, Color.TRANSPARENT);
		strokeColor = ta.getColor(R.styleable.OutlinedLinearLayout_boxStrokeColor, Color.BLACK);
		strokeWidth = ta.getDimension(R.styleable.OutlinedLinearLayout_boxStrokeWidth, 2);
		cornerRadius = ta.getDimension(R.styleable.OutlinedLinearLayout_cornerRadius, 5);
		numLabels = (byte) ta.getInt(R.styleable.OutlinedLinearLayout_numLabels, 1);
		ta.recycle();
		setBackgroundColor(Color.TRANSPARENT);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (numLabels == 1) {
			drawGroupOutline(canvas, this, getChildAt(0), bgColor, strokeColor, strokeWidth, cornerRadius);
		} else {
			drawGroupOutline(canvas, this, getChildAt(0), getChildAt(1),
					bgColor, strokeColor, strokeWidth, cornerRadius);
		}
	}
}
