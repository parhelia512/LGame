![LGame Logo](https://raw.github.com/cping/LGame/master/loon_logo.svg)
## 🎮 Loon Game — A Java Game Framework (Java Game Engine)
![LGame](https://raw.github.com/cping/LGame/master/engine_logo.png "engine_logo")
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

**LGame** is a cross-platform java game engine(framework), offering comprehensive foundational modules for 2D games (with 3D support planned for future releases). It supports platforms including Windows, Linux, macOS, Android, web browsers, and iOS. Additionally, it provides native implementations in C# and C++ alongside syntax conversion to accommodate as many systems as possible.

[EN](README.md) / [KR](README.kr.md)

[Free Game Resources Links](https://github.com/cping/LGame/blob/master/dev-res/README.md "Game Resources of Free")

[Download Loon Game Engine](https://github.com/cping/LGame "Loon Game Engine")

Only Android-studio Template : androidstudio-template

All Java code Run Template : loon-gradle-template

('task run' call main methond , 'task dist' packager game to jar)

![LGame](https://raw.github.com/cping/LGame/master/gradle_test.png "gradle_test")

* 2019 year resurrection continues to update - 2019-03-08.

International Entertainment Machines

## Loon

![LGame](https://raw.github.com/cping/LGame/master/loon_framework.png "loonframework")

formal name : Loon

A fast, simple & powerful game framework, powered by Java (also supports C# and C++).

LGame Project Restart,The game's just started.

## Features
LGame(LoonGame) is a very cool and small game library designed to simplify the complex and shorten the tedious for beginners and veterans alike. With it, you can use the best aspects of OpenGL/OpenGLES in an easy and organized way optimized for game programming. It is built around the concept that beginners should be able to start with the basics and then move up into a more complex plane of development with the veterans, all on the same platform.

LGame puts all of its effort into keeping things short and simple. The initial setup of a game consists only of making a single class; then you are done. The interface is entirely documented for easy and fast learning, so once you are started, there is nothing between you and your killer game but coding and creativity.

LGame is built around the users wishes, so do not hesitate to suggest and critique!

### Games Code Samples

![LGame](https://raw.github.com/cping/LGame/master/sample.png "samples")

[Samples](https://github.com/cping/LGame/tree/master/Java/samples "Game Sample")

[Examples](https://github.com/cping/LGame/tree/master/Java/Examples "Game Example")

### Game Run the Example(JavaSE)
```java

package org.test;

import loon.LSetting;
import loon.LazyLoading;
import loon.Screen;
import loon.javase.Loon;

public class Main  {

	public static void main(String[] args) {
		LSetting setting = new LSetting();
	        // Whether to display the basic debug data (memory, sprite, desktop components, etc.)
		setting.isDebug = true;
		// Whether to display log data to the form
		setting.isDisplayLog = false;
		// Whether to display the initial logo
		setting.isLogo = false;
		// The initial page logo
		setting.logoPath = "loon_logo.png";
		// Original size
		setting.width = 480;
		setting.height = 320;
		// Zoom to
		setting.width_zoom = 640;
		setting.height_zoom = 480;
		// Set FPS
		setting.fps = 60;
		// Game Font
		setting.fontName = "Dialog";
		// App Name
		setting.appName = "test";
		// Whether to simulate touch screen events (only desktop is valid)
		setting.emulateTouch = false;
		/* Set the global font to BMFont */
		//setting.setSystemGameFont(BMFont.getDefaultFont());
		Loon.register(setting, new LazyLoading.Data() {

			@Override
			public Screen onScreen() {
				return new YourScreen();
			}
		});
	}
}
```
### Create a LGame project

LGame comes with a file called LGameProjectMake.jar which is an executable UI and command line tool. You can simply execute the JAR file which will open the setup UI.

![LGame](https://raw.github.com/cping/LGame/master/install.png "install")

![LGame](https://raw.github.com/cping/LGame/master/e0x.png "0")

![LGame](https://raw.github.com/cping/LGame/master/e1x.png "1")

![LGame](https://raw.github.com/cping/LGame/master/e2x.jpg "2")

![LGame](https://raw.github.com/cping/LGame/master/sample.jpg "samplelist")
_________

#### 关于LGame

总之就是一个非常懒人化的2D全功能游戏库(标准版会增加3D支持)，基于OpenGL（OpenGLES）开发，有多平台适配，基本上可以一个jar满足绝大多数的2D游戏需求（暂时不含网络部分，准备有时间单开项目），目前仅以Java语法来说，算是很方便的2D游戏库了。

开发思路：

目前计划就是Java，C#，C++这三种版本（以前想过go语言支持，但语法差异大，转译太麻烦，放弃了），但是三个版本并不独立，而是以Java版为基础，然而另外两个版本作为多平台的支持环境而存在。

Java版没什么好说的，jvm能够直接运行的本地环境封装基本都做了，2d部分已经快要构建完毕，目前会专注于搞多语言跨平台，跨平台都初步实现以后，再翻过头来增添3d功能。

C#版基于MonoGame（后续会加Unity版）封装而成，会提供代码一键转换工具，但只提供Loon的C#版封装，不会提供本地环境支持(代码转化后可以直接使用MonoGame和Unity提供的)，这个完全没难度，直接Java转译C#代码过去就行，更简单是用ikvm（但是不能用，因为ikvm的支持库太大了……），想的话随时可以写完，没完成就是没（特）时（别）间（懒），加C#支持就是因为自制的C++版环境适配肯定没有人家多年开发的C#库做的好，上C#用户能少走弯路，毕竟现阶段C/C++版的配置和运行会很复杂……

C版则是基于TeaVM转C还有SDL封装的实现，一样会提供一键转换，直接Java2C后编译运行（根据cmake文件的配置不同，可细分具体编译的输出平台），理论上并不存在C版不能运行的环境，而且此版本内部也会提供绝大多数环境，特别是游戏机环境的支持。