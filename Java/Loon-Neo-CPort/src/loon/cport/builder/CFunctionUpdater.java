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
package loon.cport.builder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import loon.utils.ObjectMap;

public class CFunctionUpdater {

	public static String replaceFunctions(String source, ObjectMap<String, String[]> replacements) {
		String result = source;
		for (ObjectMap.Entry<String, String[]> entry : replacements) {
			String functionName = entry.getKey();
			String[] values = entry.getValue();
			String newParams = values[0];
			String newBody = values[1];
			result = replaceFunction(result, functionName, newParams, newBody);
			result = replaceDeclaration(result, functionName, newParams);
		}
		return result;
	}

	private static String replaceFunction(String source, String functionName, String newParams, String newBody) {
		String regex = "(?m)^\\s*([a-zA-Z_][\\w\\*\\s]+)\\b" + functionName + "\\s*\\([^)]*\\)\\s*\\{";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(source);

		if (!matcher.find()) {
			return source;
		}

		String returnTypeLine = matcher.group(1).trim();

		if (!(returnTypeLine.startsWith("void") || returnTypeLine.startsWith("int") || returnTypeLine.startsWith("char")
				|| returnTypeLine.startsWith("float") || returnTypeLine.startsWith("double")
				|| returnTypeLine.startsWith("long") || returnTypeLine.startsWith("short")
				|| returnTypeLine.startsWith("unsigned") || returnTypeLine.startsWith("signed")
				|| returnTypeLine.startsWith("struct") || returnTypeLine.startsWith("enum"))) {
			return source;
		}

		int startIndex = matcher.start();
		int bodyStartIndex = source.indexOf("{", matcher.end() - 1);

		int braceCount = 1;
		int bodyEndIndex = bodyStartIndex + 1;
		while (bodyEndIndex < source.length() && braceCount > 0) {
			char c = source.charAt(bodyEndIndex);
			if (c == '{')
				braceCount++;
			else if (c == '}')
				braceCount--;
			bodyEndIndex++;
		}

		StringBuilder newFunction = new StringBuilder();
		newFunction.append("\n").append(returnTypeLine).append(" ").append(functionName).append("(").append(newParams)
				.append(") {\n");
		for (String line : newBody.split("\n")) {
			newFunction.append("    ").append(line).append("\n");
		}
		newFunction.append("}\n");

		return source.substring(0, startIndex) + newFunction.toString() + source.substring(bodyEndIndex);
	}

	private static String replaceDeclaration(String source, String functionName, String newParams) {
		String regex = "(?m)^\\s*([\\w\\*\\s]+)" + functionName + "\\s*\\([^)]*\\)\\s*;\\s*$";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(source);

		StringBuffer sbr = new StringBuffer();
		while (matcher.find()) {
			String matchedLine = matcher.group(0).trim();
			String prefix = matcher.group(1).trim();

			boolean isTypePrefix = prefix.matches(
					"^(void|int|char|float|double|long|short|unsigned|signed|struct|enum|[A-Za-z_][A-Za-z0-9_\\*\\s]*)$");

			if (!isTypePrefix) {
				matcher.appendReplacement(sbr, Matcher.quoteReplacement(matchedLine));
				continue;
			}

			String replacement = prefix + " " + functionName + "(" + newParams + ");";

			matcher.appendReplacement(sbr, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sbr);

		return sbr.toString();
	}

}
