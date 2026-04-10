/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
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
package loon.opengl;

import loon.LTexture;
import loon.canvas.Canvas;
import loon.canvas.LColor;
import loon.geom.Affine2f;

public abstract class BaseBatch extends LTextureBind {

	public abstract BaseBatch setBlendMode(int b);

	public abstract int getBlendMode();

	public void addQuad(LTexture tex, int tint, Affine2f xf, float x, float y, float w, float h) {
		if (tex == null || tex.isClosed() || w < 1f || h < 1f || LColor.getAlpha(tint) <= 0) {
			return;
		}

		setTexture(tex);

		if (tex.isScale() || tex.isCopy()) {
			final float u2 = tex.widthRatio();
			final float v2 = tex.heightRatio();
			addQuad(tint, xf, x, y, x + w, y + h, tex.xOff(), tex.yOff(), u2, v2);
		} else {
			addQuad(tint, xf, x, y, x + w, y + h, 0f, 0f, 1f, 1f);
		}
	}

	public void addQuad(LTexture tex, int tint, Affine2f xf, float dx, float dy, float dw, float dh, float sx, float sy,
			float sw, float sh) {
		if (tex == null || tex.isClosed() || dw < 1f || dh < 1f || sw < 1f || sh < 1f || LColor.getAlpha(tint) <= 0) {
			return;
		}

		setTexture(tex);

		final boolean childTexture = (tex.isScale() || tex.isCopy());
		final LTexture baseTex = childTexture ? Painter.firstFather(tex) : tex;

		final float displayWidth = baseTex.getDisplayWidth();
		final float displayHeight = baseTex.getDisplayHeight();

		final float xOff = ((sx / displayWidth) * baseTex.widthRatio()) + baseTex.xOff()
				+ (childTexture ? tex.xOff() : 0f);
		final float yOff = ((sy / displayHeight) * baseTex.heightRatio()) + baseTex.yOff()
				+ (childTexture ? tex.yOff() : 0f);
		final float widthRatio = ((sw / displayWidth) * baseTex.widthRatio()) + xOff;
		final float heightRatio = ((sh / displayHeight) * baseTex.heightRatio()) + yOff;

		addQuad(tint, xf, dx, dy, dx + dw, dy + dh, xOff, yOff, widthRatio, heightRatio);
	}

	public void addQuad(int tint, Affine2f xf, float left, float top, float right, float bottom, float sl, float st,
			float sr, float sb) {
		addQuad(tint, xf.m00, xf.m01, xf.m10, xf.m11, xf.tx, xf.ty, left, top, right, bottom, sl, st, sr, sb);
	}

	public abstract void addQuad(int tint, float m00, float m01, float m10, float m11, float tx, float ty, float left,
			float top, float right, float bottom, float sl, float st, float sr, float sb);

	protected BaseBatch(Canvas gl) {
		super(gl);
	}
}
