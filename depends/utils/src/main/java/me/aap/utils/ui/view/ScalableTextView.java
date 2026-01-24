package me.aap.utils.ui.view;

import static android.util.TypedValue.COMPLEX_UNIT_PX;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textview.MaterialTextView;

import me.aap.utils.ui.activity.ActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class ScalableTextView extends MaterialTextView {
	private final float initSize;

	public ScalableTextView(@NonNull Context context) {
		super(context);
		initSize = getTextSize();
		scale();
	}

	public ScalableTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		initSize = getTextSize();
		scale();
	}

	public ScalableTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initSize = getTextSize();
		scale();
	}

	public ScalableTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		initSize = getTextSize();
		scale();
	}

	@Override
	public void setTextAppearance(int resId) {
		super.setTextAppearance(resId);
		scale();
	}

	public void scale() {
		ActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> scale(a.getTextIconSize()));
	}

	public void scale(float scale) {
		if (scale != 0F) setTextSize(COMPLEX_UNIT_PX, initSize * scale);
	}
}
