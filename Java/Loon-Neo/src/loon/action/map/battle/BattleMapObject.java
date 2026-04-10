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
import loon.ZIndex;
import loon.action.map.Direction;
import loon.action.map.battle.BattleMovementManager.AnimationState;
import loon.action.map.battle.BattleMovementManager.CollisionResponse;
import loon.action.map.battle.BattleMovementManager.MovementEffect;
import loon.action.map.battle.BattleMovementManager.MovementListener;
import loon.action.map.battle.BattleMovementManager.MovementMode;
import loon.action.map.battle.BattleMovementManager.MovementState;
import loon.action.map.battle.BattleSkill.BattleType;
import loon.action.map.battle.BattleType.ObjectState;
import loon.action.map.items.Role;
import loon.action.map.items.RoleEquip;
import loon.action.sprite.ISprite;
import loon.events.GameEvent;
import loon.events.GameEventType;
import loon.geom.PointI;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.utils.Easing;
import loon.utils.ISOUtils;
import loon.utils.ISOUtils.IsoConfig;
import loon.utils.ISOUtils.IsoResult;
import loon.utils.MathUtils;
import loon.utils.ObjectBundle;
import loon.utils.TArray;

/**
 * 战斗地图专属的万能地图对象(所有地图对象相关操作功能全部内置，包括但不限于寻径移动，瓦片适配，动画切换，碰撞检查，队列行进之类，所以叫万能)
 */
public class BattleMapObject extends Role implements LRelease {

	// 对象主状态变更监听
	public static interface ObjectStateListener {

		// 状态变化事件
		void onStateChanged(BattleMapObject obj, ObjectState oldState, ObjectState newState);

		// 移动中
		void onMoving(BattleMapObject obj, float deltaTime);

		// 攻击事件
		void onAttackStart(BattleMapObject obj, float deltaTime);

		void onAttackEnd(BattleMapObject obj, float deltaTime);

		// 技能事件
		void onSkillStart(BattleMapObject obj, float deltaTime);

		void onSkillEnd(BattleMapObject obj, float deltaTime);

		// 防御事件
		void onDefenced(BattleMapObject obj, float deltaTime);

		// 死亡事件
		void onDead(BattleMapObject obj, float deltaTime);

		// 行为合规性判断器只有合规行为才能真正触发
		boolean checkAllowAttack(BattleType eventType, BattleSkill skill, BattleMapObject caster,
				BattleMapObject target);

		boolean checkAllowSkill(BattleType eventType, BattleSkill skill, BattleMapObject caster,
				BattleMapObject target);

	}

	// 移动数据
	public static class MoveData {
		public final int fromX, fromY, toX, toY;
		public final String message;

		public MoveData(int fromX, int fromY, int toX, int toY, String message) {
			this.fromX = fromX;
			this.fromY = fromY;
			this.toX = toX;
			this.toY = toY;
			this.message = message;
		}
	}

	// 攻击数据
	public static class AttackData {
		public final boolean success;
		public final String message;
		public final int damage;
		public final boolean isCrit;

		public AttackData(boolean success, String message, int damage, boolean isCrit) {
			this.success = success;
			this.message = message;
			this.damage = damage;
			this.isCrit = isCrit;
		}
	}

	// 资源变化
	public static class ResourceData {
		public final String type;
		public final BattleMapObject mapObj;
		public final int change;

		public ResourceData(String type, BattleMapObject value, int change) {
			this.type = type;
			this.mapObj = value;
			this.change = change;
		}
	}

	// 技能数据
	public static class SkillData {
		public final String skillName;
		public final String effectType;
		public final int value;

		public SkillData(String skillName, String effectType, int value) {
			this.skillName = skillName;
			this.effectType = effectType;
			this.value = value;
		}
	}

	/** 最大惯性值 */
	public static final float MAX_INERTIA = 0.1f;

	/** 默认缓动函数 */
	private static final Easing DEFAULT_EASING = Easing.TIME_LINEAR;

	protected ObjectStateListener objectStateListener;

	// 初始(当前)坐标
	protected int gridX, gridY;
	// 目标(移动后到达)坐标
	protected int targetX, targetY;

	// 方向与状态
	protected Direction currentDirection = Direction.DOWN;
	protected ObjectState state = ObjectState.IDLE;

	// 渲染与移动基础参数
	protected float renderPriority = 0f;
	private boolean isMoving;
	protected float moveInertia = 0f;
	protected float moveSpeedMultiplier = 1f;

	// 斜角地图配置
	protected final IsoConfig isoConfig;
	private final int charInMapWidth, charInMapHeight;

	// 速度系统
	private float baseSpeed;
	private float currentSpeed;
	private float targetSpeed;

	// 路径系统
	private final TArray<PointI> path = new TArray<PointI>();
	private int currentStep;
	private boolean paused;
	private Easing easing;

	private boolean inCombat = false;

	private float attackProgress;
	private float skillProgress;

	// 坐标缓存对象
	private final Vector2f startPixel = new Vector2f();
	private final Vector2f targetPixel = new Vector2f();
	private final Vector2f movePixel = new Vector2f();
	private final Vector2f moveOffsetPixel = new Vector2f();
	private final Vector2f gxTempResult = new Vector2f();
	private final IsoResult isoTempResult = new IsoResult();

	// 移动模式
	private MovementMode currentMode = MovementMode.WALK;

