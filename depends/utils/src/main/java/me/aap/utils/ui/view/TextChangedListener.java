package me.aap.utils.ui.view;

import android.text.TextWatcher;

import androidx.annotation.Keep;

/**
 * @author Andrey Pavlenko
 */
public interface TextChangedListener extends TextWatcher {

	@Keep
	default void beforeTextChanged(CharSequence s, int start, int count, int after) {
	}

	@Keep
	default void onTextChanged(CharSequence s, int start, int before, int count) {
	}
}
