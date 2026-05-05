package me.aap.fermata.addon;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CacheMap;
import me.aap.utils.function.Supplier;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;

@Keep
public class TranslateAddon implements FermataAddon {
	private final List<Pair<String, String>> MODELS = List.of(
			new Pair<>("me.aap.fermata.mlkit.MlkitTranslateAddon", "ML Kit"),
			new Pair<>("me.aap.fermata.opusmt.OpusMtTranslateAddon", "Opus-MT")
	);
	public static final Pref<Supplier<String>> IMPL =
			Pref.s("TRANSLATOR", "me.aap.fermata.opusmt.OpusMtTranslateAddon");
	private static final AddonInfo info = FermataAddon.findAddonInfo(TranslateAddon.class.getName());
	private final CacheMap<String, FutureSupplier<Translator>> cache = new CacheMap<>(30);

	public static FutureSupplier<TranslateAddon> get() {
		return FermataApplication.get().getAddonManager().getOrInstallAddon(TranslateAddon.class);
	}

	@Override
	public int getAddonId() {
		return R.id.translate_addon;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore ps, PreferenceSet set,
																 ChangeableCondition visibility) {
		set.addListPref(o -> {
			var models = new ArrayList<Pair<String, String>>(MODELS.size() + 1);
			models.addAll(MODELS);
			models.add(new Pair<>("all", ctx.getString(R.string.select_all)));
			o.setStringValues(ps, IMPL, models);
			o.title = me.aap.fermata.R.string.sub_gen_model;
			o.subtitle = me.aap.fermata.R.string.string_format;
			o.formatSubtitle = true;
			o.visibility = visibility.copy();
		});
	}

	public FutureSupplier<Translator> getTranslator(PreferenceStore ps, String srcLang,
																									String targetLang) {
		var key = srcLang + "->" + targetLang;
		var p = cache.computeIfAbsent(key,
				k -> getImpl(ps).then(impl -> impl.getTranslator(srcLang, targetLang)));
		if (!requireNonNull(p).isDone()) p.onSuccess(tr -> cache.put(key, completed(tr)));
		return p;
	}

	protected FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
		throw new UnsupportedOperationException("Not implemented");
	}

	public List<Pair<String, String>> getSupportedLanguages(PreferenceStore ps,
																													@Nullable String srcLang) {
		var impl = getImpl(ps).peek();
		return impl != null ? impl.getSupportedLanguages(srcLang) :
				Collections.singletonList(new Pair<>("none", App.get().getString(R.string.loading)));
	}

	protected List<Pair<String, String>> getSupportedLanguages(@Nullable String srcLang) {
		throw new UnsupportedOperationException("Not implemented");
	}

	private FutureSupplier<TranslateAddon> getImpl(PreferenceStore ps) {
		var mgr = AddonManager.get();
		var name = ps.getStringPref(IMPL);
		if (!"all".equals(name)) return mgr.getOrInstallAddon(name).cast();
		var impls = new ConcurrentHashMap<String, TranslateAddon>();
		return Async.forEach(m -> mgr.getOrInstallAddon(m.first).<TranslateAddon>cast()
				.onSuccess(a -> impls.put(m.second, a)), MODELS).map(v -> new TranslateAddon() {
			@Override
			protected List<Pair<String, String>> getSupportedLanguages(
					@Nullable String srcLang) {
				// Get languages from all imps and select only those supported by all
				var allLangs = new ArrayList<Map<String, String>>(impls.size());
				for (var e : impls.entrySet()) {
					var m = new HashMap<String, String>();
					allLangs.add(m);
					for (var p : e.getValue().getSupportedLanguages(srcLang)) {
						m.put(p.first, p.second);
					}
				}
				var common = new ArrayList<Pair<String, String>>();
				for (var e : allLangs.get(0).entrySet()) {
					var code = e.getKey();
					var name = e.getValue();
					boolean supported = true;
					for (int i = 1; i < allLangs.size(); i++) {
						if (!allLangs.get(i).containsKey(code)) {
							supported = false;
							break;
						}
					}
					if (supported) common.add(new Pair<>(code, name));
				}
				common.sort((a, b) -> a.second.compareToIgnoreCase(b.second));
				return common;
			}

			@Override
			protected FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
				var translators = new ConcurrentHashMap<String, Translator>();
				return Async.forEach(m -> m.getValue().getTranslator(srcLang, targetLang)
						.onSuccess(t -> translators.put(m.getKey(), t)), impls.entrySet()).map(v -> text -> {
					var translation = new StringBuilder(256);
					return Async.forEach(t -> t.getValue().translate(text).onSuccess(tr -> {
						synchronized (translation) {
							if (translation.length() > 0) translation.append("\n<br/>");
							translation.append('[').append(t.getKey()).append("] ").append(tr);
						}
					}), translators.entrySet()).map(v2 -> translation.toString());
				});
			}

			@Override
			protected Collection<String> cleanUp(String srcLang, String targetLang) {
				var deleted = new ArrayList<String>();
				for (var impl : impls.values()) deleted.addAll(impl.cleanUp(srcLang, targetLang));
				return deleted;
			}
		});
	}

	public interface Translator {
		FutureSupplier<String> translate(String text);

		default boolean supportsBatch() {
			return false;
		}
	}

	public Collection<String> cleanUp(PreferenceStore ps, String srcLang,
																		String targetLang) {
		var impl = getImpl(ps).peek();
		return impl != null ? impl.cleanUp(srcLang, targetLang) : emptyList();
	}

	protected Collection<String> cleanUp(String srcLang, String targetLang) {
		return emptyList();
	}
}
