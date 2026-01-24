package me.aap.utils.vfs.sftp;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.vfs.sftp.SftpFileSystem.SCHEME_SFTP;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.UserInfo;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import me.aap.utils.app.App;
import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.ObjectPool;
import me.aap.utils.async.ObjectPool.PooledObject;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VfsException;
import me.aap.utils.vfs.VirtualFileSystem;
import me.aap.utils.vfs.VirtualFolder;

/**
 * @author Andrey Pavlenko
 */
class SftpRoot extends SftpFolder {
	private final SessionPool pool;
	private final Rid rid;

	SftpRoot(@NonNull SessionPool pool, @NonNull String path) {
		//noinspection ConstantConditions
		super(null, path);
		this.pool = pool;
		int port = (pool.port == pool.fs.getDefaultPort()) ? -1 : pool.port;
		rid = Rid.create(SCHEME_SFTP, pool.user, pool.host, port, path);
	}

	@NonNull
	@Override
	protected SftpRoot getRoot() {
		return this;
	}

	@NonNull
	@Override
	public Rid getRid() {
		return rid;
	}

	@NonNull
	@Override
	public VirtualFileSystem getVirtualFileSystem() {
		return pool.fs;
	}

	@NonNull
	@Override
	public FutureSupplier<VirtualFolder> getParent() {
		return completedNull();
	}

	static SftpRoot create(
			@NonNull SftpFileSystem fs, @NonNull String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password,
			@Nullable String keyFile, @Nullable String keyPass) {
		SessionPool pool = new SessionPool(fs, user, host, port, password, keyFile, keyPass);
		return new SftpRoot(pool, requireNonNull(path));
	}

	static FutureSupplier<VirtualFolder> createConnected(
			@NonNull SftpFileSystem fs, @NonNull String user, @NonNull String host, int port,
			@Nullable String path, @Nullable String password,
			@Nullable String keyFile, @Nullable String keyPass) {
		SessionPool pool = new SessionPool(fs, user, host, port, password, keyFile, keyPass);

		return pool.getObject().closeableThen(session -> {
			try {
				ChannelSftp ch = session.get().channel;
				String p = path;
				if (p == null) p = ch.getHome();

				SftpATTRS a = ch.lstat(p);
				if (!a.isDir()) throw new VfsException("Path is not a directory: " + p);
				return completed(new SftpRoot(pool, p));
			} catch (Throwable ex) {
				pool.close();
				return failed(ex);
			}
		});
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		return getRid().equals(((SftpRoot) o).getRid());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getRid());
	}

	FutureSupplier<PooledObject<SftpSession>> getSession() {
		return pool.getObject().onSuccess(o -> o.get().startTimer());
	}

	<T> FutureSupplier<T> useChannel(CheckedFunction<ChannelSftp, T, Throwable> task) {
		return Async.retry(() -> getSession().then(ref -> App.get().execute(() -> {
			SftpSession session = null;
			try {
				session = ref.get();
				T result = task.apply(session.getChannel());
				session = null;
				return result;
			} finally {
				if (session != null) {
					try {
						session.session.sendKeepAliveMsg();
					} catch (Throwable ex) {
						session.close();
					}
				}

				ref.release();
			}
		})));
	}

	private static class SessionPool extends ObjectPool<SftpSession> {
		private static final Pref<IntSupplier> MAX_SESSIONS = Pref.i("SFTP_MAX_SESSIONS", 3);
		final SftpFileSystem fs;
		@NonNull
		final String user;
		@NonNull
		final String host;
		final int port;
		@Nullable
		private final String password;
		@Nullable
		private final String keyFile;
		@Nullable
		private final String keyPass;

		static {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
				Security.removeProvider("BC");
				Security.insertProviderAt(new BouncyCastleProvider(), 1);
			}
		}

		public SessionPool(SftpFileSystem fs, @NonNull String user, @NonNull String host,
											 int port, @Nullable String password, @Nullable String keyFile,
											 @Nullable String keyPass) {
			super(fs.getPreferenceStore().getIntPref(MAX_SESSIONS));
			this.fs = fs;
			this.user = user;
			this.host = host;
			this.port = port;
			this.password = password;
			this.keyFile = keyFile;
			this.keyPass = keyPass;
		}

		@Override
		protected PooledObject<SftpSession> newPooledObject(Object marker, SftpSession obj) {
			return new PooledObject<SftpSession>(this, marker, obj) {
				@Override
				public boolean release() {
					SftpSession s = get();
					if (s != null) s.stopTimer();
					return super.release();
				}
			};
		}

		@Override
		protected FutureSupplier<SftpSession> createObject() {
			return App.get().execute(this::createSession);
		}

		private SftpSession createSession() throws JSchException {
			Session s = null;
			SftpSession session = null;

			try {
				JSch jsch = new JSch();
				if (keyFile != null) jsch.addIdentity(keyFile, keyPass);

				s = jsch.getSession(user, host, port);
				s.setTimeout(15000);
				if (password != null) s.setPassword(password);

				s.setUserInfo(new UserInfo() {
					@Override
					public String getPassphrase() {
						return keyPass;
					}

					@Override
					public String getPassword() {
						return password;
					}

					@Override
					public boolean promptPassword(String message) {
						return true;
					}

					@Override
					public boolean promptPassphrase(String message) {
						return true;
					}

					@Override
					public boolean promptYesNo(String message) {
						return true;
					}

					@Override
					public void showMessage(String message) {
					}
				});

				s.connect();
				ChannelSftp ch = (ChannelSftp) s.openChannel("sftp");
				ch.connect();
				return session = new SftpSession(s, ch);
			} finally {
				if ((session == null) && (s != null)) s.disconnect();
			}
		}

		@Override
		protected boolean validateObject(SftpSession session, boolean releasing) {
			try {
				return session.isValid();
			} catch (Throwable ex) {
				Log.d(ex, "Session is not valid");
				return false;
			}
		}

		@Override
		protected void destroyObject(SftpSession session) {
			session.close();
		}
	}

	static final class SftpSession implements AutoCloseable {
		private final Session session;
		private final ChannelSftp channel;
		private ScheduledFuture<?> timer;

		SftpSession(Session session, ChannelSftp channel) {
			this.session = session;
			this.channel = channel;
		}

		ChannelSftp getChannel() {
			return channel;
		}

		boolean isValid() {
			try {
				return channel.isConnected();
			} catch (Throwable ignore) {
				return false;
			}
		}

		@Override
		public void close() {
			try {
				stopTimer();
				session.disconnect();
			} catch (Throwable ignore) {
			}
			try {
				channel.disconnect();
			} catch (Throwable ignore) {
			}
		}

		void startTimer() {
			App app = App.get();
			if (app == null) return;
			timer = app.getScheduler().schedule(() -> {
				Log.w("Closing SFTP channel due to timeout");
				close();
			}, 5, SECONDS);
		}

		void stopTimer() {
			ScheduledFuture<?> t = timer;
			timer = null;
			if (t != null) t.cancel(false);
		}

		void restartTimer() {
			stopTimer();
			startTimer();
		}
	}
}
