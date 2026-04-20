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
package loon.action.collision;

import loon.LRelease;
import loon.LSystem;
import loon.action.ActionBind;
import loon.geom.RectBox;
import loon.geom.Shape;
import loon.geom.Vector2f;
import loon.utils.Easing.EasingMode;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;
import loon.utils.timer.Duration;
import loon.utils.timer.EaseTimer;

/**
 * 简单的重力控制器（主要用途是模拟特殊重力环境，有物理特性，但不是物理引擎，里面有很多反重力方式）,使用时需要绑定Gravity
 */
public class GravityHandler implements LRelease {

	public static interface GravityUpdate {
		public void action(Gravity g, float x, float y);
	}

	// 异常重力状态枚举
	public static enum ChaosGravityState {
		// 正常重力
		NORMAL,
		// 漂浮失重
		FLOAT,
		// 重力过大无法移动
		LOCKED
	}

	// 引力源（黑洞）
	public static class GravitySource {
		public float x, y;
		public float pullForce;
		public float radius;

		public GravitySource(float x, float y, float pullForce, float radius) {
			this.x = x;
			this.y = y;
			this.pullForce = pullForce;
			this.radius = radius;
		}
	}

	private EaseTimer _easeTimer;
	private EasingMode _easingMode;
	private CollisionWorld _collisionWorld;
	protected CollisionFilter worldCollisionFilter;
	private float _objectMaxSpeed;
	private ObjectMap<ActionBind, Gravity> _gravityMap;
	private GravityUpdate _gravitylistener;
	private boolean _closed;
	private float _deviation;
	private boolean _collClearVelocity;
	private float _collScale = 1f;
	private float _bindWidth;
	private float _bindHeight;
	private float _lastX = -1f, _lastY = -1f;
	private float _bindX;
	private float _bindY;
	private float _velocityX, _velocityY;
	private float _gravityScale;
	// 行星重力倍数(简单来说，地球1, 贝吉塔星20)
	private float _planetGravityMultiplier;
	// 异常重力开关(随机重力，忽高忽低)
	private boolean _enableChaosGravity;
	// 当前异常重力状态
	private ChaosGravityState _currentChaosState;
	// 异常重力计时
	private float _chaosGravityTimer;
	// 异常状态切换间隔(秒)
	private float _chaosSwitchInterval;
	// 使用电磁力
	private boolean _enableMagnetism;
	private float _magneticDamping;

	boolean isBounded;
	boolean isGravityListener;
	boolean isEnabled;
	boolean syncActionBind;

	RectBox rectLimit;
	Gravity[] lazyObjects;
	TArray<Gravity> objects;
	TArray<Gravity> pendingAdd;
	TArray<Gravity> pendingRemove;
	private final TArray<Gravity> collisionObjects = new TArray<Gravity>();

	private boolean _enableFloatEffect;
	private float _floatAmplitude;
	private float _floatSpeed;
	private float _floatCycle;
	private float _globalAntiGravityAngle;
	private boolean _useAntiGravityAngle;

	// 黑洞位置
	private TArray<GravitySource> _gravitySources;
	private boolean _enableGlobalBlackHole;
	private float _globalBlackHoleX;
	private float _globalBlackHoleY;
	private float _globalBlackHoleForce;

	// 开启大风（依照风向改变移动方向）
	private boolean _enableOneWayWind;
	private float _windForceX;
	private float _windForceY;

	public GravityHandler() {
		this(EasingMode.Linear, 1f);
	}

	public GravityHandler(int w, int h) {
		this(w, h, EasingMode.Linear, 1f);
	}

	public GravityHandler(EasingMode ease) {
		this(ease, 1f);
	}

