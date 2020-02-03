package me.aap.fermata.pref;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import me.aap.fermata.R;
import me.aap.fermata.function.BiConsumer;
import me.aap.fermata.function.BooleanSupplier;
import me.aap.fermata.function.Consumer;
import me.aap.fermata.function.DoubleSupplier;
import me.aap.fermata.function.IntFunction;
import me.aap.fermata.function.IntSupplier;
import me.aap.fermata.function.Supplier;
import me.aap.fermata.function.ToIntFunction;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.menu.AppMenu;
import me.aap.fermata.ui.menu.AppMenuItem;
import me.aap.fermata.util.ChangeableCondition;
import me.aap.fermata.util.Utils;

import static me.aap.fermata.util.Utils.toPx;

/**
 * @author Andrey Pavlenko
 */
public class PreferenceView extends ConstraintLayout {
	private Opts opts;
	private PreferenceStore.Listener prefListener;
	private ChangeableCondition.Listener condListener;

	public PreferenceView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setPreference(PreferenceViewAdapter adapter,
														@Nullable Supplier<? extends PreferenceView.Opts> supplier) {
		if (this.opts != null) {
			if (opts.visibility != null) opts.visibility.setListener(null);
			if ((this.opts instanceof PrefOpts<?>) && (this.prefListener != null)) {
				(((PrefOpts<?>) this.opts).store).removeBroadcastListener(this.prefListener);
			}
		}

		this.prefListener = null;
		setOnClickListener(null);

		if (supplier == null) {
			this.opts = null;
			return;
		}

		this.opts = supplier.get();

		if (supplier instanceof PreferenceSet) {
			setPreferenceSet(adapter, (PreferenceSet) supplier, opts);
		} else if (opts instanceof BooleanOpts) {
			setBooleanPreference((BooleanOpts) opts);
		} else if (opts instanceof StringOpts) {
			setStringPreference((StringOpts) opts);
		} else if (opts instanceof IntOpts) {
			setIntPreference((IntOpts) opts);
		} else if (opts instanceof FloatOpts) {
			setFloatPreference((FloatOpts) opts);
		} else if (opts instanceof TimeOpts) {
			setTimePreference((TimeOpts) opts);
		} else if (opts instanceof ListOpts) {
			setListPreference((ListOpts) opts);
		} else {
			throw new IllegalArgumentException();
		}
	}

	public Opts getOpts() {
		return opts;
	}

	private void setPreferenceSet(PreferenceViewAdapter adapter, PreferenceSet set, Opts o) {
		setPreference(R.layout.set_pref_layout, o);
		setOnClickListener(v -> adapter.setPreferenceSet(set));
	}

	private void setBooleanPreference(BooleanOpts o) {
		setPreference(R.layout.boolean_pref_layout, o);
		CheckBox b = findViewById(R.id.pref_value);
		b.setChecked(o.store.getBooleanPref(o.pref));
		b.setOnCheckedChangeListener((v, checked) -> o.store.applyBooleanPref(o.pref, checked));
		setOnClickListener(v -> b.setChecked(!b.isChecked()));

		setPrefListener((s, p) -> {
			if (p.contains(o.pref)) b.setChecked(o.store.getBooleanPref(o.pref));
		});
	}

	private void setStringPreference(StringOpts o) {
		setPreference(R.layout.string_pref_layout, o);
		EditText t = findViewById(R.id.pref_footer);
		boolean[] ignoreChange = new boolean[1];
		t.setText(o.store.getStringPref(o.pref));
		t.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (!ignoreChange[0]) {
					ignoreChange[0] = true;
					o.store.applyStringPref(o.pref, s.toString());
					ignoreChange[0] = false;
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}


			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		if (o.hint != Resources.ID_NULL) t.setHint(o.hint);
		else if (o.stringHint != null) t.setHint(o.stringHint);

		setOnFocusChangeListener((v, has) -> {
			if (has) t.requestFocus();
		});

		setPrefListener((s, p) -> {
			if (!ignoreChange[0] && p.contains(o.pref)) t.setText(o.store.getStringPref(o.pref));
		});
	}

	private void setIntPreference(IntOpts o) {
		setNumberPreference(o, () -> String.valueOf(o.store.getIntPref(o.pref)),
				v -> o.store.applyIntPref(o.pref, Integer.parseInt(v)), String::valueOf,
				Integer::parseInt,
				null);
	}

