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
package loon.component;

import loon.LSystem;
import loon.action.map.Direction;
import loon.canvas.LColor;
import loon.events.SysTouch;
import loon.geom.Path;
import loon.geom.PointF;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.GestureData;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;
import loon.utils.TimeUtils;
import loon.utils.URecognizer;
import loon.utils.URecognizerResult;

/**
 * 0.3.3版新增类,用以进行跨平台手势操作(触屏经过的路径,默认会以指定颜色显示出来轨迹,当然也可以隐藏轨迹,仅仅获得经过的路径)
 */
public class LGesture extends LComponent {

	private final ObjectMap<String, TArray<PointF>> _gestureTemplates = new ObjectMap<String, TArray<PointF>>();

	private float _startX, _startY;
	private float _moveX;
	private float _moveY;
	private float _curveEndX;
	private float _curveEndY;

	private boolean _resetGesture;
	private boolean _autoClear;

	private Path _goalPath;
	private int _lineWidth;

	private long _lastSampleTime;

	public LGesture(int x, int y, int w, int h, boolean c) {
		this(x, y, w, h, c, LColor.orange);
	}

	public LGesture(int x, int y, int w, int h, boolean c, LColor col) {
		super(x, y, w, h);
		this._drawBackground = false;
		this._component_baseColor = col;
		this._autoClear = c;
		this._lineWidth = 5;
		this._lastSampleTime = TimeUtils.millis();
	}

	public LGesture(int x, int y, int w, int h) {
		this(x, y, w, h, true);
	}

