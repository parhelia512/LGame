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
package loon.action.map.items;

import loon.action.map.items.RoleValue.MoveType;

/**
 * 角色职业
 */
public class JobType {

	public static class MoveFlag {

		public static final int NONE = 0;

		public static final int FAST_MOVE = 1;

		public static final int FLY_OVER = 2;

		public static final int HEAVY_ARMOR = 4;

		public static final int MAGIC_FLOAT = 8;
	}

	public static final JobType NOVICE = new JobType(0, "Novice", 1, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType WARRIOR = new JobType(1, "Warrior", 1, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType KNIGHT = new JobType(2, "Knight", 1, MoveType.CAVALRY, MoveFlag.FAST_MOVE);
	public static final JobType MAGE = new JobType(3, "Mage", 1, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType ARCHER = new JobType(4, "Archer", 1, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType THIEF = new JobType(5, "Thief", 1, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType CLERIC = new JobType(6, "Cleric", 1, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType PEGASUS = new JobType(7, "Pegasus Knight", 2, MoveType.FLY, MoveFlag.FLY_OVER);
	public static final JobType DRAGON_KNIGHT = new JobType(8, "Dragon Knight", 3, MoveType.FLY, MoveFlag.FLY_OVER);
	public static final JobType PALADIN = new JobType(9, "Paladin", 2, MoveType.CAVALRY, MoveFlag.FAST_MOVE);
	public static final JobType SWORD_MASTER = new JobType(10, "Swordmaster", 2, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType ARCHMAGE = new JobType(11, "Archmage", 3, MoveType.MAGIC, MoveFlag.MAGIC_FLOAT);
	public static final JobType PRINCESS = new JobType(20, "Princess", 2, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType MAID = new JobType(21, "Maid", 2, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType BERSERKER = new JobType(30, "Berserker", 2, MoveType.INFANTRY, MoveFlag.HEAVY_ARMOR);
	public static final JobType DARK_KNIGHT = new JobType(31, "Dark Knight", 3, MoveType.CAVALRY,
			MoveFlag.FAST_MOVE | MoveFlag.HEAVY_ARMOR);
	public static final JobType NECROMANCER = new JobType(32, "Necromancer", 3, MoveType.MAGIC, MoveFlag.MAGIC_FLOAT);
	public static final JobType SUMMONER = new JobType(33, "Summoner", 3, MoveType.MAGIC, MoveFlag.MAGIC_FLOAT);
	public static final JobType MONK = new JobType(34, "Monk", 2, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType ASSASSIN = new JobType(35, "Assassin", 3, MoveType.INFANTRY, MoveFlag.FAST_MOVE);
	public static final JobType RANGER = new JobType(36, "Ranger", 2, MoveType.INFANTRY, MoveFlag.FAST_MOVE);
	public static final JobType VALKYRIE = new JobType(37, "Valkyrie", 3, MoveType.CAVALRY, MoveFlag.FAST_MOVE);
	public static final JobType SAMURAI = new JobType(38, "Samurai", 3, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType NINJA = new JobType(39, "Ninja", 2, MoveType.INFANTRY, MoveFlag.FAST_MOVE);
	public static final JobType BEASTMASTER = new JobType(40, "Beastmaster", 3, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType TEMPLAR = new JobType(41, "Templar", 3, MoveType.INFANTRY, MoveFlag.HEAVY_ARMOR);
	public static final JobType WARLOCK = new JobType(42, "Warlock", 4, MoveType.MAGIC, MoveFlag.MAGIC_FLOAT);
	public static final JobType HERO = new JobType(50, "Hero", 4, MoveType.INFANTRY, MoveFlag.NONE);
	public static final JobType LEGENDARY_DRAGON = new JobType(60, "Legendary Dragon", 5, MoveType.FLY,
			MoveFlag.FLY_OVER | MoveFlag.HEAVY_ARMOR);
	public static final JobType DEMON_KING = new JobType(99, "Demon King", 5, MoveType.MAGIC,
			MoveFlag.FLY_OVER | MoveFlag.MAGIC_FLOAT);

	private final int id;
	private final String name;
	private final int jobTier;
	private final int moveType;
	private final int moveFlag;

	public JobType(int id, String name, int jobTier, int moveType, int moveFlag) {
		this.id = id;
		this.name = name;
		this.jobTier = jobTier;
		this.moveType = moveType;
		this.moveFlag = moveFlag;
	}

	public int getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getJobTier() {
		return jobTier;
	}

	public int getMoveType() {
		return moveType;
	}

	public int getMoveFlag() {
		return moveFlag;
	}

	public boolean isFastMove() {
		return (moveFlag & MoveFlag.FAST_MOVE) != 0;
	}

	public boolean isFlyOver() {
		return (moveFlag & MoveFlag.FLY_OVER) != 0;
	}

	public boolean isHeavyArmor() {
		return (moveFlag & MoveFlag.HEAVY_ARMOR) != 0;
	}

	public boolean isMagicFloat() {
		return (moveFlag & MoveFlag.MAGIC_FLOAT) != 0;
	}
}
