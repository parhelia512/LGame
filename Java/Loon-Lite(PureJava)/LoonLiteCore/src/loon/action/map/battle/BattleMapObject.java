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
import loon.action.map.TileIsoHighlighter.EffectType;
import loon.action.map.battle.BattleMovementManager.AnimationState;
import loon.action.map.battle.BattleMovementManager.CollisionResponse;
import loon.action.map.battle.BattleMovementManager.MovementEffect;
import loon.action.map.battle.BattleMovementManager.MovementListener;
import loon.action.map.battle.BattleMovementManager.MovementMode;
import loon.action.map.battle.BattleMovementManager.MovementState;
import loon.action.map.battle.BattleSkill.BattleType;
import loon.action.map.battle.BattleType.ObjectState;
import loon.action.map.items.Item;
import loon.action.map.items.ItemInfo;
import loon.action.map.items.Role;
import loon.action.map.items.RoleEquip;
import loon.action.map.items.RoleValue;
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

		// 道具事件
		void onItemUseStart(BattleMapObject obj, float deltaTime);

		void onItemUseEnd(BattleMapObject obj, float deltaTime);

		void onHit(BattleMapObject obj, int damage, boolean isCrit);

		void onKnockBack(BattleMapObject obj, int fromX, int fromY);

		// 行为合规性判断器只有合规行为才能真正触发
		boolean checkAllowAttack(BattleType eventType, BattleSkill skill, BattleMapObject caster,
				BattleMapObject target);

		boolean checkAllowSkill(BattleType eventType, BattleSkill skill, BattleMapObject caster,
				BattleMapObject target);

		boolean checkAllowUseItem(Item<ItemInfo> item, BattleMapObject user, BattleMapObject target);

		void useItem(Item<ItemInfo> item, BattleMapObject user, BattleMapObject target);
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

	public static class ItemData {
		public final String itemName;
		public final String effect;
		public final int value;

		public ItemData(String itemName, String effect, int value) {
			this.itemName = itemName;
			this.effect = effect;
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

	// 动作进度
	private float attackProgress;
	private float skillProgress;
	private float itemProgress;

	// 技能与道具系统
	private TArray<BattleSkill> skills = new TArray<BattleSkill>();
	private Item<ItemInfo> currentItem;
	private TArray<Item<ItemInfo>> items = new TArray<Item<ItemInfo>>();

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
	protected final PointI currentMapTile = new PointI();

	private float moveProgress = 0f;

	// 绘制移动路径
	private boolean drawPath = true;

	private BattleSkill currentSkill;
	private BattleMapObject lastAttacker;
	private boolean taunt;

	// 键盘移动控制
	private float keyDelay = 0.25f;

	private float keyMoveTimer;

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

	private void handleAttackState(float deltaTime, TArray<BattleMapObject> allObjects) {
		attackProgress += deltaTime * baseSpeed;
		// 只有技能真正触发后，才允许结束攻击状态
		if (currentSkill != null && currentSkill.isCastTriggered()) {
			performAttack(allObjects);
			attackProgress = 0f;
			if (objectStateListener != null) {
				objectStateListener.onAttackEnd(this, deltaTime);
			}
			setState(ObjectState.IDLE);
			currentSkill.resetCast();
		}
		if (objectStateListener != null) {
			objectStateListener.onAttackStart(this, deltaTime);
		}
	}

	private void handleSkillState(float deltaTime, TArray<BattleMapObject> allObjects) {
		skillProgress += deltaTime * baseSpeed;
		if (currentSkill != null && currentSkill.isCastTriggered()) {
			performSkill(allObjects);
			skillProgress = 0f;
			if (objectStateListener != null) {
				objectStateListener.onSkillEnd(this, deltaTime);
			}
			setState(ObjectState.IDLE);
			currentSkill.resetCast();
		}
		if (objectStateListener != null) {
			objectStateListener.onSkillStart(this, deltaTime);
		}
	}

	/**
	 * 使用道具状态
	 * 
	 * @param deltaTime
	 */
	private void handleItemState(float deltaTime) {
		itemProgress += deltaTime * baseSpeed;
		if (currentItem != null && itemProgress > 1.0f && currentItem.isUseTriggered()) {
			performItemUse();
			itemProgress = 0f;
			if (objectStateListener != null) {
				objectStateListener.onItemUseEnd(this, deltaTime);
			}
			setState(ObjectState.IDLE);
			currentItem.resetUse();
		}
		if (objectStateListener != null) {
			objectStateListener.onItemUseStart(this, deltaTime);
		}
	}

	public void useItem(Item<ItemInfo> item, BattleMapObject target) {
		if (state != ObjectState.IDLE || currentItem != null) {
			return;
		}
		if (item == null) {
			return;
		}
		currentItem = item;
		setState(ObjectState.USING_ITEM);

		if (objectStateListener != null) {
			objectStateListener.onItemUseStart(this, 0);
		}
	}

	private void performItemUse() {
		if (currentItem == null) {
			return;
		}
		if (objectStateListener != null) {
			objectStateListener.useItem(currentItem, this, lastAttacker);
		}
		currentItem = null;
	}

	public void updateKeyboardMove(float deltaTime, boolean up, boolean down, boolean left, boolean right) {
		keyMoveTimer -= deltaTime;
		if (keyMoveTimer > 0 || state != ObjectState.IDLE) {
			return;
		}
		int dx = 0, dy = 0;
		if (up) {
			dy = -1;
		} else if (down) {
			dy = 1;
		} else if (left) {
			dx = -1;
		} else if (right) {
			dx = 1;
		}
		if (dx != 0 || dy != 0) {
			int tx = gridX + dx;
			int ty = gridY + dy;
			if (canMoveTo(new PointI(tx, ty))) {
				moveToGrid(tx, ty);
				keyMoveTimer = keyDelay;
			}
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
		if (listener != null)
			listener.onEasingChanged(this, e);
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
		return new Vector2f(getX() + (charInMapWidth / 2f), getY() + (charInMapHeight / 2f)).addSelf(moveOffsetPixel);
	}

	public float getCharScaleX() {
		return _roleObject == null ? 1f : _roleObject.getScaleX();
	}

	public float getCharScaleY() {
		return _roleObject == null ? 1f : _roleObject.getScaleY();
	}

	private float calculateRenderPriority() {
		Vector2f screenPos = getTileToScreenPosition();
		return screenPos.y + (getLayer() * 100) + (getCharHeight() * isoConfig.heightScale);
	}

	public Vector2f getInterpolatedPosition() {
		if (state == ObjectState.MOVING) {
			float eased = Easing.outCubicEase(moveProgress);
			float ix = gridX + (targetX - gridX) * eased;
			float iy = gridY + (targetY - gridY) * eased;
			return getTileToScreen(MathUtils.ifloor(ix), MathUtils.ifloor(iy));
		}
		return getTileToScreenPosition();
	}

	public Vector2f getOffsetPixel() {
		return moveOffsetPixel;
	}

	public BattleMapObject setOffsetPixel(XY pos) {
		if (pos != null)
			moveOffsetPixel.set(pos);
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
		gridX = MathUtils.max(0, gx);
		gridY = MathUtils.max(0, gy);
		startPixel.set(getTileToScreen(gridX, gridY));
		currentMapTile.set(gridX, gridY);
		setLocation(startPixel.x, startPixel.y);
	}

	public void setTargetTile(int gx, int gy) {
		setTargetTile(gx, gy, charInMapWidth, charInMapHeight);
	}

	public void setTargetTile(int gx, int gy, int cw, int ch) {
		targetX = MathUtils.max(0, gx);
		targetY = MathUtils.max(0, gy);
		targetPixel.set(getTileToScreen(targetX, targetY));
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
		setLayer(MathUtils.ifloor(renderPriority));
		return this;
	}

	public BattleMapObject updateRenderPriorityZ() {
		renderPriority = calculateRenderPriority();
		int z = MathUtils.ifloor(renderPriority);
		if (_roleObject instanceof ZIndex) {
			z -= MathUtils.abs(((ZIndex) _roleObject).getLayer());
		}
		setLayer(-z);
		return this;
	}

	@Override
	public boolean hasAdvantageOver(RoleValue target) {
		if (UnitType.hasType(getUnitType(), UnitType.NAVAL)) {
			if (battleMap != null) {
				BattleTile tile = battleMap.getMapTile(gridX, gridY);
				if (tile != null) {
					BattleTileType tileType = tile.getTileType();
					return super.hasAdvantageOver(target) && (tileType == BattleTileType.SEA
							|| tileType == BattleTileType.COAST || tileType == BattleTileType.RIVER);
				} else {
					return false;
				}
			}
		}
		return super.hasAdvantageOver(target);
	}

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
		PointI last = path.last();
		if (last != null) {
			setTargetTile(last.x, last.y);
		}
		// 过滤无效路径
		TArray<PointI> valid = filterValidPath(path);
		path.clear();
		path.addAll(valid);
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
			PointI s = path.get(0), n = path.get(1);
			Direction d = Direction.fromDelta(n.x - s.x, n.y - s.y);
			if (listener != null) {
				listener.onDirectionChanged(this, d);
			}
		}
		// 回调通知
		if (listener != null) {
			listener.onPathUpdated(this, path);
		}
		triggerAnimation(AnimationState.WALK);
	}

	public void handleMoveState(float deltaTime) {
		if (state == ObjectState.DEAD || paused || path.isEmpty()) {
			handleIdleState(deltaTime);
			return;
		}
		// 更新移动管理器
		moveManager.update(deltaTime, this);
		for (MovementState st : moveManager.getActiveStates()) {
			if (st.isTeleport()) {
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

	public boolean isInSkillRange(BattleMapObject target) {
		int dx = MathUtils.abs(gridX - target.gridX);
		int dy = MathUtils.abs(gridY - target.gridY);
		int r = currentSkill != null ? currentSkill.rangeRadius : 1;
		return MathUtils.max(dx, dy) <= r;
	}

	private BattleMapObject findSkillTarget(TArray<BattleMapObject> allObjects) {
		for (BattleMapObject o : allObjects) {
			if (o != this && o.state != ObjectState.DEAD && isInSkillRange(o)) {
				currentDirection = Direction.fromDelta(o.gridX - gridX, o.gridY - gridY);
				if (listener != null)
					listener.onDirectionChanged(this, currentDirection);
				return o;
			}
		}
		return null;
	}

	public void castAttack(PointI grid) {
		if (grid != null) {
			castAttack(grid.x, grid.y);
		}
	}

	public void castAttack(int gx, int gy) {
		if (battleMap == null || currentSkill == null) {
			return;
		}
		if (actionPoints > 0 && actionPoints < currentSkill.actionPointCost) {
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.ATTACK, this, null,
					new AttackData(false, "Insufficient AP", 0, false)));
			return;
		}
		if (objectStateListener != null
				&& !objectStateListener.checkAllowAttack(currentSkill.battleType, currentSkill, this, null)) {
			return;
		}
		setState(ObjectState.ATTACKING);
		currentSkill.castTileEffect(gx, gy);
	}

	private void performAttack(TArray<BattleMapObject> allObjects) {
		if (currentSkill == null) {
			return;
		}
		// 查找指定范围内的目标
		BattleMapObject t = findSkillTarget(allObjects);
		if (t == null || t.state == ObjectState.DEAD) {
			setState(ObjectState.IDLE);
			return;
		}
		if (actionPoints > 0 && actionPoints < currentSkill.actionPointCost) {
			return;
		}
		if (objectStateListener != null
				&& !objectStateListener.checkAllowAttack(currentSkill.battleType, currentSkill, this, t)) {
			return;
		}
		currentSkill.castEffect(this, t);
		if (battleMap != null) {
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.ATTACK_HIT, this, t,
					new AttackData(true, "hit", 0, MathUtils.random() <= currentSkill.critRate)));
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.STATE_CHANGED, t, this,
					new ResourceData("hp", this, t.health)));
		}
		if (t.health <= 0) {
			t.setState(ObjectState.DEAD);
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.DEATH, t, this));
			}
		} else {
			lastAttacker = t;
		}
	}

	public void startCombat(BattleMapObject enemy) {
		if (!inCombat && enemy != this && enemy.state != ObjectState.DEAD) {
			inCombat = true;
			if (battleMap != null) {
				battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.COMBAT_START, this, enemy));
			}
		}
	}

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

	public void castSkillPixel(Vector2f pos) {
		if (pos != null) {
			castSkillPixel(pos.x, pos.y);
		}
	}

	public void castSkillPixel(float x, float y) {
		if (battleMap != null) {
			Vector2f pos = battleMap.findTileXY(x, y);
			if (pos != null && battleMap.inTileGrid(pos.x(), pos.y())) {
				castSkillTile(pos.x(), pos.y());
			}
		}
	}

	public void castSkillTile(PointI grid) {
		if (grid != null) {
			castSkillTile(grid.x, grid.y);
		}
	}

	public void castSkillTile(int gx, int gy) {
		if (battleMap == null || currentSkill == null) {
			return;
		}
		if (!currentSkill.isReady()) {
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.SKILL, this, null,
					new SkillData(currentSkill.name, "cooldown", 0)));
			return;
		}
		if (mana < currentSkill.mpCost) {
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.SKILL, this, null,
					new SkillData(currentSkill.name, "no mana", 0)));
			return;
		}
		if (objectStateListener != null
				&& !objectStateListener.checkAllowSkill(currentSkill.battleType, currentSkill, this, null)) {
			return;
		}
		setState(ObjectState.SKILL);
		currentSkill.castTileEffect(gx, gy);
	}

	public void castSkill(BattleMapObject target) {
		if (currentSkill == null) {
			return;
		}
		if (!currentSkill.isReady() || mana < currentSkill.mpCost) {
			return;
		}
		if (objectStateListener != null
				&& !objectStateListener.checkAllowSkill(currentSkill.battleType, currentSkill, this, target)) {
			return;
		}
		setState(ObjectState.SKILL);
	}

	private void performSkill(TArray<BattleMapObject> allObjects) {
		if (currentSkill == null) {
			return;
		}
		BattleMapObject t = findSkillTarget(allObjects);
		if (t == null) {
			return;
		}
		if (currentSkill.battleType == BattleType.HEAL || currentSkill.battleType == BattleType.BUFF) {
			currentSkill.castEffect(this, t);
		} else {
			currentSkill.castEffect(this, t);
			if (battleMap != null) {
				battleMap.getEventBus().publish(
						new GameEvent<Object>(GameEventType.ATTACK_HIT, this, t, new AttackData(true, "", 0, false)));
				if (t.health <= 0) {
					t.setState(ObjectState.DEAD);
					battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.DEATH, t, this));
				}
			}
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
		case USING_ITEM:
			handleItemState(deltaTime);
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

	public void updateCurrentMapTile(float x, float y, int cw, int ch) {
		Vector2f p = getScreenToTile(x, y, cw, ch);
		gridX = (int) p.x;
		gridY = (int) p.y;
		currentMapTile.set(gridX, gridY);
	}

	/**
	 * 速度计算（移动模式+技能+地形倍率）
	 */
	private void updateSpeed() {
		float mul = 1f;
		for (MovementState s : moveManager.getActiveStates()) {
			mul = MathUtils.max(mul, s.getSpeedMultiplier());
		}
		switch (currentMode) {
		case RUN:
			mul *= 2;
			break;
		case SNEAK:
			mul *= 0.5f;
			break;
		case CHARGE:
			mul *= 2.5f;
			break;
		default:
			break;
		}
		if (battleMap != null) {
			BattleTile t = battleMap.getMapTile(gridX, gridY);
			if (t != null) {
				mul *= t.getTileType().getMoveSpeedMultiplier();
			}
		}
		targetSpeed = baseSpeed * mul;
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
		int ap = actionPoints, steps = 0;
		for (PointI p : path) {
			int c = getTileCost(p);
			if (ap < c) {
				break;
			}
			ap -= c;
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
		path.addAll(filterValidPath(newPath));
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
		int i = MathUtils.clamp(currentStep + steps, 0, path.size() - 1);
		return getTileToScreen(path.get(i).x, path.get(i).y);
	}

	/**
	 * 预览全路径（屏幕坐标）
	 */
	public TArray<Vector2f> previewScreenFullPath() {
		TArray<Vector2f> r = new TArray<Vector2f>();
		for (PointI p : filterValidPath(path)) {
			r.add(getTileToScreen(p.x, p.y));
		}
		return r;
	}

	/**
	 * 预览全路径（瓦片坐标）
	 * 
	 * @return
	 */
	public TArray<PointI> previewTileFullPath() {
		TArray<PointI> r = new TArray<PointI>();
		for (PointI p : filterValidPath(path)) {
			r.add(new PointI(p.x, p.y));
		}
		return r;
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

	public void setBlockedTiles(TArray<PointI> b) {
		blockedTiles.clear();
		if (b != null) {
			blockedTiles.addAll(b);
		}
	}

	public void setAllowedTiles(TArray<PointI> a) {
		allowedTiles.clear();
		if (a != null) {
			allowedTiles.addAll(a);
		}
	}

	public void addCharacter(BattleMapObject o) {
		if (o != null && !otherCharacters.contains(o)) {
			otherCharacters.add(o);
		}
	}

	public void addCharacters(TArray<BattleMapObject> s) {
		if (s != otherCharacters) {
			otherCharacters.addAll(s);
		}
	}

	public void setCharacters(TArray<BattleMapObject> s) {
		otherCharacters.clear();
		otherCharacters.addAll(s);
	}

	public void removeCharacter(BattleMapObject o) {
		otherCharacters.remove(o);
	}

	public void clearCharacter() {
		otherCharacters.clear();
	}

	public TArray<PointI> getPath() {
		return new TArray<PointI>(path);
	}

	public int getRemainingSteps() {
		return MathUtils.max(0, path.size() - currentStep);
	}

	public boolean isPaused() {
		return paused;
	}

	public void moveToGrid(XY pos) {
		if (pos != null) {
			moveToGrid((int) pos.getX(), (int) pos.getY());
		}
	}

	public void moveToGrid(int tx, int ty) {
		moveToGrid(tx, ty, drawPath, EffectType.MOVE);
	}

	public void moveToGrid(int tx, int ty, boolean light, EffectType et) {
		if (battleMap == null) {
			return;
		}
		BattlePathFinder f = battleMap.getPathFinder();
		f.setFlying(isFlying());
		TArray<PointI> p = f.findPath(gridX, gridY, tx, ty);
		if (!p.isEmpty()) {
			setCharacters(battleMap._mapObjects);
			if (light) {
				battleMap.highlighterRangePathToEffect(p, et);
			}
			setPath(p);
		}
	}

	public PointI getCurrentMapTile() {
		return new PointI(currentMapTile);
	}

	public void setCurrentMapTile(PointI tile) {
		if (tile == null) {
			return;
		}
		currentMapTile.set(tile);
		gridX = tile.x;
		gridY = tile.y;
	}

	public boolean canUltimateSkill() {
		return currentSkill != null && currentSkill.canUltimateSkill();
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
		if (listener != null)
			listener.onPathInterrupted(this);
		return this;
	}

	public BattleMapObject resume() {
		paused = false;
		if (listener != null)
			listener.onPathResumed(this);
		return this;
	}

	public void setMoving(boolean moving) {
		isMoving = moving;
	}

	private void triggerAnimation(AnimationState s) {
		if (listener != null) {
			listener.onAnimationStateChanged(this, s.name());
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
			BattleTile t = battleMap.getMapTile(tile.x, tile.y);
			cost = t == null ? 0 : (int) t.getPathCost();
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
		if (battleMap == null || tile == null || listener == null) {
			return;
		}
		BattleTile t = battleMap.getMapTile(tile.x, tile.y);
		if (t == null) {
			return;
		}
		BattleTileType type = t.getTileType();
		listener.onTerrainEffectApplied(this, type.getName(), type);
		targetSpeed = baseSpeed * type.getMoveSpeedMultiplier();
	}

	/**
	 * 碰撞检测
	 */
	public CollisionResponse checkCollision() {
		if (otherCharacters.isEmpty()) {
			return CollisionResponse.CONTINUE;
		}
		PointI self = getCurrentMapTile();
		for (BattleMapObject o : otherCharacters) {
			if (o == null || o == this) {
				continue;
			}
			if (self.equals(o.getCurrentMapTile())) {
				if (listener != null) {
					listener.onCollision(this, o, CollisionResponse.STOP);
				}
				return CollisionResponse.STOP;
			}
		}
		return CollisionResponse.CONTINUE;
	}

	/**
	 * 碰撞处理
	 * 
	 * @param r
	 * @return
	 */
	private boolean handleCollision(CollisionResponse r) {
		if (r == CollisionResponse.CONTINUE) {
			return false;
		}
		paused = true;
		triggerAnimation(AnimationState.IDLE);
		if (r == CollisionResponse.BACKWARD && currentStep > 0) {
			currentStep = MathUtils.max(0, currentStep - 1);
			targetPixel.set(getTileToScreen(path.get(currentStep).x, path.get(currentStep).y));
			setPixelPosition(targetPixel);
		}
		return true;
	}

	/**
	 * 路径移动完成
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
		PointI prev = path.get(currentStep - 1), curr = path.get(currentStep);
		if (prev == null || curr == null) {
			return;
		}
		Direction d = Direction.fromDelta(curr.x - prev.x, curr.y - prev.y);
		if (d != null && d != currentDirection) {
			currentDirection = d;
			if (listener != null) {
				listener.onDirectionChanged(this, d);
			}
		}
	}

	/**
	 * 移动模式设置
	 * 
	 * @param newMode
	 */
	public void setMovementMode(MovementMode m) {
		if (m == null || state == ObjectState.DEAD) {
			return;
		}
		MovementMode old = currentMode;
		currentMode = m;
		if (listener != null) {
			listener.onMovementModeChanged(this, old, m);
		}
	}

	public ObjectBundle getSyncPacket() {
		ObjectBundle p = new ObjectBundle();
		p.put("speed", currentSpeed);
		p.put("step", currentStep);
		p.put("paused", paused);
		p.put("mode", currentMode);
		p.put("points", actionPoints);
		p.put("x", startPixel.x);
		p.put("y", startPixel.y);
		return p;
	}

	public void applySyncPacket(ObjectBundle p) {
		if (p == null) {
			return;
		}
		currentSpeed = MathUtils.max(MAX_INERTIA, p.getFloat("speed", baseSpeed));
		currentStep = MathUtils.max(0, p.getInt("step", 0));
		paused = p.getBool("paused", false);
		currentMode = (MovementMode) p.get("mode", MovementMode.WALK);
		actionPoints = MathUtils.max(0, p.getInt("points", 0));
		setPixelPosition(new Vector2f(p.getFloat("x"), p.getFloat("y")));
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
		if (listener != null) {
			listener.onStepReached(this, tile.x, tile.y);
			listener.onTileEntered(this, tile.x, tile.y);
		}
		deductMovementCost(tile);
		applyTerrainEffects(tile);
		if (handleCollision(checkCollision())) {
			return;
		}
		updateDirection();
		currentStep++;
		moveProgress = 0f;
		if (currentStep >= path.size())
			finishPath();
		else {
			startPixel.set(targetPixel);
			targetPixel.set(getTileToScreen(path.get(currentStep).x, path.get(currentStep).y));
		}
	}

	/**
	 * 判断是否可移动到目标瓦片
	 * 
	 * @param tile
	 * @return
	 */
	public boolean canMoveTo(PointI tile) {
		if (tile == null || state == ObjectState.DEAD) {
			return false;
		}
		for (MovementState s : moveManager.getActiveStates()) {
			if (s.canOverrideBlocked(tile)) {
				return true;
			}
		}
		if (battleMap != null) {
			BattleTile t = battleMap.getMapTile(tile.x, tile.y);
			if (t == null || !t.isPassable() && !isFlying()) {
				return false;
			}
		}
		return !blockedTiles.contains(tile) || allowedTiles.contains(tile);
	}

	private TArray<PointI> filterValidPath(TArray<PointI> paths) {
		TArray<PointI> r = new TArray<PointI>();
		if (paths == null || paths.isEmpty()) {
			return r;
		}
		int ap = actionPoints;
		for (PointI p : paths) {
			if (!canMoveTo(p)) {
				break;
			}
			int c = getTileCost(p);
			if (ap < c) {
				break;
			}
			ap -= c;
			r.add(p);
		}
		return r;
	}

	private int getTileCost(PointI tile) {
		if (battleMap == null || tile == null) {
			return 0;
		}
		BattleTile t = battleMap.getMapTile(tile.x, tile.y);
		return t == null ? 0 : MathUtils.max(0, (int) t.getPathCost());
	}

	public int getMaxMovementPoints() {
		return movePoints;
	}

	public void setMaxMovementPoints(int p) {
		movePoints = MathUtils.max(0, p);
	}

	@Override
	public BattleMapObject die() {
		super.die();
		setState(ObjectState.DEAD);
		return this;
	}

	public BattleTile getCurrentTile() {
		if (battleMap != null) {
			return battleMap.getMapTile(gridX, gridY);
		}
		return null;
	}

	public void resetPathState() {
		resetPathState(movePoints);
	}

	public void resetPathState(int p) {
		currentStep = 0;
		moveProgress = 0f;
		paused = false;
		movePoints = MathUtils.max(0, p);
		actionPoints = movePoints;
		path.clear();
	}

	public void setRemainingMovementPoints(int p) {
		actionPoints = MathUtils.clamp(p, 0, movePoints);
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
		state = newState;
		isMoving = state == ObjectState.MOVING;
		isDead = state == ObjectState.DEAD;
		if (isDead) {
			endCombat();
			clearPath();
		}
	}

	public void paint(GLEx g, float deltaTime, float px, float py) {
		if (isVisible()) {
			update(deltaTime);
			if (currentItem != null) {
				currentItem.update(deltaTime);
				currentItem.drawItemEffect(g, deltaTime, px, py);
			}
			if (currentSkill != null) {
				currentSkill.updateSkill(deltaTime);
				currentSkill.drawSkillEffect(g, deltaTime, px, py);
			}
		}
	}

	public boolean isMoving() {
		return isMoving;
	}

	protected void handleIdleState(float deltaTime) {
		moveInertia = MathUtils.max(0, moveInertia - deltaTime * 2);
		if (!path.isEmpty() && state != ObjectState.DEAD) {
			startMoving();
		} else {
			endMovement();
		}
	}

	protected void handleDefenceState(float deltaTime) {
		if (objectStateListener != null) {
			objectStateListener.onDefenced(this, deltaTime);
		}
	}

	protected void startMoving() {
		if (state == ObjectState.DEAD) {
			return;
		}
		setState(ObjectState.MOVING);
		isMoving = true;
		moveProgress = 0f;
	}

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

	public boolean isPositionPassable(int x, int y) {
		if (battleMap == null) {
			return false;
		}
		BattleTile t = battleMap.getMapTile(x, y);
		return t != null && (t.isPassable() || isFlying());
	}

	public BattleMap getBattleMap() {
		return battleMap;
	}

	public void setBattleMap(BattleMap m) {
		battleMap = m;
	}

	public void setCurrentSkill(BattleSkill s) {
		currentSkill = s;
		if (s != null) {
			s.setBattleMap(battleMap);
		}
	}

	public BattleSkill getCurrentSkill() {
		return currentSkill;
	}

	public float getBaseSpeed() {
		return baseSpeed;
	}

	public void setBaseSpeed(float s) {
		baseSpeed = MathUtils.max(MAX_INERTIA, s);
		if (listener != null) {
			listener.onSpeedChanged(this, baseSpeed);
		}
	}

	public float getTargetSpeed() {
		return targetSpeed;
	}

	public void setTargetSpeed(float s) {
		targetSpeed = s;
	}

	public float getSkillProgress() {
		return skillProgress;
	}

	public void setSkillProgress(float p) {
		skillProgress = p;
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

	public boolean isDrawPath() {
		return drawPath;
	}

	public void setDrawPath(boolean d) {
		drawPath = d;
	}

	public ObjectStateListener getObjectStateListener() {
		return objectStateListener;
	}

	public void setObjectStateListener(ObjectStateListener l) {
		objectStateListener = l;
	}

	public BattleMapObject getLastAttacker() {
		return lastAttacker;
	}

	public void setLastAttacker(BattleMapObject o) {
		lastAttacker = o;
	}

	public TArray<BattleSkill> getAllSkills() {
		return new TArray<BattleSkill>(skills);
	}

	public TArray<BattleSkill> getHealSkills() {
		TArray<BattleSkill> r = new TArray<BattleSkill>();
		for (BattleSkill s : skills) {
			if (s != null && !s.canUltimateSkill() && s.isHealSkill() && mana >= s.mpCost) {
				r.add(s);
			}
		}
		return r;
	}

	public TArray<BattleSkill> getUltimateSkills() {
		TArray<BattleSkill> r = new TArray<BattleSkill>();
		for (BattleSkill s : skills) {
			if (s != null && s.canUltimateSkill() && !s.isHealSkill() && mana >= s.mpCost) {
				r.add(s);
			}
		}
		return r;
	}

	public TArray<BattleSkill> getBuffSkills() {
		TArray<BattleSkill> r = new TArray<BattleSkill>();
		for (BattleSkill s : skills) {
			if (s != null && s.canBuffSkill() && mana >= s.mpCost) {
				r.add(s);
			}
		}
		return r;
	}

	public TArray<BattleSkill> getBaseAttackSkills() {
		TArray<BattleSkill> r = new TArray<BattleSkill>();
		for (BattleSkill s : skills) {
			if (s != null && s.canBaseAttackSkill() && s.isPhysicalSkill() && actionPoints >= s.actionPointCost) {
				r.add(s);
			}
		}
		return r;
	}

	public float getMaxSkillDamage() {
		float maxDamage = 0f;
		for (BattleSkill s : skills) {
			if (s != null) {
				maxDamage += s.getDamage();
			}
		}
		return maxDamage;
	}

	public void addSkills(BattleSkill s) {
		skills.add(s);
	}

	public void addSkill(BattleSkill s) {
		skills.add(s);
	}

	public void removeSkill(BattleSkill s) {
		skills.remove(s);
	}

	public void clearSkill() {
		skills.clear();
	}

	public boolean isTaunting() {
		return taunt;
	}

	public void setTaunt(boolean t) {
		taunt = t;
	}

	public float getKeyDelay() {
		return keyDelay;
	}

	public void setKeyDelay(float k) {
		keyDelay = k;
	}

	public boolean canControl() {
		return state == ObjectState.IDLE && state != ObjectState.DEAD && !paused;
	}

	public boolean isEnemy(BattleMapObject other) {
		return other != null && other.team != team;
	}

	public boolean isAlly(BattleMapObject other) {
		return other != null && other.team == team;
	}

	public BattleMapObject findNearestEnemy() {
		BattleMapObject best = null;
		float minDist = Float.MAX_VALUE;
		for (BattleMapObject obj : otherCharacters) {
			if (obj == null || obj == this || obj.isDead() || !isEnemy(obj)) {
				continue;
			}
			float dist = Vector2f.dst2(gridX, gridY, obj.gridX, obj.gridY);
			if (dist < minDist) {
				minDist = dist;
				best = obj;
			}
		}
		return best;
	}

	public void applyDamage(int damage, boolean isCrit, BattleMapObject caster) {
		if (isDead || isInvincible) {
			return;
		}
		health = MathUtils.max(0, health - damage);
		lastAttacker = caster;
		if (objectStateListener != null) {
			objectStateListener.onHit(this, damage, isCrit);
		}
		if (battleMap != null) {
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.ATTACK_HIT, this, caster,
					new AttackData(true, "hit", damage, isCrit)));
			battleMap.getEventBus().publish(new GameEvent<Object>(GameEventType.STATE_CHANGED, this, caster,
					new ResourceData("hp", this, health)));
		}
		if (health <= 0) {
			die();
		}
	}

	public void knockBack(int fromX, int fromY, int distance) {
		int dx = gridX - fromX;
		int dy = gridY - fromY;
		int tx = gridX + (dx == 0 ? 0 : dx / Math.abs(dx)) * distance;
		int ty = gridY + (dy == 0 ? 0 : dy / Math.abs(dy)) * distance;
		if (canMoveTo(new PointI(tx, ty))) {
			moveToGrid(tx, ty);
		}
		if (objectStateListener != null) {
			objectStateListener.onKnockBack(this, fromX, fromY);
		}
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
		for (BattleSkill s : skills) {
			if (s != null) {
				s.close();
			}
		}
		skills.clear();
		if (currentItem != null) {
			currentItem.close();
			currentItem = null;
		}
		for (Item<ItemInfo> i : items) {
			if (i != null) {
				i.close();
			}
		}
		items.clear();
	}

}
