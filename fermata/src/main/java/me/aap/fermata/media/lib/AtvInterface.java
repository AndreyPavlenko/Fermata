package me.aap.fermata.media.lib;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.graphics.Bitmap.Config.ARGB_8888;
import static androidx.tvprovider.media.tv.ChannelLogoUtils.storeChannelLogo;
import static androidx.tvprovider.media.tv.TvContractCompat.buildPreviewProgramUri;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_END_TIME;
import static me.aap.fermata.media.lib.MediaLib.StreamItem.STREAM_START_TIME;
import static me.aap.fermata.ui.activity.MainActivityDelegate.INTENT_ACTION_OPEN;
import static me.aap.fermata.ui.activity.MainActivityDelegate.INTENT_ACTION_PLAY;
import static me.aap.fermata.ui.activity.MainActivityDelegate.INTENT_ACTION_UPDATE;
import static me.aap.fermata.ui.activity.MainActivityDelegate.intentUriToId;
import static me.aap.fermata.ui.activity.MainActivityDelegate.toIntentUri;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.collection.CollectionUtils.forEach;
import static me.aap.utils.collection.CollectionUtils.putIfAbsent;
import static me.aap.utils.misc.MiscUtils.ifNull;
import static me.aap.utils.ui.UiUtils.toIntPx;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.tvprovider.media.tv.Channel;
import androidx.tvprovider.media.tv.PreviewProgram;
import androidx.tvprovider.media.tv.TvContractCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.fermata.provider.FermataContentProvider;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class AtvInterface implements Item.ChangeListener {
	private final DefaultMediaLib lib;
	private final SharedPreferenceStore prefs;
	private final Map<String, Long> channels = new HashMap<>();
	private final Map<String, Prog> programs = new HashMap<>();

	private AtvInterface(DefaultMediaLib lib) {
		this.lib = lib;
		prefs = SharedPreferenceStore.create(lib.getContext(), "atv");
	}

	@Nullable
	public static AtvInterface create(DefaultMediaLib lib) {
		Context ctx = lib.getContext();
		if (!ctx.getPackageManager().hasSystemFeature(FEATURE_LEANBACK)) return null;
		AtvInterface aif = new AtvInterface(lib);
		if (!aif.addChannel(lib.getFavorites())) return null;
		lib.getPlaylists().getChildren().onSuccess(list -> forEach(list, aif::addChannel));
		return aif;
	}

	public boolean addChannel(Item i) {
		if (!(i instanceof BrowsableItem)) return false;
		BrowsableItem folder = (BrowsableItem) i;
		String id = folder.getId();
		Pref<LongSupplier> cidPref = cidPref(id);
		long cid = prefs.getLongPref(cidPref);

		if (cid < 0) {
			Context ctx = getContext();
			Channel.Builder cb = new Channel.Builder();
			cb.setType(TvContractCompat.Channels.TYPE_PREVIEW)
					.setDisplayName(folder.getName())
					.setAppLinkIntentUri(toIntentUri(INTENT_ACTION_OPEN, id));
			Uri uri = ctx.getContentResolver().insert(TvContractCompat.Channels.CONTENT_URI,
					cb.build().toContentValues());
			if (uri == null) return false;

			cid = ContentUris.parseId(uri);
			if (cid < 0) return false;
			prefs.applyLongPref(cidPref, cid);
			TvContractCompat.requestChannelBrowsable(ctx, cid);
			long ccid = cid;
			createIcon(folder).onSuccess(u -> storeChannelLogo(ctx, ccid, u));
		}

		channels.put(id, cid);
		folder.getChildren().main().onSuccess(list -> forEach(list, this::addProgram));
		return true;
	}

	public void removeChannel(Item i) {
		String id = i.getId();
		Pref<LongSupplier> cidPref = cidPref(id);
		long cid = prefs.getLongPref(cidPref);
		channels.remove(id);
		if (cid < 0) return;
		prefs.removePref(cidPref);
		forEach(new ArrayList<>(programs.values()), p -> {
			if (p.cid == cid) removeProgram(p.item);
		});
		getContext().getContentResolver().delete(TvContractCompat.buildChannelUri(cid), null, null);
	}

	public void addProgram(Item i) {
		if (!(i instanceof PlayableItem)) return;
		PlayableItem pi = (PlayableItem) i;
		if (!pi.isVideo()) return;
		Long cid = channels.get(pi.getParent().getId());
		if (cid == null) return;
		Prog p = new Prog(pi, cid, prefs.getLongPref(pidPref(pi.getId())));
		if (putIfAbsent(programs, pi.getId(), p) != null) return;
		i.addChangeListener(this);
		updateProg(p);
	}

	public void removeProgram(Item i) {
		String id = i.getId();
		Prog p = programs.remove(id);
		long pid = (p == null) ? -1 : p.pid;
		Pref<LongSupplier> pref = pidPref(id);
		if (pid < 0) pid = prefs.getLongPref(pref);
		programs.remove(id);
		prefs.removePref(pref);
		i.removeChangeListener(this);
		if (pid >= 0) getContext().getContentResolver().delete(buildPreviewProgramUri(pid), null, null);
	}

	@Override
	public void mediaItemChanged(Item i) {
		String id = i.getId();
		Prog p = programs.get(id);
		if (p != null) updateProg(p);
		else programs.remove(id);
	}

	public void update(Intent i) {
		String id = intentUriToId(i.getData());
		Log.d("Update intent received: ", id);
		if (id == null) return;
		Prog p = programs.get(id);
		if (p != null) updateProg(p);
	}

	private void updateProg(Prog p) {
		p.cancelLoading();
		p.loading = getDescription(p.item).main().onSuccess(d -> {
			p.loading = completedNull();
			String id = p.item.getId();
			Uri art = d.getIconUri();
			PreviewProgram.Builder b = new PreviewProgram.Builder();
			b.setChannelId(p.cid)
					.setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
					.setTitle(getTitle(p, d))
					.setDescription(ifNull(d.getSubtitle(), "").toString())
					.setIntentUri(toIntentUri(INTENT_ACTION_PLAY, id));
			if (p.item.isStream()) b.setLive(true);
			if (art != null) b.setPosterArtUri(FermataContentProvider.toImgUri(art));

			Bundle ext = d.getExtras();
			long start = 0;
			long end = 0;

			if (ext != null) {
				start = ext.getLong(STREAM_START_TIME);
				end = ext.getLong(STREAM_END_TIME);
				if (start < end) {
					b.setStartTimeUtcMillis(start);
					b.setEndTimeUtcMillis(end);
				}
			}

			if (p.pid == -1) {
				Uri uri = getContext().getContentResolver()
						.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, b.build().toContentValues());
				if ((uri != null) && ((p.pid = ContentUris.parseId(uri)) >= 0)) {
					prefs.applyLongPref(pidPref(id), p.pid);
				} else {
					Log.w("Failed to add program ", d.getTitle());
					removeProgram(p.item);
					return;
				}
			} else {
				getContext().getContentResolver().update(buildPreviewProgramUri(p.pid),
						b.build().toContentValues(), null, null);
			}

			if ((start < end) && (end > System.currentTimeMillis())) scheduleUpdate(end, id);
		});
	}

	private FutureSupplier<MediaDescriptionCompat> getDescription(PlayableItem pi) {
		return pi.getMediaDescription().then(d -> {
			if (d.getIconUri() != null) return completed(d);
			return createIcon(pi).map(uri -> {
				MediaDescriptionCompat.Builder b = new MediaDescriptionCompat.Builder();
				b.setTitle(d.getTitle());
				b.setSubtitle(d.getSubtitle());
				b.setIconUri(uri);
				b.setExtras(d.getExtras());
				return b.build();
			});
		});
	}

	private String getTitle(Prog p, MediaDescriptionCompat d) {
		CharSequence t = d.getTitle();
		if (t == null) return p.item.getName();
		String title = t.toString();
		int idx = title.indexOf(". ");
		return (idx < 0) ? title : title.substring(idx + 2);
	}

	private void scheduleUpdate(long time, String id) {
		Log.d("Scheduling update for ", id, " at ", new Date(time));
		AlarmManager amgr = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
		if (amgr == null) return;
		Intent i = new Intent(getContext(), AtvUpdateReceiver.class);
		i.setData(MainActivityDelegate.toIntentUri(INTENT_ACTION_UPDATE, id));
		PendingIntent pi = PendingIntent.getBroadcast(getContext(), 0, i, FLAG_IMMUTABLE);
		amgr.set(AlarmManager.RTC, time, pi);
	}

	private static Pref<LongSupplier> cidPref(String id) {
		return Pref.l(id + "#CID", -1);
	}

	private static Pref<LongSupplier> pidPref(String id) {
		return Pref.l(id + "#PID", -1);
	}

	private Context getContext() {
		return lib.getContext();
	}

	private FutureSupplier<Uri> createIcon(Item item) {
		int icon;
		int size;
		String uri;
		boolean drawBg;

		if (item instanceof PlayableItem) {
			icon = item.getIcon();
			size = toIntPx(getContext(), 128);
			uri = "atv://icon/128/" + item.getClass().getSimpleName() + ".png";
			drawBg = true;
		} else {
			icon = R.drawable.launcher;
			size = toIntPx(getContext(), 80);
			uri = "atv://icon/80/fermata.png";
			drawBg = false;
		}

		return lib.getBitmapCache().addImage(uri, () -> {
			Context ctx = getContext();
			Resources res = ctx.getResources();
			Resources.Theme theme = ctx.getTheme();
			Drawable fg = ResourcesCompat.getDrawable(res, icon, theme);
			if (fg == null) throw new IOException("Failed to load drawable " + uri);
			Bitmap bm = Bitmap.createBitmap(size, size, ARGB_8888);
			Canvas c = new Canvas(bm);

			if (drawBg) {
				Drawable bg = ResourcesCompat.getDrawable(res, R.drawable.fermata_bg, theme);
				if (bg == null) throw new IOException("Failed to load background drawable " + uri);
				bg.setBounds(0, 0, size, size);
				bg.draw(c);
				fg.setTint(Color.WHITE);
				int off = size / 4;
				fg.setBounds(off, off, size - off, size - off);
			} else {
				fg.setBounds(0, 0, size, size);
			}

			fg.draw(c);
			return bm;
		});
	}

	private static final class Prog {
		final PlayableItem item;
		final long cid;
		long pid;
		FutureSupplier<?> loading = completedNull();

		Prog(PlayableItem item, long cid, long pid) {
			this.item = item;
			this.cid = cid;
			this.pid = pid;
		}

		void cancelLoading() {
			loading.cancel();
			loading = completedNull();
		}
	}
}
