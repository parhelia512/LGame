/**
 * 
 * Copyright 2014
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
 * @version 0.4.1
 */
package loon.component;

import loon.LSystem;
import loon.LTexture;
import loon.canvas.LColor;
import loon.component.skin.ClickButtonSkin;
import loon.component.skin.SkinManager;
import loon.events.ActionKey;
import loon.events.CallFunction;
import loon.events.QueryEvent;
import loon.events.SysKey;
import loon.font.FontSet;
import loon.font.FontUtils;
import loon.font.IFont;
import loon.font.LFont;
import loon.geom.PointF;
import loon.opengl.GLEx;
import loon.opengl.LSTRDictionary;
import loon.utils.MathUtils;
import loon.utils.StringUtils;
import loon.utils.TArray;

/**
 * 与LButton的差异在于，它内置有默认UI图片，并且可以选择大小，而不是必须按照图片大小拆分
 */
public class LClickButton extends LComponent implements FontSet<LClickButton> {

	private final ActionKey _currentOnTouch = new ActionKey();

	private LTexture _idleClick, _hoverClick, _disableClick;

	private IFont _currentFont;

	private boolean _clickedOver, _grayButton, _lightClickedButton;

	private boolean _allowedSpinner;

	private int _pressedTime, _offsetLeft, _offsetTop;

	private int _selectedIndex;

	private LColor _fontColor;

	private String _currentText = null;

	private CallFunction _function;

	private QueryEvent<LClickButton> _clickConsumer;

	private TArray<String> _items;

	private boolean _toggleMode = false;

	private boolean _toggled = false;

	private String accessibleName = null;

	public static LClickButton makePath(String path) {
		LTexture tex = LSystem.loadTexture(path);
		return new LClickButton(null, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, tex.getWidth(), tex.getHeight(), tex, tex,
				tex);
	}

	public LClickButton(String text, IFont font, LColor color, int x, int y, int width, int height, String path) {
		this(text, font, color, x, y, width, height, LSystem.loadTexture(path), LSystem.loadTexture(path),
				LSystem.loadTexture(path));
	}

	public LClickButton(String text, LColor color, int x, int y, int width, int height) {
		this(text, SkinManager.get().getClickButtonSkin().getFont(), color, x, y, width, height,
				SkinManager.get().getClickButtonSkin().getIdleClickTexture(),
				SkinManager.get().getClickButtonSkin().getHoverClickTexture(),
				SkinManager.get().getClickButtonSkin().getDisableTexture());
	}

	public static LClickButton make(int width, int height, String idle, String hover, String clicked) {
		return new LClickButton(null, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height, LSystem.loadTexture(idle),
				LSystem.loadTexture(hover), LSystem.loadTexture(clicked));
	}

	public static LClickButton make(int width, int height, LTexture idle, LTexture hover, LTexture clicked) {
		return new LClickButton(null, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height, idle, hover, clicked);
	}

	public static LClickButton make(LTexture texture) {
		return new LClickButton(null, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, texture.getWidth(), texture.getHeight(),
				texture, texture, texture);
	}

	public static LClickButton make(String text) {
		return new LClickButton(text, 0, 0, 1, 1);
	}

	public static LClickButton make(String text, int width, int height) {
		return new LClickButton(text, 0, 0, width, height);
	}

	public static LClickButton make(String text, int x, int y, int width, int height) {
		return new LClickButton(text, x, y, width, height);
	}

	public static LClickButton make(String text, int width, int height, String clickPath) {
		LTexture texture = LSystem.loadTexture(clickPath);
		return new LClickButton(text, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height, texture, texture, texture);
	}

	public static LClickButton make(String text, int width, int height, LTexture clicked) {
		return new LClickButton(text, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height, clicked, clicked, clicked);
	}

	public static LClickButton make(IFont font, String text, int x, int y, int width, int height) {
		return new LClickButton(text, font, SkinManager.get().getClickButtonSkin().getFontColor(), x, y, width, height);
	}

	public static LClickButton make(IFont font, String text, int width, int height) {
		return new LClickButton(text, font, SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height);
	}

	public static LClickButton make(IFont font, String text, int width, int height, LTexture clicked) {
		return new LClickButton(text, font, SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height,
				clicked, clicked, clicked);
	}

