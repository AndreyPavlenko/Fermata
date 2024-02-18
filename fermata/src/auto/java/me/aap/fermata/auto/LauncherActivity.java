package me.aap.fermata.auto;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.activity.ActivityDelegate.FULLSCREEN_FLAGS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.view.MovableRecyclerViewAdapter;

public class LauncherActivity extends AppCompatActivity {
	private static final Pref<Supplier<String[]>> AA_LAUNCHER_APPS = Pref.sa("AA_LAUNCHER_APPS",
			() -> new String[]{FermataApplication.get().getPackageName(), "com.android.chrome",
					"com.google.android.gm", "com.google.android.apps.maps", "com.google.android.youtube"});
	private static LauncherActivity activeInstance;

	static LauncherActivity getActiveInstance() {
		return activeInstance;
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this, true);
		super.onCreate(savedInstanceState);
		setRequestedOrientation();
		var v = new AppListView(this);
		setContentView(v);
		var w = getWindow();
		w.addFlags(FLAG_KEEP_SCREEN_ON);
		w.getDecorView().setSystemUiVisibility(FULLSCREEN_FLAGS);
		getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (v.selectApps != null) v.selectApps();
			}
		});
		FermataApplication.get().getHandler()
				.postDelayed(() -> v.configure(this, getResources().getConfiguration()), 1000);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		activeInstance = this;
		setRequestedOrientation();
	}

	@Override
	protected void onPause() {
		super.onPause();
		activeInstance = null;
	}

	private void setRequestedOrientation() {
		setRequestedOrientation(
				FermataApplication.get().isMirroringLandscape() ? SCREEN_ORIENTATION_SENSOR_LANDSCAPE :
						SCREEN_ORIENTATION_SENSOR_PORTRAIT);
	}

	private static final class AppListView extends RecyclerView {
		private final List<AppInfo> apps;
		private SelectAppInfo[] selectApps;
		private final Animation animation;
		private final Drawable addIcon;
		private Drawable exitIcon;
		private Drawable backIcon;
		private Drawable checkedIcon;
		private Drawable uncheckedIcon;
		private int iconSize;
		private int marginH;
		private final int marginV;
		private final int textMargin;

		public AppListView(@NonNull Context ctx) {
			super(ctx);
			apps = loadAppList();
			animation = AnimationUtils.loadAnimation(ctx, me.aap.utils.R.anim.button_press);
			addIcon = loadIcon(R.drawable.add_circle);
			marginV = toIntPx(ctx, 20) / 2;
			textMargin = toIntPx(ctx, 5);
			configure(ctx, getResources().getConfiguration());

			var adapter = new AppListAdapter();
			ItemTouchHelper h = new ItemTouchHelper(adapter.getItemTouchCallback());
			setAdapter(adapter);
			h.attachToRecyclerView(this);
		}

		@Override
		protected void onConfigurationChanged(Configuration newConfig) {
			super.onConfigurationChanged(newConfig);
			configure(getContext(), newConfig);
		}

		private Drawable loadIcon(@DrawableRes int id) {
			var color =
					MaterialColors.getColor(getContext(),
							com.google.android.material.R.attr.colorOnSecondary,
							0);
			var icon = ResourcesCompat.getDrawable(getResources(), id, getContext().getTheme());
			assert icon != null;
			icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
			return icon;
		}

		private List<AppInfo> loadAppList() {
			var selectedApps = MainActivityPrefs.get().getStringArrayPref(AA_LAUNCHER_APPS);
			var apps = new ArrayList<AppInfo>(selectedApps.length + 1);
			var pm = getContext().getPackageManager();
			var allApps = loadAllAppList(pm);
			var addApp = AppInfo.ADD.pkg + '#' + AppInfo.ADD.name;
			var exitApp = AppInfo.EXIT.pkg + '#' + AppInfo.EXIT.name;

			for (var app : selectedApps) {
				for (var info : allApps) {
					var pkg = info.activityInfo.packageName;
					if (app.equals(addApp)) {
						apps.add(AppInfo.ADD);
						addApp = null;
						break;
					}
					if (app.equals(exitApp)) {
						apps.add(AppInfo.EXIT);
						break;
					}
					if (app.startsWith(pkg) && (app.equals(pkg) ||
							(app.endsWith(info.activityInfo.name) && (app.charAt(pkg.length()) == '#') &&
									(app.length() == (pkg.length() + info.activityInfo.name.length() + 1))))) {
						apps.add(new AppInfo(info.activityInfo.packageName, info.activityInfo.name,
								info.loadLabel(pm).toString(), info.loadIcon(pm)));
						break;
					}
				}
			}

			if (addApp != null) apps.add(AppInfo.ADD);
			return apps;
		}

		private List<ResolveInfo> loadAllAppList(PackageManager pm) {
			var intent = new Intent(Intent.ACTION_MAIN);
			intent.addCategory(Intent.CATEGORY_LAUNCHER);
			return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL);
		}

		@SuppressLint("NotifyDataSetChanged")
		private void selectApps() {
			for (var app : selectApps) {
				if (app.selected) {
					if (!apps.contains(app)) {
						var idx = apps.size();
						if (apps.get(idx - 1).equals(AppInfo.ADD)) idx -= 1;
						var ai = new AppInfo(app.pkg, app.name, app.label, app.icon());
						apps.add(idx, AppInfo.EXIT.equals(ai) ? AppInfo.EXIT : ai);
					}
				} else {
					apps.remove(app);
				}
			}
			saveApps();
			selectApps = null;
			Objects.requireNonNull(getAdapter()).notifyDataSetChanged();
		}

		private void saveApps() {
			MainActivityPrefs.get().applyStringArrayPref(AA_LAUNCHER_APPS,
					CollectionUtils.mapToArray(apps, i -> i.pkg + '#' + i.name, String[]::new));
		}

		private void configure(Context ctx, Configuration cfg) {
			var span = Math.max(cfg.screenWidthDp / 96, 2);
			var width = toPx(ctx, cfg.screenWidthDp);
			var margin = width * 0.3f / (span + 1);
			iconSize = (int) ((width / span) - margin);
			marginH = (int) (margin / 2);
			var lp = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
			lp.setMargins(marginH, marginV, marginH, marginV);
			setLayoutParams(lp);
			setLayoutManager(new GridLayoutManager(ctx, span));
		}

		private static class AppInfo {
			private static final AppInfo ADD =
					new AppInfo(FermataApplication.get().getPackageName(), "add", null, null);
			private static final AppInfo BACK =
					new AppInfo(FermataApplication.get().getPackageName(), "back", null, null);
			private static final AppInfo EXIT =
					new AppInfo(FermataApplication.get().getPackageName(), "exit", null, null);
			final String pkg;
			final String name;
			final String label;
			protected Drawable icon;

			private AppInfo(String pkg, String name, String label, Drawable icon) {
				this.pkg = pkg;
				this.name = name;
				this.label = label;
				this.icon = icon;
			}

			/**
			 * @noinspection EqualsWhichDoesntCheckParameterClass
			 */
			@Override
			public boolean equals(Object o) {
				if (this == o) return true;
				AppInfo appInfo = (AppInfo) o;
				return Objects.equals(pkg, appInfo.pkg) && Objects.equals(name, appInfo.name);
			}

			@Override
			public int hashCode() {
				return Objects.hash(pkg, name);
			}

			public Drawable icon() {return icon;}
		}

		private static final class SelectAppInfo extends AppInfo implements Comparable<SelectAppInfo> {
			private final PackageManager pm;
			private final ResolveInfo info;
			boolean selected;

			private SelectAppInfo(PackageManager pm, ResolveInfo info) {
				this(pm, info, info.activityInfo.packageName, info.activityInfo.name,
						info.loadLabel(pm).toString(), null);
			}

			private SelectAppInfo(PackageManager pm, ResolveInfo info, String pkg, String name,
														String label, Drawable icon) {
				super(pkg, name, label, icon);
				this.pm = pm;
				this.info = info;
			}

			@Override
			public Drawable icon() {
				if (icon == null) icon = info.loadIcon(pm);
				return icon;
			}

			@Override
			public int compareTo(SelectAppInfo o) {
				return label.compareTo(o.label);
			}
		}

		private final class AppView extends LinearLayoutCompat implements OnClickListener {
			private final AppCompatImageView icon;
			private final MaterialTextView text;
			private AppInfo appInfo;

			public AppView(Context ctx) {
				super(ctx);
				setOnClickListener(this);
				setOrientation(VERTICAL);
				setLayoutParams(new GridLayoutManager.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
				addView(icon = new AppCompatImageView(ctx));
				addView(text = new MaterialTextView(ctx));

				var lp = new LinearLayoutCompat.LayoutParams(iconSize, iconSize);
				lp.gravity = Gravity.CENTER;
				icon.setLayoutParams(lp);
				icon.setScaleType(ImageView.ScaleType.FIT_CENTER);

				lp = new LinearLayoutCompat.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
				lp.gravity = Gravity.CENTER;
				text.setLayoutParams(lp);
				text.setMaxLines(1);
				text.setGravity(Gravity.CENTER);
				text.setEllipsize(TextUtils.TruncateAt.END);
				text.setCompoundDrawablePadding(toIntPx(getContext(), 5));
			}

			void setAppInfo(AppInfo appInfo) {
				this.appInfo = appInfo;

				if (appInfo == AppInfo.ADD) {
					icon.setImageDrawable(addIcon);
					text.setVisibility(GONE);
				} else if (appInfo == AppInfo.BACK) {
					if (backIcon == null) backIcon = loadIcon(R.drawable.back_circle);
					icon.setImageDrawable(backIcon);
					text.setVisibility(GONE);
				} else {
					text.setVisibility(VISIBLE);
					if (appInfo == AppInfo.EXIT) {
						if (exitIcon == null) exitIcon = loadIcon(R.drawable.shutdown);
						icon.setImageDrawable(exitIcon);
						text.setText(getContext().getString(R.string.exit));
					} else {
						icon.setImageDrawable(appInfo.icon());
						text.setText(appInfo.label);
					}
					if (appInfo instanceof SelectAppInfo sai) {
						Drawable icon;
						if (sai.selected) {
							if (checkedIcon == null) checkedIcon = loadIcon(me.aap.utils.R.drawable.check_box);
							icon = checkedIcon;
						} else {
							if (uncheckedIcon == null)
								uncheckedIcon = loadIcon(me.aap.utils.R.drawable.check_box_blank);
							icon = uncheckedIcon;
						}
						text.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
					} else {
						text.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
					}
				}

				var lp = (LinearLayoutCompat.LayoutParams) icon.getLayoutParams();
				lp.height = lp.width = iconSize;

				if (text.getVisibility() == GONE) {
					var bounds = new Rect();
					var s = getResources().getText(R.string.app_name).toString();
					text.getPaint().getTextBounds(s, 0, s.length(), bounds);
					var pad = bounds.height() / 2;
					lp.setMargins(marginH, marginV + pad, marginH, marginV + pad);
				} else {
					lp.setMargins(marginH, marginV, marginH, 0);
					lp = (LinearLayoutCompat.LayoutParams) text.getLayoutParams();
					lp.setMargins(0, textMargin, 0, marginV);
				}
			}

			@Override
			public boolean onTouchEvent(MotionEvent event) {
				Log.i(event);
				return super.onTouchEvent(event);
			}

			@SuppressLint("NotifyDataSetChanged")
			@Override
			public void onClick(View v) {
				if (appInfo instanceof SelectAppInfo sai) {
					sai.selected = !sai.selected;
					setAppInfo(sai);
					return;
				}

				icon.startAnimation(animation);

				if (appInfo.equals(AppInfo.EXIT)) {
					MirrorDisplay.close();
				} else if (appInfo.equals(AppInfo.ADD)) {
					var pm = getContext().getPackageManager();
					var allApps = loadAllAppList(pm);
					if (exitIcon == null) exitIcon = loadIcon(R.drawable.shutdown);
					selectApps = new SelectAppInfo[allApps.size() + 1];
					selectApps[0] = new SelectAppInfo(null, null, AppInfo.EXIT.pkg, AppInfo.EXIT.name,
							getContext().getString(R.string.exit), exitIcon);
					for (int i = 1; i < selectApps.length; i++) {
						selectApps[i] = new SelectAppInfo(pm, allApps.get(i - 1));
					}
					Arrays.sort(selectApps, 1, selectApps.length);
					for (var app : selectApps) app.selected = AppListView.this.apps.contains(app);
					Objects.requireNonNull(getAdapter()).notifyDataSetChanged();
				} else if (appInfo.equals(AppInfo.BACK)) {
					selectApps();
				} else {
					try {
						var intent = new Intent(Intent.ACTION_MAIN);
						intent.addCategory(Intent.CATEGORY_LAUNCHER);
						intent.setClassName(appInfo.pkg, appInfo.name);
						intent.setFlags(FLAG_ACTIVITY_NEW_TASK);
						getContext().startActivity(intent);
						MirrorDisplay.disableAccelRotation();
					} catch (Exception err) {
						Toast.makeText(getContext(), err.getLocalizedMessage(), Toast.LENGTH_LONG).show();
					}
				}
			}
		}

		private static final class AppViewHolder extends RecyclerView.ViewHolder {

			public AppViewHolder(@NonNull View itemView) {
				super(itemView);
			}
		}

		private final class AppListAdapter extends MovableRecyclerViewAdapter<AppViewHolder> {

			@NonNull
			@Override
			public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
				return new AppViewHolder(new AppView(getContext()));
			}

			@Override
			public void onBindViewHolder(@NonNull AppViewHolder holder, int pos) {
				var v = (AppView) holder.itemView;
				v.setAppInfo(
						(selectApps == null) ? apps.get(pos) : (pos == 0) ? AppInfo.BACK :
								selectApps[pos - 1]);
			}

			@Override
			public int getItemCount() {
				return (selectApps == null) ? apps.size() : selectApps.length + 1;
			}

			@Override
			protected void onItemDismiss(int position) {}

			@Override
			protected boolean onItemMove(int fromPosition, int toPosition) {
				if (selectApps != null) return false;
				move(apps, fromPosition, toPosition);
				saveApps();
				return true;
			}

			@Override
			protected boolean isLongPressDragEnabled() {
				return selectApps == null;
			}

			@Override
			protected boolean isItemViewSwipeEnabled() {
				return false;
			}
		}
	}
}
