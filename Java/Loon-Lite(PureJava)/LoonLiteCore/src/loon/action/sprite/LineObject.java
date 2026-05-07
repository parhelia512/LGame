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

import loon.LSystem;
import loon.action.collision.ContinuousForce;
import loon.action.collision.TimedForce;
import loon.action.map.TileMapCollision;
import loon.canvas.LColor;
import loon.geom.Circle;
import loon.geom.Ellipse;
import loon.geom.Line;
import loon.geom.PointI;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.geom.XYZW;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.TArray;
import loon.utils.timer.Duration;

/**
 * 一个可以做出各种'线'动作的ActionObject实现。
 * 主要用途有两个：1是充当激光之类直线攻击效果。2是充当类似蜘蛛人蜘蛛线的作用，即把角色通过这条线'拉'到指定位置上去，或者把敌人'拉'过来攻击
 */
public class LineObject extends ActionObject {

	public enum GrappleState {
		IDLE, FIRING, ATTACHED, PULLING, RETRACTING
	}

	public enum RopeStyle {
		NORMAL, BRAID
	}

	public enum PullMode {
		NONE, PULL_SELF_TO_TARGET, PULL_TARGET_TO_SELF, PULL_BOTH,
	}

	public static interface GrappleListener {
		void onFired();

		void onAttached();

		void onRetracted();

		void onEnemyHit(ActionObject enemy);

		void onTileHit(int tileX, int tileY);
	}

	private final LColor _finalColor = new LColor();
	private final Line _objectLine;
	private int _thickness;

	private GrappleState _state = GrappleState.IDLE;
	private float _originX, _originY;
	private float _targetX, _targetY;
	private float _progress = 0f;
	private float _fireSpeed = 2.5f;
	private float _retractSpeed = 3.5f;
	private float _holdTime = 0.15f;
	private float _holdElapsed = 0f;

	private boolean _glow = true;
	private boolean _moveToEnemy = false;

	private int _segments = 10;
	private float _colorAlphaMin = 0.25f;
	private GrappleListener _listener;
	private float _timeAccum = 0f;

	private RopeStyle _ropeStyle = RopeStyle.NORMAL;
	private int _strands = 2;
	private float _twistAmplitude = 6f;
	private float _twistFrequency = 0.02f;
	TArray<float[]> _basePts = new TArray<float[]>();
	private ActionObject _follower = null;
	private float _followerRatio = 0f;
	private float _followerLateral = 0f;
	private float _followerFollowSpeed = 8.0f;

	private int _tileSampleSteps = 48;
	private float _maxRange = 900f;
	private float _autoSampleStep = 8f;

	private PullMode _pullMode = PullMode.NONE;
	private ActionObject _owner = null;
	private ActionObject _attachedEnemy = null;
	private PointI _attachedTile = null;
	private float _pullStrength = 1200f;
	private float _pullMaxSpeed = 600f;
	private float _pullStopDistance = 12f;
	private boolean _pullUseForce = false;

	private ContinuousForce _ownerContinuousForce = null;
	private ContinuousForce _enemyContinuousForce = null;
	private boolean _continuousForceMode = true;

	private int _pullStallFrames = 0;
	private float _pullStallCooldown = 0f;
	private float _ownerMaxSpeedWhilePull = 900f;
	private float _ownerDamping = 0.85f;

	public LineObject(ActionObject player, TileMapCollision map) {
		this(player == null ? 0f : player.getX(), player == null ? 0f : player.getY(), player, map);
	}

	public LineObject(float x, float y, ActionObject player, TileMapCollision map) {
		this(x, y, 0f, 0f, player, map);
	}

	public LineObject(float x, float y, float dw, float dh) {
		this(x, y, dw, dh, null, null);
	}

	public LineObject(float x, float y, float dw, float dh, ActionObject player, TileMapCollision map) {
		this(x, y, dw, dh, null, player, map);
	}

	public LineObject(float x, float y, float dw, float dh, Animation animation, ActionObject player,
			TileMapCollision map) {
		super(x, y, dw, dh, animation, map);
		_objectLine = new Line(x, y, dw, dh);
		_thickness = 1;
		_originX = getCenterX();
		_originY = getCenterY();
		_targetX = getCenterX();
		_targetY = getCenterY();
		_progress = 0f;
		_pullUseForce = true;
		setOwner(player);
		setRopeStyle(RopeStyle.NORMAL);
		setPullMode(PullMode.PULL_SELF_TO_TARGET);
		setVisible(false);
		setColor(LColor.yellow);
		setRepaint(true);
	}

	@Override
	public void setY(float y) {
		super.setY(y);
		_originY = getCenterY();
		_objectLine.setY1(_originY);
	}

	@Override
	public void setX(float x) {
		super.setX(x);
		_originX = getCenterX();
		_objectLine.setX1(_originX);
	}

	@Override
	public void setWidth(float x2) {
		super.setWidth(x2);
	}

	@Override
	public void setHeight(float y2) {
		super.setHeight(y2);
	}

	public void setThickness(int t) {
		_thickness = t;
	}

