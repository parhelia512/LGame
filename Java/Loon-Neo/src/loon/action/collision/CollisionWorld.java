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
package loon.action.collision;

import loon.LRelease;
import loon.LSystem;
import loon.Screen;
import loon.action.ActionBind;
import loon.action.collision.CollisionGrid.TraverseCallback;
import loon.action.map.Side;
import loon.geom.PointF;
import loon.geom.RectF;
import loon.utils.IntMap;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

/**
 * 一个碰撞物体自动管理用类,和CollisionManager不同,它会自动获得碰撞后新的物体坐标
 */
public class CollisionWorld implements LRelease {

	private static class WorldCollisionFilter extends CollisionFilter {

		private final CollisionWorld _world;
		private final ObjectMap<ActionBind, Boolean> _visitedMap;
		private final CollisionFilter _filter;

		public WorldCollisionFilter(CollisionWorld world, ObjectMap<ActionBind, Boolean> visitedMap,
				CollisionFilter f) {
			this._world = world;
			this._visitedMap = visitedMap;
			this._filter = f;
		}

		@Override
		public CollisionResult filter(ActionBind obj, ActionBind other) {
			if (_visitedMap.containsKey(other)) {
				return null;
			}
			CollisionResult result = _filter == null ? _world._worldCollisionFilter.filter(obj, other)
					: _filter.filter(obj, other);
			CollisionActionQuery<ActionBind> actionQuery = _world._collisionActionQuery;
			if (actionQuery == null)
				return result;
			int dir = Side.getCollisionSide(obj.getRectBox(), other.getRectBox());
			boolean query = actionQuery.checkQuery(obj, other, dir);
			if (query) {
				actionQuery.onCollisionResult(obj, other, dir);
				return result;
			}
			return null;
		}
	}

	public class Cell {
		public int itemCount = 0;
		public int cx;
		public int cy;
		public final ObjectMap<ActionBind, Boolean> items = new ObjectMap<>();
	}

	private static final float DELTA = 1e-5f;
	private CollisionFilter _worldCollisionFilter;
	private final Screen _gameScreen;

	private final RectF detectCollisionDiff = new RectF();
	private final PointF nearestCorner = new PointF();
	private final PointF segTI = new PointF(), segN1 = new PointF(), segN2 = new PointF();
	private final CollisionData colData = new CollisionData();
	private final PointF tmpF2 = new PointF();
	private final RectF tmpRect = new RectF();

	private ObjectMap<ActionBind, RectF> rects = new ObjectMap<ActionBind, RectF>();
	private IntMap<IntMap<Cell>> rows = new IntMap<IntMap<Cell>>();

	private final CollisionGrid grid = new CollisionGrid();

	private boolean _tileMode = false;
	private boolean _closed = false;
	private boolean _autoRemoveItem = false;
	private boolean _autoAddItem = true;

	private CollisionManager collisionManager;
	private final float cellSizeX, cellSizeY;

	private final ObjectMap<Cell, Boolean> cellVisitMap = new ObjectMap<Cell, Boolean>();
	private final TArray<Cell> cellVisitCache = new TArray<Cell>();

	private final RectF removeRect = new RectF(), addRect = new RectF();
	private final RectF updateRect1 = new RectF(), updateRect2 = new RectF();
	private final RectF projectRect = new RectF();

	private final ObjectMap<ActionBind, Boolean> bindVisitMap = new ObjectMap<ActionBind, Boolean>();
	private final ObjectMap<ActionBind, Boolean> cellItemCache = new ObjectMap<ActionBind, Boolean>();

	private final Collisions checkCols = new Collisions();
	private final Collisions checkProjCols = new Collisions();
	private final CollisionResult.Result checkResult = new CollisionResult.Result();

	private CollisionActionQuery<ActionBind> _collisionActionQuery;

	public CollisionWorld() {
		this(null);
	}

	public CollisionWorld(Screen s) {
		this(s, LSystem.LAYER_TILE_SIZE, LSystem.LAYER_TILE_SIZE, true);
	}

	public CollisionWorld(Screen s, boolean mode) {
		this(s, LSystem.LAYER_TILE_SIZE, LSystem.LAYER_TILE_SIZE, mode);
	}

