/**
 * 
 * Copyright 2014
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
 * @version 0.4.1
 */
package loon.canvas;

import loon.LTexture;
import loon.utils.MathUtils;

/**
 * 一个图片阴影效果实现
 */
public final class LShadow {

	public enum ShadowType {
		SOFT_BOX, GAUSSIAN_SEPARABLE, DIRECTIONAL_DROP, INNER_SHADOW, OUTLINE_GLOW, LONG_SHADOW, RADIAL_SOFT, PIXELATED
	}

	public static final class ShadowParams {
		public ShadowType type;
		public int shadowSize;
		public float alpha;
		public LColor color;
		public int offsetX;
		public int offsetY;
		public boolean fastMode;
		public int pixelateSize;

		public ShadowParams() {
			this.type = ShadowType.SOFT_BOX;
			this.shadowSize = 5;
			this.alpha = 0.5f;
			this.color = LColor.black;
			this.offsetX = 0;
			this.offsetY = 0;
			this.fastMode = true;
			this.pixelateSize = 4;
		}
	}

	private int _shadowSize;
	private float _shadowAlpha;
	private LColor _shadowColor;
	private LTexture _texture;
	private ShadowType _type;

	private int _pixelateSize;

	private int[] tmpBuffer = null;

	public LShadow(String file, LColor c) {
		this(Image.createImage(file), 5, 0.5f, c);
	}

	public LShadow(String file, int shadowSize, float a, LColor c) {
		this(Image.createImage(file), shadowSize, a, c);
	}

	public LShadow(String file) {
		this(Image.createImage(file), 5, 0.5f, LColor.black);
	}

	public LShadow(LTexture tex) {
		this(tex.getImage(), 5, 0.5f, LColor.black);
	}

	public LShadow(Image image) {
		this(image, 5, 0.5f, LColor.black);
	}

	public LShadow(Image image, ShadowParams params) {
		if (params == null) {
			params = new ShadowParams();
		}
		this._shadowSize = MathUtils.max(1, params.shadowSize);
		this._shadowAlpha = MathUtils.max(0f, MathUtils.min(1f, params.alpha));
		this._shadowColor = params.color;
		this._type = params.type;
		this._pixelateSize = MathUtils.max(1, params.pixelateSize);

		Image tmp = makeShadow(image, params);
		this._texture = tmp.texture();
		if (tmp != null) {
			tmp.close();
			tmp = null;
		}
	}

	/**
	 * 引入指定图像，并以此生成阴影.
	 * 
	 * @param image      图像
	 * @param shadowSize 模糊程度(越高则图像越模糊)
	 * @param a          透明度
	 * @param c          希望阴影化区域显示的颜色
	 */
	public LShadow(Image image, int shadowSize, float a, LColor c) {
		ShadowParams p = new ShadowParams();
		p.type = ShadowType.SOFT_BOX;
		p.shadowSize = shadowSize;
		p.alpha = a;
		p.color = c;
		this._shadowSize = MathUtils.max(1, shadowSize);
		this._shadowAlpha = MathUtils.max(0f, MathUtils.min(1f, a));
		this._shadowColor = c;
		this._type = p.type;
		this._pixelateSize = 4;

		Image tmp = makeShadow(image, p);
		this._texture = tmp.texture();
		if (tmp != null) {
			tmp.close();
			tmp = null;
		}
	}

	public int getSize() {
		return _shadowSize;
	}

	public float getAlpha() {
		return _shadowAlpha;
	}

	public LColor getColor() {
		return _shadowColor;
	}

	public LTexture getTexture() {
		return _texture;
	}

