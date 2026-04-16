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
import loon.LTextures;
import loon.action.collision.CollisionHelper;
import loon.action.map.Config;
import loon.action.map.Side;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.Easing;
import loon.utils.MathUtils;
import loon.utils.TArray;

/**
 * 残像效果构建用类(就是上古游戏梦幻模拟战里骑士冲锋那类效果)
 * 
 * 有两种使用方式。
 * 
 * 一种是自动按照初始设定的移动方向相对位置构建，即自动生成一组向指定方向直接移动的残像。
 * 
 * 一种是依据初始注入数组的图像位置绝对路径构建，即注入一组AfterObject，然后在指定位置显示后依次淡出。
 */
public class AfterImageEffect extends BaseAbstractEffect {

	public final static int LINE = 0;
	public final static int CURVE = 1;

	public final class AfterObject {

		float alpha = 1f;
		float x = 0f;
		float y = 0f;
		float width = 0f;
		float height = 0f;
		float scale = 1f;
		float rotation = 0f;
		LTexture texture;
		AfterObject previous;

		public AfterObject() {
			this(0f, 0f, 0f, 0f, 1f);
		}

		public AfterObject(float x, float y, float w, float h, float a) {
			this(null, x, y, w, h, a);
		}

		public AfterObject(LTexture tex, float x, float y, float w, float h, float a) {
			this.texture = tex;
			this.x = x;
			this.y = y;
			this.width = w;
			this.height = h;
			this.alpha = a;
			this.scale = 1f;
			this.rotation = 0f;
		}

		public AfterObject setTexture(LTexture t) {
			this.texture = t;
			return this;
		}

		public LTexture getTexture() {
			return this.texture;
		}

		public float getX() {
			return this.x;
		}

		public float getY() {
			return this.y;
		}

		public void setPos(float x, float y) {
			this.x = x;
			this.y = y;
		}

		public AfterObject setAlpha(float a) {
			this.alpha = MathUtils.clamp(a, 0f, 1f);
			return this;
		}

		public float getAlpha() {
			return this.alpha;
		}

		public AfterObject setScale(float s) {
			this.scale = MathUtils.clamp(s, 0.1f, 2f);
			return this;
		}

		public float getScale() {
			return this.scale;
		}

		public AfterObject setRotation(float r) {
			this.rotation = r;
			return this;
		}

		public float getRotation() {
			return this.rotation;
		}
	}

	private final TArray<AfterImageEffect.AfterObject> _backAfters = new TArray<>();
	private final LColor _shadowColor = new LColor();
	private final LColor _tempColor = new LColor();
	private LTexture _afterTexture;

	private boolean _hideFirstObject;
	private boolean _makeAutoEffect;
	private boolean _displayCompleted;
	private boolean _moveOrbitReverse;
	private boolean _inited;
	private boolean _looping;
	private boolean _playing;
	private boolean _alphaDecreasing;

	private int _moveOrbit = 0;
	private int _indexNext = 0;
	private int _moveDir = Config.EMPTY;

	private float _orbitValue = 0f;
	private float _startX, _startY, _startWidth, _startHeight;
	private TArray<AfterImageEffect.AfterObject> _afterObjects;
	private float _interval = 0f;
	private int _count = 0;
	// 残影缩放衰减
	private boolean _scaleDecreasing = true;
	// 残影旋转效果
	private boolean _rotationEnabled = false;
	private boolean _reversePlay = false;
	// 缩放步长
	private float _scaleStep = 0.05f;
	// 旋转步长
	private float _rotationStep = 5f;
	// 缓动类型
	private Easing _easeType = Easing.TIME_LINEAR;

	public AfterImageEffect(String path, float startX, float startY, int count) {
		this(Config.TRIGHT, path, startX, startY, count);
	}

	public AfterImageEffect(LTexture tex, float startX, float startY, int count) {
		this(Config.TRIGHT, tex, startX, startY, count);
	}

	public AfterImageEffect(int dir, String path, float startX, float startY, int count) {
		this(dir, LTextures.loadTexture(path), startX, startY, count);
	}

	public AfterImageEffect(int dir, String path, float startX, float startY, float startW, float startH, int count) {
		this(dir, LTextures.loadTexture(path), startX, startY, startW, startH, count);
	}

	public AfterImageEffect(int dir, LTexture tex, float startX, float startY, int count) {
		this(dir, tex, startX, startY, tex.getWidth(), tex.getHeight(), count);
	}

