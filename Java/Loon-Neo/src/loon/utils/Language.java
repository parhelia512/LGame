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
package loon.utils;

public class Language {

	public static enum LanguageType {
		LANG_UNKNOWN, LANG_ENGLISH, LANG_GERMAN, LANG_SPANISH, LANG_FRENCH, LANG_ITALIAN, LANG_RUSSIAN, LANG_GREEK,
		LANG_JAPANESE, LANG_CHINESE, LANG_KOREAN, LANG_THAI, LANG_VIETNAMESE, LANG_ARABIC, LANG_PORTUGUESE, LANG_HINDI,
		LANG_BENGALI, LANG_MIXED
	}

	public static String toString(LanguageType lang) {
		if (lang == null) {
			return "Unknown";
		}
		switch (lang) {
		case LANG_ENGLISH:
			return "English";
		case LANG_GERMAN:
			return "German";
		case LANG_SPANISH:
			return "Spanish";
		case LANG_FRENCH:
			return "French";
		case LANG_ITALIAN:
			return "Italian";
		case LANG_PORTUGUESE:
			return "Portuguese";
		case LANG_RUSSIAN:
			return "Russian";
		case LANG_GREEK:
			return "Greek";
		case LANG_JAPANESE:
			return "Japanese";
		case LANG_CHINESE:
			return "Chinese";
		case LANG_KOREAN:
			return "Korean";
		case LANG_THAI:
			return "Thai";
		case LANG_VIETNAMESE:
			return "Vietnamese";
		case LANG_ARABIC:
			return "Arabic";
		case LANG_HINDI:
			return "Hindi";
		case LANG_BENGALI:
			return "Bengali";
		case LANG_MIXED:
			return "Mixed";
		default:
			return "Unknown";
		}
	}

	public static Language toLanguage(String localeStr) {
		if (StringUtils.isEmpty(localeStr)) {
			return getDefault();
		}
		String language = "";
		String country = "";
		String variant = "";
		// 先找分隔符，优先 '-'，其次 '_'
		int sepIndex = localeStr.indexOf('-');
		if (sepIndex < 0) {
			sepIndex = localeStr.indexOf('_');
		}
		if (sepIndex >= 0) {
			language = localeStr.substring(0, sepIndex);
			String remainder = localeStr.substring(sepIndex + 1);
			int secondSep = remainder.indexOf('-');
			if (secondSep < 0) {
				secondSep = remainder.indexOf('_');
			}
			if (secondSep >= 0) {
				country = remainder.substring(0, secondSep);
				variant = remainder.substring(secondSep + 1);
			} else {
				country = remainder;
			}
		} else {
			language = localeStr;
		}
		return new Language(language, country, variant);
	}

	public static LanguageType detectLanguage(final CharSequence cs) {
		if (StringUtils.isEmpty(cs)) {
			return LanguageType.LANG_UNKNOWN;
		}
		LanguageType checklang = LanguageType.LANG_UNKNOWN;
		for (int i = 0; i < cs.length(); i++) {
			char ch = cs.charAt(i);
			if (CharUtils.isReserved(ch)) {
				continue;
			}
			LanguageType lang = getLanguage(ch);
			if (lang == LanguageType.LANG_UNKNOWN) {
				return LanguageType.LANG_UNKNOWN;
			}
			if (checklang == LanguageType.LANG_UNKNOWN) {
				checklang = lang;
			} else if (checklang != lang) {
				return LanguageType.LANG_MIXED;
			}
		}
		return checklang;
	}

