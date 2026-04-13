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
 * 一个基本的游戏角色数值模板,可以套用其扩展自己的游戏属性以及属性变更算法,这些参数尽可能多的适配多种游戏，但并不是都必须用(和RoleInfo的关系在于这个更接近角色参数)
 *
 */
public abstract class RoleValue {
	/**
	 * 大职业类型
	 */
	public static class UnitType {
		// 步兵
		public static final int INFANTRY = 1 << 0;
		// 骑兵
		public static final int CAVALRY = 1 << 1;
		// 飞行
		public static final int FLY = 1 << 2;
		// 重装
		public static final int ARMOR = 1 << 3;
		// 魔兽
		public static final int HOOVES = 1 << 4;
		// 魔法
		public static final int MAGIC = 1 << 5;
		// 弓箭
		public static final int ARCHER = 1 << 6;
		// 远程
		public static final int RANGE = 1 << 7;
		// 长枪
		public static final int SPEARMAN = 1 << 8;
		// 治疗者
		public static final int HEALER = 1 << 9;
		// 海军
		public static final int NAVAL = 1 << 10;
		// 亡灵
		public static final int UNDEAD = 1 << 11;

		// 判断是否包含某类型
		public static boolean hasType(int fullType, int checkType) {
			return (fullType & checkType) != 0;
		}
	}

	/**
	 * 角色元素属性
	 */
	public static class Element {

		public static final int NONE = 0;

		public static final int FIRE = 1;

		public static final int ICE = 2;

		public static final int WIND = 3;

		public static final int THUNDER = 4;

		public static final int HOLY = 5;

		public static final int DARK = 6;

		public static final int POISON = 7;
	}

	/**
	 * 额外属性子系统全局开关
	 */
	public static class SystemSwitch {
		// 有疲劳值
		public static boolean ENABLE_BREAK_SYSTEM = true;
		// 有属性相克
		public static boolean ENABLE_ELEMENT_SYSTEM = true;
		// 有士气影响
		public static boolean ENABLE_MORALE_SYSTEM = true;
		// 有格挡机制
		public static boolean ENABLE_BLOCK_SYSTEM = true;
		// 有反击机制
		public static boolean ENABLE_COUNTER_SYSTEM = true;
		// 有等级压制
		public static boolean ENABLE_LEVEL_PRESSURE = true;
		// 有魔力护盾
		public static boolean ENABLE_MANA_SHIELD = true;
		// 有吸血功能
		public static boolean ENABLE_LIFE_STEAL = true;
		// 有伤害反弹
		public static boolean ENABLE_DAMAGE_REFLECT = true;
		// 有兵种相克
		public static boolean ENABLE_UNIT_TYPE_ADVANTAGE = true;
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
	protected int unitType;
	protected JobType jobType;

	// 负面状态
	protected boolean isPoisoned;
	protected boolean isWounded;
	protected boolean isParalyzed;
	protected int poisonDamage;
	protected int poisonDuration;
	protected int woundDefenceReduce;
	protected int woundDuration;
	protected int paralyzeDuration;

	// 战斗属性
	protected int dodgeRate;
	protected int defencePenetration;
	protected int critRate;
	protected int critDamage;
	protected int healBonus;
	protected int damageReduce;
	protected int baseAttackRange; // 基础攻击距离
	protected int baseSkillRange; // 特技攻击距离 (特技本身也有攻击范围，是指能波及周围几格)

	// 额外参数
	protected int morale; // 士气
	protected int counterAttackCount; // 反击次数
	protected boolean isDefendStance; // 防御架势(防御加成)
	protected int blockRate; // 格挡率
	protected int blockDamageReduce; // 格挡减伤
	protected int weaponElementType; // 武器属性
	protected int[] resistElements; // 各属性耐性 0~200%
	protected int[] weakElements; // 弱点属性
	protected int manaShield; // 魔力护盾（魔力抵消伤害）
	protected int killExpBonus; // 击杀敌人额外经验
	protected int breakCount; // 疲劳累积
	protected int breakResist; // 疲劳抗性
	protected boolean isBroken; // 处于疲劳状态（防御为0、无法行动）
	protected int breakDuration;
	protected boolean isFocus; // 会心状态（下次攻击命中与暴击增加）
	protected boolean isCounterStrike; // 反击姿态
	protected boolean canCounterAttack = true;
	protected boolean isNaturalRegen = false;
	protected int damageReflect; // 伤害反弹
	protected int lifeSteal; // 吸血

