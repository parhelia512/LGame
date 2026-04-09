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

import loon.utils.TArray;

/**
 * 职业模板 = 初始属性 + 成长率 + 转职路线 + 移动力
 */
public class JobTemplate {

	public TArray<JobType> promotionTargets = new TArray<JobType>();

	private final JobType jobType;

	// 初始属性
	public int maxHealth;
	public int maxMana;
	public int attack;
	public int defence;
	public int strength;
	public int intelligence;
	public int agility;
	public int fitness;
	public int dexterity;
	public int movePoints;

	// 成长率（0~1）
	public float growHp = 0.5f;
	public float growMp = 0.5f;
	public float growAtk = 0.5f;
	public float growDef = 0.5f;
	public float growStr = 0.5f;
	public float growInt = 0.5f;
	public float growAgi = 0.5f;
	public float growFit = 0.5f;
	public float growDex = 0.5f;

	// 转职
	public int needLevel = 10;

	public JobTemplate(JobType jobType) {
		this.jobType = jobType;
	}

	public JobType getJobType() {
		return jobType;
	}
}
