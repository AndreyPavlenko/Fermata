package me.aap.utils.ui.menu;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;

import static me.aap.utils.async.Completed.completedVoid;

/**
 * @author Andrey Pavlenko
 */
public interface OverlayMenu {

	Context getContext();

	void showFuture(Function<? super Builder, FutureSupplier<Void>> builder);

	default void show(Consumer<Builder> builder) {
		showFuture(b -> {
			builder.accept(b);
			return completedVoid();
		});
	}

	void hide();

	boolean back();

	@SuppressWarnings("unused")
	OverlayMenuItem findItem(int id);

	List<OverlayMenuItem> getItems();

	interface Builder {

		OverlayMenu getMenu();

		OverlayMenuItem addItem(int id, Drawable icon, CharSequence title);

		OverlayMenuItem addItem(int id, Drawable icon, CharSequence title, int relativeToId, boolean after);

		default OverlayMenuItem addItem(int id, @DrawableRes int icon, @StringRes int title) {
			Context ctx = getMenu().getContext();
			Resources r = ctx.getResources();
			return addItem(id, ResourcesCompat.getDrawable(r, icon, ctx.getTheme()), r.getString(title));
		}

		default OverlayMenuItem addItem(int id, Drawable icon, @StringRes int title) {
			return addItem(id, icon, getMenu().getContext().getResources().getString(title));
		}

		default OverlayMenuItem addItem(int id, @DrawableRes int icon, CharSequence title) {
			Context ctx = getMenu().getContext();
			Resources r = ctx.getResources();
			return addItem(id, ResourcesCompat.getDrawable(r, icon, ctx.getTheme()), title);
		}

		default OverlayMenuItem addItem(int id, @StringRes int title) {
			return addItem(id, getMenu().getContext().getResources().getString(title));
		}

		default OverlayMenuItem addItem(int id, CharSequence title) {
			return addItem(id, null, title);
		}

		default OverlayMenuItem addItem(int id, @DrawableRes int icon, @StringRes int title, int relativeToId, boolean after) {
			Context ctx = getMenu().getContext();
			Resources r = ctx.getResources();
			return addItem(id, ResourcesCompat.getDrawable(r, icon, ctx.getTheme()), r.getString(title), relativeToId, after);
		}

		default OverlayMenuItem addItem(int id, Drawable icon, @StringRes int title, int relativeToId, boolean after) {
			return addItem(id, icon, getMenu().getContext().getResources().getString(title), relativeToId, after);
		}

		default OverlayMenuItem addItem(int id, @DrawableRes int icon, CharSequence title, int relativeToId, boolean after) {
			Context ctx = getMenu().getContext();
			Resources r = ctx.getResources();
			return addItem(id, ResourcesCompat.getDrawable(r, icon, ctx.getTheme()), title, relativeToId, after);
		}

		default OverlayMenuItem addItem(int id, @StringRes int title, int relativeToId, boolean after) {
			return addItem(id, getMenu().getContext().getResources().getString(title), relativeToId, after);
		}

		default OverlayMenuItem addItem(int id, CharSequence title, int relativeToId, boolean after) {
			return addItem(id, null, title, relativeToId, after);
		}

		Builder setSelectedItem(OverlayMenuItem item);

		Builder setTitle(CharSequence title);

		default Builder setTitle(@StringRes int title) {
			return setTitle(getMenu().getContext().getString(title));
		}

		View inflate(@LayoutRes int layout);

		Builder setView(View view);

		Builder setSelectionHandler(SelectionHandler selectionHandler);

		Builder setCloseHandlerHandler(CloseHandler closeHandler);

		default Builder withSelectionHandler(SelectionHandler selectionHandler) {
			return new Builder() {
				@Override
				public OverlayMenu getMenu() {
					return Builder.this.getMenu();
				}

				@Override
				public OverlayMenuItem addItem(int id, Drawable icon, CharSequence title) {
					return Builder.this.addItem(id, icon, title).setHandler(selectionHandler);
				}

				@Override
				public OverlayMenuItem addItem(int id, Drawable icon, CharSequence title, int relativeToId, boolean after) {
					return Builder.this.addItem(id, icon, title, relativeToId, after).setHandler(selectionHandler);
				}

				@Override
				public Builder setSelectedItem(OverlayMenuItem item) {
					Builder.this.setSelectedItem(item);
					return this;
				}

				@Override
				public Builder setTitle(CharSequence title) {
					Builder.this.setTitle(title);
					return this;
				}

				@Override
				public View inflate(int layout) {
					View v = Builder.this.inflate(layout);
					for (OverlayMenuItem i : getMenu().getItems()) {
						i.setHandler(selectionHandler);
					}
					return v;
				}

				@Override
				public Builder setView(View view) {
					Builder.this.setView(view);
					for (OverlayMenuItem i : getMenu().getItems()) {
						i.setHandler(selectionHandler);
					}
					return this;
				}

				@Override
				public Builder setSelectionHandler(SelectionHandler selectionHandler) {
					Builder.this.setSelectionHandler(selectionHandler);
					return this;
				}

				@Override
				public Builder setCloseHandlerHandler(CloseHandler closeHandler) {
					Builder.this.setCloseHandlerHandler(closeHandler);
					return this;
				}
			};
		}
	}

	interface SelectionHandler {
		boolean menuItemSelected(OverlayMenuItem item);
	}

	interface CloseHandler {
		void menuClosed(OverlayMenu menu);
	}
}