	private Image makeShadow(final Image image, ShadowParams params) {
		final int w = image.getWidth();
		final int h = image.getHeight();
		final int[] srcPixels = image.getPixels();
		if (tmpBuffer == null || tmpBuffer.length < w * h) {
			tmpBuffer = new int[w * h];
		}
		int[] dst = tmpBuffer;

		final int shadowRgb = (params.color == null ? LColor.black.getRGB() : params.color.getRGB()) & 0x00FFFFFF;
		for (int i = 0; i < w * h; i++) {
			int a = (srcPixels[i] >>> 24) & 0xFF;
			dst[i] = (a << 24) | shadowRgb;
		}
		if (params.type == ShadowType.SOFT_BOX) {
			boxBlur(dst, w, h, params.shadowSize, params.fastMode);
		} else if (params.type == ShadowType.GAUSSIAN_SEPARABLE) {
			gaussianSeparable(dst, w, h, params.shadowSize, params.fastMode);
		} else if (params.type == ShadowType.DIRECTIONAL_DROP) {
			int[] shifted = shiftAlpha(dst, w, h, params.offsetX, params.offsetY, shadowRgb);
			boxBlur(shifted, w, h, params.shadowSize, params.fastMode);
			dst = shifted;
		} else if (params.type == ShadowType.INNER_SHADOW) {
			int[] inner = createInnerMask(srcPixels, w, h, shadowRgb);
			gaussianSeparable(inner, w, h, params.shadowSize, params.fastMode);
			dst = inner;
		} else if (params.type == ShadowType.OUTLINE_GLOW) {
			int[] blur = new int[w * h];
			System.arraycopy(dst, 0, blur, 0, w * h);
			gaussianSeparable(blur, w, h, params.shadowSize, params.fastMode);
			for (int i = 0; i < w * h; i++) {
				int a1 = (dst[i] >>> 24) & 0xFF;
				int a2 = (blur[i] >>> 24) & 0xFF;
				int a = MathUtils.max(a1, a2);
				dst[i] = (a << 24) | shadowRgb;
			}
		} else if (params.type == ShadowType.LONG_SHADOW) {
			int[] accum = new int[w * h];
			for (int i = 0; i < w * h; i++)
				accum[i] = 0;
			int steps = MathUtils.max(1, params.shadowSize);
			for (int step = 1; step <= steps; step++) {
				int dx = params.offsetX * step / steps;
				int dy = params.offsetY * step / steps;
				int[] s = shiftAlpha(dst, w, h, dx, dy, shadowRgb);
				for (int i = 0; i < w * h; i++) {
					int aOld = (accum[i] >>> 24) & 0xFF;
					int aNew = (s[i] >>> 24) & 0xFF;
					int a = aOld + aNew;
					if (a > 255)
						a = 255;
					accum[i] = (a << 24) | shadowRgb;
				}
			}
			gaussianSeparable(accum, w, h, MathUtils.max(1, params.shadowSize / 4), params.fastMode);
			dst = accum;
		} else if (params.type == ShadowType.RADIAL_SOFT) {
			int cx = w / 2;
			int cy = h / 2;
			for (int y = 0; y < h; y++) {
				int row = y * w;
				for (int x = 0; x < w; x++) {
					int idx = row + x;
					int a = (dst[idx] >>> 24) & 0xFF;
					float dist = MathUtils.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
					float fall = 1f - MathUtils.min(1f, dist / (MathUtils.max(w, h) * 0.5f));
					int na = (int) (a * fall);
					dst[idx] = (na << 24) | shadowRgb;
				}
			}
			gaussianSeparable(dst, w, h, params.shadowSize, params.fastMode);
		} else if (params.type == ShadowType.PIXELATED) {
			int block = MathUtils.max(1, params.pixelateSize);
			int sw = MathUtils.max(1, w / block);
			int sh = MathUtils.max(1, h / block);
			int[] small = new int[sw * sh];
			for (int by = 0; by < sh; by++) {
				for (int bx = 0; bx < sw; bx++) {
					int sum = 0, cnt = 0;
					for (int yy = by * block; yy < MathUtils.min(h, (by + 1) * block); yy++) {
						for (int xx = bx * block; xx < MathUtils.min(w, (bx + 1) * block); xx++) {
							sum += (dst[yy * w + xx] >>> 24) & 0xFF;
							cnt++;
						}
					}
					int avg = (cnt == 0) ? 0 : (sum / cnt);
					small[by * sw + bx] = (avg << 24) | shadowRgb;
				}
			}
			int[] pix = new int[w * h];
			for (int by = 0; by < sh; by++) {
				for (int bx = 0; bx < sw; bx++) {
					int val = small[by * sw + bx];
					for (int yy = by * block; yy < MathUtils.min(h, (by + 1) * block); yy++) {
						for (int xx = bx * block; xx < MathUtils.min(w, (bx + 1) * block); xx++) {
							pix[yy * w + xx] = val;
						}
					}
				}
			}
			dst = pix;
		}
		int[] finalPixels = new int[w * h];
		int aMul = MathUtils.max(0, MathUtils.min(255, (int) (params.alpha * 255f)));
		int colorRgb = (params.color == null ? LColor.black.getRGB() : params.color.getRGB()) & 0x00FFFFFF;
		for (int i = 0; i < w * h; i++) {
			int a = (dst[i] >>> 24) & 0xFF;
			int na = (a * aMul) / 255;
			finalPixels[i] = (na << 24) | colorRgb;
		}

		Image out = Image.createImage(w, h);
		out.setPixels(finalPixels, w, h);
		return out;
	}

