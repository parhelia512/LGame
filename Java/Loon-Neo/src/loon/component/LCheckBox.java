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

import loon.LTexture;
import loon.LSystem;
import loon.canvas.LColor;
import loon.component.skin.CheckBoxSkin;
import loon.component.skin.SkinManager;
import loon.events.ActionKey;
import loon.events.CallFunction;
import loon.events.SysKey;
import loon.font.FontSet;
import loon.font.IFont;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.Easing;
import loon.utils.MathUtils;
import loon.utils.TArray;

/**
 * CheckBox,单纯的选项打勾用UI
 */
public class LCheckBox extends LComponent implements FontSet<LCheckBox> {

	public static interface ChangeListener {
		void onChange(LCheckBox source, boolean oldValue, boolean newValue);
	}

	private static class Ripple {
		float x, y;
		float maxR;
		float duration;
		float elapsed = 0f;
		final LColor color = new LColor();

		Ripple(float x, float y, float maxR, float duration) {
			this.x = x;
			this.y = y;
			this.maxR = maxR;
			this.duration = MathUtils.max(1f, duration);
		}

		void advance(long dt) {
			this.elapsed += dt;
		}

		boolean finished() {
			return this.elapsed >= this.duration;
		}

		boolean updateAndDraw(GLEx g, float posX, float posY) {
			float p = MathUtils.clamp(this.elapsed / this.duration, 0f, 1f);
			float r = p * maxR;
			float alpha = 1f - p;
			g.setColor(color.setColor(1f, 1f, 1f, alpha * 0.4f));
			g.fillOval((posX + x - r), (posY + y - r), (r * 2), (r * 2));
			return finished();
		}
	}

	public static class CheckBoxGroup {
		private final TArray<LCheckBox> members = new TArray<LCheckBox>();
		private boolean singleSelection = false;

		public CheckBoxGroup(boolean singleSelection) {
			this.singleSelection = singleSelection;
		}

		public void add(LCheckBox cb) {
			if (cb == null) {
				return;
			}
			if (!members.contains(cb)) {
				members.add(cb);
				cb.setCheckBoxGroup(this);
			}
		}

		public void remove(LCheckBox cb) {
			if (cb == null) {
				return;
			}
			members.remove(cb);
			if (cb.getCheckBoxGroup() == this) {
				cb.setCheckBoxGroup(null);
			}
		}

		public void clearSelection() {
			for (LCheckBox cb : new TArray<LCheckBox>(members)) {
				cb.setTicked(false);
			}
		}

		public boolean isSingleSelection() {
			return singleSelection;
		}

		public void setSingleSelection(boolean s) {
			this.singleSelection = s;
		}
	}

	public final static LCheckBox at(String txt, int x, int y) {
		return new LCheckBox(txt, x, y);
	}

	public final static LCheckBox at(String txt, int x, int y, LColor c) {
		return new LCheckBox(txt, x, y, c);
	}

	public final static LCheckBox at(IFont font, String txt, int x, int y, LColor c) {
		return new LCheckBox(txt, x, y, c, font);
	}

	public final static LCheckBox at(IFont font, String txt, int x, int y) {
		return new LCheckBox(txt, x, y, LColor.white, font);
	}

	private final ActionKey _currentOnTouch = new ActionKey();

	private LTexture _unchecked, _checked;

	private float _boxsize;

	private boolean _boxtoleftoftext = false, _showtext = true;

	private int _fontSpace = 0;

	private LColor _fontColor;

	private IFont _font;

	private boolean _pressed = false, _over = false, _ticked = false;

	private long _pressedTime = 0;

	private String _text;

	private Vector2f _offset = new Vector2f();

	private CallFunction _function;

	private float _animProgress = 0f;
	private float _animDuration = 150f;
	private boolean _animating = false;
	private float _hoverScale = 1.05f;
	private float _pressScale = 0.95f;
	private float _cachedTextWidth = -1f;

