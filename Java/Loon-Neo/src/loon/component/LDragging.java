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

import java.util.Iterator;

import loon.LSystem;
import loon.canvas.LColor;
import loon.events.ActionKey;
import loon.events.SelectAreaListener;
import loon.events.SysTouch;
import loon.geom.Circle;
import loon.geom.RectBox;
import loon.geom.Shape;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

/**
 * 用于显示拖拽范围的拖拽效果组件
 */
public class LDragging extends LComponent {

	public enum SelectionMode {
		PARTIAL, FULL
	}

	public static interface Selectable {

		boolean contains(float x, float y);

		boolean intersects(Shape area);

		void moveBy(float dx, float dy);

		float getX();

		float getY();
	}

	public static interface DragItemsListener {

		void onDragStart(TArray<Selectable> items);

		void onDrag(TArray<Selectable> items, float dx, float dy);

		void onDragEnd(TArray<Selectable> items);
	}

	private SelectAreaListener _selectArea;

	private boolean _fillRect;

	private boolean _dashRect;

	private boolean _dragging;

	private boolean _circle;

	private int _dashDivisions;

	private RectBox _area;

	private RectBox _display_area;

	private float _dragDrawAlpha;

	private float _startX;

	private float _startY;

	private float _lastX;

	private float _lastY;

	private ActionKey _locked;

	private float _lineWidth;

	private LColor _fillColor;

	private LColor _rectColor;
	/**
	 * 构造拖拽用组件,用于渲染出特定的拖拽区域
	 */
	private final TArray<Selectable> _allSelectables = new TArray<Selectable>();

	private final TArray<Selectable> _selected = new TArray<Selectable>();

	private final ObjectMap<Selectable, float[]> _dragOffsets = new ObjectMap<Selectable, float[]>();

	private boolean _draggingItems = false;

	private DragItemsListener _dragItemsListener;

	private boolean _multiSelect = true;

	private SelectionMode _selectionMode = SelectionMode.PARTIAL;

	public LDragging() {
		this(false, false, true);
	}

	/**
	 * 构造拖拽用组件,用于渲染出特定的拖拽区域(默认使用矩形渲染,全局渲染模式,使用虚线边框,边框线宽4,每行虚线由5个子线条组成)
	 * 
	 * @param circle 是否拖拽区域为圆形
	 * @param fill   是否完全填充
	 * @param dash   是否用虚线描边
	 */
	public LDragging(boolean circle, boolean fill, boolean dash) {
		this(circle, fill, dash, 4f, 5);
	}

	/**
	 * 构造拖拽用组件,用于渲染出特定的拖拽区域
	 * 
	 * @param circle        是否拖拽区域为圆形
	 * @param fill          是否完全填充
	 * @param dash          是否用虚线描边
	 * @param lineWidth     线条宽度
	 * @param dashDivisions 虚线间隔
	 */
	public LDragging(boolean circle, boolean fill, boolean dash, float lineWidth, int dashDivisions) {
		this(0, 0, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight(), null, null, circle, fill, dash, lineWidth,
				dashDivisions);
	}

	/**
	 * 构造拖拽用组件,用于渲染出特定的拖拽区域
	 * 
	 * @param x             组件初始x
	 * @param y             组件初始y
	 * @param width         组件初始width
	 * @param height        组件初始height
	 * @param fillColor     填充选中区域用的颜色(需要fill项为true)
	 * @param rectColor     填充选中区域边框的颜色(若fill项为false则直接使用fillColor颜色)
	 * @param circle        使用圆形选择区域而非矩形
	 * @param fill          是否填充整个选框
	 * @param dash          是否使用虚线填充
	 * @param lineWidth     边框的宽度
	 * @param dashDivisions 若dash项为true时,每行显示多少个虚线
	 */
	public LDragging(int x, int y, int width, int height, LColor fillColor, LColor rectColor, boolean circle,
			boolean fill, boolean dash, float lineWidth, int dashDivisions) {
		super(x, y, width, height);
		if (fillColor == null) {
			this._fillColor = new LColor(LColor.yellow);
		} else {
			this._fillColor = new LColor(fillColor);
		}
		if (rectColor == null) {
			_rectColor = new LColor(_fillColor.lighter());
		} else {
			_rectColor = new LColor(rectColor);
		}
		this._circle = circle;
		this._fillRect = fill;
		this._dashRect = dash;
		this._lineWidth = lineWidth;
		this._dashDivisions = dashDivisions;
		this._dragDrawAlpha = 0.5f;
		this._area = new RectBox();
		this._display_area = new RectBox();
		this._locked = new ActionKey();
	}

