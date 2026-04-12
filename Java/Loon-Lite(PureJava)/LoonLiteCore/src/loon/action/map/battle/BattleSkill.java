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
import loon.action.map.battle.BattleType.ObjectState;
import loon.action.map.battle.BattleType.RangeType;
import loon.action.map.battle.BattleType.SkillType;
import loon.action.map.battle.BattleType.WeatherType;
import loon.action.sprite.Animation;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.utils.ISOUtils.IsoConfig;
import loon.utils.IntArray;
import loon.utils.MathUtils;
import loon.utils.SortedList;
import loon.utils.StrBuilder;
import loon.utils.TArray;
import loon.utils.TimeUtils;

public class BattleSkill implements LRelease {

	public static enum BattleType {
		BASEA_ATTACK, MELEE, RANGE, BUFF, DEBUFF, HEAL
	}

	public static enum LogicType {
		AND, OR
	}

	public static enum SKillEventType {
		ON_CAST_START, ON_CASTING, ON_HIT, ON_KILL, ON_FINISH
	}

	public static enum SkillState {
		READY,
		// 前摇
		CAST_START,
		// 释放中
		CASTING,
		// 弹道飞行（远程）
		PROJECTILE,
		// 命中
		HIT,
		// 后摇
		AFTER_HIT,
		// 结束
		FINISHED
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

	public static enum DamageType {
		PHYSICAL, MAGIC, TRUE, HEAL
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
		boolean onEvent(SKillEventType eventType, BattleMapObject caster, BattleMapObject target);

		String getDescription();
	}

	public static interface EventCondition {
		boolean canTrigger(BattleMapObject caster, BattleMapObject target);

		String getDescription();
	}

	private static final float DEFAULT_ANIM_SPEED = 1f;
	private static final float DEFAULT_SCALE = 1f;
	private static final float SMOOTH_FACTOR = 0.18f;
	private static final float HIT_STOP_TIME = 0.12f;

	private final LColor lightTileColor = new LColor();
	private final LColor effColor = new LColor();

	private final float[] pointX = new float[4];
	private final float[] pointY = new float[4];

	private final Vector2f position = new Vector2f();

	private float lightOverlayAlpha = 0.45f;
	private float effectAlpha = 0.9f;
	private float rangeHighlightAlpha = 0.55f;
	private float trailAlphaMul = 0.6f;

	private BattleMap battleMap;
	private SkillEffect effect;
	protected int id;
	protected String name;
	protected String description;
	protected SkillType skilltype;
	protected final IntArray limitUnits = new IntArray();
	protected BattleTileType[] limitTiles;
	protected WeatherType[] limitWeathers;
	protected BattleType battleType;
	protected int minLevel = 1;
	protected boolean needCity;
	protected boolean castTriggered;

	protected int priority = 3;
	protected float baseSuccessRate = 0.7f;
	protected int moraleCost = 20;
	protected float cooldown = 5f;
	protected float lastUseTime;
	protected int mpCost;
	protected int actionPointCost = 2;
	protected RangeType rangeType = RangeType.SINGLE;
	protected int rangeDistance = 1;
	protected int rangeRadius = 1;
	protected int damage = 1;
	protected int width, height;
	protected float hitRate;
	protected float critRate;
	protected float cooldownDuration;
	protected float rotation;

	protected Animation attackEffectAnim;
	protected Animation skillEffectAnim;
	protected Animation bgEffectAnim;
	protected LTexture tileEffectTexture;
	private float shakeIntensity, shakeDuration;

	private AnimPlayMode attackAnimMode = AnimPlayMode.ONCE;
	private AnimPlayMode skillAnimMode = AnimPlayMode.ONCE;
	private AnimPlayMode bgAnimMode = AnimPlayMode.ONCE;
	private int loopCount = 1;
	private int currentLoop;
	private boolean animFinished;
	private float animSpeed = DEFAULT_ANIM_SPEED;
	private float scaleX = DEFAULT_SCALE;
	private float scaleY = DEFAULT_SCALE;
	private float offsetX, offsetY;
	private boolean allDirection = false;
	private boolean playHalfway = false;
	private boolean fadeInEnable = true;
	private boolean fadeOutEnable = true;
	private boolean shakeEnable = true;
	private boolean lightRangEnable = true;
	private boolean trailEnable = false;
	protected boolean running = true;

	private float fadeDuration = 0.4f;
	private float fadeTimer;
	private boolean blinkEnable;
	private float blinkInterval = 0.16f;
	private float blinkTimer;

	private float hitStopTimer;
	private boolean hitStopped;

	private float projectileSpeed = 360f;
	private float projectileDist;
	private float projectileProgress;

	private boolean effectTriggered;

	private BattleMapObject casterObject;
	private BattleMapObject targetObject;
	private SkillState state = SkillState.READY;
	private float stateTimer;
	private float castDelay = 1.1f;
	private float hitDelay = 1.25f;
	private float afterHitDuration = 1.2f;

	private float lastShakeX, lastShakeY;
	private float targetAlpha = 1f;
	private float currentAlpha;

	private final TArray<SkillTriggerEvent> triggerEvents = new TArray<SkillTriggerEvent>();

	private final Vector2f attackAnimOffset = new Vector2f();
	private final Vector2f skillAnimOffset = new Vector2f();
	private final Vector2f bgAnimOffset = new Vector2f();

	private boolean attackAnimComplete;
	private boolean skillAnimComplete;
	private boolean bgAnimComplete;

	private int skillLevel = 1;
	private int maxSkillLevel = 10;
	private float critDamage = 1.5f;
	private DamageType damageType = DamageType.PHYSICAL;
	private boolean interruptible = true;
	private boolean passive = false;

	public BattleSkill(int id, String name) {
		this(id, name, BattleType.RANGE, 0, 0, 0, 0, 0, 0, 0, 0, RangeType.AOE, 0, 0);
	}

