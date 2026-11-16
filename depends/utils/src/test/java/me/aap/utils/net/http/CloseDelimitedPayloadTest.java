package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.utils.async.Completed.completedVoid;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.function.BiFunction;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.net.NetHandler;

/**
 * A response without Content-Length and Transfer-Encoding, whose payload ends when the server
 * closes the connection, as some IPTV servers send their playlists.
 */
public class CloseDelimitedPayloadTest extends Assertions {
	private static final String HEADER =
			"HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\n";
	private static ExecutorService exec;
	private static NetHandler handler;

	@BeforeAll
	public static void setUpClass() throws Exception {
		TestUtils.enableTestMode();
		exec = new NetThreadPool(2);
		handler = NetHandler.create(o -> o.executor = exec);
	}

	@AfterAll
	public static void tearDownClass() {
		handler.close();
		exec.shutdown();
	}

	@Test
	public void testGetPayload() throws Exception {
		String[] parts = {HEADER + "#EXTM3U\n", "#EXTINF:-1,One\nhttp://a/1\n", "#EXTINF:-1,Two\nhttp://a/2\n"};
		Promise<String> result = new Promise<>();

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = new Thread(() -> serve(server, parts));
			t.start();

			connect(server, (resp, err) -> {
				if (err != null) {
					result.completeExceptionally(err);
					return completedVoid();
				}
				return resp.getPayload((payload, fail) -> {
					if (fail != null) {
						result.completeExceptionally(fail);
					} else {
						byte[] b = new byte[payload.remaining()];
						payload.get(b);
						result.complete(new String(b, US_ASCII));
					}
					return completedVoid();
				});
			});

			assertEquals(String.join("", parts).substring(HEADER.length()),
					result.get(10, TimeUnit.SECONDS));
			t.join(5000);
		}
	}

	@Test
	public void testWritePayload() throws Exception {
		String[] parts = {HEADER + "#EXTM3U\n", "#EXTINF:-1,One\nhttp://a/1\n", "#EXTINF:-1,Two\nhttp://a/2\n"};
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Promise<String> result = new Promise<>();

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = new Thread(() -> serve(server, parts));
			t.start();

			connect(server, (resp, err) -> {
				if (err != null) {
					result.completeExceptionally(err);
					return completedVoid();
				}
				return resp.writePayload(out).onCompletion((v, fail) -> {
					if (fail != null) result.completeExceptionally(fail);
					else result.complete(out.toString(US_ASCII));
				});
			});

			assertEquals(String.join("", parts).substring(HEADER.length()),
					result.get(10, TimeUnit.SECONDS));
			t.join(5000);
		}
	}

	@Test
	public void testEmptyPayload() throws Exception {
		Promise<Integer> result = new Promise<>();

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = new Thread(() -> serve(server, new String[]{HEADER}));
			t.start();

			connect(server, (resp, err) -> {
				if (err != null) {
					result.completeExceptionally(err);
					return completedVoid();
				}
				return resp.getPayload((payload, fail) -> {
					if (fail != null) result.completeExceptionally(fail);
					else result.complete(payload.remaining());
					return completedVoid();
				});
			});

			assertEquals(0, result.get(10, TimeUnit.SECONDS));
			t.join(5000);
		}
	}

	private static void connect(ServerSocket server,
															BiFunction<HttpResponse, Throwable, FutureSupplier<?>> consumer) {
		HttpConnection.connect(o -> {
			o.handler = handler;
			o.url("http://localhost:" + server.getLocalPort() + "/playlist.m3u");
			o.readTimeout = 5;
		}, consumer);
	}

	// Sends each part as a separate write with a pause, then closes the connection, which ends the
	// payload.
	private static void serve(ServerSocket server, String[] parts) {
		try (Socket s = server.accept()) {
			InputStream in = s.getInputStream();
			byte[] buf = new byte[4096];
			StringBuilder req = new StringBuilder();
			while (req.indexOf("\r\n\r\n") < 0) {
				int n = in.read(buf);
				if (n < 0) return;
				req.append(new String(buf, 0, n, US_ASCII));
			}

			OutputStream out = s.getOutputStream();
			for (String p : parts) {
				out.write(p.getBytes(US_ASCII));
				out.flush();
				Thread.sleep(200);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
