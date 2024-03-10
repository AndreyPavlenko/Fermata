package me.aap.fermata.addon.felex.media;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART;
import static me.aap.fermata.util.Utils.getResourceUri;
import static me.aap.utils.async.Completed.completed;

import android.graphics.Bitmap;
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
		if (id.startsWith("dict/")) {
			var name = id.substring(5);
			return DictMgr.get().getDictionaries().map(l -> {
				var dict = CollectionUtils.find(l, d -> name.equals(d.getName()));
				return (dict == null) ? null : new Dict(lib, dict);
			});
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
			return DictMgr.get().getDictionaries()
					.map(l -> CollectionUtils.map(l, d -> new Dict(lib, d)));
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
	}

	class Dict extends BrowsableItemBase implements FelexItem {
		private final me.aap.fermata.addon.felex.dict.Dict dict;
		private static String iconUri;
		private static Bitmap iconBitmap;

		public Dict(DefaultMediaLib lib, me.aap.fermata.addon.felex.dict.Dict dict) {
			super(SCHEME + ":dict/" + dict.getName(), new Root(lib), null);
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
			return R.drawable.dictionary_small;
		}

		@NonNull
		@Override
		public FutureSupplier<Uri> getIconUri() {
			return completed(Uri.parse(iconUri()));
		}

		String iconUri() {
			if (iconUri == null) iconUri = getResourceUri(getLib().getContext(), getIcon()).toString();
			return iconUri;
		}

		@Nullable
		public Bitmap iconBitmap() {
			if (iconBitmap == null) {
				getLib().getBitmap(iconUri()).onSuccess(b -> iconBitmap = b);
			}
			return iconBitmap;
		}
	}

	class Tutor extends PlayableItemBase implements FelexItem {
		private final DictTutor.Mode mode;

		public Tutor(Dict dict, DictTutor.Mode mode) {
			super(SCHEME + ":tutor/" + mode.ordinal() + '/' + dict.getName(), dict,
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
			var icon = dict.iconBitmap();
			var b = new MediaMetadataCompat.Builder();
			b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, name);
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, name);
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, buildSubtitle().peek());
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, buildDescription().peek());
			b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, dict.iconUri());
			if (icon != null) b.putBitmap(METADATA_KEY_ALBUM_ART, icon);
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
