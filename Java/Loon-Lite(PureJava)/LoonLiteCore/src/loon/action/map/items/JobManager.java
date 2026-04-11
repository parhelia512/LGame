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

import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

/**
 * 职业管理器（线程安全单例）
 */
public class JobManager {

	private static JobManager instance;

	private JobManager() {
	}

	public static JobManager getInstance() {
		if (instance == null) {
			synchronized (JobManager.class) {
				if (instance == null) {
					instance = new JobManager();
				}
			}
		}
		return instance;
	}

	public void apply(JobType job, RoleValue role) {
		JobTemplate t = JobTree.get(job);
		if (t == null) {
			return;
		}
		role.setMaxHealth(t.maxHealth);
		role.setMaxMana(t.maxMana);
		role.setAttack(t.attack);
		role.setDefence(t.defence);
		role.setStrength(t.strength);
		role.setIntelligence(t.intelligence);
		role.setAgility(t.agility);
		role.setFitness(t.fitness);
		role.setDexterity(t.dexterity);
		role.setMovePoints(t.movePoints);
		// 特性加成
		if (job.isFastMove()) {
			role.setMovePoints(role.getMovePoints() + 2);
		}
		if (job.isFlyOver()) {
			role.setMovePoints(role.getMovePoints() + 1);
		}
		if (job.isHeavyArmor()) {
			role.setDefence(role.getDefence() + 3);
		}
		role.setHealth(role.getMaxHealth());
		role.setMana(role.getMaxMana());
	}

	public void levelUp(RoleValue role, JobType job) {
		JobTemplate t = JobTree.get(job);
		if (t == null) {
			return;
		}
		if (MathUtils.random() < t.growHp) {
			role.setMaxHealth(role.getMaxHealth() + 1);
			role.setHealth(role.getHealth() + 1);
		}
		if (MathUtils.random() < t.growMp) {
			role.setMaxMana(role.getMaxMana() + 1);
			role.setMana(role.getMana() + 1);
		}
		if (MathUtils.random() < t.growAtk) {
			role.setAttack(role.getAttack() + 1);
		}
		if (MathUtils.random() < t.growDef) {
			role.setDefence(role.getDefence() + 1);
		}
		if (MathUtils.random() < t.growStr) {
			role.setStrength(role.getStrength() + 1);
		}
		if (MathUtils.random() < t.growInt) {
			role.setIntelligence(role.getIntelligence() + 1);
		}
		if (MathUtils.random() < t.growAgi) {
			role.setAgility(role.getAgility() + 1);
		}
		if (MathUtils.random() < t.growFit) {
			role.setFitness(role.getFitness() + 1);
		}
		if (MathUtils.random() < t.growDex) {
			role.setDexterity(role.getDexterity() + 1);
		}
	}

	/**
	 * 转职判断
	 * 
	 * @param role
	 * @param from
	 * @param to
	 * @return
	 */
	public boolean canPromote(RoleValue role, JobType from, JobType to) {
		JobTemplate t = JobTree.get(from);
		return t != null && t.promotionTargets.contains(to) && role.getLevel() >= t.needLevel;
	}

	/**
	 * 转职
	 * 
	 * @param role
	 * @param from
	 * @param to
	 * @return
	 */
	public boolean promote(RoleValue role, JobType from, JobType to) {
		if (!canPromote(role, from, to)) {
			return false;
		}
		apply(to, role);
		return true;
	}

	/**
	 * 获取可转职业
	 * 
	 * @param job
	 * @return
	 */
	public TArray<JobType> getPromoteList(JobType job) {
		JobTemplate t = JobTree.get(job);
		return t == null ? new TArray<JobType>() : t.promotionTargets;
	}

	public JobTemplate getJobTemplate(JobType job) {
		return JobTree.get(job);
	}

	/**
	 * 获取角色当前属性快照
	 * 
	 * @param role
	 * @return
	 */
	public ObjectMap<String, Integer> getCurrentStats(RoleValue role) {
		ObjectMap<String, Integer> stats = new ObjectMap<String, Integer>();
		stats.put("HP", role.getHealth());
		stats.put("MP", role.getMana());
		stats.put("ATK", role.getAttack());
		stats.put("DEF", role.getDefence());
		stats.put("STR", role.getStrength());
		stats.put("INT", role.getIntelligence());
		stats.put("AGI", role.getAgility());
		stats.put("FIT", role.getFitness());
		stats.put("DEX", role.getDexterity());
		stats.put("MOVE", role.getMovePoints());
		return stats;
	}

	/**
	 * 重置到职业初始状态
	 * 
	 * @param job
	 * @param role
	 */
	public void resetToBase(JobType job, RoleValue role) {
		apply(job, role);
		role.setLevel(1);
	}

	public boolean randomPromote(RoleValue role, JobType from) {
		TArray<JobType> list = getPromoteList(from);
		if (list.isEmpty()) {
			return false;
		}
		JobType to = list.get(MathUtils.nextInt(list.size()));
		return promote(role, from, to);
	}

	/**
	 * 获得职业转职路径
	 * 
	 * @param job
	 * @return
	 */
	public TArray<JobType> getPromotionPath(JobType job) {
		TArray<JobType> path = new TArray<JobType>();
		JobTemplate t = JobTree.get(job);
		if (t != null) {
			path.add(job);
			for (JobType next : t.promotionTargets) {
				path.addAll(getPromotionPath(next));
			}
		}
		return path;
	}
}
