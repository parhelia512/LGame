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
package loon.action.sprite.effect.explosion;

import loon.BaseIO;
import loon.LSystem;
import loon.LTexture;
import loon.action.sprite.Entity;
import loon.action.sprite.effect.BaseEffect;
import loon.canvas.Image;
import loon.canvas.LColor;
import loon.canvas.Pixmap;
import loon.geom.RectBox;
import loon.geom.RectI;
import loon.opengl.GLEx;
import loon.utils.Easing.EasingMode;
import loon.utils.timer.EaseTimer;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

/**
 * 像素风爆炸特效,让指定Image以指定的爆炸方式离开Screen画面
 */
public class ExplosionEffect extends Entity implements BaseEffect {

	/** 效果模式枚举 */
	public static enum Mode {
		Tattered, Explode, FlyLeft, FlyLeftDown, FlyRight, FlyRightDown;
	}

	public interface OnExplosionListener {

		/**
		 * 播放进度回调
		 * 
		 * @param progress     0~1 进度值
		 * @param currentIndex 当前序列图片索引
		 * @param totalCount   序列总图片数
		 */
		void onProgress(float progress, int currentIndex, int totalCount);

		/**
		 * 单张图片特效播放完成
		 * 
		 * @param currentIndex 当前完成的索引
		 */
		void onSingleCompleted(int currentIndex);

		/**
		 * 自动切换到下一张图片
		 * 
		 * @param newIndex  新图片索引
		 * @param imagePath 新图片路径
		 */
		void onImageSwitched(int newIndex, String imagePath);

		/**
		 * 整个图片序列播放完成
		 */
		void onSequenceCompleted();
	}

	private static class CacheEntry {
		final Image image;
		final LTexture texture;

		CacheEntry(Image image, LTexture texture) {
			this.image = image;
			this.texture = texture;
		}
	}

	private final static ObjectMap<String, CacheEntry> IMAGE_CACHE = new ObjectMap<String, CacheEntry>();

	private static synchronized Image loadCachedImage(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		CacheEntry entry = IMAGE_CACHE.get(path);
		if (entry == null || entry.image == null || entry.image.isClosed()) {
			Image img = BaseIO.loadImage(path);
			LTexture tex = img.texture();
			entry = new CacheEntry(img, tex);
			IMAGE_CACHE.put(path, entry);
		}
		return entry.image;
	}

	public static synchronized void clearCache(String path) {
		CacheEntry entry = IMAGE_CACHE.remove(path);
		if (entry != null && entry.texture != null) {
			entry.texture.close();
		}
	}

	public static synchronized void clearAllCache() {
		for (CacheEntry entry : IMAGE_CACHE.values()) {
			entry.texture.close();
		}
		IMAGE_CACHE.clear();
	}

	private boolean _startExplision;
	private Mode _lastMode;
	private LTexture _ovalTexture;
	private Fragment[][] _fragments;
	private boolean _packed = false;
	private int _blockWidth;
	private int _blockHeight;
	private Mode _mode;
	private Image _pixmap;
	private boolean _autoRemoved;
	private EasingMode _easingMode;
	private EaseTimer _timer;
	private RectBox _imageRect;
	private LTexture _image;

	// 状态标记
	private String _currentImagePath;
	private boolean _imageChanged;
	private boolean _sizeChanged;

	private TArray<String> _imageSequence;
	private int _currentSequenceIndex;
	private boolean _sequencePlaying;
	private boolean _loopSequence;
	// 回调监听
	private OnExplosionListener _listener;

	public ExplosionEffect(Mode m, String path) {
		this(m, loadCachedImage(path), path);
	}

	public ExplosionEffect(Mode m, Image pix) {
		this(m, pix, null, 8, 8, EasingMode.Linear, 1f);
	}

	public ExplosionEffect(Mode m, String path, RectBox rect) {
		this(m, path, rect, 1f);
	}

	public ExplosionEffect(Mode m, String path, RectBox rect, float duration) {
		this(m, loadCachedImage(path), rect, duration);
		this._currentImagePath = path;
	}

	public ExplosionEffect(Mode m, Image pix, RectBox rect, float duration) {
		this(m, pix, rect, 8, 8, EasingMode.Linear, duration);
	}

