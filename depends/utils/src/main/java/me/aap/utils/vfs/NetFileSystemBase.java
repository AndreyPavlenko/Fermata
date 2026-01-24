package me.aap.utils.vfs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.text.TextBuilder;
import me.aap.utils.text.TextUtils;

import static java.nio.charset.StandardCharsets.UTF_16BE;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.text.TextUtils.appendHexString;
import static me.aap.utils.text.TextUtils.hexToBytes;

/**
 * @author Andrey Pavlenko
 */
public abstract class NetFileSystemBase implements VirtualFileSystem {
	private final Provider provider;
	private final PreferenceStore ps;
	private volatile List<VirtualFolder> roots;

	protected NetFileSystemBase(Provider provider, PreferenceStore ps) {
		this.provider = provider;
		this.ps = ps;

		String[] pref = ps.getStringArrayPref(getRootsPref());

		if (pref.length == 0) {
			roots = Collections.emptyList();
			return;
		}

		ArrayList<VirtualFolder> roots = new ArrayList<>(pref.length);

		for (String p : pref) {
			try {
				VirtualFolder r = createRoot(p);
				if (r == null) Log.e("Invalid resource id: ", p);
				else roots.add(r);
			} catch (Throwable ex) {
				Log.e("Invalid resource id: ", p);
			}
		}

		roots.trimToSize();
		this.roots = !roots.isEmpty() ? roots : Collections.emptyList();
	}

	protected VirtualFolder createRoot(String rootId) {
		Rid rid = Rid.create(rootId);
		String scheme = rid.getScheme();
		String user = rid.getUserInfo();
		String host = rid.getHost();
		int port = rid.getPort();
		if (port == -1) port = getDefaultPort();
		String path = rid.getPath();
		String password;
		String keyFile;
		String keyPass;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			appendRid(scheme, tb, user, host, port);
			tb.append('#');
			int len = tb.length();

			tb.append('P');
			password = ps.getStringPref(Pref.s(tb.toString(), () -> null));
			tb.setLength(len);
			tb.append('F');
			keyFile = ps.getStringPref(Pref.s(tb.toString(), () -> null));
			tb.setLength(len);
			tb.append('K');
			keyPass = ps.getStringPref(Pref.s(tb.toString(), () -> null));
		}

