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
package loon.action.map.battle;

import loon.LSystem;
import loon.action.map.battle.BattleType.MoveState;
import loon.action.sprite.Animation;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.ISOUtils;
import loon.utils.ISOUtils.IsoConfig;
import loon.utils.ISOUtils.IsoResult;
import loon.utils.MathUtils;

/**
 * 战斗瓦片动画纹理显示类
 */
public class BattleTile {

	public interface EffectService {
		void applyEffect(BattleTile tile, BattleTileType newType, float duration);
	}

	public interface SkillService {
		void checkAndSet(BattleTile tile, BattleMapObject unit);

		boolean trigger(BattleTile tile, BattleMapObject target);
	}

	// 网格坐标
	public int gridX, gridY;

	// 瓦片尺寸
	public int cellWidth, cellHeight;

	private final Vector2f tempResult = new Vector2f();
	private final IsoResult tempisoResult = new IsoResult();

	// 地形特效
	private BattleTerrainEffect terrainEffect;
	private BattleTileType tiletype;
	private boolean hasUnit;
	private BattleTileType originalType;
	private float specialEffectDuration;
	private BattleMapObject unit;
	private boolean hasSkill;
	private BattleMapObject skillUnit;
	private float skillDuration;

	// 特效接口
	private EffectService effectService;
	private SkillService skillService;

	// 动画对象：背景、地表、特效
	protected Animation bgAnim, groundAnim, effectAnim;

	// 背景动画偏移
	protected float bgOffsetX, bgOffsetY;
	// 地表动画偏移
	protected float groundOffsetX, groundOffsetY;
	// 特效动画偏移
	protected float effectOffsetX, effectOffsetY;
	// 整体缩放
	protected float scale = 1.0f;
	// 背景独立缩放
	protected float bgScale = 1.0f;
	// 地表独立缩放
	protected float groundScale = 1.0f;
	// 特效独立缩放
	protected float effectScale = 1.0f;
	// 旋转角度
	protected float rotation = 0f;
	// 全局动画速度
	protected float animSpeed = 1.0f;
	// 整体染色
	protected LColor finalColor = new LColor();
	// 高亮颜色
	protected LColor highlightColor = new LColor(1, 1, 0, 0.6f);
	// 渲染层级
	protected int renderLayer = 0;
	// X轴翻转
	protected boolean flipX = false;
	// Y轴翻转
	protected boolean flipY = false;
	// 破坏动画计时器
	protected float destroyEffectTime = 0f;
	// 闪烁标记
	protected boolean isBlinking = false;
	// 闪烁计时器
	protected float blinkTimer = 0f;

	protected boolean isHighlighted = false;
	protected boolean isVisible = true;
	protected float pathCost = 1.0f;
	protected float brightness = 1.0f;
	protected boolean passable = true;
	protected boolean isInteractable = false;
	protected boolean isDestroyed = false;
	protected int durability = 100;
	protected final IsoConfig isoConfig;

	public BattleTile(int x, int y, int w, int h, IsoConfig config) {
		this(x, y, w, h, config, null, null);
	}

	public BattleTile(int x, int y, int w, int h, IsoConfig config, EffectService effectService,
			SkillService skillService) {
		this(x, y, w, h, config, BattleTileType.PLAIN, BattleTileType.PLAIN, effectService, skillService, 1f, 1f, 100);
	}

	public BattleTile(int x, int y, int w, int h, IsoConfig config, BattleTileType t, EffectService effectService,
			SkillService skillService) {
		this(x, y, w, h, config, t, t, effectService, skillService, 1f, 1f, 100);
	}

