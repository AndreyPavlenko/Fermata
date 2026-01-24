package me.aap.utils.pref;

import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT_TREE;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static android.os.Build.VERSION.SDK_INT;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.misc.MiscUtils.isSafSupported;
import static me.aap.utils.text.TextUtils.isBlank;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.fragment.FilePickerFragment.FILE_OR_FOLDER;
import static me.aap.utils.ui.fragment.FilePickerFragment.WRITABLE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;

import me.aap.utils.R;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BiConsumer;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntFunction;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.function.ToIntFunction;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.log.Log;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.FilePickerFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;
import me.aap.utils.vfs.local.LocalFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class PreferenceView extends ConstraintLayout {
	private final ColorStateList textTint;
	@StyleRes
	private final int titleTextAppearance;
	@StyleRes
	private final int subtitleTextAppearance;
	private Opts opts;
	private PreferenceViewAdapter adapter;
	private Supplier<? extends PreferenceView.Opts> supplier;
	private PreferenceStore.Listener prefListener;
	private ChangeableCondition.Listener condListener;

	public PreferenceView(Context ctx) {
		this(ctx, null);
	}

	public PreferenceView(Context ctx, AttributeSet attrs) {
		super(ctx, attrs, android.R.attr.preferenceStyle);
		TypedArray ta = ctx.obtainStyledAttributes(attrs, R.styleable.PreferenceView,
				android.R.attr.preferenceStyle, R.style.Theme_Utils_Base_PreferenceStyle);
		textTint = ta.getColorStateList(R.styleable.PreferenceView_android_textColor);
		titleTextAppearance = ta.getResourceId(R.styleable.PreferenceView_titleTextAppearance, 0);
		subtitleTextAppearance = ta.getResourceId(R.styleable.PreferenceView_subtitleTextAppearance,
				0);
		ta.recycle();
	}

	void setSize(Context ctx, float scale) {
		setTextAppearance(ctx, getTitleView(), titleTextAppearance, scale);
		setTextAppearance(ctx, getSubtitleView(), subtitleTextAppearance, scale);
		setFooterTextAppearance(ctx, scale);
	}

	private void setTextAppearance(Context ctx, TextView v, @StyleRes int res, float scale) {
		v.setTextAppearance(res);
		TypedArray ta = ctx.obtainStyledAttributes(res, new int[]{android.R.attr.textSize});
		v.setTextSize(COMPLEX_UNIT_PX, ta.getDimensionPixelSize(0, 0) * scale);
		ta.recycle();
		v.setTextColor(textTint);
	}

	private void setFooterTextAppearance(Context ctx, float scale) {
		for (int i = 2, n = getChildCount(); i < n; i++) {
			View v = getChildAt(i);
			if (v instanceof TextView)
				setTextAppearance(ctx, (TextView) v, subtitleTextAppearance, scale);
		}
	}

	void cleanUp() {
		if (this.opts != null) {
			if (opts.visibility != null) opts.visibility.setListener(null);
			if ((this.opts instanceof PrefOpts<?>) && (this.prefListener != null)) {
				(((PrefOpts<?>) this.opts).store).removeBroadcastListener(this.prefListener);
			}
		}

		this.prefListener = null;
		this.condListener = null;
		setOnClickListener(null);
	}

	public void setPreference(PreferenceViewAdapter adapter,
														@Nullable Supplier<? extends PreferenceView.Opts> supplier) {
		cleanUp();

		if (supplier == null) {
			this.opts = null;
			return;
		}

		this.opts = supplier.get();
		this.adapter = adapter;
		this.supplier = supplier;

		if (supplier instanceof PreferenceSet) {
			setPreferenceSet(adapter, (PreferenceSet) supplier, opts);
		} else if (opts instanceof BooleanOpts) {
			setBooleanPreference((BooleanOpts) opts);
		} else if (opts instanceof FileOpts) {
			setFilePreference((FileOpts) opts);
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
		} else if (opts instanceof ButtonOpts) {
			setButtonPreference((ButtonOpts) opts);
		} else {
			throw new IllegalArgumentException();
		}
	}

	@Override
	protected void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		reconfigure();
	}

	private void reconfigure() {
		removeAllViews();
		if (adapter != null) setPreference(adapter, supplier);
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
		b.setOnCheckedChangeListener(
				(v, checked) -> o.store.applyBooleanPref(o.removeDefault, o.pref, checked));
		setOnClickListener(v -> b.setChecked(!b.isChecked()));

		setPrefListener((s, p) -> {
			if (p.contains(o.pref)) b.setChecked(o.store.getBooleanPref(o.pref));
		});
	}

	private void setStringPreference(StringOpts o) {
		setStringPreference(o, R.layout.string_pref_layout);
	}

	private void setStringPreference(StringOpts o, @LayoutRes int layout) {
		setPreference(layout, o);
		setOnClickListener(o.clickListener);
		EditText t = findViewById(R.id.pref_footer);
		boolean[] ignoreChange = new boolean[1];
		t.setMaxLines(o.maxLines);
		t.setOnKeyListener(UiUtils::dpadFocusHelper);
		t.setText(o.store.getStringPref(o.pref));
		t.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (!ignoreChange[0]) {
					ignoreChange[0] = true;
					String v = s.toString();
					if (o.trim) v = v.trim();
					if (o.removeBlank && isBlank(v)) {
						o.store.removePref(o.pref);
						t.setText(o.store.getStringPref(o.pref));
					} else {
						o.store.applyStringPref(o.removeDefault, o.pref, v);
					}
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

		if (o.hint != ID_NULL) t.setHint(o.hint);
		else if (o.stringHint != null) t.setHint(o.stringHint);

		setOnFocusChangeListener((v, has) -> {
			if (has) t.requestFocus();
		});

		setPrefListener((s, p) -> {
			if (!ignoreChange[0] && p.contains(o.pref)) {
				ignoreChange[0] = true;
				t.setText(o.store.getStringPref(o.pref));
				ignoreChange[0] = false;
			}
		});
	}

	private void setFilePreference(FileOpts o) {
		setStringPreference(o, R.layout.file_pref_layout);
		EditText t = findViewById(R.id.pref_footer);
		ImageView img = findViewById(R.id.pref_value);
		img.setImageResource(o.browseIcon);
		t.setNextFocusRightId(img.getId());
		img.setNextFocusLeftId(t.getId());

		OnClickListener onClick = v -> {
			ActivityDelegate a = ActivityDelegate.get(getContext());
			Runnable runPicker = () -> {
				int current = a.getActiveFragmentId();
				if (!(a.showFragment(R.id.file_picker) instanceof FilePickerFragment picker)) return;
				Object state = picker.resetState();
				picker.setMode(o.mode);
				picker.setPattern(o.pattern);
				picker.setSupplier(
						(o.supplier != null) ? o.supplier : completed(fileSupplier(t.getText())));

				picker.setFileConsumer(file -> {
					if (file != null) {
						File local = file.getLocalFile();
						t.setText((local != null) ? local.getAbsolutePath() : file.getRid().toString());
					}

					picker.restoreState(state);
					a.showFragment(current);
				});
			};

			if (o.useSaf()) {
				try {
					a.startActivityForResult(() -> {
						if ((o.mode & FilePickerFragment.FOLDER) != 0) {
							return new Intent(ACTION_OPEN_DOCUMENT_TREE);
						} else {
							Intent intent = new Intent(ACTION_OPEN_DOCUMENT);
							intent.addCategory(Intent.CATEGORY_OPENABLE);
							intent.setType("*/*");
							return intent;
						}
					}).onCompletion((data, err) -> {
						if (err != null) {
							Log.e(err, "SAF picker failed");
							runPicker.run();
							return;
						}

						if (data == null) return;
						Uri uri = data.getData();
						if (uri == null) return;
						int f = FLAG_GRANT_READ_URI_PERMISSION;
						if ((o.mode & WRITABLE) != 0) f |= FLAG_GRANT_WRITE_URI_PERMISSION;
						a.getContext().getContentResolver().takePersistableUriPermission(uri, f);
						o.store.applyStringPref(o.pref, uri.toString());
					});
					return;
				} catch (ActivityNotFoundException ex) {
					Log.e(ex);
				}
			}

			runPicker.run();
		};

		setOnClickListener(onClick);
		img.setOnClickListener(onClick);
	}

	private void setIntPreference(IntOpts o) {
		setNumberPreference(o, () -> String.valueOf(o.store.getIntPref(o.pref)),
				v -> o.store.applyIntPref(o.removeDefault, o.pref, Integer.parseInt(v)), String::valueOf,
				Integer::parseInt, null);
	}

	private void setFloatPreference(FloatOpts o) {
		setNumberPreference(o, () -> String.valueOf(o.store.getFloatPref(o.pref)),
				v -> o.store.applyFloatPref(o.removeDefault, o.pref, Float.parseFloat(v)),
				v -> String.valueOf(v * o.scale), v -> (int) (Float.parseFloat(v) / o.scale), null);
	}

	private void setTimePreference(TimeOpts o) {
		setNumberPreference(o, () -> TextUtils.timeToString(o.store.getIntPref(o.pref)),
				v -> o.store.applyIntPref(o.removeDefault, o.pref, TextUtils.stringToTime(v)),
				TextUtils::timeToString, TextUtils::stringToTime, (t, sb) -> {
					t.setInputType(InputType.TYPE_CLASS_TEXT);
					if (!o.editable) t.setKeyListener(null);
				});
	}

	private <S> void setNumberPreference(NumberOpts<S> o, Supplier<String> get, Consumer<String> set,
																			 IntFunction<String> fromInt, ToIntFunction<String> toInt,
																			 BiConsumer<EditText, SeekBar> viewConfigurator) {
		setPreference(R.layout.number_pref_layout, o);
		EditText t = findViewById(R.id.pref_value);
		SeekBar sb = findViewById(R.id.pref_footer);
		boolean[] ignoreChange = new boolean[1];
		String initValue = get.get();

		if (viewConfigurator != null) viewConfigurator.accept(t, sb);

		t.setEms(o.ems);
		t.setText(initValue);
		t.setOnKeyListener(UiUtils::dpadFocusHelper);
		t.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (!ignoreChange[0] && (s.length() != 0)) {
					try {
						ignoreChange[0] = true;
						String value = s.toString();
						set.accept(value);
						sb.setProgress(Math.max(0, toInt.applyAsInt(value) - o.seekMin) / o.seekScale);
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

		if (o.showProgress) {
			sb.setVisibility(VISIBLE);
			sb.setNextFocusUpId(t.getId());
			t.setNextFocusDownId(sb.getId());
			sb.setMax((o.seekMax - o.seekMin) / o.seekScale);
			sb.setProgress(Math.max(0, toInt.applyAsInt(initValue) - o.seekMin) / o.seekScale);

			sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (fromUser) {
						t.setText(fromInt.apply(progress * o.seekScale + o.seekMin));
						if (sb.isFocused()) App.get().getHandler().post(sb::requestFocus);
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
		} else {
			sb.setVisibility(GONE);
		}

		setPrefListener((s, p) -> {
			if (!ignoreChange[0] && p.contains(o.pref)) t.setText(get.get());
		});
	}

	private void setListPreference(ListOpts o) {
		if (o.initList != null) o.initList.accept(o);
		setPreference(R.layout.list_pref_layout, o);
		Runnable formatTitle;
		Runnable formatSubtitle;

		if (o.formatTitle && (opts.ctitle == null)) {
			formatTitle = () -> formatListTitle(o, this::getTitleView, o.title);
			formatTitle.run();
			setPrefListener((s, p) -> {
				if (p.contains(o.pref)) formatTitle.run();
			});
		} else {
			formatTitle = null;
		}

		if (o.formatSubtitle && (opts.csubtitle == null)) {
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
			ActivityDelegate a = ActivityDelegate.get(getContext());
			OverlayMenu menu = a.createMenu(this);

			menu.show(b -> {
				int currentValue = o.store.getIntPref(o.pref);
				var map = (o.cmp == null) ? new LinkedHashMap<String, Integer>() :
						new TreeMap<String, Integer>(o.cmp);

				if (o.values != null) {
					Resources res = getContext().getResources();

					for (int i = 0; i < o.values.length; i++) {
						if (!o.valuesFilter.apply(i)) continue;
						int id = (o.valuesMap == null) ? i : o.valuesMap[i];
						map.put(res.getString(o.values[i]), id);
					}
				} else {
					for (int i = 0; i < o.stringValues.length; i++) {
						if (!o.valuesFilter.apply(i)) continue;
						int id = (o.valuesMap == null) ? i : o.valuesMap[i];
						map.put(o.stringValues[i], id);
					}
				}
				for (var e : map.entrySet()) {
					int id = e.getValue();
					OverlayMenuItem item = b.addItem(id, e.getKey());
					if (id == currentValue) b.setSelectedItem(item);
				}

				b.setSelectionHandler(item -> {
					int id = item.getItemId();
					if (id == currentValue) return true;

					o.store.applyIntPref(o.removeDefault, o.pref, id);
					if (formatTitle != null) formatTitle.run();
					if (formatSubtitle != null) formatSubtitle.run();
					return true;
				});
			});
		});
	}

	private void setButtonPreference(ButtonOpts o) {
		setPreference(R.layout.set_pref_layout, o);
		setOnClickListener(v -> o.onClick.run());
	}

	private void formatListTitle(ListOpts o, Supplier<TextView> text, @StringRes int resId) {
		Resources res = getContext().getResources();
		int value = o.store.getIntPref(o.pref);
		int idx = (o.valuesMap == null) ? value : CollectionUtils.indexOf(o.valuesMap, value);

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
		Context ctx = getContext();
		inflate(ctx, layout, this);

		ImageView iconView = getIconView();
		TextView titleView = getTitleView();
		TextView subtitleView = getSubtitleView();
		if (opts.ctitle != null) titleView.setText(opts.ctitle);
		else titleView.setText(opts.title);

		if (opts.icon == ID_NULL) {
			iconView.setVisibility(GONE);
		} else {
			iconView.setVisibility(VISIBLE);
			iconView.setImageResource(opts.icon);
			iconView.setPadding(0, 0, toIntPx(getContext(), 5), 0);
		}

		if ((opts.csubtitle == null) && (opts.subtitle == ID_NULL)) {
			if (!(opts instanceof NumberOpts) && !(opts instanceof BooleanOpts)) {
				int padding = (int) toPx(getContext(), 8);
				titleView.setPadding(0, padding, 0, padding);
			}

			subtitleView.setVisibility(GONE);
		} else {
			titleView.setPadding(0, 0, 0, 0);
			subtitleView.setVisibility(VISIBLE);
			if (opts.csubtitle != null) subtitleView.setText(opts.csubtitle);
			else subtitleView.setText(opts.subtitle);
		}

		float scale = ActivityDelegate.get(getContext()).getTextIconSize();
		setTextAppearance(ctx, titleView, titleTextAppearance, scale);
		setTextAppearance(ctx, subtitleView, subtitleTextAppearance, scale);
		setFooterTextAppearance(ctx, scale);

		if (opts.visibility != null) {
			if (opts.visibility.get()) {
				setVisibility(VISIBLE);
				getLayoutParams().height = WRAP_CONTENT;
			} else {
				setVisibility(GONE);
				getLayoutParams().height = 0;
			}

			requestLayout();
			setCondListener(c -> reconfigure());
		} else {
			setVisibility(VISIBLE);

			if (getLayoutParams().height != WRAP_CONTENT) {
				getLayoutParams().height = WRAP_CONTENT;
				requestLayout();
			}
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

	private static BiHolder<? extends VirtualResource, List<? extends VirtualResource>> fileSupplier(
			CharSequence text) {
		String path = text.toString().trim();
		LocalFileSystem fs = LocalFileSystem.getInstance();

		if (!path.isEmpty()) {
			VirtualResource res = fs.getResource(path);

			if (res != null) {
				if (res.isFile()) res = res.getParent().get(null);

				if (res != null) {
					VirtualFolder f = (VirtualFolder) res;
					return new BiHolder<>(f, f.getChildren().get(Collections::emptyList));
				}
			}
		}

		return new BiHolder<>(null, fs.getRoots().get(Collections::emptyList));
	}

	public static class Opts {
		@SuppressLint("InlinedApi")
		@DrawableRes
		public int icon = ID_NULL;
		@SuppressLint("InlinedApi")
		@StringRes
		public int title = ID_NULL;
		@SuppressLint("InlinedApi")
		@StringRes
		public int subtitle = ID_NULL;
		public CharSequence ctitle;
		public CharSequence csubtitle;
		public ChangeableCondition visibility;
	}

	public static class PrefOpts<S> extends Opts {
		public PreferenceStore store;
		public PreferenceStore.Pref<S> pref;
		public boolean removeDefault = true;
	}

	public static class BooleanOpts extends PrefOpts<BooleanSupplier> {}

	public static class StringOpts extends PrefOpts<Supplier<String>> {
		@SuppressLint("InlinedApi")
		@StringRes
		public int hint = ID_NULL;
		public String stringHint;
		public int maxLines = 1;
		public boolean trim;
		public boolean removeBlank;
		public OnClickListener clickListener;
	}

	public static class NumberOpts<S> extends PrefOpts<S> {
		public int seekMin = 0;
		public int seekMax = 100;
		public int seekScale = 1;
		public int ems = 2;
		public boolean showProgress = true;
	}

	public static class IntOpts extends NumberOpts<IntSupplier> {}

	public static class FloatOpts extends NumberOpts<DoubleSupplier> {
		public float scale = 1f;
	}

	public static class TimeOpts extends NumberOpts<IntSupplier> {
		public boolean editable;
	}

	public static class FileOpts extends StringOpts {
		@DrawableRes
		public int browseIcon = R.drawable.browse;
		public byte mode = FILE_OR_FOLDER;
		public Boolean useSaf;
		public Pattern pattern;
		public FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>>
				supplier;

		public boolean useSaf() {
			if (useSaf != null) return useSaf;
			return (SDK_INT > Build.VERSION_CODES.R) &&
					!App.get().hasManifestPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) &&
					isSafSupported();
		}
	}

	public static class ListOpts extends PrefOpts<IntSupplier> {
		public int[] values;
		public int[] valuesMap;
		public String[] stringValues;
		public IntFunction<Boolean> valuesFilter = i -> true;
		public boolean formatTitle;
		public boolean formatSubtitle;
		public Consumer<ListOpts> initList;
		public Comparator<String> cmp;

		public void setStringValues(PreferenceStore ps, PreferenceStore.Pref<Supplier<String>> pref,
																List<Pair<String, String>> values) {
			var strValues = new String[values.size()];
			var selected = ps.getStringPref(pref);
			var selectedIdx = 0;
			for (int i = 0, n = strValues.length; i < n; i++) {
				var l = values.get(i);
				strValues[i] = l.second;
				if (l.first.equals(selected)) {
					selectedIdx = i;
				}
			}
			var vps = new BasicPreferenceStore();
			vps.addBroadcastListener((s, p) -> {
				//noinspection unchecked
				ps.applyStringPref(pref,
						values.get(vps.getIntPref((PreferenceStore.Pref<IntSupplier>) p.get(0))).first);
			});

			this.store = vps;
			this.stringValues = strValues;
			this.pref = PreferenceStore.Pref.i("proxy", selectedIdx);
		}
	}

	public static class LocaleOpts extends PrefOpts<Supplier<String>> {
		public Supplier<Locale[]> locales;
		public boolean formatTitle;
		public boolean formatSubtitle;
	}

	public static class ButtonOpts extends Opts {
		public Runnable onClick;
	}
}