	private void setFloatPreference(FloatOpts o) {
		setNumberPreference(o, () -> String.valueOf(o.store.getFloatPref(o.pref)),
				v -> o.store.applyFloatPref(o.pref, Float.parseFloat(v)),
				v -> String.valueOf(v * o.scale),
				v -> (int) (Float.parseFloat(v) / o.scale),
				null);
	}

	private void setTimePreference(TimeOpts o) {
		setNumberPreference(o, () -> Utils.timeToString(o.store.getIntPref(o.pref)),
				v -> o.store.applyIntPref(o.pref, Utils.stringToTime(v)),
				Utils::timeToString, Utils::stringToTime,
				(t, sb) -> {
					t.setInputType(InputType.TYPE_CLASS_TEXT);
					if (!o.editable) t.setKeyListener(null);
				});
	}

	private <S> void setNumberPreference(NumberOpts<S> o, Supplier<String> get,
																			 Consumer<String> set,
																			 IntFunction<String> fromInt,
																			 ToIntFunction<String> toInt,
																			 BiConsumer<EditText, SeekBar> viewConfigurator) {
		setPreference(R.layout.number_pref_layout, o);
		EditText t = findViewById(R.id.pref_value);
		SeekBar sb = findViewById(R.id.pref_footer);
		boolean[] ignoreChange = new boolean[1];
		String initValue = get.get();

		if (viewConfigurator != null) viewConfigurator.accept(t, sb);

		t.setEms(o.ems);
		t.setText(initValue);
		t.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (!ignoreChange[0] && (s.length() != 0)) {
					try {
						ignoreChange[0] = true;
						String value = s.toString();
						set.accept(value);
						sb.setProgress(toInt.applyAsInt(value));
					} catch (NumberFormatException ignore) {
					} finally {
						ignoreChange[0] = false;
					}
				}
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}


			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		if (o.seekMin != -1) sb.setMax(o.seekMin);
		if (o.seekMax != -1) sb.setMax(o.seekMax);
		sb.setProgress(toInt.applyAsInt(initValue));

		sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					t.setText(fromInt.apply(progress));
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});

		setOnFocusChangeListener((v, has) -> {
			if (has) sb.requestFocus();
		});

		setPrefListener((s, p) -> {
			if (!ignoreChange[0] && p.contains(o.pref)) t.setText(get.get());
		});
	}

	private void setListPreference(ListOpts o) {
		if (o.initList != null) o.initList.accept(o);
		setPreference(R.layout.list_pref_layout, o);
		Runnable formatTitle;
		Runnable formatSubtitle;

		if (o.formatTitle) {
			formatTitle = () -> formatListTitle(o, this::getTitleView, o.title);
			formatTitle.run();
			setPrefListener((s, p) -> {
				if (p.contains(o.pref)) formatTitle.run();
			});
		} else {
			formatTitle = null;
		}

		if (o.formatSubtitle) {
			formatSubtitle = () -> formatListTitle(o, this::getSubtitleView, o.subtitle);
			formatSubtitle.run();
			setPrefListener((s, p) -> {
				if (p.contains(o.pref)) formatSubtitle.run();
			});
		} else {
			formatSubtitle = null;
		}

		setOnClickListener(v -> {
			if (o.initList != null) o.initList.accept(o);
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			AppMenu menu = a.getContextMenu();
			int currentValue = o.store.getIntPref(o.pref);
			menu.hide();

			if (o.values != null) {
				Resources res = getContext().getResources();

				for (int i = 0; i < o.values.length; i++) {
					int id = (o.valuesMap == null) ? i : o.valuesMap[i];
					AppMenuItem item = menu.addItem(id, res.getString(o.values[i]));
					if (id == currentValue) menu.setSelectedItem(item);
				}
			} else {
				for (int i = 0; i < o.stringValues.length; i++) {
					int id = (o.valuesMap == null) ? i : o.valuesMap[i];
					AppMenuItem item = menu.addItem(id, o.stringValues[i]);
					if (id == currentValue) menu.setSelectedItem(item);
				}
			}

			menu.show(item -> {
				int id = item.getItemId();
				if (id == currentValue) return true;

				o.store.applyIntPref(o.pref, id);
				if (formatTitle != null) formatTitle.run();
				if (formatSubtitle != null) formatSubtitle.run();
				return true;
			});
		});
	}

	private void formatListTitle(ListOpts o, Supplier<TextView> text, @StringRes int resId) {
		Resources res = getContext().getResources();
		int value = o.store.getIntPref(o.pref);
		int idx = (o.valuesMap == null) ? value : Utils.indexOf(o.valuesMap, value);

		if (idx != -1) {
			if (o.values != null) {
				text.get().setText(res.getString(resId, res.getString(o.values[idx])));
			} else {
				text.get().setText(res.getString(resId, o.stringValues[idx]));
			}
		}
	}

	public void setPrefListener(PreferenceStore.Listener prefListener) {
		PreferenceStore.Listener current = this.prefListener;

		if (current != null) {
			this.prefListener = (s, p) -> {
				current.onPreferenceChanged(s, p);
				prefListener.onPreferenceChanged(s, p);
			};
			(((PrefOpts<?>) this.opts).store).removeBroadcastListener(current);
		} else {
			this.prefListener = prefListener;
		}

		(((PrefOpts<?>) this.opts).store).addBroadcastListener(this.prefListener);
	}

	public void setCondListener(ChangeableCondition.Listener condListener) {
		ChangeableCondition.Listener current = this.condListener;

		if (current != null) {
			this.condListener = (c) -> {
				current.onConditionChanged(c);
				condListener.onConditionChanged(c);
			};
		} else {
			this.condListener = condListener;
		}

		this.opts.visibility.setListener(this.condListener);
	}

	private void setPreference(@LayoutRes int layout, Opts opts) {
		removeAllViews();
		inflate(getContext(), layout, this);

		ImageView iconView = getIconView();
		TextView titleView = getTitleView();
		TextView subtitleView = getSubtitleView();
		titleView.setText(opts.title);

		if (opts.icon == Resources.ID_NULL) {
			iconView.setVisibility(GONE);
		} else {
			iconView.setVisibility(VISIBLE);
			iconView.setImageResource(opts.icon);
		}

		if (opts.subtitle == Resources.ID_NULL) {
			if (!(opts instanceof NumberOpts) && !(opts instanceof BooleanOpts)) {
				titleView.setPadding(0, toPx(8), 0, toPx(8));
			}

			subtitleView.setVisibility(GONE);
		} else {
			titleView.setPadding(0, 0, 0, 0);
			subtitleView.setVisibility(VISIBLE);
			subtitleView.setText(opts.subtitle);
		}

		if (opts.visibility != null) {
			setVisibility(opts.visibility.get() ? VISIBLE : GONE);
			setCondListener(c -> setVisibility(c.get() ? VISIBLE : GONE));
		} else {
			setVisibility(VISIBLE);
		}
	}

	private ImageView getIconView() {
		return (ImageView) getChildAt(0);
	}

	private TextView getTitleView() {
		return (TextView) getChildAt(1);
	}

	private TextView getSubtitleView() {
		return (TextView) getChildAt(2);
	}

	public static class Opts {
		@SuppressLint("InlinedApi")
		@DrawableRes
		public int icon = Resources.ID_NULL;
		@SuppressLint("InlinedApi")
		@StringRes
		public int title = Resources.ID_NULL;
		@SuppressLint("InlinedApi")
		@StringRes
		public int subtitle = Resources.ID_NULL;
		public ChangeableCondition visibility;
	}

	public static class PrefOpts<S> extends Opts {
		public PreferenceStore store;
		public PreferenceStore.Pref<S> pref;
	}

	public static class BooleanOpts extends PrefOpts<BooleanSupplier> {
	}

	public static class StringOpts extends PrefOpts<Supplier<String>> {
		@SuppressLint("InlinedApi")
		@StringRes
		public int hint = Resources.ID_NULL;
		public String stringHint;
	}

	public static class NumberOpts<S> extends PrefOpts<S> {
		public int seekMin = -1;
		public int seekMax = -1;
		public int ems = 2;
	}

	public static class IntOpts extends NumberOpts<IntSupplier> {
	}

	public static class FloatOpts extends NumberOpts<DoubleSupplier> {
		public float scale = 1f;
	}

	public static class TimeOpts extends NumberOpts<IntSupplier> {
		public boolean editable;
	}

	public static class ListOpts extends PrefOpts<IntSupplier> {
		public int[] values;
		public int[] valuesMap;
		public String[] stringValues;
		public boolean formatTitle;
		public boolean formatSubtitle;
		public Consumer<ListOpts> initList;
	}
}
