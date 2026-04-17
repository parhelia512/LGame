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

import loon.LSystem;
import loon.canvas.LColor;
import loon.component.layout.HorizontalAlign;
import loon.opengl.GLEx;
import loon.utils.MathUtils;

/**
 * 卡牌组管理组件,用于把一组注入此类的组件按照卡片方式展示并进行集合操作
 */
public class LCardGroup extends LContainer {

	private LComponent _lastClickedCard;
	private HorizontalAlign _alignment;
	private int _defaultSortOrder;
	private float _cardRotation;
	private float _heightOffset;
	private float _verticalOffsetY;
	private boolean _clickCardToMoveUp;
	private boolean _forceFitContainer;
	private boolean _updateCards;
	private boolean _middleProtrusion;
	private LColor _selectedColor;
	private float _baseScale = 1.0f;
	private float _baseAlpha = 1.0f;
	private float _clickedScale = 1.2f;
	private float _clickedAlpha = 0.8f;
	private LComponent _hoveredCard;
	private LColor _disabledColor = LColor.darkGray;
	// 卡牌间距
	private float _cardSpacing = 0f;
	// 自动更新布局
	private boolean _autoUpdateLayout = true;

	public LCardGroup() {
		this(-25f);
	}

	public LCardGroup(float rotation) {
		this(rotation, 25f);
	}

	public LCardGroup(float rotation, float heightOffset) {
		this(rotation, heightOffset, 0, 0, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight() / 2);
	}

	public LCardGroup(float rotation, float heightOffset, int x, int y, int w, int h) {
		super(x, y, w, h);
		setCardRotation(rotation);
		setHeightOffset(heightOffset);
		setSelectedColor(LColor.darkRed);
		setAlignment(HorizontalAlign.CENTER);
		setClickCardToMoveUp(true);
		setForceFitContainer(true);
		setMiddleProtrusionCard(true);
		setElastic(false);
		setLocked(true);
		focusIn();
		_verticalOffsetY = -1;
		_cardSpacing = 0f;
		_autoUpdateLayout = true;
	}

	public boolean isMiddleProtrusionCard() {
		return this._middleProtrusion;
	}

	public LCardGroup setMiddleProtrusionCard(boolean m) {
		this._middleProtrusion = m;
		_updateCards = false;
		return this;
	}

	public LCardGroup addCard(LComponent... cs) {
		if (cs == null) {
			return this;
		}
		add(cs);
		setCardUpdate(false);
		return this;
	}

	public int removeCurrentCard() {
		return removeCurrentClickedChild();
	}

