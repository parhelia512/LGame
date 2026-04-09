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
package loon.action.map.items;

import loon.LSystem;
import loon.utils.MathUtils;

/**
 * 一个基本的游戏角色数值模板,可以套用其扩展自己的游戏属性以及属性变更算法(和RoleInfo的关系在于这个更接近角色参数)
 *
 */
public abstract class RoleValue {
	/**
	 * 移动类型
	 */
	public static class MoveType {
		// 步兵
		public static final int INFANTRY = 0;
		// 骑兵
		public static final int CAVALRY = 1;
		// 飞行
		public static final int FLY = 2;
		// 重装
		public static final int ARMOR = 3;
		// 魔兽
		public static final int HOOVES = 4;
		// 魔法
		public static final int MAGIC = 5;
		// 远程
		public static final int RANGE = 6;
	}

	private final int _id;
	private boolean _locked;
	private String _roleName;

	protected int actionPriority;
	protected int maxHealth;
	protected int maxMana;
	protected int maxExp;
	protected int health;
	protected int mana;
	protected int exp;
	protected int attack;
	protected int defence;
	protected int strength;
	protected int intelligence;
	protected int agility;
	protected int fitness;
	protected int dexterity;
	protected int level;
	protected int team = Team.Unknown;
	protected int movePoints;
	protected int turnPoints;
	protected int actionPoints;
	protected int hitRateBonus;

	protected boolean isAttack;
	protected boolean isDefense;
	protected boolean isSkill;
	protected boolean isMoved;
	protected boolean isDead;
	protected boolean isInvincible;

	protected RoleEquip info;

	protected MoveType moveType;

	protected JobType jobType;

	public RoleValue(int id, RoleEquip info, int maxHealth, int maxMana, int attack, int defence, int strength,
			int intelligence, int fitness, int dexterity, int agility, int lv) {
		this(id, LSystem.UNKNOWN, info, maxHealth, maxMana, attack, defence, strength, intelligence, fitness, dexterity,
				agility, lv);
	}

	public RoleValue(int id, String name, RoleEquip info, int maxHealth, int maxMana, int attack, int defence,
			int strength, int intelligence, int fitness, int dexterity, int agility, int lv) {
		this._id = id;
		this._roleName = (name == null) ? LSystem.UNKNOWN : name;
		this.info = (info == null) ? new RoleEquip(0, 0, 0, 0, 0, 0, 0, 0, 0, 0) : info;
		this.maxHealth = MathUtils.max(maxHealth, 1);
		this.maxMana = MathUtils.max(maxMana, 0);
		this.health = this.maxHealth;
		this.mana = this.maxMana;
		this.agility = MathUtils.max(agility, 0);
		this.attack = MathUtils.max(attack, 0);
		this.defence = MathUtils.max(defence, 0);
		this.strength = MathUtils.max(strength, 0);
		this.intelligence = MathUtils.max(intelligence, 0);
		this.fitness = MathUtils.max(fitness, 0);
		this.dexterity = MathUtils.max(dexterity, 0);
		this.level = MathUtils.max(lv, 1);
	}

	public RoleValue setActionPriority(int a) {
		this.actionPriority = MathUtils.max(a, 0);
		return this;
	}

	public int getActionPriority() {
		return actionPriority;
	}

	public float updateTurnPoints() {
		int randomBuffer = MathUtils.nextInt(100);
		this.turnPoints += this.fitness + randomBuffer / 100;
		this.turnPoints = MathUtils.min(this.turnPoints, 100);
		return this.turnPoints;
	}

	public int calculateDamage(int enemyDefence, int damageBufferMax) {
		enemyDefence = MathUtils.max(enemyDefence, 0);
		float damage = this.attack + 0.5f * this.strength - 0.5f * enemyDefence;
		damage = this.variance(damage, damageBufferMax, true);
		damage = MathUtils.max(damage, 1f);
		return MathUtils.ifloor(damage);
	}

