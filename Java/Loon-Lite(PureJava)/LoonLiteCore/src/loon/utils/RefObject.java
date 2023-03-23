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
package loon.utils;

import loon.LRelease;
import loon.LSystem;
import loon.utils.reply.Callback;

/**
 * 模拟C#中ref以返回注入对象的修改结果，也就是引用传递
 *
 * @param <T>
 */
public class RefObject<T> implements LRelease {

	public static final <T> RefObject<T> of(T v) {
		return getValue(v);
	}
	
	public static final <T> RefObject<T> getValue(T v) {
		return new RefObject<T>(v);
	}

	private Callback<T> closed;

	public T argvalue;

	public RefObject(T refarg) {
		argvalue = refarg;
	}

	public void set(T value) {
		this.argvalue = value;
	}

	public boolean hasValue() {
		return argvalue != null;
	}

	public T get() {
		return argvalue;
	}

	public T result() {
		return argvalue;
	}

	public Callback<T> getClosed() {
		return closed;
	}

	public void setClosed(Callback<T> closed) {
		this.closed = closed;
	}

	@Override
	public String toString() {
		return StringUtils.toString(argvalue, LSystem.NULL);
	}

	@Override
	public void close() {
		if (argvalue != null && closed != null) {
			closed.onSuccess(argvalue);
		}
	}

}