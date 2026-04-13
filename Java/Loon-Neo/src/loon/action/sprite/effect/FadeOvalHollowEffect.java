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
package loon.action.sprite.effect;

import loon.LSystem;
import loon.LTexture;
import loon.canvas.LColor;
import loon.opengl.GLEx;
import loon.opengl.GLRenderer.GLType;
import loon.utils.MathUtils;

public class FadeOvalHollowEffect extends BaseAbstractEffect {

	private final static float SIZE = 8f;

	private float _previous;
	private float _diameter;
	private int _endRadius;
	private int _typeCode;
	private float _step;
	private float _spaceSize;

	// 镂空圆心坐标
	private float _centerX;
	private float _centerY;

	public FadeOvalHollowEffect(int code) {
		this(code, LColor.black);
	}

	public FadeOvalHollowEffect(int code, LColor c) {
		this(code, c, 0, 0, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public FadeOvalHollowEffect(int code, LColor c, int x, int y, int width, int height) {
		this(code, c, x, y, width, height, width / 2, height / 2);
	}

	public FadeOvalHollowEffect(int code, LColor c, int x, int y, int width, int height, float centerX, float centerY) {
		this.setLocation(x, y);
		this.setSize(width, height);
		this._typeCode = code;
		this._diameter = 1f;
		this._spaceSize = SIZE;
		this.setColor(c == null ? LColor.black : c);
		this.setRepaint(true);
		this._centerX = centerX;
		this._centerY = centerY;
		this.updateRadius();
	}

	public FadeOvalHollowEffect setCenter(float x, float y) {
		this._centerX = x;
		this._centerY = y;
		this.updateRadius();
		return this;
	}

	@Override
	public FadeOvalHollowEffect setTexture(LTexture tex) {
		super.setTexture(tex);
		this.updateRadius();
		this.setRepaint(true);
		return this;
	}

	public FadeOvalHollowEffect updateRadius() {
		float dist1 = MathUtils.sqrt(_centerX * _centerX + _centerY * _centerY);
		float dist2 = MathUtils.sqrt((getWidth() - _centerX) * (getWidth() - _centerX) + _centerY * _centerY);
		float dist3 = MathUtils.sqrt(_centerX * _centerX + (getHeight() - _centerY) * (getHeight() - _centerY));
		float dist4 = MathUtils.sqrt((getWidth() - _centerX) * (getWidth() - _centerX)
				+ (getHeight() - _centerY) * (getHeight() - _centerY));
		this._endRadius = MathUtils.ifloor(
				MathUtils.ceil(MathUtils.max(MathUtils.max(dist1, dist2), MathUtils.max(dist3, dist4))) * 2.1f);
		if (MathUtils.isOdd(_endRadius)) {
			_endRadius += 1;
		}
		this._previous = _endRadius;
		return this;
	}

	public int getEndRadius() {
		return _endRadius;
	}

	public float getEffectDiameter() {
		// in
		if (_typeCode == TYPE_FADE_IN) {
			return _endRadius * _step * 2f;
		} else {
			return (_endRadius - _endRadius * _step) * 2f;
		}
	}

	@Override
	public void onUpdate(long elapsedTime) {
		if (checkAutoRemove()) {
			return;
		}
		if (_timer.action(elapsedTime)) {
			_step += MathUtils.min(0.05f, (float) elapsedTime / LSystem.SECOND);
		}
	}

	public int getTypeCode() {
		return _typeCode;
	}

	@Override
	public void repaint(GLEx g, float offsetX, float offsetY) {
		if (completedAfterBlackScreen(g, offsetX, offsetY)) {
			return;
		}
		this._diameter = getEffectDiameter();
		int old = g.color();
		float line = g.getLineWidth();
		g.setColor(_baseColor);
		g.setLineWidth(_spaceSize);
		if (_spaceSize == 1f) {
			g.beginBatchRenderer(GLType.Line);
		}
		if (_typeCode == TYPE_FADE_IN) {
			for (int i = _endRadius * 2; i >= _diameter; i -= _spaceSize) {
				final float x = drawX(offsetX + (_centerX - i / 2f));
				final float y = drawY(offsetY + (_centerY - i / 2f));
				g.drawOval(x, y, i - _spaceSize, i - _spaceSize);
				g.drawOval(x, y, i + _spaceSize, i + _spaceSize);
			}
			if (_diameter > MathUtils.max(getWidth(), getHeight())) {
				_completed = true;
			}
		} else {
			for (int i = MathUtils.floor(_previous * 2); i >= _diameter; i -= _spaceSize) {
				final float x = drawX(offsetX + (_centerX - i / 2f));
				final float y = drawY(offsetY + (_centerY - i / 2f));
				g.drawOval(x, y, i - _spaceSize, i - _spaceSize);
				g.drawOval(x, y, i + _spaceSize, i + _spaceSize);
			}
			if (_diameter <= _endRadius / 2) {
				g.fillRect(drawX(offsetX), drawY(offsetY), getWidth(), getHeight());
				_completed = true;
			}
		}
		g.setLineWidth(line);
		if (_spaceSize == 1f) {
			g.endBatchRenderer();
		}
		g.setColor(old);
		this._previous = _diameter;
	}

	public float getSpaceSize() {
		return _spaceSize;
	}

	public FadeOvalHollowEffect setSpaceSize(float s) {
		this._spaceSize = s;
		return this;
	}

	@Override
	public FadeOvalHollowEffect setAutoRemoved(boolean autoRemoved) {
		super.setAutoRemoved(autoRemoved);
		return this;
	}

	@Override
	public FadeOvalHollowEffect reset() {
		super.reset();
		this.updateRadius();
		this._step = 0f;
		this._spaceSize = SIZE;
		return this;
	}

}
