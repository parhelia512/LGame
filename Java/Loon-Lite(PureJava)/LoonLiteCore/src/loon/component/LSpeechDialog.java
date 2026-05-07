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
package loon.component;

import loon.LSystem;
import loon.LTexture;
import loon.canvas.LColor;
import loon.component.skin.SkinManager;
import loon.font.FontSet;
import loon.font.IFont;
import loon.geom.Polygon;
import loon.geom.Shape;
import loon.geom.Vector2f;
import loon.opengl.GLEx;
import loon.utils.CharUtils;
import loon.utils.IntMap;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.SortedList;
import loon.utils.StrBuilder;
import loon.utils.StringUtils;
import loon.utils.TArray;
import loon.utils.timer.Duration;

/**
 * 漫画型气泡对话框构建用组件，用于在角色头顶显示指向性对话（漫画式小尾巴指向角色），或者特殊场合的对话效果
 */
public class LSpeechDialog extends LComponent implements FontSet<LSpeechDialog> {

	public static enum BubbleType {
		ROUND, ELLIPSE, CIRCLE
	}

	public static class Character {

		public String name;
		public LColor bubbleColor;
		public LColor textColor;
		public LTexture avatar;
		public String position;

		public Character(String name, LColor bubbleColor, LColor textColor, LTexture avatar, String position) {
			this.name = name;
			this.bubbleColor = bubbleColor;
			this.textColor = textColor;
			this.avatar = avatar;
			this.position = position;
		}
	}

	public static class TextSegment {

		public String content;
		public LColor color;

		public boolean wave;
		public boolean shakeX;
		public boolean shakeY;
		public boolean flicker;
		public boolean gradient;
		public boolean scale;
		public boolean hide;
		public boolean colorTag;
		public float offsetX;
		public float offsetY;

		public ObjectMap<String, String> strParams = new ObjectMap<String, String>();
		public IntMap<Integer> intParams = new IntMap<Integer>();

		public boolean completed;
		public float effectFrequency;

		public TextSegment(String content, LColor color) {
			this.content = content;
			this.color = color;
		}

		/**
		 * 构建一个文字显示模组
		 * 
		 * @param content         文字内容
		 * @param color           颜色
		 * @param shakex          是否震荡x轴
		 * @param shakey          是否震荡y轴
		 * @param flicker         是否闪烁
		 * @param gradient        是否过度色
		 * @param scale           是否缩放
		 * @param sx              x轴震荡幅度
		 * @param sy              y轴震荡幅度
		 * @param effectFrequency 特效触发频率
		 */
		public TextSegment(String content, LColor color, boolean shakex, boolean shakey, boolean flicker,
				boolean gradient, boolean scale, int sx, int sy, float effectFrequency) {
			this.content = content;
			this.color = color;
			this.shakeX = shakex;
			this.shakeY = shakey;
			this.flicker = flicker;
			this.gradient = gradient;
			this.scale = scale;
			this.hide = false;
			this.colorTag = false;
			this.effectFrequency = effectFrequency;
			if (this.intParams == null) {
				this.intParams = new IntMap<Integer>();
			}
			this.intParams.put("sx", sx);
			this.intParams.put("sy", sy);
			this.intParams.put("shake", MathUtils.max(sx, sy));
			if (this.strParams == null) {
				this.strParams = new ObjectMap<String, String>();
			}
		}

		/**
		 * 构建一个文字显示模组
		 * 
		 * @param content
		 * @param color
		 * @param wave
		 * @param shakeX
		 * @param shakeY
		 * @param flicker
		 * @param gradient
		 * @param scale
		 * @param hide
		 * @param colorTag
		 * @param intParams
		 * @param strParams
		 * @param freq
		 * @param offsetX
		 * @param offsetY
		 */
		public TextSegment(String content, LColor color, boolean wave, boolean shakeX, boolean shakeY, boolean flicker,
				boolean gradient, boolean scale, boolean hide, boolean colorTag, IntMap<Integer> intParams,
				ObjectMap<String, String> strParams, float freq, float offsetX, float offsetY) {
			this.content = content;
			this.color = color.cpy();
			this.wave = wave;
			this.shakeX = shakeX;
			this.shakeY = shakeY;
			this.flicker = flicker;
			this.gradient = gradient;
			this.scale = scale;
			this.hide = hide;
			this.colorTag = colorTag;
			this.offsetX = offsetX;
			this.offsetY = offsetY;
			if (intParams != null) {
				this.intParams = new IntMap<Integer>(intParams);
			}
			if (strParams != null) {
				this.strParams = new ObjectMap<String, String>(strParams);
			}
			this.effectFrequency = freq;
		}

