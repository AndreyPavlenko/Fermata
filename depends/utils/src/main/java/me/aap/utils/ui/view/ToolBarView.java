package me.aap.utils.ui.view;

import static android.content.Context.INPUT_METHOD_SERVICE;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.RIGHT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
import static me.aap.utils.ui.UiUtils.getTextAppearanceSize;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.fragment.ViewFragmentMediator.attachMediator;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.textview.MaterialTextView;

import java.util.Collection;
import java.util.LinkedList;

import me.aap.utils.R;
import me.aap.utils.event.EventBroadcaster;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.ActivityListener;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.ViewFragmentMediator;

/**
 * @author Andrey Pavlenko
 */
public class ToolBarView extends ConstraintLayout implements ActivityListener,
		EventBroadcaster<ToolBarView.Listener> {
	@DimenRes
	private final int size;
	@StyleRes
	private final int textAppearance;
	@StyleRes
	private final int editTextAppearance;
	private Mediator mediator;
	private final Collection<ListenerRef<Listener>> listeners = new LinkedList<>();

	public ToolBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, androidx.appcompat.R.attr.toolbarStyle);
	}

	public ToolBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ToolBarView,
				androidx.appcompat.R.attr.toolbarStyle, R.style.Theme_Utils_Base_ToolBarStyle);
		size = ta.getLayoutDimension(R.styleable.ToolBarView_size, 0);
		textAppearance = ta.getResourceId(R.styleable.ToolBarView_textAppearance, 0);
		editTextAppearance = ta.getResourceId(R.styleable.ToolBarView_editTextAppearance, 0);
		setBackgroundColor(ta.getColor(R.styleable.ToolBarView_android_colorBackground, Color.TRANSPARENT));
		ta.recycle();

		ActivityDelegate a = getActivity();
		a.addBroadcastListener(this, Mediator.DEFAULT_EVENT_MASK);
		setMediator(a.getActiveFragment());
	}

	public void setSize(float scale) {
		Context ctx = getContext();
		float ts = getTextAppearanceSize(ctx, textAppearance) * scale;
		float ets = getTextAppearanceSize(ctx, editTextAppearance) * scale;
		ViewGroup.LayoutParams lp = getLayoutParams();
		lp.height = (int) (size * scale);
		setLayoutParams(lp);

		for (int i = 0, n = getChildCount(); i < n; i++) {
			View v = getChildAt(i);
			if (v instanceof EditText) ((EditText) v).setTextSize(COMPLEX_UNIT_PX, ets);
			else if (v instanceof TextView) ((TextView) v).setTextSize(COMPLEX_UNIT_PX, ts);
		}
	}

	public Mediator getMediator() {
		return mediator;
	}

	protected void setMediator(Mediator mediator) {
		this.mediator = mediator;
	}

	protected boolean setMediator(ActivityFragment f) {
		boolean attached = attachMediator(this, f, (f == null) ? null : f::getToolBarMediator,
				this::getMediator, this::setMediator);
		if (!attached || (f == null)) return false;
		float scale = f.getActivityDelegate().getToolBarSize();
		if (scale != 1F) setSize(scale);
		else getLayoutParams().height = size;
		return true;
	}

	public boolean onBackPressed() {
		Mediator m = getMediator();
		return (m != null) && m.onBackPressed(this);
	}

	public ActivityDelegate getActivity() {
		return ActivityDelegate.get(getContext());
	}

	public ActivityFragment getActiveFragment() {
		return getActivity().getActiveFragment();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent e) {
		return getActivity().interceptTouchEvent(e, super::onTouchEvent);
	}

	@Override
	public void onActivityEvent(ActivityDelegate a, long e) {
		if (!handleActivityDestroyEvent(a, e)) {
			if (e == FRAGMENT_CHANGED) {
				if (setMediator(a.getActiveFragment())) return;
			}

			Mediator m = getMediator();
			if (m != null) m.onActivityEvent(this, a, e);
		} else {
			Mediator m = getMediator();
			if (m != null) m.disable(this);
		}
	}

	public EditText getFilter() {
		if (mediator instanceof Mediator.BackTitleFilter) {
			return findViewById(((Mediator.BackTitleFilter) mediator).getFilterId());
		} else {
			return findViewById(R.id.tool_bar_filter);
		}
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return listeners;
	}

	public View focusSearch() {
		View v = findViewById(R.id.tool_bar_back_button);
		return ((v != null) && isVisible(v)) ? v : this;
	}

	@Override
	public View focusSearch(View focused, int direction) {
		View v = getMediator().focusSearch(this, focused, direction);
		return (v != null) ? v : super.focusSearch(focused, direction);
	}

	public interface Listener {
		byte FILTER_CHANGED = 1;

		void onToolBarEvent(ToolBarView tb, byte event);
	}

	public interface Mediator extends ViewFragmentMediator<ToolBarView> {

		@Override
		default void enable(ToolBarView tb, ActivityFragment f) {
			tb.setVisibility(VISIBLE);
		}

		@Override
		default void disable(ToolBarView tb) {
			tb.removeAllViews();
		}

		default boolean onBackPressed(ToolBarView tb) {
			return false;
		}

		@Nullable
		default View focusSearch(ToolBarView tb, View focused, int direction) {
			ActivityDelegate a = ActivityDelegate.get(tb.getContext());
			if (direction == FOCUS_DOWN) {
				if (a.getActiveMenu() instanceof View v) return v.findFocus();
			}
			if (direction != FOCUS_UP) return null;
			NavBarView nb = a.getNavBar();
			return nb.isBottom() && isVisible(nb) ? nb.focusSearch() : null;
		}

		default void addView(ToolBarView tb, View v, @IdRes int id) {
			addView(tb, v, id, RIGHT);
		}

		default void addView(ToolBarView tb, View v, @IdRes int id, int side) {
			ConstraintLayout.LayoutParams lp;
			v.setId(id);

			if (tb.getChildCount() == 0) {
				tb.addView(v);
				lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
				if (side == RIGHT) lp.endToEnd = PARENT_ID;
				else lp.startToStart = PARENT_ID;
			} else if (side == RIGHT) {
				View rv = tb.getChildAt(tb.getChildCount() - 1);
				ConstraintLayout.LayoutParams rlp = (ConstraintLayout.LayoutParams) rv.getLayoutParams();
				rlp.endToStart = id;
				rlp.endToEnd = UNSET;
				rlp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
				tb.addView(v);
				lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
				lp.endToEnd = PARENT_ID;
			} else {
				View lv = tb.getChildAt(0);
				ConstraintLayout.LayoutParams llp = (ConstraintLayout.LayoutParams) lv.getLayoutParams();
				llp.startToEnd = id;
				llp.startToStart = UNSET;
				llp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
				tb.addView(v, 0);
				lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
				lp.startToStart = PARENT_ID;
			}

			lp.topToTop = lp.bottomToBottom = PARENT_ID;
			lp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
		}

		default void addViewAt(ToolBarView tb, View v, @IdRes int id, int idx) {
			ConstraintLayout.LayoutParams lp = null;
			tb.addView(v, idx);
			int count = tb.getChildCount();
			v.setId(id);

			if (idx > 0) {
				View lv = tb.getChildAt(idx - 1);
				lp = (ConstraintLayout.LayoutParams) lv.getLayoutParams();
				lp.endToStart = id;
				lp.endToEnd = UNSET;
				lp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
				lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
				lp.startToEnd = lv.getId();
				lp.startToStart = UNSET;
			}
			if (idx < count - 1) {
				View rv = tb.getChildAt(idx + 1);
				lp = (ConstraintLayout.LayoutParams) rv.getLayoutParams();
				lp.startToEnd = id;
				lp.startToStart = UNSET;
				lp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
				lp = (ConstraintLayout.LayoutParams) v.getLayoutParams();
				lp.endToStart = rv.getId();
				lp.endToEnd = UNSET;
			}

			assert lp != null;
			lp.topToTop = lp.bottomToBottom = PARENT_ID;
			lp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
		}

		default ImageButton addButton(ToolBarView tb, @DrawableRes int icon, OnClickListener onClick,
																	@IdRes int id) {
			return addButton(tb, icon, onClick, id, RIGHT);
		}

		default ImageButton addButton(ToolBarView tb, @DrawableRes int icon, OnClickListener onClick,
																	@IdRes int id, int side) {
			ImageButton b = new ImageButton(tb.getContext(), null,
					androidx.appcompat.R.attr.toolbarStyle);
			initButton(b, icon, onClick);
			addView(tb, b, id, side);
			return b;
		}

		default <B extends ImageButton> B initButton(B b, @DrawableRes int icon, OnClickListener onClick) {
			ConstraintLayout.LayoutParams lp = setLayoutParams(b, 0, MATCH_PARENT);
			lp.horizontalWeight = 1;
			lp.dimensionRatio = "1:1";
			b.setImageResource(icon);
			b.setScaleType(ImageView.ScaleType.FIT_CENTER);
			b.setBackgroundResource(R.drawable.focusable_shape_transparent);
			if (onClick != null) b.setOnClickListener(onClick);
			setButtonPadding(b);
			return b;
		}

		default ConstraintLayout.LayoutParams setLayoutParams(View v, int width, int height) {
			ConstraintLayout.LayoutParams lp = new ConstraintLayout.LayoutParams(width, height);
			v.setLayoutParams(lp);
			return lp;
		}

		default void setButtonPadding(View v) {
			int pad = toIntPx(v.getContext(), 6);
			v.setPadding(pad, pad, pad, pad);
		}

		default EditText createEditText(ToolBarView tb) {
			Context ctx = tb.getContext();
			int p = (int) toPx(ctx, 5);
			EditText t = ActivityDelegate.get(ctx).createEditText(ctx);
			t.setTextAppearance(tb.editTextAppearance);
			t.setPadding(p, p, p, p);
			return t;
		}

		default Mediator join(Mediator m) {
			class Joint extends JointMediator<ToolBarView, Mediator> implements Mediator {
				public Joint(Mediator m1, Mediator m2) {
					super(m1, m2);
				}

				@Override
				public boolean onBackPressed(ToolBarView tb) {
					return m1.onBackPressed(tb) | m2.onBackPressed(tb);
				}
			}

			return new Joint(Mediator.this, m);
		}

		interface Invisible extends Mediator {
			Invisible instance = new Invisible() {
			};

			@Override
			default void enable(ToolBarView tb, ActivityFragment f) {
				tb.setVisibility(GONE);
			}
		}

		/**
		 * Back button and title.
		 */
		interface BackTitle extends Mediator, OnClickListener {
			BackTitle instance = new BackTitle() {
			};

			@Override
			default void enable(ToolBarView tb, ActivityFragment f) {
				Mediator.super.enable(tb, f);
				TextView t = createTitleText(tb);
				addView(tb, t, getTitleId(), LEFT);
				t.setText(f.getTitle());
				if (backOnTitleClick()) t.setOnClickListener(this);

				ImageButton b = createBackButton(tb);
				addView(tb, b, getBackButtonId(), LEFT);
				b.setVisibility(getBackButtonVisibility(f));
			}

			@Override
			default void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
				switch ((int) e) {
					case FRAGMENT_CHANGED:
					case FRAGMENT_CONTENT_CHANGED:
						ActivityFragment f = tb.getActiveFragment();
						if (f == null) return;
						ImageButton b = tb.findViewById(getBackButtonId());
						TextView t = tb.findViewById(getTitleId());
						b.setVisibility(getBackButtonVisibility(f));
						t.setText(f.getTitle());
						break;
				}
			}

			@Override
			default void onClick(View v) {
				if (v.getId() == getTitleId()) {
					ToolBarView tb = ActivityDelegate.get(v.getContext()).getToolBar();
					ForcedVisibilityButton b = tb.findViewById(getBackButtonId());
					if (!b.isVisible()) return;
				}

				ActivityDelegate.get(v.getContext()).onBackPressed();
			}

			@IdRes
			default int getTitleId() {
				return R.id.tool_bar_title;
			}

			default TextView createTitleText(ToolBarView tb) {
				Context ctx = tb.getContext();
				MaterialTextView t = new MaterialTextView(ctx, null,
						androidx.appcompat.R.attr.toolbarStyle);
				t.setTextAppearance(tb.textAppearance);
				t.setMaxLines(1);
				t.setFocusable(false);
				t.setEllipsize(TextUtils.TruncateAt.END);
				ConstraintLayout.LayoutParams lp = setLayoutParams(t, 0, WRAP_CONTENT);
				lp.horizontalWeight = 2;
				lp.endToEnd = PARENT_ID;
				return t;
			}

			default boolean backOnTitleClick() {
				return true;
			}

			@IdRes
			default int getBackButtonId() {
				return R.id.tool_bar_back_button;
			}

			@DrawableRes
			default int getBackButtonIcon() {
				return R.drawable.back;
			}

			default ForcedVisibilityButton createBackButton(ToolBarView tb) {
				ForcedVisibilityButton b = new ForcedVisibilityButton(tb.getContext(), null,
						androidx.appcompat.R.attr.toolbarStyle);
				initButton(b, getBackButtonIcon(), this);
				return b;
			}

			default int getBackButtonVisibility(ActivityFragment f) {
				return f.getActivityDelegate().isRootPage() ? GONE : VISIBLE;
			}
		}

		/**
		 * Back button, title and filter.
		 */
		interface BackTitleFilter extends BackTitle {
			BackTitleFilter instance = new BackTitleFilter() {
			};

			@Override
			default void enable(ToolBarView tb, ActivityFragment fr) {
				EditText f = createFilter(tb);
				f.setVisibility(GONE);
				addView(tb, f, getFilterId(), LEFT);

				BackTitle.super.enable(tb, fr);

				ForcedVisibilityButton b = createFilterButton(tb);
				addView(tb, b, getFilterButtonId());
				setFilterVisibility(tb, false);
			}


			default void setFilterVisibility(ToolBarView tb, boolean visible) {
				EditText f = tb.findViewById(getFilterId());
				TextView t = tb.findViewById(getTitleId());
				ForcedVisibilityButton bb = tb.findViewById(getBackButtonId());
				ForcedVisibilityButton fb = tb.findViewById(getFilterButtonId());
				ConstraintLayout.LayoutParams flp = (ConstraintLayout.LayoutParams) f.getLayoutParams();
				ConstraintLayout.LayoutParams tlp = (ConstraintLayout.LayoutParams) t.getLayoutParams();

				if (visible) {
					bb.forceVisibility(true);
					fb.setVisibility(GONE);
					t.setVisibility(GONE);
					f.setVisibility(VISIBLE);
					f.requestFocus();

					flp.horizontalWeight = 2;
					flp.startToEnd = getBackButtonId();
					flp.endToStart = getFilterButtonId();
					flp.startToStart = UNSET;
					flp.endToEnd = UNSET;

					tlp.horizontalWeight = 0;
					tlp.startToStart = UNSET;
					tlp.startToEnd = UNSET;
					tlp.endToStart = UNSET;
					tlp.endToEnd = UNSET;
				} else {
					bb.forceVisibility(false);
					fb.setVisibility(VISIBLE);
					t.setVisibility(VISIBLE);
					f.setVisibility(GONE);
					f.setText("");
					f.clearFocus();

					tlp.horizontalWeight = 2;
					tlp.startToEnd = getBackButtonId();
					tlp.endToStart = getFilterButtonId();
					tlp.startToStart = UNSET;
					tlp.endToEnd = UNSET;

					flp.horizontalWeight = 0;
					flp.startToStart = UNSET;
					flp.startToEnd = UNSET;
					flp.endToStart = UNSET;
					flp.endToEnd = UNSET;
				}

				tlp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
				flp.resolveLayoutDirection(LAYOUT_DIRECTION_LTR);
			}

			@Override
			default void onClick(View v) {
				if (v.getId() == getFilterButtonId()) {
					setFilterVisibility((ToolBarView) v.getParent(), true);
				} else {
					BackTitle.super.onClick(v);
				}
			}

			@Override
			default boolean onBackPressed(ToolBarView tb) {
				EditText f = tb.findViewById(getFilterId());
				if ((f != null) && (f.getVisibility() == VISIBLE)) {
					setFilterVisibility(tb, false);
					return true;
				} else {
					return false;
				}
			}

			@Override
			default void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
				if (e == FRAGMENT_CHANGED) setFilterVisibility(tb, false);
				BackTitle.super.onActivityEvent(tb, a, e);
			}

			@IdRes
			default int getFilterId() {
				return R.id.tool_bar_filter;
			}

			default EditText createFilter(ToolBarView tb) {
				EditText t = createEditText(tb);
				TextChangedListener l = s -> tb.fireBroadcastEvent(r -> r.onToolBarEvent(tb, Listener.FILTER_CHANGED));
				t.addTextChangedListener(l);
				t.setBackgroundResource(R.color.tool_bar_edittext_bg);
				setLayoutParams(t, 0, WRAP_CONTENT);
				t.setOnKeyListener((v,c,e)->{
					if (e.getAction() != KeyEvent.ACTION_DOWN) return false;

					switch (c) {
						case KEYCODE_DPAD_UP:
						case KEYCODE_DPAD_DOWN:
							var next = v.focusSearch(c == KEYCODE_DPAD_UP ? View.FOCUS_UP : View.FOCUS_DOWN);

							if (next != null) {
								next.requestFocus();
								return true;
							}
						case KeyEvent.KEYCODE_ENTER:
						case KeyEvent.KEYCODE_DPAD_CENTER:
						case KeyEvent.KEYCODE_NUMPAD_ENTER:
							setFilterVisibility(tb, false);
							var imm = (InputMethodManager) v.getContext().getSystemService(INPUT_METHOD_SERVICE);
							imm.hideSoftInputFromWindow(t.getWindowToken(), 0);
							return true;
						default:
							return false;
					}
				});
				return t;
			}

			@IdRes
			default int getFilterButtonId() {
				return R.id.tool_bar_filter_button;
			}

			@DrawableRes
			default int getFilterButtonIcon() {
				return R.drawable.filter;
			}

			default ForcedVisibilityButton createFilterButton(ToolBarView tb) {
				ForcedVisibilityButton b = new ForcedVisibilityButton(tb.getContext(), null,
						androidx.appcompat.R.attr.toolbarStyle);
				initButton(b, getFilterButtonIcon(), this);
				return b;
			}
		}
	}
}
