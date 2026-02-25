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
package loon.cport;

import java.util.function.Consumer;

import loon.NetworkClient;
import loon.NetworkMessageHandler;
import loon.cport.bridge.SocketCall;
import loon.utils.CollectionUtils;

public class CSocketClient implements NetworkClient {

	private int nativeSock;
	private boolean useTLS = false;
	private boolean running = false;
	private NetworkMessageHandler messageHandler;
	private Runnable openCallback;
	private Runnable closeCallback;
	private java.util.function.Consumer<Exception> errorCallback;

	@Override
	public void connect(String host, int port) {
		int initResult = SocketCall.socketInit();
		if (initResult != 0) {
			if (errorCallback != null) {
				errorCallback.accept(new RuntimeException("Init failed"));
			}
			return;
		}
		nativeSock = SocketCall.connectClient(host, port);
		if (nativeSock < 0) {
			if (errorCallback != null) {
				errorCallback.accept(new RuntimeException("Connect failed"));
			}
			return;
		}
		if (openCallback != null) {
			openCallback.run();
		}
		running = true;
	}

	@Override
	public void connect(String uri) {
		try {
			String[] parts = uri.split(":");
			if (parts.length == 2) {
				String host = parts[0];
				int port = Integer.parseInt(parts[1]);
				connect(host, port);
			} else {
				connect(uri, 80);
			}
		} catch (Exception e) {
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
	}

	@Override
	public void send(byte[] data) {
		int ret = SocketCall.socketSend(nativeSock, data, data.length);
		if (ret < 0 && errorCallback != null) {
			errorCallback.accept(new RuntimeException("Send failed"));
		}
	}

	@Override
	public void sendAndWait(byte[] data, int timeoutMillis) {
		SocketCall.socketTimeout(nativeSock, timeoutMillis, timeoutMillis);
		send(data);
	}

	@Override
	public void onMessage(NetworkMessageHandler handler) {
		this.messageHandler = handler;
		new Thread(() -> {
			while (running && nativeSock > 0) {
				byte[] buffer = new byte[1024];
				int len = SocketCall.socketRecv(nativeSock, buffer, buffer.length);
				if (len > 0 && handler != null) {
					handler.handleMessage(CollectionUtils.copyOf(buffer, len));
				}
			}
		}).start();
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
		SocketCall.socketClose(nativeSock);
		SocketCall.socketFree();
		nativeSock = 0;
		if (closeCallback != null) {
			closeCallback.run();
		}
		running = false;
	}

	@Override
	public void setTimeout(int millis) {
		SocketCall.socketTimeout(nativeSock, millis, millis);
	}

	@Override
	public void enableHeartbeat(int intervalMillis, byte[] pingData) {
		new Thread(() -> {
			while (running && nativeSock > 0) {
				send(pingData);
				try {
					Thread.sleep(intervalMillis);
				} catch (InterruptedException ignored) {
				}
			}
		}).start();
	}

	@Override
	public void enableAutoReconnect(int retryIntervalMillis) {
		new Thread(() -> {
			while (running) {
				if (nativeSock <= 0) {
					try {
						Thread.sleep(retryIntervalMillis);
					} catch (InterruptedException ignored) {
					}
				}
			}
		}).start();
	}

	@Override
	public void enableTLS(boolean enabled) {
		useTLS = enabled;
	}

	public boolean isUseTLS() {
		return useTLS;
	}

	@Override
	public NetworkMessageHandler getMessageHandler() {
		return messageHandler;
	}

}
