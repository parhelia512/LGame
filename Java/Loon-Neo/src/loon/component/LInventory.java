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
package loon.component;

import java.util.Comparator;

import loon.LRelease;
import loon.LSystem;
import loon.LTexture;
import loon.LTextures;
import loon.action.map.items.IItem;
import loon.action.map.items.Inventory;
import loon.action.map.items.Item;
import loon.action.map.items.ItemInfo;
import loon.canvas.Canvas;
import loon.canvas.Image;
import loon.canvas.LColor;
import loon.component.skin.InventorySkin;
import loon.component.skin.SkinManager;
import loon.events.SysKey;
import loon.font.IFont;
import loon.font.Text;
import loon.geom.RectBox;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.TArray;

/**
 * 游戏背包用组件类
 */
public class LInventory extends LLayer {

	public static class ItemUI extends Item<ItemInfo> implements LRelease {

		protected LInventory _inventory;
		protected Actor _actor;
		protected int _itemId;
		private ItemUI.ItemUIClose _released;
		protected boolean _locked;
		protected boolean _disabled;
		protected boolean _saved;
		protected int _stackCount;
		protected int _maxStack = 99;
		protected LTexture _itemTexture;
		protected int _categoryId;

		private class ItemUIClose implements LRelease {
			private ItemUI _itemUI;
			private boolean _closed;

			public ItemUIClose(ItemUI ui) {
				this._itemUI = ui;
			}

			public boolean isClosed() {
				return this._closed;
			}

			@Override
			public void close() {
				_itemUI._saved = false;
				_itemUI._typeId = 0;
				_itemUI.setItem(null);
				_itemUI.setName(LSystem.UNKNOWN);
				_itemUI.setDescription(LSystem.UNKNOWN);
				if (_itemUI._actor != null) {
					_itemUI._actor.setTag(null);
				}
				_itemUI._locked = false;
				_itemUI._disabled = false;
				_itemUI._stackCount = 0;
				_itemUI._categoryId = 0;
				_itemUI._itemTexture = null;
				_itemUI.removeActor();
				_closed = true;
			}
		}

		ItemUI(LInventory inv, int id, String name, ItemInfo item, float x, float y, float w, float h) {
			super(name, x, y, w, h, item);
			this._inventory = inv;
			this._itemId = id;
			this._stackCount = 1;
			this._locked = false;
			this._disabled = false;
			this._categoryId = 0;
		}

		public int getItemId() {
			return this._itemId;
		}

		public boolean isSameItem(ItemUI other) {
			if (other == null) {
				return false;
			}
			return this._itemTexture.equals(other._itemTexture) && this._item.equals(other._item)
					&& this._categoryId == other._categoryId;
		}

		protected void setInventoryUI(LInventory ui) {
			this._inventory = ui;
		}

		protected void updateActorSize(Actor actor) {
			if (actor != null && actor.isThereparent()) {
				updateActorPos(actor, _itemArea);
			}
		}

		public void updateActorPos(Actor src, RectBox dstArea) {
			if (src != null && dstArea != null) {
				src.setLocation(MathUtils.ifloor(dstArea.getX() + _inventory._offsetGridActorX),
						MathUtils.ifloor(dstArea.getY() + _inventory._offsetGridActorY));
				src.setSize(MathUtils.iceil(dstArea.getWidth() - _inventory._offsetGridActorX * 2f),
						MathUtils.iceil(dstArea.getHeight() - _inventory._offsetGridActorY * 2f));
			}
		}

		public void updateActorPos() {
			if (_actor != null) {
				updateActorPos(_actor, _itemArea);
			}
		}

		@Override
		public ItemUI setArea(float x, float y, float w, float h) {
			super.setArea(x, y, w, h);
			resetActor();
			return this;
		}

		protected void removeActor() {
			if (_actor != null) {
				_inventory.removeObject(_actor);
				bind(null);
			} else {
				bind(null);
			}
		}

		protected void free() {
			if (isReleasing()) {
				return;
			}
			if ((_released == null) || (_released != null && _released.isClosed())) {
				_released = new ItemUI.ItemUIClose(this);
				if (_actor != null && _inventory._actorFadeTime > 0f && _actor.isActionCompleted()) {
					this._actor.selfAction().fadeOut(_inventory._actorFadeTime).start().dispose(_released);
				} else {
					_released.close();
				}
			}
		}

		public boolean isExist() {
			return this._saved && (_actor != null) && !isReleasing();
		}

		public boolean isReleasing() {
			return _released != null && !_released._closed;
		}

		public void resetActor() {
			if (_actor != null) {
				_actor.setTag(null);
				updateActorSize(_actor);
			}
		}

		public ItemUI bindTexture(LTexture tex) {
			this._itemTexture = tex;
			if (_actor == null) {
				_actor = new Actor(tex);
			} else {
				_actor.setImage(tex);
			}
			bind(_actor);
			return this;
		}

		public ItemUI bindTexture(LTexture tex, float x, float y, float w, float h) {
			this._itemTexture = tex;
			if (_actor == null) {
				_actor = new Actor(tex, x, y, w, h);
			} else {
				_actor.setImage(tex);
			}
			bind(_actor);
			return this;
		}

