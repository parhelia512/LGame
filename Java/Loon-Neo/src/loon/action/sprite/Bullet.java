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
package loon.action.sprite;

import loon.LObject;
import loon.LSystem;
import loon.LTexture;
import loon.PlayerUtils;
import loon.action.ActionBind;
import loon.action.ActionTween;
import loon.action.collision.CollisionObject;
import loon.action.map.Field2D;
import loon.action.sprite.BulletEntity.BulletListener;
import loon.canvas.LColor;
import loon.geom.RectBox;
import loon.geom.Shape;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.utils.Easing.EasingMode;
import loon.utils.MathUtils;
import loon.utils.timer.Duration;
import loon.utils.timer.EaseTimer;
import loon.utils.timer.LTimerContext;

/**
 * 子弹渲染用类,支持动画播放,
 * 角色角度和方向的自动转换，但本身不是精灵,不能直接add到Screen,由精灵类BulletEntity管理和渲染到游戏中去<br>
 * 一个游戏中，可以存在多个甚至海量的Bullet, 如果子弹过多时,可以使用CacheManager管理子弹的生命周期.
 * 
 */
public class Bullet extends LObject<BulletEntity> implements CollisionObject {

	public static enum WaveType {
		None, Cos, Sin, Cos_Rotate, Sin_Rotate
	}

	protected static String BUTTLE_DEFAULT_NAME = "Buttle";

	// 初始化的子弹移动速度设定
	protected static int INIT_MOVE_SPEED = 50;

	// 初始化的缓动动画持续时长设定
	protected static float INIT_DURATION = 2f;

	private WaveType _waveType;

	private int _bulletType;
	private int _direction;
	private int _initSpeed;

	private boolean _destroyOnHit;
	private boolean _isHoming;

	private final Vector2f _speed = new Vector2f();
	private final Vector2f _waveOffset = new Vector2f();
	private final Vector2f _baseMoveOffset = new Vector2f();
	private final Vector2f _lastPos = new Vector2f();

	private final EaseTimer _easeTimer;
	private Field2D _arrayMap;
	private Animation _animation;
	private BulletListener _listener;
	private Shape _otherShape;

	private boolean _dirToAngle;
	private boolean _visible;
	private boolean _active;
	private boolean _autoRemoved;
	private boolean _destroyed;

	private float _width;
	private float _height;
	private float _scaleX;
	private float _scaleY;
	private float _scaleSpeed;
	private float _waveamplitude;
	private float _wavefrequency;
	private float _lifeTimer;
	private float _lifeCounter;

	private LColor _baseColor;

	private float _acceleration;
	private float _maxSpeed;
	private float _gravityScale;
	private float _damping;
	private float _alphaFadeSpeed;
	private float _scaleFadeSpeed;
	private float _damage;
	private float _homingRotateSpeed;
	private float _selfRotateSpeed;

	private final Vector2f _targetPos = new Vector2f();

	private int _teamLayer;
	private int _bounceCount;
	private int _bounceMax;
	private int _pierceCount;
	private int _pierceMax;

	public Bullet(EasingMode easingMode, LTexture tex, float x, float y) {
		this(easingMode, tex, x, y, 0, INIT_DURATION);
	}

	public Bullet(EasingMode easingMode, Animation ani, float x, float y) {
		this(easingMode, ani, x, y, 0);
	}

	public Bullet(EasingMode easingMode, float x, float y) {
		this(easingMode, x, y, 0);
	}

	public Bullet(EasingMode easingMode, LTexture tex, float x, float y, int dir) {
		this(easingMode, Animation.getDefaultAnimation(tex), x, y, dir);
	}

	public Bullet(EasingMode easingMode, LTexture tex, float x, float y, int dir, float duration) {
		this(easingMode, Animation.getDefaultAnimation(tex), x, y, dir, INIT_MOVE_SPEED, duration);
	}

	public Bullet(EasingMode easingMode, Animation ani, float x, float y, int dir) {
		this(easingMode, ani, x, y, ani.getSpriteImage().getWidth(), ani.getSpriteImage().getHeight(), dir,
				INIT_DURATION);
	}