	@Override
	public LineObject setSize(float x2, float y2) {
		super.setSize(x2, y2);
		return this;
	}

	public void setRopeStyle(RopeStyle style) {
		this._ropeStyle = style;
		if (style == RopeStyle.NORMAL) {
			setStrands(1);
		} else {
			setStrands(2);
		}
	}

	public void setStrands(int count) {
		this._strands = MathUtils.max(1, count);
	}

	public void setTwist(float amplitude, float frequency) {
		this._twistAmplitude = MathUtils.max(0f, amplitude);
		this._twistFrequency = MathUtils.max(0f, frequency);
	}

	public void setSegments(int segs) {
		this._segments = MathUtils.max(1, segs);
	}

	public void setGlow(boolean g) {
		this._glow = g;
	}

	public void setMaxRange(float r) {
		this._maxRange = MathUtils.max(0f, r);
	}

	public void setAutoSampleStep(float s) {
		this._autoSampleStep = MathUtils.max(1f, s);
	}

	public void setGrappleListener(GrappleListener l) {
		this._listener = l;
	}

	public void setOwner(ActionObject owner) {
		this._owner = owner;
	}

	public void setPullMode(PullMode mode) {
		this._pullMode = (mode == null) ? PullMode.NONE : mode;
		if (_pullMode == PullMode.PULL_TARGET_TO_SELF) {
			setMoveToEnemy(true);
		}
	}

	public void setDefaultPullSelfToTarget() {
		setPullParams(800, 300, 2f, true);
		setPullMode(LineObject.PullMode.PULL_SELF_TO_TARGET);
		setContinuousForceMode(true);
	}

	public void setPullParams(float strength, float maxSpeed, float stopDistance) {
		setPullParams(strength, maxSpeed, stopDistance, _pullUseForce);
	}

	public void setPullParams(float strength, float maxSpeed, float stopDistance, boolean useForce) {
		this._pullStrength = MathUtils.max(0f, strength);
		this._pullMaxSpeed = MathUtils.max(0f, maxSpeed);
		this._pullStopDistance = MathUtils.max(0f, stopDistance);
		this._pullUseForce = useForce;
	}

	public void setContinuousForceMode(boolean continuous) {
		this._continuousForceMode = continuous;
	}

	public void attachFollower(ActionObject obj, float alongRatio, float lateralOffset) {
		if (obj == null) {
			return;
		}
		this._follower = obj;
		this._followerRatio = MathUtils.clamp(alongRatio, 0f, 1f);
		this._followerLateral = lateralOffset;
	}

	public void detachFollower() {
		this._follower = null;
	}

	public void setFollowerFollowSpeed(float s) {
		this._followerFollowSpeed = MathUtils.max(0.01f, s);
	}

	public void fireOwnerTo(float tx, float ty) {
		fireTo(tx, ty, _owner);
	}

	public void fireOwnerTo(float tx, float ty, TArray<ActionObject> enemies) {
		fireTo(tx, ty, _fireSpeed, _retractSpeed, _holdTime, _owner, enemies);
	}

	public void fireTo(float tx, float ty) {
		fireTo(tx, ty, _fireSpeed, _retractSpeed, _holdTime);
	}

	public void fireTo(float tx, float ty, float fireSpeed, float retractSpeed, float holdTime) {
		fireTo(tx, ty, fireSpeed, retractSpeed, holdTime, null, null);
	}

	public void fireTo(float tx, float ty, ActionObject owner) {
		if (owner != null) {
			fireTo(tx, ty, _fireSpeed, _retractSpeed, _holdTime, owner, null);
		} else {
			fireTo(tx, ty, _fireSpeed, _retractSpeed, _holdTime, owner, null);
		}
	}