	public ExplosionEffect(Mode m, Image pix, EasingMode ease) {
		this(m, pix, null, 8, 8, ease, 1f);
	}

	public ExplosionEffect(Mode m, Image pix, EasingMode ease, float duration) {
		this(m, pix, null, 8, 8, ease, duration);
	}

	public ExplosionEffect(Mode m, Image pix, RectBox imageSize, EasingMode ease, float duration) {
		this(m, pix, imageSize, 8, 8, ease, duration);
	}

	public ExplosionEffect(Mode m, Image pix, int tw, int th, EasingMode ease, float duration) {
		this(m, pix, null, tw, th, ease, duration);
	}

	public ExplosionEffect(Mode m, Image pix, RectBox imageSize, int tw, int th, EasingMode ease, float duration) {
		this._mode = m;
		this._pixmap = pix;
		this._blockWidth = tw;
		this._blockHeight = th;
		this._easingMode = ease;
		this._timer = new EaseTimer(duration, ease);
		this._imageChanged = false;
		this._sizeChanged = false;

		// 序列播放初始化
		this._sequencePlaying = false;
		this._loopSequence = false;
		this._currentSequenceIndex = 0;

		// 图片区域初始化
		if (imageSize == null) {
			this._imageRect = new RectBox(0, 0, pix.getWidth(), pix.getHeight());
		} else {
			this._imageRect = imageSize;
		}
		this.setBounds(_imageRect);
		setRepaint(true);
	}

	private ExplosionEffect(Mode m, Image pix, String path) {
		this(m, pix, null, 8, 8, EasingMode.Linear, 1f);
		this._currentImagePath = path;
	}

	/**
	 * 设置图片序列（自动预加载缓存）
	 * 
	 * @param sequence
	 * @return
	 */
	public ExplosionEffect setImageSequence(TArray<String> sequence) {
		this._imageSequence = sequence;
		this._currentSequenceIndex = 0;
		if (sequence != null && !sequence.isEmpty()) {
			for (String path : sequence) {
				loadCachedImage(path);
			}
			setImage(sequence.get(0));
		}
		return this;
	}

	/**
	 * 开始自动播放图片序列
	 */
	public ExplosionEffect startSequence() {
		if (_imageSequence == null || _imageSequence.isEmpty()) {
			throw new IllegalStateException("图片序列为空，请先调用setImageSequence()");
		}
		this._sequencePlaying = true;
		this._currentSequenceIndex = 0;
		setImage(_imageSequence.get(0));
		start();
		return this;
	}

	/**
	 * 停止序列播放
	 */
	public ExplosionEffect stopSequence() {
		this._sequencePlaying = false;
		stop();
		return this;
	}

	/**
	 * 设置序列循环播放
	 * 
	 * @param loop
	 * @return
	 */
	public ExplosionEffect setLoopSequence(boolean loop) {
		this._loopSequence = loop;
		return this;
	}

	/**
	 * 设置回调监听
	 * 
	 * @param listener
	 * @return
	 */
	public ExplosionEffect setOnExplosionListener(OnExplosionListener listener) {
		this._listener = listener;
		return this;
	}

	/**
	 * 获取当前播放进度（0~1）
	 */
	public float getProgress() {
		return _timer.getProgress();
	}

	/**
	 * 获取当前序列索引
	 */
	public int getCurrentSequenceIndex() {
		return _currentSequenceIndex;
	}

	/**
	 * 获取序列总数量
	 */
	public int getSequenceTotalCount() {
		return _imageSequence == null ? 0 : _imageSequence.size();
	}

	public ExplosionEffect setImage(String path) {
		stop();
		freeLocalResources();
		this._pixmap = loadCachedImage(path);
		this._currentImagePath = path;
		this._imageChanged = true;
		this._imageRect = new RectBox(0, 0, _pixmap.getWidth(), _pixmap.getHeight());
		this.setBounds(_imageRect);
		return this;
	}

	public ExplosionEffect setImage(Image pix) {
		stop();
		freeLocalResources();
		this._pixmap = pix;
		this._currentImagePath = null;
		this._imageChanged = true;
		this._imageRect = new RectBox(0, 0, pix.getWidth(), pix.getHeight());
		this.setBounds(_imageRect);
		return this;
	}