	private boolean _disabled = false;
	private String _tooltip = null;
	private boolean _showTooltip = true;
	private final TArray<ChangeListener> _changeListeners = new TArray<ChangeListener>();
	private final TArray<Ripple> _ripples = new TArray<Ripple>();
	private Easing _easing = Easing.CUBIC_OUT;
	private boolean _rippleEnabled = true;
	private float _rippleMaxRadius = 40f;
	private float _rippleDuration = 300f;
	private CheckBoxGroup _group = null;

	public LCheckBox(String txt, int x, int y) {
		this(txt, x, y, SkinManager.get().getCheckBoxSkin().getFontColor());
	}

	public LCheckBox(String txt, int x, int y, LColor textcolor) {
		this(txt, x, y, textcolor, SkinManager.get().getCheckBoxSkin().getFont());
	}

	public LCheckBox(String txt, int x, int y, LColor textcolor, IFont font) {
		this(txt, x, y, SkinManager.get().getCheckBoxSkin().getUncheckedTexture(),
				SkinManager.get().getCheckBoxSkin().getCheckedTexture(),
				SkinManager.get().getCheckBoxSkin().getUncheckedTexture().getWidth(), true, textcolor, font);
	}

	public LCheckBox(String txt, int x, int y, int boxsize) {
		this(txt, x, y, SkinManager.get().getCheckBoxSkin().getUncheckedTexture(),
				SkinManager.get().getCheckBoxSkin().getCheckedTexture(), boxsize, true,
				SkinManager.get().getCheckBoxSkin().getFontColor(), SkinManager.get().getCheckBoxSkin().getFont());
	}

	public LCheckBox(String txt, int x, int y, int boxsize, LColor textcolor) {
		this(txt, x, y, SkinManager.get().getCheckBoxSkin().getUncheckedTexture(),
				SkinManager.get().getCheckBoxSkin().getCheckedTexture(), boxsize, true, textcolor,
				SkinManager.get().getCheckBoxSkin().getFont());
	}

	public LCheckBox(String txt, int x, int y, int boxsize, boolean boxtoleftoftext) {
		this(txt, x, y, boxsize, boxtoleftoftext, SkinManager.get().getCheckBoxSkin().getFont());
	}

	public LCheckBox(String txt, int x, int y, int boxsize, boolean boxtoleftoftext, IFont font) {
		this(txt, x, y, SkinManager.get().getCheckBoxSkin().getUncheckedTexture(),
				SkinManager.get().getCheckBoxSkin().getCheckedTexture(), boxsize, boxtoleftoftext,
				SkinManager.get().getCheckBoxSkin().getFontColor(), font);
	}

	public LCheckBox(String txt, int x, int y, int boxsize, boolean boxtoleftoftext, LColor textcolor, IFont font) {
		this(txt, x, y, SkinManager.get().getCheckBoxSkin().getUncheckedTexture(),
				SkinManager.get().getCheckBoxSkin().getCheckedTexture(), boxsize, boxtoleftoftext, textcolor, font);
	}

	public LCheckBox(String txt, int x, int y, String uncheckedFile, String checkedFile, int boxsize,
			boolean boxtoleftoftext, LColor textcolor, IFont font) {
		this(txt, x, y, LSystem.loadTexture(uncheckedFile), LSystem.loadTexture(checkedFile), boxsize, boxtoleftoftext,
				textcolor, font);
	}

	public LCheckBox(CheckBoxSkin skin, String txt, int x, int y, int boxsize, boolean boxtoleftoftext) {
		this(txt, x, y, skin.getUncheckedTexture(), skin.getCheckedTexture(), boxsize, boxtoleftoftext,
				skin.getFontColor(), skin.getFont());
	}

