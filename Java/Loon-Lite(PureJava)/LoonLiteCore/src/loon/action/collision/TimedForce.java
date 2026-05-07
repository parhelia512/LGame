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
import loon.utils.MathUtils;

public class TimedForce implements Force {

	private final String id;
	private final Vector2f vecPerSecond;
	private final long durationMs;
	private long elapsedMs = 0;
	private boolean finished = false;

	public TimedForce(String id, float fxPerSec, float fyPerSec, long durationMs) {
		this.id = id == null ? "TimedForce" : id;
		this.vecPerSecond = Vector2f.at(fxPerSec, fyPerSec);
		this.durationMs = MathUtils.max(1, durationMs);
	}

	@Override
	public String identifier() {
		return id;
	}

	@Override
	public void update(long elapsedTime) {
		if (finished) {
			return;
		}
		elapsedMs += elapsedTime;
		if (elapsedMs >= durationMs) {
			finished = true;
		}
	}

	@Override
	public Vector2f direction() {
		return finished ? Vector2f.at(0f, 0f) : vecPerSecond;
	}

	@Override
	public boolean isFinished() {
		return finished;
	}
}