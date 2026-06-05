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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.packer.ProjectConfig;
import org.packer.TargetPlugin;
import org.packer.TemplateEngine;
import java.io.File;
import java.util.List;

public class TeavmCPlugin implements TargetPlugin {

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
		filters.put("@{TeavmVersion}", cfg.teavmVersion != null ? cfg.teavmVersion : "0.14.0");
		filters.put("@{TeavmDependencies}", buildDeps(cfg));

		Path module = projectRoot.resolve("teavm-c");
		Files.createDirectories(module);

		String buildKts = engine.renderResource("teavm-c/build.gradle.kts.tpl", filters);
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
				if (newName.contains("teavm-cport") || !newName.contains("loon")) {
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

		String projectName = cfg.projectName != null ? cfg.projectName : "TeaVM C Test";

		StringBuilder mainSrc = new StringBuilder();
		mainSrc.append("package ").append(pkg).append(";\n\n");
		mainSrc.append("import java.io.IOException;\n\n");
		mainSrc.append("import org.teavm.vm.TeaVMOptimizationLevel;\n\n");
		mainSrc.append("import loon.cport.make.SkipClass;\n");
		mainSrc.append("import loon.cport.builder.CInitialize;\n");
		mainSrc.append("import loon.cport.builder.MemoryMode;\n\n");
		mainSrc.append("@SkipClass\n");
		mainSrc.append("public class " + cfg.mainClass + " {\n\n");
		mainSrc.append("    public static void main(String[] args) {\n");
		mainSrc.append("        /*\n");
		mainSrc.append("         * Example: copy assets before building if needed\n");
		mainSrc.append("         */\n");
		mainSrc.append("        try {\n");
		mainSrc.append(
				"            CInitialize.create(MemoryMode.NORMAL, RunMain.class, false, false, false, TeaVMOptimizationLevel.ADVANCED,\n");
		mainSrc.append("                    \"").append(escapeForJavaString(getDefaultAssetsPath(cfg))).append("\", \"")
				.append(escapeForJavaString(getDefaultAppName(cfg))).append("\");\n");
		mainSrc.append("        } catch (IOException e) {\n");
		mainSrc.append("            e.printStackTrace();\n");
		mainSrc.append("        }\n\n");
		mainSrc.append("    }\n\n");
		mainSrc.append("}\n");

		StringBuilder runMainSrc = new StringBuilder();
		runMainSrc.append("package ").append(pkg).append(";\n\n");
		runMainSrc.append("import loon.cport.CGame.CSetting;\n\n");
		runMainSrc.append("import loon.LazyLoading.Data;\n");
		runMainSrc.append("import loon.Screen;\n");
		runMainSrc.append("import loon.cport.Loon;\n\n");
		runMainSrc.append("public class RunMain {\n\n");
		runMainSrc.append("    public static void main(String[] args) {\n\n");
		runMainSrc.append("        CSetting setting = new CSetting();\n");
		runMainSrc.append("        setting.isFPS = true;\n");
		runMainSrc.append("        setting.isDebug = true;\n");
		runMainSrc.append("        setting.isDisplayLog = false;\n");
		runMainSrc.append("        setting.isMemory = false;\n");
		runMainSrc.append("        // setting.useTrueFontClip = true;\n");
		runMainSrc.append("        setting.resizable = true;\n");
		runMainSrc.append("        // setting.emulateTouch = true;\n");
		runMainSrc.append("        // setting.isDrawCall = true;\n");
		runMainSrc.append("        setting.isLogo = false;\n");
		runMainSrc.append("        setting.logoPath = \"loon_logo.png\";\n");
		runMainSrc.append("        setting.width = ").append((cfg.width > 0) ? cfg.width : 480).append(";\n");
		runMainSrc.append("        setting.height = ").append((cfg.height > 0) ? cfg.height : 320).append(";\n");
		runMainSrc.append("        setting.title = \"").append(escapeForJavaString(projectName)).append("\";\n");
		runMainSrc.append("        setting.width_zoom = ")
				.append(Math.max(1, (cfg.width > 0) ? cfg.width * 4 / 3 : 640)).append(";\n");
		runMainSrc.append("        setting.height_zoom = ")
				.append(Math.max(1, (cfg.height > 0) ? cfg.height * 3 / 2 : 480)).append(";\n");
		runMainSrc.append("        // setting.useTrueFontClip = true;\n");
		runMainSrc.append("        // setting.fps = 30;\n");
		runMainSrc.append("        setting.fontName = \"dialog\";\n");
		runMainSrc.append("        setting.appName = \"").append(escapeForJavaString(projectName)).append("\";\n");
		runMainSrc.append("        Loon.register(setting, new Data() {\n\n");
		runMainSrc.append("            @Override\n");
		runMainSrc.append("            public Screen onScreen() {\n");
		runMainSrc.append("                return new MainScreen();\n");
		runMainSrc.append("            }\n");
		runMainSrc.append("        });\n\n");
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
		List<String> deps = cfg.mavenDeps;
		if (deps != null) {
			for (int i = 0; i < deps.size(); i++) {
				String d = deps.get(i);
				if (d != null && d.trim().length() > 0) {
					sbr.append("implementation(\"").append(d.trim()).append("\")\n");
				}
			}
		}
		return sbr.toString();
	}

	private String getDefaultAssetsPath(ProjectConfig cfg) {
		return cfg != null && cfg.teavmCOutput != null && cfg.teavmCOutput.length() > 0 ? cfg.teavmCOutput
				: ("c:\\" + (cfg != null && cfg.projectName != null ? cfg.projectName : "output"));
	}

	private String getDefaultAppName(ProjectConfig cfg) {
		return cfg != null && cfg.projectName != null && cfg.projectName.length() > 0 ? cfg.projectName : "src";
	}

	private String escapeForJavaString(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