	public CollisionWorld(Screen s, float cellx, float celly, boolean mode) {
		this._gameScreen = s;
		this.cellSizeX = cellx;
		this.cellSizeY = celly;
		this._tileMode = mode;
		this._worldCollisionFilter = CollisionFilter.getDefault();
	}

	public CollisionManager getCollisionManager() {
		if (_closed) {
			return collisionManager;
		}
		if (collisionManager == null) {
			collisionManager = new CollisionManager();
			collisionManager.initialize(MathUtils.iceil(cellSizeX), MathUtils.iceil(cellSizeY));
		}
		return collisionManager;
	}

	public CollisionData detectCollision(float x1, float y1, float w1, float h1, float x2, float y2, float w2, float h2,
			float goalX, float goalY) {
		if (_closed) {
			return null;
		}
		CollisionData col = colData;
		float dx = goalX - x1;
		float dy = goalY - y1;

		CollisionHelper.getDiff(x1, y1, w1, h1, x2, y2, w2, h2, detectCollisionDiff);
		float x = detectCollisionDiff.x, y = detectCollisionDiff.y;
		float w = detectCollisionDiff.width, h = detectCollisionDiff.height;

		boolean overlaps = false;
		float ti = -1, nx = 0, ny = 0;

		if (CollisionHelper.containsPoint(x, y, w, h, 0, 0, DELTA)) {
			CollisionHelper.getNearestCorner(x, y, w, h, 0, 0, nearestCorner);
			float px = nearestCorner.x, py = nearestCorner.y;
			ti = -MathUtils.min(w1, MathUtils.abs(px)) * MathUtils.min(h1, MathUtils.abs(py));
			overlaps = true;
		} else {
			boolean intersect = CollisionHelper.getSegmentIntersectionIndices(x, y, w, h, 0, 0, dx, dy,
					-Float.MAX_VALUE, Float.MAX_VALUE, segTI, segN1, segN2);
			float ti1 = segTI.x, ti2 = segTI.y;
			if (intersect && ti1 < 1 && MathUtils.abs(ti1 - ti2) >= DELTA
					&& (0 < ti1 + DELTA || (0 == ti1 && ti2 > 0))) {
				ti = ti1;
				nx = segN1.x;
				ny = segN1.y;
				overlaps = false;
			}
		}
		if (ti == -1) {
			return null;
		}
		float tx, ty;
		if (overlaps) {
			if (dx == 0 && dy == 0) {
				CollisionHelper.getNearestCorner(x, y, w, h, 0, 0, nearestCorner);
				float px = nearestCorner.x, py = nearestCorner.y;
				if (MathUtils.abs(px) < MathUtils.abs(py)) {
					py = 0;
				} else {
					px = 0;
				}
				nx = MathUtils.sign(px);
				ny = MathUtils.sign(py);
				tx = x1 + px;
				ty = y1 + py;
			} else {
				boolean intersect = CollisionHelper.getSegmentIntersectionIndices(x, y, w, h, 0, 0, dx, dy,
						-Float.MAX_VALUE, 1, segTI, segN1, segN2);
				if (!intersect) {
					return null;
				}
				tx = x1 + dx * segTI.x;
				ty = y1 + dy * segTI.x;
				nx = segN1.x;
				ny = segN1.y;
			}
		} else {
			tx = x1;
			ty = y1;
		}
		col.set(overlaps, ti, dx, dy, nx, ny, tx, ty, x1, y1, w1, h1, x2, y2, w2, h2);
		return col;
	}

	public void setTileMode(boolean tileMode) {
		this._tileMode = tileMode;
	}

	public boolean isTileMode() {
		return _tileMode;
	}

	private void addItemToCell(ActionBind bind, int cx, int cy) {
		if (_closed || bind == null) {
			return;
		}
		IntMap<Cell> row = rows.get(cy);
		if (row == null) {
			rows.put(cy, row = new IntMap<Cell>());
		}
		Cell cell = row.get(cx);
		if (cell == null) {
			cell = new Cell();
			cell.cx = cx;
			cell.cy = cy;
			row.put(cx, cell);
		}
		if (!cell.items.containsKey(bind)) {
			cell.items.put(bind, true);
			cell.itemCount++;
		}
	}