	public void fireTo(float tx, float ty, float fireSpeed, float retractSpeed, float holdTime, ActionObject owner,
			TArray<ActionObject> enemies) {
		if (!isActionCompleted() || (owner != null && !owner.isActionCompleted())
				|| (getScreen() != null && !getScreen().isActionCompleted())) {
			return;
		}
		float sx, sy;
		if (owner != null) {
			sx = owner.getCenterX();
			sy = owner.getCenterY();
			this._owner = owner;
		} else {
			sx = getCenterX();
			sy = getCenterY();
		}

		if (tiles != null) {
			sx = tiles.offsetXPixel(sx);
			sy = tiles.offsetYPixel(sy);
			tx = tiles.offsetXPixel(tx);
			ty = tiles.offsetYPixel(ty);
		}
		this._attachedEnemy = null;
		this._attachedTile = null;

		if (MathUtils.isNan(sx) || MathUtils.isNan(sy)) {
			if (owner != null) {
				sx = MathUtils.isNan(sx) ? owner.getCenterX() : sx;
				sy = MathUtils.isNan(sy) ? owner.getCenterY() : sy;
			} else {
				sx = MathUtils.isNan(sx) ? getCenterX() : sx;
				sy = MathUtils.isNan(sy) ? getCenterY() : sy;
			}
		}

		if (owner != null) {
			this._owner = owner;
		}

		this._attachedEnemy = null;
		this._attachedTile = null;

		boolean autoTarget = MathUtils.isNan(tx) || MathUtils.isNan(ty);

		float foundX = sx, foundY = sy;
		boolean found = false;

		if (autoTarget) {
			float dirX = MathUtils.isNan(tx) ? 1f : tx;
			float dirY = MathUtils.isNan(ty) ? 0f : ty;
			float len = MathUtils.sqrt(dirX * dirX + dirY * dirY);
			if (len == 0f) {
				dirX = 1f;
				dirY = 0f;
				len = 1f;
			}
			dirX /= len;
			dirY /= len;
			if (enemies != null && !enemies.isEmpty()) {
				ActionObject best = null;
				float bestDist = Float.MAX_VALUE;
				for (ActionObject e : enemies) {
					if (e == null) {
						continue;
					}
					float ex = e.getCenterX(), ey = e.getCenterY();
					float vx = ex - sx, vy = ey - sy;
					float proj = vx * dirX + vy * dirY;
					if (proj <= 0f || proj > _maxRange) {
						continue;
					}
					float perpX = vx - dirX * proj, perpY = vy - dirY * proj;
					float perpDist = MathUtils.sqrt(perpX * perpX + perpY * perpY);
					float radius = MathUtils.max(e.getWidth(), e.getHeight()) * 0.6f;
					if (perpDist <= radius + 8f) {
						if (proj < bestDist) {
							bestDist = proj;
							best = e;
						}
					}
				}
				if (best != null) {
					found = true;
					_attachedEnemy = best;
					foundX = best.getCenterX();
					foundY = best.getCenterY();
					if (_listener != null) {
						_listener.onEnemyHit(best);
					}
				}
			}

			if (!found && tiles != null) {
				float step = _autoSampleStep;
				for (float d = step; d <= _maxRange; d += step) {
					float px = sx + dirX * d, py = sy + dirY * d;
					Vector2f tile = tiles.getTileCollision(this, px, py);
					if (tile != null) {
						found = true;
						_attachedTile = new PointI((int) tile.x(), (int) tile.y());
						float tileCenterX = tiles.tilesToPixelsX(tile.x()) + tiles.getTileWidth() * 0.5f;
						float tileCenterY = tiles.tilesToPixelsY(tile.y()) + tiles.getTileHeight() * 0.5f;
						foundX = tileCenterX;
						foundY = tileCenterY;
						if (_listener != null) {
							_listener.onTileHit((int) tile.x(), (int) tile.y());
						}
						break;
					}
				}
			}

			if (found) {
				tx = foundX;
				ty = foundY;
			} else {
				tx = sx + dirX * _maxRange;
				ty = sy + dirY * _maxRange;
			}

		} else {
			ISprite sprite = null;
			if (tiles != null) {
				sprite = _moveToEnemy ? tiles.getObject(tx, ty) : null;
				tx = tiles.pixelsToTilesWidth(tx);
				ty = tiles.pixelsToTilesHeight(ty);
				if (sprite != null && (sprite instanceof ActionObject) && sprite != owner && sprite != this) {
					found = true;
					_attachedEnemy = (ActionObject) sprite;
					foundX = _attachedEnemy.getCenterX();
					foundY = _attachedEnemy.getCenterY();
					if (_listener != null) {
						_listener.onEnemyHit(_attachedEnemy);
					}
				} else if (!tiles.isHit((int) tx, (int) ty)) {
					found = true;
					_attachedTile = new PointI((int) tx, (int) ty);
					float tileCenterX = tiles.tilesToPixelsX(tx) + tiles.getTileWidth() * 0.5f;
					float tileCenterY = tiles.tilesToPixelsY(ty) + tiles.getTileHeight() * 0.5f;
					foundX = tileCenterX;
					foundY = tileCenterY;
					if (_listener != null) {
						_listener.onTileHit((int) tx, (int) ty);
					}
				}
			}
		}

		this._originX = sx;
		this._originY = sy;
		if (tiles != null) {
			this._targetX = tiles.tilesToPixelsX(tx) + tiles.getTileWidth() * 0.5f;
			this._targetY = tiles.tilesToPixelsY(ty) + tiles.getTileHeight() * 0.5f;
		} else {
			this._targetX = tx + LSystem.LAYER_TILE_SIZE * 0.5f;
			this._targetY = ty + LSystem.LAYER_TILE_SIZE * 0.5f;
		}
		this._progress = 0f;
		this._holdElapsed = 0f;
		if (fireSpeed > 0) {
			this._fireSpeed = fireSpeed;
		}
		if (retractSpeed > 0) {
			this._retractSpeed = retractSpeed;
		}
		if (holdTime >= 0) {
			this._holdTime = holdTime;
		}
		this._state = GrappleState.FIRING;
		this.setVisible(true);
		this._timeAccum = 0f;
		_objectLine.setX1(_originX);
		_objectLine.setY1(_originY);
		_objectLine.setX2(_originX);
		_objectLine.setY2(_originY);
		if (_listener != null) {
			_listener.onFired();
		}
	}

