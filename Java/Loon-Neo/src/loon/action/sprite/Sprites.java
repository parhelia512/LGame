/**
 * Copyright 2008 - 2010
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

import loon.LObject.State;

import java.util.Iterator;

import loon.LObject;
import loon.LRelease;
import loon.LSysException;
import loon.LSystem;
import loon.LTexture;
import loon.Screen;
import loon.Visible;
import loon.ZIndex;
import loon.action.ActionBind;
import loon.action.ActionControl;
import loon.action.PlaceActions;
import loon.action.collision.CollisionAction;
import loon.action.map.Side;
import loon.component.layout.Margin;
import loon.events.Created;
import loon.events.QueryEvent;
import loon.events.ResizeListener;
import loon.events.SysInput;
import loon.geom.Circle;
import loon.geom.DirtyRectList;
import loon.geom.Ellipse;
import loon.geom.Line;
import loon.geom.PointI;
import loon.geom.RectBox;
import loon.geom.Triangle2f;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.FrameBuffer;
import loon.opengl.GLEx;
import loon.opengl.GLEx.Direction;
import loon.opengl.ShaderMask;
import loon.opengl.ShaderSource;
import loon.opengl.light.Light2D;
import loon.opengl.light.Light2D.LightType;
import loon.opengl.mask.BilinearMask;
import loon.opengl.mask.FBOMask;
import loon.utils.CollectionUtils;
import loon.utils.IArray;
import loon.utils.IntArray;
import loon.utils.IntMap;
import loon.utils.LayerSorter;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.ObjectSet;
import loon.utils.SortUtils;
import loon.utils.StringUtils;
import loon.utils.TArray;
import loon.utils.reply.Callback;

import java.util.Comparator;

/**
 * 精灵精灵总父类，用来注册，控制，以及渲染所有精灵精灵（所有默认【不支持】触屏的精灵，被置于此。不过，
 * 当LNode系列精灵和SpriteBatchScreen合用时，也支持触屏.）
 */
public final class Sprites extends PlaceActions implements Visible, ZIndex, IArray, LRelease, Iterable<ISprite> {

	private static class Skew25SpriteSorter<T extends ISprite> implements Comparator<T> {
		@Override
		public int compare(T a, T b) {
			if (a == null || b == null) {
				return 0;
			}
			float weightA = a.getY() * 1000 + a.getX();
			float weightB = b.getY() * 1000 + b.getX();
			return MathUtils.compare(weightB, weightA);
		}
	}

	private static final int LAYOUT_ROW = 0;
	private static final int LAYOUT_COL = 1;
	private static final int LAYOUT_PADDING = 2;
	private static final int DEFAULT_INIT_CAPACITY = CollectionUtils.INITIAL_CAPACITY;

	public static interface SpriteListener {
		public Sprites update(ISprite spr);
	}

	private boolean _useFrameBufferShaderMask;
	private FBOMask _frameBufferShaderMask;
	// 改变画面UV斜率
	private boolean _changeUVTilt = false;
	private BilinearMask _uvMask;
	private float _offsetUVx, _offsetUVy;
	// 是否在整个桌面组件中使用光源
	private boolean _useLight = false;
	private Light2D _light;
	// 是否使用shadermask改变画面显示效果
	private boolean _useShaderMask = false;
	// 此项为true时，内置FrameBuffer将被启用,画面会渲染去内置FrameBuffer纹理中
	private boolean _spriteSavetoFrameBuffer;
	private FrameBuffer _spriteFrameBuffer;
	private ShaderMask _shaderMask;
	private final DirtyRectList _dirtyList = new DirtyRectList();
	private ISpritesShadow _spriteShadow;
	private Margin _margin;
	private ObjectSet<String> _collisionIgnoreStrings;
	private IntArray _collisionIgnoreTypes;
	private int _indexLayer;
	private boolean _createShadow;
	private boolean _sortableChildren;
	private boolean _isViewWindowSet = false, _limitViewWindows = false, _visible = true, _closed = false;
	private boolean _resizabled = true;

	private final Skew25SpriteSorter<ISprite> spriteSkew25Sorter = new Skew25SpriteSorter<ISprite>();
	private final LayerSorter<ISprite> spriteLayerSorter = new LayerSorter<ISprite>();
	private final SpriteSorter<ISprite> spriteXYSorter = new SpriteSorter<ISprite>();

	private int _currentPosHash = 1;
	private int _lastPosHash = 1;

	private int _viewX;
	private int _viewY;
	private int _viewWidth;
	private int _viewHeight;
	private int _size = 0;
	private int _sizeExpandCount = 1;
	private int _width = 0, _height = 0;
	private float _newLineHeight = -1f;
	private float _scrollX = 0f;
	private float _scrollY = 0f;
	private final String _sprites_name;
	private boolean _autoSortLayer;
	private boolean _checkAllCollision;
	private boolean _checkViewCollision;
	private boolean _dirtyChildren;
	private boolean _autoSortSkew25;

	private SpriteListener _sprListener = null;
	private ResizeListener<Sprites> _resizeListener;
	private CollisionAction<ISprite> _collisionActionListener;
	private RectBox _collViewSize;
	private TArray<ISprite> _collisionObjects;
	private Screen _screen;
	private SysInput _input;
	protected ISprite[] _sprites;
	// 碰撞检测缓存
	private final ObjectMap<ISprite, RectBox> _collisionBoxCache = new ObjectMap<ISprite, RectBox>();
	// 碰撞对象，避免同一对精灵重复触发碰撞
	private final IntMap<Boolean> _collisionDebounceCache = new IntMap<Boolean>();
	private ObjectMap<String, TArray<ISprite>> _nameCache;
	// 标记缓存是否需要重建
	private boolean _cacheDirty = true;

	public Sprites(Screen screen, int w, int h) {
		this(null, screen, w, h);
	}

	public Sprites(Screen screen, float width, float height) {
		this(null, screen, MathUtils.iceil(width), MathUtils.iceil(height));
	}

	public Sprites(Screen screen) {
		this(null, screen, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public Sprites(String name, Screen screen, float w, float h) {
		this(name, screen, MathUtils.iceil(w), MathUtils.iceil(h));
	}

	public Sprites(String name, Screen screen, int w, int h) {
		this._sortableChildren = this._visible = this._resizabled = true;
		this._sprites = new ISprite[DEFAULT_INIT_CAPACITY];
		this._sprites_name = StringUtils.isEmpty(name) ? "Sprites" + LSystem.getSpritesSize() : name;
		this._size = 0;
		this._sizeExpandCount = 1;
		this._currentPosHash = _lastPosHash = 1;
		this._newLineHeight = -1f;
		this._nameCache = new ObjectMap<String, TArray<ISprite>>();
		this._dirtyChildren = true;
		this.setScreen(screen);
		this.setSize(w, h);
		LSystem.pushSpritesPool(this);
	}

	/**
	 * 设定每次扩展精灵数组的基本值
	 * 
	 * @param count
	 * @return
	 */
	public Sprites setSizeExpandCount(int count) {
		_sizeExpandCount = count;
		return this;
	}

	public int getSizeExpandCount() {
		return _sizeExpandCount;
	}

	/**
	 * 标记缓存已脏
	 */
	private void invalidateCache() {
		this._cacheDirty = true;
	}

	/**
	 * 重建名称索引缓存
	 */
	private void rebuildNameCache() {
		if (!_cacheDirty) {
			return;
		}
		_nameCache.clear();
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null && spr.getName() != null) {
				TArray<ISprite> list = _nameCache.get(spr.getName());
				if (list == null) {
					list = new TArray<ISprite>();
					_nameCache.put(spr.getName(), list);
				}
				list.add(spr);
			}
		}
		_cacheDirty = false;
	}

	/**
	 * 设定当前精灵管理器对应的屏幕
	 * 
	 * @param screen
	 * @return
	 */
	public Sprites setScreen(Screen screen) {
		this._screen = screen;
		this.setInput(screen);
		return this;
	}

	/**
	 * 设定当前精灵管理器对应的操作输入器
	 * 
	 * @param input
	 * @return
	 */
	public Sprites setInput(SysInput input) {
		this._input = input;
		return this;
	}

	/**
	 * 获得当前屏幕对应的操作输入器
	 * 
	 * @return
	 */
	public SysInput screenInput() {
		return this._input;
	}

	/**
	 * 设定Sprites大小
	 * 
	 * @param w
	 * @param h
	 * @return
	 */
	public Sprites setSize(int w, int h) {
		if (this._width != w || this._height != h) {
			this._width = MathUtils.max(w, 1);
			this._height = MathUtils.max(h, 1);
			this._viewWidth = MathUtils.max(this._viewWidth, this._width);
			this._viewHeight = MathUtils.max(this._viewHeight, this._height);
			this.resize(w, h, true);
		}
		return this;
	}

	/**
	 * 设定指定对象到图层最前
	 * 
	 * @param sprite
	 */
	public Sprites sendToFront(ISprite sprite) {
		if (_closed || _size <= 1 || _sprites[0] == sprite) {
			return this;
		}
		int targetIndex = -1;
		for (int i = 0; i < _size; i++) {
			if (_sprites[i] == sprite) {
				targetIndex = i;
				break;
			}
		}
		if (targetIndex == -1) {
			return this;
		}
		System.arraycopy(_sprites, 0, _sprites, 1, targetIndex);
		_sprites[0] = sprite;
		_dirtyChildren = true;
		invalidateCache();
		return this;
	}

	/**
	 * 设定指定对象到图层最后
	 * 
	 * @param sprite
	 */
	public Sprites sendToBack(ISprite sprite) {
		if (_closed || _size <= 1 || _sprites[_size - 1] == sprite) {
			return this;
		}
		int targetIndex = -1;
		for (int i = 0; i < _size; i++) {
			if (_sprites[i] == sprite) {
				targetIndex = i;
				break;
			}
		}
		if (targetIndex == -1) {
			return this;
		}
		int moveCount = _size - targetIndex - 1;
		System.arraycopy(_sprites, targetIndex + 1, _sprites, targetIndex, moveCount);
		_sprites[_size - 1] = sprite;
		_dirtyChildren = true;
		invalidateCache();
		return this;
	}

	/**
	 * 按所在层级排序
	 * 
	 */
	public Sprites sortSprites() {
		if (_closed || _size <= 1 || !_sortableChildren || !_dirtyChildren) {
			return this;
		}
		if (_sprites.length != _size) {
			ISprite[] sprs = CollectionUtils.copyOf(_sprites, _size);
			spriteLayerSorter.sort(sprs);
			_sprites = sprs;
		} else {
			spriteLayerSorter.sort(_sprites);
		}
		_dirtyChildren = false;
		invalidateCache();
		return this;
	}

	public Sprites setSortableChildren(boolean v) {
		this._sortableChildren = v;
		return this;
	}

	public boolean isSortableChildren() {
		return this._sortableChildren;
	}

