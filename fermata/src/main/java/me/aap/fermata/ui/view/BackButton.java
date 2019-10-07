package me.aap.fermata.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * @author Andrey Pavlenko
 */
public class BackButton extends ImageButton {
	private boolean filterMode;
	private int visibility;

	public BackButton(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void setVisibility(int visibility) {
		if (filterMode) this.visibility = visibility;
		else super.setVisibility(visibility);
	}

	void filterModeOn() {
		filterMode = true;
		visibility = getVisibility();
		super.setVisibility(VISIBLE);
	}

	void filterModeOff() {
		filterMode = false;
		super.setVisibility(visibility);
	}
}
