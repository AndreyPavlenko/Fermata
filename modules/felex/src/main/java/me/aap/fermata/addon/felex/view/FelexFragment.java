package me.aap.fermata.addon.felex.view;

import static android.view.View.GONE;
import static java.util.Objects.requireNonNull;
import static me.aap.fermata.addon.felex.FelexAddon.CACHE_FOLDER;
import static me.aap.fermata.addon.felex.FelexAddon.DICT_FOLDER;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.collection.CollectionUtils.comparing;
import static me.aap.utils.collection.CollectionUtils.contains;
import static me.aap.utils.text.TextUtils.isNullOrBlank;
import static me.aap.utils.ui.UiUtils.queryPrefs;
import static me.aap.utils.ui.UiUtils.queryText;
import static me.aap.utils.ui.UiUtils.showAlert;
import static me.aap.utils.ui.UiUtils.showQuestion;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.felex.R;
import me.aap.fermata.addon.felex.dict.Dict;
import me.aap.fermata.addon.felex.dict.DictInfo;
import me.aap.fermata.addon.felex.dict.DictMgr;
import me.aap.fermata.addon.felex.dict.Example;
import me.aap.fermata.addon.felex.dict.Translation;
import me.aap.fermata.addon.felex.dict.Word;
import me.aap.fermata.addon.felex.tutor.DictTutor;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityListener;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.function.BooleanConsumer;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;
import me.aap.utils.voice.TextToSpeech;

/**
 * @author Andrey Pavlenko
 */
