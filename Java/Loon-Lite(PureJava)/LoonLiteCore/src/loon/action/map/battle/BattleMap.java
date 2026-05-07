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

import loon.LObject;
import loon.LSystem;
import loon.LTexture;
import loon.LTextures;
import loon.PlayerUtils;
import loon.Screen;
import loon.Stage;
import loon.action.ActionBind;
import loon.action.ActionTween;
import loon.action.collision.CollisionHelper;
import loon.action.map.Direction;
import loon.action.map.Field2D;
import loon.action.map.TileMapCollision;
import loon.action.map.Field2D.MapSwitchMaker;
import loon.action.map.TileIsoHighlighter;
import loon.action.map.TileIsoHighlighter.EffectType;
import loon.action.map.battle.BattleMovementManager.MovementListener;
import loon.action.map.battle.BattlePathFinder.PathResult;
import loon.action.map.battle.BattleTile.EffectService;
import loon.action.map.battle.BattleTile.SkillService;
import loon.action.map.battle.BattleTileMake.TileAnimation;
import loon.action.map.battle.BattleType.RangeType;
import loon.action.map.items.RoleEquip;
import loon.action.map.items.Team;
import loon.action.sprite.AnimationManager;
import loon.action.sprite.AnimationRenderer;
import loon.action.sprite.ISprite;
import loon.action.sprite.MoveControl;
import loon.action.sprite.SpriteCollisionListener;
import loon.action.sprite.Sprites;
import loon.canvas.LColor;
import loon.events.DrawListener;
import loon.events.GameEventBus;
import loon.events.ResizeListener;
import loon.geom.Affine2f;
import loon.geom.PointF;
import loon.geom.PointI;
import loon.geom.RectBox;
import loon.geom.Sized;
import loon.geom.Vector2f;
import loon.geom.XY;
import loon.opengl.GLEx;
import loon.utils.BoolArray;
import loon.utils.ISOUtils;
import loon.utils.IntArray;
import loon.utils.IntMap;
import loon.utils.ISOUtils.IsoConfig;
import loon.utils.ISOUtils.IsoResult;
import loon.utils.timer.Duration;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.StringUtils;
import loon.utils.TArray;

/**
 * 斜视地图，专属战斗用地图，和普通地图最大区别是，此地图全部由高度可控动画对象构建(也就是复杂特效可以具体到单个瓦片)，内置完整角色与事件管理系统
 */
public class BattleMap extends LObject<ISprite> implements TileMapCollision, Sized, ISprite {

	// 允许跨层寻径的最大高度差（设定上最多允许跨1层移动，也就是允许A-B不能A-C，必须按顺序，但若改变默认值，则可以跳着走）
	private static final int MAX_ALLOWED_HEIGHT_DIFF = 1;

	// BattleMap地图布局裁剪专用，用于设置产生BattleTile[][]时的地图样式，裁剪为只显示需要的区域，裁剪的粗细可设置
	public enum MapLayoutStyle {
		// 完整地图不裁剪
		FULL,
		// 以下为只保留特定区域的方法
		// 仅保留外围N圈边框
		OUTER_BORDER,
		// 仅保留内部N圈边框
		INNER_BORDER,
		// 仅保留中心圆形
		CENTER_CIRCLE,
		// 仅保留中心菱形
		CENTER_RHOMBUS,
		// 仅保留中心十字
		CENTER_CROSS,
		// 仅保留中心横一字
		CENTER_H_LINE,
		// 仅保留中心竖一字
		CENTER_V_LINE,
		// 仅保留中心X形
		CENTER_X,
		// 仅保留向上三角形
		CENTER_TRIANGLE_UP,
		// 仅保留向下三角形
		CENTER_TRIANGLE_DOWN,
		// 仅保留向左三角形
		CENTER_TRIANGLE_LEFT,
		// 仅保留向右三角形
		CENTER_TRIANGLE_RIGHT,
		// 此下方法和上面的正相反，上面是只保留，下面是只裁剪
		// 只裁剪外围N行矩形边
		CUT_OUTER_EDGE,
		// 只裁剪外围N行菱形边
		CUT_OUTER_RHOMBUS,
		// 只裁剪外围四边斜角
		CUT_OUTER_SLOPE_4,
		// 只裁剪外围八角形
		CUT_OUTER_OCTAGON,
		// 只裁剪外围圆角矩形
		CUT_OUTER_ROUND_RECT,
		// 只裁剪外围对角斜切四角
		CUT_OUTER_DIAGONAL_CORNER,
		// 仅外围左右斜边裁切
		CUT_OUTER_SIDE_SLOPE_LR,
		// 仅外围上下斜边裁切
		CUT_OUTER_SIDE_SLOPE_TB,
		// 仅外围六边形裁切
		CUT_OUTER_HEX,
		// 仅外围外围星型切边
		CUT_OUTER_STAR_EDGE,
		// 仅外围波浪边裁切
		CUT_OUTER_WAVE_EDGE,
		// 仅外围阶梯锯齿边裁切
		CUT_OUTER_STEP_EDGE,
		// 仅外围左侧单边斜切
		CUT_OUTER_LEFT_SLOPE,
		// 仅外围右侧单边斜切
		CUT_OUTER_RIGHT_SLOPE,
		// 仅外围顶部单边斜切
		CUT_OUTER_TOP_SLOPE,
		// 仅外围底部单边斜切
		CUT_OUTER_BOTTOM_SLOPE
	}

	// 层排序
	private final Comparator<IsoTileLayer> LAYER_SORTER = new Comparator<IsoTileLayer>() {
		@Override
		public int compare(IsoTileLayer l1, IsoTileLayer l2) {
			return MathUtils.compare(_layers.indexOf(l1), _layers.indexOf(l2));
		}
	};

	// 跨层寻径时记录路径点所在层级
	public static class PathNodeWithLayer extends PointI {

		public int layer;

		public PathNodeWithLayer(int x, int y, int layer) {
			super(x, y);
			this.layer = layer;
		}
	}

	// 层状态变更监听
	public interface LayerChangeListener {

		void onLayerOffsetChanged(int layerIndex, float offsetX, float offsetY);

		void onLayerVisibilityChanged(int layerIndex, boolean visible);

		void onLayerAdded(int layerIndex);

		void onLayerRemoved(int layerIndex);

		void onLayerSorted();
	}

	// 数组地图分层用类
	public static class IsoTileLayer {

		public boolean visible = true;
		public final BattleTile[][] tiles;
		public final int width;
		public final int height;
		public String name;
		public float offsetX;
		public float offsetY;
		public int layerIndex;

		public IsoTileLayer(BattleTile[][] tiles, String name, int layerIndex) {
			this.tiles = tiles;
			this.width = tiles == null ? 0 : tiles.length;
			this.height = (tiles == null || tiles.length == 0) ? 0 : tiles[0].length;
			this.name = name == null ? "battlelayer" : name;
			this.offsetX = 0f;
			this.offsetY = 0f;
			this.layerIndex = layerIndex;
		}
	}

	private final static class ObjectComparator implements Comparator<BattleMapObject> {
		@Override
		public int compare(BattleMapObject o1, BattleMapObject o2) {
			int layerCompare = MathUtils.compare(MathUtils.abs(o1.getLayer()), MathUtils.abs(o2.getLayer()));
			if (layerCompare != 0) {
				return layerCompare;
			}
			return MathUtils.compare(o1.renderPriority, o2.renderPriority);
		}
	}

	public final static BattleTile[][] reversalXandY(final BattleTile[][] array) {
		int col = array[0].length;
		int row = array.length;
		BattleTile[][] result = new BattleTile[col][row];
		for (int y = 0; y < col; y++) {
			for (int x = 0; x < row; x++) {
				result[y][x] = array[x][y];
			}
		}
		return result;
	}

	private final static ObjectComparator OBJ_COMPARATOR = new ObjectComparator();

	public final static float CAMERA_SMOOTH_FACTOR = 0.1f;

	protected final TArray<BattleMapObject> _mapObjects = new TArray<BattleMapObject>();
	// 斜视地图的分层管理器
	protected final TArray<IsoTileLayer> _layers = new TArray<IsoTileLayer>();

	private final TArray<PointI> _strategicPoint = new TArray<PointI>();

	private final PointF _baseComposite = new PointF(0f, 0f);

	private final float[] _tempPx = new float[4];
	private final float[] _tempPy = new float[4];

	private final TileIsoHighlighter _highlighter = new TileIsoHighlighter();
	private final BattleSkill _defaultGlobalSkill = new BattleSkill(0, "map");

	private final LColor _tileColor = new LColor();
	private final PointF _layerOffet = new PointF();
	private final PointF _scrollDrag = new PointF();
	private final Field2D _field2d;
	private final Vector2f _gxTempResult = new Vector2f();
	private final IsoResult _isoTempResult = new IsoResult();
	private final Vector2f _backgroundOffset = new Vector2f();
	private final Vector2f _backgroundSize = new Vector2f();

	// 自动生成地图时专用的地图布局剪裁遮罩系统，菱形，三角形，一字，十字什么的奇怪地行都可以生成
	private MapLayoutStyle _currentLayout = MapLayoutStyle.FULL;
	// 自动生成地图时使用的形状粗细设定(例如一字布局时，为1时占1行，为2占两行，以此类推)
	private int _layoutThickness = 1;

	// 默认的瓦片类型与索引id绑定用类，用于二维数组到地图的生成
	private final IntMap<BattleTileType> _tileIdToTypeMap = new IntMap<BattleTileType>();
	// 默认的坐标位置与瓦片绑定关系，用于二维数组到地图的生成
	private final IntMap<BattleTile> _tilePosToTiles = new IntMap<BattleTile>();
	// 寻路时允许在相邻层之间“上下楼”（楼梯/斜坡所在瓦片）
	private final TArray<PointI> _stairTiles = new TArray<PointI>();

	private final IsoConfig _isoConfig;

	// 地图自身存储子精灵的的Sprites
	private final Sprites _mapSprites;
	// 层级排序脏标记
	private boolean _layerSortDirty;
	private boolean _playAnimation;
	private ActionBind _follow;
	private boolean _visible;

	private float cameraMoveSpeed = 1f;

	private Vector2f _followOffset = new Vector2f();
	private Vector2f _pixelOffset = new Vector2f();
	private GameEventBus<Object> _eventBus;
	private BattlePathFinder _pathFinder;
	private BattleTile[][] _mapTiles;

	// 显示Map的上级Sprites
	private Sprites _screenSprites;

	private BattleMapObject _cameraTarget = null;
	private BattleSelectManager _selectionManager;
	private float _fixedWidthOffset = 0f;
	private float _fixedHeightOffset = 0f;
	private boolean _dragging = false;
	private boolean _updateBrightness = false;
	private boolean _roll = false;
	private boolean _hideAllTile = false;
	private int _dragStartX, _dragStartY;
	private int _pixelInWidth, _pixelInHeight;

	private float _deltaTime = LSystem.MIN_SECONE_SPEED_FIXED;
	public DrawListener<BattleMap> _drawListener;
	private LTexture _background;
	private ResizeListener<BattleMap> _resizeListener;
	private SpriteCollisionListener _collSpriteListener;
	private LColor _baseColor = LColor.white;
	private BattleTileMake _tileMake;
	private Vector2f _tempPosition = new Vector2f();
	private IsoResult _tempIsoResult = new IsoResult();
	// 渲染瓦片网格的颜色
	private LColor _drawGridColor = LColor.red;
	private boolean _drawGrid;
	// 层高度
	public int isolayerHeight = 0;
	// 层偏移
	public float isolayerOffsetX = -1f;
	public float isolayerOffsetY = -1f;
	// 每层默认的阶梯偏移（像素），当瓦片未设置显式layerOffset时使用
	protected float _defaultLayerStepOffsetX = 16f;
	protected float _defaultLayerStepOffsetY = -16f;
	protected GameEventBus<PathResult> _pathResultBus = null;
	// 指定用于寻路的层索引，也就是寻径时按照第几层的Layer偏移来计算，-1表示自动使用最高层，即_layers.size-1
	protected int _pathFinderLayerIndex = -1;
	protected BattleTile[][] _pathFinderTiles = null;
	// 当前用于寻路的层像素偏移，也就是寻径时偏移多少数值
	protected float _pathFinderLayerOffsetX = 0f;
	protected float _pathFinderLayerOffsetY = 0f;
	// 缓存每层渲染偏移（计算结果）与脏标记
	private final TArray<PointF> _layerRenderOffsetCache = new TArray<PointF>();
	private final BoolArray _layerRenderOffsetDirty = new BoolArray();

	// 指定渲染偏移基准层（也就是人物渲染的偏移值基于第几层Layer，不设置时默认最高层，即_layers.size-1）
	private int _renderBaseLayerIndex = -1;

	// 当层级偏移改变时的监听器
	private LayerChangeListener _layerChangeListener = null;

	// 是否启用层偏移缓存
	private boolean _useLayerOffsetCache = true;

	private int _maxAllowedHeightDiff = MAX_ALLOWED_HEIGHT_DIFF;

	public BattleMap(BattleTileMake make, Field2D field2d, Screen screen, GameEventBus<Object> events,
			IsoConfig config) {
		this(make, field2d, 0, 0, screen, events, config);
	}

	public BattleMap(BattleTileMake make, Field2D field2d, int x, int y, Screen screen, GameEventBus<Object> events,
			IsoConfig config) {
		this(make, field2d, screen, x, y, events, config, LSystem.viewSize.getWidth(), LSystem.viewSize.getHeight());
	}

