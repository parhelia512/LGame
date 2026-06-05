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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProjectConfig {

	public String projectName = "MyGame";
	public String projectPackage = "com.mygame";
	public String mainClass = "Main";
	public String outDir = System.getProperty("user.home") + File.separator + "MyGame-Project";

	public String sdkPath = "";

	public int width = 480;
	public int height = 320;

	public List<String> localJars = new ArrayList<String>();

	public List<String> mavenDeps = new ArrayList<String>();

	public List<String> targets = new ArrayList<String>();

	public String lwjglVersion = "3.4.1";
	public String graalHelperVersion = "2.0.1";
	public boolean enableGraalNative = false;
	public String robovmVersion = "2.3.24";
	public String loonVersion = "0.5";
	public String projectVersion = "1.0.0";
	public String androidMinSdk = "21";
	public String androidMaxSdk = "35";
	public String gwtVersion = "2.9.0";
	public String teavmVersion = "0.14.0";
    public String teavmCOutput = System.getProperty("user.home") + File.separator + "cport";
	public boolean autoRunGradleWrapper = true;

	public boolean includeDesktop = true;

	public ProjectConfig() {
	}

	public ProjectConfig(String projectName, String projectPackage, String outDir) {
		this.projectName = projectName;
		this.projectPackage = projectPackage;
		this.outDir = outDir;
	}

	@Override
	public String toString() {
		return "ProjectConfig{" + "projectName='" + projectName + '\'' + ", projectPackage='" + projectPackage + '\''
				+ ", mainClass='" + mainClass + '\'' + ", outDir='" + outDir + '\'' + ", sdkPath='" + sdkPath + '\''
				+ ", width=" + width + ", height=" + height + ", localJars=" + localJars + ", mavenDeps=" + mavenDeps
				+ ", targets=" + targets + ", lwjglVersion='" + lwjglVersion + '\'' + ", graalHelperVersion='"
				+ graalHelperVersion + '\'' + ", enableGraalNative=" + enableGraalNative + ", robovmVersion='"
				+ robovmVersion + '\'' + ", loonVersion='" + loonVersion + '\'' + ", projectVersion='" + projectVersion
				+ '\'' + ", autoRunGradleWrapper=" + autoRunGradleWrapper + ", includeDesktop=" + includeDesktop + '}';
	}
}