	public Bullet(EasingMode easingMode, LTexture texture, float x, float y, int dir, int initSpeed) {
		this(easingMode, Animation.getDefaultAnimation(texture), x, y, dir, initSpeed);
	}

	public Bullet(EasingMode easingMode, Animation ani, float x, float y, int dir, int initSpeed) {
		this(easingMode, ani, x, y, ani.getSpriteImage().getWidth(), ani.getSpriteImage().getHeight(), dir, initSpeed,
				INIT_DURATION);
	}

	public Bullet(EasingMode easingMode, float x, float y, int dir) {
		this(easingMode, null, x, y, LSystem.LAYER_TILE_SIZE, LSystem.LAYER_TILE_SIZE, dir, INIT_DURATION);
	}

	public Bullet(EasingMode easingMode, LTexture texture, float x, float y, int dir, int initSpeed, float duration) {
		this(easingMode, Animation.getDefaultAnimation(texture), x, y, texture.getWidth(), texture.getHeight(), dir,
				initSpeed, duration);
	}

	public Bullet(EasingMode easingMode, Animation ani, float x, float y, int dir, int initSpeed, float duration) {
		this(easingMode, ani, x, y, ani.getSpriteImage().getWidth(), ani.getSpriteImage().getHeight(), dir, initSpeed,
				duration);
	}

	public Bullet(EasingMode easingMode, Animation ani, float x, float y, float w, float h, int dir, float duration) {
		this(easingMode, ani, x, y, w, h, dir, INIT_MOVE_SPEED, duration);
	}

	public Bullet(EasingMode easingMode, Animation ani, float x, float y, float w, float h, int dir,
			int bulletInitSpeed, float duration) {
		this(0, easingMode, ani, x, y, w, h, dir, bulletInitSpeed, duration);
	}

	public Bullet(int bulletType, EasingMode easingMode, Animation ani, float x, float y, float w, float h, int dir,
			int bulletInitSpeed, float duration) {
		setLocation(x, y);
		setObjectFlag(BUTTLE_DEFAULT_NAME);
		_easeTimer = new EaseTimer(duration, easingMode);
		_waveType = WaveType.None;
		_baseColor = LColor.white.cpy();
		_animation = ani;
		_initSpeed = bulletInitSpeed;
		_bulletType = bulletType;
		_direction = -1;
		_lifeTimer = _lifeCounter = 0f;
		_scaleX = _scaleY = 1f;
		_waveamplitude = _wavefrequency = 1f;
		_scaleSpeed = 1f;
		_visible = true;
		_dirToAngle = true;
		_active = true;
		_autoRemoved = true;
		_width = w;
		_height = h;
		_destroyed = false;
		_acceleration = 0f;
		_maxSpeed = 9999f;
		_gravityScale = 0f;
		_damping = 1f;
		_bounceCount = 0;
		_bounceMax = 0;
		_pierceCount = 0;
		_pierceMax = 0;
		_alphaFadeSpeed = 0f;
		_scaleFadeSpeed = 0f;
		_teamLayer = 0;
		_damage = 1f;
		_destroyOnHit = true;
		_selfRotateSpeed = 0f;
		_isHoming = false;
		_homingRotateSpeed = 90f;
		_lastPos.set(x, y);
		setDirection(dir);
	}

	public Bullet setAcceleration(float acc) {
		_acceleration = acc;
		return this;
	}

	public Bullet setMaxSpeed(float max) {
		_maxSpeed = max;
		return this;
	}

	public Bullet setGravity(float g) {
		_gravityScale = g;
		return this;
	}

	public Bullet setDamping(float d) {
		_damping = d;
		return this;
	}

	public Bullet setBounceMax(int max) {
		_bounceMax = max;
		return this;
	}

	public Bullet setPierceMax(int max) {
		_pierceMax = max;
		return this;
	}

	public Bullet setAlphaFade(float fade) {
		_alphaFadeSpeed = fade;
		return this;
	}

