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
package loon.action.sprite.effect;

import loon.LSysException;
import loon.LSystem;
import loon.action.sprite.ISprite;
import loon.canvas.LColor;
import loon.opengl.GLEx;
import loon.utils.MathUtils;

/**
 * 以指定坐标为中心点,出现像素风斩击效果
 * 
 * <pre>
 * // 构建一个斩击效果,中心点200,200,宽2,长25
 * add(new PixelChopEffect(LColor.red, 200, 200, 2, 25));
 * </pre>
 */
public class PixelChopEffect extends PixelBaseEffect {

	public static enum ChopDirection {
		/**
		 * West North To East South
		 */
		WNTES,
		/**
		 * North East to South West
		 */
		NETSW,
		/**
		 * Top to Bottom
		 */
		TTB,
		/**
		 * Left to Right
		 */
		LTR,
		/**
		 * WNTES and NETSW combined to Cross
		 */
		WNCROSS,
		/**
		 * TTB and LTR combined to Cross
		 */
		LTCROSS,
		/**
		 * ALL combined to Cross
		 */
		ALLCROSS, CIRCLE, CIRCLE_DASH, STAR, MULTI
	}

	private final LColor _baseChopColor = new LColor();

	private ChopDirection _direction;

	private float _viewX, _viewY;

	private float _width;

	private float _progress;

	private int _mode;

	private boolean _afterEffect;

	public static PixelBaseEffect chop(ChopDirection dir, LColor color, int size, ISprite spr) {
		return chop(dir, color, size, spr, false);
	}

	public static PixelBaseEffect chop(ChopDirection dir, LColor color, int size, ISprite spr, boolean rand) {
		return chop(dir, color, size, spr, rand, 0f, 0f);
	}

	public static PixelBaseEffect chop(ChopDirection dir, LColor color, int size, ISprite spr, boolean rand,
			float offsetX, float offsetY) {
		if (spr == null) {
			throw new LSysException("The chop target does not exist !");
		}
		final int frame = MathUtils.ifloor(MathUtils.max(spr.getWidth(), spr.getHeight()) / 3f);
		final float centerX = spr.getX() + spr.getWidth() / 2f + offsetX;
		final float centerY = spr.getY() + spr.getHeight() / 2f + offsetY;
		if (rand) {
			return createRandom(color, centerX, centerY, size, frame);
		} else {
			return create(dir, color, centerX, centerY, size, frame);
		}
	}

	public static PixelBaseEffect chopRandom(LColor color, int size, ISprite spr) {
		return chop(null, color, size, spr, true, 0f, 0f);
	}

	public static PixelBaseEffect chopRandom(LColor color, int size, ISprite spr, float offsetX, float offsetY) {
		return chop(null, color, size, spr, true, offsetX, offsetY);
	}

	public static PixelBaseEffect create(ChopDirection dir, LColor color, float x, float y, int width, int frameLimit) {
		return get(dir, color, x, y, width, frameLimit).setAutoRemoved(true);
	}

	public static PixelBaseEffect createRandom(LColor color, float x, float y, int width, int frameLimit) {
		return getRandom(color, x, y, width, frameLimit).setAutoRemoved(true);
	}

	public static PixelChopEffect get(ChopDirection dir, LColor color, float x, float y, int width, int frameLimit) {
		return new PixelChopEffect(dir, color, 0, x, y, width, frameLimit);
	}

	public static PixelChopEffect getRandom(LColor color, float x, float y, int width, int frameLimit) {
		final int rand = MathUtils.random(10);
		ChopDirection[] directions = ChopDirection.values();
		if (rand >= directions.length) {
			return new PixelChopEffect(ChopDirection.WNTES, color, x, y, width, frameLimit);
		}
		return new PixelChopEffect(directions[rand], color, x, y, width, frameLimit);
	}

	public PixelChopEffect(LColor color, float x, float y) {
		this(ChopDirection.WNTES, color, 0, x, y, 2);
	}

	public PixelChopEffect(LColor color, float x, float y, int frameLimit) {
		this(ChopDirection.WNTES, color, 0, x, y, 2, 25);
	}

