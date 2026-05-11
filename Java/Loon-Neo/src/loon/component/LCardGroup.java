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
import loon.events.EventActionT;
import loon.events.QueryEvent;
import loon.opengl.GLEx;
import loon.utils.Easing;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;
import loon.utils.TimeUtils;

/**
 * 卡牌组管理组件,用于把一组注入此类的组件按照卡片方式展示并进行集合操作
 */
public class LCardGroup extends LContainer {

	/**
	 * 洗牌阶段枚举
	 */
	public enum ShufflePhase {
		IDLE, GATHER, RANDOMIZE, DISTRIBUTE
	}

	/**
	 * 洗牌方式枚举
	 */
	public enum ShuffleType {
		FISHER_YATES, RIFFLE, OVERHAND, STRIP, MONGOOSE, PERFECT_RIFFLE
	}

	private static class CardTween {

		private final LColor resultColor = new LColor();

		LComponent comp;

		float fromX, fromY, toX, toY;
		float fromScale, toScale;
		float fromAlpha, toAlpha;
		float fromRot, toRot;
		LColor fromColor, toColor;

		long startTime;
		long duration;

		Easing easing;

		boolean finished;
		boolean removed;

		EventActionT<LComponent> onComplete;

		CardTween(LComponent comp, float toX, float toY, float toScale, float toAlpha, float toRot, LColor tColor,
				long startTime, long duration, Easing easing, boolean remove, EventActionT<LComponent> onComplete) {
			this.comp = comp;
			this.fromX = comp.getX();
			this.fromY = comp.getY();
			this.fromColor = comp.getColor();
			this.toColor = tColor;
			this.toX = toX;
			this.toY = toY;
			this.fromScale = comp.getScaleX();
			this.toScale = toScale;
			this.fromAlpha = comp.getAlpha();
			this.toAlpha = toAlpha;
			this.fromRot = comp.getRotation();
			this.toRot = toRot;
			this.startTime = startTime;
			this.duration = MathUtils.max(1, duration);
			this.easing = easing == null ? Easing.TIME_LINEAR : easing;
			this.finished = false;
			this.removed = remove;
			this.onComplete = onComplete;
		}

		void update(long now) {
			if (finished || comp == null) {
				return;
			}
			if (now < startTime) {
				return;
			}
			float t = (now - startTime) / (float) duration;
			if (t >= 1f) {
				t = 1f;
			}
			float e = easing.apply(t);
			float nx = MathUtils.lerp(fromX, toX, e);
			float ny = MathUtils.lerp(fromY, toY, e);
			float ns = MathUtils.lerp(fromScale, toScale, e);
			float na = MathUtils.lerp(fromAlpha, toAlpha, e);
			float nr = MathUtils.lerp(fromRot, toRot, e);
			comp.setLocation(nx, ny);
			comp.setScale(ns);
			comp.setAlpha(na);
			comp.setRotation(nr);
			if (fromColor != null && toColor != null) {
				comp.setColor(LColor.lerp(fromColor, toColor, e, resultColor));
			}
			if (t >= 1f) {
				finished = true;
			}
		}
	}

	private ObjectMap<LComponent, Float> _targetX = new ObjectMap<LComponent, Float>();
	private ObjectMap<LComponent, Float> _targetY = new ObjectMap<LComponent, Float>();
	private ObjectMap<LComponent, Float> _startX = new ObjectMap<LComponent, Float>();
	private ObjectMap<LComponent, Float> _startY = new ObjectMap<LComponent, Float>();
	private ObjectMap<LComponent, Integer> _gatherOrder = new ObjectMap<LComponent, Integer>();

	private LComponent _lastClickedCard;
	private HorizontalAlign _alignment;

	private int _defaultSortOrder;

	private boolean _forceFitContainer;
	private boolean _updateCards;
	private boolean _middleProtrusion;

	private LColor _disabledColor = LColor.darkGray;
	private LColor _selectedColor;
	// 是否启用洗牌动画
	private boolean _enableShuffleAnimation = true;

	private float _baseScale = 1.0f;
	private float _baseAlpha = 1.0f;
	private float _clickedScale = 1.2f;
	private float _clickedAlpha = 0.8f;
	private float _cardRotation;
	private float _heightOffset;
	private float _verticalOffsetY;
	private boolean _clickCardToMoveUp;

