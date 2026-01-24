package me.aap.utils.ui.view;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.appcompat.content.res.AppCompatResources;

/**
 * @author Andrey Pavlenko
 */
public interface NavBarItem {

	@IdRes
	int getId();

	Drawable getIcon();

	CharSequence getText();

	boolean isPinned();

	static NavBarItem create(Context ctx, @IdRes int id, @DrawableRes int icon, @StringRes int text, boolean pinned) {
		Drawable i = AppCompatResources.getDrawable(ctx, icon);
		CharSequence t = ctx.getString(text);
		return new NavBarItem() {
			@Override
			public int getId() {
				return id;
			}

			@Override
			public Drawable getIcon() {
				return i;
			}

			@Override
			public CharSequence getText() {
				return t;
			}

			@Override
			public boolean isPinned() {
				return pinned;
			}
		};
	}
}
