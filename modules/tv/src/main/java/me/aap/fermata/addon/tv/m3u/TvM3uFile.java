package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.io.FileUtils.getFileExtension;
import static me.aap.utils.net.http.HttpFileDownloader.CHARSET;
import static me.aap.utils.net.http.HttpFileDownloader.ENCODING;
import static me.aap.utils.net.http.HttpFileDownloader.ETAG;
import static me.aap.utils.net.http.HttpFileDownloader.MAX_AGE;
import static me.aap.utils.net.http.HttpFileDownloader.RESP_TIMEOUT;
import static me.aap.utils.net.http.HttpFileDownloader.TIMESTAMP;
import static me.aap.utils.text.TextUtils.trim;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.net.MalformedURLException;

import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.DoubleSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.net.http.HttpFileDownloader.Status;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.resource.Rid;
import me.aap.utils.ui.notif.HttpDownloadStatusListener;

/**
 * @author Andrey Pavlenko
 */
public class TvM3uFile extends M3uFile {
	public static final int EPG_FILE_AGE = M3U_FILE_AGE;
	public static final int CATCHUP_TYPE_AUTO = 0;
	public static final int CATCHUP_TYPE_APPEND = 1;
	public static final int CATCHUP_TYPE_DEFAULT = 2;
	public static final int CATCHUP_TYPE_SHIFT = 3;
	public static final int CATCHUP_TYPE_FLUSSONIC = 4;
	public static final Pref<Supplier<String>> EPG_URL = Pref.s("EPG_URL");
	public static final Pref<DoubleSupplier> EPG_SHIFT = Pref.f("EPG_SHIFT", 0);
	public static final Pref<Supplier<String>> CATCHUP_QUERY = Pref.s("CATCHUP_QUERY");
	public static final Pref<IntSupplier> CATCHUP_TYPE = Pref.i("CATCHUP_TYPE", CATCHUP_TYPE_AUTO);
	public static final Pref<IntSupplier> CATCHUP_DAYS = Pref.i("CATCHUP_DAYS", 0);
	public static final Pref<Supplier<String>> LOGO_URL = Pref.s("LOGO_URL");
	public static final Pref<BooleanSupplier> LOGO_PREFER_EPG = Pref.b("LOGO_PREFER_EPG", true);

	public TvM3uFile(Rid rid) {
		super(rid);
	}

	@NonNull
	@Override
	public TvM3uFileSystem getVirtualFileSystem() {
		return TvM3uFileSystem.getInstance();
	}

	public String getEpgUrl() {
		return getPrefs().getStringPref(EPG_URL);
	}

	public void setEpgUrl(String url) {
		getPrefs().applyStringPref(EPG_URL, trim(url));
	}

	public float getEpgShift() {
		return getPrefs().getFloatPref(EPG_SHIFT);
	}

	public void setEpgShift(float shift) {
		getPrefs().applyFloatPref(EPG_SHIFT, shift);
	}

	public String getCatchupQuery() {
		return getPrefs().getStringPref(CATCHUP_QUERY);
	}

	public void setCatchupQuery(String q) {
		getPrefs().applyStringPref(CATCHUP_QUERY, trim(q));
	}

	public int getCatchupType() {
		return getPrefs().getIntPref(CATCHUP_TYPE);
	}

	public void setCatchupType(int type) {
		getPrefs().applyIntPref(CATCHUP_TYPE, type);
	}

	public int getCatchupDays() {
		return getPrefs().getIntPref(CATCHUP_DAYS);
	}

	public void setCatchupDays(int days) {
		getPrefs().applyIntPref(CATCHUP_DAYS, days);
	}

	public String getLogoUrl() {
		return getPrefs().getStringPref(LOGO_URL);
	}

	public void setLogoUrl(String url) {
		getPrefs().applyStringPref(LOGO_URL, trim(url));
	}

	public boolean isPreferEpgLogo() {
		return getPrefs().getBooleanPref(LOGO_PREFER_EPG);
	}

