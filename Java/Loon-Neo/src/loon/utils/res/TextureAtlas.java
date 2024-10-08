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
package loon.utils.res;

import loon.Json;
import loon.LRelease;
import loon.Json.TypedArray;
import loon.LTexture;
import loon.utils.ListMap;
import loon.utils.TArray;

public class TextureAtlas implements LRelease {

	protected LTexture _img = null;

	protected ListMap<String, TextureData> _frames = null;

	protected TArray<String> names = new TArray<String>();

	public LTexture img() {
		return _img;
	}

	public TextureData getFrame(String name) {
		return _frames.get(name);
	}

	public TextureAtlas(LTexture img, Json.Object jsonObj) {
		init(img, jsonObj);
	}

	public TextureAtlas(LTexture img, ListMap<String, TextureData> frames) {
		_img = img;
		_frames = frames;
	}

	public void init(LTexture img, Json.Object jsonObj) {
		_img = img;
		_frames = getDatas(jsonObj);
	}

	public Texture getTexture(String name) {
		TextureData td = _frames.get(name);
		if (td == null) {
			return null;
		}
		return new Texture(_img, td);
	}

	public TextureAtlas getTextureAtlas(String prefix) {
		ListMap<String, TextureData> frames = new ListMap<String, TextureData>();
		for (int i = 0; i < _frames.size; i++) {
			TextureData td = _frames.getValueAt(i);
			if (null != td && td.name.startsWith(prefix)) {
				frames.put(td.name, td);
			}
		}
		return new TextureAtlas(_img, frames);
	}

	public ListMap<String, TextureData> getDatas(Json.Object jsonObj) {
		return getDatas(jsonObj, 1f, 1f);
	}

	public ListMap<String, TextureData> getDatas(Json.Object jsonObj, float sx, float sy) {
		Json.Object jsonFrames = jsonObj.getObject("frames");
		if (jsonFrames != null) {
			TypedArray<String> keys = jsonFrames.keys();
			int charAmount = keys.length();
			ListMap<String, TextureData> frames = new ListMap<String, TextureData>(charAmount);
			for (int i = 0; i < charAmount; i++) {
				String key = keys.get(i);
				Json.Object jsonChar = jsonFrames.getObject(key);
				TextureData data = new TextureData();
				names.add(key);
				data.name = key;
				data.x = jsonChar.getInt("x");
				data.y = jsonChar.getInt("y");
				data.w = jsonChar.getInt("w");
				data.h = jsonChar.getInt("h");
				data.offX = jsonChar.getInt("offX");
				data.offY = jsonChar.getInt("offY");
				if (jsonChar.containsKey("sourceW")) {
					data.sourceW = jsonChar.getInt("sourceW");
				} else {
					data.sourceW = data.w + data.offX;
				}
				if (jsonChar.containsKey("sourceH")) {
					data.sourceH = jsonChar.getInt("sourceH");
				} else {
					data.sourceH = data.h + data.offY;
				}
				data.x *= sx;
				data.w *= sx;
				data.offX *= sx;
				data.sourceW *= sx;
				data.y *= sy;
				data.h *= sy;
				data.offY *= sy;
				data.sourceH *= sy;
				frames.put(key, data);
			}
			return frames;
		} else {
			TypedArray<String> keys = jsonObj.keys();
			for (int i = 0; i < keys.length(); i++) {
				String key = keys.get(i);
				if ("res".equals(key)) {
					Json.Object jsonObject = jsonObj.getObject(key);
					TypedArray<String> resKeys = jsonObject.keys();
					int charAmount = resKeys.length();
					ListMap<String, TextureData> frames = new ListMap<String, TextureData>(charAmount);
					for (int j = 0; j < charAmount; j++) {
						String name = resKeys.get(j);
						names.add(name);
						Json.Object jsonResult = jsonObject.getObject(name);
						TextureData data = new TextureData();
						data.name = name;
						data.x = jsonResult.getInt("x");
						data.y = jsonResult.getInt("y");
						data.w = jsonResult.getInt("w");
						data.h = jsonResult.getInt("h");
						data.offX = jsonResult.getInt("offX");
						data.offY = jsonResult.getInt("offY");
						if (jsonResult.containsKey("sourceW")) {
							data.sourceW = jsonResult.getInt("sourceW");
						} else {
							data.sourceW = data.w + data.offX;
						}
						if (jsonResult.containsKey("sourceH")) {
							data.sourceH = jsonResult.getInt("sourceH");
						} else {
							data.sourceH = data.h + data.offY;
						}
						data.x *= sx;
						data.w *= sx;
						data.offX *= sx;
						data.sourceW *= sx;
						data.y *= sy;
						data.h *= sy;
						data.offY *= sy;
						data.sourceH *= sy;
						frames.put(name, data);

					}
					return frames;

				}

			}

			return null;
		}

	}

	@Override
	public void close() {
		if (_img != null) {
			_img.close();
			_img = null;
		}

	}

	public boolean isClosed() {
		return _img == null || _img.isClosed();
	}
}
