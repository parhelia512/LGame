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
import loon.action.map.TileMapCollision;
import loon.geom.Vector2f;
import loon.utils.MathUtils;

/**
 * 一个可以做出'跳跃'动作的ActionObject实现
 */
public class JumpObject extends ActionObject {

	public static interface JumpCheckListener {

		void check(int x, int y);

	}

	public static interface JumpListener extends JumpCheckListener {

		void update(long elapsedTime);

	}

	public static interface JumpEventListener extends JumpListener {

		void onJumpStart();

		void onJumpLand();

		void onDoubleJump();

		void onAirDash();

		void onJumpAttack();
	}

	public JumpCheckListener listener;

	public float GRAVITY;

	private float _speed;

	private float _jumpSpeed;

	private boolean _forceJump;

	private boolean _jumperTwo;

	private boolean _canJumperTwo;

	public JumpObject(float x, float y, String path) {
		this(x, y, 0, 0, Animation.getDefaultAnimation(path), null);
	}

	public JumpObject(float x, float y, Animation animation) {
		this(x, y, 0, 0, animation, null);
	}

	public JumpObject(float x, float y, Animation animation, TileMapCollision map) {
		super(x, y, 0, 0, animation, map);
		this.init();
	}

	public JumpObject(float x, float y, float dw, float dh, Animation animation, TileMapCollision map) {
		super(x, y, dw, dh, animation, map);
		this.init();
	}

	public JumpObject init() {
		this.setG(0.6f);
		this.velocityX = 0f;
		this.velocityY = 0f;
		this._forceJump = false;
		this._jumperTwo = false;
		this._canJumperTwo = true;
		return this;
	}

	@Override
	public JumpObject reset() {
		this.init();
		super.reset();
		return this;
	}

	public JumpObject stop() {
		velocityX = 0;
		return this;
	}

	public JumpObject accelerateLeft() {
		velocityX = -_speed;
		return this;
	}

	public JumpObject accelerateRight() {
		velocityX = _speed;
		return this;
	}

	public JumpObject accelerateUp() {
		velocityY = _speed;
		return this;
	}

	public JumpObject accelerateDown() {
		velocityY = -_speed;
		return this;
	}

	public JumpObject jump() {
		return jump(_jumpSpeed);
	}

	@Override
	public JumpObject jump(float force) {
		if (isGround() || _forceJump) {
			velocityY = -force;
			freeGround();
			_forceJump = false;
			if (listener != null && listener instanceof JumpEventListener) {
				((JumpEventListener) listener).onJumpStart();
			}
		} else if (_jumperTwo && _canJumperTwo) {
			velocityY = -force;
			_canJumperTwo = false;
			if (listener != null && listener instanceof JumpEventListener) {
				((JumpEventListener) listener).onDoubleJump();
			}
		}
		return this;
	}

	protected void spawnAfterimage() {

	}

	public JumpObject setForceJump(boolean forceJump) {
		this._forceJump = forceJump;
		return this;
	}

	public float getSpeed() {
		return _speed;
	}

	public JumpObject setSpeed(float speed) {
		this._speed = speed;
		return this;
	}

	public float getJumpSpeed() {
		return this._jumpSpeed;
	}

	public JumpObject setJumpSpeed(float js) {
		this._jumpSpeed = js;
		return this;
	}

	public JumpObject setJumperTwo(boolean jumperTwo) {
		this._jumperTwo = jumperTwo;
		return this;
	}

	public JumpObject setG(float g) {
		this.GRAVITY = LSystem.isScaleFPS() ? MathUtils.iceil(LSystem.toScaleFPS(g)) : g;
		this._speed = LSystem.toScaleFPS(g * 10f);
		this._jumpSpeed = _speed * 2;
		return this;
	}

	public float getG() {
		return this.GRAVITY;
	}

	@Override
	public Vector2f collisionTileMap() {
		return collisionTileMap(0f, GRAVITY);
	}

	@Override
	public Vector2f collisionTileMap(float speedX, float speedY) {
		if (tiles == null) {
			return null;
		}
		float x = getX();
		float y = getY();
		velocityX += speedX;
		velocityY += speedY;
		float newX = x + velocityX;
		float newY = y + velocityY;
		Vector2f tileX = tiles.getTileCollision(this, newX, y);
		if (tileX == null) {
			x = newX;
			_groundedLeftRight = false;
		} else {
			if (velocityX > 0) {
				x = tiles.tilesToPixelsX(tileX.x) - getWidth();
			} else if (velocityX < 0) {
				x = tiles.tilesToPixelsX(tileX.x + 1);
			}
			velocityX = 0;
			_groundedLeftRight = true;
		}
		Vector2f tileY = tiles.getTileCollision(this, x, newY);
		if (tileY == null) {
			y = newY;
			_groundedTopBottom = false;
		} else {
			if (velocityY > 0) {
				y = tiles.tilesToPixelsY(tileY.y) - getHeight();
				velocityY = 0;
				_canJumperTwo = true;
				_groundedTopBottom = true;
				resetAirDash();
				if (listener != null && listener instanceof JumpEventListener) {
					((JumpEventListener) listener).onJumpLand();
				}
			} else if (velocityY < 0) {
				y = tiles.tilesToPixelsY(tileY.y + 1);
				velocityY = 0;
				isCheck(tileY.x(), tileY.y());
			}
		}

		return _tempResult.set(x, y);
	}

	@Override
	public void onProcess(long elapsedTime) {
		super.onProcess(elapsedTime);
		if (!isStaticObject()) {
			final Vector2f pos = collisionTileMap();
			setLocation(pos.x, pos.y);
		}
		if (listener != null && listener instanceof JumpListener) {
			((JumpListener) listener).update(elapsedTime);
		}
	}

	public void bounce(float bounceForce) {
		velocityY = -bounceForce;
		if (listener != null && listener instanceof JumpEventListener) {
			((JumpEventListener) listener).onJumpStart();
		}
	}

	public void jumpCancel(float force) {
		if (!_groundedTopBottom) {
			velocityY = -force;
			_canJumperTwo = true;
		}
	}

	public void jumpAttack() {
		if (!_groundedTopBottom) {
			if (listener != null && listener instanceof JumpEventListener) {
				((JumpEventListener) listener).onJumpAttack();
			}
		}
	}

	public JumpObject isCheck(int x, int y) {
		if (listener != null) {
			listener.check(x, y);
		}
		return this;
	}

	public JumpCheckListener getJumpListener() {
		return listener;
	}

	public JumpObject setJumpListener(JumpCheckListener listener) {
		this.listener = listener;
		return this;
	}

	@Override
	public int hashCode() {
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, super.hashCode());
		hashCode = LSystem.unite(hashCode, _speed);
		hashCode = LSystem.unite(hashCode, _jumpSpeed);
		hashCode = LSystem.unite(hashCode, GRAVITY);
		return hashCode;
	}

}