	private void boxBlur(int[] pixels, int w, int h, int radius, boolean fastMode) {
		if (radius <= 0)
			return;
		if (fastMode && radius >= 8) {
			int factor = (radius >= 16) ? 3 : 2;
			int sw = MathUtils.max(1, w / factor);
			int sh = MathUtils.max(1, h / factor);
			int[] small = new int[sw * sh];
			for (int y = 0; y < sh; y++) {
				for (int x = 0; x < sw; x++) {
					int sum = 0, cnt = 0;
					for (int yy = y * factor; yy < MathUtils.min(h, (y + 1) * factor); yy++) {
						for (int xx = x * factor; xx < MathUtils.min(w, (x + 1) * factor); xx++) {
							sum += (pixels[yy * w + xx] >>> 24) & 0xFF;
							cnt++;
						}
					}
					int avg = (cnt == 0) ? 0 : (sum / cnt);
					small[y * sw + x] = (avg << 24) | (pixels[0] & 0x00FFFFFF);
				}
			}
			boxBlurSeparable(small, sw, sh, MathUtils.max(1, radius / factor));
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					int sx = MathUtils.min(sw - 1, x / factor);
					int sy = MathUtils.min(sh - 1, y / factor);
					pixels[y * w + x] = small[sy * sw + sx];
				}
			}
		} else {
			boxBlurSeparable(pixels, w, h, radius);
		}
	}

	private void boxBlurSeparable(int[] pixels, int w, int h, int radius) {
		int[] tmp = new int[w * h];
		int window = radius * 2 + 1;
		for (int y = 0; y < h; y++) {
			int sum = 0;
			int row = y * w;
			for (int x = 0; x < window && x < w; x++) {
				sum += ((pixels[row + x] >>> 24) & 0xFF);
			}
			for (int x = 0; x < w; x++) {
				int addIdx = x + radius;
				if (addIdx >= w)
					addIdx = w - 1;
				int subIdx = x - radius - 1;
				if (subIdx < 0)
					subIdx = -1;
				if (subIdx >= 0) {
					sum -= ((pixels[row + subIdx] >>> 24) & 0xFF);
				}
				if (addIdx >= 0) {
					sum += ((pixels[row + addIdx] >>> 24) & 0xFF);
				}
				int avg = sum / window;
				tmp[row + x] = (avg << 24) | (pixels[row + x] & 0x00FFFFFF);
			}
		}
		for (int x = 0; x < w; x++) {
			int sum = 0;
			for (int y = 0; y < window && y < h; y++) {
				sum += ((tmp[y * w + x] >>> 24) & 0xFF);
			}
			for (int y = 0; y < h; y++) {
				int addIdx = y + radius;
				if (addIdx >= h)
					addIdx = h - 1;
				int subIdx = y - radius - 1;
				if (subIdx < 0)
					subIdx = -1;
				if (subIdx >= 0) {
					sum -= ((tmp[subIdx * w + x] >>> 24) & 0xFF);
				}
				if (addIdx >= 0) {
					sum += ((tmp[addIdx * w + x] >>> 24) & 0xFF);
				}
				int avg = sum / window;
				pixels[y * w + x] = (avg << 24) | (tmp[y * w + x] & 0x00FFFFFF);
			}
		}
	}

	private void gaussianSeparable(int[] pixels, int w, int h, int radius, boolean fastMode) {
		int r = MathUtils.max(1, radius);
		int k = MathUtils.max(1, (int) MathUtils.round(MathUtils.sqrt((12f * r * r / 3f) + 1f) - 1f) / 2);
		boxBlur(pixels, w, h, k, fastMode);
		boxBlur(pixels, w, h, k, fastMode);
		boxBlur(pixels, w, h, k, fastMode);
	}

	private int[] shiftAlpha(int[] src, int w, int h, int dx, int dy, int rgb) {
		int[] out = new int[w * h];
		for (int i = 0; i < w * h; i++)
			out[i] = 0;
		for (int y = 0; y < h; y++) {
			int ny = y + dy;
			if (ny < 0 || ny >= h) {
				continue;
			}
			for (int x = 0; x < w; x++) {
				int nx = x + dx;
				if (nx < 0 || nx >= w) {
					continue;
				}
				int a = (src[y * w + x] >>> 24) & 0xFF;
				out[ny * w + nx] = (a << 24) | rgb;
			}
		}
		return out;
	}

	private int[] createInnerMask(int[] srcPixels, int w, int h, int rgb) {
		int[] out = new int[w * h];
		for (int i = 0; i < w * h; i++) {
			int a = (srcPixels[i] >>> 24) & 0xFF;
			int na = 255 - a;
			out[i] = (na << 24) | rgb;
		}
		return out;
	}

	public int getShadowSize() {
		return _shadowSize;
	}

	public void setShadowSize(int s) {
		this._shadowSize = s;
	}

	public float getShadowAlpha() {
		return _shadowAlpha;
	}

	public void setShadowAlpha(float s) {
		this._shadowAlpha = s;
	}

	public LColor getShadowColor() {
		return _shadowColor;
	}

	public void setShadowColor(LColor s) {
		this._shadowColor = s;
	}

	public int getPixelateSize() {
		return _pixelateSize;
	}

	public ShadowType getShadowType() {
		return _type;
	}
}
