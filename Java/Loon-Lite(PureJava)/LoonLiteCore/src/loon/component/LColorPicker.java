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
import loon.canvas.Canvas;
import loon.canvas.Image;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.TArray;

/**
 * 标准的颜色选择器，就是常规的颜色选择
 */
public class LColorPicker extends LComponent {

	private static final int[] DEFAULT_COLORS = new int[] { 0x000000, 0x333333, 0x666666, 0x999999, 0xCCCCCC, 0xFFFFFF,
			0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF };

	private final TArray<LColor> _colors = new TArray<LColor>();
	private final int _colorRow;
	private final int _colorCol;
	private final int _gridSize;

	private int _selectedIndex = -1;
	private LTexture _cachePicker;
	private boolean _cacheValid = false;

	public LColorPicker(int x, int y) {
		this(x, y, 15);
	}

	public LColorPicker(int x, int y, int gridSize) {
		this(x, y, 20, 12, gridSize);
	}

	public LColorPicker(int x, int y, int colorRow, int colorCol, int gridSize) {
		super(x, y, colorRow * gridSize, colorCol * gridSize);
		if (colorRow < 1) {
			throw new LSysException("The color row must be at least 1.");
		}
		if (colorCol < 1) {
			throw new LSysException("The color column must be at least 1.");
		}
		if (colorRow > 256 || colorCol > 256) {
			throw new LSysException("colorRow/colorCol too large.");
		}
		this._colorRow = colorRow;
		this._colorCol = colorCol;
		this._gridSize = gridSize;
		this._selectedIndex = -1;
		initDefaultColors();
	}

	private void initDefaultColors() {
		_colors.clear();
		for (int r = 0; r < _colorCol; r++) {
			for (int c = 0; c < _colorRow; c++) {
				LColor col;
				if (r == 0 && c < DEFAULT_COLORS.length) {
					col = new LColor(DEFAULT_COLORS[c]);
				} else {
					float hue = (float) c / MathUtils.max(1, _colorRow);
					float sat = 0.8f;
					float bri = 1.0f - (float) r / MathUtils.max(1, _colorCol);
					int rgb = LColor.HSBtoRGB(hue, sat, bri) & 0xFFFFFF;
					col = new LColor(rgb);
				}
				_colors.add(col);
			}
		}
		invalidateCache();
	}

	public void invalidateCache() {
		_cacheValid = false;
	}

	public void rebuildCache() {
		if (_cachePicker != null) {
			freeRes().remove(_cachePicker);
			_cachePicker.close();
			_cachePicker = null;
		}
		_cachePicker = createColorPickerCache();
		if (_cachePicker != null) {
			freeRes().add(_cachePicker);
			_cacheValid = true;
		}
	}

	protected LTexture createColorPickerCache() {
		Image img = Image.createImage(getWidth(), getHeight());
		Canvas g = img.getCanvas();
		g.setColor(LColor.white);
		g.fillRect(0, 0, getWidth(), getHeight());
		int expected = _colorRow * _colorCol;
		while (_colors.size() < expected) {
			_colors.add(new LColor(0x000000));
		}
		for (int row = 0; row < _colorCol; row++) {
			for (int col = 0; col < _colorRow; col++) {
				int tx = col * _gridSize;
				int ty = row * _gridSize;
				int idx = col + _colorRow * row;
				LColor c = _colors.get(idx);
				if (c == null) {
					c = new LColor(0x000000);
					_colors.set(idx, c);
				}
				g.setColor(c);
				g.fillRect(tx, ty, _gridSize, _gridSize);
				g.setColor(LColor.white);
				g.strokeRect(tx, ty, _gridSize - 1, _gridSize - 1);
			}
		}
		return img.texture();
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (!_cacheValid || _cachePicker == null) {
			rebuildCache();
		}
		if (_cachePicker != null) {
			g.draw(_cachePicker, x, y);
		}
		Vector2f touch = getUITouchXY();
		float localX = touch.x;
		float localY = touch.y;
		if (contains(localX + x, localY + y)) {
			int idx = getColorIndex(localX, localY);
			if (idx >= 0 && idx < _colors.size()) {
				int col = idx % _colorRow;
				int row = idx / _colorRow;
				int tx = x + col * _gridSize;
				int ty = y + row * _gridSize;
				int pad = MathUtils.max(2, _gridSize / 6);
				int w = _gridSize + pad * 2;
				int h = _gridSize + pad * 2;
				int nx = tx - pad;
				int ny = ty - pad;
				LColor selColor = _colors.get(idx);
				g.fillRect(nx, ny, w, h, selColor);
				g.drawRect(nx, ny, w, h, LColor.lightGray);
			}
		}
		if (_selectedIndex >= 0 && _selectedIndex < _colors.size()) {
			int col = _selectedIndex % _colorRow;
			int row = _selectedIndex / _colorRow;
			int tx = x + col * _gridSize;
			int ty = y + row * _gridSize;
			g.drawRect(tx, ty, _gridSize - 1, _gridSize - 1, LColor.red);
		}
	}