		public ItemUI bind(Actor act) {
			if (isReleasing() || isDisabled()) {
				return this;
			}
			if (act == null) {
				if (_actor != null) {
					_actor.setTag(null);
					_actor = null;
				} else {
					this._item = new ItemInfo();
					this._name = LSystem.UNKNOWN;
					this._description = LSystem.UNKNOWN;
					this._saved = false;
				}
				return this;
			}
			Object o = act.getTag();
			boolean isDst = ((o != null) && (o instanceof ItemUI));
			if (!_saved && isDst) {
				final ItemUI item = ((ItemUI) o);
				if (item.isReleasing() || item.isLocked()) {
					return this;
				}
				final ItemInfo tmpInfo = _item.cpy();
				final int tempId = _itemId;
				final int tempType = _typeId;
				final String tmpName = _name;
				final String tempDes = _description;
				final int tmpStack = _stackCount;
				final int tmpCategory = _categoryId;

				this._item = item._item;
				this._itemId = item._itemId;
				this._name = item._name;
				this._description = item._description;
				this._stackCount = item._stackCount;
				this._categoryId = item._categoryId;
				this._itemTexture = item._itemTexture;
				this._saved = true;

				item._item = tmpInfo;
				item._itemId = tempId;
				item._typeId = tempType;
				item._name = tmpName;
				item._description = tempDes;
				item._stackCount = tmpStack;
				item._categoryId = tmpCategory;
				item._saved = false;
			}
			_actor = act;
			_actor.setTag(this);
			_image = _actor.getImage();
			updateActorSize(_actor);
			if (!_inventory.containsObject(act)) {
				_inventory.addObject(act);
				if (_inventory._actorFadeTime > 0f && _actor.isActionCompleted()) {
					_actor.setAlpha(0f);
					_actor.selfAction().fadeIn(_inventory._actorFadeTime).start();
				}
			}
			_saved = true;
			return this;
		}

		public ItemUI swap(Actor actor) {
			if (actor == null) {
				return this;
			}
			if (actor.getTag() != null && actor.getTag() instanceof ItemUI) {
				return swap((ItemUI) actor.getTag());
			}
			return this;
		}

		public ItemUI swap(ItemUI item) {
			if (item == this || item._itemId == _itemId || item._actor == _actor || isReleasing() || item.isReleasing()
					|| isLocked() || item.isLocked() || isDisabled() || item.isDisabled()) {
				return this;
			}
			final LTexture srcImg = item._image;
			final Actor srcActor = item._actor;
			final boolean srcSaved = item._saved;
			final RectBox srcArea = item._itemArea.cpy();
			final String srcName = item._name;
			final ItemInfo srcItem = item._item.cpy();
			final int srcId = item._itemId;
			final int srcType = item._typeId;
			final String srcDes = item._description;
			final int srcStack = item._stackCount;
			final boolean srcLocked = item._locked;
			final int srcCategory = item._categoryId;
			final LTexture srcTex = item._itemTexture;
			final LTexture dstImg = _image;
			final Actor dstActor = _actor;
			final boolean dstSaved = _saved;
			final RectBox dstArea = _itemArea.cpy();
			final String dstName = _name;
			final ItemInfo dstItem = _item.cpy();
			final int dstId = _itemId;
			final int dstType = _typeId;
			final String dstDes = _description;
			final int dstStack = _stackCount;
			final boolean dstLocked = _locked;
			final int dstCategory = _categoryId;
			final LTexture dstTex = _itemTexture;
			item._image = dstImg;
			item._saved = dstSaved;
			item._itemArea = dstArea;
			item._itemId = dstId;
			item._typeId = dstType;
			item._name = dstName;
			item._description = dstDes;
			item._item = srcItem;
			item._stackCount = dstStack;
			item._locked = dstLocked;
			item._categoryId = dstCategory;
			item._itemTexture = dstTex;

			this._image = srcImg;
			this._saved = srcSaved;
			this._itemArea = srcArea;
			this._itemId = srcId;
			this._typeId = srcType;
			this._name = srcName;
			this._description = srcDes;
			this._item = dstItem;
			this._stackCount = srcStack;
			this._locked = srcLocked;
			this._categoryId = srcCategory;
			this._itemTexture = srcTex;

			if (srcActor != null) {
				updateActorPos(srcActor, dstArea);
				item.bind(srcActor);
			} else {
				item.bind(null);
			}
			if (dstActor != null) {
				updateActorPos(dstActor, srcArea);
				this.bind(dstActor);
			} else {
				this.bind(null);
			}
			return this;
		}

		public Actor getActor() {
			return _actor;
		}

		@Override
		public void close() {
			free();
		}

		public boolean isLocked() {
			return _locked;
		}

		public ItemUI setLocked(boolean locked) {
			this._locked = locked;
			return this;
		}

		public boolean isDisabled() {
			return _disabled;
		}

		public ItemUI setDisabled(boolean disabled) {
			this._disabled = disabled;
			return this;
		}

		public int getStackCount() {
			return _stackCount;
		}

		public ItemUI setStackCount(int count) {
			this._stackCount = MathUtils.clamp(count, 1, _maxStack);
			this._saved = this._stackCount > 0;
			return this;
		}

		public ItemUI addStack(int count) {
			return setStackCount(_stackCount + count);
		}

		public ItemUI subStack(int count) {
			return setStackCount(_stackCount - count);
		}

		public boolean isStackable(ItemUI target) {
			return isSameItem(target) && _stackCount < _maxStack;
		}

		public int getCategoryId() {
			return _categoryId;
		}

		public ItemUI setCategoryId(int categoryId) {
			this._categoryId = categoryId;
			return this;
		}

	}

	// 全部
	public static final int CATEGORY_ALL = 0;
	// 消耗品
	public static final int CATEGORY_CONSUME = 1;
	// 装备
	public static final int CATEGORY_EQUIP = 2;
	// 材料
	public static final int CATEGORY_MATERIAL = 3;
	// 任务物品
	public static final int CATEGORY_QUEST = 4;

	private LColor _gridColor;
	private boolean _initialization;
	private boolean _isCircleGrid;
	private boolean _isDisplayBar;
	private boolean _isAllowShowTip;
	private boolean _selectedGridFlag;
	private boolean _useKeyboard;
	private int _currentRowTableSize;
	private int _currentColTableSize;
	private int _selectedGridFlagDashCount;
	private float _selectedGridFlagSize;
	private float _offsetGridActorX;
	private float _offsetGridActorY;
	private float _gridPaddingLeft, _gridPaddingTop;
	private float _gridPaddingRight, _gridPaddingBottom;
	private float _gridPaddingX, _gridPaddingY;
	private float _gridTileWidth;
	private float _gridTileHeight;
	private float _actorFadeTime;
	private boolean _displayDrawGrid;
	private boolean _dirty;
	private boolean _isMobile;
	private LColor _selectedGridFlagColor;
	private LColor _tipFontColor;
	private IFont _tipFont;
	private Text _tipText;
	private LTexture _cacheGridTexture;
	private LTexture _tipTexture;
	private LTexture _barTexture;
	private Inventory _inventory;
	private Vector2f _titleSize;
	private boolean _tipSelected;
	private ItemUI _selectedItem;