	public Bullet setScaleFade(float fade) {
		_scaleFadeSpeed = fade;
		return this;
	}

	public Bullet setTeamLayer(int layer) {
		_teamLayer = layer;
		return this;
	}

	public Bullet setDamage(float dmg) {
		_damage = dmg;
		return this;
	}

	public Bullet setDestroyOnHit(boolean b) {
		_destroyOnHit = b;
		return this;
	}

	public Bullet setSelfRotateSpeed(float s) {
		_selfRotateSpeed = s;
		return this;
	}

	public float getSelfRotateSpeed() {
		return _selfRotateSpeed;
	}

	public float getDamage() {
		return _damage;
	}

	public int getTeamLayer() {
		return _teamLayer;
	}

	public Bullet setHoming(boolean homing) {
		_isHoming = homing;
		return this;
	}

	public Bullet setHomingTarget(Vector2f target) {
		_targetPos.set(target);
		_isHoming = true;
		return this;
	}

	public void checkCollision(CollisionObject other) {
		if (_destroyed || other == null || !other.isVisible()) {
			return;
		}
		if (isCollision(other)) {
			if (_listener != null) {
				_listener.onHit(this, other);
			}
			if (_pierceCount < _pierceMax) {
				_pierceCount++;
			} else {
				if (_destroyOnHit) {
					freeBullet();
				}
			}
			if (_bounceCount < _bounceMax) {
				_speed.reflectSelf(Vector2f.AXIS_X());
				_bounceCount++;
				if (_listener != null) {
					_listener.onBounce(this, other);
				}
			}
		}
	}

	public Bullet randDirection() {
		int rx = MathUtils.random(0, 1) * 2 - 1;
		int ry = MathUtils.random(0, 1) * 2 - 1;
		setSpeedX(rx * _initSpeed);
		setSpeedY(ry * _initSpeed);
		return this;
	}

	public Bullet reflect(Vector2f axis) {
		_speed.reflectSelf(axis);
		return this;
	}

	public Bullet reflectLeft() {
		_speed.x = -MathUtils.abs(_speed.x + 1);
		_speed.y += _speed.y > 0 ? 1 : -1;
		return this;
	}

	public Bullet reflectRight() {
		_speed.x = MathUtils.abs(_speed.x + 1);
		_speed.y += _speed.y > 0 ? 1 : -1;
		return this;
	}

	public WaveType getWaveType() {
		return _waveType;
	}

	public Bullet setWaveType(WaveType w) {
		_waveType = w == null ? WaveType.None : w;
		return this;
	}

	public Bullet setWaveAmplitude(float a) {
		_waveamplitude = a;
		return this;
	}

	public float getWaveAmplitude() {
		return _waveamplitude;
	}

	public Bullet setWaveFrequency(float f) {
		_wavefrequency = f;
		return this;
	}

	public float getWaveFrequency() {
		return _wavefrequency;
	}

	public EaseTimer getEaseTimer() {
		return _easeTimer;
	}

	public Bullet setEaseTimerLoop(boolean l) {
		_easeTimer.setLoop(l);
		return this;
	}

	public boolean isEaseTimerLoop() {
		return _easeTimer.isLoop();
	}

	public boolean isEaseCompleted() {
		return _easeTimer.isCompleted();
	}

	public boolean isAutoRemoved() {
		return _autoRemoved;
	}

	public Bullet setAutoRemoved(boolean a) {
		_autoRemoved = a;
		return this;
	}

	public Bullet setListener(BulletListener l) {
		_listener = l;
		return this;
	}

	public BulletListener getListener() {
		return _listener;
	}

	public Bullet setDuration(float d) {
		_easeTimer.setDuration(d);
		return this;
	}

	public float getLifeTimer() {
		return _lifeTimer;
	}

	public Bullet setLifeTimer(float f) {
		_lifeTimer = f;
		return this;
	}

	public Bullet clearSpeed() {
		_speed.setZero();
		return this;
	}

	public void draw(GLEx g) {
		draw(g, 0, 0);
	}