	public BattleTile(int x, int y, int w, int h, IsoConfig config, BattleTileType t, BattleTileType o,
			EffectService effectService, SkillService skillService, float pathCost, float brightness, int durability) {
		this.gridX = x;
		this.gridY = y;
		this.cellWidth = w + (t != null ? t.widthOffset : 0);
		this.cellHeight = h + (t != null ? t.heightOffset : 0);
		this.isoConfig = config == null ? IsoConfig.defaultConfig() : config;
		this.tiletype = t;
		this.originalType = o;
		this.hasUnit = false;
		this.hasSkill = false;
		this.skillDuration = 0;
		this.effectService = effectService;
		this.skillService = skillService;
		this.pathCost = pathCost;
		this.brightness = brightness;
		this.isInteractable = false;
		this.isDestroyed = false;
		this.passable = true;
		this.durability = durability;

		if (pathCost <= 0) {
			this.pathCost = calculatePathCost();
		}
	}

	public BattleTile cpy() {
		BattleTile copy = new BattleTile(gridX, gridY, cellWidth, cellHeight, isoConfig);
		copy.tiletype = this.tiletype;
		copy.originalType = this.originalType;
		copy.hasUnit = this.hasUnit;
		copy.specialEffectDuration = this.specialEffectDuration;
		copy.hasSkill = this.hasSkill;
		copy.skillDuration = this.skillDuration;
		copy.unit = this.unit;
		copy.skillUnit = this.skillUnit;
		copy.effectService = this.effectService;
		copy.skillService = this.skillService;
		copy.pathCost = this.pathCost;
		copy.brightness = this.brightness;
		copy.isInteractable = this.isInteractable;
		copy.isDestroyed = this.isDestroyed;
		copy.durability = this.durability;
		copy.passable = this.passable;
		copy.terrainEffect = this.terrainEffect;
		copy.bgAnim = this.bgAnim != null ? this.bgAnim.cpy() : null;
		copy.groundAnim = this.groundAnim != null ? this.groundAnim.cpy() : null;
		copy.effectAnim = this.effectAnim != null ? this.effectAnim.cpy() : null;
		copy.bgOffsetX = this.bgOffsetX;
		copy.bgOffsetY = this.bgOffsetY;
		copy.groundOffsetX = this.groundOffsetX;
		copy.groundOffsetY = this.groundOffsetY;
		copy.effectOffsetX = this.effectOffsetX;
		copy.effectOffsetY = this.effectOffsetY;
		copy.scale = this.scale;
		copy.bgScale = this.bgScale;
		copy.groundScale = this.groundScale;
		copy.effectScale = this.effectScale;
		copy.rotation = this.rotation;
		copy.animSpeed = this.animSpeed;
		copy.finalColor = this.finalColor.cpy();
		copy.highlightColor = this.highlightColor.cpy();
		copy.renderLayer = this.renderLayer;
		copy.flipX = this.flipX;
		copy.flipY = this.flipY;
		copy.isBlinking = this.isBlinking;
		return copy;
	}

	public void setPassable(boolean p) {
		passable = p;
	}

	private float calculatePathCost() {
		float baseCost = 1f / tiletype.moveSpeedMultiplier;
		baseCost *= (1.0f + (cellHeight * 0.2f));
		if (terrainEffect != null) {
			if (terrainEffect == BattleTerrainEffect.SLOW) {
				baseCost *= 1.5f;
			} else if (terrainEffect == BattleTerrainEffect.POISON) {
				baseCost *= 2.0f;
			}
		}
		if (tiletype != null) {
			if (tiletype.defaultMoveState == MoveState.DIFFICULT) {
				baseCost *= 2.0f;
			} else if (tiletype.defaultMoveState == MoveState.CLIMB) {
				baseCost *= 1.5f;
			}
		}
		if (isDestroyed) {
			baseCost *= 1.8f;
		}
		return baseCost;
	}

	public Vector2f getScreenPosition(Vector2f result, IsoResult iso) {
		return ISOUtils.isoTransform(gridX, gridY, cellWidth, cellHeight, isoConfig, result, iso).screenPos;
	}

	public void activateSpecialEffect(BattleTileType newType, float duration) {
		if (newType == null) {
			return;
		}
		this.originalType = this.tiletype;
		this.tiletype = newType;
		this.specialEffectDuration = MathUtils.max(duration, 0f);
		if (effectService != null) {
			effectService.applyEffect(this, newType, duration);
		}
	}

