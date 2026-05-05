package me.aap.fermata.opusmt;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readAllBytes;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.opusmt.SentencePieceTokenizer.fromJson;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;

import android.util.Pair;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.TranslateAddon;
import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.io.FileUtils;
import me.aap.utils.log.Log;

@Keep
@SuppressWarnings("unused")
public class OpusMtTranslateAddon extends TranslateAddon {
	private static final AddonInfo info =
			FermataAddon.findAddonInfo(OpusMtTranslateAddon.class.getName());
	private static final ModelInfo EN_MODEL =
			new ModelInfo("af", "ar", "cs", "da", "de", "es", "fi", "fr", "hi", "hu", "id", "it",
					"ja", "nl", "ro", "ru", "sv", "uk", "vi", "zh");
	private static final Map<String, ModelInfo> MODELS = CollectionUtils.mapOf(
			"af", new ModelInfo("en"),
			"ar", new ModelInfo("en"),
			"cs", new ModelInfo("en"),
			"da", new ModelInfo("de", "en"),
			"de", new ModelInfo("en", "es", "fr").join(new DeZleModelInfo()),
			"en", EN_MODEL,
			"es", new ModelInfo("de", "en", "fr", "it", "ru"),
			"et", new ModelInfo("en"),
			"fi", new ModelInfo("de", "en"),
			"fr", new ModelInfo("de", "en", "es", "ro", "ru"),
			"hi", new ModelInfo("en"),
			"hu", new ModelInfo("en"),
			"id", new ModelInfo("en"),
			"it", new ModelInfo("en", "es", "fr"),
			"ja", new ModelInfo("en"),
			"ko", new ModelInfo("en"),
			"nl", new ModelInfo("en", "fr"),
			"no", new ModelInfo("de"),
			"pl", new ModelInfo("en"),
			"ro", new ModelInfo("fr"),
			"ru", new ModelInfo("en", "es", "fr", "uk"),
			"sv", new ModelInfo("en"),
			"th", new ModelInfo("en"),
			"tr", new ModelInfo("en"),
			"uk", new ModelInfo("en", "ru"),
			"vi", new ModelInfo("en"),
			"zh", new ModelInfo("en")
	);
	private final PromiseQueue queue = new PromiseQueue(App.get().getExecutor());

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@Override
	public FutureSupplier<Translator> getTranslator(String srcLang, String tgtLang) {
		var m = MODELS.get(srcLang);
		if (m == null)
			return failed(new IllegalArgumentException("Unsupported source language: " + srcLang));
		if (m.isSupportedLang(tgtLang)) return loadModel(m, srcLang, tgtLang);
		if (!m.isSupportedLang("en") || !EN_MODEL.isSupportedLang(tgtLang))
			return failed(new IllegalArgumentException("Unsupported target language: " + tgtLang));
		var srcToEn = loadModel(m, srcLang, "en");
		var enToTgt = loadModel(EN_MODEL, "en", tgtLang);
		return Async.all(srcToEn, enToTgt).map(v -> {
			var t1 = srcToEn.peek();
			var t2 = enToTgt.peek();
			assert t1 != null && t2 != null;
			return (Translator) text -> t1.translate(text).then(t2::translate);
		});
	}

	@SuppressWarnings({"ReadWriteStringCanBeUsed", "ResultOfMethodCallIgnored"})
	private FutureSupplier<Translator> loadModel(ModelInfo m, String srcLang, String tgtLang) {
		return m.getModelFiles(srcLang, tgtLang).map(files -> {
			var encoderFile = files[0];
			var decoderFile = files[1];
			var srcTokFile = files[2];
			var tgtTokFile = files[3];
			try {
				var srcTok = fromJson(new String(readAllBytes(srcTokFile.toPath()), UTF_8));
				var tgtTok = srcTokFile.equals(tgtTokFile) ? srcTok :
						fromJson(new String(readAllBytes(tgtTokFile.toPath()), UTF_8));
				var tag = m.getTag(tgtLang);
				var tagId = tag != null ? srcTok.getTokenId(tag) : -1;
				var model = new OpusMtModel(encoderFile, decoderFile, srcTok, tgtTok);
				return text -> queue.enqueue(() -> model.translate(text, tagId));
			} catch (Exception e) {
				srcTokFile.delete();
				tgtTokFile.delete();
				throw new RuntimeException("Failed to load model for " + srcLang + "->" + tgtLang, e);
			}
		});
	}

	@Override
	public List<Pair<String, String>> getSupportedLanguages(@Nullable String srcLang) {
		var codes = new HashSet<String>();
		if (srcLang == null) {
			for (var m : MODELS.values()) codes.addAll(m.getTargetLangs());
		} else {
			var m = MODELS.get(srcLang);
			if (m == null) return emptyList();
			codes.addAll(m.getTargetLangs());
			if (!"en".equals(srcLang) && codes.contains("en")) {
				for (var code : EN_MODEL.getTargetLangs()) {
					if (!codes.contains(code)) codes.add('>' + code);
				}
			}
		}
		List<Pair<String, String>> langs = new ArrayList<>();
		var locale = Locale.getDefault(Locale.Category.DISPLAY);
		var enSfx = Locale.ENGLISH.getDisplayLanguage(locale) + " -> ";
		for (String code : codes) {
			var lang =
					code.startsWith(">") ? enSfx + new Locale(code.substring(1)).getDisplayLanguage(locale) :
							new Locale(code).getDisplayLanguage(locale);
			langs.add(new Pair<>(code, lang));
		}
		langs.sort((a, b) -> a.second.compareToIgnoreCase(b.second));
		return langs;
	}

