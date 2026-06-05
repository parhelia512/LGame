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

public class TeavmWasmPlugin implements TargetPlugin {

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
		filters.put("@{ProjectPackage}", cfg.projectPackage != null ? cfg.projectPackage : "org.test");
		filters.put("@{TeavmVersion}", cfg.teavmVersion != null ? cfg.teavmVersion : "0.14.0");
		filters.put("@{TeavmDependencies}", buildDeps(cfg));

		Path module = projectRoot.resolve("teavm-js");
		Files.createDirectories(module);

		String buildKts = engine.renderResource("teavm-wasm/build.gradle.kts.tpl", filters);
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
				if (newName.contains("teavm-html") || !newName.contains("loon")) {
					try {
						engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
					} catch (Exception ex) {
						System.err.println("Copy jar failed: " + jar + " -> " + ex.getMessage());
					}
				}
			}
		}

		String pkg = cfg.projectPackage != null && cfg.projectPackage.length() > 0 ? cfg.projectPackage : "org.test";
		int width = (cfg.width > 0) ? cfg.width : 800;
		int height = (cfg.height > 0) ? cfg.height : 600;
		String projectName = cfg.projectName != null ? cfg.projectName : "TeaVM Test";

		StringBuilder mainSrc = new StringBuilder();
		mainSrc.append("package ").append(pkg).append(";\n\n");
		mainSrc.append("import java.io.IOException;\n\n");
		mainSrc.append("import org.teavm.vm.TeaVMOptimizationLevel;\n\n");
		mainSrc.append("import loon.teavm.builder.TargetType;\n");
		mainSrc.append("import loon.teavm.builder.TeaInitialize;\n");
		mainSrc.append("import loon.teavm.make.SkipClass;\n\n");
		mainSrc.append("@SkipClass\n");
		mainSrc.append("public class " + cfg.mainClass + " {\n\n");
		mainSrc.append("    public static void main(String[] args) {\n");
		mainSrc.append("        try {\n");
		mainSrc.append("            TeaInitialize.create(\"").append(escapeForJavaString(projectName)).append("\", ")
				.append(width).append(", ").append(height)
				.append(", RunMain.class, true, true, TeaVMOptimizationLevel.ADVANCED,\n");
		mainSrc.append("                    TargetType.WebAssembly);\n");
		mainSrc.append("        } catch (IOException e) {\n");
		mainSrc.append("            e.printStackTrace();\n");
		mainSrc.append("        }\n");
		mainSrc.append("    }\n\n");
		mainSrc.append("}\n");

		StringBuilder runMainSrc = new StringBuilder();
		runMainSrc.append("package ").append(pkg).append(";\n\n");
		runMainSrc.append("import loon.Screen;\n\n");
		runMainSrc.append("import loon.LazyLoading.Data;\n");
		runMainSrc.append("import loon.teavm.Loon;\n");
		runMainSrc.append("import loon.teavm.TeaGame.TeaSetting;\n\n");
		runMainSrc.append("public class RunMain {\n\n");
		runMainSrc.append("    public static void main(String[] args) {\n\n");
		runMainSrc.append("        TeaSetting setting = new TeaSetting();\n");
		runMainSrc.append("        setting.fps = 60;\n");
		runMainSrc.append("        setting.isDebug = true;\n");
		runMainSrc.append("        setting.isDisplayLog = false;\n");
		runMainSrc.append("        // source size\n");
		runMainSrc.append("        setting.width = ").append((cfg.width > 0) ? cfg.width : 480).append(";\n");
		runMainSrc.append("        setting.height = ").append((cfg.height > 0) ? cfg.height : 320).append(";\n");
		runMainSrc.append("        // target size\n");
		runMainSrc.append("        setting.width_zoom = ")
				.append(Math.max(1, (cfg.width > 0) ? cfg.width * 5 / 3 : 800)).append(";\n");
		runMainSrc.append("        setting.height_zoom = ")
				.append(Math.max(1, (cfg.height > 0) ? cfg.height * 15 / 8 : 600)).append(";\n\n");
		runMainSrc.append("        setting.isFPS = true;\n");
		runMainSrc.append("        setting.useTrueFontClip = true;\n");
		runMainSrc.append("        // setting.fontSizeClip = 3;\n");
		runMainSrc.append("        setting.fontSize = 20;\n");
		runMainSrc.append("        setting.fontName = \"Dialog\";\n\n");
		runMainSrc.append("        setting.isConsoleLog = true;\n\n");
		runMainSrc.append("        Loon.register(setting, new Data() {\n\n");
		runMainSrc.append("            @Override\n");
		runMainSrc.append("            public Screen onScreen() {\n");
		runMainSrc.append("                return new MainScreen();\n");
		runMainSrc.append("            }\n");
		runMainSrc.append("        });\n");
		runMainSrc.append("    }\n\n");
		runMainSrc.append("}\n");

		Path javaSrcDir = module.resolve("src").resolve("main").resolve("java");
		Path pkgPath = javaSrcDir.resolve(pkg.replace('.', File.separatorChar));
		Files.createDirectories(pkgPath);
		engine.writeString(pkgPath.resolve(cfg.mainClass + ".java"), mainSrc.toString());
		engine.writeString(pkgPath.resolve("RunMain.java"), runMainSrc.toString());
	}

	private String buildDeps(ProjectConfig cfg) {
		StringBuilder sbr = new StringBuilder();
		if (cfg == null) {
			return "";
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
		if (s == null) {
			return "";
		}
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}