	public PixelChopEffect(LColor color, int mode, float x, float y) {
		this(ChopDirection.WNTES, color, mode, x, y, 2);
	}

	public PixelChopEffect(LColor color, int mode, float x, float y, int frameLimit) {
		this(ChopDirection.WNTES, color, mode, x, y, 2, 25);
	}

	public PixelChopEffect(ChopDirection dir, LColor color, int mode, float x, float y) {
		this(dir, color, mode, x, y, 2);
	}

	public PixelChopEffect(ChopDirection dir, LColor color, float x, float y, int frameLimit) {
		this(dir, color, 0, x, y, 2, 25);
	}

	public PixelChopEffect(ChopDirection dir, LColor color, int mode, float x, float y, int frameLimit) {
		this(dir, color, mode, x, y, 2, frameLimit);
	}

	public PixelChopEffect(LColor color, int x, int y, int width, int frameLimit) {
		this(ChopDirection.WNTES, color, 0, x, y, width, frameLimit);
	}

	public PixelChopEffect(ChopDirection dir, LColor color, float x, float y, float width, int frameLimit) {
		this(dir, color, 0, x, y, width, frameLimit);
	}

	public PixelChopEffect(LColor color, float x, float y, float width, int frameLimit) {
		this(ChopDirection.WNTES, color, 0, x, y, width, frameLimit);
	}

	public PixelChopEffect(LColor color, int mode, float x, float y, float width, int frameLimit) {
		this(ChopDirection.WNTES, color, mode, x, y, width, frameLimit);
	}

	public PixelChopEffect(ChopDirection dir, LColor color, int mode, float x, float y, float width, int frameLimit) {
		super(color, x, y, 0, 0);
		this._direction = dir;
		this._mode = mode;
		this._width = width;
		this._viewX = x;
		this._viewY = y;
		this._limit = frameLimit;
		this._afterEffect = width > 1 && frameLimit > LSystem.LAYER_TILE_SIZE;
		setDelay(0);
		setEffectDelay(0);
	}

	private void paintChop(GLEx g, ChopDirection dir, float tx, float ty) {

		final float x = _viewX - tx;
		final float y = _viewY - ty;

		final int currentFrame = MathUtils.min(super._frame, _limit);
		final int size = MathUtils.iceil(currentFrame * _width);

		switch (dir) {
		case LTR:
		case TTB:
		case NETSW:
		case WNTES:
			paintLineChop(g, dir, x, y, currentFrame);
			break;
		case CIRCLE:
			g.drawOval(x - size, y - size, size * 2f, size * 2f);
			break;
		case CIRCLE_DASH:
			g.drawDashCircle(x - size, y - size, size * 2, _width);
			break;
		case STAR:
			final float starSize = size * 0.8f;
			for (int i = 0; i < 8; i++) {
				float angle = MathUtils.PI * i / 4f;
				float sx = x + (MathUtils.cos(angle) * starSize);
				float sy = y + (MathUtils.sin(angle) * starSize);
				g.drawLine(x, y, sx, sy, _width);
			}
			break;
		case MULTI:
			final float lineSpacing = size * 0.25f;
			for (int i = -2; i <= 2; i++) {
				g.drawLine(x - size, y + i * lineSpacing, x + size, y + i * lineSpacing, _width);
			}
			break;
		default:
			paintLineChop(g, dir, x, y, currentFrame);
			break;
		}
	}