	public void draw(GLEx g, float offsetX, float offsetY) {
		if (!_visible || _destroyed) {
			return;
		}
		onDrawable(g, offsetX, offsetY);
		if (_animation != null) {
			LTexture tex = _animation.getSpriteImage();
			float a = _baseColor.a;
			if (tex != null) {
				g.draw(tex, getX() + offsetX, getY() + offsetY, getWidth(), getHeight(),
						_baseColor.setAlpha(_objectAlpha), _objectRotation);
			}
			_baseColor.a = a;
		}
	}

	@Override
	public void update(long elapsedTime) {
		if (_destroyed) {
			return;
		}
		float delta = Duration.toS(elapsedTime);
		if (_active) {
			onUpdateable(elapsedTime);
			if (_animation != null) {
				_animation.update(elapsedTime);
			}
			_easeTimer.update(elapsedTime);
			applyAcceleration(delta);
			applyGravity(delta);
			updateHoming(delta);
			applyFadeEffect(delta);
			_objectRotation += _selfRotateSpeed * delta;
			if (!checkLifeOver(_listener)) {
				float v = LSystem.getScaleFPS();
				Vector2f offset = getWaveSpeedOffset(_easeTimer);
				float newX = getX() + (_speed.x + offset.x) * v;
				float newY = getY() + (_speed.y + offset.y) * v;
				setLocation(newX, newY);
			}
			if (_listener != null && _easeTimer.isCompleted()) {
				_listener.easeover(this);
			}
		}
		_lastPos.set(getLocation());
	}

	public Bullet setHomingRotateSpeed(float speed) {
		_homingRotateSpeed = speed;
		return this;
	}

	private void updateHoming(float deltaTime) {
		if (!_isHoming || _speed.isZero()) {
			return;
		}
		float targetAngle = MathUtils.atan2(_targetPos.y - getY(), _targetPos.x - getX());
		float currentAngle = MathUtils.atan2(_speed.y, _speed.x);
		float diff = targetAngle - currentAngle;
		diff = MathUtils.atan2(MathUtils.sin(diff), MathUtils.cos(diff));
		float maxStep = _homingRotateSpeed * deltaTime;
		diff = MathUtils.clamp(diff, -maxStep, maxStep);
		float newAngle = currentAngle + diff;
		float len = _speed.length();
		_speed.x = MathUtils.cos(newAngle) * len;
		_speed.y = MathUtils.sin(newAngle) * len;
	}

	private void applyAcceleration(float deltaTime) {
		if (_acceleration != 0) {
			_speed.mulSelf(1 + _acceleration * deltaTime);
		}
		if (_damping < 1) {
			_speed.mulSelf(_damping);
		}
		if (_speed.length() > _maxSpeed) {
			_speed.norSelf().mulSelf(_maxSpeed);
		}
	}

	private void applyGravity(float deltaTime) {
		if (_gravityScale != 0) {
			_speed.y += _gravityScale * deltaTime;
		}
	}

	private void applyFadeEffect(float deltaTime) {
		if (_alphaFadeSpeed != 0) {
			_baseColor.a = MathUtils.max(0, _baseColor.a - _alphaFadeSpeed * deltaTime);
			if (_baseColor.a <= 0) {
				freeBullet();
			}
		}
		if (_scaleFadeSpeed != 0) {
			_scaleX = MathUtils.max(0, _scaleX - _scaleFadeSpeed * deltaTime);
			_scaleY = MathUtils.max(0, _scaleY - _scaleFadeSpeed * deltaTime);
			if (_scaleX <= 0 && _scaleY <= 0) {
				freeBullet();
			}
		}
	}

	/**
	 * 如果子弹生命周期到时,触发监听
	 * 
	 * @param l
	 */
	protected boolean checkLifeOver(BulletListener l) {
		if (_lifeTimer <= 0) {
			return false;
		}
		_lifeCounter += _easeTimer.getDelta();
		if (_lifeCounter >= _lifeTimer) {
			if (l != null) {
				l.lifeover(this);
			}
			freeBullet();
			return true;
		}
		return false;
	}

