package me.aap.utils.vfs.smb;

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

import static me.aap.utils.async.Completed.completed;

/**
 * @author Andrey Pavlenko
 */
public class SmbFileSystem extends NetFileSystemBase {
	public static final String SCHEME_SMB = "smb";
	private static final Pref<Supplier<String[]>> SFTP_ROOTS = Pref.sa("SMB_ROOTS", () -> new String[0]);

	private SmbFileSystem(Provider provider, PreferenceStore ps) {
		super(provider, ps);
	}

	@Override
	protected VirtualFolder createRoot(
			@Nullable String user, @NonNull String host, int port, @Nullable String path,
			@Nullable String password, @Nullable String keyFile, @Nullable String keyPass) {
		return SmbRoot.create(this, user, host, port, path, password);
	}

	@Override
	protected FutureSupplier<VirtualFolder> createConnectedRoot(
			@Nullable String user, @NonNull String host, int port, @Nullable String path,
			@Nullable String password, @Nullable String keyFile, @Nullable String keyPass) {
		return SmbRoot.createConnected(this, user, host, port, path, password);
	}

	@Override
	protected FutureSupplier<VirtualResource> createResource(VirtualResource root, String path) {
		SmbRoot r = (SmbRoot) root;
		String smbPath = smbPath(path, true);

		return r.useShare(s -> {
			if (s == null) return null;
			if (s.getFileInformation(smbPath).getStandardInformation().isDirectory()) {
				return new SmbFolder(r, path);
			} else {
				return (VirtualResource) new SmbFile(r, path);
			}
		}).ifFail(fail -> null);
	}

	@Override
	protected VirtualFile createFile(VirtualResource root, String path) {
		return new SmbFile((SmbRoot) root, path);
	}

	@Override
	protected VirtualFolder createFolder(VirtualResource root, String path) {
		return new SmbFolder((SmbRoot) root, path);
	}

	@Override
	protected int getDefaultPort() {
		return 445;
	}

	@Override
	protected Pref<Supplier<String[]>> getRootsPref() {
		return SFTP_ROOTS;
	}

	public static class Provider implements VirtualFileSystem.Provider {
		private final Set<String> schemes = Collections.singleton(SCHEME_SMB);
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
			return completed(new SmbFileSystem(this, ps));
		}
	}

	static String smbPath(String p, boolean skipRoot) {
		if (p.length() == 0) return "";

		int i = (p.charAt(0) == '/') ? 1 : 0;

		if (skipRoot) {
			i = p.indexOf('/', i);
			if ((i == -1) || (i == p.length() - 1)) return "";
			i++;
		}

		return p.substring(i).replace('/', '\\');
	}
}