	protected int damageTakenMultiplier = 100;
	protected int advantageAddDamage = 125;
	protected int advantageSubDamage = 75;

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

		initAllExtraAttributes();
	}

	private void initAllExtraAttributes() {
		// 战斗基础
		dodgeRate = agility / 3;
		defencePenetration = 0;
		critRate = 5;
		critDamage = 150;
		healBonus = 0;
		damageReduce = 0;
		morale = 100;
		counterAttackCount = 1;
		isDefendStance = false;
		blockRate = agility / 4;
		blockDamageReduce = 30;
		// 默认元素
		weaponElementType = Element.NONE;
		resistElements = new int[8];
		weakElements = new int[8];
		for (int i = 0; i < 8; i++) {
			resistElements[i] = 100;
			weakElements[i] = 100;
		}
		manaShield = 0;
		killExpBonus = 10;
		// 疲劳
		breakCount = 0;
		breakResist = 5;
		isBroken = false;
		breakDuration = 0;
		// 是否会心状态
		isFocus = false;
		isCounterStrike = false;

		damageReflect = 0;
		lifeSteal = 0;
		damageTakenMultiplier = 100;
		clearNegativeStates();
	}

	public int getUnitTypeAdvantageMultiplier(RoleValue target) {
		if (!SystemSwitch.ENABLE_UNIT_TYPE_ADVANTAGE || target == null) {
			return 100;
		}
		// 我方对敌克制存在，增伤默认25%
		if (hasAdvantageOver(target)) {
			return advantageAddDamage;
		}
		// 敌方对我克制存在,减伤默认25%
		if (target.hasAdvantageOver(this)) {
			return advantageSubDamage;
		}
		return 100;
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

		// 魔力护盾（开关控制）
		if (SystemSwitch.ENABLE_MANA_SHIELD && manaShield > 0 && mana > 0) {
			int absorb = MathUtils.min((int) damageTaken, mana);
			damageTaken -= absorb;
			mana -= absorb;
		}

		float finalDefence = defence;

		// 若极度疲劳
		if (SystemSwitch.ENABLE_BREAK_SYSTEM && isBroken) {
			// 防御减半
			finalDefence = finalDefence / 2;
			// 同时额外增伤50%
			damageTaken *= 1.5f;
		}

		// 防御减伤运算
		float defenceReduction = MathUtils.max(finalDefence * 0.5f, 0);
		damageTaken = MathUtils.max(damageTaken - defenceReduction, 1);

		// 全局伤害倍率
		damageTaken = damageTaken * damageTakenMultiplier / 100f;

		// 伤害反弹
		if (SystemSwitch.ENABLE_DAMAGE_REFLECT && damageReflect > 0) {
			int reflectDmg = MathUtils.ifloor(damageTaken * damageReflect / 100f);
			if (reflectDmg > 0 && !isDead) {
				damage(reflectDmg);
			}
		}

		// 扣血
		this.health = MathUtils.ifloor(this.health - damageTaken);
		if (this.health <= 0) {
			this.health = 0;
			this.die();
		}

		// 吸血系统
		if (SystemSwitch.ENABLE_LIFE_STEAL && lifeSteal > 0 && damageTaken > 0) {
			int healVal = MathUtils.ifloor(damageTaken * lifeSteal / 100f);
			changeHeal(healVal);
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
		this.dodgeRate = agility / 3;
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
		clearNegativeStates();
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
		clearNegativeStates();
		initAllExtraAttributes();
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

	public void clearAllActionState() {
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

	public int getUnitType() {
		return unitType;
	}

	public void setUnitType(int unitType) {
		this.unitType = unitType;
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
		this.actionPoints = 10;
		this.isAttack = false;
		this.isDefense = false;
		this.isSkill = false;
		this.isMoved = false;
		this.isDead = false;
		this.isInvincible = false;
		this.clearAllActionState();
		clearNegativeStates();
		initAllExtraAttributes();
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
		clearNegativeStates();
		return this;
	}

	public boolean isFullMana() {
		return this.mana == this.maxMana;
	}

	public boolean canAction() {
		boolean paralyzeCheck = !SystemSwitch.ENABLE_BREAK_SYSTEM || !isParalyzed;
		return isAlive() && !isLocked() && paralyzeCheck && !isBroken;
	}

	public int getHitRateBonus() {
		return hitRateBonus;
	}

	public void setHitRateBonus(int hitRateBonus) {
		this.hitRateBonus = hitRateBonus;
	}

	public RoleValue applyJobAttributes() {
		if (jobType == null) {
			return this;
		}
		JobTemplate tpl = JobTree.get(jobType);
		if (tpl == null) {
			return this;
		}
		this.maxHealth = tpl.maxHealth;
		this.maxMana = tpl.maxMana;
		this.attack = tpl.attack;
		this.defence = tpl.defence;
		this.strength = tpl.strength;
		this.intelligence = tpl.intelligence;
		this.agility = tpl.agility;
		this.fitness = tpl.fitness;
		this.dexterity = tpl.dexterity;
		this.movePoints = tpl.movePoints;
		this.health = this.maxHealth;
		this.mana = this.maxMana;
		if (jobType.isFastMove()) {
			this.movePoints += 2;
		}
		if (jobType.isFlyOver()) {
			this.movePoints += 3;
		}
		if (jobType.isHeavyArmor()) {
			this.defence += 3;
		}
		undoneAction();
		return this;
	}

	/**
	 * 最终命中率计算
	 * 
	 * @param target
	 * @return
	 */
	public int getFinalHitRate(RoleValue target) {
		int baseHit = hit(target.getDexterity(), target.getAgility(), target.getFitness());
		// 士气惩罚
		if (SystemSwitch.ENABLE_MORALE_SYSTEM && isMoraleLow()) {
			baseHit -= 20;
		}
		int finalHit = baseHit - target.getDodgeRate();
		return MathUtils.clamp(finalHit, 0, 100);
	}

	/**
	 * 最终闪避率
	 */
	public int getDodgeRate() {
		int finalDodge = dodgeRate + (isWounded ? -woundDefenceReduce : 0);
		return MathUtils.clamp(finalDodge, 0, 100);
	}

	public void setDodgeRate(int dodgeRate) {
		this.dodgeRate = MathUtils.max(dodgeRate, 0);
	}

	/**
	 * 攻击伤害判定
	 */
	public int attackTarget(RoleValue target) {
		if (!canAction() || target.isInvincible() || target.isDead()) {
			return 0;
		}
		// 命中判定
		int hitRoll = MathUtils.nextInt(100);
		int finalHit = getFinalHitRate(target);
		if (hitRoll > finalHit) {
			// 闪避则无伤
			return 0;
		}
		// 格挡(击中但不破防)判定
		if (SystemSwitch.ENABLE_BLOCK_SYSTEM && target.tryBlock()) {
			return -1;
		}
		// 疲劳积累
		if (SystemSwitch.ENABLE_BREAK_SYSTEM) {
			target.addBreak(10);
		}
		// 计算最终伤害
		int damage = calculateFinalDamage(target);

		// 等级压制
		if (SystemSwitch.ENABLE_LEVEL_PRESSURE) {
			float lvlFactor = getLevelFactor(target);
			damage = MathUtils.ifloor(damage * lvlFactor);
		}

		// 元素倍率
		if (SystemSwitch.ENABLE_ELEMENT_SYSTEM) {
			int eleMul = target.getElementMultiplier(weaponElementType);
			damage = MathUtils.ifloor(damage * eleMul / 100f);
		}

		// 暴击
		boolean crit = MathUtils.nextInt(100) < (isFocus ? critRate + 30 : critRate);
		if (crit) {
			damage = damage * critDamage / 100;
		}
		target.damage(damage);
		isFocus = false;
		return damage;
	}

	/**
	 * 最终伤害计算
	 * 
	 * @param target
	 * @return
	 */
	public int calculateFinalDamage(RoleValue target) {
		int realDefence = MathUtils.max(target.getDefence() - defencePenetration, 0);
		float damage = attack + 0.5f * strength - 0.5f * realDefence;
		damage = variance(damage, 20, true);
		damage *= (100 - target.damageReduce) / 100f;
		// 若兵种相克存在
		int advantageMul = getUnitTypeAdvantageMultiplier(target);
		damage *= advantageMul / 100f;
		return MathUtils.max(MathUtils.ifloor(damage), 1);
	}

	/**
	 * 治疗
	 * 
	 * @param manaCost
	 * @param baseHeal
	 * @return
	 */
	public RoleValue healAdvanced(int manaCost, int baseHeal) {
		if (isDead || mana < manaCost || isParalyzed) {
			return this;
		}
		int bonusHeal = (int) (intelligence * 0.3f + healBonus);
		int totalHeal = baseHeal + bonusHeal;
		totalHeal = MathUtils.ifloor(variance(totalHeal, 15, true));
		changeHeal(totalHeal);
		mana -= manaCost;
		return this;
	}

	/**
	 * 自然恢复（每回合少量回血回蓝）
	 */
	public RoleValue naturalRegen() {
		if (isDead || isPoisoned) {
			return this;
		}
		if ((SystemSwitch.ENABLE_BREAK_SYSTEM && isBroken) || isParalyzed) {
			return this;
		}
		int hpRegen = fitness / 5;
		changeHeal(hpRegen);
		regenerateMana();
		return this;
	}

	/**
	 * 中毒，持续掉血，无法自然回血
	 * 
	 * @param damage
	 * @param duration
	 * @return
	 */
	public RoleValue applyPoison(int damage, int duration) {
		if (isDead || isInvincible) {
			return this;
		}
		isPoisoned = true;
		poisonDamage = MathUtils.max(damage, 1);
		poisonDuration = MathUtils.max(duration, 1);
		return this;
	}

	/**
	 * 重伤，降低防御、闪避
	 * 
	 * @param defenceReduce
	 * @param duration
	 * @return
	 */
	public RoleValue applyWound(int defenceReduce, int duration) {
		if (isDead) {
			return this;
		}
		isWounded = true;
		woundDefenceReduce = MathUtils.max(defenceReduce, 0);
		woundDuration = MathUtils.max(duration, 1);
		return this;
	}

	/**
	 * 麻痹,无法行动
	 * 
	 * @param duration
	 * @return
	 */
	public RoleValue applyParalyze(int duration) {
		if (isDead)
			return this;
		isParalyzed = true;
		paralyzeDuration = MathUtils.max(duration, 1);
		doneAction();
		return this;
	}

	/**
	 * 每回合负面状态生效（必须在回合结束调用）
	 */
	public void onTurnEndNegativeEffect() {
		if (isDead) {
			return;
		}
		// 中毒
		if (isPoisoned) {
			damage(poisonDamage);
			poisonDuration--;
			if (poisonDuration <= 0) {
				clearPoison();
			}
		}
		// 重伤
		if (isWounded) {
			woundDuration--;
			if (woundDuration <= 0) {
				clearWound();
			}
		}
		// 麻痹
		if (isParalyzed) {
			paralyzeDuration--;
			if (paralyzeDuration <= 0) {
				clearParalyze();
			}
		}
	}

	/**
	 * 清除所有负面状态
	 */
	public void clearNegativeStates() {
		clearPoison();
		clearWound();
		clearParalyze();
	}

	public void clearPoison() {
		isPoisoned = false;
		poisonDamage = 0;
		poisonDuration = 0;
	}

	public void clearWound() {
		isWounded = false;
		woundDefenceReduce = 0;
		woundDuration = 0;
	}

	public void clearParalyze() {
		isParalyzed = false;
		paralyzeDuration = 0;
		undoneAction();
	}

	/**
	 * 士气（类三国游戏必备）
	 * 
	 * @param v
	 */
	public void addMorale(int v) {
		if (!SystemSwitch.ENABLE_MORALE_SYSTEM) {
			return;
		}
		morale = MathUtils.clamp(morale + v, 0, 100);
	}

	/**
	 * 士气低落
	 * 
	 * @return
	 */
	public boolean isMoraleLow() {
		if (!SystemSwitch.ENABLE_MORALE_SYSTEM) {
			return false;
		}
		return morale <= 30;
	}

	public boolean tryBlock() {
		if (!SystemSwitch.ENABLE_BLOCK_SYSTEM) {
			return false;
		}
		if (isBroken || isMoraleLow()) {
			return false;
		}
		int rate = blockRate;
		if (isDefendStance) {
			rate += 20;
		}
		return MathUtils.nextInt(100) < rate;
	}

	/**
	 * 增加疲劳
	 * 
	 * @param value
	 */
	public void addBreak(int value) {
		if (!SystemSwitch.ENABLE_BREAK_SYSTEM || isBroken) {
			return;
		}
		breakCount += MathUtils.max(value - breakResist, 1);
		if (breakCount >= 100) {
			isBroken = true;
			breakDuration = 1;
			breakCount = 0;
		}
	}

	public void onTurnEndBreak() {
		if (!SystemSwitch.ENABLE_BREAK_SYSTEM || !isBroken) {
			return;
		}
		breakDuration--;
		if (breakDuration <= 0) {
			isBroken = false;
		}
	}

	public boolean canCounter() {
		if (!SystemSwitch.ENABLE_COUNTER_SYSTEM) {
			return false;
		}
		return canCounterAttack && !isDead && !isBroken && !isParalyzed && !isMoraleLow() && counterAttackCount > 0;
	}

	public void doCounterAttack(RoleValue target) {
		if (!canCounter()) {
			return;
		}
		int dmg = attackTarget(target);
		if (dmg > 0) {
			counterAttackCount--;
		}
	}

	public int getElementMultiplier(int element) {
		if (!SystemSwitch.ENABLE_ELEMENT_SYSTEM) {
			return 100;
		}
		if (element == Element.NONE) {
			return 100;
		}
		int res = resistElements[element];
		int weak = weakElements[element];
		return MathUtils.clamp(weak - res, 50, 200);
	}

	public void setElementResist(int element, int value) {
		if (!SystemSwitch.ENABLE_ELEMENT_SYSTEM) {
			return;
		}
		resistElements[element] = MathUtils.clamp(value, 0, 200);
	}

	public void setElementWeak(int element, int value) {
		if (!SystemSwitch.ENABLE_ELEMENT_SYSTEM) {
			return;
		}
		weakElements[element] = MathUtils.clamp(value, 100, 200);
	}

	public void enterDefendStance() {
		isDefendStance = true;
		damageReduce += 30;
		blockRate += 15;
	}

	public void exitDefendStance() {
		isDefendStance = false;
		damageReduce = MathUtils.max(damageReduce - 30, 0);
		blockRate = agility / 4;
	}

	public void focus() {
		focus(30, 20);
	}

	/**
	 * 会心状态 (命中与暴击增加)
	 * 
	 * @param hit
	 * @param crit
	 */
	public void focus(int hit, int crit) {
		isFocus = true;
		hitRateBonus += hit;
		critRate += crit;
	}

	/**
	 * 等级压制
	 * 
	 * @param target
	 * @return
	 */
	public float getLevelFactor(RoleValue target) {
		if (!SystemSwitch.ENABLE_LEVEL_PRESSURE) {
			return 1.0f;
		}
		int diff = level - target.level;
		if (diff >= 10) {
			return 1.5f;
		}
		if (diff <= -10) {
			return 0.6f;
		}
		return 1.0f + diff * 0.02f;
	}

	/**
	 * 回合开始统一更新（需要手动调用）
	 */
	public void onTurnBegin() {
		if (isDead) {
			return;
		}
		undoneAction();
		this.actionPoints = 10;
		this.turnPoints = 0;
		this.isFocus = false;
		this.isDefendStance = false;
		exitDefendStance();
		if (SystemSwitch.ENABLE_BREAK_SYSTEM) {
			if (isBroken && breakDuration <= 0) {
				isBroken = false;
				breakCount = 0;
			}
		}
		if (isParalyzed && paralyzeDuration <= 0) {
			clearParalyze();
		}
	}

	/**
	 * 回合结束统一更新（需要手动调用）
	 */
	public void onTurnEnd() {
		onTurnEndNegativeEffect();
		if (SystemSwitch.ENABLE_BREAK_SYSTEM) {
			onTurnEndBreak();
		}
		if (isNaturalRegen) {
			naturalRegen();
		}
		exitDefendStance();
		counterAttackCount = 1;
	}

	public boolean isDisabled() {
		return isDead || isParalyzed;
	}

	public boolean isPoisoned() {
		return isPoisoned;
	}

	public boolean isWounded() {
		return isWounded;
	}

	public boolean isParalyzed() {
		return isParalyzed;
	}

	public int getDefencePenetration() {
		return defencePenetration;
	}

	public void setDefencePenetration(int dp) {
		defencePenetration = MathUtils.max(dp, 0);
	}

	public int getCritRate() {
		return critRate;
	}

	public void setCritRate(int crit) {
		critRate = MathUtils.clamp(crit, 0, 100);
	}

	public int getCritDamage() {
		return critDamage;
	}

	public void setCritDamage(int cd) {
		critDamage = MathUtils.max(cd, 100);
	}

	public int getHealBonus() {
		return healBonus;
	}

	public void setHealBonus(int bonus) {
		healBonus = MathUtils.max(bonus, 0);
	}

	public int getDamageReduce() {
		return damageReduce;
	}

	public void setDamageReduce(int dr) {
		damageReduce = MathUtils.clamp(dr, 0, 80);
	}

	public int getMorale() {
		return morale;
	}

	public void setMorale(int m) {
		morale = MathUtils.clamp(m, 0, 100);
	}

	public int getCounterAttackCount() {
		return counterAttackCount;
	}

	public void setCounterAttackCount(int c) {
		counterAttackCount = MathUtils.max(c, 0);
	}

	public boolean isDefendStance() {
		return isDefendStance;
	}

	public int getBlockRate() {
		return blockRate;
	}

	public void setBlockRate(int b) {
		blockRate = MathUtils.max(b, 0);
	}

	public int getBlockDamageReduce() {
		return blockDamageReduce;
	}

	public void setBlockDamageReduce(int b) {
		blockDamageReduce = MathUtils.max(b, 0);
	}

	public int getWeaponElementType() {
		return weaponElementType;
	}

	public void setWeaponElementType(int w) {
		weaponElementType = w;
	}

	public int[] getResistElements() {
		return resistElements;
	}

	public int[] getWeakElements() {
		return weakElements;
	}

	public int getManaShield() {
		return manaShield;
	}

	public void setManaShield(int m) {
		manaShield = MathUtils.max(m, 0);
	}

	public int getKillExpBonus() {
		return killExpBonus;
	}

	public void setKillExpBonus(int k) {
		killExpBonus = MathUtils.max(k, 0);
	}

	public int getBreakCount() {
		return breakCount;
	}

	public void setBreakCount(int b) {
		breakCount = MathUtils.max(b, 0);
	}

	public int getBreakResist() {
		return breakResist;
	}

	public void setBreakResist(int b) {
		breakResist = MathUtils.max(b, 0);
	}

	public boolean isBroken() {
		return isBroken;
	}

	public int getBreakDuration() {
		return breakDuration;
	}

	public boolean isNaturalRegen() {
		return isNaturalRegen;
	}

	public void setNaturalRegen(boolean naturalRegen) {
		this.isNaturalRegen = naturalRegen;
	}

	public float getHpRate() {
		return health / maxHealth;
	}

	public float getMpRate() {
		return mana / maxMana;
	}

	public boolean isFocus() {
		return isFocus;
	}

	public boolean isCounterStrike() {
		return isCounterStrike;
	}

	public void setCounterStrike(boolean c) {
		isCounterStrike = c;
	}

	public boolean isCanCounterAttack() {
		return canCounterAttack;
	}

	public void setCanCounterAttack(boolean c) {
		canCounterAttack = c;
	}

	public int getDamageReflect() {
		return damageReflect;
	}

	public void setDamageReflect(int d) {
		damageReflect = MathUtils.max(d, 0);
	}

	public int getLifeSteal() {
		return lifeSteal;
	}

	public void setLifeSteal(int ls) {
		lifeSteal = MathUtils.max(ls, 0);
	}

	public int getDamageTakenMultiplier() {
		return damageTakenMultiplier;
	}

	public void setDamageTakenMultiplier(int d) {
		damageTakenMultiplier = MathUtils.max(d, 50);
	}

	public int getPoisonDamage() {
		return poisonDamage;
	}

	public int getPoisonDuration() {
		return poisonDuration;
	}

	public int getWoundDefenceReduce() {
		return woundDefenceReduce;
	}

	public int getWoundDuration() {
		return woundDuration;
	}

	public int getParalyzeDuration() {
		return paralyzeDuration;
	}

	public boolean isAllyOf(RoleValue o) {
		if (o == null) {
			return false;
		}
		return team == o.team || Team.Ally == o.team;
	}

	public boolean isEnemyOf(RoleValue o) {
		if (o == null) {
			return false;
		}
		return (team != o.team && team != Team.Ally);
	}

	public int getBaseAttackRange() {
		return baseAttackRange;
	}

	public void setBaseAttackRange(int baseAttackRange) {
		this.baseAttackRange = baseAttackRange;
	}

	public int getBaseSkillRange() {
		return baseSkillRange;
	}

	public void setBaseSkillRange(int baseSkillRange) {
		this.baseSkillRange = baseSkillRange;
	}

	public boolean isInfantry() {
		return UnitType.hasType(unitType, UnitType.INFANTRY);
	}

	public boolean isCavalry() {
		return UnitType.hasType(unitType, UnitType.CAVALRY);
	}

	public boolean isFlying() {
		return UnitType.hasType(unitType, UnitType.FLY);
	}

	public boolean isArmor() {
		return UnitType.hasType(unitType, UnitType.ARMOR);
	}

	public boolean isHooves() {
		return UnitType.hasType(unitType, UnitType.HOOVES);
	}

	public boolean isMagic() {
		return UnitType.hasType(unitType, UnitType.MAGIC);
	}

	public boolean isArcher() {
		return UnitType.hasType(unitType, UnitType.ARCHER);
	}

	public boolean isRange() {
		return UnitType.hasType(unitType, UnitType.RANGE);
	}

	public boolean isSpearman() {
		return UnitType.hasType(unitType, UnitType.SPEARMAN);
	}

	public boolean isHealer() {
		return UnitType.hasType(unitType, UnitType.HEALER);
	}

	public boolean isNaval() {
		return UnitType.hasType(unitType, UnitType.NAVAL);
	}

	public boolean isUndead() {
		return UnitType.hasType(unitType, UnitType.UNDEAD);
	}

	public int getAdvantageAddDamage() {
		return advantageAddDamage;
	}

	public void setAdvantageAddDamage(int advantageAddDamage) {
		this.advantageAddDamage = advantageAddDamage;
	}

	public int getAdvantageSubDamage() {
		return advantageSubDamage;
	}

	public void setAdvantageSubDamage(int advantageSubDamage) {
		this.advantageSubDamage = advantageSubDamage;
	}

	/**
	 * 判断当前单位是否对另一个单位有兵种克制优势
	 * 
	 * @param target
	 * @return
	 */
	public boolean hasAdvantageOver(RoleValue target) {
		if (target == null) {
			return false;
		}
		int atk = this.unitType;
		int def = target.unitType;
		// 长枪克骑兵
		if (UnitType.hasType(atk, UnitType.SPEARMAN) && UnitType.hasType(def, UnitType.CAVALRY)) {
			return true;
		}
		// 骑兵克步兵/远程/魔法
		if (UnitType.hasType(atk, UnitType.CAVALRY) && (UnitType.hasType(def, UnitType.INFANTRY)
				|| UnitType.hasType(def, UnitType.RANGE) || UnitType.hasType(def, UnitType.MAGIC))) {
			return true;
		}
		// 弓箭/远程克步兵/飞行
		if ((UnitType.hasType(atk, UnitType.ARCHER) || UnitType.hasType(atk, UnitType.RANGE))
				&& (UnitType.hasType(def, UnitType.INFANTRY) || UnitType.hasType(def, UnitType.FLY))) {
			return true;
		}
		// 魔法克重甲/飞行
		if (UnitType.hasType(atk, UnitType.MAGIC)
				&& (UnitType.hasType(def, UnitType.ARMOR) || UnitType.hasType(def, UnitType.FLY))) {
			return true;
		}
		// 重甲克长枪
		if (UnitType.hasType(atk, UnitType.ARMOR) && UnitType.hasType(def, UnitType.SPEARMAN)) {
			return true;
		}
		// 飞行克重甲/海军
		if (UnitType.hasType(atk, UnitType.FLY)
				&& (UnitType.hasType(def, UnitType.ARMOR) || UnitType.hasType(def, UnitType.NAVAL))) {
			return true;
		}
		// 魔兽克步兵/骑兵
		if (UnitType.hasType(atk, UnitType.HOOVES)
				&& (UnitType.hasType(def, UnitType.INFANTRY) || UnitType.hasType(def, UnitType.CAVALRY))) {
			return true;
		}
		// 治疗克亡灵
		if (UnitType.hasType(atk, UnitType.HEALER) && UnitType.hasType(def, UnitType.UNDEAD)) {
			return true;
		}
		// 海军克骑兵/步兵/重甲（水域，游戏中需要额外判定，结合地形参数）
		if (UnitType.hasType(atk, UnitType.NAVAL) && (UnitType.hasType(def, UnitType.CAVALRY)
				|| UnitType.hasType(def, UnitType.INFANTRY) || UnitType.hasType(def, UnitType.ARMOR))) {
			return true;
		}
		return false;
	}

}
