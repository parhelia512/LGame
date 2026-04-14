/**
 * Copyright 2008 - 2012
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
 * @version 0.3.3
 */
package loon.action.sprite;

import loon.LSystem;
import loon.LTexture;
import loon.action.collision.Force;
import loon.action.map.Config;
import loon.action.map.Field2D;
import loon.action.map.Side;
import loon.action.map.TileMapCollision;
import loon.action.map.items.Attribute;
import loon.geom.Vector2f;
import loon.utils.CollectionUtils;
import loon.utils.MathUtils;
import loon.utils.StrBuilder;
import loon.utils.StringUtils;
import loon.utils.TArray;

/**
 * 和瓦片地图绑定的动作对象,用来抽象一些简单的ACT地图中精灵动作
 */
public abstract class ActionObject extends Entity implements Config {

	public static interface ActionStateListener {
		void onStateChanged(ActionObject obj, ActorState oldState, ActorState newState);
	}

	public static enum GravityDirection {
		NORMAL, INVERTED
	}

	public static enum ObjectState {
		StaticObject, DynamicObject
	}

	public static enum ActorState {
		IDLE, RUN, JUMP, ATTACK, BLOCK, FALL, WALL_SLIDE, WALL_CLIMB, CEILING_WALK, DOUBLE_JUMP, ROLL, SHOOT, HIT_STUN,
		DOWN_STRIKE, DASH
	}

	public ActorState currentState = ActorState.IDLE;

	private static final float DEFAULT_DRAG_FACTOR = 0.95f;
	private static final float MAX_VELOCITY = 200f;

	private boolean _canDoubleJump = true;
	private Side _currentSide;
	private ObjectState _currentObjectState;
	private TArray<Force> _forces;
	protected boolean _groundedLeftRight;
	protected boolean _groundedTopBottom;
	protected Attribute attribute;
	protected Animation animation;
	protected TileMapCollision tiles;
	protected float velocityX;
	protected float velocityY;
	protected boolean enableContinuousCollision;

	private final Vector2f _tempResult = new Vector2f();

	protected boolean _onSlope;
	protected boolean _isWallSliding;
	protected boolean _wallSlideEnabled;
	protected boolean _flipLock = false;

	// 加个倒挂执行间隔，避免卡在特殊位置反复颠倒角色
	protected long _flipCooldown = LSystem.SECOND / 2;
	protected long _flipElapsed = 0;

	// 允许爬墙(同时按左或右和上,延墙壁上行)
	protected boolean _wallClimbEnabled = false;
	protected float _wallClimbSpeed = 2.5f;
	protected boolean _isWallClimbing = false;

	// 倒挂行走(通俗的讲，脚底查克拉模式，可以在瓦片反面移动，需配合ceilingWalkVelocityY矢量修改来抵消重力)
	protected boolean _ceilingWalkEnabled = false;
	protected boolean _isCeilingWalking = false;
	protected boolean _flipVerticalAuto = true;
	protected float _ceilingWalkVelocityY = -2f;

	protected float _wallBounceFactor = 1f;
	protected float _wallSlideSpeed = 1f;
	protected float gravityScale = 1.0f;
	protected float frictionGround = 0.85f;
	protected float frictionAir = 0.98f;
	protected float maxFallSpeed = 9.8f * 4;
	protected float maxVelocity = 12f;
	protected float minMoveThreshold = 0.01f;

	private float _superJumpCharge = 0f;
	private boolean _spawnCorrected = false;
	private boolean _canAirDash = true;

	private final TArray<ActionStateListener> _listeners = new TArray<ActionStateListener>();

	public ActionObject(float x, float y, String path) {
		this(x, y, 0, 0, Animation.getDefaultAnimation(path), null);
	}

	public ActionObject(float x, float y, Animation animation) {
		this(x, y, 0, 0, animation, null);
	}

	public ActionObject(float x, float y, Animation animation, TileMapCollision map) {
		this(x, y, 0f, 0f, animation, map);
	}

	public ActionObject(float x, float y, float dw, float dh, Animation animation, TileMapCollision map) {
		super(animation == null ? null : animation.getSpriteImage(), x, y, dw, dh);
		if (animation != null) {
			this.setTexture(animation.getSpriteImage());
		}
		this._currentObjectState = ObjectState.DynamicObject;
		this._currentSide = new Side();
		this.tiles = map;
		this.animation = animation;
		this.enableContinuousCollision = true;
		this._forces = new TArray<Force>();
		resetVelocity();
	}

