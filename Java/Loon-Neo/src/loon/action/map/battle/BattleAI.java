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

import loon.geom.PointI;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

// 战斗用AI
public class BattleAI {

	/**
	 * AI状态
	 */
	public static enum AIState {
		IDLE, MOVING_TO_ENEMY, ATTACKING, CASTING_SKILL, RETREATING, DEFENDING, FOLLOWING_ALLY, AMBUSHING, REINFORCING,
		RESTING, PATROLLING, AMBUSH_PREPARE, CHASING, FLANKING, SUPPORTING
	}

	/**
	 * AI模式
	 */
	public static enum AIStyle {
		// 正常、攻击性、防御性、谨慎战斗、支援为主、狂战
		NORMAL, AGGRESSIVE, DEFENSIVE, CAUTIOUS, SUPPORT, BURST,
	}

	/**
	 * 战术指令
	 */
	public static enum TacticalCommand {
		FOCUS_FIRE, SPREAD_ATTACK, PROTECT_ALLY, HOLD_POSITION
	}

	private BattleMapObject controlledUnit;
	private BattleMapObject targetEnemy;
	private BattleMapObject supportTarget;
	private int currentAITurn;
	private AIState currentState = AIState.IDLE;
	private BattleTile targetTile;

	private int bestMin = -9999;
	private int bestMax = 999;

	public static boolean ENABLE_CLASS_ADVANTAGE = true;
	public static boolean ENABLE_AMBUSH_SYSTEM = true;
	public static boolean ENABLE_FLANK_SYSTEM = true;
	public static boolean ENABLE_RETREAT_SYSTEM = true;
	public static boolean ENABLE_SUPPORT_SYSTEM = true;
	public static boolean ENABLE_TAUNT_SYSTEM = true;
	public static boolean ENABLE_SKILL_AI = true;
	public static boolean ENABLE_AOE_AI = true;

	public static boolean ENABLE_HATRED_SYSTEM = true;
	public static boolean ENABLE_FORMATION_AI = true;
	public static boolean ENABLE_ULTIMATE_AI = true;
	public static boolean ENABLE_FOCUS_FIRE = true;

	public AIStyle currentStyle = AIStyle.NORMAL;

	private BattleMapObject tauntTarget;
	private BattleMapObject hatredTarget;

	// 权重
	private static final float WEIGHT_DISTANCE = 10.0f;
	private static final float WEIGHT_HP_RATE = 50.0f;
	private static final float WEIGHT_SKILL_DMG = 40.0f;
	private static final float WEIGHT_HATRED = 999.0f;
	private static final float WEIGHT_CLASS_ADVANTAGE = 200.0f;

	private ObjectMap<BattleMapObject, Float> hatredMap = new ObjectMap<BattleMapObject, Float>();
	private TacticalCommand currentCommand = TacticalCommand.FOCUS_FIRE;

	private float morale = 1.0f;
	private BattleMap battleMap;

	public BattleAI(BattleMap map, BattleMapObject controlledUnit) {
		this.battleMap = map;
		this.controlledUnit = controlledUnit;
	}

	public void update() {
		if (controlledUnit.isAllDoneAction()) {
			return;
		}
		if (controlledUnit.isDisabled()) {
			return;
		}
		tauntTarget = getTauntTarget();
		hatredTarget = controlledUnit.getLastAttacker();
		if (hatredTarget != null) {
			addHatred(hatredTarget, 50f);
		}
		coordinateWithAllies();
		captureStrategicPoint();
		makeDecision();
	}

	protected BattleMapObject getTauntTarget() {
		TArray<BattleMapObject> units = battleMap.getObjects();
		for (BattleMapObject u : units) {
			if (u.isTaunting() && u.isEnemyOf(controlledUnit)) {
				return u;
			}
		}
		return null;
	}

	protected void addHatred(BattleMapObject attacker, float value) {
		Float old = hatredMap.get(attacker);
		if (old == null) {
			old = 0f;
		}
		hatredMap.put(attacker, old + value);
	}

