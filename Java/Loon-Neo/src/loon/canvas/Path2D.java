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
package loon.canvas;

import loon.LSystem;
import loon.geom.Curve;
import loon.geom.Polygon;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.CollectionUtils;
import loon.utils.FloatArray;
import loon.utils.MathUtils;
import loon.utils.TArray;

/**
 * 一个Path的本地具体实现,用于Pixmap和GLEx渲染
 */
public class Path2D implements Path {

	public static enum PathCommand {
		MoveTo, LineTo, CurveTo, CubicCurveTo, ClearSubPath, Stroke, Fill, Closed
	}

	private final TArray<PathCommand> _commands;
	private final FloatArray _tempData;
	private final FloatArray _data;
	private final Polygon _currentPolys = new Polygon();
	private final TArray<Polygon> _polygons = new TArray<Polygon>();
	private final Curve _curve = new Curve();
	private final TArray<float[]> _linePaths = new TArray<float[]>();
	private int _segments = LSystem.LAYER_TILE_SIZE;
	private float _deltaT;
	private float _lastX;
	private float _lastY;
	private float _lastStartX;
	private float _lastStartY;
	private boolean _dirty;

	private static final float MIN_TOLERANCE = 0.0001f;
	private static final float DUPLICATE_EPSILON = 0.001f;
	private static final int MAX_TESSELATION_LEVEL = 8;
	private static final int MIN_SEGMENTS = 4;
	private static final int MAX_SEGMENTS = 64;

	public Path2D() {
		this(CollectionUtils.INITIAL_CAPACITY);
	}

	public Path2D(int capacity) {
		capacity = MathUtils.max(16, capacity);
		this._commands = new TArray<PathCommand>(capacity);
		this._data = new FloatArray(capacity);
		this._tempData = new FloatArray(capacity);
	}

	public Path verticalMoveTo(float y) {
		return moveTo(_lastX, y);
	}

	public Path verticalMoveToRel(float y) {
		return moveTo(_lastX, _lastY + y);
	}

	public Path horizontalMoveTo(float x) {
		return moveTo(x, _lastY);
	}

	public Path horizontalMoveToRel(float x) {
		return moveTo(_lastX + x, _lastY);
	}

	public Path lineToRel(float x, float y) {
		return lineTo(_lastX + x, _lastY + y);
	}

	public Path curveToRel(float cx, float cy, float ax, float ay) {
		return curveTo(_lastX + cx, _lastY + cy, _lastX + ax, _lastY + ay);
	}

	public Path moveToRel(float x, float y) {
		return moveTo(_lastX + x, _lastY + y);
	}

	@Override
	public Path moveTo(float x, float y) {
		if (!_commands.isEmpty() && isDrawingLine()) {
			_commands.add(PathCommand.ClearSubPath);
		}
		_commands.add(PathCommand.MoveTo);
		add(x, y);
		_lastX = x;
		_lastY = y;
		_lastStartX = x;
		_lastStartY = y;
		_dirty = true;
		return this;
	}

	private boolean isDrawingLine() {
		if (_commands.isEmpty()) {
			return false;
		}
		PathCommand last = _commands.last();
		return last == PathCommand.LineTo || last == PathCommand.CurveTo || last == PathCommand.CubicCurveTo;
	}

	@Override
	public Path lineTo(float x, float y) {
		_commands.add(PathCommand.LineTo);
		add(x, y);
		_lastX = x;
		_lastY = y;
		_dirty = true;
		return this;
	}

	public int nextIndex(int index) {
		int len = length();
		if (len <= 0) {
			return 0;
		}
		if (index < 0 || index >= len) {
			return 0;
		}
		return (index == len - 1) ? 0 : index + 1;
	}

	public int previousIndex(int index) {
		int len = length();
		if (len <= 0) {
			return 0;
		}
		if (index < 0 || index >= len) {
			return len - 1;
		}
		return (index == 0) ? len - 1 : index - 1;
	}

	public void add(Vector2f pos) {
		if (pos == null) {
			return;
		}
		add(pos.x, pos.y);
	}

	public void add(float x, float y) {
		_data.add(x);
		_data.add(y);
		int len = length();
		_deltaT = len > 1 ? 1f / (len - 1) : 0f;
		_dirty = true;
	}

	public void set(int index, float x, float y) {
		int len = length();
		if (index < 0 || index >= len) {
			return;
		}
		int idx = index * 2;
		if (idx + 1 >= _data.size()) {
			return;
		}
		_data.set(idx, x);
		_data.set(idx + 1, y);
		_dirty = true;
	}

	public void setDataLength(int idx, float x, float y) {
		if (idx < 0 || idx + 1 >= _data.size()) {
			return;
		}
		_data.set(idx, x);
		_data.set(idx + 1, y);
		int len = length();
		_deltaT = len > 1 ? 1f / (len - 1) : 0f;
		_dirty = true;
	}

	public void addAll(float[] points) {
		if (points == null || points.length == 0 || points.length % 2 != 0) {
			return;
		}
		_data.addAll(points);
		int len = length();
		_deltaT = len > 1 ? 1f / (len - 1) : 0f;
		_dirty = true;
	}

	public Vector2f getVector(int index) {
		int len = length();
		if (len == 0 || index < 0 || index >= len) {
			return new Vector2f(0, 0);
		}
		int idx = index * 2;
		if (idx + 1 >= _data.size()) {
			return new Vector2f(0, 0);
		}
		return new Vector2f(_data.get(idx), _data.get(idx + 1));
	}

	public float getDelta() {
		return _deltaT;
	}

	public void removeAt(int index) {
		int len = length();
		if (len <= 1 || index < 0 || index >= len) {
			return;
		}
		int idx = index * 2;
		if (idx + 1 >= _data.size()) {
			return;
		}
		_data.removeIndex(idx + 1);
		_data.removeIndex(idx);
		len = length();
		_deltaT = len > 1 ? 1f / (len - 1) : 0f;
		_dirty = true;
	}