	public LCardGroup removeCard(LComponent c) {
		if (c == null) {
			return this;
		}
		remove(c);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardName(String name) {
		removeAllName(name);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardFlag(int flag) {
		removeAllFlag(flag);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardTag(Object o) {
		removeAllTag(o);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardFlagAndTag(int flag, Object o) {
		removeAllFlagAndTag(flag, o);
		setCardUpdate(false);
		return this;
	}

	public boolean isClickCardToMoveUp() {
		return this._clickCardToMoveUp;
	}

	public LCardGroup setClickCardToMoveUp(boolean b) {
		this._clickCardToMoveUp = b;
		return this;
	}

	public LColor getSelectedColor() {
		return this._selectedColor;
	}

	public LCardGroup setSelectedColor(LColor c) {
		if (c == null) {
			return this;
		}
		this._selectedColor = c;
		return this;
	}

	public LCardGroup setHeightOffset(float o) {
		this._heightOffset = o;
		_updateCards = false;
		_verticalOffsetY = -1;
		return this;
	}

	public float getHeightOffset() {
		return _heightOffset;
	}

	public LCardGroup setCardRotation(float r) {
		_cardRotation = r;
		_updateCards = false;
		return this;
	}

	public float getCardRotation() {
		return this._cardRotation;
	}

	@Override
	protected LContainer validateResize() {
		super.validateResize();
		this._updateCards = false;
		return this;
	}

	private void resetCard(LComponent comp) {
		if (comp == null) {
			return;
		}
		comp.setScale(_baseScale);
		comp.setAlpha(_baseAlpha);
		comp.setZOrder(_defaultSortOrder);
	}

	public void endClickedCard() {
		if (_lastClickedCard != null && _lastClickedCard.isClickUp()) {
			_lastClickedCard.setColor(LColor.white);
			if (_clickCardToMoveUp) {
				_lastClickedCard.setY(_lastClickedCard.getY() + _heightOffset);
			} else {
				_lastClickedCard.setY(_lastClickedCard.getY() - _heightOffset);
			}
			resetCard(_lastClickedCard);
		}
	}

	public void selectedCard(LComponent card) {
		if (card != null) {
			card.setScale(_clickedScale);
			card.setColor(_selectedColor);
			card.setAlpha(_clickedAlpha);
			card.setLayer(card.getLayer() + 10);
		}
	}

	public void stackCards(float offsetX, float offsetY) {
		if (_childs == null) {
			return;
		}
		float baseX = getX();
		float baseY = getY();
		for (LComponent comp : _childs) {
			if (comp != null) {
				comp.setLocation(baseX, baseY);
				baseX += offsetX;
				baseY += offsetY;
			}
		}
	}

	public void startClickCard() {
		final LComponent curClicked = getClickedChild();
		if (curClicked != null && curClicked.isClickUp()) {
			if (_clickCardToMoveUp) {
				curClicked.setY(curClicked.getY() - _heightOffset);
			} else {
				curClicked.setY(curClicked.getY() + _heightOffset);
			}
			selectedCard(curClicked);
			_lastClickedCard = curClicked;
		} else if (_lastClickedCard != null) {
			resetCard(_lastClickedCard);
			_lastClickedCard = null;
		}
	}

	public LComponent getClickedCard() {
		return this._lastClickedCard;
	}

	public void updateClickCard() {
		final LComponent curHovered = getClickedCard();
		if (curHovered != null) {
			_hoveredCard = curHovered;
			_hoveredCard.setScale(_clickedScale);
		} else if (_hoveredCard != null) {
			_hoveredCard.setScale(_baseScale);
			_hoveredCard = null;
		}
	}

	public void setClickedAlpha(float ha) {
		_clickedAlpha = ha;
	}

	public float getClickedAlpha() {
		return _clickedAlpha;
	}

	public void setClickedScale(float hs) {
		_clickedScale = hs;
	}

	public float getClickedScale() {
		return _clickedScale;
	}

	public void setBaseScale(float hs) {
		_baseScale = hs;
		_updateCards = false;
	}

	public float getBaseScale() {
		return _baseScale;
	}

	public void setBaseAlpha(float ba) {
		_baseAlpha = ba;
	}

	public float getBaseAlpha() {
		return _baseAlpha;
	}

	@Override
	public void process(long elapsedTime) {
		if (!_component_visible || _destroyed || _childs == null) {
			return;
		}
		if (_autoUpdateLayout && !_updateCards) {
			updateCards();
		}
		if (isPointInUI() && isClickUp()) {
			setCardClick();
		}
	}

	public void setCardClick() {
		endClickedCard();
		startClickCard();
	}

	public boolean isForceFitContainer() {
		return this._forceFitContainer;
	}

	public LCardGroup setForceFitContainer(boolean f) {
		this._forceFitContainer = f;
		_updateCards = false;
		return this;
	}

	public LCardGroup setAlignment(HorizontalAlign h) {
		if (h == null) {
			return this;
		}
		this._alignment = h;
		_updateCards = false;
		return this;
	}

	public HorizontalAlign getAlignment() {
		return this._alignment;
	}

	public LCardGroup updateCards() {
		if (isEmpty()) {
			return this;
		}
		setCardClick();
		setCardsPosition();
		setCardsRotation();
		setChildZOrders(_defaultSortOrder);
		_updateCards = true;
		_verticalOffsetY = -1;
		return this;
	}

	public boolean isCardUpdated() {
		return this._updateCards;
	}

	public LCardGroup setCardUpdate(boolean u) {
		this._updateCards = u;
		return this;
	}

	private void setCardsRotation() {
		if (_childs == null) {
			return;
		}
		final int size = _childs.length;
		for (int i = 0; i < size; i++) {
			final LComponent comp = _childs[i];
			if (comp != null) {
				final float angle = getCardRotation(i);
				comp.setRotation(angle);
				comp.setLocation(comp.getX(), comp.getY() + getCardVerticalOffset(i));
			}
		}
	}

	private void setCardsPosition() {
		final float cardsTotalWidth = getChildTotalWidth() + (_childs.length - 1) * _cardSpacing;
		final float containerWidth = getWidth();
		if (_forceFitContainer && cardsTotalWidth >= containerWidth) {
			matchChildrenToFitContainer(cardsTotalWidth);
		} else {
			matchChildrenWithoutOverlap(cardsTotalWidth);
		}
	}

	private float getAnchorPositionByAlignment(float childrenWidth) {
		if (_alignment == null) {
			return 0f;
		}
		float widthSpace = getWidth();
		switch (_alignment) {
		case LEFT:
			return getCenterX() - widthSpace / 2f;
		case CENTER:
			return getCenterX() - (childrenWidth / 2f);
		case RIGHT:
			return getCenterX() + widthSpace / 2f - childrenWidth;
		default:
			return 0f;
		}
	}

	private float getCardRotation(int index) {
		final int count = getChildCount();
		return (count < 3) ? 0 : -(_cardRotation * (index - (count - 1f) / 2f) / ((count - 1f) / 2f));
	}

	private float getCardVerticalOffset(int index) {
		if (!_updateCards || _verticalOffsetY == -1) {
			final int count = getChildCount();
			if (count < 3) {
				_verticalOffsetY = 0;
				return 0;
			}
			final float result = MathUtils.abs(_heightOffset
					* (1f - MathUtils.pow(index - (count - 1f) / 2f, 2f) / MathUtils.pow((count - 1f) / 2f, 2f)));
			float off = (_childs != null && _childs.length > 0) ? getChildTotalHeight() / count / 3f : 0f;
			_verticalOffsetY = (_middleProtrusion ? -result : result) + off;
		}
		return _verticalOffsetY;
	}

	private void matchChildrenToFitContainer(float childrenTotalWidth) {
		if (_childs == null) {
			return;
		}
		final float width = getWidth();
		final float distanceBetweenChildren = (width - childrenTotalWidth) / (getChildCount() - 1) + _cardSpacing;
		float currentX = getX();
		for (LComponent comp : _childs) {
			if (comp != null) {
				comp.setLocation(currentX, getY());
				currentX += comp.getWidth() + distanceBetweenChildren;
			}
		}
	}

	private void matchChildrenWithoutOverlap(float childrenTotalWidth) {
		if (_childs == null) {
			return;
		}
		float currentPosition = getAnchorPositionByAlignment(childrenTotalWidth);
		for (LComponent comp : _childs) {
			if (comp != null) {
				comp.setLocation(currentPosition, getY());
				currentPosition += comp.getWidth() + _cardSpacing;
			}
		}
	}

	public void resetAllCards() {
		if (_childs == null) {
			return;
		}
		final LComponent[] childs = this._childs;
		final int size = getChildCount();
		for (int i = size - 1; i > -1; i--) {
			LComponent card = childs[i];
			if (card != null) {
				resetCard(card);
			}
		}
		_lastClickedCard = null;
		_hoveredCard = null;
	}

	public LCardGroup addCards(LComponent... cards) {
		return addCard(cards);
	}

	public LCardGroup clearAllCards() {
		removeChilds();
		resetAllCards();
		setCardUpdate(false);
		return this;
	}

	public LCardGroup setCardDisabled(LComponent card, boolean disabled) {
		if (card == null)
			return this;
		if (disabled) {
			card.setColor(_disabledColor);
			card.setEnabled(false);
		} else {
			card.setColor(LColor.white);
			card.setEnabled(true);
		}
		return this;
	}

	public LCardGroup setCardSpacing(float spacing) {
		this._cardSpacing = spacing;
		_updateCards = false;
		return this;
	}

	public float getCardSpacing() {
		return _cardSpacing;
	}

	public LCardGroup setAutoUpdateLayout(boolean auto) {
		this._autoUpdateLayout = auto;
		return this;
	}

	public LComponent getCardAt(int index) {
		return getChildByIndex(index);
	}

	public int getCardIndex(LComponent card) {
		return getChildIndex(card);
	}

	/**
	 * 获取所有卡牌数量
	 */
	public int getCardCount() {
		return getChildCount();
	}

	/**
	 * 判断是否包含指定卡牌
	 * 
	 * @param card
	 * @return
	 */
	public boolean containsCard(LComponent card) {
		return getCardIndex(card) != -1;
	}

	/**
	 * 批量禁用/启用所有卡牌
	 * 
	 * @param disabled
	 * @return
	 */
	public LCardGroup setAllCardsDisabled(boolean disabled) {
		if (_childs == null) {
			return this;
		}
		final LComponent[] childs = this._childs;
		final int size = getChildCount();
		for (int i = size - 1; i > -1; i--) {
			LComponent card = childs[i];
			if (card != null) {
				setCardDisabled(card, disabled);
			}
		}
		return this;
	}

	public LCardGroup setAllCardsVisible(boolean visible) {
		if (_childs == null) {
			return this;
		}
		final LComponent[] childs = this._childs;
		final int size = getChildCount();
		for (int i = size - 1; i > -1; i--) {
			LComponent card = childs[i];
			if (card != null) {
				card.setVisible(visible);
			}
		}
		return this;
	}

	public LCardGroup setAllCardsAlpha(float alpha) {
		if (_childs == null) {
			return this;
		}
		final LComponent[] childs = this._childs;
		final int size = getChildCount();
		for (int i = size - 1; i > -1; i--) {
			LComponent card = childs[i];
			if (card != null) {
				card.setAlpha(alpha);
			}
		}
		return this;
	}

	public LCardGroup setAllCardsScale(float scale) {
		if (_childs == null) {
			return this;
		}
		final LComponent[] childs = this._childs;
		final int size = getChildCount();
		for (int i = size - 1; i > -1; i--) {
			LComponent card = childs[i];
			if (card != null) {
				card.setScale(scale);
			}
		}
		return this;
	}

	/**
	 * 取消当前选中的卡牌
	 */
	public LCardGroup deselectCurrentCard() {
		endClickedCard();
		_lastClickedCard = null;
		return this;
	}

	/**
	 * 判断是否有选中的卡牌
	 */
	public boolean hasSelectedCard() {
		return _lastClickedCard != null;
	}

	/**
	 * 交换两张卡牌的位置
	 * 
	 * @param index1
	 * @param index2
	 * @return
	 */
	public LCardGroup swapCards(int index1, int index2) {
		if (_childs == null || index1 < 0 || index2 < 0 || index1 >= _childs.length || index2 >= _childs.length) {
			return this;
		}
		swap(index1, index2);
		_updateCards = false;
		return this;
	}

	/**
	 * 插入卡牌到指定索引位置
	 * 
	 * @param index
	 * @param card
	 * @return
	 */
	public LCardGroup insertCardAt(int index, LComponent card) {
		if (card == null || index < 0 || _childs == null || index > _childs.length) {
			return this;
		}
		addCard(card);
		_updateCards = false;
		return this;
	}

	/**
	 * 水平堆叠卡牌
	 * 
	 * @param spacing
	 * @return
	 */
	public LCardGroup stackCardsHorizontal(float spacing) {
		stackCards(spacing, 0);
		return this;
	}

	/**
	 * 垂直堆叠卡牌
	 * 
	 * @param spacing
	 * @return
	 */
	public LCardGroup stackCardsVertical(float spacing) {
		stackCards(0, spacing);
		return this;
	}

	/**
	 * 判断卡牌组是否为空
	 */
	public boolean isCardGroupEmpty() {
		return getCardCount() == 0;
	}

	/**
	 * 刷新卡牌布局
	 */
	public LCardGroup refreshLayout() {
		_updateCards = false;
		updateCards();
		return this;
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
	}

	@Override
	public String getUIName() {
		return "CardGroup";
	}

	@Override
	public void destory() {
		resetAllCards();
		setCardUpdate(false);
	}

}
