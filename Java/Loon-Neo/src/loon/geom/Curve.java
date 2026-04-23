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
package loon.geom;

import loon.LSystem;
import loon.action.collision.CollisionHelper;
import loon.utils.MathUtils;

public class Curve extends Shape {

	private static final long serialVersionUID = 1L;

	private static final float discriminantEpsilon = 1e-15f;
	private static final int MIN_SEGMENTS = 1;

	private Vector2f _p1;
	private Vector2f _c1;
	private Vector2f _c2;
	private Vector2f _p2;
	private int _segments;

	public Curve() {
		this(new Vector2f(), new Vector2f(), new Vector2f(), new Vector2f(), 20);
	}

	public Curve(XY start, Bezier b) {
		this(start, b.controlPoint1, b.controlPoint2, b.endPosition, 20);
	}

	public Curve(XY p1, XY c1, XY c2, XY p2) {
		this(p1, c1, c2, p2, 20);
	}

	public Curve(XY p1, XY c1, XY c2, XY p2, int segments) {
		set(p1, c1, c2, p2, segments);
	}

	public Curve set(float px1, float py1, float cx1, float cy1, float cx2, float cy2, float px2, float py2) {
		return set(px1, py1, cx1, cy1, cx2, cy2, px2, py2, this._segments <= 0 ? MIN_SEGMENTS : this._segments);
	}

	public Curve set(float px1, float py1, float cx1, float cy1, float cx2, float cy2, float px2, float py2,
			int segments) {
		if (_p1 == null) {
			_p1 = new Vector2f();
		}
		if (_c1 == null) {
			_c1 = new Vector2f();
		}
		if (_c2 == null) {
			_c2 = new Vector2f();
		}
		if (_p2 == null) {
			_p2 = new Vector2f();
		}
		this._p1.set(px1, py1);
		this._c1.set(cx1, cy1);
		this._c2.set(cx2, cy2);
		this._p2.set(px2, py2);
		this._segments = MathUtils.max(segments, MIN_SEGMENTS);
		this.pointsDirty = true;
		return this;
	}

	public Curve set(XY p1, XY c1, XY c2, XY p2) {
		return set(p1, c1, c2, p2, this._segments <= 0 ? MIN_SEGMENTS : this._segments);
	}

	public Curve set(XY p1, XY c1, XY c2, XY p2, int segments) {
		if (p1 == null || c1 == null || c2 == null || p2 == null) {
			throw new NullPointerException("Curve control points must not be null");
		}
		this._p1 = new Vector2f(p1);
		this._c1 = new Vector2f(c1);
		this._c2 = new Vector2f(c2);
		this._p2 = new Vector2f(p2);
		this._segments = MathUtils.max(segments, MIN_SEGMENTS);
		this.pointsDirty = true;
		return this;
	}

	public Curve setEmpty() {
		if (_p1 == null) {
			_p1 = new Vector2f();
		}
		if (_c1 == null) {
			_c1 = new Vector2f();
		}
		if (_c2 == null) {
			_c2 = new Vector2f();
		}
		if (_p2 == null) {
			_p2 = new Vector2f();
		}
		_p1.setEmpty();
		_c1.setEmpty();
		_c2.setEmpty();
		_p2.setEmpty();
		_segments = 0;
		this.pointsDirty = true;
		return this;
	}

	@Override
	public void clear() {
		super.clear();
		setEmpty();
	}

	public boolean intersects(Line line) {
		if (line == null) {
			return false;
		}
		return CollisionHelper.checkIntersectCubicBezierCurveAndLine(_p1, _c1, _c2, _p2, line.getStart(),
				line.getEnd());
	}

	@Override
	public boolean intersects(XY xy) {
		if (xy == null) {
			return false;
		}
		Vector2f p = Vector2f.at(xy);
		Vector2f p2 = new Vector2f(p.x + 1e-3f, p.y + 1e-3f);
		return CollisionHelper.checkIntersectCubicBezierCurveAndLine(_p1, _c1, _c2, _p2, p, p2);
	}

	public float[] solveCubic(float a, float b, float c, float d) {
		if (MathUtils.abs(a) < Float.MIN_VALUE) {
			return solveQuadratic(b, c, d);
		}
		if (MathUtils.abs(b) < Float.MIN_VALUE) {
			return solveDepressedCubic(c / a, d / a, 1f);
		} else {
			final float s = b / (3f * a);
			final float p = c / a - 3f * s * s;
			final float q = d / a - (p + s * s) * s;
			final float[] tmp = solveDepressedCubic(p, q, 1f);
			final float[] result = new float[tmp.length];
			for (int i = 0; i < tmp.length; i++) {
				result[i] = tmp[i] - s;
			}
			return result;
		}
	}

	private float cubicRoot(float x) {
		if (x >= 0) {
			return MathUtils.pow(x, 1f / 3f);
		} else {
			return -MathUtils.pow(-x, 1f / 3f);
		}
	}

	private float[] solveDepressedCubic(float p, float q, float tau) {
		final float discriminant = MathUtils.abs(q * q / 4f + p * p * p / 27f);
		if (discriminant < discriminantEpsilon) {
			final float x1 = cubicRoot(q / 2f);
			final float x2 = -2 * x1;
			return new float[] { x1, x1, x2 };
		} else if (discriminant > 0) {
			final float w = cubicRoot(MathUtils.abs(q) / 2f + MathUtils.sqrt(discriminant));
			return new float[] { (p / (3 * w) - w) * MathUtils.sign(q) };
		} else {
			final float f = 2 * MathUtils.sqrt(-p / 3f);
			final float v = MathUtils.acos(3f * q / (f * p)) / 3f;
			final float x0 = f * MathUtils.cos(v);
			final float x1 = f * MathUtils.cos(v - 1 / 3f * tau);
			final float x2 = f * MathUtils.cos(v - 2 / 3f * tau);
			return new float[] { x0, x1, x2 };
		}
	}