	@Override
	void onProcess(long elapsedTime) {
		if (!isStaticObject()) {
			if (!_spawnCorrected && tiles != null) {
				correctSpawnPosition();
				_spawnCorrected = true;
			}
			if (!_forces.isEmpty()) {
				resetVelocity();
				for (int i = 0; i < _forces.size; i++) {
					Force force = _forces.get(i);
					if (force != null) {
						force.update(elapsedTime);
						addVelocity(force.direction());
					}
				}
				setLocation(getX() + velocityX, getY() + velocityY);
			}
			_flipElapsed += elapsedTime;
			updateWallClimb();
			updateCeilingWalk();
			updateActorState();
		}
		limitVelocity();
		cleanMicroVelocity();
		if (animation != null) {
			animation.update(elapsedTime);
			LTexture texture = animation.getSpriteImage();
			if (texture != null) {
				_image = texture;
			}
		}
		onPostProcess(elapsedTime);
	}

	public void hitStun() {
		currentState = ActorState.HIT_STUN;
		stopMovement();
	}

	private void updateActorState() {
		ActorState oldState = currentState;
		if (_isWallSliding) {
			currentState = ActorState.WALL_SLIDE;
		} else if (_isWallClimbing) {
			currentState = ActorState.WALL_CLIMB;
		} else if (_isCeilingWalking) {
			currentState = ActorState.CEILING_WALK;
		} else if (!_groundedTopBottom) {
			currentState = velocityY < 0 ? ActorState.JUMP : ActorState.FALL;
		} else if (Math.abs(velocityX) > 0.1f) {
			currentState = ActorState.RUN;
		} else {
			currentState = ActorState.IDLE;
		}
		if (oldState != currentState) {
			notifyStateChanged(oldState, currentState);
		}
	}

	public ActorState getCurrentState() {
		return currentState;
	}

	private void correctSpawnPosition() {
		if (tiles == null) {
			return;
		}
		float cx = getCenterX();
		float cy = getCenterY();
		if (tiles.getTileCollision(this, cx, getY() - 1) != null) {
			float ty = tiles.tilesToPixelsY(tiles.pixelsToTilesHeight(getY()));
			setLocation(getX(), ty + 2);
		}
		if (tiles.getTileCollision(this, cx, cy) != null) {
			float ty = tiles.tilesToPixelsY(tiles.pixelsToTilesHeight(getY() + getHeight()));
			setLocation(getX(), ty - getHeight() - 1);
		}
	}

	/**
	 * 爬墙
	 */
	private void updateWallClimb() {
		_isWallClimbing = false;
		if (!_wallClimbEnabled || _groundedTopBottom || !_groundedLeftRight || tiles == null) {
			return;
		}
		_isWallClimbing = true;
		velocityY = -_wallClimbSpeed;
		onWallClimbing();
	}

	protected void onWallClimbing() {
	}

	/**
	 * 倒挂
	 * 
	 * @param e
	 */
	private void updateCeilingWalk() {
		_isCeilingWalking = false;
		if (!_ceilingWalkEnabled || tiles == null) {
			return;
		}
		float centerX = getCenterX();
		float headY = getY() - 1;
		// 检测头顶是否有瓦片
		boolean hasCeilingTile = tiles.getTileCollision(this, centerX, headY) != null;
		// 检测左右是否有瓦片
		boolean leftTile = tiles.getTileCollision(this, getX() - 1, getCenterY()) != null;
		boolean rightTile = tiles.getTileCollision(this, getX() + getWidth() + 1, getCenterY()) != null;
		// 只有头顶有瓦片，且左右都没有瓦片时才算倒挂
		boolean canCeilingWalk = hasCeilingTile && !leftTile && !rightTile;
		if (canCeilingWalk) {
			_isCeilingWalking = true;
			_groundedTopBottom = true;
			velocityY = _ceilingWalkVelocityY;
		}
		if (_isCeilingWalking) {
			onCeilingWalking();
		}
		if (_isCeilingWalking != _flipLock && _flipElapsed >= _flipCooldown) {
			_flipLock = _isCeilingWalking;
			_flipElapsed = 0;
			updateFlip();
		}
	}

	private void updateFlip() {
		if (!_flipVerticalAuto) {
			return;
		}
		setFlipY(_flipLock);
	}

