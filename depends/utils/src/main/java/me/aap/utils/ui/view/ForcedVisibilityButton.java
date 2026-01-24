package me.aap.utils.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public class ForcedVisibilityButton extends ImageButton {
	private int visibility;
	private boolean forced;

	public ForcedVisibilityButton(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public ForcedVisibilityButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public boolean isVisible() {
		return getVisibility() == VISIBLE;
	}

	@Override
	public void setVisibility(int visibility) {
		if (forced) this.visibility = visibility;
		else super.setVisibility(visibility);
	}

	public void forceVisibility(boolean force) {
		forced = force;

		if (force) {
			visibility = getVisibility();
			super.setVisibility(VISIBLE);
		} else {
			super.setVisibility(visibility);
		}
	}
}
