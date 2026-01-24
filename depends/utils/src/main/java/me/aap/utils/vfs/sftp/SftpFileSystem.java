package me.aap.utils.vfs.sftp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Set;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.vfs.NetFileSystemBase;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completed;

/**
 * @author Andrey Pavlenko
 */
public class SftpFileSystem extends NetFileSystemBase {
	public static final String SCHEME_SFTP = "sftp";
	private static final Pref<Supplier<String[]>> SFTP_ROOTS = Pref.sa("SFTP_ROOTS", () -> new String[0]);

	private SftpFileSystem(Provider provider, PreferenceStore ps) {
		super(provider, ps);
	}

	@Override
	protected VirtualFolder createRoot(
			@Nullable String user, @NonNull String host, int port, @Nullable String path,
			@Nullable String password, @Nullable String keyFile, @Nullable String keyPass) {
		return SftpRoot.create(this, requireNonNull(user), host, port, path, password, keyFile, keyPass);
	}

	@Override
	protected FutureSupplier<VirtualFolder> createConnectedRoot(
			@Nullable String user, @NonNull String host, int port, @Nullable String path,
			@Nullable String password, @Nullable String keyFile, @Nullable String keyPass) {
		return SftpRoot.createConnected(this, requireNonNull(user), host, port, path, password, keyFile, keyPass);
	}

	@Override
	protected FutureSupplier<VirtualResource> createResource(VirtualResource root, String path) {
		SftpRoot r = (SftpRoot) root;
		return r.lstat(path).ifFail(fail -> null).map(s -> {
			if (s == null) return null;
			if (s.isDir()) return new SftpFolder(r, path);
			else return new SftpFile(r, path);
		});
	}

	@Override
	protected VirtualFile createFile(VirtualResource root, String path) {
		return new SftpFile((SftpRoot) root, path);
	}

	@Override
	protected VirtualFolder createFolder(VirtualResource root, String path) {
		return new SftpFolder((SftpRoot) root, path);
	}

	@Override
	protected int getDefaultPort() {
		return 22;
	}

	@Override
	protected Pref<Supplier<String[]>> getRootsPref() {
		return SFTP_ROOTS;
	}

	public static class Provider implements VirtualFileSystem.Provider {
		private final Set<String> schemes = Collections.singleton(SCHEME_SFTP);
		private static final Provider instance = new Provider();

		private Provider() {
		}

		public static Provider getInstance() {
			return instance;
		}

		@NonNull
		@Override
		public Set<String> getSupportedSchemes() {
			return schemes;
		}

		@NonNull
		@Override
		public FutureSupplier<VirtualFileSystem> createFileSystem(PreferenceStore ps) {
			return completed(new SftpFileSystem(this, ps));
		}
	}
}