	@Override
	public void processTouchPressed() {
		super.processTouchPressed();
		if (!(SysTouch.isDrag() && (_input != null && _input.isMoving()))) {
			if (!_locked.isPressed()) {
				start();
				_locked.press();
				if (!_selected.isEmpty()) {
					float tx = getUITouchX();
					float ty = getUITouchY();
					for (int i = _selected.size() - 1; i >= 0; i--) {
						Selectable s = _selected.get(i);
						if (s != null && s.contains(tx, ty)) {
							prepareDragSelectedItems(tx, ty);
							_draggingItems = true;
							if (_dragItemsListener != null) {
								_dragItemsListener.onDragStart(_selected);
							}
							break;
						}
					}
				}
			}
		}
	}

	@Override
	public void processTouchDragged() {
		super.processTouchDragged();
		if (_draggingItems && _locked.isPressed()) {
			float tx = getUITouchX();
			float ty = getUITouchY();
			float dx = tx - this._startX;
			float dy = ty - this._startY;
			dragSelectedItemsTo(dx, dy);
			if (_dragItemsListener != null) {
				_dragItemsListener.onDrag(_selected, dx, dy);
			}
			this._lastX = tx;
			this._lastY = ty;
			return;
		}
		if (!_locked.isPressed()) {
			start();
			_locked.press();
		} else {
			drag();
		}
	}

	@Override
	public void processTouchReleased() {
		super.processTouchReleased();
		if (_draggingItems) {
			_draggingItems = false;
			_dragOffsets.clear();
			if (_dragItemsListener != null) {
				_dragItemsListener.onDragEnd(_selected);
			}
		}
		if (_locked.isPressed() && _dragging) {
			if (_selectArea != null) {
				_selectArea.onArea(this._display_area.x, this._display_area.y,
						this._display_area.width - ((this._display_area.width / 6) - _lineWidth - 1),
						this._display_area.height - ((this._display_area.height / 6) - _lineWidth - 1));
			}
			stop();
		}
		_locked.release();
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (_destroyed) {
			return;
		}
		if (!_dragging) {
			return;
		}
		final float areaX = x + this._display_area.x;
		final float areaY = y + this._display_area.y;
		final float areaWidth = MathUtils.clamp(this._display_area.width, 1, getWidth());
		final float areaHeight = MathUtils.clamp(this._display_area.height, 1, getHeight());
		if (_circle) {
			final float areaSize = MathUtils.max(areaWidth, areaHeight) + areaWidth / LSystem.LAYER_TILE_SIZE;
			if (_fillRect) {
				float alpha = _fillColor.a;
				if (alpha >= 1f) {
					_fillColor.a = _dragDrawAlpha;
				}
				int tint = g.color();
				g.setColor(_fillColor);
				g.fillCircle(areaX, areaY, areaSize);
				float oldLineWidth = g.getLineWidth();
				g.setLineWidth(_lineWidth);
				g.setColor(_rectColor);
				if (_dashRect) {
					g.drawDashCircle(areaX, areaY, areaSize, _dashDivisions);
				} else {
					g.drawCircle(areaX, areaY, areaSize);
				}
				g.setLineWidth(oldLineWidth);
				_fillColor.a = alpha;
				g.setTint(tint);
			} else {
				int tint = g.color();
				g.setColor(_fillColor);
				float oldLineWidth = g.getLineWidth();
				g.setLineWidth(_lineWidth);
				if (_dashRect) {
					g.drawDashCircle(areaX, areaY, areaSize, _dashDivisions);
				} else {
					g.drawCircle(areaX, areaY, areaSize);
				}
				g.setLineWidth(oldLineWidth);
				g.setTint(tint);
			}
		} else {
			if (_fillRect) {
				float alpha = _fillColor.a;
				if (alpha >= 1f) {
					_fillColor.a = _dragDrawAlpha;
				}
				g.fillRect(areaX, areaY, areaWidth, areaHeight, _fillColor);
				float oldLineWidth = g.getLineWidth();
				g.setLineWidth(_lineWidth);
				if (_dashRect) {
					g.drawDashRect(areaX, areaY, areaWidth, areaHeight, _rectColor, _dashDivisions);
				} else {
					g.drawRect(areaX, areaY, areaWidth, areaHeight, _rectColor);
				}
				g.setLineWidth(oldLineWidth);
				_fillColor.a = alpha;
			} else {
				float oldLineWidth = g.getLineWidth();
				g.setLineWidth(_lineWidth);
				if (_dashRect) {
					g.drawDashRect(areaX, areaY, areaWidth, areaHeight, _fillColor, _dashDivisions);
				} else {
					g.drawRect(areaX, areaY, areaWidth, areaHeight, _fillColor);
				}
				g.setLineWidth(oldLineWidth);
			}
		}
	}

