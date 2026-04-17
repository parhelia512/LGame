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
package loon.utils;

public final class IntFloatMap {

	private static final float NO_VALUE = Float.NaN;

	private int[] keys;
	private float[] values;
	private boolean[] used;
	private int capacity;
	private int size;
	private final int maxSize;

	public IntFloatMap(int initialCapacity) {
		this(initialCapacity, 512);
	}

	public IntFloatMap(int initialCapacity, int maxSize) {
		this.capacity = MathUtils.nextPowerOfTwo(initialCapacity);
		this.keys = new int[capacity];
		this.values = new float[capacity];
		this.used = new boolean[capacity];
		this.size = 0;
		this.maxSize = maxSize;
	}

	private int hash(int key) {
		return (key * 0x9E3779B9) & (capacity - 1);
	}

	public void put(int key, float value) {
		if (size >= maxSize) {
			clear();
		}
		int idx = hash(key);
		while (used[idx]) {
			if (keys[idx] == key) {
				values[idx] = value;
				return;
			}
			idx = (idx + 1) & (capacity - 1);
		}
		used[idx] = true;
		keys[idx] = key;
		values[idx] = value;
		size++;
		if (size * 2 > capacity) {
			resize();
		}
	}

	public float get(int key) {
		int idx = hash(key);
		while (used[idx]) {
			if (keys[idx] == key) {
				return values[idx];
			}
			idx = (idx + 1) & (capacity - 1);
		}
		return NO_VALUE;
	}

	public boolean contains(int key) {
		return !MathUtils.isNan(get(key));
	}

	public void remove(int key) {
		int idx = hash(key);
		while (used[idx]) {
			if (keys[idx] == key) {
				used[idx] = false;
				size--;
				return;
			}
			idx = (idx + 1) & (capacity - 1);
		}
	}

	public boolean removeIndex(int index) {
		if (index >= 0 && index < capacity && used[index]) {
			used[index] = false;
			size--;
			return true;
		}
		return false;
	}

	public float sumValues() {
		int sum = 0;
		for (int i = 0; i < capacity; i++) {
			if (used[i]) {
				sum += values[i];
			}
		}
		return sum;
	}

	public float averageValues() {
		if (size == 0) {
			return 0;
		}
		return sumValues() / size;
	}

	public float maxValue() {
		float max = Float.MIN_VALUE;
		for (int i = 0; i < capacity; i++) {
			if (used[i] && values[i] > max) {
				max = values[i];
			}
		}
		return max;
	}

	public float minValue() {
		float min = Float.MAX_VALUE;
		for (int i = 0; i < capacity; i++) {
			if (used[i] && values[i] < min) {
				min = values[i];
			}
		}
		return min;
	}

	public int countValue(float target) {
		int count = 0;
		for (int i = 0; i < capacity; i++) {
			if (used[i] && values[i] == target) {
				count++;
			}
		}
		return count;
	}

	public int[] keysArray() {
		int[] arr = new int[size];
		int idx = 0;
		for (int i = 0; i < capacity; i++) {
			if (used[i]) {
				arr[idx++] = keys[i];
			}
		}
		return arr;
	}

	public float[] valuesArray() {
		float[] arr = new float[size];
		int idx = 0;
		for (int i = 0; i < capacity; i++) {
			if (used[i]) {
				arr[idx++] = values[i];
			}
		}
		return arr;
	}

	public void clear() {
		this.used = new boolean[capacity];
		this.size = 0;
	}

	private void resize() {
		int newCap = capacity << 1;
		int[] oldKeys = keys;
		float[] oldValues = values;
		boolean[] oldUsed = used;

		keys = new int[newCap];
		values = new float[newCap];
		used = new boolean[newCap];
		capacity = newCap;
		size = 0;

		for (int i = 0; i < oldKeys.length; i++) {
			if (oldUsed[i]) {
				put(oldKeys[i], oldValues[i]);
			}
		}
	}

	public int size() {
		return size;
	}

}
