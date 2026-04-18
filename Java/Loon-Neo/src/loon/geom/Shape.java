/**
 * 
 * Copyright 2008 - 2011
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
 * @version 0.1
 */
package loon.geom;

import java.io.Serializable;

import loon.LSystem;
import loon.action.collision.CollisionHelper;
import loon.action.sprite.ISprite;
import loon.action.sprite.ShapeEntity;
import loon.canvas.LColor;
import loon.utils.CollectionUtils;
import loon.utils.IArray;
import loon.utils.MathUtils;
import loon.utils.NumberUtils;
import loon.utils.ObjectSet;
import loon.utils.StringKeyValue;
import loon.utils.StringUtils;
import loon.utils.TArray;

public abstract class Shape implements Serializable, IArray, XY, SetXY {

	public class HitResult {
		public Line line;
		public int p1;
		public int p2;
		public Vector2f pt;
	}

	/**
	 * 物理类型，强化Shape用，让Shape及其子类可以直接用于物理计算
	 */
	public enum PhysicsType {
		STATIC, DYNAMIC, KINEMATIC
	}

	public static interface ShapeCollisionListener {

		void onCollisionEnter(Shape self, Shape other);

		void onCollisionStay(Shape self, Shape other);

		void onCollisionExit(Shape self, Shape other);
	}

	public static class ShapeCollisionInfo {

		public boolean collided;
		public final Vector2f displacement;
		public final Vector2f normal;
		public final Vector2f hitPoint;
		public float overlap;
		public float depth;

		public ShapeCollisionInfo() {
			collided = false;
			displacement = new Vector2f();
			normal = new Vector2f();
			hitPoint = new Vector2f();
			overlap = 0;
			depth = 1;
		}
	}

	public final static int MAX_POINTS = 10000;
	public final static float EDGE_SCALE = 1f;
	public final static float EPSILON = 1.0E-4f;
	private static final long serialVersionUID = 1L;

	private TArray<Vector2f> _vertices;
	public float x;
	public float y;

	protected float[] originPoints;
	protected float rotation;
	protected float[] points;
	protected float[] center;
	protected float scaleX, scaleY;
	protected float maxX, maxY;
	protected float minX, minY;
	protected float boundingCircleRadius;
	protected boolean pointsDirty;
	protected transient Triangle triangle;
	protected boolean trianglesDirty;
	protected AABB aabb;
	protected RectBox rect;
	protected ShapeEntity entity;

	private final Vector2f[] vecCache = new Vector2f[16];
	private int cachedVertexCount;
	private final Vector2f cachedPosVec;
	private final Vector2f cachedCenterVec;
	private ShapeCollisionListener _collisionListener;
	private ObjectSet<Shape> _currentCollisions;
	private ObjectSet<Shape> _lastCollisions;

	// 自动碰撞检测的目标物体列表（所有需要交互的物理物体）
	protected TArray<Shape> _physicsCollisionTargets = new TArray<Shape>();
	// 是否启用物理碰撞一体化处理
	protected boolean _autoPhysicsAndCollision = true;
	// 碰撞后是否自动执行物理反弹
	protected boolean _autoResolveCollision = true;

	// 线速度
	protected Vector2f _velocity;
	// 角速度 (旋转速度)
	protected float _angularVelocity;
	// 当前受到的力矩
	protected float _torque = 0f;
	// 转动惯量
	protected float _inertia = 0f;
	// 质量 (0表示静态物体)
	protected float _mass = 1.0f;
	// 弹性系数 (0-1, 1为完美弹性碰撞)
	protected float _restitution = 0.5f;
	// 摩擦系数 (0-1)
	protected float _friction = 0.5f;
	// 是否为静态物体 (质量无穷大，不受力影响)
	protected boolean _isStatic = false;
	// 物理类型
	protected PhysicsType _physicsType = PhysicsType.STATIC;
	// 线性阻尼
	protected float _linearDamping = 0.95f;
	// 角阻尼
	protected float _angularDamping = 0.95f;
	// 碰撞图层
	protected int _collisionLayer = 1;
	// 可碰撞的图层掩码
	protected int _collisionMask = 0xFFFFFFFF;
	// 碰撞标签
	protected String _collisionTag = "default";
	// 是否启用物理运动
	protected boolean _physicsEnabled = true;

	public Shape() {
		pointsDirty = true;
		scaleX = scaleY = 1f;
		for (int i = 0; i < vecCache.length; i++) {
			vecCache[i] = new Vector2f();
		}
		cachedPosVec = new Vector2f();
		cachedCenterVec = new Vector2f();
		points = new float[0];
		center = new float[0];
		cachedVertexCount = 0;
	}

	public static Vector2f collideSAT(final Shape s1, final Shape s2) {
		if (s1 == null || s2 == null) {
			return null;
		}
		s1.checkPoints();
		s2.checkPoints();
		Vector2f MTV = null;
		float minOverlap = Float.MAX_VALUE;
		TArray<TArray<Vector2f>> verticesList = new TArray<TArray<Vector2f>>();
		verticesList.add(s1.getVertices());
		verticesList.add(s2.getVertices());
		for (TArray<Vector2f> vertices : verticesList) {
			int len = vertices.size;
			for (int i = 0; i < len; i++) {
				Vector2f a = vertices.get(i);
				Vector2f b = vertices.get((i + 1) % len);
				Vector2f edge = b.sub(a);
				Vector2f axis = new Vector2f(-edge.y, edge.x).nor();
				float[] proj1 = projectShape(s1, axis);
				float[] proj2 = projectShape(s2, axis);
				if (proj1[0] > proj2[1] || proj2[0] > proj1[1]) {
					return null;
				}
				float overlap1 = proj1[1] - proj2[0];
				float overlap2 = proj2[1] - proj1[0];
				float overlap = MathUtils.min(overlap1, overlap2);
				if (overlap < minOverlap) {
					minOverlap = overlap;
					MTV = axis.scale(minOverlap);
				}
			}
		}
		return MTV;
	}

	private static float[] projectShape(Shape shape, Vector2f axis) {
		float min = axis.dot(shape.getVertices().get(0));
		float max = min;
		for (Vector2f v : shape.getVertices().items) {
			if (v == null) {
				break;
			}
			float p = axis.dot(v);
			if (p < min) {
				min = p;
			}
			if (p > max) {
				max = p;
			}
		}
		return new float[] { min, max };
	}

	public void setCollisionListener(ShapeCollisionListener listener) {
		this._collisionListener = listener;
	}

	public ShapeCollisionListener getShapeCollisionListener() {
		return _collisionListener;
	}

	/**
	 * 初始化物理特性，让Shape能作为物理对象控制器进行物理操作，默认不开启
	 * 
	 * @param vx
	 * @param vy
	 * @param restitution
	 * @param friction
	 * @return
	 */
	public Shape initPhysics(float mass, float restitution, float friction) {
		this._mass = mass;
		this._restitution = restitution;
		this._friction = friction;
		this._isStatic = (mass <= 0f);
		if (this._velocity == null) {
			this._velocity = new Vector2f();
		}
		float width = this.getWidth();
		float height = this.getHeight();
		if (width < 1f) {
			width = 1f;
		}
		if (height < 1f) {
			height = 1f;
		}
		this._inertia = this._mass * (width * width + height * height) / 12f;
		if (this._isStatic) {
			this._inertia = Float.MAX_VALUE;
		}
		if (this._currentCollisions == null) {
			this._currentCollisions = new ObjectSet<Shape>();
		}
		if (this._lastCollisions == null) {
			this._lastCollisions = new ObjectSet<Shape>();
		}
		if (_physicsCollisionTargets == null) {
			_physicsCollisionTargets = new TArray<Shape>();
		}
		this._physicsType = _isStatic ? PhysicsType.STATIC : PhysicsType.DYNAMIC;
		this._linearDamping = 0.95f;
		this._angularDamping = 0.95f;
		return this;
	}

	public PhysicsType getPhysicsType() {
		return _physicsType;
	}

	public void setPhysicsType(PhysicsType type) {
		this._physicsType = type;
		this._isStatic = type == PhysicsType.STATIC;
		if (_isStatic) {
			_mass = 0;
			_inertia = Float.MAX_VALUE;
		}
	}