	private void checkDisplayArea() {
		float areaX = this._area.x;
		float areaY = this._area.y;
		float areaWidth = this._area.width;
		float areaHeight = this._area.height;
		if (areaWidth < 0) {
			areaWidth = MathUtils.abs(areaWidth);
			float tmp = areaX;
			areaX = tmp - areaWidth;
		}
		if (areaHeight < 0) {
			areaHeight = MathUtils.abs(areaHeight);
			float tmp = areaY;
			areaY = tmp - areaHeight;
		}
		this._display_area.setBounds(areaX, areaY, areaWidth, areaHeight);
	}

	public RectBox getArea() {
		return this._display_area;
	}

	public LDragging start() {
		if ((getUITouchX() != this._startX || getUITouchY() != this._startY)) {
			clearArea();
			this._startX = getUITouchX();
			this._startY = getUITouchY();
			this._area.setLocation(this._startX, this._startY);
			this._dragging = false;
		}
		return this;
	}

	public LDragging drag() {
		if (getUITouchX() != this._lastX || getUITouchY() != this._lastY) {
			this._lastX = getUITouchX();
			this._lastY = getUITouchY();
			final float newSizeW = this._lastX - this._startX;
			final float newSizeH = this._lastY - this._startY;
			this._area.setSize(newSizeW, newSizeH);
			this._dragging = true;
			checkDisplayArea();
		}
		return this;
	}

	public LDragging stop() {
		if (getUITouchX() != this._lastX || getUITouchY() != this._lastY && this._dragging) {
			clearArea();
			this._lastX = getUITouchX();
			this._lastY = getUITouchY();
			this._dragging = false;
		}
		return this;
	}

	public boolean isDragging() {
		return this._dragging;
	}

	public LDragging setDragging(boolean d) {
		this._dragging = d;
		return this;
	}

	public LDragging clearArea() {
		_area.clear();
		_display_area.clear();
		return this;
	}

	/**
	 * 获得对应当前组件拖拽的拖拽范围的具体形状
	 * 
	 * @return
	 */
	public Shape getDragRang() {
		final float areaX = getScalePixelX() + this._display_area.x;
		final float areaY = getScalePixelY() + this._display_area.y;
		final float areaWidth = MathUtils.clamp(this._display_area.width, 1, getWidth());
		final float areaHeight = MathUtils.clamp(this._display_area.height, 1, getHeight());
		if (_circle) {
			final float centerRadius = (MathUtils.max(areaWidth, areaHeight) + areaWidth / LSystem.LAYER_TILE_SIZE / 2f
					+ _lineWidth / 2f) / 2f;
			return new Circle(areaX + centerRadius, areaY + centerRadius, centerRadius);
		} else {
			return new RectBox(areaX, areaY, areaWidth, areaHeight);
		}
	}

	public LDragging addSelectable(Selectable s) {
		if (s != null && !_allSelectables.contains(s)) {
			_allSelectables.add(s);
		}
		return this;
	}

	public LDragging removeSelectable(Selectable s) {
		_allSelectables.remove(s);
		_selected.remove(s);
		_dragOffsets.remove(s);
		return this;
	}

	public TArray<Selectable> getAllSelectables() {
		return new TArray<Selectable>(_allSelectables);
	}

	public TArray<Selectable> getSelected() {
		return new TArray<Selectable>(_selected);
	}

	public LDragging clearSelection() {
		_selected.clear();
		_dragOffsets.clear();
		return this;
	}

	public LDragging setMultiSelect(boolean m) {
		this._multiSelect = m;
		return this;
	}

	public boolean isMultiSelect() {
		return this._multiSelect;
	}

	public LDragging setSelectionMode(SelectionMode m) {
		if (m != null) {
			this._selectionMode = m;
		}
		return this;
	}