	public GravityHandler(EasingMode ease, float duration) {
		this(LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), ease, duration);
	}

	public GravityHandler(int w, int h, EasingMode ease) {
		this(w, h, ease, 1f);
	}

	public GravityHandler(float w, float h, EasingMode ease, float duration) {
		this(0f, 0f, w, h, ease, duration);
	}

	public GravityHandler(float x, float y, float w, float h, EasingMode ease, float duration) {
		this.setView(x, y, w, h);
		this._easingMode = ease;
		this._easeTimer = new EaseTimer(duration, _easingMode);
		this._gravityMap = new ObjectMap<ActionBind, Gravity>();
		this._gravityScale = _objectMaxSpeed = 1f;
		this._deviation = LSystem.DEFAULT_EASE_DELAY;
		this.objects = new TArray<Gravity>(10);
		this.pendingAdd = new TArray<Gravity>(10);
		this.pendingRemove = new TArray<Gravity>(10);
		this.lazyObjects = new Gravity[] {};
		this.syncActionBind = true;
		this.isEnabled = true;
		this._enableFloatEffect = false;
		this._floatAmplitude = 5f;
		this._floatSpeed = 1f;
		this._floatCycle = 0f;
		this._globalAntiGravityAngle = 270f;
		this._useAntiGravityAngle = false;
		// 默认为地球基准的1倍重力
		this._planetGravityMultiplier = 1.0f;
		this._enableChaosGravity = false;
		this._currentChaosState = ChaosGravityState.NORMAL;
		this._chaosGravityTimer = 0f;
		// 默认30秒切换一次重力异常状态
		this._chaosSwitchInterval = 30.0f;
		this._enableMagnetism = false;
		this._magneticDamping = 0.1f;
		// 特殊重力黑洞（默认关闭）
		this._gravitySources = new TArray<GravitySource>();
		this._enableGlobalBlackHole = false;
		// 特殊风力方向
		this._enableOneWayWind = false;
		this._windForceX = 0f;
		this._windForceY = 0f;
	}

	public void enableGlobalBlackHole(boolean enable, float x, float y, float force) {
		this._enableGlobalBlackHole = enable;
		this._globalBlackHoleX = x;
		this._globalBlackHoleY = y;
		this._globalBlackHoleForce = force;
	}

	public void addGravitySource(float x, float y, float force, float radius) {
		_gravitySources.add(new GravitySource(x, y, force, radius));
	}

	public void clearGravitySources() {
		_gravitySources.clear();
	}

	public void enableOneWayWind(boolean enable, float forceX, float forceY) {
		this._enableOneWayWind = enable;
		this._windForceX = forceX;
		this._windForceY = forceY;
	}

	public boolean isGravityRunning() {
		if (_closed) {
			return false;
		}
		if (objects != null) {
			for (int i = objects.size() - 1; i >= 0; i--) {
				Gravity g = objects.get(i);
				if (g != null && !g.enabled) {
					return true;
				}
			}
		}
		return false;
	}

	public GravityHandler setView(float x, float y, float w, float h) {
		if (_closed) {
			return this;
		}
		if (w > 0 && h > 0) {
			setBounded(true);
		} else {
			return this;
		}
		if (rectLimit == null) {
			this.rectLimit = new RectBox(x, y, w, h);
		} else {
			this.rectLimit.setBounds(x, y, w, h);
		}
		return this;
	}

	public GravityHandler setLimit(float w, float h) {
		return setView(rectLimit.getX(), rectLimit.getY(), w, h);
	}

	private float getGravity(Gravity g) {
		return g.g * _gravityScale * _planetGravityMultiplier;
	}

	protected boolean checkCollideSolidObjects(Gravity gravityObject, float delta, float gravity, float newX,
			float newY) {
		if (_closed) {
			return false;
		}
		if (gravityObject == null) {
			return false;
		}
		if (gravityObject != null && gravityObject.collideSolid) {
			final int size = objects.size;
			for (int i = size - 1; i > -1; i--) {
				Gravity g = objects.get(i);
				if (!gravityObject.isSolid && !g.isSolid || !g.enabled || g == gravityObject) {
					continue;
				}
				if (gravityObject.collided(g)) {
					gravityObject._collisionObject = g;
					gravityObject.updateCollisionDirection();
					_velocityX = gravityObject.velocityX;
					_velocityY = gravityObject.velocityY;
					if (gravityObject.isMovingDown()) {
						gravityObject.bounce = MathUtils.max(gravityObject.bounce, g.bounce);
						if (gravityObject.bounce > 0f) {
							gravityObject.bounce -= (gravityObject.bounce * delta);
							_velocityY = +gravityObject.bounce;
						} else {
							gravityObject.bounce = 0f;
						}
					}
					if (_velocityX > 1f) {
						gravityObject.bind.setX(gravityObject.getX() - _velocityX);
					}
					if (_velocityY > 1f) {
						gravityObject.bind.setY(gravityObject.getY() - _velocityY);
					}
					if (isGravityListener) {
						_gravitylistener.action(g, newX, newY);
					}
					gravityObject._collisioning = true;
					return true;
				}
			}
		}
		gravityObject._collisionObject = null;
		return (gravityObject._collisioning = false);
	}

	public void update(long elapsedTime) {
		if (_closed || !isEnabled) {
			return;
		}
		commits();
		if (objects == null || objects.size == 0) {
			return;
		}
		_easeTimer.action(elapsedTime);
		final float delta = MathUtils.clamp(MathUtils.max(Duration.toS(elapsedTime), LSystem.MIN_SECONE_SPEED_FIXED)
				* _easeTimer.getProgress() * _objectMaxSpeed, 0, 0.1f);
		final int size = objects.size;
		if (_enableFloatEffect) {
			_floatCycle += delta * _floatSpeed;
		}
		if (_enableChaosGravity) {
			_chaosGravityTimer += delta;
			if (_chaosGravityTimer >= _chaosSwitchInterval) {
				_chaosGravityTimer = 0f;
				int random = MathUtils.random(0, 2);
				switch (random) {
				case 0:
					_currentChaosState = ChaosGravityState.NORMAL;
					break;
				case 1:
					_currentChaosState = ChaosGravityState.FLOAT;
					break;
				case 2:
					_currentChaosState = ChaosGravityState.LOCKED;
					break;
				}
			}
		}
		for (int i = size - 1; i >= 0; i--) {
			Gravity g = objects.get(i);
			if (g.enabled && g.bind != null) {
				final float v = LSystem.getScaleFPS();
				float accelerationX = g.getAccelerationX() * v;
				float accelerationY = g.getAccelerationY() * v;
				final float angularVelocity = g.getAngularVelocity() * v;
				final float gravity = getGravity(g) * v;

				if (_enableChaosGravity && _currentChaosState == ChaosGravityState.LOCKED) {
					g.velocityX = 0;
					g.velocityY = 0;
					g.accelerationX = 0;
					g.accelerationY = 0;
					continue;
				}

				if (!g._collisioning) {
					g.initPosRotation();
					if (syncActionBind) {
						_bindX = g.bind.getX();
						_bindY = g.bind.getY();
						_bindWidth = g.bind.getWidth();
						_bindHeight = g.bind.getHeight();
						g.setArea(_bindX, _bindY, _bindWidth, _bindHeight);
					} else {
						_bindX = g.getX();
						_bindY = g.getY();
						_bindWidth = g.getWidth();
						_bindHeight = g.getHeight();
					}
					if (angularVelocity != 0) {
						final float rotate = g.bind.getRotation() + angularVelocity * delta;
						Gravity s = g.setRotation(rotate);
						_bindX = s.getX();
						_bindY = s.getY();
						_bindWidth = s.getWidth();
						_bindHeight = s.getHeight();
						g.bind.setRotation(rotate);
					}
				}
				if (_enableMagnetism) {
					for (int n = 0; n < objects.size; n++) {
						Gravity other = objects.get(n);
						if (other == g || !other.isMagnetic) {
							continue;
						}
						float mx = other.getX() - g.getX();
						float my = other.getY() - g.getY();
						float d = MathUtils.sqrt(mx * mx + my * my);
						if (d < 100 && d > 1) {
							float repel = 4000f / (d * d);
							accelerationX -= (mx / d) * repel * _magneticDamping;
							accelerationY -= (my / d) * repel * _magneticDamping;
						}
					}
				}

				if (_enableGlobalBlackHole) {
					float cx = g.getX() + g.getWidth() * 0.5f;
					float cy = g.getY() + g.getHeight() * 0.5f;
					float dx = _globalBlackHoleX - cx;
					float dy = _globalBlackHoleY - cy;
					float dist = MathUtils.sqrt(dx * dx + dy * dy);
					if (dist > 0.1f) {
						float force = _globalBlackHoleForce / dist;
						g.velocityX += (dx / dist) * force * delta;
						g.velocityY += (dy / dist) * force * delta;
					}
				}

				if (_gravitySources.size() > 0) {
					for (int s = 0; s < _gravitySources.size(); s++) {
						GravitySource source = _gravitySources.get(s);
						float cx = g.getX() + g.getWidth() * 0.5f;
						float cy = g.getY() + g.getHeight() * 0.5f;
						float dx = source.x - cx;
						float dy = source.y - cy;
						float dist = MathUtils.sqrt(dx * dx + dy * dy);

						if (dist < source.radius && dist > 0.1f) {
							float force = source.pullForce / dist;
							g.velocityX += (dx / dist) * force * delta;
							g.velocityY += (dy / dist) * force * delta;
						}
					}
				}

				// 特定风向改变矢量方向
				if (_enableOneWayWind) {
					g.velocityX += _windForceX * delta;
					g.velocityY += _windForceY * delta;
				}

				float velocityMultiplier = 1.0f / MathUtils.max(_planetGravityMultiplier, 0.1f);
				// 升空额外阻力
				float liftResistance = velocityMultiplier * 0.5f;

				if (accelerationX != 0 || accelerationY != 0) {
					g.velocityX += accelerationX * delta * velocityMultiplier;
					g.velocityY += accelerationY * delta * (accelerationY > 0 ? liftResistance : velocityMultiplier);
				}

				// 异常重力开启
				if (_enableChaosGravity && _currentChaosState == ChaosGravityState.FLOAT) {
					_useAntiGravityAngle = true;
				}

				if (_useAntiGravityAngle) {
					float rad = MathUtils.toRadians(_globalAntiGravityAngle);
					float antiGX = MathUtils.cos(rad) * MathUtils.abs(gravity);
					float antiGY = MathUtils.sin(rad) * MathUtils.abs(gravity);
					g.velocityX += antiGX * delta;
					g.velocityY += antiGY * delta;
				}

				_velocityX = g.velocityX;
				_velocityY = g.velocityY;

				if (_velocityX != 0 || _velocityY != 0) {
					_velocityX = _bindX + (_velocityX * delta);
					_velocityY = _bindY + (_velocityY * delta);

					if (gravity != 0 && g.velocityX != 0) {
						_velocityX += g.gadd;
					}
					if (gravity != 0 && g.velocityY != 0) {
						_velocityY += g.gadd;
					}
					if (gravity != 0) {
						g.gadd += gravity;
					}

					if (_enableFloatEffect) {
						float floatOffsetY = MathUtils.sin(_floatCycle + i) * _floatAmplitude;
						float floatOffsetX = MathUtils.cos(_floatCycle + i * 0.5f) * _floatAmplitude * 0.3f;
						_velocityX += floatOffsetX;
						_velocityY += floatOffsetY;
					}

					if (isBounded) {
						if (g.bounce != 0f) {
							final float limitWidth = rectLimit.getRight() - _bindWidth;
							final float limitHeight = rectLimit.getBottom() - _bindHeight;
							final boolean chageWidth = _bindX >= limitWidth;
							final boolean chageHeight = _bindY >= limitHeight;
							final float bounce = (g.bounce + gravity);
							if (chageWidth) {
								_bindX -= bounce;
								if (g.bounce > 0) {
									g.bounce -= (bounce * delta) + MathUtils.random(0f, bounce);
								} else if (g.bounce < 0) {
									g.bounce = 0;
									_bindX = limitWidth;
									g.limitX = true;
								}
							}
							if (chageHeight) {
								_bindY -= bounce;
								if (g.bounce > 0) {
									g.bounce -= (bounce * delta) + MathUtils.random(0f, bounce);
								} else if (g.bounce < 0) {
									g.bounce = 0;
									_bindY = limitHeight;
									g.limitY = true;
								}
							}
							if (chageWidth || chageHeight) {
								movePos(g, delta, gravity, _bindX, _bindY);
								return;
							}
						}
						final float limitWidth = rectLimit.getRight() - _bindWidth;
						final float limitHeight = rectLimit.getBottom() - _bindHeight;
						_velocityX = limitValue(g, _velocityX, limitWidth);
						_velocityY = limitValue(g, _velocityY, limitHeight);
					}
					movePos(g, delta, gravity, _velocityX, _velocityY);
				}
			}
		}

		if (_enableChaosGravity && _currentChaosState != ChaosGravityState.FLOAT) {
			_useAntiGravityAngle = false;
		}
	}

	private float limitValue(Gravity g, float value, float limit) {
		if (g.g < 0f) {
			if (value < 0f) {
				value = 0f;
				g.limitX = true;
			}
		}
		if (g.g > 0f) {
			if (limit < value) {
				value = limit;
				g.limitY = true;
			}
		}
		return value;
	}

	protected void commits() {
		if (_closed || !isEnabled) {
			return;
		}
		final int additionCount = pendingAdd.size;
		if (additionCount > 0) {
			for (int i = additionCount - 1; i >= 0; i--) {
				Gravity o = pendingAdd.get(i);
				objects.add(o);
			}
			pendingAdd.clear();
		}
		final int removalCount = pendingRemove.size;
		if (removalCount > 0) {
			for (int i = removalCount - 1; i >= 0; i--) {
				Gravity o = pendingRemove.get(i);
				objects.remove(o);
			}
			pendingRemove.clear();
		}
	}

	public Gravity[] getObjects() {
		if (_closed) {
			return null;
		}
		int size = objects.size;
		if (lazyObjects == null || lazyObjects.length != size) {
			lazyObjects = new Gravity[size];
		}
		for (int i = 0; i < size; i++) {
			lazyObjects[i] = objects.get(i);
		}
		return lazyObjects;
	}

	public int getCount() {
		if (_closed) {
			return 0;
		}
		return objects.size;
	}

	public int getConcreteCount() {
		if (_closed) {
			return 0;
		}
		return objects.size + pendingAdd.size - pendingRemove.size;
	}

	public Gravity get(int index) {
		if (_closed) {
			return null;
		}
		if (index > -1 && index < objects.size) {
			return objects.get(index);
		} else {
			return null;
		}
	}

	public boolean contains(ActionBind o) {
		if (_closed) {
			return false;
		}
		if (o == null) {
			return false;
		}
		return _gravityMap.containsKey(o);
	}

	public boolean contains(Gravity g) {
		if (_closed) {
			return false;
		}
		if (g == null) {
			return false;
		}
		if (pendingAdd.size > 0) {
			return pendingAdd.contains(g);
		}
		return objects.contains(g);
	}

	public boolean intersect(ActionBind a, ActionBind b) {
		if (_closed) {
			return false;
		}
		if (a == null || b == null) {
			return false;
		}
		return intersect(get(a), get(b));
	}

	public boolean intersect(Gravity a, Gravity b) {
		if (_closed) {
			return false;
		}
		if (a == null || b == null) {
			return false;
		}
		return a.getShape().intersects(b.getShape());
	}

	public Gravity intersects(float x, float y) {
		return intersects(x, y, 1f, 1f);
	}

	public Gravity intersects(float x, float y, float w, float h) {
		if (_closed) {
			return null;
		}
		int size = pendingAdd.size;
		for (int i = 0; i < size; i++) {
			Gravity g = pendingAdd.get(i);
			if (g.intersects(x, y, w, h)) {
				return g;
			}
		}
		size = objects.size;
		for (int i = 0; i < size; i++) {
			Gravity g = objects.get(i);
			if (g.intersects(x, y, w, h)) {
				return g;
			}
		}
		return null;
	}

	public boolean intersect(ActionBind g, float x, float y) {
		if (g == null) {
			return false;
		}
		return intersect(get(g), x, y);
	}

	public boolean intersect(Gravity g, float x, float y) {
		if (g == null) {
			return false;
		}
		return g.intersects(x, y);
	}

	public boolean intersect(ActionBind g, float x, float y, float width, float height) {
		if (g == null) {
			return false;
		}
		return intersect(get(g), x, y, width, height);
	}

	public boolean intersect(Gravity g, float x, float y, float width, float height) {
		if (g == null) {
			return false;
		}
		return g.intersects(x, y, width, height);
	}

	public Gravity contains(float x, float y) {
		return contains(x, y, 1f, 1f);
	}

	public Gravity contains(float x, float y, float w, float h) {
		int size = pendingAdd.size;
		for (int i = 0; i < size; i++) {
			Gravity g = pendingAdd.get(i);
			if (g.contains(x, y, w, h)) {
				return g;
			}
		}
		size = objects.size;
		for (int i = 0; i < size; i++) {
			Gravity g = objects.get(i);
			if (g.contains(x, y, w, h)) {
				return g;
			}
		}
		return null;
	}

	public Gravity collided(Shape s) {
		if (s == null) {
			return null;
		}
		int size = pendingAdd.size;
		for (int i = 0; i < size; i++) {
			Gravity g = pendingAdd.get(i);
			if (g.getShape().collided(s)) {
				return g;
			}
		}
		size = objects.size;
		for (int i = 0; i < size; i++) {
			Gravity g = objects.get(i);
			if (g.getShape().collided(s)) {
				return g;
			}
		}
		return null;
	}

	public Gravity collided(Gravity s) {
		if (s == null) {
			return null;
		}
		int size = pendingAdd.size;
		for (int i = 0; i < size; i++) {
			Gravity g = pendingAdd.get(i);
			if (g.collided(s.getShape())) {
				return g;
			}
		}
		size = objects.size;
		for (int i = 0; i < size; i++) {
			Gravity g = objects.get(i);
			if (g.collided(s.getShape())) {
				return g;
			}
		}
		return null;
	}

	public boolean contains(ActionBind g, float x, float y, float width, float height) {
		if (g == null) {
			return false;
		}
		return contains(get(g), x, y, width, height);
	}

	public boolean contains(Gravity g, float x, float y, float width, float height) {
		if (g == null) {
			return false;
		}
		return g.contains(x, y, width, height);
	}

	public boolean contains(ActionBind a, ActionBind b) {
		if (a == null || b == null) {
			return false;
		}
		return contains(get(a), get(b));
	}

	public boolean contains(Gravity a, Gravity b) {
		if (a == null || b == null) {
			return false;
		}
		return a.contains(b);
	}

	public Gravity add(ActionBind o, float vx, float vy) {
		return add(o, vx, vy, 0);
	}

	public Gravity add(ActionBind o, float vx, float vy, float ave) {
		return add(o, vx, vy, 0, 0, ave);
	}

	public Gravity add(ActionBind o, float vx, float vy, float ax, float ay, float ave) {
		if (_closed) {
			return null;
		}
		Gravity g = _gravityMap.get(o);
		if (g == null) {
			_gravityMap.put(o, (g = new Gravity(o)));
		}
		g.velocityX = vx;
		g.velocityY = vy;
		g.accelerationX = ax;
		g.accelerationY = ay;
		g.angularVelocity = ave;
		add(g);
		return g;
	}

	public Gravity add(ActionBind o) {
		if (_closed) {
			return null;
		}
		Gravity g = _gravityMap.get(o);
		if (g == null) {
			_gravityMap.put(o, (g = new Gravity(o)));
		}
		return add(g);
	}

	public Gravity get(ActionBind o) {
		if (_closed) {
			return null;
		}
		return _gravityMap.get(o);
	}

	public Gravity add(Gravity o) {
		if (_closed) {
			return null;
		}
		if (o == null) {
			return o;
		}
		if (!pendingAdd.contains(o)) {
			pendingAdd.add(o);
		}
		return o;
	}

	public Gravity remove(Gravity o) {
		if (_closed) {
			return null;
		}
		if (o == null) {
			return o;
		}
		o.enabled = false;
		pendingRemove.add(o);
		return o;
	}

	public void removeAll() {
		if (_closed) {
			return;
		}
		final int count = objects.size;
		for (int i = count - 1; i >= 0; i--) {
			Gravity g = objects.get(i);
			if (g != null) {
				g.enabled = false;
				pendingRemove.add(g);
			}
		}
		pendingAdd.clear();
	}

	public Gravity getObject(String name) {
		if (_closed) {
			return null;
		}
		commits();
		final int size = objects.size - 1;
		for (int i = size; i > -1; i--) {
			Gravity o = objects.get(i);
			if (o != null && o.name != null) {
				if (o.name.equals(name)) {
					return o;
				}
			}
		}
		return null;
	}

	public GravityResult query(ActionBind bind) {
		return query(bind, rectLimit);
	}

	public GravityResult query(ActionBind bind, RectBox pathBounds) {
		return query(bind, objects, pathBounds);
	}

	public GravityResult query(ActionBind bind, TArray<Gravity> otherObjects, RectBox pathBounds) {
		return query(bind, otherObjects, pathBounds, _collScale, _deviation, _collClearVelocity);
	}

	public GravityResult query(ActionBind bind, TArray<Gravity> otherObjects, RectBox pathBounds,
			boolean clearVelocity) {
		return query(bind, otherObjects, pathBounds, _collScale, _deviation, clearVelocity);
	}

	public GravityResult query(ActionBind bind, TArray<Gravity> otherObjects, RectBox pathBounds, float scale,
			float deviation, boolean clearVelocity) {
		if (_closed) {
			return null;
		}
		Gravity g = _gravityMap.get(bind);
		if (g != null) {
			return getCollisionBetweenObjects(g, otherObjects, pathBounds, scale, deviation, clearVelocity);
		}
		return new GravityResult();
	}

	public GravityResult query(Gravity target) {
		return getCollisionBetweenObjects(target);
	}

	public GravityResult query(Gravity target, RectBox pathBounds, boolean clearVelocity) {
		return getCollisionBetweenObjects(target, pathBounds, clearVelocity);
	}

	public GravityResult query(Gravity target, boolean clearVelocity) {
		return getCollisionBetweenObjects(target, objects, rectLimit, clearVelocity);
	}

	public GravityResult query(Gravity target, TArray<Gravity> otherObjects) {
		return getCollisionBetweenObjects(target, otherObjects);
	}

	public GravityResult query(Gravity target, TArray<Gravity> otherObjects, RectBox pathBounds) {
		return getCollisionBetweenObjects(target, otherObjects, pathBounds);
	}

	public GravityResult query(Gravity target, TArray<Gravity> otherObjects, RectBox pathBounds,
			boolean clearVelocity) {
		return getCollisionBetweenObjects(target, otherObjects, pathBounds, clearVelocity);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target) {
		return getCollisionBetweenObjects(target, objects, rectLimit, _collClearVelocity);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target, RectBox pathBounds, boolean clearVelocity) {
		return getCollisionBetweenObjects(target, objects, pathBounds, clearVelocity);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target, boolean clearVelocity) {
		return getCollisionBetweenObjects(target, objects, rectLimit, _collScale, _deviation, clearVelocity);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target, TArray<Gravity> otherObjects) {
		return getCollisionBetweenObjects(target, otherObjects, rectLimit);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target, TArray<Gravity> otherObjects, RectBox pathBounds) {
		return getCollisionBetweenObjects(target, otherObjects, pathBounds, _collClearVelocity);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target, TArray<Gravity> otherObjects, RectBox pathBounds,
			boolean clearVelocity) {
		return getCollisionBetweenObjects(target, otherObjects, pathBounds, _collScale, _deviation, clearVelocity);
	}

	public GravityResult getCollisionBetweenObjects(Gravity target, TArray<Gravity> otherObjects, RectBox pathBounds,
			float scale, float deviation, boolean clearVelocity) {
		if (_closed) {
			return null;
		}
		GravityResult result = new GravityResult();
		result.source = target;
		float remainingVX = target.velocityX * scale * _gravityScale;
		float remainingVY = target.velocityY * scale * _gravityScale;
		final RectBox rect = target.getRect();
		float positionX = rect.x;
		float positionY = rect.y;
		float halfWidth = rect.width * 0.5f;
		float halfHeight = rect.height * 0.5f;
		float moveAmountX = 0;
		float moveAmountY = 0;
		boolean lastIteration = false;

		if (MathUtils.abs(remainingVX) >= MathUtils.abs(remainingVY)) {
			if (MathUtils.abs(remainingVX) > MathUtils.abs(halfWidth)) {
				moveAmountX = halfWidth * remainingVX;
			} else {
				moveAmountX = remainingVX;
			}
			if (MathUtils.abs(remainingVY) > 0) {
				moveAmountY = remainingVY * (remainingVX == 0 ? 1 : MathUtils.abs(remainingVY / remainingVX));
			}
		} else {
			if (MathUtils.abs(remainingVY) > MathUtils.abs(halfHeight)) {
				moveAmountY = halfHeight * remainingVY;
			} else {
				moveAmountY = remainingVY;
			}
			if (MathUtils.abs(remainingVX) > 0) {
				moveAmountX = remainingVX * (remainingVY == 0 ? 1 : MathUtils.abs(remainingVX / remainingVY));
			}
		}
		result.normal.setZero();
		collisionObjects.clear();
		final int len = otherObjects.size;
		for (int i = 0; i < len; i++) {
			Gravity b = otherObjects.get(i);
			if (b != target && pathBounds.contains(b.getShape())) {
				collisionObjects.add(b);
			}
		}
		for (; result.steps <= 1024;) {
			result.steps++;
			final int size = collisionObjects.size;
			for (int i = 0; i < size; i++) {
				Gravity b = collisionObjects.get(i);
				final RectBox rb = b.getRect();
				if (rect.collided(rb)) {
					float overlapX = 0;
					float overlapY = 0;
					Vector2f normal = result.normal;
					normal.setZero();
					if (rect.x <= rb.x) {
						overlapX = (rect.x + rect.width) - rb.x;
						normal.x = -1;
					} else {
						overlapX = (rb.x + rb.width) - rect.x;
						normal.x = 1;
					}
					if (rect.y <= rb.y) {
						overlapY = (rect.y + rect.height) - rb.y;
						normal.y = -1;
					} else {
						overlapY = (rb.y + rb.height) - rect.y;
						normal.y = 1;
					}
					if (MathUtils.abs(overlapX) < MathUtils.abs(overlapY)) {
						normal.y = 0;
					}
					if (MathUtils.abs(overlapY) < MathUtils.abs(overlapX)) {
						normal.x = 0;
					}
					if (MathUtils.abs(overlapX) > rb.width && MathUtils.abs(overlapY) > rb.height) {
						continue;
					}
					if (normal.x == 1) {
						positionX = rb.x + rb.width;
						remainingVX = 0;
						if (clearVelocity) {
							target.velocityX = 0;
						}
						moveAmountX = 0;
					} else if (normal.x == -1) {
						positionX = rb.x - rect.width;
						remainingVX = 0;
						if (clearVelocity) {
							target.velocityX = 0;
						}
						moveAmountX = 0;
					}
					if (normal.y == 1) {
						positionY = rb.y + rb.height;
						remainingVY = 0;
						if (clearVelocity) {
							target.velocityY = 0;
						}
						moveAmountY = 0;
					} else if (normal.y == -1) {
						positionY = rb.y - rect.height;
						remainingVY = 0;
						if (clearVelocity) {
							target.velocityY = 0;
						}
						moveAmountY = 0;
					}
					result.targets.add(b);
					result.collided = true;
				}
				if (positionY + rect.height > rb.y && positionY < rb.y + rb.height) {
					final boolean posResultA = MathUtils.ifloor(positionX + rect.width) == rb.x && remainingVX > 0;
					final boolean posResultB = MathUtils.equal(MathUtils.ifloor(rb.x + rb.width), positionX)
							&& remainingVX < 0;
					if (posResultA || posResultB) {
						if (posResultA && result.normal.x == 0) {
							result.normal.x = -1;
						} else if (posResultB && result.normal.x == 0) {
							result.normal.x = 1;
						}
						remainingVX = 0;
						if (clearVelocity) {
							target.velocityX = 0;
						}
						moveAmountX = 0;
						result.targets.add(b);
						result.collided = true;
					}
				}
				if (positionX + rect.width > rb.x && positionX < rb.x + rb.width) {
					if ((MathUtils.equal(positionY + rect.height, rb.y) && remainingVY > 0)
							|| (MathUtils.equal(positionY, rb.y + rb.height) && remainingVY < 0)) {
						if ((MathUtils.equal(positionY + rect.height, rb.y) && remainingVY > 0)
								&& result.normal.y == 0) {
							result.normal.y = -1;
						} else if ((MathUtils.equal(positionY, rb.y + rb.height) && remainingVY < 0)
								&& result.normal.y == 0) {
							result.normal.y = 1;
						}
						remainingVY = 0;
						if (clearVelocity) {
							target.velocityY = 0;
						}
						moveAmountY = 0;
						result.targets.add(b);
						result.collided = true;
					}
				}
			}
			if (!lastIteration) {
				if (MathUtils.abs(remainingVX) < MathUtils.abs(moveAmountX)) {
					moveAmountX = remainingVX;
				}
				if (MathUtils.abs(remainingVY) < MathUtils.abs(moveAmountY)) {
					moveAmountY = remainingVY;
				}
				positionX += moveAmountX;
				positionY += moveAmountY;
				remainingVX -= moveAmountX;
				remainingVY -= moveAmountY;
			}
			if (!lastIteration && MathUtils.isEqual(0, remainingVX, deviation)
					&& MathUtils.isEqual(0, remainingVY, deviation)) {
				lastIteration = true;
				remainingVX = 0;
				remainingVY = 0;
			} else if (lastIteration) {
				break;
			}
			if (!clearVelocity) {
				break;
			}
		}
		result.position.set(positionX, positionY);
		return result;
	}

	protected GravityHandler movePos(Gravity g, float delta, float gravity, float x, float y) {
		if (_closed) {
			return null;
		}
		if (_collisionWorld == null) {
			return movePos(g, delta, gravity, x, y, _lastX, _lastY);
		}
		return movePos(g, delta, gravity, x, y, -1f, -1f);
	}

	protected GravityHandler movePos(Gravity g, float delta, float gravity, float x, float y, float lastX,
			float lastY) {
		if (_closed) {
			return null;
		}
		if (g == null) {
			return this;
		}
		ActionBind bind = g.bind;
		if (bind == null) {
			return this;
		}
		if (g.enabled) {
			if (checkCollideSolidObjects(g, delta, gravity, x, y)) {
				return this;
			}
			if (_collisionWorld != null) {
				if (worldCollisionFilter == null) {
					worldCollisionFilter = CollisionFilter.getDefault();
				}
				CollisionResult.Result result = _collisionWorld.move(bind, x, y, worldCollisionFilter);
				if (lastX != -1 && lastY != -1) {
					if (result.goalX != x || result.goalY != y) {
						bind.setLocation(lastX, lastY);
					} else {
						bind.setLocation(result.goalX, result.goalY);
					}
				} else {
					bind.setLocation(result.goalX, result.goalY);
				}
			} else {
				bind.setLocation(x, y);
			}
		}
		if (isGravityListener) {
			_gravitylistener.action(g, x, y);
		}
		this._lastX = x;
		this._lastY = y;
		return this;
	}

	public boolean isOverlapping(Vector2f pA, Vector2f sA, Vector2f pB, Vector2f sB) {
		return MathUtils.abs(pA.x - pB.x) * 2 < sA.x + sB.x && MathUtils.abs(pA.y - pB.y) * 2 < sA.y + sB.y;
	}

	public float getLastX() {
		return _lastX;
	}

	public float getLastY() {
		return _lastY;
	}

	public void setEnabled(boolean isEnabled) {
		this.isEnabled = isEnabled;
	}

	public void setBounded(boolean isBounded) {
		this.isBounded = isBounded;
	}

	public GravityHandler setListener(GravityUpdate listener) {
		return setGravityListener(listener);
	}

	public GravityHandler setGravityListener(GravityUpdate listener) {
		return onUpdate(listener);
	}

	public GravityHandler onUpdate(GravityUpdate listener) {
		this._gravitylistener = listener;
		this.isGravityListener = _gravitylistener != null;
		return this;
	}

	public CollisionFilter getCollisionFilter() {
		return worldCollisionFilter;
	}

	public void setCollisionFilter(CollisionFilter filter) {
		this.worldCollisionFilter = filter;
	}

	public CollisionWorld getCollisionWorld() {
		return _collisionWorld;
	}

	public GravityHandler setCollisionWorld(CollisionWorld world) {
		this._collisionWorld = world;
		return this;
	}

	public EasingMode getEasingMode() {
		return _easingMode;
	}

	public GravityHandler setEasingMode(EasingMode ease) {
		_easeTimer.setEasingMode(ease);
		return this;
	}

	public boolean isSyncBind() {
		return syncActionBind;
	}

	public GravityHandler setSyncBind(boolean syncBind) {
		this.syncActionBind = true;
		return this;
	}

	public GravityHandler setObjectMaxSpeed(float speed) {
		this._objectMaxSpeed = speed;
		return this;
	}

	public GravityHandler setDeviation(float d) {
		this._deviation = d;
		return this;
	}

	public GravityHandler setCollisionClearVelocity(boolean c) {
		this._collClearVelocity = c;
		return this;
	}

	public GravityHandler setCollisionScale(float s) {
		this._collScale = s;
		return this;
	}

	public void setGlobalGravityScale(float scale) {
		if (_closed) {
			return;
		}
		this._gravityScale = scale;
	}

	public void resetGlobalGravity() {
		setGlobalGravityScale(1f);
	}

	public void reverseAllGravity() {
		if (_closed) {
			return;
		}
		for (int i = objects.size - 1; i >= 0; i--) {
			Gravity g = objects.get(i);
			if (g != null) {
				g.g = -g.g;
			}
		}
	}

	public void setAllGravityStrength(float gravityValue) {
		if (_closed) {
			return;
		}
		for (int i = objects.size - 1; i >= 0; i--) {
			Gravity g = objects.get(i);
			if (g != null) {
				g.g = gravityValue;
			}
		}
	}

	public void setAllGravityDirection(float angle) {
		if (_closed) {
			return;
		}
		float rad = MathUtils.toRadians(angle);
		float gx = MathUtils.cos(rad);
		float gy = MathUtils.sin(rad);
		for (int i = objects.size - 1; i >= 0; i--) {
			Gravity g = objects.get(i);
			if (g != null) {
				g.accelerationX = gx * MathUtils.abs(g.g);
				g.accelerationY = gy * MathUtils.abs(g.g);
			}
		}
	}

	public void reverseGravity(Gravity g) {
		if (_closed || g == null) {
			return;
		}
		g.g = -g.g;
	}

	public void setGravityDirection(Gravity g, float angle) {
		if (_closed || g == null) {
			return;
		}
		float rad = MathUtils.toRadians(angle);
		g.accelerationX = MathUtils.cos(rad) * MathUtils.abs(g.g);
		g.accelerationY = MathUtils.sin(rad) * MathUtils.abs(g.g);
	}

	public void setGravityStrength(Gravity g, float strength) {
		if (_closed || g == null) {
			return;
		}
		g.g = strength;
	}

	public void useAntiGravityAngle(boolean enable) {
		this._useAntiGravityAngle = enable;
	}

	public void setGlobalAntiGravityAngle(float angle) {
		this._globalAntiGravityAngle = angle;
		this._useAntiGravityAngle = true;
	}

	public float getGlobalAntiGravityAngle() {
		return _globalAntiGravityAngle;
	}

	public void enableFloatEffect(boolean enable) {
		this._enableFloatEffect = enable;
		if (enable) {
			_floatCycle = 0f;
		}
	}

	public void setFloatParams(float amplitude, float speed) {
		this._floatAmplitude = amplitude;
		this._floatSpeed = speed;
	}

	public boolean isEnabled() {
		return isEnabled;
	}

	public boolean isBounded() {
		return isBounded;
	}

	public boolean isListener() {
		return isGravityListener;
	}

	public float getObjectMaxSpeed() {
		return _objectMaxSpeed;
	}

	public boolean isFloatEffectEnabled() {
		return _enableFloatEffect;
	}

	public boolean isClosed() {
		return _closed;
	}

	public float getDeviation() {
		return _deviation;
	}

	public boolean isCollisionClearVelocity() {
		return _collClearVelocity;
	}

	public float getCollisionScale() {
		return _collScale;
	}

	/**
	 * 设置行星重力倍数 (地球=1, 贝吉塔星=20)
	 */
	public void setPlanetGravityMultiplier(float multiplier) {
		if (_closed || multiplier < 0.1f) {
			return;
		}
		this._planetGravityMultiplier = multiplier;
	}

	public float getPlanetGravityMultiplier() {
		return _planetGravityMultiplier;
	}

	public void setEarthGravity() {
		setPlanetGravityMultiplier(1.0f);
	}

	public void setVegetaGravity() {
		setPlanetGravityMultiplier(20.0f);
	}

	public void enableChaosGravity(boolean enable) {
		this._enableChaosGravity = enable;
		this._chaosGravityTimer = 0f;
		if (!enable) {
			_currentChaosState = ChaosGravityState.NORMAL;
			_useAntiGravityAngle = false;
		}
	}

	public void setChaosSwitchInterval(float seconds) {
		if (seconds > 0.1f) {
			this._chaosSwitchInterval = seconds;
		}
	}

	public boolean isChaosGravityEnabled() {
		return _enableChaosGravity;
	}

	public ChaosGravityState getCurrentChaosState() {
		return _currentChaosState;
	}

	public GravityHandler enableMagnetism(boolean e, float damping) {
		_enableMagnetism = e;
		_magneticDamping = damping;
		return this;
	}

	@Override
	public void close() {
		this.isEnabled = false;
		if (objects != null) {
			objects.close();
			objects = null;
		}
		if (pendingAdd != null) {
			pendingAdd.close();
			pendingAdd = null;
		}
		if (pendingAdd != null) {
			pendingAdd.close();
			pendingAdd = null;
		}
		if (_gravityMap != null) {
			_gravityMap.close();
			_gravityMap = null;
		}
		lazyObjects = null;
		_closed = true;
	}

}
