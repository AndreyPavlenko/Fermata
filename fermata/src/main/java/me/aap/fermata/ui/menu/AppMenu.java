package me.aap.fermata.ui.menu;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;

/**
 * @author Andrey Pavlenko
 */
public interface AppMenu {

	View inflate(@LayoutRes int content);

	void setView(View view);

	void show(SelectionHandler handler, CloseHandler closeHandler);

	default void show(SelectionHandler handler) {
		show(handler, null);
	}

	default void show(@LayoutRes int content, SelectionHandler handler) {
		show(content, handler, null);
	}

	default void show(@LayoutRes int content, SelectionHandler handler, CloseHandler closeHandler) {
		inflate(content);
		show(handler, closeHandler);
	}

	void hide();

	AppMenuItem findItem(int id);

	AppMenuItem addItem(int id, boolean checkable, Drawable icon, CharSequence title);

	default AppMenuItem addItem(int id, @StringRes int title) {
		return addItem(id, getContext().getResources().getString(title));
	}

	default AppMenuItem addItem(int id, CharSequence title) {
		return addItem(id, false, null, title);
	}

	void setSelectedItem(AppMenuItem item);

	void setTitle(@StringRes int title);

	void setTitle(CharSequence title);

	Context getContext();

	interface SelectionHandler {

		boolean menuItemSelected(AppMenuItem item);
	}

	interface CloseHandler {
		void menuClosed(AppMenu menu);
	}
}