	public ExplosionEffect setBlockSize(int blockWidth, int blockHeight) {
		if (this._blockWidth != blockWidth || this._blockHeight != blockHeight) {
			this._blockWidth = blockWidth;
			this._blockHeight = blockHeight;
			this._sizeChanged = true;
			stop();
		}
		return this;
	}

	public ExplosionEffect setDuration(float duration) {
		this._timer = new EaseTimer(duration, _easingMode);
		return this;
	}

	private void switchToNextImage() {
		if (_imageSequence == null || !_sequencePlaying) {
			return;
		}
		_currentSequenceIndex++;
		int total = _imageSequence.size();
		if (_listener != null) {
			_listener.onSingleCompleted(_currentSequenceIndex - 1);
		}
		if (_currentSequenceIndex >= total) {
			if (_loopSequence) {
				_currentSequenceIndex = 0;
			} else {
				_sequencePlaying = false;
				if (_listener != null) {
					_listener.onSequenceCompleted();
				}
				if (_autoRemoved && getSprites() != null) {
					getSprites().remove(this);
				}
				return;
			}
		}

		String nextPath = _imageSequence.get(_currentSequenceIndex);
		setImage(nextPath);
		start();

		if (_listener != null) {
			_listener.onImageSwitched(_currentSequenceIndex, nextPath);
		}
	}

	public void pack() {
		boolean needRefresh = !_packed || _mode != _lastMode || _imageChanged || _sizeChanged;
		if (needRefresh) {
			if (_sizeChanged || _ovalTexture == null) {
				if (_ovalTexture != null) {
					_ovalTexture.close();
				}
				createOvalImage();
			}
			if (_fragments == null || _mode != _lastMode || _imageChanged || _sizeChanged) {
				_fragments = createFrags(
						new RectI(_imageRect.x(), _imageRect.y(), _imageRect.width, _imageRect.height));
			} else {
				for (int i = 0; i < _fragments.length; i++) {
					for (int j = 0; j < _fragments[i].length; j++) {
						_fragments[i][j].reset();
					}
				}
			}
			if (_image == null || _imageChanged) {
				_image = (_currentImagePath != null) ? IMAGE_CACHE.get(_currentImagePath).texture : _pixmap.texture();
			}
			_packed = true;
			_imageChanged = false;
			_sizeChanged = false;
		}
	}

	@Override
	protected void onUpdate(final long elapsedTime) {
		if (!isVisible()) {
			return;
		}
		if (_startExplision) {
			_timer.action(elapsedTime);
			if (_listener != null) {
				_listener.onProgress(getProgress(), getCurrentSequenceIndex(), getSequenceTotalCount());
			}
			if (isCompleted() && _sequencePlaying) {
				switchToNextImage();
			}
			if (isCompleted() && !_sequencePlaying) {
				if (_autoRemoved && getSprites() != null) {
					getSprites().remove(this);
				} else {
					setVisible(false);
				}
			}
		}
	}

	public ExplosionEffect start(Mode m) {
		setVisible(true);
		this._timer.reset();
		this._startExplision = true;
		this._mode = m;
		this._lastMode = null;
		this._packed = false;
		return this;
	}

	@Override
	public void repaint(GLEx g, float offsetX, float offsetY) {
		if (!isVisible()) {
			return;
		}
		final int color = g.color();
		pack();
		float x = drawX(offsetX);
		float y = drawY(offsetY);
		if (_startExplision && _image != null) {
			float process = _timer.getProgress();
			float alpha = MathUtils.max(0f, 1f - (process * 2f));
			_baseColor.setAlpha(alpha);
			g.draw(_image, x, y, _baseColor);
			for (Fragment[] fragRow : _fragments) {
				for (Fragment frag : fragRow) {
					frag.draw(g, x, y, process);
				}
			}
		} else if (_image != null) {
			g.draw(_image, x, y, _baseColor);
		}
		g.setColor(color);
	}

	LTexture createOvalImage() {
		Pixmap pixmap = new Pixmap(_blockWidth + 1, _blockHeight + 1, true);
		pixmap.setColor(LColor.white);
		pixmap.fillOval(0, 0, _blockWidth, _blockHeight);
		_ovalTexture = pixmap.texture();
		return _ovalTexture;
	}

	private void freeLocalResources() {
		stop();
		_packed = false;
		if (_ovalTexture != null) {
			_ovalTexture.close();
			_ovalTexture = null;
		}
		if (_currentImagePath == null && _image != null) {
			_image.close();
		}
		_image = null;
	}

