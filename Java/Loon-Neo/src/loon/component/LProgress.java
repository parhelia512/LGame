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
package loon.component;

import loon.LSystem;
import loon.LTexture;
import loon.LTextures;
import loon.canvas.LColor;
import loon.component.skin.ProgressSkin;
import loon.component.skin.SkinManager;
import loon.events.ValueListener;
import loon.opengl.GLEx;
import loon.utils.MathUtils;

/**
 * 一个进度条用UI
 */
public class LProgress extends LComponent {
	// 默认提供了四种进度条模式，分别是游戏类血槽，普通的UI形式，圆形UI模式，以及用户自制图像.(默认为游戏模式)
	public enum ProgressType {
		GAME, UI, CircleUI, Custom
	}

	private LTexture _defaultColorTexture;
	private LTexture _bgTexture;
	private LTexture _bgTextureEnd;
	private LTexture _bgProgressTexture;
	private LTexture _bgProgressStart;
	private ValueListener _listener;
	private float _percentage = 1f;
	private float _minValue = 0f;
	private float _maxValue = 0f;
	private boolean _vertical = false;
	private LTexture _texture;
	private ProgressType _progressType;
	private float _currentPercentage;

	private float _targetPercentage;
	private long _animationDuration;
	private boolean _enableAnimation;

	private float _circleStrokeSize;
	private float _lastNotifyPercentage;

	public LProgress(int x, int y, int width, int height) {
		this(ProgressType.GAME, LColor.red, x, y, width, height, null, null);
	}

	public LProgress(LColor color, int x, int y, int width, int height) {
		this(ProgressType.GAME, color, x, y, width, height, null, null);
	}

	public LProgress(ProgressType type, int x, int y, int width, int height) {
		this(type, LColor.red, x, y, width, height, null, null);
	}

	public LProgress(ProgressType type, LColor color, int x, int y, int width, int height) {
		this(type, color, x, y, width, height, null, null);
	}

	public LProgress(ProgressSkin skin, int x, int y, int width, int height) {
		this(ProgressType.Custom, skin.getColor(), x, y, width, height, skin.getBackgroundTexture(),
				skin.getProgressTexture());
	}

	public LProgress(ProgressType type, LColor color, int x, int y, int width, int height, LTexture bg,
			LTexture bgProgress) {
		super(x, y, width, height);
		this._animationDuration = 300;
		this._enableAnimation = true;
		this._circleStrokeSize = MathUtils.min(getWidth(), getHeight()) / 6f;
		this._targetPercentage = 1f;
		this._lastNotifyPercentage = -1f;
		this._progressType = type;
		this._component_baseColor = color == null ? LColor.red : color;
		initTextures(bg, bgProgress);
		this.reset();
	}

	@Override
	public void update(final long elapsedTime) {
		super.update(elapsedTime);
		if (_enableAnimation && !MathUtils.equal(_currentPercentage, _targetPercentage)) {
			float t = MathUtils.clamp(elapsedTime / (float) _animationDuration, 0, 1);
			_currentPercentage = MathUtils.lerp(_currentPercentage, _targetPercentage, t);
		} else {
			_currentPercentage = _targetPercentage;
		}
		if (_listener != null && !MathUtils.equal(_lastNotifyPercentage, _currentPercentage)) {
			_listener.onChange(this, _currentPercentage);
			_lastNotifyPercentage = _currentPercentage;
		}
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (_progressType != ProgressType.CircleUI) {
			draw(g, x, y);
		} else {
			drawCircleUI(g, x, y);
		}
	}

	public void draw(GLEx g, int x, int y) {
		float drawPercent = _enableAnimation ? _currentPercentage : _targetPercentage;
		if (_vertical) {
			drawVertical(g, x, y, drawPercent);
		} else {
			drawHorizontal(g, x, y, drawPercent);
		}
	}

	public boolean isZero() {
		return MathUtils.equal(this.getValue(), 0f);
	}

	public boolean isMin() {
		return MathUtils.equal(this.getValue(), this._minValue);
	}

	public boolean isMax() {
		return MathUtils.equal(this.getValue(), this._maxValue);
	}

	public float getValue() {
		if (MathUtils.equal(_maxValue, _minValue)) {
			return _minValue;
		}
		return _minValue + (_maxValue - _minValue) * _targetPercentage;
	}

	public LProgress setValue(float v) {
		if (MathUtils.equal(_maxValue, _minValue)) {
			this._targetPercentage = 1f;
			return this;
		}
		v = MathUtils.clamp(v, _minValue, _maxValue);
		this._targetPercentage = (v - _minValue) / (_maxValue - _minValue);
		this._percentage = this._targetPercentage;
		return this;
	}

	public float getMinValue() {
		return _minValue;
	}

	public LProgress setMinValue(float v) {
		if (v > this._maxValue) {
			this._maxValue = v;
		}
		this._minValue = v;
		setValue(getValue());
		return this;
	}

	public float getMaxValue() {
		return _maxValue;
	}

	public LProgress setMaxValue(float v) {
		if (v < this._minValue) {
			this._minValue = v;
		}
		this._maxValue = v;
		setValue(getValue());
		return this;
	}

	public LProgress setPercentage(float p) {
		p = MathUtils.clamp(p, 0f, 1f);
		setValue(_minValue + (_maxValue - _minValue) * p);
		return this;
	}

	public LProgress setValue(float v, float min, float max) {
		setMinValue(min);
		setMaxValue(max);
		setValue(v);
		return this;
	}

	public boolean isVertical() {
		return _vertical;
	}

	public void setVertical(boolean vertical) {
		this._vertical = vertical;
	}

	public float getPercentage() {
		return this._percentage;
	}

