package me.aap.fermata.media.service;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;

import java.util.List;

public class MediaSessionState implements Parcelable {
	public final PlaybackStateCompat playbackState;
	public final MediaMetadataCompat meta;
	public final List<MediaSessionCompat.QueueItem> queue;
	public final int repeat;
	public final int shuffle;

	public MediaSessionState(PlaybackStateCompat playbackState, MediaMetadataCompat meta,
													 List<MediaSessionCompat.QueueItem> queue, int repeat, int shuffle) {
		this.playbackState = playbackState;
		this.meta = meta;
		this.queue = queue;
		this.repeat = repeat;
		this.shuffle = shuffle;
	}

	protected MediaSessionState(Parcel in) {
		playbackState = in.readParcelable(PlaybackStateCompat.class.getClassLoader());
		meta = in.readParcelable(MediaMetadataCompat.class.getClassLoader());
		queue = in.createTypedArrayList(MediaSessionCompat.QueueItem.CREATOR);
		repeat = in.readInt();
		shuffle = in.readInt();
	}

	public static final Creator<MediaSessionState> CREATOR = new Creator<MediaSessionState>() {
		@Override
		public MediaSessionState createFromParcel(Parcel in) {
			return new MediaSessionState(in);
		}

		@Override
		public MediaSessionState[] newArray(int size) {
			return new MediaSessionState[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeParcelable(playbackState, flags);
		dest.writeParcelable(meta, flags);
		dest.writeTypedList(queue);
		dest.writeInt(repeat);
		dest.writeInt(shuffle);
	}

	@NonNull
	@Override
	public String toString() {
		return "MediaSessionState{" +
				"playbackState=" + playbackState +
				", meta=" + meta +
				", queue=" + queue +
				", repeat=" + repeat +
				", shuffle=" + shuffle +
				'}';
	}
}