	private boolean removeItemFromCell(ActionBind bind, int cx, int cy) {
		if (_closed || bind == null || !rows.containsKey(cy)) {
			return false;
		}
		IntMap<Cell> row = rows.get(cy);
		Cell cell = row.get(cx);
		if (cell == null || !cell.items.containsKey(bind)) {
			return false;
		}
		cell.items.remove(bind);
		cell.itemCount--;
		return true;
	}

	private ObjectMap<ActionBind, Boolean> getDictItemsInCellRect(int cl, int ct, int cw, int ch,
			ObjectMap<ActionBind, Boolean> result) {
		if (_closed || result == null) {
			return null;
		}
		result.clear();
		int endX = cl + cw;
		int endY = ct + ch;
		for (int cy = ct; cy < endY; cy++) {
			IntMap<Cell> row = rows.get(cy);
			if (row == null) {
				continue;
			}
			for (int cx = cl; cx < endX; cx++) {
				Cell cell = row.get(cx);
				if (cell == null || cell.itemCount == 0) {
					continue;
				}
				for (ObjectMap.Entry<ActionBind, Boolean> entry : cell.items.entries()) {
					result.put(entry.key, true);
				}
			}
		}
		return result;
	}

	public TArray<Cell> getCellsTouchedBySegment(float x1, float y1, float x2, float y2, final TArray<Cell> result) {
		if (_closed || result == null) {
			return null;
		}
		result.clear();
		cellVisitCache.clear();
		cellVisitMap.clear();
		grid.traverse(cellSizeX, cellSizeY, x1, y1, x2, y2, new TraverseCallback() {
			@Override
			public void onTraverse(float cx, float cy) {
				int icx = (int) cx;
				int icy = (int) cy;
				IntMap<Cell> row = rows.get(icy);
				if (row == null) {
					return;
				}
				Cell cell = row.get(icx);
				if (cell == null || cellVisitMap.containsKey(cell)) {
					return;
				}
				cellVisitMap.put(cell, true);
				cellVisitCache.add(cell);
				result.add(cell);
			}
		});
		return result;
	}

	public Collisions project(ActionBind bind, float x, float y, float w, float h, float goalX, float goalY,
			Collisions collisions) {
		return project(bind, x, y, w, h, goalX, goalY, _worldCollisionFilter, collisions);
	}

	public Collisions project(ActionBind bind, float x, float y, float w, float h, float goalX, float goalY,
			CollisionFilter filter, Collisions collisions) {
		if (_closed || collisions == null) {
			return null;
		}
		collisions.clear();
		bindVisitMap.clear();
		if (bind != null) {
			bindVisitMap.put(bind, true);
		}
		float tl = MathUtils.min(goalX, x), tt = MathUtils.min(goalY, y);
		float tr = MathUtils.max(goalX + w, x + w), tb = MathUtils.max(goalY + h, y + h);
		grid.toCellRect(cellSizeX, cellSizeY, tl, tt, tr - tl, tb - tt, projectRect);

		getDictItemsInCellRect((int) projectRect.x, (int) projectRect.y, (int) projectRect.width,
				(int) projectRect.height, cellItemCache);

		for (ObjectMap.Entry<ActionBind, Boolean> entry : cellItemCache.entries()) {
			ActionBind other = entry.key;
			if (bindVisitMap.containsKey(other)) {
				continue;
			}
			bindVisitMap.put(other, true);

			CollisionResult res = filter.filter(bind, other);
			if (res == null) {
				continue;
			}
			RectF r = getRect(other);
			if (r == null) {
				continue;
			}
			CollisionData col = detectCollision(x, y, w, h, r.x, r.y, r.width, r.height, goalX, goalY);
			if (col != null) {
				collisions.add(col.overlaps, col.ti, col.move.x, col.move.y, col.normal.x, col.normal.y, col.touch.x,
						col.touch.y, col.itemRect.x, col.itemRect.y, col.itemRect.width, col.itemRect.height,
						col.otherRect.x, col.otherRect.y, col.otherRect.width, col.otherRect.height, bind, other, res);
			}
		}
		if (_tileMode) {
			collisions.sort();
		}
		return collisions;
	}

