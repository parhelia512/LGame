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

import loon.utils.ObjectMap;
import loon.utils.TArray;

public class JobProgression {

	private static final ObjectMap<JobType, TArray<JobType>> progressionTree = new ObjectMap<JobType, TArray<JobType>>();

	static {
		addProgression(JobType.NOVICE, JobType.WARRIOR, JobType.MAGE, JobType.ARCHER, JobType.THIEF, JobType.CLERIC);
		addProgression(JobType.WARRIOR, JobType.KNIGHT, JobType.BERSERKER);
		addProgression(JobType.MAGE, JobType.SUMMONER, JobType.ARCHMAGE);
		addProgression(JobType.ARCHER, JobType.RANGER);
		addProgression(JobType.THIEF, JobType.ASSASSIN);
		addProgression(JobType.CLERIC, JobType.MONK, JobType.TEMPLAR);

		addProgression(JobType.KNIGHT, JobType.PALADIN, JobType.DARK_KNIGHT);
		addProgression(JobType.BERSERKER, JobType.SWORD_MASTER);
		addProgression(JobType.SUMMONER, JobType.NECROMANCER);
		addProgression(JobType.ARCHMAGE, JobType.WARLOCK);
		addProgression(JobType.RANGER, JobType.SAMURAI);
		addProgression(JobType.ASSASSIN, JobType.NINJA);
		addProgression(JobType.TEMPLAR, JobType.HERO);
		addProgression(JobType.DRAGON_KNIGHT, JobType.LEGENDARY_DRAGON);

		addProgression(JobType.PALADIN, JobType.VALKYRIE);
		addProgression(JobType.SWORD_MASTER, JobType.HERO);
		addProgression(JobType.WARLOCK, JobType.DEMON_KING);
		addProgression(JobType.HERO, JobType.DEMON_KING, JobType.LEGENDARY_DRAGON);
		addProgression(JobType.VALKYRIE, JobType.PRINCESS);
	}

	public static void addProgression(JobType from, JobType... toJobs) {
		TArray<JobType> evolutions = progressionTree.get(from);
		if (evolutions == null) {
			evolutions = new TArray<JobType>();
			progressionTree.put(from, evolutions);
		}
		for (int i = 0; i < toJobs.length; i++) {
			evolutions.add(toJobs[i]);
		}
	}

	public static void removeProgression(JobType from, JobType toJob) {
		TArray<JobType> evolutions = progressionTree.get(from);
		if (evolutions != null) {
			evolutions.remove(toJob);
		}
	}

	public static TArray<JobType> getEvolutions(JobType job) {
		TArray<JobType> evolutions = progressionTree.get(job);
		if (evolutions == null) {
			return new TArray<JobType>();
		}
		return evolutions;
	}

	public static TArray<JobType> getPredecessors(JobType job) {
		TArray<JobType> predecessors = new TArray<JobType>();
		ObjectMap.Entries<JobType, TArray<JobType>> ens = progressionTree.entries();
		for (ObjectMap.Entries<JobType, TArray<JobType>> it = ens.iterator(); it.hasNext();) {
			ObjectMap.Entry<JobType, TArray<JobType>> entry = it.next();
			if (entry.getValue().contains(job)) {
				predecessors.add(entry.getKey());
			}
		}
		return predecessors;
	}
}