	public BattleSkill(int id, String name, BattleType type, int damage, int mpCost, float hitRate, float critRate,
			float baseSuccessRate, int moraleCost, int actionPointCost, float cooldownDuration, RangeType rangeType,
			int rangeDistance, int rangeRadius) {
		this(id, name, LSystem.UNKNOWN, type, damage, mpCost, hitRate, critRate, null, null, null, mpCost, false, 1,
				baseSuccessRate, moraleCost, actionPointCost, cooldownDuration, rangeType, rangeDistance, rangeRadius);
	}

	public BattleSkill(int id, String name, String description, BattleType type, int damage, int mpCost, float hitRate,
			float critRate, int[] limitUnits, BattleTileType[] limitTiles, WeatherType[] limitWeathers, int minLevel,
			boolean needCity, int priority, float baseSuccessRate, int moraleCost, int actionPointCost,
			float cooldownDuration, RangeType rangeType, int rangeDistance, int rangeRadius) {
		this.id = MathUtils.max(0, id);
		this.name = (name == null) ? LSystem.UNKNOWN : name.trim();
		this.description = description == null ? LSystem.UNKNOWN : description;
		this.battleType = type == null ? BattleType.MELEE : type;
		this.damage = MathUtils.clamp(damage, 0, 99999999);
		this.mpCost = MathUtils.clamp(mpCost, 0, 10000000);
		this.hitRate = MathUtils.clamp(hitRate, 0f, 1f);
		this.critRate = MathUtils.clamp(critRate, 0f, 1f);
		if (limitUnits != null) {
			this.limitUnits.addAll(limitUnits);
		}
		this.limitTiles = limitTiles == null ? new BattleTileType[0] : limitTiles;
		this.limitWeathers = limitWeathers == null ? new WeatherType[0] : limitWeathers;
		this.running = true;
		this.minLevel = MathUtils.max(1, minLevel);
		this.needCity = needCity;
		this.priority = MathUtils.clamp(priority, 1, 5);
		this.baseSuccessRate = MathUtils.clamp(baseSuccessRate, 0f, 1f);
		this.moraleCost = MathUtils.max(0, moraleCost);
		this.actionPointCost = MathUtils.max(0, actionPointCost);
		this.rangeType = rangeType == null ? RangeType.SINGLE : rangeType;
		this.rangeDistance = MathUtils.max(0, rangeDistance);
		this.rangeRadius = MathUtils.max(0, rangeRadius);
		this.cooldownDuration = MathUtils.max(0.1f, cooldownDuration);
		this.cooldown = 0f;
	}

	/**
	 * 强制重置技能释放状态
	 */
	public void resetCast() {
		this.castTriggered = false;
		this.effectTriggered = false;
		this.playHalfway = false;
		this.animFinished = false;

		// 重置所有计时器
		this.stateTimer = 0f;
		this.fadeTimer = 0f;
		this.blinkTimer = 0f;
		this.hitStopTimer = 0f;
		this.projectileProgress = 0f;

		// 重置控制状态
		this.hitStopped = false;
		this.currentLoop = 0;

		// 重置透明度
		this.currentAlpha = 0f;
		this.targetAlpha = 1f;

		// 重置动画完成状态
		this.attackAnimComplete = false;
		this.skillAnimComplete = false;
		this.bgAnimComplete = false;

		// 重置动画
		resetAnim();

		// 重置技能状态为就绪
		setState(SkillState.READY);
	}

	/**
	 * 取消当前施法（被眩晕/击退/沉默时调用）
	 */
	public void cancelCast() {
		if (!interruptible) {
			return;
		}
		resetCast();
		setState(SkillState.FINISHED);
		if (casterObject != null) {
			casterObject.setState(ObjectState.IDLE);
		}
	}

	public void updateSkill(float deltaTime) {
		updateSkill(deltaTime, casterObject, targetObject);
	}

	/**
	 * 技能生命周期更新
	 * 
	 * @param deltaTime
	 * @param caster
	 * @param target
	 */
	public void updateSkill(float deltaTime, BattleMapObject caster, BattleMapObject target) {
		if (!running) {
			return;
		}
		if (state == SkillState.READY || state == SkillState.FINISHED) {
			updateCooldown(deltaTime);
			return;
		}

		// 命中停顿
		if (hitStopped) {
			hitStopTimer -= deltaTime;
			if (hitStopTimer <= 0) {
				hitStopped = false;
			} else {
				updateScreenShake(deltaTime);
				return;
			}
		}
		stateTimer += deltaTime;
		updateAlphaInterpolation();
		updateScreenShake(deltaTime);

		// 动画更新
		if (!hitStopped) {
			updateAllAnimations(deltaTime);
			updateFadeBlink(deltaTime);
			// 只有所有动画全部播放完成，并且延迟时间已过，才标记总完成状态，会比实际播放完成延迟afterHitDuration
			checkAllAnimationsFinished();
			// 当全部动画播放都超过播放一半时，激活命中状态，方便用户使用自定义设置，全播放有些设置可能来不及(播放掉血，特殊效果触发之类)
			checkAllAnimationsHalfway();
		}
		switch (state) {
		case CAST_START:
			if (stateTimer >= castDelay) {
				setState(battleType == BattleType.RANGE ? SkillState.PROJECTILE : SkillState.CASTING);
				triggerEvents(caster, target, SKillEventType.ON_CASTING);
			}
			break;
		case PROJECTILE:
			projectileProgress = MathUtils.clamp(projectileProgress + deltaTime * projectileSpeed / projectileDist, 0,
					1);
			if (projectileProgress >= 1f) {
				triggerSkillHit(caster, target);
			}
			break;
		case CASTING:
			if (stateTimer >= hitDelay && !effectTriggered) {
				triggerSkillHit(caster, target);
			}
			if (playHalfway) {
				setState(SkillState.AFTER_HIT);
			}
			break;
		case HIT:
			if (stateTimer >= 0.1f) {
				setState(SkillState.AFTER_HIT);
			}
			break;
		case AFTER_HIT:
			if (stateTimer >= afterHitDuration && animFinished) {
				if (targetObject != null && targetObject.getHealth() <= 0) {
					triggerEvents(caster, target, SKillEventType.ON_KILL);
					targetObject.setState(ObjectState.DEAD);
				}
				setState(SkillState.FINISHED);
				triggerEvents(caster, target, SKillEventType.ON_FINISH);
				resetSkill();
				castTriggered = true;
				if (caster != null) {
					caster.setState(ObjectState.IDLE);
				}
			} else if (animFinished && caster != null) {
				caster.setState(ObjectState.IDLE);
			}
			break;
		default:
			break;
		}
	}