	public TArray<Vector2f> getVertices(int divisions) {
		return getVertices(divisions, isClosed());
	}

	public TArray<Vector2f> getVertices(int divisions, boolean closed) {
		TArray<Vector2f> verts = new TArray<Vector2f>();
		int len = length();
		if (len < 2) {
			return verts;
		}
		divisions = MathUtils.max(MIN_SEGMENTS, MathUtils.min(divisions, MAX_SEGMENTS));
		float step = 1f / divisions;
		for (float t = 0; t <= 1f + MIN_TOLERANCE; t += step) {
			verts.add(getPosition(t, closed));
		}
		return verts;
	}

	public Vector2f getPosition(float time) {
		return getPosition(time, isClosed());
	}

	public Vector2f getPosition(float time, boolean closed) {
		int len = length();
		if (len < 2) {
			return new Vector2f(_lastX, _lastY);
		}
		time = MathUtils.clamp(time, 0f, 1f);
		Vector2f result;
		if (closed) {
			add(_data.get(0), _data.get(1));
			int p = (int) (time / _deltaT);
			int p0 = MathUtils.floorMod(p - 1, len);
			int p1 = MathUtils.floorMod(p, len);
			int p2 = MathUtils.floorMod(p + 1, len);
			int p3 = MathUtils.floorMod(p + 2, len);
			float lt = (time - _deltaT * p) / _deltaT;
			result = Vector2f.catmullRom(getVector(p0), getVector(p1), getVector(p2), getVector(p3), lt);
			removeAt(len);
		} else {
			int p = (int) (time / _deltaT);
			p = MathUtils.clamp(p, 0, len - 2);
			int p0 = MathUtils.clamp(p - 1, 0, len - 1);
			int p1 = MathUtils.clamp(p, 0, len - 1);
			int p2 = MathUtils.clamp(p + 1, 0, len - 1);
			int p3 = MathUtils.clamp(p + 2, 0, len - 1);
			float lt = MathUtils.clamp((time - _deltaT * p) / _deltaT, 0f, 1f);
			result = Vector2f.catmullRom(getVector(p0), getVector(p1), getVector(p2), getVector(p3), lt);
		}
		return result;
	}

	public Path fill() {
		_commands.add(PathCommand.Fill);
		return this;
	}

	public Path stroke() {
		_commands.add(PathCommand.Stroke);
		return this;
	}

	public Path curveTo(float cx, float cy, float ax, float ay) {
		return curveTo(cx, cy, ax, ay, _segments);
	}

	public Path curveTo(float cx, float cy, float ax, float ay, int segments) {
		_commands.add(PathCommand.CurveTo);
		segments = MathUtils.clamp(segments, MIN_SEGMENTS, MAX_SEGMENTS);
		_curve.set(_lastX, _lastY, cx, cy, ax, ay, ax, ay, segments);
		addAll(_curve.getPoints());
		_lastX = ax;
		_lastY = ay;
		_dirty = true;
		return this;
	}

	public Path cubicCurveTo(float cx1, float cy1, float cx2, float cy2, float ax, float ay) {
		return cubicCurveTo(cx1, cy1, cx2, cy2, ax, ay, _segments);
	}

	public Path cubicCurveTo(float cx1, float cy1, float cx2, float cy2, float ax, float ay, int segments) {
		_commands.add(PathCommand.CubicCurveTo);
		segments = MathUtils.clamp(segments, MIN_SEGMENTS, MAX_SEGMENTS);
		_curve.set(_lastX, _lastY, cx1, cy1, cx2, cy2, ax, ay, segments);
		addAll(_curve.getPoints());
		_lastX = ax;
		_lastY = ay;
		_dirty = true;
		return this;
	}

	public Path quadToRel(float cx, float cy, float x, float y) {
		return quadTo(_lastX + cx, _lastY + cy, _lastX + x, _lastY + y);
	}

	public Path quadTo(float cx, float cy, float x, float y) {
		float c1x = _lastX + 2.0f / 3.0f * (cx - _lastX);
		float c1y = _lastY + 2.0f / 3.0f * (cy - _lastY);
		float c2x = x + 2.0f / 3.0f * (cx - x);
		float c2y = y + 2.0f / 3.0f * (cy - y);
		return cubicCurveTo(c1x, c1y, c2x, c2y, x, y);
	}

	public Path cubicToRel(float cx1, float cy1, float cx2, float cy2, float x, float y) {
		return cubicCurveTo(_lastX + cx1, _lastY + cy1, _lastX + cx2, _lastY + cy2, _lastX + x, _lastY + y);
	}

	@Override
	public Path quadraticCurveTo(float cpx, float cpy, float x, float y) {
		return curveTo(cpx, cpy, x, y);
	}

	@Override
	public Path bezierTo(float c1x, float c1y, float c2x, float c2y, float x, float y) {
		return cubicCurveTo(c1x, c1y, c2x, c2y, x, y);
	}

	public Path line(float sx, float sy, float ex, float ey) {
		return moveTo(sx, sy).lineTo(ex, ey);
	}

	public Path lines(float... points) {
		if (points == null || points.length < 4 || points.length % 4 != 0)
			return this;
		return lines(points, 0, points.length / 4);
	}

	public Path lines(float[] points, int offset, int count) {
		if (points == null || offset < 0 || count <= 0) {
			return this;
		}
		int max = offset + count * 4;
		if (max > points.length) {
			return this;
		}
		for (int i = offset; i < max; i += 4) {
			moveTo(points[i], points[i + 1]).lineTo(points[i + 2], points[i + 3]);
		}
		return this;
	}

	public Path lines(FloatArray points) {
		if (points == null || points.size() < 4) {
			return this;
		}
		return lines(points, 0, points.size() / 4);
	}

	public Path lines(FloatArray points, int offset, int count) {
		if (points == null || offset < 0 || count <= 0) {
			return this;
		}
		int max = offset + count * 4;
		if (max > points.size()) {
			return this;
		}
		for (int i = offset; i < max; i += 4) {
			moveTo(points.get(i), points.get(i + 1)).lineTo(points.get(i + 2), points.get(i + 3));
		}
		return this;
	}

