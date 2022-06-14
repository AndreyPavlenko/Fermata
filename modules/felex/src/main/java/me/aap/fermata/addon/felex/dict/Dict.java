package me.aap.fermata.addon.felex.dict;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.felex.dict.DictMgr.CACHE_EXT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.NaturalOrderComparator.compareNatural;
import static me.aap.utils.misc.Assert.assertMainThread;
import static me.aap.utils.text.TextUtils.compareToIgnoreCase;
import static me.aap.utils.text.TextUtils.isBlank;
import static me.aap.utils.text.TextUtils.isNullOrBlank;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import me.aap.fermata.addon.felex.FelexAddon;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.CheckedSupplier;
import me.aap.utils.function.ToIntFunction;
import me.aap.utils.io.ByteBufferOutputStream;
import me.aap.utils.io.CountingOutputStream;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.io.Utf8LineReader;
import me.aap.utils.io.Utf8Reader;
import me.aap.utils.log.Log;
import me.aap.utils.text.TextUtils;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class Dict implements Comparable<Dict> {
	private static final String TAG_NAME = "#Name";
	private static final String TAG_SRC_LANG = "#SourceLang";
	private static final String TAG_TARGET_LANG = "#TargetLang";
	private final DictMgr mgr;
	private final VirtualFile dictFile;
	private final RandomAccessChannel channel;
	private final Locale sourceLang;
	private final Locale targetLang;
	private final String name;
	private boolean closed;
	private FutureSupplier<List<Word>> words;
	private ByteBuffer bb;
	private StringBuilder sb;
	private int wordsCount;
	private int dirProgress;
	private int revProgress;
	private RandomAccessChannel cacheChannel;
	private List<Word> progressUpdate;

	private Dict(DictMgr mgr, VirtualFile dictFile, RandomAccessChannel channel, String name,
							 Locale sourceLang, Locale targetLang) {
		this.mgr = mgr;
		this.dictFile = dictFile;
		this.channel = channel;
		this.sourceLang = sourceLang;
		this.name = name;
		this.targetLang = targetLang;
	}

	public static FutureSupplier<Dict> create(DictMgr mgr, VirtualFile dictFile) {
		Log.d("Loading dictionary from file ", dictFile);
		return mgr.enqueue(() -> {
			RandomAccessChannel ch = dictFile.getChannel("r");
			if (ch == null)
				throw new IOException("Unable to read dictionary file: " + dictFile.getName());
			Utf8LineReader r = new Utf8LineReader(ch.getInputStream(0, 4096, ByteBuffer.allocate(256)));
			StringBuilder sb = new StringBuilder(64);
			String name = null;
			Locale srcLang = null;
			Locale targetLang = null;

			for (int i = r.readLine(sb); i != -1; sb.setLength(0), i = r.readLine(sb)) {
				if ((sb.length() == 0) || sb.charAt(0) != '#') break;
				if (TextUtils.startsWith(sb, TAG_NAME))
					name = sb.substring(TAG_NAME.length()).trim();
				else if (TextUtils.startsWith(sb, TAG_SRC_LANG))
					srcLang = new Locale(sb.substring(TAG_SRC_LANG.length()).trim());
				else if (TextUtils.startsWith(sb, TAG_TARGET_LANG))
					targetLang = new Locale(sb.substring(TAG_TARGET_LANG.length()).trim());
			}

			if ((name != null) && (srcLang != null) && (targetLang != null)) {
				return new Dict(mgr, dictFile, ch, name, srcLang, targetLang);
			}

			ch.close();
			throw new IllegalArgumentException("Invalid dictionary header: " + dictFile.getName());
		}).then(d -> d.readCacheHeader().map(h -> {
			d.wordsCount = h.wordsCount;
			d.dirProgress = h.dirProgress;
			d.revProgress = h.revProgress;
			return d;
		}));
	}

	public static FutureSupplier<Dict> create(DictMgr mgr, VirtualFile dictFile,
																						String name, Locale srcLang, Locale targetLang) {
		return mgr.enqueue(() -> {
			RandomAccessChannel ch = dictFile.getChannel("rw");
			if (ch == null)
				throw new IOException("Unable to create dictionary file: " + dictFile.getName());
			Dict d = new Dict(mgr, dictFile, ch, name, srcLang, targetLang);
			try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(ch.getOutputStream(0), UTF_8))) {
				d.writeDictHeader(w);
			}
			return d;
		});
	}

	DictMgr getMgr() {
		return mgr;
	}

	public String getName() {
		return name;
	}

	public Locale getSourceLang() {
		return sourceLang;
	}

	public Locale getTargetLang() {
		return targetLang;
	}

	public int getWordsCount() {
		return wordsCount;
	}

	public int getDirProgress() {
		int c = getWordsCount();
		return (c == 0) ? 0 : (dirProgress / c);
	}

	public int getRevProgress() {
		int c = getWordsCount();
		return (c == 0) ? 0 : revProgress / c;
	}

	public VirtualFile getDictFile() {
		return dictFile;
	}

	FutureSupplier<VirtualFile> getCacheFile(boolean create) {
		return FelexAddon.get().getCacheFolder().then(dir -> {
			String name = dictFile.getName();
			int i = name.lastIndexOf('.');
			if (i != -1) name = name.substring(0, i) + CACHE_EXT;
			return create ? dir.createFile(name) : dir.getChild(name).cast();
		});
	}

	public FutureSupplier<List<Word>> getWords() {
		assertMainThread();
		if (isClosed()) throw new IllegalStateException();

		if (words == null) {
			words = readWords(wordsCount).then(words -> readCache(words).main().onCompletion((h, err) -> {
				if (err != null) {
					Log.e(err, "Failed to load words from ", dictFile.getName());
					close();
				} else {
					wordsCount = words.size();
					dirProgress = h.dirProgress;
					revProgress = h.revProgress;
					this.words = completed(words);

					if (h.wordsCount != wordsCount) {
						h.wordsCount = wordsCount;
						writeCacheHeader(h).onFailure(herr -> Log.e(herr, "Failed to write cache header"));
					}
				}
			}).map(c -> words));
		}

		return words.fork();
	}

	public FutureSupplier<Word> getRandomWord(Random rnd, Deque<Word> history,
																						ToIntFunction<Word> getProgress) {
		assertMainThread();
		return getWords().main().map(words -> {
			int s = words.size();
			if (s == 0) return null;
			int r = rnd.nextInt(s);
			Word min = words.get(r);
			int minProg = getProgress.applyAsInt(min);
			boolean minHistory = history.contains(min);

			if ((minProg != 0) || minHistory) {
				for (int i = r + 1; i != r; i++) {
					if ((i == s) && ((i = 0) == r)) break;
					Word w = words.get(i);

					if (history.contains(w)) {
						if (!minHistory) continue;
						int p = getProgress.applyAsInt(w);
						if (minProg <= p) continue;
						min = w;
						minProg = p;
					} else {
						int p = getProgress.applyAsInt(w);

						if (p == 0) {
							min = w;
							break;
						} else if (minHistory || (minProg > p)) {
							min = w;
							minProg = p;
							minHistory = false;
						}
					}
				}
			}

			if (history.peekLast() == min) history.clear();
			else if (history.size() == 10) history.pollLast();
			history.addFirst(min);
			return min;
		});
	}

	public FutureSupplier<Integer> addWord(String word, String trans, @Nullable String example,
																				 @Nullable String exampleTrans) {
		assertMainThread();
		return getWords().main().then(words -> {
			Word w = Word.create(word);
			int i = findWord(words, w.getWord());
			if (i > 0) throw new IllegalArgumentException("Word " + word + " is already exists");
			int idx = -i - 1;
			long off = (idx < words.size()) ? words.get(idx).getOffset() : -1;

			return writeDict(ch -> {
				ByteBuffer bb = bb();
				bb.clear();

				try (Writer writer = new OutputStreamWriter(new ByteBufferOutputStream(bb), UTF_8)) {
					Translation tr = new Translation(trans);
					if (!isNullOrBlank(example) && !isNullOrBlank(exampleTrans)) {
						tr.addExample(new Example(example, exampleTrans));
					}
					w.write(writer, singletonList(tr));
					writer.append('\n');
				}

				bb.flip();

				if (off == -1) {
					long o = ch.size();
					ch.write(bb, o);
					w.setOffset(o);
				} else {
					int len = bb.remaining();
					ch.transferFrom(channel, off, off + len, ch.size() - off);
					ch.write(bb, off);
					w.setOffset(off);

					for (int j = idx, n = words.size(); j < n; j++) {
						Word next = words.get(j);
						next.setOffset(next.getOffset() + len);
					}
				}

				return idx;
			}).main().onCompletion((wd, err) -> {
				if (err != null) {
					Log.e(err, "Failed to add word ", word, " to dictionary ", this);
				} else {
					words.add(idx, w);
				}
			});
		});
	}

	public FutureSupplier<Boolean> hasWord(CharSequence word) {
		return getWords().map(words -> findWord(words, Word.create(word).getWord()) >= 0);
	}

	FutureSupplier<?> close() {
		assertMainThread();
		if (isClosed()) return completedVoid();
		return flush().then(v -> mgr.enqueue(() -> {
			IoUtils.close(channel, cacheChannel);
			cacheChannel = null;
			bb = null;
			sb = null;
			return null;
		})).main().onCompletion((r2, err2) -> {
			if (err2 != null) Log.e(err2, "Failed to close dictionary: ", getName());
			words = null;
			closed = true;
		});
	}

	public boolean isClosed() {
		return closed;
	}

	@NonNull
	@Override
	public String toString() {
		return getName() + " (" + getSourceLang().getLanguage().toUpperCase()
				+ '-' + getTargetLang().getLanguage().toUpperCase() + ')';
	}

	FutureSupplier<List<Translation>> readTranslations(Word w) {
		return readDict(ch -> readTranslations(w, ch));
	}

	private List<Translation> readTranslations(Word w, RandomAccessChannel ch) throws IOException {
		Utf8LineReader r = new Utf8LineReader(ch.getInputStream(w.getOffset(), Long.MAX_VALUE, bb()));
		if (r.skipLine() == -1) return emptyList();
		StringBuilder sb = sb();
		List<Translation> list = new ArrayList<>();
		Example example = Example.DUMMY;
		Translation trans = Translation.DUMMY;

		for (; ; ) {
			sb.setLength(0);
			if (r.readLine(sb) == -1) break;
			if (isBlank(sb)) continue;
			if ((sb.charAt(0) != '\t')) break;

			if (sb.length() > 2) {
				if ((sb.charAt(1) == '\t')) {
					if (sb.charAt(2) == '\t') {
						example.setTranslation(sb.toString().trim());
					} else {
						example = new Example(sb.toString().trim());
						trans.addExample(example);
					}
					continue;
				}
			}

			trans = new Translation(sb.toString().trim());
			list.add(trans);
		}

		return list;
	}

	void incrProgress(Word w, boolean dir, int diff) {
		assertMainThread();
		if (diff == 0) return;
		if (dir) dirProgress += diff;
		else revProgress += diff;
		if (progressUpdate == null) {
			progressUpdate = new ArrayList<>();
			App.get().getHandler().postDelayed(this::flush, 30000);
		}
		if (CollectionUtils.contains(progressUpdate, u -> u == w)) return;
		progressUpdate.add(w);
	}

	private FutureSupplier<?> flush() {
		assertMainThread();
		if (progressUpdate == null) return completedVoid();

		return getWords().main().then(words -> {
			if (progressUpdate == null) return completedVoid();
			List<Word> updates = progressUpdate;
			progressUpdate = null;
			CacheHeader hdr = new CacheHeader(words.size(), getDirProgress(), getRevProgress());
			Log.d("Flushing ", updates.size(), " updates.");
			return useCache(true, ch -> {
				ByteBuffer bb = bb();
				writeCacheHeader(ch, bb, hdr);
				for (Word u : updates) writeWordCache(ch, bb, u);
				return null;
			});
		});
	}

	private <T> FutureSupplier<T> readDict(CheckedFunction<RandomAccessChannel, T, Throwable> reader) {
		return getMgr().enqueue(() -> reader.apply(channel));
	}

	private <T> FutureSupplier<T> writeDict(CheckedFunction<RandomAccessChannel, T, Throwable> writer) {
		return getMgr().enqueue(() -> {
			try (RandomAccessChannel ch = requireNonNull(dictFile.getChannel("w"))) {
				return writer.apply(ch);
			}
		});
	}

	private <T> FutureSupplier<T> useCache(boolean create,
																				 CheckedFunction<RandomAccessChannel, T, Throwable> a) {
		Promise<T> p = new Promise<>();
		enqueue(() -> {
			if (cacheChannel != null) {
				try {
					p.complete(a.apply(cacheChannel));
					return null;
				} catch (Throwable ex) {
					cacheChannel = null;
					Log.e(ex, "Failed to access cache file ", getName(), ". Retrying...");
				}
			}

			getCacheFile(create).then(f -> (f == null) ? completed(a.apply(null)) : enqueue(() -> {
				if (cacheChannel == null) cacheChannel = requireNonNull(f.getChannel("rw"));
				return a.apply(cacheChannel);
			})).thenComplete(p);
			return null;
		});
		return p;
	}

	private FutureSupplier<List<Word>> readWords(int expectedCount) {
		return readDict(ch -> {
			StringBuilder sb = sb();
			ArrayList<Word> list = new ArrayList<>(expectedCount);
			Utf8LineReader r = new Utf8LineReader(ch.getInputStream(0, Long.MAX_VALUE, bb()));

			// Skip header
			for (int i = r.readLine(sb); i != -1; sb.setLength(0), i = r.readLine(sb)) {
				if ((sb.length() == 0) || sb.charAt(0) != '#') break;
				sb.setLength(0);
			}

			for (long off = r.getBytesRead(); ; off = r.getBytesRead()) {
				sb.setLength(0);
				if (r.readLine(sb) == -1) break;
				if (isBlank(sb) || (sb.charAt(0) == '\t')) continue;
				list.add(Word.create(sb, off));
			}

			list.trimToSize();
			Collections.sort(list);
			return list;
		}).then(this::align);
	}

	private FutureSupplier<List<Word>> align(List<Word> words) {
		if (words.isEmpty() || (words.size() == 1)) return completed(words);
		int dups = 0;
		boolean move = false;
		Iterator<Word> it = words.listIterator();
		Word prev = it.next();

		while (it.hasNext()) {
			Word w = it.next();
			if (w.getWord().equals(prev.getWord())) dups++;
			else if (prev.getOffset() >= w.getOffset()) move = true;
			prev = w;
		}

		if (dups != 0) {
			List<Word> aligned = new ArrayList<>(words.size() - dups);
			it = words.listIterator();
			prev = it.next();
			aligned.add(prev);

			while (it.hasNext()) {
				Word w = it.next();
				if (!w.getWord().equals(prev.getWord())) aligned.add(w);
				prev = w;
			}

			return writeWords(aligned);
		} else if (move) {
			return writeWords(words);
		} else {
			return completed(words);
		}
	}

	private FutureSupplier<List<Word>> writeWords(List<Word> words) {
		Log.i("Saving dictionary ", getName(), " to ", dictFile);
		Promise<List<Word>> p = new Promise<>();
		mgr.enqueue(() -> dictFile.getParent()
				.then(parent -> parent.createTempFile(dictFile.getName() + '-', ".tmp"))
				.then(tmp -> {
					try (RandomAccessChannel tch = requireNonNull(tmp.getChannel("rw"));
							 RandomAccessChannel dch = requireNonNull(dictFile.getChannel("w"))) {
						CountingOutputStream out = new CountingOutputStream(
								new BufferedOutputStream(tch.getOutputStream(0)));
						Writer w = new OutputStreamWriter(out, UTF_8);
						writeDictHeader(w);
						w.flush();

						for (Word word : words) {
							long off = out.getCount();
							word.write(w, readTranslations(word, channel));
							word.setOffset(off);
							w.append('\n');
							w.flush();
						}

						w.close();
						long size = tch.size();
						dch.transferFrom(tch, 0, 0, size);
						dch.truncate(size);
						p.complete(words);
					} finally {
						tmp.delete().onFailure(err -> Log.e("Failed to delete tmp file ", tmp));
					}
					return null;
				}));

		p.onCompletion((r, err) -> {
			if (err != null) Log.e(err, "Failed to save dictionary: ", getName());
			else Log.i("Dictionary has been saved: ", getName());
		});
		return p;
	}

	private void writeDictHeader(Appendable a) throws IOException {
		a.append(TAG_NAME).append("\t\t").append(getName()).append('\n');
		a.append(TAG_SRC_LANG).append("\t").append(getSourceLang().toString()).append('\n');
		a.append(TAG_TARGET_LANG).append("\t").append(getTargetLang().toString()).append('\n');
	}

	private FutureSupplier<CacheHeader> readCacheHeader() {
		return useCache(false, ch -> (ch == null) ? new CacheHeader()
				: readCacheHeader(ch, ByteBuffer.allocate(CacheHeader.SIZE))).ifFail(err -> {
			Log.e(err, "Failed to read dictionary cache file: ", getName());
			return new CacheHeader();
		});
	}

	private CacheHeader readCacheHeader(RandomAccessChannel ch, ByteBuffer bb)
			throws IOException {
		bb.position(0).limit(CacheHeader.SIZE);
		if (ch.read(bb, 0) != bb.limit()) {
			Log.e("Dictionary ", getName(), " cache corrupted - resetting.");
			bb.position(0);
			new CacheHeader().put(bb);
			bb.flip();
			ch.write(bb, 0);
			ch.truncate(CacheHeader.SIZE);
		}
		bb.flip();
		return new CacheHeader(bb);
	}

	private FutureSupplier<CacheHeader> readCache(List<Word> words) {
		return useCache(false, ch -> {
			if (ch == null) return new CacheHeader();
			int dirProgress = 0;
			int revProgress = 0;
			ByteBuffer bb = bb();
			StringBuilder sb = sb();
			CacheHeader hdr = readCacheHeader(ch, bb);
			Utf8Reader r = new Utf8Reader(ch.getInputStream(CacheHeader.SIZE, Long.MAX_VALUE, bb));
			r.setBytesRead(CacheHeader.SIZE);

			read:
			for (int high = words.size() - 1; ; ) {
				long start = r.getBytesRead();
				sb.setLength(0);

				for (int c = r.read(); ; c = r.read()) {
					if (c == -1) break read;
					else if (c == '\0') break;
					sb.append((char) c);
				}

				byte dir = (byte) r.read();
				byte rev = (byte) r.read();

				if ((dir < 0) || (rev < 0)) {
					Log.e("Dictionary cache corrupted: " + getName());
					ch.truncate(start);
					break;
				}

				int len = (int) (r.getBytesRead() - start);
				int i = findWord(words, sb, high);

				if (i < 0) {
					Log.i("Removing from cache: ", sb);
					ch.transferFrom(ch, start + len, start, ch.size() - start - len);
					ch.truncate(ch.size() - len);
					r.setBytesRead(start);
				} else {
					dirProgress += dir;
					revProgress += rev;
					words.get(i).setCacheInfo(start, len, dir, rev);
				}
			}

			CacheHeader h = new CacheHeader(words.size(), dirProgress, revProgress);
			if (!hdr.equals(h)) writeCacheHeader(ch, bb, h);
			return h;
		});
	}

	private FutureSupplier<Void> writeCacheHeader(CacheHeader h) {
		return useCache(true, ch -> {
			writeCacheHeader(ch, bb(), h);
			return null;
		});
	}

	private void writeCacheHeader(RandomAccessChannel ch, ByteBuffer bb, CacheHeader h)
			throws IOException {
		Log.d("Writing ", h);
		bb.clear();
		h.put(bb);
		bb.flip();
		if (ch.write(bb, 0) != bb.limit()) throw new IOException("Failed to write cache header");
	}

	private void writeWordCache(RandomAccessChannel ch, ByteBuffer bb, Word word) throws IOException {
		long off = word.getCacheOffset();
		byte dir = (byte) word.getDirProgress();
		byte rev = (byte) word.getRevProgress();

		if (off == 0) {
			Log.d("Adding to cache: ", word);
			off = ch.size();
			CountingOutputStream out = new CountingOutputStream(ch.getOutputStream(off));
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(out, UTF_8));
			w.append(word.getWord()).append('\0');
			w.flush();
			word.setCacheInfo(off, (int) out.getCount() + 2, dir, rev);
		} else {
			Log.d("Updating cache: ", word);
		}

		bb.position(0).limit(2);
		bb.put(0, dir);
		bb.put(1, rev);
		ch.write(bb, off + word.getCacheLen() - 2);
	}

	public static int findWord(List<Word> words, CharSequence word) {
		return findWord(words, word, words.size() - 1);
	}

	private static int findWord(List<Word> words, CharSequence word, int high) {
		int low = 0;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int cmp = compareToIgnoreCase(words.get(mid).getWord(), word);
			if (cmp < 0) low = mid + 1;
			else if (cmp > 0) high = mid - 1;
			else return mid;
		}
		return -(low + 1);
	}

	@Override
	public int compareTo(Dict o) {
		return compareNatural(getName(), o.getName());
	}


	private ByteBuffer bb() {
		ByteBuffer b = bb;
		if (b == null) bb = b = ByteBuffer.allocateDirect(8192);
		return b;
	}

	private StringBuilder sb() {
		StringBuilder b = sb;
		if (b == null) sb = b = new StringBuilder(512);
		b.setLength(0);
		return b;
	}

	private <T> FutureSupplier<T> enqueue(CheckedSupplier<T, Throwable> task) {
		return getMgr().enqueue(task);
	}

	/*
		Header structure:
		int - words count
		int - direct progress
		int - reverse progress
		24 bytes - reserved
	 */
	private static final class CacheHeader {
		static final int SIZE = 36;
		int wordsCount;
		int dirProgress;
		int revProgress;

		CacheHeader() {
			this(0, 0, 0);
		}

		CacheHeader(ByteBuffer b) {
			this(b.getInt(), b.getInt(), b.getInt());
		}

		CacheHeader(int wordsCount, int dirProgress, int revProgress) {
			this.wordsCount = wordsCount;
			this.dirProgress = dirProgress;
			this.revProgress = revProgress;
		}

		void put(ByteBuffer b) {
			b.putInt(wordsCount);
			b.putInt(dirProgress);
			b.putInt(revProgress);
			b.putLong(0);
			b.putLong(0);
			b.putLong(0);
		}

		public boolean equals(CacheHeader h) {
			return (wordsCount == h.wordsCount) && (dirProgress == h.dirProgress)
					&& (revProgress == h.revProgress);
		}

		@NonNull
		@Override
		public String toString() {
			return "CacheHeader{" +
					"wordsCount=" + wordsCount +
					", dirProgress=" + dirProgress +
					", revProgress=" + revProgress +
					'}';
		}
	}
}