	public LGesture() {
		this(0, 0, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), true);
	}

	public LGesture(boolean flag) {
		this(0, 0, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), flag);
	}

	@Override
	public void update(long elapsedTime) {
		if (SysTouch.isUp()) {
			if (_autoClear) {
				clear();
			}
		}
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (isVisible() && _goalPath != null && !_goalPath.isEmpty()) {
			g.saveBrush();
			int tint = g.color();
			g.setLineWidth(_lineWidth);
			g.setColor(_component_baseColor);
			g.drawPolyline(_goalPath);
			g.resetLineWidth();
			g.setTint(tint);
			g.restoreBrush();
		}
	}

	@Override
	protected void processTouchPressed() {
		final float x = getUITouchX();
		final float y = getUITouchY();
		if (isPointInUI(x, y)) {
			if (_startX == 0 && _startY == 0) {
				_startX = x;
				_startY = y;
			}
			if (!MathUtils.equal(x, _moveX) || !MathUtils.equal(y, _moveY)) {
				_moveX = x;
				_moveY = y;
				if (_resetGesture) {
					_resetGesture = false;
					if (_goalPath != null) {
						_goalPath.clear();
					}
				}
				if (_goalPath == null) {
					_goalPath = new Path(x, y);
				} else {
					_goalPath.set(x, y);
				}
				_curveEndX = x;
				_curveEndY = y;
			}
		}
		super.processTouchPressed();
	}

	@Override
	protected void processTouchReleased() {
		super.processTouchReleased();
		if (_autoClear) {
			clear();
		}
	}

	private boolean needSample(float x, float y, long elapsedTime) {
		float dx = MathUtils.abs(x - _moveX);
		float dy = MathUtils.abs(y - _moveY);
		float dist = MathUtils.sqrt(dx * dx + dy * dy);
		if (dist > 2 || elapsedTime - _lastSampleTime > 50) {
			_lastSampleTime = elapsedTime;
			return true;
		}
		return false;
	}

	@Override
	protected void processTouchDragged() {
		if (SysTouch.isDrag() && _input.isMoving()) {
			final float x = getUITouchX();
			final float y = getUITouchY();
			if (isPointInUI(x, y)) {
				if (!MathUtils.equal(x, _moveX) || !MathUtils.equal(y, _moveY)) {
					final float previousX = _moveX;
					final float previousY = _moveY;
					if (needSample(x, y, TimeUtils.millis())) {
						float cX = _curveEndX = (x + previousX) / 2;
						float cY = _curveEndY = (y + previousY) / 2;
						if (_goalPath != null) {
							_goalPath.lineTo(previousX, previousY, cX, cY);
						}
						_moveX = x;
						_moveY = y;
					}
				}
			}
		}
		super.processTouchDragged();
	}

	public TArray<PointF> getSmoothedPoints(int windowSize) {
		final TArray<PointF> raw = getListPoint();
		if (raw == null || raw.size <= windowSize) {
			return raw;
		}
		final TArray<PointF> smooth = new TArray<PointF>();
		for (int i = 0; i < raw.size; i++) {
			float sumX = 0, sumY = 0;
			int count = 0;
			for (int j = MathUtils.max(0, i - windowSize); j <= MathUtils.min(raw.size - 1, i + windowSize); j++) {
				sumX += raw.get(j).x;
				sumY += raw.get(j).y;
				count++;
			}
			smooth.add(new PointF(sumX / count, sumY / count));
		}
		return smooth;
	}

	public TArray<PointF> getNormalizedPoints(int targetSize) {
		final TArray<PointF> points = getListPoint();
		if (points == null || points.size == 0) {
			return points;
		}
		float[] center = getCenter();
		for (PointF p : points) {
			p.x -= center[0];
			p.y -= center[1];
		}
		float maxDist = 0;
		for (PointF p : points) {
			maxDist = MathUtils.max(maxDist, MathUtils.sqrt(p.x * p.x + p.y * p.y));
		}
		if (maxDist > 0) {
			for (PointF p : points) {
				p.x = (p.x / maxDist) * targetSize;
				p.y = (p.y / maxDist) * targetSize;
			}
		}
		return points;
	}

	public float[] getPoints() {
		if (_goalPath != null) {
			return _goalPath.getPoints();
		}
		return null;
	}

	public TArray<PointF> getListPoint() {
		if (_goalPath != null) {
			final float[] points = _goalPath.getPoints();
			final int size = points.length;
			final TArray<PointF> result = new TArray<PointF>(size);
			for (int i = 0; i < size; i += 2) {
				result.add(new PointF(points[i], points[i + 1]));
			}
			return result;
		}
		return null;
	}

	public TArray<Vector2f> getList() {
		if (_goalPath != null) {
			final float[] points = _goalPath.getPoints();
			final int size = points.length;
			final TArray<Vector2f> result = new TArray<Vector2f>(size);
			for (int i = 0; i < size; i += 2) {
				result.add(new Vector2f(points[i], points[i + 1]));
			}
			return result;
		}
		return null;
	}

	private final static float distance(float x1, float y1, float x2, float y2) {
		float deltaX = x1 - x2;
		float deltaY = y1 - y2;
		return MathUtils.sqrt(deltaX * deltaX + deltaY * deltaY);
	}

	public float getLength() {
		if (_goalPath != null) {
			float length = 0;
			final float[] points = _goalPath.getPoints();
			final int size = points.length;
			for (int i = 0; i < size;) {
				if (i < size - 3) {
					length += distance(points[0 + i], points[1 + i], points[2 + i], points[3 + i]);
				}
				i += 4;
			}
			return length;
		}
		return 0;
	}

	public float[] getCenter() {
		if (_goalPath != null) {
			return _goalPath.getCenter();
		}
		return new float[] { 0, 0 };
	}

	public void clear() {
		if (_goalPath != null) {
			_goalPath.clear();
		}
	}

	public float getCurveEndX() {
		return _curveEndX;
	}

	public void setCurveEndX(float curveEndX) {
		this._curveEndX = curveEndX;
	}

	public float getCurveEndY() {
		return _curveEndY;
	}

	public void setCurveEndY(float curveEndY) {
		this._curveEndY = curveEndY;
	}

	public Path getPath() {
		return _goalPath;
	}

	public int getLineWidth() {
		return _lineWidth;
	}

	public void setLineWidth(int lineWidth) {
		this._lineWidth = lineWidth;
	}

	public boolean isAutoClear() {
		return _autoClear;
	}

	/**
	 * 不愿意清除绘制的内容时，可以设置此项为false
	 * 
	 * @param autoClear
	 */
	public void setAutoClear(boolean autoClear) {
		this._autoClear = autoClear;
	}

	/**
	 * 获得一个指定数据的手势分析器
	 * 
	 * @param type
	 * @return
	 */
	public URecognizerResult getRecognizer(GestureData data, int type) {
		final URecognizer analyze = new URecognizer(data, type);
		if (_goalPath != null) {
			final float[] points = _goalPath.getPoints();
			final int size = points.length;
			final TArray<PointF> v = new TArray<PointF>();
			for (int i = 0; i < size; i += 2) {
				v.add(new PointF(points[i], points[i + 1]));
			}
			return analyze.getRecognize(v);
		}
		return new URecognizerResult();
	}

	/**
	 * 获得一个指定数据的手势分析器
	 * 
	 * @param data
	 * @return
	 */
	public URecognizerResult getRecognizer(GestureData data) {
		return getRecognizer(data, URecognizer.GESTURES_DEFAULT);
	}

	/**
	 * 使用指定文件中的采样数据分析手势
	 * 
	 * @param path
	 * @param resampledFirst
	 * @return
	 */
	public URecognizerResult getRecognizer(String path, boolean resampledFirst) {
		return getRecognizer(GestureData.loadUserPoints(path, resampledFirst), URecognizer.GESTURES_NONE);
	}

	/**
	 * 获得一个手势分析器
	 * 
	 * @return
	 */
	public URecognizerResult getRecognizer() {
		return getRecognizer(new GestureData(), URecognizer.GESTURES_DEFAULT);
	}

	/**
	 * 提取手势方向序列（上下左右及斜线）
	 * 
	 * @param threshold
	 * @return
	 */
	public TArray<Direction> getDirectionSequence(float threshold) {
		TArray<PointF> points = getListPoint();
		TArray<Direction> directions = new TArray<Direction>();
		if (points == null || points.size < 2) {
			return directions;
		}
		for (int i = 1; i < points.size; i++) {
			float dx = points.get(i).x - points.get(i - 1).x;
			float dy = points.get(i).y - points.get(i - 1).y;
			if (MathUtils.abs(dx) < threshold && MathUtils.abs(dy) < threshold) {
				continue;
			}
			Direction dir;
			if (MathUtils.abs(dx) > MathUtils.abs(dy)) {
				dir = dx > 0 ? Direction.RIGHT : Direction.LEFT;
			} else if (MathUtils.abs(dy) > MathUtils.abs(dx)) {
				dir = dy > 0 ? Direction.DOWN : Direction.UP;
			} else {
				if (dx > 0 && dy > 0)
					dir = Direction.DOWN_RIGHT;
				else if (dx > 0 && dy < 0)
					dir = Direction.UP_RIGHT;
				else if (dx < 0 && dy > 0)
					dir = Direction.DOWN_LEFT;
				else
					dir = Direction.UP_LEFT;
			}
			if (directions.isEmpty() || !directions.get(directions.size - 1).equals(dir)) {
				directions.add(dir);
			}
		}
		return directions;
	}

	/**
	 * 计算手势的速度序列（像素/毫秒）
	 */
	public TArray<Float> getSpeedSequence() {
		TArray<PointF> points = getListPoint();
		TArray<Float> speeds = new TArray<Float>();
		if (points == null || points.size < 2) {
			return speeds;
		}

		long prevTime = _lastSampleTime;
		for (int i = 1; i < points.size; i++) {
			float dx = points.get(i).x - points.get(i - 1).x;
			float dy = points.get(i).y - points.get(i - 1).y;
			float dist = MathUtils.sqrt(dx * dx + dy * dy);
			long now = TimeUtils.millis();
			long dt = MathUtils.max(1, now - prevTime);
			float speed = dist / dt;
			speeds.add(speed);
			prevTime = now;
		}
		return speeds;
	}

	/**
	 * 计算手势的平均速度
	 */
	public float getAverageSpeed() {
		TArray<Float> speeds = getSpeedSequence();
		if (speeds == null || speeds.isEmpty()) {
			return 0f;
		}
		float sum = 0f;
		for (Float s : speeds) {
			sum += s;
		}
		return sum / speeds.size;
	}

	/**
	 * 计算手势的加速度序列
	 */
	public TArray<Float> getAccelerationSequence() {
		TArray<Float> speeds = getSpeedSequence();
		TArray<Float> accels = new TArray<Float>();
		if (speeds == null || speeds.size < 2) {
			return accels;
		}
		for (int i = 1; i < speeds.size; i++) {
			float accel = speeds.get(i) - speeds.get(i - 1);
			accels.add(accel);
		}
		return accels;
	}

	/**
	 * 判断手势是否为快速滑动
	 * 
	 * @param threshold
	 */
	public boolean isFastGesture(float threshold) {
		return getAverageSpeed() > threshold;
	}

	/**
	 * 判断手势是否接近直线
	 * 
	 * @param tolerance
	 */
	public boolean isLine(float tolerance) {
		TArray<PointF> points = getListPoint();
		if (points == null || points.size < 2) {
			return false;
		}
		PointF start = points.first();
		PointF end = points.last();
		float totalDist = getLength();
		float directDist = distance(start.x, start.y, end.x, end.y);
		return directDist / totalDist > (1 - tolerance);
	}

	/**
	 * 判断手势是否接近圆形
	 * 
	 * @param tolerance
	 */
	public boolean isCircle(float tolerance) {
		TArray<PointF> points = getListPoint();
		if (points == null || points.size < 5) {
			return false;
		}
		float[] center = getCenter();
		float avgRadius = 0;
		for (PointF p : points) {
			avgRadius += distance(p.x, p.y, center[0], center[1]);
		}
		avgRadius /= points.size;
		int count = 0;
		for (PointF p : points) {
			float r = distance(p.x, p.y, center[0], center[1]);
			if (MathUtils.abs(r - avgRadius) < avgRadius * tolerance) {
				count++;
			}
		}
		return (float) count / points.size > 0.8f;
	}

	/**
	 * 判断手势是否接近矩形
	 * 
	 * @param tolerance
	 */
	public boolean isRectangle(float tolerance) {
		TArray<Direction> dirs = getDirectionSequence(5f);
		if (dirs == null || dirs.size < 4) {
			return false;
		}
		boolean hasUp = dirs.contains(Direction.UP);
		boolean hasDown = dirs.contains(Direction.DOWN);
		boolean hasLeft = dirs.contains(Direction.LEFT);
		boolean hasRight = dirs.contains(Direction.RIGHT);
		return hasUp && hasDown && hasLeft && hasRight;
	}

	/**
	 * 计算手势的转折次数
	 * 
	 * @param threshold
	 * @return
	 */
	public int getTurnCount(float threshold) {
		TArray<Direction> dirs = getDirectionSequence(threshold);
		if (dirs == null || dirs.size < 2) {
			return 0;
		}
		int turns = 0;
		for (int i = 1; i < dirs.size; i++) {
			if (!dirs.get(i).equals(dirs.get(i - 1))) {
				turns++;
			}
		}
		return turns;
	}

	/**
	 * 计算方向熵
	 * 
	 * @param threshold
	 * @return
	 */
	public float getDirectionEntropy(float threshold) {
		TArray<Direction> dirs = getDirectionSequence(threshold);
		if (dirs == null || dirs.isEmpty()) {
			return 0f;
		}
		ObjectMap<Direction, Integer> freq = new ObjectMap<Direction, Integer>();
		for (Direction d : dirs) {
			Integer dv = freq.get(d);
			freq.put(d, dv == null ? 1 : dv.intValue() + 1);
		}
		float entropy = 0f;
		int total = dirs.size;
		for (Integer count : freq.values()) {
			float p = (float) count / total;
			entropy -= p * (MathUtils.log(p) / MathUtils.log(2));
		}
		return entropy;
	}

	/**
	 * 判断手势是否复杂
	 * 
	 * @param turnThreshold
	 * @param entropyThreshold
	 */
	public boolean isComplexGesture(int turnThreshold, float entropyThreshold) {
		return getTurnCount(5f) > turnThreshold || getDirectionEntropy(5f) > entropyThreshold;
	}

	/**
	 * 计算两个手势点序列的相似度（0~1） 使用简单的欧氏距离归一化方法
	 */
	public float calculateSimilarity(TArray<PointF> template, int targetSize) {
		TArray<PointF> points = getNormalizedPoints(targetSize);
		if (points == null || template == null || points.isEmpty() || template.isEmpty()) {
			return 0f;
		}

		int minSize = MathUtils.min(points.size, template.size);
		float sumDist = 0f;
		for (int i = 0; i < minSize; i++) {
			float dx = points.get(i).x - template.get(i).x;
			float dy = points.get(i).y - template.get(i).y;
			sumDist += MathUtils.sqrt(dx * dx + dy * dy);
		}
		float avgDist = sumDist / minSize;
		float similarity = 1f / (1f + avgDist);
		return similarity;
	}

	/**
	 * 与指定模板手势文件进行匹配
	 * 
	 * @param path
	 * @param name
	 * @param targetSize
	 * @return
	 */
	public float matchTemplate(String path, String name, int targetSize) {
		GestureData templateData = GestureData.loadUserPoints(path, true);
		if (templateData == null) {
			return 0f;
		}
		TArray<PointF> templatePoints = templateData.getUserPoints(name);
		return calculateSimilarity(templatePoints, targetSize);
	}

	/**
	 * 注册一个模板手势
	 * 
	 * @param name   模板名称（类别）
	 * @param points 模板点序列
	 */
	public void registerTemplate(String name, TArray<PointF> points) {
		if (name != null && points != null && !points.isEmpty()) {
			_gestureTemplates.put(name, points);
		}
	}

	/**
	 * 从文件加载并注册模板
	 * 
	 * @param name       模板名称
	 * @param path       文件路径
	 * @param targetSize 归一化大小
	 */
	public void registerTemplateFromFile(String name, String path, int targetSize) {
		GestureData templateData = GestureData.loadUserPoints(path, true);
		if (templateData != null) {
			TArray<PointF> templatePoints = templateData.getUserPoints(name);
			TArray<PointF> normalized = normalizeTemplate(templatePoints, targetSize);
			registerTemplate(name, normalized);
		}
	}

	private TArray<PointF> normalizeTemplate(TArray<PointF> templatePoints, int targetSize) {
		if (templatePoints == null || templatePoints.isEmpty()) {
			return templatePoints;
		}
		float cx = 0, cy = 0;
		for (PointF p : templatePoints) {
			cx += p.x;
			cy += p.y;
		}
		cx /= templatePoints.size;
		cy /= templatePoints.size;
		float maxDist = 0;
		for (PointF p : templatePoints) {
			maxDist = MathUtils.max(maxDist, MathUtils.sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy)));
		}
		TArray<PointF> normalized = new TArray<PointF>();
		for (PointF p : templatePoints) {
			float nx = (p.x - cx) / maxDist * targetSize;
			float ny = (p.y - cy) / maxDist * targetSize;
			normalized.add(new PointF(nx, ny));
		}
		return normalized;
	}

	/**
	 * 返回最匹配的模板类别及相似度
	 * 
	 * @param targetSize
	 * @return
	 */
	public String classifyGesture(int targetSize) {
		if (_gestureTemplates.isEmpty()) {
			return LSystem.UNKNOWN;
		}
		String bestName = LSystem.UNKNOWN;
		float bestScore = 0f;
		ObjectMap.Entries<String, TArray<PointF>> ens = _gestureTemplates.entries();
		for (ObjectMap.Entries<String, TArray<PointF>> it = ens; it.hasNext();) {
			ObjectMap.Entry<String, TArray<PointF>> entry = it.next();
			float score = calculateSimilarity(entry.getValue(), targetSize);
			if (score > bestScore) {
				bestScore = score;
				bestName = entry.getKey();
			}
		}
		return bestName + " (: " + bestScore + ")";
	}

	public float getStartX() {
		return _startX;
	}

	public float getStartY() {
		return _startY;
	}

	@Override
	public String getUIName() {
		return "Gesture";
	}

	@Override
	public void destory() {

	}

}
