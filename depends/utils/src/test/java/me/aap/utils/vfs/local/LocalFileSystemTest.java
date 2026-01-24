package me.aap.utils.vfs.local;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import me.aap.utils.vfs.VfsTest;
import me.aap.utils.vfs.VirtualFileSystem;

/**
 * @author Andrey Pavlenko
 */
public class LocalFileSystemTest extends VfsTest {
	@Override
	protected VirtualFileSystem vfsCreate(File rootDir, String... roots) {
		return new LocalFileSystem(() -> {
			List<File> list = new ArrayList<>(roots.length);
			for (String r : roots) {
				list.add(new File(rootDir, r));
			}
			return list;
		});
	}
}
