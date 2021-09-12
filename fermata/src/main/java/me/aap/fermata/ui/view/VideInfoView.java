package me.aap.fermata.ui.view;

import static me.aap.utils.text.TextUtils.isNullOrBlank;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.textview.MaterialTextView;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class VideInfoView extends ConstraintLayout
		implements MainActivityListener, FermataServiceUiBinder.Listener {

	public VideInfoView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public VideInfoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		inflate(context, R.layout.video_info_layout, this);
		MainActivityDelegate a = getActivity();
		a.addBroadcastListener(this, ACTIVITY_DESTROY);
		a.getMediaServiceBinder().addBroadcastListener(this);
		setBackgroundColor(Color.BLACK);
		onPlayableChanged(null, a.getCurrentPlayable());
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (handleActivityDestroyEvent(a, e)) a.getMediaServiceBinder().removeBroadcastListener(this);
	}

	@Override
	public void onPlayableChanged(PlayableItem oldItem, PlayableItem newItem) {
		if ((newItem == null) || !newItem.isVideo()) {
			setVisibility(GONE);
			return;
		}

		FutureSupplier<MediaDescriptionCompat> getDsc = newItem.getMediaDescription();
		FutureSupplier<MediaMetadataCompat> getMd = newItem.getMediaData();

		if (getDsc.isDone() && !getDsc.isFailed()) {
			apply(newItem, getDsc.getOrThrow());

			if (getMd.isDone() && !getMd.isFailed()) {
				apply(newItem, getMd.getOrThrow());
			} else {
				getMd.main().onSuccess(md -> {
					if (isCurrent(newItem)) apply(newItem, md);
				});
			}
		} else {
			getTitleView().setText(newItem.getName());
			getIconView().setVisibility(GONE);
			getSubtitleView().setVisibility(GONE);
			getSubtitleView().setVisibility(GONE);
			getDescriptionView().setVisibility(GONE);

			getDsc.main().onSuccess(dsc -> {
				if (isCurrent(newItem)) {
					apply(newItem, dsc);
					getMd.main().onSuccess(md -> {
						if (isCurrent(newItem)) apply(newItem, md);
					});
				}
			});
		}
	}

	private boolean isCurrent(PlayableItem i) {
		return (getActivity().getCurrentPlayable() == i);
	}

	private void apply(PlayableItem item, MediaDescriptionCompat md) {
		Uri i = md.getIconUri();
		CharSequence t = md.getTitle();
		CharSequence s = md.getSubtitle();
		CharSequence d = md.getDescription();
		MaterialTextView tv = getTitleView();
		MaterialTextView sv = getSubtitleView();
		MaterialTextView dv = getDescriptionView();
		getIconView().setVisibility(GONE);
		if (i != null) setIcon(item, i.toString());
		if (!isNullOrBlank(t)) tv.setText(t);

		if (isNullOrBlank(s)) {
			sv.setVisibility(GONE);
		} else {
			sv.setText(s);
			sv.setVisibility(VISIBLE);
		}
		if (isNullOrBlank(d)) {
			dv.setVisibility(GONE);
		} else {
			dv.setText(d);
			dv.setVisibility(VISIBLE);
		}
	}

	private void apply(PlayableItem item, MediaMetadataCompat md) {
		String i = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI);
		String s = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE);
		String d = md.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION);
		MaterialTextView sv = getSubtitleView();
		MaterialTextView dv = getDescriptionView();
		setIcon(item, i);

		if (!isNullOrBlank(s)) {
			sv.setText(s);
			sv.setVisibility(VISIBLE);
		}
		if (!isNullOrBlank(d)) {
			dv.setText(d);
			dv.setVisibility(VISIBLE);
		}
	}

	private void setIcon(PlayableItem item, String icon) {
		if (icon != null) {
			item.getLib().getBitmap(icon, true, false).main().onSuccess(b -> {
				if ((b != null) && isCurrent(item)) {
					AppCompatImageView iv = getIconView();
					iv.setImageBitmap(b);
					iv.setImageTintList(null);
					iv.setVisibility(VISIBLE);
				}
			});
		}
	}

	private AppCompatImageView getIconView() {
		return findViewById(R.id.media_item_icon);
	}

	private MaterialTextView getTitleView() {
		return findViewById(R.id.media_item_title);
	}

	private MaterialTextView getSubtitleView() {
		return findViewById(R.id.media_item_subtitle);
	}

	private MaterialTextView getDescriptionView() {
		return findViewById(R.id.media_item_dsc);
	}

	private MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
