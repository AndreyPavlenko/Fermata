package me.aap.fermata.addon.felex.media;

import static me.aap.utils.async.Completed.completed;

import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.addon.felex.R;
import me.aap.fermata.addon.felex.dict.DictMgr;
import me.aap.fermata.addon.felex.tutor.DictTutor;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ItemContainer;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.PlayableItemBase;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.pref.PreferenceStore;

public interface FelexItem extends MediaLib.Item {
	String SCHEME = "felex";

	static FutureSupplier<? extends MediaLib.Item> getItem(DefaultMediaLib lib,
																												 @Nullable String scheme, String id) {
		if (scheme == null) return Root.ID.equals(id) ? completed(new Root(lib)) : null;
		if (!SCHEME.equals(scheme)) return null;

		id = id.substring(SCHEME.length() + 1);
		if (id.startsWith("mgr/")) {
			var path = id.substring(4);
			return DictMgr.get().getChild(path).map(mgr ->
					(mgr == null) ? null : new Mgr(lib, mgr));
		} else if (id.startsWith("dict/")) {
			var path = id.substring(5);
			return DictMgr.get().getDictionary(path).map(dict ->
					(dict == null) ? null : new Dict(lib, dict));
		} else if (id.startsWith("tutor/") && (id.length() > 7) && (id.charAt(7) == '/')) {
			var mode = id.charAt(6) - '0';
			if (mode >= DictTutor.Mode.values.size()) return null;
			var getDict = getItem(lib, scheme, SCHEME + ":dict/" + id.substring(8));
			return (getDict == null) ? null : getDict.map(
					d -> (d == null) ? null : new Tutor((Dict) d, DictTutor.Mode.values.get(mode)));
		}

		return null;
	}

	@Override
	default boolean isCacheable() {
		return false;
	}

	class Root extends ItemContainer<FelexItem> implements FelexItem {
		public static final String ID = "Felex";
		private final DefaultMediaLib lib;

		public Root(DefaultMediaLib lib) {
			super(ID, null, null);
			this.lib = lib;
		}

		@Override
		protected FutureSupplier<List<MediaLib.Item>> listChildren() {
			return ls(lib, DictMgr.get());
		}

		@NonNull
		@Override
		public String getName() {
			return getLib().getContext().getString(me.aap.fermata.R.string.addon_name_felex);
		}

		@Override
		protected FutureSupplier<String> buildTitle() {
			return completed(getName());
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			return completed("");
		}

		@Override
		protected String getScheme() {
			return SCHEME;
		}

		@Override
		protected void saveChildren(List<FelexItem> children) {
		}

		@NonNull
		@Override
		public DefaultMediaLib getLib() {
			return lib;
		}

		@Override
		public MediaLib.BrowsableItem getParent() {
			return null;
		}

		@NonNull
		@Override
		public PreferenceStore getParentPreferenceStore() {
			return getLib();
		}

		@NonNull
		@Override
		public MediaLib.BrowsableItem getRoot() {
			return this;
		}

		static FutureSupplier<List<MediaLib.Item>> ls(DefaultMediaLib lib, DictMgr mgr) {
			return mgr.getChildren().then(mgrs -> mgr.getDictionaries().map(dicts -> {
				List<MediaLib.Item> list = CollectionUtils.map(mgrs, c -> new Mgr(lib, c));
				list.addAll(CollectionUtils.map(dicts, d -> new Dict(lib, d)));
				return list;
			}));
		}
	}

	class Mgr extends BrowsableItemBase implements FelexItem {
		private final DictMgr mgr;

		public Mgr(DefaultMediaLib lib, DictMgr mgr) {
			super(SCHEME + ":mgr/" + mgr.getPath(),
					mgr.getParent() == null ? new Root(lib) : new Mgr(lib, mgr.getParent()), null);
			this.mgr = mgr;
		}

		@Override
		protected FutureSupplier<List<MediaLib.Item>> listChildren() {
			return Root.ls((DefaultMediaLib) getLib(), mgr);
		}

		@NonNull
		@Override
		public String getName() {
			return mgr.getName();
		}

		@Override
		protected FutureSupplier<String> buildTitle() {
			return completed(getName());
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			return completed("");
		}

		@Override
		public int getIcon() {
			return R.drawable.library;
		}
	}

