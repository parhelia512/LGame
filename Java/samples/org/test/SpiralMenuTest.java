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
import loon.component.LSpiralMenu;

public class SpiralMenuTest extends Stage {

	@Override
	public void create() {
		setBackground("back1.png");

		// 菜单子项文本
		String[] menuItems = { "攻击", "技能", "道具", "防御", "逃跑", "变身", "治疗", "召唤" };

		// 创建螺旋菜单
		// 参数：主按钮文本、主按钮宽高、子项文本、菜单坐标、菜单区域大小
		LSpiralMenu spiralMenu = new LSpiralMenu("菜单", // 主按钮文字
				70, 70, // 主按钮宽高
				menuItems, // 子按钮文字数组
				0, 0, // 菜单容器在画面中的位置(非菜单位置，因为还要加载子菜单到容器，所以是指全部菜单展开后容许的最大x与y坐标值)
				getWidth(), getHeight() // 菜单容器大小(是指在包含子菜单与主菜单的完全展开后大小容许的最大边界值)
		);

		// 添加到舞台显示
		add(spiralMenu);

		// 设置菜单布局（可选）
		// 支持全部9种布局模式
		spiralMenu.setLayoutMode(LSpiralMenu.LayoutMode.ELLIPSE); // 椭圆环绕
		// spiralMenu.setLayoutMode(LSpiralMenu.LayoutMode.GRID); // 网格布局
		// spiralMenu.setLayoutMode(LSpiralMenu.LayoutMode.ARC_TOP); // 上半圆
		// spiralMenu.setLayoutMode(LSpiralMenu.LayoutMode.LEFT_RIGHT); // 左右分布
		// spiralMenu.setLayoutMode(LSpiralMenu.LayoutMode.RING_GRID); // 多层环形

		// 配置动画参数
		spiralMenu.setAnimationDuration(0.4f); // 动画时长（秒,可选）
		spiralMenu.setDelayScale(0.07f); // 子按钮依次出现的延迟（可选）
		spiralMenu.setAllButtonRotationSpeed(200f); // 按钮旋转速度（可选）

		// 子按钮点击事件监听
		spiralMenu.setSkillButtonListener(new LSpiralMenu.SkillButtonListener() {
			@Override
			public void onSkillClicked(int index) {
				System.out.println("点击了子按钮 → 索引：" + index + "，文字：" + menuItems[index]);
				// 点击后自动收起菜单（可选）
				// spiralMenu.collapse();
			}
		});

		// 菜单状态监听（展开/收起回调）
		spiralMenu.setStateListener(new LSpiralMenu.MenuStateListener() {
			@Override
			public void onExpandStart() {
				System.out.println("菜单开始展开");
			}

			@Override
			public void onExpandFinish() {
				System.out.println("菜单展开完成");
			}

			@Override
			public void onCollapseStart() {
				System.out.println("菜单开始收起");
			}

			@Override
			public void onCollapseFinish() {
				System.out.println("菜单收起完成");
			}
		});

		System.out.println("菜单是否展开：" + spiralMenu.isExpanded());
		System.out.println("子按钮总数：" + spiralMenu.getButtonCount());

		// 手动控制展开/收起
		// spiralMenu.expand();
		// spiralMenu.collapse();

		// 强制立即收起（无动画）
		// spiralMenu.forceCollapseImmediately();

		// 根据索引获取子按钮并操作
		LSpiralMenu.SpiralButton btn = spiralMenu.getButtonByName("技能");
		if (btn != null) {
			// 播放按钮脉动动画
			btn.triggerPulse();
		}
	}

}
