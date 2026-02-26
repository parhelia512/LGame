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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import loon.cport.builder.CMacroFix.MacroInjection;
import loon.cport.builder.CMacroFix.MacroReplacement;
import loon.utils.ObjectMap;
import loon.utils.PathUtils;
import loon.utils.TArray;

/**
 * 修正C代码用类，用于动态修正一些teavm的c版生成的c代码，有些是teavm的生成错误，有些是多平台适配需要，反正都是必改的……
 */
public class CCodeFix {

	private static final LinkedHashMap<String, String> ALL_FILE_REPLACEMENT_RULES = new LinkedHashMap<String, String>();

	static {
		// 此项暂时不需要了,TeaVM的C版已经按照标准C11规范修正命名,暂时不需要额外修正,但是功能要保留，避免其他坑……
		// ALL_FILE_REPLACEMENT_RULES.put("TeaVM_Class*, TeaVM_Class* cls",
		// "TeaVM_Class* fixedname, TeaVM_Class* cls");
	}

	public void fixMacro(Path fixFile) {
		if (fixFile == null) {
			return;
		}
		String fixFileName = fixFile.toAbsolutePath().toString();
		String fixBaseName = PathUtils.getBaseFileName(fixFileName);
		String fixExtName = PathUtils.getExtension(fixFileName);
		if ("c".equals(fixExtName)) {
			String fixMacroName1 = "time";
			if (fixBaseName.equalsIgnoreCase(fixMacroName1)) {
				Map<String, MacroReplacement> replacements = new HashMap<String, MacroReplacement>();
				replacements.put("TEAVM_WINDOWS", new MacroReplacement(null, null));
				Map<String, MacroInjection> append = new HashMap<String, MacroInjection>();
				append.put("TEAVM_WINDOWS", new MacroInjection("\r\n#if !TEAVM_WINDOWS && !TEAVM_UNIX\r\n"
						+ "    int64_t teavm_currentTimeMillis() {\r\n" + "        return (int64_t)SDL_GetTicks();\r\n"
						+ "    }\r\n" + "\r\n" + "    int64_t teavm_currentTimeNano() {\r\n"
						+ "        return (int64_t)(SDL_GetTicks64() * 1000000LL);\r\n" + "    }\r\n" + "#endif", true,
						"DEFAULT"));
				try {
					CMacroFix.processFile(fixFileName, replacements, append);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			String fixMacroName2 = "fiber";
			if (fixBaseName.equalsIgnoreCase(fixMacroName2)) {
				Map<String, MacroReplacement> replacements = new HashMap<String, MacroReplacement>();
				replacements.put("TEAVM_WINDOWS", new MacroReplacement(null, null));
				Map<String, MacroInjection> append = new HashMap<String, MacroInjection>();
				append.put("TEAVM_WINDOWS",
						new MacroInjection("\r\n#if !TEAVM_WINDOWS && !TEAVM_UNIX\r\n"
								+ "    void teavm_waitFor(int64_t timeout) {\r\n"
								+ "        SDL_Delay((Uint32)timeout);\r\n" + "    }\r\n" + "\r\n"
								+ "    void teavm_interrupt() {\r\n" + "    }\r\n" + "#endif", true, "LAST"));
				try {
					CMacroFix.processFile(fixFileName, replacements, append);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			String fixMacroName3 = "memory";
			if (fixBaseName.equalsIgnoreCase(fixMacroName3)) {
				Map<String, MacroReplacement> replacements = new HashMap<String, MacroReplacement>();
				replacements.put("TEAVM_WINDOWS", new MacroReplacement(null, null));
				Map<String, MacroInjection> append = new HashMap<String, MacroInjection>();
				append.put("TEAVM_WINDOWS", new MacroInjection("\r\n#if !TEAVM_WINDOWS && !TEAVM_UNIX\r\n"
						+ "    static void* teavm_virtualAlloc(size_t size) {\r\n" + "    #if defined(XBOX)\r\n"
						+ "            return XMemVirtualAlloc(size, XMEM_COMMIT, XMEM_READWRITE);\r\n"
						+ "    #elif defined(__linux__) || defined(__APPLE__) || defined(__ANDROID__) || defined(__IOS__) \\\r\n"
						+ "           || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__HAIKU__)\r\n"
						+ "            void* p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);\r\n"
						+ "            return (p == MAP_FAILED) ? NULL : p;\r\n" + "    #else\r\n"
						+ "            return gc_mmap((size_t)size, GC_PROT_NONE);\r\n" + "    #endif\r\n" + "    }\r\n"
						+ "\r\n" + "    static int64_t teavm_pageSize() {\r\n" + "    #if defined(XBOX)\r\n"
						+ "            return (int64_t)XMemGetPageSize();\r\n"
						+ "    #elif defined(__linux__) || defined(__APPLE__) || defined(__ANDROID__) || defined(__IOS__) \\\r\n"
						+ "       || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__HAIKU__)\r\n"
						+ "            long pageSize = sysconf(_SC_PAGESIZE);\r\n"
						+ "            return (pageSize > 0) ? (int64_t)pageSize : 4096;\r\n" + "    #else\r\n"
						+ "            return 4096;\r\n" + "    #endif\r\n" + "    }\r\n" + "\r\n"
						+ "    static void teavm_virtualCommit(void* address, int64_t size) {\r\n"
						+ "    #if defined(XBOX)\r\n"
						+ "            XMemProtect(address, (SIZE_T)size, XMEM_READWRITE);\r\n"
						+ "    #elif defined(__linux__) || defined(__APPLE__) || defined(__ANDROID__) || defined(__IOS__) \\\r\n"
						+ "       || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__HAIKU__)\r\n"
						+ "            mprotect(address, (size_t)size, PROT_READ | PROT_WRITE);\r\n" + "    #else\r\n"
						+ "            gc_mprotect(address, (size_t)size, GC_PROT_READ | GC_PROT_WRITE);\r\n"
						+ "    #endif\r\n" + "    }\r\n" + "\r\n"
						+ "    static void teavm_virtualUncommit(void* address, int64_t size) {\r\n"
						+ "    #if defined(XBOX)\r\n"
						+ "            XMemProtect(address, (SIZE_T)size, XMEM_NOACCESS);\r\n"
						+ "    #elif defined(__linux__) || defined(__APPLE__) || defined(__ANDROID__) || defined(__IOS__) \\\r\n"
						+ "       || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__HAIKU__)\r\n"
						+ "            mprotect(address, (size_t)size, PROT_NONE);\r\n" + "    #else\r\n"
						+ "            gc_mprotect(address, (size_t)size, GC_PROT_NONE);\r\n" + "    #endif\r\n"
						+ "    }\r\n" + "#endif\r\n", false, "LAST"));
				try {
					CMacroFix.processFile(fixFileName, replacements, append);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else if ("h".equals(fixExtName)) {
			String fixMacroName1 = "exceptions";
			if (fixBaseName.equalsIgnoreCase(fixMacroName1)) {
				Map<String, MacroReplacement> replacements = new HashMap<String, MacroReplacement>();
				replacements.put("TEAVM_WINDOWS", new MacroReplacement(null, null));
				Map<String, MacroInjection> append = new HashMap<String, MacroInjection>();
				append.put("TEAVM_WINDOWS", new MacroInjection("    #if !TEAVM_WINDOWS && !TEAVM_UNIX\r\n"
						+ "        #if defined(_MSC_VER)\r\n" + "        #define TEAVM_UNREACHABLE __assume(0);\r\n"
						+ "        #elif defined(__GNUC__) || defined(__clang__)\r\n"
						+ "        #define TEAVM_UNREACHABLE __builtin_unreachable();\r\n" + "        #else\r\n"
						+ "        #define TEAVM_UNREACHABLE abort();\r\n" + "        #endif\r\n" + "    #endif", true,
						"DEFAULT"));
				try {
					CMacroFix.processFile(fixFileName, replacements, append);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			String fixMacroName2 = "definitions";
			if (fixBaseName.equalsIgnoreCase(fixMacroName2)) {
				Map<String, MacroReplacement> replacements = new HashMap<String, MacroReplacement>();
				replacements.put("TEAVM_OBFUSCATED", new MacroReplacement(null, null));
				Map<String, MacroInjection> append = new HashMap<String, MacroInjection>();
				append.put("TEAVM_OBFUSCATED", new MacroInjection(
						"\r\n#if defined(__SWITCH__)      || defined(__3DS__)        || defined(_3DS)          || \\\r\n"
								+ "    defined(__WII__)         || defined(__WIIU__)       || defined(__NDS__)       || \\\r\n"
								+ "    defined(__GAMECUBE__)    || defined(__NINTENDO64__) || \\\r\n"
								+ "    defined(__PSP__)         || defined(__PSV__)        || defined(__PS2__)       || \\\r\n"
								+ "    defined(__PS3__)         || defined(__PS4__)        || defined(__ORBIS__)     || \\\r\n"
								+ "    defined(__PS5__)         || defined(__PROSPERO__)   || \\\r\n"
								+ "    defined(_XBOX)           || defined(__XBOX__)       || defined(__XBOX360__)   || \\\r\n"
								+ "    defined(_XBOX_ONE)       || defined(__XBOXONE__)    || defined(_XBOX_SERIES)  || defined(__XBOXSERIES__) || \\\r\n"
								+ "    defined(__DREAMCAST__)   || defined(__SATURN__)     || defined(__SEGA__)      || \\\r\n"
								+ "    defined(__ATARI__)       || defined(__NGAGE__)      || defined(__OUYA__)\r\n"
								+ "    #undef TEAVM_UNIX\r\n" + "    #define TEAVM_UNIX 0\r\n" + "#endif",
						true, "DEFAULT"));
				try {
					CMacroFix.processFile(fixFileName, replacements, append);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void fixProcessFile(Path filePath) {
		if (ALL_FILE_REPLACEMENT_RULES.size() == 0) {
			return;
		}
		ArrayList<String> newLines = new ArrayList<String>();
		boolean modified = false;
		try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String updatedLine = line;
				for (Map.Entry<String, String> entry : ALL_FILE_REPLACEMENT_RULES.entrySet()) {
					String target = entry.getKey();
					String replacement = entry.getValue();
					if (updatedLine.contains(target)) {
						updatedLine = updatedLine.replace(target, replacement);
						modified = true;
					}
				}
				newLines.add(updatedLine);
			}
		} catch (IOException e) {
			CBuilder.println("Error reading file: " + filePath + " - " + e.getMessage());
			return;
		}
		if (modified) {
			try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8,
					StandardOpenOption.TRUNCATE_EXISTING)) {
				for (String l : newLines) {
					writer.write(l);
					writer.newLine();
				}
			} catch (IOException e) {
				CBuilder.println("Error writing file: " + filePath + " - " + e.getMessage());
			}
		}
	}

	public final void fixAllFiles(String folderPath) throws IOException {

		final Path rootPath = Paths.get(folderPath);
		// 清理不需要的宏(我自己做多环境适配了，会冲突，删了干净，预防性多加几个删除宏……)
		final HashSet<String> clearMacros = new HashSet<String>();
		clearMacros.add("TEAVM_PSP");
		clearMacros.add("TEAVM_SWITCH");
		clearMacros.add("TEAVM_XBOX");
		clearMacros.add("TEAVM_STREAM");

		CMacroCleaner cleaner = new CMacroCleaner(clearMacros, targetPath);
		try {
			cleaner.cleanMacros(rootPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Stream<Path> paths = Files.walk(rootPath);
		try {
			for (Path path : (Iterable<Path>) paths::iterator) {
				String pathEx = path.toString();
				if (pathEx.contains(targetPath) && Files.isRegularFile(path)
						&& (pathEx.endsWith(".c") || pathEx.endsWith(".h"))) {
					fixProcessFile(path);
					fixMacro(path);
				}
			}
		} finally {
			paths.close();
		}
	}

	final static class FileFix {

		public String fileName;

		public final ObjectMap<String, String> fixContexts = new ObjectMap<String, String>();

		public boolean update = false;

		public FileFix(String name, String src, String dst) {
			fileName = name;
			if ("all".equalsIgnoreCase(src)) {
				update = true;
			}
			putFixReplace(src, dst);
		}

		public void putFixReplace(String src, String dst) {
			fixContexts.put(src, dst);
		}
	}

	private final TArray<FileFix> fixContexts = new TArray<FileFix>();
	private final CBuildConfiguration buildConfiguration;
	private final String targetPath;

	public CCodeFix(CBuildConfiguration config) {
		buildConfiguration = config;
		targetPath = PathUtils.normalizeCombinePaths(buildConfiguration.cappPath, buildConfiguration.cappOutputSource);
		FileFix fix1 = new FileFix("file.c", "file, size, 0, where)", "file, size, 0, FILE_BEGIN)");
		FileFix fix2 = new FileFix("definitions.h", "#define TEAVM_UNIX 1",
				"   #if defined(__linux__) || defined(__APPLE__) || defined(__ANDROID__) || defined(__IOS__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__HAIKU__)\r\n"
						+ "       #define TEAVM_UNIX 1\r\n" + "       #endif");
		FileFix fix3 = new FileFix("core.h", "((char*) teavm_gc_cardTable)[offset] = 0;",
				"int off = (int)offset;\r\n"
				+ "    unsigned char* result = (unsigned char*)teavm_gc_cardTable;\r\n"
				+ "    if (result != NULL && offset >= 0 && offset == (intptr_t)off) {\r\n"
				+ "        result[off] = 0;\r\n"
				+ "    }");
		FileFix fix4 = new FileFix("config.h", "#pragma once", "#pragma once\r\n" + "#include \"SDLSupport.c\"\r\n"
				+ "#include \"STBSupport.c\"\r\n" + "#include \"SocketSupport.c\"\r\n" + "#include \"gles2.c\"");
		FileFix fix5 = new FileFix("uchar.h", "all", "#pragma once\r\n" + "#include <stddef.h>\r\n"
				+ "#include <stdint.h>\r\n" + "\r\n"
				+ "#if defined(__SWITCH__)      || defined(__3DS__)        || defined(_3DS)          || \\\r\n"
				+ "    defined(__WII__)         || defined(__WIIU__)       || defined(__NDS__)       || \\\r\n"
				+ "    defined(__GAMECUBE__)    || defined(__NINTENDO64__) || \\\r\n"
				+ "    defined(__PSP__)         || defined(__PSV__)        || defined(__PS2__)       || \\\r\n"
				+ "    defined(__PS3__)         || defined(__PS4__)        || defined(__ORBIS__)     || \\\r\n"
				+ "    defined(__PS5__)         || defined(__PROSPERO__)   || \\\r\n"
				+ "    defined(_XBOX)           || defined(__XBOX__)       || defined(__XBOX360__)   || \\\r\n"
				+ "    defined(_XBOX_ONE)       || defined(__XBOXONE__)    || defined(_XBOX_SERIES)  || defined(__XBOXSERIES__) || \\\r\n"
				+ "    defined(__DREAMCAST__)   || defined(__SATURN__)     || defined(__SEGA__)      || \\\r\n"
				+ "    defined(__ATARI__)       || defined(__NGAGE__)      || defined(__OUYA__)\r\n"
				+ "#include <wchar.h>\r\n" + "typedef wchar_t u16char;\r\n" + "typedef wchar_t u32char;\r\n"
				+ "#else\r\n" + "#include <uchar.h>\r\n" + "typedef char16_t u16char;\r\n"
				+ "typedef char32_t u32char;\r\n" + "#endif\r\n" + "\r\n"
				+ "static inline size_t c16rtomb(char *s, u16char c16, mbstate_t *ps) {\r\n" + "    (void)ps;\r\n"
				+ "    if (!s) return 0;\r\n" + "    uint32_t c = (uint32_t)c16;\r\n" + "    if (c < 0x80) {\r\n"
				+ "        s[0] = (char)c;\r\n" + "        return 1;\r\n" + "    } else if (c < 0x800) {\r\n"
				+ "        s[0] = (char)(0xC0 | (c >> 6));\r\n" + "        s[1] = (char)(0x80 | (c & 0x3F));\r\n"
				+ "        return 2;\r\n" + "    } else {\r\n" + "        s[0] = (char)(0xE0 | (c >> 12));\r\n"
				+ "        s[1] = (char)(0x80 | ((c >> 6) & 0x3F));\r\n"
				+ "        s[2] = (char)(0x80 | (c & 0x3F));\r\n" + "        return 3;\r\n" + "    }\r\n" + "}\r\n"
				+ "\r\n" + "static inline size_t mbrtoc16(u16char *pc16, const char *s, size_t n, mbstate_t *ps) {\r\n"
				+ "    (void)ps;\r\n" + "    if (!s || n == 0) return 0;\r\n"
				+ "    unsigned char c = (unsigned char)s[0];\r\n" + "    if (c < 0x80) {\r\n"
				+ "        if (pc16) *pc16 = (u16char)c;\r\n" + "        return 1;\r\n"
				+ "    } else if ((c >> 5) == 0x6 && n >= 2) {\r\n"
				+ "        if (pc16) *pc16 = (u16char)(((c & 0x1F) << 6) | (s[1] & 0x3F));\r\n"
				+ "        return 2;\r\n" + "    } else if ((c >> 4) == 0xE && n >= 3) {\r\n"
				+ "        if (pc16) *pc16 = (u16char)(((c & 0x0F) << 12) | ((s[1] & 0x3F) << 6) | (s[2] & 0x3F));\r\n"
				+ "        return 3;\r\n" + "    } else {\r\n" + "        if (pc16) *pc16 = (u16char)0xFFFD; \r\n"
				+ "        return 1;\r\n" + "    }\r\n" + "}\r\n" + "\r\n" + "");
		FileFix fix6 = new FileFix("date.c", "all", "#include \"date.h\"\r\n" + "#include \"definitions.h\"\r\n"
				+ "\r\n" + "#ifndef _XOPEN_SOURCE\r\n" + "#define _XOPEN_SOURCE\r\n" + "#endif\r\n" + "\r\n"
				+ "#ifndef __USE_XOPEN\r\n" + "#define __USE_XOPEN\r\n" + "#endif\r\n" + "\r\n"
				+ "#ifndef _GNU_SOURCE\r\n" + "#define _GNU_SOURCE\r\n" + "#endif\r\n" + "\r\n"
				+ "static time_t portable_timegm(struct tm* tm) {\r\n" + "#if defined(_WIN32) || defined(_WIN64)\r\n"
				+ "    return _mkgmtime(tm);\r\n" + "#else\r\n" + "    return mktime(tm);\r\n" + "#endif\r\n" + "}\r\n"
				+ "\r\n" + "static struct tm* portable_localtime_r(const time_t* timep, struct tm* result) {\r\n"
				+ "#if defined(_WIN32) || defined(_WIN64)\r\n" + "    if (localtime_s(result, timep) == 0) {\r\n"
				+ "        return result;\r\n" + "    }\r\n" + "    return NULL;\r\n" + "#else\r\n"
				+ "    struct tm* tmp = localtime(timep);\r\n" + "    if (tmp) {\r\n" + "        *result = *tmp;\r\n"
				+ "        return result;\r\n" + "    }\r\n" + "    return NULL;\r\n" + "#endif\r\n" + "}\r\n" + "\r\n"
				+ "#if TEAVM_WINDOWS\r\n" + "    #define strcasecmp _stricmp\r\n" + "    #define timegm _mkgmtime\r\n"
				+ "    #define localtime_r(a, b) localtime_s(b, a)\r\n" + "#else\r\n"
				+ "    #define timegm portable_timegm \r\n"
				+ "    #define localtime_r(a, b) portable_localtime_r(a, b)\r\n" + "    #ifndef strcasecmp\r\n"
				+ "    #define strcasecmp strcmp\r\n" + "    #endif\r\n" + "#endif\r\n" + "\r\n"
				+ "static time_t teavm_epochStart;\r\n" + "static struct tm teavm_epochStartTm;\r\n"
				+ "static char teavm_date_formatBuffer[512];\r\n"
				+ "static char* teavm_date_defaultFormat = \"%a %b %d %H:%M:%S %Z %Y\";\r\n" + "\r\n"
				+ "void teavm_date_init() {\r\n" + "    struct tm epochStart = {\r\n" + "        .tm_year = 70,\r\n"
				+ "        .tm_mon = 0,\r\n" + "        .tm_mday = 1,\r\n" + "        .tm_hour = 0,\r\n"
				+ "        .tm_min = 0,\r\n" + "        .tm_sec = 0,\r\n" + "        .tm_isdst = -1\r\n" + "    };\r\n"
				+ "    teavm_epochStart = portable_timegm(&epochStart);\r\n"
				+ "    localtime_r(&teavm_epochStart, &teavm_epochStartTm);\r\n" + "}\r\n" + "\r\n"
				+ "inline static int64_t teavm_date_timestamp(struct tm *t) {\r\n"
				+ "    time_t result = mktime(t);\r\n"
				+ "    return (int64_t) (1000 * difftime(result, teavm_epochStart));\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_timeToTimestamp(time_t t) {\r\n"
				+ "    return (int64_t) (1000 * difftime(t, teavm_epochStart));\r\n" + "}\r\n" + "\r\n"
				+ "time_t teavm_date_timestampToTime(int64_t timestamp) {\r\n"
				+ "    int64_t seconds = (timestamp / 1000);\r\n" + "    struct tm t = {\r\n"
				+ "        .tm_year = 70,\r\n" + "        .tm_mon = 0,\r\n" + "        .tm_mday = 1,\r\n"
				+ "        .tm_hour = (int) (seconds / 3600),\r\n"
				+ "        .tm_min = (int) ((seconds / 60) % 60),\r\n" + "        .tm_sec = (int) (seconds % 60),\r\n"
				+ "        .tm_isdst = -1\r\n" + "    };\r\n" + "    return timegm(&t) + timestamp % 1000;\r\n"
				+ "}\r\n" + "\r\n"
				+ "inline static struct tm* teavm_date_decompose(int64_t timestamp, struct tm *t) {\r\n"
				+ "    *t = teavm_epochStartTm;\r\n" + "    int64_t seconds = (timestamp / 1000);\r\n"
				+ "    t->tm_sec += (int) (seconds % 60);\r\n" + "    t->tm_min += (int) ((seconds / 60) % 60);\r\n"
				+ "    t->tm_hour += (int) (seconds / 3600);\r\n" + "    mktime(t);\r\n" + "    return t;\r\n" + "}\r\n"
				+ "\r\n"
				+ "int64_t teavm_date_create(int32_t year, int32_t month, int32_t day, int32_t hour, int32_t minute, int32_t second) {\r\n"
				+ "    struct tm t = {\r\n" + "        .tm_year = year,\r\n" + "        .tm_mon = month,\r\n"
				+ "        .tm_mday = day,\r\n" + "        .tm_hour = hour,\r\n" + "        .tm_min = minute,\r\n"
				+ "        .tm_sec = second,\r\n" + "        .tm_isdst = -1\r\n" + "    };\r\n"
				+ "    return teavm_date_timestamp(&t);\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_createUtc(int32_t year, int32_t month, int32_t day, int32_t hour, int32_t minute, int32_t second) {\r\n"
				+ "    struct tm t = {\r\n" + "        .tm_year = year,\r\n" + "        .tm_mon = month,\r\n"
				+ "        .tm_mday = day,\r\n" + "        .tm_hour = hour,\r\n" + "        .tm_min = minute,\r\n"
				+ "        .tm_sec = second,\r\n" + "        .tm_isdst = -1\r\n" + "    };\r\n"
				+ "    time_t result = timegm(&t);\r\n"
				+ "    return (int64_t) (1000 * difftime(result, teavm_epochStart));\r\n" + "}\r\n" + "\r\n"
				+ "static void portable_strcpy(char* dest, size_t destSize, const char* src) {\r\n"
				+ "#if defined(_WIN32) || defined(_WIN64)\r\n" + "    strcpy_s(dest, destSize, src);\r\n"
				+ "#elif defined(__APPLE__) || defined(__unix__)\r\n" + "#if defined(HAVE_STRLCPY)\r\n"
				+ "    strlcpy(dest, src, destSize);\r\n" + "#else\r\n" + "    if (destSize > 0) {\r\n"
				+ "        strncpy(dest, src, destSize - 1);\r\n" + "        dest[destSize - 1] = '\\0';\r\n"
				+ "    }\r\n" + "#endif\r\n" + "#else\r\n" + "    if (destSize > 0) {\r\n"
				+ "        strncpy(dest, src, destSize - 1);\r\n" + "        dest[destSize - 1] = '\\0';\r\n"
				+ "    }\r\n" + "#endif\r\n" + "}\r\n" + "\r\n" + "#if defined(_WIN32) || defined(_WIN64)\r\n"
				+ "#define PORTABLE_STRTOK(buf, delim, ctx) strtok_s(buf, delim, ctx)\r\n" + "#else\r\n"
				+ "#define PORTABLE_STRTOK(buf, delim, ctx) strtok_r(buf, delim, ctx)\r\n" + "#endif\r\n" + "\r\n"
				+ "static int portable_strptime(const char* s, struct tm* t) {\r\n" + "    if (!s || !t) return 0;\r\n"
				+ "\r\n" + "    char weekDay[8] = { 0 }, monthStr[8] = { 0 }, tz[8] = { 0 };\r\n"
				+ "    int day = 0, hour = 0, minute = 0, second = 0, year = 0;\r\n" + "\r\n" + "    char buf[128];\r\n"
				+ "    portable_strcpy(buf, sizeof(buf), s);\r\n" + "\r\n"
				+ "#if defined(_WIN32) || defined(_WIN64)\r\n" + "    char* context = NULL;\r\n"
				+ "    char* token = PORTABLE_STRTOK(buf, \" \", &context);\r\n" + "#else\r\n"
				+ "    char* saveptr = NULL;\r\n" + "    char* token = PORTABLE_STRTOK(buf, \" \", &saveptr);\r\n"
				+ "#endif\r\n" + "\r\n" + "    if (token) portable_strcpy(weekDay, sizeof(weekDay), token);\r\n"
				+ "\r\n" + "    token = PORTABLE_STRTOK(NULL, \" \", &context);\r\n"
				+ "    if (token) portable_strcpy(monthStr, sizeof(monthStr), token);\r\n" + "\r\n"
				+ "    token = PORTABLE_STRTOK(NULL, \" \", &context);\r\n"
				+ "    if (token) day = (int)strtol(token, NULL, 10);\r\n" + "\r\n"
				+ "    token = PORTABLE_STRTOK(NULL, \":\", &context);\r\n"
				+ "    if (token) hour = (int)strtol(token, NULL, 10);\r\n" + "\r\n"
				+ "    token = PORTABLE_STRTOK(NULL, \":\", &context);\r\n"
				+ "    if (token) minute = (int)strtol(token, NULL, 10);\r\n" + "\r\n"
				+ "    token = PORTABLE_STRTOK(NULL, \" \", &context);\r\n"
				+ "    if (token) second = (int)strtol(token, NULL, 10);\r\n" + "\r\n"
				+ "    token = PORTABLE_STRTOK(NULL, \" \", &context);\r\n"
				+ "    if (token) portable_strcpy(tz, sizeof(tz), token);\r\n" + "\r\n"
				+ "    token = PORTABLE_STRTOK(NULL, \" \", &context);\r\n"
				+ "    if (token) year = (int)strtol(token, NULL, 10);\r\n" + "\r\n" + "    if (year > 0) {\r\n"
				+ "        static const char* months[] = {\r\n"
				+ "            \"Jan\",\"Feb\",\"Mar\",\"Apr\",\"May\",\"Jun\",\r\n"
				+ "            \"Jul\",\"Aug\",\"Sep\",\"Oct\",\"Nov\",\"Dec\"\r\n" + "        };\r\n"
				+ "        int monthIndex = -1;\r\n" + "        for (int i = 0; i < 12; i++) {\r\n"
				+ "            if (strcasecmp(monthStr, months[i]) == 0) {\r\n" + "                monthIndex = i;\r\n"
				+ "                break;\r\n" + "            }\r\n" + "        }\r\n"
				+ "        if (monthIndex != -1) {\r\n" + "            t->tm_year = year - 1900;\r\n"
				+ "            t->tm_mon = monthIndex;\r\n" + "            t->tm_mday = day;\r\n"
				+ "            t->tm_hour = hour;\r\n" + "            t->tm_min = minute;\r\n"
				+ "            t->tm_sec = second;\r\n" + "            t->tm_isdst = -1;\r\n"
				+ "            return 1;\r\n" + "        }\r\n" + "    }\r\n" + "\r\n"
				+ "    if (strlen(s) >= 19) {\r\n" + "        int y = (int)strtol(s, NULL, 10);\r\n"
				+ "        int m = (int)strtol(s + 5, NULL, 10);\r\n"
				+ "        int d = (int)strtol(s + 8, NULL, 10);\r\n"
				+ "        int h = (int)strtol(s + 11, NULL, 10);\r\n"
				+ "        int min = (int)strtol(s + 14, NULL, 10);\r\n"
				+ "        int sec = (int)strtol(s + 17, NULL, 10);\r\n" + "\r\n" + "        t->tm_year = y - 1900;\r\n"
				+ "        t->tm_mon = m - 1;\r\n" + "        t->tm_mday = d;\r\n" + "        t->tm_hour = h;\r\n"
				+ "        t->tm_min = min;\r\n" + "        t->tm_sec = sec;\r\n" + "        t->tm_isdst = -1;\r\n"
				+ "        return 1;\r\n" + "    }\r\n" + "\r\n" + "    return 0;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_parse(char* s) {\r\n" + "    struct tm t;\r\n" + "#if TEAVM_UNIX\r\n"
				+ "    if (strptime(s, teavm_date_defaultFormat, &t) == NULL) {\r\n" + "        return 0;\r\n"
				+ "    }\r\n" + "#else\r\n" + "    if (!portable_strptime(s, &t)) {\r\n" + "        return 0; \r\n"
				+ "    }\r\n" + "#endif\r\n" + "    time_t result = mktime(&t);\r\n"
				+ "    return (int64_t)(1000 * difftime(result, teavm_epochStart));\r\n" + "}\r\n" + "\r\n"
				+ "int32_t teavm_date_getYear(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_year;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_setYear(int64_t time, int32_t year) {\r\n" + "    struct tm t;\r\n"
				+ "    teavm_date_decompose(time, &t)->tm_year = year;\r\n" + "    return teavm_date_timestamp(&t);\r\n"
				+ "}\r\n" + "\r\n" + "int32_t teavm_date_getMonth(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_mon;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_setMonth(int64_t time, int32_t month) {\r\n" + "    struct tm t;\r\n"
				+ "    teavm_date_decompose(time, &t)->tm_mon = month;\r\n" + "    return teavm_date_timestamp(&t);\r\n"
				+ "}\r\n" + "\r\n" + "int32_t teavm_date_getDate(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_mday;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_setDate(int64_t time, int32_t date) {\r\n" + "    struct tm t;\r\n"
				+ "    teavm_date_decompose(time, &t)->tm_mday = date;\r\n" + "    return teavm_date_timestamp(&t);\r\n"
				+ "}\r\n" + "\r\n" + "int32_t teavm_date_getDay(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_wday;\r\n" + "}\r\n" + "\r\n"
				+ "int32_t teavm_date_getHours(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_hour;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_setHours(int64_t time, int32_t hours) {\r\n" + "    struct tm t;\r\n"
				+ "    teavm_date_decompose(time, &t)->tm_hour = hours;\r\n"
				+ "    return teavm_date_timestamp(&t);\r\n" + "}\r\n" + "\r\n"
				+ "int32_t teavm_date_getMinutes(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_min;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_setMinutes(int64_t time, int32_t minutes) {\r\n" + "    struct tm t;\r\n"
				+ "    teavm_date_decompose(time, &t)->tm_min = minutes;\r\n"
				+ "    return teavm_date_timestamp(&t);\r\n" + "}\r\n" + "\r\n"
				+ "int32_t teavm_date_getSeconds(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    return (int32_t) teavm_date_decompose(time, &t)->tm_sec;\r\n" + "}\r\n" + "\r\n"
				+ "int64_t teavm_date_setSeconds(int64_t time, int32_t seconds) {\r\n" + "    struct tm t;\r\n"
				+ "    teavm_date_decompose(time, &t)->tm_sec = seconds;\r\n"
				+ "    return teavm_date_timestamp(&t);\r\n" + "}\r\n" + "\r\n"
				+ "char* teavm_date_format(int64_t time) {\r\n" + "    struct tm t;\r\n"
				+ "    t = teavm_epochStartTm;\r\n" + "    int64_t seconds = (time / 1000);\r\n"
				+ "    t.tm_sec += (int) (seconds % 60);\r\n" + "    t.tm_min += (int) ((seconds / 60) % 60);\r\n"
				+ "    t.tm_hour += (int) (seconds / 3600);\r\n" + "    mktime(&t);\r\n"
				+ "    strftime(teavm_date_formatBuffer, 512, teavm_date_defaultFormat, &t);\r\n"
				+ "    return teavm_date_formatBuffer;\r\n" + "}\r\n" + "\r\n" + "");
		fixContexts.add(fix1);
		fixContexts.add(fix2);
		fixContexts.add(fix3);
		fixContexts.add(fix4);
		fixContexts.add(fix5);
		fixContexts.add(fix6);
	}

	public TArray<FileFix> getFixList() {
		return fixContexts;
	}

}