	private void updateAllAnimations(float delta) {
		if (attackEffectAnim == null || attackAnimMode == AnimPlayMode.PAUSE || hitStopped) {
			attackAnimComplete = attackEffectAnim == null;
		} else {
			attackEffectAnim.update(delta * animSpeed);
			if (attackEffectAnim.isFinished()) {
				switch (attackAnimMode) {
				case ONCE:
					attackAnimComplete = true;
					break;
				case LOOP:
					attackEffectAnim.reset();
					updateAnimLoop(attackEffectAnim);
					break;
				case LOOP_COUNT:
					currentLoop++;
					if (currentLoop < loopCount) {
						attackEffectAnim.reset();
						updateAnimLoop(attackEffectAnim);
					} else {
						attackAnimComplete = true;
					}
					break;
				default:
					break;
				}
			}
		}

		if (skillEffectAnim == null || skillAnimMode == AnimPlayMode.PAUSE || hitStopped) {
			skillAnimComplete = skillEffectAnim == null;
		} else {
			skillEffectAnim.update(delta * animSpeed);
			if (skillEffectAnim.isFinished()) {
				switch (skillAnimMode) {
				case ONCE:
					skillAnimComplete = true;
					break;
				case LOOP:
					skillEffectAnim.reset();
					updateAnimLoop(skillEffectAnim);
					break;
				case LOOP_COUNT:
					currentLoop++;
					if (currentLoop < loopCount) {
						skillEffectAnim.reset();
						updateAnimLoop(skillEffectAnim);
					} else {
						skillAnimComplete = true;
					}
					break;
				default:
					break;
				}
			}
		}

		if (bgEffectAnim == null || bgAnimMode == AnimPlayMode.PAUSE || hitStopped) {
			bgAnimComplete = bgEffectAnim == null;
		} else {
			bgEffectAnim.update(delta * animSpeed);
			if (bgEffectAnim.isFinished()) {
				switch (bgAnimMode) {
				case ONCE:
					bgAnimComplete = true;
					break;
				case LOOP:
					bgEffectAnim.reset();
					updateAnimLoop(bgEffectAnim);
					break;
				case LOOP_COUNT:
					currentLoop++;
					if (currentLoop < loopCount) {
						bgEffectAnim.reset();
						updateAnimLoop(bgEffectAnim);
					} else {
						bgAnimComplete = true;
					}
					break;
				default:
					break;
				}
			}
		}
	}

	private void checkAllAnimationsFinished() {
		boolean attackDone = (attackEffectAnim == null) || (attackAnimMode == AnimPlayMode.LOOP) || attackAnimComplete;
		boolean skillDone = (skillEffectAnim == null) || (skillAnimMode == AnimPlayMode.LOOP) || skillAnimComplete;
		boolean bgDone = (bgEffectAnim == null) || (bgAnimMode == AnimPlayMode.LOOP) || bgAnimComplete;
		animFinished = attackDone && skillDone && bgDone;
	}

	private void checkAllAnimationsHalfway() {
		boolean attackDone = (attackEffectAnim == null) || attackEffectAnim.isHalfwayPlaying() || attackAnimComplete;
		boolean skillDone = (skillEffectAnim == null) || skillEffectAnim.isHalfwayPlaying() || skillAnimComplete;
		boolean bgDone = (bgEffectAnim == null) || bgEffectAnim.isHalfwayPlaying() || bgAnimComplete;
		playHalfway = attackDone && skillDone && bgDone;
	}

	public void setSize(int w, int h) {
		this.width = w;
		this.height = h;
	}

	public void castEffect(float touchX, float touchY) {
		if (battleMap != null) {
			Vector2f pos = battleMap.findTileXY(touchX, touchY);
			if (battleMap.inTileGrid(pos.x(), pos.y())) {
				castTileEffect(pos.x(), pos.y());
			}
		}
	}

	public void castTileEffect(int gridX, int gridY) {
		if (battleMap != null) {
			Vector2f result = battleMap.getTileToScreen(gridX, gridY, 0, 0, 0, 0);
			if (result != null) {
				setPosition(result);
			} else {
				return;
			}
			resetSkill();
			setState(SkillState.CAST_START);
			startCooldown();
			startAnim();
			triggerEvents(casterObject, targetObject, SKillEventType.ON_CAST_START);
			castTriggered = false;
		}
	}

	public void castEffect(BattleMapObject target) {
		castEffect(null, target);
	}

	public void castEffect(BattleMapObject caster, BattleMapObject target) {
		if (caster != null && !canCast(caster.getLevel())) {
			return;
		}
		resetSkill();
		casterObject = caster;
		targetObject = target;
		setState(SkillState.CAST_START);
		startCooldown();
		startAnim();
		triggerEvents(caster, target, SKillEventType.ON_CAST_START);
		if (targetObject != null) {
			setPosition(targetObject.getCharCenterInScreenPixel());
		}
		if (battleType == BattleType.RANGE && caster != null && target != null) {
			float dx = target.getX() - caster.getX();
			float dy = target.getY() - caster.getY();
			projectileDist = MathUtils.sqrt(dx * dx + dy * dy);
			projectileProgress = 0f;
		}
		castTriggered = false;
	}

	private void triggerSkillHit(BattleMapObject caster, BattleMapObject target) {
		if (effectTriggered) {
			return;
		}
		effectTriggered = true;
		setState(SkillState.HIT);
		triggerHitShake();
		triggerHitStop();
		triggerEvents(caster, target, SKillEventType.ON_HIT);
		if (effect != null && target != null) {
			effect.apply(caster, target);
		}
	}

	private void setState(SkillState state) {
		this.state = state;
		this.stateTimer = 0f;
	}