	public SelectionMode getSelectionMode() {
		return this._selectionMode;
	}

	public TArray<Selectable> selectItemsInArea(boolean multiAdd) {
		Shape areaShape = getDragRang();
		TArray<Selectable> found = new TArray<Selectable>();
		for (Selectable s : _allSelectables) {
			boolean hit;
			if (_selectionMode == SelectionMode.FULL) {
				hit = s.intersects(areaShape) && fullyContained(s, areaShape);
			} else {
				hit = s.intersects(areaShape);
			}
			if (hit) {
				found.add(s);
			}
		}
		if (!multiAdd || !_multiSelect) {
			_selected.clear();
		}
		for (Selectable s : found) {
			if (!_selected.contains(s)) {
				_selected.add(s);
			}
		}
		return new TArray<Selectable>(_selected);
	}

	public TArray<Selectable> selectByPoint(float x, float y, boolean multiAdd) {
		Selectable hit = null;
		for (int i = _allSelectables.size() - 1; i >= 0; i--) {
			Selectable s = _allSelectables.get(i);
			if (s.contains(x, y)) {
				hit = s;
				break;
			}
		}
		if (!multiAdd || !_multiSelect) {
			_selected.clear();
		}
		if (hit != null) {
			if (_selected.contains(hit)) {
				if (multiAdd) {
					_selected.remove(hit);
				}
			} else {
				_selected.add(hit);
			}
		} else {
			if (!multiAdd) {
				_selected.clear();
			}
		}
		return new TArray<Selectable>(_selected);
	}

	private boolean fullyContained(Selectable s, Shape area) {
		return s.intersects(area);
	}

	private void prepareDragSelectedItems(float touchX, float touchY) {
		_dragOffsets.clear();
		for (Selectable s : _selected) {
			float dx = s.getX() - touchX;
			float dy = s.getY() - touchY;
			_dragOffsets.put(s, new float[] { dx, dy });
		}
	}

	private void dragSelectedItemsTo(float dx, float dy) {
		for (Iterator<Selectable> it = _selected.iterator(); it.hasNext();) {
			Selectable s = it.next();
			float[] off = _dragOffsets.get(s);
			if (off == null) {
				continue;
			}
			float targetX = this._startX + dx + off[0];
			float targetY = this._startY + dy + off[1];
			float moveDx = targetX - s.getX();
			float moveDy = targetY - s.getY();
			s.moveBy(moveDx, moveDy);
		}
	}

	public LDragging setDragItemsListener(DragItemsListener l) {
		this._dragItemsListener = l;
		return this;
	}

	public DragItemsListener getDragItemsListener() {
		return this._dragItemsListener;
	}

	public SelectAreaListener getSelectAreaListener() {
		return _selectArea;
	}

	public LDragging setSelectAreaListener(SelectAreaListener s) {
		this._selectArea = s;
		return this;
	}

	public boolean isFillRect() {
		return _fillRect;
	}

	public int getDashDivisions() {
		return _dashDivisions;
	}

	public float getDragDrawAlpha() {
		return _dragDrawAlpha;
	}

	public LDragging setDragDrawAlpha(float da) {
		this._dragDrawAlpha = da;
		return this;
	}

	public float getStartX() {
		return _startX;
	}

	public float getStartY() {
		return _startY;
	}

	public float getLastX() {
		return _lastX;
	}

	public float getLastY() {
		return _lastY;
	}

	public float getLineWidth() {
		return _lineWidth;
	}

	public boolean isCircleArea() {
		return _circle;
	}

	public LDragging setCircleArea(boolean ca) {
		this._circle = ca;
		return this;
	}

	public LDragging setLineWidth(float l) {
		this._lineWidth = l;
		return this;
	}

	public LColor getRectColor() {
		return _rectColor;
	}

	public LDragging setRectColor(LColor r) {
		this._rectColor = new LColor(r);
		return this;
	}

	public LColor getFillColor() {
		return _fillColor;
	}

	public LDragging setFillColor(LColor r) {
		this._fillColor = new LColor(r);
		return this;
	}

	@Override
	public String getUIName() {
		return "Dragging";
	}

	@Override
	public void destroy() {
		_allSelectables.clear();
		_selected.clear();
		_dragOffsets.clear();
		_dragItemsListener = null;
		_selectArea = null;
	}
}