	public BattleMap(BattleTileMake make, Field2D field2d, int x, int y, Screen screen, GameEventBus<Object> events,
			IsoConfig config, int screenWidth, int screenHeight) {
		this(make, field2d, screen, x, y, events, config, screenWidth, screenHeight);
	}

	public BattleMap(BattleTileMake make, Field2D field2d, Screen screen, int x, int y, GameEventBus<Object> events,
			IsoConfig config, int screenWidth, int screenHeight) {
		_tileMake = make;
		if (config == null) {
			config = IsoConfig.defaultConfig();
		}
		if (field2d != null) {
			this._field2d = field2d;
		} else {
			this._field2d = new Field2D((int) (screenWidth / config.tileWidth),
					(int) (screenHeight / config.tileHeight), (int) config.tileWidth, (int) config.tileHeight);
		}
		if (field2d != null && screenWidth == -1 && screenHeight == -1) {
			this._pixelInWidth = field2d.getViewWidth();
			this._pixelInHeight = field2d.getViewHeight();
		} else {
			this._pixelInWidth = screenWidth;
			this._pixelInHeight = screenHeight;
		}
		if (field2d == null) {
			this._pixelOffset = new Vector2f(0, 0);
		} else {
			this._pixelOffset = field2d.getOffset();
			config.tileWidth = field2d.getTileWidth();
			config.tileHeight = field2d.getTileHeight();
		}
		this._eventBus = events;
		if (_eventBus == null) {
			_eventBus = new GameEventBus<Object>();
		}
		this._isoConfig = config;
		this._visible = _playAnimation = true;
		this._mapSprites = new Sprites("BattleMapSprites", screen == null ? LSystem.getProcess().getScreen() : screen,
				_pixelInWidth, _pixelInHeight);
		if (x == 0 && y == 0) {
			this.fixMapLocationToCenter();
		} else {
			this.setLocation(x, y);
		}
		// 默认情况下，下一层比上一层差一格，当然可以调整，非强制
		setDefaultIsoLayerStepOffset(_isoConfig.getScaleTileX() / 2, -_isoConfig.getScaleTileY() / 2);
		_defaultGlobalSkill.setRunning(false);
		_defaultGlobalSkill.setBattleMap(this);
		_layerSortDirty = true;
		_layoutThickness = 1;
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, ISprite sprite,
			MovementListener l) {
		BattleMapObject obj = new BattleMapObject(_isoConfig, this, sprite, _mapObjects.size(), name, gx, gy, cw, ch,
				l);
		_mapObjects.add(obj);
		if (sprite != null) {
			_mapSprites.add(sprite);
		}
		return obj;
	}

	public BattleMapObject addMapObject(RoleEquip e, String name, int gx, int gy, int cw, int ch, ISprite sprite,
			MovementListener l) {
		BattleMapObject obj = new BattleMapObject(_isoConfig, this, sprite, _mapObjects.size(), e, name, gx, gy, cw, ch,
				l);
		_mapObjects.add(obj);
		if (sprite != null) {
			_mapSprites.add(sprite);
		}
		return obj;
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, AnimationManager manager,
			MovementListener l) {
		return addMapObject(gx, gy, cw, ch, name, manager, l, LColor.white);
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, AnimationManager manager,
			MovementListener l, LColor color) {
		return addMapObject(gx, gy, cw, ch, name, manager, l, 0f, 0f, color);
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, AnimationManager manager,
			MovementListener l, float x, float y, LColor color) {
		return addMapObject(gx, gy, cw, ch, name, manager, l, x, y, 1f, 1f, color);
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, AnimationManager manager,
			MovementListener l, float x, float y, float scaleX, float scaleY, LColor color) {
		return addMapObject(gx, gy, cw, ch, name, manager, l, x, y, scaleX, scaleY, false, false, color);
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, AnimationManager manager,
			MovementListener l, float x, float y, float scaleX, float scaleY, boolean flipX, boolean flipY,
			LColor color) {
		return addMapObject(gx, gy, cw, ch, name, manager, l, x, y, scaleX, scaleY, flipX, flipY, color, 0);
	}

	public BattleMapObject addMapObject(int gx, int gy, int cw, int ch, String name, AnimationManager manager,
			MovementListener l, float x, float y, float scaleX, float scaleY, boolean flipX, boolean flipY,
			LColor color, int layerIndex) {
		AnimationRenderer renderer = new AnimationRenderer(0, 0, cw, ch);
		BattleMapObject obj = new BattleMapObject(_isoConfig, this, renderer, _mapObjects.size(), name, gx, gy, cw, ch,
				l);
		_mapObjects.add(obj);
		if (manager != null) {
			renderer.addCharacter(manager, x, y, scaleX, scaleY, flipX, flipY, color, layerIndex);
		}
		_mapSprites.add(renderer);
		return obj;
	}

	public BattleMapObject addMapObject(RoleEquip e, String name, int gx, int gy, int cw, int ch,
			AnimationManager manager, MovementListener l) {
		AnimationRenderer renderer = new AnimationRenderer(0, 0, cw, ch);
		BattleMapObject obj = new BattleMapObject(_isoConfig, this, renderer, _mapObjects.size(), e, name, gx, gy, cw,
				ch, l);
		_mapObjects.add(obj);
		if (manager != null) {
			renderer.addCharacter(manager, 0, 0);
		}
		_mapSprites.add(renderer);
		return obj;
	}

	public boolean removeMapObject(BattleMapObject o) {
		if (o == null) {
			return false;
		}
		boolean result = _mapObjects.remove(o);
		if (result) {
			_mapSprites.remove(o.getRoleObject());
		}
		return result;
	}

	public boolean addSprite(ISprite spr) {
		return _mapSprites.add(spr);
	}

	public boolean containsSprite(ISprite spr) {
		return _mapSprites.contains(spr);
	}

	public boolean removeSprite(ISprite spr) {
		return _mapSprites.remove(spr);
	}

	public TileIsoHighlighter getTileHighlighter() {
		return _highlighter;
	}

	public void fixMapLocationToLeftTop() {
		fixMapLocationToOrigin("leftTop");
	}

	public void fixMapLocationToLeftBottom() {
		fixMapLocationToOrigin("leftBottom");
	}

	public void fixMapLocationToRightTop() {
		fixMapLocationToOrigin("rightTop");
	}

	public void fixMapLocationToRightBottom() {
		fixMapLocationToOrigin("rightBottom");
	}

	public void fixMapLocationToCenter() {
		fixMapLocationToOrigin("center");
	}

	public void fixMapLocationToOrigin(String style) {
		setLocation(getMapLocationToOrigin(style));
	}

	public Vector2f getMapLocationToOrigin(String style) {
		ObjectMap<String, Vector2f> offsets = _field2d != null
				? ISOUtils.alignIsoMapOffsets(_field2d.getWidth(), _field2d.getHeight(), _pixelInWidth, _pixelInHeight,
						_isoConfig)
				: ISOUtils.alignIsoMapOffsets(MathUtils.ifloor(_pixelInWidth / _isoConfig.tileWidth),
						MathUtils.ifloor(_pixelInHeight / _isoConfig.tileHeight), _pixelInWidth, _pixelInHeight,
						_isoConfig);
		if (offsets != null) {
			Vector2f centerOffset = offsets.get(style);
			if (centerOffset != null) {
				return centerOffset;
			}
		}
		return new Vector2f();
	}

	public boolean isDrawGrid() {
		return _drawGrid;
	}

	public BattleMap setDrawGrid(boolean v) {
		this._drawGrid = v;
		return this;
	}

	public float getTileHighlighterSpeed() {
		return _highlighter.getSpeed();
	}

	public TileIsoHighlighter setHighlighterSpeed(float v) {
		return _highlighter.setSpeed(v);
	}

	public TileIsoHighlighter clearHighlighterEffect() {
		_highlighter.clearEffect();
		return _highlighter;
	}

	public TileIsoHighlighter updateAllHighlighterEffect(boolean fadeOut, boolean fadeIn, boolean breath,
			LColor borderColor) {
		_highlighter.updateAllEffect(fadeOut, fadeIn, breath, borderColor);
		return _highlighter;
	}

	public void addHighlighterEffect(int x, int y, EffectType type) {
		_highlighter.addEffect(x, y, type);
	}

	public void addHighlighterEffects(EffectType e, Vector2f... coords) {
		_highlighter.addEffects(e, coords);
	}

	public void addHighlighterEffects(ObjectMap<TileIsoHighlighter.EffectType, TArray<Vector2f>> typeToCoords) {
		_highlighter.addEffects(typeToCoords);
	}

	public void highlighterMoveRange(int startX, int startY, int movePower) {
		_highlighter.generateMoveRange(startX, startY, movePower, _field2d);
	}

	public void highlighterMoveRange(int startX, int startY, int movePower, boolean allDir) {
		_highlighter.generateMoveRange(startX, startY, movePower, _field2d, allDir);
	}

	public void highlighterRange(int centerX, int centerY, RangeType rangeType, int size, EffectType effect) {
		_highlighter.generateRange(centerX, centerY, rangeType, size, _field2d, effect);
	}

	public void highlighterRange(int centerX, int centerY, RangeType rangeType, int size, EffectType effect,
			boolean allDir) {
		highlighterRange(centerX, centerY, rangeType, size, effect, null, allDir);
	}

	public void highlighterRange(int centerX, int centerY, RangeType rangeType, int size, EffectType effect,
			TArray<PointI> paths, boolean allDir) {
		_highlighter.generateRange(centerX, centerY, rangeType, size, _field2d, effect, paths, allDir);
	}

	public void highlighterRange(EffectType type, int startX, int startY, int minRange, int maxRange) {
		_highlighter.generateRange(type, startX, startY, minRange, maxRange, _field2d);
	}

	public void highlighterRadius(EffectType type, int startX, int startY, int radius) {
		_highlighter.generateRadius(type, startX, startY, radius, _field2d);
	}

	public void highlighterRangePathToEffect(TArray<PointI> paths, EffectType effect) {
		_highlighter.generateRangePathToEffect(effect, paths);
	}

	public BattleTile getMapTile(int tx, int ty) {
		if (_field2d.contains(tx, ty)) {
			return _mapTiles[tx][ty];
		}
		return null;
	}

	@Override
	public void update(long elapsedTime) {
		if (!_visible) {
			return;
		}
		_deltaTime = MathUtils.max(Duration.toS(elapsedTime), LSystem.MIN_SECONE_SPEED_FIXED);
		_highlighter.update(_deltaTime);
		if (_mapSprites != null) {
			_mapSprites.update(elapsedTime);
		}
		if (_drawListener != null) {
			_drawListener.update(elapsedTime);
		}
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
		final boolean update = (_objectRotation != 0);
		final int blend = g.getBlendMode();
		final int color = g.color();
		try {
			g.setBlendMode(_GL_BLEND);
			g.setAlpha(_objectAlpha);
			if (this._roll) {
				this._pixelOffset = toRollPosition(this._pixelOffset);
			}
			final float drawMapX = this._objectLocation.x + offsetX + _pixelOffset.getX();
			final float drawMapY = this._objectLocation.y + offsetY + _pixelOffset.getY();
			if (update) {
				g.saveTx();
				Affine2f tx = g.tx();
				if (_objectRotation != 0) {
					final float rotationCenterX = drawMapX + getWidth() / 2f;
					final float rotationCenterY = drawMapY + getHeight() / 2f;
					tx.translate(rotationCenterX, rotationCenterY);
					tx.preRotate(_objectRotation);
					tx.translate(-rotationCenterX, -rotationCenterY);
				}
			}
			followActionObject();
			drawIsoMap(g, drawMapX, drawMapY);
		} catch (Throwable ex) {
			LSystem.error("The BattleMap error !", ex);
		} finally {
			if (update) {
				g.restoreTx();
			}
			g.setBlendMode(blend);
			g.setColor(color);
		}
	}

