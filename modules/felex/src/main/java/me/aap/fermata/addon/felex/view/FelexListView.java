package me.aap.fermata.addon.felex.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.appcompat.content.res.AppCompatResources.getDrawable;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.felex.dict.Dict.findWord;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.UiUtils.showQuestion;
import static me.aap.utils.ui.UiUtils.toIntPx;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.Closeable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import me.aap.fermata.addon.felex.FelexAddon;
import me.aap.fermata.addon.felex.R;
import me.aap.fermata.addon.felex.dict.Dict;
import me.aap.fermata.addon.felex.dict.DictMgr;
import me.aap.fermata.addon.felex.dict.Example;
import me.aap.fermata.addon.felex.dict.Translation;
import me.aap.fermata.addon.felex.dict.Word;
import me.aap.fermata.provider.FermataContentProvider;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.util.Utils;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.BasicPreferenceStore;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ScalableTextView;

/**
 * @author Andrey Pavlenko
 */
public class FelexListView extends RecyclerView implements Closeable {
	private final Deque<Integer> stack = new ArrayDeque<>();
	private final MainActivityDelegate activity;
	@NonNull
	private Content<?, ?, ?> content = new MgrContent();
	@Nullable
	private Pattern filter;
	@NonNull
	private String filterText = "";
	private boolean closed;

	public FelexListView(@NonNull Context ctx, @Nullable AttributeSet attrs) {
		super(ctx, attrs);
		activity = MainActivityDelegate.get(ctx);
		setLayoutManager(new LinearLayoutManager(ctx));
		setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
		setAdapter(new DictAdapter());
	}

	DictMgr getDictMgr() {
		return content.mgr();
	}

	Object getContent() {
		return content.content;
	}

	void setContent(Dict d) {
		MgrContent mgr = content.root();
		mgr.filtered = null;
		adapter().setContent(new DictContent(mgr, d));
	}

	void refresh(int scrollTo) {
		content.clear();
		adapter().setContent(content);
		activity.post(() -> scrollToPosition(scrollTo));
	}

	@Nullable
	public Dict getCurrentDict() {
		return content.dict();
	}

	@Nullable
	public Word getCurrentWord() {
		return content.word();
	}

	public CharSequence getTitle() {
		return content.title();
	}

	public boolean isRoot() {
		return content.parent == null;
	}

	public boolean onBackPressed() {
		if (content.parent == null) return false;
		adapter().setContent(content.parent);
		Integer prev = stack.pollLast();
		if (prev != null) activity.post(() -> content.ls().thenRun(() -> scrollToPosition(prev)));
		return true;
	}

	@Override
	public void scrollToPosition(int position) {
		super.scrollToPosition(position);
		activity.post(() -> {
			for (int n = getChildCount(), i = 0; i < n; i++) {
				View c = getChildAt(i);
				if ((c instanceof DictItemView) && (((DictItemView) c).pos() == position)) {
					c.requestFocus();
					return;
				}
			}
		});
	}

	@Override
	public void close() {
		if (isClosed()) return;
		closed = true;
		content.mgr().reset();
	}

	public boolean isClosed() {
		return closed;
	}

	public void onFolderChanged() {
		Log.d("Dictionaries or cache folder changed - reloading");
		content.mgr().reset().thenRun(() -> adapter().setContent(content.root()));
	}

	public void onProgressChanged(Dict d, Word w) {
		for (int n = getChildCount(), i = 0; i < n; i++) {
			View c = getChildAt(i);
			if (!((c instanceof DictItemView)) || (c.getVisibility() != VISIBLE)) continue;
			DictItemView dv = (DictItemView) c;
			if (dv.item == d) dv.setItem(dv.item, dv.pos());
			else if (dv.item == w) dv.setProgress(w.getDirProgress(), w.getRevProgress());
		}
	}

	private DictAdapter adapter() {
		return (DictAdapter) getAdapter();
	}

