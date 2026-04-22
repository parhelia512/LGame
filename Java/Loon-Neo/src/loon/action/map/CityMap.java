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
package loon.action.map;

import java.util.Comparator;

import loon.LObject;
import loon.LSystem;
import loon.LTexture;
import loon.LTextures;
import loon.PlayerUtils;
import loon.Screen;
import loon.action.ActionBind;
import loon.action.ActionTween;
import loon.action.collision.CollisionHelper;
import loon.action.map.Field2D.MapSwitchMaker;
import loon.action.map.items.City;
import loon.action.sprite.Animation;
import loon.action.sprite.ISprite;
import loon.action.sprite.MoveControl;
import loon.action.sprite.SpriteCollisionListener;
import loon.action.sprite.Sprites;
import loon.canvas.LColor;
import loon.events.DrawListener;
import loon.events.ResizeListener;
import loon.geom.Affine2f;
import loon.geom.PointF;
import loon.geom.PointI;
import loon.geom.RectBox;
import loon.geom.Sized;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.utils.ISOUtils;
import loon.utils.IntMap;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.SortedList;
import loon.utils.StringUtils;
import loon.utils.TArray;
import loon.utils.timer.Duration;

/**
 * 城市地图显示与链接系统，作用是渲染指定城市并在任意指定城市间绘制可选效果的连接线。
 * (可用于常见的大地图系统，例如三国类游戏或者某些门派模拟类游戏.当然，充当技能树之类也行)
 */
public class CityMap extends LObject<ISprite> implements TileMapCollision, Sized, ISprite {

	/**
	 * 默认的边线样式
	 */
	public enum EdgeType {
		// 直线
		STRAIGHT,
		// 贝塞尔曲线
		BEZIER_QUAD,
		// A*寻径的结果路线
		ASTAR_PATH,
		// 崎岖地形连线（人为增加线条曲折）
		ROUGH_TERRAIN
	}

	public static class Edge {

		public final String id;
		public City from, to;
		// 默认以贝塞尔曲线链接
		public EdgeType edgeType = EdgeType.BEZIER_QUAD;
		public LColor colorFrom = LColor.white;
		public LColor colorTo = LColor.white;
		public float width = 3f;
		public float animationDuration = 0f;
		public float animationStart = 0f;
		public float bezierOffsetRatio = 0.18f;
		// 崎岖幅度
		public float roughAmplitude = 6f;
		// 崎岖地形双向偏移间距
		public float roughSideOffset = 6f;
		// 崎岖分段数 (即一条线来回曲折几次)
		public int roughSegments = 8;

		public Edge(String id, City from, City to) {
			this.id = id;
			this.from = from;
			this.to = to;
		}

		public boolean isAnimating(float now) {
			return animationDuration > 0f && now - animationStart < animationDuration;
		}

		public float progress(float now) {
			if (animationDuration <= 0f) {
				return 1f;
			}
			return MathUtils.clamp((now - animationStart) / animationDuration, 0f, 1f);
		}

		public Edge setColorBatch(LColor colorFrom, LColor colorTo) {
			this.colorFrom = colorFrom;
			this.colorTo = colorTo;
			return this;
		}

		public Edge setWidth(float width) {
			this.width = width;
			return this;
		}

		public Edge setEdgeType(EdgeType type) {
			this.edgeType = type;
			return this;
		}

		public Edge setAnimation(float duration) {
			this.animationDuration = duration;
			return this;
		}

		public Edge setBezierOffset(float ratio) {
			this.bezierOffsetRatio = ratio;
			return this;
		}

		public Edge startAnimationBatch(float nowTime) {
			this.animationStart = nowTime;
			return this;
		}

		public Edge setRoughAmplitude(float amplitude) {
			this.roughAmplitude = MathUtils.clamp(amplitude, 0f, 100f);
			return this;
		}

		public Edge setRoughSideOffset(float offset) {
			this.roughSideOffset = MathUtils.clamp(offset, 0f, 100f);
			return this;
		}

		public Edge setRoughSegments(int segments) {
			this.roughSegments = MathUtils.clamp(segments, 1, 100);
			return this;
		}
	}

	public static class CityCollisionResolver {
		private final float cellSize;
		private final IntMap<TArray<City>> grid = new IntMap<TArray<City>>();

		public CityCollisionResolver(float cellSize) {
			this.cellSize = cellSize;
		}

		private int key(int gx, int gy) {
			return (gx << 16) ^ gy;
		}

		private int gx(float x) {
			return MathUtils.floor(x / cellSize);
		}

		private int gy(float y) {
			return MathUtils.floor(y / cellSize);
		}

		public void clear() {
			grid.clear();
		}

		public void insert(City c) {
			int gx = gx(c.screenPos.x), gy = gy(c.screenPos.y);
			int k = key(gx, gy);
			TArray<City> arr = grid.get(k);
			if (arr == null) {
				arr = new TArray<City>();
				grid.put(k, arr);
			}
			arr.add(c);
		}

		public TArray<City> queryNearby(City c) {
			TArray<City> out = new TArray<City>();
			int gx = gx(c.screenPos.x), gy = gy(c.screenPos.y);
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++) {
					TArray<City> arr = grid.get(key(gx + dx, gy + dy));
					if (arr != null) {
						out.addAll(arr);
					}
				}
			return out;
		}

