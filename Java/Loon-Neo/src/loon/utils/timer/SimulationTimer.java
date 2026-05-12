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
package loon.utils.timer;

import loon.LSysException;
import loon.LSystem;
import loon.events.EventActionT;
import loon.geom.NumberValue;
import loon.utils.ArrayMap;
import loon.utils.ArrayMap.Entry;
import loon.utils.MathUtils;
import loon.utils.StringKeyValue;
import loon.utils.processes.GameProcessType;
import loon.utils.processes.RealtimeProcess;

/**
 * 时间模拟用类,用来虚拟游戏中的年月日变化,此函数时间流逝只与初始日期设定以及nextTimePass函数调用次数有关
 */
public class SimulationTimer extends RealtimeProcess {

	public enum MonthType {
		January, February, March, April, May, June, July, August, September, October, November, December
	}

	public final static boolean isLeapYear(int year) {
		return (year % 4 == 0) && (year % 100 != 0) || (year % 400 == 0);
	}

	private StringKeyValue _kvBuilder;
	private ArrayMap _monthDic = new ArrayMap();

	// 每次调用 nextTimePass或tick时增加的分钟数
	private float minutesPerTick;

	private int _year;
	private int _day;
	private int _hour;
	private MonthType _month;
	private float _minute;

	private NumberValue _bindYear;
	private NumberValue _bindMonth;
	private NumberValue _bindDay;
	private NumberValue _bindHour;
	private NumberValue _bindMinute;

	private boolean _dirty;
	private EventActionT<SimulationTimer> _timeEvent;

	public SimulationTimer(int year) {
		this(year, MonthType.January, 1, 0, 0f, 1f);
	}

	public SimulationTimer(int year, int day, int hour) {
		this(year, MonthType.January, day, hour, 0f, 1f);
	}

	public SimulationTimer(int year, MonthType month) {
		this(year, month, 1, 0, 0f, 1f);
	}

	public SimulationTimer(int year, MonthType month, int day) {
		this(year, month, day, 0, 0f, 1f);
	}

	public SimulationTimer(int year, MonthType month, int day, int hour) {
		this(year, month, day, hour, 0f, 1f);
	}

	public SimulationTimer(int year, MonthType month, int day, int hour, float minute, float speed) {
		if (year <= 0 || year >= 9999) {
			throw new LSysException("The year number is error , " + year + " is not a calculable year number");
		}
		if (month == null) {
			throw new LSysException("The month cannot be null !");
		}
		// 初始化每月天数
		_monthDic.put(MonthType.January, 31);
		_monthDic.put(MonthType.February, isLeapYear(year) ? 29 : 28);
		_monthDic.put(MonthType.March, 31);
		_monthDic.put(MonthType.April, 30);
		_monthDic.put(MonthType.May, 31);
		_monthDic.put(MonthType.June, 30);
		_monthDic.put(MonthType.July, 31);
		_monthDic.put(MonthType.August, 31);
		_monthDic.put(MonthType.September, 30);
		_monthDic.put(MonthType.October, 31);
		_monthDic.put(MonthType.November, 30);
		_monthDic.put(MonthType.December, 31);

		this.minutesPerTick = MathUtils.clamp(minutesPerTick, LSystem.MIN_SECONE_SPEED_FIXED, 65535f);
		this.setYear(year);
		this.setMonth(month);
		this.setDay(day);
		this.setHour(hour);
		this.setMinute(minute);
		this.setMinuteSpeed(speed);
		this.setProcessType(GameProcessType.SimulationTime);
	}

	/**
	 * 时间段判断（使用0为初值，0-23小时制）
	 * 
	 * @return
	 */
	public boolean isMidnight() {
		return _hour >= 0 && _hour < 6;
	}

	public boolean isMorning() {
		return _hour >= 6 && _hour < 8;
	}

	public boolean isBeforeNoon() {
		return _hour >= 8 && _hour < 12;
	}

	public boolean isAM() {
		return isMidnight() || isMorning() || isBeforeNoon();
	}

