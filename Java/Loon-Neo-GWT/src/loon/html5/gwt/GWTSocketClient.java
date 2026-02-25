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
package loon.html5.gwt;

import java.util.function.Consumer;
import loon.NetworkClient;
import loon.NetworkMessageHandler;

import com.google.gwt.typedarrays.shared.Uint8Array;

public class GWTSocketClient implements NetworkClient {

	private NetworkMessageHandler messageHandler;
	private Runnable openCallback;
	private Runnable closeCallback;
	private Consumer<Exception> errorCallback;
	private Object ws;
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
		createWebSocket(uri);
	}

	@Override
	public void connect(String uri) {
		try {
			if (!uri.startsWith("ws://") && !uri.startsWith("wss://")) {
				uri = (useTLS ? "wss://" : "ws://") + uri;
			}
			lastUri = uri;
			createWebSocket(uri);
		} catch (Exception e) {
			if (errorCallback != null)
				errorCallback.accept(e);
		}
	}

	@Override
	public void send(byte[] data) {
		sendWebSocketBinary(data);
	}

	@Override
	public void sendAndWait(byte[] data, int timeoutMillis) {
		sendWebSocketBinary(data);
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
		closeWebSocket();
		if (closeCallback != null) {
			closeCallback.run();
		}
	}

	@Override
	public void setTimeout(int millis) {
		scheduleTimeout(millis);
	}

	@Override
	public void enableHeartbeat(int intervalMillis, byte[] pingData) {
		startHeartbeat(new String(pingData), intervalMillis);
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

	private native void createWebSocket(String uri) /*-{
        var self = this;
        var ws = new WebSocket(uri);
        ws.binaryType = "arraybuffer";
        self.@loon.html5.gwt.GWTSocketClient::ws = ws;

        ws.onopen = function() {
            var cb = self.@loon.html5.gwt.GWTSocketClient::openCallback;
            if (cb != null) cb.@java.lang.Runnable::run()();
        };

        ws.onmessage = function(evt) {
            var handler = self.@loon.html5.gwt.GWTSocketClient::messageHandler;
            if (handler != null) {
                var data = evt.data;
                if (typeof data === "string") {
                    var bytes = @loon.html5.gwt.GWTSocketClient::toBytes(Ljava/lang/String;)(data);
                    handler.@loon.NetworkMessageHandler::handleMessage([B)(bytes);
                } else if (data instanceof ArrayBuffer) {
                    var arr = @com.google.gwt.typedarrays.shared.TypedArrays::createUint8Array(Lcom/google/gwt/typedarrays/shared/ArrayBuffer;)(data);
                    var bytes = @loon.html5.gwt.GWTSocketClient::toBytesFromArrayBuffer(Lcom/google/gwt/typedarrays/shared/Uint8Array;)(arr);
                    handler.@loon.NetworkMessageHandler::handleMessage([B)(bytes);
                } else if (data instanceof Blob) {
                    var reader = new FileReader();
                    reader.onload = function() {
                        var arr = @com.google.gwt.typedarrays.shared.TypedArrays::createUint8Array(Lcom/google/gwt/typedarrays/shared/ArrayBuffer;)(this.result);
                        var bytes = @loon.html5.gwt.GWTSocketClient::toBytesFromArrayBuffer(Lcom/google/gwt/typedarrays/shared/Uint8Array;)(arr);
                        handler.@loon.NetworkMessageHandler::handleMessage([B)(bytes);
                    };
                    reader.readAsArrayBuffer(data);
                }
            }
        };

        ws.onerror = function() {
            var cb = self.@loon.html5.gwt.GWTSocketClient::errorCallback;
            if (cb != null) cb.@java.util.function.Consumer::accept(Ljava/lang/Object;)(@java.lang.RuntimeException::new(Ljava/lang/String;)("WebSocket error"));
        };

        ws.onclose = function() {
            var cb = self.@loon.html5.gwt.GWTSocketClient::closeCallback;
            if (cb != null) cb.@java.lang.Runnable::run()();
            self.@loon.html5.gwt.GWTSocketClient::tryReconnect()();
        };
    }-*/;

	private native void sendWebSocketBinary(byte[] data) /*-{
		var ws = this.@loon.html5.gwt.GWTSocketClient::ws;
		if (ws && ws.readyState === WebSocket.OPEN) {
			var len = data.length;
			var buffer = new Uint8Array(len);
			for (var i = 0; i < len; i++) {
				buffer[i] = data[i] & 0xFF;
			}
			ws.send(buffer);
		}
	}-*/;

	private native void closeWebSocket() /*-{
		var ws = this.@loon.html5.gwt.GWTSocketClient::ws;
		if (ws)
			ws.close();
	}-*/;

	private native void startHeartbeat(String pingData, int intervalMillis) /*-{
		var self = this;
		$wnd.setInterval(function() {
			var ws = self.@loon.html5.gwt.GWTSocketClient::ws;
			if (ws && ws.readyState === WebSocket.OPEN) {
				ws.send(pingData);
			}
		}, intervalMillis);
	}-*/;

	private native void scheduleReconnect(String uri, int intervalMillis) /*-{
		var self = this;
		$wnd
				.setTimeout(
						function() {
							self.@loon.html5.gwt.GWTSocketClient::createWebSocket(Ljava/lang/String;)(uri);
						}, intervalMillis);
	}-*/;

	private native void scheduleTimeout(int millis) /*-{
        var self = this;
        $wnd.setTimeout(function() {
            var ws = self.@loon.html5.gwt.GWTSocketClient::ws;
            if (ws && ws.readyState !== WebSocket.OPEN) {
                self.@loon.html5.gwt.GWTSocketClient::close()();
                var cb = self.@loon.html5.gwt.GWTSocketClient::errorCallback;
                if (cb != null) cb.@java.util.function.Consumer::accept(Ljava/lang/Object;)(@java.lang.RuntimeException::new(Ljava/lang/String;)("WebSocket timeout"));
            }
        }, millis);
    }-*/;

	private static byte[] toBytes(String str) {
		return str.getBytes();
	}

	private static byte[] toBytesFromArrayBuffer(Uint8Array arr) {
		int len = arr.length();
		byte[] bytes = new byte[len];
		for (int i = 0; i < len; i++) {
			bytes[i] = (byte) arr.get(i);
		}
		return bytes;
	}
}
