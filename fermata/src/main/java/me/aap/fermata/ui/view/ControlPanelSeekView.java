package me.aap.fermata.ui.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import me.aap.fermata.R;

/**
 * @author Andrey Pavlenko
 */
public class ControlPanelSeekView extends AppCompatSeekBar {
	private ConstraintSet constraints;
	private ConstraintSet constraintsNoSeek;

	public ControlPanelSeekView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public ControlPanelSeekView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	public void setEnabled(boolean enabled) {
		if (isEnabled() == enabled) return;

		ControlPanelView p = getPanel();
		View showHide = p.findViewById(R.id.show_hide_bars);
		View menu = p.findViewById(R.id.control_menu_button);
		View prev = p.findViewById(R.id.control_prev);
		View next = p.findViewById(R.id.control_next);

		if (enabled) {
			if (constraints == null) constraints = load(R.layout.control_panel_view);
			constraints.applyTo(p);
			prev.setNextFocusLeftId(R.id.control_next);
			showHide.setNextFocusLeftId(R.id.control_menu_button);
			next.setNextFocusRightId(R.id.control_prev);
			menu.setNextFocusRightId(R.id.show_hide_bars);
		} else {
			if (constraintsNoSeek == null) constraintsNoSeek = load(R.layout.control_panel_view2);
			constraintsNoSeek.applyTo(p);
			prev.setNextFocusLeftId(R.id.show_hide_bars);
			showHide.setNextFocusLeftId(R.id.control_menu_button);
			next.setNextFocusRightId(R.id.control_menu_button);
			menu.setNextFocusRightId(R.id.show_hide_bars);
		}

		super.setEnabled(enabled);
	}

	private ConstraintSet load(@LayoutRes int layout) {
		Context ctx = getContext();
		ConstraintLayout l = new ConstraintLayout(ctx);
		inflate(ctx, layout, l);
		ConstraintSet cs = new ConstraintSet();
		cs.clone(l);
		return cs;
	}

	private ControlPanelView getPanel() {
		return (ControlPanelView) getParent();
	}
}
