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
import loon.utils.StrBuilder;
import loon.utils.TArray;
import loon.utils.TimeUtils;

public class BattleSkill implements LRelease {

	public static enum BattleType {
		MELEE, RANGE, BUFF, DEBUFF, HEAL
	}

	public static enum LogicType {
		AND, OR
	}

	public static enum SKillEventType {
		ON_ATTACK, ON_HIT, ON_KILL, ON_DEATH, ON_TURN_START, ON_TURN_END
	}

	public static enum AnimPlayMode {
		// 单次播放
		ONCE,
		// 无限循环
		LOOP,
		// 指定次数循环
		LOOP_COUNT,
		// 暂停
		PAUSE
	}

	public static interface SkillCondition {

		boolean canTrigger(BattleMapObject caster, BattleMapObject target);

		String getDescription();
	}

	public abstract class ConditionalSkillEffect implements SkillEffect {

		private SkillCondition condition;

		public ConditionalSkillEffect(SkillCondition condition) {
			this.condition = condition;
		}

		@Override
		public void apply(BattleMapObject caster, BattleMapObject target) {
			if (condition == null || condition.canTrigger(caster, target)) {
				doApply(caster, target);
			}
		}

		protected abstract void doApply(BattleMapObject caster, BattleMapObject target);
	}

	public static class CompositeCondition implements SkillCondition {

		private TArray<SkillCondition> conditions = new TArray<SkillCondition>();

		private LogicType logicType;

		public CompositeCondition(LogicType logicType) {
			this.logicType = logicType;
		}

		public void addCondition(SkillCondition condition) {
			conditions.add(condition);
		}

		@Override
		public boolean canTrigger(BattleMapObject caster, BattleMapObject target) {
			if (logicType == LogicType.AND) {
				for (SkillCondition condition : conditions) {
					if (!condition.canTrigger(caster, target)) {
						return false;
					}
				}
				return true;
			} else {
				for (SkillCondition condition : conditions) {
					if (condition.canTrigger(caster, target)) {
						return true;
					}
				}
				return false;
			}
		}

		@Override
		public String getDescription() {
			StrBuilder sbr = new StrBuilder("conditions (").append(logicType).append("): ");
			for (SkillCondition condition : conditions) {
				sbr.append(condition.getDescription()).append(LSystem.COMMA);
			}
			return sbr.toString();
		}
	}

	public static interface SkillEffect {

		void apply(BattleMapObject caster, BattleMapObject target);

		String getDescription();
	}

	public static class CompositeSkillEffect implements SkillEffect {

		private TArray<SkillEffect> effects = new TArray<SkillEffect>();

		public void addEffect(SkillEffect effect) {
			effects.add(effect);
		}

		public void removeEffect(SkillEffect effect) {
			effects.remove(effect);
		}

		@Override
		public void apply(BattleMapObject caster, BattleMapObject target) {
			for (SkillEffect effect : effects) {
				effect.apply(caster, target);
			}
		}

		@Override
		public String getDescription() {
			StrBuilder sbr = new StrBuilder("effects:");
			for (SkillEffect effect : effects) {
				sbr.append(effect.getDescription()).append(LSystem.COMMA);
			}
			return sbr.toString();
		}
	}

	public static interface SkillTriggerEvent {

		boolean onEvent(BattleMapObject caster, BattleMapObject target);

		String getDescription();
	}

	public static interface EventCondition {

		boolean canTrigger(BattleMapObject caster, BattleMapObject target);

		String getDescription();
	}

	private static final float MAX_SHAKE_INTENSITY = 12f;
	private static final float DEFAULT_ANIM_SPEED = 1f;
	private static final float DEFAULT_SCALE = 1f;
	private static final float SMOOTH_FACTOR = 0.05f;

	private final LColor effColor = new LColor();
	private float lightOverlayAlpha = 0.4f;
	private float effectAlpha = 0.8f;
	private float rangeHighlightAlpha = 0.5f;
	private BattleMap battleMap;
	private SkillEffect effect;
	protected String id;
	protected String name;
	protected String description;
	protected SkillType skilltype;
	protected UnitType[] limitUnits;
	protected BattleTileType[] limitTiles;
	protected WeatherType[] limitWeathers;
	protected BattleType battleType;
	protected int minLevel = 1;
	protected boolean needCity = false;
	protected int priority = 3;
	protected float baseSuccessRate = 0.7f;
	protected int moraleCost = 20;
	protected float cooldown = 5;
	protected float lastUseTime = 0;
	protected int mpCost = 0;
	protected int actionPointCost = 2;
	protected RangeType rangeType = RangeType.SINGLE;
	protected int rangeDistance = 1;
	protected int rangeRadius = 1;
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
	private TArray<SkillTriggerEvent> triggerEvents = new TArray<SkillTriggerEvent>();
	private AnimPlayMode attackAnimMode = AnimPlayMode.ONCE;
	private AnimPlayMode skillAnimMode = AnimPlayMode.ONCE;
	private int loopCount = 3;
	private int currentLoop = 0;
	private boolean animFinished = false;
	private float animSpeed = DEFAULT_ANIM_SPEED;
	private float scaleX = DEFAULT_SCALE;
	private float scaleY = DEFAULT_SCALE;
	private float offsetX = 0f;
	private float offsetY = 0f;
	private boolean fadeInEnable = false;
	private boolean fadeOutEnable = false;
	private float fadeDuration = 0.5f;
	private float fadeTimer = 0f;
	private boolean blinkEnable = false;
	private float blinkInterval = 0.2f;
	private float blinkTimer = 0f;

