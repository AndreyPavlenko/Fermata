package me.aap.fermata.addon;

import static me.aap.utils.async.Completed.completed;

import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CacheMap;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore.Pref;

@Keep
public class TranslateAddon implements FermataAddon {
	public static final Pref<Supplier<String>> IMPL =
			Pref.s("TR_IMPL", "me.aap.fermata.mlkit.MlkitTranslateAddon");
	private static final AddonInfo info = FermataAddon.findAddonInfo(TranslateAddon.class.getName());
	private final CacheMap<String, Translator> cache = new CacheMap<>(30);

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

	public FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
		String key = srcLang + "->" + targetLang;
		Translator translator = cache.get(key);
		if (translator != null) return completed(translator);
		return getImpl().then(impl -> impl.getTranslator(srcLang, targetLang))
				.onSuccess(tr -> cache.put(key, tr));
	}

	public List<Pair<String, String>> getSupportedLanguages() {
		var impl = getImpl().peek();
		return impl != null ? impl.getSupportedLanguages() :
				Collections.singletonList(new Pair<>("none", App.get().getString(R.string.loading)));
	}

	private FutureSupplier<TranslateAddon> getImpl() {
		return AddonManager.get().getOrInstallAddon(FermataApplication.get()
				.getPreferenceStore().getStringPref(IMPL)).cast();
	}

	public interface Translator {
		FutureSupplier<String> translate(String text);
	}
}