	protected void onCeilingWalking() {
	}

	public void roll(float rollSpeed) {
		velocityX = getDirection() == Side.TRIGHT ? rollSpeed : -rollSpeed;
		currentState = ActorState.ROLL;
	}

	public void attack() {
		currentState = ActorState.ATTACK;
	}

	public void block() {
		currentState = ActorState.BLOCK;
	}

	public void downStrike(float strikeForce) {
		if (!_groundedTopBottom) {
			velocityY = strikeForce;
			currentState = ActorState.DOWN_STRIKE;
		}
	}

	public ActionObject jump(float jumpForce) {
		if (_groundedTopBottom) {
			velocityY = -jumpForce;
			currentState = ActorState.JUMP;
			_canDoubleJump = true;
		} else if (_canDoubleJump) {
			velocityY = -jumpForce;
			currentState = ActorState.DOUBLE_JUMP;
			_canDoubleJump = false;
		}
		return this;
	}

	public ActionObject applyForce(Force force) {
		if (force != null) {
			_forces.add(force);
		}
		return this;
	}

	public ActionObject clearForces() {
		_forces.clear();
		return this;
	}

	public TArray<Force> getForces() {
		return _forces;
	}

	@Override
	public ActionObject pause() {
		super.pause();
		animationStop();
		return this;
	}

	@Override
	public ActionObject resume() {
		super.resume();
		animationResume();
		return this;
	}

	public boolean isStaticObject() {
		return _currentObjectState == ObjectState.StaticObject;
	}

	public ActionObject animationStart() {
		if (animation != null) {
			animation.start();
		}
		return this;
	}

	public ActionObject animationStop() {
		if (animation != null) {
			animation.stop();
		}
		return this;
	}

	public ActionObject animationResume() {
		if (animation != null) {
			animation.resume();
		}
		return this;
	}

	public ActionObject animationReset() {
		if (animation != null) {
			animation.reset();
		}
		return this;
	}

	public ActionObject animationReverse(boolean r) {
		if (animation != null) {
			animation.setReverse(r);
		}
		return this;
	}

	public int getAnimationIndex() {
		return animation == null ? -1 : animation.currentFrameIndex;
	}

	public ActionObject setAnimationIndex(int idx) {
		if (animation != null)
			animation.setCurrentFrameIndex(idx);
		return this;
	}

	public boolean isAnimationRunning() {
		return animation != null && animation.isRunning;
	}

	public Vector2f collisionTileMap() {
		return collisionTileMap(0f, 0f);
	}

	public Vector2f collisionTileMap(float speedX, float speedY) {
		if (tiles == null) {
			return Vector2f.at(getX(), getY());
		}
		float currentX = getX();
		float currentY = getY();
		velocityX += speedX;
		velocityY += speedY;
		_isWallSliding = false;
		_onSlope = false;

		// 倒挂时不坠落
		if (_isCeilingWalking) {
			velocityY = _ceilingWalkVelocityY;
		}

		float newX = currentX + velocityX;
		Vector2f tile = tiles.getTileCollision(this, newX, currentY);
		if (tile == null) {
			currentX = newX;
			_groundedLeftRight = false;
		} else {
			if (velocityX > 0) {
				currentX = tiles.tilesToPixelsX(tile.x) - getWidth();
			} else if (velocityX < 0) {
				currentX = tiles.tilesToPixelsX(tile.x + 1);
			}
			velocityX = -velocityX * _wallBounceFactor;
			_groundedLeftRight = true;
			if (_wallSlideEnabled && !_groundedTopBottom && velocityY > 0) {
				velocityY = _wallSlideSpeed;
				_isWallSliding = true;
			}
		}

		float newY = currentY + velocityY;
		float footCheckX = currentX + getWidth() * 0.5f;
		tile = tiles.getTileCollision(this, footCheckX, newY);

		if (tile == null) {
			Vector2f slope = checkSlopeCollision(currentX, newY);
			if (slope != null) {
				currentY = slope.y;
				velocityY = 0;
				_groundedTopBottom = true;
				_onSlope = true;
			} else {
				currentY = newY;
				_groundedTopBottom = false;
			}
		} else {
			if (velocityY > 0) {
				currentY = tiles.tilesToPixelsY(tile.y) - getHeight();
			} else if (velocityY < 0) {
				currentY = tiles.tilesToPixelsY(tile.y + 1);
			}
			velocityY = 0;
			_groundedTopBottom = true;
		}

		if (enableContinuousCollision) {
			performContinuousCollisionCheck(currentX, currentY);
		}
		return _tempResult.set(currentX, currentY);
	}