		public int getInt(String key, int def) {
			Integer v = intParams.get(key);
			return v == null ? def : v;
		}

		public float getFloat(String key, float def) {
			String v = strParams.get(key);
			if (v == null)
				return def;
			try {
				return Float.parseFloat(v);
			} catch (Exception e) {
				return def;
			}
		}

		public String getStr(String key, String def) {
			String v = strParams.get(key);
			return v == null ? def : v;
		}
	}

	public static class Dialogue {

		public Character speaker;
		public TArray<TextSegment> segments;
		public float printSpeed;
		public String tailStyle;
		public BubbleType bubbleType;
		public Vector2f textOffset = new Vector2f();

		public Dialogue(Character speaker, TArray<TextSegment> segments, float printSpeed, BubbleType bubbleType,
				String tailStyle) {
			this(speaker, segments, printSpeed, bubbleType, tailStyle, 0f, 0f);
		}

		public Dialogue(Character speaker, TArray<TextSegment> segments, float printSpeed, BubbleType bubbleType,
				String tailStyle, float x, float y) {
			this.speaker = speaker;
			this.segments = segments;
			this.printSpeed = printSpeed;
			this.bubbleType = bubbleType;
			this.tailStyle = tailStyle;
			this.textOffset.set(x, y);
		}

		public String getFullText() {
			StrBuilder sbr = new StrBuilder();
			sbr.append(speaker.name).append(LSystem.COLON);
			for (TextSegment seg : segments) {
				sbr.append(seg.content);
			}
			return sbr.toString();
		}
	}

	private static class EffectState {
		String tag = "";
		boolean wave = false;
		boolean shakeX = false;
		boolean shakeY = false;
		boolean flicker = false;
		boolean gradient = false;
		boolean scale = false;
		boolean hide = false;
		boolean colorTag = false;
		float offsetX = 0;
		float offsetY = 0;

		IntMap<Integer> intParams = new IntMap<Integer>();
		ObjectMap<String, String> strParams = new ObjectMap<String, String>();
		LColor textColor = null;

		void copyFrom(EffectState other) {
			if (other == null) {
				return;
			}
			this.wave = other.wave;
			this.shakeX = other.shakeX;
			this.shakeY = other.shakeY;
			this.flicker = other.flicker;
			this.gradient = other.gradient;
			this.scale = other.scale;
			this.hide = other.hide;
			this.colorTag = other.colorTag;
			this.offsetX = other.offsetX;
			this.offsetY = other.offsetY;
			this.intParams = new IntMap<Integer>(other.intParams);
			this.strParams = new ObjectMap<String, String>(other.strParams);
			this.textColor = other.textColor;
		}

		void parseAttributes(String attrString) {
			if (attrString == null || attrString.length() == 0) {
				applyTagDefaults();
				return;
			}
			int len = attrString.length();
			int i = 0;
			while (i < len) {
				while (i < len && CharUtils.isWhitespace(attrString.charAt(i))) {
					i++;
				}
				if (i >= len) {
					break;
				}
				int kstart = i;
				while (i < len && !CharUtils.isWhitespace(attrString.charAt(i)) && attrString.charAt(i) != '=') {
					i++;
				}
				String key = attrString.substring(kstart, i).toLowerCase();
				while (i < len && CharUtils.isWhitespace(attrString.charAt(i))) {
					i++;
				}
				String value = "true";
				if (i < len && attrString.charAt(i) == '=') {
					i++;
					while (i < len && CharUtils.isWhitespace(attrString.charAt(i))) {
						i++;
					}
					if (i < len) {
						char qc = attrString.charAt(i);
						if (qc == '"' || qc == '\'') {
							char quote = qc;
							i++;
							int vstart = i;
							while (i < len && attrString.charAt(i) != quote) {
								i++;
							}
							value = attrString.substring(vstart, MathUtils.min(i, len));
							if (i < len && attrString.charAt(i) == quote)
								i++;
						} else {
							int vstart = i;
							while (i < len && !CharUtils.isWhitespace(attrString.charAt(i))) {
								i++;
							}
							value = attrString.substring(vstart, i);
						}
					}
				}
				if (key.length() > 0) {
					strParams.put(key, value);
					try {
						int iv = Integer.parseInt(value);
						intParams.put(key, iv);
					} catch (Exception e) {
					}
				}
			}
			applyTagDefaults();
		}

