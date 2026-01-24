package me.aap.utils.net.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.aap.utils.async.Async;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.concurrent.NetThreadPool;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.io.RandomAccessChannel;
import me.aap.utils.log.Log;
import me.aap.utils.misc.TestUtils;
import me.aap.utils.net.NetChannel;
import me.aap.utils.net.NetHandler;
import me.aap.utils.net.NetServer;
import me.aap.utils.security.SecurityUtils;
import me.aap.utils.vfs.VfsHttpHandler;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.local.LocalFileSystem;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.FutureSupplier.noOp;
import static me.aap.utils.io.IoUtils.emptyByteBufferArray;
import static me.aap.utils.net.http.HttpHeader.ACCEPT_ENCODING;
import static me.aap.utils.net.http.HttpHeader.ACCEPT_RANGES;
import static me.aap.utils.net.http.HttpHeader.CONNECTION;
import static me.aap.utils.net.http.HttpHeader.CONTENT_TYPE;
import static me.aap.utils.net.http.HttpHeader.ETAG;
import static me.aap.utils.net.http.HttpHeader.LOCATION;

/**
 * @author Andrey Pavlenko
 */
public class HttpHandlerTest extends Assertions {
	private static ExecutorService exec;
	private static NetHandler handler;
	private static byte[] REQ;
	private static byte[] RESP;
	private static final String RESP_FORMAT = "HTTP/1.1 200 Ok\n" +
			"Query: %s\n" +
			"Content-Length: 10\n\n" +
			"0123456789";
	private static final byte[] CLOSE_RESP = ("HTTP/1.1 200 Ok\n" +
			"Connection: Close\n\n").getBytes(US_ASCII);

	@BeforeAll
	public static void setUpClass() throws IOException {
		TestUtils.enableTestMode();
		exec = new NetThreadPool(Runtime.getRuntime().availableProcessors() - 1);
		handler = NetHandler.create(o -> o.executor = exec);

		int nreq = 1000;
		String req = "GET /test?q=%04d HTTP/1.1\n" +
				"Host: localhost:8080\n" +
				"User-Agent: curl/7.65.3\n" +
				"Accept: */*\r\n\r\n";
		byte[] closeReq = ("GET /test HTTP/1.1\n" +
				"Host: localhost:8080\n" +
				"Connection: Close\r\n\r\n").getBytes(US_ASCII);
		int reqLen = req.length();
		int resLen = RESP_FORMAT.length() + 4;

		REQ = new byte[reqLen * nreq + closeReq.length];
		RESP = new byte[resLen * nreq + CLOSE_RESP.length];


		for (int i = 0; i < nreq; i++) {
			int n = i + 1;
			String r = String.format(req, n);
			System.arraycopy(r.getBytes(US_ASCII), 0, REQ, i * reqLen, reqLen);
			r = String.format(RESP_FORMAT, String.format("q=%04d", n));
			System.arraycopy(r.getBytes(US_ASCII), 0, RESP, i * resLen, resLen);
		}

		System.arraycopy(closeReq, 0, REQ, nreq * reqLen, closeReq.length);
		System.arraycopy(CLOSE_RESP, 0, RESP, nreq * resLen, CLOSE_RESP.length);
	}

	@AfterAll
	public static void tearDownClass() {
		handler.close();
		exec.shutdown();
		REQ = null;
		RESP = null;
	}

