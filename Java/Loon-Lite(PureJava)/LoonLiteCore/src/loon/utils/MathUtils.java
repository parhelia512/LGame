/**
 * Copyright 2008 - 2012
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
 * @version 0.3.3
 */
package loon.utils;

import loon.LObject;
import loon.LSysException;
import loon.LSystem;
import loon.geom.RangeI;
import loon.geom.RectBox;
import loon.geom.SetXY;
import loon.geom.Vector2f;
import loon.geom.Vector3f;
import loon.geom.Vector4f;
import loon.geom.XY;

public final class MathUtils {

	private MathUtils() {
	}

	public static final Random random = new Random();

	private static final RectBox TEMP_RECT = new RectBox();

	public static final float FLOAT_ROUNDING_ERROR = 0.000001f;

	public static final float ZEROTOLERANCE = 1e-6f;

	public static final int ZERO_FIXED = 0;

	public static final int ONE_FIXED = 1 << 16;

	public static final float EPSILON = 0.001f;

	public static final float NaN = 0.0f / 0.0f;

	public static final int PI_FIXED = 205887;

	public static final int PI_OVER_2_FIXED = PI_FIXED / 2;

	public static final int E_FIXED = 178145;

	public static final int HALF_FIXED = 2 << 15;

	protected final static int TO_STRING_DECIMAL_PLACES = 3;

	private static final String[] ZEROS = { "", "0", "00", "000", "0000", "00000", "000000", "0000000", "00000000",
			"000000000", "0000000000" };

	private static final int[] SHIFT = { 0, 1144, 2289, 3435, 4583, 5734, 6888, 8047, 9210, 10380, 11556, 12739, 13930,
			15130, 16340, 17560, 18792, 20036, 21294, 22566, 23853, 25157, 26478, 27818, 29179, 30560, 31964, 33392,
			34846, 36327, 37837, 39378, 40951, 42560, 44205, 45889, 47615, 49385, 51202, 53070, 54991, 56970, 59009,
			61113, 63287, 65536 };

	public static final float PI_OVER2 = 1.5708f;

	public static final float PI_OVER4 = 0.785398f;

	public static final float PHI = 0.618f;

	public static final float PI_4 = 12.566371f;

	public static final float PI_4D3 = 4.1887903f;

	public static final float TAU = 6.28318548f;

	private static final float CEIL = 0.9999999f;

	private static final int BIG_ENOUGH_INT = 16384;

	private static final float BIG_ENOUGH_CEIL = 16384.998f;

	private static final float BIG_ENOUGH_ROUND = BIG_ENOUGH_INT + 0.5f;

	private static final float BIG_ENOUGH_FLOOR = BIG_ENOUGH_INT;

	private static final int ATAN2_BITS = 7;

	private static final int ATAN2_BITS2 = ATAN2_BITS << 1;

	private static final int ATAN2_MASK = ~(-1 << ATAN2_BITS2);

	private static final int ATAN2_COUNT = ATAN2_MASK + 1;

	public static final int ATAN2_DIM = 128;

	private static final float INV_ATAN2_DIM_MINUS_1 = 1.0f / (ATAN2_DIM - 1);

	public static final float PI = 3.1415927f;

	public static final float TWO_PI = 6.28319f;

	public static final float HALF_PI = PI / 2f;

	public static final float SQRT2 = 1.4142135f;

	public static final float DEG_FULL = 360f;

	private static final int SIN_BITS = 13;

	private static final int SIN_MASK = ~(-1 << SIN_BITS);

	private static final int SIN_COUNT = SIN_MASK + 1;

	private static final float RAD_FULL = PI * 2;

	private static final float RAD_TO_INDEX = SIN_COUNT / RAD_FULL;

	private static final float DEG_TO_INDEX = SIN_COUNT / DEG_FULL;

	public static final float RAD_TO_DEG = 180.0f / PI;

	public static final float DEG_TO_RAD = PI / 180.0f;

	public static final float GRAD_TO_RAD = PI / 200.0f;

	public static final float RAD_TO_GRAD = 200.0f / PI;

	public static final float GRAD_TO_DEG = 9.0f / 10.0f;

	public static final float MINUTE = 0.000046296296296296f;

	public static final float SECOND = 0.000000771604938272f;

	public static final float MILLIRADIAN = 0.0001591549431f;

	public enum RotationType {
		Shortest, Longest, Clockwise, CounterClockwise
	}

	static final private class SinCos {

		static final float[] SIN_LIST = new float[SIN_COUNT];

		static final float[] COS_LIST = new float[SIN_COUNT];

		static {
			for (int i = 0; i < SIN_COUNT; i++) {
				float a = (i + 0.5f) / SIN_COUNT * RAD_FULL;
				SIN_LIST[i] = (float) Math.sin(a);
				COS_LIST[i] = (float) Math.cos(a);
			}
		}
	}

	static final private class Atan2 {

		static final float[] TABLE = new float[ATAN2_COUNT];

		static {
			for (int i = 0; i < ATAN2_DIM; i++) {
				for (int j = 0; j < ATAN2_DIM; j++) {
					float x0 = (float) i / ATAN2_DIM;
					float y0 = (float) j / ATAN2_DIM;
					TABLE[j * ATAN2_DIM + i] = (float) Math.atan2(y0, x0);
				}
			}
		}
	}

	public static RectBox getBounds(float x, float y, float width, float height, float rotate, float cx, float cy,
			float scaleX, float scaleY, RectBox result) {
		if (rotate == 0 && MathUtils.equal(scaleX, 1f) && MathUtils.equal(scaleY, 1f)) {
			if (result == null) {
				result = new RectBox(x, y, width, height);
			} else {
				result.setBounds(x, y, width, height);
			}
			return result;
		}
		return getLimit(x, y, width, height, rotate, cx, cy, scaleX, scaleY, result);
	}

	public static RectBox getBounds(float x, float y, float width, float height, float rotate, float scaleX,
			float scaleY, RectBox result) {
		if (rotate == 0 && MathUtils.equal(scaleX, 1f) && MathUtils.equal(scaleY, 1f)) {
			if (result == null) {
				result = new RectBox(x, y, width, height);
			} else {
				result.setBounds(x, y, width, height);
			}
			return result;
		}
		return getLimit(x, y, width, height, rotate, scaleX, scaleY, result);
	}

	public static RectBox getBounds(float x, float y, float width, float height, float rotate, float scaleX,
			float scaleY) {
		return getBounds(x, y, width, height, rotate, scaleX, scaleY, TEMP_RECT);
	}

	public static boolean isZero(float value, float tolerance) {
		return MathUtils.abs(value) <= tolerance;
	}

	public static boolean isZero(float value) {
		return isZero(value, ZEROTOLERANCE);
	}

	public static boolean isEqual(float a, float b) {
		return MathUtils.abs(a - b) <= FLOAT_ROUNDING_ERROR;
	}

	public static boolean isEqual(float a, float b, float tolerance) {
		return MathUtils.abs(a - b) <= tolerance;
	}

	public static boolean isOdd(int i) {
		return (i % 2 != 0);
	}

	public static int isAnd(float v, int result) {
		return (v % 2 != 0) ? result : 0;
	}

	public static boolean isPowerOfTwo(int w, int h) {
		return (w > 0 && (w & (w - 1)) == 0 && h > 0 && (h & (h - 1)) == 0);
	}

	public static boolean isPowerOfTwo(int n) {
		int i = 1;
		for (;;) {
			if (i > n) {
				return false;
			}
			if (i == n) {
				return true;
			}
			i = i * 2;
		}
	}

	public static boolean nearEqual(float n1, float n2) {
		if (isZero(n1 - n2)) {
			return true;
		}
		return false;
	}

	public static float fastInvSqrt(float value) {
		if (isZero(value)) {
			return value;
		}
		return 1f / sqrt(value);
	}

	public static int previousPowerOfTwo(int value) {
		final int power = (int) (log(value) / log(2));
		return (int) pow(2, power);
	}

	public static int precision(float v) {
		int e = 1;
		int p = 0;
		while (abs((round(v * e) / e) - v) > FLOAT_ROUNDING_ERROR) {
			e *= 10;
			p++;
		}
		return p;
	}

	public static int nextPowerOfTwo(int value) {
		if (value == 0)
			return 1;
		value--;
		value |= value >> 1;
		value |= value >> 2;
		value |= value >> 4;
		value |= value >> 8;
		value |= value >> 16;
		return value + 1;
	}

	/**
	 * 比较数值大小，将角度归一化到min, max区间
	 * 
	 * @param value
	 * @param min
	 * @param max
	 * @return
	 */
	public static float wrapCompare(float value, float min, float max) {
		float step = max - min;
		float newValue = (value - min) % step;
		if (newValue < 0) {
			newValue += step;
		}
		return newValue + min;
	}

	/**
	 * 计算旋转矩形的外接矩形边界
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param scaleX
	 * @param scaleY
	 * @param rotate
	 * @return
	 */
	public static RectBox getLimit(float x, float y, float width, float height, float scaleX, float scaleY,
			float rotate) {
		return getLimit(x, y, width, height, rotate, scaleX, scaleY, TEMP_RECT);
	}

	/**
	 * 计算旋转矩形的外接矩形边界
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param rotate
	 * @param scaleX
	 * @param scaleY
	 * @param result
	 * @return
	 */
	public static RectBox getLimit(float x, float y, float width, float height, float rotate, float scaleX,
			float scaleY, RectBox result) {
		if (result == null) {
			result = new RectBox();
		}
		if ((!MathUtils.equal(scaleX, 1f) || !MathUtils.equal(scaleY, 1f)) && (scaleX > 0.5f || scaleY > 0.5f)) {
			result.set(x - width / 2f, y - height / 2f, width, height);
		} else {
			result.set(x, y, width, height);
		}
		result.setRotation(rotate);
		return result;
	}

	/**
	 * 计算旋转矩形的外接矩形边界
	 *
	 * @param x
	 * @param y
	 * @param width
	 * @param height 矩形高度
	 * @param rotate
	 * @param pivotX
	 * @param pivotY
	 * @param scaleX
	 * @param scaleY
	 * 
	 * @return [newX, newY, newW, newH]
	 */
	public static RectBox getLimit(float x, float y, float width, float height, float rotate, float pivotX,
			float pivotY, float scaleX, float scaleY, RectBox result) {
		if (result == null) {
			result = new RectBox();
		}
		final float limitWidth = width * scaleX;
		final float limitHeight = height * scaleY;
		if ((!MathUtils.equal(scaleX, 1f) || !MathUtils.equal(scaleY, 1f))
				&& (limitWidth > pivotX || limitHeight > pivotY)) {
			result.set(x - pivotX, y - pivotY, width, height);
		} else {
			result.set(x, y, width, height);
		}
		result.setRotation(rotate, pivotX, pivotY);
		return result;
	}

	public static TArray<RectBox> getLimitsBatch(TArray<RectBox> rects, float pivotX, float pivotY) {
		TArray<RectBox> results = new TArray<RectBox>();
		for (RectBox rect : rects) {
			float x = rect.x;
			float y = rect.y;
			float width = rect.width;
			float height = rect.height;
			float rotate = rect.getRotation();
			results.add(getLimit(x, y, width, height, rotate, pivotX, pivotY, 1f, 1f, null));
		}
		return results;
	}

