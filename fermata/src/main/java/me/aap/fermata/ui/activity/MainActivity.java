package me.aap.fermata.ui.activity;

import static android.media.AudioManager.ADJUST_LOWER;
import static android.media.AudioManager.ADJUST_RAISE;
import static android.media.AudioManager.FLAG_SHOW_UI;
import static android.media.AudioManager.STREAM_MUSIC;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.InputDevice.SOURCE_CLASS_POINTER;
import static android.view.MotionEvent.ACTION_SCROLL;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;
import static me.aap.fermata.util.Utils.createDownloader;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;
import static me.aap.utils.async.Completed.completedVoid;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.misc.MiscUtils.isPackageInstalled;
import static me.aap.utils.pref.PreferenceStore.Pref.sa;
import static me.aap.utils.ui.UiUtils.showAlert;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.FileProvider;

import com.google.android.play.core.splitcompat.SplitCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.NaturalOrderComparator;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpConnection;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.AppActivity;
import me.aap.utils.ui.activity.SplitCompatActivityBase;

public class MainActivity extends SplitCompatActivityBase
		implements FermataActivity, AddonManager.Listener {
	private static FermataMediaServiceConnection service;
	private static MainActivity activeInstance;

	@Nullable
	public static MainActivity getActiveInstance() {
		return activeInstance;
	}

	@Override
	protected FutureSupplier<MainActivityDelegate> createDelegate(AppActivity a) {
		FermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			return completed(new MainActivityDelegate(a, service.createBinder()));
		}

		return FermataMediaServiceConnection.connect(a).map(c -> {
			assert service == null;
			service = c;
			return new MainActivityDelegate(a, service.createBinder());
		}).onFailure(err -> showAlert(getContext(), String.valueOf(err)));
	}

	@Override
	protected void attachBaseContext(Context base) {
		MainActivityDelegate.attachBaseContext(base);
		super.attachBaseContext(base);
	}

	@Override
	public void finish() {
		FermataMediaServiceConnection s = service;
		service = null;
		if (s != null) s.disconnect();
		super.finish();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		MainActivityDelegate.setTheme(this,
				isCarActivity() || FermataApplication.get().isMirroringMode());
		AddonManager.get().addBroadcastListener(this);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onDestroy() {
		AddonManager.get().removeBroadcastListener(this);
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		 activeInstance = this;
	}

	@Override
	protected void onPause() {
		super.onPause();
		activeInstance = null;
	}

	@Override
	public void onBackPressed() {
		getActivityDelegate().onSuccess(MainActivityDelegate::onBackPressed);
	}

	@Override
	public boolean isCarActivity() {
		return false;
	}

	@SuppressWarnings("unchecked")
	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return (FutureSupplier<MainActivityDelegate>) super.getActivityDelegate();
	}

	@Override
	public void onAddonChanged(AddonManager mgr, AddonInfo info, boolean installed) {
		SplitCompat.installActivity(this);
	}

	@Override
	public boolean onGenericMotionEvent(MotionEvent event) {
		if (((event.getSource() & SOURCE_CLASS_POINTER) != 0) && (event.getAction() == ACTION_SCROLL)) {
			AudioManager amgr = (AudioManager) getContext().getSystemService(AUDIO_SERVICE);
			if (amgr == null) return false;
			float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
			amgr.adjustStreamVolume(STREAM_MUSIC, (v > 0) ? ADJUST_RAISE : ADJUST_LOWER, FLAG_SHOW_UI);
			return true;
		}

		return super.onGenericMotionEvent(event);
	}

	public FutureSupplier<?> uninstallControl() {
		if (!BuildConfig.AUTO) return completedVoid();

		var pkgName = "me.aap.fermata.auto.control.dear.google.why";
		if (!isPackageInstalled(this, pkgName)) return completedVoid();

		return startActivityForResult(() -> {
			var i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + pkgName));
			i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
			return i;
		});
	}

	public void checkUpdates() {
		if (!BuildConfig.AUTO) return;

		PreferenceStore ps = FermataApplication.get().getPreferenceStore();
		Pref<Supplier<String[]>> deletePref = sa("DELETE_ON_STARTUP", new String[0]);
		String[] delete = ps.getStringArrayPref(deletePref);

		if (delete.length != 0) {
			App.get().getScheduler().schedule(() -> {
				for (String f : delete) {
					//noinspection ResultOfMethodCallIgnored
					new File(f).delete();
				}

				synchronized (this) {
					List<String> l = new ArrayList<>(Arrays.asList(ps.getStringArrayPref(deletePref)));
					l.removeAll(Arrays.asList(delete));
					if (l.isEmpty()) ps.removePref(deletePref);
					else ps.applyStringArrayPref(deletePref, l.toArray(new String[0]));
				}
			}, 1, MINUTES);
		}

		String reqUrl = "https://api.github.com/repos/AndreyPavlenko/Fermata/releases/latest";
		HttpConnection.connect(o -> o.url(reqUrl), (resp, err) -> {
			if (err != null) {
				Log.e(err, "Failed to check updates");
				return failed(err);
			}

			resp.getPayload((p, perr) -> {
				if (perr != null) {
					Log.e(perr, "Failed to read response");
					return completedNull();
				}

				try {
					JSONObject json = new JSONObject(TextUtils.toString(p, UTF_8));
					String tag = json.getString("tag_name");
					String[] res = new String[2];
					res[0] = tag;
					int idx = tag.indexOf('(');
					if (idx != -1) tag = tag.substring(0, idx);

					if (NaturalOrderComparator.compareNatural(BuildConfig.VERSION_NAME, tag.trim()) < 0) {
						Log.i("New version is available: ", res[0]);
						JSONArray assets = json.getJSONArray("assets");
						String ext = "armeabi".equals(Build.SUPPORTED_ABIS[0]) ? "-arm.apk" : "-arm64.apk";

						for (int i = 0, n = assets.length(); i < n; i++) {
							JSONObject asset = assets.getJSONObject(i);
							String name = asset.getString("name");
							if (name.endsWith(ext)) res[1] = asset.getString("browser_download_url");
						}

						return (res[1] != null) ? completed(res) : completedNull();
					} else {
						Log.i("The latest release version - ", res[0], ". Application is up to date");
						return completedNull();
					}
				} catch (Exception ex) {
					Log.e(ex, "Failed to parse response");
					return failed(ex);
				}
			}).main().onSuccess(res -> {
				if (res == null) return;
				UiUtils.showQuestion(getContext(), getString(R.string.update),
								getString(R.string.update_question, res[0]),
								AppCompatResources.getDrawable(getContext(), R.drawable.notification))
						.onSuccess(r -> update(res[1], ps, deletePref));
			});

			return completedVoid();
		});
	}

	private FutureSupplier<Void> update(String uri, PreferenceStore ps,
																			Pref<Supplier<String[]>> deletePref) {
		if (!BuildConfig.AUTO) return completedVoid();
		try {
			File tmp;
			File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

			if ((tmp = createTempFile(dir)) == null) {
				App app = App.get();
				File cache = app.getExternalCacheDir();
				if (cache == null) cache = app.getCacheDir();
				dir = new File(cache, "updates");
				//noinspection ResultOfMethodCallIgnored
				dir.mkdirs();
				if ((tmp = createTempFile(dir)) == null) {
					App.get()
							.run(() -> showAlert(this, "Update failed - unable to create a temporary " + "file"
							));
					return completedVoid();
				}
			}

			File f = tmp;

			synchronized (this) {
				List<String> l = new ArrayList<>(Arrays.asList(ps.getStringArrayPref(deletePref)));
				l.add(f.getAbsolutePath());
				ps.applyStringArrayPref(deletePref, l.toArray(new String[0]));
			}

			HttpFileDownloader d = createDownloader(getContext(), uri);
			return d.download(uri, f).then(s -> {
				Uri u = (SDK_INT >= Build.VERSION_CODES.N) ?
						FileProvider.getUriForFile(getApplicationContext(), getPackageName() + ".FileProvider",
								f) : Uri.fromFile(f);

				try {
					installApk(u, true);
				} catch (Exception ex) {
					Log.e(ex, "Update failed");
					App.get().run(() -> showAlert(this, "Update failed: " + ex.getLocalizedMessage()));
					return failed(ex);
				}

				return completedVoid();
			}).onFailure(err -> {
				Log.e(err, "Failed to download apk: ", uri);
				App.get().run(() -> showAlert(this, "Failed to download apk: " + uri));
			});
		} catch (Exception ex) {
			Log.e(ex, "Update failed");
			App.get().run(() -> showAlert(this, "Update failed: " + ex.getLocalizedMessage()));
			return failed(ex);
		}
	}

	private static File createTempFile(File dir) {
		try {
			if (dir == null) return null;
			return File.createTempFile("Fermata-", ".apk", dir);
		} catch (Exception ex) {
			Log.e(ex, "Failed to create a temporary file in the directory ", dir);
			return null;
		}
	}
}