	public LCheckBox(String txt, int x, int y, LTexture unchecked, LTexture checked, int boxsize,
			boolean boxtoleftoftext, LColor textcolor, IFont font) {
		super(x, y, (font != null ? font.stringWidth(txt) : 0) + boxsize,
				MathUtils.max(font != null ? font.getHeight() : boxsize, boxsize));
		this._text = txt;
		this._unchecked = unchecked;
		this._checked = checked;
		this._boxsize = boxsize;
		this._boxtoleftoftext = boxtoleftoftext;
		this._fontColor = textcolor;
		this._font = font;
		this._animProgress = this._ticked ? 1f : 0f;
		this._cachedTextWidth = -1f;
		if (unchecked != null || checked != null) {
			freeRes().add(unchecked, checked);
		}
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		IFont tmp = g.getFont();
		g.setFont(_font);
		LColor base = _component_baseColor != null ? _component_baseColor : LColor.white;
		LColor drawColor = base.cpy();
		if (_disabled) {
			drawColor.a *= 0.5f;
		}
		if (_cachedTextWidth < 0 && _text != null && _font != null) {
			_cachedTextWidth = _font.stringWidth(_text);
		}
		float textWidth = (_cachedTextWidth >= 0) ? _cachedTextWidth
				: (_text != null && _font != null ? _font.stringWidth(_text) : 0);
		float scale = 1f;
		if (_pressed) {
			scale = _pressScale;
		} else if (_over) {
			scale = _hoverScale;
		}
		float t = MathUtils.clamp(_animProgress, 0f, 1f);
		float ease = _easing.apply(t);
		float boxX = x;
		float boxY = y;
		float textX = x;
		float textY = y;
		if (_boxtoleftoftext) {
			boxX = x;
			textX = x + _boxsize + 2 + _offset.x;
			boxY = y + (_font != null ? (_font.getHeight() - _boxsize) / 2 : 0) + _offset.y;
			textY = y + (_font != null ? (_font.getHeight() - _boxsize) / 2 : 0) + _offset.y;
		} else {
			boxX = x + textWidth + _boxsize + _fontSpace + _offset.x;
			boxY = y + (_font != null ? (_font.getHeight() / 2 - _boxsize / 2) : 0) + _offset.y;
			textX = x + 2 + _offset.x;
			textY = y + (_font != null ? (_font.getHeight() - _boxsize) / 2 : 0) + _offset.y;
		}
		if (_showtext && _text != null) {
			g.drawString(_text, (textX), (textY + _fontSpace), _fontColor);
		}
		if (_unchecked != null) {
			g.draw(_unchecked, boxX, boxY, _boxsize * scale, _boxsize * scale, drawColor);
		}
		if (_checked != null) {
			float prevAlpha = drawColor.a;
			float newAlpha = ease * prevAlpha;
			LColor blended = new LColor(drawColor.r, drawColor.g, drawColor.b, newAlpha);
			g.draw(_checked, boxX, boxY, _boxsize * scale, _boxsize * scale, blended);
		}
		if (!_ripples.isEmpty()) {
			TArray<Ripple> remove = new TArray<Ripple>();
			for (Ripple r : _ripples) {
				if (r.updateAndDraw(g, x, y)) {
					remove.add(r);
				}
			}
			_ripples.removeAll(remove);
		}
		if (_showTooltip && _tooltip != null && _over && !_disabled) {
			int tx = (int) (x + getWidth() + 8);
			int ty = y;
			g.fillRect(tx, ty, _font.stringWidth(_tooltip) + 8, _font.getHeight() + 6,
					LColor.black.cpy().mulSelf(0.7f));
			g.drawString(_tooltip, tx + 4, ty + 3, _fontColor);
		}
		g.setFont(tmp);
	}

	@Override
	public boolean isSelected() {
		return super.isSelected() || isTicked();
	}

