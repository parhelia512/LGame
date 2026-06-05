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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.packer.ProjectConfig;
import org.packer.TargetPlugin;
import org.packer.TemplateEngine;
import java.nio.file.Files;

public class GwtPlugin implements TargetPlugin {

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
		filters.put("@{ProjectPackage}", cfg.projectPackage != null ? cfg.projectPackage : "loon.test");

		String gwtModule = (cfg.projectPackage != null && cfg.projectPackage.length() > 0)
				? cfg.projectPackage + ".GwtModule"
				: "com.example.GwtModule";
		filters.put("@{GwtModule}", gwtModule);

		filters.put("@{GwtVersion}", cfg.gwtVersion != null ? cfg.gwtVersion : "2.9.0");

		Path module = projectRoot.resolve("gwt");
		Files.createDirectories(module);

		String buildKts = engine.renderResource("gwt/build.gradle.kts.tpl", filters);
		engine.writeString(module.resolve("build.gradle.kts"), buildKts);

		String moduleXml = engine.renderResource("gwt/GwtModule.gwt.xml.tpl", filters);
		String modulePath = filters.get("@{GwtModule}").replace('.', '/');
		Path resourcesDir = module.resolve("src").resolve("main").resolve("resources");
		Files.createDirectories(resourcesDir);
		engine.writeString(resourcesDir.resolve(modulePath + ".gwt.xml"), moduleXml);

		if (cfg.localJars != null && cfg.localJars.size() > 0) {
			Path libs = module.resolve("libs");
			Files.createDirectories(libs);
			for (int i = 0; i < cfg.localJars.size(); i++) {
				String jar = cfg.localJars.get(i);
				if (jar == null) {
					continue;
				}
				jar = jar.trim();
				if (jar.length() == 0)
					continue;
				String newName = jar.toLowerCase();
				if (newName.contains("gwt") || !newName.contains("loon")) {
					try {
						engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
					} catch (Exception ex) {
						System.err.println("Copy jar failed: " + jar + " -> " + ex.getMessage());
					}
				}
			}
		}

		String pkg = cfg.projectPackage != null && cfg.projectPackage.length() > 0 ? cfg.projectPackage : "org.test";
		Path javaRoot = module.resolve("src").resolve("main").resolve("java");
		Path pkgPath = javaRoot.resolve(pkg.replace('.', File.separatorChar));
		Files.createDirectories(pkgPath);

		int width = (cfg.width > 0) ? cfg.width : 480;
		int height = (cfg.height > 0) ? cfg.height : 320;
		int widthZoom = Math.max(1, (cfg.width > 0) ? cfg.width * 4 / 3 : 640);
		int heightZoom = Math.max(1, (cfg.height > 0) ? cfg.height * 3 / 2 : 480);
		int fps = 60;
		String fontName = "Dialog";
		String rootId = pkg + ".Main";

		StringBuilder mainSrc = new StringBuilder();
		mainSrc.append("package ").append(pkg).append(";\n\n");
		mainSrc.append("import loon.Stage;\n");
		mainSrc.append("import loon.html5.gwt.GWTGame.GWTSetting;\n");
		mainSrc.append("import loon.html5.gwt.GWTGame.Repaint;\n");
		mainSrc.append("import loon.html5.gwt.Loon;\n");
		mainSrc.append("import loon.LazyLoading.Data;\n");
		mainSrc.append("import loon.Screen;\n\n");
		mainSrc.append("public class " + cfg.mainClass + " extends Loon {\n\n");
		mainSrc.append("    @Override\n");
		mainSrc.append("    public void onMain() {\n\n");
		mainSrc.append("        GWTSetting setting = new GWTSetting();\n");
		mainSrc.append("        setting.fps = ").append(fps).append(";\n");
		mainSrc.append("        setting.isDebug = true;\n");
		mainSrc.append("        setting.isDisplayLog = false;\n");
		mainSrc.append("        // source size\n");
		mainSrc.append("        setting.width = ").append(width).append(";\n");
		mainSrc.append("        setting.height = ").append(height).append(";\n");
		mainSrc.append("        // target size\n");
		mainSrc.append("        setting.width_zoom = ").append(widthZoom).append(";\n");
		mainSrc.append("        setting.height_zoom = ").append(heightZoom).append(";\n");
		mainSrc.append("        setting.repaint = Repaint.AnimationScheduler;\n");
		mainSrc.append("        setting.isFPS = true;\n");
		mainSrc.append("        setting.fontName = \"").append(escapeForJavaString(fontName)).append("\";\n");
		mainSrc.append("        setting.isConsoleLog = true;\n");
		mainSrc.append("        setting.rootId = \"").append(escapeForJavaString(rootId)).append("\";\n");
		mainSrc.append("        // setting.jsloadRes = false; // default\n\n");
		mainSrc.append("        register(setting, new Data() {\n\n");
		mainSrc.append("            @Override\n");
		mainSrc.append("            public Screen onScreen() {\n");
		mainSrc.append("                return new MainScreen();\n");
		mainSrc.append("            }\n");
		mainSrc.append("        });\n\n");
		mainSrc.append("    }\n\n");
		mainSrc.append("}\n");

		engine.writeString(pkgPath.resolve(cfg.mainClass + ".java"), mainSrc.toString());
	}

	private String escapeForJavaString(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