	private boolean _allowSwap = true;

	private InventoryListener _listener;
	private int _currentFilterCategory = CATEGORY_ALL;

	public interface InventoryListener {
		void onItemDragStart(ItemUI item);

		void onItemSwap(ItemUI src, ItemUI dst);

		void onItemAdd(ItemUI item);

		void onItemRemove(ItemUI item);

		void onItemClick(ItemUI item);

		void onCategoryChanged(int newCategory);
	}

	public LInventory(float x, float y, float w, float h) {
		this(SkinManager.get().getMessageSkin().getFont(), x, y, w, h, false);
	}

	public LInventory(IFont font, float x, float y, float w, float h) {
		this(font, x, y, w, h, false);
	}

	public LInventory(float x, float y, float w, float h, boolean limit) {
		this(SkinManager.get().getMessageSkin().getFont(), x, y, w, h, limit);
	}

	public LInventory(IFont font, float x, float y, float w, float h, boolean limit) {
		this(font, (LTexture) null, (LTexture) null, LColor.gray, x, y, w, h, limit);
	}

	public LInventory(IFont font, LColor grid, float x, float y, float w, float h, boolean limit) {
		this(font, (LTexture) null, (LTexture) null, grid, x, y, w, h, limit);
	}

	public LInventory(IFont font, LTexture bg, LTexture bar, float x, float y, float w, float h, boolean limit) {
		this(font, bg, bar, LColor.gray, x, y, w, h, limit);
	}

	public LInventory(InventorySkin skin, float x, float y, float w, float h, boolean limit) {
		this(skin.getFont(), skin.getFontColor(), skin.getBackgroundTexture(), skin.getBarTexture(),
				skin.getGridColor(), x, y, w, h, limit);
	}

	public LInventory(IFont font, LTexture bg, LTexture bar, LColor gridColor, float x, float y, float w, float h,
			boolean limit) {
		this(font, LColor.white, bg, bar, gridColor, x, y, w, h, limit);
	}

	/**
	 * 构建一个游戏用背包
	 * 
	 * @param font
	 * @param fontColor
	 * @param bg
	 * @param bar
	 * @param gridColor
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param limit
	 */
	public LInventory(IFont font, LColor fontColor, LTexture bg, LTexture bar, LColor gridColor, float x, float y,
			float w, float h, boolean limit) {
		super(MathUtils.ifloor(x), MathUtils.ifloor(y), MathUtils.ifloor(w), MathUtils.ifloor(h), limit);
		this._inventory = new Inventory();
		this._titleSize = new Vector2f(w, h);
		this._offsetGridActorX = 2f;
		this._offsetGridActorY = 2f;
		this._selectedGridFlagSize = 2f;
		this._selectedGridFlagDashCount = 3;
		this._actorFadeTime = 10f;
		if (gridColor != null) {
			this._gridColor = gridColor.lighter();
		}
		this._displayDrawGrid = _isDisplayBar = _isAllowShowTip = true;
		this._selectedGridFlag = true;
		this._isCircleGrid = false;
		this._isMobile = LSystem.isMobile() || LSystem.isEmulateTouch();
		this._tipFont = font;
		this._tipFontColor = fontColor;
		this._barTexture = (bar == null ? SkinManager.get().getWindowSkin().getBarTexture() : bar);
		setTipBackground((LTexture) null);
		setBackground(bg == null ? SkinManager.get().getWindowSkin().getBackgroundTexture() : bg, w, h);
		setLayer(1000);
		setActorDrag(true);
		setDragLocked(false);
		setElastic(false);
	}

	public LInventory topBottom(float top, float bottom, int row, int col) {
		return leftTopRightBottom(_offsetGridActorX, top, _offsetGridActorY, bottom, row, col);
	}

	public LInventory rightBottom(float right, float bottom, int row, int col) {
		return leftTopRightBottom(_offsetGridActorX, _offsetGridActorY, right, bottom, row, col);
	}

	public LInventory leftTop(float left, float top, int row, int col) {
		return leftTopRightBottom(left, top, _offsetGridActorX, _offsetGridActorY, row, col);
	}

	public LInventory leftTopRightBottom(float left, float top, float right, float bottom, int row, int col) {
		return update(left, top, right, bottom, row, col, _offsetGridActorX * 2f, _offsetGridActorX * 2f);
	}

	public LInventory update() {
		return update(_gridPaddingLeft, _gridPaddingTop, _gridPaddingRight, _gridPaddingBottom, _currentRowTableSize,
				_currentColTableSize, _gridPaddingX, _gridPaddingY);
	}