	public void updateBrightness() {
		ISOUtils.IsoResult result = ISOUtils.isoTransform(gridX, gridY, cellWidth, cellHeight, true, isoConfig,
				tempResult, tempisoResult);
		this.brightness = result.brightness;
	}

	public boolean isClicked(int screenX, int screenY) {
		return ISOUtils.isTileClicked(gridX, gridY, cellWidth, cellHeight, screenX, screenY, isoConfig, tempResult);
	}

	public void adaptToTileSize(int width, int height) {
		this.cellWidth = width;
		this.cellHeight = height;
		pathCost = calculatePathCost();
	}

	public void update(float deltaTime) {
		float actualDelta = deltaTime * animSpeed;
		if (specialEffectDuration > 0) {
			specialEffectDuration = MathUtils.max(specialEffectDuration - deltaTime, 0f);
			if (specialEffectDuration <= 0) {
				this.tiletype = originalType;
			}
		}
		if (hasSkill && skillDuration > 0) {
			skillDuration = MathUtils.max(skillDuration - deltaTime, 0f);
			if (skillDuration <= 0) {
				hasSkill = false;
				skillUnit = null;
			}
		}

		// 破坏效果计时器
		if (isDestroyed && destroyEffectTime < 1f) {
			destroyEffectTime += deltaTime;
		}

		// 闪烁效果更新
		if (isBlinking) {
			blinkTimer += deltaTime * 8f;
		}

		// 动画更新
		if (bgAnim != null) {
			bgAnim.update(actualDelta);
		}
		if (groundAnim != null) {
			groundAnim.update(actualDelta);
		}
		if (effectAnim != null) {
			effectAnim.update(actualDelta);
		}
	}

	public void paint(GLEx g, float drawX, float drawY, LColor color) {
		paint(g, drawX, drawY, cellWidth, cellHeight, color);
	}

	public void paint(GLEx g, float drawX, float drawY, float tileWidth, float tileHeight, LColor color) {
		if (!isVisible) {
			return;
		}
		if (!LColor.white.equals(color)) {
			finalColor.setColor(color);
		}
		if (brightness != 1f) {
			finalColor.setColor(brightness, brightness, brightness, 1f);
		}
		if (isHighlighted) {
			finalColor.setColor(LColor.lerp(finalColor, highlightColor, 0.5f));
		}
		if (isBlinking) {
			finalColor.a *= MathUtils.abs(MathUtils.sin(blinkTimer));
		}
		float finalScale = this.scale;
		float renderW = tileWidth * finalScale;
		float renderH = tileHeight * finalScale;
		float renderX = drawX - (renderW - tileWidth) / 2;
		float renderY = drawY - (renderH - tileHeight) / 2;

		// 背景层
		if (bgAnim != null) {
			float x = renderX + bgOffsetX;
			float y = renderY + bgOffsetY;
			float s = bgScale;
			g.draw(bgAnim.getSpriteImage(), x, y, tileWidth, tileHeight, finalColor, rotation, s, s, flipX, flipY);
		}

		// 地表层
		if (groundAnim != null) {
			float x = renderX + groundOffsetX;
			float y = renderY + groundOffsetY;
			float s = groundScale;
			g.draw(groundAnim.getSpriteImage(), x, y, tileWidth, tileHeight, finalColor, rotation, s, s, flipX, flipY);
		}

		// 特效层
		if (effectAnim != null) {
			float x = renderX + effectOffsetX;
			float y = renderY + effectOffsetY;
			float s = effectScale;
			g.draw(effectAnim.getSpriteImage(), x, y, tileWidth, tileHeight, finalColor, rotation, s, s, flipX, flipY);
		}
	}

	public void setUnit(BattleMapObject unit) {
		this.unit = unit;
		this.hasUnit = (unit != null);
		if (skillService != null) {
			skillService.checkAndSet(this, unit);
		}
	}