	/**
	 * 当设定waveType类型为sin或cos时,会根据waveamplitude以及wavefrequency参数调整子弹的波形轨迹
	 * 
	 * @param ease
	 * @return
	 */
	protected Vector2f getWaveSpeedOffset(EaseTimer ease) {
		float delta = _easeTimer.getProgress();
		_baseMoveOffset.set(_speed.x * delta, _speed.y * delta);
		float angle = 0;

		float dirAngle = MathUtils.atan2(_speed.y, _speed.x);

		switch (_waveType) {
		case Sin:
			angle = _waveamplitude * MathUtils.sin(delta * MathUtils.TWO_PI * _wavefrequency);
			_waveOffset.set(_baseMoveOffset.x + MathUtils.cos(dirAngle + MathUtils.HALF_PI) * angle,
					_baseMoveOffset.y + MathUtils.sin(dirAngle + MathUtils.HALF_PI) * angle);
			break;
		case Cos:
			angle = _waveamplitude * MathUtils.cos(delta * MathUtils.TWO_PI * _wavefrequency);
			_waveOffset.set(_baseMoveOffset.x + MathUtils.cos(dirAngle + MathUtils.HALF_PI) * angle,
					_baseMoveOffset.y + MathUtils.sin(dirAngle + MathUtils.HALF_PI) * angle);
			break;
		case Sin_Rotate:
			angle = MathUtils.waveSin(_wavefrequency, _waveamplitude, ease.getDelta()) * delta;
			_waveOffset.set(_baseMoveOffset.x * MathUtils.cos(angle) - _baseMoveOffset.y * MathUtils.sin(angle),
					_baseMoveOffset.x * MathUtils.sin(angle) + _baseMoveOffset.y * MathUtils.cos(angle));
			break;
		case Cos_Rotate:
			angle = MathUtils.waveCos(_wavefrequency, _waveamplitude, ease.getDelta()) * delta;
			_waveOffset.set(_baseMoveOffset.x * MathUtils.cos(angle) - _baseMoveOffset.y * MathUtils.sin(angle),
					_baseMoveOffset.x * MathUtils.sin(angle) + _baseMoveOffset.y * MathUtils.cos(angle));
			break;
		default:
			_waveOffset.set(_baseMoveOffset);
			break;
		}
		return _waveOffset;
	}

	public void update(LTimerContext time) {
		update(time.timeSinceLastUpdate);
	}

	public float getScaleSpeed() {
		return _scaleSpeed;
	}

	public Bullet setScaleSpeed(float s) {
		if (s <= 0 || s == _scaleSpeed) {
			return this;
		}
		_speed.mulSelf(s / _scaleSpeed);
		_scaleSpeed = s;
		return this;
	}

	public Vector2f getWaveOffset() {
		return _waveOffset.cpy();
	}

	protected void onAttached() {
		if (_listener != null) {
			_listener.attached(this);
		}
	}

	protected void onDetached() {
		if (_listener != null) {
			_listener.detached(this);
		}
	}

	protected void onDrawable(GLEx g, float offsetX, float offsetY) {
		if (_listener != null)
			_listener.drawable(g, this);
	}

	protected void onUpdateable(long elapsedTime) {
		if (_listener != null) {
			_listener.updateable(elapsedTime, this);
		}
	}

	public int getDirection() {
		return _direction;
	}

	public boolean isDirToAngle() {
		return _dirToAngle;
	}

	public Bullet setDirToAngle(boolean dta) {
		_dirToAngle = dta;
		return this;
	}

	public Vector2f getSpeed() {
		return _speed.cpy();
	}

	public Bullet setSpeedX(float x) {
		_speed.x = x;
		return this;
	}

	public Bullet setSpeedY(float y) {
		_speed.y = y;
		return this;
	}

	public Bullet setInitSpeed(int s) {
		if (s > 0 && _initSpeed != s) {
			_initSpeed = s;
			Field2D.getDirectionToPoint(_direction, _initSpeed, _speed);
		}
		return this;
	}

	public int getInitSpeed() {
		return _initSpeed;
	}