	public void resetSkill() {
		casterObject = null;
		targetObject = null;
		effectTriggered = false;
		hitStopped = false;
		hitStopTimer = 0f;
		projectileProgress = 0f;
		fadeTimer = 0f;
		blinkTimer = 0f;
		currentAlpha = 0f;
		targetAlpha = 1f;
		attackAnimComplete = false;
		skillAnimComplete = false;
		bgAnimComplete = false;
		resetAnim();
	}

	private void triggerHitStop() {
		hitStopped = true;
		hitStopTimer = HIT_STOP_TIME;
	}

	public void drawSkillEffect(GLEx g, float deltaTime, float ox, float oy) {
		drawSkillEffect(g, deltaTime, position.x, position.y, ox, oy);
	}

	public void drawSkillEffect(GLEx g, float deltaTime, float x, float y, float ox, float oy) {
		if (!running || state == SkillState.FINISHED || state == SkillState.READY) {
			return;
		}
		float ex = x + ox + offsetX;
		float ey = y + oy + offsetY;
		float alpha = effectAlpha * currentAlpha * getFadeAlpha();
		effColor.setAlpha(alpha);
		if (lightRangEnable && !animFinished && rangeRadius > 0) {
			drawSkillRangeTileHighlight(g, ex, ey);
		}
		drawBackgroundEffect(g, deltaTime, ex, ey);
		drawSkillBaseEffect(g, deltaTime, ex, ey);
		drawAttackEffect(g, deltaTime, ex, ey);
	}

	protected void drawBackgroundEffect(GLEx g, float deltaTime, float x, float y) {
		if (bgEffectAnim == null) {
			return;
		}
		LTexture frame = bgEffectAnim.getSpriteImage();
		if (frame == null) {
			return;
		}
		float rw = (width <= 0 ? frame.getWidth() : width) * scaleX;
		float rh = (height <= 0 ? frame.getHeight() : height) * scaleY;
		g.draw(frame, x - rw / 2 + bgAnimOffset.x, y - rh / 2 + bgAnimOffset.y, rw, rh, effColor, rotation);
	}

	protected void drawSkillBaseEffect(GLEx g, float deltaTime, float x, float y) {
		if (skillEffectAnim == null) {
			return;
		}
		LTexture frame = skillEffectAnim.getSpriteImage();
		if (frame == null) {
			return;
		}
		float rw = (width <= 0 ? frame.getWidth() : width) * scaleX;
		float rh = (height <= 0 ? frame.getHeight() : height) * scaleY;
		g.draw(frame, x - rw / 2 + skillAnimOffset.x, y - rh / 2 + skillAnimOffset.y, rw, rh, effColor, rotation);
		if (trailEnable) {
			g.draw(frame, x - rw / 2 + skillAnimOffset.x - 4, y - rh / 2 + skillAnimOffset.y - 4, rw * 0.9f, rh * 0.9f,
					effColor, rotation);
		}
	}

	protected void drawAttackEffect(GLEx g, float deltaTime, float x, float y) {
		if (attackEffectAnim == null) {
			return;
		}
		LTexture frame = attackEffectAnim.getSpriteImage();
		if (frame == null) {
			return;
		}
		float a = effectAlpha * currentAlpha * getFadeAlpha();
		effColor.setAlpha(a);
		float rw = (width <= 0 ? frame.getWidth() : width) * scaleX;
		float rh = (height <= 0 ? frame.getHeight() : height) * scaleY;
		g.draw(frame, x - rw / 2 + attackAnimOffset.x, y - rh / 2 + attackAnimOffset.y, rw, rh, effColor, rotation);
	}

