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
package loon.teavm;

import java.util.function.Consumer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import loon.NetworkClient;
import loon.NetworkMessageHandler;

public class TeaSocketClient implements NetworkClient {

	private NetworkMessageHandler messageHandler;
	private Runnable openCallback;
	private Runnable closeCallback;
	private Consumer<Exception> errorCallback;
	private JSObject ws;
	private int reconnectInterval = 0;
	private String lastUri;
	private boolean useTLS = false;

	private void tryReconnect() {
		if (lastUri != null && reconnectInterval > 0) {
			scheduleReconnect(lastUri, reconnectInterval);
		}
	}

	@Override
	public void connect(final String host, int port) {
		String scheme = useTLS ? "wss://" : "ws://";
		String uri = scheme + host + ":" + port;
		lastUri = uri;
		ws = createWebSocket(uri);

		setOnOpen(ws, () -> {
			if (openCallback != null)
				openCallback.run();
		});

		setOnMessage(ws, data -> {
			if (messageHandler != null)
				messageHandler.handleMessage(data);
		});

		setOnError(ws, err -> {
			if (errorCallback != null)
				errorCallback.accept(new RuntimeException(err));
		});

		setOnClose(ws, () -> {
			if (closeCallback != null)
				closeCallback.run();
			tryReconnect();
		});
	}

	@Override
	public void connect(String uri) {
		try {
			if (!uri.startsWith("ws://") && !uri.startsWith("wss://")) {
				uri = (useTLS ? "wss://" : "ws://") + uri;
			}
			lastUri = uri;
			ws = createWebSocket(uri);
		} catch (Exception e) {
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
	}

	@Override
	public void send(byte[] data) {
		sendWebSocketBinary(ws, data);
	}

	@Override
	public void sendAndWait(byte[] data, int timeoutMillis) {
		sendWebSocketBinary(ws, data);
	}

	@Override
	public void onMessage(NetworkMessageHandler handler) {
		this.messageHandler = handler;
	}

	@Override
	public void onOpen(Runnable callback) {
		this.openCallback = callback;
	}

	@Override
	public void onClose(Runnable callback) {
		this.closeCallback = callback;
	}

	@Override
	public void onError(Consumer<Exception> callback) {
		this.errorCallback = callback;
	}

	@Override
	public void close() {
		closeWebSocket(ws);
		if (closeCallback != null)
			closeCallback.run();
	}

	@Override
	public void setTimeout(int millis) {
		scheduleTimeout(ws, millis);
	}

	@Override
	public void enableHeartbeat(int intervalMillis, byte[] pingData) {
		startHeartbeat(ws, pingData, intervalMillis);
	}

	@Override
	public void enableAutoReconnect(int retryIntervalMillis) {
		this.reconnectInterval = retryIntervalMillis;
	}

	@Override
	public void enableTLS(boolean enabled) {
		this.useTLS = enabled;
	}

	@Override
	public NetworkMessageHandler getMessageHandler() {
		return messageHandler;
	}

	@JSBody(params = { "uri" }, script = "return new WebSocket(uri);")
	private static native JSObject createWebSocket(String uri);

	@JSBody(params = { "ws", "data" }, script = "if(ws && ws.readyState === WebSocket.OPEN){"
			+ " var buffer = new Uint8Array(data.length);"
			+ " for(var i=0;i<data.length;i++){ buffer[i] = data[i] & 0xFF; }" + " ws.send(buffer); }")
	private static native void sendWebSocketBinary(JSObject ws, byte[] data);

	@JSBody(params = { "ws" }, script = "if(ws) ws.close();")
	private static native void closeWebSocket(JSObject ws);

	@JSBody(params = { "ws", "callback" }, script = "ws.onopen = function(){ callback.$run(); };")
	private static native void setOnOpen(JSObject ws, Runnable callback);

	@JSFunctor
	public interface MessageConsumer extends JSObject {
		void accept(byte[] data);
	}

	@JSBody(params = { "ws", "callback" }, script = "ws.onmessage = function(evt){" + " var data = evt.data;"
			+ " if(typeof data === 'string'){" + "   var strBytes = new TextEncoder().encode(data);"
			+ "   callback.$accept(strBytes);" + " } else if(data instanceof ArrayBuffer){"
			+ "   var arr = new Uint8Array(data);" + "   callback.$accept(arr);" + " } else if(data instanceof Blob){"
			+ "   var reader = new FileReader();" + "   reader.onload = function(){"
			+ "     var arr = new Uint8Array(this.result);" + "     callback.$accept(arr);" + "   };"
			+ "   reader.readAsArrayBuffer(data);" + " } };")
	private static native void setOnMessage(JSObject ws, MessageConsumer callback);

	@JSBody(params = { "ws", "callback" }, script = "ws.onerror = function(){ callback.$accept('WebSocket error'); };")
	private static native void setOnError(JSObject ws, java.util.function.Consumer<String> callback);

	@JSBody(params = { "ws", "callback" }, script = "ws.onclose = function(){ callback.$run(); };")
	private static native void setOnClose(JSObject ws, Runnable callback);

	@JSBody(params = { "ws", "pingData",
			"intervalMillis" }, script = "setInterval(function(){ if(ws.readyState === WebSocket.OPEN){ ws.send(new Uint8Array(pingData)); } }, intervalMillis);")
	private static native void startHeartbeat(JSObject ws, byte[] pingData, int intervalMillis);

	@JSBody(params = { "uri",
			"intervalMillis" }, script = "setTimeout(function(){ new WebSocket(uri); }, intervalMillis);")
	private static native void scheduleReconnect(String uri, int intervalMillis);

	@JSBody(params = { "ws",
			"millis" }, script = "setTimeout(function(){ if(ws.readyState !== WebSocket.OPEN){ ws.close(); } }, millis);")
	private static native void scheduleTimeout(JSObject ws, int millis);
}