	protected Fragment[][] createTatteredFrags(Image img, RectI bound) {
		int w = bound.width, h = bound.height;
		int pw = w / _blockWidth, ph = h / _blockHeight;
		int iw = img.getWidth() / pw, ih = img.getHeight() / ph;
		Fragment[][] fs = new Fragment[ph][pw];
		for (int r = 0; r < ph; r++)
			for (int c = 0; c < pw; c++) {
				int color = img.getPixel(c * iw, r * ih);
				Fragment f = new TatteredFragment(color, bound.x + _blockWidth * c, bound.y + _blockHeight * r, bound,
						_ovalTexture);
				f._width = _blockWidth;
				f._height = _blockHeight;
				fs[r][c] = f;
			}
		return fs;
	}

	protected Fragment[][] createExplodeFrags(Image img, RectI bound) {
		RectI b = new RectI(bound);
		int pw = bound.width / _blockWidth, ph = bound.height / _blockHeight;
		int iw = img.getWidth() / pw, ih = img.getHeight() / ph;
		Fragment[][] fs = new Fragment[ph][pw];
		for (int r = 0; r < ph; r++)
			for (int c = 0; c < pw; c++) {
				Fragment f = createExplodeFrag(img.getPixel(c * iw, r * ih), b);
				f._width = _blockWidth;
				f._height = _blockHeight;
				fs[r][c] = f;
			}
		return fs;
	}

	private Fragment createExplodeFrag(int color, RectI bound) {
		final float dotSize = 10, ny = bound.height / 2, nv = 4, nw = 1, end = 1.4f;
		ExplodeFragment frag = new ExplodeFragment(color, 0, 0, new RectI(bound), nv, nv, end, _ovalTexture);
		frag._color = color;
		frag._width = nv;
		frag._height = nv;
		frag._baseRadius = MathUtils.random() < 0.2f ? nv + ((dotSize - nv) * MathUtils.random())
				: nw + ((nv - nw) * MathUtils.random());
		float rf = MathUtils.random();
		frag._top = bound.height * ((0.18f * MathUtils.random()) + 0.2f);
		frag._top = rf < 0.2f ? frag._top : frag._top + (frag._top * 0.2f * MathUtils.random());
		frag._bottom = (bound.height * (MathUtils.random() - 0.5f)) * 1.8f;
		frag._bottom = rf < 0.2f ? frag._bottom : rf < 0.8f ? frag._bottom * 0.6f : frag._bottom * 0.3f;
		frag._mag = 4f * frag._top / frag._bottom;
		frag._neg = (-frag._mag) / frag._bottom;
		frag._baseCx = bound.centerX() + (ny * (MathUtils.random() - 0.5f));
		frag._cx = frag._baseCx;
		frag._baseCy = bound.centerY() + (ny * (MathUtils.random() - 0.5f));
		frag._cy = frag._baseCy;
		frag._life = end / 10f * MathUtils.random();
		frag._overflow = 0.4f * MathUtils.random();
		frag._alpha = 1f;
		return frag;
	}

	protected Fragment[][] createFlyLeftDownFrags(Image img, RectI bound) {
		int pw = bound.width / _blockWidth, ph = bound.height / _blockHeight;
		int iw = img.getWidth() / pw, ih = img.getHeight() / ph;
		Fragment[][] fs = new Fragment[ph][pw];
		for (int r = 0; r < ph; r++)
			for (int c = 0; c < pw; c++) {
				Fragment f = new FlyLeftDownFragment(img.getPixel(c * iw, r * ih), bound.x + _blockWidth * c,
						bound.y + _blockHeight * r, bound, _ovalTexture);
				f._width = _blockWidth;
				f._height = _blockHeight;
				fs[r][c] = f;
			}
		return fs;
	}

	protected Fragment[][] createFlyRightFrags(Image img, RectI bound) {
		int pw = bound.width / _blockWidth, ph = bound.height / _blockHeight;
		int iw = img.getWidth() / pw, ih = img.getHeight() / ph;
		Fragment[][] fs = new Fragment[ph][pw];
		for (int r = 0; r < ph; r++)
			for (int c = 0; c < pw; c++) {
				Fragment f = new FlyRightFragment(img.getPixel(c * iw, r * ih), bound.x + _blockWidth * c,
						bound.y + _blockHeight * r, bound, _ovalTexture);
				f._width = _blockWidth;
				f._height = _blockHeight;
				fs[r][c] = f;
			}
		return fs;
	}

