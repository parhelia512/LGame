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

public class CorePlugin implements TargetPlugin {

	@Override
	public void apply(ProjectConfig cfg, Path projectRoot, TemplateEngine engine) throws Exception {
		Map<String, String> filters = new HashMap<String, String>();
		filters.put("@{ProjectName}", cfg.projectName);
		filters.put("@{ProjectPackage}", cfg.projectPackage);
		filters.put("@{ProjectVersion}", cfg.projectVersion != null ? cfg.projectVersion : "1.0.0");

		Path assets = projectRoot.resolve("assets");
		File assetsFile = assets.toFile();
		if (!assetsFile.exists()) {
			assetsFile.mkdirs();
		}

		Path module = projectRoot.resolve("core");

		String buildKts = engine.renderResource("core/build.gradle.kts.tpl", filters);
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
				if (newName.contains("core")) {
					try {
						engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
					} catch (Exception ex) {
						System.err.println("Copy jar failed: " + jar + " -> " + ex.getMessage());
					}
				}
			}
		}

		String coreGameSrc = engine.renderResource("core/src/main/java/MainScreen.java.tpl", filters);
		String pkgPath = cfg.projectPackage.replace('.', '/');
		Path coreDir = module.resolve("src").resolve("main").resolve("java").resolve(pkgPath);
		if (!Files.exists(coreDir)) {
			Files.createDirectories(coreDir);
		}
		engine.writeString(coreDir.resolve("MainScreen.java"), coreGameSrc);
	}
}