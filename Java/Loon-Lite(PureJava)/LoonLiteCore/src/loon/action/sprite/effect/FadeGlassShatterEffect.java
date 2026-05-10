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
package loon.action.sprite.effect;

import loon.LSystem;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.TArray;
import loon.utils.timer.Duration;

/**
 * 玻璃碎片形式的黑白渐变特效
 */
public class FadeGlassShatterEffect extends BaseAbstractEffect {

	private static class TileShard {
		final float[] verts;
		float centerX, centerY;
		final float jitter;
		final float rotDir;
		final float dispJitter;
		float baseDelay;

		TileShard(float[] verts) {
			this.verts = verts;
			this.jitter = MathUtils.random() * 0.5f - 0.25f;
			this.rotDir = MathUtils.random() > 0.5f ? 1f : -1f;
			this.dispJitter = MathUtils.random() * 0.6f;
			this.baseDelay = 0f;
		}

		void computeCentroid() {
			float cx = 0f, cy = 0f;
			int n = verts.length / 2;
			for (int i = 0; i < n; i++) {
				cx += verts[i * 2];
				cy += verts[i * 2 + 1];
			}
			this.centerX = cx / n;
			this.centerY = cy / n;
		}
	}

	private long _time;
	private float _currentFrame;
	private int _type;
	private int _step;
	private int _rows = 8;
	private int _cols = 8;

	private float _centerX, _centerY;
	private float _waveSpeed = 2f;
	private float _explosionStrength = 80f;
	private float _repairStrength = 10f;
	private float _vertexJitter = 6f;

	private float _minShardW = 32f;
	private float _minShardH = 32f;
	private float _maxShardW = 64f;
	private float _maxShardH = 64f;

	private LColor _tempColor = new LColor();

	private final TArray<TileShard[]> _tiles = new TArray<TileShard[]>();
	private final float[] _tileOffsets;

	private Vector2f[][] _gridPoints;

	public FadeGlassShatterEffect(int type, LColor c) {
		this(type, c, 2, 2, 1);
	}