	public int calculateDamage(int enemyDefence) {
		return calculateDamage(enemyDefence, 20);
	}

	public int hit(int enemyDex, int enemyAgi, int enemyFitness) {
		return hit(enemyDex, enemyAgi, enemyFitness, 95, 15, 55f);
	}

	public int hit(int enemyDex, int enemyAgi, int enemyFitness, int maxChance, int minChance, float hitChance) {
		hitChance += hitRateBonus;
		hitChance += (this.dexterity - enemyDex) + 0.5 * (this.fitness - enemyFitness) - enemyAgi;
		hitChance = this.variance(hitChance, 10, true);
		hitChance = MathUtils.clamp(hitChance, minChance, maxChance);
		return MathUtils.ceil(hitChance);
	}

	public RoleValue damage(float damageTaken) {
		if (this.isInvincible) {
			return this;
		}
		damageTaken = MathUtils.max(damageTaken, 0);
		this.health = MathUtils.ifloor(this.health - damageTaken);
		if (this.health <= 0) {
			this.health = 0;
			this.die();
		}
		return this;
	}

	public boolean flee(int enemyLevel, int enemyFitness) {
		return flee(enemyLevel, enemyFitness, 95, 5, 55);
	}

	public boolean flee(int enemyLevel, int enemyFitness, int maxChance, int minChance, int hitChance) {
		int fleeChance = hitChance - 3 * (enemyFitness - this.fitness);
		fleeChance = MathUtils.clamp(fleeChance, minChance, maxChance);
		int fleeRoll = MathUtils.nextInt(100);
		return fleeRoll <= fleeChance;
	}

	public int getID() {
		return _id;
	}

	public RoleValue changeHeal(int healChange) {
		if (healChange == 0 || isDead) {
			return this;
		}
		if (healChange > 0) {
			this.health = MathUtils.min(this.health + healChange, this.maxHealth);
		} else {
			this.health = MathUtils.max(this.health + healChange, 0);
			if (this.health == 0) {
				this.die();
			}
		}
		return this;
	}

	public RoleValue heal(int healCost, int healAmount) {
		if (isDead || this.getMana() < healCost) {
			return this;
		}
		healAmount = MathUtils.ifloor(this.variance(healAmount, 20, true));
		healAmount = MathUtils.max(healAmount, 0);
		this.health = MathUtils.min(this.health + healAmount, this.maxHealth);
		this.mana -= healCost;
		return this;
	}

	public RoleValue heal() {
		return heal(5, 20);
	}

	public int regenerateMana() {
		return regenerateMana(2, 50);
	}

	public int regenerateMana(int minRegen, int maxRegen) {
		int regen = intelligence / 4;
		regen = MathUtils.clamp(regen, minRegen, maxRegen);
		this.mana = MathUtils.min(this.mana + regen, this.maxMana);
		return regen;
	}

	private float variance(float base, int variance, boolean negativeAllowed) {
		variance = MathUtils.clamp(variance, 1, 100);
		int buffer = MathUtils.nextInt(++variance);
		if (MathUtils.nextBoolean() && negativeAllowed) {
			buffer = -buffer;
		}
		float percent = (float) (100 - buffer) / 100f;
		return base * percent;
	}