	public static LClickButton make(String text, int width, int height, LTexture hover, LTexture clicked) {
		return new LClickButton(text, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height, hover, hover, clicked);
	}

	public static LClickButton make(IFont font, String text, int width, int height, LTexture hover, LTexture clicked) {
		return new LClickButton(text, font, SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height,
				hover, hover, clicked);
	}

	public static LClickButton make(String text, int width, int height, LTexture idle, LTexture hover,
			LTexture clicked) {
		return new LClickButton(text, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), 0, 0, width, height, idle, hover, clicked);
	}

	public static LClickButton make(String text, IFont font, LColor fontColor, int width, int height, LTexture idle,
			LTexture hover, LTexture clicked) {
		return new LClickButton(text, font, fontColor, 0, 0, width, height, idle, hover, clicked);
	}

	public LClickButton(String text, int x, int y, int width, int height) {
		this(text, SkinManager.get().getClickButtonSkin().getFont(),
				SkinManager.get().getClickButtonSkin().getFontColor(), x, y, width, height,
				SkinManager.get().getClickButtonSkin().getIdleClickTexture(),
				SkinManager.get().getClickButtonSkin().getHoverClickTexture(),
				SkinManager.get().getClickButtonSkin().getDisableTexture());
	}

	public LClickButton(String text, IFont font, LColor color, int x, int y, int width, int height) {
		this(text, font, color, x, y, width, height, SkinManager.get().getClickButtonSkin().getIdleClickTexture(),
				SkinManager.get().getClickButtonSkin().getHoverClickTexture(),
				SkinManager.get().getClickButtonSkin().getDisableTexture());
	}

	public LClickButton(String text, LColor color, int x, int y, int width, int height, LTexture idle, LTexture hover,
			LTexture clicked) {
		this(text, SkinManager.get().getClickButtonSkin().getFont(), color, x, y, width, height, idle, hover, clicked);
	}

	public LClickButton(String text, LColor color, int x, int y, int width, int height, String a, String b, String c) {
		this(text, SkinManager.get().getClickButtonSkin().getFont(), color, x, y, width, height, LSystem.loadTexture(a),
				LSystem.loadTexture(b), LSystem.loadTexture(c));
	}

	public LClickButton(String text, IFont font, LColor color, int x, int y, int width, int height, String a, String b,
			String c) {
		this(text, font, color, x, y, width, height, LSystem.loadTexture(a), LSystem.loadTexture(b),
				LSystem.loadTexture(c));
	}

	public LClickButton(ClickButtonSkin skin, String text, int x, int y, int width, int height) {
		this(text, skin.getFont(), skin.getFontColor(), x, y, width, height, skin.getIdleClickTexture(),
				skin.getHoverClickTexture(), skin.getDisableTexture());
	}

	public LClickButton(String text, IFont font, LColor color, int x, int y, int width, int height, LTexture idle,
			LTexture hover, LTexture disable) {
		super(x, y, width, height);
		this.setTouchDownMoved(true);
		this._currentText = text;
		this._currentFont = font != null ? font : SkinManager.get().getClickButtonSkin().getFont();
		this._fontColor = color != null ? new LColor(color) : SkinManager.get().getClickButtonSkin().getFontColor();
		this._idleClick = idle;
		this._hoverClick = hover;
		this._disableClick = disable;
		this._lightClickedButton = true;

		ClickButtonSkin skin = SkinManager.get().getClickButtonSkin();
		if (this._idleClick == null) {
			this._idleClick = skin.getIdleClickTexture();
		}
		if (this._hoverClick == null) {
			this._hoverClick = skin.getHoverClickTexture();
		}
		if (this._disableClick == null) {
			this._disableClick = skin.getDisableTexture();
		}
		if (_idleClick != null) {
			freeRes().add(_idleClick);
		}
		if (_hoverClick != null) {
			freeRes().add(_hoverClick);
		}
		if (_disableClick != null) {
			freeRes().add(_disableClick);
		}

		autoSize();
	}

	public void autoSize() {
		if (_currentFont == null) {
			_currentFont = SkinManager.get().getClickButtonSkin().getFont();
		}
		if (StringUtils.isEmpty(_currentText) && _idleClick != null && getWidth() <= 1 && getHeight() <= 1) {
			this.setWidth(MathUtils.max(getWidth(), _idleClick.getWidth()));
			this.setHeight(MathUtils.max(getHeight(), _idleClick.getHeight()));
		} else if (getWidth() <= 1f || getHeight() <= 1f) {
			PointF size = FontUtils.getTextWidthAndHeight(_currentFont, _currentText, getWidth(), getHeight());
			this.setWidth(MathUtils.max(getWidth(), size.x));
			this.setHeight(MathUtils.max(getHeight(), size.y));
		}
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (!_component_visible) {
			return;
		}
		if (_currentFont == null) {
			_currentFont = SkinManager.get().getClickButtonSkin().getFont();
		}
		if (_fontColor == null) {
			_fontColor = SkinManager.get().getClickButtonSkin().getFontColor();
		}
		if (_grayButton) {
			if (!isEnabled()) {
				if (_disableClick != null) {
					g.draw(_disableClick, x, y, getWidth(), getHeight(),
							_colorTemp.setColor(_component_baseColor == null ? LColor.gray.getARGB()
									: LColor.combine(_component_baseColor, LColor.gray)));
				}
			} else if (isTouchPressed()) {
				if (_idleClick != null) {
					if (_lightClickedButton) {
						g.draw(_idleClick, x, y, getWidth(), getHeight(),
								_colorTemp.setColor(_component_baseColor == null ? LColor.lightGray.getARGB()
										: LColor.combine(_component_baseColor, LColor.lightGray)));
					} else {
						g.draw(_idleClick, x, y, getWidth(), getHeight(), _component_baseColor);
					}
				}
			} else if (isTouchOver()) {
				if (_hoverClick != null) {
					g.draw(_hoverClick, x, y, getWidth(), getHeight(), _component_baseColor);
				}
			} else {
				if (_idleClick != null) {
					g.draw(_idleClick, x, y, getWidth(), getHeight(),
							_colorTemp.setColor(_component_baseColor == null ? LColor.gray.getARGB()
									: LColor.combine(_component_baseColor, LColor.gray)));
				}
			}
		} else {
			if (!isEnabled()) {
				if (_disableClick != null) {
					g.draw(_disableClick, x, y, getWidth(), getHeight(), _component_baseColor);
				}
			} else if (isTouchPressed()) {
				if (_idleClick != null) {
					if (_lightClickedButton) {
						g.draw(_idleClick, x, y, getWidth(), getHeight(),
								_colorTemp.setColor(_component_baseColor == null ? LColor.lightGray.getARGB()
										: LColor.combine(_component_baseColor, LColor.lightGray)));
					} else {
						g.draw(_idleClick, x, y, getWidth(), getHeight(), _component_baseColor);
					}
				}
			} else if (isTouchOver()) {
				if (_hoverClick != null) {
					g.draw(_hoverClick, x, y, getWidth(), getHeight(), _component_baseColor);
				}
			} else {
				if (_idleClick != null) {
					g.draw(_idleClick, x, y, getWidth(), getHeight(), _component_baseColor);
				}
			}
		}
		if (!StringUtils.isEmpty(_currentText) && _currentFont != null) {
			final int offsetX = MathUtils
					.iceil(x + getOffsetLeft() + (getWidth() - _currentFont.stringWidth(_currentText)) / 2);
			final int offsetY = MathUtils.iceil((y + getOffsetTop() + (getHeight() - _currentFont.getHeight()) / 2));
			_currentFont.drawString(g, _currentText, offsetX, offsetY, _fontColor);
		}
	}

	@Override
	public void update(long elapsedTime) {
		if (!isVisible()) {
			return;
		}
		super.update(elapsedTime);
		if (this._pressedTime > 0 && --this._pressedTime <= 0) {
			_currentOnTouch.release();
			this._pressedTime = 0;
		}
	}

	public LClickButton checked() {
		_currentOnTouch.press();
		return this;
	}

	public LClickButton unchecked() {
		_currentOnTouch.release();
		return this;
	}

	@Override
	protected void processTouchDragged() {
		super.processTouchDragged();
		this._clickedOver = this.intersects(getUITouchX(), getUITouchY());
		if (!_currentOnTouch.isPressed()) {
			this._currentOnTouch.press();
		}
	}

	@Override
	protected void processTouchPressed() {
		super.processTouchPressed();
		if (!_currentOnTouch.isPressed()) {
			_currentOnTouch.press();
		}
	}

	@Override
	protected void processTouchReleased() {
		super.processTouchReleased();
		boolean stillOver = this.intersects(getUITouchX(), getUITouchY());
		if (_function != null) {
			try {
				if (stillOver || isSelected()) {
					_function.call(this);
				}
			} catch (Throwable t) {
				LSystem.error("LClickButton call() exception", t);
			}
		}
		if (_clickConsumer != null && (stillOver || isSelected())) {
			try {
				_clickConsumer.hit(this);
			} catch (Throwable t) {
				LSystem.error("LClickButton consumer exception", t);
			}
		}
		if (_currentOnTouch.isPressed()) {
			_currentOnTouch.release();
			if (_toggleMode && (stillOver || isSelected())) {
				_toggled = !_toggled;
			}
			nextSpinner();
		}
	}

	@Override
	protected void processTouchEntered() {
		this._clickedOver = true;
	}

	@Override
	protected void processTouchExited() {
		this._clickedOver = false;
		if (_currentOnTouch.isPressed()) {
			_currentOnTouch.release();
		}
	}

	@Override
	protected void processKeyPressed() {
		if (this.isSelected() && isKeyDown(SysKey.ENTER)) {
			if (!_currentOnTouch.isPressed()) {
				this._pressedTime = 5;
				this._currentOnTouch.press();
				this.doClick();
			}
		}
	}

	@Override
	protected void processKeyReleased() {
		if (this.isSelected() && isKeyUp(SysKey.ENTER)) {
			if (_currentOnTouch.isPressed()) {
				_currentOnTouch.release();
			}
		}
	}

	public String getText() {
		return this._currentText;
	}

	public LClickButton setText(String t) {
		if (t == null) {
			this._currentText = null;
			return this;
		}
		if (t.equals(_currentText)) {
			return this;
		}
		this._currentText = t;
		if (_currentFont instanceof LFont && _currentText != null) {
			LSTRDictionary.get().bind((LFont) _currentFont, _currentText);
		}
		return this;
	}

	public int getOffsetLeft() {
		return _offsetLeft;
	}

	public LClickButton setOffsetLeft(int offsetLeft) {
		this._offsetLeft = offsetLeft;
		return this;
	}

	public int getOffsetTop() {
		return _offsetTop;
	}

	public LClickButton setOffsetTop(int offsetTop) {
		this._offsetTop = offsetTop;
		return this;
	}

	@Override
	public IFont getFont() {
		return _currentFont;
	}

	@Override
	public LClickButton setFont(IFont font) {
		this._currentFont = font != null ? font : this._currentFont;
		return this;
	}

	public boolean isTouchOver() {
		return this._clickedOver;
	}

	public boolean isTouchPressed() {
		return _currentOnTouch.isPressed();
	}

	public boolean isPressed() {
		return isTouchPressed();
	}

	@Override
	public LColor getFontColor() {
		return _fontColor != null ? _fontColor.cpy() : LColor.white.cpy();
	}

	@Override
	public LClickButton setFontColor(LColor c) {
		this._fontColor = c != null ? new LColor(c) : this._fontColor;
		return this;
	}

	public LTexture getIdleClick() {
		return _idleClick;
	}

	public LClickButton setIdleClick(LTexture c) {
		this._idleClick = c != null ? c : this._idleClick;
		if (c != null)
			freeRes().add(c);
		return this;
	}

	public LTexture getHoverClick() {
		return _hoverClick;
	}

	public LClickButton setHoverClick(LTexture h) {
		this._hoverClick = h != null ? h : this._hoverClick;
		if (h != null)
			freeRes().add(h);
		return this;
	}

	public LTexture getClickedClick() {
		return _disableClick;
	}

	public LClickButton setClickedClick(LTexture d) {
		this._disableClick = d != null ? d : this._disableClick;
		if (d != null)
			freeRes().add(d);
		return this;
	}

	public LClickButton setTexture(LTexture click) {
		if (click == null) {
			return this;
		}
		this._disableClick = click;
		this._idleClick = click;
		this._hoverClick = click;
		freeRes().add(click);
		return this;
	}

	public LClickButton setTexture(String path) {
		if (StringUtils.isEmpty(path)) {
			return this;
		}
		setTexture(LSystem.loadTexture(path));
		return this;
	}

	public boolean isLightClickedButton() {
		return _lightClickedButton;
	}

	public LClickButton setLightClickedButton(boolean clickedButton) {
		this._lightClickedButton = clickedButton;
		return this;
	}

	public boolean isGrayButton() {
		return _grayButton;
	}

	public LClickButton setGrayButton(boolean g) {
		this._grayButton = g;
		return this;
	}

	public CallFunction getFunction() {
		return _function;
	}

	public LClickButton setFunction(CallFunction f) {
		this._function = f;
		return this;
	}

	public LClickButton onClick(QueryEvent<LClickButton> consumer) {
		this._clickConsumer = consumer;
		return this;
	}

	public boolean isOver() {
		return _clickedOver;
	}

	public String getSpinnerValue() {
		if (_items == null || _items.size == 0) {
			return LSystem.EMPTY;
		}
		_selectedIndex = MathUtils.max(0, MathUtils.min(_selectedIndex, _items.size - 1));
		return _items.get(_selectedIndex);
	}

	public LClickButton setSpinnerValue(String v) {
		if (v == null) {
			return this;
		}
		if (_items == null) {
			_items = new TArray<String>();
		}
		final String mes = v.trim().toLowerCase();
		for (int i = 0; i < _items.size; i++) {
			String text = _items.get(i).trim().toLowerCase();
			if (text.equals(mes)) {
				_selectedIndex = i;
				setText(getSpinnerSelected());
				return this;
			}
		}
		_items.add(v);
		_selectedIndex = _items.size - 1;
		setText(getSpinnerSelected());
		return this;
	}

	public LClickButton addSpinnerValues(String... vs) {
		if (vs == null || vs.length == 0) {
			return this;
		}
		if (_items == null) {
			_items = new TArray<String>();
		}
		for (int i = 0; i < vs.length; i++) {
			final String text = vs[i];
			if (!StringUtils.isNullOrEmpty(text)) {
				_items.add(text);
			}
		}
		if (_selectedIndex < 0 && _items.size > 0) {
			_selectedIndex = 0;
			setText(getSpinnerSelected());
		}
		return this;
	}

	public String getSpinnerSelected() {
		if (_items == null || _items.size == 0) {
			return LSystem.EMPTY;
		}
		_selectedIndex = MathUtils.max(0, MathUtils.min(_selectedIndex, _items.size - 1));
		return _items.get(_selectedIndex);
	}

	public LClickButton nextSpinner() {
		if (_allowedSpinner) {
			if (_items == null || _items.size == 0) {
				return this;
			}
			_selectedIndex++;
			_selectedIndex %= _items.size;
			setText(getSpinnerSelected());
		}
		return this;
	}

	public LClickButton setAllowedSpinner(boolean a) {
		this._allowedSpinner = a;
		return this;
	}

	public boolean isAllowedSpinner() {
		return _allowedSpinner;
	}

	@Override
	public String getUIName() {
		return "ClickButton";
	}

	public LClickButton setToggleMode(boolean toggle) {
		this._toggleMode = toggle;
		return this;
	}

	public boolean isToggleMode() {
		return _toggleMode;
	}

	public boolean isToggled() {
		return _toggled;
	}

	public LClickButton setToggled(boolean t) {
		this._toggled = t;
		return this;
	}

	public LClickButton setAccessibleName(String name) {
		this.accessibleName = name;
		return this;
	}

	public String getAccessibleName() {
		return this.accessibleName;
	}

	@Override
	public void destroy() {
		_clickConsumer = null;
		_function = null;
		_currentFont = null;
		_currentText = null;
		if (_items != null) {
			_items.clear();
			_items = null;
		}
	}

}
