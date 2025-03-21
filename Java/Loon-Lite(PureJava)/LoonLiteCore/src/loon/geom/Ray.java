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
package loon.geom;

import loon.utils.MathUtils;
import loon.utils.StringKeyValue;
import loon.utils.TempVars;

public class Ray {

	public static Ray create(float ox, float oy, float oz, float dx, float dy) {
		return create(ox, oy, oz, dx, dy, 1f);
	}

	public static Ray create(float ox, float oy, float oz, float dx, float dy, float dz) {
		return new Ray(Vector3f.at(ox, oy, oz), Vector3f.at(dx, dy, dz));
	}

	private final Vector3f _origin = new Vector3f();

	private final Vector3f _direction = new Vector3f();

	public Ray(Vector3f origin, Vector3f direction) {
		set(origin, direction);
	}

	public Ray set(Vector3f origin, Vector3f direction) {
		if (origin != null) {
			_origin.set(origin);
		}
		if (direction != null) {
			_direction.set(direction);
		}
		return this;
	}

	public Ray set(Ray src) {
		if (src == null) {
			return this;
		}
		return set(src._direction, src._origin);
	}

	public Ray copyFrom(Ray src) {
		return set(src);
	}

	public Vector3f getOrigin() {
		return _origin;
	}

	public Vector3f getDirection() {
		return _direction;
	}

	public Vector3f getPoint(float distance) {
		Vector3f dir = this._direction.scale(distance);
		return dir.addSelf(this._origin);
	}

	public Vector3f at(float distance) {
		return getPoint(distance);
	}

	public Vector3f intersectPoint(Line line) {
		float time = this.intersectLine(line);
		if (time < 0) {
			return null;
		}
		return this.getPoint(time);
	}

	public float intersectLine(Line line) {
		Vector2f pos = this._origin.getXY();
		Vector2f dir = this._direction.getXY();
		Vector2f numerator = line.getStart().sub(pos);
		if (dir.cross(line.getSlope()) == 0 && numerator.cross(dir) != 0) {
			return -1;
		}
		float divisor = dir.cross(line.getSlope());
		if (divisor == 0) {
			return -1;
		}
		float t = numerator.cross(line.getSlope()) / divisor;
		if (t >= 0) {
			float u = numerator.cross(dir) / divisor / line.length();
			if (u >= 0 && u <= 1) {
				return t;
			}
		}
		return -1;
	}

	public float intersectsPlane(Plane p) {
		Vector3f normal = p.getNormal();
		float dir = normal.dot(this._direction);
		if (MathUtils.abs(dir) < MathUtils.ZEROTOLERANCE) {
			return -1f;
		}
		float position = normal.dot(this._origin);
		float distance = (-p.getDistance() - position) / dir;
		if (distance < 0) {
			if (distance < -MathUtils.ZEROTOLERANCE) {
				return -1f;
			}
			distance = 0f;
		}
		return distance;
	}

	public float intersectsAABB(AABB aabb) {
		final Vector3f min = aabb.min();
		final Vector3f max = aabb.max();
		float dirX = _direction.x;
		float dirY = _direction.y;
		float dirZ = _direction.z;
		float oriX = _origin.x;
		float oriY = _origin.y;
		float oriZ = _origin.z;
		float distance = 0;
		float tmax = Float.MAX_VALUE;
		if (MathUtils.abs(dirX) < MathUtils.ZEROTOLERANCE) {
			if (oriX < min.x || oriX > max.x) {
				return -1;
			}
		} else {
			float inverse = 1f / dirX;
			float t1 = (min.x - oriX) * inverse;
			float t2 = (max.x - oriX) * inverse;
			if (t1 > t2) {
				float temp = t1;
				t1 = t2;
				t2 = temp;
			}
			distance = MathUtils.max(t1, distance);
			tmax = MathUtils.min(t2, tmax);
			if (distance > tmax) {
				return -1;
			}
		}
		if (MathUtils.abs(dirY) < MathUtils.ZEROTOLERANCE) {
			if (oriY < min.y || oriY > max.y) {
				return -1;
			}
		} else {
			float inverse = 1f / dirY;
			float t1 = (min.y - oriY) * inverse;
			float t2 = (max.y - oriY) * inverse;
			if (t1 > t2) {
				float temp = t1;
				t1 = t2;
				t2 = temp;
			}
			distance = MathUtils.max(t1, distance);
			tmax = MathUtils.min(t2, tmax);
			if (distance > tmax) {
				return -1;
			}
		}
		if (MathUtils.abs(dirZ) < MathUtils.ZEROTOLERANCE) {
			if (oriZ < min.z || oriZ > max.z) {
				return -1;
			}
		} else {
			float inverse = 1f / dirZ;
			float t1 = (min.z - oriZ) * inverse;
			float t2 = (max.z - oriZ) * inverse;
			if (t1 > t2) {
				float temp = t1;
				t1 = t2;
				t2 = temp;
			}
			distance = MathUtils.max(t1, distance);
			tmax = MathUtils.min(t2, tmax);
			if (distance > tmax) {
				return -1;
			}
		}
		return distance;
	}

	public float intersectsSphere(Sphere s) {
		Vector3f center = s.getCenter();
		float r = s.getRadius();
		Vector3f m = _origin.sub(center);
		float b = m.dot(_direction);
		float c = m.dot(m) - r * r;
		if (b > 0 && c > 0) {
			return -1f;
		}
		float discriminant = b * b - c;
		if (discriminant < 0) {
			return -1f;
		}
		float distance = -b - MathUtils.sqrt(discriminant);
		if (distance < 0) {
			distance = 0;
		}
		return distance;
	}

	public float distanceSquared(Vector3f point) {
		TempVars vars = TempVars.getClean3f();
		Vector3f tempVa = vars.vec3f1, tempVb = vars.vec3f2;
		Vector3f.sub(point, _origin, tempVa);
		float rayParam = _direction.dot(tempVa);
		if (rayParam > 0) {
			Vector3f.add(_origin, Vector3f.mul(_direction, rayParam, tempVb), tempVb);
		} else {
			tempVb.set(_origin);
			rayParam = 0f;
		}
		Vector3f.sub(tempVb, point, tempVa);
		return tempVa.lengthSquared();
	}

	public Vector3f getEndPoint(float distance, Vector3f o) {
		return o.set(this._direction).scaleSelf(distance).addSelf(this._origin);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || o.getClass() != this.getClass()) {
			return false;
		}
		if (o == this) {
			return true;
		}
		Ray r = (Ray) o;
		return this._direction.equals(r._direction) && this._origin.equals(r._origin);
	}

	@Override
	public int hashCode() {
		final int prime = 43;
		int result = 1;
		result = prime * result + this._direction.hashCode();
		result = prime * result + this._origin.hashCode();
		return result;
	}

	public Ray cpy() {
		return new Ray(this._origin, this._direction);
	}

	@Override
	public String toString() {
		return new StringKeyValue("Ray").kv("origin", _origin).comma().kv("direction", _direction).toString();
	}

}
