package com.example.hotfixinjector;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HotfixEntry {

    /**
     * این متد توسط HookInit فراخوانی میشه
     * @param classLoader - ClassLoader اپ target
     */
    public void init(ClassLoader classLoader) {
        XposedBridge.log("Hotfix initialized!");

        // مثال 1: Hook کردن onCreate
        try {
            XposedHelpers.findAndHookMethod(
                "com.example.targetapp.MainActivity",
                classLoader,
                "onCreate",
                android.os.Bundle.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log("Hotfix: MainActivity.onCreate() hooked!");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Hotfix error: " + t.getMessage());
        }

        // مثال 2: Hook کردن یک متد با پارامتر
        try {
            XposedHelpers.findAndHookMethod(
                "com.example.targetapp.Calculator",
                classLoader,
                "divide",
                int.class,
                int.class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        int divisor = ((Integer) param.args[1]).intValue();
                        if (divisor == 0) {
                            XposedBridge.log("Hotfix: Prevented division by zero!");
                            param.setResult(Integer.valueOf(0));
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Hotfix error in divide: " + t.getMessage());
        }

        // مثال 3: تغییر مقدار return
        try {
            XposedHelpers.findAndHookMethod(
                "com.example.targetapp.Utils",
                classLoader,
                "isPremium",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // همیشه true برگردون (برای تست)
                        param.setResult(Boolean.TRUE);
                        XposedBridge.log("Hotfix: isPremium forced to true");
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("Hotfix error in isPremium: " + t.getMessage());
        }
    }
}
