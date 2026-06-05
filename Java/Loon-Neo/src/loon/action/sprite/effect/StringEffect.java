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
package loon.action.sprite.effect;

import loon.LSystem;
import loon.action.map.Field2D;
import loon.canvas.LColor;
import loon.font.IFont;
import loon.font.Text;
import loon.font.TextOptions;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.Easing;
import loon.utils.MathUtils;
import loon.utils.timer.Duration;

/**
 * 一个字符淡出效果类(主要就是减血加血之类效果用的……)
 */
public class StringEffect extends BaseAbstractEffect {

	public static enum StringEffectModel {
		BASE, AWAY, ZOOM, SHAKE, ROTATED, ZOOM_ROTATED, AWAY_ZOOM_ROTATED, AWAY_SHAKE_ROTATED, AWAY_SHAKE_ZOOM_ROTATED,
		FADE_SCALE, FADE_COLOR, TRAIL, WAVE, FLASH, SCALE_IN_FADE_OUT, PULSE, POP, SPIN_SCALE_FADE, TYPEWRITER
	}

	private final static float MOVE_VALUE = 1.5f;
	private final static LColor TEMP_COLOR = new LColor();

	private StringEffectModel _model;

	private float _alphaUpdate;
	private float _scaleUpdate;
	private float _rotatedUpdate;
	private float _shakeUpdate;
	private float _awayOffsetUpdate;
	private Vector2f _updatePos;
	private Text _font;
	private boolean _isCrit;

	private float _lifetime = 1000f;
	private long _elapsed = 0L;

	private float _startScale = 0.2f;
	private float _peakScale = 1.1f;
	private float _endScale = 0.8f;

	private float _pulseFreq = 2f;
	private float _pulseMin = 0.9f;
	private float _pulseMax = 1.1f;

	private float _popPeak = 1.6f;
	private float _popDuration = 300f;

	private float _typewriterSpeed = 60f;
	private float _typewriterProgress = 0f;
	private int _visibleChars = -1;