	public void attachInstant(float sx, float sy, float tx, float ty) {
		this._originX = sx;
		this._originY = sy;
		this._targetX = tx;
		this._targetY = ty;
		this._progress = 1f;
		this._state = GrappleState.ATTACHED;
		this.setVisible(true);
		if (_listener != null) {
			_listener.onAttached();
		}
	}

	public void retractNow() {
		if (_state != GrappleState.IDLE) {
			_state = GrappleState.RETRACTING;
		}
	}

	@Override
	protected void onProcess(long elapsedTime) {
		update(Duration.toS(elapsedTime));
		super.onProcess(elapsedTime);
	}

	public void update(float dt) {
		if (_state == GrappleState.IDLE) {
			return;
		}
		_timeAccum += dt;

		if (_owner != null) {
			_originX = _owner.getCenterX();
			_originY = _owner.getCenterY();
			_objectLine.setX1(_originX);
			_objectLine.setY1(_originY);
		}

		if (_state == GrappleState.FIRING) {
			_progress += _fireSpeed * dt;
			if (_progress >= 1f) {
				_progress = 1f;
				_state = GrappleState.ATTACHED;
				_holdElapsed = 0f;
				if (_listener != null) {
					_listener.onAttached();
				}
				PointI tile = firstTileHitAlongLine();
				if (tile != null && _listener != null) {
					_listener.onTileHit(tile.x, tile.y);
				}
			}
			applyProgressToLineForFiring(_progress);
		} else if (_state == GrappleState.ATTACHED) {
			_holdElapsed += dt;
			applyProgressToLineForFiring(1f);

			if (_pullMode != PullMode.NONE) {
				updatePulling(dt);
			}
			if (_holdElapsed >= _holdTime) {
				_state = GrappleState.RETRACTING;
			}
		} else if (_state == GrappleState.RETRACTING) {
			_progress -= _retractSpeed * dt;
			if (_progress <= 0f) {
				_progress = 0f;
				_state = GrappleState.IDLE;
				setVisible(false);
				_objectLine.setX1(_originX);
				_objectLine.setY1(_originY);
				_objectLine.setX2(_originX);
				_objectLine.setY2(_originY);
				_attachedEnemy = null;
				_attachedTile = null;
				stopAndClearContinuousForces();
				if (_owner != null) {
					_owner.setVelocity(0, 0);
					_owner.exitPulling();
				}
				if (_listener != null) {
					_listener.onRetracted();
				}
				return;
			}
			applyProgressToLineForRetracting(_progress);
		}

		if (_follower != null && isVisible()) {
			float desiredRatio = computeFollowerTargetRatio();
			float alpha = 1f - MathUtils.exp(-_followerFollowSpeed * dt);
			_followerRatio = MathUtils.lerp(_followerRatio, desiredRatio, MathUtils.clamp(alpha, 0f, 1f));
			float[] pos = pointOnVisibleLine(_followerRatio);
			if (pos != null) {
				float[] tangent = tangentOnVisibleLine(_followerRatio);
				if (tangent != null) {
					float tx = tangent[0], ty = tangent[1];
					float tlen = MathUtils.sqrt(tx * tx + ty * ty);
					if (tlen != 0f) {
						tx /= tlen;
						ty /= tlen;
					}
					float nx = -ty, ny = tx;
					float fx = pos[0] + nx * _followerLateral;
					float fy = pos[1] + ny * _followerLateral;
					_follower.moveLocation(fx - _follower.getWidth() * 0.5f, fy - _follower.getHeight() * 0.5f);
				} else {
					_follower.moveLocation(pos[0] - _follower.getWidth() * 0.5f, pos[1] - _follower.getHeight() * 0.5f);
				}
			}
		}
	}

	private float computeFollowerTargetRatio() {
		if (_state == GrappleState.FIRING) {
			return MathUtils.clamp(_progress - 0.05f, 0f, 1f);
		}
		if (_state == GrappleState.ATTACHED) {
			return 1f;
		}
		if (_state == GrappleState.RETRACTING) {
			return MathUtils.clamp(_progress, 0f, 1f);
		}
		return 0.5f;
	}