	protected BattleMapObject getHighestHatredTarget() {
		BattleMapObject best = null;
		float max = bestMin;
		ObjectMap.Entries<BattleMapObject, Float> ens = hatredMap.entries();
		for (ObjectMap.Entries<BattleMapObject, Float> it = ens.iterator(); it.hasNext();) {
			ObjectMap.Entry<BattleMapObject, Float> e = it.next();
			if (!e.getKey().isDead() && e.getValue() > max) {
				max = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	private void makeDecision() {
		targetEnemy = findBestEnemyTarget(bestMin);
		supportTarget = findWoundedTeammate(bestMax);

		// 优先级,嘲讽>仇恨
		if (ENABLE_TAUNT_SYSTEM && tauntTarget != null) {
			targetEnemy = tauntTarget;
		}
		// 阵型
		if (ENABLE_HATRED_SYSTEM) {
			BattleMapObject h = getHighestHatredTarget();
			if (h != null) {
				targetEnemy = h;
			}
		}

		applyTacticalCommand();

		if (ENABLE_FORMATION_AI) {
			switch (currentState) {
			case IDLE:
				if (targetEnemy != null) {
					if (ENABLE_FLANK_SYSTEM && (targetEnemy.isCavalry() || targetEnemy.isFlying())) {
						currentState = AIState.FLANKING;
					} else if (ENABLE_AMBUSH_SYSTEM && canAmbush()) {
						currentState = AIState.AMBUSH_PREPARE;
					} else {
						currentState = AIState.MOVING_TO_ENEMY;
					}
				} else if (ENABLE_SUPPORT_SYSTEM && supportTarget != null) {
					currentState = AIState.SUPPORTING;
				} else {
					currentState = AIState.PATROLLING;
				}
				break;
			case MOVING_TO_ENEMY:
				if (isInSkillRange(targetEnemy)) {
					if (ENABLE_ULTIMATE_AI && castUltimate()) {
					} else if (ENABLE_SKILL_AI && castBestSkill())
						currentState = AIState.CASTING_SKILL;
					else {
						performAttack();
						currentState = AIState.ATTACKING;
					}
				} else if (isInAttackRange(targetEnemy)) {
					if (ENABLE_ULTIMATE_AI && castUltimate()) {
					} else if (ENABLE_SKILL_AI && castBestSkill())
						currentState = AIState.CASTING_SKILL;
					else {
						performAttack();
						currentState = AIState.ATTACKING;
					}
				} else {
					moveToTarget(targetEnemy);
				}
				break;
			case FLANKING:
				moveToFlank(targetEnemy);
				if (isInAttackRange(targetEnemy)) {
					castUltimate();
					castBestSkill();
					performAttack();
				}
				break;
			case AMBUSH_PREPARE:
				if (isEnemyInAmbushRange(targetEnemy)) {
					triggerAmbush();
				}
				break;
			case SUPPORTING:
				moveToSupport(supportTarget);
				if (isInSupportRange(supportTarget)) {
					castBestBuff();
					currentState = AIState.DEFENDING;
				}
				break;
			case ATTACKING:
				if (targetEnemy.isDead()) {
					currentState = AIState.IDLE;
				} else if (needRetreat()) {
					currentState = AIState.RETREATING;
				} else {
					currentState = AIState.MOVING_TO_ENEMY;
				}
				break;
			case CASTING_SKILL:
				currentState = AIState.ATTACKING;
				break;
			case RETREATING:
				moveToSafeTile();
				break;
			case PATROLLING:
				doPatrol();
				break;
			default:
				currentState = AIState.IDLE;
			}
		}
	}

	protected void applyTacticalCommand() {
		switch (currentCommand) {
		case FOCUS_FIRE:
			targetEnemy = findBestEnemyTarget(bestMin);
			break;
		case SPREAD_ATTACK:
			targetEnemy = findRandomEnemy();
			break;
		case PROTECT_ALLY:
			supportTarget = findWoundedTeammate(bestMax);
			break;
		case HOLD_POSITION:
			currentState = AIState.DEFENDING;
			break;
		}
	}

	protected BattleMapObject findRandomEnemy() {
		TArray<BattleMapObject> units = battleMap.getObjects();
		for (BattleMapObject u : units) {
			if (u.isEnemyOf(controlledUnit) && !u.isDead()) {
				return u;
			}
		}
		return null;
	}

	protected float rateTarget(BattleMapObject target) {
		float dist = controlledUnit.currentMapTile.distanceTo(target.currentMapTile);
		float hp = target.getHpRate();
		float score = (100f / MathUtils.max(dist, 1)) * WEIGHT_DISTANCE + (1 - hp) * WEIGHT_HP_RATE;
		if (ENABLE_CLASS_ADVANTAGE && controlledUnit.hasAdvantageOver(target)) {
			score += WEIGHT_CLASS_ADVANTAGE;
		}
		return score;
	}

	protected void coordinateWithAllies() {
		TArray<BattleMapObject> allies = battleMap.getObjects();
		for (BattleMapObject ally : allies) {
			if (ally.isAllyOf(controlledUnit) && !ally.isDead()) {
				if (ally.getLastAttacker() == targetEnemy) {
					addHatred(targetEnemy, 100f);
				}
			}
		}
	}

	protected void adjustMorale(boolean allyKilled, boolean enemyKilled) {
		if (allyKilled) {
			morale -= 0.2f;
		}
		if (enemyKilled) {
			morale += 0.1f;
		}
		if (morale < 0.3f) {
			currentState = AIState.RETREATING;
		}
		if (morale > 1.5f && currentState == AIState.IDLE) {
			currentState = AIState.MOVING_TO_ENEMY;
		}
	}

	protected void captureStrategicPoint() {
		BattleTile nearestPoint = battleMap.findNearestStrategicPoint(controlledUnit.currentMapTile);
		if (nearestPoint != null && !nearestPoint.hasUnit()) {
			controlledUnit.moveToGrid(nearestPoint.getX(), nearestPoint.getY());
			currentState = AIState.DEFENDING;
		}
	}

	protected BattleMapObject findBestEnemyTarget(float max) {
		BattleMapObject best = null;
		TArray<BattleMapObject> units = battleMap.getObjects();
		for (BattleMapObject u : units) {
			if (!u.isEnemyOf(controlledUnit) || u.isDead()) {
				continue;
			}
			float score = rateTarget(u);
			if (hatredTarget == u) {
				score += WEIGHT_HATRED;
			}
			if (score > max) {
				max = score;
				best = u;
			}
		}
		return best;
	}

	private void performAttack() {
		if (targetEnemy == null) {
			return;
		}
		controlledUnit.castSkill(targetEnemy);
	}

	private boolean castBestBuff() {
		TArray<BattleSkill> buffs = controlledUnit.getBuffSkills();
		if (buffs.isEmpty()) {
			return false;
		}
		BattleMapObject wounded = findWoundedTeammate(bestMax);
		if (wounded == null) {
			return false;
		}
		for (BattleSkill s : buffs) {
			if (controlledUnit.currentMapTile.distanceTo(wounded.currentMapTile) <= s.getRangeDistance()) {
				controlledUnit.setCurrentSkill(s);
				controlledUnit.castSkill(wounded);
				return true;
			}
		}
		return false;
	}

	private boolean canAmbush() {
		PointI me = controlledUnit.currentMapTile;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				int x = me.x + dx;
				int y = me.y + dy;
				if (battleMap.isValidTile(x, y)) {
					BattleTile t = battleMap.getMapTile(x, y);
					if (t.isPassable() && isAmbushTerrain(t.getTileType())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void triggerAmbush() {
		if (targetEnemy != null) {
			targetEnemy.addBreak(20);
		}
		currentState = AIState.ATTACKING;
	}

	private float rateSkill(BattleSkill s) {
		return s.getDamage() * WEIGHT_SKILL_DMG;
	}

	private boolean castBestSkill() {
		if (targetEnemy == null) {
			return false;
		}
		TArray<BattleSkill> skills = controlledUnit.getUltimateSkills();
		if (skills.isEmpty()) {
			return false;
		}
		BattleSkill best = null;
		float min = bestMin;
		for (BattleSkill s : skills) {
			float score = rateSkill(s);
			if (score > min) {
				min = score;
				best = s;
			}
		}
		if (best != null) {
			if (best.canRange()) {
				PointI center = findBestSkillCenter(best);
				if (center != null) {
					controlledUnit.setCurrentSkill(best);
					controlledUnit.castSkillTile(center);
					return true;
				}
			} else {
				controlledUnit.setCurrentSkill(best);
				controlledUnit.castSkill(targetEnemy);
				return true;
			}
		}
		return false;
	}

	private PointI findBestSkillCenter(BattleSkill skill) {
		PointI best = null;
		int maxHit = 0;
		int r = skill.getRangeRadius();
		int d = skill.getRangeDistance();
		PointI me = controlledUnit.currentMapTile;
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				int cx = me.x + dx;
				int cy = me.y + dy;
				if (!battleMap.isValidTile(cx, cy)) {
					continue;
				}
				int hit = countEnemiesInRange(cx, cy, d);
				if (hit > maxHit) {
					maxHit = hit;
					best = new PointI(cx, cy);
				}
			}
		}
		return best;
	}

	private boolean castUltimate() {
		if (!controlledUnit.canUltimateSkill()) {
			return false;
		}
		BattleSkill skill = controlledUnit.getCurrentSkill();
		if (skill == null) {
			return false;
		}
		if (skill.canRange()) {
			PointI center = findBestSkillCenter(skill);
			if (center != null) {
				controlledUnit.castSkillTile(center);
				return true;
			}
		} else {
			if (targetEnemy != null) {
				controlledUnit.castSkill(targetEnemy);
				return true;
			}
		}
		return false;
	}

	private boolean isInAttackRange(BattleMapObject target) {
		int attackRange = controlledUnit.currentMapTile.distanceTo(target.currentMapTile);
		boolean result = attackRange <= controlledUnit.getBaseAttackRange();
		return result && controlledUnit.getBaseAttackSkills().size > 0;
	}

	private boolean isInSkillRange(BattleMapObject target) {
		int attackRange = controlledUnit.currentMapTile.distanceTo(target.currentMapTile);
		boolean result = attackRange <= controlledUnit.getBaseSkillRange();
		return result && controlledUnit.getUltimateSkills().size > 0;
	}

	private boolean isEnemyInAmbushRange(BattleMapObject target) {
		return target != null && controlledUnit.currentMapTile.distanceTo(target.currentMapTile) <= 2;
	}

	private boolean isInSupportRange(BattleMapObject target) {
		return controlledUnit.currentMapTile.distanceTo(target.currentMapTile) <= 2;
	}

	private boolean needRetreat() {
		return controlledUnit.getHpRate() < 0.3f;
	}

	private boolean isAmbushTerrain(BattleTileType type) {
		return type == BattleTileType.FOREST || type == BattleTileType.MOUNTAIN;
	}

	protected void moveToTarget(BattleMapObject target) {
		if (controlledUnit.isMoved()) {
			return;
		}
		PointI me = controlledUnit.currentMapTile;
		PointI tg = target.currentMapTile;
		int dx = MathUtils.compare(tg.x, me.x);
		int dy = MathUtils.compare(tg.y, me.y);
		int nx = me.x + dx;
		int ny = me.y + dy;
		if (battleMap.isValidTile(nx, ny)) {
			BattleTile tile = battleMap.getMapTile(nx, ny);
			if (tile.isPassable() && !tile.hasUnit()) {
				controlledUnit.moveToGrid(nx, ny);
				targetTile = tile;
			}
		}
	}

	protected void moveToFlank(BattleMapObject target) {
		PointI p = target.currentMapTile;
		int fx = p.x + 2;
		int fy = p.y;
		if (!battleMap.isValidTile(fx, fy) || !battleMap.getMapTile(fx, fy).isPassable()) {
			fx = p.x - 2;
		}
		if (battleMap.isValidTile(fx, fy)) {
			controlledUnit.moveToGrid(fx, fy);
		}
	}

	protected void moveToSupport(BattleMapObject target) {
		PointI p = target.currentMapTile;
		int sx = p.x + 1;
		int sy = p.y;
		if (!battleMap.isValidTile(sx, sy) || battleMap.getMapTile(sx, sy).hasUnit()) {
			sx = p.x - 1;
		}
		if (battleMap.isValidTile(sx, sy)) {
			controlledUnit.moveToGrid(sx, sy);
		}
	}

	protected void moveToSafeTile() {
		PointI me = controlledUnit.currentMapTile;
		int dx = targetEnemy == null ? 1 : MathUtils.compare(me.x, targetEnemy.getGridX());
		int dy = targetEnemy == null ? 1 : MathUtils.compare(me.y, targetEnemy.getGridY());
		int nx = me.x + dx;
		int ny = me.y + dy;
		if (battleMap.isValidTile(nx, ny) && battleMap.getMapTile(nx, ny).isPassable()) {
			controlledUnit.moveToGrid(nx, ny);
		}
	}

	protected void doPatrol() {
		PointI me = controlledUnit.currentMapTile;
		int nx = me.x + MathUtils.nextInt(3);
		int ny = me.y + MathUtils.nextInt(3);
		if (battleMap.isValidTile(nx, ny) && battleMap.getMapTile(nx, ny).isPassable()
				&& !battleMap.getMapTile(nx, ny).hasUnit()) {
			controlledUnit.moveToGrid(nx, ny);
		}
	}

	protected int countEnemiesInRange(int cx, int cy, int d) {
		int c = 0;
		final PointI p = new PointI();
		final TArray<BattleMapObject> units = battleMap.getObjects();
		for (BattleMapObject u : units) {
			if (u.isEnemyOf(controlledUnit) && !u.isDead()) {
				p.set(cx, cy);
				if (p.distanceTo(u.currentMapTile) <= d) {
					c++;
				}
			}
		}
		return c;
	}

	protected BattleMapObject findWoundedTeammate(float min) {
		BattleMapObject worst = null;
		final TArray<BattleMapObject> units = battleMap.getObjects();
		for (BattleMapObject u : units) {
			if (u.isAllyOf(controlledUnit) && !u.isDead()) {
				float r = u.getHpRate();
				if (r < min) {
					min = r;
					worst = u;
				}
			}
		}
		return worst;
	}

	public BattleMapObject getControlledUnit() {
		return controlledUnit;
	}

	public void setControlledUnit(BattleMapObject controlledUnit) {
		this.controlledUnit = controlledUnit;
	}

	public BattleMapObject getTargetEnemy() {
		return targetEnemy;
	}

	public void setTargetEnemy(BattleMapObject targetEnemy) {
		this.targetEnemy = targetEnemy;
	}

	public BattleMapObject getSupportTarget() {
		return supportTarget;
	}

	public void setSupportTarget(BattleMapObject supportTarget) {
		this.supportTarget = supportTarget;
	}

	public int getCurrentAITurn() {
		return currentAITurn;
	}

	public void setCurrentAITurn(int currentAITurn) {
		this.currentAITurn = currentAITurn;
	}

	public AIState getCurrentState() {
		return currentState;
	}

	public void setCurrentState(AIState currentState) {
		this.currentState = currentState;
	}

	public BattleTile getTargetTile() {
		return targetTile;
	}

	public void setTargetTile(BattleTile targetTile) {
		this.targetTile = targetTile;
	}

	public int getBestMin() {
		return bestMin;
	}

	public void setBestMin(int bestMin) {
		this.bestMin = bestMin;
	}

	public int getBestMax() {
		return bestMax;
	}

	public void setBestMax(int bestMax) {
		this.bestMax = bestMax;
	}

	public AIStyle getCurrentStyle() {
		return currentStyle;
	}

	public void setCurrentStyle(AIStyle currentStyle) {
		this.currentStyle = currentStyle;
	}

	public BattleMapObject getHatredTarget() {
		return hatredTarget;
	}

	public void setHatredTarget(BattleMapObject hatredTarget) {
		this.hatredTarget = hatredTarget;
	}

	public BattleMap getBattleMap() {
		return battleMap;
	}

	public void setBattleMap(BattleMap battleMap) {
		this.battleMap = battleMap;
	}

	public void setTauntTarget(BattleMapObject tauntTarget) {
		this.tauntTarget = tauntTarget;
	}

}
