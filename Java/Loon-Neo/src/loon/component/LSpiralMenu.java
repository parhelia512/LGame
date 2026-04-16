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

import loon.LSysException;
import loon.LTexture;
import loon.canvas.LColor;
import loon.component.skin.SkinManager;
import loon.events.Touched;
import loon.font.IFont;
import loon.font.LFont;
import loon.opengl.GLEx;
import loon.opengl.LSTRDictionary;
import loon.utils.Easing;
import loon.utils.MathUtils;
import loon.utils.StrBuilder;
import loon.utils.TArray;
import loon.utils.timer.Duration;

/**
 * 螺旋菜单，用于以指定的排列方式生成或隐藏一组螺旋出现和隐藏的按钮使用.子菜单在构造时就生成，默认隐藏，expandMenu时播放动画显示
 */
public class LSpiralMenu extends LContainer {

	/**
	 * 螺旋菜单子按钮布局样式
	 */
	public static enum LayoutMode {
		// 椭圆环绕
		ELLIPSE,
		// 左右两排
		LEFT_RIGHT,
		// 上下两排
		TOP_BOTTOM,
		// 上多下少
		TOP_MORE_BOTTOM_LESS,
		// 上少下多
		TOP_LESS_BOTTOM_MORE,
		// 网格排序
		GRID,
		// 半圆在上方
		ARC_TOP,
		// 半圆在下方
		ARC_BOTTOM,
		// 多层环绕
		RING_GRID
	}

	public static interface MenuStateListener {

		void onExpandStart();

		void onExpandFinish();

		void onCollapseStart();

		void onCollapseFinish();
	}

	public static class SpiralButton extends LClickButton {

		private float _startX, _startY;
		private float _targetX, _targetY;
		private float _elapsed = 0f;
		private float _duration;
		private boolean _expanding;

		private float _rotationSpeed = 180f;
		private float _rotationAngle = 0f;
		private int _rotationDirection;
		private float _fadeDelay = 0f;

		private boolean _pulsing = false;
		private float _pulseElapsed = 0f;
		private float _pulseDuration = 0.3f;

		public SpiralButton(String text, int x, int y, int width, int height, float duration, int rotationDirection) {
			super(text, x, y, width, height);
			this._startX = x;
			this._startY = y;
			this._duration = duration;
			this._rotationDirection = rotationDirection;
			setGrayButton(true);
			setPosition(_startX, _startY);
			setScale(0f);
			setAlpha(0f);
			setVisible(false);
		}

		@Override
		public void process(final long elapsedTime) {
			float delta = Duration.toS(elapsedTime);
			_elapsed += delta;

			if (_elapsed >= _fadeDelay) {
				float progress = MathUtils.min(1f, (_elapsed - _fadeDelay) / _duration);
				float interp = _expanding ? Easing.sineEaseIn(progress) : Easing.sineEaseOut(progress);

				float newX = _startX + (_targetX - _startX) * interp;
				float newY = _startY + (_targetY - _startY) * interp;
				setPosition(newX, newY);

				float scale = _expanding ? interp : (1f - interp);
				setScale(scale);

				float alpha = _expanding ? interp : (1f - interp);
				setAlpha(alpha);

				if (_expanding) {
					setVisible(true);
				} else {
					if (alpha <= 0.01f) {
						setVisible(false);
					}
				}

				if (progress < 1f && alpha > 0f) {
					_rotationAngle += _rotationSpeed * delta * _rotationDirection;
					setRotation(_rotationAngle);
				} else {
					setRotation(0f);
				}
			}

			if (_pulsing) {
				_pulseElapsed += delta;
				float progress = _pulseElapsed / _pulseDuration;
				if (progress >= 1f) {
					_pulsing = false;
					setScale(1f);
				} else {
					float pulseScale = 1f + 0.2f * MathUtils.sin(progress * MathUtils.PI);
					setScale(pulseScale);
				}
			}
		}

		public void triggerPulse() {
			_pulsing = true;
			_pulseElapsed = 0f;
		}

		public void setTarget(float targetX, float targetY, boolean expanding) {
			this._startX = getX();
			this._startY = getY();
			this._targetX = targetX;
			this._targetY = targetY;
			this._elapsed = 0f;
			this._expanding = expanding;
			if (_expanding) {
				setVisible(true);
				setAlpha(0f);
				setScale(0f);
			}
		}

		public void setFadeDelay(float delay) {
			this._fadeDelay = delay;
		}

		public void setRotationSpeed(float speed) {
			this._rotationSpeed = speed;
		}

