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
		String regex = "\\b([\\w\\*\\s]+)" + functionName + "\\s*\\([^)]*\\)\\s*\\{";
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(source);

		if (!matcher.find()) {
			return source;
		}

		int startIndex = matcher.start();
		int bodyStartIndex = source.indexOf("{", matcher.end() - 1);

		int braceCount = 1;
		int bodyEndIndex = bodyStartIndex + 1;
		while (bodyEndIndex < source.length() && braceCount > 0) {
			char c = source.charAt(bodyEndIndex);
			if (c == '{') {
				braceCount++;
			} else if (c == '}') {
				braceCount--;
			}
			bodyEndIndex++;
		}

		String matchedDefinition = source.substring(startIndex, bodyEndIndex);

		if (matchedDefinition.contains("#define") || matchedDefinition.contains("inline")) {
			return source;
		}

		if (!matchedDefinition.startsWith(matcher.group(1).trim() + " " + functionName)) {
			return source;
		}

		String returnTypeLine = matcher.group(1).trim();

		StringBuilder newFunction = new StringBuilder();
		newFunction.append("\n").append(returnTypeLine).append(" ").append(functionName).append("(").append(newParams)
				.append(") {\n");
		String[] lines = newBody.split("\n");
		for (String line : lines) {
			newFunction.append("    ").append(line).append("\n");
		}
		newFunction.append("}\n");
		return source.substring(0, startIndex) + newFunction.toString() + source.substring(bodyEndIndex);
	}

	private static String replaceDeclaration(String source, String functionName, String newParams) {
		String regex = "^[\\s]*([\\w\\*\\s]+)" + functionName + "\\s*\\([^)]*\\)\\s*;[\\s]*$";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(source);

		StringBuffer sbr = new StringBuffer();
		while (matcher.find()) {
			String matchedDeclaration = matcher.group(0);
			if (matchedDeclaration.contains("#define") || matchedDeclaration.contains("inline")) {
				continue;
			}
			String returnType = matcher.group(1).trim();
			String replacement = returnType + " " + functionName + "(" + newParams + ");";
			matcher.appendReplacement(sbr, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(sbr);

		return sbr.toString();
	}

}
