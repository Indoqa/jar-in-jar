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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class SystemUtils {

    public static final String OS_NAME = getSystemProperty("os.name");
    private static final String OS_NAME_WINDOWS_PREFIX = "Windows";
    public static final boolean IS_OS_WINDOWS = getOSMatches(OS_NAME_WINDOWS_PREFIX);
    public static final List<String> NATIVE_LIB_FILE_TYPES = Arrays.asList(new String[] {"so", "sl", "dylib", "dll", "lib"});

    private SystemUtils() {
        super();
    }

    public static JarFile getCurrentJar(Class<?> classInJar) {
        try {
            File currentJar = new File(classInJar.getProtectionDomain().getCodeSource().getLocation().toURI());

            if (!currentJar.exists() || currentJar.isDirectory()) {
                System.err.println("This application isn't packaged as JAR. Stoping execution.");
                System.exit(1);
            }

            return new JarFile(currentJar);
        } catch (Exception e) {
            throw new ReadingCurrentJarException("A problem while searching the current JAR for class " + classInJar + " occurred.",
                e);
        }
    }

    public static Manifest getCurrentManifest(Class<?> classInJar) {
        try {
            return getCurrentJar(classInJar).getManifest();
        } catch (IOException e) {
            throw new ReadingCurrentManifestException(
                "A problem while searching the manifest belonging to class " + classInJar + " occurred.", e);
        }
    }

    public static String getManifestProperty(Manifest manifest, String propertyName) {
        Map<Object, Object> entries = manifest.getMainAttributes();
        for (Iterator<Object> it = entries.keySet().iterator(); it.hasNext();) {
            Attributes.Name key = (Attributes.Name) it.next();
            String keyName = key.toString();

            if (propertyName.equals(keyName)) {
                return (String) entries.get(key);
            }
        }

        return null;
    }

    public static boolean isDigits(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isJavaLibrary(JarEntry entry) {
        return entry.getName().toLowerCase().endsWith(".jar") || entry.getName().toLowerCase().endsWith(".zip");
    }

    public static boolean isNativeLibrary(JarEntry entry) {
        String name = entry.getName();

        int dotSeperatorPosition = name.lastIndexOf('.');
        if (dotSeperatorPosition <= 0) {
            return false;
        }

        String type = name.substring(dotSeperatorPosition + 1);
        if (NATIVE_LIB_FILE_TYPES.contains(type)) {
            return true;
        }

        return false;
    }

    public static boolean isWebArchive(JarEntry entry) {
        return entry.getName().toLowerCase().endsWith(".war");
    }

    private static boolean getOSMatches(String osNamePrefix) {
        if (OS_NAME == null) {
            return false;
        }
        return OS_NAME.startsWith(osNamePrefix);
    }

    private static String getSystemProperty(String property) {
        try {
            return System.getProperty(property);
        } catch (SecurityException ex) {
            // we are not allowed to look at this property
            System.err.println("Caught a SecurityException reading the system property '" + property
                + "'; the SystemUtils property value will default to null.");
            return null;
        }
    }

    public static class ReadingCurrentJarException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ReadingCurrentJarException(String msg, Exception e) {
            super(msg, e);
        }
    }

    public static class ReadingCurrentManifestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ReadingCurrentManifestException(String msg, Exception e) {
            super(msg, e);
        }
    }
}
