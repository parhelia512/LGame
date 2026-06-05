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

import java.util.Comparator;

import loon.BaseIO;
import loon.LSystem;
import loon.LTexture;
import loon.canvas.LColor;
import loon.component.layout.InputMethodLayout;
import loon.component.skin.SkinManager;
import loon.events.ActionKey;
import loon.events.ClickListener;
import loon.events.EventActionN;
import loon.events.GameKey;
import loon.events.SysKey;
import loon.events.Touched;
import loon.font.IFont;
import loon.opengl.GLEx;
import loon.utils.CharUtils;
import loon.utils.HelperUtils;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.StrBuilder;
import loon.utils.StringUtils;
import loon.utils.TArray;
import loon.utils.parse.StrTokenizer;

/**
 * loon自带的原生输入法组件，可以在不支持输入法调用的环境(比如老款掌机，而且转成C版或C#版后很多api都没有本地环境支持)使用输入法
 */
public class LIMEInput extends LContainer {

	public enum Mode {
		EN, CN, JP, KR, FR, DE, RU, ES
	}

	private final TArray<LClickButton> _keyButtons = new TArray<LClickButton>();
	private final StrBuilder _context = new StrBuilder();
	private final ObjectMap<Mode, char[]> _modeLetters = new ObjectMap<Mode, char[]>();
	// 前缀索引
	private final ObjectMap<String, TArray<String>> _pinyinPrefixIndex = new ObjectMap<String, TArray<String>>();
	private final ObjectMap<String, TArray<String>> _romajiPrefixIndex = new ObjectMap<String, TArray<String>>();
	private final ObjectMap<String, TArray<String>> _hangulPrefixIndex = new ObjectMap<String, TArray<String>>();

	private final ObjectMap<String, Integer> _candidateFreq = new ObjectMap<String, Integer>();
	private final ObjectMap<String, String[]> _pinyinDict = new ObjectMap<String, String[]>();
	private final ObjectMap<String, String[]> _romajiDict = new ObjectMap<String, String[]>();
	private final ObjectMap<String, String[]> _hangulDict = new ObjectMap<String, String[]>();
	// 物理按键与虚拟按键的绑定
	private final ObjectMap<Integer, EventActionN> _physicalKeyBindings = new ObjectMap<Integer, EventActionN>();
	private final ObjectMap<LClickButton, EventActionN> _virtualKeyBindings = new ObjectMap<LClickButton, EventActionN>();

	// 候选与分页
	private final TArray<String> _allMatchedCandidates = new TArray<String>();
	private final TArray<String> _pageCandidates = new TArray<String>();
	// 已提交与正在组成
	private final StrBuilder _committed = new StrBuilder();
	private final StrBuilder _composition = new StrBuilder();

	private final int _pageSize;

	private final ActionKey _keyLock = new ActionKey();

	private float _cursorBlinkInterval = 0.5f;

	private boolean _showCandidates = false;
	private int _candidatePage = 0;

	private String _lastComposition = "";

	private boolean _dirty = true;
	private boolean _cursorVisible = true;
	private boolean _upperCase = true;
	private boolean _capsLock = false;

	private int _maxLength = 20;
	private int _cursorPos = 0;

	private float _cursorTimer = 0f;

	private int _textOffsetX = 5;
	private int _textOffsetY = 5;
	private LClickButton _langButton;
	private LColor _textColor = LColor.white;
	private Mode _mode = Mode.EN;
	private IFont _font;
	private EventActionN _onSwitchLang;
	private EventActionN _onConfirm;

	public LIMEInput(int x, int y) {
		this(x, y, LSystem.viewSize.getWidth() - 2, MathUtils.iceil(LSystem.viewSize.getHeight() / 1.3f));
	}

	public LIMEInput(int x, int y, int w, int h) {
		this(SkinManager.get().getClickButtonSkin().getFont(), 6, x, y, w, h);
	}

	public LIMEInput(IFont font, int page, int x, int y, int w, int h) {
		super(x, y, w, h);
		if (font != null) {
			_font = font;
			_maxLength = w / MathUtils.max(2, MathUtils.ifloor(font.getSize() / 1.5f));
		}
		_pageSize = page;
		initLanguageLetterMaps();
		initKeyButtons();
	}

