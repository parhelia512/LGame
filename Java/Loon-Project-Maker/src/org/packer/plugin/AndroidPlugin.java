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

public class AndroidPlugin implements TargetPlugin {

	@Override
	public void apply(ProjectConfig cfg, Path projectRoot, TemplateEngine engine) throws Exception {
		Map<String, String> filters = new HashMap<String, String>();
		filters.put("@{ProjectName}", cfg.projectName);
		filters.put("@{ProjectPackage}", cfg.projectPackage);
		filters.put("@{AndroidMinSdk}", cfg.androidMinSdk != null ? cfg.androidMinSdk : "21");
		filters.put("@{AndroidTargetSdk}", cfg.androidMaxSdk != null ? cfg.androidMaxSdk : "35");
		filters.put("@{AndroidDependencies}", buildDeps(cfg));

		Path module = projectRoot.resolve("android");

		String buildKts = engine.renderResource("android/build.gradle.kts.tpl", filters);
		engine.writeString(module.resolve("build.gradle.kts"), buildKts);

		Path libs = module.resolve("libs");
		File libsFile = libs.toFile();
		if (!libsFile.exists()) {
			libsFile.mkdirs();
		}
		if (cfg.localJars != null) {
			for (int i = 0; i < cfg.localJars.size(); i++) {
				String jar = cfg.localJars.get(i).trim();
				String newName = jar.toLowerCase();
				if (newName.contains("android") || !newName.contains("loon")) {
					try {
						engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
					} catch (Exception ex) {
						System.err.println("Copy jar failed: " + jar + " -> " + ex.getMessage());
					}
				}
			}
		}

		String manifest = engine.renderResource("android/AndroidManifest.xml.tpl", filters);
		engine.writeString(module.resolve("src/main/AndroidManifest.xml"), manifest);

		String pkgPath = cfg.projectPackage.replace('.', '/');
		Path activityPath = module.resolve("src/main/java").resolve(pkgPath);
		File activityDir = activityPath.toFile();
		if (!activityDir.exists()) {
			activityDir.mkdirs();
		}

		final int width = (cfg.width > 0) ? cfg.width : 480;
		final int height = (cfg.height > 0) ? cfg.height : 320;

		final String appName = (cfg.projectName != null && cfg.projectName.length() > 0) ? cfg.projectName : "test";
		final String logoPath = "loon_logo.png";

		final String orientationConst = "ActivityInfo.SCREEN_ORIENTATION_SENSOR";

		StringBuilder activitySrc = new StringBuilder();
		activitySrc.append("package ").append(cfg.projectPackage).append(";\n\n");
		activitySrc.append("import loon.Stage;\n");
		activitySrc.append("import loon.Screen;\n");
		activitySrc.append("import loon.LazyLoading;\n");
		activitySrc.append("import loon.android.Loon;\n");
		activitySrc.append("import loon.android.AndroidGame;\n");
		activitySrc.append("import android.content.pm.ActivityInfo;\n\n");
		activitySrc.append("public class " + cfg.mainClass + " extends Loon {\n\n");
		activitySrc.append("    @Override\n");
		activitySrc.append("    public void onMain() {\n\n");
		activitySrc.append("        AndroidGame.AndroidSetting setting = new AndroidGame.AndroidSetting();\n");
		activitySrc.append("        setting.isFPS = true;\n");
		activitySrc.append("        setting.isDisplayLog = true;\n");
		activitySrc.append("        setting.isDebug = true;\n");
		activitySrc.append("        setting.isMemory = false;\n");
		activitySrc.append("        setting.isLogo = false;\n        ");
		activitySrc.append("        setting.fullscreen = true;\n");
		activitySrc.append("        setting.width = ").append(width).append(";\n");
		activitySrc.append("        setting.height = ").append(height).append(";\n");
		activitySrc.append("        // 禁止使用配置文件的旋转设置,直接以width,height大小决定屏幕横竖\n");
		activitySrc.append("        setting.useOrientation = true;\n");
		activitySrc.append("        // 屏幕旋转方式(useOrientation为false时不生效)\n");
		activitySrc.append("        setting.orientation = ").append(orientationConst).append(";\n");
		activitySrc.append("        // 若启动此模式，则画面等比压缩，不会失真\n");
		activitySrc.append("        setting.useRatioScaleFactor = false;\n");
		activitySrc.append("        // 强制一个显示大小(在android模式下，不填则默认全屏，此模式可能会造成画面失真)\n");
		activitySrc.append("        // setting.width_zoom = getContainerWidth();\n");
		activitySrc.append("        // setting.height_zoom = getContainerHeight();\n");
		activitySrc.append("        // 屏幕显示模式\n");
		activitySrc.append("        // setting.showMode = LMode.FitFill;\n");
		activitySrc.append("        setting.logoPath = \"").append(logoPath).append("\";\n");
		activitySrc.append("        setting.fps = 60").append(";\n");
		activitySrc.append("        setting.fontName = \"Dialog\";\n");
		activitySrc.append("        setting.appName = \"").append(escapeForJavaString(appName)).append("\";\n");
		activitySrc.append("        setting.emulateTouch = false;\n");
		activitySrc.append("        setting.lockBackDestroy = false;\n");
		activitySrc.append("        setting.isBackDestroy = false;\n");
		activitySrc.append("        setting.useTrueFontClip = true;\n");
		activitySrc.append("        register(setting, new LazyLoading.Data() {\n\n");
		activitySrc.append("            @Override\n");
		activitySrc.append("            public Screen onScreen() {\n");
		activitySrc.append("                return new MainScreen();\n");
		activitySrc.append("            }\n");
		activitySrc.append("        });\n\n");
		activitySrc.append("    }\n\n");
		activitySrc.append("}\n");

		engine.writeString(activityPath.resolve(cfg.mainClass + ".java"), activitySrc.toString());
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
		sbr.append("implementation(\"androidx.core:core-ktx:1.10.1\")\n");
		sbr.append("implementation(\"androidx.appcompat:appcompat:1.6.1\")\n");
		return sbr.toString();
	}

	private String escapeForJavaString(String s) {
		if (s == null)
			return "";
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