	public boolean isNoon() {
		return _hour >= 12 && _hour < 14;
	}

	public boolean isAfterNoon() {
		return _hour >= 14 && _hour < 18;
	}

	public boolean isEvening() {
		return _hour >= 18 && _hour < 24;
	}

	public boolean isPM() {
		return isNoon() || isAfterNoon() || isEvening();
	}

	/**
	 * 将MonthType转为1-12
	 * 
	 * @param m
	 * @return
	 */
	private int monthIndex(MonthType m) {
		return m.ordinal() + 1;
	}

	/**
	 * 将1..12转为MonthType（支持任意整数，自动循环）
	 * 
	 * @param idx
	 * @return
	 */
	private MonthType monthFromIndex(int idx) {
		int normalized = ((idx - 1) % 12 + 12) % 12;
		return MonthType.values()[normalized];
	}

	public MonthType getMonthDaysToNameType(int days) {
		for (int i = 0; i < _monthDic.size(); i++) {
			Entry it = _monthDic.getEntry(i);
			if (it != null && (int) it.getValue() == days) {
				return (MonthType) it.getKey();
			}
		}
		return null;
	}

	/**
	 * 返回对应月份的数值(1-12)
	 * 
	 * @param t
	 * @return
	 */
	public int getMonthNameTypeToInt(MonthType t) {
		if (t == null) {
			return -1;
		}
		return monthIndex(t);
	}

	/**
	 * 输入1-12的数值，返回MonthType(数值越界会循环变成12的余数计算)
	 * 
	 * @param m
	 * @return
	 */
	public MonthType getMonthIntToNameType(int m) {
		int normalized = ((m - 1) % 12 + 12) % 12;
		return MonthType.values()[normalized];
	}

	public int getMonthDays(MonthType m) {
		if (m == null) {
			return -1;
		}
		if (m == MonthType.February) {
			return isLeapYear(_year) ? 29 : 28;
		}
		return (int) _monthDic.get(m);
	}

	protected void onMinute(float m) {
		if (this._bindMinute == null) {
			this._bindMinute = new NumberValue(m);
		} else {
			this._bindMinute.update(m);
		}
	}

	protected void onHour(int h) {
		if (this._bindHour == null) {
			this._bindHour = new NumberValue(h);
		} else {
			this._bindHour.update(h);
		}
	}

	protected void onDay(int d) {
		if (this._bindDay == null) {
			this._bindDay = new NumberValue(d);
		} else {
			this._bindDay.update(d);
		}
	}

	protected void onMonth(int m) {
		if (this._bindMonth == null) {
			this._bindMonth = new NumberValue(m);
		} else {
			this._bindMonth.update(m);
		}
	}

	protected void onYear(int y) {
		if (this._bindYear == null) {
			this._bindYear = new NumberValue(y);
		} else {
			this._bindYear.update(y);
		}
	}

	public SimulationTimer setEventAction(EventActionT<SimulationTimer> e) {
		this._timeEvent = e;
		return this;
	}

	public EventActionT<SimulationTimer> getEventAction() {
		return this._timeEvent;
	}

	@Override
	public void run(LTimerContext time) {
		nextTimePass();
	}

	/**
	 * 设置每次tick增加的分钟数
	 * 
	 * @param m
	 */
	public SimulationTimer setMinutesPerTick(float m) {
		this.minutesPerTick = MathUtils.max(0f, m);
		this._dirty = true;
		return this;
	}

	/**
	 * 获取每次tick增加的分钟数
	 */
	public float getMinutesPerTick() {
		return this.minutesPerTick;
	}

	public SimulationTimer setMinuteSpeed(float m) {
		return setMinutesPerTick(m);
	}

	public float getMinuteSpeed() {
		return getMinutesPerTick();
	}

	/**
	 * 每个仿真刻推进一次
	 */
	public void tick() {
		nextTimePass();
	}