	@Override
	protected Collection<String> cleanUp(String srcLang, String tgtLang) {
		FileFilter filter;
		var m = MODELS.get(srcLang);
		if (m != null && m.isSupportedLang(tgtLang)) {
			var name = m.getDirName(srcLang, tgtLang);
			filter = f -> !f.getName().equals(name);
		} else if (m != null && m.isSupportedLang("en") && EN_MODEL.isSupportedLang(tgtLang)) {
			var name1 = m.getDirName(srcLang, "en");
			var name2 = EN_MODEL.getDirName("en", tgtLang);
			filter = f -> !f.getName().equals(name1) && !f.getName().equals(name2);
		} else {
			filter = f -> true;
		}

		var ls = new File(App.get().getCacheDir(), "opusmt").listFiles(filter);
		if (ls == null) return emptyList();
		try {
			FileUtils.delete(ls);
		} catch (Exception err) {
			Log.e(err);
		}
		return CollectionUtils.map(Arrays.asList(ls), File::getName);
	}

	private static class ModelInfo {
		private final Set<String> targetLangs;

		private ModelInfo(String... targetLangs) {this.targetLangs = Set.of(targetLangs);}

		public Set<String> getTargetLangs() {
			return targetLangs;
		}

		boolean isSupportedLang(String lang) {return targetLangs.contains(lang);}

		String getTag(String tgtLang) {
			return null;
		}

		String getDirName(String srcLang, String tgtLang) {
			return "opus-mt-" + srcLang + "-" + tgtLang;
		}

		FutureSupplier<File[]> getModelFiles(String srcLang, String tgtLang) {
			var baseDir = new File(App.get().getCacheDir(), "opusmt/" + getDirName(srcLang, tgtLang));
			var encoder = new File(baseDir, encoderFileName());
			var decoder = new File(baseDir, decoderFileName());
			var srcTok = new File(baseDir, srcTokenizerFileName());
			var tgtTok = new File(baseDir, tgtTokenizerFileName());
			var modelUrl = modelUrl(srcLang, tgtLang);
			var tokUrl = tokenizerUrl(srcLang, tgtLang);
			var tokDownload = download(tokUrl, srcTok);
			return Async.all(tokDownload, srcTok.equals(tgtTok) ? tokDownload : download(tokUrl, tgtTok),
							download(modelUrl, encoder), download(modelUrl, decoder))
					.map(v -> new File[]{encoder, decoder, srcTok, tgtTok});
		}

		ModelInfo join(ModelInfo with) {
			return new ModelInfo() {
				@Override
				public Set<String> getTargetLangs() {
					var langs = new HashSet<>(ModelInfo.this.getTargetLangs());
					langs.addAll(with.getTargetLangs());
					return langs;
				}

				@Override
				boolean isSupportedLang(String lang) {
					return ModelInfo.this.isSupportedLang(lang) || with.isSupportedLang(lang);
				}

				@Override
				String getTag(String tgtLang) {
					return ModelInfo.this.isSupportedLang(tgtLang) ? ModelInfo.this.getTag(tgtLang) :
							with.getTag(tgtLang);
				}

				@Override
				String getDirName(String srcLang, String tgtLang) {
					return ModelInfo.this.isSupportedLang(tgtLang) ?
							ModelInfo.this.getDirName(srcLang, tgtLang) :
							with.getDirName(srcLang, tgtLang);
				}

				@Override
				FutureSupplier<File[]> getModelFiles(String srcLang, String tgtLang) {
					return ModelInfo.this.isSupportedLang(tgtLang) ?
							ModelInfo.this.getModelFiles(srcLang, tgtLang) :
							with.getModelFiles(srcLang, tgtLang);
				}
			};
		}

		String encoderFileName() {
			return "encoder_model.onnx";
		}

		String decoderFileName() {
			return "decoder_model.onnx";
		}

		String srcTokenizerFileName() {
			return "tokenizer.json";
		}

		String tgtTokenizerFileName() {
			return srcTokenizerFileName();
		}

		String modelUrl(String srcLang, String tgtLang) {
			return tokenizerUrl(srcLang, tgtLang) + "/onnx";
		}

		String tokenizerUrl(String srcLang, String tgtLang) {
			if ("en".equals(srcLang) && "ja".equals(tgtLang)) tgtLang = "jap";
			return "https://huggingface.co/Xenova/opus-mt-" + srcLang + "-" + tgtLang + "/resolve/main";
		}

		private static FutureSupplier<?> download(String base, File dest) {
			if (dest.isFile() && dest.length() > 0) return completedVoid();
			var url = base + "/" + dest.getName() + "?download=true";
			Log.i("Downloading OpusMT model file: ", url);
			return Utils.createDownloader(url).download(url, dest);
		}
	}

	private static class DeZleModelInfo extends ModelInfo {

		private DeZleModelInfo() {
			super("be", "ru", "uk");
		}

		@Override
		String getTag(String tgtLang) {
			return switch (tgtLang) {
				case "be" -> ">>bel<<";
				case "ru" -> ">>rus<<";
				case "uk" -> ">>ukr<<";
				default -> null;
			};
		}

		@Override
		String getDirName(String srcLang, String tgtLang) {
			return "opus-mt-tc-big-de-zle";
		}

		@Override
		String encoderFileName() {
			return "encoder_model_int8.onnx";
		}

		@Override
		String decoderFileName() {
			return "decoder_model_int8.onnx";
		}

		@Override
		String tgtTokenizerFileName() {
			return "target_tokenizer.json";
		}

		@Override
		String modelUrl(String srcLang, String tgtLang) {
			return "https://huggingface.co/AndreyPavlenko/opus-mt-tc-big-de-zle/resolve/main";
		}

		@Override
		String tokenizerUrl(String srcLang, String tgtLang) {
			return modelUrl(srcLang, tgtLang);
		}
	}
}
