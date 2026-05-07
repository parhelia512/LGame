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
package loon.action.collision;

import loon.geom.Vector2f;

public class ConstantForce implements Force {

	private boolean _finished;

	private final Vector2f _direction;

	private final String _identifier;

	public ConstantForce(String i, Vector2f d) {
		this._identifier = i;
		this._direction = d;
	}

	@Override
	public String identifier() {
		return _identifier;
	}

	@Override
	public void update(long e) {
		_finished = true;
	}

	@Override
	public Vector2f direction() {
		return _direction;
	}

	public void setFinished(boolean f) {
		_finished = f;
	}

	@Override
	public boolean isFinished() {
		return _finished;
	}

}