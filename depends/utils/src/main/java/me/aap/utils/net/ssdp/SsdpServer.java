package me.aap.utils.net.ssdp;


import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import me.aap.utils.app.App;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Function;
import me.aap.utils.log.Log;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Andrey Pavlenko
 */
public class SsdpServer implements Closeable {
	public static final String DEVICE_MEDIASERVER = "MediaServer";
	private final String serverName;
	private final String deviceName;
	private final String uuid;
	private final ScheduledExecutorService scheduler;
	private final Function<URI, URI> location;
	private final BooleanSupplier isSuspended;
	private volatile MulticastSocket socket;
	private OkContent ok;
	private NotifyContent notify;

	public static class Opts {
		public Function<URI, URI> location;
		@Nullable
		public String serverName;
		@Nullable
		public String deviceName;
		@Nullable
		public String uuid;
		@Nullable
		public ScheduledExecutorService scheduler;
		@Nullable
		public BooleanSupplier isSuspended;

		private void init() {
			if (serverName == null) serverName = "SsdpServer";
			if (deviceName == null) deviceName = DEVICE_MEDIASERVER;
			if (uuid == null) uuid = UUID.fromString(serverName + location.apply(null)).toString();
			if (scheduler == null) scheduler = App.get().getScheduler();
			if (isSuspended == null) isSuspended = () -> false;
		}
	}

	public SsdpServer(Consumer<Opts> builder) {
		Opts o = new Opts();
		builder.accept(o);
		o.init();
		this.serverName = o.serverName + " UPnP/1.0";
		this.deviceName = o.deviceName;
		this.uuid = o.uuid;
		this.scheduler = o.scheduler;
		this.location = o.location;
		this.isSuspended = o.isSuspended;
	}

	public SsdpServer(String serverName, String deviceName, String uuid,
										ScheduledExecutorService scheduler, Function<URI, URI> location,
										BooleanSupplier isSuspended) {
		this.serverName = serverName + " UPnP/1.0";
		this.deviceName = deviceName;
		this.uuid = uuid;
		this.scheduler = scheduler;
		this.location = location;
		this.isSuspended = isSuspended;
	}

	public synchronized void start() throws IOException {
		if (socket != null) return;
		final MulticastSocket socket = new MulticastSocket(1900);
		socket.joinGroup(InetAddress.getByName("239.255.255.250"));
		this.socket = socket;

		new Thread() {
			@Override
			public void run() {
				byte[] data = new byte[1024];
				Log.d("SSDP Server started");

				Future<?> notif = scheduler.scheduleWithFixedDelay(SsdpServer.this::sendNotify, 0, 1, MINUTES);

				while (!socket.isClosed()) {
					try {
						DatagramPacket pkt = new DatagramPacket(data, data.length);
						socket.receive(pkt);
						handle(socket, pkt);
					} catch (SocketException ignore) { // Socket closed?
					} catch (Throwable ex) {
						Log.e(ex, "SSDP Server failure");
					}
				}

				notif.cancel(false);
				sendByebye();
				Log.d("SSDP Server stopped");
			}
		}.start();
	}

	public synchronized void stop() {
		if (socket == null) return;
		socket.close();
		socket = null;
	}

	public void sendNotify() {
		if (isSuspended.getAsBoolean()) return;
		NotifyContent cnt = notify;

		if ((cnt == null) || !cnt.isValid()) {
			if (cnt != null) sendByebye();
			cnt = notify = new NotifyContent();
		}

		sendMsg(cnt.content);
	}

	private void sendByebye() {
		String str = "NOTIFY * HTTP/1.1\r\n" +
				"Host: 239.255.255.250:1900\r\n" +
				"NTS: ssdp:byebye\r\n" +
				"USN: uuid:%1$s::urn:schemas-upnp-org:device:%2$s:1\r\n" +
				"NT: urn:schemas-upnp-org:device:%2$s:1\r\n\r\n";
		byte[] data = String.format(str, uuid, deviceName).getBytes(US_ASCII);
		sendMsg(data);
	}

	private void sendMsg(byte[] data) {
		try {
			if (isDebugEnabled()) {
				Log.d("Sending SSDP message:\n", new String(data, US_ASCII));
			}

			final DatagramSocket socket = new DatagramSocket();
			DatagramPacket pkt = new DatagramPacket(data, data.length,
					InetAddress.getByName("239.255.255.250"), 1900);
			socket.send(pkt);
		} catch (IOException ex) {
			Log.e(ex, "Failed to send SSDP message");
		}
	}

	@Override
	public void close() {
		stop();
	}

	public boolean isRunning() {
		MulticastSocket s = socket;
		return (s != null) && !s.isClosed();
	}

	private void handle(MulticastSocket socket, DatagramPacket pkt) {
		if (isSuspended.getAsBoolean()) return;
		OkContent cnt = ok;

		if ((cnt == null) || !cnt.isValid()) {
			cnt = ok = new OkContent();
		}

		if (isDebugEnabled()) {
			String req = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), US_ASCII);
			String resp = new String(cnt.content, US_ASCII);
			Log.d("SSDP request received:\n", req);
			Log.d("Sending SSDP response:\n", resp);
		}

		try {
			DatagramPacket resp = new DatagramPacket(cnt.content, cnt.content.length, pkt.getAddress(),
					pkt.getPort());
			socket.send(resp);
		} catch (IOException ex) {
			Log.e(ex, "Failed to send SSDP response");
		}
	}

	private boolean isDebugEnabled() {
		return false;
	}

	private abstract class Content {
		final URI uri;
		final byte[] content;

		Content() {
			uri = location.apply(null);
			content = createContent(uuid, uri.toString());
		}

		abstract byte[] createContent(String uuid, String location);

		boolean isValid() {
			return uri.equals(location.apply(uri));
		}
	}

	private final class OkContent extends Content {

		@Override
		byte[] createContent(String uuid, String location) {
			String okStr = "HTTP/1.1 200 OK\r\n" +
					"Cache-Control: max-age=1800\r\n" +
					"Ext:\r\n" +
					"Location: %1$s\r\n" +
					"Server: %2$s\r\n" +
					"ST: urn:schemas-upnp-org:device:%3$s:1\r\n" +
					"USN: uuid:%s::urn:schemas-upnp-org:device:%3$s:1\r\n\r\n";
			return String.format(okStr, location, serverName, uuid, deviceName).getBytes(US_ASCII);
		}
	}

	private final class NotifyContent extends Content {

		@Override
		byte[] createContent(String uuid, String location) {
			String notifyStr = "NOTIFY * HTTP/1.1\r\n" +
					"Host: 239.255.255.250:1900\r\n" +
					"Cache-Control: max-age=1800\r\n" +
					"Location: %1$s\r\n" +
					"Server: %2$s\r\n" +
					"NTS: ssdp:alive\r\n" +
					"USN: uuid:%s::urn:schemas-upnp-org:device:%3$s:1\r\n" +
					"NT: urn:schemas-upnp-org:device:%3$s:1\r\n\r\n";
			return String.format(notifyStr, location, serverName, uuid, deviceName).getBytes(US_ASCII);
		}
	}
}

