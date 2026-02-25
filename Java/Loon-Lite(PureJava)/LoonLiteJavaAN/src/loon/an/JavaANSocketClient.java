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
package loon.an;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import loon.NetworkClient;
import loon.NetworkMessageHandler;

public class JavaANSocketClient implements NetworkClient {

	private Socket socket;
	private NetworkMessageHandler messageHandler;
	private Runnable openCallback;
	private Runnable closeCallback;
	private Consumer<Exception> errorCallback;
	private int reconnectInterval = 0;
	private String lastHost;
	private int lastPort;
	private boolean useTLS = false;
	private Timer heartbeatTimer;
	private Thread readerThread;

	private final BlockingQueue<byte[]> responseQueue = new ArrayBlockingQueue<byte[]>(1);

	private void tryReconnect() {
		if (lastHost != null && reconnectInterval > 0) {
			new Timer().schedule(new TimerTask() {
				@Override
				public void run() {
					connect(lastHost, lastPort);
				}
			}, reconnectInterval);
		}
	}

	@Override
	public void connect(final String host, int port) {
		this.lastHost = host;
		this.lastPort = port;
		try {
			socket = new Socket();
			socket.connect(new InetSocketAddress(host, port));
			if (openCallback != null) {
				openCallback.run();
			}
			readerThread = new Thread(() -> {
				try (InputStream in = socket.getInputStream()) {
					byte[] buffer = new byte[4096];
					int len;
					while ((len = in.read(buffer)) != -1) {
						byte[] data = new byte[len];
						System.arraycopy(buffer, 0, data, 0, len);
						if (messageHandler != null) {
							messageHandler.handleMessage(data);
						}
						responseQueue.offer(data);
					}
				} catch (IOException e) {
					if (errorCallback != null) {
						errorCallback.accept(e);
					}
				} finally {
					if (closeCallback != null) {
						closeCallback.run();
					}
					tryReconnect();
				}
			});
			readerThread.start();

		} catch (IOException e) {
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
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
			if (errorCallback != null)
				errorCallback.accept(e);
		}
	}

	@Override
	public void send(byte[] data) {
		try {
			OutputStream out = socket.getOutputStream();
			out.write(data);
			out.flush();
		} catch (IOException e) {
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
	}

	@Override
	public void sendAndWait(byte[] data, int timeoutMillis) {
		send(data);
		getSendData(timeoutMillis);
	}

	public byte[] getSendData(int timeoutMillis) {
		try {
			return responseQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
		return null;
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
		try {
			if (socket != null) {
				socket.close();
			}
		} catch (IOException e) {
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
		if (closeCallback != null) {
			closeCallback.run();
		}
	}

	@Override
	public void setTimeout(int millis) {
		try {
			if (socket != null) {
				socket.setSoTimeout(millis);
			}
		} catch (IOException e) {
			if (errorCallback != null) {
				errorCallback.accept(e);
			}
		}
	}

	@Override
	public void enableHeartbeat(int intervalMillis, byte[] pingData) {
		if (heartbeatTimer != null) {
			heartbeatTimer.cancel();
		}
		heartbeatTimer = new Timer(true);
		heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				send(pingData);
			}
		}, intervalMillis, intervalMillis);
	}

	@Override
	public void enableAutoReconnect(int retryIntervalMillis) {
		this.reconnectInterval = retryIntervalMillis;
	}

	@Override
	public void enableTLS(boolean enabled) {
		this.useTLS = enabled;
	}

	public boolean isUseTLS() {
		return useTLS;
	}

	@Override
	public NetworkMessageHandler getMessageHandler() {
		return messageHandler;
	}
}
