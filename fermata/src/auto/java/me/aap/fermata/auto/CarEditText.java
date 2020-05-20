package me.aap.fermata.auto;

import android.content.Context;
import android.support.car.input.CarRestrictedEditText;
import android.util.AttributeSet;

import com.google.android.gms.car.input.CarEditable;
import com.google.android.gms.car.input.CarEditableListener;

/**
 * @author Andrey Pavlenko
 */
public class CarEditText extends CarRestrictedEditText implements CarEditable {
	public CarEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public void setCarEditableListener(final CarEditableListener listener) {
		super.setCarEditableListener(listener::onUpdateSelection);
	}
}
