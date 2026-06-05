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

import java.util.Iterator;

import loon.LSystem;
import loon.canvas.LColor;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.TArray;
import loon.utils.timer.Duration;

/**
 * 特效类，以多组线条组成的淡入淡出渐变（效果类似某影中金刚封锁）
 */
public class FadeLineEffect extends BaseAbstractEffect {

	public static interface LifecycleCallback {

		void onFilled(FadeLineEffect effect);

		void onFadeOutStart(FadeLineEffect effect);

		void onCompleted(FadeLineEffect effect);
	}

	private static class Line {
		public float x1, y1;
		public float curLen;
		public float targetLen;
		public float angle;
		public float speed;
		public float thickness;
		public float alpha;
		public int dirType;
	}

	private final TArray<Line> _lines = new TArray<Line>();

	private final LColor _tempColor = new LColor();

	private float _density = 100f;
	private float _spawnRate = 36f;
	private float _minThickness = 1.6f;
	private float _maxThickness = 10.6f;
	private float _minSpeed = 260f;
	private float _maxSpeed = 560f;
	private float _coverThreshold = 0f;
	private float _fadeOutMaskSpeed = 0.3f;

	private boolean _fadingOut = false;

	private float _globalMaskAlpha = 0f;

	private LifecycleCallback _lifecycleCallback = null;

	private int _typeCode = 0;