	public boolean trigger(BattleMapObject target) {
		return skillService != null && skillService.trigger(this, target);
	}

	public void setAllAnimOffset(float x, float y) {
		setBgOffset(x, y);
		setGroundOffset(x, y);
		setEffectOffset(x, y);
	}

	public void setBlinking(boolean blinking) {
		this.isBlinking = blinking;
		this.blinkTimer = 0f;
	}

	public void resetVisualEffects() {
		setAllAnimOffset(0, 0);
		this.scale = 1f;
		this.bgScale = this.groundScale = this.effectScale = 1f;
		this.rotation = 0f;
		this.animSpeed = 1f;
		this.finalColor.setColor(1, 1, 1, 1);
		this.flipX = this.flipY = false;
		this.isBlinking = false;
		this.isHighlighted = false;
	}

	public IsoConfig getIsoCofing() {
		return isoConfig;
	}

	public boolean isPassable() {
		return (tiletype == null || tiletype.isPassable()) && passable && !isDestroyed;
	}

	public void setBgOffset(float x, float y) {
		this.bgOffsetX = x;
		this.bgOffsetY = y;
	}

	public void setGroundOffset(float x, float y) {
		this.groundOffsetX = x;
		this.groundOffsetY = y;
	}

	public void setEffectOffset(float x, float y) {
		this.effectOffsetX = x;
		this.effectOffsetY = y;
	}

	public float getBgOffsetX() {
		return bgOffsetX;
	}

	public float getBgOffsetY() {
		return bgOffsetY;
	}

	public float getGroundOffsetX() {
		return groundOffsetX;
	}

	public float getGroundOffsetY() {
		return groundOffsetY;
	}

	public float getEffectOffsetX() {
		return effectOffsetX;
	}

	public float getEffectOffsetY() {
		return effectOffsetY;
	}

