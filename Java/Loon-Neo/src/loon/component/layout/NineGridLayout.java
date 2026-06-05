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
import loon.utils.TArray;

public class NineGridLayout extends LayoutManager {

	public final static NineGridLayout at(float[] colWeights, float[] rowWeights) {
		return new NineGridLayout(colWeights, rowWeights);
	}

	private float[] _colWeights;
	private float[] _rowWeights;

	public NineGridLayout(float[] colWeights, float[] rowWeights) {
		this._colWeights = colWeights != null ? colWeights : new float[] { 1f, 1f, 1f };
		this._rowWeights = rowWeights != null ? rowWeights : new float[] { 1f, 1f, 1f };
	}

	@Override
	public LayoutManager layoutElements(LayoutPort root, LayoutPort... children) {
		BoxSize rootBox = root.getBox();
		float totalW = rootBox.getWidth();
		float totalH = rootBox.getHeight();
		float[] colW = new float[3];
		float[] rowH = new float[3];
		float cwSum = _colWeights[0] + _colWeights[1] + _colWeights[2];
		float rhSum = _rowWeights[0] + _rowWeights[1] + _rowWeights[2];
		for (int i = 0; i < 3; i++) {
			colW[i] = totalW * (_colWeights[i] / cwSum);
		}
		for (int i = 0; i < 3; i++) {
			rowH[i] = totalH * (_rowWeights[i] / rhSum);
		}
		int idx = 0;
		float y = rootBox.getY();
		for (int r = 0; r < 3; r++) {
			float x = rootBox.getX();
			for (int c = 0; c < 3; c++) {
				if (idx < children.length) {
					LayoutPort p = children[idx++];
					BoxSize box = p.getBox();
					box.setX(x);
					box.setY(y);
					box.setWidth(colW[c]);
					box.setHeight(rowH[r]);
				}
				x += colW[c];
			}
			y += rowH[r];
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