	/**
	 * not Move
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect notMove(String mes, Vector2f pos, LColor color) {
		return notMove(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * not Move
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect notMove(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(0, 0), color).setAutoRemoved(true);
	}

	/**
	 * ↙
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Ddown(String mes, Vector2f pos, LColor color) {
		return m45Ddown(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ↙
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Ddown(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(-MOVE_VALUE, MOVE_VALUE), color).setAutoRemoved(true);
	}

	/**
	 * ↗
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Dup(String mes, Vector2f pos, LColor color) {
		return m45Dup(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ↗
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Dup(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(MOVE_VALUE, -MOVE_VALUE), color).setAutoRemoved(true);
	}

	/**
	 * ↘
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Dright(String mes, Vector2f pos, LColor color) {
		return m45Dright(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ↘
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Dright(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(MOVE_VALUE, MOVE_VALUE), color).setAutoRemoved(true);
	}

	/**
	 * ↖
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Dleft(String mes, Vector2f pos, LColor color) {
		return m45Dleft(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ↖
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect m45Dleft(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(-MOVE_VALUE, -MOVE_VALUE), color).setAutoRemoved(true);
	}

	/**
	 * →
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect right(String mes, Vector2f pos, LColor color) {
		return right(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * →
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect right(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(MOVE_VALUE, 0), color).setAutoRemoved(true);
	}

	/**
	 * ←
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect left(String mes, Vector2f pos, LColor color) {
		return left(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ←
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect left(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(-MOVE_VALUE, 0), color).setAutoRemoved(true);
	}

	/**
	 * ↑
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect up(String mes, Vector2f pos, LColor color) {
		return up(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ↑
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect up(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(0, -MOVE_VALUE), color).setAutoRemoved(true);
	}

	/**
	 * ↓
	 * 
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect down(String mes, Vector2f pos, LColor color) {
		return down(LSystem.getSystemGameFont(), mes, pos, color).setAutoRemoved(true);
	}

	/**
	 * ↓
	 * 
	 * @param font
	 * @param mes
	 * @param pos
	 * @param color
	 * @return
	 */
	public final static StringEffect down(IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Vector2f.at(0, MOVE_VALUE), color).setAutoRemoved(true);
	}

	public final static StringEffect move(int dir, IFont font, String mes, Vector2f pos, LColor color) {
		return new StringEffect(font, mes, pos, Field2D.getDirection(dir).cpy(), color).setAutoRemoved(true);
	}

	public StringEffect(String mes, Vector2f pos, Vector2f update, LColor c) {
		this(TextOptions.LEFT(), LSystem.getSystemGameFont(), mes, pos, update, c);
	}

	public StringEffect(IFont font, String mes, Vector2f pos, Vector2f update, LColor c) {
		this(TextOptions.LEFT(), font, mes, pos, update, c);
	}

	public StringEffect(IFont font, StringEffectModel model, String mes, Vector2f pos, Vector2f update, LColor c) {
		this(TextOptions.LEFT(), font, model, mes, pos, update, c);
	}

	public StringEffect(TextOptions opt, IFont font, String mes, Vector2f pos, Vector2f update, LColor color) {
		this(opt, font, StringEffectModel.BASE, mes, pos, update, color);
	}

	public StringEffect(TextOptions opt, IFont font, StringEffectModel model, String mes, Vector2f pos, Vector2f update,
			LColor color) {
		this(opt, font, model, 0.0125f, mes, pos, update, color);
	}

	public StringEffect(TextOptions opt, IFont font, StringEffectModel model, float updateValue, String mes,
			Vector2f pos, Vector2f update, LColor color) {
		this._font = new Text(font, mes, opt);
		this._alphaUpdate = _scaleUpdate = _rotatedUpdate = updateValue;
		this._shakeUpdate = _awayOffsetUpdate = updateValue * LSystem.getFPS();
		this._updatePos = update;
		this._objectAlpha = this._scaleX = this._scaleY = 1f;
		this._objectRotation = 0f;
		this._model = model;
		this._lifetime = 1000f;
		this._startScale = 0.2f;
		this._peakScale = 1.1f;
		this._endScale = 0.8f;
		this._pulseFreq = 2f;
		this._pulseMin = 0.9f;
		this._pulseMax = 1.1f;
		this._popPeak = 1.6f;
		this._popDuration = 300f;
		this._typewriterSpeed = 60f;
		this._completed = false;
		this.setLocation(pos);
		this.setColor(color);
		this.setSize(_font.getWidth(), _font.getHeight());
		this.setLocation(pos.x, pos.y);
		this.setRepaint(true);
	}

	public StringEffect setLifetime(float ms) {
		this._lifetime = ms;
		return this;
	}

	public StringEffect setScaleRange(float start, float peak, float end) {
		this._startScale = start;
		this._peakScale = peak;
		this._endScale = end;
		return this;
	}

	public StringEffect setPulse(float freq, float min, float max) {
		this._pulseFreq = freq;
		this._pulseMin = min;
		this._pulseMax = max;
		return this;
	}

	public StringEffect setPop(float peak, float durationMs) {
		this._popPeak = peak;
		this._popDuration = durationMs;
		return this;
	}

	public StringEffect setTypewriterSpeed(float charsPerSecond) {
		this._typewriterSpeed = charsPerSecond;
		return this;
	}

	public StringEffect setCrit(boolean crit) {
		this._isCrit = crit;
		if (crit) {
			setScaleUpdateValue(_scaleUpdate * 2);
			setAlphaUpdateValue(_alphaUpdate * 0.8f);
		}
		return this;
	}

	public StringEffectModel getEffectModel() {
		return this._model;
	}

	public StringEffect setStringEffect(StringEffectModel m) {
		this._model = m;
		return this;
	}

	public StringEffect setScaleUpdateValue(float s) {
		this._scaleUpdate = s;
		return this;
	}

	public float getScaleUpdateValue() {
		return _scaleUpdate;
	}

	public float getAlphaUpdateValue() {
		return _alphaUpdate;
	}

	public StringEffect setAlphaUpdateValue(float a) {
		this._alphaUpdate = a;
		return this;
	}

	public float getRotatedUpdateValue() {
		return _rotatedUpdate;
	}

	public StringEffect setRotatedUpdateValue(float r) {
		this._rotatedUpdate = r;
		return this;
	}

	public float getAwayOffsetUpdateValue() {
		return _awayOffsetUpdate;
	}

	public StringEffect setAwayOffsetUpdateValue(float a) {
		this._awayOffsetUpdate = a;
		return this;
	}

	public float getShakeUpdateValue() {
		return _shakeUpdate;
	}

	public StringEffect setShakeUpdateValue(float s) {
		this._shakeUpdate = s;
		return this;
	}

	public float getFontWidth() {
		return _font == null ? 0 : _font.getWidth();
	}

	public IFont getFont() {
		return _font == null ? null : _font.getFont();
	}

	public Text getText() {
		return _font;
	}

	public boolean isCrit() {
		return _isCrit;
	}

	protected void onAwayEffect() {
		getLocation().addSelf(_awayOffsetUpdate, 0f);
	}

	protected void onShakeEffect() {
		getLocation().addSelf(MathUtils.nextBoolean() ? _shakeUpdate : -_shakeUpdate, 0f);
	}

	protected void onZoomEffect() {
		setScale(getScaleX() + _scaleUpdate, getScaleY() + _scaleUpdate);
	}

	protected void onRotatedEffect() {
		final float v = MathUtils.toDegrees(_rotatedUpdate);
		setRotation(MathUtils.nextBoolean() ? (getRotation() + v) : (getRotation() - v));
	}

	@Override
	public void onUpdate(long elapsedTime) {
		if (checkAutoRemove()) {
			return;
		}
		if (_timer.action(elapsedTime)) {
			float dt = elapsedTime;
			getLocation().addSelf(this._updatePos);
			_elapsed += dt;
			switch (_model) {
			case BASE:
			default:
				this._objectAlpha -= _alphaUpdate;
				break;
			case AWAY:
				onAwayEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case ZOOM:
				onZoomEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case SHAKE:
				onShakeEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case ROTATED:
				onRotatedEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case ZOOM_ROTATED:
				onZoomEffect();
				onRotatedEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case AWAY_ZOOM_ROTATED:
				onAwayEffect();
				onZoomEffect();
				onRotatedEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case AWAY_SHAKE_ROTATED:
				onAwayEffect();
				onShakeEffect();
				onRotatedEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case AWAY_SHAKE_ZOOM_ROTATED:
				onAwayEffect();
				onShakeEffect();
				onZoomEffect();
				onRotatedEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case FADE_SCALE:
				onZoomEffect();
				this._objectAlpha -= _alphaUpdate * 1.1f;
				break;
			case FADE_COLOR:
				this._objectAlpha -= _alphaUpdate;
				_baseColor = _baseColor.interpolate(_baseColor, 1f - _objectAlpha);
				break;
			case TRAIL:
				onAwayEffect();
				this._objectAlpha -= _alphaUpdate;
				break;
			case WAVE:
				float wave = MathUtils.sin(MathUtils.random() * 0.2f) * 5f;
				getLocation().addSelf(0, wave);
				this._objectAlpha -= _alphaUpdate;
				break;
			case FLASH:
				if (MathUtils.random(0, 20) % 5 == 0) {
					this._objectAlpha = (this._objectAlpha > 0.5f) ? 0.2f : 1f;
				}
				this._objectAlpha -= _alphaUpdate * 0.5f;
				break;
			case SCALE_IN_FADE_OUT:
				float t = _lifetime <= 0 ? 1f : MathUtils.min(1f, _elapsed / _lifetime);
				if (t < 0.5f) {
					float p = t / 0.5f;
					float s = MathUtils.lerp(_startScale, _peakScale, Easing.elasticEaseInOut(p));
					setScale(s, s);
					this._objectAlpha = MathUtils.min(1f, p * 2f);
				} else {
					float p = (t - 0.5f) / 0.5f;
					float s = MathUtils.lerp(_peakScale, _endScale, Easing.elasticEaseInOut(p));
					setScale(s, s);
					this._objectAlpha = 1f - p;
				}
				break;
			case PULSE:
				float seconds = Duration.toS(_elapsed);
				float phase = (MathUtils.sin(seconds * MathUtils.TWO_PI * _pulseFreq) * 0.5f + 0.5f);
				float s = MathUtils.lerp(_pulseMin, _pulseMax, phase);
				setScale(s, s);
				this._objectAlpha -= _alphaUpdate * 0.5f;
				break;
			case POP:
				if (_elapsed < _popDuration) {
					float p = _elapsed / _popDuration;
					float sv = _popPeak - (_popPeak - 1f) * Easing.elasticEaseOut(p);
					setScale(sv, sv);
					this._objectAlpha = 1f;
				} else {
					this._objectAlpha -= _alphaUpdate * 2f;
				}
				break;
			case SPIN_SCALE_FADE:
				onRotatedEffect();
				setScale(getScaleX() + _scaleUpdate * 0.5f, getScaleY() + _scaleUpdate * 0.5f);
				this._objectAlpha -= _alphaUpdate;
				break;
			case TYPEWRITER:
				_typewriterProgress += dt / 1000f * _typewriterSpeed;
				int total = _font.getText().length();
				_visibleChars = MathUtils.min(total, (int) _typewriterProgress);
				if (_visibleChars >= total) {
					this._objectAlpha -= _alphaUpdate;
				}
				break;
			}
			if (this._objectAlpha <= 0) {
				_completed = true;
			}
			if (_lifetime > 0 && _elapsed >= _lifetime) {
				if (_model == StringEffectModel.SCALE_IN_FADE_OUT || _model == StringEffectModel.POP
						|| _model == StringEffectModel.TYPEWRITER) {
					_completed = true;
				}
			}
		}
	}

	@Override
	public void repaint(GLEx g, float offsetX, float offsetY) {
		if (completedAfterBlackScreen(g, offsetX, offsetY)) {
			return;
		}
		LColor drawColor = _baseColor.multiply(this._objectAlpha, TEMP_COLOR);
		if (_model == StringEffectModel.TYPEWRITER && _visibleChars > 0) {
			CharSequence full = _font.getText();
			int end = MathUtils.min(_visibleChars, full.length());
			CharSequence sub = full.subSequence(0, end);
			_font.setText(sub);
			_font.paintString(g, drawX(offsetX), drawY(offsetY), drawColor);
			_font.setText(full);
		} else {
			_font.paintString(g, drawX(offsetX), drawY(offsetY), drawColor);
		}
	}

	@Override
	public StringEffect setAutoRemoved(boolean autoRemoved) {
		super.setAutoRemoved(autoRemoved);
		return this;
	}

	@Override
	protected void _onDestroy() {
		super._onDestroy();
		if (_font != null) {
			_font.close();
		}
	}

}
