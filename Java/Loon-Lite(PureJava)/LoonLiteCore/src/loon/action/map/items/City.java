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

import loon.LRelease;
import loon.LSystem;
import loon.LTexture;
import loon.action.collision.CollisionHelper;
import loon.action.sprite.Animation;
import loon.canvas.LColor;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.MathUtils;
import loon.utils.TimeUtils;

public class City implements LRelease {

	private final String id;

	private String name;

	private boolean visible = true;

	public float lat, lon;

	public float radius = LSystem.LAYER_TILE_SIZE / 2;

	public final Vector2f screenPos = new Vector2f();

	public final Vector2f screenSize = new Vector2f();

	public int priority = 0;

	public final LColor color = new LColor();

	private Animation iconAnimation;

	public City(String name, float lat, float lon, Animation icon) {
		this("city_" + TimeUtils.millis() + "_" + MathUtils.random(0, Integer.MAX_VALUE), name, lat, lon, icon,
				LSystem.LAYER_TILE_SIZE / 2);
	}

	public City(String name, float lat, float lon) {
		this("city_" + TimeUtils.millis() + "_" + MathUtils.random(0, Integer.MAX_VALUE), name, lat, lon,
				(Animation) null, LSystem.LAYER_TILE_SIZE / 2);
	}

	public City(String id, String name, float lat, float lon) {
		this(id, name, lat, lon, (Animation) null, LSystem.LAYER_TILE_SIZE / 2);
	}

	public City(String id, String name, float lat, float lon, LTexture icon, float radius) {
		this(id, name, lat, lon, Animation.getDefaultAnimation(icon), radius);
	}

	public City(String id, String name, float lat, float lon, Animation icon, float radius) {
		this.id = id;
		this.name = name;
		this.lat = lat;
		this.lon = lon;
		this.iconAnimation = icon;
		this.visible = true;
		this.setRadius(radius);
	}

	public void update(float delta) {
		if (!visible || iconAnimation == null) {
			return;
		}
		iconAnimation.update(delta);
	}

	public void draw(GLEx g, float offsetX, float offsetY) {
		if (!visible || iconAnimation == null) {
			return;
		}
		if (screenSize.isEmpty()) {
			float x = screenPos.x - iconAnimation.getWidth() / 2f;
			float y = screenPos.y - iconAnimation.getHeight() / 2f;
			g.draw(iconAnimation.getSpriteImage(), offsetX + x, offsetY + y, color);
		} else {
			float x = screenPos.x - screenSize.x / 2f;
			float y = screenPos.y - screenSize.y / 2f;
			g.draw(iconAnimation.getSpriteImage(), offsetX + x, offsetY + y, screenSize.x, screenSize.y, color);
		}
	}

	public boolean isVisible() {
		return visible;
	}

	public City setVisible(boolean v) {
		visible = v;
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public float getLat() {
		return lat;
	}

	public void setLat(float lat) {
		this.lat = lat;
	}

	public float getLon() {
		return lon;
	}

	public void setLon(float lon) {
		this.lon = lon;
	}

	public Animation getIcon() {
		return iconAnimation;
	}

	public void setIcon(Animation icon) {
		this.iconAnimation = icon;
	}

	public Vector2f getScreenPos() {
		return screenPos;
	}

	public void setScreenPos(float x, float y) {
		this.screenPos.set(x, y);
	}

	public void setScreenPos(Vector2f screenPos) {
		this.screenPos.set(screenPos);
	}

	public Vector2f getScreenSize() {
		return screenSize;
	}

	public void setScreenSize(float w, float h) {
		this.screenSize.set(w, h);
	}

	public void setScreenSize(Vector2f screenSize) {
		this.screenSize.set(screenSize);
	}

	public float getRadius() {
		return radius;
	}

	public City setRadius(float radius) {
		this.radius = radius;
		this.setScreenSize(radius * 2, radius * 2);
		return this;
	}

	public int getPriority() {
		return priority;
	}

	public City setPriority(int priority) {
		this.priority = priority;
		return this;
	}

	public String getId() {
		return id;
	}

	public City setLatLon(float lat, float lon) {
		this.lat = lat;
		this.lon = lon;
		return this;
	}

	public void setScreenPosition(float screenX, float screenY) {
		this.screenPos.set(screenX, screenY);
	}

	public boolean intersect(float x, float y) {
		return CollisionHelper.checkPointvsAABB(x, y, screenPos.x - screenSize.x, screenPos.y - screenSize.y,
				screenSize.x, screenSize.y);
	}

	public boolean intersect(float x, float y, float w, float h) {
		return CollisionHelper.checkPointvsAABB(x, y, screenPos.x - w, screenPos.y - h, w, h);
	}

	@Override
	public String toString() {
		return id + "," + name;
	}

	@Override
	public void close() {
		if (iconAnimation != null) {
			iconAnimation.close();
			iconAnimation = null;
		}
	}

}