	/**
	 * 累加分钟，处理进位（分钟->小时->日->月->年）
	 */
	public void nextTimePass() {
		if (_timeEvent != null) {
			_timeEvent.update(this);
		}
		this._minute += this.minutesPerTick;
		onMinute(this._minute);
		if (this._minute >= 60f) {
			int extraHours = (int) (this._minute / 60f);
			this._minute = this._minute % 60f;
			onMinute(this._minute);
			addHourInternal(extraHours);
		}
	}

	/**
	 * 增加小时并处理进位到天
	 * 
	 * @param hours
	 */
	private void addHourInternal(int hours) {
		if (hours <= 0) {
			return;
		}
		this._hour += hours;
		onHour(this._hour);
		if (this._hour >= 24) {
			int days = this._hour / 24;
			this._hour = this._hour % 24;
			onHour(this._hour);
			addDayInternal(days);
		}
	}

	/**
	 * 增加天并处理进位到月
	 * 
	 * @param days
	 */
	private void addDayInternal(int days) {
		if (days <= 0) {
			return;
		}
		this._day += days;
		onDay(this._day);
		// 处理跨月（可能跨多月）
		while (this._day > getMonthDays(this._month)) {
			int monthDays = getMonthDays(this._month);
			this._day -= monthDays;
			// 进入下个月
			addMonthInternal(1);
			onDay(this._day);
		}
	}

	/**
	 * 增加月并处理进位到年
	 * 
	 * @param months
	 */
	private void addMonthInternal(int months) {
		if (months == 0) {
			return;
		}
		int currentIndex = monthIndex(this._month);
		int newIndex = currentIndex + months;
		int yearAdded = (newIndex - 1) / 12;
		int normalizedIndex = ((newIndex - 1) % 12) + 1;
		this._month = monthFromIndex(normalizedIndex);
		onMonth(normalizedIndex);
		if (yearAdded > 0) {
			addYearInternal(yearAdded);
		}
	}

	/**
	 * 增加年
	 * 
	 * @param years
	 */
	private void addYearInternal(int years) {
		if (years == 0) {
			return;
		}
		this._year += years;
		onYear(this._year);
		// 更新二月天数缓存
		_monthDic.put(MonthType.February, isLeapYear(_year) ? 29 : 28);
	}

	public NumberValue getYearBind() {
		return _bindYear;
	}

	public NumberValue getMonthBind() {
		return _bindMonth;
	}

	public NumberValue getDayBind() {
		return _bindDay;
	}

	public NumberValue getHourBind() {
		return _bindHour;
	}

	public NumberValue getMinuteBind() {
		return _bindMinute;
	}

	public int getYear() {
		return _year;
	}

	public int getMonth() {
		return getMonthNameTypeToInt(_month);
	}

	public int getDay() {
		return _day;
	}

	public int getHour() {
		return _hour;
	}

	public float getMinute() {
		return _minute;
	}

	public SimulationTimer setMinute(float m) {
		if (MathUtils.isNan(m)) {
			m = 0f;
		}
		this._minute = MathUtils.clamp(m, 0f, 59.999f);
		if (this._bindMinute == null) {
			this._bindMinute = new NumberValue(_minute);
		} else {
			this._bindMinute.update(_minute);
		}
		this._dirty = true;
		return this;
	}

	public SimulationTimer setHour(int h) {
		this._hour = MathUtils.clamp(h, 0, 23);
		if (this._bindHour == null) {
			this._bindHour = new NumberValue(_hour);
		} else {
			this._bindHour.update(_hour);
		}
		this._dirty = true;
		return this;
	}

	public SimulationTimer setDay(int d) {
		int maxDay = getMonthDays(this._month);
		this._day = MathUtils.clamp(d, 1, MathUtils.max(1, maxDay));
		if (this._bindDay == null) {
			this._bindDay = new NumberValue(_day);
		} else {
			this._bindDay.update(_day);
		}
		this._dirty = true;
		return this;
	}

