package me.aap.fermata.ui.menu;

/**
 * @author Andrey Pavlenko
 */
public interface AppMenuItem {

	int getItemId();

	AppMenu getMenu();

	boolean isLongClick();

	CharSequence getTitle();

	void setTitle(CharSequence title);

	<T> void setData(T data);

	<T> T getData();

	void setChecked(boolean checked);

	void setVisible(boolean visible);
}