		void applyTagDefaults() {
			if (tag == null) {
				return;
			}
			switch (tag) {
			case "wave":
				this.wave = true;
				break;
			case "shake":
				this.shakeX = true;
				this.shakeY = true;
				break;
			case "shakex":
				this.shakeX = true;
				break;
			case "shakey":
				this.shakeY = true;
				break;
			case "flicker":
				this.flicker = true;
				break;
			case "scale":
				this.scale = true;
				break;
			case "gradient":
				this.gradient = true;
				break;
			case "color":
				this.colorTag = true;
				String val = strParams.get("value");
				if (val == null) {
					val = strParams.get("v");
				}
				if (val != null) {
					this.textColor = LColor.findName(val);
				}
				break;
			case "hide":
				this.hide = true;
				break;
			case "offset":
				this.offsetX = getFloat("x", 0);
				this.offsetY = getFloat("y", 0);
				break;
			default:
				break;
			}
		}

		TextSegment toTextSegment(String text, LColor defaultColor) {
			LColor c = this.textColor != null ? this.textColor : defaultColor;
			return new TextSegment(text, c, this.wave, this.shakeX, this.shakeY, this.flicker, this.gradient,
					this.scale, this.hide, this.colorTag, this.intParams, this.strParams, getFloat("freq", 0.05f),
					this.offsetX, this.offsetY);
		}

		float getFloat(String key, float def) {
			String v = strParams.get(key);
			if (v == null) {
				return def;
			}
			try {
				return Float.parseFloat(v);
			} catch (Exception e) {
				return def;
			}
		}
	}

	private final LColor _combineColor = new LColor();
	private final Vector2f _bubbleTailOffset = new Vector2f();
	private final Vector2f _avatarOffset = new Vector2f(10f);
	private final Vector2f _position = new Vector2f();
	private final StrBuilder _message = new StrBuilder();
	private IntMap<Polygon> _tailShapes = new IntMap<Polygon>();
	private String _messageString;
	private boolean _initNativeDraw = false;
	private int _roundRadius = 20;
	private int _tailSize = 50;
	private boolean _isRunning = false;
	private IFont _font;
	private LColor _fontColor = LColor.white;
	private ObjectMap<String, Character> _characters = new ObjectMap<String, Character>();
	private TArray<Dialogue> _dialogues = new TArray<Dialogue>();
	private int _currentDialogueIndex = 0;
	private int _charIndex = 0;
	private float _elapsedTimeCount = 0;
	private float _textSpace = 0f;
	private float _leftTextOffsetX = 0f;
	private float _leftTextOffsetY = 0f;

	public LSpeechDialog(int x, int y, int width, int height) {
		this(x, y, width, height, 20);
	}

	public LSpeechDialog(int x, int y, int width, int height, int round) {
		this(SkinManager.get().getMessageSkin().getFont(), x, y, width, height, round);
	}

	public LSpeechDialog(IFont font, int x, int y, int width, int height, int round) {
		super(x, y, width, height);
		setFont(font);
		_roundRadius = round;
		_leftTextOffsetX = width / 7;
		_leftTextOffsetY = height / 6;
		_isRunning = true;
	}

	public LSpeechDialog putCharacter(String name, LColor bubbleColor, LColor textColor, LTexture avatar,
			String position) {
		return putCharacter(name, new Character(name, bubbleColor, textColor, avatar, position));
	}

	public LSpeechDialog putCharacter(String name, Character ch) {
		_characters.put(name, ch);
		_initNativeDraw = false;
		return this;
	}

	public Character removeCharacter(String name) {
		return _characters.remove(name);
	}

	public LSpeechDialog putDialogue(String name, TArray<TextSegment> segments, float printSpeed, BubbleType bubbleType,
			String tailStyle) {
		return putDialogue(name, segments, printSpeed, bubbleType, tailStyle, 0f, 0f);
	}

	public LSpeechDialog putDialogue(String name, TArray<TextSegment> segments, float printSpeed, BubbleType bubbleType,
			String tailStyle, float textX, float textY) {
		_dialogues.add(new Dialogue(_characters.get(name), segments, printSpeed, bubbleType, tailStyle, textX, textY));
		_initNativeDraw = false;
		return this;
	}