	public CollisionWorld syncBindToObject() {
		if (_closed) {
			return this;
		}
		for (ObjectMap.Entry<ActionBind, RectF> e : rects.entries()) {
			ActionBind act = e.key;
			RectF rect = e.value;
			if (act != null && rect != null) {
				act.setLocation(rect.x, rect.y);
				act.setSize(rect.width, rect.height);
			}
		}
		return this;
	}

	public CollisionWorld syncObjectToBind() {
		if (_closed) {
			return this;
		}
		for (ObjectMap.Entry<ActionBind, RectF> e : rects.entries()) {
			ActionBind act = e.key;
			RectF rect = e.value;
			if (act != null && rect != null) {
				rect.set(act.getX(), act.getY(), act.getWidth(), act.getHeight());
			}
		}
		return this;
	}

	public RectF getRect(ActionBind bind) {
		return _closed || bind == null ? null : rects.get(bind);
	}

	public int countCells() {
		if (_closed) {
			return 0;
		}
		int c = 0;
		for (IntMap<Cell> r : rows.values()) {
			c += r.size;
		}
		return c;
	}

	public boolean hasItem(ActionBind bind) {
		return !_closed && bind != null && rects.containsKey(bind);
	}

	public int countItems() {
		return _closed ? 0 : rects.size;
	}

	public PointF toWorld(float cx, float cy, PointF r) {
		CollisionGrid.toWorld(cellSizeX, cellSizeY, cx, cy, r);
		return r;
	}

	public PointF toCell(float x, float y, PointF r) {
		CollisionGrid.toCell(cellSizeX, cellSizeY, x, y, r);
		return r;
	}

	public CollisionWorld add(ActionBind... binds) {
		for (ActionBind b : binds) {
			if (b != null) {
				add(b);
			}
		}
		return this;
	}

	public ActionBind add(ActionBind bind) {
		return _closed || bind == null ? null : add(bind, bind.getX(), bind.getY(), bind.getWidth(), bind.getHeight());
	}

	public ActionBind add(ActionBind bind, float x, float y, float w, float h) {
		if (_closed || bind == null || rects.containsKey(bind)) {
			return bind;
		}
		if (_gameScreen != null) {
			_gameScreen.add(bind);
		}
		rects.put(bind, new RectF(x, y, w, h));
		grid.toCellRect(cellSizeX, cellSizeY, x, y, w, h, addRect);
		int cl = (int) addRect.x, ct = (int) addRect.y, cw = (int) addRect.width, ch = (int) addRect.height;
		int endX = cl + cw;
		int endY = ct + ch;
		for (int cy = ct; cy < endY; cy++) {
			for (int cx = cl; cx < endX; cx++) {
				addItemToCell(bind, cx, cy);
			}
		}
		return bind;
	}

	public void remove(ActionBind bind) {
		if (_closed || bind == null) {
			return;
		}
		RectF r = getRect(bind);
		if (r == null) {
			return;
		}
		if (_gameScreen != null) {
			_gameScreen.remove(bind);
		}
		rects.remove(bind);
		grid.toCellRect(cellSizeX, cellSizeY, r.x, r.y, r.width, r.height, removeRect);
		int cl = (int) removeRect.x, ct = (int) removeRect.y, cw = (int) removeRect.width, ch = (int) removeRect.height;
		int endX = cl + cw;
		int endY = ct + ch;
		for (int cy = ct; cy < endY; cy++) {
			for (int cx = cl; cx < endX; cx++) {
				removeItemFromCell(bind, cx, cy);
			}
		}
	}

	public void update(ActionBind bind, float x2, float y2) {
		if (_closed || bind == null) {
			return;
		}
		RectF r = getRect(bind);
		if (r != null) {
			update(bind, x2, y2, r.width, r.height);
		}
	}

