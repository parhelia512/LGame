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

import loon.action.map.battle.BattleType.RangeType;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

public class BattleComboSystem {

	public static class ComboEnergy {

		private int currentEnergy;

		private int maxEnergy;

		public ComboEnergy(int maxEnergy) {
			this.maxEnergy = maxEnergy;
			this.currentEnergy = 0;
		}

		public void gainEnergy(int amount) {
			currentEnergy = MathUtils.min(maxEnergy, currentEnergy + amount);
		}

		public boolean consumeEnergy(int amount) {
			if (currentEnergy >= amount) {
				currentEnergy -= amount;
				return true;
			}
			return false;
		}

		public int getCurrentEnergy() {
			return currentEnergy;
		}
	}

	public static class ComboSkill extends BattleSkill {

		private int comboEnergyCost;

		public ComboSkill(String id, String name, String description, BattleType type, int damage, int mpCost,
				float hitRate, float critRate, int comboEnergyCost, float cooldownDuration) {
			super(id, name, description, type, damage, mpCost, hitRate, critRate, null, null, null, 1, false, 5, 0.95f,
					40, 3, cooldownDuration, RangeType.SINGLE, 1, 1);
			this.comboEnergyCost = comboEnergyCost;
		}

		public boolean canCast(BattleMapObject caster, ComboEnergy energy) {
			return super.canCast(caster.getLevel()) && energy.consumeEnergy(comboEnergyCost);
		}
	}

	private ObjectMap<TArray<String>, BattleSkill> comboMap;

	private TArray<String> currentSequence;

	public BattleComboSystem() {
		comboMap = new ObjectMap<TArray<String>, BattleSkill>();
		currentSequence = new TArray<String>();
	}

	public void registerCombo(TArray<String> sequence, BattleSkill skill) {
		comboMap.put(sequence, skill);
	}

	public void addInput(String input) {
		currentSequence.add(input);
		checkCombo();
	}

	private void checkCombo() {
		for (ObjectMap.Entry<TArray<String>, BattleSkill> entry : comboMap.entries()) {
			TArray<String> comboSeq = entry.getKey();
			if (endsWithSequence(currentSequence, comboSeq)) {
				currentSequence.clear();
				break;
			}
		}
	}

	private boolean endsWithSequence(TArray<String> inputSeq, TArray<String> comboSeq) {
		if (inputSeq.size() < comboSeq.size()) {
			return false;
		}
		for (int i = 0; i < comboSeq.size(); i++) {
			if (!inputSeq.get(inputSeq.size() - comboSeq.size() + i).equals(comboSeq.get(i))) {
				return false;
			}
		}
		return true;
	}
}
