/**
 * Copyright 2008 - 2020 The Loon Game Engine Authors
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
package loon.html5.gwt;

import loon.Clipboard;

public class GWTClipboard extends Clipboard {

	private String content = "";

	@Override
	public String getContent() {
		return content;
	}

	@Override
	public void setContent(String content) {
		try {
			setContentJSNI(this.content = content);
		} catch (Throwable ex) {
		}
	}

	private native void setContentJSNI(String content) /*-{
		if ("clipboard" in $wnd.navigator) {
			$wnd.navigator.clipboard.writeText(content);
		}
	}-*/;

}