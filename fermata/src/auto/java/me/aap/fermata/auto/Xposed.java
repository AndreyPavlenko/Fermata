package me.aap.fermata.auto;

import static de.robv.android.xposed.XposedBridge.log;

import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * @author Andrey Pavlenko
 */
public class Xposed implements IXposedHookLoadPackage {
	private static final String AA_PKG = "com.google.android.projection.gearhead";
	private static final String GPLAY_PKG = "com.android.vending";

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
		if (!AA_PKG.equals(lpparam.packageName)) return;
		log("Hooking package " + AA_PKG);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			XposedHelpers.findAndHookMethod(InstallSourceInfo.class, "getInitiatingPackageName", new XC_MethodHook() {

				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					param.setResult(GPLAY_PKG);
				}
			});
		} else {
			XposedHelpers.findAndHookMethod(PackageManager.class, "getInstallerPackageName", new XC_MethodHook() {
				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					param.setResult(GPLAY_PKG);
				}
			});
		}
	}
}
