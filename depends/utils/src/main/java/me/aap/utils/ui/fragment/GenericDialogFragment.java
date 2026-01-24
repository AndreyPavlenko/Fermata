package me.aap.utils.ui.fragment;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.utils.R;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ImageButton;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
public class GenericDialogFragment extends GenericFragment {
	private BooleanConsumer consumer;
	private BooleanSupplier validator;
	private BooleanSupplier backHandler;

	public GenericDialogFragment() {
		this(ToolBarMediator.instance);
	}

	public GenericDialogFragment(ToolBarView.Mediator toolBarMediator) {
		setToolBarMediator(toolBarMediator);
	}

	public GenericDialogFragment(ToolBarView.Mediator toolBarMediator,
															 FloatingButton.Mediator floatingButtonMediator) {
		setToolBarMediator(toolBarMediator);
		setFloatingButtonMediator(floatingButtonMediator);
	}

	public void setDialogConsumer(BooleanConsumer consumer) {
		this.consumer = consumer;
	}

	public void setDialogValidator(BooleanSupplier validator) {
		this.validator = validator;
	}

	public void setBackHandler(BooleanSupplier backHandler) {
		this.backHandler = backHandler;
	}

	@Override
	public int getFragmentId() {
		return R.id.generic_dialog_fragment;
	}

	@Override
	public void switchingTo(@NonNull ActivityFragment newFragment) {
		super.switchingTo(newFragment);
	}

	@Override
	public boolean onBackPressed() {
		if ((backHandler != null) && backHandler.getAsBoolean()) return true;
		return super.onBackPressed();
	}

	protected void onOkButtonClick() {
		complete(true);
	}

	protected void onCloseButtonClick() {
		complete(false);
	}

	protected void complete(boolean ok) {
		BooleanConsumer c = consumer;
		consumer = null;
		validator = null;
		if (c != null) c.accept(ok);
	}

	protected int getOkButtonVisibility() {
		return ((validator == null) || validator.getAsBoolean()) ? VISIBLE : GONE;
	}

	@Override
	public void switchingFrom(@Nullable ActivityFragment currentFragment) {
		super.switchingFrom(currentFragment);
	}

	interface ToolBarMediator extends ToolBarView.Mediator.BackTitle {
		GenericDialogFragment.ToolBarMediator instance = new GenericDialogFragment.ToolBarMediator() {
		};

		@Override
		default void enable(ToolBarView tb, ActivityFragment f) {
			ToolBarView.Mediator.BackTitle.super.enable(tb, f);
			GenericDialogFragment p = (GenericDialogFragment) f;

			ImageButton b = createOkButton(tb, p);
			addView(tb, b, getOkButtonId());
			setOkButtonVisibility(b, getOkButtonVisibility(p));

			b = createCloseButton(tb, p);
			addView(tb, b, getCloseButtonId());
		}

		@Override
		default void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
			if (e == FRAGMENT_CONTENT_CHANGED) {
				ActivityFragment f = a.getActiveFragment();
				if (!(f instanceof GenericDialogFragment)) return;

				ImageButton b = tb.findViewById(getBackButtonId());
				GenericDialogFragment p = (GenericDialogFragment) f;
				b.setVisibility(getBackButtonVisibility(p));

				b = tb.findViewById(getOkButtonId());
				setOkButtonVisibility(b, getOkButtonVisibility(p));

				TextView t = tb.findViewById(getTitleId());
				t.setText(f.getTitle());
			}
		}

		default void onOkButtonClick(GenericDialogFragment f) {
			f.onOkButtonClick();
		}

		default void onCloseButtonClick(GenericDialogFragment f) {
			f.onCloseButtonClick();
		}

		@IdRes
		default int getOkButtonId() {
			return R.id.file_picker_ok;
		}

		@DrawableRes
		default int getOkButtonIcon() {
			return R.drawable.check;
		}

		default ImageButton createOkButton(ToolBarView tb, GenericDialogFragment f) {
			ImageButton b = new ImageButton(tb.getContext(), null,
					androidx.appcompat.R.attr.toolbarStyle);
			initButton(b, getOkButtonIcon(), v -> onOkButtonClick(f));
			return b;
		}

		default int getOkButtonVisibility(GenericDialogFragment f) {
			return f.getOkButtonVisibility();
		}

		default void setOkButtonVisibility(ImageButton b, int vis) {
			b.setVisibility(vis);

			if (vis == VISIBLE) {
				Animation shake = AnimationUtils.loadAnimation(b.getContext(), R.anim.shake_y_20);
				b.startAnimation(shake);
			} else {
				b.clearAnimation();
			}
		}

		@IdRes
		default int getCloseButtonId() {
			return R.id.file_picker_close;
		}

		@DrawableRes
		default int getCloseButtonIcon() {
			return R.drawable.close;
		}

		default ImageButton createCloseButton(ToolBarView tb, GenericDialogFragment f) {
			ImageButton b = new ImageButton(tb.getContext(), null,
					androidx.appcompat.R.attr.toolbarStyle);
			initButton(b, getCloseButtonIcon(), v -> onCloseButtonClick(f));
			return b;
		}
	}
}
