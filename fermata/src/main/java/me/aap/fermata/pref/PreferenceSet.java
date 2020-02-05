package me.aap.fermata.pref;

import android.content.res.Resources;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.function.Consumer;
import me.aap.fermata.function.Supplier;
import me.aap.fermata.ui.menu.AppMenu;

/**
 * @author Andrey Pavlenko
 */
public class PreferenceSet implements Supplier<PreferenceView.Opts> {
	final List<Supplier<? extends PreferenceView.Opts>> preferences = new ArrayList<>();
	private final PreferenceSet parent;
	private final Consumer<PreferenceView.Opts> builder;

	public PreferenceSet() {
		this(null, null);
	}

	private PreferenceSet(PreferenceSet parent, Consumer<PreferenceView.Opts> builder) {
		this.parent = parent;
		this.builder = builder;
	}

	public PreferenceSet getParent() {
		return parent;
	}

	@Override
	public PreferenceView.Opts get() {
		PreferenceView.Opts opts = new PreferenceView.Opts();
		builder.accept(opts);
		return opts;
	}

	public void addBooleanPref(Consumer<PreferenceView.BooleanOpts> builder) {
		preferences.add(() -> {
			PreferenceView.BooleanOpts o = new PreferenceView.BooleanOpts();
			builder.accept(o);
			return o;
		});
	}

	public void addStringPref(Consumer<PreferenceView.StringOpts> builder) {
		preferences.add(() -> {
			PreferenceView.StringOpts o = new PreferenceView.StringOpts();
			builder.accept(o);
			return o;
		});
	}

	public void addIntPref(Consumer<PreferenceView.IntOpts> builder) {
		preferences.add(() -> {
			PreferenceView.IntOpts o = new PreferenceView.IntOpts();
			builder.accept(o);
			return o;
		});
	}

	public void addFloatPref(Consumer<PreferenceView.FloatOpts> builder) {
		preferences.add(() -> {
			PreferenceView.FloatOpts o = new PreferenceView.FloatOpts();
			builder.accept(o);
			return o;
		});
	}

	public void addTimePref(Consumer<PreferenceView.TimeOpts> builder) {
		preferences.add(() -> {
			PreferenceView.TimeOpts o = new PreferenceView.TimeOpts();
			builder.accept(o);
			return o;
		});
	}

	public void addListPref(Consumer<PreferenceView.ListOpts> builder) {
		preferences.add(() -> {
			PreferenceView.ListOpts o = new PreferenceView.ListOpts();
			builder.accept(o);
			return o;
		});
	}

	public PreferenceSet subSet(Consumer<PreferenceView.Opts> builder) {
		PreferenceSet sub = new PreferenceSet(this, builder);
		preferences.add(sub);
		return sub;
	}

	public void addToView(RecyclerView v) {
		v.setHasFixedSize(true);
		v.setLayoutManager(new LinearLayoutManager(v.getContext()));
		v.setAdapter(new PreferenceViewAdapter(this));
	}

	public void addToMenu(AppMenu menu, boolean setMinWidth) {
		View v = menu.inflate(R.layout.pref_list_view);
		RecyclerView prefsView = v.findViewById(R.id.prefs_list_view);
		addToView(prefsView);

		if (setMinWidth) {
			prefsView.setMinimumWidth(Resources.getSystem().getDisplayMetrics().widthPixels * 2 / 3);
		}
	}
}