	public Shape setPhysics(float mass, float restitution, float friction) {
		this._mass = mass;
		this._restitution = restitution;
		this._friction = friction;
		this._isStatic = (mass <= 0f);
		if (this._velocity == null) {
			this._velocity = new Vector2f();
		}
		return this;
	}

	public Shape setVelocity(float vx, float vy) {
		if (this._velocity == null) {
			this._velocity = new Vector2f();
		}
		this._velocity.set(vx, vy);
		return this;
	}

	public void applyForce(Vector2f force) {
		if (_physicsType != PhysicsType.DYNAMIC || _mass <= 0) {
			return;
		}
		_velocity.addSelf(force.x / _mass, force.y / _mass);
	}

	public void applyImpulse(Vector2f impulse) {
		if (_physicsType != PhysicsType.DYNAMIC) {
			return;
		}
		_velocity.addSelf(impulse);
	}

	public void applyTorque(float torque) {
		if (_physicsType != PhysicsType.DYNAMIC || _inertia == Float.MAX_VALUE) {
			return;
		}
		_angularVelocity += torque / _inertia;
	}

	public void applyAngularImpulse(float impulse) {
		if (_physicsType != PhysicsType.DYNAMIC) {
			return;
		}
		_angularVelocity += impulse;
	}

	public void stopMovement() {
		if (_velocity != null) {
			_velocity.set(0, 0);
		}
		_angularVelocity = 0f;
		_torque = 0f;
	}

	public void resetPhysics() {
		stopMovement();
		_mass = 1f;
		_restitution = 0.5f;
		_friction = 0.5f;
		_linearDamping = 0.95f;
		_angularDamping = 0.95f;
		setPhysicsType(PhysicsType.DYNAMIC);
	}

	public boolean isCollidableWith(Shape other) {
		if (other == null) {
			return false;
		}
		if (this._physicsType == PhysicsType.STATIC && other._physicsType == PhysicsType.STATIC) {
			return false;
		}
		return (this._collisionMask & (1 << other._collisionLayer)) != 0;
	}

	public void setCollisionLayer(int layer) {
		this._collisionLayer = MathUtils.max(0, layer);
	}

	public void setCollisionMask(int... layers) {
		this._collisionMask = 0;
		for (int layer : layers) {
			_collisionMask |= (1 << layer);
		}
	}

	public boolean resolveElasticCollision(Shape other) {
		_restitution = 1.0f;
		return resolvePhysicsCollision(other);
	}

	public boolean resolveInelasticCollision(Shape other) {
		_restitution = 0f;
		return resolvePhysicsCollision(other);
	}

	public boolean resolveSlideCollision(Shape other) {
		_friction = 0.05f;
		_restitution = 0.1f;
		return resolvePhysicsCollision(other);
	}

	public void pushApart(Shape other) {
		if (!isCollidableWith(other)) {
			return;
		}
		Vector2f disp = collideSAT(this, other);
		if (disp == null) {
			return;
		}
		if (_physicsType == PhysicsType.DYNAMIC) {
			translate(disp.x, disp.y);
		}
		if (other._physicsType == PhysicsType.DYNAMIC) {
			other.translate(-disp.x, -disp.y);
		}
	}

	public boolean predictCollision(float targetX, float targetY, Shape obstacle) {
		if (!isCollidableWith(obstacle)) {
			return false;
		}
		Shape temp = this.cpy();
		temp.setLocation(targetX, targetY);
		return temp.collided(obstacle);
	}

	public float getOverlapDistance(Shape other) {
		Vector2f disp = collideSAT(this, other);
		return disp == null ? 0 : disp.length();
	}

	public boolean isOverlapping(Shape other) {
		return getOverlapDistance(other) > EPSILON;
	}

	public boolean isTouching(Shape other) {
		float dist = getOverlapDistance(other);
		return dist > 0 && dist < 5 * EPSILON;
	}

	public TArray<HitResult> raycastAll(Line ray, Shape[] obstacles) {
		TArray<HitResult> hits = new TArray<>();
		for (Shape obs : obstacles) {
			if (!isCollidableWith(obs)) {
				continue;
			}
			HitResult hit = intersect(obs, ray);
			if (hit != null) {
				hits.add(hit);
			}
		}
		return hits;
	}

	public HitResult raycastIgnoreTag(Line ray, Shape[] obstacles, String... ignoreTags) {
		HitResult closestHit = null;
		float minDist = Float.MAX_VALUE;
		TArray<String> ignoreList = StringUtils.getStringsToList(ignoreTags);
		for (Shape obs : obstacles) {
			if (ignoreList.contains(obs._collisionTag)) {
				continue;
			}
			HitResult hit = intersect(obs, ray);
			if (hit == null) {
				continue;
			}
			float dist = hit.pt.dst(ray.getStart());
			if (dist < minDist) {
				minDist = dist;
				closestHit = hit;
			}
		}
		return closestHit;
	}

	public void step(float deltaTime) {
		if (!_physicsEnabled || !_autoPhysicsAndCollision) {
			return;
		}
		if (_physicsType != PhysicsType.STATIC) {
			_velocity.scaleSelf(_linearDamping);
			_angularVelocity *= _angularDamping;
			translate(_velocity.x * deltaTime, _velocity.y * deltaTime);
			if (MathUtils.abs(_angularVelocity) > 0.01f) {
				setRotation(getRotation() + _angularVelocity * deltaTime);
			}
			_torque = 0f;
		}
		if (_physicsCollisionTargets != null && _physicsCollisionTargets.size() > 0) {
			updateCollisions(_physicsCollisionTargets);
		}
		if (_autoResolveCollision && _physicsCollisionTargets != null) {
			for (Shape other : _physicsCollisionTargets) {
				if (other == this || !isCollidableWith(other)) {
					continue;
				}
				resolvePhysicsCollision(other);
			}
		}
	}

	/**
	 * 持续的物理碰撞检测端口，需要执行initPhysics后才会初始化，作用是让任意Shape对象拥有基本的物理学组件操作特性
	 * 
	 * @param other
	 */
	public void checkCollision(Shape other) {
		if (_currentCollisions == null || _lastCollisions == null) {
			return;
		}
		boolean wasColliding = this._lastCollisions.contains(other);
		if (this.collided(other)) {
			if (!wasColliding && _collisionListener != null) {
				_collisionListener.onCollisionEnter(this, other);
			} else if (_collisionListener != null) {
				_collisionListener.onCollisionStay(this, other);
			}
			this._lastCollisions.add(other);
		} else {
			if (wasColliding && _collisionListener != null) {
				_collisionListener.onCollisionExit(this, other);
			}
			this._lastCollisions.remove(other);
		}
	}

	/**
	 * 更新碰撞状态并触发相应的事件，需要执行initPhysics后才能使用
	 * 
	 * @param other
	 */
	public void updateCollision(Shape other) {
		if (other == null) {
			return;
		}
		if (_currentCollisions == null || _lastCollisions == null) {
			return;
		}
		boolean isCollidingNow = this.collided(other);
		boolean wasCollidingLastFrame = _lastCollisions.contains(other);
		if (isCollidingNow && !wasCollidingLastFrame) {
			if (_collisionListener != null) {
				_collisionListener.onCollisionEnter(this, other);
			}
			_currentCollisions.add(other);
		} else if (isCollidingNow && wasCollidingLastFrame) {
			if (_collisionListener != null) {
				_collisionListener.onCollisionStay(this, other);
			}
			_currentCollisions.add(other);
		} else if (!isCollidingNow && wasCollidingLastFrame) {
			if (_collisionListener != null) {
				_collisionListener.onCollisionExit(this, other);
			}
			_currentCollisions.remove(other);
		}
	}

