package me.aap.fermata.media.lib;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.NaturalOrderComparator.compareNatural;

import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Folders;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Function;
import me.aap.utils.holder.Holder;
import me.aap.utils.text.TextUtils;

/**
 * @author Andrey Pavlenko
 */
public class SearchFolder extends BrowsableItemBase {
	private static final String SCHEME = "search://";
	private static final String[] META_KEYS = {
			METADATA_KEY_TITLE,
			METADATA_KEY_DISPLAY_TITLE,
			METADATA_KEY_ARTIST,
			METADATA_KEY_ALBUM,
			METADATA_KEY_DISPLAY_SUBTITLE
	};
	private final List<PlayableItem> itemsFound;

	private SearchFolder(String id, @NonNull BrowsableItem parent, List<Item> items) {
		super(id, parent, null);
		itemsFound = new ArrayList<>(items.size());
		for (Item i : items) {
			if (i instanceof PlayableItem) itemsFound.add((PlayableItem) i);
		}
		Collections.sort(itemsFound, (i1, i2) -> {
			MediaDescriptionCompat d1 = i1.getMediaDescription().peek();
			MediaDescriptionCompat d2 = i2.getMediaDescription().peek();
			CharSequence t1 = (d1 == null) ? null : d1.getTitle();
			CharSequence t2 = (d2 == null) ? null : d2.getTitle();
			String n1 = (t1 == null) ? i1.getName() : t1.toString();
			String n2 = (t2 == null) ? i2.getName() : t2.toString();
			return compareNatural(n1, n2, true);
		});
	}

	private static SearchFolder create(String id, BrowsableItem parent, List<Item> items,
																		 Function<List<PlayableItem>, BrowsableItem> parentSupplier) {
		return (SearchFolder) parent.getLib()
				.getOrCreateCachedItem(id, fid -> new SearchFolder(id, parent, items) {
					@Override
					public BrowsableItem getParent() {
						if (parentSupplier == null) return parent;
						List<PlayableItem> items = getItemsFound();
						BrowsableItem p = parentSupplier.apply((items == null) ? emptyList() : items);
						return (p == null) ? parent : p;
					}
				});
	}

	public static FutureSupplier<SearchFolder> search(
			@NonNull String q, @NonNull Function<List<PlayableItem>, BrowsableItem> parentSupplier) {
		BrowsableItem parent = parentSupplier.apply(emptyList());
		if (parent == null) return completedNull();
		String id = SCHEME + q + '@' + parent.getId();
		MediaLib lib = parent.getLib();
		Item cached = lib.getCachedItem(id);
		if (cached != null) return completed((SearchFolder) cached);

		if (parent.getRoot() instanceof Folders) {
			return lib.getMetadataRetriever().queryIds(q, 1000).then(ids -> {
				if (!ids.isEmpty()) {
					List<Item> items = new ArrayList<>(ids.size());
					return Async.forEach(iid -> lib.getItem(iid)
							.ifNotNull(items::add), ids).map(v ->
							SearchFolder.create(id, parent, items, parentSupplier));
				} else {
					return recursiveSearch(id, q, parent, parentSupplier);
				}
			});
		} else {
			return App.get().execute(() -> recursiveSearch(id, q, parent, parentSupplier))
					.map(FutureSupplier::peek);
		}
	}

