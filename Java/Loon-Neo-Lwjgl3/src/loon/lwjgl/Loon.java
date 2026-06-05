/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
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
package loon.lwjgl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import loon.LSystem;
import loon.LSetting;
import loon.LGame;
import loon.LazyLoading;
import loon.Platform;
import loon.events.KeyMake;
import loon.events.SysInput;
import loon.utils.PathUtils;

import org.lwjgl.system.JNI;
import org.lwjgl.system.macosx.ObjCRuntime;
import org.lwjgl.system.macosx.LibC;

public class Loon implements Platform {

	private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
	private static final String RESTART_FLAG_PROP = "loon.jvm.restarted";
	private static final String RESTART_LOCK = "loon_restart.lock";

	private static String _prevTmpDir;
	private static String _prevUser;
	private static Path processTempDir;

	private static Path extractedNativesDir;

	private Lwjgl3Game game;

	public Loon(LSetting config) {
		this.game = new Lwjgl3Game(this, config);
	}

	public static long getCurrentThreadIdFallback() {
		try {
			Method m = Thread.class.getMethod("getId");
			Object res = m.invoke(Thread.currentThread());
			if (res instanceof Number) {
				return ((Number) res).longValue();
			}
		} catch (Throwable ignored) {
		}
		try {
			Method m2 = Thread.class.getMethod("threadId");
			Object res2 = m2.invoke(Thread.currentThread());
			if (res2 instanceof Number) {
				return ((Number) res2).longValue();
			}
		} catch (Throwable ignored) {
		}
		return System.currentTimeMillis();
	}

	public static boolean isLinuxAndNvidia() {
		try {
			String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
			if (!os.contains("linux")) {
				return false;
			}
			Path p1 = Paths.get("/proc/driver/nvidia");
			if (Files.exists(p1)) {
				return true;
			}
			Path p2 = Paths.get("/proc/driver/nvidia/version");
			if (Files.exists(p2)) {
				return true;
			}
			File driver = new File("/proc/driver");
			String[] files = driver.list();
			if (files != null) {
				for (String f : files) {
					if (f != null && f.toLowerCase(Locale.ROOT).contains("nvidia")) {
						return true;
					}
				}
			}
		} catch (Throwable t) {
			LSystem.w("NVIDIA detection failed: " + t.getMessage());
		}
		return false;
	}

	/**
	 * 尝试在运行时为当前进程创建独立的临时目录并设置java.io.tmpdir
	 */
	private static boolean ensureProcessTempDir() {
		_prevTmpDir = System.getProperty("java.io.tmpdir");
		_prevUser = System.getProperty("user.name");
		try {
			String pid = Long.toString(getCurrentThreadIdFallback());
			String base = _prevTmpDir != null ? _prevTmpDir : System.getProperty("user.home");
			String dirName = "loon-lwjgl-temp-" + pid;
			Path tmp = Paths.get(base, dirName);
			Files.createDirectories(tmp);
			try {
				tmp.toFile().setWritable(true, false);
				tmp.toFile().setReadable(true, false);
			} catch (Exception ignore) {
			}
			processTempDir = tmp;
			System.setProperty("java.io.tmpdir", tmp.toAbsolutePath().toString());
			System.setProperty("user.name",
					("User_" + (_prevUser != null ? _prevUser.hashCode() : 0) + "_Loon").replace('.', '_'));
			LSystem.info("Set java.io.tmpdir to " + tmp);
			return true;
		} catch (Exception e) {
			LSystem.w("Failed to create process temp dir: " + e.getMessage());
			return false;
		}
	}

	private static void restoreTmpDir() {
		try {
			if (_prevTmpDir != null) {
				System.setProperty("java.io.tmpdir", _prevTmpDir);
			}
			if (_prevUser != null) {
				System.setProperty("user.name", _prevUser);
			}
			LSystem.info("Restored java.io.tmpdir and user.name");
		} catch (Exception e) {
			LSystem.w("Failed to restore tmpdir/user: " + e.getMessage());
		}
	}