	/**
	 * 传入需要进行碰撞的其他物体即可进行碰撞与基本物理操作
	 * 
	 * @param allOtherShapes
	 */
	public void updateCollisions(TArray<Shape> allOtherShapes) {
		if (_currentCollisions == null || _lastCollisions == null || allOtherShapes == null) {
			return;
		}
		_currentCollisions.clear();
		for (Shape other : allOtherShapes) {
			if (other == this) {
				continue;
			}
			boolean isCollidingNow = this.collided(other);
			boolean wasCollidingLastFrame = _lastCollisions.contains(other);
			if (isCollidingNow) {
				_currentCollisions.add(other);
				if (!wasCollidingLastFrame) {
					if (_collisionListener != null)
						_collisionListener.onCollisionEnter(this, other);
				} else {
					if (_collisionListener != null)
						_collisionListener.onCollisionStay(this, other);
				}
				if (!this._isStatic || !other._isStatic) {
					this.resolvePhysicsCollision(other);
				}
			} else {
				if (wasCollidingLastFrame) {
					if (_collisionListener != null) {
						_collisionListener.onCollisionExit(this, other);
					}
				}
			}
		}
		ObjectSet<Shape> temp = _lastCollisions;
		_lastCollisions = _currentCollisions;
		_currentCollisions = temp;
	}

	public boolean resolveCollision(Shape other) {
		ShapeCollisionInfo info = collideSATDetailed(other);
		if (!info.collided) {
			return false;
		}
		float massRatio = 0.5f;
		this.translate(info.displacement.x * massRatio, info.displacement.y * massRatio);
		Vector2f relativeVelocity = new Vector2f(this._velocity)
				.subSelf(other._velocity != null ? other._velocity : Vector2f.ZERO());
		float velAlongNormal = relativeVelocity.dot(info.normal);
		if (velAlongNormal > 0) {
			return true;
		}
		float e = MathUtils.min(this._restitution, other._restitution);
		float j = -(1 + e) * velAlongNormal;
		j /= (1 / (this._velocity == null ? 1 : 1) + 1 / (other._velocity == null ? 1 : 1));
		Vector2f impulse = info.normal.scaleSelf(j);
		if (this._velocity != null) {
			this._velocity.addSelf(impulse);
		}
		if (other._velocity != null) {
			other._velocity.subSelf(impulse);
		}
		return true;
	}

	/**
	 * 执行物理碰撞响应
	 * 
	 * @param other
	 * @return
	 */
	public boolean resolvePhysicsCollision(Shape other) {
		if (this._isStatic && other._isStatic) {
			return false;
		}
		if (this._velocity == null) {
			this._velocity = new Vector2f();
		}
		if (other._velocity == null) {
			other._velocity = new Vector2f();
		}
		ShapeCollisionInfo info = this.collideSATDetailed(other);
		if (!info.collided) {
			return false;
		}
		float totalMass = (this._isStatic ? 0 : this._mass) + (other._isStatic ? 0 : other._mass);

		if (totalMass > 0) {
			float thisRatio = this._isStatic ? 0 : (other._mass / totalMass);
			float otherRatio = other._isStatic ? 0 : (this._mass / totalMass);
			if (!this._isStatic) {
				this.translate(info.normal.x * info.depth * thisRatio, info.normal.y * info.depth * thisRatio);
			}
			if (!other._isStatic) {
				other.translate(-info.normal.x * info.depth * otherRatio, -info.normal.y * info.depth * otherRatio);
			}
		} else {
			if (!this._isStatic) {
				this.translate(info.normal.x * info.depth * 0.5f, info.normal.y * info.depth * 0.5f);
			}
			if (!other._isStatic) {
				other.translate(-info.normal.x * info.depth * 0.5f, -info.normal.y * info.depth * 0.5f);
			}
		}

		Vector2f relativeVelocity = new Vector2f(this._velocity).subSelf(other._velocity);
		float velAlongNormal = relativeVelocity.dot(info.normal);

		if (velAlongNormal > 0) {
			return true;
		}

		float invMassSum = 0;
		if (!this._isStatic) {
			invMassSum += 1.0f / this._mass;
		}
		if (!other._isStatic) {
			invMassSum += 1.0f / other._mass;
		}
		if (invMassSum == 0) {
			return true;
		}

		Vector2f centerThis = this.getCenterPos();
		Vector2f centerOther = other.getCenterPos();

		Vector2f contactPoint = new Vector2f();
		contactPoint.set(this.getX(), this.getY()).addSelf(info.normal.cpy().scaleSelf(this.getBoundingCircleRadius()));

		if (!this._isStatic) {
			Vector2f r1 = contactPoint.cpy().subSelf(centerThis);

			float r1PerpDotN = r1.x * info.normal.y - r1.y * info.normal.x;
			float w1r1Perp = this._angularVelocity * r1PerpDotN;

			float r2PerpDotN = 0;
			float w2r2Perp = 0;
			if (!other._isStatic) {
				Vector2f r2 = contactPoint.cpy().subSelf(centerOther);
				r2PerpDotN = r2.x * info.normal.y - r2.y * info.normal.x;
				w2r2Perp = other._angularVelocity * r2PerpDotN;
			}

			Vector2f linearVelDiff = new Vector2f(this._velocity).subSelf(other._velocity);
			float velAlongNormalLinear = linearVelDiff.dot(info.normal);
			float velAlongNormalAngular = w1r1Perp - w2r2Perp;
			velAlongNormal = velAlongNormalLinear + velAlongNormalAngular;

			if (velAlongNormal > 0) {
				return true;
			}

			float e = MathUtils.min(this._restitution, other._restitution);

			float denom = 0;
			denom += (!this._isStatic) ? (1.0f / this._mass) : 0;
			denom += (!other._isStatic) ? (1.0f / other._mass) : 0;
			denom += (!this._isStatic) ? ((r1PerpDotN * r1PerpDotN) / this._inertia) : 0;
			denom += (!other._isStatic) ? ((r2PerpDotN * r2PerpDotN) / other._inertia) : 0;

			if (denom == 0) {
				return true;
			}

			float j = -(1 + e) * velAlongNormal;
			j /= denom;

			Vector2f impulse = info.normal.cpy().scaleSelf(j);

			this._velocity.addSelf(impulse.cpy().scaleSelf(1.0f / this._mass));

			float torqueThis = j * r1PerpDotN;
			this._angularVelocity += torqueThis / this._inertia;

			if (!other._isStatic) {
				other._velocity.subSelf(impulse.cpy().scaleSelf(1.0f / other._mass));
				float torqueOther = j * r2PerpDotN;
				other._angularVelocity -= torqueOther / other._inertia;
			}

			Vector2f tangent = relativeVelocity.cpy().subSelf(info.normal.scaleSelf(velAlongNormal));
			if (tangent.len() > 0) {
				tangent.norSelf();
			}

			float jt = -relativeVelocity.dot(tangent);

			float mu = MathUtils.sqrt(this._friction * this._friction + other._friction * other._friction);

			float frictionImpulse = mu * j;

			if (MathUtils.abs(jt) < frictionImpulse) {
				frictionImpulse = MathUtils.abs(jt);
			}

			Vector2f frictionVec = tangent.scaleSelf(frictionImpulse);

			if (!this._isStatic) {
				this._velocity.subSelf(frictionVec.cpy().scaleSelf(1.0f / this._mass));
			}
			if (!other._isStatic) {
				other._velocity.addSelf(frictionVec.cpy().scaleSelf(1.0f / other._mass));
			}
		}
		return true;
	}

	public Vector2f getContactPoint(Shape other) {
		TArray<Vector2f> verts = this.getVertices();
		Vector2f closestPoint = verts.get(0);
		float minDst = Float.MAX_VALUE;
		Vector2f otherCenter = other.getCenterPos();
		for (Vector2f v : verts) {
			float dst = v.dst(otherCenter);
			if (dst < minDst) {
				minDst = dst;
				closestPoint = v;
			}
		}
		return new Vector2f(closestPoint).subSelf(this.getCenterPos());
	}

	public boolean wouldCollide(float targetX, float targetY, Shape obstacle) {
		float stepX = (targetX - this.x) * 0.2f;
		float stepY = (targetY - this.y) * 0.2f;
		Shape temp = this.cpy();
		for (int i = 0; i < 5; i++) {
			temp.translate(stepX, stepY);
			if (temp.collided(obstacle)) {
				return true;
			}
		}
		return false;
	}

	public HitResult raycast(Line ray, Shape[] obstacles) {
		HitResult closestHit = null;
		float closestDist = Float.MAX_VALUE;
		for (Shape obs : obstacles) {
			HitResult hit = obs.intersect(obs, ray);
			if (hit != null) {
				float dist = hit.pt.dst(ray.getStart());
				if (dist < closestDist) {
					closestDist = dist;
					closestHit = hit;
				}
			}
		}
		return closestHit;
	}

