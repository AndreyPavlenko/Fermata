package me.aap.fermata.ui.view;

import android.content.Context;
import android.support.v4.media.MediaMetadataCompat;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.textview.MaterialTextView;

import me.aap.fermata.R;
import me.aap.fermata.ui.activity.MainActivityDelegate;

/**
 * @author Andrey Pavlenko
 */
public class MediaItemDescriptionView extends ConstraintLayout {
	private boolean hasDescription;

	public MediaItemDescriptionView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MediaItemDescriptionView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		inflate(context, R.layout.media_item_dsc_layout, this);
		setAlpha(0.5f);
	}

	public boolean hasDescription() {
		return hasDescription;
	}

	public boolean apply(MediaMetadataCompat dsc) {
		hasDescription = false;
		String title = dsc.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE);
		if (title == null) return false;
		String subTitle = dsc.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE);
		String descr = dsc.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION);
		String icon = dsc.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);
		MaterialTextView tv = findViewById(R.id.media_item_title);
		AppCompatImageView iv = findViewById(R.id.media_item_icon);
		tv.setText(title);
		iv.setVisibility(GONE);

		if (icon != null) {
			MainActivityDelegate.get(getContext()).getLib().getBitmap(icon).main().onSuccess(b -> {
				iv.setImageBitmap(b);
				iv.setVisibility(VISIBLE);
			});
		}

		tv = findViewById(R.id.media_item_subtitle);
		if (subTitle != null) {
			tv.setText(subTitle);
			tv.setVisibility(VISIBLE);
		} else {
			tv.setText("");
			tv.setVisibility(GONE);
		}

		tv = findViewById(R.id.media_item_dsc);
		if (descr != null) {
			tv.setText(descr);
			tv.setVisibility(VISIBLE);
		} else {
			tv.setText("");
			tv.setVisibility(GONE);
		}

		return hasDescription = true;
	}
}
