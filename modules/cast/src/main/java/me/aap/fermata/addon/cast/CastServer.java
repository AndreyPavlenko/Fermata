package me.aap.fermata.addon.cast;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.net.http.HttpHeader.CONNECTION;
import static me.aap.utils.net.http.HttpHeader.CONTENT_TYPE;
import static me.aap.utils.security.SecurityUtils.md5String;

import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import me.aap.fermata.media.lib.MediaLib;
import me.aap.utils.app.App;
import me.aap.utils.app.NetApp;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.io.IoUtils;
import me.aap.utils.io.MemOutputStream;
import me.aap.utils.net.NetChannel;
import me.aap.utils.net.NetHandler;
import me.aap.utils.net.NetServer;
import me.aap.utils.net.NetUtils;
import me.aap.utils.net.http.HttpConnectionHandler;
import me.aap.utils.net.http.HttpError;
import me.aap.utils.net.http.HttpMethod;
import me.aap.utils.net.http.HttpRequest;
import me.aap.utils.net.http.HttpRequestHandler;
import me.aap.utils.net.http.HttpResponseBuilder;
import me.aap.utils.net.http.HttpVersion;
import me.aap.utils.resource.Rid;
import me.aap.utils.vfs.VfsHttpHandler;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
class CastServer implements HttpRequestHandler, HttpRequestHandler.Provider, Closeable {
	private static final String IMG_PATH = "/img";
	private static final String STREAM_PATH = "/stream";
	private final FutureRef<NetServer> netServer = FutureRef.create(this::create);
	private final MediaLib lib;
	private FutureSupplier<VirtualResource> stream = completedNull();
	@Nullable
	private Uri image;

	CastServer(MediaLib lib) {this.lib = lib;}

	public FutureSupplier<String[]> setContent(VirtualResource stream, @Nullable Uri image) {
		Rid rid = stream.getRid();
		Uri img = image;
		if ((rid.getScheme() == null) || (rid.getScheme().startsWith("http"))) rid = null;
		if ((img == null) || (img.getScheme() == null) || (img.getScheme().startsWith("http")))
			img = null;

		if ((rid == null) && (img == null)) {
			this.stream = completedNull();
			this.image = null;
			return completed(new String[]{stream.getRid().toString(),
					(image == null) ? null : image.toString()});
		}

		String streamUri = (rid == null) ? null : rid.toString();
		String imageUri = (img == null) ? null : img.toString();

		return netServer.get().map(server -> {
			InetSocketAddress sa = (InetSocketAddress) server.getBindAddress();
			String h = sa.getHostString();
			int p = sa.getPort();
			String a = "http://" + h + ':' + p;
			String s;
			String i;

			if (streamUri != null) {
				s = a + STREAM_PATH + "?id=" + md5String(streamUri);
				this.stream = completed(stream);
			} else {
				s = stream.getRid().toString();
				this.stream = completedNull();
			}
			if (imageUri != null) {
				i = a + IMG_PATH + "?id=" + md5String(imageUri);
				this.image = image;
			} else {
				i = (image == null) ? null : image.toString();
				this.image = null;
			}
			return new String[]{s, i};
		}).main();
	}

	@Override
	public void close() {
		netServer.get().onSuccess(IoUtils::close);
	}

	protected FutureSupplier<NetServer> create() {
		return App.get().execute(() -> {
			InetAddress addr = NetUtils.getInterfaceAddress();
			NetHandler handler = NetApp.get().getNetHandler();
			HttpConnectionHandler httpHandler = new HttpConnectionHandler();
			VfsHttpHandler vfsHandler = new VfsHttpHandler(new VfsManager() {
				@NonNull
				@Override
				public FutureSupplier<VirtualResource> getResource(Rid rid) {
					return stream;
				}
			});
			httpHandler.addHandler(IMG_PATH, this);
			httpHandler.addHandler(STREAM_PATH, (path, method, version) -> vfsHandler);
			return handler.bind(o -> {
				o.address =
						(addr == null) ? new InetSocketAddress("localhost", 0) : new InetSocketAddress(addr,
								0);
				o.handler = httpHandler;
			});
		}).then(bind -> bind);
	}

	@Override
	public FutureSupplier<?> handleRequest(HttpRequest req) {
		NetChannel channel = req.getChannel();
		if (image == null) return HttpError.NotFound.instance.write(channel);
		HttpVersion v = req.getVersion();
		boolean close = req.isConnectionClose();

		return lib.getBitmap(image.toString()).then(bm -> {
			MemOutputStream m = new MemOutputStream();
			bm.compress(Bitmap.CompressFormat.JPEG, 90, m);
			return channel.write(HttpResponseBuilder.supplier(b -> {
				b.setStatusOk(v);
				b.addHeader(CONTENT_TYPE, "image/jpeg");
				if (close) b.addHeader(CONNECTION);
				else if (v == HttpVersion.HTTP_1_0) b.addHeader(CONNECTION, "Keep-Alive");
				return b.build(ByteBuffer.wrap(m.getBuffer(), 0, m.getCount()));
			}));
		});
	}

	@Nullable
	@Override
	public HttpRequestHandler getHandler(CharSequence path, HttpMethod method, HttpVersion version) {
		return this;
	}
}
