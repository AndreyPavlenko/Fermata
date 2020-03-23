package me.aap.fermata.ui.fragment;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.NavBarView;

import static me.aap.utils.ui.UiUtils.createAlertDialog;

/**
 * @author Andrey Pavlenko
 */
public class NavBarMediator implements NavBarView.Mediator, OverlayMenu.SelectionHandler {
	public static final NavBarMediator instance = new NavBarMediator();

	private NavBarMediator() {
	}

	@Override
	public void enable(NavBarView nb, ActivityFragment f) {
		addButton(nb, R.drawable.folder, R.string.folders, R.id.nav_folders);
		addButton(nb, R.drawable.favorite_filled, R.string.favorites, R.id.nav_favorites);
		addButton(nb, R.drawable.playlist, R.string.playlists, R.id.nav_playlist);
		addButton(nb, R.drawable.menu, R.string.settings, R.id.nav_settings, this::showMenu);
	}

	public void showMenu(View v) {
		showMenu(MainActivityDelegate.get(v.getContext()));
	}

	@Override
	public void showMenu(NavBarView nb) {
		showMenu((View) nb);
	}

	public void showMenu(MainActivityDelegate a) {
		OverlayMenu menu = a.findViewById(R.id.nav_menu_view);
		menu.show(b -> {
			b.setSelectionHandler(this);

			if (a.hasCurrent())
				b.addItem(R.id.nav_got_to_current, R.drawable.go_to_current, R.string.got_to_current);

			ActivityFragment f = a.getActiveFragment();
			if (f instanceof MainActivityFragment) ((MainActivityFragment) f).contributeToNavBarMenu(b);

			b.addItem(R.id.nav_settings, R.drawable.settings, R.string.settings);
			if (!a.isCarActivity()) b.addItem(R.id.nav_exit, R.drawable.exit, R.string.exit);

			if (BuildConfig.AUTO) b.addItem(R.id.nav_donate, R.drawable.coffee, R.string.donate);
		});
	}

	@Override
	public boolean menuItemSelected(OverlayMenuItem item) {
		switch (item.getItemId()) {
			case R.id.nav_got_to_current:
				MainActivityDelegate.get(item.getContext()).goToCurrent();
				return true;
			case R.id.nav_settings:
				itemSelected(R.id.nav_settings, MainActivityDelegate.get(item.getContext()));
				MainActivityDelegate.get(item.getContext()).showFragment(R.id.nav_settings);
				return true;
			case R.id.nav_exit:
				MainActivityDelegate.get(item.getContext()).finish();
				return true;
			default:
				if (BuildConfig.AUTO && (item.getItemId() == R.id.nav_donate)) {
					Context ctx = item.getContext();
					MainActivityDelegate a = MainActivityDelegate.get(ctx);

					if (a.isCarActivity()) {
						Utils.showAlert(ctx, R.string.use_phone_for_donation);
						return true;
					}

					DialogInterface.OnClickListener ok = (d, i) -> {
						int[] selection = new int[]{0};
						String[] wallets = new String[]{"CloudTips", "PayPal", "Yandex"};
						String[] urls = new String[]{
								"https://pay.cloudtips.ru/p/a03a73da",
								"https://paypal.me/AndrewPavlenko",
								"https://money.yandex.ru/to/410014661137336"
						};

						createAlertDialog(ctx)
								.setSingleChoiceItems(wallets, 0, (dlg, which) -> selection[0] = which)
								.setNegativeButton(android.R.string.cancel, null)
								.setPositiveButton(android.R.string.ok, (d1, w1) -> {
									Uri u = Uri.parse(urls[selection[0]]);
									Intent intent = new Intent(Intent.ACTION_VIEW, u);

									try {
										MainActivityDelegate.get(ctx).startActivity(intent);
									} catch (ActivityNotFoundException ex) {
										String msg = ctx.getResources().getString(R.string.err_failed_open_url, u);
										createAlertDialog(ctx).setMessage(msg)
												.setPositiveButton(android.R.string.ok, null).show();
									}
								}).show();
					};

					createAlertDialog(ctx)
							.setIcon(R.drawable.coffee)
							.setTitle(R.string.donate)
							.setMessage(R.string.donate_text)
							.setNegativeButton(android.R.string.cancel, null)
							.setPositiveButton(android.R.string.ok, ok).show();

					return true;
				}

				return false;
		}
	}
}
