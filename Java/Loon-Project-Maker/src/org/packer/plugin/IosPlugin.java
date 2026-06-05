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

public class IosPlugin implements TargetPlugin {

	@Override
	public void apply(ProjectConfig cfg, Path projectRoot, TemplateEngine engine) throws Exception {
		if (cfg == null) {
			throw new IllegalArgumentException("cfg is null");
		}
		if (engine == null) {
			throw new IllegalArgumentException("engine is null");
		}
		Map<String, String> filters = new HashMap<String, String>();
		filters.put("@{ProjectName}", cfg.projectName != null ? cfg.projectName : "MyGame");
		filters.put("@{ProjectPackage}", cfg.projectPackage != null ? cfg.projectPackage : "org.loon.test");
		filters.put("@{IosDependencies}", buildDeps(cfg));
		filters.put("@{RoboVMVersion}", cfg.robovmVersion != null ? cfg.robovmVersion : "2.3.24");

		Path module = projectRoot.resolve("ios");
		Files.createDirectories(module);

		String buildKts = engine.renderResource("ios/build.gradle.kts.tpl", filters);
		engine.writeString(module.resolve("build.gradle.kts"), buildKts);

		Path libs = module.resolve("libs");
		Files.createDirectories(libs);

		if (cfg.localJars != null) {
			for (int i = 0; i < cfg.localJars.size(); i++) {
				String jar = cfg.localJars.get(i);
				if (jar == null) {
					continue;
				}
				jar = jar.trim();
				if (jar.length() == 0) {
					continue;
				}
				String newName = jar.toLowerCase();
				if (newName.contains("robovm") || !newName.contains("loon")) {
					try {
						engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
					} catch (Exception ex) {
						System.err.println("Copy jar failed: " + jar + " -> " + ex.getMessage());
					}
				}
			}
		}

		String pkg = cfg.projectPackage != null && cfg.projectPackage.length() > 0 ? cfg.projectPackage
				: "org.loon.test";
		String pkgPath = pkg.replace('.', File.separatorChar);
		Path javaDir = module.resolve("src").resolve("main").resolve("java").resolve(pkgPath);
		Files.createDirectories(javaDir);

		int width = (cfg.width > 0) ? cfg.width : 480;
		int height = (cfg.height > 0) ? cfg.height : 320;
		int widthZoom = Math.max(1, (cfg.width > 0) ? cfg.width * 4 / 3 : 640);
		int heightZoom = Math.max(1, (cfg.height > 0) ? cfg.height * 3 / 2 : 480);
		int fps = 60;
		String fontName = "Dialog";
		String appName = cfg.projectName != null && cfg.projectName.length() > 0 ? cfg.projectName : "test";
		String logoPath = "loon_logo.png";

		StringBuilder src = new StringBuilder();
		src.append("package ").append(pkg).append(";\n\n");
		src.append("import loon.LSetting;\n");
		src.append("import loon.Screen;\n");
		src.append("import loon.robovm.Loon;\n");
		src.append("import loon.LazyLoading.Data;\n\n");
		src.append("import org.robovm.apple.foundation.NSAutoreleasePool;\n");
		src.append("import org.robovm.apple.uikit.UIApplication;\n\n");
		src.append("public class " + cfg.mainClass + " extends Loon {\n\n");
		src.append("    @Override\n");
		src.append("    public void onMain() {\n");
		src.append("        LSetting setting = new LSetting();\n");
		src.append("        setting.isFPS = true;\n        ");
		src.append("        setting.isLogo = false;\n");
		src.append("        setting.logoPath = \"").append(escapeForJavaString(logoPath)).append("\";\n");
		src.append("        setting.width = ").append(width).append(";\n");
		src.append("        setting.height = ").append(height).append(";\n");
		src.append("        setting.width_zoom = ").append(widthZoom).append(";\n");
		src.append("        setting.height_zoom = ").append(heightZoom).append(";\n");
		src.append("        setting.fps = ").append(fps).append(";\n");
		src.append("        setting.fontName = \"").append(escapeForJavaString(fontName)).append("\";\n");
		src.append("        setting.appName = \"").append(escapeForJavaString(appName)).append("\";\n");
		src.append("        setting.emulateTouch = false;\n");
		src.append("        register(setting, new Data() {\n\n");
		src.append("            @Override\n");
		src.append("            public Screen onScreen() {\n");
		src.append("                return new ScreenTest();\n");
		src.append("            }\n");
		src.append("        });\n");
		src.append("    }\n\n");
		src.append("    public static void main(String[] args) {\n");
		src.append("        try (NSAutoreleasePool pool = new NSAutoreleasePool()) {\n");
		src.append("            UIApplication.main(args, null, " + cfg.mainClass + ".class);\n");
		src.append("        }\n");
		src.append("    }\n\n");
		src.append("}\n");

		engine.writeString(javaDir.resolve(cfg.mainClass + ".java"), src.toString());
	}

	private String buildDeps(ProjectConfig cfg) {
		StringBuilder sbr = new StringBuilder();
		if (cfg == null)
			return "";
		if (cfg.localJars != null && cfg.localJars.size() > 0) {
			sbr.append("implementation(fileTree(\"libs\") { include(\"*.jar\") })\n");
		}
		if (cfg.mavenDeps != null) {
			for (int i = 0; i < cfg.mavenDeps.size(); i++) {
				String d = cfg.mavenDeps.get(i);
				if (d != null && d.trim().length() > 0) {
					sbr.append("implementation(\"").append(d.trim()).append("\")\n");
				}
			}
		}
		return sbr.toString();
	}

	private String escapeForJavaString(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
