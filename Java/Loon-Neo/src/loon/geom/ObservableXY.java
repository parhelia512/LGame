/**
 * Copyright 2008 - 2023 The Loon Game Engine Authors
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
package loon.geom;

import loon.LSysException;

public class ObservableXY<T> implements XY, SetXY {

	public final static <T> ObservableXY<T> at(XYChange<T> change, XY pos, T obj) {
		return new ObservableXY<T>(change, pos, obj);
	}

	private XYChange<T> _change;

	private XY _pos;

	private SetXY _tempPos;

	private T _obj;

	public ObservableXY(XYChange<T> v, XY pos) {
		this(v, pos, null);
	}

	public ObservableXY(XYChange<T> v, XY pos, T obj) {
		if (v == null) {
			throw new LSysException("The XY Object cannot be null !");
		}
		this._change = v;
		this._pos = pos;
		this._obj = obj;
	}

	public T getObj() {
		return this._obj;
	}

	private boolean checkUpdate() {
		return _pos != null;
	}

	private SetXY location() {
		if (_pos instanceof SetXY) {
			return ((SetXY) _pos);
		}
		if (_tempPos == null) {
			_tempPos = new PointF();
		}
		return _tempPos;
	}

	@Override
	public void setX(float x) {
		if (checkUpdate() && _pos.getX() != x) {
			location().setX(x);
			if (_change != null) {
				_change.onUpdate(_obj);
			}
		}
	}

	@Override
	public void setY(float y) {
		if (checkUpdate() && _pos.getY() != y) {
			location().setY(y);
			if (_change != null) {
				_change.onUpdate(_obj);
			}
		}
	}

	public int x() {
		return (int) _pos.getX();
	}

	public int y() {
		return (int) _pos.getY();
	}

	@Override
	public float getX() {
		return _pos.getX();
	}

	@Override
	public float getY() {
		return _pos.getY();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null) {
			return false;
		}
		if (getClass() != o.getClass()) {
			return false;
		}
		XY xy = (XY) o;
		return (_pos.getX() == xy.getX()) && (_pos.getY() == xy.getY());
	}

	@Override
	public final String toString() {
		return "(" + _pos.getX() + "," + _pos.getY() + ")";
	}
}