	protected void drawIsoMap(GLEx g, float drawMapX, float drawMapY) {
		final float tileW = _isoConfig.tileWidth;
		final float tileH = _isoConfig.tileHeight;
		final int mapTileW = _field2d.getWidth();
		final int mapTileH = _field2d.getHeight();
		final float tileWidth = _isoConfig.tileWidth * _isoConfig.scaleX;
		final float tileHeight = _isoConfig.tileHeight * _isoConfig.scaleY;
		final float screenW = _pixelInWidth;
		final float screenH = _pixelInHeight;
		final int posOffsetX = MathUtils.ifloor(drawMapX);
		final int posOffsetY = MathUtils.ifloor(drawMapY);
		// 背景图在斜视地图中，其实一般是用于显示绘制好的大地图用的，也就是类似隔壁某模拟战某世录那种用法。
		// 在这些游戏中虽然会设定瓦片功能，但具体瓦片只是用于设置指定坐标效果，通常会隐藏瓦片，或者只显示某几个特殊瓦片。
		if (_background != null) {
			if (_backgroundSize.isEmpty()) {
				g.draw(_background, posOffsetX + _backgroundOffset.x, posOffsetY + _backgroundOffset.y, _baseColor);
			} else {
				g.draw(_background, posOffsetX + _backgroundOffset.x, posOffsetY + _backgroundOffset.y,
						_backgroundSize.x, _backgroundSize.y, _baseColor);
			}
		}

		final float worldLTX = -posOffsetX;
		final float worldLTY = -posOffsetY;
		final float worldRBX = worldLTX + screenW;
		final float worldRBY = worldLTY + screenH;
		int startX = MathUtils.ifloor(worldLTX / tileW);
		int startY = MathUtils.ifloor(worldLTY / tileH);
		int endX = MathUtils.iceil(worldRBX / tileW);
		int endY = MathUtils.iceil(worldRBY / tileH);

		final int dynamicMarginX = mapTileW / 2 + 6;
		final int dynamicMarginY = mapTileH / 2 + 6;
		startX -= dynamicMarginX;
		startY -= dynamicMarginY;
		endX += dynamicMarginX;
		endY += dynamicMarginY;

		endX = MathUtils.min(endX, mapTileW);
		endY = MathUtils.min(endY, mapTileH);

		// 单层渲染
		if (_mapTiles != null && _layers.size <= 1) {
			for (int x = startX; x < endX; x++) {
				for (int y = startY; y < endY; y++) {
					if (x < 0 || y < 0 || x >= mapTileW || y >= mapTileH) {
						continue;
					}
					BattleTile tile = _mapTiles[x][y];
					if (tile == null || !tile.isVisible) {
						continue;
					}
					Vector2f tilePos = tile.getScreenPosition(_tempPosition, _tempIsoResult);
					final float drawX = tilePos.x - _isoConfig.offsetX + posOffsetX;
					final float drawY = tilePos.y - _isoConfig.offsetY + posOffsetY;
					if (!CollisionHelper.checkAABBvsAABB(0, 0, screenW, screenH, drawX, drawY, tileWidth, tileHeight)) {
						continue;
					}
					if (!_hideAllTile) {
						if (_playAnimation) {
							tile.update(_deltaTime);
							if (_updateBrightness) {
								tile.updateBrightness();
							}
						}
						tile.paint(g, drawX, drawY, tileWidth, tileHeight, _tileColor);
					}
					_highlighter.renderTileHighlight(g, x, y, drawX, drawY, tileWidth, tileHeight);
					if (_drawGrid) {
						drawIsoTileBorder(g, drawX + tileWidth / 2 - 2, drawY + tileHeight / 2 - 2, tileWidth + 1,
								tileHeight + 1, _drawGridColor);
					}
				}
			}
			// 多层渲染
		} else {
			// 排序多层
			sortIsoLayers();
			// 遍历所有层级
			final int layerCount = _layers.size;
			for (int li = 0; li < layerCount; li++) {
				IsoTileLayer layer = _layers.get(li);
				if (layer == null || layer.tiles == null || !layer.visible) {
					continue;
				}
				final BattleTile[][] layerTiles = layer.tiles;
				final int layerW = layer.width;
				final int layerH = layer.height;
				final float layerOffsetX = layer.offsetX;
				final float layerOffsetY = layer.offsetY;

				int lxStart = MathUtils.max(startX, 0);
				int lyStart = MathUtils.max(startY, 0);
				int lxEnd = MathUtils.min(endX, layerW);
				int lyEnd = MathUtils.min(endY, layerH);

				for (int x = lxStart; x < lxEnd; x++) {
					for (int y = lyStart; y < lyEnd; y++) {
						if (!inTileLayerBounds(layer, x, y)) {
							continue;
						}
						BattleTile tile = layerTiles[x][y];
						if (tile == null || !tile.isVisible) {
							continue;
						}
						Vector2f tilePos = tile.getScreenPosition(_tempPosition, _tempIsoResult);
						float drawX = tilePos.x - _isoConfig.offsetX + posOffsetX + layerOffsetX;
						float drawY = tilePos.y - _isoConfig.offsetY + posOffsetY + layerOffsetY;

						// 先确保缓存存在并计算
						PointF cached = computeLayerRenderOffsetIfDirty(li, null);
						float compositeOffsetX = cached.x + layerOffsetX;
						float compositeOffsetY = cached.y + layerOffsetY;

						// 基于指定的基础层，合成偏移数值
						PointF baseComposite = getIsoLayerCompositeOffset(getRenderBaseIsoLayerIndex());
						drawX += compositeOffsetX - baseComposite.x;
						drawY += compositeOffsetY - baseComposite.y;

						if (!CollisionHelper.checkAABBvsAABB(0, 0, screenW, screenH, drawX, drawY, tileWidth,
								tileHeight)) {
							continue;
						}

						if (!_hideAllTile) {
							if (_playAnimation) {
								tile.update(_deltaTime);
								// 若设置亮度，可以明暗变化的显示瓦片
								if (_updateBrightness) {
									tile.updateBrightness();
								}
							}
							tile.paint(g, drawX, drawY, tileWidth, tileHeight, _tileColor);
						}
						_highlighter.renderTileHighlight(g, x, y, drawX, drawY, tileWidth, tileHeight);
						if (_drawGrid) {
							drawIsoTileBorder(g, drawX + tileWidth / 2 - 2, drawY + tileHeight / 2 - 2, tileWidth + 1,
									tileHeight + 1, _drawGridColor);
						}
					}
				}
			}
		}

		final float basePosOffsetX = posOffsetX;
		final float basePosOffsetY = posOffsetY;

		// 获得当前渲染的基准层
		final int baseLayerIndex = getRenderBaseLayerIndex();

		if (baseLayerIndex >= 0 && baseLayerIndex < _layers.size) {
			IsoTileLayer baseLayer = _layers.get(baseLayerIndex);
			PointF baseCache = computeLayerRenderOffsetIfDirty(baseLayerIndex, null);
			float bx = (baseLayer == null ? 0f : baseLayer.offsetX) + (baseCache == null ? 0f : baseCache.x);
			float by = (baseLayer == null ? 0f : baseLayer.offsetY) + (baseCache == null ? 0f : baseCache.y);
			_baseComposite.set(bx, by);
		}

		final float layerOffsetX = basePosOffsetX + _baseComposite.x;
		final float layerOffsetY = basePosOffsetY + _baseComposite.y;

		// 地图精灵渲染
		_mapSprites.paint(g, layerOffsetX, layerOffsetY, startX * tileWidth, startY * tileHeight, endX * tileWidth,
				endY * tileHeight);
		// 其它地图对象渲染
		for (int i = 0; i < _mapObjects.size; i++) {
			BattleMapObject o = _mapObjects.get(i);
			if (o != null) {
				o.paint(g, _deltaTime, layerOffsetX, layerOffsetY);
			}
		}
		// 全局特效
		if (_defaultGlobalSkill.running) {
			_defaultGlobalSkill.updateSkill(_deltaTime);
			_defaultGlobalSkill.drawSkillEffect(g, _deltaTime, layerOffsetX, layerOffsetY);
		}
		// 其他渲染
		if (_drawListener != null) {
			_drawListener.draw(g, layerOffsetX, layerOffsetY);
		}

	}

	public void drawIsoTileBorder(GLEx g, float centerX, float centerY, float tileWidth, float tileHeight,
			LColor color) {
		final float halfW = tileWidth / 2;
		final float halfH = tileHeight / 4;
		_tempPx[0] = centerX;
		_tempPy[0] = centerY - halfH;
		_tempPx[1] = centerX + halfW;
		_tempPy[1] = centerY;
		_tempPx[2] = centerX;
		_tempPy[2] = centerY + halfH;
		_tempPx[3] = centerX - halfW;
		_tempPy[3] = centerY;
		final int oldColor = g.color();
		g.setColor(color);
		g.drawPolygon(_tempPx, _tempPy, 4);
		g.setColor(oldColor);
	}

	public LColor getTileColor() {
		return _tileColor;
	}

	public BattleMap setTileColor(LColor c) {
		_tileColor.setColor(c);
		return this;
	}

	public LColor getDrawGridColor() {
		return _drawGridColor;
	}

	public BattleMap setDrawGridColor(LColor c) {
		_drawGridColor = c;
		return this;
	}

	public void onTurnBegin() {
		for (int i = _mapObjects.size - 1; i > -1; i--) {
			BattleMapObject o = _mapObjects.get(i);
			if (o != null) {
				o.onTurnBegin();
			}
		}
	}

	public void onTurnEnd() {
		for (int i = _mapObjects.size - 1; i > -1; i--) {
			BattleMapObject o = _mapObjects.get(i);
			if (o != null) {
				o.onTurnEnd();
			}
		}
	}

	/**
	 * 获取指定索引的层
	 * 
	 * @param layerIndex
	 * @return
	 */
	public IsoTileLayer getIsoLayer(int layerIndex) {
		return (layerIndex >= 0 && layerIndex < _layers.size) ? _layers.get(layerIndex) : null;
	}

	/**
	 * 移除指定索引的层
	 * 
	 * @param layerIndex
	 * @return
	 */
	public BattleMap removeIsoLayer(int layerIndex) {
		if (layerIndex < 0 || layerIndex >= _layers.size) {
			return this;
		}
		IsoTileLayer layer = _layers.get(layerIndex);
		if (layer != null && layer.tiles != null) {
			for (int x = 0; x < layer.width; x++) {
				for (int y = 0; y < layer.height; y++) {
					if (layer.tiles[x][y] != null) {
						layer.tiles[x][y].close();
					}
				}
			}
		}
		_layers.removeIndex(layerIndex);
		_pathFinderLayerIndex = MathUtils.max(0, _layers.size - 1);
		rebuildPathFinderUsingSelectedLayer();
		_layerSortDirty = true;
		ensureLayerCacheSize();
		markAllLayerOffsetsDirty();
		if (_renderBaseLayerIndex >= _layers.size) {
			_renderBaseLayerIndex = -1;
		}
		if (_layerChangeListener != null) {
			_layerChangeListener.onLayerRemoved(layerIndex);
		}
		return this;
	}

	/**
	 * 设置整个层的显示偏移
	 * 
	 * @param layerIndex
	 * @param offsetX
	 * @param offsetY
	 * @return
	 */
	public BattleMap setIsoLayerOffset(int layerIndex, float offsetX, float offsetY) {
		IsoTileLayer layer = getIsoLayer(layerIndex);
		if (layer == null) {
			return this;
		}
		layer.offsetX = offsetX;
		layer.offsetY = offsetY;
		forceRecomputeLayerOffset(layerIndex);
		if (_layerChangeListener != null) {
			_layerChangeListener.onLayerOffsetChanged(layerIndex, offsetX, offsetY);
		}
		return this;
	}

	/**
	 * 清空所有层
	 */
	public BattleMap clearAllIsoLayers() {
		closeRes();
		_layers.clear();
		_pathFinderLayerIndex = -1;
		rebuildPathFinderUsingSelectedLayer();
		_layerSortDirty = true;
		_layerRenderOffsetCache.clear();
		_layerRenderOffsetDirty.clear();
		_renderBaseLayerIndex = -1;
		return this;
	}

	/**
	 * 排序层（仅脏标记时执行，底层先渲染，高层后渲染）
	 */
	public void sortIsoLayers() {
		if (_layers.size > 1 && _layerSortDirty) {
			_layers.sort(LAYER_SORTER);
			_layerSortDirty = false;
			ensureLayerCacheSize();
			markAllLayerOffsetsDirty();
			if (_layerChangeListener != null) {
				_layerChangeListener.onLayerSorted();
			}
		}
	}

	/**
	 * 获取指定层+坐标的瓦片
	 * 
	 * @param layerIndex
	 * @param gx
	 * @param gy
	 * @return
	 */
	public BattleTile getIsoLayerTile(int layerIndex, int gx, int gy) {
		IsoTileLayer layer = getIsoLayer(layerIndex);
		if (layer == null || !inTileLayerBounds(layer, gx, gy)) {
			return null;
		}
		return layer.tiles[gx][gy];
	}

	/**
	 * 获取所有层中指定坐标的瓦片（从上到下遍历）
	 * 
	 * @param gx
	 * @param gy
	 * @return
	 */
	public BattleTile getTileFromAllIsoLayers(int gx, int gy) {
		for (int i = _layers.size - 1; i >= 0; i--) {
			BattleTile tile = getIsoLayerTile(i, gx, gy);
			if (tile != null) {
				return tile;
			}
		}
		return getMapTile(gx, gy);
	}

	/**
	 * 批量替换指定层所有瓦片的动画/纹理
	 * 
	 * @param layerIndex
	 * @param anim
	 * @return
	 */
	public BattleMap replaceIsoLayerTilesAnimation(int layerIndex, TileAnimation anim) {
		IsoTileLayer layer = getIsoLayer(layerIndex);
		if (layer == null || anim == null) {
			return this;
		}
		for (int x = 0; x < layer.width; x++) {
			for (int y = 0; y < layer.height; y++) {
				BattleTile tile = layer.tiles[x][y];
				if (tile == null) {
					continue;
				}
				if (anim.backgroundAnim != null) {
					tile.bgAnim = anim.backgroundAnim.cpy();
				}
				if (anim.groundAnim != null) {
					tile.groundAnim = anim.groundAnim.cpy();
				}
				if (anim.effectAnim != null) {
					tile.effectAnim = anim.effectAnim.cpy();
				}
			}
		}
		return this;
	}

	/**
	 * 批量修改指定层的瓦片状态
	 * 
	 * @param layerIndex
	 * @param visible
	 * @param passable
	 * @param highlighted
	 * @return
	 */
	public BattleMap setIsoLayerTilesState(int layerIndex, boolean visible, boolean passable, boolean highlighted) {
		IsoTileLayer layer = getIsoLayer(layerIndex);
		if (layer == null) {
			return this;
		}
		for (int x = 0; x < layer.width; x++) {
			for (int y = 0; y < layer.height; y++) {
				BattleTile tile = layer.tiles[x][y];
				if (tile == null) {
					continue;
				}
				tile.isVisible = visible;
				tile.setPassable(passable);
				tile.isHighlighted = highlighted;
			}
		}
		return this;
	}