	public LInventory update(float left, float top, float right, float bottom, int row, int col, float spaceSizeX,
			float spaceSizeY) {
		if (row == this._currentRowTableSize && col == this._currentColTableSize && spaceSizeX == this._gridPaddingX
				&& spaceSizeY == this._gridPaddingY && left == this._gridPaddingLeft && top == this._gridPaddingTop
				&& right == this._gridPaddingRight && bottom == this._gridPaddingBottom) {
			return this;
		}
		this._currentRowTableSize = row;
		this._currentColTableSize = col;
		this._gridPaddingLeft = left;
		this._gridPaddingTop = top;
		this._gridPaddingRight = right;
		this._gridPaddingBottom = bottom;
		this._gridPaddingX = spaceSizeX;
		this._gridPaddingY = spaceSizeY;
		this._gridTileWidth = getWidth() / _currentRowTableSize;
		this._gridTileHeight = getHeight() / _currentColTableSize;
		final int tileWidth = MathUtils
				.ifloor(_gridTileWidth - ((_gridPaddingLeft + _gridPaddingRight) / _currentRowTableSize));
		final int tileHeight = MathUtils
				.ifloor(_gridTileHeight - ((_gridPaddingTop + _gridPaddingBottom) / _currentColTableSize));
		this._titleSize.set(tileWidth, tileHeight);
		final float xLeft = MathUtils.min(_gridPaddingLeft, (_gridPaddingLeft + _gridPaddingRight))
				+ _gridPaddingX / 2f;
		final float xTop = MathUtils.min(_gridPaddingTop, (_gridPaddingTop + _gridPaddingBottom)) + _gridPaddingY / 2f;
		if (this._initialization) {
			this._inventory.clear();
		}
		final int size = _inventory.getItemCount();
		int idx = 0;
		if (size == 0) {
			for (int y = 0; y < col; y++) {
				for (int x = 0; x < row; x++) {
					ItemUI item = new ItemUI(this, idx, LSystem.UNKNOWN + idx, new ItemInfo(), xLeft + (x * tileWidth),
							xTop + (y * tileHeight), tileWidth - spaceSizeX, tileHeight - spaceSizeY);
					_inventory.addItem(item);
					idx++;
				}
			}
		} else {
			ItemUI item = null;
			for (int y = 0; y < col; y++) {
				for (int x = 0; x < row; x++) {
					if (idx < size) {
						item = (ItemUI) _inventory.getItem(idx);
						item.setArea(xLeft + (x * tileWidth), xTop + (y * tileHeight), tileWidth - spaceSizeX,
								tileHeight - spaceSizeY);
					} else {
						item = new ItemUI(this, idx, LSystem.UNKNOWN + idx, new ItemInfo(), xLeft + (x * tileWidth),
								xTop + (y * tileHeight), tileWidth - spaceSizeX, tileHeight - spaceSizeY);
						_inventory.addItem(item);
					}
					idx++;
				}
			}
		}
		this._initialization = true;
		this._dirty = true;
		return this;
	}

	public float getPaddingX() {
		return _gridPaddingX;
	}

	public float getPaddingY() {
		return _gridPaddingY;
	}

	public float getPaddingLeft() {
		return _gridPaddingLeft;
	}

	public float getPaddingTop() {
		return _gridPaddingTop;
	}

	public float getPaddingRight() {
		return _gridPaddingRight;
	}

	public float getPaddingBottom() {
		return _gridPaddingBottom;
	}

	public int getColumns() {
		return _currentColTableSize;
	}

	public int getRows() {
		return _currentRowTableSize;
	}

	public boolean isDirty() {
		return this._dirty;
	}

	public LInventory swap(ItemUI a, ItemUI b) {
		_inventory.swap(a, b);
		if (_listener != null) {
			_listener.onItemSwap(a, b);
		}
		return this;
	}

	public LInventory putItem(String path) {
		return putItem(LTextures.loadTexture(path), new ItemInfo(), CATEGORY_ALL);
	}

	public LInventory putItem(String path, ItemInfo info) {
		return putItem(LTextures.loadTexture(path), info, CATEGORY_ALL);
	}

	public LInventory putItem(LTexture tex) {
		return putItem(tex, new ItemInfo(), CATEGORY_ALL);
	}

	public LInventory putItem(LTexture tex, ItemInfo info, int c) {
		if (_initialization) {
			final int size = _inventory.getItemCount();
			for (int i = 0; i < size; i++) {
				ItemUI item = (ItemUI) _inventory.getItem(i);
				if (item != null && !item._saved && (item._actor == null) && !item.isDisabled()) {
					item.setArea(item.getArea().getX(), item.getArea().getY(), item.getArea().getWidth(),
							item.getArea().getHeight());
					item.bindTexture(tex);
					item.updateActorSize(item.getActor());
					item.updateActorPos();
					item.setName(info.getName());
					item.setItem(info);
					item.setStackCount(1);
					item._saved = true;
					item.setCategoryId(c);
					return this;
				}
			}
		} else {
			if (info == null) {
				info = new ItemInfo();
			}
			ItemUI item = new ItemUI(this, _inventory.getItemCount(), info.getName(), info, 0f, 0f, 0f, 0f);
			item.bindTexture(tex, 0f, 0f, _titleSize.x, _titleSize.y);
			item.updateActorSize(item.getActor());
			item.updateActorPos();
			item.setStackCount(1);
			item.setCategoryId(c);
			_inventory.addItem(item);
		}
		return this;
	}

	public LInventory putItem(LTexture tex, ItemInfo info) {
		return putItem(tex, info, CATEGORY_ALL);
	}

	public ItemUI removeItemIndex(int idx) {
		ItemUI item = getItem(idx);
		if (item != null && item.isExist() && !item.isLocked()) {
			item.free();
			if (_listener != null)
				_listener.onItemRemove(item);
		}
		return item;
	}

	public boolean removeItem(float x, float y) {
		ItemUI item = getItem(x, y);
		if (item != null && item.isExist() && !item.isLocked()) {
			item.free();
			if (_listener != null)
				_listener.onItemRemove(item);
		}
		return item != null;
	}

	public ItemUI popItem() {
		ItemUI item = null;
		for (int i = _inventory.getItemCount() - 1; i > -1; i--) {
			item = (ItemUI) _inventory.getItem(i);
			if (item != null && item.isExist() && !item.isLocked()) {
				break;
			}
		}
		if (item != null) {
			item.free();
			if (_listener != null)
				_listener.onItemRemove(item);
		}
		return item;
	}

	public int getItemToIndex(ItemUI im) {
		return _inventory.getItemToIndex(im);
	}

	public ItemUI getItem(int idx) {
		return (ItemUI) _inventory.getItem(idx);
	}

	public ItemUI getItem(float x, float y) {
		return (ItemUI) _inventory.getItem(x, y);
	}

