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
package org.packer.plugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.packer.ProjectConfig;
import org.packer.TargetPlugin;
import org.packer.TemplateEngine;

public class DesktopPlugin implements TargetPlugin {

	@Override
	public void apply(ProjectConfig cfg, Path projectRoot, TemplateEngine engine) throws Exception {
		Map<String, String> filters = new HashMap<String, String>();
		filters.put("@{ProjectName}", cfg.projectName);
		filters.put("@{ProjectPackage}", cfg.projectPackage);
		filters.put("@{DesktopDependencies}", buildDeps(cfg));
		filters.put("@{LwjglVersion}", cfg.lwjglVersion);
		filters.put("@{Width}", String.valueOf(cfg.width));
		filters.put("@{Height}", String.valueOf(cfg.height));
		filters.put("@{MainClass}", cfg.mainClass);

		Path module = projectRoot.resolve("desktop");

		String buildKts = engine.renderResource("desktop/build.gradle.kts.tpl", filters);
		engine.writeString(module.resolve("build.gradle.kts"), buildKts);

		Path libs = module.resolve("libs");
		File libsFile = libs.toFile();
		if (!libsFile.exists()) {
			libsFile.mkdirs();
		}
		if (cfg.localJars != null) {
			for (int i = 0; i < cfg.localJars.size(); i++) {
				String jar = cfg.localJars.get(i);
				if (jar == null) {
					continue;
				}
				jar = jar.trim();
				if (jar.isEmpty()) {
					continue;
				}
				String newName = jar.toLowerCase();
				if (newName.contains("lwjgl") || !newName.contains("loon")) {
					try {
						engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
					} catch (Exception ex) {
						System.err.println("Failed to copy jar: " + jar + " -> " + ex.getMessage());
					}
				}
			}
		}

		String pkgPath = cfg.projectPackage.replace('.', '/');
		Path mainJavaDir = module.resolve("src").resolve("main").resolve("java").resolve(pkgPath);
		if (!Files.exists(mainJavaDir)) {
			Files.createDirectories(mainJavaDir);
		}
		String mainSource;
		try {
			mainSource = engine.renderResource("desktop/src/main/java/Main.java.tpl", filters);
		} catch (Exception e) {
			mainSource = generateDefaultMain(cfg);
		}
		Path mainFile = mainJavaDir.resolve(cfg.mainClass + ".java");
		engine.writeString(mainFile, mainSource);
	}

	private String generateDefaultMain(ProjectConfig cfg) {
		String pkg = cfg.projectPackage != null ? cfg.projectPackage : "org.test";
		String appName = cfg.projectName != null ? cfg.projectName : "test";
		int width = cfg.width;
		int height = cfg.height;
		return "package " + pkg + ";\n\n" + "import loon.lwjgl.Loon;\n"
				+ "import loon.lwjgl.Lwjgl3Game.JavaSetting;\n\n" + "public class " + cfg.mainClass + " {\n\n"
				+ "    public static void main(String[] args) {\n"
				+ "        JavaSetting setting = new JavaSetting();\n" + "        setting.isFPS = true;\n"
				+ "        setting.isDebug = true;\n" + "        setting.isDisplayLog = false;\n"
				+ "        setting.isMemory = true;\n" + "        setting.isLogo = false;\n"
				+ "        setting.logoPath = \"loon_logo.png\";\n" + "        setting.width = " + width + ";\n"
				+ "        setting.height = " + height + ";\n" + "        setting.width_zoom = 800;\n"
				+ "        setting.height_zoom = 600;\n" + "        setting.fontName = \"Dialog\";\n"
				+ "        setting.appName = \"" + escapeForJava(appName) + "\";\n"
				+ "        Loon.register(setting, () -> {\n" + "            return new MainScreen();\n" + "        });\n"
				+ "    }\n" + "}\n";
	}

	private String escapeForJava(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private String buildDeps(ProjectConfig cfg) {
		StringBuilder sbr = new StringBuilder();
		if (cfg.localJars != null && cfg.localJars.size() > 0) {
			sbr.append("implementation(fileTree(\"libs\") { include(\"*.jar\") })\n");
		}
		if (cfg.mavenDeps != null) {
			for (int i = 0; i < cfg.mavenDeps.size(); i++) {
				String d = cfg.mavenDeps.get(i);
				if (d != null && d.trim().length() > 0) {
					sbr.append("implementation(\"").append(d).append("\")\n");
				}
			}
		}
		sbr.append("implementation(\"org.slf4j:slf4j-simple:2.0.7\")\n");
		return sbr.toString();
	}
}