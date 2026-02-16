package com.example.hotfixinjector;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import dalvik.system.DexClassLoader;

public class HookInit implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String TAG = "HotfixInjector";
    private static final String HOTFIX_FOLDER = "hotfix";
    private static final Set<String> processed = new HashSet<>();

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedBridge.log(TAG + ": ========================================");
        XposedBridge.log(TAG + ": 🔥 HotFix Injector (FULL DEBUG MODE)");
        XposedBridge.log(TAG + ": Android " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        XposedBridge.log(TAG + ": ========================================");
        XposedBridge.log(TAG + ": [INIT] Starting Zygote hook installation...");

        try {
            XposedBridge.log(TAG + ": [INIT] Finding Application.onCreate method...");

            // Hook Application.onCreate for ALL apps via Zygote
            XposedHelpers.findAndHookMethod(
                Application.class,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + ": [HOOK] Application.onCreate BEFORE called");
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        XposedBridge.log(TAG + ": [HOOK] Application.onCreate AFTER started");

                        try {
                            Application app = (Application) param.thisObject;
                            XposedBridge.log(TAG + ": [HOOK] Got Application object: " + app);

                            String pkg = app.getPackageName();
                            XposedBridge.log(TAG + ": [HOOK] Package name: " + pkg);

                            // Skip system
                            if (shouldSkip(pkg)) {
                                XposedBridge.log(TAG + ": [SKIP] Skipping package: " + pkg);
                                return;
                            }

                            // Skip if already processed
                            synchronized (processed) {
                                if (processed.contains(pkg)) {
                                    XposedBridge.log(TAG + ": [SKIP] Already processed: " + pkg);
                                    return;
                                }
                                XposedBridge.log(TAG + ": [HOOK] Adding to processed set: " + pkg);
                                processed.add(pkg);
                            }

                            XposedBridge.log(TAG + ": [CHECK] Checking package: " + pkg);

                            // Check hotfix folder
                            String hotfixPath = "/data/data/" + pkg + "/" + HOTFIX_FOLDER;
                            XposedBridge.log(TAG + ": [CHECK] Hotfix path: " + hotfixPath);

                            File hotfixDir = new File(hotfixPath);
                            XposedBridge.log(TAG + ": [CHECK] Created File object");

                            XposedBridge.log(TAG + ": [CHECK] Checking if exists: " + hotfixDir.exists());
                            XposedBridge.log(TAG + ": [CHECK] Checking if directory: " + hotfixDir.isDirectory());
                            XposedBridge.log(TAG + ": [CHECK] Checking if readable: " + hotfixDir.canRead());
                            XposedBridge.log(TAG + ": [CHECK] Checking if writable: " + hotfixDir.canWrite());

                            if (!hotfixDir.exists()) {
                                XposedBridge.log(TAG + ": [SKIP] Hotfix folder not found for: " + pkg);
                                return;
                            }

                            XposedBridge.log(TAG + ": [FOUND] 🔥 Hotfix detected: " + pkg);
                            XposedBridge.log(TAG + ": [FOUND] Path exists: " + hotfixPath);

                            // Get classloader
                            ClassLoader cl = app.getClassLoader();
                            XposedBridge.log(TAG + ": [FOUND] ClassLoader: " + cl);

                            // Inject
                            XposedBridge.log(TAG + ": [INJECT] Starting injection for: " + pkg);
                            injectHotfix(pkg, cl, hotfixDir, app);

                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": [ERROR] Exception in afterHookedMethod");
                            XposedBridge.log(TAG + ": [ERROR] Message: " + t.getMessage());
                            XposedBridge.log(TAG + ": [ERROR] Class: " + t.getClass().getName());
                            XposedBridge.log(t);
                        }
                    }
                }
            );

            XposedBridge.log(TAG + ": [INIT] Hook method found and hooked successfully");
            XposedBridge.log(TAG + ": ✅ Zygote hook installed!");

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ❌ Failed to install Zygote hook");
            XposedBridge.log(TAG + ": [ERROR] Message: " + t.getMessage());
            XposedBridge.log(TAG + ": [ERROR] Class: " + t.getClass().getName());
            XposedBridge.log(t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        XposedBridge.log(TAG + ": [LOAD] handleLoadPackage called for: " + lpparam.packageName);

        // Only for self module
        if (lpparam.packageName.equals("com.example.hotfixinjector")) {
            XposedBridge.log(TAG + ": [SELF] Detected self module load");
            hookSelfModule(lpparam);
        } else {
            XposedBridge.log(TAG + ": [LOAD] Not self module, skipping");
        }
    }

    private boolean shouldSkip(String pkg) {
        boolean skip = pkg == null ||
            pkg.equals("android") ||
            pkg.equals("system") ||
            pkg.equals("com.example.hotfixinjector") ||
            pkg.startsWith("com.android.systemui");

        if (skip) {
            XposedBridge.log(TAG + ": [SKIP] shouldSkip returned true for: " + pkg);
        }

        return skip;
    }

    private void injectHotfix(String packageName, ClassLoader classLoader, File hotfixDir, Application app) {
        try {
            XposedBridge.log(TAG + ": ========================================");
            XposedBridge.log(TAG + ": 🔥 INJECTING: " + packageName);
            XposedBridge.log(TAG + ": [INJ] ClassLoader: " + classLoader);
            XposedBridge.log(TAG + ": [INJ] Hotfix dir: " + hotfixDir.getAbsolutePath());

            XposedBridge.log(TAG + ": [INJ] Listing files in hotfix directory...");

            File[] allFiles = hotfixDir.listFiles();
            if (allFiles != null) {
                XposedBridge.log(TAG + ": [INJ] Total files in directory: " + allFiles.length);
                for (File f : allFiles) {
                    XposedBridge.log(TAG + ": [INJ]   - " + f.getName() + 
									 " (size: " + f.length() + 
									 ", readable: " + f.canRead() + 
									 ", writable: " + f.canWrite() + 
									 ", executable: " + f.canExecute() + ")");
                }
            } else {
                XposedBridge.log(TAG + ": [INJ] listFiles() returned null!");
            }

            XposedBridge.log(TAG + ": [INJ] Filtering DEX files...");

            File[] dexFiles = hotfixDir.listFiles(new FileFilter() {
					@Override
					public boolean accept(File file) {
						String name = file.getName();
						boolean isDex = name.endsWith(".dex");
						boolean canRead = file.canRead();

						XposedBridge.log(TAG + ": [FILTER] Checking: " + name + 
										 " (isDex: " + isDex + 
										 ", canRead: " + canRead + 
										 ", size: " + file.length() + ")");

						return isDex && canRead;
					}
				});

            if (dexFiles == null) {
                XposedBridge.log(TAG + ": ❌ listFiles with filter returned null");
                return;
            }

            if (dexFiles.length == 0) {
                XposedBridge.log(TAG + ": ❌ No DEX files found (filtered length = 0)");
                return;
            }

            XposedBridge.log(TAG + ": 📄 DEX files found: " + dexFiles.length);
            for (int i = 0; i < dexFiles.length; i++) {
                File dex = dexFiles[i];
                XposedBridge.log(TAG + ":    [" + i + "] " + dex.getName() + 
								 " (" + dex.length() + " bytes)" +
								 " (path: " + dex.getAbsolutePath() + ")" +
								 " (readable: " + dex.canRead() + ")" +
								 " (writable: " + dex.canWrite() + ")");
            }

            // Try HotfixEntry
            XposedBridge.log(TAG + ": [INJ] Attempting HotfixEntry execution...");
            boolean success = tryExecuteEntry(classLoader, dexFiles, hotfixDir);

            if (!success) {
                XposedBridge.log(TAG + ": [INFO] HotfixEntry not found, using direct injection");
            } else {
                XposedBridge.log(TAG + ": [INFO] HotfixEntry executed successfully");
            }

            // Direct injection
            XposedBridge.log(TAG + ": [INJ] Starting direct DEX injection...");
            injectDexElements(classLoader, dexFiles, hotfixDir);

            XposedBridge.log(TAG + ": ✅ INJECTION COMPLETED!");

            // Read license with root (module has privilege)
            XposedBridge.log(TAG + ": [LICENSE] Reading license file with root...");
            LicenseClient.LicenseData licenseData = null;
            try {
                licenseData = LicenseClient.readLicenseFromFile();
                if (licenseData != null && licenseData.isValid()) {
                    XposedBridge.log(TAG + ": ✅ [LICENSE] License data loaded successfully");
                    XposedBridge.log(TAG + ": [LICENSE] Device: " + licenseData.deviceId.substring(0, Math.min(16, licenseData.deviceId.length())) + "...");
                } else {
                    XposedBridge.log(TAG + ": ⚠️ [LICENSE] No valid license found - guard will crash app");
                }
            } catch (Exception licEx) {
                XposedBridge.log(TAG + ": ⚠️ [LICENSE] Failed to read license: " + licEx.getMessage());
                XposedBridge.log(licEx);
            }

            // Start License Guard with pre-loaded license data
            XposedBridge.log(TAG + ": [GUARD] Starting License Guard (5-second verification)...");
            try {
                LicenseGuard guard = LicenseGuard.getInstance(app, licenseData);
                guard.startGuard(app);
                XposedBridge.log(TAG + ": ✅ [GUARD] License Guard started successfully");
            } catch (Exception guardEx) {
                XposedBridge.log(TAG + ": ❌ [GUARD] Failed to start guard: " + guardEx.getMessage());
                XposedBridge.log(guardEx);
            }

            XposedBridge.log(TAG + ": ========================================");

        } catch (Exception e) {
            XposedBridge.log(TAG + ": ❌ Injection error");
            XposedBridge.log(TAG + ": [ERROR] Message: " + e.getMessage());
            XposedBridge.log(TAG + ": [ERROR] Class: " + e.getClass().getName());
            XposedBridge.log(e);
        }
    }

    private boolean tryExecuteEntry(ClassLoader classLoader, File[] dexFiles, File hotfixDir) {
        try {
            XposedBridge.log(TAG + ": [ENTRY] Creating opt directory...");
            File optDir = new File(hotfixDir, "opt");
            boolean created = optDir.mkdirs();
            XposedBridge.log(TAG + ": [ENTRY] Opt dir created: " + created + " (exists: " + optDir.exists() + ")");
            XposedBridge.log(TAG + ": [ENTRY] Opt dir path: " + optDir.getAbsolutePath());

            for (int i = 0; i < dexFiles.length; i++) {
                File dex = dexFiles[i];
                XposedBridge.log(TAG + ": [ENTRY] Trying DEX file [" + i + "]: " + dex.getName());

                try {
                    XposedBridge.log(TAG + ": [ENTRY] Creating DexClassLoader...");
                    XposedBridge.log(TAG + ": [ENTRY]   dexPath: " + dex.getAbsolutePath());
                    XposedBridge.log(TAG + ": [ENTRY]   optimizedDirectory: " + optDir.getAbsolutePath());
                    XposedBridge.log(TAG + ": [ENTRY]   parent: " + classLoader);

                    DexClassLoader loader = new DexClassLoader(
                        dex.getAbsolutePath(),
                        optDir.getAbsolutePath(),
                        null,
                        classLoader
                    );

                    XposedBridge.log(TAG + ": [ENTRY] DexClassLoader created: " + loader);

                    XposedBridge.log(TAG + ": [ENTRY] Loading class: com.hotfix.HotfixEntry");
                    Class<?> entry = loader.loadClass("com.hotfix.HotfixEntry");
                    XposedBridge.log(TAG + ": [ENTRY] Class loaded: " + entry);

                    XposedBridge.log(TAG + ": [ENTRY] Creating instance...");
                    Object instance = entry.newInstance();
                    XposedBridge.log(TAG + ": [ENTRY] Instance created: " + instance);

                    XposedBridge.log(TAG + ": [ENTRY] Getting init method...");
                    java.lang.reflect.Method init = entry.getMethod("init", ClassLoader.class);
                    XposedBridge.log(TAG + ": [ENTRY] Method found: " + init);

                    XposedBridge.log(TAG + ": [ENTRY] Invoking init method...");
                    init.invoke(instance, classLoader);

                    XposedBridge.log(TAG + ": 🎯 HotfixEntry executed successfully!");
                    return true;

                } catch (ClassNotFoundException e) {
                    XposedBridge.log(TAG + ": [ENTRY] ClassNotFoundException for " + dex.getName() + ": " + e.getMessage());
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": [ENTRY] Exception for " + dex.getName());
                    XposedBridge.log(TAG + ": [ENTRY] Error: " + e.getMessage());
                    XposedBridge.log(TAG + ": [ENTRY] Class: " + e.getClass().getName());
                    XposedBridge.log(e);
                }
            }

            XposedBridge.log(TAG + ": [ENTRY] No HotfixEntry found in any DEX file");

        } catch (Exception e) {
            XposedBridge.log(TAG + ": [ENTRY] Failed to execute entry");
            XposedBridge.log(TAG + ": [ENTRY] Message: " + e.getMessage());
            XposedBridge.log(TAG + ": [ENTRY] Class: " + e.getClass().getName());
            XposedBridge.log(e);
        }
        return false;
    }

    private void injectDexElements(ClassLoader classLoader, File[] dexFiles, File hotfixDir) {
        try {
            XposedBridge.log(TAG + ": [ELEM] Starting element injection");
            XposedBridge.log(TAG + ": [ELEM] Creating opt directory...");

            File optDir = new File(hotfixDir, "opt");
            boolean created = optDir.mkdirs();
            XposedBridge.log(TAG + ": [ELEM] Opt dir: " + optDir.getAbsolutePath() + " (created: " + created + ")");

            ArrayList<Object> elements = new ArrayList<>();
            XposedBridge.log(TAG + ": [ELEM] Processing " + dexFiles.length + " DEX files...");

            for (int i = 0; i < dexFiles.length; i++) {
                File dex = dexFiles[i];
                XposedBridge.log(TAG + ": [ELEM] Processing DEX [" + i + "]: " + dex.getName());

                try {
                    XposedBridge.log(TAG + ": [ELEM] Creating DexClassLoader for " + dex.getName());

                    DexClassLoader loader = new DexClassLoader(
                        dex.getAbsolutePath(),
                        optDir.getAbsolutePath(),
                        null,
                        classLoader
                    );

                    XposedBridge.log(TAG + ": [ELEM] Loader created: " + loader);
                    XposedBridge.log(TAG + ": [ELEM] Getting pathList field...");

                    Object pathList = getField(loader, "pathList");
                    XposedBridge.log(TAG + ": [ELEM] pathList: " + pathList);

                    if (pathList == null) {
                        XposedBridge.log(TAG + ": [ELEM] pathList is null for " + dex.getName());
                        continue;
                    }

                    XposedBridge.log(TAG + ": [ELEM] Getting dexElements field...");
                    Object[] dexElements = (Object[]) getField(pathList, "dexElements");

                    if (dexElements == null) {
                        XposedBridge.log(TAG + ": [ELEM] dexElements is null for " + dex.getName());
                        continue;
                    }

                    XposedBridge.log(TAG + ": [ELEM] dexElements length: " + dexElements.length);

                    for (int j = 0; j < dexElements.length; j++) {
                        Object e = dexElements[j];
                        if (e != null) {
                            XposedBridge.log(TAG + ": [ELEM] Adding element [" + j + "]: " + e);
                            elements.add(e);
                        } else {
                            XposedBridge.log(TAG + ": [ELEM] Element [" + j + "] is null, skipping");
                        }
                    }

                    XposedBridge.log(TAG + ": [ELEM] Successfully processed " + dex.getName());

                } catch (Exception e) {
                    XposedBridge.log(TAG + ": [ELEM] Failed to process " + dex.getName());
                    XposedBridge.log(TAG + ": [ELEM] Error: " + e.getMessage());
                    XposedBridge.log(TAG + ": [ELEM] Class: " + e.getClass().getName());
                    XposedBridge.log(e);
                }
            }

            XposedBridge.log(TAG + ": [ELEM] Total elements collected: " + elements.size());

            if (elements.isEmpty()) {
                XposedBridge.log(TAG + ": [ELEM] No elements to inject");
                return;
            }

            XposedBridge.log(TAG + ": [ELEM] Getting target classLoader pathList...");
            Object targetPathList = getField(classLoader, "pathList");
            XposedBridge.log(TAG + ": [ELEM] Target pathList: " + targetPathList);

            if (targetPathList == null) {
                XposedBridge.log(TAG + ": [ELEM] Target pathList is null");
                return;
            }

            XposedBridge.log(TAG + ": [ELEM] Getting target dexElements...");
            Object[] targetElements = (Object[]) getField(targetPathList, "dexElements");

            if (targetElements == null) {
                XposedBridge.log(TAG + ": [ELEM] Target dexElements is null");
                return;
            }

            XposedBridge.log(TAG + ": [ELEM] Target dexElements length: " + targetElements.length);

            int total = elements.size() + targetElements.length;
            XposedBridge.log(TAG + ": [ELEM] Creating combined array of size: " + total);

            Object combined = Array.newInstance(
                targetElements.getClass().getComponentType(),
                total
            );

            XposedBridge.log(TAG + ": [ELEM] Combined array created: " + combined);

            XposedBridge.log(TAG + ": [ELEM] Copying new elements to combined array...");
            for (int i = 0; i < elements.size(); i++) {
                Array.set(combined, i, elements.get(i));
                XposedBridge.log(TAG + ": [ELEM] Set element [" + i + "]: " + elements.get(i));
            }

            XposedBridge.log(TAG + ": [ELEM] Copying original elements to combined array...");
            System.arraycopy(targetElements, 0, combined, elements.size(), targetElements.length);
            XposedBridge.log(TAG + ": [ELEM] Original elements copied");

            XposedBridge.log(TAG + ": [ELEM] Setting new dexElements to pathList...");
            setField(targetPathList, "dexElements", combined);
            XposedBridge.log(TAG + ": [ELEM] dexElements set successfully");

            XposedBridge.log(TAG + ": 🚀 Injected " + elements.size() + " elements!");

        } catch (Exception e) {
            XposedBridge.log(TAG + ": [ELEM] Injection failed");
            XposedBridge.log(TAG + ": [ERROR] Message: " + e.getMessage());
            XposedBridge.log(TAG + ": [ERROR] Class: " + e.getClass().getName());
            XposedBridge.log(e);
        }
    }

    private Object getField(Object obj, String name) throws Exception {
        XposedBridge.log(TAG + ": [FIELD] Getting field '" + name + "' from " + obj);

        if (obj == null) {
            XposedBridge.log(TAG + ": [FIELD] Object is null");
            return null;
        }

        Class<?> clazz = obj.getClass();
        XposedBridge.log(TAG + ": [FIELD] Object class: " + clazz.getName());

        while (clazz != null) {
            try {
                XposedBridge.log(TAG + ": [FIELD] Trying to get field from class: " + clazz.getName());
                Field f = clazz.getDeclaredField(name);
                XposedBridge.log(TAG + ": [FIELD] Field found: " + f);

                f.setAccessible(true);
                XposedBridge.log(TAG + ": [FIELD] Field set accessible");

                Object value = f.get(obj);
                XposedBridge.log(TAG + ": [FIELD] Field value: " + value);

                return value;
            } catch (NoSuchFieldException e) {
                XposedBridge.log(TAG + ": [FIELD] Field not found in " + clazz.getName() + ", trying superclass");
                clazz = clazz.getSuperclass();
            }
        }

        XposedBridge.log(TAG + ": [FIELD] Field '" + name + "' not found in any class");
        throw new NoSuchFieldException(name);
    }

    private void setField(Object obj, String name, Object value) throws Exception {
        XposedBridge.log(TAG + ": [FIELD] Setting field '" + name + "' on " + obj + " to " + value);

        Class<?> clazz = obj.getClass();
        XposedBridge.log(TAG + ": [FIELD] Object class: " + clazz.getName());

        while (clazz != null) {
            try {
                XposedBridge.log(TAG + ": [FIELD] Trying to set field in class: " + clazz.getName());
                Field f = clazz.getDeclaredField(name);
                XposedBridge.log(TAG + ": [FIELD] Field found: " + f);

                f.setAccessible(true);
                XposedBridge.log(TAG + ": [FIELD] Field set accessible");

                f.set(obj, value);
                XposedBridge.log(TAG + ": [FIELD] Field value set successfully");

                return;
            } catch (NoSuchFieldException e) {
                XposedBridge.log(TAG + ": [FIELD] Field not found in " + clazz.getName() + ", trying superclass");
                clazz = clazz.getSuperclass();
            }
        }

        XposedBridge.log(TAG + ": [FIELD] Field '" + name + "' not found in any class");
        throw new NoSuchFieldException(name);
    }

    private void hookSelfModule(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.log(TAG + ": [SELF] Hooking self module MainActivity");
            XposedBridge.log(TAG + ": [SELF] ClassLoader: " + lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                "com.example.hotfixinjector.MainActivity",
                lpparam.classLoader,
                "isModuleActive",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + ": [SELF] isModuleActive called, returning true");
                        param.setResult(Boolean.TRUE);
                    }
                }
            );

            XposedBridge.log(TAG + ": ✅ Self-hook installed successfully");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": [SELF] Failed to hook self module");
            XposedBridge.log(TAG + ": [SELF] Error: " + t.getMessage());
            XposedBridge.log(t);
        }
    }
}