	/**
	 * 连续碰撞检测
	 * 
	 * @param x
	 * @param y
	 */
	private void performContinuousCollisionCheck(float x, float y) {
		if (tiles == null) {
			return;
		}
		if (MathUtils.abs(velocityX) > getWidth() * 0.5f) {
			Vector2f midTile = tiles.getTileCollision(this, x + velocityX * 0.5f, y);
			if (midTile != null) {
				velocityX = 0;
				_groundedLeftRight = true;
			}
		}
		if (MathUtils.abs(velocityY) > getHeight() * 0.5f) {
			Vector2f midTile = tiles.getTileCollision(this, x, y + velocityY * 0.5f);
			if (midTile != null) {
				velocityY = 0;
				_groundedTopBottom = true;
			}
		}
		// 顶部检测（倒挂时）
		if (_isCeilingWalking) {
			Vector2f topTile = tiles.getTileCollision(this, x, y - getHeight() * 0.5f);
			if (topTile != null) {
				velocityY = 0;
				_groundedTopBottom = true;
			}
		}
	}

	public void dash(float speed) {
		velocityX = getDirection() == Side.TRIGHT ? speed : -speed;
		currentState = ActorState.RUN;
	}

	public void doubleJump(float jumpForce) {
		if (!_groundedTopBottom) {
			velocityY = -jumpForce;
			currentState = ActorState.JUMP;
		}
	}

	public void wallKick(float kickForceX, float kickForceY) {
		if (_isWallSliding) {
			velocityX = kickForceX * (_currentSide.getDirection() == Side.TLEFT ? 1 : -1);
			velocityY = -kickForceY;
			currentState = ActorState.JUMP;
		}
	}

	public void slide(float slideSpeed) {
		if (_groundedTopBottom) {
			velocityX *= slideSpeed;
			currentState = ActorState.RUN;
		}
	}

	public boolean isGround() {
		return _groundedLeftRight || _groundedTopBottom;
	}

	public boolean isLeftRightGround() {
		return _groundedLeftRight;
	}

	public boolean isTopBottomGround() {
		return _groundedTopBottom;
	}

	public ActionObject freeGround() {
		_groundedLeftRight = false;
		_groundedTopBottom = false;
		return this;
	}

	public TileMapCollision getTileMap() {
		return tiles;
	}

	@Override
	public Field2D getField2D() {
		return tiles == null ? null : tiles.getField2D();
	}

	public Attribute getAttribute() {
		return attribute;
	}

	public ActionObject setAttribute(Attribute attribute) {
		this.attribute = attribute;
		return this;
	}

	public Animation getAnimation() {
		return animation;
	}

	public ActionObject setAnimation(Animation a) {
		this.animation = a;
		return this;
	}

	public Side getCurrentSide() {
		return _currentSide;
	}

	public ActionObject setCurrentSide(int side) {
		_currentSide.setDirection(side);
		return this;
	}

	public ActionObject setIndex(int index) {
		if (animation instanceof AnimationStorage) {
			AnimationStorage storage = (AnimationStorage) animation;
			if (storage != null) {
				storage.playIndex(index);
			}
		}
		return this;
	}

	public int getDirection() {
		return _currentSide.getDirection();
	}

	public ActionObject setDirection(int dir) {
		this._currentSide.setDirection(dir);
		return this;
	}

	public void shoot() {
		currentState = ActorState.SHOOT;
	}

	public ActionObject updateLocation() {
		this.setLocation(getLocation().add(this._currentSide.updatePostion()));
		return this;
	}

	public ActionObject flipSide() {
		if (_currentSide.getDirection() == Side.TLEFT || _currentSide.getDirection() == Side.TRIGHT) {
			flipHorizontalSide();
		} else {
			flipVerticalSide();
		}
		return this;
	}

	public ActionObject flipHorizontalSide() {
		int dir = _currentSide.getDirection();
		if (dir == Side.TRIGHT) {
			_currentSide.setDirection(Side.TLEFT);
		} else if (dir == Side.TLEFT) {
			_currentSide.setDirection(Side.TRIGHT);
		}
		return this;
	}