	@Override
	protected void processTouchPressed() {
		super.processTouchPressed();
	}

	@Override
	public void upClick() {
		super.upClick();
		Vector2f pos = getUITouchXY();
		int tx = MathUtils.floor(pos.x / this._gridSize);
		int ty = MathUtils.floor(pos.y / this._gridSize);
		if (tx < 0 || tx >= _colorRow || ty < 0 || ty >= _colorCol) {
			return;
		}
		int idx = tx + _colorRow * ty;
		if (idx >= 0 && idx < _colors.size()) {
			_selectedIndex = idx;
			onColorClicked(tx, ty, _colors.get(idx));
		}
	}

	protected void onColorClicked(int tileX, int tileY, LColor color) {
	}

	public String getColorHex() {
		LColor c = getSelectedColor();
		return c != null ? c.toString() : "#000000";
	}

	public String getColorCSS() {
		LColor c = getSelectedColor();
		return c != null ? c.toCSS() : "rgb(0,0,0)";
	}

	public int getColorIndex(float x, float y) {
		int tx = MathUtils.floor(x / this._gridSize);
		int ty = MathUtils.floor(y / this._gridSize);
		if (tx < 0 || tx >= _colorRow || ty < 0 || ty >= _colorCol) {
			return -1;
		}
		return tx + _colorRow * ty;
	}

	public int getColorIndexSelected() {
		Vector2f pos = getUITouchXY();
		return getColorIndex(pos.x, pos.y);
	}

	public LColor getSelectedColor() {
		if (_selectedIndex >= 0 && _selectedIndex < _colors.size()) {
			return _colors.get(_selectedIndex);
		}
		int idx = getColorIndexSelected();
		if (idx >= 0 && idx < _colors.size()) {
			return _colors.get(idx);
		}
		return null;
	}

	public void setSelectedIndex(int index) {
		if (index >= 0 && index < _colors.size()) {
			this._selectedIndex = index;
		} else {
			this._selectedIndex = -1;
		}
	}

	public int getSelectedIndex() {
		return this._selectedIndex;
	}

	public void setSelectedColor(LColor color) {
		if (color == null) {
			this._selectedIndex = -1;
			return;
		}
		int idx = _colors.indexOf(color);
		if (idx >= 0) {
			setSelectedIndex(idx);
		} else {
			_colors.add(color);
			invalidateCache();
			rebuildCache();
			setSelectedIndex(_colors.size() - 1);
		}
	}

	public void addColor(LColor color) {
		if (color == null) {
			return;
		}
		_colors.add(color);
		invalidateCache();
	}

	public void removeColorAt(int index) {
		if (index >= 0 && index < _colors.size()) {
			_colors.removeIndex(index);
			invalidateCache();
		}
	}

	public void clearColors() {
		_colors.clear();
		initDefaultColors();
	}

	@Override
	public String getUIName() {
		return "ColorPicker";
	}

	@Override
	public void destroy() {
		_cacheValid = false;
		_colors.clear();
	}
}
