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
package org.packer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.packer.plugin.AndroidPlugin;
import org.packer.plugin.CorePlugin;
import org.packer.plugin.DesktopPlugin;
import org.packer.plugin.GwtPlugin;
import org.packer.plugin.IosPlugin;
import org.packer.plugin.OtherJvmPlugin;
import org.packer.plugin.TeavmCPlugin;
import org.packer.plugin.TeavmJsPlugin;
import org.packer.plugin.TeavmWasmPlugin;

public class ProjectGenerator {

	private final TemplateEngine engine = new TemplateEngine();

	public interface ProgressCallback {
		void log(String msg);
	}

	public void generate(ProjectConfig cfg, ProgressCallback cb) throws Exception {
		Path out = Paths.get(cfg.outDir);
		if (Files.exists(out)) {
			deleteRecursively(out);
		}
		Files.createDirectories(out);

		Map<String, String> filters = new HashMap<String, String>();
		filters.put("@{ProjectName}", cfg.projectName);
		filters.put("@{ProjectPackage}", cfg.projectPackage);
		filters.put("@{ProjectMain}", cfg.mainClass);
		filters.put("@{Width}", String.valueOf(cfg.width));
		filters.put("@{Height}", String.valueOf(cfg.height));
		filters.put("@{AndroidMinSdk}", "21");
		filters.put("@{AndroidTargetSdk}", "33");
		filters.put("@{LwjglVersion}", cfg.lwjglVersion != null ? cfg.lwjglVersion : "3.4.1");
		filters.put("@{GraalHelperVersion}", cfg.graalHelperVersion != null ? cfg.graalHelperVersion : "2.0.1");
		filters.put("@{EnableGraalNative}", String.valueOf(cfg.enableGraalNative));
		filters.put("@{RoboVMVersion}", cfg.robovmVersion != null ? cfg.robovmVersion : "2.3.24");
		filters.put("@{LoonVersion}", cfg.loonVersion != null ? cfg.loonVersion : "0.5");
		filters.put("@{ProjectVersion}", cfg.projectVersion != null ? cfg.projectVersion : "1.0.0");

		// 动态写入项目设定
		cb.log("Writing settings.gradle.kts");
		StringBuilder includes = new StringBuilder();
		for (int i = cfg.targets.size() - 1; i > -1; i--) {
			String tag = cfg.targets.get(i);
			if (tag != null) {
				includes.append("include(\":").append(tag).append("\")\n");
			}
		}
		filters.put("@{Includes}", includes.toString());

		// 渲染 settings.gradle.kts.tpl
		String settingsContent = engine.renderResource("settings.gradle.kts.tpl", filters);
		engine.writeString(out.resolve("settings.gradle.kts"), settingsContent);

		cb.log("Writing root build.gradle.kts");
		engine.writeString(out.resolve("build.gradle.kts"),
				engine.renderResource("root.build.gradle.kts.tpl", filters));

		cb.log("Writing gradle.properties");
		try {
			engine.writeString(out.resolve("gradle.properties"),
					engine.renderResource("gradle.properties.tpl", filters));
		} catch (Exception e) {
			cb.log("gradle.properties.tpl not found or failed to render: " + e.getMessage());
		}

		cb.log("Writing Gradle wrapper scripts");
		try {
			String gradlewSh = engine.renderResource("gradlew.tpl", filters);
			engine.writeString(out.resolve("gradlew"), gradlewSh);
			try {
				File gw = out.resolve("gradlew").toFile();
				gw.setExecutable(true, false);
			} catch (Exception ex) {
			}
		} catch (Exception e) {
			cb.log("gradlew.tpl not found or failed to render: " + e.getMessage());
		}

		try {
			String gradlewBat = engine.renderResource("gradlew.bat.tpl", filters);
			engine.writeString(out.resolve("gradlew.bat"), gradlewBat);
		} catch (Exception e) {
			cb.log("gradlew.bat.tpl not found or failed to render: " + e.getMessage());
		}

		cb.log("Copying templates/gradle -> output/gradle");
		try {
			copyResourceFolder("templates/gradle", out.resolve("gradle"), cb);
			cb.log("Copied templates/gradle to " + out.resolve("gradle").toAbsolutePath());
		} catch (Exception e) {
			cb.log("Failed to copy templates/gradle: " + e.getMessage());
		}

		Map<String, TargetPlugin> registry = new HashMap<String, TargetPlugin>();
		registry.put("desktop", new DesktopPlugin());
		registry.put("android", new AndroidPlugin());
		registry.put("ios", new IosPlugin());
		registry.put("teavm-c", new TeavmCPlugin());
		registry.put("teavm-js", new TeavmJsPlugin());
		registry.put("teavm-wasm", new TeavmWasmPlugin());
		registry.put("other-jvm", new OtherJvmPlugin());
		registry.put("gwt", new GwtPlugin());
		registry.put("core", new CorePlugin());
		List<String> targets = cfg.targets.stream().distinct().collect(Collectors.toList());
		for (String t : targets) {
			TargetPlugin plugin = registry.get(t);
			if (plugin != null) {
				cb.log("Applying target: " + t);
				plugin.apply(cfg, out, engine);
			} else {
				cb.log("Unknown target: " + t);
			}
		}

		cb.log("Generation complete: " + out.toAbsolutePath());
	}

