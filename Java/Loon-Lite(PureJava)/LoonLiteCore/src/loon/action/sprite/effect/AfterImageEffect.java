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
import loon.Screen;
import loon.action.collision.CollisionHelper;
import loon.action.map.Config;
import loon.action.map.Side;
import loon.action.sprite.IEntity;
import loon.action.sprite.ISprite;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.Easing;
import loon.utils.MathUtils;
import loon.utils.TArray;
import loon.utils.timer.Duration;

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

	/**
	 * 残影类监听器
	 */
	public static interface AfterImageListener {

		void onAfterImageStart(ISprite target);

		void onAfterImageUpdate(ISprite target, AfterImageEffect.AfterObject obj);

		void onAfterImageEnd(ISprite target);

		void onSpriteToAfterImage(ISprite target, AfterImageEffect.AfterObject obj);

		void onAfterImageToSprite(ISprite target, AfterImageEffect.AfterObject obj);
	}

	/**
	 * 使用精灵快速生成残影效果
	 * 
	 * @param dir
	 * @param sprite
	 * @param interval
	 * @param count
	 * @return
	 */
	public static AfterImageEffect withSprite(int dir, ISprite sprite, int interval, int count) {
		AfterImageEffect effect = new AfterImageEffect(dir, sprite, count);
		effect.setInterval(interval);
		return effect;
	}

	/**
	 * 使用纹理快速生成残影效果
	 * 
	 * @param dir
	 * @param tex
	 * @param x
	 * @param y
	 * @param interval
	 * @param count
	 * @return
	 */
	public static AfterImageEffect withTexture(int dir, LTexture tex, float x, float y, int interval, int count) {
		AfterImageEffect effect = new AfterImageEffect(dir, tex, x, y, count);
		effect.setInterval(interval);
		return effect;
	}

	/**
	 * 使用图片路径快速生成残影效果
	 * 
	 * @param dir
	 * @param path
	 * @param x
	 * @param y
	 * @param interval
	 * @param count
	 * @return
	 */
	public static AfterImageEffect withTexturePath(int dir, String path, float x, float y, int interval, int count) {
		return withTexture(dir, LTextures.loadTexture(path), x, y, interval, count);
	}

	/**
	 * 使用精灵残影并直接添加到屏幕
	 * 
	 * @param dir
	 * @param sprite
	 * @param interval
	 * @param count
	 * @param scene
	 * @return
	 */
	public static AfterImageEffect attachToScene(int dir, ISprite sprite, int interval, int count, Screen scene) {
		AfterImageEffect effect = withSprite(dir, sprite, interval, count);
		scene.add(effect);
		return effect;
	}

	/**
	 * 使用目标精灵的当前位置和纹理，快速生成残影
	 * 
	 * @param target
	 * @param dir
	 * @param count
	 * @return
	 */
	public static AfterImageEffect ofSprite(int dir, ISprite target, int count) {
		return new AfterImageEffect(dir, target, count);
	}

	/**
	 * 使用纹理生成残影
	 * 
	 * @param dir
	 * @param tex
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param count
	 * @return
	 */
	public static AfterImageEffect ofTexture(int dir, LTexture tex, float x, float y, float w, float h, int count) {
		return new AfterImageEffect(dir, null, tex, x, y, w, h, count);
	}

	/**
	 * 使用地址生成残影
	 * 
	 * @param dir
	 * @param path
	 * @param x
	 * @param y
	 * @param count
	 * @return
	 */
	public static AfterImageEffect ofPath(int dir, String path, float x, float y, int count) {
		return new AfterImageEffect(dir, null, path, x, y, count);
	}

	// 移动轨迹类型
	public final static int LINE = 0;
	public final static int CURVE = 1;
	public final static int SINE = 2;
	public final static int PARABOLA = 3;
	public final static int CIRCLE = 4;
	public final static int SPIRAL = 5;
	public final static int RANDOM = 6;
	public final static int LISS = 7;
	public final static int JUMP = 8;
	public final static int WAVE = 9;
	public final static int TRIANGLE = 10;
	public final static int FALLING = 11;
	public final static int METEOR = 12;

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

	private final TArray<AfterImageEffect.AfterObject> _backAfters = new TArray<AfterImageEffect.AfterObject>();
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
	// 残影数量
	private int _count = 0;

	private float _fadeInTime = 0f;
	private float _fadeOutTime = 0f;
	private float _currentFadeAlpha = 1f;
	private float _trajectorySpeed = 1f;
	private float _parabolaHeight = 50f;
	private float _sineAmplitude = 30f;
	private float _randomAlphaRange = 0.1f;
	private float _randomScaleRange = 0.1f;
	private float _randomRotationRange = 5f;

	private boolean _randomEnabled = false;

	private boolean _paused = false;
	// 需要绑定的精灵
	private ISprite _targetSprite;
	// 同步动态坐标到精灵上去
	private boolean _syncToSprite;
	// 残影同步监听
	private AfterImageListener _listener;
	// 自动切换残影特效与精灵的显示关系
	private boolean _autoSyncSpriteSwitch = true;
	// 移动目标坐标(也就是最终显示位置，到哪里停止。这个参数需要会结合移动方向生效。
	// 若设定了此参数，但目的坐标是移动的不同方向，则会产生瞬移，而没有动画，因为loon
	// 中移动轨迹残影是按照移动方向设置预生成的)
	private float _targetX = -1f;
	private float _targetY = -1f;
	// 是否开启强制坐标限制
	private boolean _useTarget = false;

	public AfterImageEffect(ISprite target, String path, float startX, float startY, int count) {
		this(Config.TRIGHT, target, path, startX, startY, count);
	}

	public AfterImageEffect(ISprite target, LTexture tex, float startX, float startY, int count) {
		this(Config.TRIGHT, target, tex, startX, startY, count);
	}

	public AfterImageEffect(ISprite target, float startX, float startY, int count) {
		this(Config.TRIGHT, target, target != null ? target.getBitmap() : null, startX, startY,
				target != null ? target.getWidth() : 0f, target != null ? target.getHeight() : 0f, count);
	}

	public AfterImageEffect(int dir, ISprite target, float startX, float startY, int count) {
		this(dir, target, target != null ? target.getBitmap() : null, startX, startY,
				target != null ? target.getWidth() : 0f, target != null ? target.getHeight() : 0f, count);
	}

	public AfterImageEffect(LTexture tex, float startX, float startY, int count) {
		this(Config.TRIGHT, null, tex, startX, startY, tex != null ? tex.getWidth() : 0f,
				tex != null ? tex.getHeight() : 0f, count);
	}

	public AfterImageEffect(int dir, LTexture tex, float startX, float startY, int count) {
		this(dir, null, tex, startX, startY, tex != null ? tex.getWidth() : 0f, tex != null ? tex.getHeight() : 0f,
				count);
	}

	public AfterImageEffect(ISprite target, int count) {
		this(Config.TRIGHT, target, target != null ? target.getBitmap() : null, target != null ? target.getX() : 0f,
				target != null ? target.getY() : 0f, target != null ? target.getWidth() : 0f,
				target != null ? target.getHeight() : 0f, count);
	}

	public AfterImageEffect(int dir, ISprite target, int count) {
		this(dir, target, target != null ? target.getBitmap() : null, target != null ? target.getX() : 0f,
				target != null ? target.getY() : 0f, target != null ? target.getWidth() : 0f,
				target != null ? target.getHeight() : 0f, count);
	}

	public AfterImageEffect(int dir, String path, float startX, float startY, float startW, float startH, int count) {
		this(dir, null, LTextures.loadTexture(path), startX, startY, startW, startH, count);
	}

	public AfterImageEffect(String path, float startX, float startY, int count) {
		this(Config.TRIGHT, null, path, startX, startY, count);
	}

	public AfterImageEffect(int dir, String path, float startX, float startY, int count) {
		this(dir, null, path, startX, startY, count);
	}

	public AfterImageEffect(int dir, ISprite target, String path, float startX, float startY, int count) {
		this(dir, target, LTextures.loadTexture(path), startX, startY, count);
	}

	public AfterImageEffect(int dir, ISprite target, String path, float startX, float startY, float startW,
			float startH, int count) {
		this(dir, target, LTextures.loadTexture(path), startX, startY, startW, startH, count);
	}

	public AfterImageEffect(int dir, ISprite target, LTexture tex, float startX, float startY, int count) {
		this(dir, target, tex, startX, startY, tex.getWidth(), tex.getHeight(), count);
	}

	public AfterImageEffect(int dir, ISprite target, LTexture tex, float startX, float startY, float startW,
			float startH, int count) {
		this(dir, target, tex, 0f, 0f, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), startX, startY,
				startW, startH, count);
	}

	public AfterImageEffect(int dir, ISprite target, float startX, float startY, float startW, float startH,
			int count) {
		this(dir, target, target != null ? target.getBitmap() : null, 0f, 0f, LSystem.viewSize.getWidth(),
				LSystem.viewSize.getHeight(), startX, startY, startW, startH, count);
	}

	public AfterImageEffect(int dir, ISprite target, LTexture tex, float displayX, float displayY, float displayW,
			float displayH, float startX, float startY, float startW, float startH, int count) {
		this.setRepaint(true);
		this.setLocation(displayX, displayY);
		this.setSize(displayW, displayH);
		this.setTexture(tex);
		this.setInterval(0f);
		this.setCount(count);
		this.setStartLocation(startX, startY);
		this.setStartSize(startW, startH);
		this.setOrbitValue(MathUtils.min(startW, startH) / 6f);
		this.setColor(LColor.white);
		this.bindTargetSprite(target);
		this._moveDir = dir;
		this._makeAutoEffect = true;
		this._hideFirstObject = true;
	}

	/**
	 * 角色最多移动到
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public AfterImageEffect setTarget(float x, float y) {
		this._targetX = x;
		this._targetY = y;
		this._useTarget = true;
		return this;
	}

	/**
	 * 清空限制坐标
	 * 
	 * @return
	 */
	public AfterImageEffect clearTarget() {
		this._useTarget = false;
		this._targetX = -1f;
		this._targetY = -1f;
		return this;
	}

	/**
	 * 如果启用同步，则把残影位置写回目标精灵
	 * 
	 * @param obj
	 * @param idx
	 */
	public void updateOrbitAndSync(AfterObject obj) {
		if (_syncToSprite && _targetSprite != null && obj != null) {
			_targetSprite.setLocation(_startX + obj.getX(), _startY + obj.getY());
			_targetSprite.setRotation(obj.getRotation());
		}
	}

	/**
	 * 绑定精灵位置与图像到残像特效
	 * 
	 * @param spr
	 */
	public void bindTargetSprite(ISprite spr) {
		if (spr != null) {
			setStartLocation(spr.getX(), spr.getY());
			setStartSize(spr.getWidth(), spr.getHeight());
			if (spr instanceof IEntity) {
				setPivot(((IEntity) spr).getPivotX(), ((IEntity) spr).getPivotY());
			}
			setRotation(spr.getRotation());
			setScale(spr.getScaleX(), spr.getScaleX());
			setOffset(spr.getOffsetX(), spr.getOffsetY());
			_targetSprite = spr;
			if (_targetSprite.getBitmap() != null) {
				setTexture(_targetSprite.getBitmap());
			}
			_syncToSprite = true;
		}
	}

	public boolean isSyncToSprite() {
		return _syncToSprite;
	}

	public AfterImageEffect setSyncToSprite(boolean ss) {
		_syncToSprite = ss;
		return this;
	}

	public AfterImageEffect setAutoSyncSpriteSwitch(boolean auto) {
		this._autoSyncSpriteSwitch = auto;
		return this;
	}

	public boolean isAutoSyncSpriteSwitch() {
		return _autoSyncSpriteSwitch;
	}

	public ISprite getTargetSprite() {
		return _targetSprite;
	}

	public void setAfterImageListener(AfterImageListener l) {
		_listener = l;
	}

	public AfterImageListener getAfterImageListener() {
		return _listener;
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

	public AfterImageEffect setStartSize(float w, float h) {
		setStartWidth(w);
		setStartHeight(h);
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
			_afterObjects = new TArray<AfterImageEffect.AfterObject>(_count);
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

	/**
	 * 创建一组对象，用于显示不同的残影形态
	 * 
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @return
	 */
	public AfterImageEffect createAfterObjects(float x, float y, float w, float h) {
		initAfterObjects();
		if (_afterObjects.size > 0) {
			clearAfterObjects();
		}
		// 计算最大可生成数量
		int maxCount = _count;
		// 若设定了移动目标，则强制限制步长
		if (_useTarget && (_targetX != -1f || _targetY != -1f)) {
			float dx = MathUtils.abs(_targetX - x);
			float dy = MathUtils.abs(_targetY - y);
			float stepX = (w + _interval) * _trajectorySpeed;
			float stepY = (h + _interval) * _trajectorySpeed;

			int maxX = stepX > 0 ? (int) (dx / stepX) + 1 : _count;
			int maxY = stepY > 0 ? (int) (dy / stepY) + 1 : _count;
			maxCount = MathUtils.min(_count, MathUtils.max(maxX, maxY));
		}

		final int actualCount = maxCount;

		for (int i = 0; i < actualCount; i++) {
			final AfterObject after = new AfterObject();
			after.width = w;
			after.height = h;

			// 使用实际数量计算透明度进度
			float alphaProgress = getEaseProgress((float) i / actualCount);
			float baseAlpha = _alphaDecreasing ? (1f - alphaProgress) : alphaProgress;
			if (_randomEnabled) {
				baseAlpha += MathUtils.random(-_randomAlphaRange, _randomAlphaRange);
			}
			after.alpha = MathUtils.clamp(baseAlpha, 0.05f, 1f);
			if (_scaleDecreasing) {
				float scaleProgress = getEaseProgress(1f - (float) i / (actualCount - 1));
				float baseScale = 1f - scaleProgress * _scaleStep * 10;
				if (_randomEnabled) {
					baseScale += MathUtils.random(-_randomScaleRange, _randomScaleRange);
				}
				after.setScale(baseScale);
			} else {
				float scaleProgress = getEaseProgress((float) i / (actualCount - 1));
				float baseScale = 1f + scaleProgress * _scaleStep * 10;
				if (_randomEnabled) {
					baseScale += MathUtils.random(-_randomScaleRange, _randomScaleRange);
				}
				after.setScale(baseScale);
			}
			if (_rotationEnabled) {
				float baseRot = i * _rotationStep;
				if (_randomEnabled) {
					baseRot += MathUtils.random(-_randomRotationRange, _randomRotationRange);
				}
				after.setRotation(baseRot);
			}
			float stepX = i * (w + _interval) * _trajectorySpeed;
			float stepY = i * (h + _interval) * _trajectorySpeed;
			// 根据方向设置坐标
			switch (_moveDir) {
			case Config.UP:
				after.x = stepX;
				after.y = -stepY;
				break;
			case Config.LEFT:
				after.x = -stepX;
				after.y = -stepY;
				break;
			case Config.RIGHT:
				after.x = stepX;
				after.y = stepY;
				break;
			case Config.DOWN:
				after.x = -stepX;
				after.y = stepY;
				break;
			case Config.TUP:
				after.y = -stepY;
				break;
			case Config.TLEFT:
				after.x = -stepX;
				break;
			case Config.TRIGHT:
				after.x = stepX;
				break;
			case Config.TDOWN:
				after.y = stepY;
				break;
			}
			updateOrbit(after, i, true, true);
			if (_useTarget && (_targetX != -1f || _targetY != -1f)) {
				float curX = x + after.x;
				float curY = y + after.y;
				boolean reached = false;
				switch (_moveDir) {
				case Config.TRIGHT:
					if (curX >= _targetX) {
						reached = true;
					}
					break;
				case Config.TLEFT:
					if (curX <= _targetX) {
						reached = true;
					}
					break;
				case Config.TDOWN:
					if (curY >= _targetY) {
						reached = true;
					}
					break;
				case Config.TUP:
					if (curY <= _targetY) {
						reached = true;
					}
					break;
				case Config.RIGHT:
					if (curX >= _targetX && curY >= _targetY) {
						reached = true;
					}
					break;
				case Config.UP:
					if (curX >= _targetX && curY <= _targetY) {
						reached = true;
					}
					break;
				case Config.DOWN:
					if (curX <= _targetX && curY >= _targetY) {
						reached = true;
					}
					break;
				case Config.LEFT:
					if (curX <= _targetX && curY <= _targetY) {
						reached = true;
					}
					break;
				}
				if (reached) {
					after.setPos(_targetX - x, _targetY - y);
					_afterObjects.add(after);
					// 停止生成超越目标坐标的残像对象
					break;
				}
			}
			_afterObjects.add(after);
		}
		return this;
	}

	private void updateOrbit(AfterObject o, int idx, boolean c, boolean r) {
		final float progress = (float) idx / _count;
		switch (_moveOrbit) {
		case LINE:
			break;
		case CURVE:
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
		case SINE:
			float sine = MathUtils.sin(progress * MathUtils.TWO_PI) * _sineAmplitude;
			if (c) {
				o.x += sine;
			}
			if (r) {
				o.y += sine;
			}
			break;
		case PARABOLA:
			float parabola = (progress - 0.5f) * (progress - 0.5f) * _parabolaHeight * 4;
			parabola *= _moveOrbitReverse ? -1 : 1;
			if (c) {
				o.x += parabola;
			}
			if (r) {
				o.y += parabola;
			}
			break;
		case CIRCLE:
			float angle = progress * MathUtils.TWO_PI;
			float radius = _orbitValue;
			if (c) {
				o.x += MathUtils.cos(angle) * radius;
			}
			if (r) {
				o.y += MathUtils.sin(angle) * radius;
			}
			break;
		case SPIRAL:
			float spiralAngle = progress * MathUtils.PI_4;
			float spiralRadius = progress * _orbitValue * 2;
			if (c) {
				o.x += MathUtils.cos(spiralAngle) * spiralRadius;
			}
			if (r) {
				o.y += MathUtils.sin(spiralAngle) * spiralRadius;
			}
			break;
		case RANDOM:
			if (c) {
				o.x += (MathUtils.random() - 0.5f) * _orbitValue * 2;
			}
			if (r) {
				o.y += (MathUtils.random() - 0.5f) * _orbitValue * 2;
			}
			break;
		case LISS:
			float lissX = MathUtils.sin(progress * MathUtils.PI * 2 * 3);
			float lissY = MathUtils.sin(progress * MathUtils.PI * 2 * 2 + MathUtils.PI / 2);
			if (c) {
				o.x += lissX * _orbitValue;
			}
			if (r) {
				o.y += lissY * _orbitValue;
			}
			break;
		case JUMP:
			float jump = (idx % 2 == 0 ? 1 : -1) * _orbitValue * 5;
			if (c) {
				o.x += jump;
			}
			if (r) {
				o.y += jump;
			}
			break;
		case WAVE:
			float wave = MathUtils.sin(progress * MathUtils.PI * 6) * _orbitValue * 3;
			if (c) {
				o.x += wave;
			}
			if (r) {
				o.y += wave;
			}
			break;
		case TRIANGLE:
			float triangle = (progress % 0.5f < 0.25f ? 1 : -1) * _orbitValue * 4;
			if (c) {
				o.x += triangle;
			}
			if (r) {
				o.y += triangle;
			}
			break;
		case FALLING:
			if (progress > 0.5f) {
				float burst = (progress - 0.5f) * _orbitValue * 10;
				if (c) {
					o.x += burst;
				}
				if (r) {
					o.y += burst;
				}
			}
			break;
		case METEOR:
			int step = idx / (_count / 5);
			float stairOffset = step * _orbitValue * 6;
			if (c) {
				o.x += stairOffset;
			}
			if (r) {
				o.y += stairOffset;
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
				final TArray<AfterImageEffect.AfterObject> temp = new TArray<AfterImageEffect.AfterObject>();
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
		_currentFadeAlpha = 1f;
		// 开始播放时暂停目标精灵渲染
		if (_syncToSprite && _targetSprite != null) {
			if (_afterObjects.size > 0 && _listener != null) {
				_listener.onSpriteToAfterImage(_targetSprite, _afterObjects.first());
			}
			if (_autoSyncSpriteSwitch) {
				// 实际精灵与残像精灵显示逻辑正相反
				_targetSprite.setVisible(false);
				setVisible(true);
			}
		}
		// 监听残影播放开始事件
		if (_listener != null) {
			_listener.onAfterImageStart(_targetSprite);
		}
		return this;
	}

	public AfterImageEffect restart() {
		if (!_playing) {
			if (_inited) {
				clearData();
				bindTargetSprite(_targetSprite);
			}
			start();
		}
		return this;
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

			for (int i = 0; i < _indexNext && i < _afterObjects.size; i++) {
				final AfterObject o = _afterObjects.get(i);
				if (o != null) {
					final LTexture shadowTexture = o.texture == null ? _afterTexture : o.texture;
					if (_indexNext <= 1 && !_hideFirstObject) {
						g.draw(shadowTexture, newX + (_startX + o.x), newY + (_startY + o.y), o.width, o.height,
								this._baseColor, this._scaleX, this._scaleY, this._flipX, this._flipY,
								this._objectRotation);
					} else {
						g.draw(shadowTexture, newX + (_startX + o.x), newY + (_startY + o.y), o.width, o.height,
								_tempColor.setColor(LColor.combine(_shadowColor.setAlpha(o.alpha * _currentFadeAlpha),
										this._baseColor)),
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

	@Override
	public void onUpdate(long elapsedTime) {
		if (checkAutoRemove() || !_inited || _paused) {
			return;
		}
		updateFadeAlpha(elapsedTime);
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
			AfterObject o = null;
			if (_displayCompleted && _afterObjects.size > 1 && !_reversePlay) {
				o = _afterObjects.shift();
				_indexNext = _afterObjects.size;
			} else if (_afterObjects.size == 1) {
				o = _afterObjects.last();
				if (o != null) {
					o.alpha = 1f;
					_indexNext = 1;
					if (!_looping) {
						_completed = true;
						_playing = false;
						// 残影结束时恢复目标精灵渲染并停止同步
						if (_syncToSprite && _targetSprite != null) {
							updateOrbitAndSync(o);
							if (_listener != null) {
								_listener.onAfterImageToSprite(_targetSprite, o);
							}
							if (_autoSyncSpriteSwitch) {
								// 实际精灵与残像精灵显示逻辑取反
								setVisible(false);
								_targetSprite.setVisible(true);
							}
						}
						_syncToSprite = false;
						if (_listener != null) {
							_listener.onAfterImageEnd(_targetSprite);
						}
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
			if (o != null)
				if (_syncToSprite) {
					updateOrbitAndSync(o);
					if (_listener != null) {
						_listener.onAfterImageUpdate(_targetSprite, o);
					}
				}
		}
	}

	private void updateFadeAlpha(long elapsedTime) {
		float delta = Duration.toS(elapsedTime);
		if (_currentFadeAlpha < 1f && _fadeInTime > 0) {
			_currentFadeAlpha = MathUtils.clamp(_currentFadeAlpha + delta / _fadeInTime, 0f, 1f);
		}
		if (_completed && _fadeOutTime > 0) {
			_currentFadeAlpha = MathUtils.clamp(_currentFadeAlpha - delta / _fadeOutTime, 0f, 1f);
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
		this._currentFadeAlpha = 1f;
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

	public AfterImageEffect setRandomEnabled(boolean enable) {
		this._randomEnabled = enable;
		return this;
	}

	public AfterImageEffect setFadeTime(float fadeIn, float fadeOut) {
		this._fadeInTime = fadeIn;
		this._fadeOutTime = fadeOut;
		return this;
	}

	@Override
	public AfterImageEffect pause() {
		super.pause();
		this._paused = true;
		return this;
	}

	@Override
	public AfterImageEffect resume() {
		super.resume();
		this._paused = false;
		return this;
	}

	public AfterImageEffect setTrajectorySpeed(float speed) {
		this._trajectorySpeed = MathUtils.max(speed, 0.1f);
		return this;
	}

	public AfterImageEffect setRandomRange(float alpha, float scale, float rotation) {
		this._randomAlphaRange = alpha;
		this._randomScaleRange = scale;
		this._randomRotationRange = rotation;
		return this;
	}

	@Override
	protected void _onDestroy() {
		super._onDestroy();
		_backAfters.clear();
		if (!_syncToSprite) {
			if (_afterTexture != null) {
				_afterTexture.close();
				_afterTexture = null;
			}
		}
	}

}
