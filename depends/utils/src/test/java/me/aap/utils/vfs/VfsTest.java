package me.aap.utils.vfs;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import me.aap.utils.io.FileUtils;

/**
 * @author Andrey Pavlenko
 */
public abstract class VfsTest extends Assert {

	@Rule
	public TemporaryFolder tmpDir = new TemporaryFolder();

	@Test
	public void test() throws Exception {
		File root = tmpDir.getRoot();
		VirtualFileSystem fs = vfsCreate(root, "root1", "root2", "root3");
		List<VirtualFolder> roots = fs.getRoots().get();
		VirtualFolder folder1 = roots.get(0).createFolder("folder1").get();
		VirtualFolder folder2 = roots.get(1).createFolder("folder2").get();
		VirtualFolder folder3 = roots.get(2).createFolder("folder3").get();
		VirtualFile file1 = folder1.createFile("file1").get();
		VirtualFile file2 = folder2.createFile("file2").get();
		VirtualFile file3 = folder3.createFile("file3").get();
		Random rnd = new Random();
		byte[] content = new byte[1024 + rnd.nextInt(8192)];
		rnd.nextBytes(content);

		assertTrue(folder1.exists().get());
		assertTrue(folder2.exists().get());
		assertTrue(folder3.exists().get());
		assertTrue(folder1.getChild("file1").get().getLocalFile().isFile());
		assertTrue(folder2.getChild("file2").get().getLocalFile().isFile());
		assertTrue(folder3.getChild("file3").get().getLocalFile().isFile());
		assertFalse(folder1.create().get());
		assertFalse(folder2.create().get());
		assertFalse(folder3.create().get());

		assertTrue(file1.exists().get());
		assertTrue(file2.exists().get());
		assertTrue(file3.exists().get());
		assertFalse(file1.create().get());
		assertFalse(file2.create().get());
		assertFalse(file3.create().get());

		try (OutputStream out = file1.getOutputStream().asOutputStream()) {
			out.write(content);
		}

		ByteBuffer fileContent = FileUtils.readBytes(new File(root, "root1/folder1/file1"));
		assertTrue(Arrays.equals(content, fileContent.array()));

		file1.copyTo(file2).get();
		fileContent = FileUtils.readBytes(new File(root, "root2/folder2/file2"));
		assertTrue(Arrays.equals(content, fileContent.array()));

		assertTrue(file1.moveTo(file3).get());
		fileContent = FileUtils.readBytes(new File(root, "root3/folder3/file3"));
		assertTrue(Arrays.equals(content, fileContent.array()));
		assertFalse(file1.exists().get());
	}

	protected abstract VirtualFileSystem vfsCreate(File rootDir, String... roots);
}