		public void resolveAll(TArray<City> cities, int maxIter, float padding) {
			clear();
			for (City c : cities) {
				insert(c);
			}
			for (int iter = 0; iter < maxIter; iter++) {
				boolean moved = false;
				for (City c : cities) {
					TArray<City> nearby = queryNearby(c);
					for (City other : nearby) {
						if (other == c) {
							continue;
						}
						float minDist = c.radius + other.radius + padding;
						float dx = c.screenPos.x - other.screenPos.x;
						float dy = c.screenPos.y - other.screenPos.y;
						float dist = (float) MathUtils.sqrt(dx * dx + dy * dy);
						if (dist < 0.001f) {
							dx = MathUtils.random(-1f, 1f);
							dy = MathUtils.random(-1f, 1f);
							dist = (float) MathUtils.sqrt(dx * dx + dy * dy);
						}
						if (dist < minDist) {
							float overlap = minDist - dist;
							float nx = dx / dist, ny = dy / dist;
							float move = overlap * 0.5f;
							int pDiff = c.priority - other.priority;
							float cMove = move * (pDiff <= 0 ? 1.0f : 0.3f);
							float oMove = move * (pDiff <= 0 ? 0.3f : 1.0f);
							c.screenPos.x += nx * cMove;
							c.screenPos.y += ny * cMove;
							other.screenPos.x -= nx * oMove;
							other.screenPos.y -= ny * oMove;
							moved = true;
						}
					}
				}
				if (!moved) {
					break;
				}
				clear();
				for (City c : cities) {
					insert(c);
				}
			}
		}
	}

	static class Node {
		int gx, gy;
		float g, f;
		Node parent;

		Node(int gx, int gy) {
			this.gx = gx;
			this.gy = gy;
		}

		@Override
		public int hashCode() {
			return (gx << 16) ^ gy;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Node)) {
				return false;
			}
			Node n = (Node) o;
			return n.gx == gx && n.gy == gy;
		}
	}

	private final LColor SELECT_HALO_COLOR = LColor.cyan;
	private final LColor MARCH_COLOR = LColor.red;
	private final LColor TRANSPORT_COLOR = LColor.yellow;
	private final LColor PLAYER_MOVE_COLOR = LColor.blue;

	private final Vector2f _tempPosA = new Vector2f();
	private final Vector2f _tempPosB = new Vector2f();

	private final LColor _tempColorA = new LColor();
	private final LColor _tempColorB = new LColor();

	private int _cityEgdeMaxIter = 8;

	private float _cityEgdePadding = 2f;

	private City _selectedCity;

	private float _cityNameOffsetX = 0f;

	private float _cityNameOffsetY = 5f;

	private final Vector2f _backgroundOffset = new Vector2f();

	private final Vector2f _backgroundSize = new Vector2f();

	// A* 网格分辨率（像素）
	private int _gridCellSizeX = LSystem.LAYER_TILE_SIZE;
	private int _gridCellSizeY = LSystem.LAYER_TILE_SIZE;
	// 在城市半径基础上再膨胀
	private float _obstacleInflation = 6f;
	private int _maxAstarNodes = 20000;
	// 自动搜索边线的边界padding
	private float _astarSearchPadding = 120f;
	private boolean _enableCatmullRom = true;
	// 每段插值采样数
	private float _catmullSamplesPerSegment = 6;
	// 是否对平滑点做二次贝塞尔拟合
	private boolean _fitBezierSegments = false;
	// 额外膨胀
	private float _stringPullInflation = 2f;

	private final Vector2f _originMeters = new Vector2f();
	private final TArray<City> _cities = new TArray<City>();
	private final TArray<Edge> _edges = new TArray<Edge>();

	private CityCollisionResolver _resolver;

	// 图像于世界范围的缩放比例默认为1/5000，即1像素等于实际地图5000米，经纬度模式下专用(比如用古地图坐标设定三国之类的)
	private float _cityMapScale = 0.0005f;

	private float _nowDelta = 0f;

	private int _maxEdgeProcessPerFrame = 12;

	// 或此项为真，则使用二维地图索引坐标，而非世界地图的经纬度坐标(默认开二维坐标，用世界坐标关了即可)
	private boolean _useOrthogonalPos = true;

	private final ObjectMap<String, TArray<Vector2f>> _edgePathCache = new ObjectMap<String, TArray<Vector2f>>();

	private final ObjectMap<String, Edge> _dirtyEdges = new ObjectMap<String, Edge>();

	private final IntMap<Node> _nodeCache = new IntMap<Node>();

	private LTexture _background;

	private Field2D _field2d;

	public DrawListener<CityMap> _drawListener;

	private ResizeListener<CityMap> _resizeListener;

	// 地图自身存储子精灵的的Sprites
	private Sprites _mapSprites;

	// 显示Map的上级Sprites
	private Sprites _screenSprites;

	private SpriteCollisionListener _collSpriteListener;

	private final int _pixelInWidth, _pixelInHeight;

	private final PointF _scrollDrag = new PointF();

	private float _fixedWidthOffset = 0f;

	private float _fixedHeightOffset = 0f;

	private float _scaleX = 1f, _scaleY = 1f;

	private boolean _active, _dirty;

	private boolean _visible, _roll;

	private boolean _playAnimation;

	private ActionBind _follow;

	private Vector2f _followOffset = new Vector2f();

	private Vector2f _offset = new Vector2f();

	private LColor _baseColor = LColor.white;

	private EdgeType _defaultEdgeType = EdgeType.BEZIER_QUAD;
	// 连线缓动动画
	private boolean _edgeAnimationEnabled = true;
	// 是否显示城市（门派什么的也一样）名称
	private boolean _showCityNames = true;
	// 城市名称颜色
	private LColor _cityNameColor = LColor.white;
	// 城市边线碰撞避让开关
	private boolean _cityCollisionEnabled = true;
	// 默认城市连接边线起始段颜色
	private LColor _defaultEdgeColorFrom = LColor.yellow.lighter();
	// 默认城市连接边线结束段颜色
	private LColor _defaultEdgeColorTo = LColor.white.darker();
	// 默认连接线宽度
	private float _defaultEdgeWidth = 3f;
	// 默认连接线出现的缓动时长
	private float _defaultEdgeAnimationDuration = 2f;

	public CityMap(String fileName, int tileWidth, int tileHeight) {
		this(fileName, tileWidth, tileHeight, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public CityMap(String fileName, int tileWidth, int tileHeight, int mWidth, int mHeight) {
		this(TileMapConfig.loadAthwartArray(fileName), tileWidth, tileHeight, mWidth, mHeight);
	}

	public CityMap(String fileName, Screen screen, int tileWidth, int tileHeight, int mWidth, int mHeight) {
		this(TileMapConfig.loadAthwartArray(fileName), screen, tileWidth, tileHeight, mWidth, mHeight);
	}

	public CityMap(int[][] maps, int tileWidth, int tileHeight, int mWidth, int mHeight) {
		this(new Field2D(maps, tileWidth, tileHeight), mWidth, mHeight);
	}

	public CityMap(int[][] maps, Screen screen, int tileWidth, int tileHeight, int mWidth, int mHeight) {
		this(new Field2D(maps, tileWidth, tileHeight), screen, mWidth, mHeight);
	}

	public CityMap(int[][] maps, int tileWidth, int tileHeight) {
		this(maps, tileWidth, tileHeight, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public CityMap(int[][] maps, Screen screen, int tileWidth, int tileHeight) {
		this(maps, screen, tileWidth, tileHeight, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public CityMap(int w, int h, int tw, int th) {
		this(w, h, tw, th, 0);
	}

	public CityMap(int w, int h, int tw, int th, int v) {
		this(new Field2D(w, h, tw, th, v));
	}

	public CityMap(Field2D field2d) {
		this(field2d, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public CityMap(Field2D field2d, Screen screen) {
		this(field2d, screen, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public CityMap(Field2D field2d, int mWidth, int mHeight) {
		this(field2d, null, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public CityMap(Field2D field2d, Screen screen, int mWidth, int mHeight) {
		this._field2d = field2d;
		if (field2d != null && mWidth == -1 && mHeight == -1) {
			this._pixelInWidth = field2d.getViewWidth();
			this._pixelInHeight = field2d.getViewHeight();
		} else {
			this._pixelInWidth = mWidth;
			this._pixelInHeight = mHeight;
		}
		if (field2d == null) {
			this._offset = new Vector2f(0, 0);
		} else {
			this._offset = field2d.getOffset();
		}
		this._scaleX = this._scaleY = 1f;
		this._active = true;
		this._dirty = true;
		this._visible = true;
		this._resolver = new CityCollisionResolver(128f);
		this._mapSprites = new Sprites("CityMapSprites", screen == null ? LSystem.getProcess().getScreen() : screen,
				_pixelInWidth, _pixelInHeight);
	}

	public static CityMap loadCharsMap(String resName, int tileWidth, int tileHeight) {
		return new CityMap(TileMapConfig.loadCharsField(resName, tileWidth, tileHeight));
	}

	protected Vector2f toMapPixels(float lat, float lon, Vector2f originMeters, float scale, boolean use2d) {
		if (use2d) {
			return new Vector2f(toTileScrollPixelX(lat), toTileScrollPixelY(lon));
		} else {
			return ISOUtils.getLatLonToMapPixels(lat, lon, originMeters, scale);
		}
	}

	public void addCity(City c) {
		Vector2f mapPos = toMapPixels(c.lat, c.lon, _originMeters, _cityMapScale, _useOrthogonalPos);
		c.screenPos.set(mapPos.x, mapPos.y);
		_cities.add(c);
		if (_cityCollisionEnabled) {
			resolveCityCollisions();
		}
		for (Edge e : _edges) {
			markEdgeDirty(e);
		}
	}

	public void moveCity(City c, float newLat, float newLon) {
		c.lat = newLat;
		c.lon = newLon;
		Vector2f mapPos = toMapPixels(c.lat, c.lon, _originMeters, _cityMapScale, _useOrthogonalPos);
		c.screenPos.set(mapPos.x, mapPos.y);
		if (_cityCollisionEnabled) {
			resolveCityCollisions();
		}
		for (Edge e : _edges) {
			if (e.from == c || e.to == c) {
				markEdgeDirty(e);
			}
		}
	}

	public void removeCity(City c) {
		_cities.removeValue(c, true);
		TArray<Edge> toRemove = new TArray<Edge>();
		for (Edge e : _edges) {
			if (e.from == c || e.to == c) {
				toRemove.add(e);
			}
		}
		for (Edge e : toRemove) {
			removeEdge(e);
		}
		if (_cityCollisionEnabled) {
			resolveCityCollisions();
		}
		for (Edge e : _edges) {
			markEdgeDirty(e);
		}
	}

	public void addEdge(Edge e) {
		_edges.add(e);
		markEdgeDirty(e);
	}

	public void removeEdge(Edge e) {
		_edges.removeValue(e, true);
		_edgePathCache.remove(e.id);
		_dirtyEdges.remove(e.id);
	}

	private void markEdgeDirty(Edge e) {
		_dirtyEdges.put(e.id, e);
	}

	private TArray<Vector2f> findPathAStar(Vector2f start, Vector2f goal) {
		float padding = _astarSearchPadding;
		float minX = MathUtils.min(start.x, goal.x) - padding;
		float maxX = MathUtils.max(start.x, goal.x) + padding;
		float minY = MathUtils.min(start.y, goal.y) - padding;
		float maxY = MathUtils.max(start.y, goal.y) + padding;

		int gx0 = MathUtils.floor(minX / _gridCellSizeX);
		int gy0 = MathUtils.floor(minY / _gridCellSizeY);
		int gx1 = MathUtils.floor(maxX / _gridCellSizeX);
		int gy1 = MathUtils.floor(maxY / _gridCellSizeY);
		int gw = gx1 - gx0 + 1;
		int gh = gy1 - gy0 + 1;
		if (gw <= 0 || gh <= 0) {
			return null;
		}

		boolean[][] blocked = new boolean[gw][gh];
		for (int gx = gx0; gx <= gx1; gx++) {
			for (int gy = gy0; gy <= gy1; gy++) {
				float cx = (gx + 0.5f) * _gridCellSizeX;
				float cy = (gy + 0.5f) * _gridCellSizeY;
				boolean block = false;
				for (City c : _cities) {
					float r = c.radius + _obstacleInflation;
					float dx = cx - c.screenPos.x, dy = cy - c.screenPos.y;
					if (dx * dx + dy * dy <= r * r) {
						block = true;
						break;
					}
				}
				blocked[gx - gx0][gy - gy0] = block;
			}
		}

		int sx = MathUtils.floor(start.x / _gridCellSizeX), sy = MathUtils.floor(start.y / _gridCellSizeY);
		int gx = MathUtils.floor(goal.x / _gridCellSizeX), gy = MathUtils.floor(goal.y / _gridCellSizeY);
		if (sx < gx0) {
			sx = gx0;
		}
		if (sy < gy0) {
			sy = gy0;
		}
		if (sx > gx1) {
			sx = gx1;
		}
		if (sy > gy1) {
			sy = gy1;
		}
		if (gx < gx0) {
			gx = gx0;
		}
		if (gy < gy0) {
			gy = gy0;
		}
		if (gx > gx1) {
			gx = gx1;
		}
		if (gy > gy1) {
			gy = gy1;
		}
		int sxi = sx - gx0, syi = sy - gy0, gxi = gx - gx0, gyi = gy - gy0;
		if (blocked[sxi][syi]) {
			int[] nudged = findNearestFreeCell(blocked, sxi, syi);
			if (nudged == null) {
				return null;
			}
			sxi = nudged[0];
			syi = nudged[1];
		}
		if (blocked[gxi][gyi]) {
			int[] nudged = findNearestFreeCell(blocked, gxi, gyi);
			if (nudged == null) {
				return null;
			}
			gxi = nudged[0];
			gyi = nudged[1];
		}
		_nodeCache.clear();
		SortedList<Node> open = new SortedList<Node>();
		open.sort(new Comparator<Node>() {

			@Override
			public int compare(Node o1, Node o2) {
				return MathUtils.compare(o1.f, o2.f);
			}
		});

		IntMap<Node> openMap = new IntMap<Node>();
		IntMap<Boolean> closed = new IntMap<Boolean>();

		Node startNode = getNode(sxi + gx0, syi + gy0);
		startNode.g = 0;
		startNode.f = heuristic(sxi, syi, gxi, gyi);
		startNode.parent = null;
		open.add(startNode);
		openMap.put((startNode.gx << 16) ^ startNode.gy, startNode);

		int nodesSearched = 0;
		while (!open.isEmpty()) {
			Node cur = open.poll();
			openMap.remove((cur.gx << 16) ^ cur.gy);
			nodesSearched++;
			if (nodesSearched > _maxAstarNodes) {
				break;
			}
			int cix = cur.gx - gx0, ciy = cur.gy - gy0;
			closed.put((cur.gx << 16) ^ cur.gy, Boolean.TRUE);
			if (cix == gxi && ciy == gyi) {
				TArray<Vector2f> path = new TArray<Vector2f>();
				Node p = cur;
				while (p != null) {
					float px = (p.gx + 0.5f) * _gridCellSizeX;
					float py = (p.gy + 0.5f) * _gridCellSizeY;
					path.add(new Vector2f(px, py));
					p = p.parent;
				}
				TArray<Vector2f> rev = new TArray<Vector2f>();
				for (int i = path.size - 1; i >= 0; i--) {
					rev.add(path.get(i));
				}
				return rev;
			}
			for (int nx = -1; nx <= 1; nx++) {
				for (int ny = -1; ny <= 1; ny++) {
					if (nx == 0 && ny == 0) {
						continue;
					}
					int ngx = cur.gx + nx, ngy = cur.gy + ny;
					int idx = ngx - gx0, idy = ngy - gy0;
					if (idx < 0 || idy < 0 || idx >= gw || idy >= gh) {
						continue;
					}
					if (blocked[idx][idy]) {
						continue;
					}
					int key = (ngx << 16) ^ ngy;
					if (closed.containsKey(key)) {
						continue;
					}
					float moveCost = (nx == 0 || ny == 0) ? MathUtils.max(_gridCellSizeX, _gridCellSizeY)
							: MathUtils.max(_gridCellSizeX, _gridCellSizeY) * 1.4142f;
					Node neighbor = getNode(ngx, ngy);
					float tentativeG = cur.g + moveCost;
					if (!openMap.containsKey(key)) {
						neighbor.g = tentativeG;
						neighbor.f = tentativeG + heuristic(ngx - gx0, ngy - gy0, gxi, gyi);
						neighbor.parent = cur;
						open.add(neighbor);
						openMap.put(key, neighbor);
					} else {
						Node existing = openMap.get(key);
						if (tentativeG < existing.g) {
							existing.g = tentativeG;
							existing.f = tentativeG + heuristic(ngx - gx0, ngy - gy0, gxi, gyi);
							existing.parent = cur;
							open.remove(existing);
							open.add(existing);
						}
					}
				}
			}
		}
		return null;
	}

	private int[] findNearestFreeCell(boolean[][] blocked, int sx, int sy) {
		int w = blocked.length, h = blocked[0].length;
		TArray<int[]> q = new TArray<int[]>();
		boolean[][] seen = new boolean[w][h];
		q.add(new int[] { sx, sy });
		seen[sx][sy] = true;
		int head = 0;
		while (head < q.size) {
			int[] cur = q.get(head++);
			int cx = cur[0], cy = cur[1];
			if (!blocked[cx][cy])
				return new int[] { cx, cy };
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++) {
					int nx = cx + dx, ny = cy + dy;
					if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
						continue;
					}
					if (seen[nx][ny]) {
						continue;
					}
					seen[nx][ny] = true;
					q.add(new int[] { nx, ny });
				}
		}
		return null;
	}

	private Node getNode(int gx, int gy) {
		int key = (gx << 16) ^ gy;
		Node n = _nodeCache.get(key);
		if (n == null) {
			n = new Node(gx, gy);
			_nodeCache.put(key, n);
		}
		return n;
	}

	private float heuristic(int sx, int sy, int gx, int gy) {
		float dx = (gx - sx);
		float dy = (gy - sy);
		return MathUtils.sqrt(dx * dx + dy * dy) * MathUtils.max(_gridCellSizeX, _gridCellSizeY);
	}

	private TArray<Vector2f> computeEdgePath(Edge e) {
		Vector2f a = e.from.screenPos;
		Vector2f b = e.to.screenPos;
		Vector2f aClipped = CollisionHelper.clipPointToCircle(b, a, a, e.from.radius);
		Vector2f bClipped = CollisionHelper.clipPointToCircle(a, b, b, e.to.radius);
		Vector2f control = CollisionHelper.defaultQuadControl(aClipped, bClipped, e.bezierOffsetRatio);
		if (e.edgeType == EdgeType.ROUGH_TERRAIN) {
			return generateRoughPath(e, aClipped, bClipped, e.roughSegments, e.roughAmplitude);
		}
		if (!detectIntersectionWithCities(e, aClipped, bClipped, control)) {
			TArray<Vector2f> simple = new TArray<Vector2f>();
			if (e.edgeType == EdgeType.STRAIGHT) {
				simple.add(aClipped);
				simple.add(bClipped);
			} else {
				int seg = MathUtils.max(6, (int) (aClipped.dst(bClipped) / 12f));
				for (int i = 0; i <= seg; i++) {
					float t = i / (float) seg;
					simple.add(CollisionHelper.quadPoint(aClipped, control, bClipped, t));
				}
			}
			return smoothAndFit(simple);
		}
		TArray<Vector2f> astarPath = findPathAStar(aClipped, bClipped);
		if (astarPath == null || astarPath.size == 0) {
			Vector2f tryControl = CollisionHelper.defaultQuadControl(aClipped, bClipped,
					MathUtils.min(0.6f, e.bezierOffsetRatio * 2f));
			TArray<Vector2f> fallback = new TArray<>();
			int seg = MathUtils.max(6, (int) (aClipped.dst(bClipped) / 12f));
			for (int i = 0; i <= seg; i++) {
				fallback.add(CollisionHelper.quadPoint(aClipped, tryControl, bClipped, i / (float) seg));
			}
			return smoothAndFit(fallback);
		}
		TArray<Vector2f> pulled = stringPull(astarPath);
		if (pulled.size > 0) {
			pulled.set(0, aClipped.cpy());
			pulled.set(pulled.size - 1, bClipped.cpy());
		}
		TArray<Vector2f> smooth = smoothAndFit(pulled);
		return smooth;
	}

	private TArray<Vector2f> generateRoughPath(Edge e, Vector2f start, Vector2f end, int segments, float amplitude) {
		TArray<Vector2f> path = new TArray<Vector2f>();
		path.add(start.cpy());
		float dx = end.x - start.x;
		float dy = end.y - start.y;
		float len = MathUtils.sqrt(dx * dx + dy * dy);
		float nx = -dy / len;
		float ny = dx / len;
		float sideSign = (e.id.hashCode() % 2 == 0) ? 1f : -1f;
		float sideOffset = e.roughSideOffset * sideSign;
		for (int i = 1; i < segments; i++) {
			float t = i / (float) segments;
			float x = MathUtils.lerp(start.x, end.x, t);
			float y = MathUtils.lerp(start.y, end.y, t);
			x += nx * sideOffset;
			y += ny * sideOffset;
			x += MathUtils.random(-amplitude, amplitude);
			y += MathUtils.random(-amplitude, amplitude);
			path.add(new Vector2f(x, y));
		}
		path.add(end.cpy());
		return path;
	}

	private TArray<Vector2f> smoothAndFit(TArray<Vector2f> poly) {
		if (poly == null || poly.size < 2) {
			return poly;
		}
		if (!_enableCatmullRom) {
			return poly;
		}
		TArray<Vector2f> out = new TArray<Vector2f>();
		for (int i = 0; i < poly.size - 1; i++) {
			Vector2f p0 = (i - 1 >= 0) ? poly.get(i - 1) : poly.get(i);
			Vector2f p1 = poly.get(i);
			Vector2f p2 = poly.get(i + 1);
			Vector2f p3 = (i + 2 < poly.size) ? poly.get(i + 2) : poly.get(i + 1);
			for (int s = 0; s < _catmullSamplesPerSegment; s++) {
				float t = s / _catmullSamplesPerSegment;
				Vector2f pt = CollisionHelper.catmullRom(p0, p1, p2, p3, t);
				out.add(pt);
			}
		}
		out.add(poly.get(poly.size - 1).cpy());
		if (_fitBezierSegments) {
			TArray<Vector2f> bezierPoly = new TArray<Vector2f>();
			for (int i = 0; i < out.size - 1; i += 2) {
				Vector2f p0 = out.get(i);
				Vector2f p2 = (i + 2 < out.size) ? out.get(i + 2) : out.get(out.size - 1);
				Vector2f pmid = out.get(MathUtils.min(i + 1, out.size - 1));
				Vector2f control = CollisionHelper.fitQuadControl(p0, pmid, p2);
				int seg = 4;
				for (int s = 0; s <= seg; s++) {
					float t = s / (float) seg;
					Vector2f pt = CollisionHelper.quadPoint(p0, control, p2, t);
					bezierPoly.add(pt);
				}
			}
			return bezierPoly;
		}
		return out;
	}

	private boolean detectIntersectionWithCities(Edge e, Vector2f a, Vector2f b, Vector2f control) {
		int segments = MathUtils.max(8, (int) (a.dst(b) / 12f));
		for (int i = 0; i < segments; i++) {
			float t0 = i / (float) segments;
			float t1 = (i + 1) / (float) segments;
			Vector2f p0 = (e.edgeType == EdgeType.STRAIGHT)
					? _tempPosA.set(MathUtils.lerp(a.x, b.x, t0), MathUtils.lerp(a.y, b.y, t0))
					: CollisionHelper.quadPoint(a, control, b, t0);
			Vector2f p1 = (e.edgeType == EdgeType.STRAIGHT)
					? _tempPosA.set(MathUtils.lerp(a.x, b.x, t1), MathUtils.lerp(a.y, b.y, t1))
					: CollisionHelper.quadPoint(a, control, b, t1);
			for (City c : _cities) {
				if (c == e.from || c == e.to) {
					continue;
				}
				if (CollisionHelper.segmentIntersectsCircle(p0, p1, c.screenPos,
						c.radius + _obstacleInflation + _stringPullInflation)) {
					return true;
				}
			}
		}
		return false;
	}

	private TArray<Vector2f> stringPull(TArray<Vector2f> path) {
		if (path == null || path.size <= 2) {
			return path;
		}
		TArray<Vector2f> out = new TArray<Vector2f>();
		int a = 0;
		out.add(path.get(0));
		while (a < path.size - 1) {
			int b = path.size - 1;
			for (; b > a; b--) {
				if (lineOfSight(path.get(a), path.get(b))) {
					break;
				}
			}
			out.add(path.get(b));
			a = b;
		}
		return out;
	}

	private boolean lineOfSight(Vector2f p, Vector2f q) {
		for (City c : _cities) {
			if (CollisionHelper.segmentIntersectsCircle(p, q, c.screenPos,
					c.radius + _obstacleInflation + _stringPullInflation))
				return false;
		}
		return true;
	}

	/**
	 * 绘制带缓动的连接线
	 * 
	 * @param g
	 * @param e
	 * @param poly
	 * @param nowTime
	 */
	private void drawPolylineWithProgress(GLEx g, float offsetX, float offsetY, Edge e, TArray<Vector2f> poly,
			float nowTime) {
		if (poly == null || poly.size < 2) {
			return;
		}
		if (!_edgeAnimationEnabled) {
			drawFullPolyline(g, offsetX, offsetY, poly, e.width, e.colorFrom, e.colorTo);
			return;
		}
		float total = 0f;
		final float[] segLen = new float[poly.size - 1];
		for (int i = 0; i < poly.size - 1; i++) {
			float l = poly.get(i).dst(poly.get(i + 1));
			segLen[i] = l;
			total += l;
		}
		float prog = e.progress(nowTime);
		float drawLen = total * prog;
		float remain = drawLen;
		for (int i = 0; i < poly.size - 1; i++) {
			Vector2f p0 = poly.get(i), p1 = poly.get(i + 1);
			float l = segLen[i];
			if (remain <= 0f) {
				break;
			}
			float t = MathUtils.min(1f, remain / l);
			Vector2f q = _tempPosA.set(MathUtils.lerp(p0.x, p1.x, t), MathUtils.lerp(p0.y, p1.y, t));
			LColor col = _tempColorA.setColor(e.colorFrom).lerp(e.colorTo, (i + 0.5f) / (poly.size - 1), _tempColorB);
			drawThickLine(g, p0.x + offsetX, p0.y + offsetY, q.x + offsetX, q.y + offsetY, e.width, col);
			remain -= l;
		}
	}

	/**
	 * 绘制完整连接线（无动画）
	 * 
	 * @param g
	 * @param poly
	 * @param width
	 * @param colorFrom
	 * @param colorTo
	 */
	private void drawFullPolyline(GLEx g, float offsetX, float offsetY, TArray<Vector2f> poly, float width,
			LColor colorFrom, LColor colorTo) {
		for (int i = 0; i < poly.size - 1; i++) {
			Vector2f p0 = poly.get(i);
			Vector2f p1 = poly.get(i + 1);
			LColor col = _tempColorA.setColor(colorFrom).lerp(colorTo, (i + 0.5f) / (poly.size - 1), _tempColorB);
			drawThickLine(g, p0.x + offsetX, p0.y + offsetY, p1.x + offsetX, p1.y + offsetY, width, col);
		}
	}

	/**
	 * 绘制带缓动的单条边
	 * 
	 * @param g
	 * @param offsetX
	 * @param offsetY
	 * @param e
	 * @param a
	 * @param b
	 * @param control
	 * @param nowTime
	 */
	private void drawSingleEdgeWithProgress(GLEx g, float offsetX, float offsetY, Edge e, Vector2f a, Vector2f b,
			Vector2f control, float nowTime) {
		if (!_edgeAnimationEnabled) {
			drawFullSingleEdge(g, offsetX, offsetY, e, a, b, control);
			return;
		}
		int segments = MathUtils.max(6, (int) (a.dst(b) / 12f));
		float prog = e.progress(nowTime);
		int drawSegments = (int) (segments * prog);
		if (drawSegments < 1) {
			drawSegments = 1;
		}
		for (int i = 0; i < drawSegments; i++) {
			float t0 = i / (float) segments;
			float t1 = (i + 1) / (float) segments;
			Vector2f p0 = (e.edgeType == EdgeType.STRAIGHT)
					? _tempPosA.set(MathUtils.lerp(a.x, b.x, t0), MathUtils.lerp(a.y, b.y, t0))
					: CollisionHelper.quadPoint(a, control, b, t0);
			Vector2f p1 = (e.edgeType == EdgeType.STRAIGHT)
					? _tempPosB.set(MathUtils.lerp(a.x, b.x, t1), MathUtils.lerp(a.y, b.y, t1))
					: CollisionHelper.quadPoint(a, control, b, t1);
			LColor col = _tempColorA.setColor(e.colorFrom).lerp(e.colorTo, (t0 + t1) * 0.5f, _tempColorB);
			drawThickLine(g, p0.x + offsetX, p0.y + offsetY, p1.x + offsetX, p1.y + offsetY, e.width, col);
		}
	}

	/**
	 * 绘制完整单条边
	 * 
	 * @param g
	 * @param offsetX
	 * @param offsetY
	 * @param e
	 * @param a
	 * @param b
	 * @param control
	 */
	private void drawFullSingleEdge(GLEx g, float offsetX, float offsetY, Edge e, Vector2f a, Vector2f b,
			Vector2f control) {
		int segments = MathUtils.max(6, (int) (a.dst(b) / 12f));
		for (int i = 0; i < segments; i++) {
			float t0 = i / (float) segments;
			float t1 = (i + 1) / (float) segments;
			Vector2f p0 = (e.edgeType == EdgeType.STRAIGHT)
					? _tempPosA.set(MathUtils.lerp(a.x, b.x, t0), MathUtils.lerp(a.y, b.y, t0))
					: CollisionHelper.quadPoint(a, control, b, t0);
			Vector2f p1 = (e.edgeType == EdgeType.STRAIGHT)
					? _tempPosB.set(MathUtils.lerp(a.x, b.x, t1), MathUtils.lerp(a.y, b.y, t1))
					: CollisionHelper.quadPoint(a, control, b, t1);
			LColor col = _tempColorA.setColor(e.colorFrom).lerp(e.colorTo, (t0 + t1) * 0.5f, _tempColorB);
			drawThickLine(g, p0.x + offsetX, p0.y + offsetY, p1.x + offsetX, p1.y + offsetY, e.width, col);
		}
	}

	private void drawThickLine(GLEx g, float x1, float y1, float x2, float y2, float width, LColor color) {
		g.drawLine(x1, y1, x2, y2, width, color);
	}

	/**
	 * 统一执行城市边线碰撞避让计算
	 */
	public void resolveCityCollisions() {
		if (_cities.size > 0 && _cityCollisionEnabled) {
			_resolver.resolveAll(_cities, _cityEgdeMaxIter, _cityEgdePadding);
		}
	}

	/**
	 * 批量添加城市
	 * 
	 * @param citiesArr
	 */
	public void addCities(City... citiesArr) {
		for (City c : citiesArr) {
			addCity(c);
		}
	}

	/**
	 * 批量添加边线
	 * 
	 * @param edgesArr
	 */
	public void addEdges(Edge... edgesArr) {
		for (Edge e : edgesArr)
			addEdge(e);
	}

	/**
	 * 根据ID获取城市
	 * 
	 * @param id
	 * @return
	 */
	public City getCityById(String id) {
		for (City c : _cities)
			if (c.getId().equals(id)) {
				return c;
			}
		return null;
	}

	/**
	 * 根据ID获取边线
	 * 
	 * @param id
	 * @return
	 */
	public Edge getEdgeById(String id) {
		for (Edge e : _edges)
			if (e.id.equals(id)) {
				return e;
			}
		return null;
	}

	/**
	 * 清空所有城市和边线
	 */
	public void clearCities() {
		_cities.clear();
		clearEdges();
	}

	/**
	 * 清空所有边线
	 */
	public void clearEdges() {
		_edges.clear();
		_edgePathCache.clear();
		_dirtyEdges.clear();
	}

	/**
	 * 设置连线缓动动画开关
	 * 
	 * @param enabled
	 * @return
	 */
	public CityMap setEdgeAnimationEnabled(boolean enabled) {
		this._edgeAnimationEnabled = enabled;
		for (Edge e : _edges) {
			markEdgeDirty(e);
		}
		return this;
	}

	public boolean isEdgeAnimationEnabled() {
		return _edgeAnimationEnabled;
	}

	/**
	 * 设置城市名称显示
	 * 
	 * @param show
	 * @return
	 */
	public CityMap setShowCityNames(boolean show) {
		this._showCityNames = show;
		return this;
	}

	/**
	 * 设置城市名称样式
	 * 
	 * @param c
	 * @return
	 */
	public CityMap setCityNameColor(LColor c) {
		this._cityNameColor = c;
		return this;
	}

	/**
	 * 设定默认的开始边线颜色
	 * 
	 * @param c
	 * @return
	 */
	public CityMap setDefaultEdgeColorFrom(LColor c) {
		if (c != null) {
			_defaultEdgeColorFrom = c;
		}
		return this;
	}

	public LColor getDefaultEdgeColorFrom() {
		return _defaultEdgeColorFrom;
	}

	/**
	 * 设定默认的结束边线颜色
	 * 
	 * @param c
	 * @return
	 */
	public CityMap setDefaultEdgeColorTo(LColor c) {
		if (c != null) {
			_defaultEdgeColorTo = c;
		}
		return this;
	}

	public LColor getDefaultEdgeColorTo() {
		return _defaultEdgeColorTo;
	}

	/**
	 * 设置默认的边线样式
	 * 
	 * @param e
	 * @return
	 */
	public CityMap setDefaultEdgeType(EdgeType e) {
		if (e != null) {
			_defaultEdgeType = e;
		}
		return this;
	}

	public EdgeType getDefaultEdgeType() {
		return _defaultEdgeType;
	}

	/**
	 * 设置默认边样式
	 * 
	 * @param fromColor
	 * @param toColor
	 * @param width
	 * @param animDuration
	 * @param edge
	 * @return
	 */
	public CityMap setDefaultEdgeStyle(LColor fromColor, LColor toColor, float width, float animDuration,
			EdgeType edge) {
		this._defaultEdgeColorFrom = fromColor;
		this._defaultEdgeColorTo = toColor;
		this._defaultEdgeWidth = width;
		this._defaultEdgeAnimationDuration = animDuration;
		this._defaultEdgeType = edge;
		return this;
	}

	/**
	 * 设置城市碰撞避让开关
	 * 
	 * @param enabled
	 * @return
	 */
	public CityMap setCityCollisionEnabled(boolean enabled) {
		this._cityCollisionEnabled = enabled;
		if (enabled) {
			resolveCityCollisions();
		}
		return this;
	}

	/**
	 * 设置地图与真实世界缩放比例
	 * 
	 * @param scale
	 * @return
	 */
	public CityMap setMapScale(float scale) {
		this._cityMapScale = scale;
		refreshAllCityPositions();
		return this;
	}

	/**
	 * 刷新所有城市坐标
	 */
	public void refreshAllCityPositions() {
		for (City c : _cities) {
			Vector2f mapPos = toMapPixels(c.lat, c.lon, _originMeters, _cityMapScale, _useOrthogonalPos);
			c.screenPos.set(mapPos.x, mapPos.y);
		}
		resolveCityCollisions();
		for (Edge e : _edges) {
			markEdgeDirty(e);
		}
	}

	/**
	 * 通过屏幕坐标获取对应的城市
	 * 
	 * @param screenX 屏幕X坐标
	 * @param screenY 屏幕Y坐标
	 * @return
	 */
	public City getCityAtScreenPos(float screenX, float screenY) {
		City targetCity = null;
		float minDistance = Float.MAX_VALUE;
		for (City city : _cities) {
			float cx = city.screenPos.x;
			float cy = city.screenPos.y;
			float dx = screenX - cx;
			float dy = screenY - cy;
			float distance = MathUtils.sqrt(dx * dx + dy * dy);
			if (distance <= city.radius && distance < minDistance) {
				minDistance = distance;
				targetCity = city;
			}
		}
		return targetCity;
	}

	public City getCityAtScreenPos(Vector2f screenPos) {
		if (screenPos == null) {
			return null;
		}
		return getCityAtScreenPos(screenPos.x, screenPos.y);
	}

	/**
	 * 判断屏幕坐标是否在指定城市范围内
	 * 
	 * @param city
	 * @param screenX
	 * @param screenY
	 * @return
	 */
	public boolean isPosInCity(City city, float screenX, float screenY) {
		if (city == null) {
			return false;
		}
		float dx = screenX - city.screenPos.x;
		float dy = screenY - city.screenPos.y;
		return MathUtils.sqrt(dx * dx + dy * dy) <= city.radius;
	}

	/**
	 * 获得指定屏幕像素坐标对应的城市
	 * 
	 * @param screenX
	 * @param screenY
	 * @return
	 */
	public City getCityByScreenPos(float screenX, float screenY) {
		City nearest = null;
		float minDistSq = Float.MAX_VALUE;
		for (City c : _cities) {
			if (c != null) {
				float dx = screenX - c.screenPos.x;
				float dy = screenY - c.screenPos.y;
				float distSq = dx * dx + dy * dy;
				if (distSq <= c.radius * c.radius && distSq < minDistSq) {
					minDistSq = distSq;
					nearest = c;
				}
				if (nearest == null) {
					if (c.intersect(screenX, screenY)
							|| c.intersect(screenX, screenY, getTileWidth(), getTileHeight())) {
						nearest = c;
					}
				}
			}
		}
		return nearest;
	}

	public City getCityByScreenPos(Vector2f screenPos) {
		if (screenPos == null) {
			return null;
		}
		return getCityByScreenPos(screenPos.x, screenPos.y);
	}

	/**
	 * 以真实地理坐标(世界地图定位)获取地图上城市
	 * 
	 * @param lat
	 * @param lon
	 * @return
	 */
	public City getCityByLatLon(float lat, float lon) {
		Vector2f screenPos = toMapPixels(lat, lon, _originMeters, _cityMapScale, false);
		return getCityByScreenPos(screenPos);
	}

	public City getCityByLatLon(Vector2f latLon) {
		if (latLon == null) {
			return null;
		}
		return getCityByLatLon(latLon.x, latLon.y);
	}

	/**
	 * 以xy正交平面坐标获得地图上城市
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public City getCityByOrthogonalPos(float x, float y) {
		Vector2f screenPos = toMapPixels(x, y, _originMeters, _cityMapScale, true);
		return getCityByScreenPos(screenPos);
	}

	/**
	 * 以xy正交平面坐标获得地图上城市
	 * 
	 * @param pos
	 * @return
	 */
	public City getCityByOrthogonalPos(Vector2f pos) {
		if (pos == null) {
			return null;
		}
		return getCityByOrthogonalPos(pos.x, pos.y);
	}

	/**
	 * 以坐标获得当前城市
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public City getCityByWorldPos(float x, float y) {
		if (_useOrthogonalPos) {
			return getCityByOrthogonalPos(x, y);
		} else {
			return getCityByLatLon(x, y);
		}
	}

	/**
	 * 以像素位置获得当前屏幕城市
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public City getCityByPixelToWorldPos(float x, float y) {
		return getCityByScreenPos(offsetXPixel(x), offsetYPixel(y));
	}

	public City getCityByWorldPos(Vector2f worldPos) {
		if (worldPos == null) {
			return null;
		}
		return getCityByWorldPos(worldPos.x, worldPos.y);
	}

	@Override
	public int[][] getMap() {
		return _field2d.getMap();
	}

	public boolean isActive() {
		return _active;
	}

	public boolean isValid(int x, int y) {
		return this._field2d.inside(x, y);
	}

	public CityMap replaceType(int oldid, int newid) {
		_field2d.replaceType(oldid, newid);
		return this;
	}

	public int getTileID(int x, int y) {
		if (x >= 0 && x < _field2d.getWidth() && y >= 0 && y < _field2d.getHeight()) {
			return _field2d.getTileType(x, y);
		} else {
			return -1;
		}
	}

	public CityMap setTileID(int x, int y, int id) {
		if (x >= 0 && x < _field2d.getWidth() && y >= 0 && y < _field2d.getHeight()) {
			_field2d.setTileType(x, y, id);
		}
		return this;
	}

	public CityMap addMapSprite(ISprite sprite) {
		_mapSprites.add(sprite);
		return this;
	}

	public CityMap addMapSpriteAt(ISprite sprite, float x, float y) {
		_mapSprites.addAt(sprite, x, y);
		return this;
	}

	public CityMap removeMapSprite(int idx) {
		_mapSprites.remove(idx);
		return this;
	}

	public CityMap removeMapSprite(ISprite sprite) {
		_mapSprites.remove(sprite);
		return this;
	}

	public CityMap removeMapSprite(int start, int end) {
		_mapSprites.remove(start, end);
		return this;
	}

	public float getTileScale() {
		return MathUtils.max(_scaleX, _scaleY);
	}

	public CityMap setTileScale(float s) {
		this._scaleX = this._scaleY = s;
		return this;
	}

	public float centerX() {
		return ((getContainerX() + getContainerWidth()) - (getX() + getWidth())) / 2f;
	}

	public float centerY() {
		return ((getContainerY() + getContainerHeight()) - (getY() + getHeight())) / 2f;
	}

	public boolean isContentPositionInBounds(float x, float y) {
		float offX = MathUtils.min(this._offset.x);
		float offY = MathUtils.min(this._offset.y);
		if (x < offX) {
			return false;
		}
		if (x >= offX + (getContainerWidth() - getWidth())) {
			return false;
		}
		if (y < offY) {
			return false;
		}
		if (y >= offY + (getContainerHeight() - getHeight())) {
			return false;
		}
		return true;
	}

	public CityMap scrollDown(float distance) {
		if (distance == 0) {
			return this;
		}
		this._offset.y = (this._offset.y + distance);
		return this;
	}

	public CityMap scrollLeft(float distance) {
		if (distance == 0) {
			return this;
		}
		this._offset.x = (this._offset.x - distance);
		return this;
	}

	public CityMap scrollRight(float distance) {
		if (distance == 0) {
			return this;
		}
		this._offset.x = (this._offset.x + distance);
		return this;
	}

	public CityMap scrollUp(float distance) {
		if (distance == 0) {
			return this;
		}
		this._offset.y = this._offset.y - distance;
		return this;
	}

	public CityMap scrollLeftUp(float distance) {
		this.scrollUp(distance);
		this.scrollLeft(distance);
		return this;
	}

	public CityMap scrollRightDown(float distance) {
		this.scrollDown(distance);
		this.scrollRight(distance);
		return this;
	}

	public CityMap scrollClear() {
		if (!this._offset.equals(0f, 0f)) {
			this._offset.set(0, 0);
		}
		return this;
	}

	public CityMap scroll(float x, float y) {
		return scroll(x, y, 4f);
	}

	public CityMap scroll(float x, float y, float distance) {
		if (_scrollDrag.x == 0f && _scrollDrag.y == 0f) {
			_scrollDrag.set(x, y);
			return this;
		}
		return scroll(_scrollDrag.x, _scrollDrag.y, x, y, distance);
	}

	public CityMap scroll(float x1, float y1, float x2, float y2) {
		return scroll(x1, y1, x2, y2, 4f);
	}

	public CityMap scroll(float x1, float y1, float x2, float y2, float distance) {
		if (this._follow != null) {
			return this;
		}
		if (x1 < x2 && x1 > centerX()) {
			scrollRight(distance);
		} else if (x1 > x2) {
			scrollLeft(distance);
		}
		if (y1 < y2 && y1 > centerY()) {
			scrollDown(distance);
		} else if (y1 > y2) {
			scrollUp(distance);
		}
		_scrollDrag.set(x2, y2);
		return this;
	}

	public int[] getLimit() {
		return _field2d.getLimit();
	}

	public CityMap setLimit(int[] limit) {
		_field2d.setLimit(limit);
		return this;
	}

	public CityMap setAllowMove(int[] args) {
		_field2d.setAllowMove(args);
		return this;
	}

	@Override
	public boolean isHit(int px, int py) {
		return _field2d.isHit(px, py);
	}

	public boolean isHit(Vector2f v) {
		return isHit(v.x(), v.y());
	}

	@Override
	public boolean isPixelHit(int px, int py) {
		return isPixelHit(px, py, 0, 0);
	}

	public boolean isPixelHit(int px, int py, int movePx, int movePy) {
		return isHit(_field2d.pixelsToTilesWidth(_field2d.offsetXPixel(px)) + movePx,
				_field2d.pixelsToTilesHeight(_field2d.offsetYPixel(py)) + movePy);
	}

	@Override
	public boolean isPixelTUp(int px, int py) {
		return isPixelHit(px, py, 0, -1);
	}

	@Override
	public boolean isPixelTRight(int px, int py) {
		return isPixelHit(px, py, 1, 0);
	}

	@Override
	public boolean isPixelTLeft(int px, int py) {
		return isPixelHit(px, py, -1, 0);
	}

	@Override
	public boolean isPixelTDown(int px, int py) {
		return isPixelHit(px, py, 0, 1);
	}

	@Override
	public Vector2f getTileCollision(LObject<?> o, float newX, float newY) {
		return _field2d.getTileCollision(o.getX(), o.getY(), o.getWidth(), o.getHeight(), newX, newY);
	}

	public int getTileIDFromPixels(Vector2f v) {
		return getTileIDFromPixels(v.x, v.y);
	}

	public int getTileIDFromPixels(float sx, float sy) {
		float x = (sx + _offset.getX());
		float y = (sy + _offset.getY());
		Vector2f tileCoordinates = pixelsToTiles(x, y);
		return getTileID(MathUtils.round(tileCoordinates.getX()), MathUtils.round(tileCoordinates.getY()));
	}

	public Vector2f pixelsToTiles(float x, float y) {
		float xprime = x / _scaleX / _field2d.getTileWidth() - 1;
		float yprime = y / _scaleY / _field2d.getTileHeight() - 1;
		return new Vector2f(xprime, yprime);
	}

	@Override
	public int tilesToPixelsX(float x) {
		return MathUtils.floor(_field2d.tilesToWidthPixels(x) * _scaleX);
	}

	@Override
	public int tilesToPixelsY(float y) {
		return MathUtils.floor(_field2d.tilesToHeightPixels(y) * _scaleY);
	}

	@Override
	public int pixelsToTilesWidth(float x) {
		return _field2d.pixelsToTilesWidth(x / _scaleX);
	}

	@Override
	public int pixelsToTilesHeight(float y) {
		return _field2d.pixelsToTilesHeight(y / _scaleY);
	}

	public PointI pixelsToCityMap(float x, float y) {
		int tileX = pixelsToTilesWidth(x);
		int tileY = pixelsToTilesHeight(y);
		return new PointI(tileX, tileY);
	}

	public PointI tilePixels(float x, float y) {
		int newX = getPixelX(x);
		int newY = getPixelY(y);
		return new PointI(newX, newY);
	}

	public PointI CityMapToPixels(float x, float y) {
		int tileX = tilesToPixelsX(x);
		int tileY = tilesToPixelsY(y);
		return new PointI(tileX, tileY);
	}

	public PointI CityMapToScrollTilePixels(float x, float y) {
		int newX = toTileScrollPixelX(x);
		int newY = toTileScrollPixelX(y);
		return new PointI(newX, newY);
	}

	public PointI pixelsToScrollCityMap(float x, float y) {
		int tileX = toPixelScrollTileX(x);
		int tileY = toPixelScrollTileY(y);
		return new PointI(tileX, tileY);
	}

	public Vector2f tilesToPixels(float x, float y) {
		float xprime = x * _field2d.getTileWidth() - _offset.getX();
		float yprime = y * _field2d.getTileHeight() - _offset.getY();
		return new Vector2f(xprime, yprime);
	}

	public CityMap switchMap(MapSwitchMaker ms) {
		_field2d.switchMap(ms);
		return this;
	}

	public CityMap centerOffset() {
		this._offset.set(centerX(), centerY());
		return this;
	}

	public CityMap setOffset(float x, float y) {
		this._offset.set(x, y);
		return this;
	}

	@Override
	public CityMap setOffset(Vector2f offset) {
		this._offset.set(offset);
		return this;
	}

	@Override
	public Vector2f getOffset() {
		return _offset;
	}

	public CityMap setFollowOffset(float x, float y) {
		this._followOffset.set(x, y);
		return this;
	}

	public CityMap setFollowOffset(Vector2f offset) {
		this._followOffset.set(offset);
		return this;
	}

	public Vector2f getFollowOffset() {
		return this._followOffset;
	}

	@Override
	public int getTileWidth() {
		return _field2d.getTileWidth();
	}

	@Override
	public int getTileHeight() {
		return _field2d.getTileHeight();
	}

	@Override
	public float getHeight() {
		return (_field2d.getHeight() * _field2d.getTileHeight() * _scaleY) - _fixedHeightOffset;
	}

	@Override
	public float getWidth() {
		return (_field2d.getWidth() * _field2d.getTileWidth() * _scaleX) - _fixedWidthOffset;
	}

	@Override
	public int getRow() {
		return _field2d.getWidth();
	}

	@Override
	public int getCol() {
		return _field2d.getHeight();
	}

	public CityMap setMapValues(int v) {
		_field2d.setValues(v);
		return this;
	}

	public Field2D getNewField2D() {
		return new Field2D(_field2d);
	}

	public boolean isDirty() {
		return _dirty;
	}

	public CityMap setDirty(boolean dirty) {
		this._dirty = dirty;
		return this;
	}

	@Override
	public void setVisible(boolean v) {
		this._visible = v;
	}

	@Override
	public boolean isVisible() {
		return _visible;
	}

	@Override
	public void createUI(GLEx g) {
		createUI(g, 0f, 0f);
	}

	@Override
	public void createUI(GLEx g, float offsetX, float offsetY) {
		if (!_visible) {
			return;
		}
		final boolean update = (_objectRotation != 0)
				|| !(MathUtils.equal(_scaleX, 1f) && MathUtils.equal(_scaleY, 1f));
		final int blend = g.getBlendMode();
		final int color = g.color();
		try {
			g.setBlendMode(_GL_BLEND);
			g.setAlpha(_objectAlpha);
			if (this._roll) {
				this._offset = toRollPosition(this._offset);
			}
			float newX = this._objectLocation.x + offsetX + _offset.getX();
			float newY = this._objectLocation.y + offsetY + _offset.getY();
			if (update) {
				g.saveTx();
				Affine2f tx = g.tx();
				if (_objectRotation != 0) {
					final float rotationCenterX = newX + getWidth() / 2f;
					final float rotationCenterY = newY + getHeight() / 2f;
					tx.translate(rotationCenterX, rotationCenterY);
					tx.preRotate(_objectRotation);
					tx.translate(-rotationCenterX, -rotationCenterY);
				}
				if ((_scaleX != 1f) || (_scaleY != 1f)) {
					final float scaleCenterX = newX + getWidth() / 2f;
					final float scaleCenterY = newY + getHeight() / 2f;
					tx.translate(scaleCenterX, scaleCenterY);
					tx.preScale(_scaleX, _scaleY);
					tx.translate(-scaleCenterX, -scaleCenterY);
				}
			}
			followActionObject();
			final int moveX = MathUtils.ifloor(newX);
			final int moveY = MathUtils.ifloor(newY);
			draw(g, moveX, moveY);
			if (_mapSprites != null) {
				_mapSprites.paintPos(g, moveX, moveY);
			}
		} catch (Throwable ex) {
			LSystem.error("The CityMap error !", ex);
		} finally {
			if (update) {
				g.restoreTx();
			}
			g.setBlendMode(blend);
			g.setColor(color);
		}
	}

	public void draw(GLEx g) {
		if (this._roll) {
			this._offset = this.toRollPosition(this._offset);
		}
		draw(g, x() + _offset.x(), y() + _offset.y());
	}

	public void draw(GLEx g, int offsetX, int offsetY) {

		if (_background != null) {
			if (_backgroundSize.isEmpty()) {
				g.draw(_background, offsetX + _backgroundOffset.x, offsetY + _backgroundOffset.y, _baseColor);
			} else {
				g.draw(_background, offsetX + _backgroundOffset.x, offsetY + _backgroundOffset.y, _backgroundSize.x,
						_backgroundSize.y, _baseColor);
			}
		}

		if (!_dirtyEdges.isEmpty()) {

			int processed = 0;
			TArray<String> keys = new TArray<String>();
			keys.addAll(_dirtyEdges.keys());
			for (String id : keys) {
				if (processed >= _maxEdgeProcessPerFrame) {
					break;
				}
				Edge e = _dirtyEdges.get(id);
				if (e != null) {
					TArray<Vector2f> path = computeEdgePath(e);
					if (path != null) {
						_edgePathCache.put(e.id, path);
					} else {
						_edgePathCache.remove(e.id);
					}
				}
				_dirtyEdges.remove(id);
				processed++;
			}
		}
		for (int i = _edges.size - 1; i > -1; i--) {
			Edge e = _edges.get(i);
			if (e != null) {
				TArray<Vector2f> poly = _edgePathCache.get(e.id);
				if (poly != null && poly.size >= 2) {
					drawPolylineWithProgress(g, offsetX, offsetY, e, poly, _nowDelta);
				} else {
					Vector2f a = e.from.screenPos;
					Vector2f b = e.to.screenPos;
					Vector2f aC = CollisionHelper.clipPointToCircle(b, a, a, e.from.radius);
					Vector2f bC = CollisionHelper.clipPointToCircle(a, b, b, e.to.radius);
					Vector2f control = CollisionHelper.defaultQuadControl(aC, bC, e.bezierOffsetRatio);
					drawSingleEdgeWithProgress(g, offsetX, offsetY, e, aC, bC, control, _nowDelta);
				}
			}
		}
		for (int i = _cities.size - 1; i > -1; i--) {
			City c = _cities.get(i);
			if (c != null) {
				c.draw(g, offsetX, offsetY);
				if (_showCityNames && c.getName() != null && !c.getName().isEmpty()) {
					float textX = offsetX + c.screenPos.x - g.getFont().stringWidth(c.getName()) / 2f
							+ _cityNameOffsetX;
					float textY = offsetY + c.screenPos.y + c.radius + _cityNameOffsetY;
					g.drawString(c.getName(), textX, textY, _cityNameColor);
				}

			}
		}
		if (_selectedCity != null) {
			Vector2f pos = _selectedCity.screenPos;
			float radius = _selectedCity.radius * 2 + 4;
			final float newX = pos.x + offsetX - radius / 2;
			final float newY = pos.y + offsetY - radius / 2;
			g.drawDashCircle(newX, newY, radius, 3f, SELECT_HALO_COLOR);
			float pulseRadius = radius + 8f;
			g.drawDashCircle(newX - 4, newY - 4, pulseRadius, 3f,
					SELECT_HALO_COLOR.lerp(LColor.yellow, 0.5f, _tempColorB));
		}

		if (_drawListener != null) {
			_drawListener.draw(g, offsetX, offsetY);
		}
	}

	/**
	 * 获取所有城市数量
	 */
	public int getCityCount() {
		return _cities.size;
	}

	/**
	 * 获取所有边线数量
	 */
	public int getEdgeCount() {
		return _edges.size;
	}

	public boolean containsCity(City c) {
		return c != null && _cities.contains(c, true);
	}

	public boolean containsEdge(Edge e) {
		return e != null && _edges.contains(e, true);
	}

	public TArray<City> getAllCities() {
		return new TArray<City>(_cities);
	}

	public TArray<Edge> getAllEdges() {
		return new TArray<Edge>(_edges);
	}

	public DrawListener<CityMap> getListener() {
		return _drawListener;
	}

	public CityMap setListener(DrawListener<CityMap> l) {
		this._drawListener = l;
		return this;
	}

	public float getCityNameOffsetX() {
		return _cityNameOffsetX;
	}

	public CityMap setCityNameOffsetX(float offset) {
		this._cityNameOffsetX = offset;
		return this;
	}

	public float getCityNameOffsetY() {
		return _cityNameOffsetY;
	}

	public CityMap setCityNameOffsetY(float offset) {
		this._cityNameOffsetY = offset;
		return this;
	}

	@Override
	public RectBox getCollisionBox() {
		return getRect(x() + _offset.x, y() + _offset.y, _field2d.getTileWidth() * _field2d.getWidth(),
				_field2d.getTileHeight() * _field2d.getHeight());
	}

	@Override
	public LTexture getBitmap() {
		return _background;
	}

	@Override
	public void update(long elapsedTime) {
		float delta = Duration.toS(elapsedTime);
		_nowDelta += delta;
		if (_playAnimation) {
			for (int i = _cities.size - 1; i > -1; i--) {
				City c = _cities.get(i);
				if (c != null) {
					c.update(delta);
				}
			}
		}
		if (_mapSprites != null) {
			_mapSprites.update(elapsedTime);
		}
		if (_drawListener != null) {
			_drawListener.update(elapsedTime);
		}
	}

	public CityMap startAnimation() {
		_playAnimation = true;
		return this;
	}

	public CityMap stopAnimation() {
		_playAnimation = false;
		return this;
	}

	protected float limitOffsetX(float newOffsetX) {
		float offsetX = getContainerWidth() / 2 - newOffsetX;
		offsetX = MathUtils.min(offsetX, 0);
		offsetX = MathUtils.max(offsetX, getContainerWidth() - getWidth());
		return offsetX + _followOffset.x;
	}

	protected float limitOffsetY(float newOffsetY) {
		float offsetY = getContainerHeight() / 2 - newOffsetY;
		offsetY = MathUtils.min(offsetY, 0);
		offsetY = MathUtils.max(offsetY, getContainerHeight() - getHeight());
		return offsetY + _followOffset.y;
	}

	public CityMap followActionObject() {
		if (_follow != null) {
			float offsetX = limitOffsetX(_follow.getX());
			float offsetY = limitOffsetY(_follow.getY());
			if (offsetX != 0 || offsetY != 0) {
				setOffset(offsetX, offsetY);
				_field2d.setOffset(_offset);
			}
		}
		return this;
	}

	@Override
	public LColor getColor() {
		return new LColor(_baseColor);
	}

	@Override
	public void setColor(LColor c) {
		if (c != null && !c.equals(_baseColor)) {
			this._baseColor = c;
			this._dirty = true;
		}
	}

	public int getPixelsAtFieldType(Vector2f pos) {
		return _field2d.getPixelsAtFieldType(pos.x, pos.y);
	}

	public int getPixelsAtFieldType(float x, float y) {
		int itsX = pixelsToTilesWidth(x);
		int itsY = pixelsToTilesHeight(y);
		return _field2d.getPixelsAtFieldType(itsX, itsY);
	}

	@Override
	public Field2D getField2D() {
		return _field2d;
	}

	@Override
	public float getScaleX() {
		return _scaleX;
	}

	@Override
	public float getScaleY() {
		return _scaleY;
	}

	public void setScale(float scale) {
		setScale(scale, scale);
	}

	@Override
	public void setScale(float sx, float sy) {
		this._scaleX = sx;
		this._scaleY = sy;
	}

	@Override
	public CityMap setSize(float w, float h) {
		setScale(w / getWidth(), h / getHeight());
		return this;
	}

	@Override
	public boolean isBounded() {
		return false;
	}

	@Override
	public boolean isContainer() {
		return true;
	}

	@Override
	public boolean inContains(float x, float y, float w, float h) {
		return _field2d.getRect().contains(x, y, w, h);
	}

	@Override
	public RectBox getRectBox() {
		return getCollisionBox();
	}

	public ActionBind getFollow() {
		return _follow;
	}

	public CityMap setFollow(ActionBind follow) {
		this._follow = follow;
		return this;
	}

	public CityMap followDonot() {
		return setFollow(null);
	}

	public CityMap followAction(ActionBind follow) {
		return setFollow(follow);
	}

	public Vector2f toTilesScrollPixels(float x, float y) {
		return new Vector2f(toTileScrollPixelX(x), toTileScrollPixelY(y));
	}

	public int toTileScrollPixelX(float x) {
		return offsetXPixel(tilesToPixelsX(x));
	}

	public int toTileScrollPixelY(float y) {
		return offsetYPixel(tilesToPixelsY(y));
	}

	public Vector2f toPixelsScrollTiles(float x, float y) {
		return new Vector2f(toPixelScrollTileX(x), toPixelScrollTileY(y));
	}

	public int toPixelScrollTileX(float x) {
		return pixelsToTilesWidth(offsetXPixel(x));
	}

	public int toPixelScrollTileY(float y) {
		return pixelsToTilesHeight(offsetYPixel(y));
	}

	public Vector2f offsetNScalePixels(XY pos) {
		if (pos == null) {
			return offsetPixels(0, 0);
		}
		return offsetPixels(pos.getX(), pos.getY());
	}

	public Vector2f offsetNScalePixels(float x, float y) {
		return new Vector2f(offsetXNScalePixel(x), offsetYNScalePixel(y));
	}

	public int getNScalePixelX(float x) {
		return MathUtils.iceil((x - _objectLocation.x));
	}

	public int getNScalePixelY(float y) {
		return MathUtils.iceil((y - _objectLocation.y));
	}

	public int offsetXNScalePixel(float x) {
		return MathUtils.iceil(x - _offset.x - _objectLocation.x);
	}

	public int offsetYNScalePixel(float y) {
		return MathUtils.iceil(y - _offset.y - _objectLocation.y);
	}

	public Vector2f offsetPixels(XY pos) {
		if (pos == null) {
			return offsetPixels(0, 0);
		}
		return offsetPixels(pos.getX(), pos.getY());
	}

	public Vector2f offsetPixels(float x, float y) {
		return new Vector2f(offsetXPixel(x), offsetYPixel(y));
	}

	public int getPixelX(float x) {
		return MathUtils.iceil((x - _objectLocation.x) / _scaleX);
	}

	public int getPixelY(float y) {
		return MathUtils.iceil((y - _objectLocation.y) / _scaleY);
	}

	public int offsetXPixel(float x) {
		return MathUtils.iceil((x - _offset.x - _objectLocation.x) / _scaleX);
	}

	public int offsetYPixel(float y) {
		return MathUtils.iceil((y - _offset.y - _objectLocation.y) / _scaleY);
	}

	public Vector2f getScreenPixel(XY pos) {
		if (pos == null) {
			return getScreenPixel(0, 0);
		}
		return getScreenPixel(pos.getX(), pos.getY());
	}

	public Vector2f getScreenPixel(float x, float y) {
		return new Vector2f(getScreenPixelX(x), getScreenPixelY(y));
	}

	public float getScreenPixelX(float x) {
		return (x + _objectLocation.x + _offset.x) / _scaleX;
	}

	public float getScreenPixelY(float y) {
		return (y + _objectLocation.y + _offset.y) / _scaleY;
	}

	public Vector2f getScreenNScalePixel(XY pos) {
		if (pos == null) {
			return getScreenNScalePixel(0, 0);
		}
		return getScreenNScalePixel(pos.getX(), pos.getY());
	}

	public Vector2f getScreenNScalePixel(float x, float y) {
		return new Vector2f(getScreenPixelX(x), getScreenPixelY(y));
	}

	public float getScreenNScalePixelX(float x) {
		return (x + _objectLocation.x + _offset.x);
	}

	public float getScreenNScalePixelY(float y) {
		return (y + _objectLocation.y + _offset.y);
	}

	public boolean inMap(int x, int y) {
		return ((((x >= 0) && (x < _pixelInWidth)) && (y >= 0)) && (y < _pixelInHeight));
	}

	public MoveControl followControl(ActionBind bind) {
		followAction(bind);
		return new MoveControl(bind, this._field2d);
	}

	public Vector2f toRollPosition(Vector2f pos) {
		pos.x = pos.x % _field2d.getViewWidth();
		pos.y = pos.y % _field2d.getViewHeight();
		if (pos.x < 0f) {
			pos.x += _field2d.getViewWidth();
		}
		if (pos.y < 0f) {
			pos.y += _field2d.getViewHeight();
		}
		return pos;
	}

	public float getObstacleInflation() {
		return _obstacleInflation;
	}

	public CityMap setObstacleInflation(float o) {
		_obstacleInflation = o;
		return this;
	}

	public boolean isRoll() {
		return _roll;
	}

	public CityMap setRoll(boolean roll) {
		this._roll = roll;
		return this;
	}

	public LTexture getBackground() {
		return this._background;
	}

	public CityMap setBackground(LTexture bg) {
		this._background = bg;
		return this;
	}

	public CityMap setBackground(String path) {
		if (StringUtils.isEmpty(path)) {
			return this;
		}
		return this.setBackground(LTextures.loadTexture(path));
	}

	public CityMap setBackground(String path, float w, float h) {
		if (StringUtils.isEmpty(path)) {
			return this;
		}
		return this.setBackground(LTextures.loadTexture(path).scale(w, h));
	}

	public boolean move(ActionBind o, float newX, float newY) {
		return move(o, newX, newY, true);
	}

	public boolean move(ActionBind o, float newX, float newY, boolean toMoved) {
		if (o == null) {
			return false;
		}
		float x = offsetXPixel(o.getX()) + newX;
		float y = offsetYPixel(o.getY()) + newY;
		if (!_field2d.checkTileCollision(o, x, y)) {
			if (toMoved) {
				o.setLocation(x, y);
			}
			return true;
		}
		return false;
	}

	public boolean moveX(ActionBind o, float newX) {
		return moveX(o, newX, true);
	}

	public boolean moveX(ActionBind o, float newX, boolean toMoved) {
		if (o == null) {
			return false;
		}
		float x = offsetXPixel(o.getX()) + newX;
		float y = offsetYPixel(o.getY());
		if (!_field2d.checkTileCollision(o, x, y)) {
			if (toMoved) {
				o.setLocation(x, y);
			}
			return true;
		}
		return false;
	}

	public boolean moveY(ActionBind o, float newY) {
		return moveY(o, newY, true);
	}

	public boolean moveY(ActionBind o, float newY, boolean toMoved) {
		if (o == null) {
			return false;
		}
		float x = offsetXPixel(o.getX());
		float y = offsetYPixel(o.getY()) + newY;
		if (!_field2d.checkTileCollision(o, x, y)) {
			if (toMoved) {
				o.setLocation(x, y);
			}
			return true;
		}
		return false;
	}

	public CityMap clearAllTiles() {
		_playAnimation = false;
		_dirty = true;
		return this;
	}

	public City addCityByScreenPos(String name, float screenX, float screenY, Animation icon) {
		City city = new City(name, 0, 0, icon);
		city.setScreenPosition(screenX, screenY);
		addCityDirect(city);
		return city;
	}

	/**
	 * 屏幕坐标创建城市（无图标）
	 */
	public City addCityByScreenPos(String name, float screenX, float screenY) {
		return addCityByScreenPos(name, screenX, screenY, null);
	}

	public void moveCityToScreenPos(City city, float screenX, float screenY) {
		if (city == null) {
			return;
		}
		city.setScreenPosition(screenX, screenY);
		if (_cityCollisionEnabled) {
			resolveCityCollisions();
		}
		for (Edge e : _edges) {
			if (e.from == city || e.to == city) {
				markEdgeDirty(e);
			}
		}
	}

	public CityMap batchSetCitiesScreenPos(float screenX, float screenY, City... cities) {
		if (cities == null || cities.length == 0) {
			return this;
		}
		for (City c : cities) {
			c.setScreenPosition(screenX, screenY);
		}
		resolveCityCollisions();
		refreshAllEdgePaths();
		return this;
	}

	public CityMap batchSetCitiesScreenPosEx(City[] cities, float[] xArray, float[] yArray) {
		if (cities == null || xArray == null || yArray == null)
			return this;
		int len = MathUtils.min(MathUtils.min(cities.length, xArray.length), yArray.length);
		for (int i = 0; i < len; i++) {
			cities[i].setScreenPosition(xArray[i], yArray[i]);
		}
		resolveCityCollisions();
		refreshAllEdgePaths();
		return this;
	}

	private void addCityDirect(City c) {
		_cities.add(c);
		if (_cityCollisionEnabled) {
			resolveCityCollisions();
		}
		for (Edge e : _edges) {
			markEdgeDirty(e);
		}
	}

	private void refreshAllEdgePaths() {
		for (Edge e : _edges) {
			markEdgeDirty(e);
		}
	}

	/**
	 * 批量设置所有城
	 * 
	 * @param radius   统一半径
	 * @param priority 统一优先级
	 * @param icon     统一图标
	 */
	public CityMap batchSetAllCities(float radius, int priority, Animation icon) {
		for (City c : _cities) {
			c.setRadius(radius).setPriority(priority);
			if (icon != null) {
				c.setIcon(icon);
			}
		}
		resolveCityCollisions();
		return this;
	}

	/**
	 * 批量设置所有城市
	 * 
	 * @param radius
	 * @param priority
	 * @param icon
	 * @param lat
	 * @param lon
	 * @return
	 */
	public CityMap batchSetAllCitiesFull(float radius, int priority, Animation icon, float lat, float lon) {
		for (City c : _cities) {
			c.setRadius(radius).setPriority(priority).setLatLon(lat, lon);
			if (icon != null) {
				c.setIcon(icon);
			}
		}
		refreshAllCityPositions();
		return this;
	}

	/**
	 * 指定城市列表
	 * 
	 * @param targetCities
	 * @param radius
	 * @param priority
	 * @param icon
	 * @return
	 */
	public CityMap batchSetCities(TArray<City> targetCities, float radius, int priority, Animation icon) {
		if (targetCities == null || targetCities.isEmpty())
			return this;
		for (City c : targetCities) {
			c.setRadius(radius).setPriority(priority);
			if (icon != null) {
				c.setIcon(icon);
			}
		}
		resolveCityCollisions();
		return this;
	}

	/**
	 * 指定城市数组
	 * 
	 * @param radius
	 * @param priority
	 * @param icon
	 * @param targetCities
	 * @return
	 */
	public CityMap batchSetCities(float radius, int priority, Animation icon, City... targetCities) {
		if (targetCities == null || targetCities.length == 0) {
			return this;
		}
		for (City c : targetCities) {
			c.setRadius(radius).setPriority(priority);
			if (icon != null) {
				c.setIcon(icon);
			}
		}
		resolveCityCollisions();
		return this;
	}

	/**
	 * 批量设置连接线作用边
	 * 
	 * @param colorFrom    起点颜色
	 * @param colorTo      终点颜色
	 * @param width        线宽
	 * @param animDuration 动画时长
	 * @param type         边类型（直线/贝塞尔/A星/崎岖）
	 */
	public CityMap batchSetAllEdges(LColor colorFrom, LColor colorTo, float width, float animDuration, EdgeType type) {
		for (Edge e : _edges) {
			e.setColorBatch(colorFrom, colorTo).setWidth(width).setAnimation(animDuration).setEdgeType(type);
			markEdgeDirty(e);
		}
		return this;
	}

	public CityMap batchSetAllEdgesFull(LColor colorFrom, LColor colorTo, float width, float animDuration,
			EdgeType type, float bezierOffset) {
		for (Edge e : _edges) {
			e.setColorBatch(colorFrom, colorTo).setWidth(width).setAnimation(animDuration).setEdgeType(type)
					.setBezierOffset(bezierOffset);
			markEdgeDirty(e);
		}
		return this;
	}

	public CityMap batchSetEdges(TArray<Edge> targetEdges, LColor colorFrom, LColor colorTo, float width,
			float animDuration, EdgeType type) {
		if (targetEdges == null || targetEdges.isEmpty()) {
			return this;
		}
		for (Edge e : targetEdges) {
			e.setColorBatch(colorFrom, colorTo).setWidth(width).setAnimation(animDuration).setEdgeType(type);
			markEdgeDirty(e);
		}
		return this;
	}

	/**
	 * 指定边数组
	 * 
	 * @param colorFrom
	 * @param colorTo
	 * @param width
	 * @param animDuration
	 * @param type
	 * @param targetEdges
	 * @return
	 */
	public CityMap batchSetEdges(LColor colorFrom, LColor colorTo, float width, float animDuration, EdgeType type,
			Edge... targetEdges) {
		if (targetEdges == null || targetEdges.length == 0) {
			return this;
		}
		for (Edge e : targetEdges) {
			e.setColorBatch(colorFrom, colorTo).setWidth(width).setAnimation(animDuration).setEdgeType(type);
			markEdgeDirty(e);
		}
		return this;
	}

	public CityMap batchResetAllCitiesToDefault() {
		return batchSetAllCities(18f, 0, null);
	}

	public CityMap batchResetAllEdgesToDefault() {
		return batchSetAllEdges(_defaultEdgeColorFrom, _defaultEdgeColorTo, _defaultEdgeWidth,
				_defaultEdgeAnimationDuration, _defaultEdgeType);
	}

	/**
	 * 一键批量播放所有边线动画
	 * 
	 * @param nowTime
	 * @return
	 */
	public CityMap batchStartAllEdgesAnimation(float nowTime) {
		for (Edge e : _edges) {
			e.startAnimationBatch(nowTime);
		}
		return this;
	}

	public City addCity(String name, float lat, float lon, LTexture icon) {
		return addCity(name, lat, lon, Animation.getDefaultAnimation(icon));
	}

	public City addCity(String name, float lat, float lon, Animation icon) {
		City city = new City(name, lat, lon, icon);
		addCity(city);
		return city;
	}

	public City addCity(String name, float lat, float lon) {
		City city = new City(name, lat, lon);
		addCity(city);
		return city;
	}

	/**
	 * 自动连接两个城市
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public Edge connectCities(City from, City to) {
		if (from == null || to == null || from == to) {
			return null;
		}
		String edgeId = "edge_" + from.getId() + "_" + to.getId();
		Edge edge = new Edge(edgeId, from, to);
		applyDefaultEdgeStyle(edge);
		addEdge(edge);
		return edge;
	}

	/**
	 * 通过城市ID自动连接
	 * 
	 * @param fromId
	 * @param toId
	 * @return
	 */
	public Edge connectCitiesById(String fromId, String toId) {
		return connectCities(getCityById(fromId), getCityById(toId));
	}

	/**
	 * 链式连接城市（顺序自动连线）
	 * 
	 * @param cities
	 * @return
	 */
	public TArray<Edge> connectCitiesChain(City... cities) {
		TArray<Edge> edges = new TArray<>();
		if (cities == null || cities.length < 2) {
			return edges;
		}
		for (int i = 0; i < cities.length - 1; i++) {
			Edge edge = connectCities(cities[i], cities[i + 1]);
			if (edge != null) {
				edges.add(edge);
			}
		}
		return edges;
	}

	/**
	 * 一组城市两两互连（全连接）
	 * 
	 * @param cities
	 * @return
	 */
	public TArray<Edge> connectCitiesAll(City... cities) {
		TArray<Edge> edges = new TArray<Edge>();
		if (cities == null || cities.length < 2) {
			return edges;
		}
		for (int i = 0; i < cities.length; i++) {
			for (int j = i + 1; j < cities.length; j++) {
				Edge edge = connectCities(cities[i], cities[j]);
				if (edge != null)
					edges.add(edge);
			}
		}
		return edges;
	}

	/**
	 * 自动为边应用全局默认样式（颜色/宽度/动画）
	 * 
	 * @param edge
	 */
	private void applyDefaultEdgeStyle(Edge edge) {
		edge.colorFrom = _defaultEdgeColorFrom;
		edge.colorTo = _defaultEdgeColorTo;
		edge.width = _defaultEdgeWidth;
		edge.animationDuration = _defaultEdgeAnimationDuration;
		edge.edgeType = _defaultEdgeType;
	}

	/**
	 * 查询两个城市之间的移动路径
	 * 
	 * @param from 起点城市
	 * @param to   终点城市
	 * @return
	 */
	public TArray<Vector2f> getCityPath(City from, City to) {
		if (from == null || to == null || from == to) {
			return null;
		}
		String tempEdgeId = "temp_path_" + from.getId() + "_" + to.getId();
		Edge tempEdge = new Edge(tempEdgeId, from, to);
		applyDefaultEdgeStyle(tempEdge);
		TArray<Vector2f> path = computeEdgePath(tempEdge);
		return (path == null || path.size < 2) ? null : path;
	}

	/**
	 * 通过城市ID查询移动路径
	 * 
	 * @param fromId
	 * @param toId
	 * @return
	 */
	public TArray<Vector2f> getCityPathById(String fromId, String toId) {
		return getCityPath(getCityById(fromId), getCityById(toId));
	}

	/**
	 * 查询两个城市之间的坐标直线距离
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public float getCityDistance(City from, City to) {
		if (from == null || to == null) {
			return -1;
		}
		return from.screenPos.dst(to.screenPos);
	}

	/**
	 * 查询两个城市之间实际路径总长度
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public float getCityPathLength(City from, City to) {
		TArray<Vector2f> path = getCityPath(from, to);
		if (path == null) {
			return -1;
		}
		float totalLength = 0;
		for (int i = 0; i < path.size - 1; i++) {
			totalLength += path.get(i).dst(path.get(i + 1));
		}
		return totalLength;
	}

	/**
	 * 判断两个城市是否有直接连线(即城市是否可通行)
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public boolean hasDirectEdge(City from, City to) {
		return getEdgeBetweenCities(from, to) != null;
	}

	/**
	 * 获取两个城市之间的直接边线对象
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public Edge getEdgeBetweenCities(City from, City to) {
		if (from == null || to == null) {
			return null;
		}
		for (Edge e : _edges) {
			if ((e.from == from && e.to == to) || (e.from == to && e.to == from)) {
				return e;
			}
		}
		return null;
	}

	/**
	 * 获取指定城市的所有相邻城市
	 * 
	 * @param city
	 * @return
	 */
	public TArray<City> getAdjacentCities(City city) {
		TArray<City> adjacent = new TArray<City>();
		if (city == null) {
			return adjacent;
		}
		for (Edge e : _edges) {
			if (e.from == city) {
				adjacent.add(e.to);
			}
			if (e.to == city) {
				adjacent.add(e.from);
			}
		}
		return adjacent;
	}

	/**
	 * 取指定城市的所有连接边线
	 * 
	 * @param city
	 * @return
	 */
	public TArray<Edge> getEdgesByCity(City city) {
		TArray<Edge> cityEdges = new TArray<>();
		if (city == null) {
			return cityEdges;
		}
		for (Edge e : _edges) {
			if (e.from == city || e.to == city) {
				cityEdges.add(e);
			}
		}
		return cityEdges;
	}

	/**
	 * 两个城市之间是否可通行
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public boolean isPathAvailable(City from, City to) {
		return getCityPath(from, to) != null;
	}

	/**
	 * 设置选中城市
	 * 
	 * @param city
	 */
	public void selectCity(City city) {
		this._selectedCity = city;
	}

	/**
	 * 取消所有城市选中
	 */
	public void unselectAllCities() {
		this._selectedCity = null;
	}

	/**
	 * 获取当前选中城市
	 */
	public City getSelectedCity() {
		return _selectedCity;
	}

	/**
	 * 高光指定城市间边线作为行军效果
	 * 
	 * @param from
	 * @param to
	 */
	public void playMarchAnimation(City from, City to) {
		Edge edge = getEdgeBetweenCities(from, to);
		if (edge != null) {
			edge.colorFrom = MARCH_COLOR.lighter();
			edge.colorTo = MARCH_COLOR.darker();
			markEdgeDirty(edge);
		}
	}

	/**
	 * 高光指定城市间边线作为物资运送效果
	 * 
	 * @param from
	 * @param to
	 */
	public void playTransportAnimation(City from, City to) {
		Edge edge = getEdgeBetweenCities(from, to);
		if (edge != null) {
			edge.colorFrom = TRANSPORT_COLOR;
			edge.colorTo = TRANSPORT_COLOR.darker();
			markEdgeDirty(edge);
		}
	}

	/**
	 * 高光指定城市间边线作为玩家移动效果
	 * 
	 * @param from
	 * @param to
	 */
	public void playPlayerMoveAnimation(City from, City to) {
		Edge edge = getEdgeBetweenCities(from, to);
		if (edge != null) {
			edge.colorFrom = PLAYER_MOVE_COLOR;
			edge.colorTo = PLAYER_MOVE_COLOR.darker();
			markEdgeDirty(edge);
		}
	}

	/**
	 * 动态设置单条边的崎岖双向偏移间距(仅崎岖模式有效)
	 * 
	 * @param edge
	 * @param offset
	 * @return
	 */
	public CityMap setEdgeRoughSideOffset(Edge edge, float offset) {
		if (edge == null) {
			return this;
		}
		edge.setRoughSideOffset(offset);
		markEdgeDirty(edge);
		return this;
	}

	/**
	 * 批量设置多条边的崎岖偏移间距(仅崎岖模式有效)
	 * 
	 * @param offset
	 * @param edges
	 * @return
	 */
	public CityMap setEdgesRoughSideOffset(float offset, Edge... edges) {
		if (edges == null || edges.length == 0) {
			return this;
		}
		for (Edge e : edges) {
			setEdgeRoughSideOffset(e, offset);
		}
		return this;
	}

	public TArray<Edge> connectHub(City hub, City... targets) {
		TArray<Edge> edges = new TArray<Edge>();
		if (hub == null || targets == null || targets.length == 0) {
			return edges;
		}
		for (City target : targets) {
			Edge edge = connectCities(hub, target);
			if (edge != null) {
				edges.add(edge);
			}
		}
		return edges;
	}

	/**
	 * 中心辐射链接
	 * 
	 * @param hubId
	 * @param targetIds
	 * @return
	 */
	public TArray<Edge> connectHubById(String hubId, String... targetIds) {
		City hub = getCityById(hubId);
		TArray<City> targets = new TArray<City>();
		for (String id : targetIds) {
			City c = getCityById(id);
			if (c != null) {
				targets.add(c);
			}
		}
		return connectHub(hub, targets.toArray(new City[0]));
	}

	/**
	 * 批量自定义链接对，两两成对。可以重复输入同一对象，但注意，非成对的数据注入无效。 用户可借此简化城市间两条相互链接线的逻辑。
	 * (例如洛阳，长安，长安，洛阳这样设置，就会直接出现两组线，而不用手动定义两条线)
	 * 
	 * @param cityPairs
	 * @return
	 */
	public TArray<Edge> connectPairs(City... cityPairs) {
		TArray<Edge> edges = new TArray<Edge>();
		if (cityPairs == null || cityPairs.length % 2 != 0) {
			return edges;
		}
		for (int i = 0; i < cityPairs.length; i += 2) {
			Edge edge = connectCities(cityPairs[i], cityPairs[i + 1]);
			if (edge != null) {
				edges.add(edge);
			}
		}
		return edges;
	}

	/**
	 * 环形连接一组城市
	 * 
	 * @param cities
	 * @return
	 */
	public TArray<Edge> connectRing(City... cities) {
		TArray<Edge> edges = connectCitiesChain(cities);
		if (cities != null && cities.length >= 2) {
			Edge edge = connectCities(cities[cities.length - 1], cities[0]);
			if (edge != null) {
				edges.add(edge);
			}
		}
		return edges;
	}

	/**
	 * 断开两个城市的直接连接
	 * 
	 * @param from
	 * @param to
	 */
	public void disconnectCities(City from, City to) {
		Edge edge = getEdgeBetweenCities(from, to);
		if (edge != null) {
			removeEdge(edge);
		}
	}

	/**
	 * 断开指定城市的所有链接线
	 * 
	 * @param city
	 */
	public void disconnectAllFromCity(City city) {
		if (city == null) {
			return;
		}
		TArray<Edge> toRemove = getEdgesByCity(city);
		for (Edge e : toRemove) {
			removeEdge(e);
		}
	}

	/**
	 * 指定某城市只能链接特定城市
	 * 
	 * @param target
	 * @param hub
	 * @return
	 */
	public Edge connectOnlyToHub(City target, City hub) {
		if (target == null || hub == null) {
			return null;
		}
		disconnectAllFromCity(target);
		return connectCities(target, hub);
	}

	/**
	 * 以id名称建立两个城市之间的单独链接
	 * 
	 * @param targetId
	 * @param hubId
	 * @return
	 */
	public Edge connectOnlyToHubById(String targetId, String hubId) {
		return connectOnlyToHub(getCityById(targetId), getCityById(hubId));
	}

	/**
	 * 清空所有路径动画
	 */
	public void clearAllAnimations() {
		batchResetAllEdgesToDefault();
	}

	/**
	 * 批量设置指定城市染色
	 * 
	 * @param cities
	 * @param campColor
	 */
	public void batchSetCityCamp(TArray<City> cities, LColor campColor) {
		for (City c : cities) {
			if (c != null) {
				c.color.setColor(campColor);
			}
		}
	}

	/**
	 * 批量设置城市可到达状态（不可到达灰色）
	 * 
	 * @param cities
	 * @param reachable
	 */
	public void batchSetCityReachable(TArray<City> cities, boolean reachable) {
		for (City c : cities) {
			if (c == null) {
				continue;
			}
			if (!reachable) {
				c.color.setColor(LColor.gray);
			}
		}
	}

	public void focusCameraOnCity(City city) {
		if (city == null) {
			return;
		}
		setOffset(getContainerWidth() / 2 - city.screenPos.x, getContainerHeight() / 2 - city.screenPos.y);
	}

	/**
	 * 动态设置单条边线的连线类型
	 * 
	 * @param edge
	 * @param type
	 * @return
	 */
	public CityMap setEdgeType(Edge edge, EdgeType type) {
		if (edge == null || type == null) {
			return this;
		}
		edge.setEdgeType(type);
		markEdgeDirty(edge);
		return this;
	}

	/**
	 * 批量设置多条边线的连线类型
	 * 
	 * @param type
	 * @param edges
	 * @return
	 */
	public CityMap setEdgesType(EdgeType type, Edge... edges) {
		if (edges == null || edges.length == 0 || type == null) {
			return this;
		}
		for (Edge e : edges) {
			setEdgeType(e, type);
		}
		return this;
	}

	/**
	 * 设置全局所有边线的类型
	 * 
	 * @param type
	 * @return
	 */
	public CityMap setAllEdgesType(EdgeType type) {
		if (type == null) {
			return this;
		}
		for (Edge e : this._edges) {
			setEdgeType(e, type);
		}
		return this;
	}

	/**
	 * 动态设置两个城市之间的边线类型
	 * 
	 * @param from
	 * @param to
	 * @param type
	 * @return
	 */
	public CityMap setEdgeTypeBetweenCities(City from, City to, EdgeType type) {
		Edge edge = getEdgeBetweenCities(from, to);
		return setEdgeType(edge, type);
	}

	/**
	 * 动态设置两个ID下城市之间的边线类型
	 * 
	 * @param fromId
	 * @param toId
	 * @param type
	 * @return
	 */
	public CityMap setEdgeTypeBetweenIds(String fromId, String toId, EdgeType type) {
		return setEdgeTypeBetweenCities(getCityById(fromId), getCityById(toId), type);
	}

	/**
	 * 设置指定城市所有关联边线的类型
	 * 
	 * @param city
	 * @param type
	 * @return
	 */
	public CityMap setAllEdgesForCity(City city, EdgeType type) {
		if (city == null) {
			return this;
		}
		TArray<Edge> cityEdges = getEdgesByCity(city);
		return setEdgesType(type, cityEdges.toArray(new Edge[0]));
	}

	/**
	 * 动态设置单条边的贝塞尔偏移比例
	 * 
	 * @param edge
	 * @param ratio
	 * @return
	 */
	public CityMap setEdgeBezierOffset(Edge edge, float ratio) {
		if (edge == null) {
			return this;
		}
		edge.setBezierOffset(MathUtils.clamp(ratio, 0f, 1f));
		markEdgeDirty(edge);
		return this;
	}

	/**
	 * 批量设置边的贝塞尔偏移
	 * 
	 * @param ratio
	 * @param edges
	 * @return
	 */
	public CityMap setEdgesBezierOffset(float ratio, Edge... edges) {
		if (edges == null || edges.length == 0)
			return this;
		for (Edge e : edges) {
			setEdgeBezierOffset(e, ratio);
		}
		return this;
	}

	/**
	 * 设置两个城市之间边的贝塞尔偏移
	 * 
	 * @param from
	 * @param to
	 * @param ratio
	 * @return
	 */
	public CityMap setEdgeBezierOffsetBetweenCities(City from, City to, float ratio) {
		Edge edge = getEdgeBetweenCities(from, to);
		return setEdgeBezierOffset(edge, ratio);
	}

	/**
	 * 修改指定城市半径大小
	 * 
	 * @param city
	 * @param radius
	 * @return
	 */
	public CityMap setCityRadius(City city, float radius) {
		if (city != null) {
			city.setRadius(radius);
			resolveCityCollisions();
			refreshAllEdgePaths();
		}
		return this;
	}

	/**
	 * 修改指定城市大小
	 * 
	 * @param city
	 * @param w
	 * @param h
	 * @return
	 */
	public CityMap setCitySize(City city, float w, float h) {
		if (city != null) {
			city.setRadius(MathUtils.max(w, h) / 2);
			city.setScreenSize(w, h);
			resolveCityCollisions();
			refreshAllEdgePaths();
		}
		return this;
	}

	/**
	 * 修改范围中所有城市半径大小
	 * 
	 * @param radius
	 * @param cities
	 * @return
	 */
	public CityMap setCitiesRadius(float radius, City... cities) {
		if (cities == null || cities.length == 0) {
			return this;
		}
		float clamped = radius;
		for (City c : cities) {
			if (c != null) {
				c.setRadius(clamped);
			}
		}
		resolveCityCollisions();
		refreshAllEdgePaths();
		return this;
	}

	/**
	 * 修改范围中所有城市大小
	 * 
	 * @param w
	 * @param h
	 * @param cities
	 * @return
	 */
	public CityMap setCitiesSize(float w, float h, City... cities) {
		if (cities == null || cities.length == 0) {
			return this;
		}
		for (City c : cities) {
			if (c != null) {
				c.setRadius(MathUtils.max(w, h) / 2);
				c.setScreenSize(w, h);
			}
		}
		resolveCityCollisions();
		refreshAllEdgePaths();
		return this;
	}

	/**
	 * 修改所有城市半径大小
	 * 
	 * @param radius
	 * @return
	 */
	public CityMap setCitiesRadius(float radius) {
		for (City c : _cities) {
			if (c != null) {
				c.setRadius(radius);
			}
		}
		resolveCityCollisions();
		refreshAllEdgePaths();
		return this;
	}

	/**
	 * 修改所有城市大小
	 * 
	 * @param w
	 * @param h
	 * @return
	 */
	public CityMap setCitiesSize(float w, float h) {
		for (City c : _cities) {
			if (c != null) {
				c.setRadius(MathUtils.max(w, h) / 2);
				c.setScreenSize(w, h);
			}
		}
		resolveCityCollisions();
		refreshAllEdgePaths();
		return this;
	}

	public int getMaxEdgeProcessPerFrame() {
		return _maxEdgeProcessPerFrame;
	}

	public CityMap setMaxEdgeProcessPerFrame(int max) {
		this._maxEdgeProcessPerFrame = MathUtils.clamp(max, 4, 30);
		return this;
	}

	public CityMap setAllCitiesRadius(float radius) {
		return setCitiesRadius(radius, _cities.toArray(new City[0]));
	}

	public CityMap setCityRadiusById(String cityId, float radius) {
		return setCityRadius(getCityById(cityId), radius);
	}

	/**
	 * 设置单个城市优先级
	 * 
	 * @param city
	 * @param priority
	 * @return
	 */
	public CityMap setCityPriority(City city, int priority) {
		if (city != null) {
			city.setPriority(priority);
			resolveCityCollisions();
		}
		return this;
	}

	/**
	 * 批量设置城市优先级
	 * 
	 * @param priority
	 * @param cities
	 * @return
	 */
	public CityMap setCitiesPriority(int priority, City... cities) {
		if (cities == null) {
			return this;
		}
		for (City c : cities) {
			if (c != null) {
				c.setPriority(priority);
			}
		}
		resolveCityCollisions();
		return this;
	}

	/**
	 * 统一播放指定边线集合的动画
	 * 
	 * @param now
	 * @param edges
	 * @return
	 */
	public CityMap startEdgesAnimation(float now, Edge... edges) {
		if (edges == null) {
			return this;
		}
		for (Edge e : edges) {
			if (e != null) {
				e.startAnimationBatch(now);
			}
		}
		return this;
	}

	/**
	 * 暂停所有边线动画
	 */
	public CityMap stopAllEdgesAnimation() {
		setEdgeAnimationEnabled(false);
		return this;
	}

	/**
	 * 重置所有边线为默认样式并重启动画
	 * 
	 * @param now
	 * @return
	 */
	public CityMap resetAndRestartEdges(float now) {
		batchResetAllEdgesToDefault();
		setEdgeAnimationEnabled(true);
		batchStartAllEdgesAnimation(now);
		return this;
	}

	/**
	 * 显示或隐藏指定城市
	 * 
	 * @param visible
	 * @param cities
	 * @return
	 */
	public CityMap setCitiesVisible(boolean visible, TArray<City> cities) {
		if (cities == null) {
			return this;
		}
		for (City c : cities) {
			if (c != null) {
				c.setVisible(visible);
			}
		}
		return this;
	}

	/**
	 * 显示或隐藏所有城市
	 * 
	 * @param visible
	 * @return
	 */
	public CityMap setAllCitiesVisible(boolean visible) {
		return setCitiesVisible(visible, _cities);
	}

	public int getCityEgdeMaxIter() {
		return _cityEgdeMaxIter;
	}

	public void setCityEgdeMaxIter(int c) {
		this._cityEgdeMaxIter = c;
	}

	public float getCityEgdePadding() {
		return _cityEgdePadding;
	}

	public void setCityEgdePadding(float c) {
		this._cityEgdePadding = c;
	}

	public float getCityScale() {
		return _cityMapScale;
	}

	public void setCityScale(float c) {
		this._cityMapScale = c;
	}

	public int getGridCellSizeX() {
		return _gridCellSizeX;
	}

	public CityMap setGridCellSizeX(int size) {
		this._gridCellSizeX = size;
		refreshAllEdgePaths();
		return this;
	}

	public int getGridCellSizeY() {
		return _gridCellSizeY;
	}

	public CityMap setGridCellSizeY(int size) {
		this._gridCellSizeY = size;
		refreshAllEdgePaths();
		return this;
	}

	public int getMaxAstarNodes() {
		return _maxAstarNodes;
	}

	public CityMap setMaxAstarNodes(int max) {
		this._maxAstarNodes = MathUtils.clamp(max, 1000, 100000);
		return this;
	}

	public float getAstarSearchPadding() {
		return _astarSearchPadding;
	}

	public CityMap setAstarSearchPadding(float padding) {
		this._astarSearchPadding = MathUtils.clamp(padding, 0, 1000);
		refreshAllEdgePaths();
		return this;
	}

	public boolean isEnableCatmullRom() {
		return _enableCatmullRom;
	}

	public CityMap setEnableCatmullRom(boolean enable) {
		this._enableCatmullRom = enable;
		refreshAllEdgePaths();
		return this;
	}

	public float getCatmullSamplesPerSegment() {
		return _catmullSamplesPerSegment;
	}

	public CityMap setCatmullSamplesPerSegment(float samples) {
		this._catmullSamplesPerSegment = MathUtils.clamp(samples, 1, 30);
		refreshAllEdgePaths();
		return this;
	}

	public boolean isFitBezierSegments() {
		return _fitBezierSegments;
	}

	public CityMap setFitBezierSegments(boolean fit) {
		this._fitBezierSegments = fit;
		refreshAllEdgePaths();
		return this;
	}

	public float getStringPullInflation() {
		return _stringPullInflation;
	}

	public CityMap setStringPullInflation(float inflation) {
		this._stringPullInflation = MathUtils.clamp(inflation, 0, 50);
		refreshAllEdgePaths();
		return this;
	}

	public boolean isUseOrthogonalPos() {
		return _useOrthogonalPos;
	}

	public CityMap setUseOrthogonalPos(boolean use) {
		this._useOrthogonalPos = use;
		refreshAllCityPositions();
		return this;
	}

	public float getDefaultEdgeWidth() {
		return _defaultEdgeWidth;
	}

	public CityMap setDefaultEdgeWidth(float width) {
		this._defaultEdgeWidth = MathUtils.clamp(width, 0.5f, 20f);
		batchResetAllEdgesToDefault();
		return this;
	}

	public float getDefaultEdgeAnimationDuration() {
		return _defaultEdgeAnimationDuration;
	}

	public CityMap setDefaultEdgeAnimationDuration(float duration) {
		this._defaultEdgeAnimationDuration = MathUtils.clamp(duration, 0, 10f);
		batchResetAllEdgesToDefault();
		return this;
	}

	public void clearAllCity() {
		clearCities();
		clearEdges();
	}

	@Override
	public ActionTween selfAction() {
		return PlayerUtils.set(this);
	}

	@Override
	public boolean isActionCompleted() {
		return PlayerUtils.isActionCompleted(this);
	}

	public Sprites getMapSprites() {
		return _mapSprites;
	}

	public CityMap setMapSprites(Sprites s) {
		_mapSprites = s;
		return this;
	}

	@Override
	public ISprite setSprites(Sprites ss) {
		if (this._screenSprites == ss) {
			return this;
		}
		this._screenSprites = ss;
		return this;
	}

	@Override
	public Sprites getSprites() {
		return this._screenSprites;
	}

	@Override
	public Screen getScreen() {
		if (this._screenSprites == null) {
			return LSystem.getProcess().getScreen();
		}
		return this._screenSprites.getScreen() == null ? LSystem.getProcess().getScreen()
				: this._screenSprites.getScreen();
	}

	public float getScreenX() {
		float x = 0;
		ISprite parent = _objectSuper;
		if (parent != null) {
			x += parent.getX();
			for (; (parent = parent.getParent()) != null;) {
				x += parent.getX();
			}
		}
		return x + getX();
	}

	public float getScreenY() {
		float y = 0;
		ISprite parent = _objectSuper;
		if (parent != null) {
			y += parent.getY();
			for (; (parent = parent.getParent()) != null;) {
				y += parent.getY();
			}
		}
		return y + getY();
	}

	@Override
	public float getContainerX() {
		if (_objectSuper != null) {
			return getScreenX() - getX();
		}
		return this._screenSprites == null ? super.getContainerX() : this._screenSprites.getX();
	}

	@Override
	public float getContainerY() {
		if (_objectSuper != null) {
			return getScreenY() - getY();
		}
		return this._screenSprites == null ? super.getContainerY() : this._screenSprites.getY();
	}

	@Override
	public float getContainerWidth() {
		return this._screenSprites == null ? super.getContainerWidth() : this._screenSprites.getWidth();
	}

	@Override
	public float getContainerHeight() {
		return this._screenSprites == null ? super.getContainerHeight() : this._screenSprites.getHeight();
	}

	@Override
	public float getFixedWidthOffset() {
		return _fixedWidthOffset;
	}

	@Override
	public ISprite setFixedWidthOffset(float fixedWidthOffset) {
		this._fixedWidthOffset = fixedWidthOffset;
		return this;
	}

	@Override
	public float getFixedHeightOffset() {
		return _fixedHeightOffset;
	}

	@Override
	public ISprite setFixedHeightOffset(float fixedHeightOffset) {
		this._fixedHeightOffset = fixedHeightOffset;
		return this;
	}

	@Override
	public boolean showShadow() {
		return false;
	}

	@Override
	public boolean collides(ISprite e) {
		if (e == null || !e.isVisible()) {
			return false;
		}
		return getRectBox().intersects(e.getCollisionBox());
	}

	public boolean collidesX(ISprite other, int epsilon) {
		if (other == null || !other.isVisible()) {
			return false;
		}
		RectBox rectSelf = getRectBox();
		float selfLeft = rectSelf.getX();
		float selfRight = selfLeft + MathUtils.max(1, rectSelf.getWidth());
		RectBox rectOther = other.getRectBox();
		float otherLeft = rectOther.getX();
		float otherRight = otherLeft + MathUtils.max(1, rectOther.getWidth());
		return selfRight + epsilon >= otherLeft && otherRight + epsilon >= selfLeft;
	}

	public boolean collidesY(ISprite other, int epsilon) {
		if (other == null || !other.isVisible()) {
			return false;
		}
		RectBox rectSelf = getRectBox();
		float selfTop = rectSelf.getY();
		float selfBottom = selfTop + MathUtils.max(1, rectSelf.getHeight());
		RectBox rectOther = other.getRectBox();
		float otherTop = rectOther.getY();
		float otherBottom = otherTop + MathUtils.max(1, rectOther.getHeight());
		return selfBottom + epsilon >= otherTop && otherBottom + epsilon >= selfTop;
	}

	@Override
	public boolean collidesX(ISprite other) {
		return collidesX(other, 1);
	}

	@Override
	public boolean collidesY(ISprite other) {
		return collidesY(other, 1);
	}

	@Override
	public CityMap triggerCollision(SpriteCollisionListener sc) {
		this._collSpriteListener = sc;
		return this;
	}

	@Override
	public void onCollision(ISprite coll, int dir) {
		if (_collSpriteListener != null) {
			_collSpriteListener.onCollideUpdate(coll, dir);
		}
	}

	@Override
	public void onResize() {
		if (_resizeListener != null) {
			_resizeListener.onResize(this);
		}
		if (_mapSprites != null) {
			_mapSprites.resize(getWidth(), getHeight(), false);
		}
	}

	public ResizeListener<CityMap> getResizeListener() {
		return _resizeListener;
	}

	public CityMap setResizeListener(ResizeListener<CityMap> listener) {
		this._resizeListener = listener;
		return this;
	}

	public float getBackgroundOffsetX() {
		return _backgroundOffset.x;
	}

	public float getBackgroundOffsetY() {
		return _backgroundOffset.y;
	}

	public CityMap setBackgroundOffset(XY pos) {
		_backgroundOffset.set(pos);
		return this;
	}

	public CityMap setBackgroundOffset(float x, float y) {
		_backgroundOffset.set(x, y);
		return this;
	}

	public float getBackgroundSizeX() {
		return _backgroundSize.x;
	}

	public float getBackgroundSizeY() {
		return _backgroundSize.y;
	}

	public CityMap setBackgroundSize(XY pos) {
		_backgroundSize.set(pos);
		return this;
	}

	public CityMap setBackgroundSize(float x, float y) {
		_backgroundSize.set(x, y);
		return this;
	}

	public CityMap setOffsetX(float sx) {
		this._offset.setX(sx);
		return this;
	}

	public CityMap setOffsetY(float sy) {
		this._offset.setY(sy);
		return this;
	}

	@Override
	public float getOffsetX() {
		return _offset.x;
	}

	@Override
	public float getOffsetY() {
		return _offset.y;
	}

	@Override
	public float left() {
		return getX();
	}

	@Override
	public float top() {
		return getY();
	}

	@Override
	public float right() {
		return getWidth();
	}

	@Override
	public float bottom() {
		return getHeight();
	}

	@Override
	public boolean autoXYSort() {
		return false;
	}

	@Override
	public ISprite buildToScreen() {
		if (_mapSprites != null) {
			_mapSprites.add(this);
			return this;
		}
		getScreen().add(this);
		return this;
	}

	@Override
	public ISprite removeFromScreen() {
		if (_mapSprites != null) {
			_mapSprites.remove(this);
			return this;
		}
		getScreen().remove(this);
		return this;
	}

	@Override
	public ISprite resetAnchor() {
		return this;
	}

	@Override
	public ISprite setAnchor(float sx, float sy) {
		return this;
	}

	@Override
	public String toString() {
		return _field2d.toString();
	}

	@Override
	protected void _onDestroy() {
		_visible = false;
		_playAnimation = false;
		_roll = false;
		if (_mapSprites != null) {
			_mapSprites.close();
			_mapSprites = null;
		}
		if (_background != null) {
			_background.close();
			_background = null;
		}
		for (int i = _cities.size - 1; i > -1; i--) {
			City c = _cities.get(i);
			if (c != null) {
				c.close();
			}
		}
		clearCities();
		_resizeListener = null;
		_collSpriteListener = null;
		removeActionEvents(this);
	}
}
