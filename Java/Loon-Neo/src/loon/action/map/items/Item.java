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
package loon.action.map.items;

import loon.LRelease;
import loon.LSystem;
import loon.LTexture;
import loon.LTextures;
import loon.events.EventActionT;
import loon.geom.RectBox;
import loon.geom.Vector2f;
import loon.geom.XYZW;
import loon.opengl.GLEx;
import loon.utils.MathUtils;

public class Item<T> implements IItem, LRelease {

	public static enum ItemRarity {
		COMMON, UNCOMMON, RARE, EPIC, LEGENDARY

	}

	protected RectBox _itemArea;
	protected LTexture _image;
	protected String _name;
	protected String _description;
	protected T _item;
	protected int _typeId;
	protected boolean _used;
	protected EventActionT<Item<T>> _itemChanged;

	private final Vector2f _drawLocation = new Vector2f();

	// 基础道具属性
	// 堆叠数量
	private int _stackCount = 1;
	// 最大堆叠数
	private int _maxStack = 99;

	// 价格
	private float _price = 0;
	// 稀有度
	private ItemRarity _rarity = ItemRarity.COMMON;
	// 是否消耗品
	private boolean _consumable = true;
	// 是否可出售
	private boolean _sellable = true;
	// 是否可丢弃
	private boolean _discardable = true;

	// 使用与冷却
	// 总冷却
	private float _cooldown = 0f;
	// 当前冷却
	private float _currentCooldown = 0f;
	// 可使用次数(-1==无限)
	private int _useCount = -1;
	// 使用中状态
	private boolean _isUsing = false;
	// 使用动作时长
	private float _useDuration = 0.5f;
	// 使用进度
	private float _useProgress = 0f;

	// 战斗属性
	// 使用范围
	private int _useRange = 1;
	// 战斗中可用
	private boolean _canUseInCombat = true;
	// 非战斗可用
	private boolean _canUseOutOfCombat = true;

	public Item(String name, String imagePath, XYZW rect, T item) {
		this(0, name, imagePath, rect.getX(), rect.getY(), rect.getZ(), rect.getW(), item);
	}

	public Item(int typeId, String name, String imagePath, XYZW rect, T item) {
		this(typeId, name, imagePath, rect.getX(), rect.getY(), rect.getZ(), rect.getW(), item);
	}

	public Item(String name, String imagePath, T item) {
		this(0, name, imagePath, item);
	}

	public Item(int typeId, String name, String imagePath, T item) {
		this(typeId, name, imagePath, 0f, 0f, 0f, 0f, item);
	}

	public Item(String name, String imagePath, float x, float y, float w, float h, T item) {
		this(0, name, imagePath, x, y, w, h, item);
	}

	public Item(int typeId, String name, String imagePath, float x, float y, float w, float h, T item) {
		this(typeId, name, LTextures.loadTexture(imagePath), x, y, w, h, item);
	}

	public Item(String name, float x, float y, float w, float h, T item) {
		this(0, name, (LTexture) null, x, y, w, h, item);
	}

	public Item(String name, T item) {
		this(0, name, item);
	}

	public Item(int typeId, String name, T item) {
		this(typeId, name, (LTexture) null, item);
	}

	public Item(String name, LTexture tex, T item) {
		this(0, name, tex, item);
	}

	public Item(int typeId, String name, LTexture tex, T item) {
		this(typeId, name, tex, 0f, 0f, 0f, 0f, item);
	}

	public Item(int typeId, String name, LTexture tex, float x, float y, float w, float h, T item) {
		this._typeId = typeId;
		this._name = name;
		this._item = item;
		this._image = tex;
		if (x == 0f && y == 0f && w == 0f && h == 0f) {
			this._itemArea = null;
		} else {
			this._itemArea = new RectBox(x, y, w, h);
		}
	}

	public Item<T> setItemChanged(EventActionT<Item<T>> eve) {
		this._itemChanged = eve;
		return this;
	}

	public EventActionT<Item<T>> getItemChanged() {
		return this._itemChanged;
	}

	public Item<T> setArea(float x, float y, float w, float h) {
		if (this._itemArea == null) {
			this._itemArea = new RectBox(x, y, w, h);
		} else {
			this._itemArea.setBounds(x, y, w, h);
		}
		if (this._itemChanged != null) {
			this._itemChanged.update(this);
		}
		return this;
	}

	@Override
	public String getName() {
		return this._name;
	}

	public Item<T> setName(String name) {
		this._name = name;
		if (this._itemChanged != null) {
			this._itemChanged.update(this);
		}
		return this;
	}

	@Override
	public T getItem() {
		return this._item;
	}

	public Item<T> setItem(T o) {
		this._item = o;
		if (o != null && this._itemChanged != null) {
			this._itemChanged.update(this);
		}
		return this;
	}