	private void paintLineChop(GLEx g, ChopDirection dir, float x, float y, int currentFrame) {
		float x1 = 0f, y1 = 0f, x2 = 0f, y2 = 0f;
		float offset = currentFrame / 3f;

		switch (dir) {
		case LTR:
			if (_mode == 0) {
				x1 = x - currentFrame - offset;
				y1 = y;
				x2 = x + currentFrame + offset;
				y2 = y;
			} else if (_mode == 1) {
				x1 = x - currentFrame;
				y1 = y;
				x2 = x;
				y2 = y;
			} else {
				x1 = x - _limit;
				y1 = y;
				x2 = x + _limit;
				y2 = y;
			}
			break;
		case TTB:
			if (_mode == 0) {
				x1 = x;
				y1 = y - currentFrame - offset;
				x2 = x;
				y2 = y + currentFrame + offset;
			} else if (_mode == 1) {
				x1 = x;
				y1 = y - currentFrame;
				x2 = x;
				y2 = y;
			} else {
				x1 = x;
				y1 = y - _limit;
				x2 = x;
				y2 = y + _limit;
			}
			break;
		case NETSW:
			if (_mode == 0) {
				x1 = x - currentFrame;
				y1 = y + currentFrame;
				x2 = x + currentFrame;
				y2 = y - currentFrame;
			} else if (_mode == 1) {
				x1 = x;
				y1 = y;
				x2 = x + currentFrame;
				y2 = y - currentFrame;
			} else {
				x1 = x - _limit;
				y1 = y + _limit;
				x2 = x + _limit;
				y2 = y - _limit;
			}
			break;
		case WNTES:
			if (_mode == 0) {
				x1 = x - currentFrame;
				y1 = y - currentFrame;
				x2 = x + currentFrame;
				y2 = y + currentFrame;
			} else if (_mode == 1) {
				x1 = x;
				y1 = y;
				x2 = x + currentFrame;
				y2 = y + currentFrame;
			} else {
				x1 = x - _limit;
				y1 = y - _limit;
				x2 = x + _limit;
				y2 = y + _limit;
			}
			break;
		default:
			break;
		}
		g.drawLine(x1, y1, x2, y2, _width);
	}

	@Override
	public void draw(GLEx g, float tx, float ty) {
		if (super._completed) {
			return;
		}

		final int oldColor = g.color();
		_progress = super._frame / (float) _limit;
		float alpha = MathUtils.max(1f - (_progress * 0.8f), 0f);

		LColor renderColor;
		if (_afterEffect) {
			renderColor = _baseChopColor.interpolate(_baseColor, _progress);
			renderColor.a = alpha;
			g.setColor(renderColor);
		} else {
			renderColor = _baseColor;
			renderColor.a = alpha;
			g.setColor(renderColor);
		}

		switch (_direction) {
		case LTCROSS:
			paintChop(g, ChopDirection.LTR, tx, ty);
			paintChop(g, ChopDirection.TTB, tx, ty);
			break;
		case WNCROSS:
			paintChop(g, ChopDirection.WNTES, tx, ty);
			paintChop(g, ChopDirection.NETSW, tx, ty);
			break;
		case ALLCROSS:
			paintChop(g, ChopDirection.LTR, tx, ty);
			paintChop(g, ChopDirection.TTB, tx, ty);
			paintChop(g, ChopDirection.WNTES, tx, ty);
			paintChop(g, ChopDirection.NETSW, tx, ty);
			break;
		default:
			paintChop(g, _direction, tx, ty);
			break;
		}

		if (_afterEffect && alpha > 0.15f) {
			LColor shadowColor = renderColor.mul(0.45f);
			g.setColor(shadowColor);
			paintChop(g, _direction, tx + 1.5f, ty + 1.5f);
		}

		g.setColor(oldColor);

		if (super._frame >= _limit) {
			super._completed = true;
		}
	}

	public float getProgress() {
		return _progress;
	}

	public boolean isAfterEffect() {
		return _afterEffect;
	}

	public PixelChopEffect setAfterEffect(boolean afterimage) {
		_afterEffect = afterimage;
		return this;
	}

	public PixelChopEffect setBaseChopColor(LColor c) {
		if (c != null) {
			_baseChopColor.setColor(c);
		}
		return this;
	}

	public LColor getBaseChopColor() {
		return _baseChopColor;
	}

	public int getMode() {
		return _mode;
	}

	/**
	 * line display mode
	 * 
	 * 0:both ends 1:move line 2:only show line
	 * 
	 * @param mode
	 * 
	 */
	public PixelChopEffect setMode(int mode) {
		this._mode = mode;
		return this;
	}

}