	public static TArray<RectBox> getLimitsBatchCentered(TArray<RectBox> rects) {
		TArray<RectBox> results = new TArray<RectBox>();
		for (RectBox rect : rects) {
			float x = rect.x;
			float y = rect.y;
			float width = rect.width;
			float height = rect.height;
			float rotate = rect.getRotation();
			results.add(getLimit(x, y, width, height, rotate, 1f, 1f, null));
		}
		return results;
	}

	public static int divTwoAbs(int v) {
		if (v % 2 != 0) {
			v += 1;
		}
		return abs(v);
	}

	/**
	 * 为指定数值补足位数
	 * 
	 * @param number
	 * @param numDigits
	 * @return
	 */
	public static String addZeros(long number, int numDigits) {
		return addZeros(String.valueOf(number), numDigits);
	}

	/**
	 * 为指定数值补足位数
	 * 
	 * @param number
	 * @param numDigit
	 * @return
	 */
	public static String addZeros(String number, int numDigit) {
		return addZeros(number, numDigit, false);
	}

	/**
	 * 为指定数值补足位数
	 * 
	 * @param number
	 * @param numDigits
	 * @return
	 */
	public static String addZeros(String number, int numDigits, boolean reverse) {
		int length = numDigits - number.length();
		if (length > -1) {
			if (length - 1 < ZEROS.length) {
				if (reverse) {
					number = number + ZEROS[length];
				} else {
					number = ZEROS[length] + number;
				}
			} else {
				StrBuilder sbr = new StrBuilder();
				for (int i = 0; i < length; i++) {
					sbr.append('0');
				}
				if (reverse) {
					number = number + sbr.toString();
				} else {
					number = sbr.toString() + number;
				}
			}
		}
		return number;
	}

	/**
	 * 返回数字的位数长度
	 * 
	 * @param num
	 * @return
	 */
	public static int getBitSize(int num) {
		int numBits = 0;
		if (num < 10l) {
			numBits = 1;
		} else if (num < 100l) {
			numBits = 2;
		} else if (num < 1000l) {
			numBits = 3;
		} else if (num < 10000l) {
			numBits = 4;
		} else if (num < 100000l) {
			numBits = 5;
		} else if (num < 1000000l) {
			numBits = 6;
		} else if (num < 10000000l) {
			numBits = 7;
		} else if (num < 100000000l) {
			numBits = 8;
		} else if (num < 1000000000l) {
			numBits = 9;
		} else {
			numBits = (String.valueOf(num).length() - 1);
		}
		return numBits;
	}

	/**
	 * 返回浮点数'.'后长度
	 * 
	 * @param num
	 * @return
	 */
	public static int getFloatDotBackSize(float fv) {
		final String v = String.valueOf(fv);
		int idx = v.indexOf(LSystem.DOT);
		if (idx == -1) {
			return 0;
		}
		final String result = v.substring(idx + 1, v.length());
		return result.length();
	}

	public static float convertDecimalPlaces(float fv, int max) {
		final String v = String.valueOf(fv);
		int idx = v.indexOf(LSystem.DOT);
		if (idx == -1) {
			return fv;
		}
		final String numberv = v.substring(0, idx);
		String result = v.substring(idx + 1, v.length());
		if (result.length() > max) {
			result = result.substring(0, max);
		}
		final String numv = (numberv + LSystem.DOT + result).toLowerCase();
		return Float.parseFloat(StringUtils.replace(numv, "e", ""));
	}

	public static int getCircleSideCount(float radius, float maxLength) {
		float circumference = TWO_PI * radius;
		return (int) max(circumference / maxLength, 1f);
	}

	public static int getCircleArcSideCount(float radius, float angleDeg, float maxLength) {
		float circumference = TWO_PI * radius * (angleDeg / DEG_FULL);
		return (int) max(circumference / maxLength, 1f);
	}

	public static boolean isNan(float v) {
		return (v != v);
	}

	public static boolean isNan(double v) {
		return (v != v);
	}

	/**
	 * 判断是否为数字
	 * 
	 * @param param
	 * @return
	 */
	public static boolean isNan(String str) {
		if (StringUtils.isEmpty(str)) {
			return false;
		}
		char[] chars = str.toCharArray();
		int sz = chars.length;
		boolean hasExp = false;
		boolean hasDecPoint = false;
		boolean allowSigns = false;
		boolean foundDigit = false;
		int start = (chars[0] == '-') ? 1 : 0;
		int i = 0;
		if (sz > start + 1) {
			if (chars[start] == '0' && chars[start + 1] == 'x') {
				i = start + 2;
				if (i == sz) {
					return false;
				}
				for (; i < chars.length; i++) {
					if ((chars[i] < '0' || chars[i] > '9') && (chars[i] < 'a' || chars[i] > 'f')
							&& (chars[i] < 'A' || chars[i] > 'F')) {
						return false;
					}
				}
				return true;
			}
		}
		sz--;
		i = start;
		while (i < sz || (i < sz + 1 && allowSigns && !foundDigit)) {
			if (chars[i] >= '0' && chars[i] <= '9') {
				foundDigit = true;
				allowSigns = false;
			} else if (chars[i] == '.') {
				if (hasDecPoint || hasExp) {
					return false;
				}
				hasDecPoint = true;
			} else if (chars[i] == 'e' || chars[i] == 'E') {
				if (hasExp) {
					return false;
				}
				if (!foundDigit) {
					return false;
				}
				hasExp = true;
				allowSigns = true;
			} else if (chars[i] == '+' || chars[i] == '-') {
				if (!allowSigns) {
					return false;
				}
				allowSigns = false;
				foundDigit = false;
			} else {
				return false;
			}
			i++;
		}
		if (i < chars.length) {
			if (chars[i] >= '0' && chars[i] <= '9') {
				return true;
			}
			if (chars[i] == 'e' || chars[i] == 'E') {
				return false;
			}
			if (!allowSigns && (chars[i] == 'd' || chars[i] == 'D' || chars[i] == 'f' || chars[i] == 'F')) {
				return foundDigit;
			}
			if (chars[i] == 'l' || chars[i] == 'L') {
				return foundDigit && !hasExp;
			}
			return false;
		}
		return !allowSigns && foundDigit;
	}

	public static boolean isNumber(CharSequence num) {
		if (StringUtils.isEmpty(num)) {
			return false;
		}
		return isNan(num.toString());
	}

	public static boolean isBeside(double x, double y) {
		return abs(x - y) == 1;
	}

	public static boolean isBeside(float x, float y) {
		return abs(x - y) == 1;
	}

	public static boolean isBeside(long x, long y) {
		return abs(x - y) == 1;
	}

	public static boolean isBeside(int x, int y) {
		return abs(x - y) == 1;
	}

	public static int mul(int x, int y) {
		long z = (long) x * (long) y;
		return ((int) (z >> 16));
	}

	public static float mul(float x, float y) {
		long z = (long) x * (long) y;
		return ((float) (z >> 16));
	}

	public static int mulDiv(int f1, int f2, int f3) {
		return (int) ((long) f1 * f2 / f3);
	}

	public static long mulDiv(long f1, long f2, long f3) {
		return f1 * f2 / f3;
	}

	public static int modUnsigned(int num, int den) {
		int result = (num % den);
		if (result < 0) {
			result += den;
		}
		return result;
	}

	public static float modUnsigned(float num, float den) {
		float result = (num % den);
		if (result < 0) {
			result += den;
		}
		return result;
	}

	public static int mid(int i, int min, int max) {
		return limit(i, min, max);
	}

	public static float mid(float i, float min, float max) {
		return limit(i, min, max);
	}

	public static int mapValue(int v, int fromStart, int fromStop, int toStart, int toStop) {
		return (int) (toStart
				+ ((toStop - (float) toStart) * ((v - (float) fromStart) / (fromStop - (float) fromStart))));
	}

	public static float mapValue(int v, float fromStart, float fromStop, float toStart, float toStop) {
		return toStart + ((toStop - toStart) * ((v - fromStart) / (fromStop - fromStart)));
	}

	public static float moveTowards(float start, float end, float max) {
		float diff = end - start;
		float s = MathUtils.sign(diff);
		return start + MathUtils.min(max, MathUtils.abs(diff)) * s;
	}

	public static int div(int x, int y) {
		long z = (((long) x) << 32);
		return (int) ((z / y) >> 16);
	}

	public static float div(float x, float y) {
		long z = (((long) x) << 32);
		return (float) ((z / (long) y) >> 16);
	}

	public static int round(int n) {
		if (n > 0) {
			if ((n & 0x8000) != 0) {
				return (((n + 0x10000) >> 16) << 16);
			} else {
				return (((n) >> 16) << 16);
			}
		} else {
			int k;
			n = -n;
			if ((n & 0x8000) != 0) {
				k = (((n + 0x10000) >> 16) << 16);
			} else {
				k = (((n) >> 16) << 16);
			}
			return -k;
		}
	}