	public Path lines(Vector2f... points) {
		if (points == null || points.length < 2) {
			return this;
		}
		return lines(points, 0, points.length / 2);
	}

	public Path lines(Vector2f[] points, int offset, int count) {
		if (points == null || offset < 0 || count <= 0) {
			return this;
		}
		int max = offset + count * 2;
		if (max > points.length) {
			return this;
		}
		for (int i = offset; i < max; i += 2) {
			Vector2f s = points[i];
			Vector2f e = points[i + 1];
			if (s == null || e == null) {
				continue;
			}
			moveTo(s.x, s.y).lineTo(e.x, e.y);
		}
		return this;
	}

	public Path polyline(float... points) {
		if (points == null || points.length < 4 || points.length % 2 != 0) {
			return this;
		}
		moveTo(points[0], points[1]);
		for (int i = 2; i < points.length; i += 2) {
			lineTo(points[i], points[i + 1]);
		}
		return this;
	}

	public Path polyline(FloatArray points) {
		if (points == null || points.size() < 4 || points.size() % 2 != 0) {
			return this;
		}
		moveTo(points.get(0), points.get(1));
		for (int i = 2; i < points.size(); i += 2) {
			lineTo(points.get(i), points.get(i + 1));
		}
		return this;
	}

	public Path polyline(Vector2f... points) {
		if (points == null || points.length < 2) {
			return this;
		}
		moveTo(points[0].x, points[0].y);
		for (int i = 1; i < points.length; i++) {
			if (points[i] == null) {
				continue;
			}
			lineTo(points[i].x, points[i].y);
		}
		return this;
	}

	public Path polygon(float... points) {
		if (points == null || points.length < 4 || points.length % 2 != 0) {
			return this;
		}
		moveTo(points[0], points[1]);
		for (int i = 2; i < points.length; i += 2) {
			lineTo(points[i], points[i + 1]);
		}
		return close();
	}

	public Path polygon(FloatArray points) {
		if (points == null || points.size() < 4 || points.size() % 2 != 0) {
			return this;
		}
		moveTo(points.get(0), points.get(1));
		for (int i = 2; i < points.size(); i += 2) {
			lineTo(points.get(i), points.get(i + 1));
		}
		return close();
	}

	public Path polygon(Vector2f... points) {
		if (points == null || points.length < 2) {
			return this;
		}
		moveTo(points[0].x, points[0].y);
		for (int i = 1; i < points.length; i++) {
			if (points[i] == null) {
				continue;
			}
			lineTo(points[i].x, points[i].y);
		}
		return close();
	}

	public Path oval(float x, float y, float w, float h, int segments) {
		return arcToBezier(x, y, w * 0.5f, h * 0.5f, 0, MathUtils.TWO_PI, false, segments);
	}

	public Path oval(float x, float y, float w, float h) {
		return oval(x, y, w, h, _segments);
	}

	public Path arcToBezier(float x, float y, float rx, float ry, float end) {
		return arcToBezier(x, y, rx, ry, 0, end);
	}

	public Path arcToBezier(float x, float y, float rx, float ry, float start, float end) {
		return arcToBezier(x, y, rx, ry, start, end, false, _segments);
	}

	public Path arcToBezier(float x, float y, float rx, float ry, float start, float end, boolean acw, int segments) {
		rx = MathUtils.max(MIN_TOLERANCE, rx);
		ry = MathUtils.max(MIN_TOLERANCE, ry);
		segments = MathUtils.clamp(segments, MIN_SEGMENTS, MAX_SEGMENTS);
		float total = acw ? start - end : end - start;
		if (MathUtils.abs(total) < MIN_TOLERANCE) {
			return this;
		}
		float cx = x + rx;
		float cy = y + ry;
		float step = total / segments;
		float curr = start;
		float sx = cx + MathUtils.cos(curr) * rx;
		float sy = cy + MathUtils.sin(curr) * ry;
		moveTo(sx, sy);
		for (int i = 0; i < segments; i++) {
			float next = curr + step;
			float k = 0.551915f;
			float ca = curr + step * k;
			float na = next - step * k;
			float c1x = cx + MathUtils.cos(ca) * rx;
			float c1y = cy + MathUtils.sin(ca) * ry;
			float c2x = cx + MathUtils.cos(na) * rx;
			float c2y = cy + MathUtils.sin(na) * ry;
			float ex = cx + MathUtils.cos(next) * rx;
			float ey = cy + MathUtils.sin(next) * ry;
			cubicCurveTo(c1x, c1y, c2x, c2y, ex, ey);
			curr = next;
		}
		return this;
	}

	public Path drawRect(float x, float y, float w, float h) {
		if (w < MIN_TOLERANCE || h < MIN_TOLERANCE) {
			return this;
		}
		moveTo(x, y);
		lineTo(x + w, y);
		lineTo(x + w, y + h);
		lineTo(x, y + h);
		close();
		stroke();
		return this;
	}

	public Path drawCircle(float x, float y, float r, int segments) {
		return arcToBezier(x - r, y - r, r, r, 0, MathUtils.TWO_PI, false, segments);
	}

	public Path drawCircle(float x, float y, float r) {
		return drawCircle(x, y, r, _segments);
	}

	public Path drawEllipse(float x, float y, float w, float h, int segments) {
		return arcToBezier(x, y, w * 0.5f, h * 0.5f, 0, MathUtils.TWO_PI, false, segments);
	}

	public Path drawEllipse(float x, float y, float w, float h) {
		return drawEllipse(x, y, w, h, _segments);
	}

	public Path drawRoundRect(float x, float y, float w, float h, float ew, float eh) {
		return drawRoundRect(x, y, w, h, ew, eh, _segments);
	}