	// 地图阻挡配置
	private final TArray<PointI> blockedTiles = new TArray<PointI>();
	private final TArray<PointI> allowedTiles = new TArray<PointI>();

	// 地图引用
	private BattleMap battleMap;

	// 移动管理器
	private final BattleMovementManager moveManager;

	private final TArray<BattleMapObject> otherCharacters = new TArray<BattleMapObject>();

	private MovementListener listener;

	// 当前所在瓦片
	private final PointI currentMapTile = new PointI();

	private float moveProgress = 0f;

	private BattleSkill currentSkill;

	public BattleMapObject(IsoConfig cfg, BattleMap map, ISprite sprite, int id, String name, int gx, int gy, int w,
			int h, MovementListener l) {
		this(cfg, map, sprite, id, name, gx, gy, w, h, l, DEFAULT_EASING);
	}

	public BattleMapObject(IsoConfig cfg, BattleMap map, ISprite sprite, int id, String name, int gx, int gy, int w,
			int h, MovementListener l, Easing ease) {
		this(cfg, map, sprite, id, new RoleEquip(0, 0, 0, 0, 0, 0, 0, 0, 0, 0), name, gx, gy, w, h, l, ease);
	}

	public BattleMapObject(IsoConfig cfg, BattleMap map, ISprite sprite, int id, RoleEquip e, String name, int gx,
			int gy, int w, int h, MovementListener l) {
		this(cfg, map, sprite, id, e, name, gx, gy, w, h, l, DEFAULT_EASING);
	}

	public BattleMapObject(IsoConfig cfg, BattleMap map, ISprite sprite, int id, RoleEquip e, String name, int gx,
			int gy, int w, int h, MovementListener l, Easing ease) {
		super(id, e, name);
		// 非空校验
		this.isoConfig = (cfg == null) ? new IsoConfig() : cfg;
		this.battleMap = map;
		this.easing = (ease == null) ? DEFAULT_EASING : ease;

		// 初始化坐标
		this.gridX = MathUtils.max(0, gx);
		this.gridY = MathUtils.max(0, gy);
		this.targetX = gridX;
		this.targetY = gridY;

		// 尺寸校验
		this.charInMapWidth = MathUtils.max(1, w);
		this.charInMapHeight = MathUtils.max(1, h);
		this.listener = l;

		// 初始化移动管理器
		this.moveManager = new BattleMovementManager(listener);
		this.currentMapTile.set(gridX, gridY);

		// 初始化像素坐标
		this.startPixel.set(getTileToScreen(gridX, gridY));
		this.targetPixel.set(startPixel);

		// 基础速度
		this.baseSpeed = MathUtils.max(MAX_INERTIA, 5f);
		this.renderPriority = calculateRenderPriority();

		// 重置移动路径状态与初始移动点为100
		resetPathState(100);

		// 精灵初始化
		if (sprite != null) {
			sprite.setSize(charInMapWidth, charInMapHeight);
			sprite.setLocation(startPixel.x, startPixel.y);
			setRoleObject(sprite);
		}
	}

	public MovementListener getMovementListener() {
		return listener;
	}

	public BattleMapObject setMovementListener(MovementListener l) {
		listener = l;
		return this;
	}

	public Easing getEasing() {
		return easing;
	}

	public BattleMapObject setEasing(Easing e) {
		easing = e;
		if (listener != null) {
			listener.onEasingChanged(this, e);
		}
		return this;
	}

	public int getRenderPriority() {
		return MathUtils.ifloor(renderPriority);
	}

	public int getCharWidth() {
		return MathUtils.iceil(charInMapWidth * getCharScaleX());
	}

	public int getCharHeight() {
		return MathUtils.iceil(charInMapHeight * getCharScaleY());
	}

	public Vector2f getCharCenterInScreenPixel() {
		return new Vector2f(getX() + (charInMapWidth / 2), getY() + (charInMapHeight / 2)).addSelf(moveOffsetPixel);
	}

	public float getCharScaleX() {
		if (_roleObject == null) {
			return 1f;
		}
		return _roleObject.getScaleX();
	}

	public float getCharScaleY() {
		if (_roleObject == null) {
			return 1f;
		}
		return _roleObject.getScaleY();
	}

	private float calculateRenderPriority() {
		Vector2f screenPos = getTileToScreenPosition();
		return screenPos.y + (getLayer() * 100) + (getCharHeight() * isoConfig.heightScale);
	}

	public Vector2f getInterpolatedPosition() {
		if (state == ObjectState.MOVING) {
			float easedProgress = Easing.outCubicEase(moveProgress);
			float interpX = gridX + (targetX - gridX) * easedProgress;
			float interpY = gridY + (targetY - gridY) * easedProgress;
			return getTileToScreen(MathUtils.ifloor(interpX), MathUtils.ifloor(interpY));
		}
		return getTileToScreenPosition();
	}

	public Vector2f getOffsetPixel() {
		return moveOffsetPixel;
	}

	public BattleMapObject setOffsetPixel(XY pos) {
		if (pos != null) {
			moveOffsetPixel.set(pos);
		}
		return this;
	}

	public Vector2f getStartPixel() {
		return startPixel;
	}

	public Vector2f getTargetPixel() {
		return targetPixel;
	}

	public void setStartTile(int gx, int gy) {
		setStartTile(gx, gy, charInMapWidth, charInMapHeight);
	}