	public void setPreferEpgLogo(boolean prefer) {
		getPrefs().applyBooleanPref(LOGO_PREFER_EPG, prefer);
	}

	public long getEpgTimeStamp() {
		return getPrefs().getLongPref(EpgPrefs.EPG_TIMESTAMP);
	}

	public void clearStamps() {
		try (PreferenceStore.Edit e = getPrefs().editPreferenceStore()) {
			e.removePref(ETAG);
			e.removePref(TIMESTAMP);
			e.removePref(EpgPrefs.EPG_ETAG);
			e.removePref(EpgPrefs.EPG_TIMESTAMP);
		}
	}

	public int getEpgMaxAge() {
		return getPrefs().getIntPref(EpgPrefs.EPG_MAX_AGE);
	}

	public void setEpgMaxAge(int age) {
		getPrefs().applyIntPref(EpgPrefs.EPG_MAX_AGE, age);
	}

	public File getEpgFile() {
		String epgUrl = getEpgUrl();
		String ext = (epgUrl == null) ? null : getFileExtension(epgUrl);
		return new File(getCacheDir(), getId() + '.' + ("gz".equals(ext) ? "xmltv.gz" : "xmltv"));
	}

	public File getEpgDbFile() {
		return new File(getCacheDir(), getId() + ".db");
	}

	public FutureSupplier<Status> downloadEpg() {
		String url = getEpgUrl();
		if (url == null) return failed(new MalformedURLException("EPG URL is not set"));
		File epgFile = getEpgFile();
		HttpFileDownloader d = new HttpFileDownloader();
		Context ctx = App.get();
		HttpDownloadStatusListener l = new HttpDownloadStatusListener(ctx);
		l.setSmallIcon(me.aap.fermata.R.drawable.notification);
		l.setTitle(ctx.getResources().getString(me.aap.fermata.R.string.downloading, url));
		l.setFailureTitle(s -> ctx.getResources().getString(me.aap.fermata.R.string.err_failed_to_download, url));
		d.setStatusListener(l);
		d.setReturnExistingOnFail(true);
		return d.download(url, epgFile, getEpgPrefs());
	}

	protected void cleanUp() {
		File f = getEpgFile();
		if (f.isFile() && !f.delete()) Log.e("Failed to delete EPG cache file ", f);
		SQLite.delete(getEpgDbFile());
		super.cleanUp();
	}

	private PreferenceStore getEpgPrefs() {
		return new EpgPrefs((M3uPrefs) getPrefs());
	}

	private static final class EpgPrefs extends M3uPrefs {
		static final Pref<Supplier<String>> EPG_ETAG = Pref.s("EPG_ETAG");
		static final Pref<Supplier<String>> EPG_CHARSET = Pref.s("EPG_CHARSET", CHARSET.getDefaultValue());
		static final Pref<Supplier<String>> EPG_ENCODING = Pref.s("EPG_ENCODING");
		static final Pref<IntSupplier> EPG_RESP_TIMEOUT = Pref.i("EPG_RESP_TIMEOUT", 30);
		static final Pref<LongSupplier> EPG_TIMESTAMP = Pref.l("EPG_TIMESTAMP", 0);
		static final Pref<IntSupplier> EPG_MAX_AGE = Pref.i("EPG_MAX_AGE", EPG_FILE_AGE);

		EpgPrefs(M3uPrefs prefs) {
			super(prefs);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <S> Pref<S> getPref(Pref<S> pref) {
			if (pref == ETAG) return (Pref<S>) EPG_ETAG;
			else if (pref == CHARSET) return (Pref<S>) EPG_CHARSET;
			else if (pref == ENCODING) return (Pref<S>) EPG_ENCODING;
			else if (pref == RESP_TIMEOUT) return (Pref<S>) EPG_RESP_TIMEOUT;
			else if (pref == TIMESTAMP) return (Pref<S>) EPG_TIMESTAMP;
			else if (pref == MAX_AGE) return (Pref<S>) EPG_MAX_AGE;
			return super.getPref(pref);
		}
	}
}