	void scale(float scale) {
		for (int n = getChildCount(), i = 0; i < n; i++) {
			View c = getChildAt(i);
			if (c instanceof DictItemView) {
				((DictItemView) c).scale(scale);
			}
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	void setFilter(@NonNull String filter) {
		if (filter.equals(filterText)) return;
		filterText = filter;
		this.filter = filter.isEmpty() ? null
				: Pattern.compile(Pattern.quote(filter) + ".*", Pattern.CASE_INSENSITIVE);
		content.filtered = null;
		adapter().notifyDataSetChanged();
	}

	private static final class Holder extends ViewHolder {
		public Holder(@NonNull View itemView) {
			super(itemView);
		}
	}

	private final class DictAdapter extends Adapter<Holder> implements OnClickListener,
			OnLongClickListener {

		@NonNull
		@Override
		public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			DictItemView v = new DictItemView(parent.getContext());
			v.setClickable(true);
			v.setOnClickListener(this);
			v.setOnLongClickListener(this);
			return new Holder(v);
		}

		@Override
		public void onBindViewHolder(@NonNull Holder holder, int pos) {
			Content<?, ?, ?> c = content;
			FutureSupplier<? extends List<?>> f = c.ls();
			DictItemView v = (DictItemView) holder.itemView;

			if (f.isDone() && !f.isFailed()) {
				v.setItem(requireNonNull(f.peek()).get(pos), pos);
			} else {
				v.setItem(null, pos);
				f.main().onCompletion((r, err) -> {
					if (isClosed() || (c != content)) return;
					if (err != null) Log.e(err);
					else App.get().getHandler().post(this::notifyDataSetChanged);
				});
			}
		}

		@Override
		public int getItemCount() {
			Content<?, ?, ?> c = content;
			FutureSupplier<? extends List<?>> f = c.ls();

			if (f.isDone() && !f.isFailed()) {
				return requireNonNull(f.peek()).size();
			} else {
				f.main().onCompletion((r, err) -> {
					if (isClosed() || (c != content)) return;
					if (err != null) Log.e(err);
					else App.get().getHandler().post(this::notifyDataSetChanged);
				});
				return 0;
			}
		}

		@Override
		public void onClick(View v) {
			DictItemView dv = (DictItemView) v;
			Object item = dv.item;

			if (item instanceof Dict) {
				assert content instanceof MgrContent;
				setContent(new DictContent((MgrContent) content, (Dict) item));
			} else if (item instanceof Word) {
				assert content instanceof DictContent;
				setContent(new WordContent((DictContent) content, (Word) item));
			} else if (item instanceof Translation) {
				assert content instanceof WordContent;
				setContent(new TransContent((WordContent) content, (Translation) item));
			} else {
				return;
			}

			stack.addLast(dv.pos());
		}

		@Override
		public boolean onLongClick(View v) {
			DictItemView dv = (DictItemView) v;
			Object item = dv.item;
			activity.getContextMenu().show(b -> {
				b.setSelectionHandler(this::menuHandler);
				b.addItem(me.aap.fermata.R.id.delete, me.aap.fermata.R.drawable.delete,
						me.aap.fermata.R.string.delete).setData(dv);

				if (item instanceof Dict) {
					b.addItem(me.aap.fermata.R.id.share, me.aap.fermata.R.drawable.share,
							me.aap.fermata.R.string.share).setData(dv);
					b.addItem(R.id.wipe_prog, R.drawable.progress, R.string.wipe_prog).setData(dv);
				} else if (item instanceof Word) {
					assert content instanceof DictContent;
					b.addItem(R.id.change_prog, R.drawable.progress, R.string.change_prog)
							.setSubmenu(sb -> addProgressMenu(sb, dv));
				}
			});
			return true;
		}

		private boolean menuHandler(OverlayMenuItem i) {
			int id = i.getItemId();
			DictItemView dv = i.getData();
			int pos = dv.pos();
			Content<?, ?, ?> cnt = content;
			Context ctx = getContext();

			if (id == me.aap.fermata.R.id.delete) {
				Object item = dv.item;
				Supplier<FutureSupplier<Integer>> delete;

				if (item instanceof Dict) {
					delete = () -> getDictMgr().deleteDictionary((Dict) item);
				} else if (item instanceof Word) {
					delete = () -> requireNonNull(content.dict()).deleteWord(((Word) item).getWord());
				} else if (item instanceof Translation) {
					WordContent wc = (WordContent) content;
					delete = () -> wc.ls().then(tr -> {
						int idx = tr.indexOf(item);
						if (idx == -1) return completed(-1);
						tr.remove(idx);
						wc.notFiltered.remove(item);
						return wc.content.setTranslations(wc.dict(), wc.notFiltered).map(v -> idx);
					});
				} else if (item instanceof Example) {
					TransContent tc = (TransContent) content;
					delete = () -> tc.ls().then(ex -> {
						if ((tc.parent.notFiltered == null) || !tc.parent.notFiltered.contains(tc.content))
							return completed(-1);
						int idx = ex.indexOf(item);
						if (idx == -1) return completed(-1);
						ex.remove(idx);
						tc.notFiltered.remove(item);
						return tc.parent.content.setTranslations(tc.dict(),
								tc.parent.notFiltered).map(v -> idx);
					});
				} else {
					return false;
				}

				String title = ctx.getString(me.aap.fermata.R.string.delete);
				String msg = ctx.getString(me.aap.fermata.R.string.delete_confirm, item.toString());
				Drawable icon = getDrawable(ctx, me.aap.fermata.R.drawable.delete);
				showQuestion(ctx, title, msg, icon).onSuccess(v -> delete.get().main()
						.onFailure(err -> showAlert(ctx, err.toString()))
						.onSuccess(idx -> {
							if ((cnt == content) && (idx >= 0)) notifyItemRemoved(idx);
						}));
			} else if (id == R.id.wipe_prog) {
				Dict d = (Dict) dv.item;
				String title = ctx.getString(R.string.wipe_prog);
				String msg = ctx.getString(R.string.wipe_prog_for, d.toString());
				Drawable icon = getDrawable(ctx, R.drawable.progress);
				showQuestion(ctx, title, msg, icon).onSuccess(v -> d.wipeProgress().main().thenRun(() -> {
					if (cnt == content) notifyItemChanged(pos);
				}));
			} else if (id == me.aap.fermata.R.id.share) {
				Dict d = (Dict) dv.item;
				Uri u = FermataContentProvider.toAddonUri(FelexAddon.get().getInfo().moduleName,
						d.getDictFile().getRid().toAndroidUri(), d.getName() + DictMgr.DICT_EXT);
				Utils.send(activity, u, d.toString(), "text/plain");
			}

			return true;
		}

		private void addProgressMenu(OverlayMenu.Builder b, DictItemView dv) {
			Word w = (Word) dv.item;
			DictContent dc = (DictContent) content;
			PreferenceSet set = new PreferenceSet();
			PreferenceStore store = new BasicPreferenceStore();
			Pref<IntSupplier> dir = Pref.i("DIR", w.getDirProgress());
			Pref<IntSupplier> rev = Pref.i("REV", w.getRevProgress());

			set.addIntPref(o -> {
				o.pref = dir;
				o.store = store;
				o.seekScale = 10;
				o.title = R.string.dir_prog;
			});
			set.addIntPref(o -> {
				o.pref = rev;
				o.store = store;
				o.seekScale = 10;
				o.title = R.string.rev_prog;
			});

			b.setView(set.createView(getContext(), true));
			b.setCloseHandlerHandler(m -> {
				Dict d = dc.content;
				w.incrProgress(d, true, store.getIntPref(dir) - w.getDirProgress());
				w.incrProgress(d, false, store.getIntPref(rev) - w.getRevProgress());
				notifyItemChanged(dv.pos());
			});
		}

		@SuppressLint("NotifyDataSetChanged")
		void setContent(@NonNull Content<?, ?, ?> c) {
			content = c;
			notifyDataSetChanged();
			activity.fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
		}
	}

	@SuppressWarnings("rawtypes")
	private abstract class Content<C, L, P extends Content> {
		final P parent;
		final C content;
		FutureSupplier<List<L>> filtered;
		List<L> notFiltered;

		Content(P parent, C content) {
			this.parent = parent;
			this.content = content;
		}

		@NonNull
		abstract DictMgr mgr();

		@Nullable
		abstract Dict dict();

		@Nullable
		abstract Word word();

		abstract CharSequence title();

		abstract FutureSupplier<List<L>> list();

		abstract boolean matches(Pattern filter, L item);

		FutureSupplier<List<L>> ls() {
			return (filtered != null) ? filtered : (filtered = list().main().map(l -> {
				List<L> filtered = filter(l);
				this.filtered = completed(filtered);
				this.notFiltered = l;
				return filtered;
			}));
		}

		List<L> filter(List<L> list) {
			Pattern f = filter;
			if (f == null) return list;
			List<L> ls = new ArrayList<>();
			for (L i : list) {
				if (matches(f, i)) ls.add(i);
			}
			return ls;
		}

		MgrContent root() {
			for (Content c = this; ; c = c.parent) {
				if (c instanceof MgrContent) return (MgrContent) c;
			}
		}

		void clear() {
			filtered = null;
			if (parent != null) parent.clear();
		}
	}

	private final class MgrContent extends Content<DictMgr, Dict, MgrContent> {
		MgrContent() {
			super(null, DictMgr.get());
		}

		@NonNull
		@Override
		DictMgr mgr() {
			return content;
		}

		@Nullable
		@Override
		Dict dict() {
			return null;
		}

		@Nullable
		@Override
		Word word() {
			return null;
		}

		@Override
		CharSequence title() {
			return getContext().getString(me.aap.fermata.R.string.addon_name_felex);
		}

		@Override
		FutureSupplier<List<Dict>> list() {
			return content.getDictionaries();
		}

		@Override
		boolean matches(Pattern filter, Dict item) {
			return filter.matcher(item.getName()).matches();
		}
	}

	private final class DictContent extends Content<Dict, Word, MgrContent> {
		DictContent(MgrContent mgr, Dict content) {
			super(mgr, content);
		}

		@NonNull
		@Override
		DictMgr mgr() {
			return parent.mgr();
		}

		@NonNull
		@Override
		Dict dict() {
			return content;
		}

		@Nullable
		@Override
		Word word() {
			return null;
		}

		@Override
		CharSequence title() {
			return content.toString();
		}

		@Override
		protected FutureSupplier<List<Word>> list() {
			return content.getWords();
		}

		@Override
		boolean matches(Pattern filter, Word item) {
			return filter.matcher(item.getWord()).matches();
		}

		@Override
		List<Word> filter(List<Word> list) {
			Pattern f = filter;
			if ((f == null) || filterText.isEmpty()) return list;
			int from = findWord(list, filterText);
			if (from < 0) from = -from - 1;
			int to = from;
			for (int s = list.size(); (to < s) && matches(f, list.get(to)); to++) ;
			return list.subList(from, to);
		}
	}

	private final class WordContent extends Content<Word, Translation, DictContent> {

		WordContent(DictContent parent, Word content) {
			super(parent, content);
		}

		@NonNull
		@Override
		DictMgr mgr() {
			return parent.mgr();
		}

		@NonNull
		@Override
		Dict dict() {
			return parent.dict();
		}

		@NonNull
		@Override
		Word word() {
			return content;
		}

		@Override
		CharSequence title() {
			return content.getExpr();
		}

		@Override
		protected FutureSupplier<List<Translation>> list() {
			return content.getTranslations(dict());
		}

		@Override
		boolean matches(Pattern filter, Translation item) {
			if (filter.matcher(item.getTranslation()).find()) return true;
			for (Example e : item.getExamples()) {
				if (filter.matcher(e.getSentence()).find()) return true;
				if (filter.matcher(e.getTranslation()).find()) return true;
			}
			return false;
		}
	}

	private final class TransContent extends Content<Translation, Example, WordContent> {

		TransContent(WordContent parent, Translation content) {
			super(parent, content);
		}

		@NonNull
		@Override
		DictMgr mgr() {
			return parent.mgr();
		}

		@NonNull
		@Override
		Dict dict() {
			return parent.dict();
		}

		@NonNull
		@Override
		Word word() {
			return parent.word();
		}

		@Override
		CharSequence title() {
			return content.getTranslation();
		}

		@Override
		protected FutureSupplier<List<Example>> list() {
			return completed(content.getExamples());
		}

		@Override
		boolean matches(Pattern filter, Example item) {
			return filter.matcher(item.getSentence()).find()
					|| filter.matcher(item.getTranslation()).find();
		}
	}

	private final class DictItemView extends ConstraintLayout {
		private final ImageView icon;
		private final ScalableTextView title;
		private final ScalableTextView subtitle;
		private final ProgressBar progress;
		private final LayerDrawable dirProgEq;
		private final LayerDrawable dirProgDone;
		private final LayerDrawable dirProgLess;
		private final LayerDrawable revProgLess;
		Object item;
		int pos;

		DictItemView(@NonNull Context ctx) {
			super(ctx);
			inflate(ctx, R.layout.dict_item, this);
			icon = findViewById(R.id.icon);
			title = findViewById(R.id.title);
			subtitle = findViewById(R.id.subtitle);
			progress = findViewById(R.id.progress);
			setBackgroundResource(me.aap.fermata.R.drawable.media_item_bg);
			MarginLayoutParams lp = new MarginLayoutParams(MATCH_PARENT, WRAP_CONTENT);
			lp.bottomMargin = toIntPx(ctx, 3);
			setLayoutParams(lp);
			Drawable eqBg = new ScaleDrawable(new ColorDrawable(0xFFFFA200), Gravity.START, 1, -1);
			Drawable doneBg = new ScaleDrawable(new ColorDrawable(Color.GREEN), Gravity.START, 1, -1);
			Drawable dirBg = new ScaleDrawable(new ColorDrawable(Color.YELLOW), Gravity.START, 1, -1);
			Drawable revBg = new ScaleDrawable(new ColorDrawable(Color.RED), Gravity.START, 1, -1);
			dirProgEq = new LayerDrawable(new Drawable[]{eqBg, eqBg});
			dirProgDone = new LayerDrawable(new Drawable[]{doneBg, doneBg});
			dirProgLess = new LayerDrawable(new Drawable[]{revBg, dirBg});
			revProgLess = new LayerDrawable(new Drawable[]{dirBg, revBg});
			dirProgEq.setId(0, android.R.id.progress);
			dirProgEq.setId(1, android.R.id.secondaryProgress);
			dirProgDone.setId(0, android.R.id.progress);
			dirProgDone.setId(1, android.R.id.secondaryProgress);
			dirProgLess.setId(0, android.R.id.progress);
			dirProgLess.setId(1, android.R.id.secondaryProgress);
			revProgLess.setId(0, android.R.id.progress);
			revProgLess.setId(1, android.R.id.secondaryProgress);
		}

		int pos() {
			List<?> l = content.list().peek();
			if ((l == null) || (pos < 0) || (pos >= l.size())) return 0;
			if (l.get(pos) != item) pos = Math.max(l.indexOf(item), 0);
			return pos;
		}

		void setItem(Object item, int pos) {
			this.item = item;
			this.pos = pos;

			if (item == null) {
				setIcon(0);
				title.setText(null);
				setSubtitle(null);
			} else if (item instanceof Dict) {
				Dict d = (Dict) item;
				Context ctx = getContext();
				int wc = d.getWordsCount();
				int dir = d.getDirProgress();
				int rev = d.getRevProgress();
				setIcon(me.aap.fermata.R.drawable.felex);
				title.setText(d.toString());

				if (wc > 0) {
					setProgress(dir, rev);
					if ((dir == 0) && (rev == 0)) {
						setSubtitle(ctx.getString(R.string.dict_sub1, wc));
					} else {
						setSubtitle(ctx.getString(R.string.dict_sub2, wc, dir, rev));
					}
				} else {
					setSubtitle(null);
					setProgress(0, 0);
				}
			} else if (item instanceof Word) {
				assert content instanceof DictContent;
				Word w = (Word) item;
				Dict d = ((DictContent) content).content;
				setIcon(0);
				title.setText(w.getWord());
				setProgress(w.getDirProgress(), w.getRevProgress());
				w.getTranslations(d).main().onSuccess(trans -> {
					if ((this.item != w) || (content.content != d)) return;
					StringBuilder sb = new StringBuilder();
					for (Iterator<Translation> it = trans.iterator(); it.hasNext(); ) {
						Translation t = it.next();
						sb.append(t.getTranslation());
						if (it.hasNext()) sb.append("; ");
					}
					setSubtitle(sb);
				}).ifNotDone(() -> setSubtitle(null));
			} else if (item instanceof Translation) {
				Translation t = (Translation) item;
				StringBuilder sb = new StringBuilder();
				setIcon(0);
				setProgress(0, 0);
				title.setText(t.getTranslation());
				for (Iterator<Example> it = t.getExamples().iterator(); it.hasNext(); ) {
					Example e = it.next();
					String trans = e.getTranslation();
					sb.append(" - ").append(e.getSentence());
					if (trans != null) sb.append("\n   ").append(trans);
					if (it.hasNext()) sb.append('\n');
				}
				setSubtitle(sb);
			} else if (item instanceof Example) {
				Example e = (Example) item;
				setIcon(0);
				setProgress(0, 0);
				title.setText(e.getSentence());
				setSubtitle(e.getTranslation());
			}
		}

		private void setIcon(int i) {
			if (i == 0) {
				icon.setVisibility(GONE);
			} else {
				icon.setImageResource(i);
				icon.setVisibility(VISIBLE);
				setIconSize(activity.getTextIconSize());
			}
		}

		private void setSubtitle(CharSequence text) {
			if ((text == null) || TextUtils.isBlank(text)) {
				int m = toIntPx(getContext(), 10);
				MarginLayoutParams lp = (MarginLayoutParams) title.getLayoutParams();
				lp.topMargin = lp.bottomMargin = m;
				subtitle.setText(null);
				subtitle.setVisibility(GONE);
			} else {
				int m = toIntPx(getContext(), 1);
				MarginLayoutParams lp = (MarginLayoutParams) title.getLayoutParams();
				lp.topMargin = lp.bottomMargin = m;
				subtitle.setText(text);
				subtitle.setVisibility(VISIBLE);
			}
		}

		void setProgress(int dir, int rev) {
			if ((dir == 0) && (rev == 0)) {
				progress.setVisibility(GONE);
				return;
			}

			progress.setVisibility(VISIBLE);
			// A workaround for the progress update issue
			progress.setLayoutParams(progress.getLayoutParams());

			if (dir == rev) {
				progress.setProgress(dir);
				progress.setSecondaryProgress(rev);
				progress.setProgressDrawable((dir == 100) ? dirProgDone : dirProgEq);
			} else if (dir < rev) {
				progress.setProgress(rev);
				progress.setSecondaryProgress(dir);
				progress.setProgressDrawable(dirProgLess);
			} else {
				progress.setProgress(dir);
				progress.setSecondaryProgress(rev);
				progress.setProgressDrawable(revProgLess);
			}
		}

		void scale(float scale) {
			title.scale(scale);
			subtitle.scale(scale);
			if (icon.getVisibility() == VISIBLE) setIconSize(scale);
		}

		private void setIconSize(float scale) {
			Context ctx = getContext();
			ViewGroup.LayoutParams lp = icon.getLayoutParams();
			int s = (int) ((subtitle.getTextSize() + subtitle.getTextSize() + toPx(ctx, 10)) * scale);
			lp.height = s;
			lp.width = s;
			icon.setLayoutParams(lp);
		}
	}
}
