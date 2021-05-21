package me.aap.fermata.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;

import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.ControlPanelView;
import me.aap.fermata.ui.view.MediaItemListView;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.Supplier;
import me.aap.utils.holder.IntHolder;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Compound;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.fragment.GenericFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.NavBarItem;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.NavButtonView;
import me.aap.utils.ui.view.PrefNavBarMediator;
import me.aap.utils.ui.view.ToolBarView;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.view.View.FOCUS_DOWN;
import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.FOCUS_UP;
import static me.aap.fermata.BuildConfig.VERSION_CODE;
import static me.aap.fermata.BuildConfig.VERSION_NAME;
import static me.aap.utils.ui.UiUtils.ID_NULL;
import static me.aap.utils.ui.UiUtils.isVisible;
import static me.aap.utils.ui.UiUtils.showInfo;
import static me.aap.utils.ui.view.NavBarView.POSITION_BOTTOM;

/**
 * @author Andrey Pavlenko
 */
public class NavBarMediator extends PrefNavBarMediator implements AddonManager.Listener,
		OverlayMenu.SelectionHandler {

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		super.enable(nb, f);
		FermataApplication.get().getAddonManager().addBroadcastListener(this);
	}

	@Override
	public void disable(NavBarView nb) {
		super.disable(nb);
		FermataApplication.get().getAddonManager().removeBroadcastListener(this);
	}

	@Override
	public void addonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		NavBarView nb = navBar;
		if (nb != null) reload(nb);
	}

	@Override
	protected PreferenceStore getPreferenceStore(NavBarView nb) {
		return MainActivityDelegate.get(nb.getContext()).getPrefs();
	}

	@Override
	protected Pref<Compound<List<NavBarItem>>> getPref(NavBarView nb) {
		return new NavBarPref(nb);
	}

	@Override
	public void itemSelected(View item, int id, ActivityDelegate a) {
		if (id == R.id.menu) {
			showMenu(MainActivityDelegate.get(item.getContext()));
		} else {
			super.itemSelected(item, id, a);
		}
	}

	@Override
	protected boolean extItemSelected(OverlayMenuItem item) {
		if (item.getItemId() == R.id.menu) {
			NavButtonView.Ext ext = getExtButton();

			if ((ext != null) && !ext.isSelected()) {
				NavBarItem i = item.getData();
				setExtButton(null, i);
			}

			showMenu(MainActivityDelegate.get(item.getContext()));
			return true;
		} else {
			return super.extItemSelected(item);
		}
	}

	@Nullable
	@Override
	public View focusSearch(NavBarView nb, View focused, int direction) {
		if (direction == FOCUS_UP) {
			if (!nb.isBottom()) return null;
			ControlPanelView p = MainActivityDelegate.get(nb.getContext()).getControlPanel();
			return isVisible(p) ? p.focusSearch() : MediaItemListView.focusLast(focused);
		} else if (direction == FOCUS_DOWN) {
			if (!nb.isBottom()) return null;
			ToolBarView tb = MainActivityDelegate.get(nb.getContext()).getToolBar();
			if (isVisible(tb)) return tb.focusSearch();
		} else if (direction == FOCUS_RIGHT) {
			if (nb.isLeft()) return MediaItemListView.focusActive(focused);
		} else if (direction == FOCUS_LEFT) {
			if (nb.isRight()) return MediaItemListView.focusActive(focused);
		}

		return null;
	}

	@Override
	public void showMenu(NavBarView nb) {
		showMenu(MainActivityDelegate.get(nb.getContext()));
	}

	public void showMenu(MainActivityDelegate a) {
		OverlayMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.show(b -> {
			b.setSelectionHandler(this);

			if (a.hasCurrent())
				b.addItem(R.id.nav_got_to_current, R.drawable.go_to_current, R.string.got_to_current);

			ActivityFragment f = a.getActiveFragment();
			if (f instanceof MainActivityFragment) ((MainActivityFragment) f).contributeToNavBarMenu(b);

			b.addItem(R.id.nav_about, R.drawable.about, R.string.about);
			b.addItem(R.id.settings_fragment, R.drawable.settings, R.string.settings);
			if (!a.isCarActivity()) b.addItem(R.id.nav_exit, R.drawable.exit, R.string.exit);

			if (BuildConfig.AUTO) b.addItem(R.id.nav_donate, R.drawable.coffee, R.string.donate);
		});
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.nav_got_to_current) {
			MainActivityDelegate.get(item.getContext()).goToCurrent();
			return true;
		} else if (itemId == R.id.nav_about) {
			MainActivityDelegate a = MainActivityDelegate.get(item.getContext());
			GenericFragment f = a.showFragment(R.id.generic_fragment);
			f.setTitle(item.getContext().getString(R.string.about));
			f.setContentProvider(g -> {
				Context ctx = g.getContext();
				MaterialTextView v = new MaterialTextView(ctx);
				String url = "https://github.com/AndreyPavlenko/Fermata";
				String html = ctx.getString(R.string.about_html, VERSION_NAME, VERSION_CODE, url);
				int pad = UiUtils.toIntPx(ctx, 10);
				v.setPadding(pad, pad, pad, pad);
				v.setText(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY));
				v.setOnClickListener(t -> openUrl(t.getContext(), url));
				g.addView(v);
			});
			return true;
		} else if (itemId == R.id.settings_fragment) {
			MainActivityDelegate.get(item.getContext()).showFragment(R.id.settings_fragment);
			return true;
		} else if (itemId == R.id.nav_exit) {
			MainActivityDelegate.get(item.getContext()).finish();
			return true;
		}
		MainActivityDelegate a;
		if (BuildConfig.AUTO && (item.getItemId() == R.id.nav_donate)) {
			Context ctx = item.getContext();
			a = MainActivityDelegate.get(ctx);

			DialogInterface.OnClickListener ok = (d, i) -> {
				IntHolder selection = new IntHolder();
				String[] wallets = new String[]{"PayPal", "CloudTips", "Yandex",};
				String[] urls = new String[]{
						"https://paypal.me/AndrewPavlenko",
						"https://pay.cloudtips.ru/p/a03a73da",
						"https://money.yandex.ru/to/410014661137336"
				};

				a.createDialogBuilder()
						.setTitle(R.drawable.coffee, R.string.donate)
						.setSingleChoiceItems(wallets, 0, (dlg, which) -> selection.value = which)
						.setNegativeButton(android.R.string.cancel, null)
						.setPositiveButton(android.R.string.ok, (d1, w1) -> openUrl(ctx, urls[selection.value]))
						.show();
			};

			a.createDialogBuilder()
					.setTitle(R.drawable.coffee, R.string.donate)
					.setMessage(R.string.donate_text)
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(android.R.string.ok, ok)
					.show();

			return true;
		}

		return false;
	}

	private static void openUrl(Context ctx, String url) {
		MainActivityDelegate a = MainActivityDelegate.get(ctx);

		if (a.isCarActivity()) {
			if (!openUrlInBrowserFragment(a, url)) showInfo(ctx, R.string.use_phone_for_donation);
			return;
		}

		Uri u = Uri.parse(url);
		Intent intent = new Intent(Intent.ACTION_VIEW, u);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		try {
			a.startActivity(intent);
		} catch (ActivityNotFoundException ex) {
			if (openUrlInBrowserFragment(a, url)) return;

			String msg = ctx.getResources().getString(R.string.err_failed_open_url, u);
			a.createDialogBuilder().setMessage(msg)
					.setPositiveButton(android.R.string.ok, null).show();
		}
	}

	private static boolean openUrlInBrowserFragment(MainActivityDelegate a, String url) {
		try {
			a.showFragment(R.id.web_browser_fragment).setInput(url);
			return true;
		} catch (Exception ex) {
			Log.d(ex);
			return false;
		}
	}

	private static final class NavBarPref implements Pref<Compound<List<NavBarItem>>>, Compound<List<NavBarItem>> {
		private static final Pref<Supplier<String>> prefV = Pref.s("NAV_BAR_V", () -> null);
		private static final Pref<Supplier<String>> prefH = Pref.s("NAV_BAR_H", () -> null);
		private final NavBarView nb;

		public NavBarPref(NavBarView nb) {
			this.nb = nb;
		}

		@Override
		public String getName() {
			return "NAV_BAR";
		}

		@Override
		public Compound<List<NavBarItem>> getDefaultValue() {
			return this;
		}

		@Override
		public List<NavBarItem> get(PreferenceStore store, String name) {
			AddonManager amgr = FermataApplication.get().getAddonManager();
			List<NavBarItem> items = new ArrayList<>(BuildConfig.ADDONS.length + 4);
			Pref<Supplier<String>> pref = getPref();
			int max = (pref == prefV) ? 4 : 7;
			String v = store.getStringPref(pref);
			if (v == null) v = store.getStringPref((pref == prefH) ? prefV : prefH);

			if (v != null) {
				for (String s : v.split(",")) {
					int idx = s.indexOf('_');

					if ((idx == -1) || (idx == s.length() - 1)) {
						Log.w("Invalid value of NAV_BAR pref: " + v);
						break;
					}

					boolean pin = s.startsWith("true_");
					s = s.substring(idx + 1);
					NavBarItem item = getItem(amgr, s, pin);
					if (item != null) items.add(item);
				}
			}

			Context ctx = nb.getContext();

			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.folders_fragment)) {
				items.add(NavBarItem.create(ctx, R.id.folders_fragment, R.drawable.folder, R.string.folders, true));
			}
			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.favorites_fragment)) {
				items.add(NavBarItem.create(ctx, R.id.favorites_fragment, R.drawable.favorite_filled, R.string.favorites, true));
			}
			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.playlists_fragment)) {
				items.add(NavBarItem.create(ctx, R.id.playlists_fragment, R.drawable.playlist, R.string.playlists, true));
			}

			for (AddonInfo ai : BuildConfig.ADDONS) {
				FermataAddon a = amgr.getAddon(ai.className);
				if ((a != null) && (a.getAddonId() != ID_NULL) &&
						!CollectionUtils.contains(items, i -> i.getId() == a.getAddonId())) {
					items.add(NavBarItem.create(ctx, a.getAddonId(), ai.icon, ai.addonName, items.size() < max));
				}
			}

			if (!CollectionUtils.contains(items, i -> i.getId() == R.id.menu)) {
				items.add(NavBarItem.create(ctx, R.id.menu, R.drawable.menu, R.string.menu, false));
			}

			return items;
		}

		@Override
		public void set(PreferenceStore.Edit edit, String name, List<NavBarItem> value) {
			AddonManager amgr = FermataApplication.get().getAddonManager();
			String v;

			try (SharedTextBuilder tb = SharedTextBuilder.get()) {
				for (NavBarItem i : value) {
					String itemName = getName(amgr, i);

					if (itemName == null) {
						Log.w("Nav bar item name not found for " + i.getText());
					} else {
						if (tb.length() > 0) tb.append(',');
						tb.append(i.isPinned()).append('_').append(itemName);
					}
				}

				v = tb.toString();
			}

			edit.setStringPref(getPref(), v);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return Objects.equals(getName(), ((NavBarPref) o).getName());
		}

		@Override
		public int hashCode() {
			return getName().hashCode();
		}

		private Pref<Supplier<String>> getPref() {
			Context ctx = nb.getContext();
			if (ctx.getResources().getConfiguration().smallestScreenWidthDp > 600) return prefH;

			boolean bottom = nb.getPosition() == POSITION_BOTTOM;
			boolean portrait = nb.getContext().getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;

			if (bottom) return portrait ? prefV : prefH;
			else return portrait ? prefH : prefV;
		}

		private static String getName(AddonManager amgr, NavBarItem i) {
			int id = i.getId();

			if (id == R.id.folders_fragment) {
				return "folders";
			} else if (id == R.id.favorites_fragment) {
				return "favorites";
			} else if (id == R.id.playlists_fragment) {
				return "playlists";
			} else if (id == R.id.menu) {
				return "menu";
			}
			for (AddonInfo ai : BuildConfig.ADDONS) {
				FermataAddon a = amgr.getAddon(ai.className);
				if ((a != null) && (a.getAddonId() == id)) return ai.className;
			}

			return null;
		}

		private NavBarItem getItem(AddonManager amgr, String name, boolean pin) {
			Context ctx = nb.getContext();

			switch (name) {
				case "folders":
					return NavBarItem.create(ctx, R.id.folders_fragment, R.drawable.folder, R.string.folders, pin);
				case "favorites":
					return NavBarItem.create(ctx, R.id.favorites_fragment, R.drawable.favorite_filled, R.string.favorites, pin);
				case "playlists":
					return NavBarItem.create(ctx, R.id.playlists_fragment, R.drawable.playlist, R.string.playlists, pin);
				case "menu":
					return NavBarItem.create(ctx, R.id.menu, R.drawable.menu, R.string.menu, pin);
				default:
					for (AddonInfo ai : BuildConfig.ADDONS) {
						if (name.equals(ai.className)) {
							FermataAddon a = amgr.getAddon(ai.className);
							if ((a != null) && (a.getAddonId() != ID_NULL)) {
								return NavBarItem.create(ctx, a.getAddonId(), ai.icon, ai.addonName, pin);
							}
						}
					}

					return null;
			}
		}
	}
}