	public float[] solveQuadratic(float a, float b, float c) {
		if (a == 0f) {
			return new float[] { -c / b };
		}
		final float det = b * b - 4f * a * c;
		if (det >= 0f) {
			final float sqrtDet = MathUtils.sqrt(det);
			return new float[] { (-b - sqrtDet) / (2 * a), (-b + sqrtDet) / (2 * a) };
		} else {
			return new float[] {};
		}
	}

	public Vector2f pointAt(float t) {
		t = MathUtils.max(0f, MathUtils.min(1f, t));
		float a = 1 - t;
		float b = t;

		float f1 = a * a * a;
		float f2 = 3f * a * a * b;
		float f3 = 3f * a * b * b;
		float f4 = b * b * b;

		float nx = (_p1.x * f1) + (_c1.x * f2) + (_c2.x * f3) + (_p2.x * f4);
		float ny = (_p1.y * f1) + (_c1.y * f2) + (_c2.y * f3) + (_p2.y * f4);

		return new Vector2f(nx, ny);
	}

	@Override
	protected void createPoints() {
		if (_segments <= 0) {
			points = new float[0];
			return;
		}
		float step = 1.0f / _segments;
		points = new float[(_segments + 1) * 2];
		for (int i = 0; i <= _segments; i++) {
			float t = i * step;
			Vector2f p = pointAt(t);
			points[i * 2] = p.x;
			points[(i * 2) + 1] = p.y;
		}
	}

	@Override
	public Shape transform(Matrix3 transform) {
		float[] pts = new float[8];
		float[] dest = new float[8];
		pts[0] = _p1.x;
		pts[1] = _p1.y;
		pts[2] = _c1.x;
		pts[3] = _c1.y;
		pts[4] = _c2.x;
		pts[5] = _c2.y;
		pts[6] = _p2.x;
		pts[7] = _p2.y;
		transform.transform(pts, 0, dest, 0, 4);

		return new Curve(new Vector2f(dest[0], dest[1]), new Vector2f(dest[2], dest[3]), new Vector2f(dest[4], dest[5]),
				new Vector2f(dest[6], dest[7]), this._segments);
	}

	@Override
	public boolean closed() {
		return false;
	}

	public boolean equals(Curve e) {
		if (e == null) {
			return false;
		}
		if (e == this) {
			return true;
		}
		if (_p1.equals(e._p1) && _p2.equals(e._p2) && _c1.equals(e._c1) && _c2.equals(e._c2) && _segments == e._segments
				&& equalsRotateScale(this.rotation, this.scaleX, this.scaleY)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (obj instanceof Curve) {
			return equals((Curve) obj);
		}
		return false;
	}

	public Vector2f getP1() {
		return new Vector2f(_p1);
	}

	public Vector2f getC1() {
		return new Vector2f(_c1);
	}

	public Vector2f getC2() {
		return new Vector2f(_c2);
	}

	public Vector2f getP2() {
		return new Vector2f(_p2);
	}

	public int getSegments() {
		return _segments;
	}

	public void setSegments(int segments) {
		this._segments = MathUtils.max(segments, MIN_SEGMENTS);
		this.pointsDirty = true;
	}

	public Curve copy(Curve e) {
		if (e == null) {
			return this;
		}
		if (equals(e)) {
			return this;
		}
		this._p1.set(e._p1);
		this._c1.set(e._c1);
		this._c2.set(e._c2);
		this._p2.set(e._p2);
		this._segments = e._segments;
		this.x = e.x;
		this.y = e.y;
		this.rotation = e.rotation;
		this.boundingCircleRadius = e.boundingCircleRadius;
		this.minX = e.minX;
		this.minY = e.minY;
		this.maxX = e.maxX;
		this.maxY = e.maxY;
		this.scaleX = e.scaleX;
		this.scaleY = e.scaleY;
		this.pointsDirty = true;
		checkPoints();
		return this;
	}

	@Override
	public Curve copy(Shape e) {
		if (e instanceof Curve) {
			copy((Curve) e);
		} else {
			super.copy(e);
		}
		return this;
	}

	@Override
	public Curve cpy() {
		return new Curve(new Vector2f(this._p1), new Vector2f(this._c1), new Vector2f(this._c2), new Vector2f(this._p2),
				this._segments);
	}

	@Override
	public int hashCode() {
		final int prime = 37;
		int hashCode = 1;
		hashCode = prime * LSystem.unite(hashCode, x);
		hashCode = prime * LSystem.unite(hashCode, y);
		hashCode = prime * LSystem.unite(hashCode, _p1.getX());
		hashCode = prime * LSystem.unite(hashCode, _p1.getY());
		hashCode = prime * LSystem.unite(hashCode, _c1.getX());
		hashCode = prime * LSystem.unite(hashCode, _c1.getY());
		hashCode = prime * LSystem.unite(hashCode, _p2.getX());
		hashCode = prime * LSystem.unite(hashCode, _p2.getY());
		hashCode = prime * LSystem.unite(hashCode, _c2.getX());
		hashCode = prime * LSystem.unite(hashCode, _c2.getY());
		hashCode = prime * LSystem.unite(hashCode, _segments);
		return hashCode;
	}

	@Override
	public String toString() {
		return "Curve[p1=" + _p1 + ", c1=" + _c1 + ", c2=" + _c2 + ", p2=" + _p2 + ", segments=" + _segments + "]";
	}

}