	public FadeGlassShatterEffect(int type, LColor c, int rows, int cols, int step) {
		this(type, 120, c, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), rows, cols, step);
	}

	public FadeGlassShatterEffect(int type, long delay, LColor c, int w, int h, int rows, int cols, int step) {
		this._type = type;
		this._rows = MathUtils.max(1, rows);
		this._cols = MathUtils.max(1, cols);
		this.setDelay(delay);
		this.setColor(c);
		this.setSize(w, h);
		this.setRepaint(true);
		this.setStep(step);
		int total = _rows * _cols;
		this._tileOffsets = new float[total];
		this._centerX = w / 2f;
		this._centerY = h / 2f;
		initShards();
	}

	@Override
	public long getDelay() {
		return _time;
	}

	@Override
	public float getDelayS() {
		return Duration.ofS(_time);
	}

	@Override
	public FadeGlassShatterEffect setDelay(long delay) {
		this._time = delay;
		if (_type == TYPE_FADE_IN) {
			this._currentFrame = this._time;
		} else {
			this._currentFrame = 0;
		}
		return this;
	}

	@Override
	public FadeGlassShatterEffect setDelayS(float s) {
		return setDelay(Duration.ofS(s));
	}

	public FadeGlassShatterEffect setStep(int s) {
		_step = LSystem.toIScaleFPS(s, 1);
		return this;
	}

	public int getStep() {
		return _step;
	}

	public FadeGlassShatterEffect setCenter(float x, float y) {
		this._centerX = x;
		this._centerY = y;
		initShards();
		return this;
	}

	public FadeGlassShatterEffect setWaveSpeed(float s) {
		this._waveSpeed = MathUtils.max(0.01f, s);
		return this;
	}

	public FadeGlassShatterEffect setExplosionStrength(float s) {
		this._explosionStrength = s;
		return this;
	}

	public FadeGlassShatterEffect setRepairStrength(float s) {
		this._repairStrength = s;
		return this;
	}

	public FadeGlassShatterEffect setVertexJitter(float px) {
		this._vertexJitter = MathUtils.max(0f, px);
		initShards();
		return this;
	}

	/**
	 * 碎片尺寸设置
	 * 
	 * @param minW
	 * @param maxW
	 * @param minH
	 * @param maxH
	 * @return
	 */
	public FadeGlassShatterEffect setShardSizeRange(float minW, float maxW, float minH, float maxH) {
		this._minShardW = MathUtils.max(1f, MathUtils.min(minW, maxW));
		this._maxShardW = MathUtils.max(this._minShardW, maxW);
		this._minShardH = MathUtils.max(1f, MathUtils.min(minH, maxH));
		this._maxShardH = MathUtils.max(this._minShardH, maxH);
		initShards();
		return this;
	}

	/**
	 * 初始化碎片
	 */
	private void initShards() {
		_tiles.clear();
		_gridPoints = new Vector2f[_rows + 1][_cols + 1];
		float cellW = getWidth() / (float) _cols;
		float cellH = getHeight() / (float) _rows;
		for (int r = 0; r <= _rows; r++) {
			for (int c = 0; c <= _cols; c++) {
				float gx = c * cellW;
				float gy = r * cellH;
				float jitterX = MathUtils.random(-_vertexJitter, _vertexJitter);
				float jitterY = MathUtils.random(-_vertexJitter, _vertexJitter);
				float px = MathUtils.clamp(gx + jitterX, 0f, getWidth());
				float py = MathUtils.clamp(gy + jitterY, 0f, getHeight());
				_gridPoints[r][c] = new Vector2f(px, py);
			}
		}

		int idx = 0;
		for (int row = 0; row < _rows; row++) {
			for (int col = 0; col < _cols; col++) {
				float tx = col * cellW;
				float ty = row * cellH;
				Vector2f p00 = _gridPoints[row][col];
				Vector2f p10 = _gridPoints[row][col + 1];
				Vector2f p11 = _gridPoints[row + 1][col + 1];
				Vector2f p01 = _gridPoints[row + 1][col];

				float avgShardW = (_minShardW + _maxShardW) * 0.5f;
				float avgShardH = (_minShardH + _maxShardH) * 0.5f;
				int subCols = MathUtils.max(1, (int) MathUtils.round(cellW / avgShardW));
				int subRows = MathUtils.max(1, (int) MathUtils.round(cellH / avgShardH));
				subCols = MathUtils.clamp(subCols, 1, MathUtils.max(1, (int) (cellW / MathUtils.max(1f, _minShardW))));
				subRows = MathUtils.clamp(subRows, 1, MathUtils.max(1, (int) (cellH / MathUtils.max(1f, _minShardH))));
				float subW = cellW / (float) subCols;
				float subH = cellH / (float) subRows;

				TArray<float[]> polyList = new TArray<float[]>();

				for (int sr = 0; sr < subRows; sr++) {
					for (int sc = 0; sc < subCols; sc++) {
						float sx = tx + sc * subW;
						float sy = ty + sr * subH;
						float ax = sx;
						float ay = sy;
						float bx = sx + subW;
						float by = sy;
						float cx = sx + subW;
						float cy = sy + subH;
						float dx = sx;
						float dy = sy + subH;
						float centerX = sx + subW * 0.5f;
						float centerY = sy + subH * 0.5f;
						float biasX = centerX - _centerX;
						float biasY = centerY - _centerY;
						float biasLen = (float) MathUtils.hypot(biasX, biasY);
						float biasFactor = (biasLen < 1e-4f) ? 0f
								: MathUtils.clamp(1f - (biasLen / MathUtils.max(getWidth(), getHeight())), 0f, 1f);
						float cjx = MathUtils.random(-_vertexJitter, _vertexJitter) * (0.6f + biasFactor * 0.8f);
						float cjy = MathUtils.random(-_vertexJitter, _vertexJitter) * (0.6f + biasFactor * 0.8f);
						float centerJX = MathUtils.clamp(centerX + cjx, sx, sx + subW);
						float centerJY = MathUtils.clamp(centerY + cjy, sy, sy + subH);

						float[] p1 = new float[] { ax, ay, bx, by, centerJX, centerJY };
						float[] p2 = new float[] { bx, by, cx, cy, centerJX, centerJY };
						float[] p3 = new float[] { cx, cy, dx, dy, centerJX, centerJY };
						float[] p4 = new float[] { dx, dy, ax, ay, centerJX, centerJY };

						addIfWithinSize(polyList, p1, subW, subH);
						addIfWithinSize(polyList, p2, subW, subH);
						addIfWithinSize(polyList, p3, subW, subH);
						addIfWithinSize(polyList, p4, subW, subH);
					}
				}

				if (polyList.size == 0) {
					float[] fallback = new float[] { p00.x, p00.y, p10.x, p10.y, p11.x, p11.y, p01.x, p01.y };
					polyList.add(fallback);
				}

				TileShard[] shards = new TileShard[polyList.size];
				for (int s = 0; s < polyList.size; s++) {
					float[] verts = polyList.get(s);
					TileShard ts = new TileShard(verts);
					ts.computeCentroid();
					float d = (float) MathUtils.hypot(ts.centerX - _centerX, ts.centerY - _centerY);
					float maxDist = (float) MathUtils.hypot(getWidth(), getHeight());
					ts.baseDelay = MathUtils.clamp(d / maxDist, 0f, 1f) * 0.9f;
					shards[s] = ts;
				}
				_tiles.add(shards);
				_tileOffsets[idx] = MathUtils.random() * 0.12f;
				idx++;
			}
		}
	}

	private void addIfWithinSize(TArray<float[]> polyList, float[] poly, float approxW, float approxH) {
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		int n = poly.length / 2;
		for (int i = 0; i < n; i++) {
			float x = poly[i * 2];
			float y = poly[i * 2 + 1];
			if (x < minX) {
				minX = x;
			}
			if (y < minY) {
				minY = y;
			}
			if (x > maxX) {
				maxX = x;
			}
			if (y > maxY) {
				maxY = y;
			}
		}
		float w = maxX - minX;
		float h = maxY - minY;

		if (w >= _minShardW && w <= _maxShardW && h >= _minShardH && h <= _maxShardH) {
			if (!isDegenerate(poly)) {
				polyList.add(poly);
			}
			return;
		}

		if (w > _maxShardW || h > _maxShardH) {
			float cx = 0f, cy = 0f;
			for (int i = 0; i < n; i++) {
				cx += poly[i * 2];
				cy += poly[i * 2 + 1];
			}
			cx /= n;
			cy /= n;
			int farIdx = 0;
			float farDist = -1f;
			for (int i = 0; i < n; i++) {
				float dx = poly[i * 2] - cx;
				float dy = poly[i * 2 + 1] - cy;
				float d = dx * dx + dy * dy;
				if (d > farDist) {
					farDist = d;
					farIdx = i;
				}
			}
			int midIdx = (farIdx + 1) % n;
			float[] a = new float[] { cx, cy, poly[farIdx * 2], poly[farIdx * 2 + 1], poly[midIdx * 2],
					poly[midIdx * 2 + 1] };
			float[] b = new float[n * 2 + 2];
			int bi = 0;
			b[bi++] = cx;
			b[bi++] = cy;
			for (int i = 0; i < n; i++) {
				if (i == farIdx || i == midIdx) {
					continue;
				}
				b[bi++] = poly[i * 2];
				b[bi++] = poly[i * 2 + 1];
			}
			if (!isDegenerate(a)) {
				addIfWithinSize(polyList, a, approxW / 2f, approxH / 2f);
			}
			if (bi >= 6) {
				float[] btrim = new float[bi];
				System.arraycopy(b, 0, btrim, 0, bi);
				if (!isDegenerate(btrim)) {
					addIfWithinSize(polyList, btrim, approxW / 2f, approxH / 2f);
				}
			}
			return;
		}

		if (w < _minShardW || h < _minShardH) {
			float cx = 0f, cy = 0f;
			for (int i = 0; i < n; i++) {
				cx += poly[i * 2];
				cy += poly[i * 2 + 1];
			}
			cx /= n;
			cy /= n;
			float scaleX = (w < _minShardW) ? (_minShardW / MathUtils.max(1f, w)) : 1f;
			float scaleY = (h < _minShardH) ? (_minShardH / MathUtils.max(1f, h)) : 1f;
			float scale = MathUtils.max(scaleX, scaleY);
			float[] scaled = new float[n * 2];
			for (int i = 0; i < n; i++) {
				float vx = poly[i * 2];
				float vy = poly[i * 2 + 1];
				float nx = cx + (vx - cx) * scale;
				float ny = cy + (vy - cy) * scale;
				scaled[i * 2] = MathUtils.clamp(nx, 0f, getWidth());
				scaled[i * 2 + 1] = MathUtils.clamp(ny, 0f, getHeight());
			}
			if (!isDegenerate(scaled)) {
				polyList.add(scaled);
			}
			return;
		}
	}

	private boolean isDegenerate(float[] poly) {
		int n = poly.length / 2;
		if (n < 3) {
			return true;
		}
		float area = 0f;
		for (int i = 0; i < n; i++) {
			float x1 = poly[i * 2];
			float y1 = poly[i * 2 + 1];
			float x2 = poly[((i + 1) % n) * 2];
			float y2 = poly[((i + 1) % n) * 2 + 1];
			area += x1 * y2 - x2 * y1;
		}
		area = MathUtils.abs(area) * 0.5f;
		return area < 0.5f;
	}

	@Override
	public FadeGlassShatterEffect reset() {
		super.reset();
		this.setDelay(this._time);
		initShards();
		return this;
	}

	@Override
	public void repaint(GLEx g, float sx, float sy) {
		if (completedAfterBlackScreen(g, sx, sy)) {
			return;
		}
		final float rawProgress = _currentFrame / _time;
		final float globalProgress = (_type == TYPE_FADE_IN) ? (1f - rawProgress) : rawProgress;
		if (_type == TYPE_FADE_IN && _completed) {
			g.fillRect(drawX(sx), drawY(sy), _width, _height, _baseColor);
			return;
		}
		if (_type == TYPE_FADE_OUT && _completed) {
			return;
		}
		if (globalProgress <= 0.2f && _type == TYPE_FADE_OUT) {
			return;
		}
		if (globalProgress <= 0.2f && _type == TYPE_FADE_IN) {
			g.fillRect(drawX(sx), drawY(sy), _width, _height, _baseColor);
			return;
		}
		int idx = 0;
		float centerX = getWidth() * 0.5f;
		float screenHalf = getWidth() * 0.5f;
		for (int row = 0; row < _rows; row++) {
			for (int col = 0; col < _cols; col++) {
				TileShard[] shards = _tiles.get(idx);
				for (TileShard s : shards) {
					float tileOffset = _tileOffsets[idx];
					float shardDelay;
					if (_type == TYPE_FADE_OUT) {
						float distToLeft = s.centerX;
						float distToRight = getWidth() - s.centerX;
						float minEdgeDist = MathUtils.min(distToLeft, distToRight);
						float norm = MathUtils.clamp(minEdgeDist / screenHalf, 0f, 1f);
						shardDelay = norm * 0.9f + tileOffset;
					} else {
						shardDelay = s.baseDelay / _waveSpeed + tileOffset;
					}

					float localProgress = MathUtils.clamp((globalProgress - shardDelay) / (1f - shardDelay), 0f, 1f);
					float shardProg = MathUtils.clamp(localProgress + s.jitter * 0.12f, 0f, 1f);
					float eased = (-(MathUtils.cos(MathUtils.PI * shardProg) - 1f) / 2f);

					float alpha;
					float scale;
					float rot;
					float disp;

					if (_type == TYPE_FADE_IN) {
						alpha = eased;
						scale = 0.6f + 0.4f * eased;
						rot = s.rotDir * eased * 0.6f;
						disp = eased * _explosionStrength * (0.6f + s.dispJitter);
						float dirX = s.centerX - _centerX;
						float dirY = s.centerY - _centerY;
						float len = MathUtils.hypot(dirX, dirY);
						if (len < 1e-4f) {
							dirX = 1f;
							dirY = 0f;
							len = 1f;
						}
						dirX /= len;
						dirY /= len;
						if (alpha > 0.01f) {
							int n = s.verts.length / 2;
							float[] tx = new float[n];
							float[] ty = new float[n];
							float cos = MathUtils.cos(rot), sin = MathUtils.sin(rot);
							for (int i = 0; i < n; i++) {
								float vx = s.verts[i * 2];
								float vy = s.verts[i * 2 + 1];
								float dx = vx - s.centerX;
								float dy = vy - s.centerY;
								float rx = dx * cos - dy * sin;
								float ry = dx * sin + dy * cos;
								rx *= scale;
								ry *= scale;
								float outX = s.centerX + rx + dirX * disp;
								float outY = s.centerY + ry + dirY * disp;
								tx[i] = outX;
								ty[i] = outY;
							}
							g.fillPolygon(tx, ty, n, _tempColor.setColor(_baseColor).setAlpha(alpha));
						}
					} else {
						alpha = eased;
						scale = 0.4f + 0.6f * eased;
						rot = -s.rotDir * eased * 0.6f;
						disp = eased * _repairStrength * (0.4f + s.dispJitter);
						float dirX = centerX - s.centerX;
						float len = MathUtils.abs(dirX);
						if (len < 1e-4f) {
							dirX = 0f;
							len = 1f;
						}
						dirX /= len;
						float signedDisp = dirX * disp;
						if (alpha > 0.01f) {
							int n = s.verts.length / 2;
							float[] tx = new float[n];
							float[] ty = new float[n];
							float cos = MathUtils.cos(rot), sin = MathUtils.sin(rot);
							for (int i = 0; i < n; i++) {
								float vx = s.verts[i * 2];
								float vy = s.verts[i * 2 + 1];
								float dx = vx - s.centerX;
								float dy = vy - s.centerY;
								float rx = dx * cos - dy * sin;
								float ry = dx * sin + dy * cos;
								rx *= scale;
								ry *= scale;
								float outX = s.centerX + rx + signedDisp;
								float outY = s.centerY + ry;
								tx[i] = outX;
								ty[i] = outY;
							}
							g.fillPolygon(tx, ty, n, _tempColor.setColor(_baseColor).setAlpha(alpha * 0.6f));
						}
					}
				}
				idx++;
			}
		}
	}

	@Override
	public void onUpdate(long timer) {
		if (checkAutoRemove()) {
			return;
		}
		if (_type == TYPE_FADE_IN) {
			_currentFrame -= _step;
			if (_currentFrame <= _step) {
				_completed = true;
			}
		} else {
			_currentFrame += _step;
			if (_currentFrame >= _time - _step) {
				_completed = true;
			}
		}
	}

	public FadeGlassShatterEffect setShardCountRange(int min, int max) {
		this._vertexJitter = MathUtils.max(0f, min);
		initShards();
		return this;
	}

	@Override
	public FadeGlassShatterEffect setAutoRemoved(boolean autoRemoved) {
		super.setAutoRemoved(autoRemoved);
		return this;
	}
}