	public SimulationTimer setMonth(int m) {
		int clamped = MathUtils.clamp(m, 1, 12);
		this._month = getMonthIntToNameType(clamped);
		if (this._bindMonth == null) {
			this._bindMonth = new NumberValue(clamped);
		} else {
			this._bindMonth.update(clamped);
		}
		this._dirty = true;
		return this;
	}

	public SimulationTimer setMonth(MonthType m) {
		if (m == null) {
			return this;
		}
		this._month = m;
		int idx = getMonthNameTypeToInt(m);
		if (this._bindMonth == null) {
			this._bindMonth = new NumberValue(idx);
		} else {
			this._bindMonth.update(idx);
		}
		this._dirty = true;
		return this;
	}

	public SimulationTimer setYear(int y) {
		this._year = MathUtils.clamp(y, 1, 9999);
		if (this._bindYear == null) {
			this._bindYear = new NumberValue(_year);
		} else {
			this._bindYear.update(_year);
		}
		// 更新二月天数缓存
		_monthDic.put(MonthType.February, isLeapYear(_year) ? 29 : 28);
		this._dirty = true;
		return this;
	}

	public boolean isDirty() {
		return this._dirty;
	}

	public SimulationTimer setDirty(boolean d) {
		this._dirty = d;
		return this;
	}

	public SimulationTimer addYear(int y) {
		addYearInternal(y);
		return this;
	}

	public SimulationTimer addMonth(int m) {
		addMonthInternal(m);
		return this;
	}

	public SimulationTimer addDay(int d) {
		addDayInternal(d);
		return this;
	}

	public SimulationTimer addHour(int h) {
		addHourInternal(h);
		return this;
	}

	public SimulationTimer addMinute(int m) {
		if (m <= 0) {
			return this;
		}
		float totalMinutes = this._minute + m;
		int extraHours = (int) (totalMinutes / 60f);
		this._minute = totalMinutes % 60f;
		onMinute(this._minute);
		if (extraHours > 0) {
			addHourInternal(extraHours);
		}
		return this;
	}

	/**
	 * 按秒推进
	 * 
	 * @param seconds
	 * @return
	 */
	public SimulationTimer advanceBySeconds(int seconds) {
		if (seconds <= 0) {
			return this;
		}
		int addMinutes = seconds / 60;
		int remSeconds = seconds % 60;
		// 先按整分钟推进
		if (addMinutes > 0) {
			addMinute(addMinutes);
		}
		// 剩余秒数转换为小数分钟
		if (remSeconds > 0) {
			float fractional = remSeconds / 60f;
			this._minute += fractional;
			if (this._minute >= 60f) {
				int extraHours = (int) (this._minute / 60f);
				this._minute = this._minute % 60f;
				addHourInternal(extraHours);
			}
			onMinute(this._minute);
		}
		return this;
	}

	public SimulationTimer setMinutesPerSecond(float minutesPerSecond, float ticksPerSecond) {
		if (ticksPerSecond <= 0f) {
			throw new LSysException("ticksPerSecond must be > 0");
		}
		return setMinutesPerTick(minutesPerSecond / ticksPerSecond);
	}

	public float getMinutesPerSecond(float ticksPerSecond) {
		if (ticksPerSecond <= 0f) {
			return 0f;
		}
		return this.minutesPerTick * ticksPerSecond;
	}

	public String toData() {
		return toString(LSystem.EMPTY, true);
	}

	public String toString(String name, boolean newLine) {
		if (_kvBuilder == null) {
			_kvBuilder = new StringKeyValue(name);
		} else {
			_kvBuilder.clear();
		}
		if (newLine) {
			_kvBuilder.kv("year", _year).comma().newLine().kv("month", _month).comma().newLine().kv("day", _day).comma()
					.newLine().kv("hour", _hour).comma().newLine().kv("minute", _minute);
		} else {
			_kvBuilder.kv("year", _year).comma().kv("month", _month).comma().kv("day", _day).comma().kv("hour", _hour)
					.comma().kv("minute", _minute);
		}
		return _kvBuilder.toData();
	}

	@Override
	public String toString() {
		return toString("SimulationTimer", true);
	}

}
