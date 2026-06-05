/**
 * 
 * Copyright 2008 - 2009
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
 * @version 0.1
 */
package loon.component;

import loon.LSysException;
import loon.LSystem;
import loon.LTexture;
import loon.canvas.LColor;
import loon.events.ActionKey;
import loon.events.CallFunction;
import loon.events.SysKey;
import loon.font.FontSet;
import loon.font.IFont;
import loon.opengl.GLEx;
import loon.opengl.TextureUtils;

/**
 * 按钮用UI,它和LClickButton的主要差异在于，这个按钮必须依赖纹理生成,也就是有图才能用(而且最好注入4张图,对应四种状态)
 */
public class LButton extends LComponent implements FontSet<LButton> {

	private final ActionKey _onTouch = new ActionKey();

	private String _currentText = null;

	private boolean _clickOver, _clickException;

	private int _pressedTime, _offsetLeft, _offsetTop, _type;

	private IFont _font;

	private LColor _fontColor = LColor.white;

	private CallFunction _function;

	public LButton(String fileName, LColor color) {
		this(fileName, null, color, 0, 0);
	}

	public LButton(String fileName, String text, LColor color, int row, int col) {
		this(LSystem.loadTexture(fileName), text, color, row, col, 0, 0);
	}

	public LButton(String fileName, int row, int col) {
		this(fileName, null, LColor.white, row, col, 0, 0);
	}

	public LButton(String fileName, LColor color, int row, int col) {
		this(fileName, null, color, row, col, 0, 0);
	}

	public LButton(LTexture texture, int row, int col) {
		this(TextureUtils.getSplitTextures(texture, row, col), null, LColor.white, row, col, 0, 0);
	}

	public LButton(String fileName, String text, LColor color, int row, int col, int x, int y) {
		this(LSystem.loadTexture(fileName), text, color, row, col, x, y);
	}

	public LButton(LTexture img, String text, LColor color, int row, int col, int x, int y) {
		this(TextureUtils.getSplitTextures(img, row, col), text, color, row, col, x, y);
	}

	public LButton(LTexture[] img, String text, LColor color, int row, int col, int x, int y) {
		this(LSystem.getSystemGameFont(), img, text, color, row, col, x, y);
	}

	public LButton(IFont font, LTexture[] img, String text, LColor color, int row, int col, int x, int y) {
		super(x, y, row, col);
		this.setFontColor(color);
		this._font = font;
		this._currentText = text;
		if (img != null) {
			this.setImages(img);
		}
		if (img != null) {
			freeRes().add(img);
		}
	}

	public LButton(int x, int y) {
		this(LSystem.getSystemGameFont(), LSystem.EMPTY, x, y, 1, 1);
	}

	public LButton(String text, int x, int y, int w, int h) {
		this(LSystem.getSystemGameFont(), text, x, y, w, h);
	}

	public LButton(IFont font, String text, int x, int y, int w, int h) {
		this(font, text, LColor.white, x, y, w, h);
	}

	public LButton(IFont font, String text, LColor color, int x, int y, int w, int h) {
		super(x, y, w, h);
		this.setFontColor(color);
		this._font = font;
		this._currentText = text;
	}

	public LButton setDefAndPress(LTexture defImage, LTexture pressImage) {
		return setImages(defImage, defImage, pressImage, defImage);
	}

	public LButton setImages(LTexture... images) {
		if (images == null || images.length == 0) {
			this._clickException = true;
			this._type = 0;
			throw new LSysException("LButton setImages exception, images is null or empty");
		}

		int size = images.length;
		this._type = size;

		if (size > 4) {
			this._clickException = true;
			throw new LSysException("LButton setImages exception, buttons size = " + size + " (max 4)");
		}

		LTexture[] buttons = new LTexture[4];
		switch (size) {
		case 1:
			buttons[0] = images[0];
			buttons[1] = images[0];
			buttons[2] = images[0];
			buttons[3] = images[0];
			break;
		case 2:
			buttons[0] = images[0];
			buttons[1] = images[1];
			buttons[2] = images[0];
			buttons[3] = images[0];
			break;
		case 3:
			buttons[0] = images[0];
			buttons[1] = images[1];
			buttons[2] = images[2];
			buttons[3] = images[0];
			break;
		case 4:
			System.arraycopy(images, 0, buttons, 0, 4);
			break;
		default:
			throw new LSysException("LButton setImages unexpected size = " + size);
		}

		this._clickException = false;
		this.setImageUI(buttons, true);
		return this;
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (_imageUI != null) {
			if (!isEnabled()) {
				g.draw(_imageUI[3], x, y, _component_baseColor);
			} else if (isTouchPressed()) {
				g.draw(_imageUI[2], x, y, _component_baseColor);
			} else if (isTouchOver()) {
				g.draw(_imageUI[1], x, y, _component_baseColor);
			} else {
				if (_type == 1) {
					int argb = _component_baseColor == null ? LColor.gray.getARGB()
							: LColor.combine(_component_baseColor, LColor.gray);
					g.draw(_imageUI[0], x, y, _colorTemp.setColor(argb));
				} else {
					g.draw(_imageUI[0], x, y, _component_baseColor);
				}
			}
		}

		if (_currentText != null && _font != null) {
			int textWidth = _font.stringWidth(_currentText);
			float drawX = x + getOffsetLeft() + (getWidth() - textWidth) / 2;
			int fontHeight = _font.getHeight();
			float drawY = y + getOffsetTop() + (getHeight() - fontHeight) / 2;
			LColor colorToUse = _fontColor != null ? _fontColor : LColor.white;
			_font.drawString(g, _currentText, drawX, drawY, colorToUse);
		}
	}