	private LComponent _hoveredCard;

	// 卡牌间距
	private float _cardSpacing = 0f;
	private float _cardShuffleOffset = 8f;
	// 自动更新布局
	private boolean _autoUpdateLayout = true;

	private ShufflePhase _shufflePhase = ShufflePhase.IDLE;
	private boolean _isShuffling = false;
	private long _shuffleTotalDuration = 1200L;
	private long _distributeDuration = 500L;
	private int _staggerValue = 80;

	private LComponent[] _backupChilds = null;

	private Easing _ease = Easing.TIME_EASE_OUT;

	private ShuffleType _shuffleType = ShuffleType.FISHER_YATES;
	private boolean _shuffleDisableInteract = true;

	private final TArray<CardTween> _tweens = new TArray<CardTween>();

	private boolean _autoShuffleEnabled = false;
	private long _autoShuffleInterval = 0L;
	private long _lastAutoShuffleTime = 0L;

	private boolean _autoDrawAfterShuffle = false;
	private float _autoDrawTargetX = 0f;
	private float _autoDrawTargetY = 0f;
	private float _autoDrawTargetScale = 1f;
	private float _autoDrawTargetAlpha = 1f;
	private float _autoDrawTargetRotation = 0f;
	private LColor _autoDrawTargetColor = LColor.white;
	private long _autoDrawDuration = 600L;

	private long _randomizeWiggleDuration = 220L;
	private long _randomizeWiggleDelayStep = 20L;

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

	/**
	 * 启用自动周期性洗牌
	 * 
	 * @param interval 周期（毫秒）
	 */
	public void enableAutoShuffle(long interval) {
		if (interval <= 0) {
			_autoShuffleEnabled = false;
			return;
		}
		_autoShuffleEnabled = true;
		_autoShuffleInterval = interval;
		_lastAutoShuffleTime = TimeUtils.millis();
	}

	/**
	 * 随机在洗牌结束后自动抽一张卡并飞向目标位置
	 * 
	 * @param targetX
	 * @param targetY
	 * @param targetScale
	 * @param targetAlpha
	 * @param targetRotation
	 * @param targetColor
	 * @param durationMs
	 */
	public void enableAutoDrawAfterShuffle(float targetX, float targetY, float targetScale, float targetAlpha,
			float targetRotation, LColor targetColor, long durationMs) {
		_autoDrawAfterShuffle = true;
		_autoDrawTargetX = targetX;
		_autoDrawTargetY = targetY;
		_autoDrawTargetScale = targetScale;
		_autoDrawTargetAlpha = targetAlpha;
		_autoDrawTargetRotation = targetRotation;
		_autoDrawTargetColor = targetColor;
		_autoDrawDuration = durationMs;
	}

	/**
	 * 不指定卡牌时使用当前点击卡牌播放特效
	 * 
	 * @param tx
	 * @param ty
	 * @param tScale
	 * @param tAlpha
	 * @param tRotation
	 * @param tColor
	 * @param duration
	 */
	public void playCard(float tx, float ty, float tScale, float tAlpha, float tRotation, LColor tColor,
			long duration) {
		playCard(tx, ty, tScale, tAlpha, tRotation, tColor, duration, null);
	}

	/**
	 * 不指定卡牌时使用当前点击卡牌播放特效
	 * 
	 * @param tx
	 * @param ty
	 * @param tScale
	 * @param tAlpha
	 * @param tRotation
	 * @param duration
	 * @param onComplete
	 */
	public void playCard(float tx, float ty, float tScale, float tAlpha, float tRotation, long duration,
			EventActionT<LComponent> onComplete) {
		playCard(tx, ty, tScale, tAlpha, tRotation, _autoDrawTargetColor, duration, onComplete);
	}

	/**
	 * 不指定卡牌时使用当前点击卡牌播放特效
	 * 
	 * @param tx
	 * @param ty
	 * @param tScale
	 * @param tAlpha
	 * @param tRotation
	 * @param tColor
	 * @param duration
	 * @param onComplete
	 */
	public void playCard(float tx, float ty, float tScale, float tAlpha, float tRotation, LColor tColor, long duration,
			EventActionT<LComponent> onComplete) {
		playCard(tx, ty, tScale, tAlpha, tRotation, tColor, duration, _ease, onComplete);
	}