	private ActionObject flipVerticalSide() {
		int dir = _currentSide.getDirection();
		if (dir == Side.TOP) {
			_currentSide.setDirection(Side.BOTTOM);
		} else if (dir == Side.BOTTOM) {
			_currentSide.setDirection(Side.TOP);
		}
		return this;
	}

	public TileMapCollision getTiles() {
		return tiles;
	}

	public ActionObject setTiles(TileMapCollision tile) {
		this.tiles = tile;
		return this;
	}

	public String getDirectionString() {
		return Side.getDirectionName(getDirection());
	}

	public boolean isFalling() {
		return velocityY > 0f;
	}

	public boolean isJumping() {
		return velocityY < 0f;
	}

	public float getVelocityX() {
		return this.velocityX;
	}

	public float getVelocityY() {
		return this.velocityY;
	}

	public ActionObject setVelocityX(final float vx) {
		this.velocityX = vx;
		limitVelocity();
		return this;
	}

	public ActionObject setVelocityY(final float vy) {
		this.velocityY = vy;
		limitVelocity();
		return this;
	}

	public ActionObject setVelocity(final float v) {
		return setVelocity(v, v);
	}

	public ActionObject setVelocity(final float vx, final float vy) {
		this.velocityX = vx;
		this.velocityY = vy;
		limitVelocity();
		return this;
	}

	public ActionObject clearVelocity() {
		return setVelocity(0f);
	}

	public Vector2f getVelocity() {
		return Vector2f.at(this.velocityX, this.velocityY);
	}

	public ActionObject addVelocity(final float vx, final float vy) {
		return setVelocity(this.velocityX + vx, this.velocityY + vy);
	}

	public ActionObject addVelocity(final Vector2f v) {
		if (v == null) {
			return this;
		}
		return addVelocity(v.x, v.y);
	}

	public ActionObject resetVelocity() {
		this.velocityX = 0f;
		this.velocityY = 0f;
		return this;
	}

	public ActionObject drag(final float drag) {
		this.velocityX *= drag;
		this.velocityY *= drag;
		return this;
	}

	public ActionObject applyDefaultDrag() {
		return drag(DEFAULT_DRAG_FACTOR);
	}

	public boolean isMovingLeft() {
		return this.velocityX < -0.1f;
	}

	public boolean isMovingRight() {
		return this.velocityX > 0.1f;
	}

	public boolean isMovingUp() {
		return this.velocityY < -0.1f;
	}

	public boolean isMovingDown() {
		return this.velocityY > 0.1f;
	}

	public boolean isIdle() {
		return MathUtils.abs(velocityX) < 0.1f && MathUtils.abs(velocityY) < 0.1f;
	}

	@Override
	public LTexture getBitmap() {
		return animation == null ? null : animation.getSpriteImage();
	}

	public ObjectState getObjectState() {
		return _currentObjectState;
	}

	public ActionObject setObjectState(ObjectState c) {
		if (c != null) {
			this._currentObjectState = c;
		}
		return this;
	}

	private void limitVelocity() {
		velocityX = MathUtils.clamp(velocityX, -MAX_VELOCITY, MAX_VELOCITY);
		velocityY = MathUtils.clamp(velocityY, -MAX_VELOCITY, MAX_VELOCITY);
	}

	public ActionObject limitPosition(float minX, float minY, float maxX, float maxY) {
		float x = MathUtils.clamp(getX(), minX, maxX);
		float y = MathUtils.clamp(getY(), minY, maxY);
		setLocation(x, y);
		return this;
	}

	public ActionObject stopMovement() {
		clearVelocity();
		clearForces();
		return this;
	}

	protected void onPostProcess(long elapsedTime) {

	}

	protected Vector2f checkSlopeCollision(float x, float y) {
		if (tiles == null) {
			return null;
		}
		Vector2f tileL = tiles.getTileCollision(this, x, y + getHeight() * 0.9f);
		Vector2f tileR = tiles.getTileCollision(this, x + getWidth(), y + getHeight() * 0.9f);
		if (tileL != null || tileR != null) {
			Vector2f use = tileL != null ? tileL : tileR;
			return Vector2f.at(x, tiles.tilesToPixelsY(use.y));
		}
		return null;
	}

	public boolean isWallSliding() {
		return _isWallSliding;
	}

	public boolean isOnSlope() {
		return _onSlope;
	}

	public boolean isWallSlideEnabled() {
		return _wallSlideEnabled;
	}