	public LSpeechDialog putDialogue(Dialogue d) {
		_dialogues.add(d);
		_initNativeDraw = false;
		return this;
	}

	@Override
	public LSpeechDialog setFont(IFont font) {
		_font = font;
		return this;
	}

	@Override
	public IFont getFont() {
		return _font;
	}

	@Override
	public LSpeechDialog setFontColor(LColor color) {
		_fontColor = color;
		return this;
	}

	@Override
	public LColor getFontColor() {
		return _fontColor;
	}

	protected int getTotalChars(Dialogue dialogue) {
		int count = 0;
		for (TextSegment seg : dialogue.segments) {
			count += seg.content.length();
		}
		return count;
	}

	protected void replayDialogue(int index) {
		_currentDialogueIndex = index;
	}

	private void drawSpeechBubble(GLEx g, int x, int y, int w, int h, Dialogue dialogue, BubbleType type,
			String tailStyle, LColor backgroundColor, LColor textColor, LTexture avatar, int charIndex) {
		String side;
		switch (dialogue.speaker.position) {
		case "left":
			side = "left";
			break;
		case "right":
			side = "right";
			break;
		default:
			side = "bottom";
			break;
		}
		Vector2f target = getBubbleTailTarget(dialogue.speaker, x, y, w, h);
		Shape tailShape = createTailShape(x, y, w, h, target.x(), target.y(), tailStyle, side);
		paintBubble(g, tailShape, type, x, y, w, h, backgroundColor);
		paintAvatar(g, avatar, x, y);
		Vector2f offset = dialogue.textOffset;
		drawTextWithEffects(g, x + _leftTextOffsetX + offset.x, y + _leftTextOffsetY + offset.y, dialogue, charIndex);
	}

	private void paintBubble(GLEx g, Shape tailShape, BubbleType type, float x, float y, float w, float h,
			LColor backgroundColor) {
		g.setColor(backgroundColor);
		if (type == BubbleType.ROUND) {
			g.fillRoundRect(x, y, w, h, _roundRadius);
		} else if (type == BubbleType.ELLIPSE) {
			g.fillOval(x - 1, y - 1, w + 2, h + 2);
		} else if (type == BubbleType.CIRCLE) {
			g.fillCircle(x - 1, y - 1, MathUtils.min(w, h) + 2);
		}
		g.fill(tailShape);
	}

	protected Shape createTailShape(int x, int y, int w, int h, int targetX, int targetY, String tailStyle,
			String side) {
		int result = 1;
		result = LSystem.unite(result, x);
		result = LSystem.unite(result, y);
		result = LSystem.unite(result, w);
		result = LSystem.unite(result, h);
		result = LSystem.unite(result, targetX);
		result = LSystem.unite(result, targetY);
		result = LSystem.unite(result, tailStyle);
		result = LSystem.unite(result, side);
		Polygon tailShape = _tailShapes.get(result);
		if (tailShape == null) {
			tailShape = new Polygon();
			if ("left".equals(side)) {
				int anchorY = y + h / 2;
				if ("zigzag".equals(tailStyle)) {
					tailShape.addPoint(x - _roundRadius, anchorY);
					tailShape.addPoint(x, anchorY - _roundRadius / 2);
					tailShape.addPoint(x - _roundRadius / 2, anchorY);
					tailShape.addPoint(x, anchorY + _roundRadius / 2);
				} else {
					tailShape.addPoint(x - _roundRadius, anchorY);
					tailShape.addPoint(x, anchorY - _roundRadius / 2);
					tailShape.addPoint(x, anchorY + _roundRadius / 2);
				}
			} else if ("right".equals(side)) {
				int anchorY = y + h / 2;
				if ("zigzag".equals(tailStyle)) {
					tailShape.addPoint(x + w + _roundRadius, anchorY);
					tailShape.addPoint(x + w, anchorY - _roundRadius / 2);
					tailShape.addPoint(x + w + _roundRadius / 2, anchorY);
					tailShape.addPoint(x + w, anchorY + _roundRadius / 2);
				} else {
					tailShape.addPoint(x + w + _roundRadius, anchorY);
					tailShape.addPoint(x + w, anchorY - _roundRadius / 2);
					tailShape.addPoint(x + w, anchorY + _roundRadius / 2);
				}
			} else {
				int anchorX = MathUtils.max(x, MathUtils.min(targetX, x + w));
				int anchorY = y + h;
				tailShape.addPoint(targetX, targetY);
				tailShape.addPoint(anchorX - _roundRadius / 2, anchorY);
				tailShape.addPoint(anchorX + _roundRadius / 2, anchorY);
			}
			_tailShapes.put(result, tailShape);
		}
		return tailShape;
	}

