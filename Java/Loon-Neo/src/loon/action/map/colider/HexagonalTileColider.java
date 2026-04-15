/**
 * Copyright 2014
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
 * @version 0.4.1
 */
package loon.action.map.colider;

import loon.action.map.Direction;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.TArray;

/**
 * 六边形瓦片碰撞器
 */
public class HexagonalTileColider extends TileColider {

	public HexagonalTileColider(int tileWidth, int tileHeight) {
		super(tileWidth, tileHeight);
	}

	@Override
	public boolean colideTile(Tile tile, int mx, int my, int offsetX, int offsetY) {
		return colideHexagonal(tile, mx, my, offsetX, offsetY);
	}

	private boolean colideHexagonal(Tile tile, int px, int py, int offsetX, int offsetY) {
		int localX = px - tile.getX() - offsetX;
		int localY = py - tile.getY() - offsetY;

		int thirdHeight = tile.getHeight() / 3;
		int halfWidth = tile.getWidth() / 2;

		if (localX > halfWidth * 3) {
			localX = halfWidth - (localX - halfWidth * 3);
		} else if (localX > halfWidth) {
			return py >= tile.getY() && py <= tile.getY() + tile.getHeight();
		}

		return !(localY > thirdHeight + 1 + (2 * localX) || localY < thirdHeight - 1 - (2 * localX));
	}

	public boolean colideRectangle(Tile tile, int rectX, int rectY, int rectWidth, int rectHeight, int offsetX,
			int offsetY) {
		return colideHexagonal(tile, rectX, rectY, offsetX, offsetY)
				|| colideHexagonal(tile, rectX + rectWidth, rectY, offsetX, offsetY)
				|| colideHexagonal(tile, rectX, rectY + rectHeight, offsetX, offsetY)
				|| colideHexagonal(tile, rectX + rectWidth, rectY + rectHeight, offsetX, offsetY);
	}

	public Direction getCollisionDirection(Tile tile, int rectX, int rectY, int rectWidth, int rectHeight, int offsetX,
			int offsetY) {
		if (!colideRectangle(tile, rectX, rectY, rectWidth, rectHeight, offsetX, offsetY)) {
			return Direction.NONE;
		}

		int centerX = rectX + rectWidth / 2;
		int centerY = rectY + rectHeight / 2;

		int tileCenterX = tile.getX() + tile.getWidth() / 2;
		int tileCenterY = tile.getY() + tile.getHeight() / 2;

		int dx = centerX - tileCenterX;
		int dy = centerY - tileCenterY;

		return Direction.fromDelta(MathUtils.ifloor(MathUtils.signum(dx)), MathUtils.ifloor(MathUtils.signum(dy)));
	}

	public int[] getCollisionCorrection(Tile tile, int rectX, int rectY, int rectWidth, int rectHeight, int offsetX,
			int offsetY) {
		if (!colideRectangle(tile, rectX, rectY, rectWidth, rectHeight, offsetX, offsetY)) {
			return new int[] { rectX, rectY };
		}

		Direction direction = getCollisionDirection(tile, rectX, rectY, rectWidth, rectHeight, offsetX, offsetY);

		int correctedX = rectX;
		int correctedY = rectY;

		if (direction == Direction.LEFT) {
			correctedX = tile.getX() - rectWidth + offsetX;
		} else if (direction == Direction.RIGHT) {
			correctedX = tile.getX() + tile.getWidth() + offsetX;
		} else if (direction == Direction.UP) {
			correctedY = tile.getY() - rectHeight + offsetY;
		} else if (direction == Direction.DOWN) {
			correctedY = tile.getY() + tile.getHeight() + offsetY;
		}

		return new int[] { correctedX, correctedY };
	}

	public ObjectMap<Tile, Direction> batchCollision(TArray<Tile> tiles, int rectX, int rectY, int rectWidth,
			int rectHeight, int offsetX, int offsetY) {
		ObjectMap<Tile, Direction> collisions = new ObjectMap<Tile, Direction>();
		for (Tile tile : tiles) {
			if (colideRectangle(tile, rectX, rectY, rectWidth, rectHeight, offsetX, offsetY)) {
				Direction dir = getCollisionDirection(tile, rectX, rectY, rectWidth, rectHeight, offsetX, offsetY);
				collisions.put(tile, dir);
			}
		}
		return collisions;
	}
}
