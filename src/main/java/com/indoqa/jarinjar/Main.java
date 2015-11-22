/*
 * Licensed to the Indoqa Software Design und Beratung GmbH (Indoqa) under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Indoqa licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.indoqa.jarinjar;

import java.awt.SystemTray;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Use the {@link Main} if a Java library (jar) contains other JARs and you have to make sure that you use a file system based
 * classloader. It internally uses a {@link URLClassLoader} which works well with e.g. Spring's path matching resource filters.
 * 
 * The {@link Main} extracts all libraries to a temporary directory and deletes them at the end.
 * 
 * Note: Because of a bug in the {@link URLClassLoader} in Sun JVMs, the temporary directory can't be deleted at runtime on Windows
 * machines. Hence the cleanup shutdown hook only works on *nix systems. For that reason there is a cleanup method executed at the
 * beginning that removes all non-active directories on Windows machines.
 * 
 * see http://blogs.sun.com/CoreJavaTechTips/entry/closing_a_urlclassloader
 */
public class Main {

    private static final int COPY_BUFFER_SIZE = 1024 * 4;
    private static final String TEMP_TARGET_DIR_PREFIX = "_indoqa-jarinjar-";
    private static final String MANIFEST_PROPERTY_DELEGATED_MAIN_CLASS = "delegatedMainClass";
    private static final String TRAY_ICON_PROVIDER_CLASS_PROPERTY = "tray-icon-provider-class";

    private static File targetDirectory;
    private static ClassLoader classLoader;
    private static JarFile currentJar;

    public static File getTargetDirectory() {
        return targetDirectory;
    }

    public static void main(String[] args) throws Exception {
        cleanupOldTargetDirs();

        createTargetDirectory();
        findCurrentJar();
        setupClassLoader();
        setContextClassLoader();

        createTrayIcon();
        registerCleanupShutdownHook();

        invokeMainMethod(args);
    }

    protected static void deleteTargetDirectory(File targetDir) {
        for (File eachFile : targetDir.listFiles()) {
            eachFile.delete();
        }

        boolean isDeleted = targetDir.delete();
        System.out.println("[delete] " + targetDir.getAbsolutePath() + " _" + (isDeleted ? "" : "NOT ") + "deleted_");
    }

    private static void cleanupOldTargetDirs() {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return;
        }

        FileFilter targetDirectoryFileFilter = new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory() && pathname.getName().startsWith(TEMP_TARGET_DIR_PREFIX)) {
                    return true;
                }

                return false;
            }
        };

        for (File eachTargetDirectory : getTargetParentDirectory().listFiles(targetDirectoryFileFilter)) {
            deleteTargetDirectory(eachTargetDirectory);
        }
    }

    private static long copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long count = 0;
        int n = 0;

        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }

        return count;
    }

    private static void createTargetDirectory() {
        File targetDir = new File(getTargetParentDirectory(), TEMP_TARGET_DIR_PREFIX + System.currentTimeMillis());
        targetDir.mkdirs();
        targetDirectory = targetDir;
    }

    private static void createTrayIcon() {
        if (!SystemTray.isSupported()) {
            return;
        }

        Manifest manifest = SystemUtils.getCurrentManifest(com.indoqa.jarinjar.Main.class);
        Class<?> trayIconProviderClass = null;
        String trayIconProviderClassName = null;
        try {
            trayIconProviderClassName = SystemUtils.getManifestProperty(manifest, TRAY_ICON_PROVIDER_CLASS_PROPERTY);
            if (trayIconProviderClassName == null || "".equals(trayIconProviderClassName)) {
                return;
            }

            trayIconProviderClass = classLoader.loadClass(trayIconProviderClassName);

            if (!TrayIconProvider.class.isAssignableFrom(trayIconProviderClass)) {
                throw new IllegalArgumentException("The property '" + TRAY_ICON_PROVIDER_CLASS_PROPERTY
                    + "' doesn't refer to a class that implements the interface " + TrayIconProvider.class.getName());
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("The property '" + TRAY_ICON_PROVIDER_CLASS_PROPERTY + "' points to the class '"
                + trayIconProviderClassName + "' which can't be loaded from the classpath.", e);
        }

        try {
            TrayIconProvider trayIconProvider = (TrayIconProvider) trayIconProviderClass.newInstance();
            SystemTray tray = SystemTray.getSystemTray();
            tray.add(trayIconProvider.getTrayIcon());
        } catch (Exception e) {
            throw new IllegalStateException("TrayIcon could not be initialized.", e);
        }
    }

    private static void findCurrentJar() {
        currentJar = SystemUtils.getCurrentJar(Main.class);
    }

    private static String getMainClassFQName() throws IOException {
        Manifest manifest = currentJar.getManifest();

        String delegatedMainClass = SystemUtils.getManifestProperty(manifest, MANIFEST_PROPERTY_DELEGATED_MAIN_CLASS);

        if (delegatedMainClass == null) {
            throw new NoDelegatedMainClassDefinedException("The manifest property 'delegatedMainClass' has to be set.");
        }

        return delegatedMainClass;
    }

    private static File getTargetParentDirectory() {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    private static void invokeMainMethod(String[] args)
            throws ClassNotFoundException, IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class<?> mainClass = classLoader.loadClass(getMainClassFQName());
        Method mainMethod = mainClass.getMethod("main", new Class[] {args.getClass()});
        mainMethod.invoke((Object) null, new Object[] {args});
    }

    private static void registerCleanupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new CleanupTargetDirShutdownHook(targetDirectory));
    }

    private static void setContextClassLoader() {
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    private static void setupClassLoader() throws IOException {
        List<URL> jarUrls = new ArrayList<URL>();

        for (Enumeration<JarEntry> entries = currentJar.entries(); entries.hasMoreElements();) {
            JarEntry entry = entries.nextElement();

            if (!SystemUtils.isJavaLibrary(entry) && !SystemUtils.isWebArchive(entry) && !SystemUtils.isNativeLibrary(entry)) {
                continue;
            }

            File library = new File(targetDirectory, entry.getName());
            copy(currentJar.getInputStream(entry), new FileOutputStream(library));

            if (SystemUtils.isJavaLibrary(entry)) {
                jarUrls.add(library.toURI().toURL());
            }
        }

        classLoader = new URLClassLoader(jarUrls.toArray(new URL[jarUrls.size()]), Main.class.getClassLoader());
    }

    private static class CleanupTargetDirShutdownHook extends Thread {

        private final File targetDir;

        public CleanupTargetDirShutdownHook(File targetDir) {
            super();
            this.targetDir = targetDir;
        }

        @Override
        public void run() {
            deleteTargetDirectory(this.targetDir);
        }
    }

    private static class NoDelegatedMainClassDefinedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoDelegatedMainClassDefinedException(String message) {
            super(message);
        }
    }
}