	private void updatePulling(float dt) {
		if (_owner == null)
			return;

		if (_attachedTile == null && _attachedEnemy == null) {
			return;
		}

		final float STALL_SPEED_SQ = 1f;

		if (_pullStallCooldown > 0f) {
			_pullStallCooldown = MathUtils.max(0f, _pullStallCooldown - dt);
			Vector2f vv = _owner.getVelocity();
			_owner.setVelocity(vv.x * 0.85f, vv.y * 0.85f);
			return;
		}

		float tx = _targetX, ty = _targetY;

		if (_attachedEnemy != null) {
			tx = _attachedEnemy.getCenterX();
			ty = _attachedEnemy.getCenterY();
		} else if (_attachedTile != null && tiles != null) {
			float tileLeft = tiles.tilesToPixelsX(_attachedTile.x);
			float tileTop = tiles.tilesToPixelsY(_attachedTile.y);
			tx = tileLeft + tiles.getTileWidth() / 2;
			ty = tileTop + tiles.getTileHeight() / 2;
		}

		if (tiles != null && (_attachedEnemy == null)) {
			float stageWidth = tiles.getWidth();
			float stageHeight = tiles.getHeight();

			final float EDGE_MARGIN = LSystem.LAYER_TILE_SIZE;
			boolean isOutsideMap = (tx < -EDGE_MARGIN || tx > stageWidth + EDGE_MARGIN || ty < -EDGE_MARGIN
					|| ty > stageHeight + EDGE_MARGIN);

			if (isOutsideMap) {
				float refX = _owner.getCenterX();
				float refY = _owner.getCenterY();

				float ddx = tx - refX;
				float ddy = ty - refY;

				float maxPullDist = 600f;
				float distSq = ddx * ddx + ddy * ddy;

				if (distSq > maxPullDist * maxPullDist) {
					float dist = MathUtils.sqrt(distSq);
					float scale = maxPullDist / dist;
					tx = refX + ddx * scale;
					ty = refY + ddy * scale;

				} else {
					tx = MathUtils.clamp(tx, EDGE_MARGIN, stageWidth - EDGE_MARGIN);
					ty = MathUtils.clamp(ty, EDGE_MARGIN, stageHeight - EDGE_MARGIN);
				}
			}
		}

		float ox = _owner.getCenterX(), oy = _owner.getCenterY();
		float dx = tx - ox, dy = ty - oy;
		float dist = MathUtils.sqrt(dx * dx + dy * dy);

		if (dist <= _pullStopDistance) {
			if (_attachedEnemy != null
					&& (_pullMode == PullMode.PULL_TARGET_TO_SELF || _pullMode == PullMode.PULL_BOTH)) {
				_attachedEnemy.setVelocity(0, 0);
				_attachedEnemy.exitPulling();
			}

			stopAndClearContinuousForces();
			_owner.setVelocity(0, 0);
			_owner.exitPulling();
			_state = GrappleState.RETRACTING;
			return;
		}

		float dirX = dx / (dist == 0f ? 1f : dist);
		float dirY = dy / (dist == 0f ? 1f : dist);

		Vector2f vel = _owner.getVelocity();
		float speedSq = vel.x * vel.x + vel.y * vel.y;
		boolean isStalled = (speedSq < STALL_SPEED_SQ);
		boolean nearTarget = dist < 50f;
		boolean isBlockedByWall = false;

		if (_owner._groundedLeftRight) {
			if (MathUtils.abs(dirX) > 0.1f) {
				isBlockedByWall = true;
			}
		}
		if (_owner._groundedTopBottom) {
			if (MathUtils.abs(dirY) > 0.1f) {
				isBlockedByWall = true;
			}
		}

		if (isStalled && nearTarget && isBlockedByWall) {
			_pullStallFrames++;
			if (_pullStallFrames > 2) {
				stopAndClearContinuousForces();
				_owner.setVelocity(0, 0);
				_owner.exitPulling();
				_state = GrappleState.RETRACTING;
				return;
			}
		} else {
			_pullStallFrames = 0;
		}

		if (_pullUseForce) {
			if (_continuousForceMode) {
				if ((_pullMode == PullMode.PULL_SELF_TO_TARGET || _pullMode == PullMode.PULL_BOTH)) {
					if (_ownerContinuousForce == null) {
						_ownerContinuousForce = new ContinuousForce("rope_owner_cont", dirX * _pullStrength,
								dirY * _pullStrength);
						_owner.applyForce(_ownerContinuousForce);
					} else {
						_ownerContinuousForce.setVector(dirX * _pullStrength, dirY * _pullStrength);
					}
				}
				if ((_pullMode == PullMode.PULL_TARGET_TO_SELF || _pullMode == PullMode.PULL_BOTH)
						&& _attachedEnemy != null) {
					if (_enemyContinuousForce == null) {
						float ex = _attachedEnemy.getCenterX(), ey = _attachedEnemy.getCenterY();
						float dx2 = _owner.getCenterX() - ex, dy2 = _owner.getCenterY() - ey;
						float d2 = MathUtils.sqrt(dx2 * dx2 + dy2 * dy2);
						float ndx = d2 == 0f ? 0f : dx2 / d2;
						float ndy = d2 == 0f ? 0f : dy2 / d2;
						_enemyContinuousForce = new ContinuousForce("rope_enemy_cont", ndx * _pullStrength,
								ndy * _pullStrength);
						_attachedEnemy.applyForce(_enemyContinuousForce);
						_attachedEnemy.enterPulling();
					} else {
						float ex = _attachedEnemy.getCenterX(), ey = _attachedEnemy.getCenterY();
						float dx2 = _owner.getCenterX() - ex, dy2 = _owner.getCenterY() - ey;
						float d2 = MathUtils.sqrt(dx2 * dx2 + dy2 * dy2);
						float ndx = d2 == 0f ? 0f : dx2 / d2;
						float ndy = d2 == 0f ? 0f : dy2 / d2;
						_enemyContinuousForce.setVector(ndx * _pullStrength, ndy * _pullStrength);
					}
				}
			} else {
				long durationMs = Duration.ofS(dt);
				if (_pullMode == PullMode.PULL_SELF_TO_TARGET || _pullMode == PullMode.PULL_BOTH) {
					TimedForce f = new TimedForce("rope_pull_owner", dirX * _pullStrength, dirY * _pullStrength,
							durationMs);
					_owner.applyForce(f);
				}
				if ((_pullMode == PullMode.PULL_TARGET_TO_SELF || _pullMode == PullMode.PULL_BOTH)
						&& _attachedEnemy != null) {
					float ex = _attachedEnemy.getCenterX(), ey = _attachedEnemy.getCenterY();
					float dx2 = _owner.getCenterX() - ex, dy2 = _owner.getCenterY() - ey;
					float d2 = MathUtils.sqrt(dx2 * dx2 + dy2 * dy2);
					float ndx = d2 == 0f ? 0f : dx2 / d2;
					float ndy = d2 == 0f ? 0f : dy2 / d2;
					TimedForce f2 = new TimedForce("rope_pull_enemy", ndx * _pullStrength, ndy * _pullStrength,
							durationMs);
					_attachedEnemy.applyForce(f2);
					_attachedEnemy.enterPulling();
				}
			}
		} else {
			float dv = _pullStrength * dt;
			if (_pullMode == PullMode.PULL_SELF_TO_TARGET || _pullMode == PullMode.PULL_BOTH) {
				Vector2f v = _owner.getVelocity();
				float vmag = MathUtils.sqrt(v.x * v.x + v.y * v.y);
				float desiredV = MathUtils.min(_pullMaxSpeed, vmag + dv);
				float desiredVx = dirX * desiredV;
				float desiredVy = dirY * desiredV;
				_owner.addVelocity(desiredVx - v.x, desiredVy - v.y);
			}
			if ((_pullMode == PullMode.PULL_TARGET_TO_SELF || _pullMode == PullMode.PULL_BOTH)
					&& _attachedEnemy != null) {
				ActionObject target = _attachedEnemy;
				float txc = target.getCenterX(), tyc = target.getCenterY();
				float dx2 = _owner.getCenterX() - txc, dy2 = _owner.getCenterY() - tyc;
				float dist2 = MathUtils.sqrt(dx2 * dx2 + dy2 * dy2);
				if (dist2 <= _pullStopDistance) {
					target.setVelocity(0, 0);
					_owner.exitPulling();
					_state = GrappleState.RETRACTING;
					return;
				}
				float dirTx = dx2 / (dist2 == 0f ? 1f : dist2);
				float dirTy = dy2 / (dist2 == 0f ? 1f : dist2);
				Vector2f tv = target.getVelocity();
				float tvmag = MathUtils.sqrt(tv.x * tv.x + tv.y * tv.y);
				float desiredV = MathUtils.min(_pullMaxSpeed, tvmag + dv);
				float desiredVx = dirTx * desiredV;
				float desiredVy = dirTy * desiredV;
				target.addVelocity(desiredVx - tv.x, desiredVy - tv.y);
			}
		}
		fixOwnerVelocity(dist, dirX, dirY);
	}

