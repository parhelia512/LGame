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

import loon.LRelease;
import loon.LSystem;
import loon.LTexture;
import loon.action.map.battle.BattleType.RangeType;
import loon.action.map.battle.BattleType.SkillType;
import loon.action.map.battle.BattleType.UnitType;
import loon.action.map.battle.BattleType.WeatherType;
import loon.action.sprite.Animation;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.SortedList;
import loon.utils.TArray;
import loon.utils.TimeUtils;

public class BattleSkill implements LRelease {

	public static enum BattleType {
		MELEE, RANGE, BUFF, DEBUFF, HEAL
	}

	private static final float MAX_SHAKE_INTENSITY = 12f;

	private final LColor effColor = new LColor();

	private float lightOverlayAlpha = 0.4f;

	private float effectAlpha = 0.8f;

	private float rangeHighlightAlpha = 0.5f;

	private BattleMap battleMap;

	// 唯一标识
	protected String id;
	// 名称
	protected String name;
	// 描述
	protected String description;
	// 类型
	protected SkillType skilltype;
	// 限定兵种
	protected UnitType[] limitUnits;
	// 限定地形
	protected BattleTileType[] limitTiles;
	// 限定天气
	protected WeatherType[] limitWeathers;
	// 战技类型
	protected BattleType battleType;
	// 最低使用等级
	protected int minLevel = 1;
	// 是否需要城池
	protected boolean needCity = false;
	// 优先级(1-5)
	protected int priority = 3;
	// 基础成功率
	protected float baseSuccessRate = 0.7f;
	// 气力消耗
	protected int moraleCost = 20;
	// 冷却时间(秒/回合)
	protected float cooldown = 5;
	// 上次使用时间
	protected float lastUseTime = 0;
	// 魔力消耗
	protected int mpCost = 0;
	// 行动点消耗
	protected int actionPointCost = 2;
	// 攻击范围
	protected RangeType rangeType = RangeType.SINGLE;
	// 攻击距离
	protected int rangeDistance = 1;
	// 范围半径
	protected int rangeRadius = 1;
	// 伤害值
	protected int damage = 1;

	protected int width, height;

	protected float hitRate;

	protected float critRate;

	protected float cooldownDuration;

	protected float rotation = 0f;

	protected Animation attackEffectAnim;

	protected Animation skillEffectAnim;

	protected LTexture defaultEffectTexture;

	protected float shakeIntensity, shakeDuration;

	protected boolean isCastTriggered;

	public BattleSkill(String id, String name, String description, BattleType type, int damage, int mpCost,
			float hitRate, float critRate, UnitType[] limitUnits, BattleTileType[] limitTiles,
			WeatherType[] limitWeathers, int minLevel, boolean needCity, int priority, float baseSuccessRate,
			int moraleCost, int actionPointCost, float cooldownDuration, RangeType rangeType, int rangeDistance,
			int rangeRadius) {
		// 基础
		this.id = id == null || id.isBlank() ? "skill_default" : id.trim();
		this.name = name == null || name.isBlank() ? LSystem.UNKNOWN : name.trim();
		this.description = description == null ? LSystem.UNKNOWN : description;
		this.battleType = type == null ? BattleType.MELEE : type;

		// 基础数值
		this.damage = MathUtils.max(0, MathUtils.min(damage, 99999));
		this.mpCost = MathUtils.max(0, MathUtils.min(mpCost, 10000));
		this.hitRate = MathUtils.clamp(hitRate, 0f, 1f);
		this.critRate = MathUtils.clamp(critRate, 0f, 1f);

		// 战斗限制
		this.limitUnits = limitUnits == null ? new UnitType[0] : limitUnits;
		this.limitTiles = limitTiles == null ? new BattleTileType[0] : limitTiles;
		this.limitWeathers = limitWeathers == null ? new WeatherType[0] : limitWeathers;
		this.minLevel = MathUtils.max(1, minLevel);
		this.needCity = needCity;
		this.priority = MathUtils.clamp(priority, 1, 5);
		this.baseSuccessRate = MathUtils.clamp(baseSuccessRate, 0f, 1f);
		this.moraleCost = MathUtils.max(0, moraleCost);
		this.actionPointCost = MathUtils.max(0, actionPointCost);

		// 范围
		this.rangeType = rangeType == null ? RangeType.SINGLE : rangeType;
		this.rangeDistance = MathUtils.max(0, rangeDistance);
		this.rangeRadius = MathUtils.max(0, rangeRadius);

		// 冷却
		this.cooldownDuration = MathUtils.max(0.1f, cooldownDuration);
		this.cooldown = 0;
		this.lastUseTime = 0;

	}

