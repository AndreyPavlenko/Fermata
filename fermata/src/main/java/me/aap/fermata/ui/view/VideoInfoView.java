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
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.engine.StreamEngine;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.media.lib.MediaLib.StreamItem;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.utils.async.FutureSupplier;

/**
 * @author Andrey Pavlenko
 */
public class VideoInfoView extends ConstraintLayout
		implements MainActivityListener, FermataServiceUiBinder.Listener {

	public VideoInfoView(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public VideoInfoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		inflate(context, R.layout.video_info_layout, this);
		getActivity().onSuccess(a -> {
			a.addBroadcastListener(this, ACTIVITY_DESTROY);
			a.getMediaServiceBinder().addBroadcastListener(this);
			setBackgroundColor(Color.BLACK);
			onPlayableChanged(null, a.getCurrentPlayable());
		});
	}

	@Override
	public void setVisibility(int visibility) {
		if (visibility == VISIBLE) {
			getActivity().onSuccess(a -> {
				MediaEngine eng = a.getMediaServiceBinder().getCurrentEngine();
				if (eng == null) return;
				PlayableItem i = eng.getSource();
				if ((i instanceof StreamItem) && !(eng instanceof StreamEngine)) onPlayableChanged(i, i);
			});
		}

		super.setVisibility(visibility);
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
		setData(newItem, getDsc, getMd);
	}

	public void setData(PlayableItem item, FutureSupplier<MediaDescriptionCompat> getDsc,
											FutureSupplier<MediaMetadataCompat> getMd) {
		if (getDsc.isDone() && !getDsc.isFailed()) {
			setDescription(item, getDsc.getOrThrow());

			if (getMd.isDone() && !getMd.isFailed()) {
				setMetadata(item, getMd.getOrThrow());
			} else {
				getMd.main().onSuccess(md -> getActivity().onSuccess(a -> {
					if (isCurrent(a, item)) setMetadata(item, md);
				}));
			}
		} else {
			getTitleView().setText(item.getName());
			getIconView().setVisibility(GONE);
			getSubtitleView().setVisibility(GONE);
			getSubtitleView().setVisibility(GONE);
			getDescriptionView().setVisibility(GONE);

			getDsc.main().onSuccess(dsc -> getActivity().onSuccess(a -> {
				if (isCurrent(a, item)) {
					setDescription(item, dsc);
					getMd.main().onSuccess(md -> {
						if (isCurrent(a, item)) setMetadata(item, md);
					});
				}
			}));
		}
	}

	public void setDescription(PlayableItem item, MediaDescriptionCompat md) {
		if (md == null) return;
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

	public void setMetadata(PlayableItem item, MediaMetadataCompat md) {
		if (md == null) return;
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
				if (b == null) return;
				getActivity().onSuccess(a -> {
					if (isCurrent(a, item)) {
						AppCompatImageView iv = getIconView();
						iv.setImageBitmap(b);
						iv.setImageTintList(null);
						iv.setVisibility(VISIBLE);
					}
				});
			});
		}
	}

	private boolean isCurrent(MainActivityDelegate a, PlayableItem i) {
		return (a.getCurrentPlayable() == i);
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

	private FutureSupplier<MainActivityDelegate> getActivity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}
}