	public static boolean fixJVMTempDir() {
		final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (osName.contains("windows")) {
			String fixProgramData = System.getenv("ProgramData");
			if (fixProgramData == null || fixProgramData.length() == 0) {
				fixProgramData = "C:\\Temp\\";
			}
			_prevTmpDir = System.getProperty("java.io.tmpdir", fixProgramData);
			_prevUser = System.getProperty("user.name", "loon_temp_user");
			final String tempDir = PathUtils.normalize(PathUtils.getCombinePaths(fixProgramData, "loon-lwjgl-temp"));
			try {
				File td = new File(tempDir);
				if (!td.exists()) {
					td.mkdirs();
				}
				System.setProperty("java.io.tmpdir", tempDir);
				System.setProperty("user.name",
						("user_" + _prevUser.hashCode() + "_loon" + LSystem.getVersion()).replace('.', '_'));
				LSystem.info("Windows: set java.io.tmpdir to " + tempDir);
				return false;
			} catch (Throwable t) {
				LSystem.w("Windows temp dir fix failed: " + t.getMessage());
				return false;
			}
		}
		if (!osName.contains("mac")) {
			if (!isLinuxAndNvidia()) {
				return false;
			}
			if ("0".equals(System.getenv("__GL_THREADED_OPTIMIZATIONS"))) {
				return false;
			}
		}

		if (!System.getProperty("org.graalvm.nativeimage.imagecode", "").isEmpty()) {
			return false;
		}

		if (osName.contains("mac")) {
			try {
				if ("true".equals(System.getProperty("jvmIsRestarted")) || Boolean.getBoolean(RESTART_FLAG_PROP)) {
					return false;
				}
				boolean needRestart = false;
				try {
					long objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
					long NSThread = ObjCRuntime.objc_getClass("NSThread");
					long currentThread = JNI.invokePPP(NSThread, ObjCRuntime.sel_getUid("currentThread"), objc_msgSend);
					boolean isMainThread = JNI.invokePPZ(currentThread, ObjCRuntime.sel_getUid("isMainThread"),
							objc_msgSend);
					if (!isMainThread) {
						needRestart = true;
					}
				} catch (Throwable t) {
					LSystem.info("ObjCRuntime not available or check failed: " + t.getMessage());
					needRestart = false;
				}

				if (!needRestart) {
					return false;
				}

				long pid = LibC.getpid();
				if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) {
					return false;
				}

				Path lockFile = Paths.get(System.getProperty("java.io.tmpdir"), RESTART_LOCK);
				FileChannel ch = null;
				FileLock lock = null;
				try {
					ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					lock = ch.tryLock();
					if (lock == null) {
						LSystem.info("Another process is handling mac restart, skipping.");
						return false;
					}

					final String jvm_args = "jvmIsRestarted";
					final String separator = System.getProperty("file.separator", "/");
					final String javaExecPath = System.getProperty("java.home") + separator + "bin" + separator
							+ "java";
					if (!(new java.io.File(javaExecPath)).exists()) {
						LSystem.w("Java exec not found: " + javaExecPath);
						return false;
					}

					final ArrayList<String> jvmArgs = new ArrayList<String>();
					jvmArgs.add(javaExecPath);
					jvmArgs.add("-XstartOnFirstThread");
					jvmArgs.add("-D" + jvm_args + "=true");
					jvmArgs.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
					jvmArgs.add("-cp");
					jvmArgs.add(System.getProperty("java.class.path"));

					String mainClass = System.getenv("JAVA_MAIN_CLASS_" + pid);
					if (mainClass == null) {
						final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
						if (trace.length > 0) {
							mainClass = trace[trace.length - 1].getClassName();
						} else {
							LSystem.w("Cannot determine main class for restart.");
							return false;
						}
					}
					jvmArgs.add(mainClass);

					try {
						ProcessBuilder processBuilder = new ProcessBuilder(jvmArgs);
						Map<String, String> env = processBuilder.environment();
						env.put("JAVA_STARTED_ON_FIRST_THREAD_" + pid, "1");
						processBuilder.inheritIO();
						processBuilder.start();
						LSystem.info("Started mac restart process with -XstartOnFirstThread");
						System.exit(0);
					} catch (Exception e) {
						LSystem.w("Failed to start restarted JVM: " + e.getMessage());
					}
				} catch (IOException ioe) {
					LSystem.w("Failed to acquire restart lock: " + ioe.getMessage());
				} finally {
					try {
						if (lock != null)
							lock.release();
					} catch (Throwable ignore) {
					}
					try {
						if (ch != null)
							ch.close();
					} catch (Throwable ignore) {
					}
				}
			} catch (Throwable e) {
				LSystem.w("mac restart attempt failed: " + e.getMessage());
			}
		}

