package me.aap.utils.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.TextViewCompat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import me.aap.utils.R;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.menu.OverlayMenuItemView;
import me.aap.utils.ui.menu.OverlayMenuView;

import static android.view.View.LAYOUT_DIRECTION_LTR;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.view.NavBarView.POSITION_BOTTOM;

/**
 * @author Andrey Pavlenko
 */
public abstract class CustomizableNavBarMediator implements NavBarView.Mediator,
		View.OnLongClickListener {
	private final List<NavBarItem> ext = new ArrayList<>();
	private NavButtonView.Ext extButton;
	protected NavBarView navBar;

	protected abstract Collection<NavBarItem> getItems(NavBarView nb);

	protected boolean swap(NavBarView nb, @IdRes int id1, @IdRes int id2) {
		return false;
	}

	protected boolean canSwap(NavBarView nb) {
		return false;
	}

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		navBar = nb;
		ext.clear();
		extButton = null;
		boolean selected = false;
		ActivityDelegate a = ActivityDelegate.get(nb.getContext());
		int activeId = a.getActiveNavItemId();
		if (activeId == ID_NULL) activeId = a.getActiveFragmentId();

		NavButtonView first = null;
		NavButtonView last = null;

		for (NavBarItem i : getItems(nb)) {
			if (i.isPinned()) {
				int id = i.getId();
				NavButtonView v = addButton(nb, i.getIcon(), i.getText(), id);
				if (id == activeId) {
					v.setSelected(true);
					selected = true;
				}
				if (first == null) first = v;
				last = v;
			} else {
				ext.add(i);
			}
		}

		if (ext.isEmpty()) {
			setFocus(nb, first, last);
			return;
		}

		if (!selected) {
			for (NavBarItem i : ext) {
				int id = i.getId();
				if (id == activeId) {
					extButton = createButton(nb, NavButtonView.Ext::new, i.getIcon(), i.getText());
					extButton.setSelected(true);
					extButton.setHasExt(ext.size() > 1);
					addView(nb, extButton, id, this);
					setFocus(nb, first, extButton);
					return;
				}
			}
		}

		NavBarItem i = ext.get(ext.size() - 1);
		extButton = createButton(nb, NavButtonView.Ext::new, i.getIcon(), i.getText());
		extButton.setHasExt(ext.size() > 1);
		addView(nb, extButton, i.getId(), this);
		setFocus(nb, first, extButton);
	}

	private void setFocus(NavBarView nb, View first, View last) {
		if ((first != null) && (last != null)) {
			if (nb.getPosition() == POSITION_BOTTOM) {
				first.setNextFocusLeftId(last.getId());
				last.setNextFocusRightId(first.getId());
			} else {
				first.setNextFocusUpId(last.getId());
				last.setNextFocusDownId(first.getId());
			}
		}
	}

	@Override
	public void disable(NavBarView nb) {
		NavBarView.Mediator.super.disable(nb);
		navBar = null;
		extButton = null;
	}

	@Override
	public void addView(NavBarView nb, View v, int id, View.OnClickListener onClick) {
		if (canSwap(nb)) v.setOnLongClickListener(this);
		NavBarView.Mediator.super.addView(nb, v, id, onClick);
	}

	@Override
	public void onClick(View v) {
		if ((v != extButton) || (ext.size() <= 1)) {
			NavBarView.Mediator.super.onClick(v);
			return;
		}

		NavBarView nb = (NavBarView) v.getParent();
		OverlayMenuView menu = createOverlayMenu(nb, false);
		ColorStateList tint = extButton.getIcon().getImageTintList();
		menu.show(b -> {
			int selectedId = extButton.getId();
			OverlayMenuItemView selected = null;

			for (NavBarItem i : ext) {
				int id = i.getId();
				OverlayMenuItemView item = (OverlayMenuItemView) b.addItem(id,
						i.getIcon(), i.getText()).setData(i);
				item.setTextColor(tint);
				TextViewCompat.setCompoundDrawableTintList(item, tint);
				if (id == selectedId) selected = item;
			}

			if (selected != null) b.setSelectedItem(selected);
			b.setSelectionHandler(this::extItemSelected);
			b.setCloseHandlerHandler(m -> ((ViewGroup) (nb.getParent())).removeView((View) m));
		});
	}

	@Override
	public boolean onLongClick(View v) {
		NavBarView nb = (NavBarView) v.getParent();
		NavButtonView btn = (NavButtonView) v;
		int idx = nb.indexOfChild(v);
		int count = nb.getChildCount();
		OverlayMenuView menu = createOverlayMenu(nb, true);
		ColorStateList tint = btn.getIcon().getImageTintList();
		menu.show(b -> {
			if (nb.getPosition() == POSITION_BOTTOM) {
				if (idx != 0) {
					OverlayMenuItemView item = (OverlayMenuItemView)
							b.addItem(R.id.left, R.drawable.move_left, R.string.move_left);
					item.setHandler(i -> swap(nb, btn.getId(), nb.getChildAt(idx - 1).getId()));
					item.setTextColor(tint);
					TextViewCompat.setCompoundDrawableTintList(item, tint);
				}
				if (idx != (count - 1)) {
					OverlayMenuItemView item = (OverlayMenuItemView)
							b.addItem(R.id.right, R.drawable.move_right, R.string.move_right);
					item.setHandler(i -> swap(nb, btn.getId(), nb.getChildAt(idx + 1).getId()));
					item.setTextColor(tint);
					TextViewCompat.setCompoundDrawableTintList(item, tint);
				}
			} else {
				if (idx != 0) {
					OverlayMenuItemView item = (OverlayMenuItemView)
							b.addItem(R.id.up, R.drawable.move_up, R.string.move_up);
					item.setHandler(i -> swap(nb, btn.getId(), nb.getChildAt(idx - 1).getId()));
					item.setTextColor(tint);
					TextViewCompat.setCompoundDrawableTintList(item, tint);
				}
				if (idx != (count - 1)) {
					OverlayMenuItemView item = (OverlayMenuItemView)
							b.addItem(R.id.down, R.drawable.move_down, R.string.move_down);
					item.setHandler(i -> swap(nb, btn.getId(), nb.getChildAt(idx + 1).getId()));
					item.setTextColor(tint);
					TextViewCompat.setCompoundDrawableTintList(item, tint);
				}
			}
			b.setCloseHandlerHandler(m -> ((ViewGroup) (nb.getParent())).removeView((View) m));
		});

		return true;
	}

	protected OverlayMenuView createOverlayMenu(NavBarView nb, boolean center) {
		Context ctx = nb.getContext();
		ViewGroup parent = (ViewGroup) nb.getParent();
		OverlayMenuView menu = new OverlayMenuView(ctx, null);
		parent.addView(menu);
		ViewGroup.LayoutParams lp = menu.getLayoutParams();
		menu.setElevation(toPx(ctx, 10));
		menu.setBackgroundColor(nb.getBgColor());

		if (lp instanceof ConstraintLayout.LayoutParams) {
			ConstraintLayout.LayoutParams clp = (ConstraintLayout.LayoutParams) lp;

			if (nb.getPosition() == POSITION_BOTTOM) {
				if (center) clp.startToStart = PARENT_ID;
				clp.endToEnd = PARENT_ID;
				clp.bottomToTop = nb.getId();
			} else if (nb.getPosition() == NavBarView.POSITION_LEFT) {
				if (center) clp.topToTop = PARENT_ID;
				clp.startToEnd = nb.getId();
				clp.bottomToBottom = PARENT_ID;
			} else {
				if (center) clp.topToTop = PARENT_ID;
				clp.endToStart = nb.getId();
				clp.bottomToBottom = PARENT_ID;
			}

			clp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
		}

		lp.height = WRAP_CONTENT;
		lp.width = WRAP_CONTENT;
		return menu;
	}

	protected boolean extItemSelected(OverlayMenuItem item) {
		NavBarItem i = item.getData();
		NavButtonView.Ext ext = setExtButton(null, i);
		itemSelected(ext, i.getId(), ActivityDelegate.get(ext.getContext()));
		return true;
	}

	@Override
	public void fragmentChanged(NavBarView nb, ActivityDelegate a, ActivityFragment f) {
		int id = f.getFragmentId();
		View v = nb.findViewById(id);

		if (v == null) {
			for (NavBarItem i : ext) {
				if (id == i.getId()) {
					v = setExtButton(nb, i);
					break;
				}
			}
		}

		if (v == null) return;

		v.setSelected(true);
		View active = nb.findViewById(a.getActiveNavItemId());

		if (active == null) {
			a.setActiveNavItemId(id);
		} else if (v != active) {
			a.setActiveNavItemId(id);
			active.setSelected(false);
		}
	}

	protected NavButtonView.Ext setExtButton(@Nullable NavBarView nb, NavBarItem i) {
		if (extButton == null) {
			assert nb != null;
			extButton = createButton(nb, NavButtonView.Ext::new, i.getIcon(), i.getText());
			extButton.setHasExt(ext.size() > 1);
			addView(nb, extButton, i.getId(), this);
		} else {
			extButton.setId(i.getId());
			extButton.getIcon().setImageDrawable(i.getIcon());
			extButton.setText(i.getText());
		}

		return extButton;
	}

	@Nullable
	protected NavButtonView.Ext getExtButton() {
		return extButton;
	}
}
