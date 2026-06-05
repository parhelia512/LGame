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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.Map;

public class TemplateEngine {

	public String renderResource(String resourcePath, Map<String, String> filters) throws IOException {
		InputStream in = getClass().getResourceAsStream("/templates/" + resourcePath);
		if (in == null) {
			throw new FileNotFoundException("Template not found: " + resourcePath);
		}

		BufferedReader reader = null;
		StringBuilder sbr = new StringBuilder();
		try {
			reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
			String line;
			while ((line = reader.readLine()) != null) {
				sbr.append(line);
				sbr.append("\n");
			}
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
		String content = sbr.toString();
		if (filters != null) {
			for (Map.Entry<String, String> e : filters.entrySet()) {
				content = content.replace(e.getKey(), e.getValue());
			}
		}
		return content;
	}

	public void writeString(Path dest, String content) throws IOException {
		File parent = dest.getParent().toFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest.toFile()), "UTF-8"));
			writer.write(content);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public void copyFile(Path src, Path dest) throws IOException {
		File parent = dest.getParent().toFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}

		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(src.toFile());
			out = new FileOutputStream(dest.toFile());

			byte[] buffer = new byte[4096];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
				}
			}
		}
	}

}
