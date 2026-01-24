package me.aap.utils.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static me.aap.utils.text.TextUtils.toByteArray;
import static me.aap.utils.text.TextUtils.toHexString;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import me.aap.utils.app.App;

/**
 * @author Andrey Pavlenko
 */
@SuppressWarnings("unused")
public class SecurityUtils {
	public static final int SHA1_DIGEST_LEN = 20;

	public static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static byte[] sha256(byte... bytes) {
		return digest(sha256Digest(), bytes);
	}

	public static String sha256String(byte... bytes) {
		return digestString(sha256Digest(), bytes);
	}

	public static byte[] sha256(ByteBuffer bytes) {
		return digest(sha256Digest(), bytes);
	}

	public static String sha256String(ByteBuffer bytes) {
		return digestString(sha256Digest(), bytes);
	}

	public static byte[] sha256(CharSequence... text) {
		return sha256(UTF_8, text);
	}

	public static String sha256String(CharSequence... text) {
		return sha256String(UTF_8, text);
	}

	public static byte[] sha256(Charset charset, CharSequence... text) {
		return digest(sha256Digest(), charset, text);
	}

	public static String sha256String(Charset charset, CharSequence... text) {
		return digestString(sha256Digest(), charset, text);
	}

	public static byte[] sha256(File f) throws IOException {
		return digest(sha256Digest(), f);
	}

	public static String sha256String(File f) throws IOException {
		return digestString(sha256Digest(), f);
	}

	public static byte[] sha256(FileChannel c) throws IOException {
		return digest(sha256Digest(), c);
	}

	public static String sha256String(FileChannel c) throws IOException {
		return digestString(sha256Digest(), c);
	}

