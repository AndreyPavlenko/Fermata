package me.aap.fermata.auto;

import static me.aap.utils.async.Completed.completed;

import android.os.Build;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import me.aap.fermata.FermataApplication;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;

public class Su {
	private static volatile FutureSupplier<Su> su;
	private final ExecutorService executor;
	private final PrintWriter pw;

	private Su(ExecutorService executor, PrintWriter pw) {
		this.executor = executor;
		this.pw = pw;
	}

	public static FutureSupplier<Su> get() {
		var f = su;
		if (f != null) return f;
		synchronized (Su.class) {
			if ((f = su) != null) return f;
			var executor = Executors.newFixedThreadPool(2);
			var promise = new Promise<PrintWriter>();
			su = f = promise.map(pw -> new Su(executor, pw)).onSuccess(su -> Su.su = completed(su))
					.onFailure(err -> {
						Log.e(err);
						executor.shutdown();
						Su.su = null;
					});
			executor.execute(() -> {
				try {
					int timeout = 15000;
					var pb = new ProcessBuilder("su", "-c", "ls");
					var p = pb.start();
					int ec;

					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
						if (p.waitFor(timeout, TimeUnit.MILLISECONDS)) ec = p.exitValue();
						else ec = 1;
					} else {
						FermataApplication.get().getHandler().postDelayed(p::destroy, timeout);
						ec = p.waitFor();
					}
					if (ec == 0) {
						promise.complete(new PrintWriter(new ProcessBuilder("su").start().getOutputStream()));
					} else {
						promise.completeExceptionally(new IOException("su not found"));
						p.destroy();
					}
				} catch (Exception err) {
					promise.completeExceptionally(err);
				}
			});
			return f;
		}
	}

	public void exec(String cmd) {
		executor.execute(() -> {
			try {
				Log.d("executing: ", cmd);
				pw.println(cmd);
				pw.flush();
			} catch (Exception err) {
				Log.e(err, "Failed to execute command as root: ", cmd);
			}
		});
	}
}