	public TArray<Shape> clip(Shape cutter) {
		TArray<Shape> result = new TArray<Shape>();
		if (!this.intersects(cutter)) {
			result.add(this.cpy());
			return result;
		}
		Shape[] diff = this.subtract(this, cutter);
		result.addAll(diff);
		return result;
	}

	public Shape merge(Shape other) {
		if (!this.intersects(other)) {
			return null;
		}
		Shape[] unions = union(this, other);
		return unions.length > 0 ? unions[0] : null;
	}

	public Shape setLocation(XY pos) {
		if (pos == null) {
			return this;
		}
		return setLocation(pos.getX(), pos.getY());
	}

	public Shape setLocation(float x, float y) {
		if (!MathUtils.equal(this.x, x) || !MathUtils.equal(this.y, y)) {
			this.pointsDirty = true;
		}
		setX(x);
		setY(y);
		return this;
	}

	public abstract Shape transform(Matrix3 transform);

	protected abstract void createPoints();

	public Shape translate(float deltaX, float deltaY) {
		if (MathUtils.equal(deltaX, 0) && MathUtils.equal(deltaY, 0)) {
			return this;
		}
		this.pointsDirty = true;
		setX(x + deltaX);
		setY(y + deltaY);
		return this;
	}

	public int vertexCount() {
		checkPoints();
		return cachedVertexCount;
	}

	public float getDoubleRadius() {
		checkPoints();
		return boundingCircleRadius * boundingCircleRadius;
	}

	public Vector2f getPosition() {
		checkPoints();
		return cachedPosVec.set(x, y);
	}

	public Vector2f getCenterPos() {
		checkPoints();
		return cachedCenterVec.set(getCenterX(), getCenterY());
	}

	public boolean inPoint(XY pos) {
		return inPoint(pos.getX(), pos.getY(), 1f);
	}

	public boolean inPoint(XY pos, float size) {
		return inPoint(pos.getX(), pos.getY(), size);
	}

	public boolean inPoint(float px, float py, float size) {
		return CollisionHelper.checkPointvsPolygon(px, py, this.getPoints(), size);
	}

	public boolean inPoint(float px, float py, float w, float h) {
		return CollisionHelper.checkPointvsPolygon(px, py, this.getPoints(), w, h);
	}

	public boolean inCircle(Circle c) {
		return c != null && inCircle(c.getRealX(), c.getRealY(), c.getDiameter());
	}

	public boolean inCircle(float cx, float cy, float diameter) {
		return CollisionHelper.checkCirclevsPolygon(cx, cy, diameter, this.getVertices());
	}

	public boolean inRect(RectBox rect) {
		return rect != null && inRect(rect.x, rect.y, rect.width, rect.height);
	}

	public boolean inRect(float x, float y, float w, float h) {
		return CollisionHelper.checkAABBvsPolygon(x, y, w, h, this.getVertices());
	}

	public boolean inShape(float[] points) {
		return points != null && CollisionHelper.checkPolygonvsPolygon(this.getPoints(), points);
	}

	public boolean inShape(Shape shape) {
		return shape != null && CollisionHelper.checkPolygonvsPolygon(this.getPoints(), shape.getPoints());
	}

	@Override
	public float getX() {
		checkPoints();
		return x;
	}

