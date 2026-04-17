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
package loon.opengl;

import loon.canvas.LColor;
import loon.geom.Affine2f;
import loon.geom.Matrix4;
import loon.utils.GLUtils;
import loon.utils.IntFloatMap;
import loon.utils.MathUtils;
import loon.utils.NumberUtils;
import loon.LSystem;

public final class TrilateralBatch extends BaseBatch {

	private final static String BATCHNAME = "trilbatch";

	private final Matrix4 _viewMatrix;
	private final ExpandVertices _expandVertices;

	private float _ubufWidth = 0;
	private float _ubufHeight = 0;
	private float _currentFloatColor = -1f;

	private int _currentBlendMode = -1;
	private int _currentAlpha;
	private int _currentIndexCount = 0;
	private int _maxVertsInBatch = 0;
	private int _currentIntColor = -1;

	private boolean _uflip = true;
	private boolean _loaded = false, _locked = false;

	private ShaderProgram _currentBatchShader;
	private Submit _currentSubmit;

	private final static int COORD_SIZE = 8;
	private final static int VERT_SIZE = 20;

	private final float[] _coordCache = new float[COORD_SIZE];
	private final float[] _vertCache = new float[VERT_SIZE];

	private final Affine2f _affineCache = new Affine2f();
	private final IntFloatMap colorCache = new IntFloatMap(256);

	public TrilateralBatch(GL20 gl) {
		this(gl, LSystem.DEF_SOURCE);
	}

	public TrilateralBatch(GL20 gl, ShaderSource src) {
		this(gl, 2048, src);
	}

	public TrilateralBatch(GL20 gl, int maxSize, ShaderSource src) {
		super(gl);
		this._expandVertices = ExpandVertices.getVerticeCache(maxSize);
		this._shader_source = src;
		this._viewMatrix = new Matrix4();
		this.init();
	}

	@Override
	public void init() {
		this._currentSubmit = Submit.create();
		this._currentFloatColor = LColor.white.toFloatBits();
		preloadPalette();
		reset();
	}

	private void preloadPalette() {
		colorCache.put(0x00000000, NumberUtils.intBitsToFloat(0x00000000));
		colorCache.put(0xFFFFFFFF, NumberUtils.intBitsToFloat(0xFFFFFFFF));
		colorCache.put(0xFF000000, NumberUtils.intBitsToFloat(0xFF000000));
		colorCache.put(0xFFFF0000, NumberUtils.intBitsToFloat(0xFFFF0000));
		colorCache.put(0xFF00FF00, NumberUtils.intBitsToFloat(0xFF00FF00));
		colorCache.put(0xFF0000FF, NumberUtils.intBitsToFloat(0xFF0000FF));
		colorCache.put(0xFF808080, NumberUtils.intBitsToFloat(0xFF808080));
		colorCache.put(0xFFFFFF00, NumberUtils.intBitsToFloat(0xFFFFFF00));
		colorCache.put(0xFF800080, NumberUtils.intBitsToFloat(0xFF800080));
		colorCache.put(0xFFFFA500, NumberUtils.intBitsToFloat(0xFFFFA500));
		colorCache.put(0xFFFF6666, NumberUtils.intBitsToFloat(0xFFFF6666));
		colorCache.put(0xFF66FF66, NumberUtils.intBitsToFloat(0xFF66FF66));
		colorCache.put(0xFF6666FF, NumberUtils.intBitsToFloat(0xFF6666FF));
		colorCache.put(0xFFFFFF66, NumberUtils.intBitsToFloat(0xFFFFFF66));
		colorCache.put(0xFFAA66FF, NumberUtils.intBitsToFloat(0xFFAA66FF));
		colorCache.put(0xFFFFCC66, NumberUtils.intBitsToFloat(0xFFFFCC66));
		colorCache.put(0xFF800000, NumberUtils.intBitsToFloat(0xFF800000));
		colorCache.put(0xFF006400, NumberUtils.intBitsToFloat(0xFF006400));
		colorCache.put(0xFF000080, NumberUtils.intBitsToFloat(0xFF000080));
		colorCache.put(0xFF808000, NumberUtils.intBitsToFloat(0xFF808000));
		colorCache.put(0xFF4B0082, NumberUtils.intBitsToFloat(0xFF4B0082));
		colorCache.put(0xFF8B4500, NumberUtils.intBitsToFloat(0xFF8B4500));
		colorCache.put(0xFF123456, NumberUtils.intBitsToFloat(0xFF123456));
		colorCache.put(0xFF654321, NumberUtils.intBitsToFloat(0xFF654321));
	}

