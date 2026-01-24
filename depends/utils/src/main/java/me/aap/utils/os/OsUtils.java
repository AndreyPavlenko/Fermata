package me.aap.utils.os;

import static android.os.Build.VERSION.SDK_INT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Build.VERSION_CODES;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.CheckedRunnable;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;

/**
 * @author Andrey Pavlenko
 */
public class OsUtils {

	public static FutureSupplier<Integer> su(long timeout, Object... script) {
		String cmd;

		if (script.length == 1) {
			cmd = script[0].toString();
		} else {
			try (SharedTextBuilder b = SharedTextBuilder.get()) {
				for (Object o : script) b.append(o);
				cmd = b.toString();
			}
		}

		return su(timeout, new ByteArrayInputStream(cmd.getBytes(UTF_8)));
	}

	public static FutureSupplier<Integer> su(long timeout, InputStream script) {
		return exec(timeout, script, "su");
	}

	public static FutureSupplier<Integer> exec(long timeout, InputStream script, String... cmd) {
		return exec(timeout, script, Arrays.asList(cmd));
	}

	public static FutureSupplier<Integer> exec(long timeout, InputStream script, List<String> cmd) {
		return App.get().execute(() -> {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			Log.d("Executing command ", pb.command());
			Process p = pb.start();

			try {
				if (script != null) {
					try (OutputStream out = p.getOutputStream()) {
						byte[] buf = new byte[1024];
						for (int i = script.read(buf); i != -1; i = script.read(buf)) out.write(buf, 0, i);
					} catch (IOException ex) {
						Log.e(ex, "Failed to execute command ", pb.command());
						throw ex;
					}
				}

				if (timeout > 0) {
					if (SDK_INT >= VERSION_CODES.O) {
						if (!p.waitFor(timeout, MILLISECONDS)) {
							if (p.isAlive()) {
								p.destroy();
								if (p.isAlive()) p.destroyForcibly();
							}
						}
						return p.exitValue();
					} else {
						App.get().getScheduler().schedule(p::destroy, timeout, MILLISECONDS);
						return p.waitFor();
					}
				} else {
					return p.waitFor();
				}
			} finally {
				if (SDK_INT >= VERSION_CODES.O) {
					if (p.isAlive()) {
						p.destroy();
						if (p.isAlive()) p.destroyForcibly();
					}
				} else {
					p.destroy();
				}
				Log.d("Command ", pb.command(), " completed with exit code ", p.exitValue());
			}
		});
	}

	public static boolean isAndroid() {
		return Android.isAndroid;
	}

	public static boolean isCommandAvailable(String name) {
		String path = System.getenv("PATH");
		if (path == null) return false;
		for (String p : path.split(":")) {
			if (new File(p, name).canExecute()) return true;
		}
		return false;
	}

	public static boolean isSuAvailable() {
		return SuAvailable.available;
	}

	public static boolean isRmAvailable() {
		return RmAvailable.available;
	}

	public static void addShutdownHook(CheckedRunnable hook) {
		ShutdownHook.instance.hooks.add(hook);
	}

	private static final class Android {
		static final boolean isAndroid;

		static {
			boolean android;
			try {
				android = App.get() != null;
			} catch (Throwable ex) {
				android = false;
			}
			isAndroid = android;
		}
	}

	private static final class SuAvailable {
		static final boolean available = isCommandAvailable("su");
	}

	private static final class RmAvailable {
		static final boolean available = isCommandAvailable("rm");
	}

	private static final class ShutdownHook extends Thread {
		static final ShutdownHook instance = new ShutdownHook();
		final List<CheckedRunnable> hooks = new ArrayList<>(3);

		ShutdownHook() {
			super("ShutdownHook");
			Runtime.getRuntime().addShutdownHook(this);
		}

		@Override
		public void run() {
			for (CheckedRunnable h : hooks) {
				Log.d("Running shutdown hook: ", h);

				try {
					h.run();
				} catch (Throwable err) {
					Log.e(err, "Shutdown hook failed: ", h);
				}
			}
		}
	}
}
