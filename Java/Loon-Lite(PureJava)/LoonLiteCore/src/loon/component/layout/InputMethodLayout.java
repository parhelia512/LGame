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
package loon.component.layout;

import loon.geom.BoxSize;
import loon.geom.SizeValue;
import loon.utils.MathUtils;
import loon.utils.TArray;

public class InputMethodLayout extends LayoutManager {

	public final static InputMethodLayout at(int columns, float keyWidth, float keyHeight, float gapX, float gapY) {
		return new InputMethodLayout(columns, keyWidth, keyHeight, gapX, gapY);
	}

	private int _columns;

	private float _keyWidth, _keyHeight, _gapX, _gapY;

	public InputMethodLayout(int columns, float keyWidth, float keyHeight, float gapX, float gapY) {
		this._columns = MathUtils.max(1, columns);
		this._keyWidth = keyWidth;
		this._keyHeight = keyHeight;
		this._gapX = gapX;
		this._gapY = gapY;
	}

	@Override
	public LayoutManager layoutElements(LayoutPort root, LayoutPort... children) {
		BoxSize rootBox = root.getBox();
		float startX = rootBox.getX();
		float startY = rootBox.getY();
		int col = 0;
		float x = startX;
		float y = startY;
		for (LayoutPort p : children) {
			BoxSize box = p.getBox();
			box.setX(x);
			box.setY(y);
			box.setWidth(_keyWidth);
			box.setHeight(_keyHeight);
			col++;
			if (col >= _columns) {
				col = 0;
				x = startX;
				y += (_keyHeight + _gapY);
			} else {
				x += (_keyWidth + _gapX);
			}
		}
		return this;
	}

	@Override
	SizeValue calculateConstraintWidth(LayoutPort root, TArray<LayoutPort> children) {
		return null;
	}

	@Override
	SizeValue calculateConstraintHeight(LayoutPort root, TArray<LayoutPort> children) {
		return null;
	}
}