	public Path drawRoundRect(float x, float y, float w, float h, float ew, float eh, int s) {
		if (w < MIN_TOLERANCE || h < MIN_TOLERANCE) {
			return this;
		}
		float rx = MathUtils.clamp(ew * 0.5f, 0, w * 0.5f);
		float ry = MathUtils.clamp(eh * 0.5f, 0, h * 0.5f);
		s = MathUtils.clamp(s, MIN_SEGMENTS, MAX_SEGMENTS);

		moveTo(x + rx, y);
		lineTo(x + w - rx, y);
		curveTo(x + w, y, x + w, y + ry, s);

		lineTo(x + w, y + h - ry);
		curveTo(x + w, y + h, x + w - rx, y + h, s);

		lineTo(x + rx, y + h);
		curveTo(x, y + h, x, y + h - ry, s);

		lineTo(x, y + ry);
		curveTo(x, y, x + rx, y, s);

		close();
		return this;
	}

	public Path drawSmoothLine(Vector2f[] points) {
		if (points == null || points.length < 2) {
			return this;
		}
		moveTo(points[0].x, points[0].y);
		for (int i = 1; i < points.length; i++) {
			Vector2f p0 = i > 1 ? points[i - 2] : points[0];
			Vector2f p1 = points[i - 1];
			Vector2f p2 = points[i];
			Vector2f p3 = i < points.length - 1 ? points[i + 1] : points[i];

			for (float t = 0; t < 1; t += 0.1f) {
				Vector2f v = Vector2f.catmullRom(p0, p1, p2, p3, t);
				lineTo(v.x, v.y);
			}
		}
		stroke();
		return this;
	}

	public Path drawAxis(float x, float y, float width, float height) {
		moveTo(x, y);
		lineTo(x + width, y);
		moveTo(x, y);
		lineTo(x, y - height);
		stroke();
		return this;
	}

	public Path drawGrid(float x, float y, float w, float h, int cols, int rows) {
		float cw = w / cols;
		float ch = h / rows;
		for (int i = 1; i < cols; i++) {
			moveTo(x + cw * i, y);
			lineTo(x + cw * i, y - h);
		}
		for (int i = 1; i < rows; i++) {
			moveTo(x, y - ch * i);
			lineTo(x + w, y - ch * i);
		}
		stroke();
		return this;
	}

	public Path drawArrow(float fromX, float fromY, float toX, float toY, float headSize) {
		moveTo(fromX, fromY);
		lineTo(toX, toY);

		float angle = MathUtils.atan2(toY - fromY, toX - fromX);
		float p1x = toX - headSize * MathUtils.cos(angle - MathUtils.PI / 6);
		float p1y = toY - headSize * MathUtils.sin(angle - MathUtils.PI / 6);
		float p2x = toX - headSize * MathUtils.cos(angle + MathUtils.PI / 6);
		float p2y = toY - headSize * MathUtils.sin(angle + MathUtils.PI / 6);

		moveTo(toX, toY);
		lineTo(p1x, p1y);
		moveTo(toX, toY);
		lineTo(p2x, p2y);
		stroke();
		return this;
	}

	public Path drawRadar(float cx, float cy, float radius, int sides, int layers) {
		sides = MathUtils.max(3, sides);
		layers = MathUtils.max(1, layers);
		radius = MathUtils.max(MIN_TOLERANCE, radius);
		float step = MathUtils.TWO_PI / sides;
		for (int l = 1; l <= layers; l++) {
			float r = radius * l / layers;
			moveTo(cx + r, cy);
			for (int i = 1; i <= sides; i++) {
				float a = step * i;
				lineTo(cx + MathUtils.cos(a) * r, cy + MathUtils.sin(a) * r);
			}
			close();
		}
		for (int i = 0; i < sides; i++) {
			float a = step * i;
			moveTo(cx, cy);
			lineTo(cx + MathUtils.cos(a) * radius, cy + MathUtils.sin(a) * radius);
		}
		stroke();
		return this;
	}

	public Path drawRadarData(float cx, float cy, float[] values, float maxR) {
		if (values == null || values.length < 3) {
			return this;
		}
		maxR = MathUtils.max(MIN_TOLERANCE, maxR);
		int n = values.length;
		float step = MathUtils.TWO_PI / n;
		for (int i = 0; i < n; i++) {
			float r = MathUtils.clamp(values[i], 0, maxR);
			float a = step * i;
			float x = cx + MathUtils.cos(a) * r;
			float y = cy + MathUtils.sin(a) * r;
			if (i == 0) {
				moveTo(x, y);
			} else {
				lineTo(x, y);
			}
		}
		close();
		fill();
		return this;
	}

	public Path drawSanguoAbility(float cx, float cy, float maxRadius, int levels, float leader, float force,
			float intellect, float politics, float charm) {
		final int COUNT = 5;
		final float stepAng = MathUtils.TWO_PI / COUNT;
		final float offsetAng = -MathUtils.HALF_PI;
		float[] angles = new float[COUNT];
		for (int i = 0; i < COUNT; i++) {
			angles[i] = offsetAng + i * stepAng;
		}
		float[] values = { leader, force, intellect, politics, charm };
		float[] radii = new float[COUNT];
		for (int i = 0; i < COUNT; i++) {
			float v = MathUtils.clamp(values[i], 0, 100);
			radii[i] = maxRadius * (v / 100f); // 纯线性等距
		}
		for (int l = 1; l <= levels; l++) {
			float r = maxRadius * l / levels;
			float x0 = cx + MathUtils.cos(angles[0]) * r;
			float y0 = cy + MathUtils.sin(angles[0]) * r;
			moveTo(x0, y0);
			for (int i = 1; i < COUNT; i++) {
				float x = cx + MathUtils.cos(angles[i]) * r;
				float y = cy + MathUtils.sin(angles[i]) * r;
				lineTo(x, y);
			}
			close();
		}
		for (int i = 0; i < COUNT; i++) {
			moveTo(cx, cy);
			float x = cx + MathUtils.cos(angles[i]) * maxRadius;
			float y = cy + MathUtils.sin(angles[i]) * maxRadius;
			lineTo(x, y);
		}
		stroke();
		moveTo(cx + MathUtils.cos(angles[0]) * radii[0], cy + MathUtils.sin(angles[0]) * radii[0]);
		for (int i = 1; i < COUNT; i++) {
			lineTo(cx + MathUtils.cos(angles[i]) * radii[i], cy + MathUtils.sin(angles[i]) * radii[i]);
		}
		close();
		fill();
		moveTo(cx + MathUtils.cos(angles[0]) * radii[0], cy + MathUtils.sin(angles[0]) * radii[0]);
		for (int i = 1; i < COUNT; i++) {
			lineTo(cx + MathUtils.cos(angles[i]) * radii[i], cy + MathUtils.sin(angles[i]) * radii[i]);
		}
		close();
		stroke();
		return this;
	}

