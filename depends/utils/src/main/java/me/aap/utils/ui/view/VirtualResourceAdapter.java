package me.aap.utils.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

import me.aap.utils.R;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.holder.BiHolder;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

import static me.aap.utils.async.Completed.completedEmptyList;
import static me.aap.utils.async.Completed.completedNull;

/**
 * @author Andrey Pavlenko
 */
public class VirtualResourceAdapter implements ListView.ItemAdapter<VirtualResource> {
	@Nullable
	private final Drawable fileIcon;
	@Nullable
	private final Drawable folderIcon;

	public VirtualResourceAdapter(@Nullable Drawable fileIcon, @Nullable Drawable folderIcon) {
		this.fileIcon = fileIcon;
		this.folderIcon = folderIcon;
	}

	public VirtualResourceAdapter(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, R.attr.fileListViewStyle);
	}

	public VirtualResourceAdapter(Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
		TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.FileListView, defStyleAttr,
				R.style.Theme_Utils_Base_FileListViewStyle);
		fileIcon = ta.getDrawable(R.styleable.FileListView_fileIcon);
		folderIcon = ta.getDrawable(R.styleable.FileListView_folderIcon);
		ta.recycle();
	}

	@Nullable
	@Override
	public Drawable getIcon(VirtualResource item) {
		return item.isFile() ? fileIcon : folderIcon;
	}

	@NonNull
	@Override
	public CharSequence getText(VirtualResource item) {
		return item.getName();
	}

	@Override
	public boolean hasChildren(VirtualResource item) {
		return item.isFolder();
	}

	@Override
	public FutureSupplier<List<VirtualResource>> getChildren(VirtualResource item) {
		return (item instanceof VirtualFolder) ? ((VirtualFolder) item).getChildren() : completedEmptyList();
	}

	@Override
	public FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>> getParent(VirtualResource item) {
		return item.getParent().then(p -> {
			if (p == null) {
				return item.getVirtualFileSystem().getRoots().map(roots ->
						new BiHolder<>(null, roots.isEmpty() ? Collections.singletonList(item) : roots));
			} else {
				return p.getChildren().map(children -> new BiHolder<>(p, children));
			}
		});
	}

	@Override
	public FutureSupplier<BiHolder<? extends VirtualResource, List<? extends VirtualResource>>> findParent(VirtualResource current, String find) {
		return current.getVirtualFileSystem().getResource(Rid.create(find)).then(p -> {
			if (!(p instanceof VirtualFolder)) return completedNull();
			return ((VirtualFolder) p).getChildren().map(children -> new BiHolder<>(p, children));
		});
	}
}
