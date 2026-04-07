package org.test;

import loon.Stage;
import loon.canvas.LColor;
import loon.canvas.Path2D;

public class Path2DTest extends Stage {

	@Override
	public void create() {
		Path2D path = new Path2D();
		// path.drawGrid(115, 155, 75,66,4,8);
		// path.drawCircle(155, 155, 125) ;
		// path.drawRadar(155, 155, 125, 6, 4);
		// 产生三国风格能力雷达图
		path.drawSanguoAbilityRound(150, 150, // 中心坐标
				100, // 半径大小
				1, // 1层网格（内圈矩形代表能力值，外围圆代表最大值）
				92, // 统帅
				93, // 武力
				72, // 智力
				59, // 政治
				92 // 魅力
		);
		drawable((g, x, y) -> {
			path.strokes(g, x, y,LColor.red);
			//path.fills(g, x, y,LColor.red);
		});

	}

}