	public Path drawSanguoAbilityRound(float cx, float cy, float maxRadius, int levels, float leader, float force,
			float intellect, float politics, float charm) {
		final int COUNT = 5;
		final float stepAng = MathUtils.TWO_PI / COUNT;
		final float offsetAng = -MathUtils.HALF_PI;
		float[] angles = new float[COUNT];
		for (int i = 0; i < COUNT; i++) {
			angles[i] = offsetAng + i * stepAng;
		}
		float[] values = { leader, force, intellect, politics, charm };
		float[] radii = new float[COUNT];
		for (int i = 0; i < COUNT; i++) {
			float v = MathUtils.clamp(values[i], 0, 100);
			radii[i] = maxRadius * (v / 100f);
		}
		final int circleSegments = 180;
		for (int l = 1; l <= levels; l++) {
			float r = maxRadius * l / levels;
			float firstX = cx + r * MathUtils.cos(offsetAng);
			float firstY = cy + r * MathUtils.sin(offsetAng);
			moveTo(firstX, firstY);
			for (int j = 1; j <= circleSegments; j++) {
				float ang = offsetAng + MathUtils.TWO_PI * j / circleSegments;
				float x = cx + r * MathUtils.cos(ang);
				float y = cy + r * MathUtils.sin(ang);
				lineTo(x, y);
			}
			close();
		}
		for (int i = 0; i < COUNT; i++) {
			moveTo(cx, cy);
			float x = cx + maxRadius * MathUtils.cos(angles[i]);
			float y = cy + maxRadius * MathUtils.sin(angles[i]);
			lineTo(x, y);
		}
		stroke();
		moveTo(cx + radii[0] * MathUtils.cos(angles[0]), cy + radii[0] * MathUtils.sin(angles[0]));
		for (int i = 1; i < COUNT; i++) {
			lineTo(cx + radii[i] * MathUtils.cos(angles[i]), cy + radii[i] * MathUtils.sin(angles[i]));
		}
		close();
		fill();
		moveTo(cx + radii[0] * MathUtils.cos(angles[0]), cy + radii[0] * MathUtils.sin(angles[0]));
		for (int i = 1; i < COUNT; i++) {
			lineTo(cx + radii[i] * MathUtils.cos(angles[i]), cy + radii[i] * MathUtils.sin(angles[i]));
		}
		close();
		stroke();
		return this;
	}

	public Path drawBar(float x, float by, float w, float h, float r) {
		if (w < MIN_TOLERANCE || h < MIN_TOLERANCE) {
			return this;
		}
		r = MathUtils.clamp(r, 0, MathUtils.min(w, h) * 0.5f);
		drawRoundRect(x, by - h, w, h, r * 2, r * 2);
		fill();
		return this;
	}

	public Path drawDonut(float cx, float cy, float ir, float or, float s, float e) {
		ir = MathUtils.max(MIN_TOLERANCE, ir);
		or = MathUtils.max(ir + MIN_TOLERANCE, or);
		arcToBezier(cx - or, cy - or, or, or, s, e, false, _segments);
		arcToBezier(cx - ir, cy - ir, ir, ir, e, s, true, _segments);
		close();
		fill();
		return this;
	}

	public Path2D translate(Vector2f v) {
		if (v == null) {
			return this;
		}
		for (int i = 0; i < _data.size(); i += 2) {
			_data.set(i, _data.get(i) + v.x);
			_data.set(i + 1, _data.get(i + 1) + v.y);
		}
		_dirty = true;
		return this;
	}

	public float getArea() {
		_tempData.clear();
		float area = 0f;
		int cmdSize = _commands.size();
		if (cmdSize == 0 || _data.isEmpty()) {
			return 0f;
		}
		float lx = 0, ly = 0, lsx = 0, lsy = 0;
		int seg = 0;
		for (int i = 0; i < cmdSize; i++) {
			PathCommand c = _commands.get(i);
			if (c == PathCommand.MoveTo) {
				if (seg > 0) {
					_tempData.add(lsx, lsy);
					area += MathUtils.abs(getSegmentArea());
					_tempData.clear();
				}
				if (i * 2 + 1 >= _data.size()) {
					break;
				}
				lsx = _data.get(i * 2);
				lsy = _data.get(i * 2 + 1);
				lx = lsx;
				ly = lsy;
				seg = 0;
			} else if (c == PathCommand.LineTo) {
				if (i * 2 + 1 >= _data.size()) {
					break;
				}
				lx = _data.get(i * 2);
				ly = _data.get(i * 2 + 1);
				_tempData.add(lx, ly);
				seg++;
			} else if (c == PathCommand.CubicCurveTo) {
				if (i * 2 + 5 >= _data.size()) {
					break;
				}
				float x1 = lx, y1 = ly;
				float x2 = _data.get(i * 2);
				float y2 = _data.get(i * 2 + 1);
				float x3 = _data.get(i * 2 + 2);
				float y3 = _data.get(i * 2 + 3);
				float x4 = _data.get(i * 2 + 4);
				float y4 = _data.get(i * 2 + 5);
				tesselateBezier(x1, y1, x2, y2, x3, y3, x4, y4, 0);
				lx = x4;
				ly = y4;
				seg++;
			}
		}
		if (seg > 0) {
			_tempData.add(lsx, lsy);
			area += MathUtils.abs(getSegmentArea());
		}
		return area;
	}