	public void setWallSlideEnabled(boolean w) {
		this._wallSlideEnabled = w;
	}

	public float getWallBounceFactor() {
		return _wallBounceFactor;
	}

	public void setWallBounceFactor(float w) {
		this._wallBounceFactor = w;
	}

	public float getWallSlideSpeed() {
		return _wallSlideSpeed;
	}

	public void setWallSlideSpeed(float w) {
		this._wallSlideSpeed = w;
	}

	public float getGravityScale() {
		return gravityScale;
	}

	public void setGravityScale(float gravityScale) {
		this.gravityScale = gravityScale;
	}

	public float getFrictionGround() {
		return frictionGround;
	}

	public void setFrictionGround(float frictionGround) {
		this.frictionGround = frictionGround;
	}

	public float getFrictionAir() {
		return frictionAir;
	}

	public void setFrictionAir(float frictionAir) {
		this.frictionAir = frictionAir;
	}

	public float getMaxFallSpeed() {
		return maxFallSpeed;
	}

	public void setMaxFallSpeed(float maxFallSpeed) {
		this.maxFallSpeed = maxFallSpeed;
	}

	public float getMaxVelocity() {
		return maxVelocity;
	}

	public float getMinMoveThreshold() {
		return minMoveThreshold;
	}

	private void cleanMicroVelocity() {
		if (MathUtils.abs(velocityX) < minMoveThreshold) {
			velocityX = 0;
		}
		if (MathUtils.abs(velocityY) < minMoveThreshold) {
			velocityY = 0;
		}
	}

	public boolean checkGroundedPrecise() {
		if (tiles == null) {
			return false;
		}
		float centerX = getX() + getWidth() * 0.5f;
		float checkY = getY() + getHeight() + 1f;
		return tiles.getTileCollision(this, centerX, checkY) != null;
	}

	public boolean checkForwardCollision() {
		if (tiles == null) {
			return false;
		}
		float dir = getDirection() == Side.TRIGHT ? 1 : -1;
		float checkX = getX() + getWidth() * 0.5f + dir * 3f;
		float checkY = getY() + getHeight() * 0.5f;
		return tiles.getTileCollision(this, checkX, checkY) != null;
	}

	public boolean checkHeadCollision() {
		if (tiles == null) {
			return false;
		}
		return tiles.getTileCollision(this, getX() + getWidth() * 0.5f, getY() - 1f) != null;
	}

	public boolean isOnEdge() {
		if (tiles == null) {
			return false;
		}
		float footY = getY() + getHeight() + 1f;
		boolean leftCheck = tiles.getTileCollision(this, getX() - 2f, footY) == null;
		boolean rightCheck = tiles.getTileCollision(this, getX() + getWidth() + 2f, footY) == null;
		return leftCheck != rightCheck;
	}

	public boolean checkCollisionInRadius(float radius) {
		if (tiles == null) {
			return false;
		}
		float cx = getX() + getWidth() * 0.5f;
		float cy = getY() + getHeight() * 0.5f;
		for (int d = 0; d < 360; d += 45) {
			float rad = MathUtils.toRadians(d);
			float px = cx + MathUtils.cos(rad) * radius;
			float py = cy + MathUtils.sin(rad) * radius;
			if (tiles.getTileCollision(this, px, py) != null)
				return true;
		}
		return false;
	}

	public ActionObject setMaxVelocity(float maxVelocity) {
		this.maxVelocity = maxVelocity;
		return this;
	}

	public ActionObject setMinMoveThreshold(float threshold) {
		this.minMoveThreshold = threshold;
		return this;
	}

	public ActionObject setContinuousCollisionEnabled(boolean enabled) {
		this.enableContinuousCollision = enabled;
		return this;
	}

	public boolean isWallClimbing() {
		return _isWallClimbing;
	}

	public boolean isWallClimbEnabled() {
		return _wallClimbEnabled;
	}

	public void setWallClimbEnabled(boolean enabled) {
		this._wallClimbEnabled = enabled;
	}

	public float getWallClimbSpeed() {
		return _wallClimbSpeed;
	}

	public void setWallClimbSpeed(float speed) {
		this._wallClimbSpeed = speed;
	}

	public boolean isCeilingWalking() {
		return _isCeilingWalking;
	}

	public boolean isCeilingWalkEnabled() {
		return _ceilingWalkEnabled;
	}