	public void setStartTile(int gx, int gy, int cw, int ch) {
		this.gridX = MathUtils.max(0, gx);
		this.gridY = MathUtils.max(0, gy);
		this.startPixel.set(getTileToScreen(gridX, gridY));
		this.currentMapTile.set(gridX, gridY);
		setLocation(startPixel.x, startPixel.y);
	}

	public void setTargetTile(int gx, int gy) {
		setTargetTile(gx, gy, charInMapWidth, charInMapHeight);
	}

	public void setTargetTile(int gx, int gy, int cw, int ch) {
		this.targetX = MathUtils.max(0, gx);
		this.targetY = MathUtils.max(0, gy);
		this.targetPixel.set(getTileToScreen(targetX, targetY));
	}

	public Vector2f getScreenToTile() {
		return getScreenToTile(getX(), getY());
	}

	public Vector2f getScreenToTile(float px, float py) {
		return getScreenToTile(px, py, charInMapWidth, charInMapHeight);
	}

	public Vector2f getScreenToTile(float px, float py, int cw, int ch) {
		return ISOUtils.getScreenToTile(isoConfig, px, py, cw, ch, moveOffsetPixel.x, moveOffsetPixel.y, gxTempResult);
	}

	public Vector2f getTileToScreen(int gx, int gy) {
		return getTileToScreen(gx, gy, charInMapWidth, charInMapHeight);
	}

	public Vector2f getTileToScreen(int gx, int gy, int cw, int ch) {
		return ISOUtils.getTileToScreen(isoConfig, gx, gy, cw, ch, moveOffsetPixel.x, moveOffsetPixel.y, gxTempResult,
				isoTempResult);
	}

	public Vector2f getTileToScreenPosition() {
		return getTileToScreen(gridX, gridY);
	}

	public int getGridX() {
		return gridX;
	}

	public int getGridY() {
		return gridY;
	}

	public BattleMapObject setLayer(int l) {
		if (_roleObject != null) {
			_roleObject.setLayer(l);
		}
		return this;
	}

	public BattleMapObject updateRenderPriorityLayer() {
		renderPriority = calculateRenderPriority();
		int orderZ = MathUtils.ifloor(renderPriority);
		setLayer(orderZ);
		return this;
	}

	public BattleMapObject updateRenderPriorityZ() {
		renderPriority = calculateRenderPriority();
		int orderZ = MathUtils.ifloor(renderPriority);
		if (_roleObject != null) {
			if (_roleObject instanceof ZIndex) {
				orderZ = orderZ - MathUtils.abs(((ZIndex) this._roleObject).getLayer());
			}
		}
		setLayer(-orderZ);
		return this;
	}

	/**
	 * 重置并设置新路径
	 * 
	 * @param newPath
	 */
	public void setResetPath(TArray<PointI> newPath) {
		resetPathState();
		setPath(newPath);
	}

	/**
	 * 设置移动路径
	 * 
	 * @param newPath
	 */
	public void setPath(TArray<PointI> newPath) {
		// 空值，死亡状态拦截
		if (newPath == null || newPath.isEmpty() || state == ObjectState.DEAD) {
			return;
		}

		path.clear();
		path.addAll(newPath);

		// 设置最终目标点
		PointI lastPos = path.last();
		if (lastPos != null) {
			setTargetTile(lastPos.x, lastPos.y);
		}

		// 过滤无效路径
		TArray<PointI> validPath = filterValidPath(path);
		path.clear();
		path.addAll(validPath);

		if (path.isEmpty()) {
			endMovement();
			return;
		}

		// 初始化移动状态
		currentStep = 0;
		paused = false;
		moveProgress = 0f;
		targetPixel.set(getTileToScreen(path.get(0).x, path.get(0).y));

		// 矫正角色初始方向
		if (path.size() > 1) {
			PointI start = path.get(0);
			PointI next = path.get(1);
			Direction newDir = Direction.fromDelta(next.x - start.x, next.y - start.y);
			if (listener != null) {
				listener.onDirectionChanged(this, newDir);
			}
		}

		// 回调通知
		if (listener != null) {
			listener.onPathUpdated(this, path);
		}
		triggerAnimation(AnimationState.WALK);
	}

	public void handleMoveState(float deltaTime) {
		// 死亡/暂停/无路径，直接返回，不予执行
		if (state == ObjectState.DEAD || paused || path.isEmpty()) {
			handleIdleState(deltaTime);
			return;
		}
		// 更新移动管理器
		moveManager.update(deltaTime, this);

		// 传送技能直接执行
		for (MovementState state : moveManager.getActiveStates()) {
			if (state.isTeleport()) {
				performTeleport(this);
				return;
			}
		}

		// 更新速度
		updateSpeed();
		// 平滑惯性移动
		currentSpeed += (targetSpeed - currentSpeed) * MAX_INERTIA;
		moveProgress += currentSpeed * deltaTime;

		float eased = MathUtils.min(easing.apply(moveProgress), 1f);
		movePixel.set(startPixel.x + (targetPixel.x - startPixel.x) * eased,
				startPixel.y + (targetPixel.y - startPixel.y) * eased);

		// 更新像素位置
		setPixelPosition(movePixel);

		// 更新瓦片位置
		updateCurrentMapTile(movePixel.x, movePixel.y);

		// 单步移动完成
		if (moveProgress >= 1f) {
			completeStep();
		}

		if (isMoving) {
			// 更新渲染优先级
			renderPriority = calculateRenderPriority();
		}

		if (objectStateListener != null) {
			objectStateListener.onMoving(this, deltaTime);
		}
	}

