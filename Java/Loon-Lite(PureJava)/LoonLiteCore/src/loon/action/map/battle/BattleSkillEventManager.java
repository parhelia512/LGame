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

import java.util.Comparator;

import loon.action.map.battle.BattleSkill.EventCondition;
import loon.action.map.battle.BattleSkill.SKillEventType;
import loon.action.map.battle.BattleSkill.SkillTriggerEvent;
import loon.utils.ObjectMap;
import loon.utils.TArray;

public class BattleSkillEventManager {

	public static class PrioritizedEvent {

		SkillTriggerEvent event;
		int priority;
		EventCondition condition;

		PrioritizedEvent(SkillTriggerEvent event, int priority, EventCondition condition) {
			this.event = event;
			this.priority = priority;
			this.condition = condition;
		}
	}

	public static class SkillEventQueue {

		private TArray<QueuedEvent> queue;

		private TArray<QueuedEvent> readyEvents = new TArray<QueuedEvent>();

		public SkillEventQueue() {
			queue = new TArray<QueuedEvent>();
		}

		public void addEvent(QueuedEvent event) {
			if (event != null) {
				queue.add(event);
			}
		}

		public void update(float delta) {
			if (queue.isEmpty()) {
				return;
			}
			readyEvents.clear();
			// 更新计时
			for (int i = 0; i < queue.size(); i++) {
				QueuedEvent e = queue.get(i);
				e.update(delta);
				if (e.isReady()) {
					readyEvents.add(e);
				}
			}
			// 冒泡排序按优先级
			for (int i = 0; i < readyEvents.size(); i++) {
				for (int j = i + 1; j < readyEvents.size(); j++) {
					if (readyEvents.get(i).getPriority() < readyEvents.get(j).getPriority()) {
						QueuedEvent temp = readyEvents.get(i);
						readyEvents.set(i, readyEvents.get(j));
						readyEvents.set(j, temp);
					}
				}
			}
			// 执行并移除
			for (int i = 0; i < readyEvents.size(); i++) {
				QueuedEvent e = readyEvents.get(i);
				e.execute(this);
				queue.remove(e);
			}
		}

		public boolean isEmpty() {
			return queue.isEmpty();
		}
	}

	public static class QueuedEvent {

		private SkillTriggerEvent event;
		private BattleMapObject caster;
		private BattleMapObject target;
		private int priority;
		private float delay;
		private float elapsed;
		private TArray<QueuedEvent> chainedEvents;

		public QueuedEvent(SkillTriggerEvent event, BattleMapObject caster, BattleMapObject target, int priority,
				float delay) {
			this.event = event;
			this.caster = caster;
			this.target = target;
			this.priority = priority;
			this.delay = delay;
			this.elapsed = 0;
			this.chainedEvents = new TArray<QueuedEvent>();
		}

		public void addChainedEvent(QueuedEvent nextEvent) {
			if (nextEvent != null) {
				chainedEvents.add(nextEvent);
			}
		}

		public void update(float delta) {
			elapsed += delta;
		}

		public boolean isReady() {
			return elapsed >= delay;
		}

		public void execute(SkillEventQueue queue) {
			if (event != null) {
				event.onEvent(caster, target);
			}
			for (int i = 0; i < chainedEvents.size(); i++) {
				queue.addEvent(chainedEvents.get(i));
			}
		}

		public int getPriority() {
			return priority;
		}
	}

	private ObjectMap<SKillEventType, TArray<PrioritizedEvent>> eventMap;

	public BattleSkillEventManager() {
		eventMap = new ObjectMap<SKillEventType, TArray<PrioritizedEvent>>();
	}

	public void registerEvent(SKillEventType type, SkillTriggerEvent event, int priority, EventCondition condition) {
		TArray<PrioritizedEvent> events = eventMap.get(type);
		if (events == null) {
			events = new TArray<PrioritizedEvent>();
			eventMap.put(type, events);
		}
		events.add(new PrioritizedEvent(event, priority, condition));
	}

	public void removeEvent(SKillEventType type, SkillTriggerEvent event) {
		TArray<PrioritizedEvent> events = eventMap.get(type);
		if (events != null) {
			for (int i = 0; i < events.size(); i++) {
				if (events.get(i).event == event) {
					events.removeIndex(i);
					break;
				}
			}
			if (events.isEmpty()) {
				eventMap.remove(type);
			}
		}
	}

	public void clearEvents(PrioritizedEvent type) {
		eventMap.remove(type);
	}

	public void triggerEvent(SKillEventType type, BattleMapObject caster, BattleMapObject target) {
		TArray<PrioritizedEvent> events = eventMap.get(type);
		if (events != null) {
			events.sort(new Comparator<PrioritizedEvent>() {
				@Override
				public int compare(PrioritizedEvent e1, PrioritizedEvent e2) {
					return e2.priority - e1.priority;
				}
			});
			for (int i = 0; i < events.size(); i++) {
				PrioritizedEvent pe = events.get(i);
				if (pe.condition == null || pe.condition.canTrigger(caster, target)) {
					boolean continueChain = pe.event.onEvent(caster, target);
					if (!continueChain) {
						break;
					}
				}
			}
		}
	}

	public void clearEvents(SKillEventType type) {
		eventMap.remove(type);
	}

	public void clearAllEvents() {
		eventMap.clear();
	}
}