	public AfterImageEffect(int dir, LTexture tex, float startX, float startY, float startW, float startH, int count) {
		this(dir, tex, 0f, 0f, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), startX, startY, startW,
				startH, count);
	}

	public AfterImageEffect(int dir, LTexture tex, float displayX, float displayY, float displayW, float displayH,
			float startX, float startY, float startW, float startH, int count) {
		this.setRepaint(true);
		this.setLocation(displayX, displayY);
		this.setSize(displayW, displayH);
		this.setTexture(tex);
		this.setInterval(0f);
		this.setCount(count);
		this.setStartLocation(startX, startY);
		this.setOrbitValue(MathUtils.min(startW, startH) / 6f);
		this.setColor(LColor.white);
		this._moveDir = dir;
		this._startWidth = startW;
		this._startHeight = startH;
		this._makeAutoEffect = true;
		this._hideFirstObject = true;
	}

	public boolean isMakeAutoEffect() {
		return this._makeAutoEffect;
	}

	public AfterImageEffect setMakeAutoEffect(boolean m) {
		this._makeAutoEffect = m;
		return this;
	}

	public AfterImageEffect setStartLocation(float x, float y) {
		setStartX(x);
		setStartY(y);
		return this;
	}

	public int getModeDirection() {
		return _moveDir;
	}

	public AfterImageEffect makeEffect() {
		createAfterObjects(_startX, _startY, _startWidth, _startHeight);
		return this;
	}

	@Override
	public AfterImageEffect setTexture(LTexture tex) {
		this._afterTexture = tex;
		return this;
	}

	@Override
	public AfterImageEffect setTexture(String path) {
		this._afterTexture = LTextures.loadTexture(path);
		return this;
	}

	@Override
	public LTexture getBitmap() {
		return this._afterTexture;
	}

	public AfterImageEffect setInterval(float i) {
		this._interval = MathUtils.max(i, 0f);
		return this;
	}

	public float getInterval() {
		return this._interval;
	}

	public AfterImageEffect setCount(int c) {
		this._count = MathUtils.max(c + 1, 2);
		return this;
	}

	public int getCount() {
		return _count - 1;
	}

	private void initAfterObjects() {
		if (_afterObjects == null) {
			_afterObjects = new TArray<>(_count);
			_inited = false;
		}
	}

	public AfterImageEffect clearAfterObjects() {
		if (_afterObjects != null) {
			_afterObjects.clear();
			_makeAutoEffect = true;
			_inited = false;
		}
		return this;
	}

	public AfterImageEffect addAfterObject(float x, float y) {
		return addAfterObject((LTexture) null, x, y);
	}

	public AfterImageEffect addAfterObject(String path, float x, float y) {
		return addAfterObject(LTextures.loadTexture(path), x, y);
	}

	public AfterImageEffect addAfterObject(LTexture tex, float x, float y) {
		initAfterObjects();
		_makeAutoEffect = false;
		AfterImageEffect.AfterObject after = new AfterImageEffect.AfterObject(tex, x, y,
				tex != null ? tex.getWidth() : _startWidth, tex != null ? tex.getHeight() : _startHeight, 1f);
		_afterObjects.add(after);
		_inited = false;
		return this;
	}

	public AfterImageEffect addAfterObject(float x, float y, float w, float h) {
		return addAfterObject((LTexture) null, x, y, w, h);
	}

	public AfterImageEffect addAfterObject(String path, float x, float y, float w, float h) {
		return addAfterObject(LTextures.loadTexture(path), x, y, w, h);
	}

	public AfterImageEffect addAfterObject(LTexture tex, float x, float y, float w, float h) {
		initAfterObjects();
		_makeAutoEffect = false;
		AfterImageEffect.AfterObject after = new AfterImageEffect.AfterObject(tex, x, y, w, h, 1f);
		_afterObjects.add(after);
		_inited = false;
		return this;
	}

	public AfterImageEffect createAfterObjects(float x, float y, float w, float h) {
		initAfterObjects();
		if (_afterObjects.size > 0) {
			clearAfterObjects();
		}

		final float totalCount = _count;

		for (int i = 0; i < _count; i++) {
			final AfterObject after = new AfterObject();
			after.width = w;
			after.height = h;
			float alphaProgress = getEaseProgress((float) i / totalCount);
			if (_alphaDecreasing) {
				after.alpha = MathUtils.clamp(1f - alphaProgress, _scaleStep, 1f);
			} else {
				after.alpha = MathUtils.clamp(alphaProgress, _scaleStep, 1f);
			}

			if (_scaleDecreasing) {
				float progress = (float) i / (_count - 1);
				float scale = 0.5f + progress * 0.5f;
				after.setScale(scale);
			}

			if (_rotationEnabled) {
				after.setRotation(i * _rotationStep);
			}

			float stepX = i * (w + _interval);
			float stepY = i * (h + _interval);

			switch (_moveDir) {
			case Config.UP:
				after.x = stepX;
				after.y = -stepY;
				updateOrbit(after, i, true, true);
				break;
			case Config.LEFT:
				after.x = -stepX;
				after.y = -stepY;
				updateOrbit(after, i, false, true);
				break;
			default:
			case Config.RIGHT:
				after.x = stepX;
				after.y = stepY;
				updateOrbit(after, i, false, true);
				break;
			case Config.DOWN:
				after.x = -stepX;
				after.y = stepY;
				updateOrbit(after, i, true, true);
				break;
			case Config.TUP:
				after.y = -stepY;
				updateOrbit(after, i, true, false);
				break;
			case Config.TLEFT:
				after.x = -stepX;
				updateOrbit(after, i, false, true);
				break;
			case Config.TRIGHT:
				after.x = stepX;
				updateOrbit(after, i, false, true);
				break;
			case Config.TDOWN:
				after.y = stepY;
				updateOrbit(after, i, true, false);
				break;
			}
			_afterObjects.add(after);
		}
		return this;
	}

	private void updateOrbit(AfterObject o, int idx, boolean c, boolean r) {
		switch (_moveOrbit) {
		case LINE:
			break;
		case CURVE:
			if (c) {
				if (!_moveOrbitReverse) {
					if (idx % 2 == 0) {
						o.x += _orbitValue;
					} else {
						o.x -= _orbitValue;
					}
				} else {
					if (idx % 2 != 0) {
						o.x += _orbitValue;
					} else {
						o.x -= _orbitValue;
					}
				}
			}
			if (r) {
				if (!_moveOrbitReverse) {
					if (idx % 2 == 0) {
						o.y += _orbitValue;
					} else {
						o.y -= _orbitValue;
					}
				} else {
					if (idx % 2 != 0) {
						o.y += _orbitValue;
					} else {
						o.y -= _orbitValue;
					}
				}
			}
			break;
		}
	}

	public Vector2f getAutoMoveDistance() {
		final Vector2f result = new Vector2f();
		for (int i = 0; i < _afterObjects.size; i++) {
			final AfterObject after = _afterObjects.get(i);
			switch (_moveDir) {
			case Config.UP:
				result.x += +(i * (after.width + _interval));
				result.y += -(i * (after.height + _interval));
				break;
			case Config.LEFT:
				result.x += -(i * (after.width + _interval));
				result.y += -(i * (after.height + _interval));
				break;
			default:
			case Config.RIGHT:
				result.x += +(i * (after.width + _interval));
				result.y += +(i * (after.height + _interval));
				break;
			case Config.DOWN:
				result.x += -(i * (after.width + _interval));
				result.y += +(i * (after.height + _interval));
				break;
			case Config.TUP:
				result.y += -(i * (after.height + _interval));
				break;
			case Config.TLEFT:
				result.x += -(i * (after.width + _interval));
				break;
			case Config.TRIGHT:
				result.x += +(i * (after.width + _interval));
				break;
			case Config.TDOWN:
				result.y += +(i * (after.height + _interval));
				break;
			}
		}
		return result;
	}

	public boolean isAlphaDecreasing() {
		return this._alphaDecreasing;
	}

	public AfterImageEffect setAlphaDecreasing(boolean d) {
		this._alphaDecreasing = d;
		return this;
	}

	public AfterImageEffect start() {
		if (_playing) {
			return this;
		}
		if (_afterTexture == null && _makeAutoEffect) {
			return this;
		}
		if (!_inited && _makeAutoEffect) {
			makeEffect();
			_inited = true;
		} else if (!_inited) {
			final int size = _afterObjects.size;
			if (size <= 0) {
				return this;
			}
			if (size > _count) {
				final float skip = (float) size / _count;
				final TArray<AfterImageEffect.AfterObject> temp = new TArray<>();
				int counter = 0;
				for (float i = 0; i < size; i += skip) {
					int idx = MathUtils.iceil(i);
					if (idx < size) {
						final AfterObject after = _afterObjects.get(idx);
						float alphaProgress = getEaseProgress((float) counter / _count);
						after.alpha = _alphaDecreasing ? MathUtils.clamp(1f - alphaProgress, 0.05f, 1f)
								: MathUtils.clamp(alphaProgress, 0.05f, 1f);
						temp.add(after);
						counter++;
					}
				}
				_afterObjects.clear();
				_afterObjects.addAll(temp);
			} else {
				_count = size;
				for (int i = 0; i < size; i++) {
					final AfterObject after = _afterObjects.get(i);
					float alphaProgress = getEaseProgress((float) i / size);
					after.alpha = _alphaDecreasing ? MathUtils.clamp(1f - alphaProgress, 0.05f, 1f)
							: MathUtils.clamp(alphaProgress, 0.05f, 1f);
				}
			}
			_backAfters.addAll(_afterObjects);
			_inited = true;
		}
		_playing = true;
		return this;
	}

	public AfterImageEffect restart() {
		if (!_playing) {
			clearData();
			start();
		}
		return this;
	}

	@Override
	public void onUpdate(long elapsedTime) {
		if (checkAutoRemove() || !_inited) {
			return;
		}
		if (_timer.action(elapsedTime)) {
			if (_reversePlay) {
				if (_indexNext > 0) {
					_indexNext--;
				} else {
					_displayCompleted = true;
				}
			} else {
				if (!_displayCompleted) {
					_indexNext = MathUtils.min(_indexNext + 1, _count - 1);
					if (_indexNext >= _count - 1) {
						_displayCompleted = true;
					}
				}
			}
			if (_displayCompleted && _afterObjects.size > 1 && !_reversePlay) {
				_afterObjects.shift();
				_indexNext = _afterObjects.size;
			} else if (_afterObjects.size == 1) {
				final AfterObject o = _afterObjects.last();
				if (o != null) {
					o.alpha = 1f;
					_indexNext = 1;
					if (!_looping) {
						_completed = true;
						_playing = false;
					} else {
						_playing = false;
						if (_makeAutoEffect) {
							_moveDir = Side.getOppositeSide(_moveDir);
							_moveOrbitReverse = !_moveOrbitReverse;
							setStartX(_startX + o.x);
							setStartY(_startY + o.y);
						}
						restart();
					}
				}
			}
		}
	}

	@Override
	public void repaint(GLEx g, float sx, float sy) {
		if (completedAfterBlackScreen(g, sx, sy)) {
			return;
		}
		if (!_inited) {
			return;
		}
		final float newX = drawX(sx);
		final float newY = drawY(sy);
		if (!_completed) {
			for (int i = 0; i < _indexNext; i++) {
				final AfterObject o = _afterObjects.get(i);
				if (o != null) {
					final LTexture shadowTexture = o.texture == null ? _afterTexture : o.texture;
					if (_indexNext <= 1 && !_hideFirstObject) {
						g.draw(shadowTexture, newX + (_startX + o.x), newY + (_startY + o.y), o.width, o.height,
								this._baseColor, this._scaleX, this._scaleY, this._flipX, this._flipY,
								this._objectRotation);
					} else {
						g.draw(shadowTexture, newX + (_startX + o.x), newY + (_startY + o.y), o.width, o.height,
								_tempColor.setColor(LColor.combine(_shadowColor.setAlpha(o.alpha), this._baseColor)),
								this._scaleX, this._scaleY, this._flipX, this._flipY, this._objectRotation);
					}
				}
			}
		} else {
			final AfterObject o = _afterObjects.last();
			if (o != null && !_alphaDecreasing) {
				final LTexture shadowTexture = o.texture == null ? _afterTexture : o.texture;
				g.draw(shadowTexture, newX + (_startX + o.x), newY + (_startY + o.y), o.width, o.height,
						this._baseColor, this._scaleX, this._scaleY, this._flipX, this._flipY, this._objectRotation);
			}
		}
	}

	public boolean isHideFirstObject() {
		return this._hideFirstObject;
	}

	public AfterImageEffect setHideFirstObject(boolean f) {
		this._hideFirstObject = f;
		return this;
	}

	public AfterImageEffect setShadowColor(LColor c) {
		_shadowColor.setColor(c);
		return this;
	}

	public LColor getShadowColor() {
		return _shadowColor;
	}

	public int getAfterObjectIndex() {
		return MathUtils.min(_indexNext, _afterObjects.size - 1);
	}

	public float getAfterObjectX() {
		final AfterObject o = _afterObjects.get(getAfterObjectIndex());
		return o != null ? drawX(o.x) : 0f;
	}

	public float getAfterObjectY() {
		final AfterObject o = _afterObjects.get(getAfterObjectIndex());
		return o != null ? drawY(o.y) : 0f;
	}

	public float getAfterObjectWidth() {
		final AfterObject o = _afterObjects.get(getAfterObjectIndex());
		return o != null ? o.width : 0f;
	}

	public float getAfterObjectHeight() {
		final AfterObject o = _afterObjects.get(getAfterObjectIndex());
		return o != null ? o.height : 0f;
	}

	public boolean containsAfterObject(float x, float y) {
		return containsAfterObject(x, y, 1f, 1f);
	}

	public boolean containsAfterObject(float x, float y, float w, float h) {
		final AfterObject o = _afterObjects.get(getAfterObjectIndex());
		if (o != null) {
			return CollisionHelper.contains(drawX(_startX + o.x), drawY(_startY + o.y), o.width, o.height, x, y, w, h);
		}
		return false;
	}

	public boolean intersectsAfterObject(float x, float y) {
		return intersectsAfterObject(x, y, 1f, 1f);
	}

	public boolean intersectsAfterObject(float x, float y, float w, float h) {
		final AfterObject o = _afterObjects.get(getAfterObjectIndex());
		if (o != null) {
			return CollisionHelper.intersects(drawX(_startX + o.x), drawY(_startY + o.y), o.width, o.height, x, y, w,
					h);
		}
		return false;
	}

	public float getStartX() {
		return _startX;
	}

	public AfterImageEffect setStartX(float sx) {
		this._startX = sx;
		return this;
	}

	public float getStartY() {
		return _startY;
	}

	public AfterImageEffect setStartY(float sy) {
		this._startY = sy;
		return this;
	}

	public float getStartWidth() {
		return _startWidth;
	}

	public AfterImageEffect setStartWidth(float sw) {
		this._startWidth = sw;
		return this;
	}

	public float getStartHeight() {
		return _startHeight;
	}

	public AfterImageEffect setStartHeight(float sh) {
		this._startHeight = sh;
		return this;
	}

	public boolean isPlaying() {
		return _playing;
	}

	public boolean isMoveLooping() {
		return _looping;
	}

	public AfterImageEffect setMoveLoop(boolean l) {
		this._looping = l;
		return this;
	}

	public AfterImageEffect clearData() {
		if (_backAfters.size > 0) {
			_afterObjects.clear();
			_afterObjects.addAll(_backAfters);
			_backAfters.clear();
		}
		this._indexNext = 0;
		this._completed = false;
		this._displayCompleted = false;
		this._inited = _playing = false;
		return this;
	}

	public float getOrbitValue() {
		return _orbitValue;
	}

	public AfterImageEffect setOrbitValue(float o) {
		this._orbitValue = MathUtils.max(o, 0f);
		return this;
	}

	public int getMoveOrbit() {
		return _moveOrbit;
	}

	public AfterImageEffect setMoveOrbit(int m) {
		this._moveOrbit = m;
		return this;
	}

	@Override
	public AfterImageEffect setAutoRemoved(boolean autoRemoved) {
		super.setAutoRemoved(autoRemoved);
		return this;
	}

	@Override
	public AfterImageEffect reset() {
		super.reset();
		this.clearData();
		this.start();
		return this;
	}

	private float getEaseProgress(float progress) {
		progress = MathUtils.clamp(progress, 0f, 1f);
		return _easeType.apply(progress);
	}

	public AfterImageEffect setScaleDecreasing(boolean enable) {
		this._scaleDecreasing = enable;
		return this;
	}

	public AfterImageEffect setScaleStep(float step) {
		this._scaleStep = MathUtils.clamp(step, 0f, 0.2f);
		return this;
	}

	/**
	 * 设置是否开启旋转残影效果
	 * 
	 * @param enable
	 * @return
	 */
	public AfterImageEffect setRotationEnabled(boolean enable) {
		this._rotationEnabled = enable;
		return this;
	}

	/**
	 * 设置旋转步长
	 * 
	 * @param step
	 * @return
	 */
	public AfterImageEffect setRotationStep(float step) {
		this._rotationStep = step;
		return this;
	}

	/**
	 * 设置缓动类型
	 * 
	 * @param easeType
	 * @return
	 */
	public AfterImageEffect setEaseType(Easing easeType) {
		this._easeType = easeType;
		return this;
	}

	/**
	 * 设置反向播放
	 * 
	 * @param reverse
	 * @return
	 */
	public AfterImageEffect setReversePlay(boolean reverse) {
		this._reversePlay = reverse;
		this._displayCompleted = false;
		this._indexNext = reverse ? _count - 1 : 0;
		return this;
	}

	@Override
	protected void _onDestroy() {
		super._onDestroy();
		_backAfters.clear();
		if (_afterTexture != null) {
			_afterTexture.close();
			_afterTexture = null;
		}
	}

}
