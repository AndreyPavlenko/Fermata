package me.aap.utils.ui.fragment;

import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.LEFT;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.ui.UiUtils.toPx;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import me.aap.utils.R;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.log.Log;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.view.FloatingButton;
import me.aap.utils.ui.view.ListView;
import me.aap.utils.ui.view.ToolBarView;
import me.aap.utils.ui.view.VirtualResourceAdapter;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class FilePickerFragment extends GenericDialogFragment implements
		ListView.ItemClickListener<VirtualResource>, ListView.ItemsChangeListener<VirtualResource> {
	public static final byte FILE = 1;
	public static final byte FOLDER = 2;
	public static final byte WRITABLE = 4;
	public static final byte FILE_OR_FOLDER = FILE | FOLDER;
	private State state = new State();

	public FilePickerFragment() {
		super(ToolBarMediator.instance, FloatingButtonMediator.instance);
	}

	@Override
	public int getFragmentId() {
		return R.id.file_picker;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Override
	public void setInput(Object input) {
		if (input instanceof FutureSupplier) {
			setSupplier((FutureSupplier) input);
		} else if (input instanceof VirtualFileSystem) {
			setFileSystem((VirtualFileSystem) input);
		} else {
			super.setInput(input);
		}
	}

	public void setFileSystem(VirtualFileSystem fs) {
		setSupplier(fs.getRoots().map(r -> new BiHolder<>(null, r)));
	}

	public void setFolder(VirtualFolder folder) {
		setSupplier(folder.getChildren().map(c -> new BiHolder<>(folder, c)));
	}

	public void setResources(VirtualResource parent, List<? extends VirtualResource> children) {
		setSupplier(completed(new BiHolder<>(parent, children)));
	}

	public void setSupplier(FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>> supplier) {
		state.supplier = supplier;
		ListView<VirtualResource> v = getListView();
		if (v != null) v.setItems(supplier);
	}

	public void setFileConsumer(Consumer<VirtualResource> consumer) {
		state.consumer = consumer;
	}

	public void setMode(byte mode) {
		state.mode = mode;
		setFilter(getListView());
	}

	public void setPattern(@Nullable Pattern pattern) {
		state.pattern = pattern;
		setFilter(getListView());
	}

	public void setCreateFolder(CreateFolder create) {
		state.create = create;
	}

	public void setOnLongClick(Function<VirtualResource, Boolean> onLongClick) {
		state.onLongClick = onLongClick;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestManageAllFilesPerm(requireContext());
	}

	public static boolean requestManageAllFilesPerm(Context ctx) {
		if (!(ctx instanceof Activity) || (SDK_INT < Build.VERSION_CODES.R)) return false;

		App app = App.get();

		if (app.hasManifestPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE)
				&& !Environment.isExternalStorageManager()) {
			Intent req = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
			Uri uri = Uri.fromParts("package", app.getPackageName(), null);
			req.setData(uri);

			try {
				ctx.startActivity(req);
				return true;
			} catch (ActivityNotFoundException ex) {
				Log.e(ex, "Failed to request %s", ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
			}
		}

		return false;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		ListView<VirtualResource> v = new ListView<>(getContext(), null, 0, R.style.Theme_Utils_Base_FileListViewStyle);
		v.setItemAdapter(new VirtualResourceAdapter(getContext(), null));
		return v;
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ListView<VirtualResource> v = getListView();
		if (v == null) return;
		ViewGroup.LayoutParams lp = v.getLayoutParams();
		if (lp != null) lp.height = lp.width = MATCH_PARENT;
		v.setHasFixedSize(true);
		v.setItemClickListener(this);
		v.setItemsChangeListener(this);
		setFilter(v);
		if (state.supplier != null) v.setItems(requireNonNull(state.supplier));
	}

	private void setFilter(ListView<VirtualResource> v) {
		if (v == null) return;

		if ((state.pattern != null) || (state.mode != FILE_OR_FOLDER)) {
			v.setFilter(this::filter);
		} else {
			v.setFilter(null);
		}
	}

	private boolean filter(VirtualResource f) {
		return (state.pattern == null) || (f instanceof VirtualFolder) ||
				state.pattern.matcher(f.getName()).matches();
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return ToolBarMediator.instance;
	}

	@Override
	public boolean isRootPage() {
		ListView<VirtualResource> v = getListView();
		return (v == null) || (v.getParentItem() == null);
	}

	@Override
	public boolean onBackPressed() {
		ListView<VirtualResource> v = getListView();
		if ((v == null) || (v.getParentItem() == null)) {
			if ((state != null) && (state.consumer != null)) {
				pick(null);
				return true;
			}
			return false;
		}

		v.setParentItems();
		return true;
	}

	@Override
	public boolean onListItemClick(VirtualResource item) {
		ListView<VirtualResource> v = getListView();
		if (v == null) return false;

		if (item.isFile()) {
			if ((state.mode & FILE) != 0) pick(item);
			return true;
		}

		return false;
	}

	@Override
	public boolean onListItemLongClick(VirtualResource item) {
		return (state.onLongClick != null) && state.onLongClick.apply(item);
	}

	@Override
	public void onListItemsChange(@Nullable VirtualResource parent, @NonNull List<? extends VirtualResource> items) {
		getActivityDelegate().fireBroadcastEvent(FRAGMENT_CONTENT_CHANGED);
		if (parent == null) return;
		if (parent.isFile()) onListItemClick(parent);
	}

	public void setPath(String path) {
		ListView<VirtualResource> v = getListView();
		if (v == null) return;
		v.setItems(path);
	}

	public CharSequence getPath() {
		ListView<VirtualResource> v = getListView();
		if (v == null) return "";
		VirtualResource p = v.getParentItem();
		return (p == null) ? "" : p.getRid().toString();
	}

	protected void onOkButtonClick() {
		ListView<VirtualResource> v = getListView();
		if (v != null) pick(v.getParentItem());
	}

	protected void onCloseButtonClick() {
		pick(null);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private ListView<VirtualResource> getListView() {
		return (ListView<VirtualResource>) getView();
	}

	private void pick(VirtualResource v) {
		Consumer<? super VirtualResource> c = state.consumer;
		ListView<VirtualResource> view = getListView();
		state = new State();
		if (view != null) view.cleanUp();
		if (c != null) c.accept(v);
	}

	protected int getOkButtonVisibility() {
		ListView<VirtualResource> v = getListView();
		if (v == null) return GONE;
		if (v.getParentItem() == null) return GONE;
		if ((state.mode & FOLDER) != 0) {
			if (state.pattern == null) return VISIBLE;
			return state.pattern.matcher(v.getParentItem().getName()).matches() ? VISIBLE : GONE;
		}
		return GONE;
	}

	public Object resetState() {
		State st = state;
		state = new State();
		ListView<VirtualResource> v = getListView();

		if (v != null) {
			st.parent = v.getParentItem();
			st.children = v.getItems();
		} else {
			st.parent = null;
			st.children = Collections.emptyList();
		}

		return st;
	}

	public void restoreState(Object state) {
		State st = (State) state;
		this.state = st;
		ListView<VirtualResource> v = getListView();
		if (v != null) v.setItems(st.parent, st.children);
	}

	private static final class State {
		Consumer<? super VirtualResource> consumer;
		FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>> supplier;
		byte mode = FILE_OR_FOLDER;
		Pattern pattern;
		CreateFolder create;
		Function<VirtualResource, Boolean> onLongClick;
		VirtualResource parent;
		List<? extends VirtualResource> children;
	}

	public interface CreateFolder {

		@DrawableRes
		int getIcon();

		boolean isAvailable(VirtualResource parent, List<? extends VirtualResource> children);

		FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>>
		create(VirtualResource parent, List<? extends VirtualResource> children);

		default boolean isAvailable(FilePickerFragment p) {
			ListView<VirtualResource> v = p.getListView();
			return (v != null) && p.state.create.isAvailable(v.getParentItem(), v.getItems());
		}
	}

	public interface ToolBarMediator extends GenericDialogFragment.ToolBarMediator {
		ToolBarMediator instance = new ToolBarMediator() {
		};

		@Override
		default void enable(ToolBarView tb, ActivityFragment f) {
			FilePickerFragment p = (FilePickerFragment) f;

			EditText t = createPath(tb, p);
			t.setText(p.getPath());
			addView(tb, t, getPathId(), LEFT);

			GenericDialogFragment.ToolBarMediator.super.enable(tb, f);
		}

		@Override
		default void onActivityEvent(ToolBarView tb, ActivityDelegate a, long e) {
			if (e == FRAGMENT_CONTENT_CHANGED) {
				ActivityFragment f = a.getActiveFragment();
				if (!(f instanceof FilePickerFragment)) return;
				FilePickerFragment p = (FilePickerFragment) f;

				EditText t = tb.findViewById(getPathId());
				if (t == null) return;

				t.setText(p.getPath());
				GenericDialogFragment.ToolBarMediator.super.onActivityEvent(tb, a, e);
			}
		}

		default boolean onPathKeyEvent(FilePickerFragment f, EditText text, int keyCode, KeyEvent event) {
			if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

			switch (keyCode) {
				case KEYCODE_DPAD_CENTER:
				case KEYCODE_ENTER:
				case KEYCODE_NUMPAD_ENTER:
					f.setPath(text.getText().toString());
					return true;
				default:
					return UiUtils.dpadFocusHelper(text, keyCode, event);
			}
		}

		@IdRes
		default int getPathId() {
			return R.id.file_picker_path;
		}

		default EditText createPath(ToolBarView tb, FilePickerFragment f) {
			EditText t = createEditText(tb);
			ConstraintLayout.LayoutParams lp = setLayoutParams(t, 0, WRAP_CONTENT);
			t.setBackgroundResource(R.color.tool_bar_edittext_bg);
			t.setOnKeyListener((v, k, e) -> onPathKeyEvent(f, t, k, e));
			t.setMaxLines(1);
			t.setSingleLine(true);
			lp.horizontalWeight = 2;
			setPathPadding(t);
			return t;
		}

		default void setPathPadding(EditText t) {
			int p = (int) toPx(t.getContext(), 2);
			t.setPadding(p, p, p, p);
		}
	}

	public interface FloatingButtonMediator extends FloatingButton.Mediator.Back {
		FloatingButtonMediator instance = new FloatingButtonMediator() {
		};

		@Override
		default void onActivityEvent(FloatingButton fb, ActivityDelegate a, long e) {
			FloatingButton.Mediator.Back.super.onActivityEvent(fb, a, e);

			switch ((int) e) {
				case FRAGMENT_CHANGED:
				case FRAGMENT_CONTENT_CHANGED:
					fb.setImageResource(getIcon(fb));
					break;
			}
		}

		@Override
		default int getIcon(FloatingButton fb) {
			ActivityDelegate a = ActivityDelegate.get(fb.getContext());
			ActivityFragment f = a.getActiveFragment();

			if (f instanceof FilePickerFragment) {
				FilePickerFragment p = (FilePickerFragment) f;
				State st = p.state;
				if ((st.create != null) && st.create.isAvailable(p)) return st.create.getIcon();
			}

			return FloatingButton.Mediator.Back.super.getIcon(fb);
		}

		@Override
		default void onClick(View v) {
			ActivityDelegate a = ActivityDelegate.get(v.getContext());
			ActivityFragment f = a.getActiveFragment();

			if (f instanceof FilePickerFragment) {
				FilePickerFragment p = (FilePickerFragment) f;
				State st = p.state;

				if ((st.create != null) && st.create.isAvailable(p)) {
					State state = (State) p.resetState();
					state.create.create(st.parent, st.children).main().onCompletion((h, err) -> {
						if (h != null) {
							state.parent = h.getValue1();
							state.children = h.getValue2();
						}
						if ((err != null) && !(err instanceof CancellationException)) {
							ListView<VirtualResource> lv = p.getListView();
							if (lv != null) lv.onFailure(err);
						}
						p.restoreState(state);
						p.getActivityDelegate().showFragment(R.id.file_picker);
					});
					return;
				}
			}

			FloatingButton.Mediator.Back.super.onClick(v);
		}
	}
}