	@Override
	public void process(long elapsedTime) {
		if (!isVisible()) {
			return;
		}

		long dt = elapsedTime;

		if (this._pressedTime > 0) {
			this._pressedTime -= dt;
			if (this._pressedTime <= 0) {
				this._pressed = false;
				this._pressedTime = 0;
				_currentOnTouch.release();
			}

		}

		if (_animating) {
			if (_animDuration <= 0) {
				_animProgress = _ticked ? 1f : 0f;
				_animating = false;
			} else {
				float delta = dt / _animDuration;
				if (_ticked) {
					_animProgress += delta;
					if (_animProgress >= 1f) {
						_animProgress = 1f;
						_animating = false;
					}
				} else {
					_animProgress -= delta;
					if (_animProgress <= 0f) {
						_animProgress = 0f;
						_animating = false;
					}
				}
			}
		}

		TArray<Ripple> remove = new TArray<Ripple>();
		for (Ripple r : _ripples) {
			r.advance(dt);
			if (r.finished()) {
				remove.add(r);
			}
		}
		_ripples.removeAll(remove);
	}

	public LCheckBox checked() {
		_currentOnTouch.press();
		return this;
	}

	public LCheckBox unchecked() {
		_currentOnTouch.release();
		return this;
	}

	public boolean isTouchOver() {
		return this._over;
	}

	public boolean isTouchPressed() {
		return this._pressed;
	}

	@Override
	protected void processTouchDragged() {
		if (_input != null) {
			boolean inside = isPointInUI();
			this._over = inside;
			if (!inside) {
				this._pressed = false;
			}
		}
		if (!_currentOnTouch.isPressed()) {
			this._currentOnTouch.press();
		}
		super.processTouchDragged();
	}

	@Override
	protected void processTouchEntered() {
		this._over = true;
	}

	@Override
	protected void processTouchExited() {
		this._over = this._pressed = false;
		if (_currentOnTouch.isPressed()) {
			_currentOnTouch.release();
		}
	}

	@Override
	protected void processKeyPressed() {
		if (this.isSelected() && isKeyDown(SysKey.SPACE)) {
			this._pressedTime = 150;
			this._pressed = true;
			this.doClick();
		}
		if (this.isSelected() && isKeyDown(SysKey.ENTER)) {
			this._pressedTime = 150;
			this._pressed = true;
			this.doClick();
		}
	}

	@Override
	protected void processKeyReleased() {
		if (this.isSelected() && (isKeyUp(SysKey.SPACE) || isKeyUp(SysKey.ENTER))) {
			this._pressed = false;
		}
	}

	@Override
	protected void processTouchPressed() {
		super.processTouchPressed();
		if (_disabled) {
			return;
		}
		this._pressed = true;
		this._pressedTime = 150;
		if (_rippleEnabled) {
			_ripples.add(new Ripple(getUITouchX(), getUITouchY(), _rippleMaxRadius, _rippleDuration));
		}
		if (!_currentOnTouch.isPressed()) {
			_currentOnTouch.press();
		}
	}

	@Override
	protected void processTouchReleased() {
		super.processTouchReleased();
		if (_currentOnTouch.isPressed() && isPointInUI()) {
			if (_disabled) {
				this._pressed = false;
				return;
			}
			if (_function != null) {
				_function.call(this);
			}
			boolean old = this._ticked;
			if (_group != null && _group.isSingleSelection()) {
				if (!old) {
					_group.clearSelection();
					this._ticked = true;
				}
			} else {
				this._ticked = !_ticked;
			}
			this._animating = true;
			this._animProgress = MathUtils.clamp(this._animProgress, 0f, 1f);
			fireChange(old, this._ticked);
			this._pressed = false;
			_currentOnTouch.release();
		}

	}

	public CallFunction getFunction() {
		return _function;
	}

	public LCheckBox setFunction(CallFunction f) {
		this._function = f;
		return this;
	}

	public boolean isTicked() {
		return _ticked;
	}