	private Vector2f getBubbleTailTarget(Character speaker, int bubbleX, int bubbleY, int bubbleW, int bubbleH) {
		switch (speaker.position) {
		case "left":
			return _position.set(bubbleX + _tailSize + _bubbleTailOffset.x,
					bubbleY + bubbleH + (_tailSize / 2 + 5) + _bubbleTailOffset.y);
		case "center":
			return _position.set(bubbleX + bubbleW / 2 + _bubbleTailOffset.x,
					bubbleY + bubbleH + (_tailSize / 2 + 5) + _bubbleTailOffset.y);
		case "right":
			return _position.set(bubbleX + bubbleW - _tailSize + _bubbleTailOffset.x,
					bubbleY + bubbleH + (_tailSize / 2 + 5) + _bubbleTailOffset.y);
		default:
			return _position.set(bubbleX + bubbleW / 2 + _bubbleTailOffset.x,
					bubbleY + bubbleH + (_tailSize / 2 + 5) + _bubbleTailOffset.y);
		}
	}

	private void paintAvatar(GLEx g, LTexture avatar, int x, int y) {
		if (avatar != null) {
			g.draw(avatar, x + _avatarOffset.x, y + _avatarOffset.y);
		}
	}

	public static TArray<TextSegment> parseSegmentsFromMarkup(String markup, LColor defaultColor) {
		TArray<TextSegment> segments = new TArray<TextSegment>();
		SortedList<EffectState> stack = new SortedList<EffectState>();
		stack.push(new EffectState());
		int len = markup.length();
		int i = 0;
		int lastTextStart = 0;
		while (i < len) {
			char c = markup.charAt(i);
			if (c == '<') {
				if (i > lastTextStart) {
					String text = markup.substring(lastTextStart, i);
					if (!text.isEmpty()) {
						EffectState cur = stack.peek();
						TextSegment seg = cur.toTextSegment(text, defaultColor);
						segments.add(seg);
					}
				}
				int tagStart = i + 1;
				boolean closing = false;
				if (tagStart < len && markup.charAt(tagStart) == '/') {
					closing = true;
					tagStart++;
				}
				int p = tagStart;
				while (p < len) {
					char tc = markup.charAt(p);
					if (tc == ' ' || tc == '\t' || tc == '\n' || tc == '\r' || tc == '>' || tc == '/') {
						break;
					}
					p++;
				}
				String tagName = markup.substring(tagStart, p).toLowerCase();
				int attrStart = p;
				boolean selfClose = false;
				while (p < len && markup.charAt(p) != '>') {
					if (markup.charAt(p) == '/') {
						selfClose = true;
					}
					p++;
				}
				int tagEnd = (p < len) ? p : len - 1;
				String attrString = "";
				if (attrStart < tagEnd) {
					attrString = markup.substring(attrStart, tagEnd);
				}
				i = (p < len) ? p + 1 : len;
				lastTextStart = i;

				if (closing) {
					EffectState top = stack.peek();
					if (top != null && tagName.equals(top.tag)) {
						stack.pop();
					} else {
						SortedList<EffectState> tmp = new SortedList<EffectState>();
						while (!stack.isEmpty()) {
							EffectState s = stack.pop();
							tmp.push(s);
							if (tagName.equals(s.tag)) {
								tmp.pop();
								break;
							}
						}
						while (!tmp.isEmpty()) {
							stack.push(tmp.pop());
						}
					}
				} else {
					EffectState ns = new EffectState();
					ns.tag = tagName;
					ns.copyFrom(stack.peek());
					ns.parseAttributes(attrString);
					stack.push(ns);
					if (selfClose) {
						stack.pop();
					}
				}
			} else {
				i++;
			}
		}
		if (lastTextStart < len) {
			String text = markup.substring(lastTextStart);
			if (!text.isEmpty()) {
				EffectState cur = stack.peek();
				TextSegment seg = cur.toTextSegment(text, defaultColor);
				segments.add(seg);
			}
		}
		return segments;
	}