	public void update(ActionBind bind, float x2, float y2, float w2, float h2) {
		if (_closed || bind == null) {
			return;
		}
		RectF r = getRect(bind);
		if (r == null) {
			return;
		}
		if (MathUtils.equal(r.x, x2) && MathUtils.equal(r.y, y2) && MathUtils.equal(r.width, w2)
				&& MathUtils.equal(r.height, h2)) {
			return;
		}
		grid.toCellRect(cellSizeX, cellSizeY, r.x, r.y, r.width, r.height, updateRect1);
		grid.toCellRect(cellSizeX, cellSizeY, x2, y2, w2, h2, updateRect2);
		int c1l = (int) updateRect1.x, c1t = (int) updateRect1.y, c1w = (int) updateRect1.width,
				c1h = (int) updateRect1.height;
		int c2l = (int) updateRect2.x, c2t = (int) updateRect2.y, c2w = (int) updateRect2.width,
				c2h = (int) updateRect2.height;
		if (c1l != c2l || c1t != c2t || c1w != c2w || c1h != c2h) {
			int c1r = c1l + c1w - 1, c1b = c1t + c1h - 1;
			int c2r = c2l + c2w - 1, c2b = c2t + c2h - 1;
			if (_autoAddItem)
				for (int cy = c2t; cy <= c2b; cy++) {
					for (int cx = c2l; cx <= c2r; cx++) {
						if (cy < c1t || cy > c1b || cx < c1l || cx > c1r) {
							addItemToCell(bind, cx, cy);
						}
					}
				}
			if (_autoRemoveItem)
				for (int cy = c1t; cy <= c1b; cy++) {
					for (int cx = c1l; cx <= c1r; cx++) {
						if (cy < c2t || cy > c2b || cx < c2l || cx > c2r) {
							removeItemFromCell(bind, cx, cy);
						}
					}
				}
		}
		r.set(x2, y2, w2, h2);
	}

	public CollisionResult.Result check(ActionBind bind, float goalX, float goalY) {
		return check(bind, goalX, goalY, _worldCollisionFilter);
	}

	public CollisionResult.Result check(ActionBind bind, float goalX, float goalY, final CollisionFilter filter) {
		if (_closed || rects.size == 0) {
			checkResult.set(goalX, goalY);
			return checkResult;
		}
		bindVisitMap.clear();
		bindVisitMap.put(bind, true);
		WorldCollisionFilter f = new WorldCollisionFilter(this, bindVisitMap, filter);
		RectF r = getRect(bind);
		if (r == null) {
			checkResult.set(goalX, goalY);
			return checkResult;
		}
		checkCols.clear();
		Collisions proj = project(bind, r.x, r.y, r.width, r.height, goalX, goalY, filter, checkProjCols);
		CollisionResult.Result res = checkResult;
		while (proj != null && !proj.isEmpty()) {
			CollisionData c = proj.get(0);
			checkCols.add(c.overlaps, c.ti, c.move.x, c.move.y, c.normal.x, c.normal.y, c.touch.x, c.touch.y,
					c.itemRect.x, c.itemRect.y, c.itemRect.width, c.itemRect.height, c.otherRect.x, c.otherRect.y,
					c.otherRect.width, c.otherRect.height, c.item, c.other, c.type);
			bindVisitMap.put(c.other, true);
			c.type.response(this, c, r.x, r.y, r.width, r.height, goalX, goalY, f, res);
			goalX = res.goalX;
			goalY = res.goalY;
			proj = res.collisions;
		}
		res.set(goalX, goalY);
		res.collisions.clear();
		for (int i = 0; i < checkCols.size(); i++) {
			res.collisions.add(checkCols.get(i));
		}
		return res;
	}

	public CollisionResult.Result move(ActionBind bind, float goalX, float goalY) {
		return move(bind, goalX, goalY, _worldCollisionFilter);
	}

	public CollisionResult.Result move(ActionBind bind, float goalX, float goalY, CollisionFilter filter) {
		if (_closed) {
			checkResult.set(goalX, goalY);
			return checkResult;
		}
		CollisionResult.Result r = check(bind, goalX, goalY, filter);
		if (r != null)
			update(bind, r.goalX, r.goalY);
		return r;
	}

	public CollisionFilter getWorldCollisionFilter() {
		return _worldCollisionFilter;
	}

	public CollisionWorld setWorldCollisionFilter(CollisionFilter c) {
		this._worldCollisionFilter = c;
		return this;
	}

	public CollisionActionQuery<ActionBind> getCollisionActionQuery() {
		return _collisionActionQuery;
	}