	@Override
	public void update(long elapsedTime) {
		if (!isVisible()) {
			return;
		}
		super.update(elapsedTime);
		if (this._pressedTime > 0 && --this._pressedTime <= 0) {
			_onTouch.release();
		}
	}

	public boolean isTouchOver() {
		return this._clickOver;
	}

	public boolean isTouchPressed() {
		return _onTouch.isPressed();
	}

	public String getText() {
		return this._currentText;
	}

	public LButton setText(String st) {
		this._currentText = st;
		return this;
	}

	public LButton checked() {
		_onTouch.press();
		return this;
	}

	public LButton unchecked() {
		_onTouch.release();
		return this;
	}

	@Override
	protected void processTouchDragged() {
		super.processTouchDragged();
		boolean nowOver = this.intersects(getUITouchX(), getUITouchY());
		this._clickOver = nowOver;
		if (nowOver) {
			if (!_onTouch.isPressed()) {
				this._onTouch.press();
			}
		} else {
			if (_onTouch.isPressed()) {
				_onTouch.release();
			}
		}
	}

	@Override
	protected void processTouchPressed() {
		super.processTouchPressed();
		this._clickOver = true;
		if (!_onTouch.isPressed()) {
			_onTouch.press();
		}
	}

	@Override
	protected void processTouchReleased() {
		super.processTouchReleased();
		if (_onTouch.isPressed()) {
			_onTouch.release();
			if (_function != null) {
				try {
					_function.call(this);
				} catch (Throwable t) {
					LSystem.error("LClickButton call() exception", t);
				}
			}
		}
	}

	@Override
	protected void processTouchEntered() {
		this._clickOver = true;
	}

	@Override
	protected void processTouchExited() {
		this._clickOver = false;
		if (_onTouch.isPressed()) {
			_onTouch.release();
		}
	}

	@Override
	protected void processKeyPressed() {
		if (this.isSelected() && isKeyDown(SysKey.ENTER)) {
			if (!_onTouch.isPressed()) {
				this._pressedTime = 5;
				this._onTouch.press();
				this.doClick();
			}
		}
	}

	@Override
	protected void processKeyReleased() {
		if (this.isSelected() && isKeyUp(SysKey.ENTER)) {
			if (_onTouch.isPressed()) {
				_onTouch.release();
			}
		}
	}

	public boolean isException() {
		return _clickException;
	}

	@Override
	public IFont getFont() {
		return _font;
	}

	@Override
	public LButton setFont(IFont font) {
		this._font = font;
		return this;
	}

	public CallFunction getFunction() {
		return _function;
	}

	public LButton setFunction(CallFunction function) {
		this._function = function;
		return this;
	}

	@Override
	public LColor getFontColor() {
		return _fontColor != null ? _fontColor.cpy() : LColor.white.cpy();
	}

	@Override
	public LButton setFontColor(LColor c) {
		this._fontColor = (c != null) ? new LColor(c) : LColor.white.cpy();
		return this;
	}

	public int getOffsetLeft() {
		return _offsetLeft;
	}

	public LButton setOffsetLeft(int l) {
		this._offsetLeft = l;
		return this;
	}

	public int getOffsetTop() {
		return _offsetTop;
	}

	public LButton setOffsetTop(int t) {
		this._offsetTop = t;
		return this;
	}

	@Override
	public String getUIName() {
		return "Button";
	}

	@Override
	public void destroy() {
		_function = null;
		_font = null;
		_currentText = null;
		_fontColor = null;
		_clickOver = false;
		_clickException = false;
		_onTouch.release();
	}

}
