/**
 * Copyright 2008 - 2009
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
 * @project loonframework
 * @author chenpeng
 * @email：ceponline@yahoo.com.cn
 * @version 0.1
 */
package loon.log;

import loon.LSystem;
import loon.utils.MathUtils;

public class LogFormat {

	final static private int TIME_INDEX = 0;

	final static private int APP_INDEX = 1;

	final static private int MODULE_INDEX = 2;

	final static private int MESSAGE_INDEX = 3;

	final static private String[] LOG_TITLE = { "time", "app", "module", "message" };

	final static private String[] LOG_TAG = { "-", "-", "-", "-" };

	private int limitTagSize;

	private int count;

	private String logMsg;

	private boolean show;

	protected final int[] logTypeStyle;

	protected int logType;

	public LogFormat(boolean s, int t) {
		this(s, t, 25, 15, 7, 256, 64);
	}

	public LogFormat(boolean s, int t, int timeSize, int appSize, int moduleSize, int messageSize, int maxTagSize) {
		this.show = s;
		this.logType = t;
		this.limitTagSize = maxTagSize;
		this.logTypeStyle = new int[MESSAGE_INDEX + 1];
		logTypeStyle[TIME_INDEX] = timeSize;
		logTypeStyle[APP_INDEX] = appSize;
		logTypeStyle[MODULE_INDEX] = moduleSize;
		logTypeStyle[MESSAGE_INDEX] = messageSize;
	}

	private String formatString(String str[], String pad, String sp) {
		return formatString(str, pad, sp, true);
	}

	private String formatString(String str[], String pad, String sp, boolean tag) {
		StringBuffer sbr = new StringBuffer();
		if (str == null || str.length == 0) {
			return "";
		}
		if (logTypeStyle == null) {
			throw new IllegalStateException("logTypeStyle is null");
		}
		int maxIndex = Math.min(str.length, logTypeStyle.length);
		for (int i = 0; i < maxIndex; i++) {
			String cur = str[i] == null ? "" : str[i];
			int size = cur.length();
			int padTo = MathUtils.min(logTypeStyle[i], limitTagSize);
			if (tag) {
				if (size > logTypeStyle[i] || size > limitTagSize) {
					int cut = Math.min(Math.min(logTypeStyle[i], limitTagSize), size);
					sbr.append(cur.substring(0, cut)).append(sp);
					continue;
				}
				sbr.append(cur);
				for (int j = size; j < padTo; j++) {
					sbr.append(pad);
				}
				sbr.append(sp);
			} else {
				if (size > logTypeStyle[i]) {
					int cut = Math.min(logTypeStyle[i], size);
					sbr.append(cur.substring(0, cut)).append(sp);
					continue;
				}
				sbr.append(cur);
				int padLimit = Math.max(0, logTypeStyle[i]);
				for (int j = size; j < padLimit; j++) {
					sbr.append(pad);
				}
				sbr.append(sp);
			}
		}
		if (str.length > logTypeStyle.length) {
			for (int i = logTypeStyle.length; i < str.length; i++) {
				String cur = str[i] == null ? "" : str[i];
				sbr.append(cur).append(sp);
			}
		}
		return sbr.toString();
	}

	public synchronized void title(int flag, String msg) {
		switch (flag) {
		case 0:
			System.out.print(msg);
			break;
		case 1:
			System.err.print(msg);
			break;
		}
	}

	public synchronized void out(String msg) {
		if (!show) {
			return;
		}
		title(logType, msg);
	}

	public boolean isShow() {
		return show;
	}

	public void setShow(boolean show) {
		this.show = show;
	}

	public int getLimitTagSize() {
		return limitTagSize;
	}

	public void setLimitTagSize(int tagSize) {
		this.limitTagSize = tagSize;
	}

	public synchronized void out(String tm, String app, String level, String msg) {
		String value[] = { tm, app, level, msg };
		if (count++ % 9999 == 0) {
			logMsg = new StringBuffer(formatString(LOG_TAG, "-", " ")).append(LSystem.LS)
					.append(formatString(LOG_TITLE, " ", " ")).append(LSystem.LS)
					.append(formatString(LOG_TAG, "-", " ")).append(LSystem.LS).append(formatString(value, " ", " "))
					.append(LSystem.LS).toString();
		} else {
			logMsg = formatString(value, " ", " ", false) + LSystem.LS;
		}
		out(logMsg);
	}

}