	private void fixOwnerVelocity(float dist, float dirX, float dirY) {
		Vector2f curV = _owner.getVelocity();
		float curSpeed = MathUtils.sqrt(curV.x * curV.x + curV.y * curV.y);
		if (dist < 50f) {
			float springK = 0.12f;
			float displacement = dist - _pullStopDistance;
			float forceX = dirX * displacement * springK;
			float forceY = dirY * displacement * springK;
			curV.x += forceX;
			curV.y += forceY;
		}
		if (curSpeed > _ownerMaxSpeedWhilePull) {
			float scale = _ownerMaxSpeedWhilePull / (curSpeed == 0f ? 1f : curSpeed);
			curV.x *= scale;
			curV.y *= scale;
		} else {
			float damping = _ownerDamping;
			curV.x *= damping;
			curV.y *= damping;
		}
		_owner.setVelocity(curV.x, curV.y);
	}

	private void stopAndClearContinuousForces() {
		if (_ownerContinuousForce != null) {
			_ownerContinuousForce.stop();
			_ownerContinuousForce = null;
		}
		if (_enemyContinuousForce != null) {
			_enemyContinuousForce.stop();
			_enemyContinuousForce = null;
		}
		exitPulling();
		if (_owner != null) {
			_owner.exitPulling();
		}
	}

	private void applyProgressToLineForFiring(float p) {
		float e = smoothStep(p);
		float ex = lerp(_originX, _targetX, e);
		float ey = lerp(_originY, _targetY, e);
		_objectLine.setX1(_originX);
		_objectLine.setY1(_originY);
		_objectLine.setX2(ex);
		_objectLine.setY2(ey);
	}