	public SortedList<Vector2f> getSkillRange(BattleMapObject caster, BattleTile[][] map, int mapWidth, int mapHeight,
			RangeType rangeType, int rangeRadius, int rangeDistance) {
		return getSkillRange(caster, map, mapWidth, mapHeight, rangeType, rangeRadius, rangeDistance, true, null);
	}

	public SortedList<Vector2f> getSkillRange(BattleMapObject caster, BattleTile[][] map, int mapWidth, int mapHeight,
			RangeType rangeType, int rangeRadius, int rangeDistance, boolean allDir, TArray<Vector2f> pathList) {
		SortedList<Vector2f> range = new SortedList<Vector2f>();
		final int[][] dirs4 = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		final int[][] dirs8 = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
		final int[][] dirs = allDir ? dirs8 : dirs4;
		switch (rangeType) {
		case SINGLE:
		case SELF:
			range.add(new Vector2f(caster.gridX, caster.gridY));
			break;
		case ADJACENT:
			for (int[] d : dirs) {
				int nx = caster.gridX + d[0], ny = caster.gridY + d[1];
				if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
					range.add(new Vector2f(nx, ny));
				}
			}
			break;
		case CROSS:
			for (int i = 1; i <= rangeDistance; i++) {
				for (int[] d : dirs) {
					int nx = caster.gridX + d[0] * i;
					int ny = caster.gridY + d[1] * i;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
						range.add(new Vector2f(nx, ny));
					}
				}
			}
			break;
		case DIAGONAL:
			for (int i = 1; i <= rangeDistance; i++) {
				int[][] diagDirs = { { i, i }, { i, -i }, { -i, i }, { -i, -i } };
				for (int[] d : diagDirs) {
					int nx = caster.gridX + d[0], ny = caster.gridY + d[1];
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
						range.add(new Vector2f(nx, ny));
					}
				}
			}
			break;
		case CIRCLE:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++) {
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					if (dx * dx + dy * dy <= rangeDistance * rangeDistance) {
						int nx = caster.gridX + dx, ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
							range.add(new Vector2f(nx, ny));
						}
					}
				}
			}
			break;
		case AOE:
			for (int x = 0; x < mapWidth; x++) {
				for (int y = 0; y < mapHeight; y++) {
					range.add(new Vector2f(x, y));
				}
			}
			break;
		case SQUARE:
		case AREA:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++) {
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					int nx = caster.gridX + dx, ny = caster.gridY + dy;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
						range.add(new Vector2f(nx, ny));
					}
				}
			}
			break;

		case LINE:
			for (int i = 1; i <= rangeDistance; i++) {
				int nx = caster.gridX + i, ny = caster.gridY;
				if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
					range.add(new Vector2f(nx, ny));
				}
			}
			break;
		case LINE_AOE:
			for (int i = 1; i <= rangeDistance; i++) {
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					int nx = caster.gridX + i, ny = caster.gridY + dy;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
						range.add(new Vector2f(nx, ny));
					}
				}
			}
			break;
		case GLOBAL:
			for (int x = 0; x < mapWidth; x++) {
				for (int y = 0; y < mapHeight; y++) {
					range.add(new Vector2f(x, y));
				}
			}
			break;
		case ROW:
			for (int x = 0; x < mapWidth; x++) {
				range.add(new Vector2f(x, caster.gridY));
			}
			break;

		case COLUMN:
			for (int y = 0; y < mapHeight; y++) {
				range.add(new Vector2f(caster.gridX, y));
			}
			break;
		case RING:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++) {
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					int dist2 = dx * dx + dy * dy;
					if (dist2 <= rangeRadius * rangeRadius && dist2 >= (rangeRadius - 1) * (rangeRadius - 1)) {
						int nx = caster.gridX + dx, ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
							range.add(new Vector2f(nx, ny));
						}
					}
				}
			}
			break;
		case SECTOR:
			for (int dx = 0; dx <= rangeDistance; dx++) {
				for (int dy = -dx; dy <= dx; dy++) {
					int nx = caster.gridX + dx, ny = caster.gridY + dy;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
						range.add(new Vector2f(nx, ny));
					}
				}
			}
			break;
		case DIAMOND:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++) {
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					if (MathUtils.abs(dx) + MathUtils.abs(dy) <= rangeRadius) {
						int nx = caster.gridX + dx, ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
							range.add(new Vector2f(nx, ny));
						}
					}
				}
			}
			break;
		case PLUS:
			for (int i = -rangeRadius; i <= rangeRadius; i++) {
				int nx1 = caster.gridX + i, ny1 = caster.gridY;
				int nx2 = caster.gridX, ny2 = caster.gridY + i;
				if (nx1 >= 0 && nx1 < mapWidth && ny1 >= 0 && ny1 < mapHeight) {
					range.add(new Vector2f(nx1, ny1));
				}
				if (nx2 >= 0 && nx2 < mapWidth && ny2 >= 0 && ny2 < mapHeight) {
					range.add(new Vector2f(nx2, ny2));
				}
			}
			break;
		case CHECKER:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++) {
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					if ((dx + dy) % 2 == 0) {
						int nx = caster.gridX + dx, ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
							range.add(new Vector2f(nx, ny));
						}
					}
				}
			}
			break;
		case RANDOM:
			for (int i = 0; i < rangeRadius; i++) {
				int nx = caster.gridX + MathUtils.nextInt(rangeRadius * 2 + 1) - rangeRadius;
				int ny = caster.gridY + MathUtils.nextInt(rangeRadius * 2 + 1) - rangeRadius;
				if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight) {
					range.add(new Vector2f(nx, ny));
				}
			}
			break;
		case PATH:
			if (pathList != null) {
				for (Vector2f p : pathList) {
					if (p.x >= 0 && p.x < mapWidth && p.y >= 0 && p.y < mapHeight) {
						range.add(p);
					}
				}
			}
			break;
		default:
			range.add(new Vector2f(caster.gridX, caster.gridY));
			break;
		}
		return range;
	}

	public void drawAttackEffect(GLEx g, float deltaTime, float x, float y) {
		if (attackEffectAnim == null) {
			return;
		}
		attackEffectAnim.update(deltaTime);
		LTexture frame = attackEffectAnim.getSpriteImage();
		if (frame == null) {
			return;
		}
		int a = g.color();
		g.setAlpha(effectAlpha);
		g.draw(frame, x, y, MathUtils.max(width, frame.getWidth()), MathUtils.max(height, frame.getHeight()));
		g.setAlpha(a);
	}

	private void drawSkillRangeHighlight(GLEx g, float px, float py, float ox, float oy, int w, int h) {
		if (rangeDistance <= 0) {
			return;
		}
		if (battleMap == null) {
			return;
		}
		effColor.setColor(1f, 1f, 0f, rangeHighlightAlpha);
		int cellWidth = battleMap.getTileWidth();
		int cellHeight = battleMap.getTileHeight();
		int cx = MathUtils.ifloor(px / cellWidth);
		int cy = MathUtils.ifloor(py / cellHeight);
		int minX = MathUtils.max(0, cx - rangeRadius);
		int maxX = MathUtils.min(battleMap.getRow() - 1, cx + rangeRadius);
		int minY = MathUtils.max(0, cy - rangeRadius);
		int maxY = MathUtils.min(battleMap.getCol() - 1, cy + rangeRadius);
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				if (MathUtils.max(MathUtils.abs(x - cx), MathUtils.abs(y - cy)) > rangeRadius) {
					continue;
				}
				Vector2f sp = battleMap.getTileToScreen(x, y, w, h, 0f, 0f);
				g.draw(defaultEffectTexture, sp.x + ox, sp.y + oy, w, h);
			}
		}
	}

	private void drawLightOverlay(GLEx g, float x, float y, float w, float h) {
		switch (battleType) {
		case RANGE:
			effColor.setColor(0f, 0.5f, 1f, lightOverlayAlpha);
			break;
		case MELEE:
			effColor.setColor(1f, 0.2f, 0f, lightOverlayAlpha);
			break;
		case HEAL:
			effColor.setColor(0f, 1f, 0.3f, lightOverlayAlpha);
			break;
		default:
			g.setColor(1f, 1f, 1f, lightOverlayAlpha);
			break;
		}
		g.draw(defaultEffectTexture, x, y, w, h, effColor);
	}

	private void updateScreenShake(float delta) {
		if (shakeDuration <= 0) {
			return;
		}
		shakeDuration = MathUtils.max(0, shakeDuration - delta);
		shakeIntensity = MathUtils.clamp(shakeIntensity - delta * 5f, 0, MAX_SHAKE_INTENSITY);
		float newX = (MathUtils.random() - 0.5f) * shakeIntensity;
		float newY = (MathUtils.random() - 0.5f) * shakeIntensity;
		if (battleMap != null) {
			battleMap.setOffset(newX, newY);
		}
	}

	public void triggerHitShake() {
		shakeIntensity = 8f;
		shakeDuration = 0.2f;
	}

	public void drawSkillEffect(GLEx g, float deltaTime, float x, float y, float ox, float oy) {
		float effectX = x + ox;
		float effectY = y + oy;
		drawSkillRangeHighlight(g, x, y, ox, oy, width, height);
		if (skillEffectAnim != null) {
			skillEffectAnim.update(deltaTime);
			LTexture frame = skillEffectAnim.getSpriteImage();
			if (frame != null) {
				g.draw(frame, effectX, effectY, MathUtils.max(width, frame.getWidth()),
						MathUtils.max(height, frame.getHeight()), effColor, rotation);

			}
		}
		updateScreenShake(deltaTime);
		drawLightOverlay(g, effectX, effectY, width, height);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public SkillType getSkilltype() {
		return skilltype;
	}

	public void setSkilltype(SkillType skilltype) {
		this.skilltype = skilltype;
	}

	public UnitType[] getLimitUnits() {
		return limitUnits;
	}

	public void setLimitUnits(UnitType[] limitUnits) {
		this.limitUnits = limitUnits;
	}

	public BattleTileType[] getLimitTiles() {
		return limitTiles;
	}

	public void setLimitTiles(BattleTileType[] limitTiles) {
		this.limitTiles = limitTiles;
	}

	public WeatherType[] getLimitWeathers() {
		return limitWeathers;
	}

	public void setLimitWeathers(WeatherType[] limitWeathers) {
		this.limitWeathers = limitWeathers;
	}

	public int getMinLevel() {
		return minLevel;
	}

	public void setMinLevel(int minLevel) {
		this.minLevel = minLevel;
	}

	public boolean isNeedCity() {
		return needCity;
	}

	public void setNeedCity(boolean needCity) {
		this.needCity = needCity;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public float getBaseSuccessRate() {
		return baseSuccessRate;
	}

	public void setBaseSuccessRate(float baseSuccessRate) {
		this.baseSuccessRate = baseSuccessRate;
	}

	public int getMoraleCost() {
		return moraleCost;
	}

	public void setMoraleCost(int moraleCost) {
		this.moraleCost = moraleCost;
	}

	public float getCooldown() {
		return cooldown;
	}

	public void setCooldown(float cooldown) {
		this.cooldown = cooldown;
	}

	public float getLastUseTime() {
		return lastUseTime;
	}

	public void setLastUseTime(float lastUseTime) {
		this.lastUseTime = lastUseTime;
	}

	public int getMpCost() {
		return mpCost;
	}

	public void setMpCost(int mpCost) {
		this.mpCost = mpCost;
	}

	public int getActionPointCost() {
		return actionPointCost;
	}

	public void setActionPointCost(int actionPointCost) {
		this.actionPointCost = actionPointCost;
	}

	public RangeType getRangeType() {
		return rangeType;
	}

	public void setRangeType(RangeType rangeType) {
		this.rangeType = rangeType;
	}

	public int getRangeDistance() {
		return rangeDistance;
	}

	public void setRangeDistance(int rangeDistance) {
		this.rangeDistance = rangeDistance;
	}

	public int getRangeRadius() {
		return rangeRadius;
	}

	public void setRangeRadius(int rangeRadius) {
		this.rangeRadius = rangeRadius;
	}

	public float getWidth() {
		return width;
	}

	public void setWidth(int w) {
		this.width = w;
	}

	public float getHeight() {
		return height;
	}

	public void setHeight(int h) {
		this.height = h;
	}

	public void updateCooldown(float delta) {
		if (cooldown > 0) {
			cooldown = MathUtils.max(0, cooldown - delta);
		}
	}

	public boolean isReady() {
		return cooldown <= 0.01f;
	}

	public void startCooldown() {
		if (isReady()) {
			cooldown = cooldownDuration;
			lastUseTime = TimeUtils.currentMillis();
		}
	}

	public boolean canCast(int casterLevel) {
		return casterLevel >= minLevel && isReady();
	}

	public float getLightOverlayAlpha() {
		return lightOverlayAlpha;
	}

	public void setLightOverlayAlpha(float lightOverlayAlpha) {
		this.lightOverlayAlpha = lightOverlayAlpha;
	}

	public float getEffectAlpha() {
		return effectAlpha;
	}

	public void setEffectAlpha(float effectAlpha) {
		this.effectAlpha = effectAlpha;
	}

	public float getRangeHighlightAlpha() {
		return rangeHighlightAlpha;
	}

	public void setRangeHighlightAlpha(float rangeHighlightAlpha) {
		this.rangeHighlightAlpha = rangeHighlightAlpha;
	}

	public int getDamage() {
		return damage;
	}

	public void setDamage(int damage) {
		this.damage = damage;
	}

	public float getHitRate() {
		return hitRate;
	}

	public void setHitRate(float hitRate) {
		this.hitRate = hitRate;
	}

	public float getCritRate() {
		return critRate;
	}

	public void setCritRate(float critRate) {
		this.critRate = critRate;
	}

	public float getCooldownDuration() {
		return cooldownDuration;
	}

	public void setCooldownDuration(float cooldownDuration) {
		this.cooldownDuration = cooldownDuration;
	}

	public float getRotation() {
		return rotation;
	}

	public void setRotation(float rotation) {
		this.rotation = rotation;
	}

	public Animation getSkillEffectAnim() {
		return skillEffectAnim;
	}

	public void setSkillEffectAnim(Animation skillEffectAnim) {
		this.skillEffectAnim = skillEffectAnim;
	}

	public float getShakeIntensity() {
		return shakeIntensity;
	}

	public void setShakeIntensity(float shakeIntensity) {
		this.shakeIntensity = shakeIntensity;
	}

	public float getShakeDuration() {
		return shakeDuration;
	}

	public void setShakeDuration(float shakeDuration) {
		this.shakeDuration = shakeDuration;
	}

	public LColor getEffColor() {
		return effColor;
	}

	public BattleMap getBattleMap() {
		return battleMap;
	}

	public BattleType getBattleType() {
		return battleType;
	}

	public Animation getAttackEffectAnim() {
		return attackEffectAnim;
	}

	public void setAttackEffectAnim(Animation attackEffectAnim) {
		this.attackEffectAnim = attackEffectAnim;
	}

	public LTexture getDefaultEffectTexture() {
		return defaultEffectTexture;
	}

	public void setDefaultEffectTexture(LTexture defaultEffectTexture) {
		this.defaultEffectTexture = defaultEffectTexture;
	}

	@Override
	public void close() {
		if (attackEffectAnim != null) {
			attackEffectAnim.close();
			attackEffectAnim = null;
		}
		if (skillEffectAnim != null) {
			skillEffectAnim.close();
			skillEffectAnim = null;
		}
		if (defaultEffectTexture != null) {
			defaultEffectTexture.close();
			defaultEffectTexture = null;
		}
	}

}