	@Override
	public LTexture getTexture() {
		return _image;
	}

	@Override
	public RectBox getArea() {
		return _itemArea;
	}

	public boolean isPositionOut(float posX, float posY) {
		return _itemArea.inPoint(posX, posY);
	}

	public boolean isPositionOut(XYZW rect) {
		return _itemArea.inRect(rect);
	}

	@Override
	public int getItemTypeId() {
		return _typeId;
	}

	public Item<T> setItemTypeId(int id) {
		this._typeId = id;
		return this;
	}

	@Override
	public void update() {
		update(0f);
	}

	@Override
	public boolean isUsed() {
		return _used;
	}

	@Override
	public IItem setUse(boolean u) {
		if (this._used == u) {
			return this;
		}
		this._used = u;
		if (_itemChanged != null) {
			_itemChanged.update(this);
		}
		return this;
	}

	public Item<T> setDescription(String d) {
		this._description = d;
		return this;
	}

	@Override
	public String getDescription() {
		return _description;
	}

	public boolean equals(Item<T> e) {
		if (e == null) {
			return false;
		}
		if (e == this) {
			return true;
		}
		final boolean checkItem = e._item != null;
		final boolean checkName = e._name != null;
		final boolean checkDes = e._description != null;
		final boolean checkTexture = e._image != null;
		final boolean checkRect = e._itemArea != null;

		if (checkItem && _item == null) {
			return false;
		}
		if (checkTexture && _image == null) {
			return false;
		}
		if (checkRect && _itemArea == null) {
			return false;
		}
		if (checkName && _name == null) {
			return false;
		}
		if (checkDes && _description == null) {
			return false;
		}
		if (checkItem && !e._item.equals(_item)) {
			return false;
		}
		if (checkName && !e._name.equals(_name)) {
			return false;
		}
		if (checkDes && !e._description.equals(_description)) {
			return false;
		}
		if (checkTexture && !e._image.equals(_image)) {
			return false;
		}
		if (checkRect && !e._itemArea.equals(_itemArea)) {
			return false;
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (o == this) {
			return true;
		}
		if (o instanceof Item) {
			return equals((Item<T>) o);
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hashCode = _item == null ? super.hashCode() : _item.hashCode();
		hashCode = LSystem.unite(hashCode, _typeId);
		if (_itemArea != null) {
			hashCode = LSystem.unite(hashCode, _itemArea.hashCode());
		}
		if (_image != null) {
			hashCode = LSystem.unite(hashCode, _image.hashCode());
		}
		if (_name != null) {
			hashCode = LSystem.unite(hashCode, _name.hashCode());
		}
		if (_description != null) {
			hashCode = LSystem.unite(hashCode, _description.hashCode());
		}
		hashCode = LSystem.unite(hashCode, _used);
		return hashCode;
	}

	public void update(float deltaTime) {
		// 冷却更新
		if (_currentCooldown > 0) {
			_currentCooldown = MathUtils.max(0, _currentCooldown - deltaTime);
			notifyChanged();
		}
		// 使用进度更新
		if (_isUsing && !isCooldown()) {
			_useProgress += deltaTime / _useDuration;
			_useProgress = MathUtils.min(1f, _useProgress);
			notifyChanged();
		}
		if (_itemChanged != null) {
			_itemChanged.update(this);
		}
	}

	/**
	 * 道具是否完成触发
	 */
	public boolean isUseTriggered() {
		return _useProgress >= 1f && !isCooldown() && isCanUse();
	}

	/**
	 * 重置使用状态
	 */
	public void resetUse() {
		_isUsing = false;
		_useProgress = 0f;
		setUse(false);
		notifyChanged();
	}

	/**
	 * 开始使用道具
	 */
	public boolean startUse() {
		if (!isCanUse()) {
			return false;
		}
		_isUsing = true;
		setUse(true);
		notifyChanged();
		return true;
	}

	/**
	 * 结束使用
	 */
	public void finishUse() {
		if (!isCanUse() || !_isUsing) {
			return;
		}
		// 消耗品扣除数量
		if (_consumable && _stackCount > 0) {
			_stackCount--;
		}
		// 有限使用次数扣除
		if (_useCount > 0) {
			_useCount--;
		}
		// 进入冷却
		_currentCooldown = _cooldown;
		resetUse();
	}

	/**
	 * 是否可以使用
	 */
	public boolean isCanUse() {
		if (_isUsing) {
			return false;
		}
		if (isCooldown()) {
			return false;
		}
		if (_useCount == 0) {
			return false;
		}
		if (_consumable && _stackCount <= 0) {
			return false;
		}
		return true;
	}

	/**
	 * 是否在冷却中
	 */
	public boolean isCooldown() {
		return _currentCooldown > 0f;
	}

	/**
	 * 绘制道具（战斗/场景中）
	 * 
	 * @param g
	 * @param deltaTime
	 * @param x
	 * @param y
	 */
	public void drawItemEffect(GLEx g, float deltaTime, float x, float y) {
		if (_image == null || _itemArea == null) {
			return;
		}
		g.draw(_image, x + _drawLocation.x + _itemArea.x, y + _drawLocation.y + _itemArea.y, _itemArea.getWidth(),
				_itemArea.getHeight());
	}

	public Item<T> setTexture(LTexture texture) {
		this._image = texture;
		notifyChanged();
		return this;
	}

	public int getStackCount() {
		return _stackCount;
	}

	public Item<T> setStackCount(int stackCount) {
		this._stackCount = MathUtils.min(stackCount, _maxStack);
		notifyChanged();
		return this;
	}

	public int getMaxStack() {
		return _maxStack;
	}

	public Item<T> setMaxStack(int maxStack) {
		this._maxStack = MathUtils.max(1, maxStack);
		notifyChanged();
		return this;
	}

	public float getPrice() {
		return _price;
	}

	public Item<T> setPrice(float price) {
		this._price = MathUtils.max(0, price);
		notifyChanged();
		return this;
	}

	public ItemRarity getRarity() {
		return _rarity;
	}

	public Item<T> setRarity(ItemRarity rarity) {
		this._rarity = rarity == null ? ItemRarity.COMMON : rarity;
		notifyChanged();
		return this;
	}

	public boolean isConsumable() {
		return _consumable;
	}

	public Item<T> setConsumable(boolean consumable) {
		this._consumable = consumable;
		notifyChanged();
		return this;
	}

	public boolean isSellable() {
		return _sellable;
	}

	public Item<T> setSellable(boolean sellable) {
		this._sellable = sellable;
		notifyChanged();
		return this;
	}

	public boolean isDiscardable() {
		return _discardable;
	}

	public Item<T> setDiscardable(boolean discardable) {
		this._discardable = discardable;
		notifyChanged();
		return this;
	}

	public float getCooldown() {
		return _cooldown;
	}

	public Item<T> setCooldown(float cooldown) {
		this._cooldown = MathUtils.max(0, cooldown);
		notifyChanged();
		return this;
	}

	public int getUseCount() {
		return _useCount;
	}

	public Item<T> setUseCount(int useCount) {
		this._useCount = useCount;
		notifyChanged();
		return this;
	}

	public int getUseRange() {
		return _useRange;
	}

	public Item<T> setUseRange(int useRange) {
		this._useRange = MathUtils.max(0, useRange);
		notifyChanged();
		return this;
	}

	public boolean isCanUseInCombat() {
		return _canUseInCombat;
	}

	public Item<T> setCanUseInCombat(boolean canUseInCombat) {
		this._canUseInCombat = canUseInCombat;
		notifyChanged();
		return this;
	}

	public boolean isCanUseOutOfCombat() {
		return _canUseOutOfCombat;
	}

	public Item<T> setCanUseOutOfCombat(boolean canUseOutOfCombat) {
		this._canUseOutOfCombat = canUseOutOfCombat;
		notifyChanged();
		return this;
	}

	private void notifyChanged() {
		if (_itemChanged != null) {
			_itemChanged.update(this);
		}
	}

	public float getCurrentCooldown() {
		return _currentCooldown;
	}

	public void setCurrentCooldown(float currentCooldown) {
		this._currentCooldown = currentCooldown;
	}

	public boolean isUsing() {
		return _isUsing;
	}

	public void setUsing(boolean isUsing) {
		this._isUsing = isUsing;
	}

	public float getUseDuration() {
		return _useDuration;
	}

	public void setUseDuration(float useDuration) {
		this._useDuration = useDuration;
	}

	public float getUseProgress() {
		return _useProgress;
	}

	public void setUseProgress(float useProgress) {
		this._useProgress = useProgress;
	}

	public Vector2f getDrawLocation() {
		return _drawLocation;
	}

	public void setDrawLocation(float x, float y) {
		_drawLocation.set(x, y);
	}

	public Item<T> addGold(float i) {
		_price += i;
		notifyChanged();
		return this;
	}

	public Item<T> subGold(float i) {
		_price -= i;
		notifyChanged();
		return this;
	}

	public Item<T> mulGold(float i) {
		_price *= i;
		notifyChanged();
		return this;
	}

	public Item<T> divGold(float i) {
		_price /= i;
		notifyChanged();
		return this;
	}

	public Item<T> setGold(float i) {
		_price = i;
		notifyChanged();
		return this;
	}

	public float getGold() {
		return _price;
	}

	@Override
	public void close() {
		if (_image != null) {
			_image.close();
			_image = null;
		}
	}

}