	class Dict extends BrowsableItemBase implements FelexItem {
		private final me.aap.fermata.addon.felex.dict.Dict dict;

		public Dict(DefaultMediaLib lib, me.aap.fermata.addon.felex.dict.Dict dict) {
			super(SCHEME + ":dict/" + dict.getPath(),
					dict.getDictMgr().getParent() == null ? new Root(lib) : new Mgr(lib, dict.getDictMgr()),
					null);
			this.dict = dict;
		}

		public me.aap.fermata.addon.felex.dict.Dict getDict() {
			return dict;
		}

		@Override
		protected FutureSupplier<List<MediaLib.Item>> listChildren() {
			return completed(CollectionUtils.map(DictTutor.Mode.values, m -> new Tutor(this, m)));
		}

		@NonNull
		@Override
		public String getName() {
			return getDict().getName();
		}

		@Override
		protected FutureSupplier<String> buildTitle() {
			return completed(getName());
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			var info = getDict().getInfo();
			return completed(info.getSourceLang().getLanguage().toUpperCase() + '-' +
					info.getTargetLang().getLanguage().toUpperCase());
		}

		@Override
		public int getIcon() {
			return R.drawable.dictionary;
		}
	}

	class Tutor extends PlayableItemBase implements FelexItem {
		private final DictTutor.Mode mode;

		public Tutor(Dict dict, DictTutor.Mode mode) {
			super(SCHEME + ":tutor/" + mode.ordinal() + '/' + dict.getDict().getPath(), dict,
					dict.getDict().getDictFile());
			this.mode = mode;
		}

		public static Tutor create(DefaultMediaLib lib, me.aap.fermata.addon.felex.dict.Dict dict,
															 DictTutor.Mode mode) {
			return new Tutor(new Dict(lib, dict), mode);
		}

		public DictTutor.Mode getMode() {
			return mode;
		}

		@NonNull
		@Override
		public Dict getParent() {
			return (Dict) super.getParent();
		}

		@Override
		public boolean isVideo() {
			return true;
		}

		@Override
		public boolean isSeekable() {
			return false;
		}

		@Override
		public String getOrigId() {
			return getId();
		}

		@NonNull
		@Override
		public String getName() {
			int resId = switch (getMode()) {
				case DIRECT -> R.string.start_tutor_dir;
				case REVERSE -> R.string.start_tutor_rev;
				case LISTENING -> R.string.start_tutor_listen;
				case MIXED -> R.string.start_tutor_mix;
			};
			return getLib().getContext().getString(resId);
		}

		@Override
		protected FutureSupplier<String> buildTitle() {
			return completed(getName());
		}

		@Override
		protected FutureSupplier<String> buildSubtitle() {
			return completed(getParent().getDict().getInfo().toString());
		}

		@Override
		protected FutureSupplier<String> buildDescription() {
			return completed("");
		}

		@NonNull
		@Override
		protected FutureSupplier<MediaMetadataCompat> loadMeta() {
			var dict = getParent();
			var name = dict.getName();
			var b = new MediaMetadataCompat.Builder();
			b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, name);
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, buildSubtitle().peek());
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, buildDescription().peek());
			return completed(b.build());
		}

		@Nullable
		@Override
		public MediaEngine getMediaEngine(@Nullable MediaEngine current,
																			MediaEngine.Listener listener) {
			return (current instanceof FelexMediaEngine) ? current : new FelexMediaEngine(listener);
		}

		boolean isPrev() {
			return false;
		}

		boolean isNext() {
			return false;
		}

		@NonNull
		@Override
		public FutureSupplier<MediaLib.PlayableItem> getPrevPlayable() {
			return completed(new Tutor(getParent(), getMode()) {
				@Override
				boolean isPrev() {
					return true;
				}
			});
		}

		@NonNull
		@Override
		public FutureSupplier<MediaLib.PlayableItem> getNextPlayable() {
			return completed(new Tutor(getParent(), getMode()) {
				@Override
				boolean isNext() {
					return true;
				}
			});
		}

		@Override
		public int getIcon() {
			return getParent().getIcon();
		}

		@NonNull
		@Override
		public FutureSupplier<Uri> getIconUri() {
			return getParent().getIconUri();
		}
	}
}