	public static LanguageType getLanguage(int ch) {
		// 英语
		if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
			return LanguageType.LANG_ENGLISH;
		}
		// 德语
		if (ch == 0x00DF || ch == 0x00FC || ch == 0x00F6 || ch == 0x00E4 || ch == 0x00DC || ch == 0x00D6
				|| ch == 0x00C4) {
			return LanguageType.LANG_GERMAN;
		}
		// 西班牙语
		if (ch == 0x00F1 || ch == 0x00E1 || ch == 0x00E9 || ch == 0x00ED || ch == 0x00F3 || ch == 0x00FA
				|| ch == 0x00FC) {
			return LanguageType.LANG_SPANISH;
		}
		// 法语
		if (ch == 0x00E7 || ch == 0x00E9 || ch == 0x00E8 || ch == 0x00EA || ch == 0x00E0 || ch == 0x00E2 || ch == 0x00EE
				|| ch == 0x00F4 || ch == 0x00FB) {
			return LanguageType.LANG_FRENCH;
		}
		// 意大利语
		if (ch == 0x00E0 || ch == 0x00E8 || ch == 0x00E9 || ch == 0x00EC || ch == 0x00F2 || ch == 0x00F9) {
			return LanguageType.LANG_ITALIAN;
		}
		// 葡萄牙语
		if (ch == 0x00E3 || ch == 0x00F5 || ch == 0x00E7) {
			return LanguageType.LANG_PORTUGUESE;
		}
		// 俄语
		if ((ch >= 0x0410 && ch <= 0x042F) || (ch >= 0x0430 && ch <= 0x044F)) {
			return LanguageType.LANG_RUSSIAN;
		}
		// 希腊语
		if ((ch >= 0x0391 && ch <= 0x03A9) || (ch >= 0x03B1 && ch <= 0x03C9)) {
			return LanguageType.LANG_GREEK;
		}
		// 日语
		if ((ch >= 0x3040 && ch <= 0x309F) || (ch >= 0x30A0 && ch <= 0x30FF)) {
			return LanguageType.LANG_JAPANESE;
		}
		// 中文
		if ((ch >= 0x4E00 && ch <= 0x9FFF) || (ch >= 0x3400 && ch <= 0x4DBF)) {
			return LanguageType.LANG_CHINESE;
		}
		// 韩文
		if ((ch >= 0xAC00 && ch <= 0xD7AF) || (ch >= 0x1100 && ch <= 0x11FF)) {
			return LanguageType.LANG_KOREAN;
		}
		// 泰文
		if (ch >= 0x0E00 && ch <= 0x0E7F) {
			return LanguageType.LANG_THAI;
		}
		// 越南语
		if ((ch == 0x0102 || ch == 0x0103 || ch == 0x0110 || ch == 0x0111) || (ch >= 0x1EA0 && ch <= 0x1EF9)) {
			return LanguageType.LANG_VIETNAMESE;
		}
		// 阿拉伯语
		if ((ch >= 0x0600 && ch <= 0x06FF) || (ch >= 0x0750 && ch <= 0x077F)) {
			return LanguageType.LANG_ARABIC;
		}
		// 印地语 (天城文)
		if (ch >= 0x0900 && ch <= 0x097F) {
			return LanguageType.LANG_HINDI;
		}
		// 孟加拉语
		if (ch >= 0x0980 && ch <= 0x09FF) {
			return LanguageType.LANG_BENGALI;
		}
		return LanguageType.LANG_UNKNOWN;
	}

	private static Language ROOT_LANG;

	private String _language;

	private String _country;

	private String _variant;

	public Language(String language, String country, String variant) {
		this._language = language;
		this._country = country;
		this._variant = variant;
	}

	public Language(String language, String country) {
		this(language, country, "");
	}

	public Language(String language) {
		this(language, "", "");
	}

	public static final Language getDefault() {
		if (ROOT_LANG == null) {
			ROOT_LANG = getEN();
		}
		return ROOT_LANG;
	}

	public static final Language getEN() {
		return new Language("en", "");
	}

	public static final Language getJP() {
		return new Language("ja", "JP");
	}

	public static final Language getKR() {
		return new Language("ko", "KR");
	}

	public static final Language getZHCN() {
		return new Language("zh", "CN");
	}

	public static final Language getZHTW() {
		return new Language("zh", "TW");
	}

	public static final Language getZHHK() {
		return new Language("zh", "HK");
	}

	public String getLanguage() {
		return StringUtils.isEmpty(_language) ? " " : _language;
	}

	public String getCountry() {
		return StringUtils.isEmpty(_country) ? " " : _country;
	}

	public String getVariant() {
		return StringUtils.isEmpty(_variant) ? " " : _variant;
	}

	public void setVariant(String variant) {
		this._variant = variant;
	}

	public void setLanguage(String language) {
		this._language = language;
	}

	public void setCountry(String country) {
		this._country = country;
	}

	@Override
	public String toString() {
		return getLanguage() + "_" + getCountry() + "_" + getVariant();
	}

}