	public LInventory setItem(LTexture tex, ItemInfo info, float x, float y) {
		if (_initialization) {
			final int size = _inventory.getItemCount();
			for (int i = 0; i < size; i++) {
				ItemUI item = (ItemUI) _inventory.getItem(i);
				if (item != null) {
					RectBox rect = item.getArea();
					if (rect.contains(x, y)) {
						item.setItem(info);
						item.bindTexture(tex);
						item.updateActorPos();
					}
					return this;
				}
			}
		}
		return this;
	}

	public LInventory setItem(LTexture tex, ItemInfo info, int idx) {
		if (_initialization) {
			ItemUI item = (ItemUI) _inventory.getItem(idx);
			if (item != null) {
				RectBox rect = item.getArea();
				item.setItem(info);
				if (rect != null) {
					item.bindTexture(tex);
					item.updateActorPos();
				} else {
					item.bindTexture(tex, 0f, 0f, _titleSize.x, _titleSize.y);
				}
			}
		}
		return this;
	}

	public LInventory setTipBackground(String path) {
		return setTipBackground(LTextures.loadTexture(path));
	}

	public LInventory setTipBackground(LTexture tex) {
		_tipTexture = (tex == null ? this._tipTexture = SkinManager.get().getMessageSkin().getBackgroundTexture()
				: tex);
		return this;
	}

	protected LInventory setTipText(String message) {
		return setTipText(_tipFont, message);
	}

	protected LInventory setTipText(IFont font, String message) {
		if (_tipText == null) {
			_tipText = new Text(font, message);
		} else {
			_tipText.setText(font, message);
		}
		return this;
	}

	protected void checkTouchTip() {
		if (!_isAllowShowTip) {
			return;
		}
		final Vector2f pos = getUITouchXY();
		setTipItem(getItem(pos.x, pos.y));
	}

	public LInventory setTipItem(int idx) {
		return setTipItem(getItem(idx));
	}

	public LInventory setTipItem(ItemUI item) {
		if (item == null) {
			freeTipSelected();
			return this;
		}
		_selectedItem = item;
		if (_selectedItem != null && _selectedItem._saved) {
			ItemInfo iteminfo = _selectedItem.getItem();
			if (iteminfo != null) {
				final String name = iteminfo.getName();
				final String des = iteminfo.getDescription();
				final String stack = _selectedItem.getStackCount() >= 1 ? " x:" + _selectedItem.getStackCount()
						: " x:0";
				final String category = getCategoryName(_selectedItem.getCategoryId());
				final String context = name + stack + " | " + category + LSystem.LF + des;
				setTipText(context);
				_tipSelected = true;
			}
		} else {
			_tipSelected = false;
		}
		return this;
	}

	public int getItemCount() {
		return this._inventory.getItemCount();
	}

	public float getGold() {
		return this._inventory.getGold();
	}

	public LInventory addGold(float i) {
		this._inventory.addGold(i);
		return this;
	}

	public LInventory subGold(float i) {
		this._inventory.subGold(i);
		return this;
	}

	public LInventory mulGold(float i) {
		this._inventory.mulGold(i);
		return this;
	}

	public LInventory divGold(float i) {
		this._inventory.divGold(i);
		return this;
	}

	public LInventory setGold(float i) {
		this._inventory.setGold(i);
		return this;
	}

	public LInventory merge(LInventory inv) {
		this._inventory.merge(inv._inventory);
		return this;
	}

	public LInventory sort(Comparator<IItem> comp) {
		this._inventory.sort(comp);
		return this;
	}