	protected Fragment[][] createFlyRightDownFrags(Image img, RectI bound) {
		int pw = bound.width / _blockWidth, ph = bound.height / _blockHeight;
		int iw = img.getWidth() / pw, ih = img.getHeight() / ph;
		Fragment[][] fs = new Fragment[ph][pw];
		for (int r = 0; r < ph; r++)
			for (int c = 0; c < pw; c++) {
				Fragment f = new FlayRightDownFragment(img.getPixel(c * iw, r * ih), bound.x + _blockWidth * c,
						bound.y + _blockHeight * r, bound, _ovalTexture);
				f._width = _blockWidth;
				f._height = _blockHeight;
				fs[r][c] = f;
			}
		return fs;
	}

	protected Fragment[][] createFlyLeftFrags(Image img, RectI bound) {
		int pw = bound.width / _blockWidth, ph = bound.height / _blockHeight;
		int iw = img.getWidth() / pw, ih = img.getHeight() / ph;
		Fragment[][] fs = new Fragment[ph][pw];
		for (int r = 0; r < ph; r++)
			for (int c = 0; c < pw; c++) {
				Fragment f = new FlyLeftFragment(img.getPixel(c * iw, r * ih), bound.x + _blockWidth * c,
						bound.y + _blockHeight * r, bound, _ovalTexture);
				f._width = _blockWidth;
				f._height = _blockHeight;
				fs[r][c] = f;
			}
		return fs;
	}

	public Fragment[][] createFrags(RectI bound) {
		return createFrags(_pixmap, bound);
	}

	public Fragment[][] createFrags(Image img, RectI bound) {
		Fragment[][] fs = null;
		switch (_mode) {
		case Tattered:
			fs = createTatteredFrags(img, bound);
			break;
		case Explode:
			fs = createExplodeFrags(img, bound);
			break;
		case FlyRight:
			fs = createFlyRightFrags(img, bound);
			break;
		case FlyLeftDown:
			fs = createFlyLeftDownFrags(img, bound);
			break;
		case FlyRightDown:
			fs = createFlyRightDownFrags(img, bound);
			break;
		case FlyLeft:
			fs = createFlyLeftFrags(img, bound);
			break;
		}
		_lastMode = _mode;
		return fs;
	}

	public Mode getMode() {
		return _mode;
	}

	public ExplosionEffect setMode(Mode m) {
		this._mode = m;
		return this;
	}

	public EasingMode getEasingMode() {
		return _easingMode;
	}

	public Mode getLastMode() {
		return this._lastMode;
	}

	public int getBlockWidth() {
		return _blockWidth;

	}

	public int getBlockHeight() {
		return _blockHeight;
	}

	public ExplosionEffect setEasingMode(EasingMode easingMode) {
		this._easingMode = easingMode;
		return this;
	}

	public boolean isAutoRemoved() {
		return _autoRemoved;
	}

	public ExplosionEffect setAutoRemoved(boolean a) {
		_autoRemoved = a;
		return this;
	}

	@Override
	public boolean isCompleted() {
		return !_startExplision || _timer.getProgress() >= 0.99f;
	}

	public ExplosionEffect start() {
		return start(_mode);
	}

	public ExplosionEffect stop() {
		_timer.reset();
		_startExplision = false;
		_lastMode = null;
		_baseColor.reset();
		return this;
	}

	@Override
	public ExplosionEffect setStop(boolean c) {
		if (c) {
			_timer.add(LSystem.MINUTE);
		} else {
			_timer.reset();
		}
		return this;
	}

	@Override
	public ExplosionEffect reset() {
		super.reset();
		stop();
		setVisible(true);
		_sequencePlaying = false;
		_currentSequenceIndex = 0;
		_packed = false;
		_imageChanged = false;
		_sizeChanged = false;
		return this;
	}

	@Override
	protected void _onDestroy() {
		super._onDestroy();
		freeLocalResources();
		_pixmap = null;
		_image = null;
		_fragments = null;
		_listener = null;
		_imageSequence = null;
	}

}
