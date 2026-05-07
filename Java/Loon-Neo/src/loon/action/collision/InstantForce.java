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

public class InstantForce implements Force {

	private final String id;
	private final Vector2f vec;
	private boolean consumed = false;

	public InstantForce(String id, float fx, float fy) {
		this.id = id == null ? "InstantForce" : id;
		this.vec = Vector2f.at(fx, fy);
	}

	@Override
	public String identifier() {
		return id;
	}

	@Override
	public void update(long elapsedTime) {
		consumed = true;
	}

	@Override
	public Vector2f direction() {
		if (consumed) {
			return Vector2f.at(0f, 0f);
		}
		return vec;
	}

	public boolean isConsumed() {
		return consumed;
	}

	@Override
	public boolean isFinished() {
		return consumed;
	}
}