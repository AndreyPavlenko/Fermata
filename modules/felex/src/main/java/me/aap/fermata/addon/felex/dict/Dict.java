package me.aap.fermata.addon.felex.dict;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.shuffle;
import static java.util.Collections.singletonList;
import static java.util.Collections.sort;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.felex.dict.DictMgr.CACHE_EXT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.comparingInt;
import static me.aap.utils.collection.NaturalOrderComparator.compareNatural;
import static me.aap.utils.misc.Assert.assertMainThread;
import static me.aap.utils.misc.Assert.assertNotMainThread;
import static me.aap.utils.text.TextUtils.compareToIgnoreCase;
import static me.aap.utils.text.TextUtils.isBlank;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
import me.aap.utils.function.LongObjectFunction;
import me.aap.utils.function.ToIntFunction;
import me.aap.utils.io.CountingOutputStream;
import me.aap.utils.io.FileUtils;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.io.Utf8LineReader;
import me.aap.utils.io.Utf8Reader;
import me.aap.utils.log.Log;
import me.aap.utils.vfs.VirtualFile;

/**
 * @author Andrey Pavlenko
 */
public class Dict implements Comparable<Dict> {
	private final DictMgr mgr;
	private final VirtualFile dictFile;
	private final RandomAccessChannel channel;
	private final DictInfo info;
	private boolean closed;
	private FutureSupplier<List<Word>> words;
	private ByteBuffer bb;
	private StringBuilder sb;
	private int wordsCount;
	private int dirProgress;
	private int revProgress;
	private RandomAccessChannel cacheChannel;
	private List<Word> progressUpdate;

	private Dict(DictMgr mgr, VirtualFile dictFile, RandomAccessChannel channel, DictInfo info) {
		this.mgr = mgr;
		this.dictFile = dictFile;
		this.channel = channel;
		this.info = info;
	}

	public static FutureSupplier<Dict> create(DictMgr mgr, VirtualFile dictFile) {
		Log.d("Loading dictionary from file ", dictFile);
		return mgr.queue.enqueue(() -> {
			RandomAccessChannel ch = dictFile.getChannel("r");
			if (ch == null)
				throw new IOException("Unable to read dictionary file: " + dictFile.getName());
			DictInfo info = DictInfo.read(ch.getInputStream(0, 4096, ByteBuffer.allocate(256)));
			if (info != null) return new Dict(mgr, dictFile, ch, info);
			ch.close();
			throw new IllegalArgumentException("Invalid dictionary header: " + dictFile.getName());
		}).then(d -> d.readCacheHeader().map(h -> {
			d.wordsCount = h.wordsCount;
			d.dirProgress = h.dirProgress;
			d.revProgress = h.revProgress;
			return d;
		}));
	}

