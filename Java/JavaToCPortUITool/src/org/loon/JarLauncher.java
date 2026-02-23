/**
 * Copyright 2008 - 2019 The Loon Game Engine Authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * @project loon
 * @author cping
 * @email：javachenpeng@yahoo.com
 * @version 0.5
 */
package org.loon;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.Enumeration;

public class JarLauncher {

	private static final Logger logger = Logger.getLogger(JarLauncher.class.getName());

	public static void main(String[] args) {
		Properties props = new Properties();
		try (FileInputStream fis = new FileInputStream("config.properties")) {
			props.load(fis);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load configuration file", e);
			return;
		}

		String jarDirPath = props.getProperty("jarDir");
		String mainClassName = props.getProperty("mainClass");
		String mainMethodName = props.getProperty("mainMethod", "main");
		String methodArgsStr = props.getProperty("methodArgs", "");
		String constructorArgsStr = props.getProperty("constructorArgs", "");
		String whitelistStr = props.getProperty("whitelistJars", "");

		if (jarDirPath == null || mainClassName == null) {
			logger.severe("Missing required fields in configuration: jarDir or mainClass");
			return;
		}

		File jarDir = new File(jarDirPath);
		if (!jarDir.exists() || !jarDir.isDirectory()) {
			logger.severe("Specified JAR directory does not exist or is not a directory: " + jarDirPath);
			return;
		}

		File[] jarFiles = jarDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
		if (jarFiles == null || jarFiles.length == 0) {
			logger.warning("No JAR files found in directory: " + jarDirPath);
			return;
		}

		List<String> whitelist = new ArrayList<String>();
		if (!whitelistStr.isEmpty()) {
			for (String w : whitelistStr.split(",")) {
				whitelist.add(w.trim());
			}
		}

		List<URL> jarUrls = new ArrayList<URL>();
		try {
			for (File jarFile : jarFiles) {
				if (!whitelist.isEmpty() && !whitelist.contains(jarFile.getName())) {
					logger.info("Skipping JAR not in whitelist: " + jarFile.getName());
					continue;
				}
				jarUrls.add(jarFile.toURI().toURL());
				logger.info("Loaded JAR: " + jarFile.getName());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to load JAR files", e);
			return;
		}

		try (FileWriter fw = new FileWriter("loaded-classes.log")) {
			for (URL url : jarUrls) {
				try {
					File jarFile = new File(url.toURI());
					try (JarFile jf = new JarFile(jarFile)) {
						logger.info("Scanning JAR: " + jarFile.getName());
						fw.write("JAR: " + jarFile.getName() + "\n");
						Enumeration<JarEntry> entries = jf.entries();
						while (entries.hasMoreElements()) {
							JarEntry entry = entries.nextElement();
							if (entry.getName().endsWith(".class")) {
								String className = entry.getName().replace('/', '.').replace(".class", "");
								logger.fine("Loaded class: " + className);
								fw.write("  " + className + "\n");
							}
						}
					}
				} catch (Exception e) {
					logger.log(Level.WARNING, "Failed to scan JAR: " + url, e);
				}
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to write loaded classes to file", e);
		}

		try (URLClassLoader classLoader = new URLClassLoader(jarUrls.toArray(new URL[0]),
				JarLauncher.class.getClassLoader())) {

			Class<?> mainClass = Class.forName(mainClassName, true, classLoader);

			Object[] ctorArgs = parseArgs(constructorArgsStr);
			Constructor<?> targetCtor = selectBestConstructor(mainClass, ctorArgs);
			Object instance = null;
			if (targetCtor != null) {
				instance = targetCtor.newInstance(ctorArgs);
			} else {
				instance = mainClass.getDeclaredConstructor().newInstance();
			}

			Object[] parsedArgs = parseArgs(methodArgsStr);

			Method targetMethod = selectBestMethod(mainClass, mainMethodName, parsedArgs);
			if (targetMethod == null) {
				logger.severe("No matching method found: " + mainMethodName);
				return;
			}

			if (Modifier.isStatic(targetMethod.getModifiers())) {
				targetMethod.invoke(null, parsedArgs);
			} else {
				targetMethod.invoke(instance, parsedArgs);
			}

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed to invoke entry method", e);
		}
	}

	private static Object[] parseArgs(String argsStr) {
		String[] values = argsStr.isEmpty() ? new String[] {} : argsStr.split(",");
		Object[] parsedArgs = new Object[values.length];
		for (int i = 0; i < values.length; i++) {
			parsedArgs[i] = inferType(values[i].trim());
		}
		return parsedArgs;
	}

	private static Object inferType(String testVal) {
		if (testVal.equalsIgnoreCase("true") || testVal.equalsIgnoreCase("false")) {
			return Boolean.parseBoolean(testVal);
		}
		try {
			return Integer.parseInt(testVal);
		} catch (NumberFormatException e1) {
			try {
				return Long.parseLong(testVal);
			} catch (NumberFormatException e2) {
				try {
					return Double.parseDouble(testVal);
				} catch (NumberFormatException e3) {
					try {
						return Float.parseFloat(testVal);
					} catch (NumberFormatException e4) {
						return testVal;
					}
				}
			}
		}
	}

	private static Constructor<?> selectBestConstructor(Class<?> clazz, Object[] args) {
		Constructor<?> bestMatch = null;
		int bestScore = -1;
		for (Constructor<?> ctor : clazz.getConstructors()) {
			if (ctor.getParameterCount() != args.length) {
				continue;
			}
			Class<?>[] paramTypes = ctor.getParameterTypes();
			int score = 0;
			boolean compatible = true;

			for (int i = 0; i < paramTypes.length; i++) {
				if (isCompatible(args[i], paramTypes[i])) {
					score += getMatchScore(args[i], paramTypes[i]);
				} else {
					compatible = false;
					break;
				}
			}

			if (compatible && score > bestScore) {
				bestScore = score;
				bestMatch = ctor;
			}
		}

		return bestMatch;
	}

	private static Method selectBestMethod(Class<?> clazz, String methodName, Object[] args) {
		Method bestMatch = null;
		int bestScore = -1;

		for (Method m : clazz.getMethods()) {
			if (!m.getName().equals(methodName)) {
				continue;
			}
			if (m.getParameterCount() != args.length) {
				continue;
			}

			Class<?>[] paramTypes = m.getParameterTypes();
			int score = 0;
			boolean compatible = true;
			for (int i = 0; i < paramTypes.length; i++) {
				if (isCompatible(args[i], paramTypes[i])) {
					score += getMatchScore(args[i], paramTypes[i]);
				} else {
					compatible = false;
					break;
				}
			}
			if (compatible && score > bestScore) {
				bestScore = score;
				bestMatch = m;
			}
		}
		return bestMatch;
	}

	private static boolean isCompatible(Object arg, Class<?> paramType) {
		if (arg == null) {
			return !paramType.isPrimitive();
		}
		if (paramType.isInstance(arg)) {
			return true;
		}
		if (paramType == int.class && arg instanceof Integer) {
			return true;
		}
		if (paramType == long.class && arg instanceof Long) {
			return true;
		}
		if (paramType == double.class && arg instanceof Double) {
			return true;
		}
		if (paramType == float.class && arg instanceof Float) {
			return true;
		}
		if (paramType == boolean.class && arg instanceof Boolean) {
			return true;
		}
		if (paramType == String.class && arg instanceof String) {
			return true;
		}
		return false;
	}

	private static int getMatchScore(Object arg, Class<?> paramType) {
		if (arg == null) {
			return 1;
		}
		if (paramType.isInstance(arg)) {
			return 3;
		}
		if (paramType == int.class && arg instanceof Integer) {
			return 3;
		}
		if (paramType == long.class && arg instanceof Long) {
			return 3;
		}
		if (paramType == double.class && arg instanceof Double) {
			return 3;
		}
		if (paramType == String.class && arg instanceof String) {
			return 3;
		}
		return 1;
	}
}
