/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed _to in writing, software
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
package loon.utils.timer;

import loon.LSystem;
import loon.events.EventAction;
import loon.utils.HelperUtils;
import loon.utils.MathUtils;
import loon.utils.StringKeyValue;
import loon.utils.TimeUtils;

/**
 * 计时器(也就是俗称的秒表，需要正常计算时间的游戏都会用到)
 */
public class StopwatchTimer {

	public static interface OnCompleteListener {
		void onComplete(StopwatchTimer timer, long elapsedMillis);
	}

	public static String formatElapsed(long millis) {
		long ms = millis % 1000;
		long totalSeconds = millis / 1000;
		long s = totalSeconds % 60;
		long totalMinutes = totalSeconds / 60;
		long m = totalMinutes % 60;
		long h = totalMinutes / 60;
		return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
	}

	public static StopwatchTimer create() {
		return make();
	}

	public static StopwatchTimer make() {
		return new StopwatchTimer();
	}

	public static StopwatchTimer begin() {
		StopwatchTimer sw = new StopwatchTimer();
		sw.start();
		return sw;
	}

	public static StopwatchTimer run(EventAction a) {
		StopwatchTimer sw = begin();
		HelperUtils.callEventAction(a, sw);
		sw.stop();
		return sw;
	}

	private OnCompleteListener _onCompleteListener;
	private boolean autoResetOnComplete = false;
	private String _currentName;

	private long _from;
	private long _to;
	private long _lastStop;
	private long _target;
	private long _timeOn = -1;
	private long _timeOff = -1;
	private long _accumulated;

	private boolean _over;
	private boolean _running;

	public StopwatchTimer() {
		this(LSystem.EMPTY);
	}

	public StopwatchTimer(String name) {
		this(name, 0L);
	}

	public StopwatchTimer(long target) {
		this(LSystem.EMPTY, target);
	}

	public StopwatchTimer(String name, long target) {
		this._currentName = name;
		this._target = target;
		reset();
	}

	private long currentTime() {
		return TimeUtils.millis();
	}

	public boolean isWaiting() {
		return isRunning();
	}

	public boolean isRunning() {
		return _running;
	}

	public long start() {
		if (!_running) {
			_from = (_timeOn == -1) ? currentTime() : _timeOn;
			_to = _from;
			_running = true;
			_over = false;
		}
		return _from;
	}

	public StopwatchTimer stop() {
		if (_running) {
			_lastStop = _to;
			_to = (_timeOff == -1) ? currentTime() : _timeOff;
			long segment = _to - _from;
			if (segment < 0) {
				segment = 0;
			}
			_accumulated += segment;
			_running = false;
			_over = true;
			checkTargetAndNotify();
		}
		return this;
	}

	public StopwatchTimer end() {
		return stop();
	}

	public StopwatchTimer pause() {
		return stop();
	}

	public StopwatchTimer resume() {
		start();
		return this;
	}

	public StopwatchTimer reset() {
		_from = 0;
		_to = 0;
		_lastStop = 0;
		_accumulated = 0;
		_running = false;
		_over = false;
		_timeOn = -1;
		_timeOff = -1;
		return this;
	}

	public long getTime() {
		if (_running) {
			long now = currentTime();
			long seg = now - _from;
			if (seg < 0) {
				seg = 0;
			}
			return _accumulated + seg;
		} else {
			return _accumulated;
		}
	}

	public long getDuration() {
		if (_running) {
			long now = currentTime();
			long seg = now - _from;
			return MathUtils.max(0, seg);
		} else {
			return MathUtils.max(0, _to - _from);
		}
	}

	public long getLastDuration() {
		if (_lastStop == 0) {
			return 0;
		}
		return MathUtils.max(0, _to - _lastStop);
	}

	public long getStartTime() {
		return _from;
	}

	public long getEndTime() {
		return _to;
	}

	public boolean isDone() {
		if (_target <= 0) {
			return false;
		}
		return getTime() >= _target;
	}

	public boolean isDoneAndReset() {
		if (isDone()) {
			reset();
			return true;
		}
		return false;
	}

	public boolean isPassedTime(long interval) {
		return getTime() >= interval;
	}

	public StopwatchTimer setName(String n) {
		this._currentName = n;
		return this;
	}

	public String getName() {
		return this._currentName;
	}

	public long getTimeOn() {
		return _timeOn;
	}

	public long getTimeOff() {
		return _timeOff;
	}

	public StopwatchTimer setTimeOn(long timeOn) {
		_timeOn = timeOn;
		return this;
	}

	public StopwatchTimer setTimeOff(long timeOff) {
		_timeOff = timeOff;
		return this;
	}

	public StopwatchTimer setOnCompleteListener(OnCompleteListener l) {
		this._onCompleteListener = l;
		return this;
	}

	public StopwatchTimer setAutoResetOnComplete(boolean v) {
		this.autoResetOnComplete = v;
		return this;
	}

	private void checkTargetAndNotify() {
		if (_target > 0 && getTime() >= _target) {
			if (_onCompleteListener != null) {
				try {
					_onCompleteListener.onComplete(this, getTime());
				} catch (Throwable t) {
				}
			}
			if (autoResetOnComplete) {
				reset();
			}
		}
	}

	public boolean completed() {
		return this._over;
	}

	public long getTimestamp() {
		return currentTime();
	}

	public String formatElapsed() {
		return formatElapsed(getTime());
	}

	@Override
	public String toString() {
		StringKeyValue builder = new StringKeyValue("StopwatchTimer");
		builder.kv("name", _currentName).comma().kv("from", _from).comma().kv("to", _to).comma()
				.kv("lastStop", _lastStop).comma().kv("target", _target);
		return builder.toString();
	}
}