	public static boolean equal(float[] a, float[] b) {
		if (a == null || b == null) {
			return false;
		}
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (!equal(a[i], b[i])) {
				return false;
			}
		}
		return true;
	}

	public static boolean equal(int[] a, int[] b) {
		if (a == null || b == null) {
			return false;
		}
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (!equal(a[i], b[i])) {
				return false;
			}
		}
		return true;
	}

	public static boolean equal(boolean[] a, boolean[] b) {
		if (a == null || b == null) {
			return false;
		}
		if (a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static boolean equal(float[] points, float[] xpoints, float[] ypoints) {
		int count = 0;
		for (int i = 0; i < points.length; i++) {
			if (i % 2 == 0) {
				final float newX = xpoints[count];
				if (!equal(points[i], newX)) {
					return false;
				}
			} else {
				final float newY = ypoints[count];
				if (!equal(points[i], newY)) {
					return false;
				}
				count++;
			}
		}
		return true;
	}

	public static boolean equal(float[] points, int[] xpoints, int[] ypoints) {
		int count = 0;
		for (int i = 0; i < points.length; i++) {
			if (i % 2 == 0) {
				final float newX = xpoints[count];
				if (!equal(points[i], newX)) {
					return false;
				}
			} else {
				final float newY = ypoints[count];
				if (!equal(points[i], newY)) {
					return false;
				}
				count++;
			}
		}
		return true;
	}

	public static boolean equal(int a, int b, int epsilon) {
		if (a > b)
			return a - b <= epsilon;
		else
			return b - a <= epsilon;
	}

	public static boolean equal(int a, int b) {
		return a == b;
	}

	public static boolean equal(double a, double b, double e) {
		if (a > b)
			return a - b <= e;
		else
			return b - a <= e;
	}

	public static boolean equal(double a, double b) {
		if (a > b)
			return a - b <= EPSILON;
		else
			return b - a <= EPSILON;
	}

	public static boolean equal(float a, float b, float e) {
		if (a > b)
			return a - b <= e;
		else
			return b - a <= e;
	}

	public static boolean equal(float a, float b) {
		if (a > b)
			return a - b <= EPSILON;
		else
			return b - a <= EPSILON;
	}

	public static int sign(float x) {
		if (x > 0) {
			return 1;
		} else if (x < 0) {
			return -1;
		}
		return 0;
	}

	public static int randomSign() {
		return random.nextSignInt();
	}

	final static int SK1 = 498;

	final static int SK2 = 10882;

	public static int sinInt(int f) {
		int sign = 1;
		if ((f > PI_OVER_2_FIXED) && (f <= PI_FIXED)) {
			f = PI_FIXED - f;
		} else if ((f > PI_FIXED) && (f <= (PI_FIXED + PI_OVER_2_FIXED))) {
			f = f - PI_FIXED;
			sign = -1;
		} else if (f > (PI_FIXED + PI_OVER_2_FIXED)) {
			f = (PI_FIXED << 1) - f;
			sign = -1;
		}
		int sqr = mul(f, f);
		int result = SK1;
		result = mul(result, sqr);
		result -= SK2;
		result = mul(result, sqr);
		result += ONE_FIXED;
		result = mul(result, f);
		return sign * result;
	}

	final static int CK1 = 2328;

	final static int CK2 = 32551;

	public static int cosInt(int f) {
		int sign = 1;
		if ((f > PI_OVER_2_FIXED) && (f <= PI_FIXED)) {
			f = PI_FIXED - f;
			sign = -1;
		} else if ((f > PI_OVER_2_FIXED) && (f <= (PI_FIXED + PI_OVER_2_FIXED))) {
			f = f - PI_FIXED;
			sign = -1;
		} else if (f > (PI_FIXED + PI_OVER_2_FIXED)) {
			f = (PI_FIXED << 1) - f;
		}
		int sqr = mul(f, f);
		int result = CK1;
		result = mul(result, sqr);
		result -= CK2;
		result = mul(result, sqr);
		result += ONE_FIXED;
		return result * sign;
	}

	final static int TK1 = 13323;

	final static int TK2 = 20810;

	public static int tanInt(int f) {
		int sqr = mul(f, f);
		int result = TK1;
		result = mul(result, sqr);
		result += TK2;
		result = mul(result, sqr);
		result += ONE_FIXED;
		result = mul(result, f);
		return result;
	}

	public static int atanInt(int f) {
		int sqr = mul(f, f);
		int result = 1365;
		result = mul(result, sqr);
		result -= 5579;
		result = mul(result, sqr);
		result += 11805;
		result = mul(result, sqr);
		result -= 21646;
		result = mul(result, sqr);
		result += 65527;
		result = mul(result, f);
		return result;
	}

	final static int AS1 = -1228;

	final static int AS2 = 4866;

	final static int AS3 = 13901;

	final static int AS4 = 102939;

	public static int asinInt(int f) {
		int fRoot = sqrtInt(ONE_FIXED - f);
		int result = AS1;
		result = mul(result, f);
		result += AS2;
		result = mul(result, f);
		result -= AS3;
		result = mul(result, f);
		result += AS4;
		result = PI_OVER_2_FIXED - (mul(fRoot, result));
		return result;
	}

	public static int acosInt(int f) {
		int fRoot = sqrtInt(ONE_FIXED - f);
		int result = AS1;
		result = mul(result, f);
		result += AS2;
		result = mul(result, f);
		result -= AS3;
		result = mul(result, f);
		result += AS4;
		result = mul(fRoot, result);
		return result;
	}

	public static <T> float angleBetween(LObject<T> a1, LObject<T> b1, boolean degree) {
		if (a1 == null || b1 == null) {
			return 0f;
		}
		float dx = b1.getX() - a1.getX();
		float dy = b1.getY() - a1.getY();
		return angleFrom(dx, dy, degree);
	}

	public static <T> float degreesBetween(LObject<T> a1, LObject<T> b1) {
		return angleBetween(a1, b1, true);
	}

	public static <T> float radiansBetween(LObject<T> a1, LObject<T> b1) {
		return angleBetween(a1, b1, false);
	}

	public static <T> float angleBetweenPoint(LObject<T> o, XY pos, boolean degree) {
		float dx = pos.getX() - o.getX();
		float dy = pos.getY() - o.getY();
		return angleFrom(dx, dy, degree);
	}

	public static <T> float degreesBetweenPoint(LObject<T> o, XY pos) {
		return angleBetweenPoint(o, pos, true);
	}

	public static <T> float radiansBetweenPoint(LObject<T> o, XY pos) {
		return angleBetweenPoint(o, pos, false);
	}

	/**
	 * 转化极坐标系为笛卡尔坐标系
	 * 
	 * @param x
	 * @param y
	 * @param radius
	 * @param angle
	 * @return
	 */
	public static Vector2f getCartesianCoords(float x, float y, float radius, float angle) {
		return new Vector2f(radius * MathUtils.cos(angle * DEG_TO_RAD), radius * MathUtils.sin(angle * DEG_TO_RAD));
	}

	/**
	 * 转化笛卡尔坐标系为极坐标系
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public static Vector2f getPolarCoords(float x, float y) {
		return new Vector2f(MathUtils.sqrt((x * x) + (y * y)), degreesFrom(x, y));
	}

	public static float gauss(float amplitude, float x, float y, float centerX, float centerY, float sigmaX,
			float sigmaY) {
		float cx = x - centerX;
		float cy = y - centerY;
		float componentX = cx * cx / (2f * sigmaX * sigmaX);
		float componentY = cy * cy / (2f * sigmaY * sigmaY);
		return amplitude * exp(-(componentX + componentY));
	}

	public static float trunc(float x) {
		return x < 0f ? MathUtils.ceil(x) : MathUtils.floor(x);
	}

	public static float tan(float angle) {
		return (float) Math.tan(angle);
	}

	public static float asin(float value) {
		return (float) Math.asin(value);
	}

	public static float acos(float value) {
		return (float) Math.acos(value);
	}

	public static float atan(float value) {
		return (float) Math.atan(value);
	}

	public static float atan2(float y, float x) {
		float add, mul;
		if (x < 0) {
			if (y < 0) {
				y = -y;
				mul = 1;
			} else
				mul = -1;
			x = -x;
			add = -3.141592653f;
		} else {
			if (y < 0) {
				y = -y;
				mul = -1;
			} else
				mul = 1;
			add = 0;
		}
		float invDiv = 1 / ((x < y ? y : x) * INV_ATAN2_DIM_MINUS_1);
		int xi = (int) (x * invDiv);
		int yi = (int) (y * invDiv);
		return (Atan2.TABLE[yi * ATAN2_DIM + xi] + add) * mul;
	}

	public static float abs(float n) {
		return (n < 0) ? -n : n;
	}

	public static double abs(double n) {
		return (n < 0) ? -n : n;
	}

	public static int abs(int n) {
		return (n < 0) ? -n : n;
	}

	public static long abs(long n) {
		return (n < 0) ? -n : n;
	}

	public static float mag(float a, float b) {
		return sqrt(a * a + b * b);
	}

	public static float mag(float a, float b, float c) {
		return sqrt(a * a + b * b + c * c);
	}

	public static float mag(float a, float b, float c, float d) {
		return sqrt(a * a + b * b + c * c + d * d);
	}

	public static float median(float a, float b, float c) {
		return (a <= b) ? ((b <= c) ? b : ((a < c) ? c : a)) : ((a <= c) ? a : ((b < c) ? c : b));
	}

	public static float distance(float x1, float x2) {
		return abs(x1 - x2);
	}

	public static float distance(float x1, float y1, float x2, float y2) {
		return dist(x1, y1, x2, y2);
	}

	public static float dist(float x1, float y1) {
		return abs(x1 - y1);
	}

	public static float dist(float x1, float y1, float x2, float y2) {
		return sqrt(sq(x2 - x1) + sq(y2 - y1));
	}

	public static float dist(float x1, float y1, float z1, float x2, float y2, float z2) {
		return sqrt(sq(x2 - x1) + sq(y2 - y1) + sq(z2 - z1));
	}

	public static float distSquared(float x1, float y1, float x2, float y2) {
		return (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
	}

	public static float distRectPoint(float px, float py, float rx, float ry, float rw, float rh) {
		if (px >= rx && px <= rx + rw) {
			if (py >= ry && py <= ry + rh) {
				return 0f;
			}
			if (py > ry) {
				return py - (ry + rh);
			}
			return ry - py;
		}
		if (py >= ry && py <= ry + rh) {
			if (px > rx) {
				return px - (rx + rw);
			}
			return rx - px;
		}
		if (px > rx) {
			if (py > ry) {
				return dist(px, py, rx + rw, ry + rh);
			}
			return dist(px, py, rx + rw, ry);
		}
		if (py > ry) {
			return dist(px, py, rx, ry + rh);
		}
		return dist(px, py, rx, ry);
	}

	public static float distRects(float x1, float y1, float w1, float h1, float x2, float y2, float w2, float h2) {
		if (x1 < x2 + w2 && x2 < x1 + w1) {
			if (y1 < y2 + h2 && y2 < y1 + h1) {
				return 0f;
			}
			if (y1 > y2) {
				return y1 - (y2 + h2);
			}
			return y2 - (y1 + h1);
		}
		if (y1 < y2 + h2 && y2 < y1 + h1) {
			if (x1 > x2) {
				return x1 - (x2 + w2);
			}
			return x2 - (x1 + w1);
		}
		if (x1 > x2) {
			if (y1 > y2) {
				return dist(x1, y1, (x2 + w2), (y2 + h2));
			}
			return dist(x1, y1 + h1, x2 + w2, y2);
		}
		if (y1 > y2) {
			return dist(x1 + w1, y1, x2, y2 + h2);
		}
		return dist(x1 + w1, y1 + h1, x2, y2);
	}

	public static float sq(float a) {
		return a * a;
	}

	public static float sqrt(float a) {
		return (float) Math.sqrt(a);
	}

	public static int sqrtInt(int n) {
		int s = (n + 65536) >> 1;
		for (int i = 0; i < 8; i++) {
			s = (s + div(n, s)) >> 1;
		}
		return s;
	}

	public static Vector2f stepVector(Vector2f a, Vector2f b) {
		if (Vector2f.lessThan(a, b)) {
			return b;
		} else {
			return a;
		}
	}

	public static Vector3f stepVector(Vector3f a, Vector3f b) {
		if (Vector3f.lessThan(a, b)) {
			return b;
		} else {
			return a;
		}
	}

	public static Vector4f stepVector(Vector4f a, Vector4f b) {
		if (Vector4f.lessThan(a, b)) {
			return b;
		} else {
			return a;
		}
	}

	public static float log(float a) {
		return (float) Math.log(a);
	}

	public static float exp(float a) {
		return (float) Math.exp(a);
	}

	public static float pow(float a, float b) {
		return (float) Math.pow(a, b);
	}

	public static int max(int a, int b) {
		return (a > b) ? a : b;
	}

	public static float max(float a, float b) {
		return (a > b) ? a : b;
	}

	public static long max(long a, long b) {
		return (a > b) ? a : b;
	}

	public static int max(int a, int b, int c) {
		return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
	}

	public static float max(float a, float b, float c) {
		return (a > b) ? ((a > c) ? a : c) : ((b > c) ? b : c);
	}

	public static int max(final int... numbers) {
		int max = Integer.MIN_VALUE;
		for (int i = 0; i < numbers.length; i++) {
			if (numbers[i] > max) {
				max = numbers[i];
			}
		}
		return max;
	}

	public static float max(final float... numbers) {
		float max = Integer.MIN_VALUE;
		for (int i = 0; i < numbers.length; i++) {
			if (numbers[i] > max) {
				max = numbers[i];
			}
		}
		return max;
	}

	public static int min(int a, int b, int c) {
		return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
	}

	public static float min(float a, float b, float c) {
		return (a < b) ? ((a < c) ? a : c) : ((b < c) ? b : c);
	}

	public static float min(float a, float b) {
		return (a <= b) ? a : b;
	}

	public static int min(int a, int b) {
		return (a <= b) ? a : b;
	}

	public static int min(final int... numbers) {
		int min = Integer.MAX_VALUE;
		for (int i = 0; i < numbers.length; i++) {
			if (numbers[i] < min) {
				min = numbers[i];
			}
		}
		return min;
	}

	public static float min(final float... numbers) {
		float min = Integer.MAX_VALUE;
		for (int i = 0; i < numbers.length; i++) {
			if (numbers[i] < min) {
				min = numbers[i];
			}
		}
		return min;
	}

	public static float mix(final float x, final float y, final float m) {
		return x * (1 - m) + y * m;
	}

	public static int mix(final int x, final int y, final float m) {
		return MathUtils.round(x * (1 - m) + y * m);
	}

	public static float norm(float value, float start, float stop) {
		return (value - start) / (stop - start);
	}

	public static float map(float value, float istart, float istop, float ostart, float ostop) {
		return ostart + (ostop - ostart) * ((value - istart) / (istop - istart));
	}

	public static float sin(float n, float angle, float arc, boolean plus) {
		return plus ? n + MathUtils.sin(angle) + arc : n - MathUtils.sin(angle) * arc;
	}

	public static float sin(float rad) {
		return SinCos.SIN_LIST[(int) (rad * RAD_TO_INDEX) & SIN_MASK];
	}

	public static float sinDeg(float deg) {
		return SinCos.SIN_LIST[(int) (deg * DEG_TO_INDEX) & SIN_MASK];
	}

	public static float sinRatio(float v) {
		if (v >= 1f) {
			v = v % 1f;
		}
		return (MathUtils.sin(v * MathUtils.PI * 2f) + 1f) * 0.5f;
	}

	public static float cos(float n, float angle, float arc, boolean plus) {
		return plus ? n + MathUtils.cos(angle) + arc : n - MathUtils.cos(angle) * arc;
	}

	public static float cos(float rad) {
		return SinCos.COS_LIST[(int) (rad * RAD_TO_INDEX) & SIN_MASK];
	}

	public static float cosDeg(float deg) {
		return SinCos.COS_LIST[(int) (deg * DEG_TO_INDEX) & SIN_MASK];
	}

	public static float cosRatio(float v) {
		if (v >= 1f) {
			v = v % 1f;
		}
		return (MathUtils.cos(v * MathUtils.PI * 2f) + 1f) * 0.5f;
	}

	public static float choose(float... choices) {
		if (choices == null) {
			return 0f;
		}
		return choices[MathUtils.ifloor(random() * choices.length)];
	}

	public static boolean between(float v, float min, float max) {
		return (v > min && v < max);
	}

	public static float distanceBetween(float x1, float y1, float x2, float y2) {
		final float dx = x1 - x2;
		final float dy = y1 - y2;
		return MathUtils.sqrt(dx * dx + dy * dy);
	}

	public static float distanceBetweenPoints(XY a, XY b) {
		final float dx = a.getX() - b.getX();
		final float dy = a.getY() - b.getY();
		return MathUtils.sqrt(dx * dx + dy * dy);
	}

	public static float distanceBetweenPointsSquared(XY a, XY b) {
		final float dx = a.getX() - b.getX();
		final float dy = a.getY() - b.getY();
		return dx * dx + dy * dy;
	}

	public static float toDegrees(final float radians) {
		return radians * RAD_TO_DEG;
	}

	public static float toRadians(final float degrees) {
		return degrees * DEG_TO_RAD;
	}

	public static float toRadiansTAU(final float degrees) {
		return toRadians(degrees) * TAU;
	}

	public static float toDegreesTAU(final float radians) {
		return toDegrees(radians) * TAU;
	}

	public static float toGradiansDegrees(final float gradian) {
		return gradian * GRAD_TO_DEG;
	}

	public static float toGradiansRadians(final float gradian) {
		return gradian * GRAD_TO_RAD;
	}

	public static float translateX(float angle, float length) {
		return length * MathUtils.cosDeg(angle);
	}

	public static float translateY(float angle, float length) {
		return length * MathUtils.sinDeg(angle);
	}

	public static RangeI transformIndexToCoordinates(int index, int rows, int cols) {
		return transformIndexToCoordinates(index, rows, cols, true);
	}

	public static RangeI transformIndexToCoordinates(int index, int rows, int cols, boolean leftToRight) {
		if (leftToRight) {
			int row = index / cols;
			int col = index % cols;
			return new RangeI(col, row);
		} else {
			int col = index / rows;
			int row = index % rows;
			return new RangeI(col, row);
		}
	}

	public static int transformCoordinatesToIndex(int row, int col, int rows, int cols) {
		return transformCoordinatesToIndex(row, col, rows, cols, true);
	}

	public static int transformCoordinatesToIndex(int row, int col, int rows, int cols, boolean leftToRight) {
		if (leftToRight) {
			return row * cols + col;
		} else {
			return col * rows + row;
		}
	}

	public static int dip2px(float scale, float dpValue) {
		return (int) (dpValue * scale + 0.5f);
	}

	public static int px2dip(int pixels, float dpValue) {
		return (int) (pixels * LSystem.DEFAULT_DPI / dpValue);
	}

	public static float oscilliate(float x, float min, float max, float period) {
		return max - (sin(x * 2f * PI / period) * ((max - min) / 2f) + ((max - min) / 2f));
	}

	public static float degToRad(float deg) {
		return deg * 360 / TWO_PI;
	}

	public static float safeAdd(float left, float right) {
		if (right > 0 ? left > Long.MAX_VALUE - right : left < Long.MIN_VALUE - right) {
			throw new LSysException("Integer overflow");
		}
		return left + right;
	}

	public static float safeSubtract(float left, float right) {
		if (right > 0 ? left < Long.MIN_VALUE + right : left > Long.MAX_VALUE + right) {
			throw new LSysException("Integer overflow");
		}
		return left - right;
	}

	public static float safeMultiply(float left, float right) {
		if (right > 0 ? left > Long.MAX_VALUE / right || left < Long.MIN_VALUE / right
				: (right < -1 ? left > Long.MIN_VALUE / right || left < Long.MAX_VALUE / right
						: right == -1 && left == Long.MIN_VALUE)) {
			throw new LSysException("Integer overflow");
		}
		return left * right;
	}

	public static float safeDivide(float left, float right) {
		if ((left == Float.MIN_VALUE) && (right == -1)) {
			throw new LSysException("Integer overflow");
		}
		return left / right;
	}

	public static float safeNegate(float a) {
		if (a == Long.MIN_VALUE) {
			throw new LSysException("Integer overflow");
		}
		return -a;
	}

	public static float safeAbs(float a) {
		if (a == Long.MIN_VALUE) {
			throw new LSysException("Integer overflow");
		}
		return abs(a);
	}

	public static int bringToBounds(final int minValue, final int maxValue, final int v) {
		return max(minValue, min(maxValue, v));
	}

	public static float bringToBounds(final float minValue, final float maxValue, final float v) {
		return max(minValue, min(maxValue, v));
	}

	public static float nearest(float x, float a, float b) {
		if (abs(a - x) < abs(b - x)) {
			return a;
		}
		return b;
	}

	public static boolean nextBoolean() {
		return randomBoolean();
	}

	public static int nextInt(int range) {
		return random.nextInt(range);
	}

	public static int nextInt(int start, int end) {
		return random.nextInt(start, end);
	}

	public static float nextFloat(float range) {
		return random.nextFloat(range);
	}

	public static float nextFloat(float start, float end) {
		return random.nextFloat(start, end);
	}

	public static long randomLong(long start, long end) {
		return random.nextLong(start, end);
	}

	public static CharSequence nextChars(CharSequence... chs) {
		return random.nextChars(chs);
	}

	public static int random(int range) {
		return random.nextInt(range);
	}

	public static int random(int start, int end) {
		return random.nextInt(start, end);
	}

	public static boolean randomBoolean() {
		return random.nextBoolean();
	}

	public static float random() {
		return random.nextFloat();
	}

	public static float random(float range) {
		return random.nextFloat(range);
	}

	public static float random(float start, float end) {
		return random.nextFloat(start, end);
	}

	public static float randomFloor(float start, float end) {
		return MathUtils.floor(random(start, end));
	}

	public static int floorMod(int dividend, int modulus) {
		if (modulus == 0) {
			throw new LSysException("/ by zero");
		}
		int quotient = dividend / modulus;
		if ((dividend ^ modulus) < 0 && (dividend % modulus != 0)) {
			quotient = quotient - 1;
		}
		return dividend - (quotient * modulus);
	}

	public static long floorMod(long dividend, long modulus) {
		if (modulus == 0) {
			throw new LSysException("/ by zero");
		}
		long quotient = dividend / modulus;
		if ((dividend ^ modulus) < 0 && (dividend % modulus != 0)) {
			quotient = quotient - 1;
		}
		return dividend - (quotient * modulus);
	}

	public static int ifloor(float v) {
		return (int) (v + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
	}

	public static int floor(float x) {
		return (int) (x + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
	}

	public static int floorInt(long x) {
		return (int) (x + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
	}

	public static int floorInt(double x) {
		return (int) (x + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
	}

	public static long floor(double x) {
		return (long) (x + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
	}

	public static int floorPositive(float x) {
		return (int) x;
	}

	public static Vector2f floorVector(Vector2f v) {
		Vector2f temp = v.cpy();
		temp.x = floor(v.x);
		temp.y = floor(v.y);
		return temp;
	}

	public static Vector3f floorVector(Vector3f v) {
		Vector3f temp = v.cpy();
		temp.x = floor(v.x);
		temp.y = floor(v.y);
		temp.z = floor(v.z);
		return temp;
	}

	public static Vector4f floorVector(Vector4f v) {
		Vector4f temp = v.cpy();
		temp.x = floor(v.x);
		temp.y = floor(v.y);
		temp.z = floor(v.z);
		temp.w = floor(v.w);
		return temp;
	}

	public static int iceil(float v) {
		return (int) (v + BIG_ENOUGH_CEIL) - BIG_ENOUGH_INT;
	}

	public static int ceil(float x) {
		return (int) (x + BIG_ENOUGH_CEIL) - BIG_ENOUGH_INT;
	}

	public static int ceilPositive(float x) {
		return (int) (x + CEIL);
	}

	public static float crt(float v) {
		if (v < 0f) {
			return -((-v) * (1f / 3f));
		} else {
			return v * (1f / 3f);
		}
	}

	public static float calcPpf(float pps, float delta) {
		return calcPpf(pps, LSystem.getFPS(), delta);
	}

	public static float calcPpf(float pps, float fps, float delta) {
		if (fps <= 0f && delta <= 0f) {
			return pps;
		}
		if (fps != 0f && delta <= 0f) {
			return pps / fps;
		}
		if (fps <= 0f && delta != 0f) {
			return pps / delta;
		}
		return pps / fps / delta;
	}

	public static int round(float x) {
		return (int) (x + BIG_ENOUGH_ROUND) - BIG_ENOUGH_INT;
	}

	public static int round(long x) {
		return (int) (x + BIG_ENOUGH_ROUND) - BIG_ENOUGH_INT;
	}

	public static int roundPositive(float x) {
		return (int) (x + 0.5f);
	}

	public static float barycentric(float value1, float value2, float value3, float amount1, float amount2) {
		return value1 + (value2 - value1) * amount1 + (value3 - value1) * amount2;
	}

	public static float quadBezier(float a, float b, float c, float t) {
		float t1 = 1f - t;
		return (t1 * t1) * a + 2f * (t1) * t * b + (t * t) * c;
	}

	public static float cubicBezier(float a, float b, float c, float d, float t) {
		float t1 = 1f - t;
		return ((t1 * t1 * t1) * a + 3f * t * (t1 * t1) * b + 3f * (t * t) * (t1) * c + (t * t * t) * d);
	}

	public static float catmullRom(float value1, float value2, float value3, float value4, float amount) {
		float amountSquared = amount * amount;
		float amountCubed = amountSquared * amount;
		return (0.5f * (2f * value2 + (value3 - value1) * amount
				+ (2f * value1 - 5f * value2 + 4f * value3 - value4) * amountSquared
				+ (3f * value2 - value1 - 3f * value3 + value4) * amountCubed));
	}

	public static Vector2f cardinalSplineAt(Vector2f p0, Vector2f p1, Vector2f p2, Vector2f p3, float tension,
			float t) {
		if (tension < 0f) {
			tension = 0f;
		}
		if (tension > 1f) {
			tension = 1f;
		}
		final float t2 = t * t;
		final float t3 = t2 * t;
		final float s = (1f - tension) / 2;
		final float b1 = s * ((-t3 + (2 * t2)) - t);
		final float b2 = s * (-t3 + t2) + (2 * t3 - 3 * t2 + 1);
		final float b3 = s * (t3 - 2 * t2 + t) + (-2 * t3 + 3 * t2);
		final float b4 = s * (t3 - t2);
		final float x = (p0.x * b1 + p1.x * b2 + p2.x * b3 + p3.x * b4);
		final float y = (p0.y * b1 + p1.y * b2 + p2.y * b3 + p3.y * b4);
		return Vector2f.at(x, y);
	}

	public static int clamp(int value, int min, int max) {
		value = (value > max) ? max : value;
		value = (value < min) ? min : value;
		return value;
	}

	public static float clamp(float value, float min, float max) {
		value = (value > max) ? max : value;
		value = (value < min) ? min : value;
		return value;
	}

	public static double clamp(double value, double min, double max) {
		value = (value > max) ? max : value;
		value = (value < min) ? min : value;
		return value;
	}

	public static long clamp(long value, long min, long max) {
		value = (value > max) ? max : value;
		value = (value < min) ? min : value;
		return value;
	}

	public static float clamp(final float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	public static float clampAngle(final float v) {
		float value = v % PI * 2;
		if (value < 0) {
			value += PI * 2;
		}
		return value;
	}

	public static float clampAngle(float angle, float center, float range) {
		float halfRange = range / 2f;
		angle = (angle - center + MathUtils.PI) % (2 * MathUtils.PI);
		if (angle < 0) {
			angle += 2 * MathUtils.PI;
		}
		angle = angle - MathUtils.PI + center;
		return MathUtils.max(center - halfRange, MathUtils.min(center + halfRange, angle));
	}

	public static Vector2f clampInRect(XY v, float x, float y, float width, float height) {
		return clampInRect(v, x, y, width, height, 0f);
	}

	public static Vector2f clampInRect(XY v, float x, float y, float width, float height, float padding) {
		if (v == null) {
			return Vector2f.ZERO();
		}
		Vector2f obj = new Vector2f();
		obj.x = clamp(v.getX(), x + padding, x + width - padding);
		obj.y = clamp(v.getY(), y + padding, y + height - padding);
		return obj;
	}

	public static Vector2f clampVector(Vector2f v, Vector2f min, Vector2f max) {
		if (Vector2f.greaterThan(min, max)) {
			Vector2f temp = min;
			min = max;
			max = temp;
		}
		return Vector2f.lessThan(v, min) ? min : Vector2f.greaterThan(v, max) ? max : v;
	}

	public static Vector3f clampVector(Vector3f v, Vector3f min, Vector3f max) {
		if (Vector3f.greaterThan(min, max)) {
			Vector3f temp = min;
			min = max;
			max = temp;
		}
		return Vector3f.lessThan(v, min) ? min : Vector3f.greaterThan(v, max) ? max : v;
	}

	public static Vector4f clampVector(Vector4f v, Vector4f min, Vector4f max) {
		if (Vector4f.greaterThan(min, max)) {
			Vector4f temp = min;
			min = max;
			max = temp;
		}
		return Vector4f.lessThan(v, min) ? min : Vector4f.greaterThan(v, max) ? max : v;
	}

	public static float clampTime(float start, float now, float duration) {
		return clampTime(now - start, duration);
	}

	public static float clampTime(float elapsed, float maxTime) {
		return clamp(MathUtils.clamp(elapsed, 0, MathUtils.max(0, maxTime)) / maxTime);
	}

	public static float clampTime(float elapsed, float inDuration, float delayDuration, float outDuration) {
		if (elapsed < 0 || elapsed > inDuration + delayDuration + outDuration) {
			return 0f;
		}
		if (elapsed < inDuration) {
			return clampTime(elapsed, inDuration);
		}
		if (elapsed < inDuration + delayDuration) {
			return 1f;
		}
		return 1f - clampTime(elapsed - inDuration - delayDuration, outDuration);
	}

	public static float cameraLerp(float elapsed, float l) {
		return l * (elapsed / LSystem.DEFAULT_EASE_DELAY);
	}

	public static float coolLerp(float elapsed, float b, float t, float r) {
		return b + cameraLerp(elapsed, r) * (t - b);
	}

	public static Vector2f toBarycoord(Vector2f p, Vector2f a, Vector2f b, Vector2f c, Vector2f barycentricOut) {
		Vector2f tmp = TempVars.get().vec2f6;
		Vector2f v0 = tmp.set(b).sub(a);
		Vector2f v1 = tmp.set(c).sub(a);
		Vector2f v2 = tmp.set(p).sub(a);
		float d00 = v0.dot(v0);
		float d01 = v0.dot(v1);
		float d11 = v1.dot(v1);
		float d20 = v2.dot(v0);
		float d21 = v2.dot(v1);
		float denom = d00 * d11 - d01 * d01;
		barycentricOut.x = (d11 * d20 - d01 * d21) / denom;
		barycentricOut.y = (d00 * d21 - d01 * d20) / denom;
		return barycentricOut;
	}

	public static boolean barycoordInsideTriangle(Vector2f barycentric) {
		return barycentric.x >= 0 && barycentric.y >= 0 && barycentric.x + barycentric.y <= 1;
	}

	public static Vector2f fromBarycoord(Vector2f barycentric, Vector2f a, Vector2f b, Vector2f c,
			Vector2f interpolatedOut) {
		float u = 1 - barycentric.x - barycentric.y;
		interpolatedOut.x = u * a.x + barycentric.x * b.x + barycentric.y * c.x;
		interpolatedOut.y = u * a.y + barycentric.x * b.y + barycentric.y * c.y;
		return interpolatedOut;
	}

	public static float fromBarycoord(Vector2f barycentric, float a, float b, float c) {
		float u = 1 - barycentric.x - barycentric.y;
		return u * a + barycentric.x * b + barycentric.y * c;
	}

	public static float lowestPositiveRoot(float a, float b, float c) {
		if (a == 0) {
			if (b == 0) {
				return Float.NaN;
			}
			float r = -c / b;
			return r > 0 ? r : Float.NaN;
		}
		float det = b * b - 4 * a * c;
		if (det < 0) {
			return Float.NaN;
		}
		float sqrtD = sqrt(det);
		float invA = 1 / (2 * a);
		float r1 = (-b - sqrtD) * invA;
		float r2 = (-b + sqrtD) * invA;
		if (r1 > r2) {
			float tmp = r2;
			r2 = r1;
			r1 = tmp;
		}
		if (r1 > 0) {
			return r1;
		}
		if (r2 > 0) {
			return r2;
		}
		return Float.NaN;
	}

	public static boolean colinear(float x1, float y1, float x2, float y2, float x3, float y3) {
		float dx21 = x2 - x1, dy21 = y2 - y1;
		float dx32 = x3 - x2, dy32 = y3 - y2;
		float det = dx32 * dy21 - dx21 * dy32;
		return Math.abs(det) < MathUtils.FLOAT_ROUNDING_ERROR;
	}

	public static Vector2f triangleCentroid(float x1, float y1, float x2, float y2, float x3, float y3,
			Vector2f centroid) {
		centroid.x = (x1 + x2 + x3) / 3;
		centroid.y = (y1 + y2 + y3) / 3;
		return centroid;
	}

	public static Vector2f triangleCircumcenter(float x1, float y1, float x2, float y2, float x3, float y3,
			Vector2f circumcenter) {
		float dx21 = x2 - x1, dy21 = y2 - y1;
		float dx32 = x3 - x2, dy32 = y3 - y2;
		float dx13 = x1 - x3, dy13 = y1 - y3;
		float det = dx32 * dy21 - dx21 * dy32;
		if (abs(det) < FLOAT_ROUNDING_ERROR) {
			throw new IllegalArgumentException("The triangle points must not be colinear.");
		}
		det *= 2;
		float sqr1 = x1 * x1 + y1 * y1, sqr2 = x2 * x2 + y2 * y2, sqr3 = x3 * x3 + y3 * y3;
		circumcenter.set((sqr1 * dy32 + sqr2 * dy13 + sqr3 * dy21) / det,
				-(sqr1 * dx32 + sqr2 * dx13 + sqr3 * dx21) / det);
		return circumcenter;
	}

	public static float triangleQuality(float x1, float y1, float x2, float y2, float x3, float y3) {
		float dx12 = x1 - x2, dy12 = y1 - y2;
		float dx23 = x2 - x3, dy23 = y2 - y3;
		float dx31 = x3 - x1, dy31 = y3 - y1;
		float sqLength1 = dx12 * dx12 + dy12 * dy12;
		float sqLength2 = dx23 * dx23 + dy23 * dy23;
		float sqLength3 = dx31 * dx31 + dy31 * dy31;
		return sqrt(min(sqLength1, min(sqLength2, sqLength3))) / triangleCircumradius(x1, y1, x2, y2, x3, y3);
	}

	public static float triangleCircumradius(float x1, float y1, float x2, float y2, float x3, float y3) {
		float m1, m2, mx1, mx2, my1, my2, x, y;
		if (abs(y2 - y1) < FLOAT_ROUNDING_ERROR) {
			m2 = -(x3 - x2) / (y3 - y2);
			mx2 = (x2 + x3) / 2;
			my2 = (y2 + y3) / 2;
			x = (x2 + x1) / 2;
			y = m2 * (x - mx2) + my2;
		} else if (abs(y3 - y2) < FLOAT_ROUNDING_ERROR) {
			m1 = -(x2 - x1) / (y2 - y1);
			mx1 = (x1 + x2) / 2;
			my1 = (y1 + y2) / 2;
			x = (x3 + x2) / 2;
			y = m1 * (x - mx1) + my1;
		} else {
			m1 = -(x2 - x1) / (y2 - y1);
			m2 = -(x3 - x2) / (y3 - y2);
			mx1 = (x1 + x2) / 2;
			mx2 = (x2 + x3) / 2;
			my1 = (y1 + y2) / 2;
			my2 = (y2 + y3) / 2;
			x = (m1 * mx1 - m2 * mx2 + my2 - my1) / (m1 - m2);
			y = m1 * (x - mx1) + my1;
		}
		float dx = x1 - x, dy = y1 - y;
		return sqrt(dx * dx + dy * dy);
	}

	public static float triangleArea(float x1, float y1, float x2, float y2, float x3, float y3) {
		return abs((x1 - x3) * (y2 - y1) - (x1 - x2) * (y3 - y1)) * 0.5f;
	}

	public static Vector2f quadrilateralCentroid(float x1, float y1, float x2, float y2, float x3, float y3, float x4,
			float y4, Vector2f centroid) {
		float cx1 = (x1 + x2 + x3) / 3, cy1 = (y1 + y2 + y3) / 3;
		float cx2 = (x1 + x3 + x4) / 3, cy2 = (y1 + y3 + y4) / 3;
		float area1 = abs((x1 - x3) * (y2 - y1) - (x1 - x2) * (y3 - y1)) * 0.5f;
		float area2 = abs((x1 - x3) * (y4 - y1) - (x1 - x4) * (y3 - y1)) * 0.5f;
		float total = area1 + area2;
		if (total == 0) {
			centroid.x = cx1;
			centroid.y = cy1;
		} else {
			centroid.x = (cx1 * area1 + cx2 * area2) / total;
			centroid.y = (cy1 * area1 + cy2 * area2) / total;
		}
		return centroid;
	}

	public static Vector2f polygonCentroid(float[] polygon, int offset, int count, Vector2f centroid) {
		if (count < 6) {
			return new Vector2f();
		}
		float area = 0, x = 0, y = 0;
		int last = offset + count - 2;
		float x1 = polygon[last], y1 = polygon[last + 1];
		for (int i = offset; i <= last; i += 2) {
			float x2 = polygon[i], y2 = polygon[i + 1];
			float a = x1 * y2 - x2 * y1;
			area += a;
			x += (x1 + x2) * a;
			y += (y1 + y2) * a;
			x1 = x2;
			y1 = y2;
		}
		if (area == 0) {
			centroid.x = 0;
			centroid.y = 0;
		} else {
			area *= 0.5f;
			centroid.x = x / (6 * area);
			centroid.y = y / (6 * area);
		}
		return centroid;
	}

	public static float polygonArea(float[] polys, int offset, int count) {
		float area = 0;
		int last = offset + count - 2;
		float x1 = polys[last], y1 = polys[last + 1];
		for (int i = offset; i <= last; i += 2) {
			float x2 = polys[i], y2 = polys[i + 1];
			area += x1 * y2 - x2 * y1;
			x1 = x2;
			y1 = y2;
		}
		return abs(area * 0.5f);
	}

	public static void ensureCCW(float[] polys) {
		ensureCCW(polys, 0, polys.length);
	}

	public static void ensureCCW(float[] polys, int offset, int count) {
		if (isCCW(polys, offset, count)) {
			return;
		}
		reverseVertices(polys, offset, count);
	}

	public static void reverseVertices(float[] polys, int offset, int count) {
		int lastX = offset + count - 2;
		for (int i = offset, n = offset + count / 2; i < n; i += 2) {
			int other = lastX - i;
			float x = polys[i];
			float y = polys[i + 1];
			polys[i] = polys[other];
			polys[i + 1] = polys[other + 1];
			polys[other] = x;
			polys[other + 1] = y;
		}
	}

	public static boolean isCCW(float[] polys, int offset, int count) {
		if (count < 6) {
			return false;
		}
		float area = 0;
		int last = offset + count - 2;
		float x1 = polys[last], y1 = polys[last + 1];
		for (int i = offset; i <= last; i += 2) {
			float x2 = polys[i], y2 = polys[i + 1];
			area += x1 * y2 - x2 * y1;
			x1 = x2;
			y1 = y2;
		}
		return area < 0;
	}

	public static float hermite(float value1, float tangent1, float value2, float tangent2, float amount) {
		float v1 = value1, v2 = value2, t1 = tangent1, t2 = tangent2, s = amount, result;
		float sCubed = s * s * s;
		float sSquared = s * s;

		if (amount == 0f) {
			result = value1;
		} else if (amount == 1f) {
			result = value2;
		} else {
			result = (2 * v1 - 2 * v2 + t2 + t1) * sCubed + (3 * v2 - 3 * v1 - 2 * t1 - t2) * sSquared + t1 * s + v1;
		}
		return (float) result;
	}

	public static float smoothStep(float value1, float value2, float amount) {
		float result = clamp(amount, 0f, 1f);
		result = hermite(value1, 0f, value2, 0f, result);
		return result;
	}

	public static float lerp(float value1, float value2, float amount) {
		return value1 + (value2 - value1) * amount;
	}

	public static float lerpInverse(float value1, float value2, float amount) {
		return (amount - value1) / (value2 - value1);
	}

	public static float lerpAngle(RotationType rotationType, float startAngle, float endAngle, float timer) {
		final boolean pathIsPositive = (startAngle - endAngle + TWO_PI) % TWO_PI >= MathUtils.PI;
		final float dst1 = MathUtils.abs(endAngle - startAngle);
		final float dst2 = TWO_PI - dst1;
		float shortDst = 0;
		float longDst = 0;
		if (dst1 > dst2) {
			shortDst = dst2;
			longDst = dst1;
		} else {
			shortDst = dst1;
			longDst = dst2;
		}
		float distance = 0;
		float dir = 1;
		switch (rotationType) {
		case Shortest:
			distance = shortDst;
			dir = pathIsPositive ? 1 : -1;
			break;
		case Longest:
			distance = longDst;
			dir = pathIsPositive ? -1 : 1;
			break;
		case Clockwise:
			dir = 1;
			distance = pathIsPositive ? shortDst : longDst;
			break;
		case CounterClockwise:
			dir = -1;
			distance = pathIsPositive ? longDst : shortDst;
			break;
		}
		return startAngle + dir * (distance * timer);
	}

	public static float wave(float time) {
		return waveCos(1f, 1f, time);
	}

	public static float wave(float frequency, float amplitude, float time) {
		return waveCos(frequency, amplitude, time);
	}

	public static float waveCos(float frequency, float amplitude, float time) {
		return amplitude / 2f * (1f - MathUtils.cos(time * frequency * MathUtils.TWO_PI));
	}

	public static float waveSin(float frequency, float amplitude, float time) {
		return amplitude / 2f * (1f - MathUtils.sin(time * frequency * MathUtils.TWO_PI));
	}

	public static float wrapAngle(float angle) {
		angle = (float) IEEEremainder((double) angle, 6.2831854820251465d);
		if (angle <= -3.141593f) {
			angle += 6.283185f;
			return angle;
		}
		if (angle > 3.141593f) {
			angle -= 6.283185f;
		}
		return angle;
	}

	public static float warp(float v, float min, float max) {
		return (((v - min) % (max - min) + (max - min)) % (max - min)) + min;
	}

	public static double signum(double d) {
		return d > 0 ? 1 : d < -0 ? -1 : d;
	}

	public static float signum(float d) {
		return d > 0 ? 1 : d < -0 ? -1 : d;
	}

	public static double IEEEremainder(double f1, double f2) {
		double r = abs(f1 % f2);
		if (isNan(r) || r == f2 || r <= abs(f2) / 2.0) {
			return r;
		} else {
			return signum(f1) * (r - f2);
		}
	}

	public static double normalizeLon(double lon) {
		while ((lon < -180d) || (lon > 180d)) {
			lon = IEEEremainder(lon, 360d);
		}
		return lon;
	}

	public static float sortBigFirst(float a, float b) {
		if (a == b) {
			return 0;
		}
		return b > a ? 1 : -1;
	}

	public static float sortSmallFirst(float a, float b) {
		if (a == b) {
			return 0;
		}
		return b > a ? -1 : 1;
	}

	public static int sum(final int[] values) {
		int sum = 0;
		for (int i = values.length - 1; i >= 0; i--) {
			sum += values[i];
		}
		return sum;
	}

	public static float snap(float src, float dst) {
		return round(src / dst) * dst;
	}

	public static Vector2f snap(XY src, float dst) {
		if (src == null) {
			return new Vector2f();
		}
		return new Vector2f(snap(src.getX(), dst), snap(src.getY(), dst));
	}

	public static Vector2f snap(XY src, XY dst) {
		if (src == null || dst == null) {
			return new Vector2f();
		}
		return new Vector2f(snap(src.getX(), dst.getX()), snap(src.getY(), dst.getY()));
	}

	public static float snapFloor(float src, float dst) {
		return floor(src / dst) * dst;
	}

	public static Vector2f snapFloor(XY src, float dst) {
		if (src == null) {
			return new Vector2f();
		}
		return new Vector2f(snapFloor(src.getX(), dst), snapFloor(src.getY(), dst));
	}

	public static Vector2f snapFloor(XY src, XY dst) {
		if (src == null || dst == null) {
			return new Vector2f();
		}
		return new Vector2f(snapFloor(src.getX(), dst.getX()), snapFloor(src.getY(), dst.getY()));
	}

	public static float snapCeil(float src, float dst) {
		return ceil(src / dst) * dst;
	}

	public static Vector2f snapCeil(XY src, float dst) {
		if (src == null) {
			return new Vector2f();
		}
		return new Vector2f(snapCeil(src.getX(), dst), snapCeil(src.getY(), dst));
	}

	public static Vector2f snapCeil(XY src, XY dst) {
		if (src == null || dst == null) {
			return new Vector2f();
		}
		return new Vector2f(snapCeil(src.getX(), dst.getX()), snapCeil(src.getY(), dst.getY()));
	}

	public static void arraySumInternal(final int[] values) {
		final int valueCount = values.length;
		for (int i = 1; i < valueCount; i++) {
			values[i] = values[i - 1] + values[i];
		}
	}

	public static void arraySumInternal(final long[] values) {
		final int valueCount = values.length;
		for (int i = 1; i < valueCount; i++) {
			values[i] = values[i - 1] + values[i];
		}
	}

	public static void arraySumInternal(final long[] values, final long factor) {
		values[0] = values[0] * factor;
		final int valueCount = values.length;
		for (int i = 1; i < valueCount; i++) {
			values[i] = values[i - 1] + values[i] * factor;
		}
	}

	public static void arraySumInto(final long[] values, final long[] targetValues, final long factor) {
		targetValues[0] = values[0] * factor;
		final int valueCount = values.length;
		for (int i = 1; i < valueCount; i++) {
			targetValues[i] = targetValues[i - 1] + values[i] * factor;
		}
	}

	public static float arraySum(final float[] values) {
		float sum = 0;
		final int valueCount = values.length;
		for (int i = 0; i < valueCount; i++) {
			sum += values[i];
		}
		return sum;
	}

	public static float arrayAverage(final float[] values) {
		return MathUtils.arraySum(values) / values.length;
	}

	public static float average(final float a, final float b) {
		return a + (b - a) * 0.5f;
	}

	public static float approach(float src, float dst, float amount) {
		if (src > dst) {
			return max(src - amount, dst);
		} else {
			return min(src + amount, dst);
		}
	}

	public static float approachIfLower(float src, float dst, float amount) {
		if (sign(src) != sign(dst) || abs(src) < abs(dst)) {
			return approach(src, dst, amount);
		} else {
			return src;
		}
	}

	public static Vector2f approach(Vector2f src, Vector2f dst, float amount) {
		if (src == null || dst == null) {
			return new Vector2f();
		}
		if (src == dst) {
			return dst;
		} else {
			Vector2f diff = dst.sub(src);
			if (diff.lengthSquared() <= amount * amount) {
				return dst;
			} else {
				return diff.nor().mulSelf(amount).addSelf(src);
			}
		}
	}

	public static float angle(XY vec) {
		if (vec == null) {
			return 0f;
		}
		return atan2(vec.getY(), vec.getX());
	}

	public static float angle(XY src, XY dst) {
		if (src == null || dst == null) {
			return 0f;
		}
		return atan2(dst.getY() - src.getY(), dst.getX() - src.getX());
	}

	public static float angle(float start, float end, float max) {
		start = start % TWO_PI;
		end = end % TWO_PI;
		float diff = end - start;
		float s = sign(diff);
		if (MathUtils.abs(diff) > MathUtils.PI) {
			diff = start - end;
			s = -s;
		}
		return start + MathUtils.min(max, MathUtils.abs(diff)) * s;
	}

	public static Vector2f angleToVector(float angle) {
		return angleToVector(angle, 1f);
	}

	public static Vector2f angleToVector(float angle, float length) {
		return angleToVector(angle, length, null);
	}

	public static Vector2f angleToVector(float angle, float length, Vector2f result) {
		float newX = cos(angle) * length;
		float newY = sin(angle) * length;
		if (result == null) {
			return new Vector2f(newX, newY);
		}
		return result.set(newX, newY);
	}

	public static float angleLerp(float startAngle, float endAngle, float percent) {
		return startAngle + angleDiff(startAngle, endAngle) * percent;
	}

	public static float angleDiff(float radiansA, float radiansB) {
		return ((radiansB - radiansA - PI) % TAU + TAU) % TAU - PI;
	}

	public static float angleApproach(float val, float dst, float maxMove) {
		float diff = angleDiff(val, dst);
		if (abs(diff) < maxMove) {
			return dst;
		}
		return val + clamp(diff, -maxMove, maxMove);
	}

	public static float absAngleDiff(float radiansA, float radiansB) {
		return abs(angleDiff(radiansA, radiansB));
	}

	public static Vector2f rotateToward(Vector2f dir, Vector2f dst, float maxAngleDelta, float maxMagnitudeDelta) {
		float angle = dir.angle();
		float len = dir.length();
		if (maxAngleDelta > 0f) {
			angle = angleApproach(angle, dst.angle(), maxAngleDelta);
		}
		if (maxMagnitudeDelta > 0f) {
			len = approach(len, dst.length(), maxMagnitudeDelta);
		}
		return angleToVector(angle, len);
	}

	public static float[] scaleAroundCenter(final float[] vertices, final float scaleX, final float scaleY,
			final float scaleCenterX, final float scaleCenterY) {
		if (scaleX != 1 || scaleY != 1) {
			for (int i = vertices.length - 2; i >= 0; i -= 2) {
				vertices[i] = scaleCenterX + (vertices[i] - scaleCenterX) * scaleX;
				vertices[i + 1] = scaleCenterY + (vertices[i + 1] - scaleCenterY) * scaleY;
			}
		}
		return vertices;
	}

	public static boolean isInBounds(final int minValue, final int maxValue, final int val) {
		return val >= minValue && val <= maxValue;
	}

	public static boolean isInBounds(final float minValue, final float maxValue, final float val) {
		return val >= minValue && val <= maxValue;
	}

	public static Vector2f randomXY(Vector2f v, float scale) {
		float r = MathUtils.random() * MathUtils.TWO_PI;
		v.x = MathUtils.cos(r) * scale;
		v.y = MathUtils.sin(r) * scale;
		return v;
	}

	public static Vector3f randomXYZ(Vector3f v, float radius) {
		float r = MathUtils.random() * MathUtils.TWO_PI;
		float z = (MathUtils.random() * 2) - 1;
		float zScale = MathUtils.sqrt(1 - z * z) * radius;
		v.x = MathUtils.cos(r) * zScale;
		v.y = MathUtils.sin(r) * zScale;
		v.z = z * radius;
		return v;
	}

	public static SetXY rotate(XY src, SetXY dst, float angle) {
		final float x = src.getX();
		final float y = src.getY();

		dst.setX((x * MathUtils.cos(angle)) - (y * MathUtils.sin(angle)));
		dst.setY((x * MathUtils.sin(angle)) + (y * MathUtils.cos(angle)));

		return dst;
	}

	public static SetXY rotateTo(SetXY dst, float x, float y, float angle, float distance) {
		dst.setX(x + (distance * MathUtils.cos(angle)));
		dst.setY(y + (distance * MathUtils.sin(angle)));
		return dst;
	}

	public static SetXY rotateAround(XY src, SetXY dst, float x, float y, float angle) {
		final float cos = MathUtils.cos(angle);
		final float sin = MathUtils.sin(angle);

		final float tx = src.getX() - x;
		final float ty = src.getY() - y;

		dst.setX(tx * cos - ty * sin + x);
		dst.setY(tx * sin + ty * cos + y);

		return dst;
	}

	public static SetXY rotateAroundDistance(XY src, SetXY dst, float x, float y, float angle, float distance) {
		final float t = angle + MathUtils.atan2(src.getY() - y, src.getX() - x);

		dst.setX(x + (distance * MathUtils.cos(t)));
		dst.setY(y + (distance * MathUtils.sin(t)));

		return dst;
	}

	public static String toString(float value) {
		return toString(value, TO_STRING_DECIMAL_PLACES);
	}

	public static String toString(float value, boolean showTag) {
		return toString(value, TO_STRING_DECIMAL_PLACES, showTag);
	}

	public static String toString(float value, int decimalPlaces) {
		return toString(value, decimalPlaces, false);
	}

	public static String toString(float value, int decimalPlaces, boolean showTag) {
		if (isNan(value)) {
			return "NaN";
		}
		StrBuilder buf = new StrBuilder();
		if (value >= 0) {
			if (showTag) {
				buf.append("+");
			}
		} else {
			if (showTag) {
				buf.append("-");
			}
			value = -value;
		}
		int ivalue = (int) value;
		buf.append(ivalue);
		if (decimalPlaces > 0) {
			buf.append(".");
			for (int ii = 0; ii < decimalPlaces; ii++) {
				value = (value - ivalue) * 10;
				ivalue = (int) value;
				buf.append(ivalue);
			}
			// trim trailing zeros
			for (int ii = 0; ii < decimalPlaces - 1; ii++) {
				if (buf.charAt(buf.length() - 1) == '0') {
					buf.setLength(buf.length() - 1);
				}
			}
		}
		return buf.toString();
	}

	public static int round(int div1, int div2) {
		final int remainder = div1 % div2;
		if (MathUtils.abs(remainder) * 2 <= MathUtils.abs(div2)) {
			return div1 / div2;
		} else if (div1 * div2 < 0) {
			return div1 / div2 - 1;
		} else {
			return div1 / div2 + 1;
		}
	}

	public static float round(float div1, float div2) {
		final float remainder = div1 % div2;
		if (MathUtils.abs(remainder) * 2 <= MathUtils.abs(div2)) {
			return div1 / div2;
		} else if (div1 * div2 < 0) {
			return div1 / div2 - 1;
		} else {
			return div1 / div2 + 1;
		}
	}

	public static long round(long div1, long div2) {
		final long remainder = div1 % div2;
		if (MathUtils.abs(remainder) * 2 <= MathUtils.abs(div2)) {
			return div1 / div2;
		} else if (div1 * div2 < 0) {
			return div1 / div2 - 1;
		} else {
			return div1 / div2 + 1;
		}
	}

	public static float roundToNearest(float v, float n) {
		int p = max(precision(v), precision(n));
		float inv = 1f / n;
		return round(round(v, inv) / inv, p);
	}

	public static int toShift(int angle) {
		if (angle <= 45) {
			return SHIFT[angle];
		} else if (angle >= 315) {
			return -SHIFT[360 - angle];
		} else if (angle >= 135 && angle <= 180) {
			return -SHIFT[180 - angle];
		} else if (angle >= 180 && angle <= 225) {
			return SHIFT[angle - 180];
		} else if (angle >= 45 && angle <= 90) {
			return SHIFT[90 - angle];
		} else if (angle >= 90 && angle <= 135) {
			return -SHIFT[angle - 90];
		} else if (angle >= 225 && angle <= 270) {
			return SHIFT[270 - angle];
		} else {
			return -SHIFT[angle - 270];
		}
	}

	public static float bezierAt(float a, float b, float c, float d, float t) {
		return (MathUtils.pow(1 - t, 3) * a + 3 * t * (MathUtils.pow(1 - t, 2)) * b
				+ 3 * MathUtils.pow(t, 2) * (1 - t) * c + MathUtils.pow(t, 3) * d);
	}

	public static int parseUnsignedInt(String s) {
		return parseUnsignedInt(s, 10);
	}

	public static int parseUnsignedInt(String s, int radix) {
		if (s == null) {
			throw new LSysException("null");
		}
		int len = s.length();
		if (len > 0) {
			char firstChar = s.charAt(0);
			if (firstChar == '-') {
				throw new LSysException("on unsigned string %s.");
			} else {
				if (len <= 5 || (radix == 10 && len <= 9)) {
					return Integer.parseInt(s, radix);
				} else {
					long ell = Long.parseLong(s, radix);
					if ((ell & 0xffff_ffff_0000_0000L) == 0) {
						return (int) ell;
					} else {
						throw new LSysException("range of unsigned int.");
					}
				}
			}
		} else {
			throw new LSysException(s);
		}
	}

	public static int numberOfTrailingZeros(long i) {
		int x, y;
		if (i == 0) {
			return 64;
		}
		int n = 63;
		y = (int) i;
		if (y != 0) {
			n = n - 32;
			x = y;
		} else
			x = (int) (i >>> 32);
		y = x << 16;
		if (y != 0) {
			n = n - 16;
			x = y;
		}
		y = x << 8;
		if (y != 0) {
			n = n - 8;
			x = y;
		}
		y = x << 4;
		if (y != 0) {
			n = n - 4;
			x = y;
		}
		y = x << 2;
		if (y != 0) {
			n = n - 2;
			x = y;
		}
		return n - ((x << 1) >>> 31);

	}

	public static float maxAbs(float x, float y) {
		return MathUtils.abs(x) >= MathUtils.abs(y) ? x : y;
	}

	public static float minAbs(float x, float y) {
		return MathUtils.abs(x) <= MathUtils.abs(y) ? x : y;
	}

	public static float lerpCut(float progress, float progressLowCut, float progressHighCut, float fromValue,
			float toValue) {
		progress = MathUtils.clamp(progress, progressLowCut, progressHighCut);
		float a = (progress - progressLowCut) / (progressHighCut - progressLowCut);
		return MathUtils.lerp(fromValue, toValue, a);
	}

	public static float scale(float value, float maxValue, float maxScale) {
		return (maxScale / maxValue) * value;
	}

	public static float scale(float value, float minValue, float maxValue, float min2, float max2) {
		return min2 + ((value - minValue) / (maxValue - minValue)) * (max2 - min2);
	}

	public static float scaleClamp(float value, float minValue, float maxValue, float min2, float max2) {
		value = min2 + ((value - minValue) / (maxValue - minValue)) * (max2 - min2);
		if (max2 > min2) {
			value = value < max2 ? value : max2;
			return value > min2 ? value : min2;
		}
		value = value < min2 ? value : min2;
		return value > max2 ? value : max2;
	}

	public static float percent(float value, float min, float max) {
		return percent(value, min, max, 1f);
	}

	public static float percent(float value, float min, float max, float upperMax) {
		if (max <= -1f) {
			max = min + 1f;
		}
		float percentage = (value - min) / (max - min);
		if (percentage > 1f) {
			if (upperMax != -1f) {
				percentage = ((upperMax - value)) / (upperMax - max);
				if (percentage < 0f) {
					percentage = 0f;
				}
			} else {
				percentage = 1f;
			}
		} else if (percentage < 0f) {
			percentage = 0f;
		}
		return percentage;
	}

	public static float percent(float value, float percent) {
		return value * (percent * 0.01f);
	}

	public static int percent(int value, int percent) {
		return (int) (value * (percent * 0.01f));
	}

	public static int compare(int x, int y) {
		return (x < y) ? -1 : ((x == y) ? 0 : 1);
	}

	public static int compare(float x, float y) {
		if (x < y) {
			return -1;
		}
		if (x > y) {
			return 1;
		}
		int thisBits = NumberUtils.floatToIntBits(x);
		int anotherBits = NumberUtils.floatToIntBits(y);
		return (thisBits == anotherBits ? 0 : (thisBits < anotherBits ? -1 : 1));
	}

	public static boolean isCompare(float x, float y) {
		return isCompare(x, y, EPSILON);
	}

	public static boolean isCompare(XY p1, XY p2) {
		return isCompare(p1, p2, EPSILON);
	}

	public static boolean isCompare(float x, float y, float epsilon) {
		return MathUtils.abs(x - y) <= epsilon * MathUtils.max(1.0f, MathUtils.max(Math.abs(x), MathUtils.abs(y)));
	}

	public static boolean isCompare(XY p1, XY p2, float epsilon) {
		return isCompare(p1.getX(), p2.getX(), epsilon) && isCompare(p1.getY(), p2.getY(), epsilon);
	}

	public static int longOfZeros(long i) {
		if (i == 0) {
			return 64;
		}
		int n = 1;
		int x = (int) (i >>> 32);
		if (x == 0) {
			n += 32;
			x = (int) i;
		}
		if (x >>> 16 == 0) {
			n += 16;
			x <<= 16;
		}
		if (x >>> 24 == 0) {
			n += 8;
			x <<= 8;
		}
		if (x >>> 28 == 0) {
			n += 4;
			x <<= 4;
		}
		if (x >>> 30 == 0) {
			n += 2;
			x <<= 2;
		}
		n -= x >>> 31;
		return n;
	}

	public static float logBase(float b, float v) {
		return log(v) / log(b);
	}

	public static int limit(int i, int min, int max) {
		if (i < min) {
			return min;
		} else if (i > max) {
			return max;
		} else {
			return i;
		}
	}

	public static float limit(float i, float min, float max) {
		if (i < min) {
			return min;
		} else if (i > max) {
			return max;
		} else {
			return i;
		}
	}

	public static float parseAngle(String angle, float value) {
		if (StringUtils.isEmpty(angle)) {
			return 0f;
		}
		angle = angle.toLowerCase();
		if ("deg".equals(angle)) {
			return MathUtils.DEG_TO_RAD * value;
		} else if ("grad".equals(angle)) {
			return MathUtils.PI / 200 * value;
		} else if ("rad".equals(angle)) {
			return value;
		} else if ("turn".equals(angle)) {
			return MathUtils.TWO_PI * value;
		}
		return value;
	}

	public static boolean isLimit(int value, int minX, int maxX) {
		return value >= minX && value <= maxX;
	}

	public static float fixRotation(final float rotation) {
		float newAngle = 0f;
		if (rotation == -360f || rotation == 360f) {
			return newAngle;
		}
		newAngle = rotation;
		if (newAngle < 0f) {
			while (newAngle < -360f) {
				newAngle += 360f;
			}
		}
		if (newAngle > 0f) {
			while (newAngle > 360f) {
				newAngle -= 360f;
			}
		}
		return newAngle;
	}

	public static float fixRotationLimit(final float rotation, final float min, final float max) {
		float result = rotation;
		if (rotation > max) {
			result = max;
		} else if (rotation < min) {
			result = min;
		}
		return fixRotation(result);
	}

	public static float fixAngle(final float angle) {
		float newAngle = 0f;
		if (angle == -TWO_PI || angle == TWO_PI) {
			return newAngle;
		}
		newAngle = angle;
		if (newAngle < 0) {
			while (newAngle < 0) {
				newAngle += TWO_PI;
			}
		}
		if (newAngle > TWO_PI) {
			while (newAngle > TWO_PI) {
				newAngle -= TWO_PI;
			}
		}
		return newAngle;
	}

	public static float fixAngleLimit(final float angle, final float min, final float max) {
		float result = angle;
		if (angle > max) {
			result = max;
		} else if (angle < min) {
			result = min;
		}
		return fixAngle(result);
	}

	public static float adjust(final float angle) {
		float newAngle = angle;
		while (newAngle < 0) {
			newAngle += RAD_FULL;
		}
		while (newAngle > RAD_FULL) {
			newAngle -= RAD_FULL;
		}
		return newAngle;
	}

	public static float getNormalizedAngle(float angle) {
		while (angle < 0) {
			angle += MathUtils.RAD_FULL;
		}
		return angle % MathUtils.RAD_FULL;
	}

	public static int getGreatestCommonDivisor(int a, int b) {
		if (a < b) {
			int t = a;
			a = b;
			b = t;
		}
		for (; b > 0;) {
			int t = a % b;
			a = b;
			b = t;
		}
		return a;
	}

	public static boolean inAngleRange(final float angle, final float startAngle, final float endAngle) {
		float newAngle = adjust(angle);
		float newStartAngle = adjust(startAngle);
		float newEndAngle = adjust(endAngle);
		if (newStartAngle > newEndAngle) {
			newEndAngle += RAD_FULL;
			if (newAngle < newStartAngle) {
				newAngle += RAD_FULL;
			}
		}
		return newAngle >= newStartAngle && newAngle <= newEndAngle;
	}

	/**
	 * 转换坐标为angle
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 * @return
	 */
	public static float angleFrom(float x1, float y1, float x2, float y2) {
		float diffX = x2 - x1;
		float diffY = y2 - y1;
		return atan2(diffY, diffX);
	}

	/**
	 * 转换坐标为angle
	 * 
	 * @param x
	 * @param y
	 * @param d
	 * @return
	 */
	public static float angleFrom(float x, float y, boolean d) {
		if (d) {
			return atan2(y, x) * RAD_TO_DEG;
		}
		return atan2(y, x);
	}

	public static float radiansFrom(float x, float y) {
		return angleFrom(x, y, false);
	}

	public static float degreesFrom(float x, float y) {
		return angleFrom(x, y, true);
	}

	/**
	 * 滚动指定参数值
	 * 
	 * @param scroll
	 * @param side
	 * @return
	 */
	public static float scroll(float scroll, float side) {
		float start = 0;
		final float v = MathUtils.abs(scroll) % side;
		if (v < 0) {
			start = -(side - v);
		} else if (v > 0) {
			start = -v;
		}
		return start;
	}

	/**
	 * 迭代下降指定数值
	 * 
	 * @param total
	 * @param start
	 * @param side
	 * @return
	 */
	public static float inerations(float start, float side) {
		final float diff = start;
		final float v = diff / side;
		return v + (diff % side > 0 ? 1f : 0f);
	}

	/**
	 * 计算指定数值的阶乘
	 * 
	 * @param v
	 * @return
	 */
	public static float factorial(float v) {
		if (v == 0f) {
			return 1f;
		}
		float result = v;
		while (--v > 0) {
			result *= v;
		}
		return result;
	}

	public static float fmodulo(float v1, float v2) {
		int p = max(precision(v1), precision(v2));
		int e = 1;
		for (int i = 0; i < p; i++) {
			e *= 10;
		}
		int i1 = round(v1 * e);
		int i2 = round(v2 * e);
		return round(i1 % i2 / e, p);
	}

	public static float forcePositive(float v) {
		return v < 0 ? v * -1 : v;
	}

	public static float forceNegative(float v) {
		return v > 0 ? v * -1 : v;
	}

	public static float hypot(float x, float y) {
		x = MathUtils.abs(x);
		y = MathUtils.abs(y);
		if (x < y) {
			float temp = x;
			x = y;
			y = temp;
		}
		if (y == 0.0f) {
			return x;
		}
		float r = y / x;
		return x * MathUtils.sqrt(1.0f + r * r);
	}

	/**
	 * 让两值做加法,若大于第三值则返回第三值
	 * 
	 * @param v
	 * @param amount
	 * @param max
	 * @return
	 */
	public static float maxAdd(float v, float amount, float max) {
		v += amount;
		if (v > max) {
			v = max;
		}
		return v;
	}

	/**
	 * 让两值做减法,若小于第三值则返回第三值
	 * 
	 * @param v
	 * @param amount
	 * @param min
	 * @return
	 */
	public static float minSub(float v, float amount, float min) {
		v -= amount;
		if (v < min) {
			v = min;
		}
		return v;
	}

	/**
	 * 返回一个数值增加指定变量后与指定值比较的余数
	 * 
	 * @param v
	 * @param amount
	 * @param max
	 * @return
	 */
	public static float wrapValue(float v, float amount, float max) {
		float diff = 0f;
		v = MathUtils.abs(v);
		amount = MathUtils.abs(amount);
		max = MathUtils.abs(max);
		diff = (v + amount) % max;
		return diff;
	}

	/**
	 * 判定数值是否在指定模糊查询值区间内
	 * 
	 * @param src
	 * @param dst
	 * @param vague
	 * @param found
	 * @param invalid
	 * @return
	 */
	public static float interval(final float src, final float dst, final float vague, final float found,
			final float invalid) {
		if ((src + vague == dst) || (src - vague) == dst) {
			return found;
		}
		final float result = (dst - src);
		if (result > 0) {
			return result <= vague ? found : invalid;
		} else if (result < 0) {
			return (-result) <= vague ? found : invalid;
		} else {
			return found;
		}
	}

	/**
	 * 判定数值是否在指定模糊查询值区间内
	 * 
	 * @param src
	 * @param dst
	 * @param vague
	 * @param found
	 * @param invalid
	 * @return
	 */
	public static int intervalInt(final int src, final int dst, final int vague, final int found, final int invalid) {
		if ((src + vague == dst) || (src - vague) == dst) {
			return found;
		}
		final int result = (dst - src);
		if (result > 0) {
			return result <= vague ? found : invalid;
		} else if (result < 0) {
			return (-result) <= vague ? found : invalid;
		} else {
			return found;
		}
	}

	/**
	 * 返回一个概率事件在100%范围是否被触发的布尔值
	 * 
	 * @param chance>0 && <100
	 * @return
	 */
	public static boolean chanceRoll(final float chance) {
		return chanceRoll(chance, 100f);
	}

	/**
	 * 返回一个概率事件是否被触发的布尔值
	 * 
	 * @param chance
	 * @param max
	 * @return
	 */
	public static boolean chanceRoll(final float chance, final float max) {
		if (chance <= 0f) {
			return false;
		} else if (chance >= max) {
			return true;
		} else if (MathUtils.random(max) >= chance) {
			return false;
		} else {
			return true;
		}
	}

	/**
	 * 返回一个对象集合中的随机概率对象
	 * 
	 * @param <T>
	 * @param values
	 * @return
	 */
	public static <T> T chanceRollValues(final T[] values) {
		final int maxIndex = max(0, values.length - 1);
		final int rolledIndex = round(random() * maxIndex);
		return values[rolledIndex];
	}

	/**
	 * 返回一个值在指定概率范围内是否可能被触发
	 * 
	 * @param k
	 * @param p
	 * @return
	 */
	public static boolean isSuccess(final float k, final float p) {
		return chanceRoll(k, p);
	}

	/**
	 * 返回一个值在100%概率范围内是否可能被触发
	 * 
	 * @param p
	 * @return
	 */
	public static boolean isSuccess(final float p) {
		return chanceRoll(p);
	}

}
