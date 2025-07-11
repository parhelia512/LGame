/**
 * Copyright 2008 - 2012
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
 * @version 0.3.3
 */
package loon.action.node;

import loon.LSystem;
import loon.LTexture;
import loon.LTextures;
import loon.action.Flip;
import loon.action.sprite.SpriteBatch;
import loon.geom.RectBox;
import loon.geom.Vector2f;
import loon.opengl.BlendState;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;

public class LNSprite extends LNNode implements Flip<LNSprite> {

	private BlendState blendState = BlendState.NonPremultiplied;

	private float rotation;

	protected LTexture _texture;

	private float[] pos, scale;

	protected LNAnimation _ans;

	protected boolean _flipX = false, _flipY = false;

	protected ObjectMap<String, LNAnimation> _anims;

	public LNSprite(RectBox rect) {
		super(rect);
		this._ans = null;
		super._anchor = new Vector2f(0f, 0f);
	}

	public LNSprite(int x, int y, int w, int h) {
		super(x, y, w, h);
		this._ans = null;
		super._anchor = new Vector2f(0f, 0f);
	}

	public LNSprite(String fsName) {
		super();
		this._ans = null;
		LNFrameStruct struct = LNDataCache.getFrameStruct(fsName);
		if (struct == null) {
			throw new RuntimeException("");
		}
		this._texture = struct._texture;
		super._left = struct._textCoords.x();
		super._top = struct._textCoords.y();
		super._orig_width = struct._orig_width;
		super._orig_height = struct._orig_height;
		super.setNodeSize(struct._size_width, struct._size_height);
		super._anchor.set(struct._anchor);
		this.blendState = struct._state;
		if (!struct._place.equals(0, 0)) {
			setPosition(struct._place);
		}
	}

	public LNSprite() {
		this(LSystem.viewSize.getRect());
	}

	public static LNSprite GInitWithTexture(LTexture tex2d) {
		LNSprite sprite = new LNSprite();
		sprite.initWithTexture(tex2d);
		return sprite;
	}

	public static LNSprite GInitWithFilename(String file) {
		LNSprite sprite = new LNSprite();
		sprite.initWithFilename(file);
		return sprite;
	}

	public static LNSprite GInitWithFrameStruct(LNFrameStruct fs) {
		LNSprite sprite = new LNSprite();
		sprite.initWithFrameStruct(fs);
		return sprite;
	}

	public static LNSprite GInitWithFrameStruct(String fsName) {
		return GInitWithFrameStruct(LNDataCache.getFrameStruct(fsName));
	}

	public static LNSprite GInitWithAnimation(LNAnimation ans) {
		LNSprite sprite = new LNSprite();
		sprite.initWithAnimation(ans, 0);
		return sprite;
	}

	@Override
	public void draw(SpriteBatch batch) {
		if (super._visible && (this._texture != null)) {
			pos = super.convertToWorldPos();
			if (_screenRect.intersects(pos[0], pos[1], getWidth(), getHeight())
					|| _screenRect.contains(pos[0], pos[1])) {
				if (_objectSuper != null) {
					rotation = convertToWorldRot();
					scale = convertToWorldScale();
				} else {
					rotation = super._objectRotation;
					scale[0] = _scale.x;
					scale[1] = _scale.y;
				}
				batch.setColor(super._color.r, super._color.g, super._color.b, super._objectAlpha);
				if (rotation == 0 && scale[0] == 1f && scale[1] == 1f) {
					batch.draw(_texture, pos[0], pos[1], super._size_width, super._size_height, super._left, super._top,
							super._orig_width, super._orig_height, _flipX, _flipY);
				} else {
					batch.draw(_texture, pos[0], pos[1], _anchor.x, _anchor.y, super._size_width, super._size_height,
							scale[0], scale[1], MathUtils.toDegrees(rotation), super._left, super._top,
							super._orig_width, super._orig_height, _flipX, _flipY);
				}
				batch.resetColor();
				BlendState oldState = batch.getBlendState();
				if (blendState != oldState) {
					batch.submit(blendState);
					batch.setBlendState(oldState);
				}
			}
		}
	}