	private void drawSkillRangeTileHighlight(GLEx g, float px, float py) {
		if (battleMap == null) {
			return;
		}
		lightTileColor.setColor(1f, 0.85f, 0.25f, rangeHighlightAlpha * currentAlpha * getFadeAlpha());
		IsoConfig cfg = battleMap.getIsoConfig();
		final int tileWidth = MathUtils.ifloor(cfg.tileWidth);
		final int tileHeight = MathUtils.ifloor(cfg.tileHeight);
		final int scaleWidth = MathUtils.ifloor(tileWidth * cfg.scaleX);
		final int scaleHeight = MathUtils.ifloor(tileHeight * cfg.scaleY);
		int size = rangeRadius;

		final int[][] dirs4 = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
		final int[][] dirs8 = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
		final int[][] dirs = allDirection ? dirs8 : dirs4;

		float cx = px - scaleWidth / 2;
		float cy = py + scaleHeight;
		switch (rangeType) {
		default:
		case SINGLE:
		case SELF:
			drawTile(g, 0, 0, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case ADJACENT:
			for (int[] d : dirs)
				drawTile(g, d[0], d[1], cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case CROSS:
			for (int i = 1; i <= size; i++)
				for (int[] d : dirs)
					drawTile(g, d[0] * i, d[1] * i, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case DIAMOND:
			for (int i = 1; i <= size; i++) {
				drawTile(g, i, i, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
				drawTile(g, i, -i, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
				drawTile(g, -i, i, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
				drawTile(g, -i, -i, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			}
			break;
		case CIRCLE:
			for (int dx = -size; dx <= size; dx++)
				for (int dy = -size; dy <= size; dy++)
					if (dx * dx + dy * dy <= size * size)
						drawTile(g, dx, dy, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case LINE:
			for (int i = 1; i <= size; i++)
				drawTile(g, i, 0, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case LINE_AOE:
			for (int i = 1; i <= size; i++)
				for (int dy = -1; dy <= 1; dy++)
					drawTile(g, i, dy, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case SQUARE:
		case AREA:
			for (int dx = -size; dx <= size; dx++)
				for (int dy = -size; dy <= size; dy++)
					drawTile(g, dx, dy, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case RING:
			for (int dx = -size; dx <= size; dx++)
				for (int dy = -size; dy <= size; dy++) {
					int d2 = dx * dx + dy * dy;
					if (d2 >= (size - 1) * (size - 1) && d2 <= size * size)
						drawTile(g, dx, dy, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
				}
			break;
		case SECTOR:
			for (int dx = 0; dx <= size; dx++)
				for (int dy = -dx; dy <= dx; dy++)
					drawTile(g, dx, dy, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case PLUS:
			for (int i = -size; i <= size; i++) {
				drawTile(g, i, 0, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
				drawTile(g, 0, i, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			}
			break;
		case ROW:
			for (int x = 0; x < battleMap.getRow(); x++)
				drawTile(g, x, 0, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case COLUMN:
			for (int y = 0; y < battleMap.getCol(); y++)
				drawTile(g, 0, y, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		case AOE:
		case GLOBAL:
			for (int x = 0; x < battleMap.getRow(); x++)
				for (int y = 0; y < battleMap.getCol(); y++)
					drawTile(g, x, y, cx, cy, tileWidth, tileHeight, scaleWidth, scaleHeight);
			break;
		}
	}

	private void drawTile(GLEx g, int dx, int dy, float px, float py, int tw, int th, int sw, int sh) {
		Vector2f sp = battleMap.getTileToScreen(dx, dy, tw, th, 0, 0);
		float drawX = px + sp.x;
		float drawY = py + sp.y;
		drawTileLightOverlay(g, tileEffectTexture, drawX, drawY, sw, sh, lightTileColor);
	}

	private void drawTileLightOverlay(GLEx g, LTexture texture, float x, float y, float w, float h, LColor color) {
		switch (battleType) {
		case RANGE:
			color.setColor(0.1f, 0.6f, 1f, lightOverlayAlpha * currentAlpha);
			break;
		case MELEE:
			color.setColor(1f, 0.35f, 0.1f, lightOverlayAlpha * currentAlpha);
			break;
		case HEAL:
			color.setColor(0.2f, 1f, 0.4f, lightOverlayAlpha * currentAlpha);
			break;
		case BUFF:
			color.setColor(0.85f, 1f, 0.85f, lightOverlayAlpha * currentAlpha);
			break;
		case DEBUFF:
			color.setColor(0.6f, 0.2f, 1f, lightOverlayAlpha * currentAlpha);
			break;
		default:
			color.setColor(1f, 1f, 1f, lightOverlayAlpha * currentAlpha);
			break;
		}
		if (texture != null) {
			g.draw(texture, x, y, w, h, color);
		} else {
			int old = g.color();
			float hw = w / 2f;
			float hh = h / 4f;
			pointX[0] = x + w / 2f;
			pointY[0] = y + hh;
			pointX[1] = x + w / 2f + hw;
			pointY[1] = y + h / 2f;
			pointX[2] = x + w / 2f;
			pointY[2] = y + h - hh;
			pointX[3] = x + w / 2f - hw;
			pointY[3] = y + h / 2f;
			g.setColor(color);
			g.fillPolygon(pointX, pointY, 4);
			g.setColor(old);
		}
	}

	private void updateScreenShake(float delta) {
		if (!shakeEnable) {
			return;
		}
		if (shakeDuration <= 0) {
			lastShakeX = MathUtils.lerp(lastShakeX, 0, SMOOTH_FACTOR);
			lastShakeY = MathUtils.lerp(lastShakeY, 0, SMOOTH_FACTOR);
			if (battleMap != null) {
				battleMap.setLocation(battleMap.getX() + lastShakeX, battleMap.getY() + lastShakeY);
			}
			return;
		}
		shakeDuration = MathUtils.max(0f, shakeDuration - delta);
		shakeIntensity = MathUtils.lerp(shakeIntensity, 0f, delta * 4.5f);
		lastShakeX = (MathUtils.random(0.5f, 5.5f) - shakeIntensity) * 2f;
		lastShakeY = (MathUtils.random(0.5f, 5.5f) - shakeIntensity) * 2f;
		if (battleMap != null) {
			battleMap.setLocation(battleMap.getX() + lastShakeX, battleMap.getY() + lastShakeY);
		}
	}

	public void triggerHitShake() {
		shakeIntensity = battleType == BattleType.MELEE ? 8.5f : 6f;
		shakeDuration = 0.28f;
	}

	private void updateFadeBlink(float delta) {
		if (fadeInEnable || fadeOutEnable) {
			fadeTimer = MathUtils.min(fadeTimer + delta, fadeDuration);
		}
		if (blinkEnable) {
			blinkTimer += delta;
			if (blinkTimer >= blinkInterval) {
				blinkTimer = 0f;
				effColor.setColor(MathUtils.random(0.75f, 1f), MathUtils.random(0.75f, 1f), MathUtils.random(0.75f, 1f),
						effColor.a);
			}
		}
	}

	private float getFadeAlpha() {
		if (fadeInEnable) {
			return MathUtils.clamp(fadeTimer / fadeDuration, 0f, 1f);
		}
		if (fadeOutEnable) {
			return MathUtils.clamp(1f - fadeTimer / fadeDuration, 0f, 1f);
		}
		return 1f;
	}

	private void updateAlphaInterpolation() {
		currentAlpha = MathUtils.lerp(currentAlpha, targetAlpha, SMOOTH_FACTOR);
	}

	public void updateCooldown(float delta) {
		if (cooldown > 0) {
			cooldown = MathUtils.max(0f, cooldown - delta);
		}
	}

	public boolean isReady() {
		return cooldown <= 0.01f && state == SkillState.READY;
	}

	public void startCooldown() {
		if (isReady()) {
			cooldown = cooldownDuration;
			lastUseTime = TimeUtils.currentMillis();
		}
	}

	public boolean canCast(int level) {
		return level >= minLevel && isReady();
	}

	private void updateAnimLoop(Animation ani) {
		if (ani != null) {
			// 写0不是写错了，是因为battleskil默认允许最多三组动画，所以循环管理器其实在BattleSkill里，方便管理，animation内部的计数器只能停用
			ani.setLoopCount(0);
			playHalfway = false;
		}
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
				if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
					range.add(new Vector2f(nx, ny));
			}
			break;
		case CROSS:
			for (int i = 1; i <= rangeDistance; i++)
				for (int[] d : dirs) {
					int nx = caster.gridX + d[0] * i;
					int ny = caster.gridY + d[1] * i;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
						range.add(new Vector2f(nx, ny));
				}
			break;
		case DIAGONAL:
			for (int i = 1; i <= rangeDistance; i++) {
				int[][] d = { { i, i }, { i, -i }, { -i, i }, { -i, -i } };
				for (int[] p : d) {
					int nx = caster.gridX + p[0];
					int ny = caster.gridY + p[1];
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
						range.add(new Vector2f(nx, ny));
				}
			}
			break;
		case CIRCLE:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++)
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++)
					if (dx * dx + dy * dy <= rangeDistance * rangeDistance) {
						int nx = caster.gridX + dx;
						int ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
							range.add(new Vector2f(nx, ny));
					}
			break;
		case AOE:
		case GLOBAL:
			for (int x = 0; x < mapWidth; x++)
				for (int y = 0; y < mapHeight; y++)
					range.add(new Vector2f(x, y));
			break;
		case SQUARE:
		case AREA:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++)
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					int nx = caster.gridX + dx;
					int ny = caster.gridY + dy;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
						range.add(new Vector2f(nx, ny));
				}
			break;
		case LINE:
			for (int i = 1; i <= rangeDistance; i++) {
				int nx = caster.gridX + i;
				int ny = caster.gridY;
				if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
					range.add(new Vector2f(nx, ny));
			}
			break;
		case LINE_AOE:
			for (int i = 1; i <= rangeDistance; i++)
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					int nx = caster.gridX + i;
					int ny = caster.gridY + dy;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
						range.add(new Vector2f(nx, ny));
				}
			break;
		case ROW:
			for (int x = 0; x < mapWidth; x++)
				range.add(new Vector2f(x, caster.gridY));
			break;
		case COLUMN:
			for (int y = 0; y < mapHeight; y++)
				range.add(new Vector2f(caster.gridX, y));
			break;
		case RING:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++)
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++) {
					int d = dx * dx + dy * dy;
					if (d <= rangeRadius * rangeRadius && d >= (rangeRadius - 1) * (rangeRadius - 1)) {
						int nx = caster.gridX + dx;
						int ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
							range.add(new Vector2f(nx, ny));
					}
				}
			break;
		case SECTOR:
			for (int dx = 0; dx <= rangeDistance; dx++)
				for (int dy = -dx; dy <= dx; dy++) {
					int nx = caster.gridX + dx;
					int ny = caster.gridY + dy;
					if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
						range.add(new Vector2f(nx, ny));
				}
			break;
		case DIAMOND:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++)
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++)
					if (Math.abs(dx) + Math.abs(dy) <= rangeRadius) {
						int nx = caster.gridX + dx;
						int ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
							range.add(new Vector2f(nx, ny));
					}
			break;
		case PLUS:
			for (int i = -rangeRadius; i <= rangeRadius; i++) {
				int x1 = caster.gridX + i, y1 = caster.gridY;
				int x2 = caster.gridX, y2 = caster.gridY + i;
				if (x1 >= 0 && x1 < mapWidth && y1 >= 0 && y1 < mapHeight)
					range.add(new Vector2f(x1, y1));
				if (x2 >= 0 && x2 < mapWidth && y2 >= 0 && y2 < mapHeight)
					range.add(new Vector2f(x2, y2));
			}
			break;
		case CHECKER:
			for (int dx = -rangeRadius; dx <= rangeRadius; dx++)
				for (int dy = -rangeRadius; dy <= rangeRadius; dy++)
					if ((dx + dy) % 2 == 0) {
						int nx = caster.gridX + dx;
						int ny = caster.gridY + dy;
						if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
							range.add(new Vector2f(nx, ny));
					}
			break;
		case RANDOM:
			for (int i = 0; i < rangeRadius; i++) {
				int nx = caster.gridX + MathUtils.nextInt(rangeRadius * 2 + 1) - rangeRadius;
				int ny = caster.gridY + MathUtils.nextInt(rangeRadius * 2 + 1) - rangeRadius;
				if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight)
					range.add(new Vector2f(nx, ny));
			}
			break;
		case PATH:
			if (pathList != null)
				for (Vector2f p : pathList)
					if (p.x >= 0 && p.x < mapWidth && p.y >= 0 && p.y < mapHeight)
						range.add(p);
			break;
		default:
			range.add(new Vector2f(caster.gridX, caster.gridY));
			break;
		}
		return range;
	}

	public void startAnim() {
		if (attackEffectAnim != null) {
			attackEffectAnim.start();
			updateAnimLoop(attackEffectAnim);
		}
		if (skillEffectAnim != null) {
			skillEffectAnim.start();
			updateAnimLoop(skillEffectAnim);
		}
		if (bgEffectAnim != null) {
			bgEffectAnim.start();
			updateAnimLoop(bgEffectAnim);
		}
		currentLoop = 0;
		animFinished = false;
		attackAnimComplete = false;
		skillAnimComplete = false;
		bgAnimComplete = false;
		playHalfway = false;
	}

	public void resetAnim() {
		if (attackEffectAnim != null) {
			attackEffectAnim.reset();
			updateAnimLoop(attackEffectAnim);
		}
		if (skillEffectAnim != null) {
			skillEffectAnim.reset();
			updateAnimLoop(skillEffectAnim);
		}
		if (bgEffectAnim != null) {
			bgEffectAnim.reset();
			updateAnimLoop(bgEffectAnim);
		}
		currentLoop = 0;
		animFinished = false;
		attackAnimComplete = false;
		skillAnimComplete = false;
		bgAnimComplete = false;
		playHalfway = false;
	}

	public BattleSkill setEffect(SkillEffect eff) {
		effect = eff;
		return this;
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

	public void triggerEvents(BattleMapObject caster, BattleMapObject target, SKillEventType eventType) {
		for (SkillTriggerEvent event : triggerEvents) {
			event.onEvent(eventType, caster, target);
		}
	}

	public int getSkillLevel() {
		return skillLevel;
	}

	public BattleSkill setSkillLevel(int level) {
		this.skillLevel = MathUtils.clamp(level, 1, maxSkillLevel);
		return this;
	}

	public DamageType getDamageType() {
		return damageType;
	}

	public BattleSkill setDamageType(DamageType type) {
		this.damageType = type;
		return this;
	}

	public float getCritDamage() {
		return critDamage;
	}

	public BattleSkill setCritDamage(float dmg) {
		this.critDamage = MathUtils.max(1f, dmg);
		return this;
	}

	public boolean isPassive() {
		return passive;
	}

	public BattleSkill setPassive(boolean val) {
		this.passive = val;
		return this;
	}

	public boolean isInterruptible() {
		return interruptible;
	}

	public BattleSkill setInterruptible(boolean val) {
		this.interruptible = val;
		return this;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
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

	public int[] getLimitUnits() {
		return limitUnits.items;
	}

	public void setLimitUnits(int[] limitUnits) {
		this.limitUnits.addAll(limitUnits);
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

	public LTexture getDefaultTileEffectTexture() {
		return tileEffectTexture;
	}

	public void setDefaultTileEffectTexture(LTexture defaultEffectTexture) {
		this.tileEffectTexture = defaultEffectTexture;
	}

	public SkillEffect getSkillEffect() {
		return effect;
	}

	public boolean isAnimFinished() {
		return animFinished;
	}

	public float getTrailAlphaMul() {
		return trailAlphaMul;
	}

	public void setTrailAlphaMul(float trailAlphaMul) {
		this.trailAlphaMul = trailAlphaMul;
	}

	public AnimPlayMode getAttackAnimMode() {
		return attackAnimMode;
	}

	public void setAttackAnimMode(AnimPlayMode attackAnimMode) {
		this.attackAnimMode = attackAnimMode;
	}

	public AnimPlayMode getSkillAnimMode() {
		return skillAnimMode;
	}

	public void setSkillAnimMode(AnimPlayMode skillAnimMode) {
		this.skillAnimMode = skillAnimMode;
	}

	public AnimPlayMode getBgAnimMode() {
		return bgAnimMode;
	}

	public void setBgAnimMode(AnimPlayMode bgAnimMode) {
		this.bgAnimMode = bgAnimMode;
	}

	public int getCurrentLoop() {
		return currentLoop;
	}

	public void setCurrentLoop(int currentLoop) {
		this.currentLoop = currentLoop;
	}

	public float getScaleX() {
		return scaleX;
	}

	public void setScaleX(float scaleX) {
		this.scaleX = scaleX;
	}

	public float getScaleY() {
		return scaleY;
	}

	public void setScaleY(float scaleY) {
		this.scaleY = scaleY;
	}

	public float getOffsetX() {
		return offsetX;
	}

	public void setOffsetX(float offsetX) {
		this.offsetX = offsetX;
	}

	public float getOffsetY() {
		return offsetY;
	}

	public void setOffsetY(float offsetY) {
		this.offsetY = offsetY;
	}

	public boolean isFadeInEnable() {
		return fadeInEnable;
	}

	public void setFadeInEnable(boolean fadeInEnable) {
		this.fadeInEnable = fadeInEnable;
	}

	public boolean isFadeOutEnable() {
		return fadeOutEnable;
	}

	public void setFadeOutEnable(boolean fadeOutEnable) {
		this.fadeOutEnable = fadeOutEnable;
	}

	public boolean isTrailEnable() {
		return trailEnable;
	}

	public void setTrailEnable(boolean trailEnable) {
		this.trailEnable = trailEnable;
	}

	public boolean canUltimateSkill() {
		if (battleType != null
				&& (battleType == BattleType.MELEE || battleType == BattleType.RANGE || battleType == BattleType.DEBUFF)
				&& (battleType != BattleType.BASEA_ATTACK)) {
			return true;
		}
		return false;
	}

	public boolean canBaseAttackSkill() {
		if (battleType != null && (battleType == BattleType.BASEA_ATTACK)) {
			return true;
		}
		return false;
	}

	public boolean canBuffSkill() {
		if (battleType != null && (battleType == BattleType.BUFF || battleType == BattleType.HEAL)) {
			return true;
		}
		return false;
	}

	public boolean canRange() {
		return (rangeType != null && (rangeType != RangeType.SINGLE));
	}

	public float getFadeDuration() {
		return fadeDuration;
	}

	public void setFadeDuration(float fadeDuration) {
		this.fadeDuration = fadeDuration;
	}

	public float getFadeTimer() {
		return fadeTimer;
	}

	public void setFadeTimer(float fadeTimer) {
		this.fadeTimer = fadeTimer;
	}

	public boolean isBlinkEnable() {
		return blinkEnable;
	}

	public void setBlinkEnable(boolean blinkEnable) {
		this.blinkEnable = blinkEnable;
	}

	public float getBlinkInterval() {
		return blinkInterval;
	}

	public void setBlinkInterval(float blinkInterval) {
		this.blinkInterval = blinkInterval;
	}

	public float getBlinkTimer() {
		return blinkTimer;
	}

	public void setBlinkTimer(float blinkTimer) {
		this.blinkTimer = blinkTimer;
	}

	public float getHitStopTimer() {
		return hitStopTimer;
	}

	public void setHitStopTimer(float hitStopTimer) {
		this.hitStopTimer = hitStopTimer;
	}

	public boolean isHitStopped() {
		return hitStopped;
	}

	public void setHitStopped(boolean hitStopped) {
		this.hitStopped = hitStopped;
	}

	public float getProjectileSpeed() {
		return projectileSpeed;
	}

	public void setProjectileSpeed(float projectileSpeed) {
		this.projectileSpeed = projectileSpeed;
	}

	public float getProjectileDist() {
		return projectileDist;
	}

	public void setProjectileDist(float projectileDist) {
		this.projectileDist = projectileDist;
	}

	public float getProjectileProgress() {
		return projectileProgress;
	}

	public void setProjectileProgress(float projectileProgress) {
		this.projectileProgress = projectileProgress;
	}

	public boolean isEffectTriggered() {
		return effectTriggered;
	}

	public void setEffectTriggered(boolean effectTriggered) {
		this.effectTriggered = effectTriggered;
	}

	public float getStateTimer() {
		return stateTimer;
	}

	public void setStateTimer(float stateTimer) {
		this.stateTimer = stateTimer;
	}

	public float getCastDelay() {
		return castDelay;
	}

	public void setCastDelay(float castDelay) {
		this.castDelay = castDelay;
	}

	public float getHitDelay() {
		return hitDelay;
	}

	public void setHitDelay(float hitDelay) {
		this.hitDelay = hitDelay;
	}

	public float getAfterHitDuration() {
		return afterHitDuration;
	}

	public void setAfterHitDuration(float afterHitDuration) {
		this.afterHitDuration = afterHitDuration;
	}

	public float getLastShakeX() {
		return lastShakeX;
	}

	public void setLastShakeX(float lastShakeX) {
		this.lastShakeX = lastShakeX;
	}

	public float getLastShakeY() {
		return lastShakeY;
	}

	public void setLastShakeY(float lastShakeY) {
		this.lastShakeY = lastShakeY;
	}

	public float getTargetAlpha() {
		return targetAlpha;
	}

	public void setTargetAlpha(float targetAlpha) {
		this.targetAlpha = targetAlpha;
	}

	public float getCurrentAlpha() {
		return currentAlpha;
	}

	public void setCurrentAlpha(float currentAlpha) {
		this.currentAlpha = currentAlpha;
	}

	public SkillEffect getEffect() {
		return effect;
	}

	public int getLoopCount() {
		return loopCount;
	}

	public float getAnimSpeed() {
		return animSpeed;
	}

	public SkillState getState() {
		return state;
	}

	public TArray<SkillTriggerEvent> getTriggerEvents() {
		return triggerEvents;
	}

	public void setBattleMap(BattleMap battleMap) {
		this.battleMap = battleMap;
	}

	public void setAnimFinished(boolean animFinished) {
		this.animFinished = animFinished;
	}

	public boolean isCastTriggered() {
		return castTriggered;
	}

	public void setCastTriggered(boolean isCastTriggered) {
		this.castTriggered = isCastTriggered;
	}

	public Animation getBgEffectAnim() {
		return bgEffectAnim;
	}

	public void setBgEffectAnim(Animation bgEffectAnim) {
		this.bgEffectAnim = bgEffectAnim;
	}

	public LTexture getTileEffectTexture() {
		return tileEffectTexture;
	}

	public void setTileEffectTexture(LTexture tileEffectTexture) {
		this.tileEffectTexture = tileEffectTexture;
	}

	public void setBattleType(BattleType battleType) {
		this.battleType = battleType;
	}

	public boolean isRunning() {
		return running;
	}

	public void setRunning(boolean r) {
		this.running = r;
	}

	public void setPosition(XY pos) {
		if (pos != null) {
			position.set(pos);
		}
	}

	public void setPosition(float x, float y) {
		position.set(x, y);
	}

	public Vector2f getPosition() {
		return position;
	}

	public BattleMapObject getCasterObject() {
		return casterObject;
	}

	public void setCasterObject(BattleMapObject casterObject) {
		this.casterObject = casterObject;
	}

	public BattleMapObject getTargetObject() {
		return targetObject;
	}

	public void setTargetObject(BattleMapObject targetObject) {
		this.targetObject = targetObject;
	}

	public LColor getEffectColor() {
		return effColor;
	}

	public void setEffectColor(LColor c) {
		effColor.setColor(c);
	}

	public boolean isShakeEnable() {
		return shakeEnable;
	}

	public void setShakeEnable(boolean shakeEnable) {
		this.shakeEnable = shakeEnable;
	}

	public boolean isLightRangEnable() {
		return lightRangEnable;
	}

	public void setLightRangEnable(boolean lightRangEnable) {
		this.lightRangEnable = lightRangEnable;
	}

	public boolean isAttackAnimComplete() {
		return attackAnimComplete;
	}

	public void setAttackAnimComplete(boolean attackAnimComplete) {
		this.attackAnimComplete = attackAnimComplete;
	}

	public boolean isSkillAnimComplete() {
		return skillAnimComplete;
	}

	public void setSkillAnimComplete(boolean skillAnimComplete) {
		this.skillAnimComplete = skillAnimComplete;
	}

	public boolean isBgAnimComplete() {
		return bgAnimComplete;
	}

	public void setBgAnimComplete(boolean bgAnimComplete) {
		this.bgAnimComplete = bgAnimComplete;
	}

	public LColor getLightTileColor() {
		return lightTileColor;
	}

	public boolean isPlayHalfway() {
		return playHalfway;
	}

	public void setPlayHalfway(boolean playHalfway) {
		this.playHalfway = playHalfway;
	}

	public boolean isAllDirection() {
		return allDirection;
	}

	public void setAllDirection(boolean allDirection) {
		this.allDirection = allDirection;
	}

	public void setAttackAnimOffset(XY pos) {
		setAttackAnimOffset(pos.getX(), pos.getY());
	}

	public void setSkillAnimOffset(XY pos) {
		setSkillAnimOffset(pos.getX(), pos.getY());
	}

	public void setBgAnimOffset(XY pos) {
		setBgAnimOffset(pos.getX(), pos.getY());
	}

	public void setAttackAnimOffset(float x, float y) {
		attackAnimOffset.set(x, y);
	}

	public void setSkillAnimOffset(float x, float y) {
		skillAnimOffset.set(x, y);
	}

	public void setBgAnimOffset(float x, float y) {
		bgAnimOffset.set(x, y);
	}

	public Vector2f getAttackAnimOffset() {
		return attackAnimOffset;
	}

	public Vector2f getSkillAnimOffset() {
		return skillAnimOffset;
	}

	public Vector2f getBgAnimOffset() {
		return bgAnimOffset;
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
		if (bgEffectAnim != null) {
			bgEffectAnim.close();
			bgEffectAnim = null;
		}
		if (tileEffectTexture != null) {
			tileEffectTexture.close();
			tileEffectTexture = null;
		}
	}

}