		public float getRotationSpeed() {
			return _rotationSpeed;
		}

		@Override
		public SpiralButton reset() {
			super.reset();
			setPosition(_startX, _startY);
			setScale(0f);
			setAlpha(0f);
			setVisible(false);
			_elapsed = 0;
			_rotationAngle = 0;
			setRotation(0);
			return this;
		}
	}

	public static interface SkillButtonListener {
		void onSkillClicked(int index);
	}

	private final TArray<SpiralButton> _skillButtons = new TArray<>();
	private final LClickButton _mainButton;

	private float _childButtonWidth;
	private float _childButtonHeight;
	private float _duration = 0.5f;
	private boolean _expanded = false;
	private int _rotationDirection = 1;
	private float _delayScale = 0.08f;

	private SkillButtonListener _listener;
	private LayoutMode _layoutMode = LayoutMode.ELLIPSE;
	private String[] _menuTexts;

	private MenuStateListener _stateListener;

	public LSpiralMenu(final String text, final int btnW, final int btnH, final String[] texts, int x, int y,
			int menuWidth, int menuHeight) {
		this(SkinManager.get().getClickButtonSkin().getFont(), text, btnW, btnH, texts, x, y, menuWidth, menuHeight);
	}

	public LSpiralMenu(final IFont font, final String text, final int btnW, final int btnH, final String[] texts, int x,
			int y, int menuWidth, int menuHeight) {
		this(font, text, DefUI.createDefaultButton(LColor.gray, btnW, btnH), btnW, btnH, texts, x, y, menuWidth, menuHeight);
	}

	public LSpiralMenu(final IFont font, final String text, final LTexture image, final int btnW, final int btnH,
			final String[] texts, int x, int y, int menuWidth, int menuHeight) {
		this(font, LClickButton.make(font, text, btnW, btnH, image), texts, x, y, menuWidth, menuHeight);
	}

	public LSpiralMenu(final IFont font, final String text, final LTexture hover, final LTexture clicked,
			final int btnW, final int btnH, final String[] texts, int x, int y, int menuWidth, int menuHeight) {
		this(font, LClickButton.make(font, text, btnW, btnH, hover, clicked), texts, x, y, menuWidth, menuHeight);
	}

	public LSpiralMenu(IFont font, LClickButton mainBtn, final String[] texts, int x, int y, int w, int h) {
		super(x, y, w, h);
		if (mainBtn == null) {
			throw new LSysException("The main button cannot be null !");
		}
		this._menuTexts = texts;
		this._mainButton = mainBtn;
		_mainButton.setFont(font);
		_mainButton.setGrayButton(true);
		add(_mainButton);
		centerOn(_mainButton);
		setLocked(false);

		_childButtonWidth = _mainButton.getWidth();
		_childButtonHeight = _mainButton.getHeight();

		createAllButtonsOnce();

		_mainButton.up(new Touched() {
			@Override
			public void on(float x, float y) {
				if (!_expanded) {
					expandMenu();
				} else {
					collapseMenu();
				}
				_mainButton.focusIn();
			}
		});
	}

	private void createAllButtonsOnce() {
		if (_menuTexts == null || _menuTexts.length == 0) {
			return;
		}
		float cx = _mainButton.getX();
		float cy = _mainButton.getY();
		_skillButtons.clear();

		final StrBuilder sbr = new StrBuilder();

		for (String txt : _menuTexts) {
			SpiralButton btn = new SpiralButton(txt, MathUtils.ifloor(cx), MathUtils.ifloor(cy),
					MathUtils.ifloor(_childButtonWidth), MathUtils.ifloor(_childButtonHeight), _duration,
					_rotationDirection);

			if (_mainButton.getIdleClick() != null) {
				btn.setIdleClick(_mainButton.getIdleClick());
			}
			if (_mainButton.getHoverClick() != null) {
				btn.setHoverClick(_mainButton.getHoverClick());
			}
			if (_mainButton.getClickedClick() != null) {
				btn.setClickedClick(_mainButton.getClickedClick());
			}

			sbr.append(txt);

			btn.up(new Touched() {
				@Override
				public void on(float x, float y) {
					if (_listener != null) {
						_listener.onSkillClicked(_skillButtons.indexOf(btn, true));
					}
				}
			});

			add(btn);
			_skillButtons.add(btn);
		}

		if (_mainButton.getFont() != null && _mainButton.getFont() instanceof LFont) {
			LSTRDictionary.get().bind((LFont) _mainButton.getFont(), sbr.toString());
		}
	}

