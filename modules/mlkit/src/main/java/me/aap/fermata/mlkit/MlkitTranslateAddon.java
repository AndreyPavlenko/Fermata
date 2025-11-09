package me.aap.fermata.mlkit;

import android.os.Build;
import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.TranslateAddon;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;

@Keep
@SuppressWarnings("unused")
public class MlkitTranslateAddon extends TranslateAddon {
	private static final AddonInfo info =
			FermataAddon.findAddonInfo(MlkitTranslateAddon.class.getName());

	public FutureSupplier<Translator> getTranslator(String srcLang, String targetLang) {
		var translator = Translation.getClient(new TranslatorOptions.Builder()
				.setSourceLanguage(srcLang).setTargetLanguage(targetLang)
				.setExecutor(App.get().getExecutor()).build());
		var p = new Promise<Translator>();
		translator.downloadModelIfNeeded()
				.addOnSuccessListener(v -> p.complete(new MlkitTranslator(translator)))
				.addOnCanceledListener(p::cancel)
				.addOnFailureListener(p::completeExceptionally);
		return p;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public List<Pair<String, String>> getSupportedLanguages() {
		var locale = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ?
				Locale.getDefault(Locale.Category.DISPLAY) : Locale.getDefault();
		var langs = CollectionUtils.map(TranslateLanguage.getAllLanguages(),
				lang -> new Pair<>(lang, new Locale(lang).getDisplayLanguage(locale)));
		Collections.sort(langs, (a, b) -> a.second.compareToIgnoreCase(b.second));
		return langs;
	}

	private record MlkitTranslator(com.google.mlkit.nl.translate.Translator translator)
			implements Translator {

		public FutureSupplier<String> translate(String text) {
			var p = new Promise<String>();
			translator.translate(text)
					.addOnSuccessListener(p::complete)
					.addOnCanceledListener(p::cancel)
					.addOnFailureListener(p::completeExceptionally);
			return p;
		}
	}
}
