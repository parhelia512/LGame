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
public class OtherJvmPlugin implements TargetPlugin {
    @Override
    public void apply(ProjectConfig cfg, Path projectRoot, TemplateEngine engine) throws Exception {
        Map<String,String> filters = new HashMap<>();
        filters.put("@{ProjectName}", cfg.projectName);
        filters.put("@{ProjectPackage}", cfg.projectPackage);
        filters.put("@{OtherJvmDependencies}", buildDeps(cfg));
        Path module = projectRoot.resolve("other-jvm");
        engine.writeString(module.resolve("build.gradle.kts"), engine.renderResource("other-jvm/build.gradle.kts.tpl", filters));
        Path libs = module.resolve("libs");
        Files.createDirectories(libs);
        if (cfg.localJars != null) {
			for (int i = 0; i < cfg.localJars.size(); i++) {
				String jar = cfg.localJars.get(i).trim();
				engine.copyFile(Paths.get(jar), libs.resolve(Paths.get(jar).getFileName()));
			}
        }
    }

    private String buildDeps(ProjectConfig cfg) {
        StringBuilder sb = new StringBuilder();
        if (!cfg.localJars.isEmpty()) sb.append("implementation(fileTree(\"libs\") { include(\"*.jar\") })\n");
        for (String d : cfg.mavenDeps) sb.append("implementation(\"").append(d).append("\")\n");
        return sb.toString();
    }
}