	public void setScale(float scale) {
		this.scale = MathUtils.max(scale, 0.1f);
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public void setAnimSpeed(float speed) {
		this.animSpeed = MathUtils.max(speed, 0.1f);
	}

	public void setTintColor(LColor color) {
		this.finalColor.setColor(color);
	}

	public void setHighlightColor(LColor color) {
		this.highlightColor = new LColor(color);
	}

	public void setFlip(boolean flipX, boolean flipY) {
		this.flipX = flipX;
		this.flipY = flipY;
	}

	public int getX() {
		return gridX;
	}

	public int getY() {
		return gridY;
	}

	public BattleTileType getTileType() {
		return tiletype;
	}

	public void setTileType(BattleTileType t) {
		this.tiletype = t;
		this.pathCost = calculatePathCost();
	}

	public boolean hasUnit() {
		return hasUnit;
	}

	public BattleMapObject getUnit() {
		return unit;
	}

	public boolean hasSkill() {
		return hasSkill;
	}

	public BattleMapObject getSkillUnit() {
		return skillUnit;
	}

	public float getSkillDuration() {
		return skillDuration;
	}

	public void setSkillUnit(BattleMapObject skillUnit) {
		this.skillUnit = skillUnit;
	}

	public int getGridX() {
		return gridX;
	}

	public void setGridX(int gridX) {
		this.gridX = gridX;
	}

	public int getGridY() {
		return gridY;
	}

	public void setGridY(int gridY) {
		this.gridY = gridY;
	}

	public int getCellWidth() {
		return cellWidth;
	}

	public void setCellWidth(int cellWidth) {
		this.cellWidth = cellWidth;
	}

	public int getCellHeight() {
		return cellHeight;
	}

	public void setCellHeight(int cellHeight) {
		this.cellHeight = cellHeight;
	}

	public boolean isHasUnit() {
		return hasUnit;
	}

	public void setHasUnit(boolean hasUnit) {
		this.hasUnit = hasUnit;
	}

	public BattleTileType getOriginalType() {
		return originalType;
	}

	public void setOriginalType(BattleTileType originalType) {
		this.originalType = originalType;
	}

	public float getSpecialEffectDuration() {
		return specialEffectDuration;
	}

	public void setSpecialEffectDuration(float specialEffectDuration) {
		this.specialEffectDuration = MathUtils.max(specialEffectDuration, 0f);
	}

	public boolean isHasSkill() {
		return hasSkill;
	}

	public void setHasSkill(boolean hasSkill) {
		this.hasSkill = hasSkill;
	}

	public EffectService getEffectService() {
		return effectService;
	}

	public void setEffectService(EffectService effectService) {
		this.effectService = effectService;
	}

	public SkillService getSkillService() {
		return skillService;
	}

	public void setSkillService(SkillService skillService) {
		this.skillService = skillService;
	}

	public Animation getBgAnim() {
		return bgAnim;
	}

	public void setBgAnim(Animation bgAnim) {
		this.bgAnim = bgAnim;
	}

	public Animation getGroundAnim() {
		return groundAnim;
	}

	public void setGroundAnim(Animation groundAnim) {
		this.groundAnim = groundAnim;
	}

	public Animation getEffectAnim() {
		return effectAnim;
	}

	public void setEffectAnim(Animation effectAnim) {
		this.effectAnim = effectAnim;
	}

	public boolean isHighlighted() {
		return isHighlighted;
	}

	public void setHighlighted(boolean isHighlighted) {
		this.isHighlighted = isHighlighted;
	}

	public boolean isVisible() {
		return isVisible;
	}

	public void setVisible(boolean isVisible) {
		this.isVisible = isVisible;
	}

	public float getPathCost() {
		return pathCost;
	}

	public void setPathCost(float pathCost) {
		this.pathCost = MathUtils.max(pathCost, 0.1f);
	}

	public float getBrightness() {
		return brightness;
	}

	public void setBrightness(float brightness) {
		this.brightness = brightness;
	}

	public MoveState getMoveState() {
		return tiletype != null ? tiletype.getDefaultMoveState() : MoveState.NORMAL;
	}

	public boolean isInteractable() {
		return isInteractable;
	}

	public void setInteractable(boolean isInteractable) {
		this.isInteractable = isInteractable;
	}

	public boolean isDestroyed() {
		return isDestroyed;
	}

	public void setDestroyed(boolean isDestroyed) {
		this.isDestroyed = isDestroyed;
		this.pathCost = calculatePathCost();
		if (isDestroyed) {
			this.destroyEffectTime = 0f;
		}
	}

	public int getDurability() {
		return durability;
	}

	public void setDurability(int durability) {
		this.durability = MathUtils.max(durability, 0);
		if (this.durability <= 0) {
			setDestroyed(true);
		}
	}

	public void setSkillDuration(float skillDuration) {
		this.skillDuration = MathUtils.max(skillDuration, 0f);
	}

	public BattleTerrainEffect getTerrainEffect() {
		return terrainEffect;
	}

	public void setTerrainEffect(BattleTerrainEffect terrainEffect) {
		this.terrainEffect = terrainEffect;
		this.pathCost = calculatePathCost();
	}

	public float getScale() {
		return scale;
	}

	public float getRotation() {
		return rotation;
	}

	public float getAnimSpeed() {
		return animSpeed;
	}

	public LColor getTintColor() {
		return finalColor;
	}

	public LColor getHighlightColor() {
		return highlightColor;
	}

	public int getRenderLayer() {
		return renderLayer;
	}

	public void setRenderLayer(int layer) {
		this.renderLayer = layer;
	}

	public boolean isFlipX() {
		return flipX;
	}

	public boolean isFlipY() {
		return flipY;
	}

	public boolean isBlinking() {
		return isBlinking;
	}

	public float getDestroyEffectTime() {
		return destroyEffectTime;
	}

	public void setDestroyEffectTime(float destroyEffectTime) {
		this.destroyEffectTime = destroyEffectTime;
	}

	@Override
	public String toString() {
		return tiletype == null ? LSystem.UNKNOWN : tiletype.toString();
	}
}
