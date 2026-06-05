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
import loon.component.LIMEInput;
import loon.utils.ObjectMap;

public class ImeInputTest extends Stage {

	@Override
	public void create() {
		// 构建一个loon原生的虚拟输入法，大小300x300
		LIMEInput limeInput = new LIMEInput(0, 0, 300, 300);

		// 中文拼音字典（硬编码示例）
		ObjectMap<String, String[]> pinyinDict = new ObjectMap<>();
		// 多字会翻页，默认6个字一翻
		pinyinDict.put("ni", new String[] { "你", "尼", "拟", "妮", "逆", "腻", "霓", "鲵", "呢", "昵", "腻" });
		pinyinDict.put("hao", new String[] { "好", "号", "浩" });
		pinyinDict.put("shi", new String[] { "是", "时", "世", "市" });
		pinyinDict.put("jie", new String[] { "界", "解", "节", "接" });
		pinyinDict.put("ren", new String[] { "人", "仁", "任" });
		pinyinDict.put("gong", new String[] { "工", "公", "功" });
		pinyinDict.put("zhi", new String[] { "智", "之", "只", "志" });
		pinyinDict.put("neng", new String[] { "能", "嫩" });

		// 简单频率表（频率越大排序越靠前）
		ObjectMap<String, Integer> pinyinFreq = new ObjectMap<>();
		pinyinFreq.put("你", 100);
		pinyinFreq.put("好", 100);
		// 复合词也使用频率表展示
		pinyinFreq.put("世界", 80);
		pinyinFreq.put("界", 60);
		pinyinFreq.put("人", 90);
		pinyinFreq.put("工", 70);
		pinyinFreq.put("智", 85);
		pinyinFreq.put("能", 85);

		// 加载到组件（此处仅示例，建议自行构建适当字典，默认不提供（太大，内置不合适））
		limeInput.loadPinyinDictionaryWithFreq(pinyinDict, pinyinFreq);
		// 直接加载拼音,文件格式如下，每行一个
		// ni  你,尼,拟
		// limeInput.loadDictFromFile("pinyin.txt");
		// in.createKeyButton(getGameFont(), 30, 25);
		centerOn(limeInput);
		add(limeInput);
	}

}