	public LInventory clearInventory() {
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && !item.isLocked()) {
				item.free();
			}
		}
		return this;
	}

	public boolean containsItem(float x, float y) {
		return getItem(x, y) != null;
	}

	public boolean isInitialized() {
		return this._initialization;
	}

	public IItem getSelectedItem() {
		return _selectedItem;
	}

	public Vector2f getSelectedItemPos() {
		if (_selectedItem != null) {
			for (int i = _inventory.getItemCount() - 1; i > -1; i--) {
				IItem item = _inventory.getItem(i);
				if (item == _selectedItem || item.equals(_selectedItem)) {
					return item.getArea().getPosition();
				}
			}
		}
		return null;
	}

	public Vector2f getSelectedItemGridXY() {
		if (_selectedItem != null) {
			int idx = 0;
			for (int y = 0; y < _currentColTableSize; y++) {
				for (int x = 0; x < _currentRowTableSize; x++) {
					IItem item = _inventory.getItem(idx);
					if (item == _selectedItem || item.equals(_selectedItem)) {
						return new Vector2f(x, y);
					}
					idx++;
				}
			}
		}
		return null;
	}

	public LInventory gotoSelectItemTo(XY pos) {
		if (pos == null) {
			return null;
		}
		return gotoSelectItemTo(MathUtils.iceil(pos.getX()), MathUtils.iceil(pos.getY()));
	}

	public LInventory gotoSelectItemTo(int tileX, int tileY) {
		if (tileX >= 0 && tileX <= this._currentColTableSize && tileY >= 0 && tileY <= this._currentRowTableSize) {
			return setTipItem(tileX + tileY * this._currentRowTableSize);
		}
		return this;
	}

	protected void drawItemGrid(Canvas g, float x, float y, float w, float h, boolean oval) {
		if (oval) {
			g.drawOval(x, y, w, h);
		} else {
			g.strokeRect(x, y, w, h);
		}
	}

	protected LTexture createGridCache() {
		if (_dirty) {
			if (_cacheGridTexture != null) {
				_cacheGridTexture.cancalSubmit();
				_cacheGridTexture.close(true);
				_cacheGridTexture = null;
			}
			Canvas g = Image.createCanvas(getWidth(), getHeight());
			final int tint = g.getStrokeColor();
			g.setColor(_gridColor);
			for (int i = _inventory.getItemCount() - 1; i > -1; i--) {
				RectBox rect = _inventory.getItem(i).getArea();
				if (rect != null) {
					if (_displayDrawGrid) {
						drawItemGrid(g, rect.x, rect.y, rect.width, rect.height, _isCircleGrid);
					}
				}
			}
			g.setStrokeColor(tint);
			_cacheGridTexture = g.toTexture();
			_dirty = false;
			g = null;
		}
		return _cacheGridTexture;
	}

	protected void drawBarToUI(GLEx g, float x, float y, float w, float h) {
		if (_isDisplayBar) {
			if (_gridPaddingLeft > _gridPaddingX && _barTexture != null) {
				g.draw(_barTexture, x, y, _gridPaddingLeft, h);
			}
			if (_gridPaddingRight > _gridPaddingX && _barTexture != null) {
				g.draw(_barTexture, x + getWidth() - _gridPaddingRight - _gridPaddingX, y,
						_gridPaddingRight + _gridPaddingX, h);
			}
			if (_gridPaddingTop > _gridPaddingY && _barTexture != null) {
				g.draw(_barTexture, x, y, w, _gridPaddingTop);
			}
			if (_gridPaddingBottom > _gridPaddingY && _barTexture != null) {
				g.draw(_barTexture, x, y + getHeight() - _gridPaddingBottom - _gridPaddingY, w,
						_gridPaddingBottom + _gridPaddingY);
			}
		}
	}

	protected void drawSelectedFlagToUI(GLEx g, float x, float y) {
		if (_selectedGridFlag && _tipSelected && _selectedItem != null) {
			RectBox rect = _selectedItem.getArea();
			drawItemSelectedFlagGrid(g, x + rect.x, y + rect.y, rect.width, rect.height);
		}
	}

	protected void drawItemSelectedFlagGrid(GLEx g, float x, float y, float w, float h) {
		float lineSize = g.getLineWidth();
		g.setLineWidth(_selectedGridFlagSize);
		g.drawDashRect(x, y, w, h, _selectedGridFlagColor, _selectedGridFlagDashCount);
		g.setLineWidth(lineSize);
	}

	protected void drawTipToUI(GLEx g, float x, float y) {
		if (!_isAllowShowTip) {
			return;
		}
		if (_tipSelected && _selectedItem != null && _tipText != null) {
			final RectBox rect = _selectedItem.getArea();
			final IFont font = _tipText.getFont();
			final float fontSize = font.getSize();
			final float texW = _tipText.getWidth();
			final float texH = _tipText.getHeight();
			final float width = texW + fontSize;
			final float height = texH + fontSize;
			final float posX = x + rect.x + (rect.getWidth() + width - _gridTileWidth) / 2f - fontSize;
			final float posY = y + rect.y + (rect.getHeight() + height - _gridTileHeight) / 2f;
			g.draw(_tipTexture, posX - (width - texW + fontSize) / 2f, posY, width, height);
			_tipText.paintString(g, posX - (width - texW) / 2f, posY + (height - texH) / 2f, _tipFontColor);
		}
	}

	protected void drawGridToUI(GLEx g, int x, int y) {
		if (_displayDrawGrid) {
			createGridCache();
			if (_cacheGridTexture != null) {
				g.draw(_cacheGridTexture, x, y);
			}
		}
	}

	@Override
	public void createCustomUI(GLEx g, int x, int y, int w, int h) {
		if (!_component_visible) {
			return;
		}
		drawBarToUI(g, x, y, w, h);
		drawGridToUI(g, x, y);
		super.createCustomUI(g, x, y, w, h);
		drawSelectedFlagToUI(g, x, y);
		drawTipToUI(g, x, y);
	}

	public String getCategoryName(int categoryId) {
		switch (categoryId) {
		case CATEGORY_CONSUME:
			return "CONSUME";
		case CATEGORY_EQUIP:
			return "EQUIP";
		case CATEGORY_MATERIAL:
			return "MATERIAL";
		case CATEGORY_QUEST:
			return "QUEST";
		default:
			return "OTHER";
		}
	}

	public LInventory switchCategory(int categoryId) {
		this._currentFilterCategory = categoryId;
		if (_listener != null) {
			_listener.onCategoryChanged(categoryId);
		}
		return this;
	}

	public int getCurrentCategory() {
		return _currentFilterCategory;
	}

	public LInventory setSelectedGridFlagColor(LColor c) {
		this._selectedGridFlagColor = c;
		return this;
	}

	public LColor getSelectedGridFlagColor() {
		return this._selectedGridFlagColor;
	}

	public LInventory setSelectedGridFlagSize(float s) {
		this._selectedGridFlagSize = s;
		return this;
	}

	public float getSelectedGridFlagSize() {
		return _selectedGridFlagSize;
	}

	public LInventory setSelectedGridFlagDashCount(int s) {
		this._selectedGridFlagDashCount = s;
		return this;
	}

	public int getSelectedGridFlagDashCount() {
		return _selectedGridFlagDashCount;
	}

	public LInventory freeTipSelected() {
		_tipSelected = false;
		_selectedItem = null;
		return this;
	}

	@Override
	public void processTouchMoved() {
		super.processTouchMoved();
		if (!_useKeyboard && !_isMobile) {
			checkTouchTip();
		}
	}

	@Override
	public void downClick(int dx, int dy) {
		super.downClick(dx, dy);
		if (!_useKeyboard) {
			freeTipSelected();
			ItemUI item = getItem(dx, dy);
			if (item != null && _listener != null) {
				_listener.onItemClick(item);
			}
			if (_isMobile && isLongPressed()) {
				checkTouchTip();
			}
		}
	}

	@Override
	public void dragClick(int dx, int dy) {
		super.dragClick(dx, dy);
		if (!_useKeyboard) {
			findDragActor();
			final boolean draged = _input == null ? false : (_input.getTouchDX() == 0 && _input.getTouchDY() == 0);
			if (_isMobile && !_tipSelected && draged) {
				checkTouchTip();
			}
		}
	}

	@Override
	public void upClick(int dx, int dy) {
		super.upClick(dx, dy);
		if (!_useKeyboard) {
			final Actor act = getClickActor();
			if (act == null) {
				freeDragActor();
				freeTipSelected();
				return;
			}
			final Object o = act.getTag();
			final ItemUI itemSrc = (o instanceof ItemUI) ? ((ItemUI) o) : null;
			final ItemUI itemDst = getItem(dx, dy);
			boolean needReset = true;
			try {
				if (_actorDrag && _allowSwap && itemSrc != null && !itemSrc.isLocked() && !itemSrc.isDisabled()) {
					if (itemDst != null && !itemDst.isLocked() && !itemDst.isDisabled()) {
						if (itemDst != itemSrc) {
							final boolean srcNotDst = (itemSrc != null && itemDst._itemId != itemSrc._itemId);
							if (!itemDst.isExist() && (srcNotDst || itemSrc == null)) {
								itemDst.bind(act);
								needReset = false;
							} else if (srcNotDst && itemDst.isExist() && itemSrc.isExist()) {
								itemDst.swap(itemSrc);
								needReset = false;
							}
						}
					}
				}
				if (needReset) {
					if (itemSrc != null) {
						itemSrc.updateActorPos(act, itemSrc.getArea());
						itemSrc.bind(act);
					} else {
						act.setTag(null);
						if (itemDst != null) {
							itemDst.resetActor();
						}
					}
				}
				if (itemSrc != null && _listener != null) {
					_listener.onItemSwap(itemSrc, itemDst);
				}
			} catch (Exception e) {
				if (itemSrc != null) {
					itemSrc.bind(act);
				} else {
					act.setTag(null);
				}
			}
			freeTipSelected();
			freeDragActor();
		}
	}

	/**
	 * 此项为true时,使用键盘而非触屏选择物品
	 * 
	 * @param u
	 * @return
	 */
	public LInventory useKeyboard(boolean u) {
		this._useKeyboard = u;
		if (u) {
			this.focusIn();
		} else {
			this.focusOut();
		}
		return this;
	}

	public boolean isUseKeyboard() {
		return _useKeyboard;
	}

	@Override
	public void processKeyReleased() {
		super.processKeyReleased();
		if (_useKeyboard) {
			Vector2f itemXY = getSelectedItemGridXY();
			if (itemXY != null) {
				if (isKeyUp(SysKey.LEFT)) {
					itemXY.move_left(1);
				} else if (isKeyUp(SysKey.LEFT)) {
					itemXY.move_right(1);
				} else if (isKeyUp(SysKey.UP)) {
					itemXY.move_up(1);
				} else if (isKeyUp(SysKey.DOWN)) {
					itemXY.move_down(1);
				}
				gotoSelectItemTo(itemXY);
			}
		}
	}

	public float getGridTileWidth() {
		return _gridTileWidth;
	}

	public float getGridTileHeight() {
		return _gridTileHeight;
	}

	public boolean isDisplayDrawGrid() {
		return _displayDrawGrid;
	}

	public LInventory setDisplayDrawGrid(boolean d) {
		this._displayDrawGrid = d;
		this._dirty = true;
		return this;
	}

	public boolean isCircleGrid() {
		return _isCircleGrid;
	}

	public LInventory setCircleGrid(boolean d) {
		this._isCircleGrid = d;
		this._dirty = true;
		return this;
	}

	public LTexture getBarImage() {
		return _barTexture;
	}

	public LInventory setBarImage(LTexture bar) {
		this._barTexture = bar;
		return this;
	}

	public boolean isAllowShowTip() {
		return _isAllowShowTip;
	}

	public LInventory setAllowShowTip(boolean a) {
		this._isAllowShowTip = a;
		return this;
	}

	public boolean isDisplayBar() {
		return _isDisplayBar;
	}

	public LInventory setDisplayBar(boolean d) {
		this._isDisplayBar = d;
		return this;
	}

	public float getOffsetGridActorX() {
		return _offsetGridActorX;
	}

	public LInventory setOffsetGridActorX(float x) {
		this._offsetGridActorX = x;
		this._dirty = true;
		return this;
	}

	public float getOffsetGridActorY() {
		return _offsetGridActorY;
	}

	public LInventory setOffsetGridActorY(float y) {
		this._offsetGridActorY = y;
		this._dirty = true;
		return this;
	}

	public float getItemFadeAlphaTime() {
		return _actorFadeTime;
	}

	public LInventory setItemFadeAlphaTime(float a) {
		this._actorFadeTime = a;
		return this;
	}

	public ItemUI getTipItem() {
		return _selectedItem;
	}

	public LColor getTipFontColor() {
		return _tipFontColor.cpy();
	}

	public LInventory setTipFontColor(LColor t) {
		this._tipFontColor = new LColor(t);
		return this;
	}

	public Vector2f getTitleSize() {
		return _titleSize.cpy();
	}

	public boolean isTipSelected() {
		return _tipSelected;
	}

	/**
	 * 变化当前背包为指定高度的可滚动容器(大于背包大小则背包最大值的2/3)
	 * 
	 * @param x
	 * @param y
	 * @param h
	 * @return
	 */
	public LScrollContainer toVerticalScroll(float x, float y, float h) {
		LScrollContainer scroll = LScrollContainer.createVerticalScrollContainer(this, x, y, h);
		scroll.add(this);
		return scroll;
	}

	public LScrollContainer toHorizontalScroll(float x, float y, float w) {
		LScrollContainer scroll = LScrollContainer.createHorizontalScrollContainer(this, x, y, w);
		scroll.add(this);
		return scroll;
	}

	public boolean isAllowDrag() {
		return isActorDrag();
	}

	public LInventory setAllowDrag(boolean allow) {
		this.setActorDrag(allow);
		return this;
	}

	public boolean isAllowSwap() {
		return _allowSwap && _actorDrag;
	}

	public LInventory setAllowSwap(boolean allow) {
		this._allowSwap = allow;
		return this;
	}

	public LInventory setInventoryListener(InventoryListener listener) {
		this._listener = listener;
		return this;
	}

	public int getEmptySlotCount() {
		int count = 0;
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && !item.isExist() && !item.isDisabled())
				count++;
		}
		return count;
	}

	public boolean isFull() {
		return getEmptySlotCount() == 0;
	}

	public int getFirstEmptySlotIndex() {
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && !item.isExist() && !item.isDisabled())
				return i;
		}
		return -1;
	}

	public TArray<ItemUI> getNonNullItems() {
		TArray<ItemUI> list = new TArray<ItemUI>();
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && item.isExist())
				list.add(item);
		}
		return list;
	}

	/**
	 * 按分类筛选物品
	 * 
	 * @param categoryId
	 * @return
	 */
	public TArray<ItemUI> filterItemsByCategory(int categoryId) {
		TArray<ItemUI> list = new TArray<ItemUI>();
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && item.isExist() && item.getCategoryId() == categoryId) {
				list.add(item);
			}
		}
		return list;
	}

	public ItemUI findItemById(int itemId) {
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && item.isExist() && item.getItemId() == itemId)
				return item;
		}
		return null;
	}

	public TArray<ItemUI> filterItemsByName(String name) {
		TArray<ItemUI> list = new TArray<ItemUI>();
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && item.isExist() && item.getName().contains(name)) {
				list.add(item);
			}
		}
		return list;
	}

	public LInventory unlockAllItems() {
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null) {
				item.setLocked(false);
			}
		}
		return this;
	}

	public LInventory clearEmptySlots() {
		for (int i = 0; i < getItemCount(); i++) {
			ItemUI item = getItem(i);
			if (item != null && !item.isExist() && !item.isLocked()) {
				item.setStackCount(0);
			}
		}
		return this;
	}

	public LInventory swapByIndex(int srcIndex, int dstIndex) {
		ItemUI src = getItem(srcIndex);
		ItemUI dst = getItem(dstIndex);
		if (src != null && dst != null) {
			swap(src, dst);
		}
		return this;
	}

	public LInventory setSlotDisabled(int index, boolean disabled) {
		ItemUI item = getItem(index);
		if (item != null) {
			item.setDisabled(disabled);
		}
		return this;
	}

	/**
	 * 自动统计同类物品数量
	 * 
	 * @param o
	 * @return
	 */
	public LInventory autoStackItem(ItemUI o) {
		if (o == null || !o.isExist() || !o.isStackable(o)) {
			return this;
		}
		int maxStack = o._maxStack;
		TArray<ItemUI> items = getNonNullItems();
		for (int i = 0; i < items.size; i++) {
			ItemUI target = items.get(i);
			if (target == o || !target.isSameItem(o)) {
				continue;
			}
			int newCount = o.getStackCount() + target.getStackCount();
			if (newCount <= maxStack) {
				target.setStackCount(newCount);
				o.setStackCount(0);
				break;
			} else if (target.getStackCount() < maxStack) {
				int needed = maxStack - target.getStackCount();
				target.setStackCount(maxStack);
				o.setStackCount(o.getStackCount() - needed);
			}
		}
		return this;
	}

	/**
	 * 整理背包，清除空位并堆叠物品
	 * 
	 * @return
	 */
	public LInventory tidy() {
		TArray<ItemUI> items = getNonNullItems();
		int count = items.size;
		for (int i = 0; i < count; i++) {
			ItemUI item = items.get(i);
			ItemUI targetSlot = getItem(i);
			if (item != targetSlot) {
				this.swap(item, targetSlot);
			}
		}
		TArray<ItemUI> currentItems = getNonNullItems();
		for (int i = 0; i < currentItems.size; i++) {
			final ItemUI target = currentItems.get(i);
			if (!target.isExist()) {
				continue;
			}
			for (int j = i + 1; j < currentItems.size; j++) {
				final ItemUI source = currentItems.get(j);
				if (!source.isExist()) {
					continue;
				}
				if (target.isSameItem(source) && target.getStackCount() < target._maxStack) {
					int space = target._maxStack - target.getStackCount();
					int amount = Math.min(space, source.getStackCount());
					target.addStack(amount);
					source.subStack(amount);
					if (source.getStackCount() <= 0) {
						source.free();
					}
				}
			}
		}
		TArray<ItemUI> finalItems = getNonNullItems();
		for (int i = 0; i < finalItems.size; i++) {
			ItemUI item = finalItems.get(i);
			ItemUI targetSlot = getItem(i);
			if (item != targetSlot) {
				this.swap(item, targetSlot);
			}
		}
		return this;
	}

	public LInventory resize(int newRows, int newCols) {
		int newSize = newRows * newCols;
		TArray<ItemUI> tempItems = new TArray<ItemUI>(getNonNullItems());
		update(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom(), newRows, newCols,
				getPaddingX(), getPaddingY());
		for (int i = 0; i < newSize; i++) {
			ItemUI slot = getItem(i);
			if (slot != null) {
				slot.setStackCount(0);
			}
		}
		for (int n = 0; n < tempItems.size; n++) {
			ItemUI item = tempItems.get(n);
			for (int i = 0; i < newSize; i++) {
				ItemUI slot = getItem(i);
				if (slot != null && !slot.isExist()) {
					slot.bind(item.getActor());
					break;
				}
			}
		}
		return this;
	}

	public LInventory showOnlyCategory(int categoryId) {
		int total = getItemCount();
		for (int i = 0; i < total; i++) {
			ItemUI item = getItem(i);
			if (item == null) {
				continue;
			}
			if (categoryId == CATEGORY_ALL || item.getCategoryId() == categoryId) {
				item.setDisabled(false);
			} else {
				item.setDisabled(true);
			}
		}
		return this;
	}

	public TArray<ItemUI> searchItems(String keyword) {
		TArray<ItemUI> results = new TArray<>();
		int total = getItemCount();
		for (int i = 0; i < total; i++) {
			ItemUI item = getItem(i);
			if (item != null && item.isExist() && item.getName().toLowerCase().contains(keyword.toLowerCase())) {
				results.add(item);
			}
		}
		return results;
	}

	@Override
	protected void _onDestroy() {
		super._onDestroy();
		if (_cacheGridTexture != null) {
			_cacheGridTexture.close();
			_cacheGridTexture = null;
		}
		if (_barTexture != null) {
			_barTexture.close();
			_barTexture = null;
		}
	}

}