	/**
	 * 不指定卡牌时使用当前点击卡牌播放特效（默认从卡组中飞出并移除）
	 *
	 * @param tx         目标 X
	 * @param ty         目标 Y
	 * @param tScale     目标缩放
	 * @param tAlpha     目标透明度
	 * @param tRotation  目标旋转度
	 * @param tColor
	 * @param duration   动画时长（毫秒）
	 * @param easing     缓动函数（可为null）
	 * @param onComplete 回调事件（可为null）
	 */
	public void playCard(float tx, float ty, float tScale, float tAlpha, float tRotation, LColor tColor, long duration,
			Easing easing, EventActionT<LComponent> onComplete) {
		playCard(null, true, tx, ty, tScale, tAlpha, tRotation, tColor, duration, easing, onComplete);
	}

	/**
	 * 播放指定卡牌的特效动画(主要是出牌，不过其它也行)
	 *
	 * @param card            要播放特效的卡牌；若为null则使用getClickedCard()
	 * @param removeFromGroup 如果为true，则在播放前从卡组中移除该卡
	 * @param tx              目标X
	 * @param ty              目标Y
	 * @param tScale          目标缩放
	 * @param tAlpha          目标透明度
	 * @param tRotation       目标旋转度
	 * @param tColor          目标最终颜色
	 * @param duration        动画时长（毫秒）
	 * @param easing          缓动函数（可为null，使用默认_ease）
	 * @param onComplete      回调事件（可为null）
	 */
	public void playCard(LComponent card, boolean removeFromGroup, float tx, float ty, float tScale, float tAlpha,
			float tRotation, LColor tColor, long duration, Easing easing, EventActionT<LComponent> onComplete) {
		LComponent target = card;
		if (target == null) {
			target = getClickedCard();
		}
		if (target == null) {
			return;
		}
		final LComponent animTarget = target;
		Easing useEase = (easing == null) ? _ease : easing;
		long start = TimeUtils.millis();
		CardTween t = new CardTween(animTarget, tx, ty, tScale, tAlpha, tRotation, tColor, start,
				MathUtils.max(1, duration), useEase, removeFromGroup, onComplete);
		_tweens.add(t);
	}

	/**
	 * 播放指定组件的缓动动画
	 * 
	 * @param c
	 * @param tx
	 * @param ty
	 * @param tScale
	 * @param tAlpha
	 * @param tRot
	 * @param tColor
	 * @param duration
	 * @param delay
	 */
	public void animateTo(LComponent c, float tx, float ty, float tScale, float tAlpha, float tRot, LColor tColor,
			long duration, long delay) {
		animateTo(c, tx, ty, tScale, tAlpha, tRot, tColor, duration, delay, false);
	}

	/**
	 * 播放指定组件的缓动动画
	 * 
	 * @param c
	 * @param tx
	 * @param ty
	 * @param tScale
	 * @param tAlpha
	 * @param tRot
	 * @param tColor
	 * @param duration
	 * @param delay
	 * @param removed
	 */
	public void animateTo(LComponent c, float tx, float ty, float tScale, float tAlpha, float tRot, LColor tColor,
			long duration, long delay, boolean removed) {
		animateTo(c, tx, ty, tScale, tAlpha, tRot, tColor, duration, delay, removed, null);
	}

	/**
	 * 播放指定组件的缓动动画
	 * 
	 * @param c          组件对象
	 * @param tx         目标x轴
	 * @param ty         目标y轴
	 * @param tScale     缩放变更值
	 * @param tAlpha     透明度变更值
	 * @param tRot       旋转变更值
	 * @param tColor     目标颜色
	 * @param duration   动画时长（毫秒）
	 * @param delay      每步延迟（毫秒）
	 * @param removed    播放后是否删除对象（默认false）
	 * @param onComplete 播放完毕回调函数（可为null）
	 */
	public void animateTo(LComponent c, float tx, float ty, float tScale, float tAlpha, float tRot, LColor tColor,
			long duration, long delay, boolean removed, EventActionT<LComponent> onComplete) {
		if (c == null) {
			return;
		}
		long start = TimeUtils.millis() + MathUtils.max(0, delay);
		CardTween t = new CardTween(c, tx, ty, tScale, tAlpha, tRot, tColor, start, duration, _ease, removed,
				onComplete);
		_tweens.add(t);
	}

