package me.aap.utils.ui.menu;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.ui.menu.OverlayMenu.Builder;
import me.aap.utils.ui.menu.OverlayMenu.SelectionHandler;

import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
public interface OverlayMenuItem {

	Context getContext();

	int getItemId();

	OverlayMenu getMenu();

	boolean isLongClick();

	CharSequence getTitle();

	OverlayMenuItem setTitle(@StringRes int title);

	OverlayMenuItem setTitle(CharSequence title);

	<T> OverlayMenuItem setData(T data);

	<T> T getData();

	default OverlayMenuItem setChecked(boolean checked) {
		return setChecked(checked, false);
	}

	OverlayMenuItem setChecked(boolean checked, boolean selectChecked);

	OverlayMenuItem setVisible(boolean visible);

	OverlayMenuItem setMultiLine(boolean multiLine);

	OverlayMenuItem setHandler(SelectionHandler handler);

	OverlayMenuItem setFutureSubmenu(Function<? super Builder, FutureSupplier<Void>> builder);

	default OverlayMenuItem setSubmenu(Consumer<Builder> builder) {
		return setFutureSubmenu(b -> {
			builder.accept(b);
			return completedVoid();
		});
	}

	default OverlayMenuItem setSubmenu(@LayoutRes int layout) {
		return setSubmenu(b -> b.inflate(layout));
	}
}