	/**
	 * 扩充当前集合容量
	 * 
	 * @param capacity
	 */
	private void expandCapacity(int capacity) {
		if (_sprites.length < capacity) {
			ISprite[] bagArray = new ISprite[capacity];
			System.arraycopy(_sprites, 0, bagArray, 0, _size);
			_sprites = bagArray;
		}
	}

	/**
	 * 压缩当前集合容量
	 * 
	 * @param capacity
	 */
	protected void compressCapacity(int capacity) {
		if (capacity + this._size < _sprites.length) {
			final ISprite[] newArray = new ISprite[this._size + capacity];
			System.arraycopy(_sprites, 0, newArray, 0, this._size);
			_sprites = newArray;
		}
	}

	/**
	 * 查找指定位置的精灵对象
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public ISprite find(int x, int y) {
		if (_closed) {
			return null;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite child = _sprites[i];
			if (child != null && child.isVisible()) {
				RectBox rect = child.getCollisionBox();
				if (rect != null && rect.contains(x, y)) {
					return child;
				}
			}
		}
		return null;
	}

	/**
	 * 查找指定名称的精灵对象
	 * 
	 * @param name
	 * @return
	 */
	public ISprite find(String name) {
		if (_closed || name == null) {
			return null;
		}
		rebuildNameCache();
		TArray<ISprite> list = _nameCache.get(name);
		if (list != null && list.size > 0) {
			return list.peek();
		}
		return null;
	}

	/**
	 * 按照上一个精灵的x,y位置,另起一行添加精灵,并偏移指定位置
	 * 
	 * @param spr
	 * @param offX
	 * @param offY
	 * @return
	 */
	public ISprite addPadding(ISprite spr, float offX, float offY) {
		return addPadding(spr, offX, offY, LAYOUT_PADDING);
	}

	/**
	 * 按照上一个精灵的y轴,另起一行添加精灵
	 * 
	 * @param spr
	 * @return
	 */
	public ISprite addCol(ISprite spr) {
		return addPadding(spr, 0, 0, LAYOUT_COL);
	}

	/**
	 * 按照上一个精灵的y轴,另起一行添加精灵,并让y轴偏移指定位置
	 * 
	 * @param spr
	 * @param offY
	 * @return
	 */
	public ISprite addCol(ISprite spr, float offY) {
		return addPadding(spr, 0, offY, LAYOUT_COL);
	}

	/**
	 * 按照上一个精灵的x轴,另起一行添加精灵
	 * 
	 * @param spr
	 * @return
	 */
	public ISprite addRow(ISprite spr) {
		return addPadding(spr, 0, 0, LAYOUT_ROW);
	}

	/**
	 * 按照上一个精灵的x轴,另起一行添加精灵,并将x轴偏移指定位置
	 * 
	 * @param spr
	 * @param offX
	 * @return
	 */
	public ISprite addRow(ISprite spr, float offX) {
		return addPadding(spr, offX, 0, LAYOUT_ROW);
	}

	/**
	 * 按照上一个精灵的x,y位置,另起一行添加精灵,并偏移指定位置
	 * 
	 * @param spr
	 * @param offX
	 * @param offY
	 * @param code
	 * @return
	 */
	public ISprite addPadding(ISprite spr, float offX, float offY, int code) {
		if (_closed || spr == null) {
			return spr;
		}
		float maxX = 0, maxY = 0;
		ISprite tag = null;
		if (_size == 1) {
			ISprite cp = _sprites[0];
			if (cp != null && cp.getY() >= _newLineHeight) {
				maxX = cp.getX();
				maxY = cp.getY();
				tag = cp;
			}
		} else {
			for (int i = 0; i < _size; i++) {
				ISprite c = _sprites[i];
				if (c != null && c != spr && c.getY() >= _newLineHeight) {
					float oldX = maxX, oldY = maxY;
					maxX = MathUtils.max(maxX, c.getX());
					maxY = MathUtils.max(maxY, c.getY());
					if (oldX != maxX || oldY != maxY) {
						tag = c;
					}
				}
			}
		}
		tag = (tag == null && _size > 0) ? _sprites[_size - 1] : tag;
		if (tag != null && tag != spr) {
			switch (code) {
			case LAYOUT_ROW:
				spr.setLocation(maxX + tag.getWidth() + offX, maxY + offY);
				break;
			case LAYOUT_COL:
				spr.setLocation(offX, maxY + tag.getHeight() + offY);
				break;
			default:
				spr.setLocation(maxX + tag.getWidth() + offX, maxY + tag.getHeight() + offY);
				break;
			}
		} else {
			spr.setLocation(offX, offY);
		}
		add(spr);
		_newLineHeight = spr.getY();
		return spr;
	}

	private void updateViewSize(ISprite sprite) {
		if (sprite == null) {
			return;
		}
		int spriteW = MathUtils.iceil(sprite.getWidth());
		int spriteH = MathUtils.iceil(sprite.getHeight());
		int targetW = MathUtils.max(_width, spriteW);
		int targetH = MathUtils.max(_height, spriteH);
		if (_viewWidth < targetW || _viewHeight < targetH) {
			float screenW = _screen == null ? LSystem.viewSize.width : _screen.getWidth();
			float screenH = _screen == null ? LSystem.viewSize.height : _screen.getHeight();
			setViewWindow(_viewX, _viewY, MathUtils.iceil(MathUtils.max(targetW, screenW)),
					MathUtils.iceil(MathUtils.max(targetH, screenH)));
		}
	}

	/**
	 * 设定指定索引为指定精灵,并替换位置和层级
	 * 
	 * @param idx
	 * @param sprite
	 * @return
	 */
	public boolean set(int idx, ISprite sprite) {
		if (_closed || sprite == null || idx < 0) {
			return false;
		}
		if (idx == _size) {
			return add(sprite);
		}
		if (idx > _size) {
			return false;
		}
		updateViewSize(sprite);
		ISprite oldSpr = _sprites[idx];
		if (oldSpr != null) {
			sprite.setLocation(oldSpr.getX(), oldSpr.getY());
			sprite.setLayer(oldSpr.getLayer());
			oldSpr.setState(State.REMOVED);
			if (oldSpr instanceof IEntity) {
				((IEntity) oldSpr).onDetached();
			}
		}
		_sprites[idx] = sprite;
		sprite.setSprites(this);
		sprite.setState(State.ADDED);
		if (sprite instanceof IEntity) {
			((IEntity) sprite).onAttached();
		}
		_dirtyChildren = true;
		invalidateCache();
		return true;
	}

	/**
	 * 在指定索引新增一个精灵,其余精灵顺序排后
	 * 
	 * @param idx
	 * @param sprite
	 * @return
	 */
	public boolean addAt(int idx, ISprite sprite) {
		if (_closed || sprite == null) {
			return false;
		}
		if (contains(sprite)) {
			return set(idx, sprite);
		}
		idx = MathUtils.min(MathUtils.max(idx, 0), _size);
		if (idx == _size) {
			return add(sprite);
		}
		updateViewSize(sprite);
		if (_size >= _sprites.length) {
			expandCapacity((_size + _sizeExpandCount) * 2);
		}
		System.arraycopy(_sprites, idx, _sprites, idx + 1, _size - idx);
		_sprites[idx] = sprite;
		_size++;
		sprite.setSprites(this);
		sprite.setState(State.ADDED);
		if (sprite instanceof IEntity) {
			((IEntity) sprite).onAttached();
		}
		_dirtyChildren = true;
		invalidateCache();
		return true;
	}

	public Sprites addAt(ISprite child, float x, float y) {
		if (_closed || child == null) {
			return this;
		}
		child.setLocation(x, y);
		add(child);
		return this;
	}

	public ISprite getSprite(int index) {
		if (_closed || index < 0 || index >= _size) {
			return null;
		}
		return _sprites[index];
	}

	public ISprite getRandomSprite() {
		return getRandomSprite(0, _size);
	}

	public ISprite getRandomSprite(int min, int max) {
		if (_closed || _size == 0) {
			return null;
		}
		min = MathUtils.max(0, min);
		max = MathUtils.min(max, _size);
		return _sprites[MathUtils.nextInt(min, max)];
	}

	/**
	 * 返回位于顶部的精灵
	 * 
	 * @return
	 */
	public ISprite getTopSprite() {
		return getSprite(0);
	}

	/**
	 * 返回位于底部的精灵
	 * 
	 * @return
	 */
	public ISprite getBottomSprite() {
		return getSprite(_size - 1);
	}

	/**
	 * 批量且顺序的添加一组精灵
	 * 
	 * @param sprites
	 * @return
	 */
	public Sprites addAll(ISprite... sprites) {
		if (sprites == null) {
			return this;
		}
		for (int i = 0; i < sprites.length; i++) {
			ISprite spr = sprites[i];
			if (spr != null) {
				add(spr);
			}
		}
		return this;
	}

	/**
	 * 顺序添加精灵
	 * 
	 * @param sprite
	 * @return
	 */
	public boolean add(ISprite sprite) {
		if (_closed || sprite == null || contains(sprite)) {
			return false;
		}
		updateViewSize(sprite);
		if (_size >= _sprites.length) {
			expandCapacity((_size + _sizeExpandCount) * 2);
		}
		_sprites[_size++] = sprite;
		sprite.setSprites(this);
		sprite.setState(State.ADDED);
		if (sprite instanceof IEntity) {
			((IEntity) sprite).onAttached();
		}
		_dirtyChildren = true;
		invalidateCache();
		return true;
	}

	/**
	 * 顺序添加精灵
	 * 
	 * @param sprite
	 * @return
	 */
	public Sprites append(ISprite sprite) {
		add(sprite);
		return this;
	}

	/**
	 * 返回一组拥有指定标签的精灵
	 * 
	 * @param tags
	 * @return
	 */
	public TArray<ISprite> findTags(Object... tags) {
		if (_closed) {
			return null;
		}
		TArray<ISprite> list = new TArray<ISprite>();
		final int size = this._size;
		final int len = tags.length;
		final ISprite[] childs = this._sprites;
		for (int j = 0; j < len; j++) {
			final Object tag = tags[j];
			if (tag != null) {
				for (int i = size - 1; i > -1; i--) {
					ISprite child = childs[i];
					if (child != null && (child.getTag() == tag || tag.equals(child.getTag()))) {
						list.add(child);
					}
				}
			}
		}
		return list;
	}

	/**
	 * 返回一组没有指定标签的精灵
	 * 
	 * @param tags
	 * @return
	 */
	public TArray<ISprite> findNotTags(Object... tags) {
		if (_closed) {
			return null;
		}
		TArray<ISprite> list = new TArray<ISprite>();
		final int size = this._size;
		final int len = tags.length;
		final ISprite[] childs = this._sprites;
		for (int j = 0; j < len; j++) {
			final Object tag = tags[j];
			if (tag != null) {
				for (int i = size - 1; i > -1; i--) {
					ISprite child = childs[i];
					if (child != null && !tag.equals(child.getTag())) {
						list.add(child);
					}
				}
			}
		}
		return list;
	}

