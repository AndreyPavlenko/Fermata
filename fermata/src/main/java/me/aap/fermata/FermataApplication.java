package me.aap.fermata;

import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import java.io.File;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.media.engine.BitmapCache;
import me.aap.fermata.vfs.FermataVfsManager;
import me.aap.utils.app.App;
import me.aap.utils.app.NetSplitCompatApp;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;

public class FermataApplication extends NetSplitCompatApp {
    private FermataVfsManager vfsManager;
    private BitmapCache bitmapCache;
    private volatile SharedPreferenceStore preferenceStore;
    private volatile AddonManager addonManager;

    public static FermataApplication get() {
        return App.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        vfsManager = new FermataVfsManager();
        bitmapCache = new BitmapCache();
    }

    public FermataVfsManager getVfsManager() {
        return vfsManager;
    }

    public BitmapCache getBitmapCache() {
        return bitmapCache;
    }

    public PreferenceStore getPreferenceStore() {
        if (preferenceStore == null) {
            synchronized (this) {
                if (preferenceStore == null) {
                    preferenceStore = SharedPreferenceStore.create(getSharedPreferences("fermata", MODE_PRIVATE));
                }
            }
        }

        return preferenceStore;
    }

    public SharedPreferences getDefaultSharedPreferences() {
        return getPreferenceStore().getSharedPreferences();
    }

    public AddonManager getAddonManager() {
        if (addonManager == null) {
            synchronized (this) {
                if (addonManager == null) {
                    addonManager = new AddonManager(getPreferenceStore());
                }
            }
        }

        return addonManager;
    }

    @Override
    protected int getMaxNumberOfThreads() {
        return 5;
    }

    @Nullable
    @Override
    public File getLogFile() {
        File dir = getExternalFilesDir(null);
        if (dir == null) {
            dir = getFilesDir();
        }
        return new File(dir, "Fermata.log");
    }

    @Nullable
    @Override
    public String getCrashReportEmail() {
        return "andrey.a.pavlenko@gmail.com";
    }
}