	public Bullet fireTo(ISprite spr) {
		return fireTo(spr, 0, 0);
	}

	public Bullet fireTo(ISprite spr, float ox, float oy) {
		if (spr == null) {
			return this;
		}
		return setMoveTargetToRotation(spr.getX() + spr.getWidth() / 2 + ox, spr.getY() + spr.getHeight() / 2 + oy);
	}

	public Bullet fireTo(ActionBind act) {
		return fireTo(act, 0, 0);
	}

	public Bullet fireTo(ActionBind act, float ox, float oy) {
		if (act == null) {
			return this;
		}
		return setMoveTargetToRotation(act.getX() + act.getWidth() / 2 + ox, act.getY() + act.getHeight() / 2 + oy);
	}

	public Bullet fireTo(XY pos) {
		if (pos == null) {
			return this;
		}
		return setMoveTargetToRotation(pos.getX(), pos.getY());
	}

	public Bullet fireTo(float x, float y) {
		return setMoveTargetToRotation(x, y);
	}

	public Bullet setMoveTargetToRotation(float tx, float ty) {
		float dx = tx - getX();
		float dy = ty - getY();
		float len = MathUtils.sqrt(dx * dx + dy * dy);
		if (len > 0) {
			_speed.x = dx / len * _initSpeed;
			_speed.y = dy / len * _initSpeed;
		}
		if (_dirToAngle) {
			setRotation(MathUtils.atan2(dy, dx) * MathUtils.RAD_TO_DEG);
		}
		_direction = Field2D.getDirection(getLocation(), Vector2f.at(tx, ty));
		return this;
	}

	public Bullet setMoveTargetToRotation(Vector2f target) {
		if (target == null) {
			return this;
		}
		return setMoveTargetToRotation(target.x, target.y);
	}

	public Bullet setDirection(int dir) {
		Field2D.getDirectionToPoint(dir, _initSpeed, _speed);
		if (_dirToAngle) {
			setRotation(Field2D.getDirectionToAngle(dir));
		}
		_direction = dir;
		return this;
	}

	public boolean isCollision(CollisionObject o) {
		if (o == null || _destroyed) {
			return false;
		}
		if (_otherShape != null) {
			return _otherShape.intersects(o.getRectBox());
		}
		return getCollisionArea().intersects(o.getRectBox());
	}

	public boolean isCollision(Shape shape) {
		if (shape == null || _destroyed) {
			return false;
		}
		if (_otherShape != null) {
			return _otherShape.intersects(shape);
		}
		return getCollisionArea().intersects(shape);
	}

	public Animation getAnimation() {
		return _animation;
	}

	public LTexture getTexture() {
		return _animation == null ? null : _animation.getSpriteImage();
	}

	@Override
	public RectBox getCollisionArea() {
		return MathUtils.getBounds(getScalePixelX(), getScalePixelY(), getWidth(), getHeight(), _objectRotation,
				_objectRect);
	}

	public float getScalePixelX() {
		return _scaleX == 1 ? getX() : getX() + getWidth() / 2;
	}

	public float getScalePixelY() {
		return _scaleY == 1 ? getY() : getY() + getHeight() / 2;
	}

	@Override
	public RectBox getBoundingRect() {
		return getCollisionArea();
	}

	@Override
	public RectBox getRectBox() {
		return getCollisionArea();
	}

	@Override
	public boolean containsPoint(float x, float y) {
		return getCollisionArea().contains(x, y);
	}

	@Override
	public boolean contains(CollisionObject o) {
		return getCollisionArea().contains(o.getRectBox());
	}

	@Override
	public boolean intersects(CollisionObject o) {
		return getCollisionArea().intersects(o.getRectBox());
	}

	@Override
	public boolean intersects(Shape s) {
		return getCollisionArea().intersects(s);
	}

	@Override
	public boolean contains(Shape s) {
		return getCollisionArea().contains(s);
	}

	@Override
	public boolean collided(Shape s) {
		return getCollisionArea().collided(s);
	}

	@Override
	public float getWidth() {
		return _width > 1 ? _width * _scaleX : (_animation == null ? 0 : _animation.getWidth() * _scaleX);
	}