	private boolean checkTesselationTolerance(float x1, float y1, float x2, float y2, float x3, float y3, float x4,
			float y4) {
		float dx = x4 - x1;
		float dy = y4 - y1;
		float d2 = MathUtils.abs((x2 - x4) * dy - (y2 - y4) * dx);
		float d3 = MathUtils.abs((x3 - x4) * dy - (y3 - y4) * dx);
		return (d2 + d3) * (d2 + d3) < 0.25f * (dx * dx + dy * dy);
	}

	private void tesselateBezier(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4,
			int level) {
		if (level >= MAX_TESSELATION_LEVEL || checkTesselationTolerance(x1, y1, x2, y2, x3, y3, x4, y4)) {
			_tempData.add(x4, y4);
			return;
		}
		float x12 = (x1 + x2) * 0.5f;
		float y12 = (y1 + y2) * 0.5f;
		float x23 = (x2 + x3) * 0.5f;
		float y23 = (y2 + y3) * 0.5f;
		float x34 = (x3 + x4) * 0.5f;
		float y34 = (y3 + y4) * 0.5f;
		float x123 = (x12 + x23) * 0.5f;
		float y123 = (y12 + y23) * 0.5f;
		float x234 = (x23 + x34) * 0.5f;
		float y234 = (y23 + y34) * 0.5f;
		float x1234 = (x123 + x234) * 0.5f;
		float y1234 = (y123 + y234) * 0.5f;
		tesselateBezier(x1, y1, x12, y12, x123, y123, x1234, y1234, level + 1);
		tesselateBezier(x1234, y1234, x234, y234, x34, y34, x4, y4, level + 1);
	}

	private float getSegmentArea() {
		float a = 0f;
		int n = _tempData.size();
		if (n < 4) {
			return 0f;
		}
		for (int i = 0; i < n; i += 2) {
			int j = (i + 2) % n;
			a += _tempData.get(i) * _tempData.get(j + 1);
			a -= _tempData.get(j) * _tempData.get(i + 1);
		}
		return a * 0.5f;
	}

	public float[] getPointfX() {
		int n = length();
		float[] r = new float[n];
		for (int i = 0; i < n; i++) {
			r[i] = getVector(i).x;
		}
		return r;
	}

	public float[] getPointfY() {
		int n = length();
		float[] r = new float[n];
		for (int i = 0; i < n; i++) {
			r[i] = getVector(i).y;
		}
		return r;
	}

	public int[] getPointiX() {
		int n = length();
		int[] r = new int[n];
		for (int i = 0; i < n; i++) {
			r[i] = (int) getVector(i).x;
		}
		return r;
	}

	public int[] getPointiY() {
		int n = length();
		int[] r = new int[n];
		for (int i = 0; i < n; i++) {
			r[i] = (int) getVector(i).y;
		}
		return r;
	}

	public boolean isEmpty() {
		return _data.isEmpty();
	}

	public float getLastX() {
		return _lastX;
	}

	public float getLastY() {
		return _lastY;
	}

	public float getLastStartX() {
		return _lastStartX;
	}

	public float getLastStartY() {
		return _lastStartY;
	}

	public int size() {
		return _data.size();
	}

	public int length() {
		return _data.size() / 2;
	}

	public boolean isClosed() {
		return !_commands.isEmpty() && _commands.last() == PathCommand.Closed;
	}

	public boolean isStroke() {
		return !_commands.isEmpty() && _commands.last() == PathCommand.Stroke;
	}

	public boolean isFill() {
		return !_commands.isEmpty() && _commands.last() == PathCommand.Fill;
	}

	public Path2D update(FloatArray arr) {
		if (arr == null) {
			return this;
		}
		_data.clear();
		addAll(arr.toArray());
		return this;
	}

	public Polygon getShape() {
		return getShape(isFill());
	}

	/**
	 * 此函数形状只适合单独连续图形使用(例如矩形，圆形之类，可以直接使用这个)，复杂不连贯图应使用getShapes，否则全部图形的连接线会混合在一起(例如雷达图，例如网状图，应使用getShapes返回)
	 * 
	 * @param fill
	 * @return
	 */
	public Polygon getShape(boolean fill) {
		if (!_dirty) {
			return _currentPolys;
		}
		_currentPolys.clear();
		TArray<Vector2f> current = new TArray<Vector2f>();
		int dataIdx = 0;
		for (PathCommand cmd : _commands) {
			if (cmd == PathCommand.MoveTo) {
				if (current.size() >= 3) {
					addShape(current, fill);
					current.clear();
				}
				if (dataIdx + 1 < _data.size()) {
					float x = _data.get(dataIdx++);
					float y = _data.get(dataIdx++);
					current.add(new Vector2f(x, y));
				}
			} else if (cmd == PathCommand.LineTo) {
				if (current.isEmpty()) {
					continue;
				}
				if (dataIdx + 1 < _data.size()) {
					float x = _data.get(dataIdx++);
					float y = _data.get(dataIdx++);
					current.add(new Vector2f(x, y));
				}
			} else if (cmd == PathCommand.CurveTo || cmd == PathCommand.CubicCurveTo) {
				int count = _curve.getPoints().length / 2;
				for (int i = 0; i < count && dataIdx + 1 < _data.size(); i++) {
					current.add(new Vector2f(_data.get(dataIdx++), _data.get(dataIdx++)));
				}
			} else if (cmd == PathCommand.Closed) {
				if (current.size() >= 3) {
					current.add(current.get(0));
					addShape(current, fill);
					current.clear();
				}
			} else if (cmd == PathCommand.ClearSubPath) {
				if (current.size() >= 3) {
					addShape(current, fill);
					current.clear();
				}
			}
		}
		if (current.size() >= 3) {
			addShape(current, fill);
		}
		_dirty = false;
		return _currentPolys;
	}