	@Override
	public void setX(float x) {
		if (MathUtils.equal(this.x, x)) {
			return;
		}
		this.pointsDirty = true;
		float dx = x - this.x;
		this.x = x;
		if (points == null || points.length == 0 || center == null || center.length == 0) {
			return;
		}
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2;
			if (idx >= points.length) {
				break;
			}
			points[idx] += dx;
		}
		center[0] += dx;
		maxX += dx;
		minX += dx;
		trianglesDirty = true;
	}

	@Override
	public void setY(float y) {
		if (MathUtils.equal(this.y, y)) {
			return;
		}
		this.pointsDirty = true;
		float dy = y - this.y;
		this.y = y;
		if (points == null || points.length == 0 || center == null || center.length == 0) {
			return;
		}
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2 + 1;
			if (idx >= points.length) {
				break;
			}
			points[idx] += dy;
		}
		center[1] += dy;
		maxY += dy;
		minY += dy;
		trianglesDirty = true;
	}

	@Override
	public float getY() {
		checkPoints();
		return y;
	}

	private int getCachedVertexCount() {
		if (points == null || points.length < 2) {
			cachedVertexCount = 0;
		} else {
			cachedVertexCount = points.length / 2;
		}
		return cachedVertexCount;
	}

	public float length() {
		checkPoints();
		return MathUtils.sqrt(x * x + y * y);
	}

	public float getCenterX() {
		checkPoints();
		return center == null || center.length == 0 ? x : center[0];
	}

	public Shape setCenterX(float centerX) {
		checkPoints();
		if (center == null || center.length == 0) {
			return this;
		}
		float xDiff = centerX - getCenterX();
		setX(x + xDiff);
		return this;
	}

	public float getCenterY() {
		checkPoints();
		return center == null || center.length == 0 ? y : center[1];
	}

	public Shape setCenterY(float centerY) {
		checkPoints();
		if (center == null || center.length == 0) {
			return this;
		}
		float yDiff = centerY - getCenterY();
		setY(y + yDiff);
		return this;
	}

	public Shape setCenter(float x, float y) {
		setCenterX(x);
		setCenterY(y);
		return this;
	}

	public Shape setCenter(Vector2f pos) {
		return setCenter(pos.x, pos.y);
	}

	public float getMaxX() {
		checkPoints();
		return maxX;
	}

	public float getMaxY() {
		checkPoints();
		return maxY;
	}

	public float getMinX() {
		checkPoints();
		return minX;
	}

	public float getMinY() {
		checkPoints();
		return minY;
	}

	public float getLeft() {
		return getMinX();
	}

	public float getRight() {
		return getMaxX();
	}

	public float getTop() {
		return getMinY();
	}

	public float getBottom() {
		return getMaxY();
	}

	public float getBoundingCircleRadius() {
		checkPoints();
		return boundingCircleRadius;
	}

	public float getDiameter() {
		return getBoundingCircleRadius() * 2f;
	}

	public float perimeter() {
		checkPoints();
		float perimeter = 0;
		int count = getCachedVertexCount();
		if (count < 2 || points == null || points.length == 0) {
			return 0;
		}
		for (int i = 0; i < count; i++) {
			int idx1 = i * 2;
			int idx2 = ((i + 1) % count) * 2;
			if (idx1 + 1 >= points.length || idx2 + 1 >= points.length) {
				break;
			}
			float dx = points[idx2] - points[idx1];
			float dy = points[idx2 + 1] - points[idx1 + 1];
			perimeter += MathUtils.sqrt(dx * dx + dy * dy);
		}
		return perimeter;
	}

	public float[] getCenter() {
		checkPoints();
		return center;
	}

	public float[] getPoints() {
		checkPoints();
		return points;
	}

	public TArray<Vector2f> getVertices() {
		checkPoints();
		if (_vertices == null) {
			_vertices = new TArray<Vector2f>();
		}
		if (!pointsDirty) {
			return _vertices;
		}
		_vertices.clear();
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2;
			if (idx + 1 >= points.length) {
				break;
			}
			Vector2f vec = new Vector2f(points[idx], points[idx + 1]);
			_vertices.add(vec);
		}
		pointsDirty = false;
		return _vertices;
	}

	public int getPointCount() {
		checkPoints();
		return getCachedVertexCount();
	}

	public float[] getPoint(int index) {
		checkPoints();
		int count = getCachedVertexCount();
		if (index < 0 || index >= count || points == null || points.length == 0) {
			return new float[] { 0, 0 };
		}
		int idx = index * 2;
		if (idx + 1 >= points.length) {
			return new float[] { 0, 0 };
		}
		return new float[] { points[idx], points[idx + 1] };
	}

	public float[] getNormal(int index) {
		int count = getCachedVertexCount();
		if (count < 2) {
			return new float[] { 0, 1 };
		}
		float[] current = getPoint(index);
		float[] prev = getPoint(index - 1 < 0 ? count - 1 : index - 1);
		float[] next = getPoint(index + 1 >= count ? 0 : index + 1);

		float[] t1 = getNormal(prev, current);
		float[] t2 = getNormal(current, next);

		if ((index == 0) && (!closed())) {
			return t2;
		}
		if ((index == count - 1) && (!closed())) {
			return t1;
		}
		float tx = (t1[0] + t2[0]) / 2;
		float ty = (t1[1] + t2[1]) / 2;
		float len = MathUtils.sqrt(tx * tx + ty * ty);
		return len < EPSILON ? new float[] { 0, 1 } : new float[] { tx / len, ty / len };
	}

	private float[] getNormal(float[] start, float[] end) {
		float dx = start[0] - end[0];
		float dy = start[1] - end[1];
		float len = MathUtils.sqrt(dx * dx + dy * dy);
		if (len < EPSILON) {
			return new float[] { 0, 1 };
		}
		dx /= len;
		dy /= len;
		return new float[] { -dy, dx };
	}

	public Vector2f collideSAT(Shape s) {
		return collideSAT(this, s);
	}

	public boolean contains(XY xy) {
		return xy != null && contains(xy.getX(), xy.getY());
	}

	public boolean contains(Shape other) {
		if (other == null) {
			return false;
		}
		int count = other.getPointCount();
		for (int i = 0; i < count; i++) {
			float[] pt = other.getPoint(i);
			if (!contains(pt[0], pt[1])) {
				return false;
			}
		}
		return true;
	}

	public boolean includes(float x, float y) {
		checkPoints();
		if (points == null || points.length == 0) {
			return false;
		}
		Vector2f pt = vecCache[0];
		pt.set(x, y);
		int count = getCachedVertexCount();

		for (int i = 0; i < count; i++) {
			int n = (i + 1) % count;
			int idx1 = i * 2;
			int idx2 = n * 2;
			if (idx1 + 1 >= points.length || idx2 + 1 >= points.length)
				break;

			Line line = new Line(points[idx1], points[idx1 + 1], points[idx2], points[idx2 + 1]);
			if (line.on(pt))
				return true;
		}
		return false;
	}

	public int indexOf(float x, float y) {
		checkPoints();
		if (points == null || points.length == 0) {
			return -1;
		}
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2;
			if (idx + 1 >= points.length) {
				break;
			}
			if (MathUtils.equal(points[idx], x) && MathUtils.equal(points[idx + 1], y)) {
				return i;
			}
		}
		return -1;
	}

	public boolean contains(float x, float y, float size) {
		checkPoints();
		return CollisionHelper.containsPointvsPolygon(x, y, getPoints(), size, size);
	}

	public boolean contains(float x, float y, float w, float h) {
		checkPoints();
		return CollisionHelper.containsPointvsPolygon(x, y, getPoints(), w, h);
	}

	public boolean contains(float x, float y) {
		checkPoints();
		if (points == null || points.length < 2) {
			return false;
		}
		boolean result = false;
		int npoints = points.length;
		float xold = points[npoints - 2];
		float yold = points[npoints - 1];

		for (int i = 0; i < npoints; i += 2) {
			if (i + 1 >= npoints) {
				break;
			}
			float xnew = points[i];
			float ynew = points[i + 1];

			float x1, x2, y1, y2;
			if (xnew > xold) {
				x1 = xold;
				x2 = xnew;
				y1 = yold;
				y2 = ynew;
			} else {
				x1 = xnew;
				x2 = xold;
				y1 = ynew;
				y2 = yold;
			}

			if ((xnew < x) == (x <= xold) && (y - y1) * (x2 - x1) < (y2 - y1) * (x - x1)) {
				result = !result;
			}
			xold = xnew;
			yold = ynew;
		}
		return result;
	}

	public boolean intersects(XY pos) {
		return pos != null && intersects(pos.getX(), pos.getY(), 1);
	}

	public boolean intersects(float x, float y, int size) {
		checkPoints();
		return CollisionHelper.checkPointvsPolygon(x, y, getPoints(), size);
	}

	public boolean intersects(float x, float y, float w, float h) {
		checkPoints();
		return CollisionHelper.checkPointvsPolygon(x, y, getPoints(), w, h);
	}

	public boolean intersects(Shape shape) {
		if (shape == null) {
			return false;
		}
		checkPoints();
		shape.checkPoints();

		final float[] pointsA = getPoints();
		final float[] pointsB = shape.getPoints();
		if (pointsA == null || pointsB == null || pointsA.length < 2 || pointsB.length < 2) {
			return false;
		}
		boolean result = false;
		int lenA = closed() ? pointsA.length : pointsA.length - 2;
		int lenB = shape.closed() ? pointsB.length : pointsB.length - 2;

		for (int i = 0; i < lenA; i += 2) {
			int iNext = i + 2 >= lenA ? 0 : i + 2;
			if (iNext + 1 >= pointsA.length) {
				break;
			}
			for (int j = 0; j < lenB; j += 2) {
				int jNext = j + 2 >= lenB ? 0 : j + 2;
				if (jNext + 1 >= pointsB.length) {
					break;
				}
				float dx1 = pointsA[iNext] - pointsA[i];
				float dy1 = pointsA[iNext + 1] - pointsA[i + 1];
				float dx2 = pointsB[jNext] - pointsB[j];
				float dy2 = pointsB[jNext + 1] - pointsB[j + 1];
				float denom = dx1 * dy2 - dy1 * dx2;

				if (MathUtils.abs(denom) < EPSILON) {
					continue;
				}
				float unknownA = ((pointsB[j + 1] - pointsA[i + 1]) * dx2 - (pointsB[j] - pointsA[i]) * dy2) / denom;
				float unknownB = ((pointsB[j + 1] - pointsA[i + 1]) * dx1 - (pointsB[j] - pointsA[i]) * dy1) / denom;

				if (unknownA >= 0 && unknownA <= 1 && unknownB >= 0 && unknownB <= 1) {
					result = true;
					break;
				}
			}
			if (result) {
				break;
			}
		}
		return result;
	}

	public boolean collided(Shape shape) {
		if (shape == null) {
			return false;
		}
		if (!this.getAABB().intersects(shape.getAABB())) {
			return false;
		}
		return contains(shape) || intersects(shape);
	}

	public boolean hasVertex(float x, float y) {
		return indexOf(x, y) != -1;
	}

	public float[] getOriginPoints() {
		return CollectionUtils.copyOf(originPoints);
	}

	protected void findCenter() {
		if (points == null || points.length < 2) {
			center = new float[] { x, y };
			return;
		}
		center = new float[] { 0, 0 };
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2;
			if (idx + 1 >= points.length) {
				break;
			}
			center[0] += points[idx];
			center[1] += points[idx + 1];
		}
		if (count > 0) {
			center[0] /= count;
			center[1] /= count;
		}
	}

	protected void calculateRadius() {
		if (points == null || center == null || points.length < 2) {
			boundingCircleRadius = 0;
			return;
		}
		boundingCircleRadius = 0;
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2;
			if (idx + 1 >= points.length) {
				break;
			}
			float dx = points[idx] - center[0];
			float dy = points[idx + 1] - center[1];
			boundingCircleRadius = MathUtils.max(boundingCircleRadius, dx * dx + dy * dy);
		}
		boundingCircleRadius = MathUtils.sqrt(boundingCircleRadius);
	}

	protected void calculateTriangles() {
		if (!trianglesDirty && triangle != null) {
			return;
		}
		if (points == null || points.length < 6) {
			return;
		}
		triangle = new TriangleNeat();
		int count = getCachedVertexCount();
		for (int i = 0; i < count; i++) {
			int idx = i * 2;
			if (idx + 1 >= points.length) {
				break;
			}
			triangle.addPolyPoint(points[idx], points[idx + 1]);
		}
		triangle.triangulate();
		trianglesDirty = false;
	}

	private void callTransform(Matrix3 m) {
		if (points == null || points.length == 0) {
			return;
		}
		float[] result = new float[points.length];
		m.transform(points, 0, result, 0, points.length / 2);
		this.points = result;
		this.pointsDirty = true;
		this.checkPoints();
	}

	public void setScale(float s) {
		this.setScale(s, s);
	}

	public void setScale(float sx, float sy) {
		if (MathUtils.equal(scaleX, sx) && MathUtils.equal(scaleY, sy)) {
			return;
		}
		this.scaleX = sx;
		this.scaleY = sy;
		applyCombinedTransform(getCenterX(), getCenterY());
		this.pointsDirty = true;
		this.checkPoints();
	}

	private void applyCombinedTransform(float cx, float cy) {
		if (originPoints == null || originPoints.length == 0) {
			return;
		}
		System.arraycopy(originPoints, 0, points, 0, originPoints.length);
		this.callTransform(Matrix3.setToCombineTransform(cx, cy, scaleX, scaleY, rotation));
	}

	public float getScaleX() {
		return scaleX;
	}

	public float getScaleY() {
		return scaleY;
	}

	public boolean isScaled() {
		return scaleX != 1f || scaleY != 1f;
	}

	public float getRotation() {
		return rotation;
	}

	public boolean isRotated() {
		return rotation != 0f;
	}

	public Shape setRotation(float r) {
		checkPoints();
		return this.setRotation(r, getCenterX(), getCenterY());
	}

	public Shape setRotation(float r, float cx, float cy) {
		if (MathUtils.equal(this.rotation, r)) {
			return this;
		}
		this.rotation = r;
		applyCombinedTransform(cx, cy);
		this.updatePoints();
		return this;
	}

	public void increaseTriangulation() {
		checkPoints();
		calculateTriangles();
		triangle = new TriangleOver(triangle);
	}

	public Triangle getTriangles() {
		checkPoints();
		calculateTriangles();
		return triangle;
	}

	protected final void updatePoints() {
		this.pointsDirty = true;
		if (points == null || points.length == 0) {
			maxX = maxY = minX = minY = 0;
			return;
		}
		maxX = minX = points[0];
		maxY = minY = points[1];
		int count = getCachedVertexCount();
		for (int i = 1; i < count; i++) {
			int idx = i * 2;
			if (idx + 1 >= points.length)
				break;
			float x = points[idx];
			float y = points[idx + 1];
			if (x < minX) {
				minX = x;
			}
			if (x > maxX) {
				maxX = x;
			}
			if (y < minY) {
				minY = y;
			}
			if (y > maxY) {
				maxY = y;
			}
		}
	}

	protected final void checkPoints() {
		if (!pointsDirty) {
			return;
		}
		createPoints();
		if (originPoints == null || originPoints.length != points.length) {
			originPoints = new float[points.length];
			System.arraycopy(points, 0, originPoints, 0, points.length);
		}
		getCachedVertexCount();
		findCenter();
		calculateRadius();
		updatePoints();
		pointsDirty = false;
		trianglesDirty = true;
	}

	public Shape reverse() {
		checkPoints();
		if (points == null || points.length < 2) {
			return this;
		}
		int numVertices = getCachedVertexCount();
		float tmp;
		for (int i = 0; i < numVertices / 2; i++) {
			int opp = numVertices - 1 - i;
			int idxI = i * 2;
			int idxOpp = opp * 2;
			if (idxI + 1 >= points.length || idxOpp + 1 >= points.length) {
				break;
			}
			tmp = points[idxI];
			points[idxI] = points[idxOpp];
			points[idxOpp] = tmp;
			tmp = points[idxI + 1];
			points[idxI + 1] = points[idxOpp + 1];
			points[idxOpp + 1] = tmp;
		}
		return this;
	}

	public void preCache() {
		checkPoints();
		getTriangles();
	}

	public boolean closed() {
		return true;
	}

	public Polygon prune() {
		Polygon result = new Polygon();
		int count = getPointCount();
		for (int i = 0; i < count; i++) {
			int next = i + 1 >= count ? 0 : i + 1;
			int prev = i - 1 < 0 ? count - 1 : i - 1;

			float[] pI = getPoint(i);
			float[] pPrev = getPoint(prev);
			float[] pNext = getPoint(next);

			float dx1 = pI[0] - pPrev[0];
			float dy1 = pI[1] - pPrev[1];
			float dx2 = pNext[0] - pI[0];
			float dy2 = pNext[1] - pI[1];

			float len1 = MathUtils.sqrt(dx1 * dx1 + dy1 * dy1);
			float len2 = MathUtils.sqrt(dx2 * dx2 + dy2 * dy2);
			if (len1 > 0) {
				dx1 /= len1;
				dy1 /= len1;
			}
			if (len2 > 0) {
				dx2 /= len2;
				dy2 /= len2;
			}

			if (!MathUtils.equal(dx1, dx2) || !MathUtils.equal(dy1, dy2)) {
				result.addPoint(pI[0], pI[1]);
			}
		}
		return result;
	}

	public float getWidth() {
		checkPoints();
		return maxX - minX;
	}

	public float getHeight() {
		checkPoints();
		return maxY - minY;
	}

	public RectBox getRect() {
		checkPoints();
		if (rect == null) {
			rect = new RectBox(x, y, getWidth(), getHeight());
		} else {
			rect.setBounds(x, y, getWidth(), getHeight());
		}
		return rect;
	}

	public AABB getAABB() {
		checkPoints();
		if (aabb == null) {
			aabb = new AABB(minX, minY, maxX, maxY);
		} else {
			aabb.set(minX, minY, maxX, maxY);
		}
		return aabb;
	}

	public Shape[] subtract(Shape source) {
		return subtract(this, source);
	}

	public Shape[] subtract(Shape target, Shape source) {
		target = target.cpy();
		source = source.cpy();
		int count = 0;
		int tCount = target.getPointCount();
		for (int i = 0; i < tCount; i++) {
			float[] pt = target.getPoint(i);
			if (source.contains(pt[0], pt[1])) {
				count++;
			}
		}
		if (count == tCount) {
			return new Shape[0];
		}
		if (!target.intersects(source)) {
			return new Shape[] { target };
		}
		int found = 0;
		int sCount = source.getPointCount();
		for (int j = 0; j < sCount; j++) {
			float[] pt = source.getPoint(j);
			if (target.contains(pt[0], pt[1]) && !onPath(target, pt[0], pt[1])) {
				found++;
			}
		}
		for (int j = 0; j < tCount; j++) {
			float[] pt = target.getPoint(j);
			if (source.contains(pt[0], pt[1]) && !onPath(source, pt[0], pt[1])) {
				found++;
			}
		}
		return found < 1 ? new Shape[] { target } : combine(target, source, true);
	}

	private Shape[] combine(Shape target, Shape other, boolean subtract) {
		if (subtract) {
			TArray<Shape> shapes = new TArray<Shape>();
			TArray<Vector2f> used = new TArray<Vector2f>();
			int tCount = target.getPointCount();
			for (int j = 0; j < tCount; j++) {
				float[] pt = target.getPoint(j);
				if (other.contains(pt[0], pt[1])) {
					used.add(vecCache[4].set(pt[0], pt[1]));
				}
			}
			for (int j = 0; j < tCount; j++) {
				float[] pt = target.getPoint(j);
				Vector2f v = vecCache[5].set(pt[0], pt[1]);
				if (!used.contains(v)) {
					Shape res = combineSingle(target, other, true, j);
					shapes.add(res);
					int rCount = res.getPointCount();
					for (int k = 0; k < rCount; k++) {
						float[] kp = res.getPoint(k);
						used.add(vecCache[6].set(kp[0], kp[1]));
					}
				}
			}
			Shape[] list = new Shape[shapes.size];
			for (int i = 0; i < shapes.size; i++) {
				list[i] = shapes.get(i);
			}
			return list;
		}
		int tCount = target.getPointCount();
		for (int i = 0; i < tCount; i++) {
			float[] pt = target.getPoint(i);
			if (!other.contains(pt[0], pt[1]) && !other.hasVertex(pt[0], pt[1])) {
				return new Shape[] { combineSingle(target, other, false, i) };
			}
		}
		return new Shape[] { other };
	}

	public static int rationalPoint(Shape shape, int p) {
		int count = shape.getPointCount();
		if (count == 0) {
			return 0;
		}
		while (p < 0) {
			p += count;
		}
		while (p >= count) {
			p -= count;
		}
		return p;
	}

	public Shape[] union(Shape target, Shape other) {
		target = target.cpy();
		other = other.cpy();
		if (!target.intersects(other)) {
			return new Shape[] { target, other };
		}
		boolean touches = false;
		int buttCount = 0;
		int tCount = target.getPointCount();
		for (int i = 0; i < tCount; i++) {
			float[] pt = target.getPoint(i);
			if (other.contains(pt[0], pt[1]) && !other.hasVertex(pt[0], pt[1])) {
				touches = true;
				break;
			}
			if (other.hasVertex(pt[0], pt[1])) {
				buttCount++;
			}
		}
		if (!touches) {
			int oCount = other.getPointCount();
			for (int i = 0; i < oCount; i++) {
				float[] pt = other.getPoint(i);
				if (target.contains(pt[0], pt[1]) && !target.hasVertex(pt[0], pt[1])) {
					touches = true;
					break;
				}
			}
		}
		if (!touches && buttCount < 2) {
			return new Shape[] { target, other };
		}
		return combine(target, other, false);
	}

	public Line getLine(Shape shape, int s, int e) {
		float[] start = shape.getPoint(s);
		float[] end = shape.getPoint(e);
		return new Line(start[0], start[1], end[0], end[1]);
	}

	public Line getLine(Shape shape, float sx, float sy, int e) {
		float[] end = shape.getPoint(e);
		return new Line(sx, sy, end[0], end[1]);
	}

	public HitResult intersect(Shape shape, Line line) {
		float distance = Float.MAX_VALUE;
		HitResult hit = null;
		int count = shape.getPointCount();
		for (int i = 0; i < count; i++) {
			int next = rationalPoint(shape, i + 1);
			Line local = getLine(shape, i, next);
			Vector2f pt = line.intersect(local, true);
			if (pt != null) {
				float newDis = pt.distance(line.getStart());
				if (newDis < distance && newDis > EPSILON) {
					hit = new HitResult();
					hit.pt = vecCache[1];
					hit.pt.set(pt.x, pt.y);
					hit.line = local;
					hit.p1 = i;
					hit.p2 = next;
					distance = newDis;
				}
			}
		}
		return hit;
	}

	private Shape combineSingle(Shape target, Shape missing, boolean subtract, int start) {
		Shape current = target;
		Shape other = missing;
		int point = start;
		int dir = 1;
		Polygon poly = new Polygon();
		boolean first = true;
		int loop = 0;
		float[] startPt = current.getPoint(point);
		float px = startPt[0];
		float py = startPt[1];

		while (!poly.hasVertex(px, py) || first || current != target) {
			first = false;
			if (++loop > MAX_POINTS) {
				break;
			}
			poly.addPoint(px, py);
			Line line = getLine(current, px, py, rationalPoint(current, point + dir));
			HitResult hit = intersect(other, line);

			if (hit != null) {
				Vector2f pt = hit.pt;
				px = pt.x;
				py = pt.y;
				if (other.hasVertex(px, py)) {
					point = other.indexOf(px, py);
					dir = 1;
					Shape tmp = current;
					current = other;
					other = tmp;
					continue;
				}
				Line hitLine = hit.line;
				float len = hitLine.length();
				if (len < EPSILON) {
					continue;
				}
				float dx = hitLine.getDX() / len * EDGE_SCALE;
				float dy = hitLine.getDY() / len * EDGE_SCALE;

				if (current.contains(pt.x + dx, pt.y + dy)) {
					point = subtract ? (current == missing ? hit.p2 : hit.p1) : hit.p2;
					dir = subtract ? (current == missing ? -1 : 1) : -1;
					Shape tmp = current;
					current = other;
					other = tmp;
					continue;
				}
				if (current.contains(pt.x - dx, pt.y - dy)) {
					point = subtract ? (current == target ? hit.p2 : hit.p1) : hit.p1;
					dir = subtract ? (current == target ? -1 : 1) : 1;
					Shape tmp = current;
					current = other;
					other = tmp;
					continue;
				}
				if (subtract) {
					break;
				}
				point = hit.p1;
				dir = 1;
				Shape tmp = current;
				current = other;
				other = tmp;
				point = rationalPoint(current, point + dir);
				float[] newPt = current.getPoint(point);
				px = newPt[0];
				py = newPt[1];
				continue;
			}
			point = rationalPoint(current, point + dir);
			float[] newPt = current.getPoint(point);
			px = newPt[0];
			py = newPt[1];
		}
		poly.addPoint(px, py);
		return poly;
	}

	private boolean onPath(Shape path, float x, float y) {
		int count = path.getPointCount();
		for (int i = 0; i < count + 1; i++) {
			int n = rationalPoint(path, i + 1);
			Line line = getLine(path, rationalPath(path, i), n);
			if (line.distance(vecCache[7].set(x, y)) < EPSILON * 100f) {
				return true;
			}
		}
		return false;
	}

	private int rationalPath(Shape path, int i) {
		return rationalPoint(path, i);
	}

	public ShapeEntity getEntity() {
		return getEntity(LColor.white, true);
	}

	public ShapeEntity getEntity(LColor c, boolean fill) {
		if (entity == null) {
			entity = new ShapeEntity(this, c, fill);
		} else {
			entity.setShape(this);
		}
		return entity;
	}

	public boolean equalsRotateScale(float rotate, float sx, float sy) {
		return MathUtils.equal(rotate, rotation) && MathUtils.equal(sx, scaleX) && MathUtils.equal(sy, scaleY);
	}

	public boolean equals(Shape shape) {
		if (shape == null) {
			return false;
		}
		if (shape == this) {
			return true;
		}
		boolean eq = MathUtils.equal(shape.x, x) && MathUtils.equal(shape.y, y)
				&& MathUtils.equal(shape.rotation, rotation) && MathUtils.equal(shape.minX, minX)
				&& MathUtils.equal(shape.minY, minY) && MathUtils.equal(shape.maxX, maxX)
				&& MathUtils.equal(shape.maxY, maxY)
				&& MathUtils.equal(shape.boundingCircleRadius, boundingCircleRadius);
		if (eq) {
			checkPoints();
			eq = CollectionUtils.equals(points, shape.points) && CollectionUtils.equals(center, shape.center);
		}
		return eq;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		return obj == this || equals((Shape) obj);
	}

	public Shape cpy() {
		checkPoints();
		float[] copyPoints = new float[points.length];
		System.arraycopy(points, 0, copyPoints, 0, copyPoints.length);
		return new Polygon(copyPoints);
	}

	public Shape copy(Shape shape) {
		if (shape == null || shape == this) {
			return this;
		}
		this.x = shape.x;
		this.y = shape.y;
		this.rotation = shape.rotation;
		this.points = CollectionUtils.copyOf(shape.points);
		this.center = CollectionUtils.copyOf(shape.center);
		this.scaleX = shape.scaleX;
		this.scaleY = shape.scaleY;
		this.minX = shape.minX;
		this.minY = shape.minY;
		this.maxX = shape.maxX;
		this.maxY = shape.maxY;
		this.boundingCircleRadius = shape.boundingCircleRadius;
		this.pointsDirty = shape.pointsDirty;
		this.triangle = shape.triangle;
		this.trianglesDirty = shape.trianglesDirty;
		this.aabb = shape.aabb != null ? shape.aabb.cpy() : null;
		this.rect = shape.rect != null ? shape.rect.cpy() : null;
		this.entity = shape.entity;
		this.cachedVertexCount = shape.cachedVertexCount;
		this.checkPoints();
		return this;
	}

	private void getVec(int index, Vector2f out) {
		float[] pt = getPoint(index);
		out.set(pt[0], pt[1]);
	}

	public Vector2f raycast(Vector2f origin, Vector2f direction) {
		checkPoints();
		if (cachedVertexCount < 2) {
			return null;
		}
		Vector2f end = vecCache[8];
		end.set(origin).addSelf(direction.nor().scaleSelf(10000));
		return raycastIntersect(origin, end);
	}

	public Vector2f raycastIntersect(Vector2f start, Vector2f end) {
		checkPoints();
		if (cachedVertexCount < 2) {
			return null;
		}
		Vector2f hit = vecCache[9];
		float minDist = Float.MAX_VALUE;
		int count = cachedVertexCount;

		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			Vector2f p1 = vecCache[10];
			Vector2f p2 = vecCache[11];
			getVec(i, p1);
			getVec(j, p2);

			Vector2f pt = Line.intersect(start, end, p1, p2);
			if (pt != null) {
				float dist = pt.distance(start);
				if (dist < minDist) {
					minDist = dist;
					hit.set(pt);
				}
			}
		}
		return minDist == Float.MAX_VALUE ? null : hit;
	}

	public ShapeCollisionInfo collideSATDetailed(Shape other) {
		ShapeCollisionInfo info = new ShapeCollisionInfo();
		Vector2f disp = collideSAT(other);
		if (disp == null) {
			return info;
		}
		info.collided = true;
		info.displacement.set(disp);
		info.normal.set(disp).norSelf();
		info.overlap = disp.length();
		info.hitPoint.set(getCenterPos()).addSelf(info.normal.scaleSelf(boundingCircleRadius));
		return info;
	}

	public void applyCollisionResponse(Shape other, float bounce) {
		ShapeCollisionInfo info = collideSATDetailed(other);
		if (!info.collided) {
			return;
		}
		translate(info.displacement.x * bounce, info.displacement.y * bounce);
	}

	public Vector2f getCollisionNormal(Shape other) {
		return collideSATDetailed(other).normal;
	}

	public float getLinearDamping() {
		return _linearDamping;
	}

	public void setLinearDamping(float linearDamping) {
		this._linearDamping = MathUtils.clamp(linearDamping, 0, 1);
	}

	public float getAngularDamping() {
		return _angularDamping;
	}

	public void setAngularDamping(float angularDamping) {
		this._angularDamping = MathUtils.clamp(angularDamping, 0, 1);
	}

	public String getCollisionTag() {
		return _collisionTag;
	}

	public void setCollisionTag(String tag) {
		this._collisionTag = tag;
	}

	public boolean isPhysicsEnabled() {
		return _physicsEnabled;
	}

	public void setPhysicsEnabled(boolean enabled) {
		this._physicsEnabled = enabled;
	}

	public void addCollisionTarget(Shape shape) {
		if (shape == null || shape == this || _physicsCollisionTargets.contains(shape)) {
			return;
		}
		_physicsCollisionTargets.add(shape);
	}

	public void addCollisionTargets(TArray<Shape> shapes) {
		if (shapes == null || shapes.isEmpty()) {
			return;
		}
		for (Shape s : shapes) {
			addCollisionTarget(s);
		}
	}

	public void clearCollisionTargets() {
		_physicsCollisionTargets.clear();
	}

	public void removeCollisionTarget(Shape shape) {
		_physicsCollisionTargets.removeValue(shape, true);
	}

	public boolean isAutoPhysicsAndCollision() {
		return _autoPhysicsAndCollision;
	}

	public void setAutoPhysicsAndCollision(boolean auto) {
		this._autoPhysicsAndCollision = auto;
	}

	public boolean isAutoResolveCollision() {
		return _autoResolveCollision;
	}

	public void setAutoResolveCollision(boolean auto) {
		this._autoResolveCollision = auto;
	}

	public TArray<Shape> getPhysicsCollisionTargets() {
		return _physicsCollisionTargets;
	}

	public Shape resizeSize(float width, float height) {
		if (width <= 0 || height <= 0 || originPoints == null || originPoints.length == 0) {
			return this;
		}
		float originalWidth = getOriginalWidth();
		float originalHeight = getOriginalHeight();
		if (originalWidth <= 0 || originalHeight <= 0) {
			return this;
		}
		float scaleX = width / originalWidth;
		float scaleY = height / originalHeight;
		resizeShape(scaleX, scaleY);
		return this;
	}

	protected void resizeShape(float scaleX, float scaleY) {
		if (originPoints == null || points == null) {
			return;
		}
		this.scaleX = scaleX;
		this.scaleY = scaleY;
		applyCombinedTransform(getCenterX(), getCenterY());
		pointsDirty = true;
		checkPoints();
	}

	public float getOriginalWidth() {
		if (originPoints == null || originPoints.length < 2) {
			return getWidth();
		}
		float minX = originPoints[0];
		float maxX = originPoints[0];
		for (int i = 0; i < originPoints.length; i += 2) {
			minX = MathUtils.min(minX, originPoints[i]);
			maxX = MathUtils.max(maxX, originPoints[i]);
		}
		return maxX - minX;
	}

	public float getOriginalHeight() {
		if (originPoints == null || originPoints.length < 2) {
			return getHeight();
		}
		float minY = originPoints[1];
		float maxY = originPoints[1];
		for (int i = 1; i < originPoints.length; i += 2) {
			minY = MathUtils.min(minY, originPoints[i]);
			maxY = MathUtils.max(maxY, originPoints[i]);
		}
		return maxY - minY;
	}

	public void syncShapeToSprite(ISprite sprite) {
		if (sprite == null || this.isEmpty()) {
			return;
		}
		sprite.setX(this.getX());
		sprite.setY(this.getY());
		float rotation = this.getRotation();
		sprite.setRotation(rotation);
		float width = this.getWidth();
		float height = this.getHeight();
		sprite.setSize(width, height);
	}

	public void syncSpriteToShape(ISprite sprite) {
		if (sprite == null || this.isEmpty()) {
			return;
		}
		if (getX() != sprite.getX() || getY() != sprite.getY()) {
			this.pointsDirty = true;
			this.setLocation(sprite.getX(), sprite.getY());
		}
		float w = sprite.getWidth();
		float h = sprite.getHeight();
		if (w != getWidth() || h != getHeight()) {
			this.pointsDirty = true;
			this.resizeSize(w, h);
		}
		if (sprite.getRotation() != getRotation()) {
			this.pointsDirty = true;
			setRotation(sprite.getRotation());
		}
		if (sprite.getScaleX() != getScaleX() || sprite.getScaleY() != getScaleY()) {
			this.pointsDirty = true;
			setScale(sprite.getScaleX(), sprite.getScaleY());
		}
		this.checkPoints();

	}

	@Override
	public int size() {
		return points == null ? 0 : points.length;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean isNotEmpty() {
		return !isEmpty();
	}

	@Override
	public void clear() {
		points = new float[0];
		center = new float[0];
		x = y = 0;
		rotation = 0;
		scaleX = scaleY = 1f;
		maxX = maxY = minX = minY = 0;
		boundingCircleRadius = 0;
		pointsDirty = true;
		cachedVertexCount = 0;
		if (_vertices != null) {
			_vertices.clear();
		}
		triangle = null;
		aabb = null;
		rect = null;
		entity = null;
	}

	@Override
	public int hashCode() {
		final int prime = 67;
		int hashCode = 17;
		hashCode = prime * LSystem.unite(hashCode, x);
		hashCode = prime * LSystem.unite(hashCode, y);
		hashCode = prime * LSystem.unite(hashCode, minX);
		hashCode = prime * LSystem.unite(hashCode, minY);
		hashCode = prime * LSystem.unite(hashCode, maxX);
		hashCode = prime * LSystem.unite(hashCode, maxY);
		hashCode = prime * LSystem.unite(hashCode, scaleX);
		hashCode = prime * LSystem.unite(hashCode, scaleY);
		for (int j = 0; j < points.length; j++) {
			final long val = NumberUtils.floatToIntBits(this.points[j]);
			hashCode += prime * hashCode + (int) (val ^ (val >>> 32));
		}
		if (center != null) {
			for (int i = 0; i < center.length; i++) {
				hashCode = prime * LSystem.unite(hashCode, center[i]);
			}
		}
		return hashCode;
	}

	@Override
	public String toString() {
		StringKeyValue builder = new StringKeyValue("Shape");
		builder.kv("pos", x + "," + y).comma().kv("size", getWidth() + "," + getHeight()).comma()
				.kv("scale", scaleX + "," + scaleY).comma().kv("points", "[" + StringUtils.join(',', points) + "]")
				.comma().kv("center", "[" + StringUtils.join(',', center) + "]").comma()
				.kv("circleRadius", boundingCircleRadius).comma().kv("rotation", rotation).comma().kv("minX", minX)
				.kv("minY", minY).comma().kv("maxX", maxX).kv("maxY", maxY);
		return builder.toString();
	}

}