	private float lastShakeX, lastShakeY;
	private float targetAlpha = 1f;
	private float currentAlpha = 1f;

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

	public void setBattleMap(BattleMap map) {
		this.battleMap = map;
	}

	public void drawAttackEffect(GLEx g, float deltaTime, float x, float y) {
		if (attackEffectAnim == null) {
			return;
		}
		updateAnimCommon(attackEffectAnim, attackAnimMode, deltaTime);
		updateFadeBlink(deltaTime);
		updateAlphaInterpolation();
		LTexture frame = attackEffectAnim.getSpriteImage();
		if (frame == null) {
			return;
		}
		effColor.setAlpha(effectAlpha * currentAlpha * getFadeAlpha());
		float renderW = MathUtils.max(width, frame.getWidth()) * scaleX;
		float renderH = MathUtils.max(height, frame.getHeight()) * scaleY;
		g.draw(frame, x + offsetX, y + offsetY, renderW, renderH, effColor);
	}

	private void drawSkillRangeHighlight(GLEx g, float px, float py, float ox, float oy, int w, int h) {
		if (battleMap == null) {
			return;
		}
		if (rangeDistance <= 0) {
			return;
		}
		effColor.setColor(1f, 1f, 0f, rangeHighlightAlpha * currentAlpha);
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
				g.draw(defaultEffectTexture, sp.x + ox, sp.y + oy, w, h, effColor);
			}
		}
	}

	private void drawLightOverlay(GLEx g, float x, float y, float w, float h) {
		switch (battleType) {
		case RANGE:
			effColor.setColor(0f, 0.5f, 1f, lightOverlayAlpha * currentAlpha);
			break;
		case MELEE:
			effColor.setColor(1f, 0.2f, 0f, lightOverlayAlpha * currentAlpha);
			break;
		case HEAL:
			effColor.setColor(0f, 1f, 0.3f, lightOverlayAlpha * currentAlpha);
			break;
		default:
			effColor.setColor(1f, 1f, 1f, lightOverlayAlpha * currentAlpha);
			break;
		}
		g.draw(defaultEffectTexture, x, y, w, h, effColor);
	}

	private void updateScreenShake(float delta) {
		if (shakeDuration <= 0) {
			lastShakeX = MathUtils.lerp(lastShakeX, 0, SMOOTH_FACTOR);
			lastShakeY = MathUtils.lerp(lastShakeY, 0, SMOOTH_FACTOR);
			if (battleMap != null) {
				battleMap.setOffset(lastShakeX, lastShakeY);
			}
			return;
		}
		shakeDuration = MathUtils.max(0, shakeDuration - delta);
		shakeIntensity = MathUtils.clamp(shakeIntensity - delta * 5f, 0, MAX_SHAKE_INTENSITY);
		float targetX = (MathUtils.random() - 0.5f) * shakeIntensity;
		float targetY = (MathUtils.random() - 0.5f) * shakeIntensity;
		lastShakeX = MathUtils.lerp(lastShakeX, targetX, SMOOTH_FACTOR);
		lastShakeY = MathUtils.lerp(lastShakeY, targetY, SMOOTH_FACTOR);
		if (battleMap != null) {
			battleMap.setOffset(lastShakeX, lastShakeY);
		}
	}

	public void triggerHitShake() {
		shakeIntensity = 8f;
		shakeDuration = 0.2f;
	}

	public void drawSkillEffect(GLEx g, float deltaTime, float x, float y, float ox, float oy) {
		float effectX = x + ox;
		float effectY = y + oy;

		updateAnimCommon(skillEffectAnim, skillAnimMode, deltaTime);
		updateFadeBlink(deltaTime);
		updateAlphaInterpolation();
		drawSkillRangeHighlight(g, x, y, ox, oy, width, height);

		if (skillEffectAnim != null) {
			LTexture frame = skillEffectAnim.getSpriteImage();
			if (frame != null) {
				effColor.setAlpha(effectAlpha * currentAlpha * getFadeAlpha());
				float renderW = MathUtils.max(width, frame.getWidth()) * scaleX;
				float renderH = MathUtils.max(height, frame.getHeight()) * scaleY;
				g.draw(frame, effectX + offsetX, effectY + offsetY, renderW, renderH, effColor, rotation);
			}
		}
		updateScreenShake(deltaTime);
		drawLightOverlay(g, effectX, effectY, width, height);
	}

	private void updateAlphaInterpolation() {
		currentAlpha = MathUtils.lerp(currentAlpha, targetAlpha, SMOOTH_FACTOR);
	}

	private void updateAnimCommon(Animation anim, AnimPlayMode mode, float delta) {
		if (anim == null || mode == AnimPlayMode.PAUSE || animFinished) {
			return;
		}
		anim.update(delta * animSpeed);

		if (anim.isFinished()) {
			switch (mode) {
			default:
			case ONCE:
				animFinished = true;
				break;
			case LOOP:
				anim.reset();
				break;
			case LOOP_COUNT:
				currentLoop++;
				if (currentLoop < loopCount) {
					anim.reset();
				} else {
					animFinished = true;
				}
				break;
			}
		}
	}

	private void updateFadeBlink(float delta) {
		if (fadeInEnable || fadeOutEnable) {
			fadeTimer = MathUtils.min(fadeTimer + delta, fadeDuration);
		}
		if (blinkEnable) {
			blinkTimer += delta;
			if (blinkTimer >= blinkInterval) {
				float r = MathUtils.random(0.6f, 1f);
				float g = MathUtils.random(0.6f, 1f);
				float b = MathUtils.random(0.6f, 1f);
				effColor.setColor(r, g, b, effColor.a);
				blinkTimer = 0f;
			}
		}
	}

	private float getFadeAlpha() {
		if (!fadeInEnable && !fadeOutEnable) {
			return 1f;
		}
		if (fadeInEnable) {
			return fadeTimer / fadeDuration;
		}
		if (fadeOutEnable) {
			return 1f - (fadeTimer / fadeDuration);
		}
		return 1f;
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

	public void castEffect(BattleMapObject caster, BattleMapObject target) {
		if (effect != null && canCast(caster.getLevel())) {
			effect.apply(caster, target);
			startCooldown();
			resetAnim();
		}
	}

	public BattleSkill setEffect(SkillEffect eff) {
		effect = eff;
		return this;
	}

	public void resetAnim() {
		if (attackEffectAnim != null) {
			attackEffectAnim.reset();
		}
		if (skillEffectAnim != null) {
			skillEffectAnim.reset();
		}
		currentLoop = 0;
		animFinished = false;
		fadeTimer = 0f;
		blinkTimer = 0f;
		currentAlpha = 0f;
	}

	public void setSkillAnimPlayMode(AnimPlayMode mode) {
		this.skillAnimMode = mode;
		resetAnim();
	}

	public void setAttackAnimPlayMode(AnimPlayMode mode) {
		this.attackAnimMode = mode;
		resetAnim();
	}

	public void setLoopCount(int count) {
		this.loopCount = MathUtils.max(1, count);
		this.skillAnimMode = AnimPlayMode.LOOP_COUNT;
		resetAnim();
	}

	public void pauseAnim(boolean pause) {
		this.attackAnimMode = pause ? AnimPlayMode.PAUSE : AnimPlayMode.LOOP;
		this.skillAnimMode = pause ? AnimPlayMode.PAUSE : AnimPlayMode.LOOP;
	}

	public void setAnimScale(float scale) {
		this.scaleX = scale;
		this.scaleY = scale;
	}

	public void setAnimOffset(float x, float y) {
		this.offsetX = x;
		this.offsetY = y;
	}

	public void setFadeIn(boolean enable) {
		this.fadeInEnable = enable;
		this.fadeOutEnable = !enable;
	}

	public void setFadeOut(boolean enable) {
		this.fadeOutEnable = enable;
		this.fadeInEnable = !enable;
	}

	public void setBlink(boolean enable) {
		this.blinkEnable = enable;
	}

	public void setAnimSpeed(float speed) {
		this.animSpeed = MathUtils.clamp(speed, 0.1f, 3f);
	}

	public void addTriggerEvent(SkillTriggerEvent event) {
		triggerEvents.add(event);
	}

	public void triggerEvents(BattleMapObject caster, BattleMapObject target, String eventType) {
		for (SkillTriggerEvent event : triggerEvents) {
			event.onEvent(caster, target);
		}
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

	public SkillEffect getSkillEffect() {
		return effect;
	}

	public boolean isAnimFinished() {
		return animFinished;
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