	public CollisionWorld setCollisionActionQuery(CollisionActionQuery<ActionBind> c) {
		this._collisionActionQuery = c;
		return this;
	}

	public Screen getGameScreen() {
		return _gameScreen;
	}

	public boolean isClose() {
		return _closed;
	}

	public CollisionWorld setAutoRemoveItem(boolean a) {
		this._autoRemoveItem = a;
		return this;
	}

	public boolean isAutoRemoveItem() {
		return _autoRemoveItem;
	}

	public CollisionWorld setAutoAddItem(boolean a) {
		this._autoAddItem = a;
		return this;
	}

	public boolean isAutoAddItem() {
		return _autoAddItem;
	}

	public CollisionData raycast(float startX, float startY, float endX, float endY) {
		return raycast(startX, startY, endX, endY, _worldCollisionFilter);
	}

	public CollisionData raycast(float startX, float startY, float endX, float endY, CollisionFilter filter) {
		if (_closed) {
			return null;
		}
		TArray<Cell> cells = getCellsTouchedBySegment(startX, startY, endX, endY, cellVisitCache);
		float minTI = Float.MAX_VALUE;
		CollisionData hit = null;
		for (Cell cell : cells) {
			for (ObjectMap.Entry<ActionBind, Boolean> entry : cell.items.entries()) {
				ActionBind b = entry.key;
				RectF r = getRect(b);
				if (r == null || filter.filter(null, b) == null)
					continue;
				CollisionData col = detectCollision(startX, startY, 0, 0, r.x, r.y, r.width, r.height, endX, endY);
				if (col != null && col.ti < minTI) {
					minTI = col.ti;
					hit = col;
				}
			}
		}
		return hit;
	}

	public ActionBind pointCheck(float x, float y) {
		if (_closed) {
			return null;
		}
		toCell(x, y, tmpF2);
		int cx = (int) tmpF2.x, cy = (int) tmpF2.y;
		IntMap<Cell> row = rows.get(cy);
		if (row == null) {
			return null;
		}
		Cell cell = row.get(cx);
		if (cell == null) {
			return null;
		}
		for (ObjectMap.Entry<ActionBind, Boolean> entry : cell.items.entries()) {
			ActionBind b = entry.key;
			RectF r = getRect(b);
			if (r != null && x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height) {
				return b;
			}
		}
		return null;
	}

	public boolean overlapCheck(float x, float y, float w, float h) {
		if (_closed) {
			return false;
		}
		grid.toCellRect(cellSizeX, cellSizeY, x, y, w, h, tmpRect);
		getDictItemsInCellRect((int) tmpRect.x, (int) tmpRect.y, (int) tmpRect.width, (int) tmpRect.height,
				cellItemCache);
		for (ObjectMap.Entry<ActionBind, Boolean> entry : cellItemCache.entries()) {
			ActionBind b = entry.key;
			RectF r = getRect(b);
			if (r != null && x < r.x + r.width && x + w > r.x && y < r.y + r.height && y + h > r.y) {
				return true;
			}
		}
		return false;
	}

	public TArray<ActionBind> getBindsInRect(float x, float y, float w, float h, TArray<ActionBind> out) {
		if (_closed || out == null) {
			return out;
		}
		out.clear();
		grid.toCellRect(cellSizeX, cellSizeY, x, y, w, h, tmpRect);
		getDictItemsInCellRect((int) tmpRect.x, (int) tmpRect.y, (int) tmpRect.width, (int) tmpRect.height,
				cellItemCache);
		for (ObjectMap.Entry<ActionBind, Boolean> entry : cellItemCache.entries()) {
			out.add(entry.key);
		}
		return out;
	}

	@Override
	public void close() {
		if (_closed) {
			return;
		}
		_closed = true;
		_autoAddItem = _autoRemoveItem = false;
		rects.clear();
		rows.clear();
		if (collisionManager != null) {
			collisionManager.clear();
			collisionManager = null;
		}
		cellVisitCache.clear();
		cellVisitMap.clear();
		cellItemCache.clear();
		bindVisitMap.clear();
		checkCols.clear();
		checkProjCols.clear();
	}

}