	public RoleValue updateAttack(float attackModifier) {
		this.info.updateAttack(attackModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateDefence(float defenceModifier) {
		this.info.updateDefence(defenceModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateStrength(float strengthModifier) {
		this.info.updateStrength(strengthModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateIntelligence(float intelligenceModifier) {
		this.info.updateIntelligence(intelligenceModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateFitness(float fitnessModifier) {
		this.info.updateFitness(fitnessModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateDexterity(float dexterityModifier) {
		this.info.updateDexterity(dexterityModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateMaxHealth(float maxHealthModifier) {
		this.info.updateMaxHealth(maxHealthModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateSkillPoints(float skillModifier) {
		this.info.updateSkillPoints(skillModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateManaPoints(float manaModifier) {
		this.info.updateManaPoints(manaModifier);
		syncEquipToStatus();
		return this;
	}

	public RoleValue updateAgility(float agilityModifier) {
		this.info.updateAgility(agilityModifier);
		syncEquipToStatus();
		return this;
	}

	public boolean fellow(RoleValue c) {
		if (c == null) {
			return false;
		}
		return this.team == c.team;
	}

	public boolean fellow(Team team) {
		if (team == null) {
			return false;
		}
		return this.team == team.getTeam();
	}

	public int getAttack() {
		return this.attack;
	}

	public RoleValue setAttack(int attack) {
		this.attack = MathUtils.max(attack, 0);
		return this;
	}

	public int getMaxMana() {
		return this.maxMana;
	}

	public RoleValue setMaxMana(int maxMana) {
		this.maxMana = MathUtils.max(maxMana, 0);
		this.mana = MathUtils.min(this.mana, this.maxMana);
		return this;
	}

	public int getDefence() {
		return this.defence;
	}

	public RoleValue setDefence(int defence) {
		this.defence = MathUtils.max(defence, 0);
		return this;
	}

	public int getStrength() {
		return this.strength;
	}

	public RoleValue setStrength(int strength) {
		this.strength = MathUtils.max(strength, 0);
		return this;
	}

	public int getIntelligence() {
		return this.intelligence;
	}

	public RoleValue setIntelligence(int intelligence) {
		this.intelligence = MathUtils.max(intelligence, 0);
		return this;
	}

	public int getFitness() {
		return this.fitness;
	}

	public RoleValue setFitness(int fitness) {
		this.fitness = MathUtils.max(fitness, 0);
		return this;
	}

	public int getDexterity() {
		return this.dexterity;
	}

	public RoleValue setDexterity(int dexterity) {
		this.dexterity = MathUtils.max(dexterity, 0);
		return this;
	}

	public RoleValue setHealth(int health) {
		if (isInvincible) {
			return this;
		}
		this.health = MathUtils.max(health, 0);
		this.isDead = this.health <= 0;
		return this;
	}

	public RoleValue setMana(int mana) {
		this.mana = MathUtils.clamp(mana, 0, this.maxMana);
		return this;
	}

	public float getTurnPoints() {
		return this.turnPoints;
	}

	public RoleValue setTurnPoints(int turnPoints) {
		this.turnPoints = MathUtils.max(turnPoints, 0);
		return this;
	}

	public int getLevel() {
		return this.level;
	}

	public int getHealth() {
		return this.health;
	}

	public int getMana() {
		return this.mana;
	}

	public int getBaseMaxHealth() {
		return this.info.getBaseMaxHealth();
	}

	public RoleValue setBaseMaxHealth(int baseMaxHealth) {
		this.info.setBaseMaxHealth(baseMaxHealth);
		return this;
	}

	public int getEquipMaxHealth() {
		return this.info.getEquipMaxHealth();
	}

	public RoleValue setEquipMaxHealth(int equipMaxHealth) {
		this.info.setEquipMaxHealth(equipMaxHealth);
		return this;
	}

	public boolean isFullHealth() {
		return health == maxHealth;
	}

	public int getMaxHealth() {
		return this.maxHealth;
	}

	public RoleValue setMaxHealth(int maxHealth) {
		this.maxHealth = MathUtils.max(maxHealth, 1);
		if (this.health > this.maxHealth) {
			this.health = this.maxHealth;
		}
		return this;
	}

	public int getAgility() {
		return agility;
	}

	public RoleValue setAgility(int agility) {
		this.agility = MathUtils.max(agility, 0);
		return this;
	}

	public int getTeam() {
		return team;
	}

	public RoleValue setTeam(int team) {
		this.team = team;
		return this;
	}

	public int getMovePoints() {
		return movePoints;
	}

	public RoleValue setMovePoints(int movePoints) {
		this.movePoints = MathUtils.max(movePoints, 0);
		return this;
	}

	public boolean isAllDoneAction() {
		return isAttack && isDefense && isMoved && isSkill;
	}

	public boolean isAllUnDoneAction() {
		return !isAttack && !isDefense && !isMoved && !isSkill;
	}

	public RoleValue undoneAction() {
		setAttack(false);
		setDefense(false);
		setSkill(false);
		setMoved(false);
		return this;
	}

	public RoleValue doneAction() {
		setAttack(true);
		setDefense(true);
		setSkill(true);
		setMoved(true);
		return this;
	}

	public boolean isAttack() {
		return isAttack;
	}

	public RoleValue setAttack(boolean attack) {
		this.isAttack = attack;
		return this;
	}

	public boolean isDefense() {
		return isDefense;
	}

	public RoleValue setDefense(boolean defense) {
		this.isDefense = defense;
		return this;
	}

	public boolean isSkill() {
		return isSkill;
	}

	public RoleValue setSkill(boolean skill) {
		this.isSkill = skill;
		return this;
	}

	public boolean isMoved() {
		return isMoved;
	}

	public RoleValue setMoved(boolean moved) {
		this.isMoved = moved;
		return this;
	}

	public boolean isDead() {
		return this.isDead;
	}

	public boolean isAlive() {
		return !isDead;
	}

	public RoleValue setDead(boolean dead) {
		this.isDead = dead;
		return this;
	}

	public RoleValue die() {
		this.isDead = true;
		this.health = 0;
		return this;
	}

	public boolean isInvincible() {
		return isInvincible;
	}

	public RoleValue setInvincible(boolean i) {
		this.isInvincible = i;
		return this;
	}

	public RoleEquip getInfo() {
		return info;
	}

	public RoleValue setInfo(RoleEquip i) {
		this.info = i;
		return this;
	}

	public RoleValue setLevel(int level) {
		this.level = MathUtils.max(level, 1);
		return this;
	}

	public int getActionPoints() {
		return actionPoints;
	}

	public RoleValue setActionPoints(int actionPoints) {
		this.actionPoints = MathUtils.max(actionPoints, 0);
		return this;
	}

	public String getRoleName() {
		return _roleName;
	}

	public RoleValue setRoleName(String n) {
		this._roleName = (n == null) ? LSystem.UNKNOWN : n;
		return this;
	}

	public RoleValue setLocked(boolean l) {
		this._locked = l;
		return this;
	}

	public boolean isLocked() {
		return _locked;
	}

	public int getMaxExp() {
		return maxExp;
	}

	public RoleValue setMaxExp(int maxExp) {
		this.maxExp = MathUtils.max(maxExp, 0);
		return this;
	}

	public int getExp() {
		return exp;
	}

	public RoleValue setExp(int exp) {
		this.exp = MathUtils.max(exp, 0);
		return this;
	}

	public float getUpLevelMaxExp() {
		return getUpLevelMaxExp(0f);
	}

	public float getUpLevelMaxExp(float offset) {
		return (2f * level * (MathUtils.pow(1.3f, level / 3f)) + offset) + 4f;
	}

	public float getEnemyExpEarned(int enemyLevel) {
		return getEnemyExpEarned(enemyLevel, 0f);
	}

	public float getEnemyExpEarned(int enemyLevel, float offset) {
		return (MathUtils.pow(enemyLevel, 0.95f)) + offset;
	}

	public RoleValue reset() {
		this.isAttack = false;
		this.isDefense = false;
		this.isSkill = false;
		this.isMoved = false;
		this.isDead = false;
		this.isInvincible = false;
		this._locked = false;
		this.health = this.maxHealth;
		this.mana = this.maxMana;
		return this;
	}

	public RoleValue setStatus(int v) {
		v = MathUtils.max(v, 0);
		this.maxHealth = v;
		this.maxMana = v;
		this.health = v;
		this.mana = v;
		this.attack = v;
		this.defence = v;
		this.strength = v;
		this.intelligence = v;
		this.agility = v;
		this.fitness = v;
		this.dexterity = v;
		return this;
	}

	public RoleValue setStatus(RoleEquip e) {
		if (e == null) {
			return this;
		}
		this.maxHealth = (e.getBaseMaxHealth() + e.getEquipMaxHealth());
		this.maxMana = (e.getBaseManaPoint() + e.getEquipManaPoint());
		this.health = maxHealth;
		this.mana = maxMana;
		this.attack = (e.getBaseAttack() + e.getEquipAttack());
		this.defence = (e.getBaseDefence() + e.getEquipDefence());
		this.strength = (e.getBaseStrength() + e.getEquipStrength());
		this.intelligence = (e.getBaseIntelligence() + e.getEquipIntelligence());
		this.agility = (e.getBaseAgility() + e.getEquipAgility());
		this.fitness = (e.getBaseFitness() + e.getEquipFitness());
		this.dexterity = (e.getBaseDexterity() + e.getEquipDexterity());
		return this;
	}

	public RoleValue addEquip(RoleEquip e) {
		if (e == null) {
			return this;
		}
		this.maxHealth = (e.getBaseMaxHealth() + e.getEquipMaxHealth());
		this.maxMana = (e.getBaseManaPoint() + e.getEquipManaPoint());
		this.health = maxHealth;
		this.mana = maxMana;
		this.attack = MathUtils.max(this.attack + e.getEquipAttack(), 0);
		this.defence = MathUtils.max(this.defence + e.getEquipDefence(), 0);
		this.strength = MathUtils.max(this.strength + e.getEquipStrength(), 0);
		this.intelligence = MathUtils.max(this.intelligence + e.getEquipIntelligence(), 0);
		this.agility = MathUtils.max(this.agility + e.getEquipAgility(), 0);
		this.fitness = MathUtils.max(this.fitness + e.getEquipFitness(), 0);
		this.dexterity = MathUtils.max(this.dexterity + e.getEquipDexterity(), 0);
		return this;
	}

	public RoleValue mulStatus(int v) {
		v = MathUtils.max(v, 1);
		this.maxHealth *= v;
		this.maxMana *= v;
		this.health *= v;
		this.mana *= v;
		this.attack *= v;
		this.defence *= v;
		this.strength *= v;
		this.intelligence *= v;
		this.agility *= v;
		this.fitness *= v;
		this.dexterity *= v;
		return this;
	}

	public RoleValue addStatus(int v) {
		this.maxHealth = MathUtils.max(this.maxHealth + v, 1);
		this.maxMana = MathUtils.max(this.maxMana + v, 0);
		this.health = MathUtils.min(this.health + v, this.maxHealth);
		this.mana = MathUtils.min(this.mana + v, this.maxMana);
		this.attack = MathUtils.max(this.attack + v, 0);
		this.defence = MathUtils.max(this.defence + v, 0);
		this.strength = MathUtils.max(this.strength + v, 0);
		this.intelligence = MathUtils.max(this.intelligence + v, 0);
		this.agility = MathUtils.max(this.agility + v, 0);
		this.fitness = MathUtils.max(this.fitness + v, 0);
		this.dexterity = MathUtils.max(this.dexterity + v, 0);
		return this;
	}

	public RoleValue subStatus(int v) {
		v = MathUtils.max(v, 0);
		this.maxHealth = MathUtils.max(this.maxHealth - v, 1);
		this.maxMana = MathUtils.max(this.maxMana - v, 0);
		this.health = MathUtils.clamp(this.health - v, 0, this.maxHealth);
		this.mana = MathUtils.clamp(this.mana - v, 0, this.maxMana);
		this.attack = MathUtils.max(this.attack - v, 0);
		this.defence = MathUtils.max(this.defence - v, 0);
		this.strength = MathUtils.max(this.strength - v, 0);
		this.intelligence = MathUtils.max(this.intelligence - v, 0);
		this.agility = MathUtils.max(this.agility - v, 0);
		this.fitness = MathUtils.max(this.fitness - v, 0);
		this.dexterity = MathUtils.max(this.dexterity - v, 0);
		if (this.health == 0) {
			this.die();
		}
		return this;
	}

	public RoleValue divStatus(int v) {
		if (v <= 1) {
			return this;
		}
		this.maxHealth = MathUtils.max(this.maxHealth / v, 1);
		this.maxMana = MathUtils.max(this.maxMana / v, 0);
		this.health = MathUtils.clamp(this.health / v, 0, this.maxHealth);
		this.mana = MathUtils.clamp(this.mana / v, 0, this.maxMana);
		this.attack = MathUtils.max(this.attack / v, 0);
		this.defence = MathUtils.max(this.defence / v, 0);
		this.strength = MathUtils.max(this.strength / v, 0);
		this.intelligence = MathUtils.max(this.intelligence / v, 0);
		this.agility = MathUtils.max(this.agility / v, 0);
		this.fitness = MathUtils.max(this.fitness / v, 0);
		this.dexterity = MathUtils.max(this.dexterity / v, 0);
		if (this.health == 0) {
			this.die();
		}
		return this;
	}

	private void syncEquipToStatus() {
		if (info == null) {
			return;
		}
		this.maxHealth = info.getBaseMaxHealth() + info.getEquipMaxHealth();
		this.maxMana = info.getBaseManaPoint() + info.getEquipManaPoint();
		this.attack = info.getBaseAttack() + info.getEquipAttack();
		this.defence = info.getBaseDefence() + info.getEquipDefence();
		this.strength = info.getBaseStrength() + info.getEquipStrength();
		this.intelligence = info.getBaseIntelligence() + info.getEquipIntelligence();
		this.agility = info.getBaseAgility() + info.getEquipAgility();
		this.fitness = info.getBaseFitness() + info.getEquipFitness();
		this.dexterity = info.getBaseDexterity() + info.getEquipDexterity();
	}

	private void clearAllActionState() {
		isAttack = false;
		isDefense = false;
		isSkill = false;
		isMoved = false;
	}

	public JobType getJobType() {
		return jobType;
	}

	public void setJobType(JobType jobType) {
		this.jobType = jobType;
	}

	public MoveType getMoveType() {
		return moveType;
	}

	public void setMoveType(MoveType moveType) {
		this.moveType = moveType;
	}

	public RoleValue clear() {
		this.actionPriority = 0;
		this.maxHealth = 0;
		this.maxMana = 0;
		this.maxExp = 0;
		this.health = 0;
		this.mana = 0;
		this.exp = 0;
		this.attack = 0;
		this.defence = 0;
		this.strength = 0;
		this.intelligence = 0;
		this.agility = 0;
		this.fitness = 0;
		this.dexterity = 0;
		this.level = 0;
		this.team = Team.Unknown;
		this.movePoints = 0;
		this.turnPoints = 0;
		this.actionPoints = 0;
		this.isAttack = false;
		this.isDefense = false;
		this.isSkill = false;
		this.isMoved = false;
		this.isDead = false;
		this.isInvincible = false;
		this.clearAllActionState();
		return this;
	}

	public RoleValue addStatusToMaxHp(int v) {
		this.maxHealth += v;
		this.health = this.maxHealth;
		return this;
	}

	public RoleValue addStatusToMaxMp(int v) {
		this.maxMana += v;
		this.mana = this.maxMana;
		return this;
	}

	public RoleValue revive() {
		this.isDead = false;
		this.health = maxHealth;
		this.mana = maxMana;
		undoneAction();
		return this;
	}

	public boolean isFullMana() {
		return this.mana == this.maxMana;
	}

	public boolean canAction() {
		return isAlive() && !isLocked();
	}

	public int getHitRateBonus() {
		return hitRateBonus;
	}

	public void setHitRateBonus(int hitRateBonus) {
		this.hitRateBonus = hitRateBonus;
	}
}