	public LSpeechDialog putDialogueFromMarkup(String name, String markup, float printSpeed, BubbleType bubbleType,
			String tailStyle) {
		Character ch = _characters.get(name);
		if (ch == null) {
			return this;
		}
		TArray<TextSegment> segments = parseSegmentsFromMarkup(markup, ch.textColor);
		_dialogues.add(new Dialogue(ch, segments, printSpeed, bubbleType, tailStyle));
		_initNativeDraw = false;
		return this;
	}

	private void drawTextWithEffects(GLEx g, float x, float y, Dialogue dialogue, int charIndex) {
		float animTime = _elapsedTimeCount * 10f;
		float cursorX = x;
		float cursorY = y;
		int globalCharIndex = 0;
		TArray<TextSegment> segments = dialogue.segments;

		for (TextSegment seg : segments) {
			LColor baseColor = seg.color != null ? seg.color : dialogue.speaker.textColor;
			final String text = seg.content;
			final int len = text.length();

			for (int j = 0; j < len; j++) {
				if (globalCharIndex >= charIndex) {
					return;
				}
				final char c = text.charAt(j);
				if (c == LSystem.LF) {
					cursorX = x;
					cursorY += _font.getHeight() + 1;
					globalCharIndex++;
					continue;
				}
				String mes = String.valueOf(c);
				final int width = (StringUtils.isCJK(c) || StringUtils.isFullChar(c))
						? (_font.stringWidth(mes) + _font.getSize()) / 2
						: (_font.charWidth(c) + _font.getSize() - 4) / 2;

				float offsetX = 0, offsetY = 0;
				boolean visible = true;
				LColor drawColor = baseColor;

				offsetX += seg.offsetX;
				offsetY += seg.offsetY;

				// 波纹效果
				if (seg.wave) {
					int amp = seg.getInt("amp", 5);
					float freq = seg.getFloat("freq", 2f);
					float phase = seg.getFloat("phase", 0.8f);
					offsetY += MathUtils.sin(animTime * freq + globalCharIndex * phase) * amp;
				}
				// 震荡
				if (seg.shakeX) {
					int sx = seg.getInt("shake", seg.getInt("sx", 3));
					float sf = seg.getFloat("freq", 3f);
					offsetX += MathUtils.sin(animTime * sf + globalCharIndex) * sx;
				}
				if (seg.shakeY) {
					int sy = seg.getInt("shake", seg.getInt("sy", 3));
					float sf = seg.getFloat("freq", 3f);
					offsetY += MathUtils.cos(animTime * sf + globalCharIndex) * sy;
				}
				// 闪烁
				if (seg.flicker) {
					float prob = seg.getFloat("prob", 0.2f);
					float random = MathUtils.abs(MathUtils.sin(globalCharIndex + animTime * 5));
					visible = random > prob;
				}
				// 渐变
				if (seg.gradient) {
					float hue = MathUtils.abs(MathUtils.max(1f, animTime));
					_combineColor.setColor(baseColor.r * hue, baseColor.g * hue, baseColor.b * hue, 1f);
					baseColor = _combineColor;
				}
				// 颜色/隐藏
				if (seg.colorTag && seg.color != null) {
					drawColor = seg.color;
				}
				if (seg.hide) {
					visible = false;
				}

				// 缩放并绘制文字
				if (seg.scale) {
					float amp = seg.getFloat("amp", 0.2f);
					float freq = seg.getFloat("freq", 2.5f);
					float scale = 1f + MathUtils.sin(animTime * freq + globalCharIndex) * amp;
					if (visible) {
						g.drawString(mes, cursorX + offsetX, cursorY + offsetY, scale, scale, 0, 0, 0, drawColor);
					}
				} else { // 绘制文字
					if (visible) {
						g.drawString(mes, cursorX + offsetX, cursorY + offsetY, drawColor);
					}
				}

				cursorX += width + _textSpace;
				globalCharIndex++;
			}
		}
	}

	@Override
	public void process(final long elapsedTime) {
		if (!_isRunning || _currentDialogueIndex >= _dialogues.size()) {
			return;
		}
		Dialogue dialogue = _dialogues.get(_currentDialogueIndex);
		float delta = MathUtils.max(Duration.toS(elapsedTime), LSystem.MIN_SECONE_SPEED_FIXED);
		_elapsedTimeCount += delta;
		int totalChars = getTotalChars(dialogue);
		if (_charIndex >= totalChars && _currentDialogueIndex < _dialogues.size - 1) {
			_currentDialogueIndex++;
			_charIndex = 0;
		}
		if (_charIndex < totalChars) {
			float interval = dialogue.printSpeed;
			if (_elapsedTimeCount >= interval) {
				_charIndex++;
				_elapsedTimeCount = 0;
			}
		}
	}

