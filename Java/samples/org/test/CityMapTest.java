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
package org.test;

import loon.LTexture;
import loon.LTextures;
import loon.Stage;
import loon.action.map.CityMap;
import loon.action.map.CityMap.Edge;
import loon.action.map.CityMap.EdgeType;
import loon.action.map.items.City;
import loon.canvas.LColor;

public class CityMapTest extends Stage {

	@Override
	public void create() {
		LTexture icon = LTextures.loadTexture("ball.png");

		// 设定地图大小16x16，单独瓦片大小32x32
		// (这个城市地图模块基本和隔壁某三国志1X的城市布局系统功能一致，当然，没有他的分区之类细节功能，但布局功能上一样)
		CityMap map = new CityMap(16, 16, 32, 32, 0);
        // 使用虚线显示链接路径
		map.setDefaultDash(true);
		// 在指定xy坐标位置添加城市
		City luoyang = map.addCity("洛阳", 11, 2, icon);
		City changan = map.addCity("长安", 4, 2, icon);
		City xuchang = map.addCity("许昌", 10, 7, icon);
		City wan = map.addCity("宛", 6, 6, icon);
		City xinye = map.addCity("新野", 4, 8, icon);

		// 设置默认的链接线颜色
		// map.setDefaultEdgeColorFrom(LColor.red);
		// map.setDefaultEdgeColorTo(LColor.blue);
		// 设置默认链接方式为崎岖折线
		// map.setDefaultEdgeType(EdgeType.ROUGH_TERRAIN);
		// 是否缓动连线动画
		// map.setEdgeAnimationEnabled(false);
		// 构建一组相互链接的城市（城市往返两次不是写错了，而是会出现两个链接线，象征可往返，当然，不这样用一根链接线也行，不强迫）
		map.connectPairs(luoyang, changan, changan, luoyang, changan, wan, luoyang, wan, xuchang, luoyang, xuchang, wan);
		// 构建两个单独链接的城市
		map.connectCities(wan, xinye);
		// 设定指定城市半径大小(例如洛阳和长安是大城，可以设置大一些)
		map.setCitiesRadius(32, luoyang, changan);
		// 许昌规模较大
		map.setCitiesRadius(24, xuchang);
		// 设定全部城市统一半径大小
		// map.setCitiesRadius(16);
		// 设定全部城市统一大小
		// map.setCitiesSize(32, 32);
		// 获得指定平面坐标指向的城市
		System.out.println(map.getCityByOrthogonalPos(4, 8));
		add(map);
		drag((x, y) -> {
			map.scroll(x, y);
		});
		up((x, y) -> {
			// 向指定城市行军
			// map.playMarchAnimation(luoyang, changan);
			// 以像素坐标获得选中城市
			City city = map.getCityByPixelToWorldPos(x, y);
			if(city != null) {
				// 选中一个城市
				map.selectCity(city);
			}
		});


		/*
		// 单独设置城市参数
		City c1 = new City("c1", "城A", 1, 0, icon, 32f);
		City c2 = new City("c2", "城B", 4, 2, icon, 32f);
		City c3 = new City("c3", "城C", 4, 5, icon, 32f);
		City c4 = new City("c4", "城D", 7, 4, icon, 32f);
		City c5 = new City("c5", "城E", 6, 9, icon, 32f);
		map.addCity(c1);
		map.addCity(c2);
		map.addCity(c3);
		map.addCity(c4);
		map.addCity(c5);

		// 单独设置边线链接设置
		Edge e1 = new Edge("e1", c1, c2);
		e1.colorFrom = LColor.cyan;
		e1.colorTo = LColor.blue;
		e1.width = 4f;
		e1.animationDuration = 3f;
		e1.animationStart = 0f;
		e1.type = EdgeType.ASTAR_PATH;
		map.addEdge(e1);
		Edge e2 = new Edge("e2", c2, c3);
		e2.colorFrom = LColor.orange;
		e2.colorTo = LColor.red;
		e2.width = 3f;
		e2.animationDuration = 2f;
		e2.animationStart = 0.5f;
		e2.type = EdgeType.ASTAR_PATH;
		map.addEdge(e2);
		Edge e3 = new Edge("e3", c1, c3);
		e3.colorFrom = LColor.green;
		e3.colorTo = LColor.blue;
		e3.width = 3.5f;
		e3.animationDuration = 2.5f;
		e3.animationStart = 0.2f;
		e3.type = EdgeType.ASTAR_PATH;
		map.addEdge(e3);
		
		add(map);*/
	}

}