	private void handleDeadState(float deltaTime) {
		if (objectStateListener != null) {
			objectStateListener.onDead(this, deltaTime);
		}
	}

	private void handleAttackState(float deltaTime, TArray<BattleMapObject> allObjects) {
		attackProgress += deltaTime * baseSpeed;
		if (attackProgress >= 1.0f) {
			// 执行攻击逻辑
			performAttack(allObjects);
			// 重置攻击状态
			attackProgress = 0f;
		}
		if (objectStateListener != null) {
			objectStateListener.onAttackEnd(this, deltaTime);
		}
	}

	public boolean isInSkillRange(BattleMapObject target) {
		int dx = MathUtils.abs(gridX - target.gridX);
		int dy = MathUtils.abs(gridY - target.gridY);
		int maxRange = currentSkill != null ? currentSkill.rangeRadius : 1;
		return MathUtils.max(dx, dy) <= maxRange;
	}

	private BattleMapObject findSkillTarget(TArray<BattleMapObject> allObjects) {
		for (BattleMapObject obj : allObjects) {
			if (obj != this && obj.state != ObjectState.DEAD && isInSkillRange(obj)) {
				currentDirection = Direction.fromDelta(obj.gridX - gridX, obj.gridY - gridY);
				if (listener != null) {
					listener.onDirectionChanged(this, currentDirection);
				}
				return obj;
			}
		}
		return null;
	}