	private static FutureSupplier<SearchFolder> recursiveSearch(
			String id, String q, BrowsableItem parent,
			Function<List<PlayableItem>, BrowsableItem> ps) {
		Pattern p;
		List<Pattern> words;
		String[] split = q.split(" ");
		Holder<Item> exact = new Holder<>();
		List<Item> matches = new ArrayList<>();

		if (split.length == 1) {
			p = Pattern.compile("\\b" + Pattern.quote(q) + "\\b", Pattern.CASE_INSENSITIVE);
			words = singletonList(p);
		} else {
			p = Pattern.compile(Pattern.quote(q), Pattern.CASE_INSENSITIVE);
			words = new ArrayList<>(split.length);
			for (String w : split) {
				words.add(Pattern.compile("\\b" + Pattern.quote(w) + "\\b", Pattern.CASE_INSENSITIVE));
			}
		}

		return recursiveVoiceSearch(parent, p, words, exact, matches, ps).then(v -> {
			if (exact.value != null) {
				return completed(SearchFolder.create(id, parent, singletonList(exact.value), ps));
			} else if (!matches.isEmpty()) {
				return completed(SearchFolder.create(id, parent, matches, ps));
			} else {
				return recursiveVoiceSearch(parent.getRoot(), p, words, exact, matches, ps).then(v2 -> {
					if (exact.value != null) {
						return completed(SearchFolder.create(id, parent, singletonList(exact.value), ps));
					} else if (!matches.isEmpty()) {
						return completed(SearchFolder.create(id, parent, matches, ps));
					} else {
						return completedNull();
					}
				});
			}
		});
	}

	private static FutureSupplier<?> recursiveVoiceSearch(
			BrowsableItem i, Pattern p, List<Pattern> words, Holder<Item> exact,
			List<Item> matches, Function<List<PlayableItem>, BrowsableItem> ps) {
		return i.getUnsortedChildren().then(children ->
				recursiveVoiceSearch(children, p, words, exact, matches, ps));
	}

	private static FutureSupplier<?> recursiveVoiceSearch(
			List<Item> items, Pattern p, List<Pattern> words, Holder<Item> exact,
			List<Item> matches, Function<List<PlayableItem>, BrowsableItem> ps) {
		return Async.forEach(i -> {
			if (exact.value != null) return null;
			if (i instanceof PlayableItem) {
				// Use MetadataRetriever for file search
				if (i.getParent() instanceof FolderItem) return completedVoid();
				PlayableItem pi = (PlayableItem) i;
				return pi.getMediaData().onSuccess(d -> match(pi, d, p, words, exact, matches));
			} else if (i instanceof BrowsableItem) {
				return recursiveVoiceSearch((BrowsableItem) i, p, words, exact, matches, ps);
			} else {
				return completedVoid();
			}
		}, items);
	}

	private static void match(PlayableItem i, MediaMetadataCompat d, Pattern p,
														List<Pattern> words, Holder<Item> exact, List<Item> matches) {
		for (String k : META_KEYS) {
			String v = d.getString(k);

			switch (match(v, p, words)) {
				case 1:
					if (METADATA_KEY_TITLE.equals(k) || METADATA_KEY_DISPLAY_TITLE.equals(k)) {
						exact.value = i;
						return;
					}
				case 2:
					matches.add(i);
			}
		}
	}

	private static byte match(CharSequence s, Pattern p, List<Pattern> words) {
		if ((s == null) || TextUtils.isBlank(s)) return 0;
		if (p.matcher(s).matches()) return 1;
		for (Pattern w : words) {
			if (!w.matcher(s).find()) return 0;
		}
		return 2;
	}

	public List<PlayableItem> getItemsFound() {
		return itemsFound;
	}

	@NonNull
	@Override
	public String getName() {
		return getLib().getContext().getString(R.string.voice_search);
	}

	@NonNull
	@Override
	public FutureSupplier<List<Item>> getChildren() {
		return listChildren();
	}

	@NonNull
	@Override
	public FutureSupplier<List<Item>> getUnsortedChildren() {
		return listChildren();
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getName());
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}

	@NonNull
	public FutureSupplier<PlayableItem> getPrevPlayable(Item i) {
		int idx = (i instanceof PlayableItem) ? itemsFound.indexOf(i) : -1;
		return (idx > 0) ? completed(itemsFound.get(idx - 1)) : i.getPrevPlayable();
	}

	@NonNull
	public FutureSupplier<PlayableItem> getNextPlayable(Item i) {
		int idx = (i instanceof PlayableItem) ? itemsFound.indexOf(i) : -1;
		return (idx < (itemsFound.size() - 1)) ? completed(itemsFound.get(idx + 1)) : i.getNextPlayable();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return completed((List) getItemsFound());
	}
}