	public void expandMenu() {
		if (_expanded) {
			return;
		}
		_expanded = true;

		if (_stateListener != null) {
			_stateListener.onExpandStart();
		}

		float cx = _mainButton.getX();
		float cy = _mainButton.getY();
		int count = _menuTexts.length;

		switch (_layoutMode) {
		case ELLIPSE:
			layoutEllipse(cx, cy, count);
			break;
		case LEFT_RIGHT:
			layoutLeftRight(cx, cy, count);
			break;
		case TOP_BOTTOM:
			layoutTopBottom(cx, cy, count);
			break;
		case TOP_MORE_BOTTOM_LESS:
			layoutTopMore(cx, cy, count);
			break;
		case TOP_LESS_BOTTOM_MORE:
			layoutBottomMore(cx, cy, count);
			break;
		case GRID:
			layoutGrid(cx, cy, count);
			break;
		case ARC_TOP:
			layoutArc(cx, cy, count, true);
			break;
		case ARC_BOTTOM:
			layoutArc(cx, cy, count, false);
			break;
		case RING_GRID:
			layoutRing(cx, cy, count);
			break;
		}
	}

	public void collapseMenu() {
		if (!_expanded) {
			return;
		}
		_expanded = false;

		if (_stateListener != null) {
			_stateListener.onCollapseStart();
		}

		float cx = _mainButton.getX();
		float cy = _mainButton.getY();

		for (int i = 0; i < _skillButtons.size(); i++) {
			SpiralButton btn = _skillButtons.get(i);
			btn.setTarget(cx, cy, false);
			btn.setFadeDelay(i * _delayScale);
		}
	}

	private void layoutEllipse(float cx, float cy, int count) {
		float rx = (_mainButton.getWidth() / 2 + _childButtonWidth / 2) * 1.6f;
		float ry = (_mainButton.getHeight() / 2 + _childButtonHeight / 2) * 1.6f;
		float step = 360f / count;
		for (int i = 0; i < count; i++) {
			float a = i * step;
			float tx = cx + rx * MathUtils.cosDeg(a);
			float ty = cy + ry * MathUtils.sinDeg(a);
			_skillButtons.get(i).setTarget(tx, ty, true);
			_skillButtons.get(i).setFadeDelay(i * _delayScale);
		}
	}

	private void layoutLeftRight(float cx, float cy, int count) {
		int half = count / 2;
		float space = _childButtonWidth * 1.3f;
		float oy = _mainButton.getHeight() * 1.2f;
		for (int i = 0; i < count; i++) {
			float ox = (i < half ? -(half - i) : (i - half)) * space;
			float tx = cx + ox;
			float ty = cy + (i < half ? -oy : oy);
			_skillButtons.get(i).setTarget(tx, ty, true);
			_skillButtons.get(i).setFadeDelay(i * _delayScale);
		}
	}

	private void layoutTopBottom(float cx, float cy, int count) {
		int half = count / 2;
		float space = _childButtonHeight * 1.3f;
		float ox = _mainButton.getWidth() * 1.2f;
		for (int i = 0; i < count; i++) {
			float oy = (i < half ? -(half - i) : (i - half)) * space;
			float tx = cx + (i < half ? -ox : ox);
			float ty = cy + oy;
			_skillButtons.get(i).setTarget(tx, ty, true);
			_skillButtons.get(i).setFadeDelay(i * _delayScale);
		}
	}

	private void layoutTopMore(float cx, float cy, int count) {
		int top = MathUtils.ifloor(count * 0.7f);
		int btm = count - top;
		float space = _childButtonWidth * 1.2f;
		for (int i = 0; i < top; i++) {
			float tx = cx + (i - top / 2f) * space;
			float ty = cy - _childButtonHeight * 2;
			_skillButtons.get(i).setTarget(tx, ty, true);
			_skillButtons.get(i).setFadeDelay(i * _delayScale);
		}
		for (int i = 0; i < btm; i++) {
			float tx = cx + (i - btm / 2f) * space;
			float ty = cy + _childButtonHeight * 2;
			_skillButtons.get(top + i).setTarget(tx, ty, true);
			_skillButtons.get(top + i).setFadeDelay((top + i) * _delayScale);
		}
	}

	private void layoutBottomMore(float cx, float cy, int count) {
		int btm = MathUtils.ifloor(count * 0.7f);
		int top = count - btm;
		float space = _childButtonWidth * 1.2f;
		for (int i = 0; i < top; i++) {
			float tx = cx + (i - top / 2f) * space;
			float ty = cy - _childButtonHeight * 2;
			_skillButtons.get(i).setTarget(tx, ty, true);
			_skillButtons.get(i).setFadeDelay(i * _delayScale);
		}
		for (int i = 0; i < btm; i++) {
			float tx = cx + (i - btm / 2f) * space;
			float ty = cy + _childButtonHeight * 2;
			_skillButtons.get(top + i).setTarget(tx, ty, true);
			_skillButtons.get(top + i).setFadeDelay((top + i) * _delayScale);
		}
	}