	public static FutureSupplier<Dict> create(DictMgr mgr, VirtualFile dictFile, DictInfo info) {
		return mgr.queue.enqueue(() -> {
			RandomAccessChannel ch = dictFile.getChannel("rw");
			if (ch == null)
				throw new IOException("Unable to create dictionary file: " + dictFile.getName());
			Dict d = new Dict(mgr, dictFile, ch, info);
			try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(ch.getOutputStream(0), UTF_8))) {
				info.write(w);
			}
			return d;
		});
	}

	public DictInfo getInfo() {
		return info;
	}

	public String getName() {
		return getInfo().getName();
	}

	public Locale getSourceLang() {
		return getInfo().getSourceLang();
	}

	public Locale getTargetLang() {
		return getInfo().getTargetLang();
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
			words = readWords(wordsCount).then(words -> readCache(words).main().then(h -> {
				wordsCount = words.size();
				dirProgress = h.dirProgress;
				revProgress = h.revProgress;
				this.words = completed(words);
				if (h.wordsCount != wordsCount) {
					h.wordsCount = wordsCount;
					return writeCacheHeader(h).map(v -> words);
				}
				return completed(words);
			})).onFailure(err -> {
				Log.e(err, "Failed to load words from ", dictFile.getName());
				close();
			}).main();
		}

		return words.fork();
	}

	public FutureSupplier<Word> getRandomWord(Random rnd, Deque<Word> history,
																						ToIntFunction<Word> getProgress) {
		assertMainThread();
		if (!history.isEmpty()) return completed(history.pollFirst());

		return getWords().main().map(words -> {
			int s = words.size();
			if (s == 0) return null;

			List<Word> sorted = new ArrayList<>(words);
			shuffle(sorted, rnd);
			sort(sorted, comparingInt(getProgress));

			for (int i = 0, n = Math.min(100, s); i < n; i++) {
				history.addLast(sorted.get(i));
			}

			return history.pollFirst();
		});
	}

	public FutureSupplier<Dict> addWords(Uri dictUri) {
		assertMainThread();
		return writeDict(ch -> {
			try (InputStream in = App.get().getContentResolver().openInputStream(dictUri);
					 OutputStream out = ch.getOutputStream(ch.size())) {
				DictInfo i = DictInfo.read(in);
				if (i == null) throw new IOException("Invalid dictionary header: " + dictUri);
				FileUtils.copy(in, out);
				return this;
			}
		}).main().onSuccess(v -> words = null);
	}

	public FutureSupplier<Integer> addWord(String word, String trans, @Nullable String example,
																				 @Nullable String exampleTrans) {
		assertMainThread();
		return editWords((size, words) -> {
			Word w = Word.create(word);
			int i = findWord(words, w.getWord());
			if (i > 0) throw new IllegalArgumentException("Word " + word + " is already exists");
			int idx = -i - 1;
			long off = (idx < words.size()) ? words.get(idx).getOffset() : size;
			ByteBuffer bb = w.toBytes(singletonList(new Translation(trans, example, exampleTrans)));
			int len = bb.remaining();
			w.setOffset(off);
			words.add(idx, w);
			shiftWords(words, idx + 1, len);
			return dictInsert(off, bb).map(v -> idx);
		}).then(f -> f).main()
				.onFailure(err -> Log.e(err, "Failed to add word ", word, " to dictionary ", this));
	}

	public FutureSupplier<Integer> deleteWord(String word) {
		assertMainThread();
		return editWords((size, words) -> {
			int idx = findWord(words, word);
			if (idx < 0) return completed(-1);
			Word w = words.remove(idx);
			wordsCount = words.size();
			dirProgress -= w.getDirProgress();
			revProgress -= w.getRevProgress();
			assert (dirProgress >= 0) && (revProgress >= 0);
			CacheHeader hdr = new CacheHeader(wordsCount, dirProgress, revProgress);
			long off = w.getOffset();
			long len = (idx == words.size()) ? -1 : words.get(idx).getOffset() - off;
			shiftWords(words, idx, -len);
			return dictDelete(off, len).then(v -> writeCacheHeader(hdr)).map(v -> idx);
		}).then(f -> f).main().onFailure(b -> {
			Log.e("Failed to remove word ", word);
			close();
		});
	}

	private void shiftWords(List<Word> words, int idx, long diff) {
		for (int n = words.size(); idx < n; idx++) {
			Word next = words.get(idx);
			next.setOffset(next.getOffset() + diff);
		}
	}

	public FutureSupplier<Boolean> hasWord(CharSequence word) {
		return getWords().map(words -> findWord(words, Word.create(word).getWord()) >= 0);
	}

	public FutureSupplier<Void> wipeProgress() {
		assertMainThread();
		return getWords().then(words -> {
			CacheHeader hdr = new CacheHeader(words.size(), 0, 0);
			dirProgress = revProgress = 0;
			for (Word w : words) w.wipeProgress();
			return useCache(false, ch -> {
				if (ch == null) return null;
				ByteBuffer bb = bb();
				bb.position(0).limit(CacheHeader.SIZE);
				hdr.put(bb);
				ch.write(bb, 0);
				ch.truncate(CacheHeader.SIZE);
				return null;
			});
		});
	}

	public FutureSupplier<Void> reset() {
		assertMainThread();
		if (words == null) return completedVoid();
		return words.thenRun(() -> words = null).map(w -> null);
	}

	FutureSupplier<?> close() {
		assertMainThread();
		if (isClosed()) return completedVoid();
		return flush().then(v -> enqueue(() -> {
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
		return getInfo().toString();
	}

	FutureSupplier<List<Translation>> readTranslations(Word w) {
		return readDict(ch -> readTranslations(w, ch)).main();
	}

	FutureSupplier<Void> writeTranslations(Word w, List<Translation> translations) {
		assertMainThread();
		return editWords((size, words) -> {
			int idx = findWord(words, w.getWord());
			if (idx < 0) throw new IllegalArgumentException("Word " + w + " not found");
			long nextOff = (idx != (words.size() - 1)) ? words.get(idx + 1).getOffset() : size;
			ByteBuffer bb = w.toBytes(translations);
			long len = nextOff - w.getOffset();
			shiftWords(words, idx + 1, bb.remaining() - len);
			return dictReplace(w.getOffset(), len, bb);
		}).then(f -> f).main()
				.onFailure(err -> Log.e(err, "Failed to write word translation to dictionary ", this));
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

		return getWords().then(words -> {
			if (progressUpdate == null) return completedVoid();
			List<Word> updates = progressUpdate;
			progressUpdate = null;
			CacheHeader hdr = new CacheHeader(words.size(), dirProgress, revProgress);
			Log.d("Flushing ", updates.size(), " updates.");
			return useCache(true, ch -> {
				ByteBuffer bb = bb();
				writeCacheHeader(ch, bb, hdr);
				for (Word u : updates) writeWordCache(ch, bb, u);
				return null;
			});
		}).main();
	}

	private <T> FutureSupplier<T> readDict(CheckedFunction<RandomAccessChannel, T, Throwable> reader) {
		return enqueue(() -> reader.apply(channel));
	}

	private <T> FutureSupplier<T> writeDict(CheckedFunction<RandomAccessChannel, T, Throwable> writer) {
		return enqueue(() -> {
			try (RandomAccessChannel ch = requireNonNull(dictFile.getChannel("w"))) {
				return writer.apply(ch);
			}
		});
	}

	// Whe words must be edited in the main thread and the background tasks must not be running
	private <T> FutureSupplier<T> editWords(LongObjectFunction<List<Word>, T> func) {
		assertMainThread();
		return enqueue(channel::size).main().then(size -> {
			if (!mgr.queue.isEmpty()) return editWords(func);
			List<Word> w = words.peek();
			return (w != null) ? completed(func.apply(size, w)) : editWords(func);
		});
	}

	private FutureSupplier<Void> dictInsert(long off, ByteBuffer bb) {
		return writeDict(ch -> {
			long size = ch.size();

			if (off == size) {
				ch.write(bb, ch.size());
			} else {
				int len = bb.remaining();
				moveRight(channel, ch, off, len);
				ch.write(bb, off);
			}

			return null;
		});
	}

	private FutureSupplier<Void> dictDelete(long off, long del) {
		return writeDict(ch -> {
			if (del == -1) {
				ch.truncate(off);
			} else {
				long size = ch.size();
				long newSize = size - del;
				ch.transferFrom(channel, off + del, off, newSize - off);
				ch.truncate(newSize);
			}
			return null;
		});
	}

	private FutureSupplier<Void> dictReplace(long off, long del, ByteBuffer ins) {
		return writeDict(ch -> {
			int len = ins.remaining();

			if (len > del) {
				moveRight(channel, ch, off + del, len - del);
			} else {
				long size = ch.size();
				ch.transferFrom(channel, off + del, off + len, size - off - del);
				ch.truncate(size + len - del);
			}

			ch.write(ins, off);
			return null;
		});
	}

	private static void moveRight(RandomAccessChannel reader, RandomAccessChannel writer, long off,
													 long len) throws IOException {
		long size = writer.size();
		long tailLen = (size - off) % len;
		reader.transferTo(size - tailLen, size + len - tailLen, tailLen, writer);
		for (long i = size - tailLen, end = off + len; i >= end; i -= len) {
			reader.transferTo(i - len, i, len, writer);
		}
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

			getCacheFile(create).main().then(f -> (f == null) ? completed(a.apply(null)) : enqueue(() -> {
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
			sort(list);
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

			return completed(aligned).main().then(this::writeWords);
		} else if (move) {
			return completed(words).main().then(this::writeWords);
		} else {
			return completed(words);
		}
	}

	private FutureSupplier<List<Word>> writeWords(List<Word> words) {
		Log.i("Saving dictionary ", getName(), " to ", dictFile);
		Promise<List<Word>> p = new Promise<>();
		enqueue(() -> dictFile.getParent()
				.then(parent -> parent.createTempFile(dictFile.getName() + '-', ".tmp"))
				.then(tmp -> {
					try (RandomAccessChannel tch = requireNonNull(tmp.getChannel("rw"));
							 RandomAccessChannel dch = requireNonNull(dictFile.getChannel("w"))) {
						CountingOutputStream out = new CountingOutputStream(
								new BufferedOutputStream(tch.getOutputStream(0)));
						Writer w = new OutputStreamWriter(out, UTF_8);
						getInfo().write(w);
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
		assertNotMainThread();
		ByteBuffer b = bb;
		if (b == null) bb = b = ByteBuffer.allocateDirect(8192);
		return b;
	}

	private StringBuilder sb() {
		assertNotMainThread();
		StringBuilder b = sb;
		if (b == null) sb = b = new StringBuilder(512);
		b.setLength(0);
		return b;
	}

	private <T> FutureSupplier<T> enqueue(CheckedSupplier<T, Throwable> task) {
		return mgr.queue.enqueue(task);
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