	private final float toFloatColor(int tint) {
		if (_currentIntColor != tint || _currentIntColor == -1) {
			_currentIntColor = tint;
			_currentAlpha = (tint >>> 24) & 0xFF;
			float cached = colorCache.get(tint);
			if (!MathUtils.isNan(cached)) {
				_currentFloatColor = cached;
			} else {
				int a = _currentAlpha;
				int r = (tint >>> 16) & 0xFF;
				int g = (tint >>> 8) & 0xFF;
				int b = tint & 0xFF;
				int abgr = (a << 24) | (b << 16) | (g << 8) | r;
				_currentFloatColor = NumberUtils.intBitsToFloat(abgr);
				colorCache.put(tint, _currentFloatColor);
			}
		}
		return _currentFloatColor;
	}

	@Override
	public void addQuad(int tint, float m00, float m01, float m10, float m11, float tx, float ty, float x1, float y1,
			float sx1, float sy1, float x2, float y2, float sx2, float sy2, float x3, float y3, float sx3, float sy3,
			float x4, float y4, float sx4, float sy4) {

		if (_locked) {
			return;
		}

		updateTexture();

		final float colorFloat = toFloatColor(tint);

		_coordCache[0] = m00 * x1 + m10 * y1 + tx;
		_coordCache[1] = m01 * x1 + m11 * y1 + ty;
		_coordCache[2] = m00 * x2 + m10 * y2 + tx;
		_coordCache[3] = m01 * x2 + m11 * y2 + ty;
		_coordCache[4] = m00 * x3 + m10 * y3 + tx;
		_coordCache[5] = m01 * x3 + m11 * y3 + ty;
		_coordCache[6] = m00 * x4 + m10 * y4 + tx;
		_coordCache[7] = m01 * x4 + m11 * y4 + ty;

		final int index = _currentIndexCount;
		_expandVertices.expand(index);

		_vertCache[0] = _coordCache[0];
		_vertCache[1] = _coordCache[1];
		_vertCache[2] = colorFloat;
		_vertCache[3] = sx1;
		_vertCache[4] = sy1;
		_vertCache[5] = _coordCache[2];
		_vertCache[6] = _coordCache[3];
		_vertCache[7] = colorFloat;
		_vertCache[8] = sx2;
		_vertCache[9] = sy2;
		_vertCache[10] = _coordCache[6];
		_vertCache[11] = _coordCache[7];
		_vertCache[12] = colorFloat;
		_vertCache[13] = sx4;
		_vertCache[14] = sy4;
		_vertCache[15] = _coordCache[4];
		_vertCache[16] = _coordCache[5];
		_vertCache[17] = colorFloat;
		_vertCache[18] = sx3;
		_vertCache[19] = sy3;

		_expandVertices.setBatch(index, _vertCache);
		_currentIndexCount = index + VERT_SIZE;
	}

	@Override
	public void quad(float m00, float m01, float m10, float m11, float tx, float ty, float x1, float y1, float c1,
			float x2, float y2, float c2, float x3, float y3, float c3, float x4, float y4, float c4, float u, float v,
			float u2, float v2) {

		if (_locked) {
			return;
		}

		updateTexture();

		_coordCache[0] = m00 * x1 + m10 * y1 + tx;
		_coordCache[1] = m01 * x1 + m11 * y1 + ty;
		_coordCache[2] = m00 * x2 + m10 * y2 + tx;
		_coordCache[3] = m01 * x2 + m11 * y2 + ty;
		_coordCache[4] = m00 * x3 + m10 * y3 + tx;
		_coordCache[5] = m01 * x3 + m11 * y3 + ty;
		_coordCache[6] = m00 * x4 + m10 * y4 + tx;
		_coordCache[7] = m01 * x4 + m11 * y4 + ty;

		final int index = _currentIndexCount;
		_expandVertices.expand(index);

		_vertCache[0] = _coordCache[0];
		_vertCache[1] = _coordCache[1];
		_vertCache[2] = c1;
		_vertCache[3] = u;
		_vertCache[4] = v;
		_vertCache[5] = _coordCache[2];
		_vertCache[6] = _coordCache[3];
		_vertCache[7] = c2;
		_vertCache[8] = u;
		_vertCache[9] = v2;
		_vertCache[10] = _coordCache[4];
		_vertCache[11] = _coordCache[5];
		_vertCache[12] = c3;
		_vertCache[13] = u2;
		_vertCache[14] = v2;
		_vertCache[15] = _coordCache[6];
		_vertCache[16] = _coordCache[7];
		_vertCache[17] = c4;
		_vertCache[18] = u2;
		_vertCache[19] = v;

		_expandVertices.setBatch(index, _vertCache);
		_currentIndexCount = index + VERT_SIZE;
	}

	@Override
	public void quad(int tint, float m00, float m01, float m10, float m11, float tx, float ty, float x1, float y1,
			float x2, float y2, float x3, float y3, float x4, float y4, float u, float v, float u2, float v2) {
		final float colorFloat = toFloatColor(tint);
		quad(m00, m01, m10, m11, tx, ty, x1, y1, colorFloat, x2, y2, colorFloat, x3, y3, colorFloat, x4, y4, colorFloat,
				u, v, u2, v2);
	}