		return createRoot(user, host, port, path, decrypt(password), keyFile, decrypt(keyPass));
	}

	protected abstract VirtualFolder createRoot(
			@Nullable String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password,
			@Nullable String keyFile, @Nullable String keyPass);

	protected abstract FutureSupplier<VirtualFolder> createConnectedRoot(
			@Nullable String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password,
			@Nullable String keyFile, @Nullable String keyPass);

	protected abstract FutureSupplier<VirtualResource> createResource(VirtualResource root, String path);

	protected abstract VirtualFile createFile(VirtualResource root, String path);

	protected abstract VirtualFolder createFolder(VirtualResource root, String path);

	protected abstract int getDefaultPort();

	protected abstract Pref<Supplier<String[]>> getRootsPref();

	@NonNull
	@Override
	public Provider getProvider() {
		return provider;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualResource> getResource(Rid rid) {
		int[] cmp = new int[1];
		String path = rid.getPath();
		VirtualResource root = findRoot(rid, path, cmp);
		if (root == null) return completedNull();
		return (cmp[0] == 0) ? completed(root) : createResource(root, path);
	}

	@Override
	public FutureSupplier<VirtualFile> getFile(Rid rid) {
		int[] cmp = new int[1];
		String path = rid.getPath();
		VirtualResource root = findRoot(rid, path, cmp);
		if (root == null) return completedNull();
		return (cmp[0] == 0) ? completedNull() : completed(createFile(root, path));
	}

	@Override
	public FutureSupplier<VirtualFolder> getFolder(Rid rid) {
		int[] cmp = new int[1];
		String path = rid.getPath();
		VirtualResource root = findRoot(rid, path, cmp);
		if (root == null) return completedNull();
		return (cmp[0] == 0) ? completed((VirtualFolder) root) : completed(createFolder(root, path));
	}

	private VirtualResource findRoot(Rid rid, String path, int[] match) {
		if (!isSupported(rid)) return null;

		String user = rid.getUserInfo();
		String host = rid.getHost();
		int port = rid.getPort();
		if (port == -1) port = getDefaultPort();

		for (VirtualResource root : roots) {
			int cmp = compareRoot(root, user, host, port, path);
			if (cmp == -1) continue;
			match[0] = cmp;
			return root;
		}

		return null;
	}

	protected boolean isSupported(Rid rid) {
		return getProvider().getSupportedSchemes().contains(rid.getScheme());
	}

	protected int compareRoot(VirtualResource root, CharSequence user, CharSequence host, int port,
														CharSequence path) {
		Rid rootId = root.getRid();

		if (!TextUtils.equals(user, rootId.getUserInfo())) return -1;
		if (!TextUtils.equals(host, rootId.getHost())) return -1;

		int rootPort = rootId.getPort();
		if (rootPort == -1) rootPort = getDefaultPort();
		if (port != rootPort) return -1;

		CharSequence rootPath = rootId.getPath();
		if (!TextUtils.startsWith(path, rootPath)) return -1;

		int pathLen = path.length();
		int rootPathLen = rootPath.length();
		if (pathLen == rootPathLen) return 0;
		return (path.charAt(rootPathLen) == '/') ? 1 : -1;
	}

	@NonNull
	@Override
	public FutureSupplier<List<VirtualFolder>> getRoots() {
		return completed(roots);
	}

	public FutureSupplier<VirtualFolder> addRoot(@Nullable String user, @NonNull String host, int port,
																							 @Nullable String path, @Nullable String password,
																							 @Nullable String keyFile, @Nullable String keyPass) {
		return createConnectedRoot(user, host, port, path, password, keyFile, keyPass).then(root -> {
			Rid rid = root.getRid();

			synchronized (this) {
				List<VirtualFolder> roots = this.roots;

				for (VirtualFolder r : roots) {
					if (rid.equals(r.getRid())) return completed(r);
				}

				List<VirtualFolder> newRoots = new ArrayList<>(roots.size() + 1);
				newRoots.addAll(roots);
				newRoots.add(root);
				setRoots(newRoots);
				saveCredentials(rid.getScheme(), user, host, port, password, keyFile, keyPass);
				return completed(root);
			}
		});
	}

	public synchronized boolean removeRoot(VirtualFolder root) {
		List<VirtualFolder> roots = this.roots;
		if (roots.isEmpty()) return false;

		boolean removed = false;
		List<VirtualFolder> newRoots = new ArrayList<>(roots.size() - 1);
		Rid rid = root.getRid();

		for (VirtualFolder r : roots) {
			if (r.getRid().equals(rid)) removed = true;
			else newRoots.add(r);
		}

		if (!removed) return false;

		String scheme = rid.getScheme();
		String user = rid.getUserInfo();
		String host = rid.getHost();
		int port = rid.getPort();
		if (port == -1) port = getDefaultPort();
		Pref<Supplier<String>> password;
		Pref<Supplier<String>> keyFile;
		Pref<Supplier<String>> keyPass;

		try (SharedTextBuilder tb = SharedTextBuilder.get()) {
			appendRid(scheme, tb, user, host, port);
			tb.append('#');
			int len = tb.length();

			tb.append('P');
			password = Pref.s(tb.toString(), () -> null);
			tb.setLength(len);
			tb.append('F');
			keyFile = Pref.s(tb.toString(), () -> null);
			tb.setLength(len);
			tb.append('K');
			keyPass = Pref.s(tb.toString(), () -> null);
		}

		try (PreferenceStore.Edit e = getPreferenceStore().editPreferenceStore()) {
			e.removePref(password);
			e.removePref(keyFile);
			e.removePref(keyPass);
		}

		setRoots(newRoots.isEmpty() ? Collections.emptyList() : newRoots);
		return true;
	}

	private void setRoots(List<VirtualFolder> roots) {
		this.roots = roots;
		String[] pref = new String[roots.size()];

		for (int i = 0, s = roots.size(); i < s; i++) {
			pref[i] = roots.get(i).getRid().toString();
		}

		getPreferenceStore().applyStringArrayPref(getRootsPref(), pref);
	}

	public PreferenceStore getPreferenceStore() {
		return ps;
	}

	protected void saveCredentials(@NonNull String scheme, @Nullable String user, @NonNull String host,
																 int port, @Nullable String password, @Nullable String keyFile,
																 @Nullable String keyPass) {
		try (SharedTextBuilder tb = SharedTextBuilder.get();
				 PreferenceStore.Edit e = getPreferenceStore().editPreferenceStore()) {
			appendRid(scheme, tb, user, host, port);
			tb.append('#');
			int len = tb.length();

			if (password != null) {
				encrypt(password, tb);
				String pwd = tb.substring(len);
				tb.setLength(len);
				tb.append('P');
				e.setStringPref(Pref.s(tb.toString(), () -> null), pwd);
			}

			if (keyFile != null) {
				tb.setLength(len);
				tb.append('F');
				e.setStringPref(Pref.s(tb.toString(), () -> null), keyFile);
			}

			if (keyPass != null) {
				tb.setLength(len);
				encrypt(keyPass, tb);
				String pwd = tb.substring(len);
				tb.setLength(len);
				tb.append('K');
				e.setStringPref(Pref.s(tb.toString(), () -> null), pwd);
			}
		}
	}

	private void appendRid(@NonNull CharSequence scheme, TextBuilder tb, @Nullable String user,
												 @NonNull String host, int port) {
		tb.append(scheme).append("://");
		if (user != null) tb.append(user).append('@');
		if (host.indexOf(':') != -1) tb.append('[').append(host).append(']');
		else tb.append(host);
		if (port != getDefaultPort()) tb.append(':').append(port);
	}

	// Weak encryption
	private static void encrypt(@NonNull String v, SharedTextBuilder tb) {
		appendHexString(tb, v.getBytes(UTF_16BE));
	}

	private static String decrypt(@Nullable String v) {
		if (v == null) return v;
		return new String(hexToBytes(v), UTF_16BE);
	}
}
