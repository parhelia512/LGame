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

import loon.action.map.items.Team;
import loon.utils.MathUtils;
import loon.utils.TArray;

public abstract class BattleTurnable implements Comparable<BattleTurnable> {

	private boolean _hasActed = false;

	private int _actionPoints = 1;

	private long _speed = 10;

	private int _team = Team.Unknown;

	private TArray<String> _messages = new TArray<String>();

	public BattleTurnable(int team, long speed) {
		this._team = team;
		this._speed = speed;
	}

	public void startTurn() {
		_hasActed = false;
		_actionPoints = getBaseActionPoints();
		onTurnStart();
	}

	public void endTurn() {
		onTurnEnd();
	}

	public abstract void takeAction(long elapsedTime);

	public boolean canAct() {
		return !_hasActed && _actionPoints > 0;
	}

	protected void consumeActionPoint() {
		if (_actionPoints > 0) {
			_actionPoints--;
			if (_actionPoints == 0) {
				_hasActed = true;
			}
		}
	}

	protected abstract void onTurnStart();

	protected abstract void onTurnEnd();

	protected int getBaseActionPoints() {
		return _actionPoints;
	}

	protected void setBaseActionPoints(int a) {
		_actionPoints = a;
	}

	public int getTeam() {
		return _team;
	}

	public void setTeam(int newTeam) {
		this._team = newTeam;
	}

	public long getSpeed() {
		return _speed;
	}

	public void addMessage(String buff) {
		_messages.add(buff);
	}

	public TArray<String> getMessage() {
		return new TArray<String>(_messages);
	}

	@Override
	public int compareTo(BattleTurnable other) {
		return MathUtils.compare(other._speed, this._speed);
	}

}