	private void applyProgressToLineForRetracting(float p) {
		float e = smoothStep(p);
		float sx = lerp(_originX, _targetX, 1f - e);
		float sy = lerp(_originY, _targetY, 1f - e);
		_objectLine.setX1(sx);
		_objectLine.setY1(sy);
		_objectLine.setX2(_targetX);
		_objectLine.setY2(_targetY);
	}

	private float[] pointOnVisibleLine(float ratio) {
		float x1 = _objectLine.getX1(), y1 = _objectLine.getY1();
		float x2 = _objectLine.getX2(), y2 = _objectLine.getY2();
		float rx = lerp(x1, x2, ratio);
		float ry = lerp(y1, y2, ratio);
		return new float[] { rx, ry };
	}

	private float[] tangentOnVisibleLine(float ratio) {
		float x1 = _objectLine.getX1(), y1 = _objectLine.getY1();
		float x2 = _objectLine.getX2(), y2 = _objectLine.getY2();
		return new float[] { x2 - x1, y2 - y1 };
	}

	public PointI firstTileHitAlongLine() {
		if (tiles == null) {
			return null;
		}
		float x1 = _objectLine.getX1(), y1 = _objectLine.getY1();
		float x2 = _objectLine.getX2(), y2 = _objectLine.getY2();
		for (int i = 0; i <= _tileSampleSteps; i++) {
			float t = i / (float) _tileSampleSteps;
			float px = lerp(x1, x2, t), py = lerp(y1, y2, t);
			Vector2f tile = tiles.getTileCollision(this, px, py);
			if (tile != null) {
				return new PointI(tile.x(), tile.y());
			}
		}
		return null;
	}

	public float getContactTileTopPixel() {
		PointI hit = firstTileHitAlongLine();
		if (hit == null) {
			return MathUtils.NaN;
		}
		return tiles.tilesToPixelsY(hit.y);
	}

	public boolean isAtTileEdge(int endpointIndex, float threshold) {
		float px = (endpointIndex == 0) ? _objectLine.getX1() : _objectLine.getX2();
		float py = (endpointIndex == 0) ? _objectLine.getY1() : _objectLine.getY2();
		if (tiles == null) {
			return false;
		}
		Vector2f tile = tiles.getTileCollision(this, px, py);
		if (tile == null) {
			return false;
		}
		float tileLeft = tiles.tilesToPixelsX(tile.x);
		float tileTop = tiles.tilesToPixelsY(tile.y);
		float localX = px - tileLeft, localY = py - tileTop;
		float tileW = tiles.getTileWidth(), tileH = tiles.getTileHeight();
		if (localX <= threshold || localX >= tileW - threshold) {
			return true;
		}
		if (localY <= threshold || localY >= tileH - threshold) {
			return true;
		}
		return false;
	}

	public boolean checkEnemyCollision(ActionObject enemy, float padding) {
		if (enemy == null) {
			return false;
		}
		float ex = enemy.getCenterX();
		float ey = enemy.getCenterY();
		float[] cp = closestPointOnLine(ex, ey);
		if (cp == null) {
			return false;
		}
		float dx = ex - cp[0], dy = ey - cp[1];
		float dist = MathUtils.sqrt(dx * dx + dy * dy);
		float er = MathUtils.sqrt(enemy.getWidth() * enemy.getWidth() + enemy.getHeight() * enemy.getHeight()) * 0.5f;
		if (dist <= er + padding) {
			if (_listener != null) {
				_listener.onEnemyHit(enemy);
			}
			return true;
		}
		return false;
	}

	private float[] closestPointOnLine(float x, float y) {
		float x1 = _objectLine.getX1(), y1 = _objectLine.getY1();
		float x2 = _objectLine.getX2(), y2 = _objectLine.getY2();
		float dx = x2 - x1, dy = y2 - y1;
		float len2 = dx * dx + dy * dy;
		if (len2 == 0f) {
			return new float[] { x1, y1 };
		}
		float t = ((x - x1) * dx + (y - y1) * dy) / len2;
		t = MathUtils.clamp(t, 0f, 1f);
		return new float[] { x1 + dx * t, y1 + dy * t };
	}

	@Override
	protected void repaint(GLEx g, float x, float y) {
		if (!isVisible()) {
			return;
		}
		_basePts.clear();
		float drawOffsetX = x;
		float drawOffsetY = y;
		if (tiles != null) {
			Vector2f offset = tiles.getOffset();
			drawOffsetX -= offset.x();
			drawOffsetY -= offset.y();
		}
		float sx = _objectLine.getX1() - drawOffsetX;
		float sy = _objectLine.getY1() - drawOffsetY;
		float ex = _objectLine.getX2() - drawOffsetX;
		float ey = _objectLine.getY2() - drawOffsetY;

		if (_segments <= 1) {
			_basePts.add(new float[] { sx, sy });
			_basePts.add(new float[] { ex, ey });
		} else {
			for (int i = 0; i <= _segments; i++) {
				float t = i / (float) _segments;
				_basePts.add(new float[] { lerp(sx, ex, t), lerp(sy, ey, t) });
			}
		}
		LColor baseColor = _baseColor != null ? _baseColor : LColor.white;
		float alpha = lerp(_colorAlphaMin, 1f, _progress);
		if (_glow) {
			_finalColor.setColor(baseColor.r, baseColor.g, baseColor.b, MathUtils.min(0.22f, alpha * 0.6f));
			drawRope(g, _basePts, _finalColor, MathUtils.max(1, _thickness + 4));
		}
		drawRope(g, _basePts, _finalColor.setColor(baseColor.r, baseColor.g, baseColor.b, alpha), _thickness);
	}