	@Override
	public void draw(GLEx g) {
		if (super._visible && (this._texture != null)) {
			if (this.pos == null) {
				this.pos = new float[2];
			}
			if (this.scale == null) {
				this.scale = new float[2];
			}
			pos = super.convertToWorldPos();
			if (_screenRect.intersects(pos[0], pos[1], getWidth(), getHeight())
					|| _screenRect.contains(pos[0], pos[1])) {
				int color = g.color();
				if (_objectSuper != null) {
					rotation = convertToWorldRot();
					scale = convertToWorldScale();
				} else {
					rotation = super._objectRotation;
					scale[0] = _scale.x;
					scale[1] = _scale.y;
				}
				g.setColor(super._color.r, super._color.g, super._color.b, super._objectAlpha);
				g.draw(_texture, pos[0] - _anchor.x, pos[1] - _anchor.y, _anchor, super._size_width, super._size_height,
						scale[0], scale[1], MathUtils.toDegrees(rotation), super._left, super._top, super._orig_width,
						super._orig_height, _flipX, _flipY, null);
				g.setColor(color);
			}
		}
	}

	public final void initWithTexture(LTexture tex2d) {
		this._texture = tex2d;
		super._left = 0;
		super._top = 0;
		super.setNodeSize(_texture.getWidth(), _texture.getHeight());
		super._anchor.set(super.getWidth() / 2f, super.getHeight() / 2f);
	}

	public final void initWithFilename(String filename) {
		this._texture = LTextures.loadTexture(filename);
		super._left = 0;
		super._top = 0;
		super.setNodeSize(_texture.getWidth(), _texture.getHeight());
		super._anchor.set(super.getWidth() / 2f, super.getHeight() / 2f);
	}

	public final void initWithFrameStruct(LNFrameStruct fs) {
		this._texture = fs._texture;
		blendState = fs._state;
		super._left = fs._textCoords.x();
		super._top = fs._textCoords.y();
		super._orig_width = fs._orig_width;
		super._orig_height = fs._orig_height;
		super.setNodeSize(fs._size_width, fs._size_height);
		super._anchor.set(fs._anchor);
	}

	public final void initWithAnimation(LNAnimation ans, int idx) {
		if (ans != null) {
			if (this._anims == null) {
				this._anims = new ObjectMap<String, LNAnimation>();
			}
			this._ans = ans;
			initWithFrameStruct(this._ans.getFrame(idx));
			_anims.put(ans.getName(), ans);
		}
	}

	public void addAnimation(LNAnimation anim) {
		initWithAnimation(anim, 0);
	}

	public void setFrame(String animName, int index) {
		if (this._anims == null) {
			this._anims = new ObjectMap<String, LNAnimation>();
		}
		if (this._anims.containsKey(animName)) {
			this._ans = this._anims.get(animName);
			initWithAnimation(this._ans, index);
		}
	}

	public void setFrame(int idx) {
		if (_ans != null) {
			initWithFrameStruct(_ans.getFrame(idx));
		}
	}

	public void setFrameTime(float time) {
		if (_ans != null) {
			initWithFrameStruct(_ans.getFrameByTime(time));
		}
	}

	public final LTexture getFrameTexture() {
		return this._texture;
	}

	public final LNAnimation getAnimation() {
		return this._ans;
	}

	public final void setAnimation(LNAnimation ans) {
		this._ans = ans;
		this.initWithTexture(this._ans.getFrame(0)._texture);
	}

	public boolean isFlipX() {
		return _flipX;
	}

	public LNSprite setFlipX(boolean flipX) {
		this._flipX = flipX;
		return this;
	}

	public boolean isFlipY() {
		return _flipY;
	}

	public LNSprite setFlipY(boolean flipY) {
		this._flipY = flipY;
		return this;
	}

	@Override
	public LNSprite setFlipXY(boolean x, boolean y) {
		setFlipX(x);
		setFlipY(y);
		return this;
	}

	public BlendState getBlendState() {
		return blendState;
	}

	public void setBlendState(BlendState blendState) {
		this.blendState = blendState;
	}

}