public class FelexFragment extends MainActivityFragment implements
		MainActivityListener, PreferenceStore.Listener, ToolBarView.Listener {
	private DictTutor tutor;
	private Uri importDict;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.felex_fragment;
	}

	@Override
	public boolean onBackPressed() {
		return view().onBackPressed();
	}

	@Override
	public boolean isRootPage() {
		return view().isRoot();
	}

	@Override
	public CharSequence getTitle() {
		return view().getTitle();
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarMediator.instance;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
													 @Nullable Bundle savedInstanceState) {
		activity().onSuccess(a -> {
			a.addBroadcastListener(this);
			a.getPrefs().addBroadcastListener(this);
			a.getToolBar().addBroadcastListener(this);
			FermataApplication.get().getPreferenceStore().addBroadcastListener(this);
		});
		return inflater.inflate(R.layout.felex_list, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		importDict();
	}

	@Override
	public void onDestroyView() {
		view().close();
		activity().onSuccess(this::cleanUp);
		super.onDestroyView();
	}

	@Override
	public void onHiddenChanged(boolean hidden) {
		super.onHiddenChanged(hidden);
		closeTutor();
	}

	public boolean canScrollUp() {
		FelexListView v = view();
		Object content = v.getContent();
		if (!(content instanceof DictMgr) && !(content instanceof Dict)) return true;
		return (v.getScrollY() > 0);
	}

	@Override
	public void onRefresh(BooleanConsumer refreshing) {
		refresh(view().getContent()).onCompletion((r, f) -> refreshing.accept(false));
	}

	private FutureSupplier<Void> refresh(Object content) {
		if (content instanceof DictMgr) {
			return ((DictMgr) content).reset().thenRun(() -> view().refresh(0));
		} else if (content instanceof Dict) {
			return ((Dict) content).reset().thenRun(() -> view().refresh(0));
		} else {
			return completedVoid();
		}
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder builder) {
		FutureSupplier<List<Dict>> f = view().getDictMgr().getDictionaries();
		if (!f.isDone() || f.peek(Collections::emptyList).isEmpty()) return;
		OverlayMenu.Builder b = builder.withSelectionHandler(this::navBarMenuItemSelected);
		b.addItem(R.id.start_tutor, me.aap.fermata.R.drawable.record_voice,
				R.string.start_tutor).setSubmenu(this::buildTutorMenu);

		Object content = view().getContent();

		if ((content instanceof DictMgr) || (content instanceof Dict)) {
			b.addItem(me.aap.fermata.R.id.refresh, me.aap.fermata.R.drawable.refresh,
					me.aap.fermata.R.string.refresh).setData(content);
		}

		super.contributeToNavBarMenu(b);
	}

	private void buildTutorMenu(OverlayMenu.Builder sb) {
		sb.setSelectionHandler(this::navBarMenuItemSelected);
		sb.addItem(R.id.start_tutor_dir, R.string.start_tutor_dir);
		sb.addItem(R.id.start_tutor_rev, R.string.start_tutor_rev);
		sb.addItem(R.id.start_tutor_mix, R.string.start_tutor_mix);
		sb.addItem(R.id.start_tutor_listen, R.string.start_tutor_listen);
	}

	private boolean navBarMenuItemSelected(OverlayMenuItem item) {
		int id = item.getItemId();

		if (id == R.id.start_tutor_dir) {
			startTutor(DictTutor.MODE_DIRECT);
		} else if (id == R.id.start_tutor_rev) {
			startTutor(DictTutor.MODE_REVERSE);
		} else if (id == R.id.start_tutor_mix) {
			startTutor(DictTutor.MODE_MIXED);
		} else if (id == R.id.start_tutor_listen) {
			startTutor(DictTutor.MODE_LISTENING);
		} else if (id == me.aap.fermata.R.id.refresh) {
			refresh(item.getData());
		}

		return true;
	}

	@Override
	public void onActivityEvent(MainActivityDelegate a, long e) {
		if (e == ACTIVITY_DESTROY) cleanUp(a);
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		activity().onSuccess(a -> {
			if (MainActivityPrefs.hasTextIconSizePref(a, prefs)) {
				View v = getView();
				if (v instanceof FelexListView) ((FelexListView) v).scale(a.getTextIconSize());
			} else if (prefs.contains(DICT_FOLDER) || prefs.contains(CACHE_FOLDER)) {
				closeTutor();
				view().onFolderChanged();
			}
		});
	}

	@Override
	public void onToolBarEvent(ToolBarView tb, byte event) {
		if (event == FILTER_CHANGED) {
			view().setFilter(tb.getFilter().getText().toString());
		}
	}

	@Override
	public void setInput(Object input) {
		if (!(input instanceof Uri)) return;
		importDict = (Uri) input;
		if (getView() != null) importDict();
	}

	private void importDict() {
		if (importDict == null) return;
		Uri uri = importDict;
		importDict = null;
		Context ctx = requireContext();
		App.get().execute(() -> {
			try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
				return DictInfo.read(in);
			}
		}).main().onFailure(err -> {
			Log.e(err);
			showAlert(ctx, ctx.getString(R.string.err_invalid_dict_hdr, uri));
		}).then(info -> {
			String title = ctx.getString(R.string.import_dict);
			String msg = ctx.getString(R.string.import_dict_q, info);
			Drawable icon = AppCompatResources.getDrawable(ctx, me.aap.fermata.R.drawable.felex);
			return showQuestion(ctx, title, msg, icon).then(v -> DictMgr.get()
					.getDictionary(info.getName()).then(d -> {
						if (d == null) {
							return DictMgr.get().createDictionary(info);
						} else if (d.getSourceLang().equals(info.getSourceLang())
								&& d.getTargetLang().equals(info.getTargetLang())) {
							return showQuestion(ctx, title, ctx.getString(R.string.import_dict_exist, d), icon)
									.then(ok -> completed(d), cancel -> createDict(info));
						} else {
							return createDict(info);
						}
					}));
		}).then(d -> d.addWords(uri)).onSuccess(view()::setContent);
	}

	private FutureSupplier<Dict> createDict(DictInfo i) {
		return queryText(requireContext(), R.string.dict_name, me.aap.fermata.R.drawable.felex)
				.then(name -> DictMgr.get().createDictionary(name, i.getSourceLang(), i.getTargetLang()));
	}

	@NonNull
	private FelexListView view() {
		return (FelexListView) requireView();
	}

	private void startTutor(byte mode) {
		closeTutor();
		activity().onSuccess(a -> {
			FelexListView v = view();
			Dict d = v.getCurrentDict();
			FutureSupplier<DictTutor> f = (d != null) ? DictTutor.create(a, d, mode)
					: v.getDictMgr().getDictionaries().then(list ->
					list.isEmpty() ? completedNull() : DictTutor.create(a, list.get(0), mode).map(t -> t));
			f.onCompletion((t, err) -> {
				closeTutor();
				if (err != null) {
					showAlert(requireContext(), err.toString());
					return;
				}
				if (t == null) return;
				tutor = t;
				t.start();
			});
		});
	}

	private void closeTutor() {
		if (tutor != null) {
			tutor.close();
			tutor = null;
		}
	}

	private void cleanUp(MainActivityDelegate a) {
		closeTutor();
		a.removeBroadcastListener(this);
		a.getPrefs().removeBroadcastListener(this);
		a.getToolBar().removeBroadcastListener(this);
		FermataApplication.get().getPreferenceStore().removeBroadcastListener(this);
	}

	private FutureSupplier<MainActivityDelegate> activity() {
		return MainActivityDelegate.getActivityDelegate(getContext());
	}

	private static final class ToolBarMediator implements ToolBarView.Mediator.BackTitleFilter {
		static final ToolBarView.Mediator instance = new ToolBarMediator();

		@Override
		public void enable(ToolBarView tb, ActivityFragment f) {
			BackTitleFilter.super.enable(tb, f);
			addButton(tb, me.aap.fermata.R.drawable.playlist_add, ToolBarMediator::add, R.id.add);
			addButton(tb, me.aap.fermata.R.drawable.record_voice, ToolBarMediator::tutor, R.id.start_tutor);
			setButtonsVisibility(tb, f);
		}

		@Override
		public void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
			ToolBarView.Mediator.BackTitleFilter.super.onActivityEvent(tb, a, e);
			if ((e == FRAGMENT_CHANGED) || e == FRAGMENT_CONTENT_CHANGED) {
				setButtonsVisibility(tb, a.getActiveFragment());
			}
		}

		private void setButtonsVisibility(ToolBarView tb, ActivityFragment f) {
			if (!(f instanceof FelexFragment)) return;
			FelexFragment ff = (FelexFragment) f;
			Object content = ff.view().getContent();

			if ((content instanceof DictMgr) || (content instanceof Dict)
					|| (content instanceof Word) || (content instanceof Translation)) {
				tb.findViewById(R.id.add).setVisibility(View.VISIBLE);
			} else {
				tb.findViewById(R.id.add).setVisibility(GONE);
			}
		}

		private static void add(View v) {
			MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
			ActivityFragment f = a.getActiveFragment();
			if (!(f instanceof FelexFragment)) return;
			FelexFragment ff = (FelexFragment) f;
			FelexListView lv = ff.view();
			Object content = lv.getContent();

			if (content instanceof DictMgr) {
				addDict(ff, (DictMgr) content);
			} else if (content instanceof Dict) {
				addWord(ff, (Dict) content);
			} else if (content instanceof Word) {
				addTrans(ff, requireNonNull(lv.getCurrentDict()), (Word) content);
			} else if (content instanceof Translation) {
				addExample(ff, requireNonNull(lv.getCurrentDict()),
						requireNonNull(lv.getCurrentWord()), (Translation) content);
			}
		}

		private static void addDict(FelexFragment ff, DictMgr mgr) {
			Context ctx = ff.requireContext();
			TextToSpeech.create(ctx).onSuccess(tts -> {
				List<Locale> locales = new ArrayList<>(tts.getAvailableLanguages());
				tts.close();

				if (locales.isEmpty()) {
					showAlert(ctx, R.string.no_lang_supported).thenRun(() ->
							TextToSpeech.installTtsData(ctx));
					return;
				}

				Collections.sort(locales, comparing(Locale::getDisplayName));
				String[] langs = new String[locales.size()];
				Locale defaultLang = Locale.getDefault();
				int defaultLangIdx = 0;

				for (int i = 0; i < langs.length; i++) {
					Locale l = locales.get(i);
					langs[i] = l.getDisplayName();
					if (l.equals(defaultLang)) defaultLangIdx = i;
				}

				Pref<Supplier<String>> namePref = Pref.s("NAME", "");
				Pref<IntSupplier> srcLangPref = Pref.i("SRC_LANG", defaultLangIdx);
				Pref<IntSupplier> targetLangPref = Pref.i("TARGET_LANG", defaultLangIdx);
				queryPrefs(ctx, R.string.add_dict, (store, set) -> {
					set.addStringPref(o -> {
						o.pref = namePref;
						o.title = R.string.dict_name;
						o.store = store;
					});
					set.addListPref(o -> {
						o.pref = srcLangPref;
						o.title = R.string.src_lang;
						o.store = store;
						o.stringValues = langs;
						o.formatSubtitle = true;
						o.subtitle = me.aap.fermata.R.string.string_format;
					});
					set.addListPref(o -> {
						o.pref = targetLangPref;
						o.title = R.string.target_lang;
						o.store = store;
						o.stringValues = langs;
						o.formatSubtitle = true;
						o.subtitle = me.aap.fermata.R.string.string_format;
					});
				}, p -> {
					String name = p.getStringPref(namePref);
					if (isNullOrBlank(name)) return false;
					List<Dict> dicts = mgr.getDictionaries().peek();
					return (dicts == null) || !contains(dicts, d -> d.getName().equalsIgnoreCase(name));
				}).onSuccess(p -> {
					String name = p.getStringPref(namePref);
					if (isNullOrBlank(name)) return;
					int srcLangIdx = p.getIntPref(srcLangPref);
					int targetLangIdx = p.getIntPref(targetLangPref);
					mgr.createDictionary(name, locales.get(srcLangIdx), locales.get(targetLangIdx)).main()
							.onCompletion((d, err) -> {
								if (err != null) {
									showAlert(ctx, err.getLocalizedMessage());
								} else {
									ff.view().setContent(d);
								}
							});
				});
			});
		}

		private static void addWord(FelexFragment ff, Dict d) {
			Pref<Supplier<String>> wordPref = Pref.s("WORD");
			Pref<Supplier<String>> transPref = Pref.s("TRANS");
			Pref<Supplier<String>> exPref = Pref.s("EX");
			Pref<Supplier<String>> exTransPref = Pref.s("EX_TRANS");

			queryPrefs(ff.getContext(), R.string.add_word, (store, set) -> {
				set.addStringPref(o -> {
					o.pref = wordPref;
					o.title = R.string.word;
					o.store = store;
				});
				set.addStringPref(o -> {
					o.pref = transPref;
					o.title = R.string.trans;
					o.store = store;
				});
				set.addStringPref(o -> {
					o.pref = exPref;
					o.title = R.string.example;
					o.store = store;
				});
				set.addStringPref(o -> {
					o.pref = exTransPref;
					o.title = R.string.example_trans;
					o.store = store;
				});
			}, p -> {
				if (isNullOrBlank(p.getStringPref(transPref))) return false;
				String word = p.getStringPref(wordPref);
				if (isNullOrBlank(word)) return false;
				Boolean exist = d.hasWord(word).peek();
				return (exist == null) || !exist;
			}).onSuccess(p -> {
				String word = p.getStringPref(wordPref);
				String trans = p.getStringPref(transPref);
				String ex = p.getStringPref(exPref);
				String exTrans = p.getStringPref(exTransPref);
				d.addWord(word, trans, ex, exTrans).main().onCompletion((idx, err) -> {
					if (err != null) {
						showAlert(ff.getContext(), err.getLocalizedMessage());
					} else {
						ff.view().refresh(idx);
					}
				});
			});
		}

		private static void addTrans(FelexFragment ff, Dict d, Word w) {
			Pref<Supplier<String>> transPref = Pref.s("TRANS");
			Pref<Supplier<String>> exPref = Pref.s("EX");
			Pref<Supplier<String>> exTransPref = Pref.s("EX_TRANS");

			w.getTranslations(d).then(translations ->
					queryPrefs(ff.getContext(), R.string.add_trans, (store, set) -> {
						set.addStringPref(o -> {
							o.pref = transPref;
							o.title = R.string.trans;
							o.store = store;
						});
						set.addStringPref(o -> {
							o.pref = exPref;
							o.title = R.string.example;
							o.store = store;
						});
						set.addStringPref(o -> {
							o.pref = exTransPref;
							o.title = R.string.example_trans;
							o.store = store;
						});
					}, p -> {
						String trans = p.getStringPref(transPref);
						if (isNullOrBlank(trans)) return false;
						return !CollectionUtils.contains(translations,
								t -> trans.equalsIgnoreCase(t.getTranslation()));
					}).onSuccess(p -> {
						String trans = p.getStringPref(transPref);
						String ex = p.getStringPref(exPref);
						String exTrans = p.getStringPref(exTransPref);
						List<Translation> newTrans = new ArrayList<>(translations.size() + 1);
						newTrans.addAll(translations);
						newTrans.add(new Translation(trans, ex, exTrans));
						w.setTranslations(d, newTrans).onCompletion((v, err) -> {
							if (err != null) {
								showAlert(ff.getContext(), err.getLocalizedMessage());
							} else {
								ff.view().refresh(newTrans.size() - 1);
							}
						});
					}));
		}

		private static void addExample(FelexFragment ff, Dict d, Word w, Translation tr) {
			Pref<Supplier<String>> exPref = Pref.s("EX");
			Pref<Supplier<String>> exTransPref = Pref.s("EX_TRANS");

			w.getTranslations(d).then(translations -> {
				int idx = CollectionUtils.indexOf(translations,
						t -> t.getTranslation().equalsIgnoreCase(tr.getTranslation()));
				if (idx == -1) return completedNull();
				translations.set(idx, tr);
				return queryPrefs(ff.getContext(), R.string.add_example, (store, set) -> {
					set.addStringPref(o -> {
						o.pref = exPref;
						o.title = R.string.example;
						o.store = store;
					});
					set.addStringPref(o -> {
						o.pref = exTransPref;
						o.title = R.string.example_trans;
						o.store = store;
					});
				}, p -> {
					String ex = p.getStringPref(exPref);
					if (isNullOrBlank(ex)) return false;
					return !CollectionUtils.contains(tr.getExamples(),
							e -> ex.equalsIgnoreCase(e.getSentence()));
				}).onSuccess(p -> {
					String ex = p.getStringPref(exPref);
					String exTrans = p.getStringPref(exTransPref);
					List<Example> examples = tr.getExamples();
					List<Example> newExamples = new ArrayList<>(examples.size() + 1);
					newExamples.addAll(examples);
					newExamples.add(new Example(ex, exTrans));
					tr.setExamples(newExamples);
					w.setTranslations(d, translations).onCompletion((v, err) -> {
						if (err != null) {
							showAlert(ff.getContext(), err.getLocalizedMessage());
						} else {
							ff.view().refresh(newExamples.size() - 1);
						}
					});
				});
			});
		}

		private static void tutor(View v) {
			MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
			ActivityFragment f = a.getActiveFragment();
			if (!(f instanceof FelexFragment)) return;
			FelexFragment ff = (FelexFragment) f;
			a.getToolBarMenu().show(ff::buildTutorMenu);
		}
	}
}