	private void copyResourceFolder(String resourceFolderPath, Path targetRoot, ProgressCallback cb)
			throws IOException, URISyntaxException {
		if (resourceFolderPath == null || resourceFolderPath.isEmpty()) {
			return;
		}
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		URL url = cl.getResource(resourceFolderPath);
		if (url == null) {
			cb.log("Resource folder not found on classpath: " + resourceFolderPath);
			return;
		}
		String protocol = url.getProtocol();
		if ("file".equals(protocol)) {
			Path folder = Paths.get(url.toURI());
			Files.walk(folder).forEach(src -> {
				try {
					Path rel = folder.relativize(src);
					Path dest = targetRoot.resolve(rel.toString());
					if (Files.isDirectory(src)) {
						if (!Files.exists(dest))
							Files.createDirectories(dest);
					} else {
						if (!Files.exists(dest.getParent())) {
							Files.createDirectories(dest.getParent());
						}
						Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
						if (dest.getFileName().toString().equals("gradlew")) {
							dest.toFile().setExecutable(true, false);
						}
					}
				} catch (Exception e) {
					cb.log("Failed to copy resource file: " + src + " -> " + e.getMessage());
				}
			});
		} else if ("jar".equals(protocol)) {
			JarURLConnection jarConn = (JarURLConnection) url.openConnection();
			JarFile jar = jarConn.getJarFile();
			Enumeration<JarEntry> entries = jar.entries();
			String prefix = resourceFolderPath.endsWith("/") ? resourceFolderPath : resourceFolderPath + "/";
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith(prefix)) {
					continue;
				}
				String relPath = name.substring(prefix.length());
				if (relPath.isEmpty()) {
					continue;
				}
				Path dest = targetRoot.resolve(relPath);
				if (entry.isDirectory()) {
					if (!Files.exists(dest)) {
						Files.createDirectories(dest);
					}
				} else {
					if (!Files.exists(dest.getParent())) {
						Files.createDirectories(dest.getParent());
					}
					try (InputStream is = new BufferedInputStream(jar.getInputStream(entry))) {
						Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
					} catch (Exception e) {
						cb.log("Failed to extract jar entry: " + name + " -> " + e.getMessage());
					}
					if (dest.getFileName().toString().equals("gradlew")) {
						dest.toFile().setExecutable(true, false);
					}
				}
			}
		} else {
			cb.log("Unsupported resource protocol: " + protocol + " for " + resourceFolderPath);
		}
	}

	private void deleteRecursively(Path p) throws Exception {
		if (!Files.exists(p)) {
			return;
		}
		Files.walk(p).sorted(Comparator.reverseOrder()).forEach(path -> {
			try {
				Files.delete(path);
			} catch (Exception e) {
			}
		});
	}
}