	/**
	 * 返回一组拥有指定标签的精灵
	 * 
	 * @param flags
	 * @return
	 */
	public TArray<ISprite> findFlags(int... flags) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed) {
			return list;
		}
		final int len = flags.length;
		for (int j = 0; j < len; j++) {
			final int f = flags[j];
			for (int i = _size - 1; i > -1; i--) {
				ISprite child = _sprites[i];
				if (child != null && f == child.getFlagType()) {
					list.add(child);
				}
			}
		}
		return list;
	}

	/**
	 * 返回一组不具备指定标签的精灵
	 * 
	 * @param flags
	 * @return
	 */
	public TArray<ISprite> findNotFlags(int... flags) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed) {
			return list;
		}
		final int len = flags.length;
		for (int j = 0; j < len; j++) {
			final int f = flags[j];
			for (int i = _size - 1; i > -1; i--) {
				ISprite child = _sprites[i];
				if (child != null && f != child.getFlagType()) {
					list.add(child);
				}
			}
		}
		return list;
	}

	/**
	 * 返回一组指定名的精灵
	 * 
	 * @param names
	 * @return
	 */
	public TArray<ISprite> findNames(String... names) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed || names == null) {
			return list;
		}
		rebuildNameCache();
		final int len = names.length;
		for (int j = 0; j < len; j++) {
			final String name = names[j];
			if (name == null) {
				continue;
			}
			TArray<ISprite> cachedList = _nameCache.get(name);
			if (cachedList != null) {
				list.addAll(cachedList);
			}
		}
		return list;
	}

	public TArray<ISprite> findNameContains(String... names) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed || names == null) {
			return list;
		}
		final int len = names.length;
		for (int j = 0; j < len; j++) {
			final String name = names[j];
			if (name == null) {
				continue;
			}
			for (int i = _size - 1; i > -1; i--) {
				ISprite child = _sprites[i];
				if (child != null && child.getName() != null && child.getName().contains(name)) {
					list.add(child);
				}
			}
		}
		return list;
	}

	/**
	 * 返回一组没有指定名的精灵
	 * 
	 * @param names
	 * @return
	 */
	public TArray<ISprite> findNotNames(String... names) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed || names == null) {
			return list;
		}
		final int len = names.length;
		for (int j = 0; j < len; j++) {
			final String name = names[j];
			if (name == null) {
				continue;
			}
			for (int i = _size - 1; i > -1; i--) {
				ISprite child = _sprites[i];
				if (child != null && !name.equals(child.getName())) {
					list.add(child);
				}
			}
		}
		return list;
	}

	/**
	 * 查找符合的Flag整型对象
	 * 
	 * @param flag
	 * @return
	 */
	public TArray<ISprite> findFlagTypes(int flag) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed) {
			return list;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite child = _sprites[i];
			if (child != null && child.getFlagType() == flag) {
				list.add(child);
			}
		}
		return list;
	}

	/**
	 * 查找符合的Flag字符对象
	 * 
	 * @param flag
	 * @return
	 */
	public TArray<ISprite> findFlagObjects(String flag) {
		TArray<ISprite> list = new TArray<ISprite>();
		if (_closed || flag == null) {
			return list;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite child = _sprites[i];
			if (child != null && flag.equals(child.getObjectFlag())) {
				list.add(child);
			}
		}
		return list;
	}

	/**
	 * 检查指定精灵是否存在
	 * 
	 * @param sprite
	 * @return
	 */
	public boolean contains(ISprite sprite) {
		if (_closed || sprite == null) {
			return false;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite sp = _sprites[i];
			if (sp == null) {
				continue;
			}
			if (sp == sprite || sp.equals(sprite)) {
				return true;
			}
			if (sp instanceof IEntity) {
				IEntity entity = (IEntity) sp;
				for (int j = 0; j < entity.getChildCount(); j++) {
					IEntity child = entity.getChildByIndex(j);
					if (child != null && (child == sprite || child.equals(sprite))) {
						return true;
					}
				}
			} else if (sp instanceof Sprite) {
				Sprite spr = (Sprite) sp;
				for (int j = 0; j < spr.getChildCount(); j++) {
					ISprite child = spr.getChildByIndex(j);
					if (child != null && (child == sprite || child.equals(sprite))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * 返回指定位置内的所有精灵
	 * 
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @return
	 */
	public TArray<ISprite> contains(float x, float y, float w, float h) {
		TArray<ISprite> sprites = new TArray<ISprite>();
		if (_closed) {
			return sprites;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite sp = _sprites[i];
			if (sp != null && sp.inContains(x, y, w, h)) {
				sprites.add(sp);
			}
		}
		return sprites;
	}

	/**
	 * 返回包含指定位置的所有精灵
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public TArray<ISprite> contains(float x, float y) {
		return contains(x, y, 1f, 1f);
	}

	/**
	 * 返回包含指定精灵位置的所有精灵
	 * 
	 * @param sprite
	 * @return
	 */
	public TArray<ISprite> containsSprite(ISprite sprite) {
		if (sprite == null) {
			return new TArray<ISprite>();
		}
		return contains(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
	}

	/**
	 * 交换两个精灵位置
	 * 
	 * @param first
	 * @param second
	 * @return
	 */
	public Sprites swapSprite(int first, int second) {
		if (_closed || first == second || first >= _size || second >= _size) {
			return this;
		}
		ISprite temp = _sprites[first];
		_sprites[first] = _sprites[second];
		_sprites[second] = temp;
		_dirtyChildren = true;
		invalidateCache();
		return this;
	}

	/**
	 * 交换两个精灵位置
	 * 
	 * @param first
	 * @param second
	 * @return
	 */
	public Sprites swapSprite(ISprite first, ISprite second) {
		if (_closed || first == null || second == null || first == second) {
			return this;
		}
		int fi = -1, bi = -1;
		for (int i = 0; i < _size; i++) {
			if (_sprites[i] == first) {
				fi = i;
			}
			if (_sprites[i] == second) {
				bi = i;
			}
			if (fi != -1 && bi != -1) {
				break;
			}
		}
		return (fi != -1 && bi != -1) ? swapSprite(fi, bi) : this;
	}

	public Sprites swapSprite(String first, String second) {
		if (_closed || first == null || second == null || first.equals(second)) {
			return this;
		}
		int fi = -1, bi = -1;
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null && first.equals(spr.getName())) {
				fi = i;
			}
			if (spr != null && second.equals(spr.getName())) {
				bi = i;
			}
			if (fi != -1 && bi != -1) {
				break;
			}
		}
		return (fi != -1 && bi != -1) ? swapSprite(fi, bi) : this;
	}

	/**
	 * 返回指定位置内的所有精灵
	 * 
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @return
	 */
	public TArray<ISprite> intersects(float x, float y, float w, float h) {
		if (_closed) {
			return null;
		}
		if (_sprites == null) {
			return null;
		}
		final TArray<ISprite> sprites = new TArray<ISprite>();
		final int size = this._size;
		final ISprite[] sprs = this._sprites;
		for (int i = 0; i < size; i++) {
			ISprite sp = sprs[i];
			if (sp != null) {
				if (sp.getCollisionBox().intersects(x, y, w, h)) {
					sprites.add(sp);
				}
			}
		}
		return sprites;
	}

	/**
	 * 返回与指定位置相交的所有精灵
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public TArray<ISprite> intersects(float x, float y) {
		return intersects(x, y, 1f, 1f);
	}

	/**
	 * 返回与指定精灵位置相交的所有精灵
	 * 
	 * @param sprite
	 * @return
	 */
	public TArray<ISprite> intersectsSprite(ISprite sprite) {
		if (sprite == null) {
			return new TArray<ISprite>();
		}
		return intersects(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight());
	}

	/**
	 * 删除指定索引处精灵
	 * 
	 * @param index
	 * @return
	 */
	public ISprite remove(int index) {
		if (_closed || index < 0 || index >= _size) {
			return null;
		}
		ISprite removed = _sprites[index];
		if (removed != null) {
			removed.setState(State.REMOVED);
			if (removed instanceof IEntity) {
				((IEntity) removed).onDetached();
			}
			if (removed instanceof ActionBind) {
				ActionControl.get().removeAllActions((ActionBind) removed);
			}
		}
		int newIndex = _size - 1;
		if (index != newIndex) {
			_sprites[index] = _sprites[newIndex];
		}
		_sprites[newIndex] = null;
		_size--;
		_dirtyChildren = true;
		invalidateCache();
		return removed;
	}

	/**
	 * 清空所有精灵
	 */
	public Sprites removeAll() {
		if (_closed || _size == 0) {
			return this;
		}
		if (_size != 0) {
			clear();
			_sprites = new ISprite[DEFAULT_INIT_CAPACITY];
		}
		return this;
	}

	/**
	 * 删除指定精灵
	 * 
	 * @param sprite
	 * @return
	 */
	public boolean remove(ISprite sprite) {
		if (_closed || sprite == null || _size == 0) {
			return false;
		}
		for (int i = 0; i < _size; i++) {
			if (_sprites[i] == sprite) {
				remove(i);
				return true;
			}
		}
		return false;
	}

	/**
	 * 删除alpha大于或小于指定范围的精灵
	 * 
	 * @param spr
	 * @param more
	 * @param limit
	 * @return
	 */
	public boolean removeWhenAlpha(ISprite spr, boolean more, float limit) {
		if (_size == 0 || spr == null) {
			return false;
		}
		boolean match = more ? spr.getAlpha() >= limit : spr.getAlpha() <= limit;
		return match && remove(spr);
	}

	/**
	 * 删除指定名称的精灵
	 * 
	 * @param name
	 * @return
	 */
	public boolean removeName(String name) {
		if (_closed || name == null || _size == 0) {
			return false;
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null && name.equals(spr.getName())) {
				remove(i);
				return true;
			}
		}
		return false;
	}

	/**
	 * 删除指定范围内精灵
	 * 
	 * @param startIndex
	 * @param endIndex
	 */
	public Sprites remove(int startIndex, int endIndex) {
		if (_closed || startIndex < 0 || endIndex > _size || startIndex >= endIndex) {
			return this;
		}
		int removeCount = endIndex - startIndex;
		for (int i = startIndex; i < endIndex; i++) {
			ISprite spr = _sprites[i];
			if (spr != null) {
				spr.setState(State.REMOVED);
				if (spr instanceof IEntity) {
					((IEntity) spr).onDetached();
				}
				// 删除精灵同时，删除缓动动画
				if (spr instanceof ActionBind) {
					ActionControl.get().removeAllActions((ActionBind) spr);
				}
			}
		}
		System.arraycopy(_sprites, endIndex, _sprites, startIndex, _size - endIndex);
		for (int i = _size - removeCount; i < _size; i++) {
			_sprites[i] = null;
		}
		_size -= removeCount;
		if (_size == 0) {
			_sprites = new ISprite[DEFAULT_INIT_CAPACITY];
		}
		_dirtyChildren = true;
		invalidateCache();
		return this;
	}

	public PointI getMinPos() {
		PointI p = new PointI(Integer.MAX_VALUE, Integer.MAX_VALUE);
		if (_closed || _size == 0)
			return new PointI(0, 0);
		for (int i = 0; i < _size; i++) {
			ISprite sprite = _sprites[i];
			if (sprite != null) {
				p.x = MathUtils.min(p.x, sprite.x());
				p.y = MathUtils.min(p.y, sprite.y());
			}
		}
		return p;
	}

	public PointI getMaxPos() {
		PointI p = new PointI(Integer.MIN_VALUE, Integer.MIN_VALUE);
		if (_closed || _size == 0)
			return new PointI(0, 0);
		for (int i = 0; i < _size; i++) {
			ISprite sprite = _sprites[i];
			if (sprite != null) {
				p.x = MathUtils.max(p.x, sprite.x());
				p.y = MathUtils.max(p.y, sprite.y());
			}
		}
		return p;
	}

	/**
	 * 清空碰撞缓存
	 */
	private void clearCollisionCache() {
		_collisionBoxCache.clear();
		_collisionDebounceCache.clear();
	}

	/**
	 * 碰撞缓存
	 * 
	 * @param spr
	 * @return
	 */
	private RectBox getCachedCollisionBox(ISprite spr) {
		if (spr == null || !spr.isVisible()) {
			return null;
		}
		RectBox box = _collisionBoxCache.get(spr);
		if (box == null) {
			box = spr.getCollisionBox();
			if (box != null) {
				_collisionBoxCache.put(spr, box);
			}
		}
		return box;
	}

	/**
	 * 生成碰撞防抖Key
	 */
	private int getCollisionKey(ISprite a, ISprite b) {
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, a);
		hashCode = LSystem.unite(hashCode, b);
		return hashCode;
	}

	/**
	 * 全量碰撞检测
	 */
	protected void checkAllCollisionObjects() {
		if (!_checkAllCollision || _closed) {
			return;
		}
		clearCollisionCache();
		for (int i = 0; i < _size; i++) {
			ISprite src = _sprites[i];
			RectBox srcBox = getCachedCollisionBox(src);
			if (srcBox == null) {
				continue;
			}
			for (int j = i + 1; j < _size; j++) {
				ISprite dst = _sprites[j];
				RectBox dstBox = getCachedCollisionBox(dst);
				if (dstBox == null || src == dst) {
					continue;
				}
				// 缓存两个精灵是否已经碰撞过
				int key = getCollisionKey(src, dst);
				if (_collisionDebounceCache.get(key) != null) {
					continue;
				}
				if (srcBox.collided(dstBox)) {
					_collisionDebounceCache.put(key, Boolean.TRUE);
					correctSpritePosition(src, dst);
					onTriggerCollision(src, dst);
				}
			}
		}
	}

	/**
	 * 视图范围内碰撞检测
	 */
	protected void checkViewCollisionObjects() {
		checkViewCollisionObjects(0f, 0f);
	}

	/**
	 * 视图范围内碰撞检测
	 * 
	 * @param x X偏移
	 * @param y Y偏移
	 */
	protected void checkViewCollisionObjects(float x, float y) {
		if (!_checkViewCollision || _closed) {
			return;
		}
		clearCollisionCache();
		float minX = _isViewWindowSet ? x + _viewX : x;
		float minY = _isViewWindowSet ? y + _viewY : y;
		float maxX = minX + (_isViewWindowSet ? _viewWidth : _width);
		float maxY = minY + (_isViewWindowSet ? _viewHeight : _height);
		_collViewSize.setBounds(minX, minY, maxX, maxY);
		if (_collisionObjects == null) {
			_collisionObjects = new TArray<ISprite>();
		} else {
			_collisionObjects.clear();
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null && spr.isVisible()) {
				RectBox box = getCachedCollisionBox(spr);
				if (box != null && _collViewSize.collided(box)) {
					_collisionObjects.add(spr);
				}
			}
		}
		int collSize = _collisionObjects.size;
		for (int i = 0; i < collSize; i++) {
			ISprite src = (ISprite) _collisionObjects.get(i);
			RectBox srcBox = getCachedCollisionBox(src);
			if (srcBox == null)
				continue;

			for (int j = i + 1; j < collSize; j++) {
				ISprite dst = (ISprite) _collisionObjects.get(j);
				if (src == dst || !dst.isVisible()) {
					continue;
				}
				RectBox dstBox = getCachedCollisionBox(dst);
				if (dstBox == null) {
					continue;
				}
				int key = getCollisionKey(src, dst);
				if (_collisionDebounceCache.get(key) != null) {
					continue;
				}
				if (srcBox.collided(dstBox)) {
					_collisionDebounceCache.put(key, Boolean.TRUE);
					correctSpritePosition(src, dst);
					onTriggerCollision(src, dst);
				}
			}
		}
	}

	/**
	 * 圆形碰撞检测
	 * 
	 * @param centerX 圆心X
	 * @param centerY 圆心Y
	 * @param radius  半径
	 * @return 碰撞精灵列表
	 */
	public TArray<ISprite> checkCircleCollision(float centerX, float centerY, float radius) {
		TArray<ISprite> result = new TArray<ISprite>();
		if (_closed) {
			return result;
		}
		clearCollisionCache();
		float radiusSq = radius * radius;
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			RectBox box = getCachedCollisionBox(spr);
			if (box == null) {
				continue;
			}
			float cx = box.x + box.width / 2;
			float cy = box.y + box.height / 2;
			float dx = cx - centerX;
			float dy = cy - centerY;
			if (dx * dx + dy * dy <= radiusSq) {
				result.add(spr);
			}
		}
		return result;
	}

	/**
	 * 两个精灵精准碰撞检测
	 * 
	 * @param a 精灵A
	 * @param b 精灵B
	 * @return 是否碰撞
	 */
	public boolean checkSpriteCollision(ISprite a, ISprite b) {
		if (a == null || b == null || a == b || _closed) {
			return false;
		}
		RectBox boxA = getCachedCollisionBox(a);
		RectBox boxB = getCachedCollisionBox(b);
		return boxA != null && boxB != null && boxA.collided(boxB);
	}

	/**
	 * 像素碰撞检测
	 * 
	 * @param a 精灵A
	 * @param b 精灵B
	 * @return 是否碰撞
	 */
	public boolean checkPixelCollision(ISprite a, ISprite b) {
		if (!checkSpriteCollision(a, b)) {
			return false;
		}
		RectBox boxA = getCachedCollisionBox(a);
		RectBox boxB = getCachedCollisionBox(b);
		return boxA != null && boxB != null;
	}

	/**
	 * 碰撞位置修正
	 * 
	 * @param a
	 * @param b
	 */
	private void correctSpritePosition(ISprite a, ISprite b) {
		if (a == null || b == null) {
			return;
		}
		RectBox boxA = getCachedCollisionBox(a);
		RectBox boxB = getCachedCollisionBox(b);
		if (boxA == null || boxB == null) {
			return;
		}
		float overlapX = (boxA.width + boxB.width) / 2 - MathUtils.abs(a.getX() - b.getX());
		float overlapY = (boxA.height + boxB.height) / 2 - MathUtils.abs(a.getY() - b.getY());
		if (overlapX > 0) {
			a.setLocation(a.getX() + (a.getX() > b.getX() ? overlapX : -overlapX), a.getY());
		}
		if (overlapY > 0) {
			a.setLocation(a.getX(), a.getY() + (a.getY() > b.getY() ? overlapY : -overlapY));
		}
	}

	/**
	 * 碰撞触发回调
	 * 
	 * @param spr
	 * @param dst
	 */
	private void onTriggerCollision(final ISprite spr, final ISprite dst) {
		if (spr == null || dst == null || spr == dst || checkCollisionSkip(spr, dst)) {
			return;
		}
		int dir = Side.getCollisionSide(getCachedCollisionBox(spr), getCachedCollisionBox(dst));
		spr.onCollision(dst, dir);
		if (_collisionActionListener != null) {
			_collisionActionListener.onCollision(spr, dst, dir);
		}
	}

	/**
	 * 碰撞过滤检测
	 * 
	 * @param spr
	 * @param dst
	 * @return
	 */
	private boolean checkCollisionSkip(final ISprite spr, final ISprite dst) {
		if (_collisionIgnoreTypes != null && (_collisionIgnoreTypes.contains(spr.getFlagType())
				|| _collisionIgnoreTypes.contains(dst.getFlagType()))) {
			return true;
		}
		return _collisionIgnoreStrings != null && (_collisionIgnoreStrings.contains(spr.getObjectFlag())
				|| _collisionIgnoreStrings.contains(dst.getObjectFlag()));
	}

	public void onTriggerCollisions() {
		if (_checkAllCollision) {
			checkAllCollisionObjects();
		} else if (_checkViewCollision) {
			checkViewCollisionObjects();
		}
	}

	public Sprites collidable() {
		return setCheckAllCollision(true);
	}

	public Sprites disableCollidable() {
		return setCheckAllCollision(false);
	}

	public Sprites collidableView() {
		return setCheckViewCollision(true);
	}

	public Sprites disableCollidableView() {
		return setCheckViewCollision(false);
	}

	public boolean isCheckAllCollision() {
		return _checkAllCollision;
	}

	public Sprites setCheckAllCollision(boolean c) {
		this._checkAllCollision = c;
		return this;
	}

	public boolean isCheckViewCollision() {
		return _checkViewCollision;
	}

	public Sprites setCheckViewCollision(boolean c) {
		this._checkViewCollision = c;
		return this;
	}

	public IntArray getCollisionIgnoreTypes() {
		return _collisionIgnoreTypes == null ? new IntArray() : _collisionIgnoreTypes.cpy();
	}

	public Sprites addCollisionIgnoreType(int t) {
		if (_collisionIgnoreTypes == null) {
			_collisionIgnoreTypes = new IntArray();
		}
		if (!_collisionIgnoreTypes.contains(t)) {
			_collisionIgnoreTypes.add(t);
		}
		return this;
	}

	public boolean removeCollisionIgnoreType(int t) {
		return _collisionIgnoreTypes != null && _collisionIgnoreTypes.removeValue(t);
	}

	public TArray<String> getCollisionIgnoreStrings() {
		final TArray<String> result = new TArray<String>(_collisionIgnoreStrings.size());
		for (Iterator<String> it = _collisionIgnoreStrings.keys(); it.hasNext();) {
			final String key = it.next();
			if (key != null) {
				result.add(key);
			}
		}
		return result;
	}

	public Sprites addCollisionIgnoreString(String t) {
		if (_collisionIgnoreStrings == null) {
			_collisionIgnoreStrings = new ObjectSet<String>();
		}
		_collisionIgnoreStrings.add(t);
		return this;
	}

	public boolean removeCollisionIgnoreString(String t) {
		return _collisionIgnoreStrings != null && _collisionIgnoreStrings.remove(t);
	}

	public Sprites triggerCollision(CollisionAction<ISprite> c) {
		setCheckAllCollision(c != null);
		setCollisionAction(c);
		return this;
	}

	public Sprites viewCollision(CollisionAction<ISprite> c) {
		return triggerViewCollision(c);
	}

	public Sprites triggerViewCollision(CollisionAction<ISprite> c) {
		setCheckViewCollision(c != null);
		setCollisionAction(c);
		return this;
	}

	public Sprites setCollisionAction(CollisionAction<ISprite> c) {
		this._collisionActionListener = c;
		return this;
	}

	public CollisionAction<ISprite> getCollisionAction() {
		return _collisionActionListener;
	}

	public Sprites sortSkew25XY() {
		if (_closed || _size <= 1 || !_sortableChildren || !_dirtyChildren) {
			return this;
		}
		if (_sprites.length != _size) {
			ISprite[] sprs = CollectionUtils.copyOf(_sprites, _size);
			SortUtils.defaultSort(sprs, spriteSkew25Sorter);
			_sprites = sprs;
		} else {
			SortUtils.defaultSort(_sprites, spriteSkew25Sorter);
		}
		_dirtyChildren = false;
		invalidateCache();
		return this;
	}

	public Sprites setAutoSortSkew25XY(boolean sort) {
		_autoSortSkew25 = sort;
		return this;
	}

	public boolean isAutoSortSkew25XY() {
		return _autoSortSkew25;
	}

	public boolean checkAdd(ISprite spr, QueryEvent<ISprite> e) {
		return e != null && e.hit(spr) && add(spr);
	}

	public boolean checkRemove(ISprite spr, QueryEvent<ISprite> e) {
		return e != null && e.hit(spr) && remove(spr);
	}

	/**
	 * 刷新事务
	 * 
	 * @param elapsedTime
	 */
	public void update(long elapsedTime) {
		if (!_visible || _closed) {
			return;
		}
		boolean hasListener = _sprListener != null;
		for (int i = _size - 1; i > -1; i--) {
			ISprite child = _sprites[i];
			if (child == null)
				continue;
			try {
				child.update(elapsedTime);
				if (hasListener) {
					_sprListener.update(child);
				}
				if (_autoSortLayer) {
					_currentPosHash = LSystem.unite(_currentPosHash, child.getX());
					_currentPosHash = LSystem.unite(_currentPosHash, child.getY());
					_currentPosHash = LSystem.unite(_currentPosHash, child.getOffsetX());
					_currentPosHash = LSystem.unite(_currentPosHash, child.getOffsetY());
				}
			} catch (Throwable e) {
				LSystem.error("Sprites update sprite exception: " + child.getName(), e);
			}
		}
		if (_autoSortLayer && _currentPosHash != _lastPosHash && _size > 1) {
			if (_sprites.length != _size) {
				ISprite[] sprs = CollectionUtils.copyOf(_sprites, _size);
				spriteXYSorter.sort(sprs);
				_sprites = sprs;
			} else {
				spriteXYSorter.sort(_sprites);
			}
			_lastPosHash = _currentPosHash;
			invalidateCache();
		}
		onTriggerCollisions();
	}

	public Light2D createGlobalLight(LightType lt) {
		if (lt == null) {
			_useLight = false;
			return null;
		}
		_light = (_light == null) ? new Light2D(lt) : _light;
		_light.updateLightType(lt);
		_useLight = true;
		return _light;
	}

	public boolean isGlobalLight() {
		return _useLight;
	}

	public Light2D getGlobalLight() {
		return _light;
	}

	public boolean isShaderMask() {
		return _useShaderMask;
	}

	public ShaderMask getShaderMask() {
		return _shaderMask;
	}

	public Sprites setShaderMask(ShaderMask mask) {
		_shaderMask = mask;
		_useShaderMask = _shaderMask != null;
		return this;
	}

	public void paint(final GLEx g, final float minX, final float minY, final float maxX, final float maxY) {
		paint(g, 0f, 0f, minX, minY, maxX, maxY);
	}

	public void paint(final GLEx g, final float offsetX, final float offsetY, final float minX, final float minY,
			final float maxX, final float maxY) {
		if (!_visible || _closed) {
			return;
		}
		sortSprites();
		renderSprites(g, offsetX, offsetY, minX, minY, maxX, maxY, true);
	}

	public void paintPos(final GLEx g, final float offsetX, final float offsetY) {
		if (!_visible || _closed) {
			return;
		}
		sortSprites();
		renderSprites(g, offsetX, offsetY, 0, 0, 0, 0, false);
	}

	/**
	 * 创建UI图像
	 * 
	 * @param g
	 */
	public void createUI(final GLEx g) {
		createUI(g, 0, 0);
	}

	/**
	 * 创建UI图像
	 * 
	 * @param g
	 */
	public void createUI(final GLEx g, final int x, final int y) {
		if (!_visible || _closed) {
			return;
		}
		sortSprites();
		final float scrollX = _scrollX, scrollY = _scrollY;
		final float startX = MathUtils.scroll(scrollX, _width);
		final float startY = MathUtils.scroll(scrollY, _height);

		final float minX = _isViewWindowSet ? x + _viewX : x;
		final float minY = _isViewWindowSet ? y + _viewY : y;
		final float maxX = minX + (_isViewWindowSet ? _viewWidth : _width);
		final float maxY = minY + (_isViewWindowSet ? _viewHeight : _height);

		try {
			afterSaveToBuffer(g);
			if (startX != 0 || startY != 0) {
				g.translate(startX, startY);
			}
			if (minX != 0 || minY != 0) {
				g.translate(minX, minY);
			}
			renderSprites(g, 0, 0, minX, minY, maxX, maxY, true);
		} finally {
			if (minX != 0 || minY != 0) {
				g.translate(-minX, -minY);
			}
			if (startX != 0 || startY != 0) {
				g.translate(-startX, -startY);
			}
			beforeSaveToBuffer(g);
		}
	}

	private void renderSprites(GLEx g, float offsetX, float offsetY, float minX, float minY, float maxX, float maxY,
			boolean checkView) {
		boolean useLight = _useLight && _light != null && !_light.isClosed();
		boolean useMask = _useShaderMask && _shaderMask != null;
		if (useLight) {
			_light.setAutoTouchTimer(_screen.getTouchX(), _screen.getTouchY(), _screen.getCurrentTimer());
			_light.getMask().pushBatch(g);
		} else if (useMask) {
			_shaderMask.pushBatch(g);
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr == null || !spr.isVisible()) {
				continue;
			}
			if (checkView && _limitViewWindows) {
				float sx = minX + spr.getX();
				float sy = minY + spr.getY();
				float sw = spr.getWidth();
				float sh = spr.getHeight();
				if (sx + sw < minX || sx > maxX || sy + sh < minY || sy > maxY) {
					continue;
				}
			}
			// 绘制阴影
			if (_createShadow && _spriteShadow != null && spr.showShadow()) {
				_spriteShadow.drawShadow(g, spr, offsetX, offsetY);
			}
			spr.createUI(g, offsetX, offsetY);
		}

		if (useLight) {
			_light.getMask().popBatch(g);
		} else if (useMask) {
			_shaderMask.popBatch(g);
		}
	}

	public Sprites addEntityGroup(int count) {
		for (int i = 0; i < count; i++) {
			add(new Entity());
		}
		return this;
	}

	public Sprites addEntityGroup(LTexture tex, int count) {
		for (int i = 0; i < count; i++) {
			add(new Entity(tex));
		}
		return this;
	}

	public Sprites addEntityGroup(String path, int count) {
		for (int i = 0; i < count; i++) {
			add(new Entity(path));
		}
		return this;
	}

	public Sprites addEntityGroup(Created<? extends IEntity> s, int count) {
		if (s == null) {
			return this;
		}
		for (int i = 0; i < count; i++) {
			add(s.make());
		}
		return this;
	}

	public Sprites addSpriteGroup(int count) {
		for (int i = 0; i < count; i++) {
			add(new Sprite());
		}
		return this;
	}

	public Sprites addSpriteGroup(LTexture tex, int count) {
		for (int i = 0; i < count; i++) {
			add(new Sprite(tex));
		}
		return this;
	}

	public Sprites addSpriteGroup(String path, int count) {
		for (int i = 0; i < count; i++) {
			add(new Sprite(path));
		}
		return this;
	}

	public Sprites addSpriteGroup(Created<? extends ISprite> s, int count) {
		if (s == null) {
			return this;
		}
		for (int i = 0; i < count; i++) {
			add(s.make());
		}
		return this;
	}

	public ISprite addSprite(final String name, final TArray<TComponent<ISprite>> comps) {
		Sprite newSprite = new Sprite();
		newSprite.setName(name);
		for (int i = 0; i < comps.size; i++) {
			TComponent<ISprite> t = comps.get(i);
			if (t != null) {
				newSprite.addComponent(t);
			}
		}
		add(newSprite);
		return newSprite;
	}

	public ISprite addSprite(final String name, final String imagePath, final TArray<TComponent<ISprite>> comps) {
		Sprite newSprite = new Sprite(imagePath);
		newSprite.setName(name);
		for (int i = 0; i < comps.size; i++) {
			TComponent<ISprite> t = comps.get(i);
			if (t != null)
				newSprite.addComponent(t);
		}
		add(newSprite);
		return newSprite;
	}

	public ISprite addSprite(final String name, final LTexture tex, final TArray<TComponent<ISprite>> comps) {
		Sprite newSprite = new Sprite(tex);
		newSprite.setName(name);
		for (int i = 0; i < comps.size; i++) {
			TComponent<ISprite> t = comps.get(i);
			if (t != null)
				newSprite.addComponent(t);
		}
		add(newSprite);
		return newSprite;
	}

	@SuppressWarnings("unchecked")
	public ISprite addSprite(final String name, final TComponent<ISprite>... comps) {
		Sprite newSprite = new Sprite();
		newSprite.setName(name);
		for (int i = 0; i < comps.length; i++) {
			TComponent<ISprite> t = comps[i];
			if (t != null) {
				newSprite.addComponent(t);
			}
		}
		add(newSprite);
		return newSprite;
	}

	@SuppressWarnings("unchecked")
	public ISprite addSprite(final String name, final String imagePath, final TComponent<ISprite>... comps) {
		Sprite newSprite = new Sprite(imagePath);
		newSprite.setName(name);
		for (int i = 0; i < comps.length; i++) {
			TComponent<ISprite> t = comps[i];
			if (t != null) {
				newSprite.addComponent(t);
			}
		}
		add(newSprite);
		return newSprite;
	}

	@SuppressWarnings("unchecked")
	public ISprite addSprite(final String name, final LTexture tex, final TComponent<ISprite>... comps) {
		Sprite newSprite = new Sprite(tex);
		newSprite.setName(name);
		for (int i = 0; i < comps.length; i++) {
			TComponent<ISprite> t = comps[i];
			if (t != null) {
				newSprite.addComponent(t);
			}
		}
		add(newSprite);
		return newSprite;
	}

	public IEntity addEntity(final String name, final TArray<TComponent<IEntity>> comps) {
		Entity newEntity = new Entity();
		newEntity.setName(name);
		for (int i = 0; i < comps.size; i++) {
			TComponent<IEntity> t = comps.get(i);
			if (t != null) {
				newEntity.addComponent(t);
			}
		}
		add(newEntity);
		return newEntity;
	}

	public IEntity addEntity(final String name, final String imagePath, final TArray<TComponent<IEntity>> comps) {
		Entity newEntity = new Entity(imagePath);
		newEntity.setName(name);
		for (int i = 0; i < comps.size; i++) {
			TComponent<IEntity> t = comps.get(i);
			if (t != null) {
				newEntity.addComponent(t);
			}
		}
		add(newEntity);
		return newEntity;
	}

	public IEntity addEntity(final String name, final LTexture tex, final TArray<TComponent<IEntity>> comps) {
		Entity newEntity = new Entity(tex);
		newEntity.setName(name);
		for (int i = 0; i < comps.size; i++) {
			TComponent<IEntity> t = comps.get(i);
			if (t != null) {
				newEntity.addComponent(t);
			}
		}
		add(newEntity);
		return newEntity;
	}

	@SuppressWarnings("unchecked")
	public IEntity addEntity(final String name, final TComponent<IEntity>... comps) {
		Entity newEntity = new Entity();
		newEntity.setName(name);
		for (int i = 0; i < comps.length; i++) {
			TComponent<IEntity> t = comps[i];
			if (t != null) {
				newEntity.addComponent(t);
			}
		}
		add(newEntity);
		return newEntity;
	}

	@SuppressWarnings("unchecked")
	public IEntity addEntity(final String name, final String imagePath, final TComponent<IEntity>... comps) {
		Entity newEntity = new Entity(imagePath);
		newEntity.setName(name);
		for (int i = 0; i < comps.length; i++) {
			TComponent<IEntity> t = comps[i];
			if (t != null) {
				newEntity.addComponent(t);
			}
		}
		add(newEntity);
		return newEntity;
	}

	@SuppressWarnings("unchecked")
	public IEntity addEntity(final String name, final LTexture tex, final TComponent<IEntity>... comps) {
		Entity newEntity = new Entity(tex);
		newEntity.setName(name);
		for (int i = 0; i < comps.length; i++) {
			TComponent<IEntity> t = comps[i];
			if (t != null) {
				newEntity.addComponent(t);
			}
		}
		add(newEntity);
		return newEntity;
	}

	public float getX() {
		return _viewX;
	}

	public float getY() {
		return _viewY;
	}

	public float getStageX() {
		return getX() - getScreenX();
	}

	public float getStageY() {
		return getY() - getScreenY();
	}

	/**
	 * 设定精灵集合在屏幕中的位置与大小
	 * 
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 */
	public Sprites setViewWindow(int x, int y, int width, int height) {
		_isViewWindowSet = true;
		_viewX = x;
		_viewY = y;
		_viewWidth = MathUtils.max(width, 1);
		_viewHeight = MathUtils.max(height, 1);
		return this;
	}

	/**
	 * 设定精灵集合在屏幕中的位置
	 * 
	 * @param x
	 * @param y
	 */
	public Sprites setLocation(int x, int y) {
		_isViewWindowSet = true;
		_viewX = x;
		_viewY = y;
		_viewWidth = MathUtils.max(_viewWidth, getWidth());
		_viewHeight = MathUtils.max(_viewHeight, getHeight());
		return this;
	}

	/**
	 * 创建对应当前精灵集合的精灵控制器
	 * 
	 * @return
	 */
	public SpriteControls createSpriteControls() {
		return _closed ? new SpriteControls() : new SpriteControls(getSprites());
	}

	public SpriteControls controls() {
		return createSpriteControls();
	}

	public SpriteControls createSpriteControls(TArray<ISprite> sprites) {
		return sprites == null || sprites.isEmpty() ? createSpriteControls() : new SpriteControls(sprites);
	}

	public SpriteControls controls(TArray<ISprite> sprites) {
		return createSpriteControls(sprites);
	}

	public SpriteControls findNamesToSpriteControls(String... names) {
		return _closed ? new SpriteControls() : new SpriteControls(findNames(names));
	}

	public SpriteControls findNameContainsToSpriteControls(String... names) {
		return _closed ? new SpriteControls() : new SpriteControls(findNameContains(names));
	}

	public SpriteControls findNotNamesToSpriteControls(String... names) {
		return _closed ? new SpriteControls() : new SpriteControls(findNotNames(names));
	}

	public SpriteControls findTagsToSpriteControls(Object... o) {
		return _closed ? new SpriteControls() : new SpriteControls(findTags(o));
	}

	public SpriteControls findNotTagsToSpriteControls(Object... o) {
		return _closed ? new SpriteControls() : new SpriteControls(findNotTags(o));
	}

	public SpriteControls findFlagsToSpriteControls(int... flags) {
		return _closed ? new SpriteControls() : new SpriteControls(findFlags(flags));
	}

	public SpriteControls findNotFlagsToSpriteControls(int... flags) {
		return _closed ? new SpriteControls() : new SpriteControls(findNotFlags(flags));
	}

	/**
	 * 获得当前精灵集合中的全部精灵
	 * 
	 * @return
	 */
	public ISprite[] getSprites() {
		return _closed ? new ISprite[0] : CollectionUtils.copyOf(_sprites, _size);
	}

	/**
	 * 获得当前精灵集合中的全部精灵
	 * 
	 * @return
	 */
	public TArray<ISprite> getSpritesArray() {
		TArray<ISprite> result = new TArray<ISprite>();
		if (_closed)
			return result;
		for (int i = _size - 1; i > -1; i--) {
			ISprite spr = _sprites[i];
			if (spr != null)
				result.add(spr);
		}
		return result;
	}

	/**
	 * 删除符合指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<ISprite> remove(QueryEvent<ISprite> query) {
		TArray<ISprite> result = new TArray<ISprite>();
		if (_closed || query == null) {
			return result;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite sprite = _sprites[i];
			if (sprite != null && query.hit(sprite)) {
				result.add(sprite);
				remove(i);
			}
		}
		return result;
	}

	public TArray<ISprite> removeTagAndFlag(Object tag, int flag) {
		TArray<ISprite> result = new TArray<ISprite>();
		if (_closed || tag == null) {
			return result;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite sprite = _sprites[i];
			if (sprite != null && (tag.equals(sprite.getTag()) && sprite.getFlagType() == flag)) {
				result.add(sprite);
				remove(i);
			}
		}
		return result;
	}

	/**
	 * 查找符合指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<ISprite> find(QueryEvent<ISprite> query) {
		TArray<ISprite> result = new TArray<ISprite>();
		if (_closed || query == null) {
			return result;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite sprite = _sprites[i];
			if (sprite != null && query.hit(sprite)) {
				result.add(sprite);
			}
		}
		return result;
	}

	/**
	 * 删除指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<ISprite> delete(QueryEvent<ISprite> query) {
		return remove(query);
	}

	/**
	 * 查找符合指定条件的精灵并返回操作的集合
	 * 
	 * @param query
	 * @return
	 */
	public TArray<ISprite> select(QueryEvent<ISprite> query) {
		return find(query);
	}

	private void addRect(TArray<RectBox> rects, RectBox child) {
		if (_closed || child == null || child.width <= 1 || child.height <= 1) {
			return;
		}
		if (!rects.contains(child)) {
			rects.add(child);
		}
	}

	private void addAllRect(TArray<RectBox> rects, ISprite spr) {
		if (_closed) {
			return;
		}
		if (spr instanceof Entity) {
			if (spr.isContainer()) {
				final Entity ns = (Entity) spr;
				final TArray<IEntity> childs = ns._childrens;
				final int size = childs.size;
				for (int i = size - 1; i > -1; i--) {
					IEntity cc = childs.get(i);
					if (cc != null) {
						final RectBox rect1 = ns.getCollisionBox();
						final RectBox rect2 = cc.getCollisionBox();
						if (rect1 != null && !rect1.equals(rect2)) {
							addRect(rects, rect1.add(rect2));
						}
					}
				}
			} else {
				addRect(rects, spr.getCollisionBox());
			}
		} else if (spr instanceof Sprite) {
			if (spr.isContainer()) {
				final Sprite ns = (Sprite) spr;
				final TArray<ISprite> childs = ns._childrens;
				final int size = childs.size;
				for (int i = size - 1; i > -1; i--) {
					ISprite cc = childs.get(i);
					if (cc != null) {
						final RectBox rect1 = ns.getCollisionBox();
						final RectBox rect2 = cc.getCollisionBox();
						if (rect1 != null && !rect1.equals(rect2)) {
							addRect(rects, rect1.add(rect2));
						}
					}
				}
			} else {
				addRect(rects, spr.getCollisionBox());
			}
		} else {
			addRect(rects, spr.getCollisionBox());
		}
	}

	public DirtyRectList getDirtyList() {
		_dirtyList.clear();
		if (_closed) {
			return _dirtyList;
		}
		TArray<RectBox> rects = new TArray<RectBox>();
		for (int i = _size - 1; i > -1; i--) {
			addAllRect(rects, _sprites[i]);
		}
		for (int i = 0; i < rects.size(); i++) {
			RectBox rect = rects.get(i);
			if (rect.width > 1 && rect.height > 1) {
				_dirtyList.add(rect);
			}
		}
		return _dirtyList;
	}

	public Sprites setSpritesShadow(ISpritesShadow s) {
		_spriteShadow = s;
		_createShadow = _spriteShadow != null;
		return this;
	}

	public ISpritesShadow shadow() {
		return _spriteShadow;
	}

	public boolean getSpritesShadow() {
		return _createShadow;
	}

	public int getMaxX() {
		int maxX = 0;
		for (int i = _size - 1; i > -1; i--) {
			ISprite comp = _sprites[i];
			if (comp != null) {
				maxX = MathUtils.max(maxX, comp.x());
			}
		}
		return maxX;
	}

	public int getMaxY() {
		int maxY = 0;
		for (int i = _size - 1; i > -1; i--) {
			ISprite comp = _sprites[i];
			if (comp != null) {
				maxY = MathUtils.max(maxY, comp.y());
			}
		}
		return maxY;
	}

	public int getMaxZ() {
		int maxZ = 0;
		for (int i = _size - 1; i > -1; i--) {
			ISprite comp = _sprites[i];
			if (comp != null) {
				maxZ = MathUtils.max(maxZ, comp.getZ());
			}
		}
		return maxZ;
	}

	@Override
	public int size() {
		return _size;
	}

	public RectBox getBoundingBox() {
		return _isViewWindowSet ? new RectBox(_viewX, _viewY, _viewWidth, _viewHeight)
				: new RectBox(_viewX, _viewY, _width, _height);
	}

	public int getHeight() {
		return _height;
	}

	public int getWidth() {
		return _width;
	}

	public Sprites hide() {
		setVisible(false);
		return this;
	}

	public Sprites show() {
		setVisible(true);
		return this;
	}

	@Override
	public boolean isVisible() {
		return _visible;
	}

	@Override
	public void setVisible(boolean v) {
		_visible = v;
	}

	public SpriteListener getSpriteListener() {
		return _sprListener;
	}

	public Sprites setSpriteListener(SpriteListener s) {
		this._sprListener = s;
		return this;
	}

	public Screen getScreen() {
		return _screen;
	}

	public float getScreenX() {
		return _screen == null ? 0 : _screen.getX();
	}

	public float getScreenY() {
		return _screen == null ? 0 : _screen.getY();
	}

	public Sprites scrollBy(float x, float y) {
		_scrollX += x;
		_scrollY += y;
		return this;
	}

	public Sprites scrollTo(float x, float y) {
		_scrollX = x;
		_scrollY = y;
		return this;
	}

	public float scrollX() {
		return _scrollX;
	}

	public float scrollY() {
		return _scrollY;
	}

	public Sprites scrollX(float x) {
		_scrollX = x;
		return this;
	}

	public Sprites scrollY(float y) {
		_scrollY = y;
		return this;
	}

	public Margin margin(boolean vertical, float left, float top, float right, float bottom) {
		float size = vertical ? getHeight() : getWidth();
		if (_closed) {
			return new Margin(size, vertical);
		}
		if (_margin == null) {
			_margin = new Margin(size, vertical);
		} else {
			_margin.setSize(size);
			_margin.setVertical(vertical);
		}
		_margin.setMargin(left, top, right, bottom);
		_margin.clear();
		final int len = this._size;
		final ISprite[] childs = this._sprites;
		for (int i = 0; i < len; i++) {
			ISprite spr = childs[i];
			if (spr != null) {
				_margin.addChild(spr);
			}
		}
		return _margin;
	}

	/**
	 * 遍历Sprites中所有精灵对象并反馈给Callback
	 * 
	 * @param callback
	 */
	public Sprites forChildren(Callback<ISprite> callback) {
		if (callback == null) {
			return this;
		}
		for (int i = _size - 1; i > -1; i--) {
			ISprite child = _sprites[i];
			if (child != null) {
				callback.onSuccess(child);
			}
		}
		return this;
	}

	public boolean isResizabled() {
		return _resizabled;
	}

	public Sprites setResizabled(boolean r) {
		_resizabled = r;
		return this;
	}

	public Sprites resize(float width, float height, boolean forceResize) {
		if (_closed) {
			return this;
		}
		if (_resizabled) {
			if (_resizeListener != null) {
				_resizeListener.onResize(this);
			}
			if (forceResize || (!MathUtils.equal(this._width, width) && !MathUtils.equal(this._height, height))) {
				this._width = (int) width;
				this._height = (int) height;
				final ISprite[] childs = _sprites;
				final int size = _size;
				for (int i = size - 1; i > -1; i--) {
					final ISprite child = childs[i];
					if (child != null) {
						child.onResize();
					}
				}
			}
		}
		return this;
	}

	public ResizeListener<Sprites> getResizeListener() {
		return _resizeListener;
	}

	public Sprites setResizeListener(ResizeListener<Sprites> listener) {
		_resizeListener = listener;
		return this;
	}

	public boolean isLimitViewWindows() {
		return _limitViewWindows;
	}

	public Sprites setLimitViewWindows(boolean limit) {
		_limitViewWindows = limit;
		return this;
	}

	public Sprites rect(RectBox rect) {
		return rect(rect, 0);
	}

	public Sprites rect(RectBox rect, int shift) {
		rect(this, rect, shift);
		return this;
	}

	public Sprites triangle(Triangle2f t) {
		return triangle(t, 1);
	}

	public Sprites triangle(Triangle2f t, int stepRate) {
		triangle(this, t, stepRate);
		return this;
	}

	public Sprites circle(Circle circle) {
		return circle(circle, -1f, -1f);
	}

	public Sprites circle(Circle circle, float startAngle, float endAngle) {
		circle(this, circle, startAngle, endAngle);
		return this;
	}

	public Sprites ellipse(Ellipse e) {
		return ellipse(e, -1f, -1f);
	}

	public Sprites ellipse(Ellipse e, float startAngle, float endAngle) {
		ellipse(this, e, startAngle, endAngle);
		return this;
	}

	public Sprites line(Line e) {
		line(this, e);
		return this;
	}

	public Sprites rotateAround(XY point, float angle) {
		rotateAround(this, point, angle);
		return this;
	}

	public Sprites rotateAroundDistance(XY point, float angle, float distance) {
		rotateAroundDistance(this, point, angle, distance);
		return this;
	}

	public String getName() {
		return _sprites_name;
	}

	@Override
	public boolean isEmpty() {
		return _size == 0;
	}

	@Override
	public boolean isNotEmpty() {
		return !isEmpty();
	}

	public Sprites setAutoYLayer(boolean y) {
		spriteXYSorter.setSortY(y);
		return this;
	}

	public boolean isAutoSortXYLayer() {
		return _autoSortLayer;
	}

	public Sprites setAutoSortXYLayer(boolean sort) {
		_autoSortLayer = sort;
		return this;
	}

	public Sprites centerOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.centerOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites centerTopOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.centerTopOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites centerBottomOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.centerBottomOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites topOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.topOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites topLeftOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.topLeftOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites topRightOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.topRightOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites leftOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.leftOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites rightOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.rightOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites bottomOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.bottomOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites bottomLeftOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.bottomLeftOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites bottomRightOn(final LObject<?> object) {
		if (object == null) {
			return this;
		}
		LObject.bottomRightOn(object, getX(), getY(), getWidth(), getHeight());
		return this;
	}

	public Sprites centerOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		centerOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites centerTopOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		centerTopOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites centerBottomOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		centerBottomOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites topOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		topOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites topLeftOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		topLeftOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites topRightOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		topRightOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites leftOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		leftOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites rightOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		rightOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites bottomOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		bottomOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites bottomLeftOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		bottomLeftOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	public Sprites bottomRightOn(final LObject<?> object, final float offsetX, final float offsetY) {
		if (object == null) {
			return this;
		}
		bottomRightOn(object);
		object.setLocation(object.getX() + offsetX, object.getY() + offsetY);
		return this;
	}

	/**
	 * 删除指定集合中的精灵
	 * 
	 * @param removes
	 * @return
	 */
	public TArray<Boolean> clear(TArray<ISprite> removes) {
		TArray<Boolean> result = new TArray<Boolean>();
		if (_closed || removes == null) {
			return result;
		}
		for (int i = 0; i < removes.size(); i++) {
			ISprite spr = removes.get(i);
			result.add(remove(spr));
		}
		return result;
	}

	/**
	 * 删除指定集合中的精灵
	 * 
	 * @param removes
	 * @return
	 */
	public TArray<Boolean> clear(ISprite... removes) {
		if (_closed || removes == null) {
			return new TArray<Boolean>();
		}
		TArray<Boolean> result = new TArray<Boolean>();
		final int size = removes.length;
		for (int i = size - 1; i > -1; i--) {
			final ISprite sprite = removes[i];
			if (sprite != null) {
				result.add(remove(sprite));
			}
		}
		return result;
	}

	public Sprites setDirtyChildren(boolean v) {
		_dirtyChildren = v;
		return this;
	}

	public boolean isDirtyChildren() {
		return _dirtyChildren;
	}

	public Sprites setLayerTop() {
		return setLayer(Integer.MAX_VALUE);
	}

	public Sprites setLayerBottom() {
		return setLayer(Integer.MIN_VALUE);
	}

	public Sprites setLayer(int z) {
		_indexLayer = z;
		return this;
	}

	public Sprites setZ(int z) {
		return setZOrder(z);
	}

	public Sprites setZOrder(int z) {
		return setLayer(-z);
	}

	public int getZOrder() {
		return MathUtils.abs(getLayer());
	}

	public int getZ() {
		return getZOrder();
	}

	@Override
	public int getLayer() {
		return _indexLayer;
	}

	public Sprites clearListerner() {
		_sprListener = null;
		_resizeListener = null;
		_collisionActionListener = null;
		if (_collisionIgnoreTypes != null) {
			_collisionIgnoreTypes.clear();
			_collisionIgnoreTypes = null;
		}
		if (_collisionIgnoreStrings != null) {
			_collisionIgnoreStrings.clear();
			_collisionIgnoreStrings = null;
		}
		return this;
	}

	public Sprites saveToFrameBuffer(boolean s) {
		_spriteSavetoFrameBuffer = s;
		return this;
	}

	public boolean isSaveFrameBuffer() {
		return _spriteSavetoFrameBuffer;
	}

	private void afterSaveToBuffer(GLEx g) {
		if (!_spriteSavetoFrameBuffer) {
			return;
		}
		if (_spriteFrameBuffer == null || _spriteFrameBuffer.getWidth() != getWidth()
				|| _spriteFrameBuffer.getHeight() != getHeight()) {
			if (_spriteFrameBuffer != null) {
				_spriteFrameBuffer.close();
			}
			_spriteFrameBuffer = new FrameBuffer(getWidth(), getHeight());
		}
		_spriteFrameBuffer.begin(g, -getX(), -getY(), getWidth(), getHeight());
	}

	private void beforeSaveToBuffer(GLEx g) {
		if (!_spriteSavetoFrameBuffer || _spriteFrameBuffer == null) {
			return;
		}
		_spriteFrameBuffer.end(g);
		boolean changeUV = _changeUVTilt && _uvMask != null;
		if (_useFrameBufferShaderMask && _frameBufferShaderMask != null) {
			_frameBufferShaderMask.setViewSize(getWidth(), getHeight());
			_frameBufferShaderMask.update();
			ShaderSource old = g.updateShaderSource(_frameBufferShaderMask.getShader());
			if (changeUV) {
				_spriteFrameBuffer.begin(g);
			}
			g.draw(_spriteFrameBuffer.texture(), _offsetUVx + getX(), _offsetUVy + getY(), Direction.TRANS_FLIP);
			if (changeUV) {
				_spriteFrameBuffer.end(g);
			}
			g.updateShaderSource(old);
		}
		if (changeUV) {
			_uvMask.setViewSize(getWidth(), getHeight());
			_uvMask.update();
			ShaderSource old = g.updateShaderSource(_uvMask.getBilinearShader());
			g.draw(_spriteFrameBuffer.texture(), _offsetUVx + getX(), _offsetUVy + getY());
			g.updateShaderSource(old);
		}
	}

	public Sprites setFBOShaderMask(FBOMask mask) {
		_frameBufferShaderMask = mask;
		_useFrameBufferShaderMask = _frameBufferShaderMask != null;
		saveToFrameBuffer(_useFrameBufferShaderMask);
		return this;
	}

	public FBOMask getFBOShaderMask() {
		return _frameBufferShaderMask;
	}

	public Sprites freeFBOShaderMask() {
		if (_frameBufferShaderMask != null) {
			_frameBufferShaderMask.close();
			_frameBufferShaderMask = null;
		}
		_useFrameBufferShaderMask = false;
		return this;
	}

	public float getOffsetUVx() {
		return _offsetUVx;
	}

	public float getOffsetUVy() {
		return _offsetUVy;
	}

	public Sprites setOffsetUVx(float x) {
		_offsetUVx = x;
		return this;
	}

	public Sprites setOffsetUVy(float y) {
		_offsetUVy = y;
		return this;
	}

	public BilinearMask getUVMask() {
		if (_uvMask == null) {
			_uvMask = new BilinearMask(true, getWidth(), getHeight());
		}
		return _uvMask;
	}

	public Sprites updateUVXTopLeftRight(float topLeft, float topRight) {
		getUVMask().setViewSize(getWidth(), getHeight());
		getUVMask().setXTopLeftRight(topLeft, topRight);
		getUVMask().update();
		setAllowUVChange(true);
		return this;
	}

	public Sprites updateUVYTopLeftRight(float topLeft, float topRight) {
		getUVMask().setViewSize(getWidth(), getHeight());
		getUVMask().setYTopLeftRight(topLeft, topRight);
		getUVMask().update();
		setAllowUVChange(true);
		return this;
	}

	public Sprites setAllowUVChange(boolean a) {
		_changeUVTilt = a;
		saveToFrameBuffer(a);
		return this;
	}

	public boolean isAllowUVChange() {
		return _changeUVTilt;
	}

	public Sprites freeUVMask() {
		_changeUVTilt = false;
		if (_uvMask != null)
			_uvMask = null;
		return this;
	}

	public Sprites freeMask() {
		if (_shaderMask != null) {
			_useShaderMask = false;
			_shaderMask.close();
			_shaderMask = null;
		}
		return this;
	}

	public Sprites freeFrameBuffer() {
		if (_spriteFrameBuffer != null) {
			_spriteFrameBuffer.close();
			_spriteFrameBuffer = null;
		}
		_spriteSavetoFrameBuffer = false;
		return this;
	}

	public FrameBuffer getFrameBuffer() {
		return _spriteFrameBuffer;
	}

	@Override
	public Iterator<ISprite> iterator() {
		return new Iterator<ISprite>() {

			private int index = 0;
			private int expectedSize = _size;

			@Override
			public boolean hasNext() {
				return index < _size;
			}

			@Override
			public ISprite next() {
				if (_size != expectedSize) {
					throw new LSysException("Sprites modified during iteration");
				}
				if (!hasNext()) {
					return null;
				}
				return _sprites[index++];
			}

			@Override
			public void remove() {
				Sprites.this.remove(--index);
				_size--;
				expectedSize = _size;
			}
		};
	}

	public Sprites moveUp(ISprite sprite) {
		int idx = indexOf(sprite);
		if (idx > 0) {
			swapSprite(idx, idx - 1);
		}
		return this;
	}

	public Sprites moveDown(ISprite sprite) {
		int idx = indexOf(sprite);
		if (idx != -1 && idx < _size - 1) {
			swapSprite(idx, idx + 1);
		}
		return this;
	}

	public int indexOf(ISprite sprite) {
		for (int i = 0; i < _size; i++) {
			if (_sprites[i] == sprite) {
				return i;
			}
		}
		return -1;
	}

	public Sprites batchSetVisible(boolean visible, ISprite... sprites) {
		for (int i = 0; i < sprites.length; i++) {
			ISprite spr = sprites[i];
			if (spr != null) {
				spr.setVisible(visible);
			}
		}
		return this;
	}

	public Sprites batchSetLocation(float x, float y, ISprite... sprites) {
		for (int i = 0; i < sprites.length; i++) {
			ISprite spr = sprites[i];
			if (spr != null) {
				spr.setLocation(x, y);
			}
		}
		return this;
	}

	public ISprite safeFind(String name) {
		try {
			return find(name);
		} catch (Exception e) {
			return null;
		}
	}

	public void queryEach(QueryEvent<ISprite> action) {
		if (action == null) {
			return;
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null) {
				action.hit(spr);
			}
		}
	}

	public TArray<ISprite> query(QueryEvent<ISprite> query) {
		TArray<ISprite> result = new TArray<ISprite>();
		if (query == null) {
			return result;
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null && query.hit(spr)) {
				result.add(spr);
			}
		}
		return result;
	}

	public Sprites moveAll(float dx, float dy) {
		queryEach(new QueryEvent<ISprite>() {
			@Override
			public boolean hit(ISprite spr) {
				spr.setLocation(spr.getX() + dx, spr.getY() + dy);
				return true;
			}
		});
		return this;
	}

	public void setLocationAll(float newX, float newY) {
		if (_closed || _size == 0) {
			return;
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null) {
				spr.setLocation(newX, newY);
			}
		}
	}

	public void setScaleAll(float scaleX, float scaleY) {
		if (_closed || _size == 0) {
			return;
		}
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null) {
				spr.setScale(scaleX, scaleY);
			}
		}
	}

	public Sprites setAllVisible(boolean visible) {
		queryEach(new QueryEvent<ISprite>() {
			@Override
			public boolean hit(ISprite spr) {
				spr.setVisible(visible);
				return true;
			}
		});
		return this;
	}

	public Sprites setAllZ(int z) {
		queryEach(new QueryEvent<ISprite>() {
			@Override
			public boolean hit(ISprite spr) {
				spr.setLayer(-z);
				return true;
			}
		});
		return this;
	}

	/**
	 * 矩形范围内精灵向指定位置移动
	 * 
	 * @param rangeX
	 * @param rangeY
	 * @param rangeW
	 * @param rangeH
	 * @param dx
	 * @param dy
	 * @return
	 */
	public Sprites moveSpritesInRectRange(float rangeX, float rangeY, float rangeW, float rangeH, float dx, float dy) {
		if (_closed) {
			return this;
		}
		queryEach(new QueryEvent<ISprite>() {
			@Override
			public boolean hit(ISprite spr) {
				RectBox box = getCachedCollisionBox(spr);
				if (box != null && box.intersects(rangeX, rangeY, rangeW, rangeH)) {
					spr.setLocation(spr.getX() + dx, spr.getY() + dy);
				}
				return true;
			}
		});
		clearCollisionCache();
		_dirtyChildren = true;
		return this;
	}

	/**
	 * 圆形范围内精灵向指定位置移动
	 * 
	 * @param centerX
	 * @param centerY
	 * @param radius
	 * @param dx
	 * @param dy
	 * @return
	 */
	public Sprites moveSpritesInCircleRange(float centerX, float centerY, float radius, float dx, float dy) {
		if (_closed) {
			return this;
		}
		TArray<ISprite> sprites = checkCircleCollision(centerX, centerY, radius);
		for (int i = 0; i < sprites.size; i++) {
			ISprite spr = (ISprite) sprites.get(i);
			spr.setLocation(spr.getX() + dx, spr.getY() + dy);
		}
		_dirtyChildren = true;
		return this;
	}

	/**
	 * 按标签移动精灵
	 * 
	 * @param tag
	 * @param dx
	 * @param dy
	 * @return
	 */
	public Sprites moveSpritesByTag(Object tag, float dx, float dy) {
		if (_closed || tag == null) {
			return this;
		}
		TArray<ISprite> sprites = findTags(tag);
		for (int i = 0; i < sprites.size; i++) {
			ISprite spr = (ISprite) sprites.get(i);
			spr.setLocation(spr.getX() + dx, spr.getY() + dy);
		}
		_dirtyChildren = true;
		return this;
	}

	/**
	 * 计算当前所有精灵的平均坐标
	 * 
	 * @return
	 */
	public Vector2f getAveragePosition() {
		if (_closed || _size == 0) {
			return new Vector2f();
		}
		float totalX = 0, totalY = 0;
		int count = 0;
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null) {
				totalX += (spr.getX() + spr.getWidth() / 2f);
				totalY += (spr.getY() + spr.getHeight() / 2f);
				count++;
			}
		}
		if (count > 0) {
			return new Vector2f(totalX / count, totalY / count);
		}
		return new Vector2f();
	}

	/**
	 * 计算当前所有精灵体积与位置之和
	 * 
	 * @return
	 */
	public RectBox getBounds() {
		if (_closed || _size == 0) {
			return new RectBox();
		}
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
		float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
		for (int i = 0; i < _size; i++) {
			ISprite spr = _sprites[i];
			if (spr != null) {
				RectBox box = spr.getCollisionBox();
				if (box != null) {
					minX = MathUtils.min(minX, box.x);
					minY = MathUtils.min(minY, box.y);
					maxX = MathUtils.max(maxX, box.x + box.width);
					maxY = MathUtils.max(maxY, box.y + box.height);
				}
			}
		}
		return new RectBox(minX, minY, maxX - minX, maxY - minY);
	}

	@Override
	public String toString() {
		return super.toString() + " [name=" + _sprites_name + ", total=" + size() + "]";
	}

	/**
	 * 清空当前精灵集合
	 */
	@Override
	public void clear() {
		if (_closed || _size == 0) {
			return;
		}
		for (int i = 0; i < _size; i++) {
			ISprite removed = _sprites[i];
			if (removed != null) {
				removed.setState(State.REMOVED);
				if (removed instanceof IEntity) {
					((IEntity) removed).onDetached();
				}
				if (removed instanceof ActionBind) {
					ActionControl.get().removeAllActions((ActionBind) removed);
				}
				_sprites[i] = null;
			}
		}
		_size = 0;
		_dirtyChildren = true;
		invalidateCache();
		clearCollisionCache();
	}

	public boolean isClosed() {
		return _closed;
	}

	@Override
	public void close() {
		if (_closed) {
			return;
		}
		_visible = _createShadow = _autoSortLayer = _checkAllCollision = _checkViewCollision = false;
		if (_spriteShadow != null) {
			_spriteShadow.close();
		}
		_newLineHeight = 0;
		for (int i = _size - 1; i > -1; i--) {
			ISprite child = _sprites[i];
			if (child != null) {
				child.close();
			}
		}
		clear();
		if (_light != null) {
			_light.close();
			_light = null;
		}
		_useLight = false;
		_closed = true;
		_resizabled = false;
		_sprites = null;
		_collViewSize = null;
		_collisionObjects = null;
		freeMask();
		freeUVMask();
		freeFrameBuffer();
		freeFBOShaderMask();
		clearListerner();
		clearCollisionCache();
		LSystem.popSpritesPool(this);
	}

}
