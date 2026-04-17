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

import loon.Stage;
import loon.action.map.Config;
import loon.action.sprite.AnimatedEntity;
import loon.action.sprite.AnimatedEntity.PlayIndex;
import loon.action.sprite.effect.AfterImageEffect;
import loon.canvas.LColor;
import loon.component.LClickButton;
import loon.utils.MathUtils;

public class AfterImageTest extends Stage {

	@Override
	public void create() {

		// 构建残像图片特效.默认向右移动,图片为ball.png,初始位置60,160,大小32x32,残像数量9
		AfterImageEffect effect = new AfterImageEffect(Config.TRIGHT, "ball.png", 60, 160, 32, 32, 7);
		/*for(int i=0;i<12;i++) {
			effect.addAfterObject(MathUtils.random(0,480), MathUtils.random(0,320));
		}
		effect.addAfterObject(25, 20);*/
		//effect.setAlphaDecreasing(true);
		// 残影间隔2像素
		effect.setInterval(2f);
		//effect.setMoveOrbit(AfterImageEffect.WAVE);
		// 设定残影颜色
		// effect.setShadowColor(LColor.red);
		// 曲线方式移动残影
		// effect.setMoveOrbit(AfterImageEffect.CURVE);
		// 开始播放残像动画
		effect.start();
		// 注入效果
		add(effect);

		// 构建Click节点,名称AfterStart,位置60,60,大小100x50
		LClickButton click = node("click", "start", 60, 60, 100, 50);

		// 注入节点,并设定点击节点时事件
		add(click.down((x, y) -> {
			// 停止循环播放移动
			effect.setMoveLoop(false);
			// 重新开始
			effect.restart();
		}));

		LClickButton loopPlay = node("click", "loop", 220, 60, 100, 50);

		add(loopPlay.down((x, y) -> {
			// 循环播放移动
			effect.setMoveLoop(true);
			// 重新开始
			effect.restart();
		}));

		// 监听Screen点击,返回是否点击了残影对象
		down((x, y) -> {
			println(effect.containsAfterObject(x, y));
		});
		
		/**
		 * 
		// 构建精灵以70x124的大小拆分图片，放置在坐标位置0x0,显示大小宽70,高124
		final AnimatedEntity hero = new AnimatedEntity("assets/rpg/sword.png", 70, 124, 0, 0, 70, 124);
		// 播放动画,速度每帧220
		// final long[] frames = { 220, 220, 220, 220 };
		// 绑定字符串和帧索引关系,左右下上以及斜角(等距视角)上下左右共8方向的帧播放顺序(也可以理解为具体播放的帧)
		// PlayIndex的作用是序列化帧,注入每帧播放时间以及播放帧的顺序,比如4,7就是播放索引号4,5,6,7这4帧
		hero.setPlayIndex("tleft", PlayIndex.at(220, 4, 7));
		hero.setPlayIndex("tright", PlayIndex.at(220, 8, 11));
		hero.setPlayIndex("tdown", PlayIndex.at(220, 0, 3));
		hero.setPlayIndex("tup", PlayIndex.at(220, 12, 15));
		hero.setPlayIndex("left", PlayIndex.at(220, 24, 27));
		hero.setPlayIndex("right", PlayIndex.at(220, 20, 23));
		hero.setPlayIndex("down", PlayIndex.at(220, 16, 19));
		hero.setPlayIndex("up", PlayIndex.at(220, 28, 31));
		// 播放绑定到down的动画帧
		hero.animate("right");
		// 注入精灵到Screen
		add(hero);
		up((x,y)->{
			//构建一个残影类，向右，走5步(默认残像间隔即图像大小，若要改变可以设置间隔参数)
			AfterImageEffect effect = AfterImageEffect.ofSprite(Config.TRIGHT, hero, 5);
			//添加残影到窗体
			add(effect);
			//开始执行
			effect.start();
		});
		 */
	}

}
