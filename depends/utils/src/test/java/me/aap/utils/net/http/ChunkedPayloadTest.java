package me.aap.utils.net.http;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static me.aap.utils.async.Completed.completedVoid;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.net.NetHandler;

/**
 * A chunked payload whose chunk-size line is split across reads (here, the CRLF after a chunk's
 * data arrives in the next read), as happens with large chunked downloads such as IPTV playlists.
 */
public class ChunkedPayloadTest extends Assertions {
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
	public void testSplitChunkLine() throws Exception {
		String[] parts = {
				"HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n5\r\nhello\r",
				"\n6\r\n world\r\n1",
				"\r\n!\r\n0\r\n\r\n"
		};

		try (ServerSocket server = new ServerSocket(0)) {
			Thread t = new Thread(() -> serve(server, parts));
			t.start();
			Promise<String> result = new Promise<>();

			HttpConnection.connect(o -> {
				o.handler = handler;
				o.url("http://localhost:" + server.getLocalPort() + "/playlist.m3u");
				o.readTimeout = 5;
			}, (resp, err) -> {
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

			assertEquals("hello world!", result.get(10, TimeUnit.SECONDS));
			t.join(5000);
		}
	}

	// Sends each part as a separate write with a pause, so that each one arrives in its own read.
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