	public void clearTweens() {
		_tweens.clear();
	}

	public boolean isCardPlaying() {
		return !_tweens.isEmpty();
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
	}

	public void disableAutoDrawAfterShuffle() {
		_autoDrawAfterShuffle = false;
	}

	/**
	 * 获取当前洗牌类型
	 */
	public ShuffleType getShuffleType() {
		return _shuffleType;
	}

	public LCardGroup setShuffleType(ShuffleType type) {
		if (type != null && !_isShuffling) {
			this._shuffleType = type;
		}
		return this;
	}

	public LCardGroup setShuffleDisableInteract(boolean disable) {
		this._shuffleDisableInteract = disable;
		return this;
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
		if (cs == null || _isShuffling) {
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
		if (c == null || _isShuffling) {
			return this;
		}
		remove(c);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardName(String name) {
		if (_isShuffling) {
			return this;
		}
		removeAllName(name);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardFlag(int flag) {
		if (_isShuffling) {
			return this;
		}
		removeAllFlag(flag);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardTag(Object o) {
		if (_isShuffling) {
			return this;
		}
		removeAllTag(o);
		setCardUpdate(false);
		return this;
	}

	public LCardGroup removeCardFlagAndTag(int flag, Object o) {
		if (_isShuffling) {
			return this;
		}
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

	public void endClickedCard() {
		if (_childs == null) {
			return;
		}
		for (int i = _childs.length - 1; i > -1; i--) {
			LComponent comp = _childs[i];
			if (comp == null) {
				continue;
			}
			comp.setColor(LColor.white);
			if (comp == _lastClickedCard && comp.isClickUp()) {
				if (_clickCardToMoveUp) {
					comp.setY(comp.getY() + _heightOffset);
				} else {
					comp.setY(comp.getY() - _heightOffset);
				}
				resetCard(comp);
			} else {
				resetCard(comp);
			}
		}
		if (_lastClickedCard == null || !_lastClickedCard.isClickUp()) {
			_lastClickedCard = null;
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
		if (_childs == null || _isShuffling) {
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
		if (_autoShuffleEnabled && !_isShuffling) {
			long now = TimeUtils.millis();
			if (now - _lastAutoShuffleTime >= _autoShuffleInterval) {
				_lastAutoShuffleTime = now;
				shuffleLayered(getCenterX(), getCenterY(), _cardShuffleOffset, _shuffleTotalDuration, _staggerValue);
			}
		}
		updateTweens();
		if (_isShuffling) {
			if (_shuffleDisableInteract) {
				return;
			}
		} else {
			if (_autoUpdateLayout && !_updateCards) {
				updateCards();
			}
			if (isPointInUI() && isClickUp()) {
				setCardClick();
			}
		}
	}

	private void updateTweens() {
		long now = TimeUtils.millis();
		for (int i = _tweens.size - 1; i >= 0; i--) {
			CardTween t = _tweens.get(i);
			t.update(now);
			if (t.finished) {
				LComponent card = t.comp;
				if (card != null) {
					if (t.onComplete != null) {
						try {
							t.onComplete.update(card);
						} catch (Throwable ex) {
						}
					}
					if (t.removed) {
						try {
							if (containsCard(card)) {
								remove(card);
								setCardUpdate(false);
							}
						} catch (Throwable ex) {
						}
					}
				}
				_tweens.removeIndex(i);
			}
		}
		if (_isShuffling && _shufflePhase == ShufflePhase.GATHER && _tweens.size == 0) {
			startRandomizeWiggle();
		}
		if (_isShuffling && _shufflePhase == ShufflePhase.RANDOMIZE && _tweens.size == 0) {
			finalizeRandomizeAndDistribute();
		}
		if (_isShuffling && _shufflePhase == ShufflePhase.DISTRIBUTE && _tweens.size == 0) {
			finishShuffle();
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
		if (_isShuffling) {
			return this;
		}
		removeChilds();
		resetAllCards();
		setCardUpdate(false);
		return this;
	}

	public LCardGroup setCardDisabled(LComponent card, boolean disabled) {
		if (card == null || _isShuffling) {
			return this;
		}
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
		if (_childs == null || _isShuffling) {
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
	 * 判断是否有上次选中的卡牌
	 */
	public boolean hasLastSelectedCard() {
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
		if (_childs == null || index1 < 0 || index2 < 0 || index1 >= _childs.length || index2 >= _childs.length
				|| _isShuffling) {
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
		if (card == null || index < 0 || _childs == null || index > _childs.length || _isShuffling) {
			return this;
		}
		addCard(card);
		_updateCards = false;
		return this;
	}

	public LCardGroup stackCardsHorizontal(float spacing) {
		stackCards(spacing, 0);
		return this;
	}

	public LCardGroup stackCardsVertical(float spacing) {
		stackCards(0, spacing);
		return this;
	}

	public boolean isCardGroupEmpty() {
		return getCardCount() == 0;
	}

	public LCardGroup setEase(Easing e) {
		if (e == null) {
			return this;
		}
		_ease = e;
		return this;
	}

	public Easing getEase() {
		return _ease;
	}

	public LCardGroup refreshLayout() {
		_updateCards = false;
		updateCards();
		return this;
	}

	public LCardGroup sortCardsAsc() {
		if (_isShuffling || getCardCount() <= 1) {
			return this;
		}
		_updateCards = false;
		return this;
	}

	public LCardGroup sortCardsDesc() {
		if (_isShuffling || getCardCount() <= 1) {
			return this;
		}
		reverseCards();
		_updateCards = false;
		return this;
	}

	/**
	 * 反转卡牌顺序
	 */
	public LCardGroup reverseCards() {
		if (_isShuffling || getCardCount() <= 1) {
			return this;
		}
		int len = _childs.length;
		for (int i = 0; i < len / 2; i++) {
			swap(i, len - 1 - i);
		}
		_updateCards = false;
		return this;
	}

	/**
	 * 随机抽取一张卡牌
	 */
	public LComponent drawRandomCard() {
		if (isCardGroupEmpty() || _isShuffling) {
			return null;
		}
		int index = MathUtils.nextInt(getCardCount() - 1);
		return drawCardAt(index);
	}

	/**
	 * 抽取指定位置卡牌
	 * 
	 * @param index
	 * @return
	 */
	public LComponent drawCardAt(int index) {
		if (index < 0 || index >= getCardCount() || _isShuffling) {
			return null;
		}
		LComponent card = getCardAt(index);
		removeCard(card);
		return card;
	}

	/**
	 * 启动分层洗牌动画
	 */
	public LCardGroup shuffleLayered() {
		return shuffleLayered(_shuffleTotalDuration, _staggerValue);
	}

	/**
	 * 启动分层洗牌动画
	 * 
	 * @param totalDuration
	 * @param staggerMs
	 * @return
	 */
	public LCardGroup shuffleLayered(long totalDuration, int staggerMs) {
		return shuffleLayered(getCenterX(), getCenterY(), totalDuration, staggerMs);
	}

	/**
	 * 启动分层洗牌动画
	 * 
	 * @param x
	 * @param y
	 * @param totalDuration
	 * @param staggerMs
	 * @return
	 */
	public LCardGroup shuffleLayered(float x, float y, long totalDuration, int staggerMs) {
		return shuffleLayered(x, y, 6f, totalDuration, staggerMs);
	}

	/**
	 * 启动分层洗牌动画
	 * 
	 * @param x
	 * @param y
	 * @param offset
	 * @param totalDuration
	 * @param staggerMs
	 * @return
	 */
	public LCardGroup shuffleLayered(float x, float y, float offset, long totalDuration, int staggerMs) {
		if (isEmpty() || _isShuffling) {
			return this;
		}
		if (totalDuration > 0) {
			_shuffleTotalDuration = totalDuration;
		}
		if (staggerMs >= 0) {
			_staggerValue = staggerMs;
		}
		long available = MathUtils.max(600, _shuffleTotalDuration);
		_distributeDuration = (long) (available * 0.45f);
		if (_enableShuffleAnimation) {
			startLayeredShuffle(x, y, offset);
		} else {
			doImmediateShuffle(x, y, offset);
		}
		return this;
	}

	private void doImmediateShuffle(float x, float y, float offset) {
		_isShuffling = true;
		_shufflePhase = ShufflePhase.RANDOMIZE;
		doShuffleByType();
		updateCards();
		_isShuffling = false;
		_shufflePhase = ShufflePhase.IDLE;
		_updateCards = false;
		_verticalOffsetY = -1;
		_cardSpacing = 0f;
		_autoUpdateLayout = true;
		if (_autoDrawAfterShuffle && getCardCount() > 0) {
			int idx = MathUtils.nextInt(getCardCount());
			LComponent drawn = getCardAt(idx);
			if (drawn != null) {
				playCard(drawn, true, _autoDrawTargetX, _autoDrawTargetY, _autoDrawTargetScale, _autoDrawTargetAlpha,
						_autoDrawTargetRotation, _autoDrawTargetColor, _autoDrawDuration, _ease, null);
			}
		}
	}

	public LCardGroup shuffleLayered(ShuffleType type) {
		setShuffleType(type);
		return shuffleLayered();
	}

	public boolean isShuffling() {
		return _isShuffling;
	}

	private void startLayeredShuffle(float x, float y, float offset) {
		final int n = getChildCount();
		if (n <= 0) {
			return;
		}
		updateCards();
		_backupChilds = new LComponent[n];
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			_backupChilds[i] = c;
			if (c != null) {
				_startX.put(c, c.getX());
				_startY.put(c, c.getY());
			}
			_gatherOrder.put(c, i);
		}
		setAllCardsVisible(true);
		setAllCardsAlpha(_baseAlpha);
		_isShuffling = true;
		_shufflePhase = ShufflePhase.GATHER;
		float centerX = x;
		float centerY = y;
		float layerOffset = offset;
		clearTweens();
		for (int i = 0; i < n; i++) {
			LComponent c = _backupChilds[i];
			if (c != null) {
				float gx = centerX + (i - n / 2f) * 0.5f;
				float gy = centerY - i * layerOffset;
				_targetX.put(c, gx);
				_targetY.put(c, gy);
				c.setZOrder(_defaultSortOrder + i + 1);
			}
		}
	}

	private void startRandomizeWiggle() {
		if (!_isShuffling) {
			return;
		}
		_shufflePhase = ShufflePhase.RANDOMIZE;
		final int n = getChildCount();
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			if (c == null) {
				continue;
			}
			float origScale = c.getScaleX();
			float wiggleScale = origScale * (1.04f + MathUtils.random() * 0.02f);
			float origRot = c.getRotation();
			float wiggleRot = origRot + (MathUtils.random() - 0.5f) * 18f;
			long delay = i * _randomizeWiggleDelayStep;
			animateTo(c, c.getX(), c.getY(), wiggleScale, c.getAlpha(), wiggleRot, _autoDrawTargetColor,
					_randomizeWiggleDuration, delay);
		}
	}

	private void finalizeRandomizeAndDistribute() {
		if (!_isShuffling) {
			return;
		}
		doShuffleByType();
		final int n = getChildCount();
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			if (c != null) {
				_startX.put(c, c.getX());
				_startY.put(c, c.getY());
			}
		}
		updateCards();
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			if (c != null) {
				_targetX.put(c, c.getX());
				_targetY.put(c, c.getY());
			}
		}
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			if (c != null) {
				float sx = _startX.containsKey(c) ? _startX.get(c) : c.getX();
				float sy = _startY.containsKey(c) ? _startY.get(c) : c.getY();
				c.setLocation(sx, sy);
				c.setAlpha(1f);
			}
		}
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			if (c != null) {
				long delay = i * _staggerValue;
				animateTo(c, _targetX.get(c), _targetY.get(c), _baseScale, _baseAlpha, c.getRotation(),
						_autoDrawTargetColor, _distributeDuration, delay);
			}
		}
		_shufflePhase = ShufflePhase.DISTRIBUTE;
	}

	private void finishShuffle() {
		_isShuffling = false;
		_shufflePhase = ShufflePhase.IDLE;
		_updateCards = false;
		_verticalOffsetY = -1;
		_cardSpacing = 0f;
		_autoUpdateLayout = true;
		updateCards();
		_startX.clear();
		_startY.clear();
		_targetX.clear();
		_targetY.clear();
		_gatherOrder.clear();
		if (_autoDrawAfterShuffle && getCardCount() > 0) {
			int idx = MathUtils.nextInt(getCardCount() - 1);
			LComponent drawn = getCardAt(idx);
			if (drawn != null) {
				animateTo(drawn, _autoDrawTargetX, _autoDrawTargetY, _autoDrawTargetScale, _autoDrawTargetAlpha,
						_autoDrawTargetRotation, _autoDrawTargetColor, _autoDrawDuration, 0L);
			}
		}
	}

	public void selectCard(LComponent card) {
		if (card == null || _childs == null) {
			return;
		}
		endClickedCard();
		_lastClickedCard = card;
		selectedCard(card);
	}

	public void selectCardAt(int index) {
		if (_childs == null || index < 0 || index >= getCardCount()) {
			return;
		}
		LComponent c = getCardAt(index);
		selectCard(c);
	}

	public void selectRandomCard() {
		if (isCardGroupEmpty()) {
			return;
		}
		int idx = MathUtils.nextInt(getCardCount());
		selectCardAt(idx);
	}

	public LComponent selectByQuery(QueryEvent<LComponent> query) {
		if (query == null || _childs == null) {
			return null;
		}
		final int n = getChildCount();
		for (int i = 0; i < n; i++) {
			LComponent c = getChildByIndex(i);
			if (c != null && query.hit(c)) {
				selectCard(c);
				return c;
			}
		}
		return null;
	}

	public void selectNextCard() {
		if (isCardGroupEmpty()) {
			return;
		}
		if (_lastClickedCard == null) {
			selectCardAt(0);
			return;
		}
		int idx = getCardIndex(_lastClickedCard);
		if (idx == -1) {
			selectCardAt(0);
			return;
		}
		int next = (idx + 1) % getCardCount();
		selectCardAt(next);
	}

	public void selectPreviousCard() {
		if (isCardGroupEmpty()) {
			return;
		}
		if (_lastClickedCard == null) {
			selectCardAt(getCardCount() - 1);
			return;
		}
		int idx = getCardIndex(_lastClickedCard);
		if (idx == -1) {
			selectCardAt(getCardCount() - 1);
			return;
		}
		int prev = (idx - 1 + getCardCount()) % getCardCount();
		selectCardAt(prev);
	}

	public void clearSelection() {
		deselectCurrentCard();
	}

	private void doShuffleByType() {
		int n = getChildCount();
		if (n <= 1) {
			return;
		}
		switch (_shuffleType) {
		case FISHER_YATES:
			for (int i = n - 1; i > 0; i--) {
				int j = MathUtils.nextInt(i);
				swap(i, j);
			}
			break;
		case RIFFLE:
			int half = n / 2;
			LComponent[] temp = new LComponent[n];
			int idx = 0;
			for (int i = 0; i < half; i++) {
				if (i < n) {
					temp[idx++] = getChildByIndex(i);
				}
				if (i + half < n) {
					temp[idx++] = getChildByIndex(i + half);
				}
			}
			if (n % 2 != 0) {
				temp[idx] = getChildByIndex(n - 1);
			}
			replaceChilds(temp);
			break;
		case OVERHAND:
			int stacks = MathUtils.nextInt(2) + 3;
			int step = n / stacks;
			LComponent[] over = new LComponent[n];
			int pos = 0;
			for (int i = stacks - 1; i >= 0; i--) {
				int start = i * step;
				int end = (i == stacks - 1) ? n : start + step;
				for (int j = start; j < end; j++) {
					over[pos++] = getChildByIndex(j);
				}
			}
			replaceChilds(over);
			break;
		case STRIP:
			int split = MathUtils.nextInt(n - 2);
			LComponent[] strip = new LComponent[n];
			System.arraycopy(_childs, split, strip, 0, n - split);
			System.arraycopy(_childs, 0, strip, n - split, split);
			replaceChilds(strip);
			break;
		case MONGOOSE:
			doShuffleByType(ShuffleType.STRIP);
			doShuffleByType(ShuffleType.RIFFLE);
			break;
		case PERFECT_RIFFLE:
			int mid = n / 2;
			LComponent[] perfect = new LComponent[n];
			int left = 0, right = mid, p = 0;
			while (left < mid || right < n) {
				if (left < mid) {
					perfect[p++] = getChildByIndex(left++);
				}
				if (right < n) {
					perfect[p++] = getChildByIndex(right++);
				}
			}
			replaceChilds(perfect);
			break;
		}
	}

	private void replaceChilds(LComponent[] newChilds) {
		if (newChilds == null || newChilds.length != _childs.length) {
			return;
		}
		System.arraycopy(newChilds, 0, _childs, 0, newChilds.length);
	}

	private void doShuffleByType(ShuffleType t) {
		ShuffleType old = _shuffleType;
		_shuffleType = t;
		doShuffleByType();
		_shuffleType = old;
	}

	public LCardGroup setCardShuffleOffset(float off) {
		_cardShuffleOffset = off;
		return this;
	}

	public float getCardShuffleOffset() {
		return _cardShuffleOffset;
	}

	public LCardGroup setShuffleAnimationEnabled(boolean enable) {
		this._enableShuffleAnimation = enable;
		return this;
	}

	public boolean isShuffleAnimationEnabled() {
		return this._enableShuffleAnimation;
	}

	public boolean isAutoUpdateLayout() {
		return _autoUpdateLayout;
	}

	public boolean isAutoShuffleEnabled() {
		return _autoShuffleEnabled;
	}

	public void setAutoShuffleEnabled(boolean a) {
		this._autoShuffleEnabled = a;
	}

	public long getAutoShuffleInterval() {
		return _autoShuffleInterval;
	}

	public void setAutoShuffleInterval(long a) {
		this._autoShuffleInterval = a;
	}

	public boolean isAutoDrawAfterShuffle() {
		return _autoDrawAfterShuffle;
	}

	public void set_autoDrawAfterShuffle(boolean a) {
		this._autoDrawAfterShuffle = a;
	}

	public float getAutoDrawTargetX() {
		return _autoDrawTargetX;
	}

	public void setAutoDrawTargetX(float a) {
		this._autoDrawTargetX = a;
	}

	public float getAutoDrawTargetY() {
		return _autoDrawTargetY;
	}

	public void setAutoDrawTargetY(float a) {
		this._autoDrawTargetY = a;
	}

	public float getAutoDrawTargetScale() {
		return _autoDrawTargetScale;
	}

	public void setAutoDrawTargetScale(float a) {
		this._autoDrawTargetScale = a;
	}

	public float getAutoDrawTargetAlpha() {
		return _autoDrawTargetAlpha;
	}

	public void setAutoDrawTargetAlpha(float a) {
		this._autoDrawTargetAlpha = a;
	}

	public float getAutoDrawTargetRotation() {
		return _autoDrawTargetRotation;
	}

	public void setAutoDrawTargetRotation(float a) {
		this._autoDrawTargetRotation = a;
	}

	public LColor getAutoDrawTargetColor() {
		return _autoDrawTargetColor;
	}

	public void setAutoDrawTargetColor(LColor a) {
		this._autoDrawTargetColor = a;
	}

	public long getAutoDrawDuration() {
		return _autoDrawDuration;
	}

	public void setAutoDrawDuration(long a) {
		this._autoDrawDuration = a;
	}

	@Override
	public String getUIName() {
		return "CardGroup";
	}

	@Override
	public void destroy() {
		resetAllCards();
		setCardUpdate(false);
		if (_startX != null) {
			_startX.clear();
		}
		if (_startY != null) {
			_startY.clear();
		}
		if (_targetX != null) {
			_targetX.clear();
		}
		if (_targetY != null) {
			_targetY.clear();
		}
		if (_gatherOrder != null) {
			_gatherOrder.clear();
		}
		clearTweens();
	}

}