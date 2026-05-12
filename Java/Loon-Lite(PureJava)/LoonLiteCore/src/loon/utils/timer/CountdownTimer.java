/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
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
package loon.utils.timer;

import loon.LSystem;
import loon.events.EventActionT;
import loon.geom.BooleanValue;
import loon.utils.MathUtils;
import loon.utils.StringKeyValue;
import loon.utils.StringUtils;
import loon.utils.processes.GameProcessType;
import loon.utils.processes.RealtimeProcess;
import loon.utils.processes.RealtimeProcessManager;

/**
 * 倒计时处理器(比如勇者30之类需要倒数计算时间的游戏会用到)
 */
public class CountdownTimer extends RealtimeProcess {

	// 因为考虑web平台的关系，所以毫秒精确不到小数点后3位，只能2位……（gwt没有实现精确的nanoTime,获取3位最后一个数也会是0）
	private static final String DEF_FORMAT = "s{0}m";
	private static final String SEPARATOR_FORMAT = ":";

	private String _separator = SEPARATOR_FORMAT;
	private String _format = DEF_FORMAT;
	private String _result = DEF_FORMAT;
	private String _currentName;

	private float _second = 0f;

	// 毫秒显示位数（0表示不显示毫秒）
	private int _digits = 2;

	private long _millisecond = 0L;
	// 运行状态
	private final BooleanValue _finished = new BooleanValue(false);
	private final BooleanValue _running = new BooleanValue(false);

	// 是否显示毫秒（等同于 digits > 0）
	private boolean _displayMillisecond = true;

	// 回调
	private Runnable _onFinish;
	private EventActionT<Long> _onTick;

	private final LTimer _tickInterval = new LTimer();

	public CountdownTimer() {
		this(0f, true);
	}

	public CountdownTimer(float second) {
		this(second, true);
	}

	public CountdownTimer(float second, boolean displayMilliSecond) {
		this(LSystem.UNKNOWN, second, displayMilliSecond);
	}

	public CountdownTimer(String name, float second, boolean displayMilliSecond) {
		super(0);
		this._currentName = name;
		this._digits = 2;
		this.set(second);
		this.setProcessType(GameProcessType.Time);
		this.setDisplayMilliSecond(displayMilliSecond);
	}

	/**
	 * 直接以秒为单位增加时间
	 * 
	 * @param second
	 */
	public void add(float second) {
		long addMs = (long) (second * LSystem.SECOND);
		_millisecond += addMs;
		_second = MathUtils.max(0f, _millisecond / (float) LSystem.SECOND);
	}

	/**
	 * 以秒设置倒计时
	 * 
	 * @param second
	 */
	public void set(float second) {
		if (second < 0) {
			second = 0;
		}
		this._second = second;
		this._millisecond = ((long) (second * LSystem.SECOND));
		this._finished.set(false);
		this._result = formatZeroTimeData();
	}

	/**
	 * 以毫秒直接设置倒计时
	 * 
	 * @param millis
	 */
	public void setMilliseconds(long millis) {
		if (millis < 0) {
			millis = 0;
		}
		this._millisecond = millis;
		this._second = MathUtils.max(0f, millis / (float) LSystem.SECOND);
		this._finished.set(false);
		this._result = formatZeroTimeData();
	}

	@Override
	public void kill() {
		super.kill();
		this._finished.set(true);
		this._running.set(false);
	}

	public float startSecond() {
		return _second;
	}

	public String getTime() {
		return nowSecond();
	}

	public long getMillisecondLong() {
		return _millisecond;
	}

	/**
	 * 返回以秒为单位的浮点毫秒值
	 */
	public float getMillisecond() {
		return MathUtils.max((float) _millisecond / LSystem.SECOND, 0f);
	}

	public long getSecond() {
		return MathUtils.max(_millisecond / LSystem.SECOND, 0L);
	}

	protected String formatZeroTimeData() {
		return formatTimeData("0", "0");
	}

	protected String formatTimeData(String sSeconds, String sMillis) {
		String f = StringUtils.format(_format, _separator);
		String secStr = (sSeconds != null) ? MathUtils.addZeros(sSeconds, _digits) : MathUtils.addZeros("0", _digits);
		f = StringUtils.replaceIgnoreCase(f, "s", secStr);
		if (_displayMillisecond && _digits > 0) {
			String msStr = (sMillis != null) ? MathUtils.addZeros(sMillis, _digits) : MathUtils.addZeros("0", _digits);
			f = StringUtils.replaceIgnoreCase(f, "m", msStr);
		} else {
			f = StringUtils.replaceIgnoreCase(f, _separator, "");
			f = StringUtils.replaceIgnoreCase(f, "m", "");
		}
		this._result = f;
		return this._result;
	}