	public void setCeilingWalkEnabled(boolean enabled) {
		this._ceilingWalkEnabled = enabled;
	}

	public float getCeilingWalkVelocityY() {
		return _ceilingWalkVelocityY;
	}

	public void setCeilingWalkVelocityY(float w) {
		this._ceilingWalkVelocityY = w;
	}

	public void setAutoFlipVertical(boolean enable) {
		this._flipVerticalAuto = enable;
	}

	public void chargeSuperJump(float chargeRate) {
		_superJumpCharge += chargeRate;
		if (_superJumpCharge > 1f)
			_superJumpCharge = 1f;
	}

	public void releaseSuperJump(float baseForce) {
		float jumpForce = baseForce * (1f + _superJumpCharge);
		velocityY = -jumpForce;
		currentState = ActorState.JUMP;
		_superJumpCharge = 0f;
	}

	public void airDash(float speed) {
		if (!_groundedTopBottom && _canAirDash) {
			velocityX = getDirection() == Side.TRIGHT ? speed : -speed;
			currentState = ActorState.DASH;
			_canAirDash = false;
		}
	}

	@Override
	public float getCenterX() {
		return getX() + getWidth() * 0.5f;
	}

	@Override
	public float getCenterY() {
		return getY() + getHeight() * 0.5f;
	}

	public boolean isFlipLock() {
		return _flipLock;
	}

	public long getFlipElapsed() {
		return _flipElapsed;
	}

	public long getFlipCoolDown() {
		return _flipCooldown;
	}

	public boolean isFlipVerticalAuto() {
		return _flipVerticalAuto;
	}

	public void addActionStateListener(ActionStateListener listener) {
		if (listener != null) {
			_listeners.add(listener);
		}
	}

	public void removeActionStateListener(ActionStateListener listener) {
		_listeners.remove(listener);
	}

	private void notifyStateChanged(ActorState oldState, ActorState newState) {
		for (ActionStateListener l : _listeners) {
			l.onStateChanged(this, oldState, newState);
		}
	}

	public String debugInfo() {
		return StringUtils.format("State: {0}, VelX: {1}, VelY: {2}, Grounded: {3}, WallSlide: {4}, Ceiling: {5}",
				currentState.name(), velocityX, velocityY, isGround(), _isWallSliding, _isCeilingWalking);
	}

	@Override
	public void toString(final StrBuilder s) {
		s.append(LSystem.LS);
		s.append(" [");
		s.append(_currentSide);
		s.append("] [Vel:");
		s.append(velocityX).append(',').append(velocityY);
		s.append("] [Ground:");
		s.append(isGround());
		s.append("]");
	}

	@Override
	public String toString() {
		final StrBuilder sbr = new StrBuilder();
		sbr.append(super.toString());
		this.toString(sbr);
		return sbr.toString();
	}

	@Override
	public int hashCode() {
		if (tiles == null) {
			return super.hashCode();
		}
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, super.hashCode());
		if (attribute != null) {
			hashCode = LSystem.unite(hashCode, attribute.hashCode());
		}
		if (animation != null) {
			hashCode = LSystem.unite(hashCode, animation.hashCode());
		}
		if (_currentObjectState != null) {
			hashCode = LSystem.unite(hashCode, _currentObjectState == ObjectState.DynamicObject ? 0 : 1);
		}
		if (tiles != null) {
			hashCode = LSystem.unite(hashCode, tiles.pixelsToTilesWidth(x()));
			hashCode = LSystem.unite(hashCode, tiles.pixelsToTilesHeight(y()));
			hashCode = LSystem.unite(hashCode, tiles.pixelsToTilesWidth(tiles.getOffset().x));
			hashCode = LSystem.unite(hashCode, tiles.pixelsToTilesHeight(tiles.getOffset().y));
			hashCode = LSystem.unite(hashCode, tiles.getWidth());
			hashCode = LSystem.unite(hashCode, tiles.getHeight());
			hashCode = LSystem.unite(hashCode, tiles.getTileWidth());
			hashCode = LSystem.unite(hashCode, tiles.getTileHeight());
			hashCode = LSystem.unite(hashCode, CollectionUtils.hashCode(tiles.getMap()));
		}
		return hashCode;
	}

	@Override
	protected void _onDestroy() {
		super._onDestroy();
		if (animation != null) {
			animation.close();
		}
		if (_forces != null) {
			_forces.clear();
		}
	}

}