	private void performAttack(TArray<BattleMapObject> allObjects) {
		if (currentSkill == null) {
			return;
		}
		// 查找指定范围内的目标
		BattleMapObject target = findSkillTarget(allObjects);
		if (target == null || target.state == ObjectState.DEAD) {
			setState(ObjectState.IDLE);
			return;
		}
		// 消耗MP不足判定（如果技能需要）
		if (currentSkill.mpCost > 0 && getMana() < currentSkill.mpCost) {
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.ATTACK, this, target,
						new AttackData(false, "Insufficient Magic", 0, false)));
			}
			setState(ObjectState.IDLE);
			return;
		}

		// 如果不允许攻击则跳过
		if (objectStateListener != null
				&& !objectStateListener.checkAllowAttack(currentSkill.battleType, currentSkill, this, target)) {
			setState(ObjectState.IDLE);
			return;
		}

		// 触发实际技能效果
		currentSkill.castEffect(this, target);

		if (battleMap != null) {
			// 发布命中事件
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.ATTACK_HIT, this, target,
					new AttackData(true, "hit", 0, MathUtils.random() <= currentSkill.critRate)));
			// 发布HP变化事件
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.STATE_CHANGED, target, this,
					new ResourceData("hp", this, target.health)));
		}

		// 检查是否击杀
		if (target.health <= 0) {
			setState(ObjectState.DEAD);
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.DEATH, target, this));
			}
			// 若目标死亡，结束战斗
			setState(ObjectState.DEAD);
		}

	}

	/**
	 * 指定对象开始战斗
	 * 
	 * @param enemy
	 */
	public void startCombat(BattleMapObject enemy) {
		if (!inCombat && enemy != this && enemy.state != ObjectState.DEAD) {
			inCombat = true;
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.COMBAT_START, this, enemy));
			}
		}
	}

	/**
	 * 指定对象结束战斗
	 * 
	 * @param enemy
	 */
	public void endCombat(BattleMapObject enemy) {
		if (inCombat) {
			inCombat = false;
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.COMBAT_END, this, enemy));
			}
		}
	}

	public void endCombat() {
		endCombat(this);
	}

	private void performSkill(TArray<BattleMapObject> allObjects) {
		if (currentSkill == null) {
			return;
		}
		// 检查技能是否冷却完成
		if (!currentSkill.isReady()) {
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.SKILL, this, null,
						new SkillData(currentSkill.name, "Skill on cooldown", 0)));
			}
			setState(ObjectState.IDLE);
			return;
		}
		// 判断MP是否允许释放技能
		if (mana < currentSkill.mpCost) {
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.SKILL, this, null,
						new SkillData(currentSkill.name, "Insufficient Magic", 0)));
			}
			setState(ObjectState.IDLE);
			return;
		}

		// 查找技能对象
		BattleMapObject target = findSkillTarget(allObjects);

		// 如果不允许技能则跳过
		if (objectStateListener != null
				&& !objectStateListener.checkAllowSkill(currentSkill.battleType, currentSkill, this, target)) {
			setState(ObjectState.IDLE);
			return;
		}

		// 根据技能类型执行不同逻辑
		switch (currentSkill.battleType) {
		case HEAL:
		case BUFF:
		case DEBUFF:
			if (currentSkill.battleType == BattleType.HEAL || currentSkill.battleType == BattleType.BUFF
					|| currentSkill.battleType == BattleType.DEBUFF) {
				if (target != null && target.state != ObjectState.DEAD) {
					currentSkill.castEffect(this, target);
				} else {
					currentSkill.castEffect(this, this);
				}
				if (battleMap != null) {
					battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.SKILL, this, null,
							new SkillData(currentSkill.name, "state", 0)));
					battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.STATE_CHANGED, this, null,
							new ResourceData("state", this, health)));
				}
			}
			break;
		case RANGE:
		case MELEE:
			// 远程近战技能攻击
			if (target != null && target.state != ObjectState.DEAD) {
				if (currentSkill.battleType == BattleType.RANGE || currentSkill.battleType == BattleType.MELEE) {
					// 实际调用技能效果
					currentSkill.castEffect(this, target);
					if (battleMap != null) {
						battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.ATTACK_HIT, this, target,
								new AttackData(true, "hit", 0, MathUtils.random() <= currentSkill.critRate)));
						battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.STATE_CHANGED, target, null,
								new ResourceData("state", this, target.health)));
					}
					if (target.health <= 0) {
						setState(ObjectState.DEAD);
						if (battleMap != null) {
							battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.DEATH, target, this));
						}
					}
				}
			}
			break;
		default:
			break;
		}
	}

	private void handleSkillState(float deltaTime, TArray<BattleMapObject> allObjects) {
		skillProgress += deltaTime * baseSpeed;
		if (skillProgress >= 1.0f) {
			// 执行技能逻辑
			performSkill(allObjects);
			// 重置技能状态
			skillProgress = 0f;
		}
		if (objectStateListener != null) {
			objectStateListener.onSkillEnd(this, deltaTime);
		}
	}

	private void handlePrepareAttack(float deltaTime) {
		attackProgress += deltaTime * baseSpeed;
		if (attackProgress >= 1.0f) {
			attackProgress = 0f;
			setState(ObjectState.PREPARE_ATTACK);
			if (objectStateListener != null) {
				objectStateListener.onAttackStart(this, deltaTime);
			}
		}
	}

	private void handlePrepareSkill(float deltaTime) {
		skillProgress += deltaTime * baseSpeed;
		if (skillProgress >= 1.0f) {
			skillProgress = 0f;
			setState(ObjectState.PREPARE_SKILL);
			if (objectStateListener != null) {
				objectStateListener.onSkillStart(this, deltaTime);
			}
		}
	}

	public void update(float deltaTime) {
		switch (state) {
		case IDLE:
			handleIdleState(deltaTime);
			break;
		case MOVING:
			handleMoveState(deltaTime);
			break;
		case DEFENSEING:
			handleDefenceState(deltaTime);
			break;
		case ATTACKING:
			handleAttackState(deltaTime, otherCharacters);
			break;
		case SKILL:
			handleSkillState(deltaTime, otherCharacters);
			break;
		case DEAD:
			handleDeadState(deltaTime);
			break;
		case PREPARE_ATTACK:
			handlePrepareAttack(deltaTime);
			break;
		case PREPARE_SKILL:
			handlePrepareSkill(deltaTime);
			break;
		default:
			setState(ObjectState.IDLE);
			break;
		}
	}

	public void updateCurrentMapTile() {
		updateCurrentMapTile(getX(), getY());
	}

	public void updateCurrentMapTile(float x, float y) {
		updateCurrentMapTile(x, y, charInMapWidth, charInMapHeight);
	}

	public void updateCurrentMapTile(float x, float y, int charW, int charH) {
		Vector2f p = getScreenToTile(x, y, charW, charH);
		this.gridX = p.x();
		this.gridY = p.y();
		this.currentMapTile.set(gridX, gridY);
	}

	/**
	 * 速度计算（移动模式+技能+地形倍率）
	 */
	private void updateSpeed() {
		float multiplier = 1f;

		// 特殊状态速度倍率
		for (MovementState skill : moveManager.getActiveStates()) {
			multiplier = MathUtils.max(multiplier, skill.getSpeedMultiplier());
		}

		// 移动模式倍率
		switch (currentMode) {
		case RUN:
			multiplier *= 2f;
			break;
		case SNEAK:
			multiplier *= 0.5f;
			break;
		case CHARGE:
			multiplier *= 2.5f;
			break;
		default:
			multiplier = 1f;
			break;
		}

		// 地形速度倍率
		if (battleMap != null) {
			BattleTile tile = battleMap.getMapTile(gridX, gridY);
			if (tile != null) {
				multiplier *= tile.getTileType().getMoveSpeedMultiplier();
			}
		}

		targetSpeed = baseSpeed * multiplier;
	}

	/**
	 * 传送执行
	 * 
	 * @param obj
	 */
	private void performTeleport(BattleMapObject obj) {
		if (path.isEmpty()) {
			return;
		}
		PointI end = path.peek();
		targetPixel.set(getTileToScreen(end.x, end.y));
		obj.setPixelPosition(targetPixel);
		obj.setCurrentMapTile(end);
		currentStep = path.size();
		finishPath();
	}

	/**
	 * 获取最大可移动步数
	 */
	public int getMaxReachableSteps() {
		int steps = 0;
		int tempPoints = actionPoints;
		for (PointI tile : path) {
			int cost = getTileCost(tile);
			if (tempPoints < cost) {
				break;
			}
			tempPoints -= cost;
			steps++;
		}
		return steps;
	}

	/**
	 * 追加路径
	 * 
	 * @param newPath
	 */
	public void appendPath(TArray<PointI> newPath) {
		if (newPath == null || newPath.isEmpty() || state == ObjectState.DEAD) {
			return;
		}
		TArray<PointI> valid = filterValidPath(newPath);
		path.addAll(valid);

		if (listener != null) {
			listener.onPathUpdated(this, path);
		}
	}

	/**
	 * 模拟未来位置
	 * 
	 * @param steps
	 * @return
	 */
	public Vector2f simulateFuturePosition(int steps) {
		if (path.isEmpty()) {
			return getPixelPosition();
		}
		int idx = MathUtils.clamp(currentStep + steps, 0, path.size() - 1);
		return getTileToScreen(path.get(idx).x, path.get(idx).y);
	}

	/**
	 * 预览全路径（屏幕坐标）
	 */
	public TArray<Vector2f> previewScreenFullPath() {
		TArray<Vector2f> result = new TArray<Vector2f>();
		for (PointI p : filterValidPath(path)) {
			result.add(new Vector2f(getTileToScreen(p.x, p.y)));
		}
		return result;
	}

	/**
	 * 预览全路径（瓦片坐标）
	 * 
	 * @return
	 */
	public TArray<PointI> previewTileFullPath() {
		TArray<PointI> result = new TArray<PointI>();
		for (PointI p : filterValidPath(path)) {
			result.add(new PointI(getTileToScreen(p.x, p.y)));
		}
		return result;
	}

	/**
	 * 清空路径
	 */
	public void clearPath() {
		path.clear();
		paused = true;
		endMovement();
	}

	public void addMoveEffect(MovementEffect effect) {
		if (effect != null) {
			moveManager.addEffect(effect);
		}
	}

	public void setBlockedTiles(TArray<PointI> blocked) {
		blockedTiles.clear();
		if (blocked != null) {
			blockedTiles.addAll(blocked);
		}
	}

	public void setAllowedTiles(TArray<PointI> allowed) {
		allowedTiles.clear();
		if (allowed != null) {
			allowedTiles.addAll(allowed);
		}
	}

	public void addCharacter(BattleMapObject o) {
		if (o != null && !otherCharacters.contains(o)) {
			otherCharacters.add(o);
		}
	}

	public void removeCharacter(BattleMapObject o) {
		otherCharacters.remove(o);
	}

	public void clearCharacter() {
		otherCharacters.clear();
	}

	public TArray<PointI> getPath() {
		return new TArray<>(path);
	}

	public int getRemainingSteps() {
		return MathUtils.max(0, path.size() - currentStep);
	}

	public boolean isPaused() {
		return paused;
	}

	public PointI getCurrentMapTile() {
		return new PointI(currentMapTile);
	}

	public void setCurrentMapTile(PointI tile) {
		if (tile != null) {
			this.currentMapTile.set(tile);
			this.gridX = tile.x;
			this.gridY = tile.y;
		}
	}

	public Vector2f getPixelPosition() {
		return new Vector2f(getX(), getY());
	}

	public void setPixelPosition(Vector2f pos) {
		if (pos != null) {
			setLocation(pos.x, pos.y);
		}
	}

	public int getCharacterId() {
		return getID();
	}

	public BattleMapObject pause() {
		paused = true;
		if (listener != null) {
			listener.onPathInterrupted(this);
		}
		return this;
	}

	public BattleMapObject resume() {
		paused = false;
		if (listener != null) {
			listener.onPathResumed(this);
		}
		return this;
	}

	public void setMoving(boolean moving) {
		this.isMoving = moving;
	}

	private void triggerAnimation(AnimationState state) {
		if (listener != null && state != null) {
			listener.onAnimationStateChanged(this, state.name());
		}
	}

	/**
	 * 扣除移动点数
	 * 
	 * @param tile
	 */
	private void deductMovementCost(PointI tile) {
		int cost = 0;
		if (battleMap != null) {
			BattleTile battleTile = battleMap.getMapTile(tile.x, tile.y);
			cost = battleTile == null ? 0 : (int) battleTile.getPathCost();
		}
		actionPoints = MathUtils.max(0, actionPoints - cost);
		if (listener != null) {
			listener.onTerrainCostDeducted(this, cost, actionPoints);
			listener.onMovementPointChanged(this, actionPoints);
		}
		if (actionPoints <= 0) {
			pause();
		}
	}

	/**
	 * 应用地形效果
	 * 
	 * @param tile
	 */
	private void applyTerrainEffects(PointI tile) {
		if (battleMap == null || tile == null) {
			return;
		}
		BattleTile battleTile = battleMap.getMapTile(tile.x, tile.y);
		if (battleTile == null || listener == null) {
			return;
		}
		BattleTileType tileType = battleTile.getTileType();
		listener.onTerrainEffectApplied(this, tileType.getName(), tileType);
		targetSpeed = baseSpeed * tileType.getMoveSpeedMultiplier();
	}

	/**
	 * 碰撞检测
	 */
	public CollisionResponse checkCollision() {
		if (otherCharacters.isEmpty()) {
			return CollisionResponse.CONTINUE;
		}
		PointI selfTile = getCurrentMapTile();
		for (BattleMapObject other : otherCharacters) {
			if (this == other || other == null) {
				continue;
			}
			if (selfTile.equals(other.getCurrentMapTile())) {
				if (listener != null) {
					listener.onCollision(this, other, CollisionResponse.STOP);
				}
				return CollisionResponse.STOP;
			}
		}
		return CollisionResponse.CONTINUE;
	}

	/**
	 * 碰撞处理
	 */
	private boolean handleCollision(CollisionResponse response) {
		if (response == CollisionResponse.CONTINUE) {
			return false;
		}
		paused = true;
		triggerAnimation(AnimationState.IDLE);
		// 后退处理
		if (response == CollisionResponse.BACKWARD && currentStep > 0) {
			currentStep = MathUtils.max(0, currentStep - 1);
			targetPixel.set(getTileToScreen(path.get(currentStep).x, path.get(currentStep).y));
			setPixelPosition(targetPixel);
		}
		return true;
	}

	/**
	 * 路径完成
	 */
	private void finishPath() {
		endMovement();
		if (listener != null) {
			listener.onPathCompleted(this);
		}
	}

	/**
	 * 更新朝向
	 */
	private void updateDirection() {
		if (currentStep <= 0 || currentStep >= path.size()) {
			return;
		}
		PointI prev = path.get(currentStep - 1);
		PointI curr = path.get(currentStep);
		if (prev == null || curr == null) {
			return;
		}
		Direction newDir = Direction.fromDelta(curr.x - prev.x, curr.y - prev.y);
		if (newDir != null && newDir != currentDirection) {
			currentDirection = newDir;
			if (listener != null) {
				listener.onDirectionChanged(this, currentDirection);
			}
		}
	}

	/**
	 * 移动模式设置
	 * 
	 * @param newMode
	 */
	public void setMovementMode(MovementMode newMode) {
		if (newMode == null || state == ObjectState.DEAD) {
			return;
		}
		MovementMode old = this.currentMode;
		this.currentMode = newMode;
		if (listener != null) {
			listener.onMovementModeChanged(this, old, newMode);
		}
	}

	public ObjectBundle getSyncPacket() {
		ObjectBundle packet = new ObjectBundle();
		packet.put("speed", currentSpeed);
		packet.put("step", currentStep);
		packet.put("paused", paused);
		packet.put("mode", currentMode);
		packet.put("points", actionPoints);
		packet.put("x", startPixel.x);
		packet.put("y", startPixel.y);
		return packet;
	}

	public void applySyncPacket(ObjectBundle packet) {
		if (packet == null) {
			return;
		}
		this.currentSpeed = MathUtils.max(MAX_INERTIA, packet.getFloat("speed", baseSpeed));
		this.currentStep = MathUtils.max(0, packet.getInt("step", 0));
		this.paused = packet.getBool("paused", false);
		this.currentMode = (MovementMode) packet.get("mode", MovementMode.WALK);
		this.actionPoints = MathUtils.max(0, packet.getInt("points", 0));
		float x = packet.getFloat("x", getX());
		float y = packet.getFloat("y", getY());
		setPixelPosition(new Vector2f(x, y));
	}

	/**
	 * 单步移动完成
	 */
	private void completeStep() {
		if (currentStep >= path.size()) {
			finishPath();
			return;
		}

		PointI tile = path.get(currentStep);
		setPixelPosition(targetPixel);
		setCurrentMapTile(tile);

		// 事件回调
		if (listener != null) {
			listener.onStepReached(this, tile.x, tile.y);
			listener.onTileEntered(this, tile.x, tile.y);
		}

		// 消耗移动力 + 地形效果 + 碰撞检测
		deductMovementCost(tile);
		applyTerrainEffects(tile);
		if (handleCollision(checkCollision())) {
			return;
		}

		// 更新方向
		updateDirection();

		// 进入下一步
		currentStep++;
		moveProgress = 0f;

		if (currentStep >= path.size()) {
			finishPath();
		} else {
			startPixel.set(targetPixel);
			targetPixel.set(getTileToScreen(path.get(currentStep).x, path.get(currentStep).y));
		}
	}

	/**
	 * 判断是否可移动到目标瓦片
	 */
	public boolean canMoveTo(PointI tile) {
		if (tile == null || state == ObjectState.DEAD) {
			return false;
		}
		// 技能强制可移动
		for (MovementState state : moveManager.getActiveStates()) {
			if (state.canOverrideBlocked(tile)) {
				return true;
			}
		}
		// 地图边界检查
		if (battleMap != null) {
			BattleTile battleTile = battleMap.getMapTile(tile.x, tile.y);
			if (battleTile == null) {
				return false;
			}
			if (!battleTile.isPassable() && !isFlying()) {
				return false;
			}
		}
		// 自定义阻挡检查
		return !blockedTiles.contains(tile) || allowedTiles.contains(tile);
	}

	/**
	 * 过滤路径，判定是否合规
	 * 
	 * @param paths
	 * @return
	 */
	private TArray<PointI> filterValidPath(TArray<PointI> paths) {
		TArray<PointI> valid = new TArray<PointI>();
		if (paths == null || paths.isEmpty()) {
			return valid;
		}
		int tempPoints = actionPoints;
		for (PointI tile : paths) {
			if (!canMoveTo(tile)) {
				break;
			}
			int cost = getTileCost(tile);
			if (tempPoints < cost) {
				break;
			}
			tempPoints -= cost;
			valid.add(tile);
		}
		return valid;
	}

	/**
	 * 获取瓦片消耗
	 * 
	 * @param tile
	 * @return
	 */
	private int getTileCost(PointI tile) {
		if (battleMap == null || tile == null) {
			return 0;
		}
		BattleTile battleTile = battleMap.getMapTile(tile.x, tile.y);
		return battleTile == null ? 0 : MathUtils.max(0, (int) battleTile.getPathCost());
	}

	public int getMaxMovementPoints() {
		return movePoints;
	}

	public void setMaxMovementPoints(int points) {
		this.movePoints = MathUtils.max(0, points);
	}

	@Override
	public BattleMapObject die() {
		super.die();
		setState(ObjectState.DEAD);
		return this;
	}

	public void resetPathState() {
		resetPathState(movePoints);
	}

	public void resetPathState(int points) {
		currentStep = 0;
		moveProgress = 0f;
		paused = false;
		movePoints = MathUtils.max(0, points);
		actionPoints = movePoints;
		path.clear();
	}

	public void setRemainingMovementPoints(int points) {
		this.actionPoints = MathUtils.clamp(points, 0, movePoints);
	}

	public int getRemainingMovementPoints() {
		return actionPoints;
	}

	public Direction getDirection() {
		return currentDirection;
	}

	public RoleEquip getRoleEquip() {
		return getInfo();
	}

	public ObjectState getState() {
		return state;
	}

	public void setState(ObjectState newState) {
		if (newState == null || newState == state) {
			return;
		}
		if (objectStateListener != null) {
			objectStateListener.onStateChanged(this, state, newState);
		}
		this.state = newState;
		this.isMoving = (state == ObjectState.MOVING);
		this.isDead = (state == ObjectState.DEAD);
		if (isDead) {
			endCombat();
		}
		// 死亡状态强制停止移动
		if (state == ObjectState.DEAD) {
			clearPath();
		}
	}

	public void paint(GLEx g, float deltaTime, float posX, float posY) {
		if (isVisible()) {
			update(deltaTime);
			if (currentSkill != null) {
				currentSkill.updateSkill(deltaTime);
				currentSkill.drawSkillEffect(g, deltaTime, posX, posY);
			}
		}
	}

	public boolean isMoving() {
		return isMoving;
	}

	/**
	 * 待机状态
	 * 
	 * @param deltaTime
	 */
	protected void handleIdleState(float deltaTime) {
		moveInertia = MathUtils.max(0, moveInertia - deltaTime * 2);
		if (!path.isEmpty() && state != ObjectState.DEAD) {
			startMoving();
		} else if (path.isEmpty()) {
			endMovement();
		}
	}

	/**
	 * 防御状态
	 * 
	 * @param deltaTime
	 */
	protected void handleDefenceState(float deltaTime) {
		if (objectStateListener != null) {
			objectStateListener.onDefenced(this, deltaTime);
		}
	}

	/**
	 * 开始移动(死亡状态默认不可触发)
	 */
	protected void startMoving() {
		if (state == ObjectState.DEAD) {
			return;
		}
		setState(ObjectState.MOVING);
		isMoving = true;
		moveProgress = 0f;
	}

	/**
	 * 结束移动
	 */
	protected void endMovement() {
		paused = true;
		setState(ObjectState.IDLE);
		isMoving = false;
		moveProgress = 0f;
		targetX = gridX;
		targetY = gridY;
		if (listener != null) {
			listener.onPathCompleted(this);
		}
		triggerAnimation(AnimationState.ARRIVED);
	}

	/**
	 * 位置通行性检查
	 */
	public boolean isPositionPassable(int x, int y) {
		if (battleMap == null) {
			return false;
		}
		BattleTile tile = battleMap.getMapTile(x, y);
		return (tile != null && (tile.isPassable() || isFlying()));
	}

	public BattleMap getBattleMap() {
		return battleMap;
	}

	public void setBattleMap(BattleMap battleMap) {
		this.battleMap = battleMap;
	}

	public void setCurrentSkill(BattleSkill skill) {
		this.currentSkill = skill;
		if (currentSkill != null) {
			currentSkill.setBattleMap(battleMap);
		}
	}

	public BattleSkill getCurrentSkill() {
		return currentSkill;
	}

	public float getBaseSpeed() {
		return baseSpeed;
	}

	public void setBaseSpeed(float baseSpeed) {
		this.baseSpeed = MathUtils.max(MAX_INERTIA, baseSpeed);
		if (listener != null) {
			listener.onSpeedChanged(this, baseSpeed);
		}
	}

	public float getTargetSpeed() {
		return targetSpeed;
	}

	public void setTargetSpeed(float targetSpeed) {
		this.targetSpeed = targetSpeed;
	}

	public float getSkillProgress() {
		return skillProgress;
	}

	public void setSkillProgress(float skillProgress) {
		this.skillProgress = skillProgress;
	}

	public MovementListener getListener() {
		return listener;
	}

	public void setListener(MovementListener listener) {
		this.listener = listener;
	}

	public int getTargetX() {
		return targetX;
	}

	public int getTargetY() {
		return targetY;
	}

	public Direction getCurrentDirection() {
		return currentDirection;
	}

	public float getCurrentSpeed() {
		return currentSpeed;
	}

	public int getCurrentStep() {
		return currentStep;
	}

	public Vector2f getMovePixel() {
		return movePixel;
	}

	public Vector2f getMoveOffsetPixel() {
		return moveOffsetPixel;
	}

	public MovementMode getCurrentMode() {
		return currentMode;
	}

	public float getMoveProgress() {
		return moveProgress;
	}

	public ObjectStateListener getObjectStateListener() {
		return objectStateListener;
	}

	public void setObjectStateListener(ObjectStateListener l) {
		this.objectStateListener = l;
	}

	@Override
	public void close() {
		if (_roleObject != null) {
			_roleObject.close();
		}
		if (currentSkill != null) {
			currentSkill.close();
			currentSkill = null;
		}
	}

}