	private void addShape(TArray<Vector2f> shape, boolean fill) {
		TArray<Vector2f> out = new TArray<Vector2f>();
		Vector2f last = null;
		for (Vector2f v : shape) {
			if (v == null) {
				continue;
			}
			if (last != null) {
				float dx = MathUtils.abs(v.x - last.x);
				float dy = MathUtils.abs(v.y - last.y);
				if (dx < DUPLICATE_EPSILON && dy < DUPLICATE_EPSILON) {
					continue;
				}
			}
			out.add(v);
			last = v;
		}
		if (out.size() < 3) {
			return;
		}
		for (Vector2f v : out) {
			_currentPolys.addPoint(v.x, v.y);
		}
		if (fill) {
			_currentPolys.addPoint(out.first().x, out.first().y);
		}
	}

	private void flushToPolygon(TArray<Vector2f> points, boolean fill) {
		if (points.size() < 3) {
			return;
		}
		Polygon p = new Polygon();
		Vector2f last = null;
		for (Vector2f v : points) {
			if (last == null) {
				p.addPoint(v.x, v.y);
				last = v;
				continue;
			}
			float dx = MathUtils.abs(v.x - last.x);
			float dy = MathUtils.abs(v.y - last.y);
			if (dx < DUPLICATE_EPSILON && dy < DUPLICATE_EPSILON) {
				continue;
			}
			p.addPoint(v.x, v.y);
			last = v;
		}
		if (p.getPointCount() >= 3) {
			_polygons.add(p);
		}
	}

	/**
	 * 由于Polygon本身的数据结构是连续线,所以不连贯的复杂图形会返回多个polygon，复杂图形应使用此集合数据渲染
	 * 
	 * @param fill
	 * @return
	 */

	public TArray<Polygon> getShapes() {
		return getShapes(isFill());
	}

	public TArray<Polygon> getShapes(boolean fill) {
		if (!_dirty) {
			return _polygons;
		}

		// 清空缓存
		_polygons.clear();
		_linePaths.clear();

		TArray<Vector2f> current = new TArray<Vector2f>();
		int dataIdx = 0;

		for (PathCommand cmd : _commands) {
			switch (cmd) {
			case MoveTo:
				if (current.size() >= 2) {
					if (fill) {
						flushToPolygon(current, true);
					} else {
						addLinePath(current);
					}
					current.clear();
				}
				if (dataIdx + 1 < _data.size()) {
					current.add(new Vector2f(_data.get(dataIdx++), _data.get(dataIdx++)));
				}
				break;
			case LineTo:
				if (!current.isEmpty() && dataIdx + 1 < _data.size()) {
					current.add(new Vector2f(_data.get(dataIdx++), _data.get(dataIdx++)));
				}
				break;
			case CurveTo:
			case CubicCurveTo:
				int count = _curve.getPoints().length / 2;
				for (int i = 0; i < count && dataIdx + 1 < _data.size(); i++) {
					current.add(new Vector2f(_data.get(dataIdx++), _data.get(dataIdx++)));
				}
				break;
			case Closed:
				if (current.size() >= 2) {
					current.add(current.get(0));
					flushToPolygon(current, true);
					current.clear();
				}
				break;
			case ClearSubPath:
				if (current.size() >= 2) {
					if (fill) {
						flushToPolygon(current, true);
					} else {
						addLinePath(current);
					}
					current.clear();
				}
				break;
			default:
				break;
			}
		}

		if (current.size() >= 2) {
			if (fill) {
				flushToPolygon(current, true);
			} else {
				addLinePath(current);
			}
		}

		_dirty = false;
		return _polygons;
	}

	private void addLinePath(TArray<Vector2f> points) {
		if (points.size() < 2) {
			return;
		}
		int n = points.size();
		float[] arr = new float[n * 2];
		for (int i = 0; i < n; i++) {
			Vector2f v = points.get(i);
			arr[i * 2] = v.x;
			arr[i * 2 + 1] = v.y;
		}
		_linePaths.add(arr);
	}

	public TArray<float[]> getLinePaths() {
		getShapes(false);
		return _linePaths;
	}

	public TArray<Vector2f> getVecs() {
		return getVertices(_segments);
	}

	public Path2D addPath(Path2D p, float px, float py) {
		if (p == null) {
			return this;
		}
		_commands.addAll(p._commands);
		for (int i = 0; i < p._data.size(); i += 2) {
			add(p._data.get(i) + px, p._data.get(i + 1) + py);
		}
		return this;
	}

	public Path2D fills(GLEx g, float px, float py) {
		return fills(g, px, py, LColor.white);
	}

	public Path2D fills(GLEx g, float px, float py, LColor color) {
		TArray<Polygon> result = getShapes(true);
		for (Polygon p : result) {
			g.fill(p, px, py, color);
		}
		if (result.size <= 0) {
			strokeLines(g, px, py, color);
		}
		return this;
	}

	public Path2D fill(GLEx g, float px, float py) {
		return fill(g, px, py, LColor.white);
	}

	public Path2D fill(GLEx g, float px, float py, LColor color) {
		if (g == null) {
			return this;
		}
		g.fill(getShape(true), px, py, color);
		return this;
	}

	public Path2D fill(Canvas c, float px, float py) {
		if (c == null) {
			return this;
		}
		Path path = c.createPath();
		boolean first = true;
		int dataIdx = 0;
		for (PathCommand cmd : _commands) {
			if (cmd == PathCommand.MoveTo) {
				if (!first) {
					path.close();
				}
				float x = _data.get(dataIdx++);
				float y = _data.get(dataIdx++);
				path.moveTo(x + px, y + py);
				first = false;
			} else if (cmd == PathCommand.LineTo) {
				float x = _data.get(dataIdx++);
				float y = _data.get(dataIdx++);
				path.lineTo(x + px, y + py);
			} else if (cmd == PathCommand.Closed) {
				path.close();
				first = true;
			}
		}
		path.close();
		c.fillPath(path);
		return this;
	}

	public Path2D stroke(GLEx g, float px, float py) {
		return stroke(g, px, py, LColor.white);
	}

