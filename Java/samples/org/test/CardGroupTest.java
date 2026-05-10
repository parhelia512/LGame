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
import loon.component.LCardGroup;
import loon.component.LCardGroup.ShuffleType;
import loon.component.LClickButton;
import loon.component.LPaper;

public class CardGroupTest extends Stage {

	@Override
	public void create() {
		// 构建卡牌管理组件，默认大小等于Screen大小
		final LCardGroup cards = new LCardGroup();

		// 卡牌容器整个向下50像素
		cards.setLocation(0, 50);
		// 注入一组卡牌
		for (int i = 0; i < 5; i++) {
			LPaper p = new LPaper("assets/1.png");
			p.setFlagType(i);
			// p.setGroup("Card");
			// p.Tag = "Other";
			cards.add(p);
		}
		// cards.setMiddleProtrusionCard(false);
		// cards.setClickCardToMoveUp(false);
		// cards.updateCards();
		// cards.setSelectedColor(LColor.yellow);
		cards.setClickedScale(1.5f);
		add(cards);

		// 构建一个洗牌按钮
		LClickButton shuffle = LClickButton.make("Shuffle Card");
		bottomLeftOn(shuffle, 5, -10);
		add(shuffle);
		// 点击按钮触发洗牌特效
		shuffle.up((x, y) -> {
			// 设定洗牌特效播放位置
			// cards.shuffleLayered(cards.getCenterX(), cards.getCenterY(), 8f, 1400L, 60);
			// 启用自动周期性洗牌（每10秒）
			// cards.enableAutoShuffle(10000);
			// 设定洗牌动画样式并播放洗牌，不设置则没有洗牌,洗牌会导致对牌的选择失效，需要重新设置选中牌，否则下面直接playCard会无效，因为失去了选择
			cards.shuffleLayered(ShuffleType.FISHER_YATES);
			// 是否开启洗牌动画(默认shuffleLayered时开启，允许关闭)
			// cards.setShuffleAnimationEnabled(false);

		});

		// 构建一个发牌按钮
		LClickButton play = LClickButton.make("Play Card");
		bottomRightOn(play, -5, -10);
		add(play);
		// 点击按钮触发发牌特效
		play.up((x, y) -> {
			// 播放出牌缓动动画,将选中牌(没有选中不执行)出牌到位置getWidth()/2f-40x10,出牌后牌缓动变成缩放1f,透明度1f，旋转90f的样式，出牌耗时1600毫秒
			// ps:出牌也是在LCardGroup组件中，而非直接发在上级Desktop组件中，所以LCardGroup设定的大小要能足够，否则会看不到.
			// 然后x和y坐标也一样，例如演示的LCardGroup的y向下偏移了50像素，所以发牌位置要向上偏移，减50才是屏幕0，减40才是屏幕10，在此说明.
			cards.playCard(getWidth() / 2f - 40, -40f, 1f, 1f, 90, 1600L, (t) -> {
				System.out.println(t.getFlagType() + ":执行完毕");
			});
			// cards.addCard(new LPaper("assets/1.png"));
		});
	}

}