	@Override
	public void begin(float fbufWidth, float fbufHeight, boolean flip) {
		super.begin(fbufWidth, fbufHeight, flip);
		if (this._ubufWidth != fbufWidth || this._ubufHeight != fbufHeight || this._uflip != flip) {
			this._ubufWidth = fbufWidth;
			this._ubufHeight = fbufHeight;
			this._viewMatrix.setToOrtho2D(0, 0, _ubufWidth, _ubufHeight);
			this._uflip = flip;
			if (!flip) {
				float w = _ubufWidth / 2;
				float h = _ubufHeight / 2;
				_affineCache.idt();
				_affineCache.translate(w, h);
				_affineCache.scale(-1, 1);
				_affineCache.translate(-w, -h);
				_affineCache.translate(w, h);
				_affineCache.rotateRadians(MathUtils.PI);
				_affineCache.translate(-w, -h);
				this._viewMatrix.mul(_affineCache);
			}
		}
		final boolean dirty = isShaderDirty();
		if (!_loaded || dirty) {
			if (_currentBatchShader == null || dirty) {
				if (_currentBatchShader != null) {
					_currentBatchShader.close();
					_currentBatchShader = null;
				}
				_currentBatchShader = createShaderProgram();
				setShaderDirty(false);
			}
			_loaded = true;
		}
		this._currentBatchShader.begin();
		this.drawCallCount = 0;
		this.setupMatrices();
	}

	@Override
	protected ShaderProgram createShaderProgram() {
		return GLUtils.createShaderProgram(_shader_source.vertexShader(), _shader_source.fragmentShader());
	}

	@Override
	protected ShaderProgram getShaderProgram() {
		return _currentBatchShader;
	}

	protected int vertexSize() {
		return _expandVertices.vertexSize();
	}

	protected void reset() {
		this._currentBlendMode = -1;
		this._currentIntColor = -1;
		this._currentAlpha = 255;
	}

	@Override
	public void end() {
		super.end();
		reset();
	}

	@Override
	public void flush() {
		if (_currentIndexCount == 0) {
			return;
		}
		super.flush();
		submit();
		if (_currentBatchShader != null) {
			_currentBatchShader.end();
		}
	}

	public int getSize() {
		return _expandVertices.getSize();
	}

	public TrilateralBatch setShaderUniformf(String name, LColor color) {
		if (_currentBatchShader != null) {
			_currentBatchShader.setUniformf(name, color);
		}
		return this;
	}

	public TrilateralBatch setShaderUniformf(int name, LColor color) {
		if (_currentBatchShader != null) {
			_currentBatchShader.setUniformf(name, color);
		}
		return this;
	}

	public boolean isLockSubmit() {
		return _locked;
	}

	public TrilateralBatch setLockSubmit(boolean locked) {
		this._locked = locked;
		return this;
	}

	public void submit() {
		if (_currentIndexCount == 0) {
			return;
		}
		try {
			final int vertCount = _currentIndexCount / _expandVertices.vertexSize();
			if (vertCount > _maxVertsInBatch) {
				_maxVertsInBatch = vertCount;
			}
			final int count = vertCount * 6;
			bindTexture();
			final GL20 gl = LSystem.base().graphics().gl;
			final int blend = GLUtils.getBlendMode();
			if (_currentBlendMode == -1) {
				if (_currentAlpha >= 240) {
					GLUtils.setBlendMode(gl, BlendMethod.MODE_NORMAL);
				} else {
					GLUtils.setBlendMode(gl, BlendMethod.MODE_SPEED);
				}
			} else {
				GLUtils.setBlendMode(gl, _currentBlendMode);
			}
			_currentSubmit.post(BATCHNAME, _expandVertices.getSize(), _currentBatchShader,
					_expandVertices.getVertices(), _currentIndexCount, count);
			GLUtils.setBlendMode(gl, blend);
		} catch (Throwable ex) {
			LSystem.error("Batch submit() error", ex);
		} finally {
			if (_expandVertices.expand(this._currentIndexCount)) {
				_currentSubmit.reset(BATCHNAME, _expandVertices.length());
			}
			if (!_locked) {
				_currentIndexCount = 0;
			}
		}
	}

	private void setupMatrices() {
		if (_currentBatchShader != null) {
			_currentBatchShader.setUniformMatrix("u_projTrans", _viewMatrix);
			_currentBatchShader.setUniformi("u_texture", 0);
			_shader_source.setupShader(_currentBatchShader);
		}
	}

	@Override
	public BaseBatch setBlendMode(int b) {
		this._currentBlendMode = b;
		return this;
	}

	@Override
	public int getBlendMode() {
		return _currentBlendMode;
	}

	@Override
	public void close() {
		super.close();
		if (_currentBatchShader != null) {
			_currentBatchShader.close();
		}
		this._currentBlendMode = -1;
	}

	@Override
	public String toString() {
		return "tris/" + _expandVertices.length();
	}
}