	private void layoutGrid(float cx, float cy, int count) {
		int cols = MathUtils.ceil(MathUtils.sqrt(count));
		int rows = MathUtils.ceil(count / (float) cols);
		float sx = _childButtonWidth * 1.5f;
		float sy = _childButtonHeight * 1.5f;
		float ox = (cols - 1) * sx / 2f;
		float oy = (rows - 1) * sy / 2f;
		int idx = 0;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				if (idx >= count) {
					break;
				}
				float tx = cx + (c * sx - ox);
				float ty = cy + (r * sy - oy);
				_skillButtons.get(idx).setTarget(tx, ty, true);
				_skillButtons.get(idx).setFadeDelay(idx * _delayScale);
				idx++;
			}
		}
	}

	private void layoutArc(float cx, float cy, int count, boolean top) {
		float rx = (_mainButton.getWidth() / 2 + _childButtonWidth / 2) * 1.6f;
		float ry = (_mainButton.getHeight() / 2 + _childButtonHeight / 2) * 1.6f;
		float start = top ? 180 : 0;
		float step = 180f / (count - 1);
		for (int i = 0; i < count; i++) {
			float a = start + i * step;
			float tx = cx + rx * MathUtils.cosDeg(a);
			float ty = cy + ry * MathUtils.sinDeg(a);
			_skillButtons.get(i).setTarget(tx, ty, true);
			_skillButtons.get(i).setFadeDelay(i * _delayScale);
		}
	}

	private void layoutRing(float cx, float cy, int count) {
		float base = (_mainButton.getWidth() / 2 + _childButtonWidth / 2) * 2f;
		int idx = 0, ring = 0;
		while (idx < count) {
			float r = base + ring * _childButtonHeight * 2f;
			int max = MathUtils.floor((2 * MathUtils.PI * r) / (_childButtonWidth * 1.2f));
			max = MathUtils.max(max, 1);
			int num = MathUtils.min(max, count - idx);
			float step = 360f / num;
			for (int i = 0; i < num; i++) {
				float a = i * step;
				float tx = cx + r * MathUtils.cosDeg(a);
				float ty = cy + r * MathUtils.sinDeg(a);
				_skillButtons.get(idx).setTarget(tx, ty, true);
				_skillButtons.get(idx).setFadeDelay(idx * _delayScale);
				idx++;
			}
			ring++;
		}
	}

	public void setStateListener(MenuStateListener listener) {
		this._stateListener = listener;
	}

	public void setAnimationDuration(float duration) {
		this._duration = duration;
		for (SpiralButton btn : _skillButtons) {
			btn._duration = duration;
		}
	}

	public void setDelayScale(float delayScale) {
		this._delayScale = delayScale;
	}

	public void setAllButtonRotationSpeed(float speed) {
		for (SpiralButton btn : _skillButtons) {
			btn.setRotationSpeed(speed);
		}
	}

	public void forceCollapseImmediately() {
		_expanded = false;
		float cx = _mainButton.getX();
		float cy = _mainButton.getY();
		for (SpiralButton btn : _skillButtons) {
			btn.setPosition(cx, cy);
			btn.setAlpha(0);
			btn.setScale(0);
			btn.setVisible(false);
		}
	}

	public boolean isExpanded() {
		return _expanded;
	}

	public int getButtonCount() {
		return _skillButtons.size;
	}

	public LClickButton getMainButton() {
		return _mainButton;
	}

	public void setSkillButtonListener(SkillButtonListener listener) {
		this._listener = listener;
	}

	public void setLayoutMode(LayoutMode mode) {
		this._layoutMode = mode;
	}

	public LayoutMode getLayoutMode() {
		return _layoutMode;
	}

	public SpiralButton getButtonByIndex(int index) {
		return index >= 0 && index < _skillButtons.size ? _skillButtons.get(index) : null;
	}

	public SpiralButton getButtonByName(String name) {
		for (SpiralButton b : _skillButtons) {
			if (b.getText().equals(name)) {
				return b;
			}
		}
		return null;
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
	}

	@Override
	public String getUIName() {
		return "SpiralMenu";
	}

	@Override
	public void destory() {
		_skillButtons.clear();
	}
}