	/**
	 * 设定当前渲染层(即除了layer之外的画面渲染，基于哪个层级的偏移值进行)
	 * 
	 * @param layerIndex
	 * @return
	 */
	public BattleMap setRenderBaseIsoLayerIndex(int layerIndex) {
		if (layerIndex < -1) {
			layerIndex = -1;
		}
		this._renderBaseLayerIndex = layerIndex;
		return this;
	}

	/**
	 * 获得当前渲染层
	 * 
	 * @return
	 */
	public int getRenderBaseIsoLayerIndex() {
		if (_renderBaseLayerIndex < 0) {
			return _layers.size - 1;
		}
		return MathUtils.clamp(_renderBaseLayerIndex, 0, MathUtils.max(0, _layers.size - 1));
	}

	/**
	 * 获取某层相对于最下层的偏移数值
	 * 
	 * @param layerIndex
	 * @return
	 */
	public PointF getIsoLayerCompositeOffset(int layerIndex) {
		int base = getRenderBaseIsoLayerIndex();
		if (base < 0 || base >= _layers.size) {
			base = _layers.size - 1;
		}
		PointF result = new PointF(0f, 0f);
		if (layerIndex < 0 || layerIndex >= _layers.size) {
			return result;
		}
		IsoTileLayer layer = _layers.get(layerIndex);
		IsoTileLayer baseLayer = _layers.get(base);
		PointF layerCache = _layerRenderOffsetCache.size > layerIndex ? _layerRenderOffsetCache.get(layerIndex) : null;
		PointF baseCache = _layerRenderOffsetCache.size > base ? _layerRenderOffsetCache.get(base) : null;
		float lx = (layer == null ? 0f : layer.offsetX) + (layerCache == null ? 0f : layerCache.x);
		float ly = (layer == null ? 0f : layer.offsetY) + (layerCache == null ? 0f : layerCache.y);
		float bx = (baseLayer == null ? 0f : baseLayer.offsetX) + (baseCache == null ? 0f : baseCache.x);
		float by = (baseLayer == null ? 0f : baseLayer.offsetY) + (baseCache == null ? 0f : baseCache.y);
		result.set(lx - bx, ly - by);
		return result;
	}

	/**
	 * 判断坐标是否在层瓦片范围内
	 * 
	 * @param layer
	 * @param gx
	 * @param gy
	 * @return
	 */
	private boolean inTileLayerBounds(IsoTileLayer layer, int gx, int gy) {
		return gx >= 0 && gy >= 0 && gx < layer.width && gy < layer.height;
	}

	/**
	 * 计算两个瓦片的高度差
	 * 
	 * @param tileA
	 * @param tileB
	 * @return
	 */
	public int getTileHeightDiff(BattleTile tileA, BattleTile tileB) {
		if (tileA == null || tileB == null) {
			return 0;
		}
		return MathUtils.abs(tileA.getLayerHeight() - tileB.getLayerHeight());
	}

	/**
	 * 判断两个瓦片是否可通行（高度差+可通行属性）
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	public boolean isTileReachable(BattleTile from, BattleTile to) {
		if (to == null || !to.isPassable()) {
			return false;
		}
		return getTileHeightDiff(from, to) <= _maxAllowedHeightDiff;
	}

	/**
	 * 判断两个层之间是否可以直接切换
	 * 
	 * @param fromLayer
	 * @param toLayer
	 * @return
	 */
	public boolean canSwitchLayer(int fromLayer, int toLayer) {
		if (fromLayer == toLayer) {
			return true;
		}
		return MathUtils.abs(fromLayer - toLayer) <= _maxAllowedHeightDiff;
	}

	/**
	 * 获取一个坐标所有可用层级（从上往下）
	 * 
	 * @param gx
	 * @param gy
	 * @return
	 */
	public IntArray getValidLayersAt(int gx, int gy) {
		IntArray layers = new IntArray();
		for (int i = _layers.size - 1; i >= 0; i--) {
			BattleTile tile = getIsoLayerTile(i, gx, gy);
			if (tile != null && tile.isPassable()) {
				layers.add(i);
			}
		}
		return layers;
	}

	/**
	 * 获取从某层出发，能到达的目标层索引
	 * 
	 * @param fromLayer
	 * @param gx
	 * @param gy
	 * @return
	 */
	public int getReachableLayer(int fromLayer, int gx, int gy) {
		IntArray layers = getValidLayersAt(gx, gy);
		for (int i = 0; i < layers.length; i++) {
			int l = layers.get(i);
			if (canSwitchLayer(fromLayer, l)) {
				return l;
			}
		}
		return fromLayer;
	}