	//	@RepeatedTest(10)
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void testIncomplete(boolean useSsl) throws Exception {
		int nreq = 1000;
		int nclients = 100;
		Map<NetChannel, Integer> requests = new ConcurrentHashMap<>(nreq);
		AtomicBoolean failed = new AtomicBoolean();
		HttpConnectionHandler http = new HttpConnectionHandler();
		HttpRequestHandler reqHandler = (req) -> {
			NetChannel ch = req.getChannel();

			if (req.isConnectionClose()) {
				Log.i("Close req received - sending close resp");
				ch.write(() -> ByteBuffer.wrap(CLOSE_RESP)).thenRun(ch::close);
			} else {
				try {
					String q = requireNonNull(req.getQuery()).toString();
					int n = Integer.parseInt(q.substring(2));
					Integer prev = requests.put(ch, n);
					if (n == 1) assertNull(prev);
					else assertEquals(n - 1, prev);
					byte[] resp = String.format(RESP_FORMAT, req.getQuery()).getBytes(US_ASCII);
					ch.write(() -> ByteBuffer.wrap(resp));
				} catch (Throwable ex) {
					ex.printStackTrace();
					fail(ex);
				}
			}

			return completedVoid();
		};

		http.addHandler("/test", (p, m, v) -> reqHandler);

		NetServer server = handler.bind(o -> {
			o.handler = http;
			o.backlog = nclients * 10;
			o.ssl = useSsl;
			o.opt.put(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
		}).get();

		SocketAddress addr = server.getBindAddress();
		FutureSupplier<?>[] tasks = new FutureSupplier[nclients];

		for (int i = 0; i < nclients; i++) {
			int n = i + 1;

			tasks[i] = handler.connect(o -> {
				o.address = addr;
				o.ssl = useSsl;
				o.opt.put(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
			}).then(ch -> {
				for (int off = 0, len = 10; off < REQ.length; off += len) {
					int o = off;
					int l = ((off + len) < REQ.length) ? len : (REQ.length - off);
					ch.write(() -> ByteBuffer.wrap(REQ, o, l)).onFailure(err -> {
						err.printStackTrace(System.out);
						System.exit(1);
					});
				}

				ByteBuffer resp = ByteBuffer.allocate(RESP.length);

				return Async.iterate(() -> {
					if (!resp.hasRemaining()) return null;

					return ch.read((bb, err) -> {
						resp.put(bb);

						if (!resp.hasRemaining()) {
							assertEquals(new String(RESP), new String(resp.array()), () -> "n = " + n);
							Log.i("Close resp received - closing channel");
							ch.close();
						}
					});
				});
			}).onFailure(fail -> {
				fail.printStackTrace(System.out);
				System.exit(1);
			});
		}

		for (FutureSupplier<?> t : tasks) {
			t.get();
		}

		assertFalse(failed.get());
		server.close();
	}

	//	@RepeatedTest(10)
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	public void test(boolean useSsl) throws Exception {
		int nreq = 10000;
		int nclients = 100;
		Map<NetChannel, Integer> requests = new ConcurrentHashMap<>(nreq);
		AtomicBoolean failed = new AtomicBoolean();
		HttpConnectionHandler http = new HttpConnectionHandler();
		HttpRequestHandler reqHandler = (req) -> {
			NetChannel ch = req.getChannel();
			if (req.isConnectionClose()) {
//				Log.i("Close req received - sending close resp");
				req.getChannel().write(() -> ByteBuffer.wrap(CLOSE_RESP)).thenRun(ch::close);
			} else {
				try {
					String q = requireNonNull(req.getQuery()).toString();
					int n = Integer.parseInt(q.substring(2));
					Integer prev = requests.put(ch, n);
					if (n == 1) assertNull(prev);
					else assertEquals(n - 1, prev);
					byte[] resp = String.format(RESP_FORMAT, req.getQuery()).getBytes(US_ASCII);
					ch.write(() -> ByteBuffer.wrap(resp));
				} catch (Throwable ex) {
					fail(ex);
				}
			}

			return completedVoid();
		};

		http.addHandler("/test", (p, m, v) -> reqHandler);

		NetServer server = handler.bind(o -> {
			o.handler = http;
			o.backlog = nclients;
			o.ssl = useSsl;
		}).get();
		SocketAddress addr = server.getBindAddress();
		FutureSupplier<?>[] tasks = new FutureSupplier[nclients];

		for (int i = 0; i < nclients; i++) {
			int n = i + 1;
			tasks[i] = handler.connect(o -> {
				o.address = addr;
				o.ssl = useSsl;
			}).then(ch -> {
				ch.write(() -> ByteBuffer.wrap(REQ));
				ByteBuffer resp = ByteBuffer.allocate(RESP.length);

				return Async.iterate(() -> {
					if (!resp.hasRemaining()) return null;

					return ch.read((bb, err) -> {
						resp.put(bb);

						if (!resp.hasRemaining()) {
							assertEquals(new String(RESP), new String(resp.array()), () -> "n = " + n);
//							Log.i("Close resp received - closing channel");
							ch.close();
						}
					});
				});
			}).onFailure(fail -> {
				fail.printStackTrace();
				failed.set(true);
			});
		}

		for (FutureSupplier<?> t : tasks) {
			t.get();
		}

		server.close();
		assertFalse(failed.get());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"no-ssl,getPayload", "ssl,getPayload",
			"no-ssl,writePayload", "ssl,writePayload",
			"no-ssl,readPayload", "ssl,readPayload",
			"no-ssl,skipPayload", "ssl,skipPayload"})
	public void testHttpConnection(String mode) throws Exception {
		String[] mod = mode.split(",");
		boolean useSsl = mod[0].equals("ssl");
		boolean skipPayload = mod[1].equals("skipPayload");
		boolean readPayload = mod[1].equals("readPayload");
		boolean writePayload = mod[1].equals("writePayload");
		int nreq = 100;
		int nclients = 10;
		AtomicInteger counter = new AtomicInteger(nreq * nclients);
		Promise<Void> done = new Promise<>();

		Random rnd = new Random();
		byte[] payloadBytes = new byte[255999];
//		byte[] payloadBytes = new byte[1024];
//		byte[] payloadBytes = new byte[rnd.nextInt(1024 * 1024)];
		rnd.nextBytes(payloadBytes);
		String etag = "W/\"" + SecurityUtils.sha1String(payloadBytes) + "\"";
		HttpConnectionHandler http = new HttpConnectionHandler();
		HttpRequestHandler reqHandler = (req) -> {
//			System.out.println("Request received:\n" + req);
			req.getPath();
			long len = req.getContentLength();
			NetChannel channel = req.getChannel();
			HttpVersion version = req.getVersion();
			boolean close = req.isConnectionClose();

			return req.getPayload((payload, fail) -> {
				if (fail != null) {
					fail.printStackTrace();
					System.exit(1);
				}

				assertEquals(len, payload.remaining());
				byte[] pl = new byte[payload.remaining()];
				payload.duplicate().get(pl);
				assertArrayEquals(payloadBytes, pl);

				try {
//					ByteBuffer resp = b.buildWithPayload(os -> os.write(payload.array(), payload.arrayOffset(), payload.remaining()));
					FutureSupplier<Void> w = channel.write(HttpResponseBuilder.supplier(b -> {
						b.setStatusOk(version);
						b.addHeader(ACCEPT_RANGES);
						b.addHeader(LOCATION, "/test2");
						b.addHeader(CONTENT_TYPE, "text/plain; charset=utf-8");
						b.addHeader(ETAG, etag);
						if (close) b.addHeader(CONNECTION);
						return b.build(ByteBuffer.wrap(pl));
					}));

					if (close) {
						w.thenRun(channel::close);
						return FutureSupplier.noOp();
					} else {
						return w;
					}
				} catch (Exception ex) {
					fail(ex);
					return noOp();
				}
			});
		};

		String uri = "/test";
		http.addHandler(uri, (p, m, v) -> reqHandler);
		NetServer server = handler.bind(o -> {
			o.handler = http;
			o.backlog = nreq;
			o.ssl = useSsl;
			o.opt.put(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
		}).get();
		SocketAddress addr = server.getBindAddress();

		for (int n = 0; n < nclients; n++) {
			handler.connect(o -> {
				o.address = addr;
				o.ssl = useSsl;
				o.opt.put(StandardSocketOptions.TCP_NODELAY, Boolean.TRUE);
			}).onCompletion((ch, connectFail) -> {
				if (connectFail != null) {
					done.completeExceptionally(connectFail);
					return;
				}

				HttpConnection c = new HttpConnection(ch);

				for (int i = 0; i < nreq; i++) {
					boolean close = i == (nreq - 1);
					c.sendRequest(b -> {
								try {
									b.setRequest(uri, HttpMethod.POST);
									b.addHeader(ACCEPT_ENCODING);
									if (close) b.addHeader(CONNECTION);
//									ByteBuffer[] req = b.build(os -> os.write(payloadBytes));
//									return req;
									return b.build(ByteBuffer.wrap(payloadBytes));
								} catch (Exception ex) {
									done.completeExceptionally(ex);
									return emptyByteBufferArray();
								}
							}
							, (resp, err) -> {
								if (err != null) {
									done.completeExceptionally(err);
									return done;
								}

//							System.out.println(counter + "). Response received:\n" + resp);
								assertEquals(HttpStatusCode.OK, resp.getStatusCode());
								assertEquals("OK", resp.getReason().toString());
								assertEquals("/test2", resp.getLocation().toString());
								assertEquals("text/plain; charset=utf-8", resp.getContentType().toString());
								assertEquals(etag, resp.getEtag().toString());
								long len = resp.getContentLength();

								if (writePayload) {
									MemOutputStream out = new MemOutputStream(payloadBytes.length);

									return resp.writePayload(out).onCompletion((v, fail) -> {
										if (fail != null) done.completeExceptionally(fail);
										else if (out.getCount() != payloadBytes.length)
											done.completeExceptionally(new Exception("Invalid len: " + out.getCount()));
										else if (counter.decrementAndGet() == 0) done.complete(null);
									});
								}

								if (readPayload) {
									return new MemOutputStream(payloadBytes.length).readFrom(resp.readPayload()).
											onCompletion((out, fail) -> {
												if (fail != null) done.completeExceptionally(fail);
												else if (out.getCount() != payloadBytes.length)
													done.completeExceptionally(new Exception("Invalid len: " + out.getCount()));
												else if (counter.decrementAndGet() == 0) done.complete(null);
											});
								}

								if (skipPayload) {
									return resp.skipPayload().then(v -> {
										if (counter.decrementAndGet() == 0) done.complete(null);
										return completedVoid();
									});
								}

								return resp.getPayload((payload, fail) -> {
									if (fail != null) {
										done.completeExceptionally(fail);
										return done;
									}

//								System.out.println(counter + "). Payload received: " + payload.remaining());
									assertEquals(len, payload.remaining());
									byte[] pl = new byte[payload.remaining()];
									payload.duplicate().get(pl);
									assertArrayEquals(payloadBytes, pl);
//									assertEquals(ByteBuffer.wrap(payloadBytes), payload);

									if (counter.decrementAndGet() == 0) done.complete(null);
									return completedVoid();
								});
							});
				}
			});
		}

		done.get();
		server.close();
	}

	//	@Disabled
	@ParameterizedTest
	@ValueSource(strings = {
			"no-ssl,getPayload", "ssl,getPayload",
			"no-ssl,writePayload", "ssl,writePayload",
			"no-ssl,readPayload", "ssl,readPayload",
			"no-ssl,skipPayload", "ssl,skipPayload"})
	public void testHttpConnection2(String mode) throws Exception {
		String[] mod = mode.split(",");
		boolean useSsl = mod[0].equals("ssl");
		boolean skipPayload = mod[1].equals("skipPayload");
		boolean readPayload = mod[1].equals("readPayload");
		boolean writePayload = mod[1].equals("writePayload");
		int nreq = 100;
		AtomicInteger counter = new AtomicInteger(nreq);
		Promise<Void> done = new Promise<>();
		int payloadLen = 1679461;
		int compressedPayloadLen = 213685;
//		String uri = "/tmp.html";
		String uri = "/tmp_redirect.html";
//		String uri = "/permanent_redirect.html";

		for (int i = 0; i < nreq; i++) {
			HttpConnection.connect(o -> {
				o.handler = handler;
				o.url((useSsl ? "https" : "http") + "://localhost" + uri);
				o.writeTimeout = 1;
				o.readTimeout = 5;
			}, (resp, err) -> {
				if (err != null) {
					done.completeExceptionally(err);
					return completedVoid();
				}

				if (skipPayload) {
					return resp.skipPayload().onCompletion((v, fail) -> {
						if (fail != null) done.completeExceptionally(fail);
						if (counter.decrementAndGet() == 0) done.complete(null);
					});
				}

				if (writePayload) {
					MemOutputStream out = new MemOutputStream(compressedPayloadLen);

					return resp.writePayload(out).onCompletion((v, fail) -> {
						if (fail != null) done.completeExceptionally(fail);
						else if (out.getCount() != compressedPayloadLen)
							done.completeExceptionally(new Exception("Invalid len: " + out.getCount()));
						else if (counter.decrementAndGet() == 0) done.complete(null);
					});
				}

				if (readPayload) {
					return new MemOutputStream().readFrom(resp.readPayload()).
							onCompletion((out, fail) -> {
								if (fail != null) done.completeExceptionally(fail);
								else if (out.getCount() != compressedPayloadLen)
									done.completeExceptionally(new Exception("Invalid len: " + out.getCount()));
								else if (counter.decrementAndGet() == 0) done.complete(null);
							});
				}

//				Log.i("Response:\n" + resp);
				return resp.getPayload((payload, fail) -> {
					if (fail != null) {
						done.completeExceptionally(fail);
						return done;
					}

					assertEquals(payloadLen, payload.remaining());
					int cnt = counter.decrementAndGet();
//					byte[] bb = new byte[payload.remaining()];
//					payload.get(bb);
//					Log.i("Payload:\n" + new String(bb));
//					Log.i(cnt + "). Payload received: " + payload.remaining() + ", Close: " + resp.isConnectionClose());
					if (cnt == 0) done.complete(null);
					return completedVoid();
				});
			});
		}

		done.get();
		assertEquals(0, counter.get());
	}

	@Test
	@Disabled
	public void httpServer() throws Exception {
		File index = new File("/var/www/html/index.nginx-debian.html");
//		File index = new File("/var/www/html/tmp.html");
//		File index = new File("/var/www/html/data");
		long len = index.length();
		byte[] h = ("HTTP/1.1 200 OK\r\n" +
				"Server: nginx/1.17.10 (Ubuntu)\r\n" +
				"Date: Tue, 05 May 2020 17:08:57 GMT\r\n" +
				"Content-Type: text/html\r\n" +
				"Content-Length: " + len + "\r\n" +
				"Last-Modified: Tue, 24 Nov 2015 00:22:32 GMT\r\n" +
				"Connection: keep-alive\r\n" +
				"ETag: \"5653adc8-264\"\r\n" +
				"Accept-Ranges: bytes\r\n\r\n").getBytes(US_ASCII);
		FileChannel fc = new FileInputStream(index).getChannel();
		RandomAccessChannel rc = RandomAccessChannel.wrap(fc);
		ByteBuffer mapped = fc.map(FileChannel.MapMode.READ_ONLY, 0, len);
		ByteBuffer header = ByteBuffer.allocateDirect(h.length);
		ByteBuffer body = ByteBuffer.allocateDirect(mapped.remaining());
		header.put(h);
		header.position(0);
		body.put(mapped.duplicate());
		body.position(0);
		HttpConnectionHandler http = new HttpConnectionHandler();


//		http.addHandler("/", (path, method, version) -> (req) -> {
//					req.getRange();
//					FutureSupplier<Void> w = req.getChannel().write(() -> new ByteBuffer[]{header.duplicate(), body.duplicate()});
//					if (req.isConnectionClose()) {
//						w.thenRun(req.getChannel()::close);
//						return noOp();
//					} else {
//						return w;
//					}
//				}
//		);


//		http.addHandler("/", (path, method, version) -> (req) -> {
//					FutureSupplier<Void> w = req.getChannel().send(rc, 0, rc.size(), () -> new ByteBuffer[]{header.duplicate()});
//					if (req.isConnectionClose()) {
//						w.thenRun(req.getChannel()::close);
//						return noOp();
//					} else {
//						return w;
//					}
//				}
//		);


		http.addHandler("/", (path, method, version) -> (req) -> {
			NetChannel channel = req.getChannel();
			HttpVersion v = req.getVersion();
			boolean close = req.isConnectionClose();

			FutureSupplier<Void> w = channel.write(HttpResponseBuilder.supplier(b -> {
				b.setStatusOk(v);
				b.addHeader(ACCEPT_RANGES);
				if (close) b.addHeader(CONNECTION);
				else if (v == HttpVersion.HTTP_1_0) b.addHeader(CONNECTION, "Keep-Alive");
				return b.build(body.duplicate());
			}));

			if (close) {
				w.thenRun(channel::close);
				return FutureSupplier.noOp();
			}

			return w;
		});

		handler.bind(o -> {
			o.port = 8080;
			o.handler = http;
			o.backlog = 10000;
//			o.ssl = true;
			o.opt.put(StandardSocketOptions.SO_SNDBUF, 65536);
			o.opt.put(StandardSocketOptions.SO_RCVBUF, 65536);
//			o.readTimeout = 1;
//			o.writeTimeout = 1;
		}).get();

		Thread.sleep(600000);
	}

	@Test
	@Disabled
	public void httpServer2() throws Exception {
		String etag = "W/\"183040eab789e2d9b6dfad496ce4e106bddb6607\"";
		HttpConnectionHandler http = new HttpConnectionHandler();
		HttpRequestHandler reqHandler = (req) -> {
			req.getPath();
			long len = req.getContentLength();
			HttpVersion v = req.getVersion();
			NetChannel channel = req.getChannel();
			HttpVersion version = req.getVersion();
			boolean close = req.isConnectionClose();

			return req.getPayload((payload, fail) -> {
				if (fail != null) {
					fail.printStackTrace(System.out);
					System.exit(1);
				}

				assertEquals(len, payload.remaining());

				try {
					FutureSupplier<Void> w = channel.write(HttpResponseBuilder.supplier(b -> {
						b.setStatusOk(version);
						b.addHeader(ACCEPT_RANGES);
						b.addHeader(LOCATION, "/test2");
						b.addHeader(CONTENT_TYPE, "text/plain; charset=utf-8");
						b.addHeader(ETAG, etag);
						if (close) b.addHeader(CONNECTION);
						else if (v == HttpVersion.HTTP_1_0) b.addHeader(CONNECTION, "Keep-Alive");
						return b.build(payload);
					}));

					if (close) {
						w.thenRun(channel::close);
						return FutureSupplier.noOp();
					} else {
						return w;
					}
				} catch (Exception ex) {
					ex.printStackTrace(System.out);
					System.exit(1);
					return noOp();
				}
			});
		};

		http.addHandler("/data", (p, m, v) -> reqHandler);
		handler.bind(o -> {
			o.port = 8080;
			o.handler = http;
			o.backlog = 1000;
			o.ssl = true;
		}).get();
		Thread.sleep(1000000000);
	}

	@Test
	@Disabled
	public void vfsHttpServer() throws Exception {
		VfsManager mgr = new VfsManager(LocalFileSystem.getInstance());
		VfsHttpHandler vfsHandler = new VfsHttpHandler(mgr);
		HttpConnectionHandler http = new HttpConnectionHandler();
		http.addHandler(VfsHttpHandler.HTTP_PATH, (path, method, version) -> vfsHandler);

		handler.bind(o -> {
//			o.ssl = true;
			o.port = 8080;
			o.handler = http;
			o.backlog = 10000;
			o.opt.put(StandardSocketOptions.SO_SNDBUF, 65536);
			o.opt.put(StandardSocketOptions.SO_RCVBUF, 65536);
		}).get();

		Thread.sleep(600000);
	}
}
