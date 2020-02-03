package me.aap.fermata.ui.menu;

import androidx.annotation.StringRes;

/**
 * @author Andrey Pavlenko
 */
public interface AppMenuItem {

	int getItemId();

	AppMenu getMenu();

	boolean isLongClick();

	CharSequence getTitle();

	AppMenuItem setTitle(@StringRes int title);

	AppMenuItem setTitle(CharSequence title);

	<T> AppMenuItem setData(T data);

	<T> T getData();

	AppMenuItem setChecked(boolean checked);

	AppMenuItem setVisible(boolean visible);
}