	public ValueListener getListener() {
		return _listener;
	}

	public void setListener(ValueListener listener) {
		this._listener = listener;
	}

	private void safeCloseTexture(LTexture texture) {
		if (texture != null && !texture.isClosed()) {
			texture.close();
		}
	}

	private void initTextures(LTexture bg, LTexture bgProgress) {
		switch (_progressType) {
		case GAME:
			initGameTexture();
			break;
		case UI:
		case CircleUI:
			initUITexture();
			break;
		default:
			_bgTexture = bg == null ? getDefaultColorTexture() : bg;
			_bgProgressTexture = bgProgress == null ? getDefaultColorTexture() : bgProgress;
			break;
		}
	}

	private void initGameTexture() {
		_texture = LTextures.loadTexture(LSystem.getSystemImagePath() + "bar.png");
		_bgTexture = _texture.cpy(3, 0, 1, _texture.height() - 2);
		_bgProgressTexture = _texture.cpy(1, 0, 1, _texture.height() - 2);
		_bgProgressStart = _texture.cpy(0, 0, 1, _texture.height() - 2);
		_bgTextureEnd = _texture.cpy(4, 0, 1, _texture.height() - 2);
	}

	private void initUITexture() {
		_defaultColorTexture = getDefaultColorTexture();
		ProgressSkin skin = SkinManager.get() != null ? SkinManager.get().getProgressSkin() : new ProgressSkin();
		_bgTexture = skin.getBackgroundTexture() == null ? _defaultColorTexture : skin.getBackgroundTexture();
		_bgProgressTexture = _defaultColorTexture;
	}

	private LTexture getDefaultColorTexture() {
		if (_defaultColorTexture == null || _defaultColorTexture.isClosed()) {
			_defaultColorTexture = LSystem.base().graphics().finalColorTex();
		}
		return _defaultColorTexture;
	}

	private void drawHorizontal(GLEx g, int x, int y, float percent) {
		switch (_progressType) {
		case GAME:
			g.draw(_bgTexture, x + getWidth() * percent + 1, y, getWidth() * (1 - percent), getHeight());
			g.draw(_bgTextureEnd, x + getWidth() + 1, y, _bgTextureEnd.width(), getHeight());
			g.setColor(_component_baseColor);
			g.draw(_bgProgressTexture, x + 1, y, getWidth() * percent, getHeight());
			g.draw(_bgProgressStart, x, y, _bgProgressStart.width(), getHeight());
			g.resetColor();
			break;
		case UI:
			g.draw(_bgTexture, x, y, getWidth(), getHeight());
			g.setColor(_component_baseColor);
			g.draw(_bgProgressTexture, x + 1, y + 1, getWidth() * percent - 2, getHeight() - 2);
			g.resetColor();
			break;
		default:
			g.draw(_bgTexture, x, y, getWidth(), getHeight());
			g.setColor(_component_baseColor);
			g.draw(_bgProgressTexture, x, y, getWidth() * percent, getHeight());
			g.resetColor();
			break;
		}
	}

	private void drawVertical(GLEx g, int x, int y, float percent) {
		switch (_progressType) {
		case GAME:
			float size = getWidth() * (1f - percent);
			float posY = getHeight() / 2;
			g.draw(_bgTexture, x + getHeight() / 2 + getWidth() / 2, y - posY, size, getHeight(), 0f, 0f, 90);
			g.setColor(_component_baseColor);
			size = getWidth() * percent;
			g.draw(_bgProgressTexture, x + getHeight() / 2 + getWidth() / 2, y + getWidth() - size - posY,
					getWidth() * percent, getHeight(), 0f, 0f, 90);
			g.resetColor();
			break;
		case UI:
			g.draw(_bgTexture, x, y, getHeight(), getWidth());
			g.setColor(_component_baseColor);
			size = (getWidth() * percent - 2);
			g.draw(_bgProgressTexture, x + 1, y + getWidth() - size - 1, getHeight() - 2, size);
			g.resetColor();
			break;
		default:
			g.draw(_bgTexture, x, y, getHeight(), getWidth());
			g.setColor(_component_baseColor);
			size = (getWidth() * percent);
			g.draw(_bgProgressTexture, x, y + getWidth() - size, getHeight(), size);
			g.resetColor();
			break;
		}
	}

	private void drawCircleUI(GLEx g, int x, int y) {
		float drawPercent = _enableAnimation ? _currentPercentage : _targetPercentage;
		g.drawStrokeGradientCircle(x, y, 0, 360f, getWidth(), getHeight(), _circleStrokeSize, LColor.gray,
				LColor.darkGray);
		g.drawStrokeGradientCircle(x, y, 0, 360f * drawPercent, getWidth(), getHeight(), _circleStrokeSize,
				_component_baseColor.darker(), _component_baseColor);
	}

	public void setEnableAnimation(boolean enable) {
		this._enableAnimation = enable;
	}

	public void setAnimationDuration(long duration) {
		this._animationDuration = Math.max(0, duration);
	}

	public void setCircleStrokeSize(float size) {
		this._circleStrokeSize = Math.max(1, size);
	}

	@Override
	public LProgress reset() {
		super.reset();
		this._percentage = 1f;
		this._targetPercentage = 1f;
		this._currentPercentage = 1f;
		this._minValue = 0f;
		this._maxValue = 100f;
		return this;
	}

	@Override
	public String getUIName() {
		return "Progress";
	}

	@Override
	public void destory() {
		safeCloseTexture(_bgTexture);
		safeCloseTexture(_bgTextureEnd);
		safeCloseTexture(_bgProgressTexture);
		safeCloseTexture(_bgProgressStart);
		safeCloseTexture(_texture);
	}
}