	public FadeLineEffect(int code, LColor c) {
		this(code, c, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public FadeLineEffect(int code, LColor c, int width, int height) {
		this.setSize(width, height);
		this.setRepaint(true);
		this.setCompletedAfterBlack(true);
		this.setColor(c);
		_typeCode = code;
		if (_typeCode == TYPE_FADE_IN) {
			skipToFadeIn();
			triggerFadeIn();
			_globalMaskAlpha = 1f;
		} else {
			for (int i = 0; i < (int) (_density * 0.45f); i++) {
				_lines.add(createRandomLine());
			}
			_globalMaskAlpha = 0f;
		}
		_maxSpeed = MathUtils.max(width, height);
	}

	public int getEffectTypeCode() {
		return _typeCode;
	}

	public FadeLineEffect skipToFadeIn() {
		_lines.clear();
		for (int i = 0; i < (int) _density; i++) {
			Line l = createRandomLine();
			l.curLen = l.targetLen;
			l.alpha = 1f;
			_lines.add(l);
		}
		_fadingOut = false;
		_globalMaskAlpha = 1f;
		if (_lifecycleCallback != null) {
			_lifecycleCallback.onFadeOutStart(this);
		}
		return this;
	}

	private LColor getDefaultColor(float progress) {
		float a = MathUtils.min(1f, 0.6f + 0.4f * progress);
		return _tempColor.setColor(_baseColor.r, _baseColor.g, _baseColor.b, a);
	}

	private static float getDirection() {
		float p = MathUtils.random();
		if (p < 0.45f) {
			return (MathUtils.random() - 0.5f) * 0.25f;
		} else if (p < 0.9f) {
			return (MathUtils.PI / 2f + (MathUtils.random() - 0.5f) * 0.25f);
		} else {
			return ((MathUtils.random() - 0.5f) * MathUtils.PI * 0.6f);
		}
	}

	public FadeLineEffect setLifecycleCallback(LifecycleCallback cb) {
		this._lifecycleCallback = cb;
		return this;
	}

	public FadeLineEffect setDensity(float _density) {
		this._density = MathUtils.max(8f, _density);
		return this;
	}

	public FadeLineEffect setSpawnRate(float _spawnRate) {
		this._spawnRate = MathUtils.max(0f, _spawnRate);
		return this;
	}

	public FadeLineEffect setThicknessRange(float min, float max) {
		this._minThickness = MathUtils.max(0.1f, MathUtils.min(min, max));
		this._maxThickness = MathUtils.max(_minThickness, max);
		return this;
	}

	public FadeLineEffect setSpeedRange(float min, float max) {
		this._minSpeed = MathUtils.max(1f, MathUtils.min(min, max));
		this._maxSpeed = MathUtils.max(_minSpeed, max);
		return this;
	}

	public FadeLineEffect setCoverThreshold(float t) {
		this._coverThreshold = MathUtils.max(0f, MathUtils.min(1f, t));
		return this;
	}

	private Line createRandomLine() {
		Line l = new Line();
		int w = width();
		int h = height();
		float r0 = MathUtils.random();
		if (r0 < 0.45f) {
			l.dirType = 0;
		} else if (r0 < 0.9f) {
			l.dirType = 1;
		} else {
			l.dirType = 2;
		}
		switch (l.dirType) {
		case 0:
			l.x1 = MathUtils.nextBoolean() ? -w * 0.08f : w + w * 0.08f;
			l.y1 = MathUtils.random() * h;
			break;
		case 1:
			l.y1 = MathUtils.nextBoolean() ? -h * 0.08f : h + h * 0.08f;
			l.x1 = MathUtils.random() * w;
			break;
		default:
			if (MathUtils.nextBoolean()) {
				l.x1 = -w * 0.08f;
				l.y1 = MathUtils.random() * h;
			} else {
				l.x1 = MathUtils.random() * w;
				l.y1 = -h * 0.08f;
			}
			break;
		}
		l.angle = getDirection();
		float diag = MathUtils.hypot(w, h);
		l.targetLen = diag * (0.6f + MathUtils.random() * 1.2f);
		l.curLen = MathUtils.random() * l.targetLen * 0.08f;
		l.speed = _minSpeed + MathUtils.random() * (_maxSpeed - _minSpeed);
		l.thickness = _minThickness + MathUtils.random() * (_maxThickness - _minThickness);
		l.alpha = 0f;
		return l;
	}

	@Override
	public void onUpdate(long elapsedTime) {
		if (checkAutoRemove()) {
			return;
		}
		float seconds = Duration.toS(elapsedTime);
		float spawnThisFrame = _spawnRate * seconds;
		int toSpawn = (int) spawnThisFrame;
		if (MathUtils.random() < (spawnThisFrame - toSpawn)) {
			toSpawn++;
		}
		for (int i = 0; i < toSpawn && _lines.size() < _density * 2; i++) {
			_lines.add(createRandomLine());
		}
		Iterator<Line> it = _lines.iterator();
		int activeCount = 0;
		while (it.hasNext()) {
			Line l = it.next();
			if (!_fadingOut) {
				l.curLen += l.speed * seconds;
				l.alpha = MathUtils.min(1f, l.alpha + 1.6f * seconds);
				if (l.curLen >= l.targetLen) {
					l.curLen = l.targetLen;
				}
				if (l.alpha > 0.02f) {
					activeCount++;
				}
			} else {
				l.curLen -= l.speed * 0.95f * seconds;
				l.alpha = MathUtils.max(0f, l.alpha - 0.95f * seconds);
				if (l.curLen <= 0f || l.alpha <= 0f) {
					it.remove();
					continue;
				} else {
					activeCount++;
				}
			}
		}
		if (!_fadingOut) {
			_globalMaskAlpha = MathUtils.min(1f, _globalMaskAlpha + _fadeOutMaskSpeed * seconds);
			float cover = MathUtils.min(1f, activeCount / MathUtils.max(1f, _density));
			if (cover >= _coverThreshold && _lines.size >= (_density * 2)) {
				_completed = true;
				if (_lifecycleCallback != null) {
					_lifecycleCallback.onFilled(this);
				}
			}
		} else {
			_globalMaskAlpha = MathUtils.max(0f, _globalMaskAlpha - (_fadeOutMaskSpeed * seconds));
			if (_lines.isEmpty()) {
				_completed = true;
				if (_lifecycleCallback != null) {
					_lifecycleCallback.onCompleted(this);
				}
			}
		}
	}

	@Override
	public void repaint(GLEx g, float offsetX, float offsetY) {
		if (completedAfterBlackScreen(g, offsetX, offsetY)) {
			return;
		}
		int oldColor = g.color();
		float oldLine = g.getLineWidth();

		for (int i = _lines.size - 1; i > -1; i--) {
			Line l = _lines.get(i);
			if (l != null) {
				float progress = l.curLen / MathUtils.max(1f, l.targetLen);
				float dx = MathUtils.cos(l.angle);
				float dy = MathUtils.sin(l.angle);
				float ex = l.x1 + dx * l.curLen;
				float ey = l.y1 + dy * l.curLen;
				g.setLineWidth(l.thickness);
				g.drawLine(drawX(offsetX + l.x1), drawY(offsetY + l.y1), drawX(offsetX + ex), drawY(offsetY + ey),
						getDefaultColor(progress));
			}
		}

		if (_globalMaskAlpha > 0f) {
			g.fillRect(drawX(offsetX), drawY(offsetY), getWidth(), getHeight(),
					new LColor(0f, 0f, 0f, _globalMaskAlpha));
		}
		g.setLineWidth(oldLine);
		g.setColor(oldColor);
	}

	public void triggerFadeIn() {
		if (!_fadingOut) {
			_completed = false;
			_fadingOut = true;
			_globalMaskAlpha = 1f;
			if (_lifecycleCallback != null) {
				_lifecycleCallback.onFadeOutStart(this);
			}
			for (int i = _lines.size - 1; i > -1; i--) {
				Line l = _lines.get(i);
				if (l != null) {
					l.speed *= 0.7f + MathUtils.random() * 0.8f;
				}
			}
		}
	}

	@Override
	public FadeLineEffect reset() {
		super.reset();
		_lines.clear();
		if (_typeCode == TYPE_FADE_IN) {
			_globalMaskAlpha = 1f;
		} else {
			_globalMaskAlpha = 0f;
		}
		_fadingOut = false;
		for (int i = 0; i < (int) (_density * 0.45f); i++) {
			_lines.add(createRandomLine());
		}
		return this;
	}

}