	/**
	 * 直接进入下一个对话模组
	 */
	public boolean nextDialogue() {
		if (_currentDialogueIndex >= _dialogues.size()) {
			return false;
		}
		Dialogue dialogue = _dialogues.get(_currentDialogueIndex);
		int totalChars = getTotalChars(dialogue);
		if (_isRunning) {
			_charIndex = totalChars;
		} else {
			_charIndex++;
		}
		if (_currentDialogueIndex < _dialogues.size - 1) {
			_currentDialogueIndex++;
			_charIndex = 0;
			_elapsedTimeCount = 0;
		}
		return true;
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		if (!_initNativeDraw) {
			_message.clear();
			for (Dialogue dialog : _dialogues) {
				for (TextSegment seg : dialog.segments) {
					_message.append(seg.content);
				}
			}
			if (_message.size() > 0) {
				this._messageString = StringUtils.unificationChars(_message.toString().toCharArray());
				_initNativeDraw = true;
			}
		}
		IFont oldFont = g.getFont();
		g.setFont(_font);
		int oldColor = g.color();
		Dialogue dialogue = _dialogues.get(_currentDialogueIndex);
		Character speaker = dialogue.speaker;
		drawSpeechBubble(g, x, y, width(), height(), dialogue, dialogue.bubbleType, dialogue.tailStyle,
				speaker.bubbleColor, speaker.textColor, speaker.avatar, _charIndex);
		g.setFont(oldFont);
		g.setColor(oldColor);
	}

	/**
	 * 设置当前对话所有文字的偏移
	 * 
	 * @param x
	 * @param y
	 */
	public void setCurrentTextOffset(float x, float y) {
		if (_currentDialogueIndex >= _dialogues.size) {
			return;
		}
		Dialogue d = _dialogues.get(_currentDialogueIndex);
		d.textOffset.set(x, y);
	}

	/**
	 * 为指定段落设置文字偏移
	 * 
	 * @param dialogueIndex
	 * @param segIndex
	 * @param x
	 * @param y
	 */
	public void setSegmentOffset(int dialogueIndex, int segIndex, float x, float y) {
		if (dialogueIndex >= _dialogues.size) {
			return;
		}
		Dialogue d = _dialogues.get(dialogueIndex);
		if (segIndex >= d.segments.size) {
			return;
		}
		d.segments.get(segIndex).offsetX = x;
		d.segments.get(segIndex).offsetY = y;
	}

	public String getMessageString() {
		return _messageString;
	}

	public Vector2f getBubbleTailOffset() {
		return _bubbleTailOffset;
	}

	public LSpeechDialog setBubbleTailOffset(float x, float y) {
		_bubbleTailOffset.set(x, y);
		return this;
	}

	public Vector2f getAvatarOffset() {
		return _avatarOffset;
	}

	public LSpeechDialog setAvatarOffset(float x, float y) {
		_avatarOffset.set(x, y);
		return this;
	}

	public float getTextSpace() {
		return _textSpace;
	}

	public LSpeechDialog setTextSpace(float space) {
		this._textSpace = space;
		return this;
	}

	public float getLeftTextOffsetX() {
		return _leftTextOffsetX;
	}

	public void setLeftTextOffsetX(float x) {
		this._leftTextOffsetX = x;
	}

	public float getLeftTextOffsetY() {
		return _leftTextOffsetY;
	}

	public void setLeftTextOffsetY(float y) {
		this._leftTextOffsetY = y;
	}

	public boolean isRunning() {
		return _isRunning;
	}

	public void setRunning(boolean r) {
		this._isRunning = r;
	}

	public int getCurrentDialogueIndex() {
		return _currentDialogueIndex;
	}

	public void setCurrentDialogueIndex(int d) {
		this._currentDialogueIndex = d;
	}

	public int getCharIndex() {
		return _charIndex;
	}

	public void setCharIndex(int i) {
		this._charIndex = i;
	}

	public int getTailSize() {
		return _tailSize;
	}

	public void setTailSize(int t) {
		this._tailSize = t;
	}

	@Override
	public String getUIName() {
		return "SpeechBubble";
	}

	@Override
	public void destroy() {
		_isRunning = false;
		_initNativeDraw = false;
		_tailShapes.clear();
	}

}
