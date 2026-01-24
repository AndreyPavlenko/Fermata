package me.aap.utils.net;

import static android.content.Context.CONNECTIVITY_SERVICE;
import static android.content.Context.WIFI_SERVICE;
import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static java.net.NetworkInterface.getNetworkInterfaces;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.annotation.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;

import me.aap.utils.app.App;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class NetUtils {

	@Nullable
	public static InetAddress getInterfaceAddress() {
		InetAddress result = null;
		Context ctx = App.get().getApplicationContext();
		ConnectivityManager cmgr = (ConnectivityManager) ctx.getSystemService(CONNECTIVITY_SERVICE);

		if (cmgr != null) {
			Network net = cmgr.getActiveNetwork();
			if (net != null) {
				NetworkInfo inf = cmgr.getNetworkInfo(net);
				if (inf != null) {
					if (!inf.isConnected()) return null;
					switch (inf.getType()) {
						case TYPE_WIFI:
							result = getWiFiAddr(ctx);
							break;
						case TYPE_ETHERNET:
							result = getEthAddr();
					}
				}
			}
		}

		if (result != null) return result;
		InetAddress ethResult = null;

		try {
			main:
			for (Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces(); ifs.hasMoreElements(); ) {
				NetworkInterface i = ifs.nextElement();
				if (i.isLoopback() || i.isVirtual() || !i.isUp()) continue;
				boolean isEth = i.getName().startsWith("eth");

				for (Enumeration<InetAddress> addrs = i.getInetAddresses(); addrs.hasMoreElements(); ) {
					InetAddress addr = addrs.nextElement();

					if (isEth) {
						if (addr instanceof Inet6Address) {
							if (ethResult == null) ethResult = addr;
						} else {
							ethResult = addr;
							break main;
						}
					} else {
						if (addr instanceof Inet6Address) {
							if (result == null) result = addr;
						} else {
							result = addr;
						}
					}
				}
			}
		} catch (Exception ex) {
			Log.d(ex);
		}

		return (ethResult != null) ? ethResult : result;
	}

	private static InetAddress getWiFiAddr(Context ctx) {
		WifiManager wmgr = (WifiManager) ctx.getSystemService(WIFI_SERVICE);

		if ((wmgr != null) && wmgr.isWifiEnabled()) {
			WifiInfo info = wmgr.getConnectionInfo();

			if (info.getNetworkId() != -1) {
				int ip = info.getIpAddress();

				if (ip != 0) {
					try {
						ByteBuffer buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
						return InetAddress.getByAddress(null, buf.putInt(ip).array());
					} catch (UnknownHostException ex) {
						Log.e(ex, "Failed to create InetAddress from int=", ip);
					}
				}
			}
		}

		return null;
	}

	private static InetAddress getEthAddr() {
		InetAddress ip6 = null;

		try {
			for (Enumeration<NetworkInterface> ifs = getNetworkInterfaces(); ifs.hasMoreElements(); ) {
				NetworkInterface i = ifs.nextElement();

				if (i.isLoopback() || i.isVirtual() || !i.isUp() || !i.getName().startsWith("eth")) {
					continue;
				}

				for (Enumeration<InetAddress> addrs = i.getInetAddresses(); addrs.hasMoreElements(); ) {
					InetAddress addr = addrs.nextElement();
					if (addr instanceof Inet6Address) ip6 = addr;
					else return addr;
				}
			}
		} catch (Exception ex) {
			Log.d(ex);
		}

		return ip6;
	}

	public static StringBuilder decodeUrl(CharSequence encoded, StringBuilder sb, boolean fail) {
		int i = 0;
		int len = encoded.length();

		loop:
		for (; i < len; i++) {
			char c = encoded.charAt(i);

			if ((c >= '0') && (c <= '9') || ((c >= 'a') && (c <= 'z')) || (c >= 'A') && (c <= 'Z')) {
				sb.append(c);
			} else if (c == '+') {
				sb.append(' ');
			} else if (c == '%') {
				if (i < len - 2) {
// TODO: implement
				} else {
					break loop;
				}
			} else {
				switch (c) {
					case '*':
					case '-':
					case '.':
					case '_':
						sb.append(' ');
						break;
					default:
						break loop;
				}
			}
		}

		if (i != len) {
			if (fail) throw new IllegalArgumentException("Invalid encoded URL: " + encoded);
			sb.append(encoded, i, len);
		}

		return sb;
	}
}
