package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.media.sub.Subtitles;
import me.aap.fermata.ui.activity.MainActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CacheMap;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityBase;

@Keep
public class SubGenAddon implements FermataAddon {
	public static final Pref<BooleanSupplier> ENABLED = Pref.b("SG_ENABLED", false);
	public static final Pref<Supplier<String>> IMPL = Pref.s("SG_IMPL", "whisper");
	public static final Pref<Supplier<String>> LANG = Pref.s("SG_LANG", "auto");
	public static final Pref<IntSupplier> BUF_LEN = Pref.i("SG_BUF_LEN", 20);
	public static final Pref<IntSupplier> CHUNK_LEN = Pref.i("SG_CHUNK_LEN", 5);
	public static final PreferenceStore.Pref<BooleanSupplier> TRANSLATE =
			PreferenceStore.Pref.b("SG_TRANSLATE", false);
	private static final AddonInfo info = FermataAddon.findAddonInfo(SubGenAddon.class.getName());
	private final CacheMap<Object, Transcriptor> cache = new CacheMap<>(30);

	@Override
	public int getAddonId() {
		return R.id.subgen_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public final void contributeSettings(Context ctx, PreferenceStore ps, PreferenceSet set,
																			 ChangeableCondition visibility) {
		contributeSettings(ctx, ps, set, visibility, true);
	}

	public void contributeSettings(Context ctx, PreferenceStore ps, PreferenceSet set,
																 ChangeableCondition visibility, boolean isGlobalSettings) {
		var mps = isGlobalSettings ?
				MainActivityDelegate.get(ctx).getMediaServiceBinder().getLib().getPrefs() : ps;

		if (getClass() == SubGenAddon.class) {
			var impl = getImpl().peek();
			if (impl != null) {
				impl.contributeSettings(ctx, mps, set, visibility, isGlobalSettings);
				return;
			}
		}

		set.addListPref(o -> {
			o.setStringValues(mps, LANG, getSupportedLanguages());
			o.title = R.string.lang;
			o.subtitle = R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = visibility.copy();
		});
		set.addBooleanPref(o -> {
			o.store = ps;
			o.pref = TRANSLATE;
			o.title = me.aap.fermata.R.string.sub_gen_translate_en;
			o.visibility = visibility.copy();
		});
		set.addIntPref(o -> {
			o.store = mps;
			o.pref = BUF_LEN;
			o.title = R.string.sub_gen_buf;
			o.subtitle = R.string.sub_gen_buf_sub;
			o.seekMin = 10;
			o.seekMax = 60;
			o.seekScale = 5;
			o.visibility = visibility.copy();
		});
		set.addIntPref(o -> {
			o.store = mps;
			o.pref = CHUNK_LEN;
			o.title = R.string.sub_gen_chunk;
			o.subtitle = R.string.sub_gen_chunk_sub;
			o.seekMin = 5;
			o.seekMax = 20;
			o.seekScale = 5;
			o.visibility = visibility.copy();
		});
	}

	@Override
	public void install() {
		if (getClass() != SubGenAddon.class) return;
		var ps = FermataApplication.get().getPreferenceStore();
		ps.applyBooleanPref(getImplInfo().enabledPref, true);
	}

	@Override
	public void uninstall() {
		FermataApplication.get().getPreferenceStore()
				.applyBooleanPref(getImplInfo().enabledPref, false);
	}

	public FutureSupplier<Transcriptor> getTranscriptor(PreferenceStore ps) {
		var getImpl = getImpl();
		var impl = getImpl.peek();
		if (impl != null) {
			var key = impl.createCacheKey(ps);
			var cached = cache.remove(key);
			if (cached != null) {
				Log.d("Returning cached transcriptor: ", key);
				return completed(new CachedTranscriptor(key, cached));
			}
		}

		var t = getImpl.then(
				i -> i.getTranscriptor(ps)
						.map(r -> (Transcriptor) new CachedTranscriptor(i.createCacheKey(ps), r)));
		if (t.isDoneNotFailed()) return t;
		var ctx = App.get();
		ActivityBase.create(App.get(), "me.aap.fermata.subgen", "subgen",
				me.aap.fermata.R.drawable.notification, ctx.getString(R.string.downloading, "..."),
				null, MainActivity.class).onSuccess(a -> {
			if (t.isDoneNotFailed()) return;
			if (t.isFailed()) {
				var err = t.getFailure();
				UiUtils.showAlert(a, ctx.getString(R.string.sub_gen_download_fail,
						err == null ? "" : err.getLocalizedMessage()));
			} else {
				UiUtils.showInfo(a, ctx.getString(R.string.sub_gen_downloading));
			}
		});
		return t;
	}

	protected Object createCacheKey(PreferenceStore ps) {
		return ps.getStringPref(IMPL) + "|" + ps.getStringPref(LANG) + "|" +
				ps.getIntPref(BUF_LEN) + "|" + ps.getIntPref(CHUNK_LEN);
	}

	protected List<Pair<String, String>> getSupportedLanguages() {
		return Collections.singletonList(
				new Pair<>("auto", FermataApplication.get().getString(R.string.auto)));
	}

	private AddonInfo getImplInfo() {
		var app = FermataApplication.get();
		return FermataAddon.findAddonInfo(app.getPreferenceStore().getStringPref(IMPL));
	}

	private FutureSupplier<SubGenAddon> getImpl() {
		var app = FermataApplication.get();
		return app.getAddonManager().getOrInstallAddon(app.getPreferenceStore()
				.getStringPref(IMPL)).cast();
	}

	public interface Transcriptor {
		boolean reconfigure(PreferenceStore ps);

		boolean read(ByteBuffer buf, int chunkLen, int bytesPerSample, int channels, int frameRate);

		List<Subtitles.Text> transcribe(long timeOffset);

		void reset();

		void release();
	}

	private class CachedTranscriptor implements Transcriptor {
		private final Object key;
		private Transcriptor transcriptor;

		CachedTranscriptor(Object key, Transcriptor transcriptor) {
			this.key = key;
			this.transcriptor = transcriptor;
		}

		@Override
		public synchronized boolean reconfigure(PreferenceStore ps) {
			var impl = getImpl().peek();
			if (impl == null) return false;
			return key.equals(impl.createCacheKey(ps)) && get().reconfigure(ps);
		}

		@Override
		public synchronized boolean read(ByteBuffer buf, int chunkLen, int bytesPerSample,
																		 int channels,
																		 int frameRate) {
			return get().read(buf, chunkLen, bytesPerSample, channels, frameRate);
		}

		@Override
		public synchronized List<Subtitles.Text> transcribe(long timeOffset) {
			return get().transcribe(timeOffset);
		}

		@Override
		public synchronized void reset() {
			get().reset();
		}

		@Override
		public synchronized void release() {
			var t = get();
			transcriptor = null;
			if (cache.putIfAbsent(key, t) == null) {
				Log.d("Transcriptor cached: ", key);
			} else {
				Log.d("Releasing transcriptor: ", key);
				t.release();
			}
		}

		private Transcriptor get() {
			if (transcriptor == null) throw new IllegalStateException("Transcriptor released!");
			return transcriptor;
		}

	}
}