	/**
	 * 生成当前显示字符串
	 */
	public String nowSecond() {
		if (StringUtils.isEmpty(_result)) {
			return StringUtils.format(_format, _separator);
		}
		long ms = _millisecond;
		if (ms < 0) {
			ms = 0;
		}
		if (_displayMillisecond && _digits > 0) {
			long seconds = ms / 1000L;
			int msDigits = _digits;
			// 计算要显示的毫秒位数
			int divisor = (int) MathUtils.pow(10, 3 - MathUtils.min(3, MathUtils.max(0, msDigits)));
			int msPart = (int) ((ms % 1000L) / divisor);
			String sSec = String.valueOf(seconds);
			String sMs = String.valueOf(msPart);
			return formatTimeData(sSec, sMs);
		} else {
			long seconds = ms / 1000L;
			return formatTimeData(String.valueOf(seconds), null);
		}
	}

	/**
	 * 开始并加入RealtimeProcessManager
	 */
	public CountdownTimer play() {
		return play(this._second);
	}

	public CountdownTimer play(float second) {
		synchronized (CountdownTimer.class) {
			this.set(second);
			RealtimeProcessManager manager = RealtimeProcessManager.get();
			manager.delete(getId());
			super.isDead = false;
			manager.addProcess(this);
			this._running.set(true);
			this._finished.set(false);
		}
		return this;
	}

	/**
	 * 暂停计时
	 */
	@Override
	public CountdownTimer pause() {
		super.pause();
		this._running.set(false);
		return this;
	}

	/**
	 * 继续计时
	 */
	@Override
	public CountdownTimer resume() {
		super.resume();
		if (!_finished.get()) {
			this._running.set(true);
		}
		return this;
	}

	/**
	 * 重置为初始秒数
	 */
	@Override
	public CountdownTimer reset() {
		super.reset();
		set(this._second);
		this._finished.set(false);
		this._running.set(false);
		return this;
	}

	public void restart() {
		play(this._second);
	}

	public boolean isCompleted() {
		return _finished.get();
	}

	public boolean isRunning() {
		return _running.get();
	}

	@Override
	public void run(LTimerContext time) {
		if (!_running.get() || _finished.get()) {
			return;
		}
		float elapsed = time.unscaledTimeSinceLastUpdate;
		_millisecond -= elapsed;
		long remain = _millisecond;
		if (remain <= 0) {
			_millisecond = 0;
			_running.set(false);
			_finished.set(true);
			if (_onTick != null) {
				_onTick.update(0L);
			}
			if (_onFinish != null) {
				_onFinish.run();
			}
			kill();
			return;
		}
		if (_tickInterval.action(elapsed)) {
			if (_onTick != null) {
				_onTick.update(remain);
			}
		}
	}

	public String getSeparator() {
		return _separator;
	}

	public CountdownTimer setSeparator(String separator) {
		this._separator = separator;
		return this;
	}

	public boolean isDisplayMilliSecond() {
		return _displayMillisecond;
	}

	public CountdownTimer setDisplayMilliSecond(boolean d) {
		this._displayMillisecond = d;
		if (!d) {
			this._digits = 0;
		} else if (this._digits == 0) {
			this._digits = 2;
		}
		return this;
	}

	public CountdownTimer resetDefaultFormat() {
		return setFormat(DEF_FORMAT);
	}

	public String getFormat() {
		return _format;
	}

	public CountdownTimer setFormat(String f) {
		if (f == null || f.isEmpty()) {
			f = DEF_FORMAT;
		}
		this._format = f;
		return this;
	}

	public int getDigits() {
		return _digits;
	}

	/**
	 * 设置毫秒显示位数（0表示不显示毫秒）
	 * 
	 * @param d
	 * @return
	 */
	public CountdownTimer setDigits(int d) {
		if (d < 0) {
			d = 0;
		} else if (d > 3) {
			d = 3;
		}
		this._digits = d;
		this._displayMillisecond = d > 0;
		return this;
	}

	public String getResult() {
		return this._result;
	}

	public CountdownTimer setName(String n) {
		this._currentName = n;
		return this;
	}

	public String getName() {
		return this._currentName;
	}

	public CountdownTimer setOnFinish(Runnable r) {
		this._onFinish = r;
		return this;
	}

	public CountdownTimer setOnTick(EventActionT<Long> tick) {
		this._onTick = tick;
		return this;
	}

	/**
	 * 设置tick回调最小间隔（毫秒），避免每帧都回调导致性能问题
	 * 
	 * @param millis
	 * @return
	 */
	public CountdownTimer setTickIntervalMillis(long millis) {
		if (millis < 0) {
			millis = 0;
		}
		this._tickInterval.setDelay(millis);
		return this;
	}

	@Override
	public String toString() {
		StringKeyValue builder = new StringKeyValue("CountdownTimer");
		builder.kv("name", _currentName).comma().kv("second", _second).comma().kv("millisecond", _millisecond).comma()
				.kv("result", _result).comma().kv("finished", _finished.get()).comma().kv("running", _running.get());
		return builder.toString();
	}
}