	public static MessageDigest sha1Digest() {
		try {
			return MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static byte[] sha1(byte... bytes) {
		return digest(sha1Digest(), bytes);
	}

	public static String sha1String(byte... bytes) {
		return digestString(sha1Digest(), bytes);
	}

	public static byte[] sha1(ByteBuffer bytes) {
		return digest(sha1Digest(), bytes);
	}

	public static String sha1String(ByteBuffer bytes) {
		return digestString(sha1Digest(), bytes);
	}

	public static byte[] sha1(CharSequence... text) {
		return sha1(UTF_8, text);
	}

	public static String sha1String(CharSequence... text) {
		return sha1String(UTF_8, text);
	}

	public static boolean isSha1String(String s) {
		if (s.length() != 40) return false;

		for (int i = 0; i < 40; i++) {
			char c = s.charAt(i);
			if ((c >= '0') && (c <= '9')) continue;
			if ((c >= 'a') && (c <= 'f')) continue;
			if ((c >= 'A') && (c <= 'F')) continue;
			return false;
		}

		return true;
	}

	public static byte[] sha1(Charset charset, CharSequence... text) {
		return digest(sha1Digest(), charset, text);
	}

	public static String sha1String(Charset charset, CharSequence... text) {
		return digestString(sha1Digest(), charset, text);
	}

	public static byte[] sha1(File f) throws IOException {
		return digest(sha1Digest(), f);
	}

	public static String sha1String(File f) throws IOException {
		return digestString(sha1Digest(), f);
	}

	public static byte[] sha1(FileChannel c) throws IOException {
		return digest(sha1Digest(), c);
	}

	public static String sha1String(FileChannel c) throws IOException {
		return digestString(sha1Digest(), c);
	}

	public static MessageDigest md5Digest() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static byte[] md5(byte... bytes) {
		return digest(md5Digest(), bytes);
	}

	public static String md5String(byte... bytes) {
		return digestString(md5Digest(), bytes);
	}

	public static byte[] md5(ByteBuffer bytes) {
		return digest(md5Digest(), bytes);
	}

	public static String md5String(ByteBuffer bytes) {
		return digestString(md5Digest(), bytes);
	}

	public static byte[] md5(CharSequence... text) {
		return md5(UTF_8, text);
	}

	public static String md5String(CharSequence... text) {
		return md5String(UTF_8, text);
	}

	public static byte[] md5(Charset charset, final CharSequence... text) {
		return digest(md5Digest(), charset, text);
	}

	public static String md5String(Charset charset, final CharSequence... text) {
		return digestString(md5Digest(), charset, text);
	}

	public static byte[] md5(File f) throws IOException {
		return digest(md5Digest(), f);
	}

	public static String md5String(File f) throws IOException {
		return digestString(md5Digest(), f);
	}

	public static byte[] digest(MessageDigest md, byte... bytes) {
		md.update(bytes);
		return md.digest();
	}

	public static String digestString(MessageDigest md, byte... bytes) {
		return toHexString(digest(md, bytes));
	}

	public static byte[] digest(MessageDigest md, ByteBuffer bytes) {
		md.update(bytes);
		return md.digest();
	}

	public static String digestString(MessageDigest md, ByteBuffer bytes) {
		return toHexString(digest(md, bytes));
	}

	public static byte[] digest(MessageDigest md, CharSequence... text) {
		return digest(md, UTF_8, text);
	}

	public static String digestString(MessageDigest md, CharSequence... text) {
		return digestString(md, UTF_8, text);
	}

	public static byte[] digest(MessageDigest md, Charset charset, CharSequence... text) {
		for (CharSequence s : text) {
			md.update(toByteArray(s, charset));
		}

		return md.digest();
	}

	public static String digestString(MessageDigest md, Charset charset, CharSequence... text) {
		return toHexString(digest(md, charset, text));
	}

	public static byte[] digest(MessageDigest md, File f) throws IOException {
		long len = f.length();

		if (len > 0) {
			try (FileInputStream in = new FileInputStream(f)) {
				byte[] buf = new byte[(int) Math.min(8192, len)];

				for (int i = in.read(buf); i != -1; i = in.read(buf)) {
					md.update(buf, 0, i);
				}
			} catch (IOException ex) {
				throw new IOException(ex);
			}
		}

		return md.digest();
	}

	public static String digestString(MessageDigest md, File f) throws IOException {
		return toHexString(digest(md, f));
	}

	public static byte[] digest(MessageDigest md, FileChannel c) throws IOException {
		long size = c.size();

		if (size > 0) {
			ByteBuffer buf = ByteBuffer.allocate((int) Math.min(8192, size));
			long pos = 0;

			for (int i = c.read(buf, pos); i != -1; i = c.read(buf, pos)) {
				buf.flip();
				md.update(buf);
				buf.clear();
				pos += i;
			}
		}

		return md.digest();
	}

	public static String digestString(MessageDigest md, FileChannel c) throws IOException {
		return toHexString(digest(md, c));
	}

	public static String digestString(MessageDigest md) {
		return toHexString(md.digest());
	}

	public static SSLEngine createClientSslEngine(String peerHost, int peerPort) {
		try {
			SSLEngine eng = ClientContextHolder.context.createSSLEngine(peerHost, peerPort);
			eng.setUseClientMode(true);
			return eng;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public static SSLEngine createServerSslEngine() {
		try {
			SSLEngine eng = ServerContextHolder.context.createSSLEngine();
			eng.setUseClientMode(false);
			return eng;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static final class ClientContextHolder {
		static final SSLContext context = create();

		static SSLContext create() {
			SSLContext ctx;

			try {
				ctx = SSLContext.getInstance("TLS");
				ctx.init(null, new TrustManager[]{InsecureTrustManager.instance}, null);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}

			return ctx;
		}
	}

	// Used for testing
	private static final class ServerContextHolder {
		static final SSLContext context = create();

		static SSLContext create() {
			try {
				KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
				KeyManagerFactory kmf =
						KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				String ksPath = System.getProperty("javax.net.ssl.keyStore");

				if (ksPath != null) {
					String pwd = System.getProperty("javax.net.ssl.keyStorePassword", "");
					try (InputStream in = new FileInputStream(ksPath)) {
						ks.load(in, pwd.toCharArray());
					}
					kmf.init(ks, pwd.toCharArray());
				} else {
					KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
					g.initialize(2048);
					KeyPair kp = g.generateKeyPair();
					Certificate[] chain = new Certificate[1];
					chain[0] = generateSelfSignedCertificate(kp);
					ks.load(null, null);
					ks.setKeyEntry("alias", kp.getPrivate(), null, chain);
					kmf.init(ks, null);
				}

				SSLContext ctx = SSLContext.getInstance("TLS");
				ctx.init(kmf.getKeyManagers(), new TrustManager[]{InsecureTrustManager.instance}, null);
				return ctx;
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private static X509Certificate generateSelfSignedCertificate(KeyPair kp)
			throws GeneralSecurityException, OperatorCreationException {
		App app = App.get();
		X500Name name = new X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN,
				(app == null) ? "localhost" : app.getPackageName()).build();
		long now = System.currentTimeMillis();
		X509v3CertificateBuilder cb = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now),
				new Date(now - (1000 * 60 * 60 * 24)), new Date(now + (1000L * 60 * 60 * 24 * 365)), name,
				kp.getPublic());
		ContentSigner cs = new JcaContentSignerBuilder("SHA256WithRSA").build(kp.getPrivate());
		return new JcaX509CertificateConverter().getCertificate(cb.build(cs));
	}

	private static final class InsecureTrustManager implements X509TrustManager {
		static final InsecureTrustManager instance = new InsecureTrustManager();
		private static final X509Certificate[] CERTIFICATES = {};

		@Override
		public void checkClientTrusted(X509Certificate[] chain, String s) {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] chain, String s) {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return CERTIFICATES;
		}
	}
}