	/**
	 * 判断是否是楼梯/可上下层的瓦片
	 * 
	 * @param gx
	 * @param gy
	 * @return
	 */
	public boolean isStairTile(int gx, int gy) {
		for (PointI p : _stairTiles) {
			if (p.x == gx && p.y == gy) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 添加楼梯位置(以楼梯跨层寻径时必须存在)
	 * 
	 * @param gx
	 * @param gy
	 * @return
	 */
	public BattleMap addStairTile(int gx, int gy) {
		_stairTiles.add(new PointI(gx, gy));
		return this;
	}

	/**
	 * 清除所有楼梯
	 */
	public BattleMap clearStairTiles() {
		_stairTiles.clear();
		return this;
	}

	/**
	 * 带楼梯验证的跨层寻路（切换层级时，必须走楼梯入口）
	 * 
	 * @param fromLayer
	 * @param fromX
	 * @param fromY
	 * @param toLayer
	 * @param toX
	 * @param toY
	 * @return
	 */
	public TArray<PathNodeWithLayer> findStairLayerPath(int fromLayer, int fromX, int fromY, int toLayer, int toX,
			int toY) {
		TArray<PathNodeWithLayer> result = new TArray<PathNodeWithLayer>();
		BattleTile fromTile = getIsoLayerTile(fromLayer, fromX, fromY);
		BattleTile toTile = getIsoLayerTile(toLayer, toX, toY);
		if (fromTile == null || toTile == null || !fromTile.isPassable() || !toTile.isPassable()) {
			return result;
		}
		if (fromLayer == toLayer) {
			TArray<PointI> path = findCrossIsoLayerPath(fromLayer, fromX, fromY, toLayer, toX, toY);
			if (path != null) {
				for (PointI p : path) {
					result.add(new PathNodeWithLayer(p.x, p.y, fromLayer));
				}
			}
			return result;
		}
		if (!isStairTile(fromX, fromY) && !isStairTile(toX, toY)) {
			return result;
		}
		PointI stair = findNearestStair(fromX, fromY);
		if (stair == null) {
			return result;
		}
		TArray<PointI> p1 = findCrossIsoLayerPath(fromLayer, fromX, fromY, fromLayer, stair.x, stair.y);
		TArray<PointI> p2 = findCrossIsoLayerPath(toLayer, stair.x, stair.y, toLayer, toX, toY);
		if (p1 != null) {
			for (PointI p : p1) {
				result.add(new PathNodeWithLayer(p.x, p.y, fromLayer));
			}
		}
		result.add(new PathNodeWithLayer(stair.x, stair.y, toLayer));
		if (p2 != null) {
			for (PointI p : p2) {
				result.add(new PathNodeWithLayer(p.x, p.y, toLayer));
			}
		}
		return result;
	}

	/**
	 * 寻找最近的可跨层楼梯
	 */
	public PointI findNearestStair(int gx, int gy) {
		PointI nearest = null;
		float minDist = Float.MAX_VALUE;
		for (PointI s : _stairTiles) {
			float d = MathUtils.distance(s.x, s.y, gx, gy);
			if (d < minDist) {
				minDist = d;
				nearest = s;
			}
		}
		return nearest;
	}

	/**
	 * 自动获取对象所在层和楼梯位置并跨层寻路（移动动画需要特殊准备）
	 * 
	 * @param obj
	 * @param targetX
	 * @param targetY
	 * @return
	 */
	public TArray<PathNodeWithLayer> findStairLayerPath(BattleMapObject obj, int targetX, int targetY) {
		if (obj == null || _pathFinder == null) {
			return new TArray<PathNodeWithLayer>();
		}
		int objLayer = getObjectIsoLayerIndex(obj);
		int targetLayer = getReachableLayer(objLayer, targetX, targetY);
		return findStairLayerPath(objLayer, obj.getGridX(), obj.getGridY(), targetLayer, targetX, targetY);
	}

	/**
	 * 跨层寻路，即在两个不同层级间寻径（移动动画需要特殊准备）
	 * 
	 * @param fromLayer
	 * @param fromX
	 * @param fromY
	 * @param toLayer
	 * @param toX
	 * @param toY
	 * @return
	 */
	public TArray<PointI> findCrossIsoLayerPath(int fromLayer, int fromX, int fromY, int toLayer, int toX, int toY) {
		if (_pathFinder == null) {
			return null;
		}
		if (!canSwitchLayer(fromLayer, toLayer)) {
			return null;
		}
		BattleTile fromTile = getIsoLayerTile(fromLayer, fromX, fromY);
		BattleTile toTile = getIsoLayerTile(toLayer, toX, toY);
		if (!isTileReachable(fromTile, toTile)) {
			return null;
		}
		int original = _pathFinderLayerIndex;
		setPathFinderLayerIndex(toLayer);
		TArray<PointI> path = _pathFinder.findPath(fromX, fromY, toX, toY);
		setPathFinderLayerIndex(original);
		return path;
	}

	/**
	 * 获取对象所在的地图层级
	 * 
	 * @param obj
	 * @return
	 */
	public int getObjectIsoLayerIndex(BattleMapObject obj) {
		if (obj == null) {
			return 0;
		}
		for (int i = 0; i < _layers.size; i++) {
			BattleTile tile = getIsoLayerTile(i, obj.getGridX(), obj.getGridY());
			if (tile != null) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * 分层渲染偏移计算，默认台阶式排列
	 * 
	 * @param layerIndex
	 * @param tile
	 * @return
	 */
	protected PointF calculateLayerOffset(int layerIndex, BattleTile tile) {
		float stepX = _defaultLayerStepOffsetX;
		float stepY = _defaultLayerStepOffsetY;
		float autoOx = 0, autoOy = 0;
		final boolean hasTile = (tile != null);
		if (hasTile && tile.hasLayerOffset()) {
			autoOx = tile.baseOffsetX;
			autoOy = tile.baseOffsetY;
		} else if (hasTile) {
			autoOx = stepX * layerIndex + (tile.getLayerHeight() * stepX * 0.3f);
			autoOy = stepY * layerIndex + (tile.getLayerHeight() * stepY * 0.3f);
		} else {
			autoOx = stepX * layerIndex;
			autoOy = stepY * layerIndex;
		}
		return _layerOffet.set(autoOx, autoOy);
	}

	/**
	 * 获取当前渲染基准层索引（若为-1则返回最高层索引）
	 * 
	 * @return
	 */
	public int getRenderBaseLayerIndex() {
		if (_renderBaseLayerIndex < 0) {
			return MathUtils.max(0, _layers.size - 1);
		}
		if (_renderBaseLayerIndex >= _layers.size) {
			return MathUtils.max(0, _layers.size - 1);
		}
		return _renderBaseLayerIndex;
	}

	/**
	 * 设定当前画面渲染人物特效时基于的基准层，即按照那一层的坐标来计算
	 * 
	 * @param idx
	 * @return
	 */
	public BattleMap setRenderBaseLayerIndex(int idx) {
		if (idx < -1) {
			idx = -1;
		}
		this._renderBaseLayerIndex = idx;
		return this;
	}

	protected void ensureLayerCacheSize() {
		int layerCount = _layers.size;
		while (_layerRenderOffsetCache.size < layerCount) {
			_layerRenderOffsetCache.add(new PointF(0f, 0f));
			_layerRenderOffsetDirty.add(true);
		}
		while (_layerRenderOffsetCache.size > layerCount) {
			_layerRenderOffsetCache.removeIndex(_layerRenderOffsetCache.size - 1);
			_layerRenderOffsetDirty.removeIndex(_layerRenderOffsetDirty.length - 1);
		}
	}

	/**
	 * 标记某层缓存为脏
	 * 
	 * @param layerIndex
	 */
	public void forceRecomputeLayerOffset(int layerIndex) {
		if (layerIndex >= 0 && layerIndex < _layerRenderOffsetDirty.length) {
			_layerRenderOffsetDirty.set(layerIndex, true);
		}
	}

	/**
	 * 标记所有层为脏
	 */
	public void markAllLayerOffsetsDirty() {
		for (int i = 0; i < _layerRenderOffsetDirty.length; i++) {
			_layerRenderOffsetDirty.set(i, true);
		}
	}

	/**
	 * 计算单层的偏移
	 * 
	 * @param layerIndex
	 * @param tile
	 * @return
	 */
	protected PointF computeLayerRenderOffsetIfDirty(int layerIndex, BattleTile tile) {
		ensureLayerCacheSize();
		if (layerIndex < 0 || layerIndex >= _layers.size) {
			return _baseComposite.set(0f, 0f);
		}
		if (!_useLayerOffsetCache) {
			return calculateLayerOffset(layerIndex, tile);
		}
		if (_layerRenderOffsetDirty.get(layerIndex)) {
			PointF computed = calculateLayerOffset(layerIndex, tile);
			PointF cache = _layerRenderOffsetCache.get(layerIndex);
			cache.set(computed.x, computed.y);
			_layerRenderOffsetDirty.set(layerIndex, false);
			return cache;
		}
		return _layerRenderOffsetCache.get(layerIndex);
	}

	/**
	 * 获取某层相对于基准层的偏移
	 * 
	 * @param layerIndex
	 * @return
	 */
	public PointF getLayerCompositeOffsetRelativeToBase(int layerIndex) {
		ensureLayerCacheSize();
		PointF result = new PointF(0f, 0f);
		if (layerIndex < 0 || layerIndex >= _layers.size) {
			return result;
		}
		int baseIdx = getRenderBaseLayerIndex();
		IsoTileLayer layer = _layers.get(layerIndex);
		IsoTileLayer baseLayer = _layers.get(baseIdx);

		PointF layerCache = computeLayerRenderOffsetIfDirty(layerIndex, null);
		PointF baseCache = computeLayerRenderOffsetIfDirty(baseIdx, null);

		float lx = layer.offsetX + layerCache.x;
		float ly = layer.offsetY + layerCache.y;
		float bx = baseLayer.offsetX + baseCache.x;
		float by = baseLayer.offsetY + baseCache.y;

		result.set(lx - bx, ly - by);
		return result;
	}

	/**
	 * 设置指定索引层是否可见性
	 * 
	 * @param layerIndex
	 * @param visible
	 * @return
	 */
	public BattleMap setIsoLayerVisible(int layerIndex, boolean visible) {
		IsoTileLayer layer = getIsoLayer(layerIndex);
		if (layer == null || layer.visible == visible) {
			return this;
		}
		layer.visible = visible;
		_layerSortDirty = true;
		forceRecomputeLayerOffset(layerIndex);
		if (_layerChangeListener != null) {
			_layerChangeListener.onLayerVisibilityChanged(layerIndex, visible);
		}
		return this;
	}

	/**
	 * 将一个新增的渲染层插入指定位置取代原有数据
	 * 
	 * @param index
	 * @param tiles
	 * @param name
	 * @return
	 */
	public BattleMap insertIsoLayer(int index, BattleTile[][] tiles, String name) {
		if (index < 0 || index > _layers.size) {
			return addIsoLayer(tiles, name);
		}
		IsoTileLayer newLayer = new IsoTileLayer(tiles, name, index);
		_layers.insert(index, newLayer);
		for (int i = 0; i < _layers.size; i++) {
			_layers.get(i).layerIndex = i;
		}
		_mapTiles = tiles;
		_pathFinderLayerIndex = index;
		rebuildPathFinderUsingSelectedLayer();
		ensureLayerCacheSize();
		markAllLayerOffsetsDirty();
		_layerSortDirty = true;
		if (_layerChangeListener != null) {
			_layerChangeListener.onLayerAdded(index);
		}
		return this;
	}

	/**
	 * 交换两个层的所有数据
	 * 
	 * @param idx1
	 * @param idx2
	 * @return
	 */
	public BattleMap swapIsoLayers(int idx1, int idx2) {
		if (idx1 < 0 || idx2 < 0 || idx1 >= _layers.size || idx2 >= _layers.size || idx1 == idx2) {
			return this;
		}
		IsoTileLayer temp = _layers.get(idx1);
		_layers.set(idx1, _layers.get(idx2));
		_layers.set(idx2, temp);
		_layers.get(idx1).layerIndex = idx1;
		_layers.get(idx2).layerIndex = idx2;
		_layerSortDirty = true;
		markAllLayerOffsetsDirty();
		return this;
	}

	/**
	 * 设定允许跨越层的最大差值
	 * 
	 * @param diff
	 */
	public void setMaxAllowedHeightDiff(int diff) {
		this._maxAllowedHeightDiff = MathUtils.max(0, diff);
	}

	/**
	 * 获取层可见性
	 * 
	 * @param layerIndex
	 * @return
	 */
	public boolean isIsoLayerVisible(int layerIndex) {
		IsoTileLayer layer = getIsoLayer(layerIndex);
		return layer != null && layer.visible;
	}

	/**
	 * 设置所有层可见性
	 * 
	 * @param visible
	 * @return
	 */
	public BattleMap setAllIsoLayersVisible(boolean visible) {
		for (int i = 0; i < _layers.size; i++) {
			IsoTileLayer layer = _layers.get(i);
			if (layer != null) {
				layer.visible = visible;
			}
		}
		return this;
	}

	/**
	 * 上移指定层级（调整渲染顺序）
	 * 
	 * @param layerIndex
	 * @return
	 */
	public BattleMap moveIsoLayerUp(int layerIndex) {
		if (layerIndex <= 0 || layerIndex >= _layers.size) {
			return this;
		}
		IsoTileLayer layer = _layers.removeIndex(layerIndex);
		_layers.set(layerIndex - 1, layer);
		_layerSortDirty = true;
		return this;
	}

	/**
	 * 下移指定层级（调整渲染顺序）
	 * 
	 * @param layerIndex
	 * @return
	 */
	public BattleMap moveIsoLayerDown(int layerIndex) {
		if (layerIndex < 0 || layerIndex >= _layers.size - 1) {
			return this;
		}
		IsoTileLayer layer = _layers.removeIndex(layerIndex);
		_layers.set(layerIndex + 1, layer);
		_layerSortDirty = true;
		return this;
	}

	/**
	 * 获取某坐标从上到下的所有可见层索引
	 * 
	 * @param gx
	 * @param gy
	 * @return
	 */
	public IntArray getVisibleLayersAt(int gx, int gy) {
		IntArray result = new IntArray();
		for (int i = _layers.size - 1; i >= 0; i--) {
			IsoTileLayer l = _layers.get(i);
			if (l != null && l.visible && inTileLayerBounds(l, gx, gy)) {
				BattleTile t = l.tiles[gx][gy];
				if (t != null && t.isVisible) {
					result.add(i);
				}
			}
		}
		return result;
	}

	/**
	 * 层级监听器设置
	 * 
	 * @param l
	 * @return
	 */
	public BattleMap setLayerChangeListener(LayerChangeListener l) {
		this._layerChangeListener = l;
		return this;
	}

	/**
	 * 手动触发层级重排序
	 */
	public void markLayerSortDirty() {
		this._layerSortDirty = true;
	}

	public BattleMap createMap(Field2D map) {
		return createMap(map, _layers.size - 1, null, null);
	}

	public BattleMap createMap(Field2D map, int layerIndex) {
		return createMap(map, layerIndex, null, null);
	}

	public BattleMap createMap(Field2D map, int layerIndex, EffectService effectService, SkillService skillService) {
		return createMap(generateMergedMap(map, MathUtils.max(0, layerIndex), effectService, skillService));
	}

	public BattleMap createMap() {
		return createMap(generateMap(null, null));
	}

	public BattleMap createMap(BattleTile[][] maps) {
		return createMap(new GameEventBus<PathResult>(), new GameEventBus<BattleMapObject>(), maps);
	}

	public BattleMap createMap(GameEventBus<PathResult> pathResult, GameEventBus<BattleMapObject> bus) {
		return createMap(pathResult, bus, null, null);
	}

	public BattleMap createMap(GameEventBus<PathResult> pathResult, GameEventBus<BattleMapObject> bus,
			EffectService effectService, SkillService skillService) {
		return createMap(pathResult, bus, _field2d, effectService, skillService);
	}

	public BattleMap createMap(GameEventBus<PathResult> pathResult, GameEventBus<BattleMapObject> bus, Field2D map,
			EffectService effectService, SkillService skillService) {
		return createMap(pathResult, bus, generateMap(effectService, skillService));
	}

	public BattleMap createMap(GameEventBus<PathResult> pathResult, GameEventBus<BattleMapObject> bus,
			BattleTile[][] maps) {
		return createMap(null, pathResult, bus, maps);
	}

	/**
	 * 创建一个BattleMap使用的地图层，注意，每次create都会产生一个新Layer层，将自动叠加。
	 * 在不设定操作Layer的前提下，loon默认操作位于最上面一层。
	 * 
	 * @param name
	 * @param pathResult
	 * @param bus
	 * @param maps
	 * @return
	 */
	public BattleMap createMap(String name, GameEventBus<PathResult> pathResult, GameEventBus<BattleMapObject> bus,
			BattleTile[][] maps) {
		_selectionManager = new BattleSelectManager(bus);
		BattleTile[][] layerTiles = maps;
		if (layerTiles == null) {
			layerTiles = generateMap(null, null);
		}
		IsoTileLayer newLayer = new IsoTileLayer(layerTiles, StringUtils.isEmpty(name) ? "layer_" + _layers.size : name,
				_layers.size);
		_layers.add(newLayer);
		_mapTiles = layerTiles;
		int newIndex = _layers.size - 1;
		for (int gx = 0; gx < newLayer.width; gx++) {
			for (int gy = 0; gy < newLayer.height; gy++) {
				try {
					BattleTile t = newLayer.tiles[gx][gy];
					if (t != null) {
						t.setLayerIndex(newIndex);
					}
				} catch (Throwable ex) {
				}
			}
		}
		_pathFinderLayerIndex = MathUtils.max(0, newIndex);
		rebuildPathFinderUsingSelectedLayer();
		_layerSortDirty = true;
		ensureLayerCacheSize();
		markAllLayerOffsetsDirty();
		if (_layerChangeListener != null) {
			_layerChangeListener.onLayerAdded(_layers.size - 1);
		}
		_renderBaseLayerIndex = -1;
		return this;
	}

	private void applyTileAnimation(BattleTile tile, int tileId) {
		if (_tileMake == null) {
			return;
		}
		TileAnimation ani = _tileMake.getTileAnimation(tileId);
		if (ani == null) {
			return;
		}
		if (ani.backgroundAnim != null) {
			tile.bgAnim = ani.backgroundAnim.cpy();
		}
		if (ani.groundAnim != null) {
			tile.groundAnim = ani.groundAnim.cpy();
		}
		if (ani.effectAnim != null) {
			tile.effectAnim = ani.effectAnim.cpy();
		}
	}

	public BattleTileMake getTileMake() {
		return _tileMake;
	}

	public void setGeneratetMapLayout(MapLayoutStyle layout) {
		this.setGeneratetMapLayout(layout, _layoutThickness);
	}

	public void setGeneratetMapLayout(MapLayoutStyle layout, int thickness) {
		this._currentLayout = layout;
		this._layoutThickness = MathUtils.max(1, thickness);
	}

	public void resetGenerateMapLayout() {
		this._currentLayout = MapLayoutStyle.FULL;
	}

	public BattleTile[][] generateMap(EffectService effectService, SkillService skillService) {
		return generateMap(_field2d, effectService, skillService);
	}

	public BattleTile[][] generateMap(Field2D map, EffectService effectService, SkillService skillService) {
		return generateMergedMap(map, _pathFinderLayerIndex, effectService, skillService);
	}

	public BattleTile[][] generateMergedMap(Field2D map, int layerIndex, EffectService effectService,
			SkillService skillService) {
		return generateMergedMap(map, layerIndex, effectService, skillService, _currentLayout, _layoutThickness);
	}

	/**
	 * 数组地图构建器，以指定的数组地图构建一个指定参数的BattleMap专用瓦片数组集合
	 * 
	 * @param map
	 * @param layerIndex
	 * @param effectService
	 * @param skillService
	 * @param layout
	 * @param tickness
	 * @return
	 */
	public BattleTile[][] generateMergedMap(Field2D map, int layerIndex, EffectService effectService,
			SkillService skillService, MapLayoutStyle layout, int tickness) {
		int width = map.getWidth();
		int height = map.getHeight();
		int tileWidth = map.getTileWidth();
		int tileHeight = map.getTileHeight();
		BattleTile[][] finalTiles = new BattleTile[width][height];
		int centerX = width / 2;
		int centerY = height / 2;
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				boolean inLayout = isInLayoutShape(x, y, width, height, centerX, centerY, layout, tickness);
				if (!inLayout) {
					finalTiles[x][y] = null;
					continue;
				}
				int key = getTilePosKey(x, y, layerIndex);
				if (_tilePosToTiles.containsKey(key)) {
					finalTiles[x][y] = _tilePosToTiles.get(key);
					continue;
				}
				int tileId = map.getTileType(x, y);
				BattleTileType tileType = _tileIdToTypeMap.get(tileId);
				if (tileType == null) {
					tileType = BattleTileType.getById(tileId);
				}
				BattleTile tile = new BattleTile(x, y, tileWidth, tileHeight, _isoConfig, tileType, effectService,
						skillService);
				applyTileAnimation(tile, tileId);
				finalTiles[x][y] = tile;
			}
		}
		return finalTiles;
	}

	/**
	 * 自动生成地图时专用的布局器，剪裁生成的地图为指定形状
	 * 
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param cx
	 * @param cy
	 * @param layout
	 * @param thickness
	 * @return
	 */
	private boolean isInLayoutShape(int x, int y, int w, int h, int cx, int cy, MapLayoutStyle layout, int thickness) {
		int dx = MathUtils.abs(x - cx);
		int dy = MathUtils.abs(y - cy);
		int dx2 = 0;
		int dy2 = 0;
		int cut = 0;
		boolean left, right;
		boolean top, bot;
		switch (layout) {
		case FULL:
			return true;
		case OUTER_BORDER:
			for (int i = 0; i < thickness; i++) {
				if (x == i || x == w - 1 - i || y == i || y == h - 1 - i) {
					return true;
				}
			}
			return false;
		case INNER_BORDER:
			int m = thickness;
			boolean inner = x >= m && x < w - m && y >= m && y < h - m;
			boolean edge = (x == m || x == w - 1 - m || y == m || y == h - 1 - m);
			return inner && edge;
		case CENTER_CIRCLE:
			return dx * dx + dy * dy <= thickness * thickness;
		case CENTER_RHOMBUS:
			return dx + dy <= thickness;
		case CENTER_CROSS:
			return (MathUtils.abs(x - cx) <= thickness && y == cy) || (MathUtils.abs(y - cy) <= thickness && x == cx);
		case CENTER_H_LINE:
			return y == cy && MathUtils.abs(x - cx) <= thickness;
		case CENTER_V_LINE:
			return x == cx && MathUtils.abs(y - cy) <= thickness;
		case CENTER_X:
			return MathUtils.abs(dx - dy) <= thickness && (dx + dy) <= thickness * 2;
		case CENTER_TRIANGLE_UP:
			dy2 = cy - y;
			if (dy2 < 0 || dy2 > thickness) {
				return false;
			}
			return MathUtils.abs(x - cx) <= dy2;
		case CENTER_TRIANGLE_DOWN:
			dy2 = y - cy;
			if (dy2 < 0 || dy2 > thickness) {
				return false;
			}
			return MathUtils.abs(x - cx) <= dy2;
		case CENTER_TRIANGLE_LEFT:
			dx2 = cx - x;
			if (dx2 < 0 || dx2 > thickness) {
				return false;
			}
			return MathUtils.abs(y - cy) <= dx2;
		case CENTER_TRIANGLE_RIGHT:
			dx2 = x - cx;
			if (dx2 < 0 || dx2 > thickness) {
				return false;
			}
			return MathUtils.abs(y - cy) <= dx2;
		case CUT_OUTER_EDGE:
			return !(x < thickness || x >= w - thickness || y < thickness || y >= h - thickness);
		case CUT_OUTER_RHOMBUS:
			return (dx + dy) <= (cx + cy) - thickness;
		case CUT_OUTER_SLOPE_4:
			cut = thickness;
			top = y < cut && (MathUtils.abs(x - cx) > (cut - y));
			bot = y >= h - cut && (MathUtils.abs(x - cx) > (cut - (h - 1 - y)));
			left = x < cut && (MathUtils.abs(y - cy) > (cut - x));
			right = x >= w - cut && (MathUtils.abs(y - cy) > (cut - (w - 1 - x)));
			return !(top || bot || left || right);
		case CUT_OUTER_OCTAGON:
			int oc = thickness;
			boolean corner = (x < oc && y < oc) || (x < oc && y >= h - oc) || (x >= w - oc && y < oc)
					|| (x >= w - oc && y >= h - oc);
			return !corner;
		case CUT_OUTER_ROUND_RECT:
			int r = thickness;
			if (x >= r && x < w - r && y >= r && y < h - r) {
				return true;
			}
			boolean tl = (x - r) * (x - r) + (y - r) * (y - r) <= r * r;
			boolean tr = (x - (w - 1 - r)) * (x - (w - 1 - r)) + (y - r) * (y - r) <= r * r;
			boolean bl = (x - r) * (x - r) + (y - (h - 1 - r)) * (y - (h - 1 - r)) <= r * r;
			boolean br = (x - (w - 1 - r)) * (x - (w - 1 - r)) + (y - (h - 1 - r)) * (y - (h - 1 - r)) <= r * r;
			return !(tl || tr || bl || br);
		case CUT_OUTER_DIAGONAL_CORNER:
			int dia = thickness;
			boolean c1 = x + y < dia;
			boolean c2 = (w - 1 - x) + (h - 1 - y) < dia;
			boolean c3 = (w - 1 - x) + y < dia;
			boolean c4 = x + (h - 1 - y) < dia;
			return !(c1 || c2 || c3 || c4);
		case CUT_OUTER_SIDE_SLOPE_LR:
			cut = thickness;
			boolean l = x < cut && dx > (cut - x);
			boolean rv = x >= w - cut && dx > (cut - (w - 1 - x));
			return !l && !rv;
		case CUT_OUTER_SIDE_SLOPE_TB:
			cut = thickness;
			boolean t = y < cut && dy > (cut - y);
			boolean b = y >= h - cut && dy > (cut - (h - 1 - y));
			return !t && !b;
		case CUT_OUTER_HEX:
			return dx <= cx - thickness && dy <= cy - thickness && (dx + dy) <= (cx + cy) - thickness;
		case CUT_OUTER_STAR_EDGE:
			return (dx + dy) <= (cx + cy) - thickness || MathUtils.abs(dx - dy) <= thickness;
		case CUT_OUTER_WAVE_EDGE:
			return !(x < thickness && (y + x) % (thickness + 1) == 0)
					&& !(x >= w - thickness && (y - x) % (thickness + 1) == 0);
		case CUT_OUTER_STEP_EDGE:
			return !((x < thickness || x >= w - thickness) && (y / thickness) % 2 == 0)
					&& !((y < thickness || y >= h - thickness) && (x / thickness) % 2 == 0);
		case CUT_OUTER_LEFT_SLOPE:
			cut = thickness;
			left = x < cut && dx > (cut - x);
			return !left;
		case CUT_OUTER_RIGHT_SLOPE:
			cut = thickness;
			right = x >= w - cut && dx > (cut - (w - 1 - x));
			return !right;
		case CUT_OUTER_TOP_SLOPE:
			cut = thickness;
			top = y < cut && dy > (cut - y);
			return !top;
		case CUT_OUTER_BOTTOM_SLOPE:
			cut = thickness;
			bot = y >= h - cut && dy > (cut - (h - 1 - y));
			return !bot;
		default:
			return true;
		}
	}

	public void registerTileTypeId(int tileId, BattleTileType type) {
		_tileIdToTypeMap.put(tileId, type);
	}

	public void registerTileTypeIdMap(IntMap<BattleTileType> map) {
		if (map != null) {
			_tileIdToTypeMap.putAll(map);
		}
	}

	public void clearTileTypeIdMap() {
		_tileIdToTypeMap.clear();
	}

	public void setTilePos(int x, int y, int layerIndex, BattleTile tile) {
		if (tile == null) {
			return;
		}
		int key = getTilePosKey(x, y, layerIndex);
		_tilePosToTiles.put(key, tile);
	}

	public void removeTilePos(int x, int y, int layerIndex) {
		int key = getTilePosKey(x, y, layerIndex);
		_tilePosToTiles.remove(key);
	}

	public void clearTilePosTiles() {
		_tilePosToTiles.clear();
	}

	private int getTilePosKey(int x, int y, int layer) {
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, x);
		hashCode = LSystem.unite(hashCode, y);
		hashCode = LSystem.unite(hashCode, layer);
		return hashCode;
	}

	protected void sortObjects() {
		_mapObjects.sort(OBJ_COMPARATOR);
	}

	public Vector2f findOffsetScreenXY(float touchX, float touchY) {
		return findOffsetScreenXY(touchX, touchY, 0f, 0f);
	}

	public Vector2f findOffsetScreenXY(float touchX, float touchY, float offsetX, float offsetY) {
		Vector2f pos = findTileXY(touchX, touchY);
		if (pos.x >= 0 && pos.y >= 0) {
			return getTileToScreen(pos.x(), pos.y(), 0, 0, offsetX, offsetY);
		}
		return null;
	}

	public Vector2f findTileXY(float touchX, float touchY) {
		return findTileXY(touchX, touchY, true);
	}

	/**
	 * 转化屏幕触点为游戏瓦片坐标并返回
	 * 
	 * @param touchX
	 * @param touchY
	 * @return
	 */
	public Vector2f findTileXY(float touchX, float touchY, boolean offsetLayer) {
		int tx = MathUtils.floor(offsetXNScalePixel(touchX));
		int ty = MathUtils.floor(offsetYNScalePixel(touchY));
		if (offsetLayer) {
			tx -= MathUtils.ifloor(this._pathFinderLayerOffsetX);
			ty -= MathUtils.ifloor(this._pathFinderLayerOffsetY);
		}
		Vector2f gridPos = ISOUtils.screenToGrid(tx, ty, _isoConfig, _tempPosition);
		try {
			int gx = gridPos.x();
			int gy = gridPos.y();
			if (this._pathFinderTiles != null && gx >= 0 && gy >= 0 && gx < this._pathFinderTiles.length
					&& gy < this._pathFinderTiles[0].length) {
				BattleTile t = this._pathFinderTiles[gx][gy];
				if (t != null && t.hasLayerOffset()) {
					int tx2 = tx - MathUtils.ifloor(t.baseOffsetX);
					int ty2 = ty - MathUtils.ifloor(t.baseOffsetY);
					Vector2f gridPos2 = ISOUtils.screenToGrid(tx2, ty2, _isoConfig, _tempPosition);
					if (gridPos2.x() >= 0 && gridPos2.y() >= 0) {
						return gridPos2;
					}
				}
			}
		} catch (Throwable ex) {
		}
		return gridPos;
	}

	public TArray<PointI> findObjectMovePathToTile(BattleMapObject obj, float touchX, float touchY) {
		if (_pathFinder == null) {
			return null;
		}
		if (obj == null) {
			return null;
		}
		Vector2f pos = findTileXY(touchX, touchY);
		if (pos.x < 0 || pos.y < 0) {
			return null;
		}
		int startGX = obj.getGridX();
		int startGY = obj.getGridY();
		_pathFinder.setFindDirFour(obj.isFindDirFour());
		_pathFinder.setFlying(obj.isFlying());
		TArray<PointI> result = _pathFinder.findPath(startGX, startGY, pos.x(), pos.y());
		return result;
	}

	public void handleCameraMovement(float deltaTime, Direction d) {
		if (d == Direction.LEFT) {
			_pixelOffset.x -= cameraMoveSpeed * deltaTime;
		}
		if (d == Direction.RIGHT) {
			_pixelOffset.x += cameraMoveSpeed * deltaTime;
		}
		if (d == Direction.UP) {
			_pixelOffset.y -= cameraMoveSpeed * deltaTime;
		}
		if (d == Direction.DOWN) {
			_pixelOffset.y += cameraMoveSpeed * deltaTime;
		}
		float maxOffsetX = (_field2d.getWidth() * _isoConfig.tileWidth) - _pixelInWidth;
		float maxOffsetY = (_field2d.getHeight() * _isoConfig.tileHeight / 2) - _pixelInHeight;
		_pixelOffset.x = MathUtils.max(0, MathUtils.min(_pixelOffset.x, maxOffsetX));
		_pixelOffset.y = MathUtils.max(0, MathUtils.min(_pixelOffset.y, maxOffsetY));
	}

	public void clickTile(float touchX, float touchY) {
		BattleTile clickedTile = findTileTouch(touchX, touchY);
		if (clickedTile != null && !_selectionManager._selectedObjects.isEmpty()) {
			clickedTile.isHighlighted = !clickedTile.isHighlighted;
			BattleMapObject selected = _selectionManager._selectedObjects.get(0);
			TArray<PointI> path = _pathFinder.findPath(selected.gridX, selected.gridY, clickedTile.gridX,
					clickedTile.gridY);
			selected.setPath(path);
		}
	}

	public BattleMapObject findObjectPixel(float px, float py) {
		return findObjectPixel(px, py, true);
	}

	public BattleMapObject findObjectPixel(float px, float py, boolean offsetLayer) {
		int touchX = MathUtils.floor(offsetXNScalePixel(px));
		int touchY = MathUtils.floor(offsetYNScalePixel(py));
		if (offsetLayer) {
			touchX -= MathUtils.ifloor(this._pathFinderLayerOffsetX);
			touchY -= MathUtils.ifloor(this._pathFinderLayerOffsetY);
		}
		int size = _mapObjects.size - 1;
		for (int i = size; i > -1; i--) {
			BattleMapObject o = _mapObjects.get(i);
			if (o != null && o.getBounds().contains(touchX, touchY)) {
				return o;
			}
		}
		return null;
	}

	public BattleMapObject findObjectTouch(float touchX, float touchY) {
		Vector2f pos = findTileXY(touchX, touchY);
		return findObjectTile(pos.x(), pos.y());
	}

	public BattleMapObject findObjectTile(int gridX, int gridY) {
		int size = _mapObjects.size - 1;
		for (int i = size; i > -1; i--) {
			BattleMapObject o = _mapObjects.get(i);
			if (o != null && o.isClick(gridX, gridY)) {
				return o;
			}
		}
		return null;
	}

	public BattleTile findTileTouch(float touchX, float touchY) {
		return findTileTouch(touchX, touchY, 0f, 0f);
	}

	/**
	 * 查找触屏位置的瓦片
	 * 
	 * @param touchX
	 * @param touchY
	 * @param tileWidth
	 * @param tileHeight
	 * @return
	 */
	public BattleTile findTileTouch(float touchX, float touchY, float tileWidth, float tileHeight) {
		Vector2f pos = findTileXY(touchX, touchY);
		int tx = pos.x();
		int ty = pos.y();
		if (_field2d.contains(tx, ty)) {
			return getTileFromAllIsoLayers(tx, ty);
		}
		return null;
	}

	/**
	 * 当前地图的最大层宽
	 * 
	 * @return
	 */
	public int getMaxLayerWidth() {
		int maxW = 0;
		for (int i = 0; i < _layers.size; i++) {
			IsoTileLayer layer = _layers.get(i);
			maxW = MathUtils.max(maxW, layer.width);
		}
		return maxW;
	}

	/**
	 * 当前地图的最大层高
	 * 
	 * @return
	 */
	public int getMaxLayerHeight() {
		int maxH = 0;
		for (int i = 0; i < _layers.size; i++) {
			IsoTileLayer layer = _layers.get(i);
			maxH = MathUtils.max(maxH, layer.height);
		}
		return maxH;
	}

	public GameEventBus<Object> getEventBus() {
		return _eventBus;
	}

	public BattlePathFinder getPathFinder() {
		return _pathFinder;
	}

	public void setPathFinder(BattlePathFinder p) {
		this._pathFinder = p;
	}

	public int getObjectCount() {
		return _mapObjects.size;
	}

	public TArray<BattleMapObject> getObjects() {
		return new TArray<BattleMapObject>(_mapObjects);
	}

	public TArray<BattleMapObject> getTeamObjects(int t) {
		final TArray<BattleMapObject> result = new TArray<BattleMapObject>();
		for (int i = _mapObjects.size - 1; i > -1; i--) {
			BattleMapObject o = _mapObjects.get(i);
			if (o != null && o.getTeam() == t) {
				result.add(o);
			}
		}
		return result;
	}

	public TArray<BattleMapObject> getTeamPlayObjects() {
		return getTeamObjects(Team.Player);
	}

	public TArray<BattleMapObject> getTeamEnemyObjects() {
		return getTeamObjects(Team.Enemy);
	}

	public TArray<BattleMapObject> getTeamNpcObjects() {
		return getTeamObjects(Team.Npc);
	}

	public TArray<BattleMapObject> getTeamAllyObjects() {
		return getTeamObjects(Team.Ally);
	}

	public TArray<BattleMapObject> getTeamOtherObjects() {
		return getTeamObjects(Team.Other);
	}

	public BattleMapObject getCameraTarget() {
		return _cameraTarget;
	}

	public void setCameraTarget(BattleMapObject t) {
		this._cameraTarget = t;
	}

	public BattleSelectManager getSelectionManager() {
		return _selectionManager;
	}

	public boolean isDragging() {
		return _dragging;
	}

	public void setDragging(boolean dragging) {
		this._dragging = dragging;
	}

	public int getDragStartX() {
		return _dragStartX;
	}

	public void setDragStartX(int dragStartX) {
		this._dragStartX = dragStartX;
	}

	public int getDragStartY() {
		return _dragStartY;
	}

	public void setDragStartY(int dragStartY) {
		this._dragStartY = dragStartY;
	}

	public IsoConfig getIsoConfig() {
		return _isoConfig;
	}

	public void setIsoConfig(IsoConfig s) {
		this._isoConfig.set(s);
	}

	public float getWidthScale() {
		return _isoConfig.scaleX;
	}

	public void setWidthScale(float widthScale) {
		_isoConfig.scaleX = widthScale;
	}

	public float getHeightScale() {
		return _isoConfig.scaleY;
	}

	public void setHeightScale(float heightScale) {
		_isoConfig.scaleY = heightScale;
	}

	public BattleMap resizeScreen(int width, int height) {
		_pixelInWidth = width;
		_pixelInHeight = height;
		return this;
	}

	public float centerX() {
		return ((getContainerX() + getContainerWidth()) - (getX() + getWidth())) / 2f;
	}

	public float centerY() {
		return ((getContainerY() + getContainerHeight()) - (getY() + getHeight())) / 2f;
	}

	public boolean isContentPositionInBounds(float x, float y) {
		float offX = MathUtils.min(this._pixelOffset.x + _isoConfig.offsetX);
		float offY = MathUtils.min(this._pixelOffset.y + _isoConfig.offsetY);
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

	public BattleMap scrollDown(float distance) {
		if (distance == 0) {
			return this;
		}
		this._pixelOffset.y = (this._pixelOffset.y + _isoConfig.offsetY + distance);
		return this;
	}

	public BattleMap scrollUp(float distance) {
		if (distance == 0) {
			return this;
		}
		this._pixelOffset.y = (this._pixelOffset.y + _isoConfig.offsetY - distance);
		return this;
	}

	public BattleMap scrollLeft(float distance) {
		if (distance == 0) {
			return this;
		}
		this._pixelOffset.x = this._pixelOffset.x + _isoConfig.offsetX - distance;
		return this;
	}

	public BattleMap scrollRight(float distance) {
		if (distance == 0) {
			return this;
		}
		this._pixelOffset.x = this._pixelOffset.x + _isoConfig.offsetX + distance;
		return this;
	}

	public BattleMap scrollLeftUp(float distance) {
		this.scrollUp(distance);
		this.scrollLeft(distance);
		return this;
	}

	public BattleMap scrollRightDown(float distance) {
		this.scrollDown(distance);
		this.scrollRight(distance);
		return this;
	}

	public BattleMap scrollClear() {
		if (!this._pixelOffset.equals(0f, 0f)) {
			this._pixelOffset.set(0, 0);
		}
		return this;
	}

	public BattleMap scroll(float x, float y) {
		return scroll(x, y, 4f);
	}

	public BattleMap scroll(float x, float y, float distance) {
		if (_scrollDrag.x == 0f && _scrollDrag.y == 0f) {
			_scrollDrag.set(x, y);
			return this;
		}
		return scroll(_scrollDrag.x, _scrollDrag.y, x, y, distance);
	}

	public BattleMap scroll(float x1, float y1, float x2, float y2) {
		return scroll(x1, y1, x2, y2, 4f);
	}

	public BattleMap scroll(float x1, float y1, float x2, float y2, float distance) {
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

	public BattleMap setLimit(int[] limit) {
		_field2d.setLimit(limit);
		return this;
	}

	public BattleMap setAllowMove(int[] args) {
		_field2d.setAllowMove(args);
		return this;
	}

	public BattleTile findNearestStrategicPoint(PointI point) {
		for (int i = _strategicPoint.size - 1; i > -1; i--) {
			PointI result = _strategicPoint.get(i);
			if (result != null && result.equals(point)) {
				return getMapTile(point.x, point.y);
			}
		}
		return null;
	}

	public boolean inTileGrid(int px, int py) {
		return _field2d.contains(px, py);
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
		float x = (sx + _pixelOffset.getX() + _isoConfig.offsetX);
		float y = (sy + _pixelOffset.getY() + _isoConfig.offsetY);
		Vector2f tileCoordinates = pixelsToTiles(x, y);
		return getTileID(MathUtils.round(tileCoordinates.getX()), MathUtils.round(tileCoordinates.getY()));
	}

	@Override
	public int[][] getMap() {
		return _field2d.getMap();
	}

	public BattleTile[][] getTileMap() {
		return _mapTiles;
	}

	public boolean isValid(int x, int y) {
		return this._field2d.inside(x, y);
	}

	public boolean isValidTile(int x, int y) {
		return x >= 0 && y >= 0 && x < _mapTiles.length && y < _mapTiles[0].length;
	}

	public BattleMap replaceType(int oldid, int newid) {
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

	public BattleMap setTileID(int x, int y, int id) {
		if (x >= 0 && x < _field2d.getWidth() && y >= 0 && y < _field2d.getHeight()) {
			_field2d.setTileType(x, y, id);
		}
		return this;
	}

	public Vector2f pixelsToTiles(float x, float y) {
		float xprime = x / _isoConfig.scaleX / _field2d.getTileWidth() - 1;
		float yprime = y / _isoConfig.scaleY / _field2d.getTileHeight() - 1;
		return new Vector2f(xprime, yprime);
	}

	@Override
	public int tilesToPixelsX(float x) {
		return MathUtils.floor(_field2d.tilesToWidthPixels(x) * _isoConfig.scaleX);
	}

	@Override
	public int tilesToPixelsY(float y) {
		return MathUtils.floor(_field2d.tilesToHeightPixels(y) * _isoConfig.scaleY);
	}

	@Override
	public int pixelsToTilesWidth(float x) {
		return _field2d.pixelsToTilesWidth(x / _isoConfig.scaleX);
	}

	@Override
	public int pixelsToTilesHeight(float y) {
		return _field2d.pixelsToTilesHeight(y / _isoConfig.scaleY);
	}

	public PointI pixelsToTileMap(float x, float y) {
		int tileX = pixelsToTilesWidth(x);
		int tileY = pixelsToTilesHeight(y);
		return new PointI(tileX, tileY);
	}

	public PointI tilePixels(float x, float y) {
		int newX = getPixelX(x);
		int newY = getPixelY(y);
		return new PointI(newX, newY);
	}

	/**
	 * 转化地图到屏幕像素(不考虑地图滚动)
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public PointI tileMapToPixels(float x, float y) {
		int tileX = tilesToPixelsX(x);
		int tileY = tilesToPixelsY(y);
		return new PointI(tileX, tileY);
	}

	/**
	 * 转化地图到屏幕像素(考虑地图滚动)
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public PointI tileMapToScrollTilePixels(float x, float y) {
		int newX = toTileScrollPixelX(x);
		int newY = toTileScrollPixelX(y);
		return new PointI(newX, newY);
	}

	/**
	 * 转化屏幕像素到地图(考虑地图滚动)
	 * 
	 * @param x
	 * @param y
	 * @return
	 */
	public PointI pixelsToScrollTileMap(float x, float y) {
		int tileX = toPixelScrollTileX(x);
		int tileY = toPixelScrollTileY(y);
		return new PointI(tileX, tileY);
	}

	/**
	 * 转换坐标为像素坐标
	 *
	 * @param x
	 * @param y
	 * @return
	 */
	public Vector2f tilesToPixels(float x, float y) {
		float xprime = x * _field2d.getTileWidth() - _pixelOffset.getX() + _isoConfig.offsetX;
		float yprime = y * _field2d.getTileHeight() - _pixelOffset.getY() + _isoConfig.offsetY;
		return new Vector2f(xprime, yprime);
	}

	public BattleMap switchMap(MapSwitchMaker ms) {
		_field2d.switchMap(ms);
		return this;
	}

	/**
	 * 地图居中偏移
	 *
	 * @return
	 */
	public BattleMap centerOffset() {
		this._pixelOffset.set(centerX(), centerY());
		return this;
	}

	/**
	 * 设定偏移量
	 *
	 * @param x
	 * @param y
	 */
	public BattleMap setOffset(float x, float y) {
		this._pixelOffset.set(x, y);
		return this;
	}

	/**
	 * 设定偏移量
	 *
	 * @param offset
	 */
	@Override
	public BattleMap setOffset(Vector2f offset) {
		this._pixelOffset.set(offset);
		return this;
	}

	/**
	 * 获得瓦片位置
	 *
	 * @return
	 */
	@Override
	public Vector2f getOffset() {
		return _pixelOffset;
	}

	/**
	 * 设定跟随角色偏移量
	 *
	 * @param x
	 * @param y
	 */
	public BattleMap setFollowOffset(float x, float y) {
		this._followOffset.set(x, y);
		return this;
	}

	/**
	 * 设定跟随角色偏移量
	 *
	 * @param offset
	 */
	public BattleMap setFollowOffset(Vector2f offset) {
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
		return (_field2d.getViewHeight() * _isoConfig.scaleY) - _fixedHeightOffset;
	}

	@Override
	public float getWidth() {
		return (_field2d.getViewHeight() * _isoConfig.scaleX) - _fixedWidthOffset;
	}

	@Override
	public int getRow() {
		return _field2d.getWidth();
	}

	@Override
	public int getCol() {
		return _field2d.getHeight();
	}

	public BattleMap setMapValues(int v) {
		_field2d.setValues(v);
		return this;
	}

	public Field2D getNewField2D() {
		return new Field2D(_field2d);
	}

	public DrawListener<BattleMap> getListener() {
		return _drawListener;
	}

	public BattleMap setListener(DrawListener<BattleMap> l) {
		this._drawListener = l;
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
	public RectBox getCollisionBox() {
		return getRect(x() + _pixelOffset.x + _isoConfig.offsetX, y() + _pixelOffset.y + _isoConfig.offsetY,
				_field2d.getTileWidth() * _field2d.getWidth(), _field2d.getTileHeight() * _field2d.getHeight());
	}

	@Override
	public LTexture getBitmap() {
		return _background;
	}

	public Vector2f getTileToScreen(int gx, int gy, int cw, int ch, float offsetX, float offsetY) {
		return ISOUtils.getTileToScreen(_isoConfig, gx, gy, cw, ch, offsetX, offsetY, _gxTempResult, _isoTempResult);
	}

	public Vector2f getScreenToTile(float px, float py, int cw, int ch, float offsetX, float offsetY) {
		return ISOUtils.getScreenToTile(_isoConfig, px, py, cw, ch, offsetX, offsetY, _gxTempResult);
	}

	public BattleMap startAnimation() {
		_playAnimation = true;
		return this;
	}

	public BattleMap stopAnimation() {
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

	public BattleMap followActionObject() {
		if (_follow != null) {
			float offsetX = limitOffsetX(_follow.getX());
			float offsetY = limitOffsetY(_follow.getY());
			if (offsetX != 0 || offsetY != 0) {
				setOffset(offsetX, offsetY);
				_field2d.setOffset(_pixelOffset);
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
		return _isoConfig.scaleX;
	}

	@Override
	public float getScaleY() {
		return _isoConfig.scaleY;
	}

	public void setScale(float scale) {
		setScale(scale, scale);
	}

	@Override
	public void setScale(float sx, float sy) {
		_isoConfig.setScale(sx, sy);
	}

	@Override
	public BattleMap setSize(float w, float h) {
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

	public BattleMap setFollow(ActionBind follow) {
		this._follow = follow;
		return this;
	}

	public BattleMap followDonot() {
		return setFollow(null);
	}

	public BattleMap followAction(ActionBind follow) {
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
		return MathUtils.iceil(x - _pixelOffset.x - _objectLocation.x - _isoConfig.offsetX);
	}

	public int offsetYNScalePixel(float y) {
		return MathUtils.iceil(y - _pixelOffset.y - _objectLocation.y - _isoConfig.offsetY);
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
		return MathUtils.iceil((x - _objectLocation.x) / _isoConfig.scaleX);
	}

	public int getPixelY(float y) {
		return MathUtils.iceil((y - _objectLocation.y) / _isoConfig.scaleY);
	}

	@Override
	public int offsetXPixel(float x) {
		return MathUtils.iceil((x - _pixelOffset.x - _objectLocation.x - _isoConfig.offsetX) / _isoConfig.scaleX);
	}

	@Override
	public int offsetYPixel(float y) {
		return MathUtils.iceil((y - _pixelOffset.y - _objectLocation.y - _isoConfig.offsetY) / _isoConfig.scaleY);
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

	@Override
	public float getScreenPixelX(float x) {
		return (x + _objectLocation.x + _pixelOffset.x + _isoConfig.offsetX) / _isoConfig.scaleX;
	}

	@Override
	public float getScreenPixelY(float y) {
		return (y + _objectLocation.y + _pixelOffset.y + _isoConfig.offsetY) / _isoConfig.scaleY;
	}

	@Override
	public ISprite getObject(float x, float y) {
		ISprite sprite = null;
		if (_mapSprites != null) {
			sprite = _mapSprites.find(MathUtils.ifloor(x), MathUtils.ifloor(y));
		}
		if (sprite == null && _screenSprites != null) {
			sprite = _screenSprites.find(MathUtils.ifloor(x), MathUtils.ifloor(y));
		}
		if (sprite == null && getScreen() != null) {
			sprite = getScreen().getSprites().find(MathUtils.ifloor(x), MathUtils.ifloor(y));
		}
		if (sprite == null && getScreen() != null && (getScreen() instanceof Stage)) {
			Stage stage = (Stage) getScreen();
			sprite = stage.findObject(MathUtils.ifloor(x), MathUtils.ifloor(y));
		}
		return sprite;
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
		return (x + _objectLocation.x + _pixelOffset.x + _isoConfig.offsetX);
	}

	public float getScreenNScalePixelY(float y) {
		return (y + _objectLocation.y + _pixelOffset.y + _isoConfig.offsetY);
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

	public boolean isRoll() {
		return _roll;
	}

	public BattleMap setRoll(boolean roll) {
		this._roll = roll;
		return this;
	}

	public int getPixelInWidth() {
		return _pixelInWidth;
	}

	public int getPixelInHeight() {
		return _pixelInHeight;
	}

	public LTexture getBackground() {
		return this._background;
	}

	public BattleMap setBackground(LTexture bg) {
		this._background = bg;
		return this;
	}

	public BattleMap setBackground(String path) {
		if (StringUtils.isEmpty(path)) {
			return this;
		}
		return this.setBackground(LTextures.loadTexture(path));
	}

	public BattleMap setBackground(String path, float w, float h) {
		if (StringUtils.isEmpty(path)) {
			return this;
		}
		return this.setBackground(LTextures.loadTexture(path).scale(w, h));
	}

	/**
	 * 添加层级(除非再次设定，否则默认寻径操作只在渲染层级最高的显示层级进行)
	 * 
	 * @param tiles
	 * @param name
	 * @return
	 */
	public BattleMap addIsoLayer(BattleTile[][] tiles, String name) {
		IsoTileLayer layer = new IsoTileLayer(tiles, name, _layers.size);
		_layers.add(layer);
		int idx = _layers.size - 1;
		for (int gx = 0; gx < layer.width; gx++) {
			for (int gy = 0; gy < layer.height; gy++) {
				try {
					BattleTile t = layer.tiles[gx][gy];
					if (t != null) {
						t.setLayerIndex(idx);
					}
				} catch (Throwable ex) {
				}
			}
		}
		this._mapTiles = tiles;
		this._pathFinderLayerIndex = MathUtils.max(0, idx);
		rebuildPathFinderUsingSelectedLayer();
		ensureLayerCacheSize();
		int newIndex = _layers.size - 1;
		if (newIndex >= 0 && newIndex < _layerRenderOffsetDirty.length) {
			_layerRenderOffsetDirty.set(newIndex, true);
		}
		if (_layerChangeListener != null) {
			_layerChangeListener.onLayerAdded(_layers.size - 1);
		}
		_layerSortDirty = true;
		return this;
	}

	public BattleMap setDefaultIsoLayerStepOffset(float ox, float oy) {
		this._defaultLayerStepOffsetX = ox;
		this._defaultLayerStepOffsetY = oy;
		markAllLayerOffsetsDirty();
		return this;
	}

	public BattleMap setTileIsoLayerOffset(int layerIndex, int gx, int gy, float ox, float oy) {
		if (layerIndex < 0 || layerIndex >= _layers.size) {
			return this;
		}
		IsoTileLayer layer = _layers.get(layerIndex);
		if (layer == null || layer.tiles == null) {
			return this;
		}
		if (gx >= 0 && gx < layer.width && gy >= 0 && gy < layer.height) {
			BattleTile t = layer.tiles[gx][gy];
			if (t != null) {
				t.setBaseLayerOffset(ox, oy);
			}
		}
		return this;
	}

	public BattleMap setIsoTileHeight(int layerIndex, int gx, int gy, int height) {
		if (layerIndex < 0 || layerIndex >= _layers.size) {
			return this;
		}
		IsoTileLayer layer = _layers.get(layerIndex);
		if (layer == null || layer.tiles == null) {
			return this;
		}
		if (gx >= 0 && gx < layer.width && gy >= 0 && gy < layer.height) {
			BattleTile t = layer.tiles[gx][gy];
			if (t != null) {
				t.setLayerHeight(height);
			}
		}
		return this;
	}

	public BattleMap setPathFinderLayerIndex(int layerIndex) {
		this._pathFinderLayerIndex = layerIndex;
		rebuildPathFinderUsingSelectedLayer();
		return this;
	}

	public int getPathFinderLayerIndex() {
		return this._pathFinderLayerIndex;
	}

	public void rebuildPathFinderUsingSelectedLayer() {
		if (this._layers.size == 0) {
			if (this._mapTiles != null) {
				this._pathFinderTiles = this._mapTiles;
				this._pathFinderLayerOffsetX = 0f;
				this._pathFinderLayerOffsetY = 0f;
				this._pathFinder = new BattlePathFinder(this._pathResultBus, this._pathFinderTiles,
						this._field2d.getWidth(), this._field2d.getHeight());
			} else {
				this._pathFinder = null;
				this._pathFinderTiles = null;
				this._pathFinderLayerOffsetX = 0f;
				this._pathFinderLayerOffsetY = 0f;
			}
			return;
		}
		int useIndex = this._pathFinderLayerIndex;
		if (useIndex < 0 || useIndex >= this._layers.size) {
			useIndex = _layers.size - 1;
		}
		IsoTileLayer chosen = this._layers.get(useIndex);
		if (chosen != null && chosen.tiles != null) {
			this._pathFinderTiles = chosen.tiles;
			PointF totalOffset = calculateLayerOffset(useIndex, null);
			this._pathFinderLayerOffsetX = chosen.offsetX + totalOffset.x;
			this._pathFinderLayerOffsetY = chosen.offsetY + totalOffset.y;
			this._pathFinder = new BattlePathFinder(this._pathResultBus, this._pathFinderTiles, chosen.width,
					chosen.height);
		} else {
			if (this._mapTiles != null) {
				this._pathFinderTiles = this._mapTiles;
				this._pathFinderLayerOffsetX = 0f;
				this._pathFinderLayerOffsetY = 0f;
				this._pathFinder = new BattlePathFinder(this._pathResultBus, this._pathFinderTiles,
						this._field2d.getWidth(), this._field2d.getHeight());
			} else {
				this._pathFinder = null;
				this._pathFinderTiles = null;
				this._pathFinderLayerOffsetX = 0f;
				this._pathFinderLayerOffsetY = 0f;
			}
		}
		ensureLayerCacheSize();
		markAllLayerOffsetsDirty();
	}

	public int getIsoLayerCount() {
		return _layers.size;
	}

	public void setIsoLayerHeight(int h) {
		this.isolayerHeight = h;
	}

	public int getIsoLayerHeight() {
		return this.isolayerHeight;
	}

	public void setIsoLayerOffset(float ox, float oy) {
		this.isolayerOffsetX = ox;
		this.isolayerOffsetY = oy;
	}

	public boolean hasIsoLayerOffset() {
		return !MathUtils.equal(isolayerOffsetX, -1f) && !MathUtils.equal(isolayerOffsetY, -1f);
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

	public void addStrategicPoint(int gx, int gy) {
		addStrategicPoint(new PointI(gx, gy));
	}

	public void addStrategicPoint(PointI p) {
		if (p == null) {
			return;
		}
		_strategicPoint.add(p);
	}

	public void removeStrategicPoint(PointI p) {
		if (p == null) {
			return;
		}
		_strategicPoint.remove(p);
	}

	public void clearStrategicPoint() {
		_strategicPoint.clear();
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

	public Sprites sortSprites() {
		if (_mapSprites != null) {
			_mapSprites.sortSprites();
		}
		return _mapSprites;
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

	public boolean isUpdateBrightness() {
		return _updateBrightness;
	}

	public void setUpdateBrightness(boolean u) {
		this._updateBrightness = u;
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
	public BattleMap triggerCollision(SpriteCollisionListener sc) {
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

	public ResizeListener<BattleMap> getResizeListener() {
		return _resizeListener;
	}

	public BattleMap setResizeListener(ResizeListener<BattleMap> listener) {
		this._resizeListener = listener;
		return this;
	}

	public BattleSkill getGlobalSkill() {
		return _defaultGlobalSkill;
	}

	public BattleMap setOffsetX(float sx) {
		this._pixelOffset.setX(sx);
		return this;
	}

	public BattleMap setOffsetY(float sy) {
		this._pixelOffset.setY(sy);
		return this;
	}

	public boolean isHideAllTile() {
		return _hideAllTile;
	}

	public void setHideAllTile(boolean hideAllTile) {
		this._hideAllTile = hideAllTile;
	}

	public float getBackgroundOffsetX() {
		return _backgroundOffset.x;
	}

	public float getBackgroundOffsetY() {
		return _backgroundOffset.y;
	}

	public BattleMap setBackgroundOffset(XY pos) {
		_backgroundOffset.set(pos);
		return this;
	}

	public BattleMap setBackgroundOffset(float x, float y) {
		_backgroundOffset.set(x, y);
		return this;
	}

	public float getBackgroundSizeX() {
		return _backgroundSize.x;
	}

	public float getBackgroundSizeY() {
		return _backgroundSize.y;
	}

	public BattleMap setBackgroundSize(XY pos) {
		_backgroundSize.set(pos);
		return this;
	}

	public BattleMap setBackgroundSize(float x, float y) {
		_backgroundSize.set(x, y);
		return this;
	}

	@Override
	public float getOffsetX() {
		return _pixelOffset.x;
	}

	@Override
	public float getOffsetY() {
		return _pixelOffset.y;
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

	/**
	 * 关闭所有纹理资源
	 */
	public void closeRes() {
		if (this._layers != null) {
			for (int li = 0; li < this._layers.size; li++) {
				IsoTileLayer layer = this._layers.get(li);
				if (layer == null || layer.tiles == null) {
					continue;
				}
				for (int x = 0; x < layer.width; x++) {
					for (int y = 0; y < layer.height; y++) {
						try {
							BattleTile t = layer.tiles[x][y];
							if (t != null) {
								try {
									t.close();
								} catch (Throwable ex) {
								}
								layer.tiles[x][y] = null;
							}
						} catch (Throwable ex) {
						}
					}
				}
			}
			this._layers.clear();
		}
		if (this._mapTiles != null) {
			for (int x = 0; x < this._mapTiles.length; x++) {
				BattleTile[] col = this._mapTiles[x];
				if (col == null) {
					continue;
				}
				for (int y = 0; y < col.length; y++) {
					try {
						BattleTile t = col[y];
						if (t != null) {
							try {
								t.close();
							} catch (Throwable ex) {
							}
							col[y] = null;
						}
					} catch (Throwable ex) {
					}
				}
				this._mapTiles[x] = null;
			}
			this._mapTiles = null;
		}
		try {
			if (this._mapSprites != null) {
				try {
					this._mapSprites.close();
				} catch (Throwable ex) {
				}
			}
		} catch (Throwable ex) {
		}
		try {
			if (this._background != null) {
				try {
					this._background.close();
				} catch (Throwable ex) {
				}
				this._background = null;
			}
		} catch (Throwable ex) {
		}
		try {
			this._pathFinder = null;
		} catch (Throwable ex) {
		}
		try {
			this._selectionManager = null;
		} catch (Throwable ex) {
		}
		try {
			this._eventBus = null;
		} catch (Throwable ex) {
		}
		try {
			if (this._mapObjects != null) {
				for (int i = 0; i < _mapObjects.size; i++) {
					BattleMapObject o = _mapObjects.get(i);
					if (o != null) {
						o.close();
					}
				}
				this._mapObjects.clear();
			}
		} catch (Throwable ex) {
		}
		this._pathFinderLayerIndex = -1;
		this._pathFinderTiles = null;
		this._pathFinder = null;
	}

	@Override
	public String toString() {
		return _field2d.toString();
	}

	@Override
	protected void _onDestroy() {
		closeRes();
		_visible = false;
		_playAnimation = false;
		_roll = false;
		_strategicPoint.clear();
		_tileIdToTypeMap.clear();
		_tilePosToTiles.clear();
		_defaultGlobalSkill.close();
		_resizeListener = null;
		_collSpriteListener = null;
		removeActionEvents(this);
	}

}