		return true;
	}

	/**
	 * 将资源从classpath提取到进程临时目录并加载
	 *
	 * @param resourcePath
	 * @param libBaseName
	 * @throws IOException
	 */
	public static void loadNativeResource(String resourcePath, String libBaseName) throws IOException {
		int attempts = 0;
		int maxAttempts = 3;
		long[] backoff = new long[] { 100, 300, 900 };
		IOException lastEx = null;

		while (attempts < maxAttempts) {
			attempts++;
			try {
				if (processTempDir == null) {
					ensureProcessTempDir();
				}
				String pid = Long.toString(getCurrentThreadIdFallback());
				String suffix = "-" + pid + "-" + Long.toHexString(System.nanoTime()).substring(0, 6);

				String mapped = System.mapLibraryName(libBaseName);
				String fileName = mapped + suffix;
				File outFile = processTempDir.resolve(fileName).toFile();

				InputStream in = Loon.class.getResourceAsStream(resourcePath);
				if (in == null) {
					throw new FileNotFoundException("Resource not found: " + resourcePath);
				}
				OutputStream out = null;
				try {
					out = new FileOutputStream(outFile);
					byte[] buffer = new byte[8192];
					int len;
					while ((len = in.read(buffer)) != -1) {
						out.write(buffer, 0, len);
					}
				} finally {
					try {
						in.close();
					} catch (IOException ignored) {
					}
					if (out != null) {
						try {
							out.close();
						} catch (IOException ignored) {
						}
					}
				}
				outFile.deleteOnExit();
				System.load(outFile.getAbsolutePath());
				LSystem.info("Loaded native " + libBaseName + " from " + outFile);
				return;
			} catch (IOException ioe) {
				lastEx = ioe;
				LSystem.w("Attempt " + attempts + " to load native failed: " + ioe.getMessage());
			} catch (UnsatisfiedLinkError ule) {
				lastEx = new IOException("UnsatisfiedLinkError: " + ule.getMessage(), ule);
				LSystem.w("Attempt " + attempts + " to load native failed: " + ule.getMessage());
			}
			try {
				Thread.sleep(backoff[Math.min(attempts - 1, backoff.length - 1)]);
			} catch (InterruptedException ignored) {
			}
		}
		throw lastEx != null ? lastEx : new IOException("Unknown native load failure for " + libBaseName);
	}

	/**
	 * 校验目录是否存在且包含当前平台的有效本地库文件
	 */
	private static boolean isDirectoryHasValidNatives(Path dir) {
		if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
			return false;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path path : stream) {
				if (Files.isRegularFile(path) && isNativeFileForCurrentPlatform(path.getFileName().toString())) {
					return true;
				}
			}
		} catch (Exception e) {
			LSystem.w("Failed to check native files in directory: " + dir + ", error: " + e.getMessage());
		}
		return false;
	}

	private static void prepareAndSetNativeLibraryPath() {
		try {
			if (processTempDir == null) {
				ensureProcessTempDir();
			}
			extractedNativesDir = processTempDir.resolve("loon-extracted-natives");
			Files.createDirectories(extractedNativesDir);

			// 解压本地库文件
			extractNativeResourcesFromClasspath(extractedNativesDir);

			boolean hasValidNatives = isDirectoryHasValidNatives(extractedNativesDir);
			if (hasValidNatives) {
				setLwjglLibraryPath(extractedNativesDir.toAbsolutePath().toString());
				tryPreloadExtractedLibs(extractedNativesDir);
				LSystem.info("Prepared native libs in " + extractedNativesDir);
			} else {
				LSystem.warn("No valid native files extracted in: " + extractedNativesDir
						+ ", skip setting LWJGL library path");
			}

			cleanupOnExit(extractedNativesDir);
		} catch (Throwable t) {
			LSystem.w("prepareAndSetNativeLibraryPath failed: " + t.getMessage());
			try {
				String nativePath = (extractedNativesDir != null && Files.exists(extractedNativesDir))
						? extractedNativesDir.toAbsolutePath().toString()
						: null;
				setJavaLibraryPathFallback(nativePath);
			} catch (Throwable ignored) {
			}
		}
	}

	private static void extractNativeResourcesFromClasspath(Path targetDir) {
		if (targetDir == null || !Files.isDirectory(targetDir)) {
			return;
		}
		String[] prefixes = new String[] { "/natives/", "/native/", "/lib/", "/libs/" };
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		for (int pi = 0; pi < prefixes.length; pi++) {
			String prefix = prefixes[pi];
			try {
				Enumeration<URL> urls = cl.getResources(prefix);
				while (urls.hasMoreElements()) {
					URL url = urls.nextElement();
					try {
						String protocol = url.getProtocol();
						if ("file".equals(protocol)) {
							File dir = new File(url.toURI());
							if (dir.isDirectory()) {
								File[] files = dir.listFiles();
								if (files != null) {
									for (int i = 0; i < files.length; i++) {
										File f = files[i];
										if (isNativeFileForCurrentPlatform(f.getName())) {
											Path dest = targetDir.resolve(f.getName());
											try {
												Files.copy(f.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
												dest.toFile().setExecutable(true, false);
											} catch (Throwable ignore) {
											}
										}
									}
								}
							}
						} else {
							String path = url.getPath();
							String jarPath = path;
							if (path.startsWith("file:")) {
								int idx = path.indexOf("!");
								if (idx > 0) {
									jarPath = path.substring(5, idx);
								} else {
									jarPath = path.substring(5);
								}
							} else {
								int idx = path.indexOf("!");
								if (idx > 0) {
									jarPath = path.substring(0, idx);
								}
							}
							jarPath = URLDecoder.decode(jarPath, "UTF-8");
							JarFile jar = null;
							try {
								jar = new JarFile(jarPath);
								Enumeration<JarEntry> entries = jar.entries();
								while (entries.hasMoreElements()) {
									JarEntry entry = entries.nextElement();
									String name = entry.getName();
									String prefixNoSlash = prefix.startsWith("/") ? prefix.substring(1) : prefix;
									if (name.startsWith(prefixNoSlash) && !entry.isDirectory()) {
										String fileName = name.substring(name.lastIndexOf('/') + 1);
										if (isNativeFileForCurrentPlatform(fileName)) {
											InputStream in = null;
											try {
												in = jar.getInputStream(entry);
												Path out = targetDir.resolve(fileName);
												Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
												out.toFile().setExecutable(true, false);
											} catch (Throwable ioe) {
												LSystem.w("Failed to extract " + name + ": " + ioe.getMessage());
											} finally {
												try {
													if (in != null)
														in.close();
												} catch (Throwable ignore) {
												}
											}
										}
									}
								}
							} catch (Throwable je) {
							} finally {
								try {
									if (jar != null)
										jar.close();
								} catch (Throwable ignore) {
								}
							}
						}
					} catch (Throwable t) {
						LSystem.w("Error scanning resource " + url + ": " + t.getMessage());
					}
				}
			} catch (IOException e) {
			}
		}

		try {
			String classpath = System.getProperty("java.class.path", "");
			String[] entries = classpath.split(File.pathSeparator);
			for (int ei = 0; ei < entries.length; ei++) {
				String cpEntry = entries[ei];
				if (cpEntry.endsWith(".jar")) {
					JarFile jar = null;
					try {
						jar = new JarFile(cpEntry);
						Enumeration<JarEntry> jentries = jar.entries();
						while (jentries.hasMoreElements()) {
							JarEntry entry = jentries.nextElement();
							String name = entry.getName();
							if ((name.contains("/natives/") || name.contains("/native/") || name.contains("/lib/"))
									&& !entry.isDirectory()) {
								String fileName = name.substring(name.lastIndexOf('/') + 1);
								if (isNativeFileForCurrentPlatform(fileName)) {
									InputStream in = null;
									try {
										in = jar.getInputStream(entry);
										Path out = targetDir.resolve(fileName);
										Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
										out.toFile().setExecutable(true, false);
									} catch (Throwable ioe) {
										LSystem.w("Failed to extract " + name + " from " + cpEntry + ": "
												+ ioe.getMessage());
									} finally {
										try {
											if (in != null)
												in.close();
										} catch (Throwable ignore) {
										}
									}
								}
							}
						}
					} catch (Throwable ignored) {
					} finally {
						try {
							if (jar != null)
								jar.close();
						} catch (Throwable ignore) {
						}
					}
				} else {
					File f = new File(cpEntry);
					if (f.isDirectory()) {
						copyNativeFilesFromDir(f, targetDir);
					}
				}
			}
		} catch (Throwable t) {
			LSystem.w("Classpath scan failed: " + t.getMessage());
		}
	}

	private static void copyNativeFilesFromDir(File srcDir, Path targetDir) {
		if (srcDir == null || !srcDir.exists()) {
			return;
		}
		File[] files = srcDir.listFiles();
		if (files == null) {
			return;
		}
		for (int i = 0; i < files.length; i++) {
			File f = files[i];
			if (f.isDirectory()) {
				copyNativeFilesFromDir(f, targetDir);
			} else {
				String fn = f.getName();
				if (isNativeFileForCurrentPlatform(fn)) {
					try {
						Path dest = targetDir.resolve(fn);
						Files.copy(f.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
						dest.toFile().setExecutable(true, false);
					} catch (Throwable ignore) {
					}
				}
			}
		}
	}

	private static boolean isNativeFileForCurrentPlatform(String fileName) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		fileName = fileName.toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return fileName.endsWith(".dll");
		} else if (os.contains("mac") || os.contains("darwin")) {
			return fileName.endsWith(".dylib") || fileName.endsWith(".jnilib");
		} else {
			return fileName.endsWith(".so");
		}
	}

	private static void setLwjglLibraryPath(String path) {
		if (path == null || path.length() == 0 || !Files.exists(Paths.get(path))) {
			LSystem.warn("Skip invalid LWJGL library path: " + path);
			return;
		}
		System.setProperty("org.lwjgl.librarypath", path);
		LSystem.info("Set org.lwjgl.librarypath = " + path);
	}

	private static void tryPreloadExtractedLibs(Path dir) {
		if (dir == null || !Files.exists(dir)) {
			return;
		}
		DirectoryStream<Path> ds = null;
		try {
			ds = Files.newDirectoryStream(dir);
			for (Path p : ds) {
				String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
				if (name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".dylib")
						|| name.endsWith(".jnilib")) {
					try {
						System.load(p.toAbsolutePath().toString());
						LSystem.info("Preloaded native: " + p.getFileName());
					} catch (UnsatisfiedLinkError ule) {
						LSystem.w("Preload failed for " + p.getFileName() + ": " + ule.getMessage());
					} catch (Throwable t) {
					}
				}
			}
		} catch (Throwable ignored) {
		} finally {
			try {
				if (ds != null)
					ds.close();
			} catch (Throwable ignore) {
			}
		}
	}

	/**
	 * 仅有效路径才执行兜底
	 */
	private static void setJavaLibraryPathFallback(String path) {
		if (path == null || path.length() == 0 || !Files.exists(Paths.get(path))) {
			LSystem.warn("Skip invalid java.library.path fallback: " + path);
			return;
		}
		try {
			String current = System.getProperty("java.library.path", "");
			String newPath = path + File.pathSeparator + current;
			System.setProperty("java.library.path", newPath);
			Field field = ClassLoader.class.getDeclaredField("sys_paths");
			field.setAccessible(true);
			field.set(null, null);
			LSystem.info("Updated java.library.path fallback to include " + path);
		} catch (Throwable t) {
			LSystem.w("Failed to set java.library.path fallback: " + t.getMessage());
		}
	}

	private static void cleanupOnExit(final Path dir) {
		if (dir == null) {
			return;
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				deleteDirectory(dir.toFile());
				LSystem.info("Cleaned up extracted natives " + dir);
			} catch (Exception e) {
				LSystem.w("Failed to cleanup extracted natives: " + e.getMessage());
			}
		}, "Loon-Native-Cleaner"));
	}

	private static void deleteDirectory(File dir) {
		if (dir == null)
			return;
		if (dir.isDirectory()) {
			File[] files = dir.listFiles();
			if (files != null) {
				for (int i = 0; i < files.length; i++) {
					deleteDirectory(files[i]);
				}
			}
		}
		try {
			dir.delete();
		} catch (Throwable ignore) {
		}
	}

	public static void register(LSetting setting, LazyLoading.Data lazy) {
		register(setting, lazy, true);
	}

	public static void register(LSetting setting, LazyLoading.Data lazy, boolean fixTempDir) {
		if (!REGISTERED.compareAndSet(false, true)) {
			LSystem.info("Loon already registered, skipping duplicate register call.");
			return;
		}
		boolean createdTemp = false;
		try {
			if (fixTempDir) {
				createdTemp = ensureProcessTempDir();
			}
			try {
				// 尝试准备并设置本地库路径（自动发现并解压）
				prepareAndSetNativeLibraryPath();
			} catch (Throwable t) {
				LSystem.w("prepareAndSetNativeLibraryPath threw: " + t.getMessage());
			}
			try {
				fixJVMTempDir();
			} catch (Throwable t) {
				LSystem.w("fixJVMTempDir threw: " + t.getMessage());
			}
			try {
				final Loon plat = new Loon(setting);
				plat.game.register(lazy.onScreen());
				plat.game.reset();
			} catch (Throwable t) {
				LSystem.w("Loon platform creation failed: " + t.getMessage());
			}
		} catch (Throwable t) {
			LSystem.e("Loon.register failed: " + t.getMessage());
			throw new RuntimeException("Loon initialization failed", t);
		} finally {
			if (fixTempDir && createdTemp) {
				restoreTmpDir();
				if (processTempDir != null) {
					Runtime.getRuntime().addShutdownHook(new Thread(() -> {
						try {
							deleteDirectory(processTempDir.toFile());
							LSystem.info("Cleaned up process temp dir " + processTempDir);
						} catch (Exception e) {
							LSystem.w("Failed to cleanup temp dir: " + e.getMessage());
						}
					}, "Loon-Temp-Cleaner"));
				}
			}
		}
	}

	@Override
	public int getContainerWidth() {
		return ((Lwjgl3Graphics) game.graphics()).screenSize().getWidth();
	}

	@Override
	public int getContainerHeight() {
		return ((Lwjgl3Graphics) game.graphics()).screenSize().getHeight();
	}

	@Override
	public void close() {
		System.exit(-1);
	}

	@Override
	public Orientation getOrientation() {
		if (getContainerHeight() > getContainerWidth()) {
			return Orientation.Portrait;
		} else {
			return Orientation.Landscape;
		}
	}

	@Override
	public void sysText(final SysInput.TextEvent event, final KeyMake.TextType textType, final String label,
			final String initVal) {
		if (Lwjgl3Game.isMacOS()) {
			return;
		}
		if (game == null) {
			event.cancel();
			return;
		}
		game.invokeAsync(new Runnable() {
			@Override
			public void run() {
				final String output = (String) javax.swing.JOptionPane.showInputDialog(null, label, "",
						javax.swing.JOptionPane.QUESTION_MESSAGE, null, null, initVal);
				if (output != null) {
					event.input(output);
				} else {
					event.cancel();
				}
			}
		});
	}

	@Override
	public void sysDialog(final SysInput.ClickEvent event, final String title, final String text, final String ok,
			final String cancel) {
		if (Lwjgl3Game.isMacOS()) {
			return;
		}
		if (game == null) {
			event.cancel();
			return;
		}
		game.invokeAsync(new Runnable() {
			@Override
			public void run() {
				int optType = javax.swing.JOptionPane.OK_CANCEL_OPTION;
				int msgType = cancel == null ? javax.swing.JOptionPane.INFORMATION_MESSAGE
						: javax.swing.JOptionPane.QUESTION_MESSAGE;
				Object[] options = (cancel == null) ? new Object[] { ok } : new Object[] { ok, cancel };
				Object defOption = (cancel == null) ? ok : cancel;
				int result = javax.swing.JOptionPane.showOptionDialog(null, text, title, optType, msgType, null,
						options, defOption);
				if (result == 0) {
					event.clicked();
				} else {
					event.cancel();
				}
			}
		});
	}

	@Override
	public LGame getGame() {
		return game;
	}

}
