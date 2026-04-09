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

import loon.utils.IntMap;

public class JobTree {

	private static final IntMap<JobTemplate> TEMPLATES = new IntMap<JobTemplate>();

	// 默认职业设定
	static {
		// 战士
		JobTemplate warrior = new JobTemplate(JobType.WARRIOR);
		warrior.maxHealth = 320;
		warrior.maxMana = 50;
		warrior.attack = 18;
		warrior.defence = 16;
		warrior.strength = 12;
		warrior.movePoints = 4;
		warrior.growHp = 0.85f;
		warrior.growAtk = 0.75f;
		warrior.growStr = 0.8f;
		warrior.needLevel = 10;
		warrior.promotionTargets.add(JobType.SWORD_MASTER);
		warrior.promotionTargets.add(JobType.PALADIN);
		register(warrior);

		// 骑士
		JobTemplate knight = new JobTemplate(JobType.KNIGHT);
		knight.maxHealth = 380;
		knight.defence = 22;
		knight.fitness = 14;
		knight.movePoints = 6; // 骑兵移动更高
		knight.growDef = 0.8f;
		knight.needLevel = 10;
		knight.promotionTargets.add(JobType.PALADIN);
		knight.promotionTargets.add(JobType.DARK_KNIGHT);
		register(knight);

		// 天马骑士
		JobTemplate pegasus = new JobTemplate(JobType.PEGASUS);
		pegasus.maxHealth = 420;
		pegasus.agility = 25;
		pegasus.movePoints = 7;
		pegasus.growAgi = 0.95f;
		pegasus.needLevel = 20;
		pegasus.promotionTargets.add(JobType.DRAGON_KNIGHT);
		register(pegasus);

		// 魔术师
		JobTemplate mage = new JobTemplate(JobType.MAGE);
		mage.maxHealth = 160;
		mage.maxMana = 220;
		mage.intelligence = 18;
		mage.movePoints = 3;
		mage.growMp = 0.95f;
		mage.growInt = 0.98f;
		mage.needLevel = 10;
		mage.promotionTargets.add(JobType.ARCHMAGE);
		mage.promotionTargets.add(JobType.SUMMONER);
		register(mage);

		// 弓箭手
		JobTemplate archer = new JobTemplate(JobType.ARCHER);
		archer.maxHealth = 240;
		archer.attack = 20;
		archer.agility = 18;
		archer.movePoints = 4;
		archer.growAtk = 0.8f;
		archer.growAgi = 0.85f;
		archer.needLevel = 10;
		archer.promotionTargets.add(JobType.RANGER);
		register(archer);

		// 盗贼
		JobTemplate thief = new JobTemplate(JobType.THIEF);
		thief.maxHealth = 220;
		thief.attack = 16;
		thief.agility = 22;
		thief.movePoints = 5;
		thief.growAgi = 0.9f;
		thief.growAtk = 0.7f;
		thief.needLevel = 10;
		thief.promotionTargets.add(JobType.ASSASSIN);
		register(thief);

		// 僧侣
		JobTemplate cleric = new JobTemplate(JobType.CLERIC);
		cleric.maxHealth = 200;
		cleric.maxMana = 180;
		cleric.intelligence = 16;
		cleric.defence = 14;
		cleric.movePoints = 4;
		cleric.growMp = 0.9f;
		cleric.growInt = 0.85f;
		cleric.needLevel = 10;
		cleric.promotionTargets.add(JobType.MONK);
		cleric.promotionTargets.add(JobType.TEMPLAR);
		register(cleric);
	}

	public static void register(JobTemplate tmp) {
		TEMPLATES.put(tmp.getJobType().getId(), tmp);
	}

	public static JobTemplate get(JobType job) {
		return TEMPLATES.get(job.getId());
	}
}
