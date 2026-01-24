package me.aap.utils.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;

import me.aap.utils.R;


/**
 * @author Andrey Pavlenko
 */
public class ImageButton extends AppCompatImageButton implements OnLongClickListener {
	private final Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.button_press);
	private OnLongClickListener longClickListener;
	private long enterPressedTime = -1;

	public ImageButton(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, androidx.appcompat.R.attr.imageButtonStyle);
	}

	public ImageButton(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		super.setOnLongClickListener(this);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (isEnabled()) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				startAnimation(animation);
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				setScaleX(1);
				setScaleY(1);
			}
		}

		return super.onTouchEvent(event);
	}

	@Override
	public boolean onLongClick(View v) {
		setScaleX(1.5f);
		setScaleY(1.5f);
		if (longClickListener != null) longClickListener.onLongClick(v);
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (isEnter(keyCode)) {
			if (enterPressedTime == -1) {
				startAnimation(animation);
				enterPressedTime = System.currentTimeMillis();
				return super.onKeyDown(keyCode, event);
			} else if (enterPressedTime >= (System.currentTimeMillis() - 500)) {
				onLongClick(this);
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (isEnter(keyCode)) {
			setScaleX(1);
			setScaleY(1);
			enterPressedTime = -1;
		}

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void setOnLongClickListener(@Nullable OnLongClickListener l) {
		longClickListener = l;
	}

	@Override
	protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
		if (visibility == VISIBLE) {
			setScaleX(1);
			setScaleY(1);
		}
		super.onVisibilityChanged(changedView, visibility);
	}

	private boolean isEnter(int keyCode) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_ENTER:
			case KeyEvent.KEYCODE_NUMPAD_ENTER:
			case KeyEvent.KEYCODE_SPACE:
				return true;
			default:
				return false;
		}
	}
}