	private void drawRope(GLEx g, TArray<float[]> basePts, LColor color, int thickness) {
		if (_ropeStyle == RopeStyle.NORMAL || _strands <= 1) {
			for (int i = 0; i < basePts.size() - 1; i++) {
				float[] a = basePts.get(i), b = basePts.get(i + 1);
				g.drawLine(a[0], a[1], b[0], b[1], thickness, color);
			}
			return;
		}

		for (int s = 0; s < _strands; s++) {
			TArray<float[]> pts = new TArray<float[]>();
			float phaseOffset = (s * (2f * MathUtils.PI / _strands));
			float totalLen = 0f;
			float[] lens = new float[basePts.size()];
			lens[0] = 0f;
			for (int i = 1; i < basePts.size(); i++) {
				float dx = basePts.get(i)[0] - basePts.get(i - 1)[0];
				float dy = basePts.get(i)[1] - basePts.get(i - 1)[1];
				float d = MathUtils.sqrt(dx * dx + dy * dy);
				totalLen += d;
				lens[i] = totalLen;
			}
			float invTotal = totalLen > 0f ? 1f / totalLen : 0f;
			for (int i = 0; i < basePts.size(); i++) {
				float[] p = basePts.get(i);
				float tx, ty;
				if (i == 0) {
					tx = basePts.get(1)[0] - p[0];
					ty = basePts.get(1)[1] - p[1];
				} else {
					tx = p[0] - basePts.get(i - 1)[0];
					ty = p[1] - basePts.get(i - 1)[1];
				}
				float tlen = MathUtils.sqrt(tx * tx + ty * ty);
				if (tlen != 0f) {
					tx /= tlen;
					ty /= tlen;
				}
				float nx = -ty, ny = tx;
				float along = lens[i] * invTotal;
				float cycles = totalLen * _twistFrequency;
				float phase = (2f * MathUtils.PI * cycles * along) + phaseOffset + _timeAccum * _twistFrequency * 2f;
				float amp = _twistAmplitude * (1f - _progress);
				float offset = MathUtils.sin(phase) * amp;
				float sep = (s - (_strands - 1) / 2f) * MathUtils.max(1f, _thickness * 0.6f);
				float ox = nx * (offset + sep);
				float oy = ny * (offset + sep);
				pts.add(new float[] { p[0] + ox, p[1] + oy });
			}
			for (int i = 0; i < pts.size() - 1; i++) {
				float[] a = pts.get(i), b = pts.get(i + 1);
				g.drawLine(a[0], a[1], b[0], b[1], thickness, color);
			}
		}
	}

	public LineObject setMoveToEnemy(boolean m) {
		_moveToEnemy = m;
		return this;
	}

	public boolean isMoveToEnemy() {
		return _moveToEnemy;
	}

	private float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private float smoothStep(float t) {
		return t * t * (3f - 2f * t);
	}

	public PointI getFirstHitTile() {
		return firstTileHitAlongLine();
	}

	public float getContactTileTopPixelPublic() {
		return getContactTileTopPixel();
	}

	public boolean isAtEdgeOfHitTile(int endpointIndex, float threshold) {
		return isAtTileEdge(endpointIndex, threshold);
	}

	public boolean enemyHit(ActionObject enemy, float padding) {
		return checkEnemyCollision(enemy, padding);
	}

	public boolean intersects(float x, float y) {
		return _objectLine.inPoint(x, y);
	}

	public boolean contains(float x, float y) {
		return _objectLine.contains(x, y);
	}

	public boolean contains(XY xy) {
		if (xy == null) {
			return false;
		}
		return _objectLine.contains(xy.getX(), xy.getY());
	}

	public boolean inPoint(XY pos) {
		if (pos == null) {
			return false;
		}
		return _objectLine.inPoint(pos.getX(), pos.getY());
	}

	public boolean inPoint(float x, float y) {
		return _objectLine.inPoint(x, y);
	}

	public boolean inRect(XYZW rect) {
		return _objectLine.inRect(rect);
	}

	public boolean inRect(float x, float y, float w, float h) {
		return _objectLine.inRect(x, y, w, h);
	}

	public boolean inLine(Line e) {
		return _objectLine.inLine(e);
	}

	public boolean inCircle(Circle e) {
		return _objectLine.inCircle(e);
	}

	public boolean inEllipse(Ellipse e) {
		return _objectLine.inEllipse(e);
	}

	public float distance(Vector2f point) {
		return _objectLine.distance(point);
	}

	public float distance(float x, float y) {
		return _objectLine.distance(x, y);
	}

	public Line getLine() {
		return _objectLine;
	}
}