	public LCheckBox setTicked(boolean ticked) {
		if (this._ticked != ticked) {
			boolean old = this._ticked;
			this._ticked = ticked;
			this._animating = true;
			this._animProgress = MathUtils.clamp(this._animProgress, 0f, 1f);
			fireChange(old, this._ticked);
		}
		return this;
	}

	public boolean isShowText() {
		return _showtext;
	}

	public LCheckBox setShowText(boolean show) {
		this._showtext = show;
		return this;
	}

	@Override
	public LColor getFontColor() {
		return _fontColor != null ? _fontColor.cpy() : LColor.white.cpy();
	}

	@Override
	public LCheckBox setFontColor(LColor fontColor) {
		this._fontColor = fontColor;
		return this;
	}

	public LTexture getChecked() {
		return _checked;
	}

	public int getFontSpace() {
		return _fontSpace;
	}

	public LCheckBox setFontSpace(int fontSpace) {
		this._fontSpace = fontSpace;
		return this;
	}

	public boolean isBoxtoleftofText() {
		return _boxtoleftoftext;
	}

	public LCheckBox setBoxtoleftofText(boolean b) {
		this._boxtoleftoftext = b;
		_cachedTextWidth = -1f;
		return this;
	}

	public Vector2f getBoxOffset() {
		return _offset;
	}

	public LCheckBox setBoxOffset(Vector2f offset) {
		this._offset = offset;
		return this;
	}

	@Override
	public LCheckBox setFont(IFont font) {
		if (font == null) {
			return this;
		}
		this._font = font;
		this._cachedTextWidth = -1f;
		this.setSize((int) (this._font.stringWidth(_text) + _boxsize), MathUtils.max(font.getHeight(), _boxsize));
		return this;
	}

	@Override
	public IFont getFont() {
		return _font;
	}

	@Override
	public String getUIName() {
		return "CheckBox";
	}

	@Override
	public void destory() {

	}

	public LCheckBox setText(String text) {
		this._text = text;
		this._cachedTextWidth = -1f;
		if (this._font != null) {
			this.setSize((int) (this._font.stringWidth(_text) + _boxsize),
					MathUtils.max(_font.getHeight(), (int) _boxsize));
		}
		return this;
	}

	public String getText() {
		return this._text;
	}

	public LCheckBox setDisabled(boolean d) {
		this._disabled = d;
		return this;
	}

	public boolean isDisabled() {
		return this._disabled;
	}

	public LCheckBox setTooltip(String tip) {
		this._tooltip = tip;
		return this;
	}

	public String getTooltip() {
		return this._tooltip;
	}

	public LCheckBox setRippleEnabled(boolean enable) {
		this._rippleEnabled = enable;
		return this;
	}

	public boolean isRippleEnabled() {
		return this._rippleEnabled;
	}

	public LCheckBox setRippleMaxRadius(float r) {
		this._rippleMaxRadius = r;
		return this;
	}

	public LCheckBox setRippleDuration(float ms) {
		this._rippleDuration = ms;
		return this;
	}

	public LCheckBox setEasing(Easing easing) {
		this._easing = easing;
		return this;
	}

	public LCheckBox setAnimDuration(float ms) {
		this._animDuration = ms;
		return this;
	}

	public LCheckBox addChangeListener(ChangeListener l) {
		if (l != null && !_changeListeners.contains(l)) {
			_changeListeners.add(l);
		}
		return this;
	}

	public LCheckBox removeChangeListener(ChangeListener l) {
		_changeListeners.remove(l);
		return this;
	}

	protected void fireChange(boolean oldValue, boolean newValue) {
		for (ChangeListener l : new TArray<ChangeListener>(_changeListeners)) {
			try {
				l.onChange(this, oldValue, newValue);
			} catch (Throwable t) {
			}
		}
	}

	public LCheckBox setCheckBoxGroup(CheckBoxGroup group) {
		this._group = group;
		return this;
	}

	public CheckBoxGroup getCheckBoxGroup() {
		return this._group;
	}

}
