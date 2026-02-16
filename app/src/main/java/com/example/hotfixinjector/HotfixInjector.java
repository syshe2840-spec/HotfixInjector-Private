package com.example.hotfixinjector;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import de.robv.android.xposed.XposedBridge;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;

public class HotfixInjector {

    private static final String TAG = "HotfixInjector";
    private static final String HOTFIX_FOLDER = "hotfix";

    public static void checkAndInject(String packageName, ClassLoader classLoader, Context context) {
        try {
            String hotfixPath = "/data/data/" + packageName + "/" + HOTFIX_FOLDER;
            File hotfixDir = new File(hotfixPath);

            if (!hotfixDir.exists()) {
                return;
            }

            File[] dexFiles = hotfixDir.listFiles(new FileFilter() {
					@Override
					public boolean accept(File file) {
						return file.isFile() && file.getName().endsWith(".dex") && file.canRead();
					}
				});

            if (dexFiles == null || dexFiles.length == 0) {
                XposedBridge.log(TAG + ": ❌ No DEX files in: " + hotfixPath);
                return;
            }

            XposedBridge.log(TAG + ": ========================================");
            XposedBridge.log(TAG + ": 🔥🔥🔥 HOTFIX DETECTED!");
            XposedBridge.log(TAG + ": 📦 Package: " + packageName);
            XposedBridge.log(TAG + ": 📂 Path: " + hotfixPath);
            XposedBridge.log(TAG + ": 📄 DEX files: " + dexFiles.length);

            for (File dex : dexFiles) {
                XposedBridge.log(TAG + ":    📌 " + dex.getName() + " (" + dex.length() + " bytes)");
            }

            XposedBridge.log(TAG + ": ========================================");

            // Try method 1: Execute HotfixEntry
            boolean success = tryExecuteEntry(classLoader, dexFiles, hotfixDir);

            if (!success) {
                XposedBridge.log(TAG + ": ℹ️ HotfixEntry not found, using direct injection...");
                injectDexElements(classLoader, dexFiles, hotfixDir);
            }

            XposedBridge.log(TAG + ": ✅ Hotfix injection completed!");
            XposedBridge.log(TAG + ": ========================================");

            showToast(context, "🔥 Hotfix Applied to " + packageName + "!");

        } catch (Exception e) {
            XposedBridge.log(TAG + ": ❌ Error: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private static boolean tryExecuteEntry(ClassLoader classLoader, File[] dexFiles, File hotfixDir) {
        try {
            File optDir = new File(hotfixDir, "opt");
            optDir.mkdirs();

            String[] classes = {
                "com.hotfix.HotfixEntry",
                "com.example.hotfixinjector.HotfixEntry",
                "HotfixEntry"
            };

            for (File dex : dexFiles) {
                try {
                    XposedBridge.log(TAG + ": 🔍 Checking " + dex.getName() + " for HotfixEntry...");

                    DexClassLoader loader = new DexClassLoader(
                        dex.getAbsolutePath(),
                        optDir.getAbsolutePath(),
                        null,
                        classLoader
                    );

                    for (String className : classes) {
                        try {
                            Class<?> entry = loader.loadClass(className);
                            XposedBridge.log(TAG + ": ✅ Found: " + className);

                            Object instance = entry.newInstance();

                            java.lang.reflect.Method init = entry.getMethod("init", ClassLoader.class);
                            init.invoke(instance, classLoader);

                            XposedBridge.log(TAG + ": 🎯 HotfixEntry.init() executed successfully!");
                            return true;

                        } catch (ClassNotFoundException e) {
                            // Try next class name
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log(TAG + ": ⚠️ Failed: " + e.getMessage());
                }
            }

            XposedBridge.log(TAG + ": ℹ️ No HotfixEntry class found");
            return false;

        } catch (Exception e) {
            XposedBridge.log(TAG + ": ❌ tryExecuteEntry failed: " + e.getMessage());
            return false;
        }
    }

    private static void injectDexElements(ClassLoader classLoader, File[] dexFiles, File hotfixDir) {
        try {
            File optDir = new File(hotfixDir, "opt");
            optDir.mkdirs();

            ArrayList<Object> elements = new ArrayList<>();

            for (File dex : dexFiles) {
                try {
                    XposedBridge.log(TAG + ": 📥 Loading " + dex.getName() + "...");

                    DexClassLoader loader = new DexClassLoader(
                        dex.getAbsolutePath(),
                        optDir.getAbsolutePath(),
                        null,
                        classLoader
                    );

                    Object pathList = getField(loader, "pathList");
                    if (pathList == null) {
                        XposedBridge.log(TAG + ": ⚠️ pathList null");
                        continue;
                    }

                    Object[] dexElements = (Object[]) getField(pathList, "dexElements");
                    if (dexElements == null || dexElements.length == 0) {
                        XposedBridge.log(TAG + ": ⚠️ dexElements empty");
                        continue;
                    }

                    for (Object e : dexElements) {
                        if (e != null) {
                            elements.add(e);
                        }
                    }

                    XposedBridge.log(TAG + ": ✅ Extracted " + dexElements.length + " elements");

                } catch (Exception e) {
                    XposedBridge.log(TAG + ": ⚠️ Load failed: " + e.getMessage());
                }
            }

            if (elements.isEmpty()) {
                XposedBridge.log(TAG + ": ❌ No elements to inject");
                return;
            }

            Object targetPathList = getField(classLoader, "pathList");
            if (targetPathList == null) {
                XposedBridge.log(TAG + ": ❌ Target pathList null");
                return;
            }

            Object[] targetElements = (Object[]) getField(targetPathList, "dexElements");
            if (targetElements == null) {
                XposedBridge.log(TAG + ": ❌ Target dexElements null");
                return;
            }

            int total = elements.size() + targetElements.length;
            Object combined = Array.newInstance(
                targetElements.getClass().getComponentType(),
                total
            );

            // Hotfix elements first (higher priority)
            for (int i = 0; i < elements.size(); i++) {
                Array.set(combined, i, elements.get(i));
            }

            // Original elements
            System.arraycopy(targetElements, 0, combined, elements.size(), targetElements.length);

            setField(targetPathList, "dexElements", combined);

            XposedBridge.log(TAG + ": 🚀 Successfully injected " + elements.size() + " elements!");
            XposedBridge.log(TAG + ": 📊 Total: " + total + " elements");

        } catch (Exception e) {
            XposedBridge.log(TAG + ": ❌ Injection failed: " + e.getMessage());
            XposedBridge.log(e);
        }
    }

    private static Object getField(Object obj, String name) throws Exception {
        if (obj == null) return null;

        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        if (obj == null) throw new IllegalArgumentException("obj is null");

        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                f.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void showToast(final Context context, final String msg) {
        try {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {
						try {
							Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
						} catch (Exception e) {
							// Ignore
						}
					}
				}, 3000);
        } catch (Exception e) {
            // Ignore
        }
    }
}