	private void initLanguageLetterMaps() {
		// 英文键盘（默认）
		_modeLetters.put(Mode.EN, "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray());
		// 法语键盘
		_modeLetters.put(Mode.FR, "AZERTYUIOPQSDFGHJKLMWXCVBN".toCharArray());
		// 德语键盘
		_modeLetters.put(Mode.DE, "QWERTZUIOPASDFGHJKLYXCVBNM".toCharArray());
		// 西班牙语键盘
		_modeLetters.put(Mode.ES, "QWERTYUIOPASDFGHJKLÑZXCVBNM".toCharArray());
		// 俄语键盘
		_modeLetters.put(Mode.RU, "ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ".toCharArray());
		// 中文键盘
		_modeLetters.put(Mode.CN, "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray());
		// 日语键盘
		_modeLetters.put(Mode.JP, "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray());
		// 韩语键盘
		_modeLetters.put(Mode.KR, "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray());
	}

	protected EventActionN insertCharAction(final String ch) {
		return new EventActionN() {
			@Override
			public void update() {
				if (_mode == Mode.EN || _mode == Mode.FR || _mode == Mode.DE || _mode == Mode.ES || _mode == Mode.RU) {
					insertTextAtCursor(ch);
				} else {
					inputCompositionChar(ch);
				}
			}
		};
	}

	protected EventActionN selectCandidateGlobalAction(final int globalIndex) {
		return new EventActionN() {
			@Override
			public void update() {
				selectCandidateByGlobalIndex(globalIndex);
			}
		};
	}

	protected EventActionN nextPageAction() {
		return new EventActionN() {
			@Override
			public void update() {
				nextCandidatePage();
			}
		};
	}

	protected EventActionN prevPageAction() {
		return new EventActionN() {
			@Override
			public void update() {
				prevCandidatePage();
			}
		};
	}

	protected EventActionN commitAction() {
		return new EventActionN() {
			@Override
			public void update() {
				commitComposition();
			}
		};
	}

	/**
	 * 绑定物理键
	 * 
	 * @param key
	 * @param action
	 */
	public void bindPhysicalKey(int key, EventActionN action) {
		if (action == null) {
			return;
		}
		_physicalKeyBindings.put(key, action);
	}

	/**
	 * 解绑物理键
	 * 
	 * @param key
	 */
	public void unbindPhysicalKey(int key) {
		_physicalKeyBindings.remove(key);
	}

	/**
	 * 绑定虚拟按键
	 * 
	 * @param btn
	 * @param action
	 */
	public void bindVirtualKey(LClickButton btn, final EventActionN action) {
		if (btn == null || action == null) {
			return;
		}
		_virtualKeyBindings.put(btn, action);
		btn.up(new Touched() {
			@Override
			public void on(float x, float y) {
				action.update();
			}
		});
	}

	/**
	 * 解绑虚拟按键
	 */
	public void unbindVirtualKey(LClickButton btn) {
		if (btn == null) {
			return;
		}
		_virtualKeyBindings.remove(btn);
		btn.up(null);
	}

	@Override
	public void keyReleased(GameKey key) {
		super.keyReleased(key);
		_keyLock.release();
	}

	@Override
	public void keyPressed(GameKey key) {
		super.keyPressed(key);
		if (_keyLock.isPressed()) {
			return;
		}
		int keyCode = key.getKeyCode();

		EventActionN action = _physicalKeyBindings.get(keyCode);
		if (action != null) {
			action.update();
			_keyLock.release();
			return;
		}
		char nextchar = key.getKeyChar();
		if (nextchar != 0 && CharUtils.isAlpha(nextchar)) {
			String s = String.valueOf(nextchar);
			if (_mode == Mode.EN || _mode == Mode.FR || _mode == Mode.DE || _mode == Mode.ES || _mode == Mode.RU) {
				insertTextAtCursor(s);
			} else {
				inputCompositionChar(Character.toString(Character.toLowerCase(nextchar)));
			}
		} else {
			if (keyCode == SysKey.ENTER) {
				if (_mode != Mode.EN && _composition.length() > 0) {
					commitComposition();
				} else if (_onConfirm != null) {
					_onConfirm.update();
				}
			} else if (keyCode == SysKey.SPACE) {
				if (_mode != Mode.EN && _composition.length() > 0) {
					commitComposition();
				} else {
					insertTextAtCursor(" ");
				}
			} else if (keyCode == SysKey.DEL || keyCode == SysKey.BACK) {
				if (_mode != Mode.EN && _composition.length() > 0) {
					compositionBackspace();
				} else {
					deleteBeforeCursor();
				}
			} else if (CharUtils.isDigit(nextchar)) {
				callDigit(nextchar - '0');
			}
		}

		_keyLock.release();
	}

	protected void buildPrefixIndexForDict(ObjectMap<String, String[]> dict, ObjectMap<String, TArray<String>> index) {
		index.clear();
		if (dict == null) {
			return;
		}
		for (ObjectMap.Entry<String, String[]> e : dict.entries()) {
			String key = e.key;
			String[] arr = e.value;
			if (key == null || arr == null) {
				continue;
			}
			String lowerKey = key.toLowerCase();
			int maxPrefix = MathUtils.min(3, lowerKey.length());
			for (int len = 1; len <= maxPrefix; len++) {
				String prefix = lowerKey.substring(0, len);
				TArray<String> list = index.get(prefix);
				if (list == null) {
					list = new TArray<String>();
					index.put(prefix, list);
				}
				for (String cand : arr) {
					if (cand != null && !list.contains(cand)) {
						list.add(cand);
					}
				}
			}
		}
	}

	protected void updateCandidates() {
		String comp = _composition.toString().toLowerCase();
		if (comp.equals(_lastComposition)) {
			refreshPageCandidates();
			return;
		}
		_lastComposition = comp;
		_allMatchedCandidates.clear();
		if (comp.length() == 0) {
			_showCandidates = false;
			refreshContextFromCommittedAndComposition();
			return;
		}
		String[] exact = null;
		if (_mode == Mode.CN) {
			exact = _pinyinDict.get(comp);
		} else if (_mode == Mode.JP) {
			exact = _romajiDict.get(comp);
		} else if (_mode == Mode.KR) {
			exact = _hangulDict.get(comp);
		}
		if (exact != null) {
			for (String s : exact) {
				if (s != null && !_allMatchedCandidates.contains(s)) {
					_allMatchedCandidates.add(s);
				}
			}
		}
		TArray<String> prefixList = null;
		if (_mode == Mode.CN) {
			prefixList = _pinyinPrefixIndex.get(comp.length() <= 3 ? comp : comp.substring(0, 3));
		} else if (_mode == Mode.JP) {
			prefixList = _romajiPrefixIndex.get(comp.length() <= 3 ? comp : comp.substring(0, 3));
		} else if (_mode == Mode.KR) {
			prefixList = _hangulPrefixIndex.get(comp.length() <= 3 ? comp : comp.substring(0, 3));
		}
		if (prefixList != null) {
			for (String s : prefixList) {
				if (s != null && !_allMatchedCandidates.contains(s)) {
					if (candidateMatchesPrefix(s, comp)) {
						_allMatchedCandidates.add(s);
					}
				}
			}
		}
		if (_allMatchedCandidates.size() < 50) {
			ObjectMap<String, String[]> dict = (_mode == Mode.CN ? _pinyinDict
					: (_mode == Mode.JP ? _romajiDict : _hangulDict));
			if (dict != null) {
				for (ObjectMap.Entry<String, String[]> e : dict.entries()) {
					String key = e.key;
					String[] arr = e.value;
					if (key == null || arr == null) {
						continue;
					}
					String lk = key.toLowerCase();
					if (lk.startsWith(comp) || comp.startsWith(lk)
							|| levenshtein(lk, comp) <= MathUtils.max(1, comp.length() / 3)) {
						for (String s : arr) {
							if (s != null && !_allMatchedCandidates.contains(s)) {
								_allMatchedCandidates.add(s);
							}
						}
					}
				}
			}
		}
		_allMatchedCandidates.sort(new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				float sa = scoreCandidate(a, comp);
				float sb = scoreCandidate(b, comp);
				return MathUtils.compare(sb, sa);
			}
		});
		TArray<String> dedup = new TArray<String>();
		for (String s : _allMatchedCandidates) {
			if (!dedup.contains(s)) {
				dedup.add(s);
			}
			if (dedup.size() >= 200) {
				break;
			}
		}
		_allMatchedCandidates.clear();
		_allMatchedCandidates.addAll(dedup);

		_candidatePage = 0;
		refreshPageCandidates();
	}

	protected boolean candidateMatchesPrefix(String candidate, String comp) {
		ObjectMap<String, String[]> dict = (_mode == Mode.CN ? _pinyinDict
				: (_mode == Mode.JP ? _romajiDict : _hangulDict));
		if (dict == null) {
			return false;
		}
		for (ObjectMap.Entry<String, String[]> e : dict.entries()) {
			String key = e.key;
			String[] arr = e.value;
			if (arr == null || key == null) {
				continue;
			}
			for (String s : arr) {
				if (candidate.equals(s)) {
					if (key.toLowerCase().startsWith(comp)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	protected void refreshPageCandidates() {
		_pageCandidates.clear();
		int total = _allMatchedCandidates.size();
		if (total == 0) {
			_showCandidates = false;
			refreshContextFromCommittedAndComposition();
			return;
		}
		int pages = (total + _pageSize - 1) / _pageSize;
		if (_candidatePage < 0) {
			_candidatePage = 0;
		} else if (_candidatePage >= pages) {
			_candidatePage = pages - 1;
		}
		int start = _candidatePage * _pageSize;
		int end = MathUtils.min(start + _pageSize, total);
		for (int i = start; i < end; i++) {
			_pageCandidates.add(_allMatchedCandidates.get(i));
		}
		_showCandidates = true;
		refreshContextFromCommittedAndComposition();
	}

	protected void nextCandidatePage() {
		if (_mode == Mode.JP || _mode == Mode.CN || _mode == Mode.KR) {
			int total = _allMatchedCandidates.size();
			int pages = (total + _pageSize - 1) / _pageSize;
			if (pages <= 1) {
				return;
			}
			_candidatePage = MathUtils.min(_candidatePage + 1, pages - 1);
			refreshPageCandidates();
		} else {
			moveCursorRight();
		}
	}

	protected void prevCandidatePage() {
		if (_mode == Mode.JP || _mode == Mode.CN || _mode == Mode.KR) {
			if (_candidatePage <= 0) {
				return;
			}
			_candidatePage = MathUtils.max(0, _candidatePage - 1);
			refreshPageCandidates();
		} else {
			moveCursorLeft();
		}
	}

	protected void selectCandidateByGlobalIndex(int globalIndex) {
		if (globalIndex < 0 || globalIndex >= _allMatchedCandidates.size()) {
			return;
		}
		String chosen = _allMatchedCandidates.get(globalIndex);
		if (chosen != null) {
			_committed.append(chosen);
		}
		_composition.setLength(0);
		_allMatchedCandidates.clear();
		_pageCandidates.clear();
		_showCandidates = false;
		_candidatePage = 0;
		refreshContextFromCommittedAndComposition();
		resetCursorBlink();
	}

	protected float scoreCandidate(String candidate, String comp) {
		int freq = _candidateFreq.containsKey(candidate) ? _candidateFreq.get(candidate) : 1;
		float freqWeight = MathUtils.log(1 + freq);
		float prefixBoost = candidateStartsWithComposition(candidate, comp) ? 1f : 0f;
		int edit = levenshtein(candidate.toLowerCase(), comp);
		float editPenalty = edit * 0.5f;
		float lengthPenalty = candidate.length() * 0.01f;
		return prefixBoost * 1000f + freqWeight * 10f - editPenalty - lengthPenalty;
	}

	protected boolean candidateStartsWithComposition(String candidate, String comp) {
		ObjectMap<String, String[]> dict = (_mode == Mode.CN ? _pinyinDict
				: (_mode == Mode.JP ? _romajiDict : _hangulDict));
		if (dict == null) {
			return false;
		}
		for (ObjectMap.Entry<String, String[]> e : dict.entries()) {
			String key = e.key;
			String[] arr = e.value;
			if (key == null || arr == null) {
				continue;
			}
			for (String s : arr) {
				if (candidate.equals(s)) {
					if (key.toLowerCase().startsWith(comp)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	protected int levenshtein(String a, String b) {
		if (a == null) {
			a = LSystem.EMPTY;
		}
		if (b == null) {
			b = LSystem.EMPTY;
		}
		int la = a.length();
		int lb = b.length();
		if (la == 0) {
			return lb;
		}
		if (lb == 0) {
			return la;
		}
		int[] prev = new int[lb + 1];
		int[] curr = new int[lb + 1];
		for (int j = 0; j <= lb; j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= la; i++) {
			curr[0] = i;
			char ca = a.charAt(i - 1);
			for (int j = 1; j <= lb; j++) {
				int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
				curr[j] = MathUtils.min(MathUtils.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] tmp = prev;
			prev = curr;
			curr = tmp;
		}
		return prev[lb];
	}

	protected void inputCompositionChar(String ch) {
		if (ch == null || ch.length() == 0) {
			return;
		}
		if (_mode == Mode.EN) {
			insertTextAtCursor(ch);
			return;
		}
		_composition.append(ch.toLowerCase());
		updateCandidates();
		resetCursorBlink();
	}

	protected void compositionBackspace() {
		if (_composition.length() > 0) {
			_composition.setLength(_composition.length() - 1);
			updateCandidates();
		} else {
			if (_committed.length() > 0) {
				_committed.setLength(_committed.length() - 1);
			}
			refreshContextFromCommittedAndComposition();
		}
		resetCursorBlink();
	}

	protected void commitComposition() {
		if (_composition.length() > 0) {
			if (!_allMatchedCandidates.isEmpty()) {
				selectCandidateByGlobalIndex(0);
			} else {
				_committed.append(_composition.toString());
				_composition.setLength(0);
				_allMatchedCandidates.clear();
				_pageCandidates.clear();
				_showCandidates = false;
				refreshContextFromCommittedAndComposition();
			}
		}
	}

	protected void refreshContextFromCommittedAndComposition() {
		_context.clear();
		_context.append(_committed.toString());
		if (_composition.length() > 0) {
			_context.append(_composition.toString());
		}
		if (_showCandidates && !_pageCandidates.isEmpty()) {
			_context.append(" ");
			_context.append("[");
			for (int i = 0; i < _pageCandidates.size(); i++) {
				String s = _pageCandidates.get(i);
				int num = i + 1;
				_context.append(num).append("、").append(s);
				if (i < _pageCandidates.size() - 1) {
					_context.append(" ");
				}
			}
			_context.append("]");
		}
		_cursorPos = _context.length();
	}

	public LIMEInput createKeyButton(int width, int height) {
		return createKeyButton(SkinManager.get().getClickButtonSkin().getFont(), width, height);
	}

	public LIMEInput createKeyButton(IFont font, int width, int height) {
		return createKeyButton(font, _component_baseColor, width, height,
				SkinManager.get().getClickButtonSkin().getIdleClickTexture(),
				SkinManager.get().getClickButtonSkin().getHoverClickTexture(),
				SkinManager.get().getClickButtonSkin().getDisableTexture());
	}

	public LIMEInput createKeyButton(IFont font, LColor fontColor, int width, int height, LTexture idle, LTexture hover,
			LTexture clicked) {
		_keyButtons.clear();
		clear();
		char[] letters = _modeLetters.get(_mode);
		if (letters == null) {
			letters = "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray();
		}
		for (int idx = 0; idx < letters.length; idx++) {
			char c = letters[idx];
			String charText = String.valueOf(c);
			LClickButton click = LClickButton.make(charText, font, fontColor, width, height, idle, hover, clicked);
			click.setFlagType(9999);
			click.setTag(charText);
			click.S(new ClickListener() {
				@Override
				public void UpClick(LComponent comp, float x, float y) {
					if (comp != null) {
						String s = HelperUtils.toStr(comp.Tag);
						if (s == null) {
							return;
						}
						if (_mode == Mode.EN || _mode == Mode.FR || _mode == Mode.DE || _mode == Mode.ES) {
							if (_capsLock) {
								s = s.toUpperCase();
							} else if (!_upperCase) {
								s = s.toLowerCase();
							} else {
								s = s.toUpperCase();
							}
							insertTextAtCursor(s);
						} else if (_mode == Mode.RU) {
							insertTextAtCursor(s);
						} else {
							// 其它模式（CN/JP/KR）
							if (_mode == Mode.CN || _mode == Mode.JP || _mode == Mode.KR) {
								inputCompositionChar(s.toLowerCase());
							} else {
								insertTextAtCursor(s);
							}
						}
					}
				}

				@Override
				public void DragClick(LComponent comp, float x, float y) {
				}

				@Override
				public void DownClick(LComponent comp, float x, float y) {
				}

				@Override
				public void DoClick(LComponent comp) {
				}
			});
			add(click);
			_keyButtons.add(click);
		}

		String digits = "1234567890";
		for (char c : digits.toCharArray()) {
			String s = String.valueOf(c);
			LClickButton click = LClickButton.make(s, font, fontColor, width, height, idle, hover, clicked);
			click.setTag(s);
			final int digit = HelperUtils.toInt(s);
			click.up(new Touched() {
				@Override
				public void on(float x, float y) {
					callDigit(digit);
				}
			});
			add(click);
		}

		String punct = ".,?!'\"-/:;()[]";
		for (char c : punct.toCharArray()) {
			String s = String.valueOf(c);
			LClickButton click = LClickButton.make(s, font, fontColor, width, height, idle, hover, clicked);
			click.setTag(s);
			click.up(new Touched() {
				@Override
				public void on(float x, float y) {
					if (_mode == Mode.EN) {
						insertTextAtCursor(s);
					} else {
						_committed.append(s);
						refreshContextFromCommittedAndComposition();
					}
				}
			});
			add(click);
		}

		LClickButton leftClick = LClickButton.make("<", font, fontColor, width, height, idle, hover, clicked);
		leftClick.up(new Touched() {
			@Override
			public void on(float x, float y) {
				prevCandidatePage();
			}
		});

		LClickButton rightClick = LClickButton.make(">", font, fontColor, width, height, idle, hover, clicked);
		rightClick.up(new Touched() {
			@Override
			public void on(float x, float y) {
				nextCandidatePage();
			}
		});

		add(leftClick, rightClick);

		packLayout(InputMethodLayout.at(MathUtils.ifloor(getWidth() / width) - 2, width, height, 1f, 1f), 0, 30, 0, 0);

		_langButton = LClickButton.make(_mode.name(), font, fontColor, width * 2, height, idle, hover, clicked);
		_langButton.up(new Touched() {
			@Override
			public void on(float x, float y) {
				updateSwitchLang();
			}
		});
		if (rightClick.getX() < (getWidth() / 2 - 2)) {
			addRowNext(_langButton);
		} else {
			addColNext(_langButton);
			_langButton.setX(0);
		}

		LClickButton space = LClickButton.make("Space", font, fontColor, width * 3, height, idle, hover, clicked);
		space.up(new Touched() {
			@Override
			public void on(float x, float y) {
				if (_mode != Mode.EN && _composition.length() > 0) {
					commitComposition();
				}
				insertTextAtCursor(" ");
			}
		});

		LClickButton delClick = LClickButton.make("Del", font, fontColor, width * 2, height, idle, hover, clicked);
		delClick.up(new Touched() {
			@Override
			public void on(float x, float y) {
				if (_mode != Mode.EN && _composition.length() > 0) {
					compositionBackspace();
				} else {
					deleteBeforeCursor();
				}
			}
		});
		addRowNext(delClick);

		LClickButton shiftClick = LClickButton.make("Shift", font, fontColor, width * 3, height, idle, hover, clicked);
		shiftClick.up(new Touched() {
			@Override
			public void on(float x, float y) {
				toggleCase();
			}
		});
		addRowNext(shiftClick);

		LClickButton backspace = LClickButton.make("Enter", font, fontColor, width * 3, height, idle, hover, clicked);
		backspace.up(new Touched() {
			@Override
			public void on(float x, float y) {
				if (_mode != Mode.EN && _composition.length() > 0) {
					commitComposition();
				}
				if (_onConfirm != null) {
					_onConfirm.update();
				}
			}
		});
		addRowNext(backspace);
		bindPhysicalKey(SysKey.LEFT, nextPageAction());
		bindPhysicalKey(SysKey.RIGHT, prevPageAction());
		bindPhysicalKey(SysKey.DPAD_LEFT, nextPageAction());
		bindPhysicalKey(SysKey.DPAD_RIGHT, prevPageAction());
		_dirty = false;
		refreshContextFromCommittedAndComposition();
		return this;
	}

	protected void callDigit(int digit) {
		if (_mode != Mode.EN && _composition.length() > 0 && !_allMatchedCandidates.isEmpty()) {
			if (digit == 0) {
				nextCandidatePage();
				return;
			}
			int globalIdx = _candidatePage * _pageSize + (digit - 1);
			if (globalIdx >= 0 && globalIdx < _allMatchedCandidates.size()) {
				selectCandidateByGlobalIndex(globalIdx);
				return;
			} else {
				// 如果数字超出当前页范围但在全局范围内，跳转到对应页并选择
				if (globalIdx >= 0 && globalIdx < _allMatchedCandidates.size()) {
					selectCandidateByGlobalIndex(globalIdx);
					return;
				}
			}
		}
		// 否则插入数字
		insertTextAtCursor(String.valueOf(digit));
	}

	protected void updateSwitchLang() {
		if (_mode == Mode.EN) {
			_mode = Mode.CN;
		} else if (_mode == Mode.CN) {
			_mode = Mode.JP;
		} else if (_mode == Mode.JP) {
			_mode = Mode.KR;
		} else if (_mode == Mode.KR) {
			_mode = Mode.FR;
		} else if (_mode == Mode.FR) {
			_mode = Mode.DE;
		} else if (_mode == Mode.DE) {
			_mode = Mode.RU;
		} else if (_mode == Mode.RU) {
			_mode = Mode.ES;
		} else if (_mode == Mode.ES) {
			_mode = Mode.EN;
		}
		if (_langButton != null) {
			_langButton.setText(_mode.name());
		}
		_composition.setLength(0);
		_allMatchedCandidates.clear();
		_pageCandidates.clear();
		_showCandidates = false;
		_candidatePage = 0;
		updateKeyButtons();
		refreshContextFromCommittedAndComposition();
		if (_onSwitchLang != null) {
			_onSwitchLang.update();
		}
	}

	protected void insertTextAtCursor(String text) {
		if (text == null || text.length() == 0) {
			return;
		}
		int newLen = _committed.length() + text.length();
		if (newLen > _maxLength) {
			int allowed = _maxLength - _committed.length();
			if (allowed <= 0) {
				return;
			}
			text = text.substring(0, allowed);
		}
		_committed.insert(_committed.length(), text);
		refreshContextFromCommittedAndComposition();
		resetCursorBlink();
	}

	public void toggleCapsLock() {
		_capsLock = !_capsLock;
		if (_capsLock) {
			_upperCase = true;
		}
		updateKeyButtons();
	}

	private void updateKeyButtons() {
		char[] letters = _modeLetters.get(_mode);
		if (letters == null) {
			letters = "QWERTYUIOPASDFGHJKLZXCVBNM".toCharArray();
		}
		int letterIndex = 0;
		for (int i = 0; i < _keyButtons.size; i++) {
			LClickButton btn = _keyButtons.get(i);
			if (btn == null) {
				continue;
			}
			Object tag = btn.Tag;
			if (tag != null && (btn.getFlagType() == 9999)) {
				if (letterIndex < letters.length) {
					String s = String.valueOf(letters[letterIndex]);
					// 大小写处理（仅对拉丁字母有效）
					if (_mode == Mode.EN || _mode == Mode.FR || _mode == Mode.DE || _mode == Mode.ES) {
						if (!_upperCase) {
							s = s.toLowerCase();
						} else {
							s = s.toUpperCase();
						}
					}
					btn.setText(s);
					btn.setTag(s);
				} else {
					btn.setText(LSystem.EMPTY);
					btn.setTag(LSystem.EMPTY);
				}
				letterIndex++;
			}
		}
	}

	public void deleteBeforeCursor() {
		if (_committed.length() <= 0) {
			return;
		}
		int removeIndex = _committed.length() - 1;
		_committed.deleteCharAt(removeIndex);
		refreshContextFromCommittedAndComposition();
	}

	public void deleteAfterCursor() {
		if (_committed.length() <= 0) {
			return;
		}
		_committed.deleteCharAt(_committed.length() - 1);
		refreshContextFromCommittedAndComposition();
	}

	public void moveCursorLeft() {
		if (_cursorPos > 0) {
			_cursorPos--;
			resetCursorBlink();
		}
	}

	public void moveCursorRight() {
		if (_cursorPos < _context.length()) {
			_cursorPos++;
			resetCursorBlink();
		}
	}

	public void setCursorPosition(int pos) {
		_cursorPos = MathUtils.max(0, MathUtils.min(pos, _context.length()));
		resetCursorBlink();
	}

	public int getCursorPosition() {
		return _cursorPos;
	}

	public void toggleCase() {
		_upperCase = !_upperCase;
		updateKeyButtons();
	}

	public boolean isUpperCase() {
		return _upperCase;
	}

	public void setTextColor(LColor color) {
		if (color != null) {
			_textColor = color;
		}
	}

	public void setTextOffset(int dx, int dy) {
		_textOffsetX = dx;
		_textOffsetY = dy;
	}

	public void setText(String text) {
		_committed.clear();
		if (text != null) {
			_committed.append(text);
		}
		_composition.setLength(0);
		_cursorPos = _committed.length();
		refreshContextFromCommittedAndComposition();
	}

	public String getText() {
		StringBuilder sb = new StringBuilder();
		sb.append(_committed.toString());
		if (_composition.length() > 0) {
			sb.append(_composition.toString());
		}
		return sb.toString();
	}

	protected void resetCursorBlink() {
		_cursorVisible = true;
		_cursorTimer = 0f;
	}

	public boolean isDirty() {
		return _dirty;
	}

	public void setFont(IFont font) {
		_font = font;
	}

	public IFont getFont() {
		return _font;
	}

	protected void initKeyButtons() {
		if (_dirty) {
			int size = _font == null ? 20 : _font.getSize();
			createKeyButton(size, size);
			_dirty = false;
		}
	}

	@Override
	public void createUI(GLEx g, int x, int y) {
		initKeyButtons();
		int drawX = x + _textOffsetX;
		int drawY = y + _textOffsetY;
		String text = _context.toString();
		g.drawString(text, drawX, drawY, _textColor);
		float cursorPixelX = drawX;
		IFont font = _font == null ? g.getFont() : _font;
		if (font != null) {
			String before = _context.substring(0, MathUtils.max(0, MathUtils.min(_cursorPos, _context.length())));
			cursorPixelX += font.stringWidth(before);
		} else {
			cursorPixelX += _cursorPos * 8;
		}
		int cursorTop = drawY;
		int cursorBottom = drawY + (font != null ? font.getHeight() : 16);
		if (_cursorVisible) {
			g.drawLine(cursorPixelX, cursorTop, cursorPixelX, cursorBottom, _textColor);
		}
		String countText = (_committed.length() + _composition.length()) + " / "
				+ (_maxLength == Integer.MAX_VALUE ? "∞" : String.valueOf(_maxLength));
		float countX = x + getWidth() - _textOffsetX;
		float countY = y + getHeight() / 1.5f - _textOffsetY;
		// 如果有多页，显示页码提示
		int total = _allMatchedCandidates.size();
		int pages = (total + _pageSize - 1) / _pageSize;
		if (pages > 1) {
			countText += " Page " + (_candidatePage + 1) + "/" + pages;
		}
		if (font != null) {
			int w = font.stringWidth(countText);
			font.drawString(g, countText, countX - w - ((countText.length() * font.getSize()) / 8f) - 2,
					countY - font.getHeight(), _textColor);
		} else {
			g.drawString(countText, countX - (countText.length() * 16) - 2, countY - 16);
		}
		_dirty = false;
	}

	@Override
	public void update(long elapsedTime) {
		super.update(elapsedTime);
		_cursorTimer += elapsedTime / 1000f;
		if (_cursorTimer >= _cursorBlinkInterval) {
			_cursorTimer = 0f;
			_cursorVisible = !_cursorVisible;
		}
	}

	public void setMaxLength(int max) {
		_maxLength = MathUtils.max(0, max);
		if (_committed.length() + _composition.length() > _maxLength) {
			int newLen = MathUtils.min(_committed.length(), _maxLength);
			_committed.setLength(newLen);
			_composition.setLength(MathUtils.max(0, _maxLength - _committed.length()));
			_cursorPos = MathUtils.min(_cursorPos, _maxLength);
			refreshContextFromCommittedAndComposition();
		}
	}

	public int getMaxLength() {
		return _maxLength;
	}

	public EventActionN getOnConfirm() {
		return _onConfirm;
	}

	public void setOnConfirm(EventActionN c) {
		this._onConfirm = c;
	}

	public EventActionN getOnSwitchLanguage() {
		return _onSwitchLang;
	}

	public void setOnSwitchLanguage(EventActionN s) {
		this._onSwitchLang = s;
	}

	public Mode getMode() {
		return _mode;
	}

	public void setMode(Mode m) {
		this._mode = m;
		_composition.setLength(0);
		_allMatchedCandidates.clear();
		_pageCandidates.clear();
		_showCandidates = false;
		_candidatePage = 0;
		refreshContextFromCommittedAndComposition();
	}

	public void loadPinyinDictionary(ObjectMap<String, String[]> dict) {
		_pinyinDict.clear();
		if (dict != null) {
			_pinyinDict.putAll(dict);
			for (String[] arr : dict.values()) {
				for (String s : arr) {
					_candidateFreq.put(s, 1);
				}
			}
			buildPrefixIndexForDict(_pinyinDict, _pinyinPrefixIndex);
		}
	}

	public void loadPinyinDictionaryWithFreq(ObjectMap<String, String[]> dict, ObjectMap<String, Integer> freq) {
		loadPinyinDictionary(dict);
		if (freq != null) {
			for (ObjectMap.Entry<String, Integer> e : freq.entries()) {
				_candidateFreq.put(e.getKey(), MathUtils.max(1, e.getValue()));
			}
		}
	}

	public void loadRomajiDictionary(ObjectMap<String, String[]> dict) {
		_romajiDict.clear();
		if (dict != null) {
			_romajiDict.putAll(dict);
			for (String[] arr : dict.values()) {
				for (String s : arr) {
					_candidateFreq.put(s, 1);
				}
			}
			buildPrefixIndexForDict(_romajiDict, _romajiPrefixIndex);
		}
	}

	public void loadRomajiDictionaryWithFreq(ObjectMap<String, String[]> dict, ObjectMap<String, Integer> freq) {
		loadRomajiDictionary(dict);
		if (freq != null) {
			for (ObjectMap.Entry<String, Integer> e : freq.entries()) {
				_candidateFreq.put(e.getKey(), MathUtils.max(1, e.getValue()));
			}
		}
	}

	public void loadHangulDictionary(ObjectMap<String, String[]> dict) {
		_hangulDict.clear();
		if (dict != null) {
			_hangulDict.putAll(dict);
			for (String[] arr : dict.values()) {
				for (String s : arr) {
					_candidateFreq.put(s, 1);
				}
			}
			buildPrefixIndexForDict(_hangulDict, _hangulPrefixIndex);
		}
	}

	public void loadHangulDictionaryWithFreq(ObjectMap<String, String[]> dict, ObjectMap<String, Integer> freq) {
		loadHangulDictionary(dict);
		if (freq != null) {
			for (ObjectMap.Entry<String, Integer> e : freq.entries()) {
				_candidateFreq.put(e.getKey(), MathUtils.max(1, e.getValue()));
			}
		}
	}

	public LIMEInput loadDictFromFile(String path) {
		loadDictFromFile(path, _pinyinDict, _pinyinPrefixIndex);
		buildPrefixIndexForDict(_pinyinDict, _pinyinPrefixIndex);
		return this;
	}

	public LIMEInput loadDictFromFile(String path, ObjectMap<String, String[]> target,
			ObjectMap<String, TArray<String>> prefixIndex) {
		String context = BaseIO.loadText(path);
		if (StringUtils.isEmpty(context)) {
			LSystem.debug("Dict file not found: " + path);
			return this;
		}
		final StrTokenizer reader = new StrTokenizer(context, LSystem.NL);
		try {
			String line = null;
			while (reader.hasMoreTokens()) {
				line = reader.nextToken();
				if (line == null) {
					continue;
				}
				line = line.trim();
				if (line.length() == 0 || line.startsWith("#")) {
					continue;
				}
				int tabIdx = -1;
				for (int i = 0; i < line.length(); i++) {
					if (line.charAt(i) == '\t') {
						tabIdx = i;
						break;
					}
				}
				if (tabIdx >= 0) {
					String key = line.substring(0, tabIdx).trim().toLowerCase();
					String right = line.substring(tabIdx + 1).trim();
					String[] cands = StringUtils.splitColonComma(right);
					target.put(key, cands);
					String lk = key;
					int maxPrefix = MathUtils.min(3, lk.length());
					for (int len = 1; len <= maxPrefix; len++) {
						String p = lk.substring(0, len);
						TArray<String> list = prefixIndex.get(p);
						if (list == null) {
							list = new TArray<String>();
							prefixIndex.put(p, list);
						}
						for (String s : cands) {
							if (s != null && s.length() > 0 && !list.contains(s)) {
								list.add(s);
							}
						}
					}
					continue;
				}
				// 如果没有制表符，此时尝试简单JSON行解析
				if (line.startsWith("{") && line.endsWith("}")) {
					String body = line.substring(1, line.length() - 1).trim();
					int colonIdx = -1;
					for (int i = 0; i < body.length(); i++) {
						if (body.charAt(i) == ':') {
							colonIdx = i;
							break;
						}
					}
					if (colonIdx >= 0) {
						String rawKey = body.substring(0, colonIdx).trim();
						String rawVal = body.substring(colonIdx + 1).trim();
						String key = StringUtils.stripQuotes(rawKey).toLowerCase();
						String arrBody = rawVal;
						int leftBracket = -1;
						int rightBracket = -1;
						if (arrBody.startsWith("[")) {
							leftBracket = 0;
							for (int i = arrBody.length() - 1; i >= 0; i--) {
								if (arrBody.charAt(i) == ']') {
									rightBracket = i;
									break;
								}
							}
						} else {
							for (int i = 0; i < body.length(); i++) {
								if (body.charAt(i) == '[') {
									leftBracket = i - (1);
									break;
								}
							}
							for (int i = body.length() - 1; i >= 0; i--) {
								if (body.charAt(i) == ']') {
									rightBracket = i - (1);
									break;
								}
							}
						}
						if (leftBracket >= 0 && rightBracket >= 0 && rightBracket > leftBracket) {
							String arrayContent = arrBody.substring(leftBracket, rightBracket + 1).trim();
							if (arrayContent.startsWith("[")) {
								arrayContent = arrayContent.substring(1);
							}
							if (arrayContent.endsWith("]")) {
								arrayContent = arrayContent.substring(0, arrayContent.length() - 1);
							}
							String[] items = StringUtils.splitColonComma(arrayContent);
							for (int i = 0; i < items.length; i++) {
								items[i] = StringUtils.stripQuotes(items[i].trim());
							}
							target.put(key, items);
							String lk = key;
							int maxPrefix = MathUtils.min(3, lk.length());
							for (int len = 1; len <= maxPrefix; len++) {
								String p = lk.substring(0, len);
								TArray<String> list = prefixIndex.get(p);
								if (list == null) {
									list = new TArray<String>();
									prefixIndex.put(p, list);
								}
								for (String s : items) {
									if (s != null && s.length() > 0 && !list.contains(s)) {
										list.add(s);
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LSystem.debug("Error reading dict file: " + e.getMessage());
		}
		return this;
	}

	public LIMEInput loadFreqFromFile(String path, ObjectMap<String, Integer> freqTarget) {
		String context = BaseIO.loadText(path);
		if (context != null) {
			LSystem.debug("Freq file not found: " + path);
			return this;
		}
		final StrTokenizer reader = new StrTokenizer(context, LSystem.NL);
		try {
			String line = null;
			while (reader.hasMoreTokens()) {
				line = reader.nextToken();
				if (line == null) {
					continue;
				}
				line = line.trim();
				if (line.length() == 0 || line.startsWith("#")) {
					continue;
				}
				int tabIdx = -1;
				for (int i = 0; i < line.length(); i++) {
					if (line.charAt(i) == '\t') {
						tabIdx = i;
						break;
					}
				}
				if (tabIdx >= 0) {
					String cand = line.substring(0, tabIdx).trim();
					String freqStr = line.substring(tabIdx + 1).trim();
					int freq = 1;
					try {
						freq = Integer.parseInt(freqStr);
					} catch (NumberFormatException ex) {
						freq = 1;
					}
					freqTarget.put(cand, MathUtils.max(1, freq));
				} else {
					int lastSpace = -1;
					for (int i = line.length() - 1; i >= 0; i--) {
						if (Character.isWhitespace(line.charAt(i))) {
							lastSpace = i;
							break;
						}
					}
					if (lastSpace >= 0) {
						String cand = line.substring(0, lastSpace).trim();
						String freqStr = line.substring(lastSpace + 1).trim();
						int freq = 1;
						try {
							freq = Integer.parseInt(freqStr);
						} catch (NumberFormatException ex) {
							freq = 1;
						}
						freqTarget.put(cand, MathUtils.max(1, freq));
					}
				}
			}
		} catch (Exception e) {
			LSystem.debug("Error reading freq file: " + e.getMessage());
		}
		return this;
	}

	public float getCursorBlinkInterval() {
		return _cursorBlinkInterval;
	}

	public LIMEInput setCursorBlinkInterval(float c) {
		_cursorBlinkInterval = c;
		return this;
	}

	@Override
	public String getUIName() {
		return "IMEInput";
	}

	@Override
	public void destroy() {
		_dirty = true;
		_keyButtons.clear();
	}
}
