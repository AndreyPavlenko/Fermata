package me.aap.fermata.ui.activity;

import android.content.pm.ActivityInfo;
import android.text.TextWatcher;
import android.widget.EditText;

import androidx.annotation.Nullable;

import me.aap.utils.ui.activity.AppActivity;

/**
 * @author Andrey Pavlenko
 */
public interface FermataActivity extends AppActivity {

	boolean isCarActivity();

	void setRequestedOrientation(int requestedOrientation);

	@Nullable
	default EditText startInput(TextWatcher w) {
		return null;
	}

	default void stopInput(TextWatcher w) {
	}

	default boolean isInputActive() {
		return false;
	}
}