	@Override
	public float getHeight() {
		return _height > 1 ? _height * _scaleY : (_animation == null ? 0 : _animation.getHeight() * _scaleY);
	}

	@Override
	public boolean isVisible() {
		return _visible;
	}

	@Override
	public void setVisible(boolean v) {
		_visible = v;
	}

	@Override
	public LColor getColor() {
		return _baseColor;
	}

	@Override
	public void setColor(LColor c) {
		if (c != null) {
			_baseColor = c;
		}
	}

	public Bullet setField2D(Field2D f) {
		_arrayMap = f;
		return this;
	}

	@Override
	public Field2D getField2D() {
		return _arrayMap;
	}

	@Override
	public float getScaleX() {
		return _scaleX;
	}

	@Override
	public float getScaleY() {
		return _scaleY;
	}

	public Bullet setScale(float s) {
		setScale(s, s);
		return this;
	}

	@Override
	public void setScale(float x, float y) {
		_scaleX = x;
		_scaleY = y;
	}

	@Override
	public Bullet setSize(float w, float h) {
		_width = w;
		_height = h;
		return this;
	}

	public Bullet setWidth(float w) {
		_width = w;
		return this;
	}

	public Bullet setHeight(float h) {
		_height = h;
		return this;
	}

	@Override
	public boolean isBounded() {
		return false;
	}

	@Override
	public boolean isContainer() {
		return false;
	}

	public boolean isActive() {
		return _active;
	}

	public Bullet setActive(boolean b) {
		_active = b;
		return this;
	}

	public Bullet pause() {
		return setActive(false);
	}

	public Bullet resume() {
		return setActive(true);
	}

	public int getBulletType() {
		return _bulletType;
	}

	public Bullet setBulletType(int t) {
		_bulletType = t;
		return this;
	}

	public Bullet setCustomShape(Shape s) {
		_otherShape = s;
		return this;
	}

	public Shape getCustomShape() {
		return _otherShape;
	}

	@Override
	public boolean inContains(float x, float y, float w, float h) {
		return getRectBox().contains(x, y, w, h);
	}

	@Override
	public ActionTween selfAction() {
		return PlayerUtils.set(this);
	}

	@Override
	public boolean isActionCompleted() {
		return PlayerUtils.isActionCompleted(this);
	}

	public void resetTo(EasingMode easingMode, Animation ani, float x, float y) {
		_destroyed = false;
		_visible = true;
		_active = true;
		_autoRemoved = true;
		_easeTimer.reset();
		_easeTimer.setEasingMode(easingMode);
		_animation = ani;
		setLocation(x, y);
		_lastPos.set(x, y);
		clearSpeed();
		_direction = -1;
		_lifeCounter = 0;
		_pierceCount = 0;
		_bounceCount = 0;
		_baseColor.reset();
		_scaleX = _scaleY = 1f;
		_objectRotation = 0;
		_objectAlpha = 1f;
	}

	public void resetTo(EasingMode easingMode, Animation ani, float x, float y, int dir) {
		resetTo(easingMode, ani, x, y);
		setDirection(dir);
	}

	public void resetTo(EasingMode easingMode, Animation ani, float x, float y, int dir, int initSpeed) {
		resetTo(easingMode, ani, x, y, dir);
		_initSpeed = initSpeed;
		setDirection(dir);
	}

	public void resetTo(EasingMode easingMode, Animation ani, float x, float y, int dir, int initSpeed,
			float duration) {
		resetTo(easingMode, ani, x, y, dir, initSpeed);
		_easeTimer.setDuration(duration);
	}

	public void freeBullet() {
		if (_destroyed) {
			return;
		}
		if (_autoRemoved && getSuper() != null) {
			getSuper().removeWorld(this);
		}
		_destroyed = true;
	}

	@Override
	protected void _onDestroy() {
		if (_animation != null) {
			_animation.close();
			_animation = null;
		}
		_listener = null;
		_visible = false;
		_active = false;
		_destroyed = true;
	}
}