	public Path2D stroke(GLEx g, float px, float py, LColor color) {
		if (g == null) {
			return this;
		}
		g.draw(getShape(false), px, py, color);
		return this;
	}

	public Path2D strokes(GLEx g, float px, float py) {
		return strokes(g, px, py, LColor.white);
	}

	/**
	 * 复杂图形用这个，polygon必须拆分，否则来回往返的坐标会连线
	 * 
	 * @param g
	 * @param px
	 * @param py
	 * @return
	 */
	public Path2D strokes(GLEx g, float px, float py, LColor color) {
		TArray<Polygon> result = getShapes(false);
		for (Polygon p : result) {
			g.draw(p, px, py, color);
		}
		if (result.size <= 0) {
			strokeLines(g, px, py, color);
		}
		return this;
	}

	public Path2D stroke(Canvas c, float px, float py) {
		if (c == null) {
			return this;
		}
		Path path = c.createPath();
		int dataIdx = 0;
		for (PathCommand cmd : _commands) {
			if (cmd == PathCommand.MoveTo) {
				float x = _data.get(dataIdx++);
				float y = _data.get(dataIdx++);
				path.moveTo(x + px, y + py);
			} else if (cmd == PathCommand.LineTo) {
				float x = _data.get(dataIdx++);
				float y = _data.get(dataIdx++);
				path.lineTo(x + px, y + py);
			} else if (cmd == PathCommand.Closed) {
				path.close();
			}
		}
		c.strokePath(path);
		return this;
	}

	public Path2D strokeLines(GLEx g, float px, float py, LColor color) {
		for (float[] line : getLinePaths()) {
			int len = line.length;
			if (len < 4) {
				continue;
			}
			for (int i = 0; i < len - 2; i += 2) {
				g.drawLine(line[i] + px, line[i + 1] + py, line[i + 2] + px, line[i + 3] + py, color);
			}
		}
		return this;
	}

	public Path2D dashStrokeLines(GLEx g, float px, float py, int divisions, LColor color) {
		for (float[] line : getLinePaths()) {
			int len = line.length;
			if (len < 4) {
				continue;
			}
			for (int i = 0; i < len - 2; i += 2) {
				g.drawDashLine(line[i] + px, line[i + 1] + py, line[i + 2] + px, line[i + 3] + py, divisions, color);
			}
		}
		return this;
	}

	public Path2D dashStroke(GLEx g, float px, float py) {
		return dashStroke(g, px, py, LColor.white);
	}

	public Path2D dashStroke(GLEx g, float px, float py, LColor color) {
		return dashStroke(g, px, py, 16, color);
	}

	public Path2D dashStroke(GLEx g, float px, float py, int divisions, LColor color) {
		final Polygon linePath = getShape(false);
		float renderX = 0;
		float renderY = 0;
		final TArray<Vector2f> list = linePath.getVertices();
		for (int i = 0; i < list.size() - 1; i++) {
			Vector2f p1 = list.get(i);
			Vector2f p2 = list.get(i + 1);
			g.drawDashLine(p1.x + renderX, p1.y + renderY, p2.x + renderX, p2.y + renderY, divisions, color);
		}
		return this;
	}

	public Path2D dashStrokes(GLEx g, float px, float py, LColor color) {
		return dashStrokes(g, px, py, 16, color);
	}

	public Path2D dashStrokes(GLEx g, float px, float py, int divisions, LColor color) {
		final TArray<Polygon> result = getShapes(false);
		float renderX = 0;
		float renderY = 0;
		for (Polygon linePath : result) {
			if (linePath == null || linePath.size() < 2) {
				continue;
			}
			final TArray<Vector2f> list = linePath.getVertices();
			for (int i = 0; i < list.size() - 1; i++) {
				Vector2f p1 = list.get(i);
				Vector2f p2 = list.get(i + 1);
				g.drawDashLine(p1.x + renderX, p1.y + renderY, p2.x + renderX, p2.y + renderY, divisions, color);
			}
		}
		if (result.size <= 0) {
			dashStrokeLines(g, px, py, divisions, color);
		}
		return this;
	}

	public float[] getResult() {
		return _data.toArray();
	}

	public int getSegments() {
		return _segments;
	}

	public Path2D setSegments(int s) {
		_segments = MathUtils.clamp(s, MIN_SEGMENTS, MAX_SEGMENTS);
		_dirty = true;
		return this;
	}

	public boolean isDirty() {
		return _dirty;
	}

	@Override
	public int hashCode() {
		return _data.hashCode();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Path2D{\n");
		int dataIdx = 0;
		for (PathCommand cmd : _commands) {
			sb.append(cmd);
			if (cmd == PathCommand.MoveTo || cmd == PathCommand.LineTo) {
				if (dataIdx + 1 < _data.size()) {
					sb.append("(").append(_data.get(dataIdx)).append(",").append(_data.get(dataIdx + 1)).append(")");
					dataIdx += 2;
				}
			} else if (cmd == PathCommand.CubicCurveTo) {
				if (dataIdx + 5 < _data.size()) {
					sb.append("(").append(_data.get(dataIdx)).append(",").append(_data.get(dataIdx + 1)).append(",")
							.append(_data.get(dataIdx + 2)).append(",").append(_data.get(dataIdx + 3)).append(",")
							.append(_data.get(dataIdx + 4)).append(",").append(_data.get(dataIdx + 5)).append(")");
					dataIdx += 6;
				}
			}
			sb.append("\n");
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public Path reset() {
		_commands.clear();
		_data.clear();
		_tempData.clear();
		_curve.clear();
		_currentPolys.clear();
		_polygons.clear();
		_linePaths.clear();
		_lastX = _lastY = _lastStartX = _lastStartY = 0f;
		_deltaT = 0f;
		_dirty = true;
		return this;
	}

	@Override
	public Path close() {
		_commands.add(PathCommand.Closed);
		_lastX = _lastStartX;
		_lastY = _lastStartY;
		_dirty = true;
		return this;
	}
}
