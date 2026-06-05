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
package loon.canvas;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import loon.utils.BufferUtils;
import loon.utils.CharUtils;
import loon.utils.CollectionUtils;
import loon.utils.IntArray;
import loon.utils.ObjectMap;

/**
 * 字节转化用类，转化特定的字节流为图片
 */
public final class PixmapConverter {

	private static class TiffTag {

		int type;
		int count;
		int valueOffset;

		TiffTag(int type, int count, int valueOffset) {
			this.type = type;
			this.count = count;
			this.valueOffset = valueOffset;
		}
	}

	private static class BitInputStream {

		private final InputStream in;
		private int bitBuffer = 0;
		private int bitCount = 0;

		BitInputStream(InputStream in) {
			this.in = in;
		}

		int readBits(int n) throws IOException {
			while (bitCount < n) {
				int b = in.read();
				if (b == -1) {
					return -1;
				}
				bitBuffer |= (b & 0xFF) << bitCount;
				bitCount += 8;
			}
			int mask = (1 << n) - 1;
			int val = bitBuffer & mask;
			bitBuffer >>>= n;
			bitCount -= n;
			return val;
		}
	}

	public enum Format {
		PNG, BMP, GIF, JPG, PPM, TGA, ICO, RAW, WEBP, TIFF, UNKNOWN
	}

	private PixmapConverter() {
	}

	public static Pixmap parseToPixmap(byte[] data) throws IOException {
		Format f = detectFormat(data);
		if (f == Format.PNG) {
			return parsePNG(data);
		}
		if (f == Format.BMP) {
			return parseBMP(data);
		}
		if (f == Format.GIF) {
			return parseGIF(data);
		}
		if (f == Format.PPM) {
			Pixmap p = parsePPM(data);
			if (p != null) {
				return p;
			}
		}
		if (f == Format.TGA) {
			Pixmap t = parseTGA(data);
			if (t != null) {
				return t;
			}
		}
		if (f == Format.ICO) {
			return parseICO(data);
		}
		if (f == Format.TIFF) {
			return parseTIFF(data);
		}
		if (f == Format.WEBP) {
			return parseWebP(data);
		}
		Pixmap raw = tryParseRawWithHeader(data);
		if (raw != null) {
			return raw;
		}
		Pixmap rawGuess = tryParseRawGuess(data);
		if (rawGuess != null) {
			return rawGuess;
		}
		throw new IOException("Unknown or unsupported image format");
	}

	public static Format detectFormat(byte[] data) {
		if (data == null || data.length < 4) {
			return Format.UNKNOWN;
		}
		if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
			return Format.PNG;
		}
		if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
			return Format.JPG;
		}
		if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') {
			return Format.GIF;
		}
		if (data[0] == 'B' && data[1] == 'M') {
			return Format.BMP;
		}
		if (data[0] == 'P' && data[1] == '6') {
			return Format.PPM;
		}
		if (data.length >= 4) {
			if ((data[0] == 'I' && data[1] == 'I' && data[2] == 0x2A && data[3] == 0x00)
					|| (data[0] == 'M' && data[1] == 'M' && data[2] == 0x00 && data[3] == 0x2A)) {
				return Format.TIFF;
			}
		}
		if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W'
				&& data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
			return Format.WEBP;
		}
		if (data.length >= 6 && data[0] == 0 && data[1] == 0 && (data[2] == 1 || data[2] == 2)) {
			return Format.ICO;
		}
		return Format.UNKNOWN;
	}

	private static Pixmap parseBMP(byte[] data) throws IOException {
		if (data.length < 54) {
			throw new IOException("Invalid BMP");
		}
		int pos = 0;
		if (data[pos++] != 'B' || data[pos++] != 'M') {
			throw new IOException("Not a BMP");
		}

		pos += 4;
		pos += 4;
		int bfOffBits = BufferUtils.readLEInt(data, pos);
		pos += 4;

		int biSize = BufferUtils.readLEInt(data, pos);
		pos += 4;
		if (biSize < 40) {
			throw new IOException("Unsupported BMP header");
		}
		int biWidth = BufferUtils.readLEInt(data, pos);
		pos += 4;
		int biHeight = BufferUtils.readLEInt(data, pos);
		pos += 4;

		pos += 2;
		int biBitCount = BufferUtils.readLEShort(data, pos);
		pos += 2;
		int biCompression = BufferUtils.readLEInt(data, pos);
		pos += 4;

		pos += 4;
		pos += 16;

		if (biCompression != 0) {
			throw new IOException("Compressed BMP not supported");
		}
		int width = biWidth;
		int height = Math.abs(biHeight);
		boolean topDown = biHeight < 0;
		int bytesPerPixel = biBitCount / 8;
		if (bytesPerPixel != 3 && bytesPerPixel != 4) {
			throw new IOException("Only 24/32-bit BMP supported");
		}
		int rowSize = ((biBitCount * width + 31) / 32) * 4;
		byte[] rgba = new byte[width * height * 4];

		for (int row = 0; row < height; row++) {
			int readRow = topDown ? row : (height - 1 - row);
			int rowStart = bfOffBits + row * rowSize;
			if (rowStart + bytesPerPixel * width > data.length) {
				throw new IOException("BMP truncated");
			}
			int p = rowStart;
			for (int col = 0; col < width; col++) {
				int b = data[p++] & 0xFF;
				int g = data[p++] & 0xFF;
				int r = data[p++] & 0xFF;
				int a = 0xFF;
				if (bytesPerPixel == 4) {
					a = data[p++] & 0xFF;
				}
				int idx = (readRow * width + col) * 4;
				rgba[idx] = (byte) r;
				rgba[idx + 1] = (byte) g;
				rgba[idx + 2] = (byte) b;
				rgba[idx + 3] = (byte) a;
			}
		}
		return new Pixmap(rgba, width, height);
	}

	private static Pixmap parsePNG(byte[] data) throws IOException {
		if (data.length < 8) {
			throw new IOException("Invalid PNG");
		}
		byte[] pngSig = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		for (int i = 0; i < 8; i++) {
			if (data[i] != pngSig[i]) {
				throw new IOException("Invalid PNG signature");
			}
		}
		int pos = 8;
		int width = -1, height = -1, bitDepth = -1, colorType = -1, compression = -1, filter = -1, interlace = -1;
		byte[] palette = null;
		byte[] transPalette = null;
		ByteArrayOutputStream idatStream = new ByteArrayOutputStream();

		while (pos + 8 <= data.length) {
			int length = BufferUtils.readIntBE(data, pos);
			pos += 4;
			if (pos + 4 > data.length) {
				throw new IOException("Truncated PNG");
			}
			String chunkType = new String(data, pos, 4, "ASCII");
			pos += 4;
			if (pos + length > data.length) {
				throw new IOException("Truncated PNG chunk");
			}
			byte[] chunkData = new byte[length];
			System.arraycopy(data, pos, chunkData, 0, length);
			pos += length;

			pos += 4;

			if ("IHDR".equals(chunkType)) {
				width = BufferUtils.readIntBE(chunkData, 0);
				height = BufferUtils.readIntBE(chunkData, 4);
				bitDepth = chunkData[8] & 0xFF;
				colorType = chunkData[9] & 0xFF;
				compression = chunkData[10] & 0xFF;
				filter = chunkData[11] & 0xFF;
				interlace = chunkData[12] & 0xFF;
				if (compression != 0 || filter != 0) {
					throw new IOException("Unsupported PNG compression/filter");
				}
			} else if ("PLTE".equals(chunkType)) {
				palette = chunkData;
			} else if ("tRNS".equals(chunkType)) {
				transPalette = chunkData;
			} else if ("IDAT".equals(chunkType)) {
				idatStream.write(chunkData);
			} else if ("IEND".equals(chunkType)) {
				break;
			}
		}

		if (width <= 0 || height <= 0) {
			throw new IOException("Invalid PNG IHDR");
		}

		byte[] compressed = idatStream.toByteArray();
		byte[] raw;

		try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
				java.util.zip.InflaterInputStream iis = new java.util.zip.InflaterInputStream(bais);
				ByteArrayOutputStream decompressed = new ByteArrayOutputStream()) {
			byte[] buf = new byte[8192];
			int r;
			while ((r = iis.read(buf)) != -1) {
				decompressed.write(buf, 0, r);
			}
			raw = decompressed.toByteArray();
		} catch (IOException e) {
			throw new IOException("PNG inflate failed (InflaterInputStream)", e);
		}

		if (interlace == 0) {
			return parseNonInterlacedPNG(width, height, bitDepth, colorType, raw, palette, transPalette);
		} else {
			return deinterlaceAdam7AndConvertToRGBA(width, height, bitDepth, colorType, raw, palette, transPalette);
		}
	}

	private static Pixmap parseNonInterlacedPNG(int width, int height, int bitDepth, int colorType, byte[] raw,
			byte[] palette, byte[] transPalette) throws IOException {
		boolean isPalette = (colorType == 3);
		int samplesPerPixel;
		switch (colorType) {
		case 0:
			samplesPerPixel = 1;
			break;
		case 2:
			samplesPerPixel = 3;
			break;
		case 3:
			samplesPerPixel = 1;
			break;
		case 4:
			samplesPerPixel = 2;
			break;
		case 6:
			samplesPerPixel = 4;
			break;
		default:
			throw new IOException("Unsupported PNG color type: " + colorType);
		}

		int bytesPerSample = (bitDepth <= 8) ? 1 : 2;
		int expectedRowBytes;
		if (isPalette) {
			expectedRowBytes = ((width * bitDepth) + 7) / 8;
		} else {
			expectedRowBytes = width * samplesPerPixel * bytesPerSample;
		}

		byte[] rgba = new byte[width * height * 4];
		int inPos = 0;
		byte[] prevLine = new byte[expectedRowBytes];
		byte[] curLine = new byte[expectedRowBytes];

		for (int y = 0; y < height; y++) {
			if (inPos >= raw.length) {
				throw new IOException("PNG truncated");
			}
			int filterType = raw[inPos++] & 0xFF;
			if (inPos + expectedRowBytes > raw.length) {
				throw new IOException("PNG truncated row");
			}
			System.arraycopy(raw, inPos, curLine, 0, expectedRowBytes);
			inPos += expectedRowBytes;

			int stride = isPalette ? Math.max(1, (bitDepth + 7) / 8) : samplesPerPixel * bytesPerSample;
			switch (filterType) {
			case 0:
				break;
			case 1:
				for (int i = stride; i < expectedRowBytes; i++) {
					int val = (curLine[i] & 0xFF) + (curLine[i - stride] & 0xFF);
					curLine[i] = (byte) (val & 0xFF);
				}
				break;
			case 2:
				for (int i = 0; i < expectedRowBytes; i++) {
					int val = (curLine[i] & 0xFF) + (prevLine[i] & 0xFF);
					curLine[i] = (byte) (val & 0xFF);
				}
				break;
			case 3:
				for (int i = 0; i < expectedRowBytes; i++) {
					int left = (i - stride) >= 0 ? (curLine[i - stride] & 0xFF) : 0;
					int up = prevLine[i] & 0xFF;
					int val = (curLine[i] & 0xFF) + ((left + up) >> 1);
					curLine[i] = (byte) (val & 0xFF);
				}
				break;
			case 4:
				for (int i = 0; i < expectedRowBytes; i++) {
					int a = (i - stride) >= 0 ? (curLine[i - stride] & 0xFF) : 0;
					int b = prevLine[i] & 0xFF;
					int c = (i - stride) >= 0 ? (prevLine[i - stride] & 0xFF) : 0;
					int p = a + b - c;
					int pa = Math.abs(p - a);
					int pb = Math.abs(p - b);
					int pc = Math.abs(p - c);
					int pr;
					if (pa <= pb && pa <= pc) {
						pr = a;
					} else if (pb <= pc) {
						pr = b;
					} else {
						pr = c;
					}
					int val = (curLine[i] & 0xFF) + pr;
					curLine[i] = (byte) (val & 0xFF);
				}
				break;
			default:
				throw new IOException("Unsupported PNG filter: " + filterType);
			}

			int outBase = y * width * 4;
			if (isPalette) {
				for (int x = 0; x < width; x++) {
					int idx = BufferUtils.getPackedSample(curLine, x, bitDepth);
					int palPos = idx * 3;
					int r = 0, g = 0, b = 0;
					int a = 0xFF;
					if (palette != null && palPos + 2 < palette.length) {
						r = palette[palPos] & 0xFF;
						g = palette[palPos + 1] & 0xFF;
						b = palette[palPos + 2] & 0xFF;
					}
					if (transPalette != null && idx < transPalette.length) {
						a = transPalette[idx] & 0xFF;
					}
					int base = outBase + x * 4;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) a;
				}
			} else {
				if (bytesPerSample == 1) {
					if (samplesPerPixel == 1) {
						for (int x = 0; x < width; x++) {
							int g = curLine[x] & 0xFF;
							int base = outBase + x * 4;
							rgba[base] = (byte) g;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) g;
							rgba[base + 3] = (byte) 0xFF;
						}
					} else if (samplesPerPixel == 2) {
						for (int x = 0; x < width; x++) {
							int p = x * 2;
							int g = curLine[p] & 0xFF;
							int a = curLine[p + 1] & 0xFF;
							int base = outBase + x * 4;
							rgba[base] = (byte) g;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) g;
							rgba[base + 3] = (byte) a;
						}
					} else if (samplesPerPixel == 3) {
						for (int x = 0; x < width; x++) {
							int p = x * 3;
							int r = curLine[p] & 0xFF;
							int g = curLine[p + 1] & 0xFF;
							int b = curLine[p + 2] & 0xFF;
							int base = outBase + x * 4;
							rgba[base] = (byte) r;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) b;
							rgba[base + 3] = (byte) 0xFF;
						}
					} else if (samplesPerPixel == 4) {
						for (int x = 0; x < width; x++) {
							int p = x * 4;
							int r = curLine[p] & 0xFF;
							int g = curLine[p + 1] & 0xFF;
							int b = curLine[p + 2] & 0xFF;
							int a = curLine[p + 3] & 0xFF;
							int base = outBase + x * 4;
							rgba[base] = (byte) r;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) b;
							rgba[base + 3] = (byte) a;
						}
					}
				} else {
					if (samplesPerPixel == 3) {
						for (int x = 0; x < width; x++) {
							int p = x * 6;
							int r = curLine[p] & 0xFF;
							int g = curLine[p + 2] & 0xFF;
							int b = curLine[p + 4] & 0xFF;
							int base = outBase + x * 4;
							rgba[base] = (byte) r;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) b;
							rgba[base + 3] = (byte) 0xFF;
						}
					} else if (samplesPerPixel == 4) {
						for (int x = 0; x < width; x++) {
							int p = x * 8;
							int r = curLine[p] & 0xFF;
							int g = curLine[p + 2] & 0xFF;
							int b = curLine[p + 4] & 0xFF;
							int a = curLine[p + 6] & 0xFF;
							int base = outBase + x * 4;
							rgba[base] = (byte) r;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) b;
							rgba[base + 3] = (byte) a;
						}
					} else {
						throw new IOException("Unsupported PNG 16-bit sample layout");
					}
				}
			}

			System.arraycopy(curLine, 0, prevLine, 0, curLine.length);
		}

		return new Pixmap(rgba, width, height);
	}

	private static Pixmap deinterlaceAdam7AndConvertToRGBA(int width, int height, int bitDepth, int colorType,
			byte[] decompressedIDAT, byte[] palette, byte[] transPalette) throws IOException {
		final int[] ADAM7_X_START = { 0, 4, 0, 2, 0, 1, 0 };
		final int[] ADAM7_Y_START = { 0, 0, 4, 0, 2, 0, 1 };
		final int[] ADAM7_X_STEP = { 8, 8, 4, 4, 2, 2, 1 };
		final int[] ADAM7_Y_STEP = { 8, 8, 8, 4, 4, 2, 2 };

		boolean isPalette = (colorType == 3);
		int samplesPerPixel;
		switch (colorType) {
		case 0:
			samplesPerPixel = 1;
			break;
		case 2:
			samplesPerPixel = 3;
			break;
		case 3:
			samplesPerPixel = 1;
			break;
		case 4:
			samplesPerPixel = 2;
			break;
		case 6:
			samplesPerPixel = 4;
			break;
		default:
			throw new IOException("Unsupported PNG color type for Adam7: " + colorType);
		}
		int bytesPerSample = (bitDepth <= 8) ? 1 : 2;
		byte[] rgba = new byte[width * height * 4];
		for (int i = 0; i < rgba.length; i += 4) {
			rgba[i] = 0;
			rgba[i + 1] = 0;
			rgba[i + 2] = 0;
			rgba[i + 3] = (byte) 0xFF;
		}

		int inPos = 0;
		for (int pass = 0; pass < 7; pass++) {
			int passX0 = ADAM7_X_START[pass];
			int passY0 = ADAM7_Y_START[pass];
			int stepX = ADAM7_X_STEP[pass];
			int stepY = ADAM7_Y_STEP[pass];

			int passWidth = (width - passX0 + stepX - 1) / stepX;
			int passHeight = (height - passY0 + stepY - 1) / stepY;
			if (passWidth <= 0 || passHeight <= 0) {
				continue;
			}
			int expectedRowBytes;
			if (isPalette) {
				expectedRowBytes = ((passWidth * bitDepth) + 7) / 8;
			} else {
				expectedRowBytes = passWidth * samplesPerPixel * bytesPerSample;
			}

			byte[] prevLine = new byte[expectedRowBytes];
			byte[] curLine = new byte[expectedRowBytes];

			for (int row = 0; row < passHeight; row++) {
				if (inPos >= decompressedIDAT.length) {
					throw new IOException("PNG Adam7 truncated");
				}
				int filterType = decompressedIDAT[inPos++] & 0xFF;
				if (inPos + expectedRowBytes > decompressedIDAT.length) {
					throw new IOException("PNG Adam7 truncated row");
				}
				System.arraycopy(decompressedIDAT, inPos, curLine, 0, expectedRowBytes);
				inPos += expectedRowBytes;

				int stride = isPalette ? Math.max(1, (bitDepth + 7) / 8) : samplesPerPixel * bytesPerSample;
				switch (filterType) {
				case 0:
					break;
				case 1:
					for (int i = stride; i < expectedRowBytes; i++) {
						int val = (curLine[i] & 0xFF) + (curLine[i - stride] & 0xFF);
						curLine[i] = (byte) (val & 0xFF);
					}
					break;
				case 2:
					for (int i = 0; i < expectedRowBytes; i++) {
						int val = (curLine[i] & 0xFF) + (prevLine[i] & 0xFF);
						curLine[i] = (byte) (val & 0xFF);
					}
					break;
				case 3:
					for (int i = 0; i < expectedRowBytes; i++) {
						int left = (i - stride) >= 0 ? (curLine[i - stride] & 0xFF) : 0;
						int up = prevLine[i] & 0xFF;
						int val = (curLine[i] & 0xFF) + ((left + up) >> 1);
						curLine[i] = (byte) (val & 0xFF);
					}
					break;
				case 4:
					for (int i = 0; i < expectedRowBytes; i++) {
						int a = (i - stride) >= 0 ? (curLine[i - stride] & 0xFF) : 0;
						int b = prevLine[i] & 0xFF;
						int c = (i - stride) >= 0 ? (prevLine[i - stride] & 0xFF) : 0;
						int p = a + b - c;
						int pa = Math.abs(p - a);
						int pb = Math.abs(p - b);
						int pc = Math.abs(p - c);
						int pr;
						if (pa <= pb && pa <= pc) {
							pr = a;
						} else if (pb <= pc) {
							pr = b;
						} else {
							pr = c;
						}
						int val = (curLine[i] & 0xFF) + pr;
						curLine[i] = (byte) (val & 0xFF);
					}
					break;
				default:
					throw new IOException("Unsupported PNG filter in Adam7: " + filterType);
				}

				if (isPalette) {
					for (int x = 0; x < passWidth; x++) {
						int idx = BufferUtils.getPackedSample(curLine, x, bitDepth);
						int palPos = idx * 3;
						int r = 0, g = 0, b = 0;
						int a = 0xFF;
						if (palette != null && palPos + 2 < palette.length) {
							r = palette[palPos] & 0xFF;
							g = palette[palPos + 1] & 0xFF;
							b = palette[palPos + 2] & 0xFF;
						}
						if (transPalette != null && idx < transPalette.length) {
							a = transPalette[idx] & 0xFF;
						}
						int outX = passX0 + x * stepX;
						int outY = passY0 + row * stepY;
						if (outX < width && outY < height) {
							int base = (outY * width + outX) * 4;
							rgba[base] = (byte) r;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) b;
							rgba[base + 3] = (byte) a;
						}
					}
				} else {
					if (bytesPerSample == 1) {
						if (samplesPerPixel == 1) {
							for (int x = 0; x < passWidth; x++) {
								int g = curLine[x] & 0xFF;
								int outX = passX0 + x * stepX;
								int outY = passY0 + row * stepY;
								if (outX < width && outY < height) {
									int base = (outY * width + outX) * 4;
									rgba[base] = (byte) g;
									rgba[base + 1] = (byte) g;
									rgba[base + 2] = (byte) g;
									rgba[base + 3] = (byte) 0xFF;
								}
							}
						} else if (samplesPerPixel == 2) {
							for (int x = 0; x < passWidth; x++) {
								int p = x * 2;
								int g = curLine[p] & 0xFF;
								int a = curLine[p + 1] & 0xFF;
								int outX = passX0 + x * stepX;
								int outY = passY0 + row * stepY;
								if (outX < width && outY < height) {
									int base = (outY * width + outX) * 4;
									rgba[base] = (byte) g;
									rgba[base + 1] = (byte) g;
									rgba[base + 2] = (byte) g;
									rgba[base + 3] = (byte) a;
								}
							}
						} else if (samplesPerPixel == 3) {
							for (int x = 0; x < passWidth; x++) {
								int p = x * 3;
								int r = curLine[p] & 0xFF;
								int g = curLine[p + 1] & 0xFF;
								int b = curLine[p + 2] & 0xFF;
								int outX = passX0 + x * stepX;
								int outY = passY0 + row * stepY;
								if (outX < width && outY < height) {
									int base = (outY * width + outX) * 4;
									rgba[base] = (byte) r;
									rgba[base + 1] = (byte) g;
									rgba[base + 2] = (byte) b;
									rgba[base + 3] = (byte) 0xFF;
								}
							}
						} else if (samplesPerPixel == 4) {
							for (int x = 0; x < passWidth; x++) {
								int p = x * 4;
								int r = curLine[p] & 0xFF;
								int g = curLine[p + 1] & 0xFF;
								int b = curLine[p + 2] & 0xFF;
								int a = curLine[p + 3] & 0xFF;
								int outX = passX0 + x * stepX;
								int outY = passY0 + row * stepY;
								if (outX < width && outY < height) {
									int base = (outY * width + outX) * 4;
									rgba[base] = (byte) r;
									rgba[base + 1] = (byte) g;
									rgba[base + 2] = (byte) b;
									rgba[base + 3] = (byte) a;
								}
							}
						}
					} else {
						if (samplesPerPixel == 3) {
							for (int x = 0; x < passWidth; x++) {
								int p = x * 6;
								int r = curLine[p] & 0xFF;
								int g = curLine[p + 2] & 0xFF;
								int b = curLine[p + 4] & 0xFF;
								int outX = passX0 + x * stepX;
								int outY = passY0 + row * stepY;
								if (outX < width && outY < height) {
									int base = (outY * width + outX) * 4;
									rgba[base] = (byte) r;
									rgba[base + 1] = (byte) g;
									rgba[base + 2] = (byte) b;
									rgba[base + 3] = (byte) 0xFF;
								}
							}
						} else if (samplesPerPixel == 4) {
							for (int x = 0; x < passWidth; x++) {
								int p = x * 8;
								int r = curLine[p] & 0xFF;
								int g = curLine[p + 2] & 0xFF;
								int b = curLine[p + 4] & 0xFF;
								int a = curLine[p + 6] & 0xFF;
								int outX = passX0 + x * stepX;
								int outY = passY0 + row * stepY;
								if (outX < width && outY < height) {
									int base = (outY * width + outX) * 4;
									rgba[base] = (byte) r;
									rgba[base + 1] = (byte) g;
									rgba[base + 2] = (byte) b;
									rgba[base + 3] = (byte) a;
								}
							}
						} else {
							throw new IOException("16-bit Adam7 sample type not supported");
						}
					}
				}

				System.arraycopy(curLine, 0, prevLine, 0, curLine.length);
			}
		}

		return new Pixmap(rgba, width, height);
	}

	private static Pixmap parseGIF(byte[] data) throws IOException {
		if (data.length < 10) {
			throw new IOException("Invalid GIF");
		}
		int pos = 0;
		String sig = new String(data, pos, 6, "ASCII");
		pos += 6;
		if (!sig.startsWith("GIF")) {
			throw new IOException("Not a GIF");
		}
		int width = BufferUtils.readLEShort(data, pos);
		pos += 2;
		int height = BufferUtils.readLEShort(data, pos);
		pos += 2;
		int packed = data[pos++] & 0xFF;

		boolean globalColorTableFlag = (packed & 0x80) != 0;
		int gctSize = 2 << (packed & 0x07);

		int[] globalTable = null;
		if (globalColorTableFlag) {
			globalTable = new int[gctSize];
			for (int i = 0; i < gctSize; i++) {
				int r = data[pos++] & 0xFF;
				int g = data[pos++] & 0xFF;
				int b = data[pos++] & 0xFF;
				globalTable[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
			}
		}

		int[] activeTable = globalTable;
		int[] localTable = null;
		int transparentIndex = -1;
		boolean hasTransparency = false;

		while (pos < data.length) {
			int block = data[pos++] & 0xFF;
			if (block == 0x2C) {
				int ix = BufferUtils.readLEShort(data, pos);
				pos += 2;
				int iy = BufferUtils.readLEShort(data, pos);
				pos += 2;
				int iw = BufferUtils.readLEShort(data, pos);
				pos += 2;
				int ih = BufferUtils.readLEShort(data, pos);
				pos += 2;
				int ipacked = data[pos++] & 0xFF;
				boolean lctFlag = (ipacked & 0x80) != 0;
				boolean interlace = (ipacked & 0x40) != 0;
				int lctSize = 2 << (ipacked & 0x07);
				if (lctFlag) {
					localTable = new int[lctSize];
					for (int i = 0; i < lctSize; i++) {
						int r = data[pos++] & 0xFF;
						int g = data[pos++] & 0xFF;
						int b = data[pos++] & 0xFF;
						localTable[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
					}
					activeTable = localTable;
				} else {
					activeTable = globalTable;
				}
				int lzwMin = data[pos++] & 0xFF;
				ByteArrayOutputStream lzwStream = new ByteArrayOutputStream();
				while (true) {
					int subLen = data[pos++] & 0xFF;
					if (subLen == 0) {
						break;
					}
					if (pos + subLen > data.length) {
						throw new IOException("GIF truncated");
					}
					lzwStream.write(data, pos, subLen);
					pos += subLen;
				}
				byte[] lzwBytes = lzwStream.toByteArray();
				int[] indices = decodeGIFLZW(lzwBytes, lzwMin, iw * ih);
				byte[] rgba = new byte[width * height * 4];
				for (int i = 0; i < width * height; i++) {
					int base = i * 4;
					rgba[base] = 0;
					rgba[base + 1] = 0;
					rgba[base + 2] = 0;
					rgba[base + 3] = 0;
				}
				int idx = 0;
				if (!interlace) {
					for (int y = 0; y < ih; y++) {
						for (int x = 0; x < iw; x++) {
							int outX = ix + x;
							int outY = iy + y;
							if (outX < 0 || outX >= width || outY < 0 || outY >= height) {
								idx++;
								continue;
							}
							int colorIndex = indices[idx++] & 0xFF;
							int color = 0x00000000;
							if (activeTable != null && colorIndex < activeTable.length) {
								color = activeTable[colorIndex];
							}
							int base = (outY * width + outX) * 4;
							int r = (color >> 16) & 0xFF;
							int g = (color >> 8) & 0xFF;
							int b = color & 0xFF;
							int a = 0xFF;
							if (hasTransparency && colorIndex == transparentIndex) {
								a = 0x00;
							}
							rgba[base] = (byte) r;
							rgba[base + 1] = (byte) g;
							rgba[base + 2] = (byte) b;
							rgba[base + 3] = (byte) a;
						}
					}
				} else {
					int[] passStart = { 0, 4, 2, 1 };
					int[] passInc = { 8, 8, 4, 2 };
					idx = 0;
					for (int pass = 0; pass < 4; pass++) {
						for (int y = passStart[pass]; y < ih; y += passInc[pass]) {
							for (int x = 0; x < iw; x++) {
								int outX = ix + x;
								int outY = iy + y;
								if (outX < 0 || outX >= width || outY < 0 || outY >= height) {
									idx++;
									continue;
								}
								int colorIndex = indices[idx++] & 0xFF;
								int color = 0x00000000;
								if (activeTable != null && colorIndex < activeTable.length) {
									color = activeTable[colorIndex];
								}
								int base = (outY * width + outX) * 4;
								int r = (color >> 16) & 0xFF;
								int g = (color >> 8) & 0xFF;
								int b = color & 0xFF;
								int a = 0xFF;
								if (hasTransparency && colorIndex == transparentIndex) {
									a = 0x00;
								}
								rgba[base] = (byte) r;
								rgba[base + 1] = (byte) g;
								rgba[base + 2] = (byte) b;
								rgba[base + 3] = (byte) a;
							}
						}
					}
				}
				return new Pixmap(rgba, width, height);
			} else if (block == 0x21) {
				int label = data[pos++] & 0xFF;
				if (label == 0xF9) {
					int packedG = data[pos++] & 0xFF;
					pos += 2;
					int transp = data[pos++] & 0xFF;
					boolean hasTrans = (packedG & 0x01) != 0;
					if (hasTrans) {
						hasTransparency = true;
						transparentIndex = transp;
					} else {
						hasTransparency = false;
						transparentIndex = -1;
					}
				} else {
					while (true) {
						int subLen = data[pos++] & 0xFF;
						if (subLen == 0) {
							break;
						}
						pos += subLen;
					}
				}
			} else if (block == 0x3B) {
				break;
			} else {
				throw new IOException("Unknown GIF block: " + block);
			}
		}
		throw new IOException("No image frame found in GIF");
	}

	private static int[] decodeGIFLZW(byte[] compressed, int minCodeSize, int expectedPixels) throws IOException {
		ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
		BitInputStream bis = new BitInputStream(bais);

		int clearCode = 1 << minCodeSize;
		int endCode = clearCode + 1;
		int codeSize = minCodeSize + 1;
		int dictSize = endCode + 1;

		ObjectMap<Integer, byte[]> dict = new ObjectMap<Integer, byte[]>();
		for (int i = 0; i < clearCode; i++) {
			dict.put(i, new byte[] { (byte) i });
		}
		IntArray output = new IntArray(expectedPixels);
		int prevCode = -1;
		while (true) {
			int code = bis.readBits(codeSize);
			if (code == -1) {
				break;
			}
			if (code == clearCode) {
				dict.clear();
				for (int i = 0; i < clearCode; i++) {
					dict.put(i, new byte[] { (byte) i });
				}
				dictSize = endCode + 1;
				codeSize = minCodeSize + 1;
				prevCode = -1;
				continue;
			} else if (code == endCode) {
				break;
			}

			byte[] entry;
			if (dict.containsKey(code)) {
				entry = dict.get(code);
			} else {
				if (prevCode == -1) {
					throw new IOException("LZW decode error");
				}
				byte[] prevEntry = dict.get(prevCode);
				entry = CollectionUtils.concat(prevEntry, new byte[] { prevEntry[0] });
			}

			for (byte b : entry) {
				output.add(b);
			}
			if (prevCode != -1) {
				byte[] prevEntry = dict.get(prevCode);
				byte[] newEntry = CollectionUtils.concat(prevEntry, new byte[] { entry[0] });
				dict.put(dictSize++, newEntry);
			}

			prevCode = code;
			if (dictSize == (1 << codeSize) && codeSize < 12) {
				codeSize++;
			}
		}

		int[] res = new int[output.size()];
		for (int i = 0; i < output.size(); i++) {
			res[i] = output.get(i) & 0xFF;
		}
		return res;
	}

	private static Pixmap parsePPM(byte[] data) {
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(data);
			BufferedInputStream bis = new BufferedInputStream(bais);
			int a = bis.read();
			int b = bis.read();
			if (a != 'P' || b != '6') {
				return null;
			}
			String wtok = nextToken(bis);
			String htok = nextToken(bis);
			String mval = nextToken(bis);
			if (wtok == null || htok == null || mval == null) {
				return null;
			}
			int width = Integer.parseInt(wtok);
			int height = Integer.parseInt(htok);
			int maxval = Integer.parseInt(mval);
			if (maxval > 255) {
				return null;
			}
			byte[] rgba = new byte[width * height * 4];
			int pixelCount = width * height;
			for (int i = 0; i < pixelCount; i++) {
				int r = bis.read();
				int g = bis.read();
				int bl = bis.read();
				if (r == -1 || g == -1 || bl == -1) {
					return null;
				}
				int base = i * 4;
				rgba[base] = (byte) r;
				rgba[base + 1] = (byte) g;
				rgba[base + 2] = (byte) bl;
				rgba[base + 3] = (byte) 0xFF;
			}
			return new Pixmap(rgba, width, height);
		} catch (Exception e) {
			return null;
		}
	}

	private static String nextToken(BufferedInputStream bis) throws IOException {
		StringBuilder sbr = new StringBuilder();
		int ch;
		while (true) {
			bis.mark(1);
			ch = bis.read();
			if (ch == -1) {
				return null;
			}
			if (CharUtils.isWhitespace(ch)) {
				continue;
			}
			if (ch == '#') {
				while ((ch = bis.read()) != -1 && ch != '\n') {
				}
				continue;
			}
			break;
		}
		sbr.append((char) ch);
		while (true) {
			bis.mark(1);
			ch = bis.read();
			if (ch == -1) {
				break;
			}
			if (CharUtils.isWhitespace(ch)) {
				break;
			}
			sbr.append((char) ch);
		}
		return sbr.toString();
	}

	private static Pixmap parseTGA(byte[] data) {
		try {
			if (data.length < 18) {
				return null;
			}
			int idLen = data[0] & 0xFF;
			int imageType = data[2] & 0xFF;
			if (imageType != 2) {
				return null;
			}
			int pos = 3 + 5;
			int width = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8);
			int height = (data[pos++] & 0xFF) | ((data[pos++] & 0xFF) << 8);
			int pixelDepth = data[pos++] & 0xFF;
			int imageDesc = data[pos++] & 0xFF;
			boolean topToBottom = (imageDesc & 0x20) != 0;
			int bytesPerPixel = pixelDepth / 8;
			if (bytesPerPixel != 3 && bytesPerPixel != 4) {
				return null;
			}
			int imageDataOffset = 18 + idLen;
			if (imageDataOffset + width * height * bytesPerPixel > data.length) {
				return null;
			}
			byte[] rgba = new byte[width * height * 4];
			for (int y = 0; y < height; y++) {
				int row = topToBottom ? y : (height - 1 - y);
				for (int x = 0; x < width; x++) {
					int p = imageDataOffset + (y * width + x) * bytesPerPixel;
					int b = data[p] & 0xFF;
					int g = data[p + 1] & 0xFF;
					int r = data[p + 2] & 0xFF;
					int a = 0xFF;
					if (bytesPerPixel == 4) {
						a = data[p + 3] & 0xFF;
					}
					int base = (row * width + x) * 4;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) a;
				}
			}
			return new Pixmap(rgba, width, height);
		} catch (Exception e) {
			return null;
		}
	}

	private static Pixmap parseICO(byte[] data) throws IOException {
		if (data.length < 6) {
			throw new IOException("Invalid ICO");
		}
		int reserved = ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8));
		int type = ((data[2] & 0xFF) | ((data[3] & 0xFF) << 8));
		int count = ((data[4] & 0xFF) | ((data[5] & 0xFF) << 8));
		if (reserved != 0 || (type != 1 && type != 2) || count <= 0) {
			throw new IOException("Not an ICO/CUR file");
		}
		int entryOffset = 6;
		if (data.length < entryOffset + 16) {
			throw new IOException("ICO truncated");
		}
		int bytesInRes = BufferUtils.readLEInt(data, entryOffset + 8);
		int imageOffset = BufferUtils.readLEInt(data, entryOffset + 12);
		if (imageOffset + bytesInRes > data.length)
			throw new IOException("ICO image truncated");
		if (bytesInRes >= 8 && (data[imageOffset] & 0xFF) == 0x89 && data[imageOffset + 1] == 0x50
				&& data[imageOffset + 2] == 0x4E && data[imageOffset + 3] == 0x47) {
			byte[] pngBytes = new byte[bytesInRes];
			System.arraycopy(data, imageOffset, pngBytes, 0, bytesInRes);
			return parsePNG(pngBytes);
		} else {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			baos.write('B');
			baos.write('M');
			int fileSize = 14 + bytesInRes;
			baos.write(BufferUtils.intToLittleEndian(fileSize), 0, 4);
			baos.write(new byte[] { 0, 0, 0, 0 }, 0, 4);
			baos.write(BufferUtils.intToLittleEndian(14 + 40), 0, 4);
			baos.write(data, imageOffset, bytesInRes);
			byte[] bmpFile = baos.toByteArray();
			return parseBMP(bmpFile);
		}
	}

	private static Pixmap tryParseRawWithHeader(byte[] data) {
		try {
			if (data.length < 9) {
				return null;
			}
			int width = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8)
					| (data[3] & 0xFF);
			int height = ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8)
					| (data[7] & 0xFF);
			int fmt = data[8] & 0xFF;
			if (width <= 0 || height <= 0) {
				return null;
			}
			int bytesPerPixel;
			switch (fmt) {
			case 1:
				bytesPerPixel = 3;
				break;
			case 2:
				bytesPerPixel = 4;
				break;
			case 3:
				bytesPerPixel = 4;
				break;
			case 4:
				bytesPerPixel = 3;
				break;
			case 5:
				bytesPerPixel = 4;
				break;
			default:
				return null;
			}
			int expected = width * height * bytesPerPixel;
			if (9 + expected > data.length)
				return null;
			byte[] rgba = new byte[width * height * 4];
			int src = 9;
			for (int i = 0; i < width * height; i++) {
				int base = i * 4;
				if (fmt == 1) {
					int r = data[src++] & 0xFF;
					int g = data[src++] & 0xFF;
					int b = data[src++] & 0xFF;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) 0xFF;
				} else if (fmt == 2) {
					int r = data[src++] & 0xFF;
					int g = data[src++] & 0xFF;
					int b = data[src++] & 0xFF;
					int a = data[src++] & 0xFF;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) a;
				} else if (fmt == 3) {
					int a = data[src++] & 0xFF;
					int r = data[src++] & 0xFF;
					int g = data[src++] & 0xFF;
					int b = data[src++] & 0xFF;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) a;
				} else if (fmt == 4) {
					int b = data[src++] & 0xFF;
					int g = data[src++] & 0xFF;
					int r = data[src++] & 0xFF;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) 0xFF;
				} else {
					int b = data[src++] & 0xFF;
					int g = data[src++] & 0xFF;
					int r = data[src++] & 0xFF;
					int a = data[src++] & 0xFF;
					rgba[base] = (byte) r;
					rgba[base + 1] = (byte) g;
					rgba[base + 2] = (byte) b;
					rgba[base + 3] = (byte) a;
				}
			}
			return new Pixmap(rgba, width, height);
		} catch (Exception e) {
			return null;
		}
	}

	private static Pixmap tryParseRawGuess(byte[] data) {
		int len = data.length;
		int pixels3 = len / 3;
		int sq = (int) Math.round(Math.sqrt(pixels3));
		if (sq * sq == pixels3) {
			int width = sq, height = sq;
			byte[] rgba = new byte[width * height * 4];
			int src = 0;
			for (int i = 0; i < width * height; i++) {
				int r = data[src++] & 0xFF;
				int g = data[src++] & 0xFF;
				int b = data[src++] & 0xFF;
				int base = i * 4;
				rgba[base] = (byte) r;
				rgba[base + 1] = (byte) g;
				rgba[base + 2] = (byte) b;
				rgba[base + 3] = (byte) 0xFF;
			}
			return new Pixmap(rgba, width, height);
		}
		int pixels4 = len / 4;
		sq = (int) Math.round(Math.sqrt(pixels4));
		if (sq * sq == pixels4) {
			int width = sq, height = sq;
			byte[] rgba = new byte[width * height * 4];
			int src = 0;
			for (int i = 0; i < width * height; i++) {
				int a = data[src++] & 0xFF;
				int r = data[src++] & 0xFF;
				int g = data[src++] & 0xFF;
				int b = data[src++] & 0xFF;
				int base = i * 4;
				rgba[base] = (byte) r;
				rgba[base + 1] = (byte) g;
				rgba[base + 2] = (byte) b;
				rgba[base + 3] = (byte) a;
			}
			return new Pixmap(rgba, width, height);
		}
		return null;
	}

	private static Pixmap parseWebP(byte[] data) throws IOException {
		throw new UnsupportedOperationException(
				"WebP decoding not implemented. Integrate a WebP decoder library or platform decoder.");
	}

	private static Pixmap parseTIFF(byte[] data) throws IOException {
		if (data == null || data.length < 8) {
			throw new IOException("Invalid TIFF");
		}
		boolean littleEndian;
		if (data[0] == 'I' && data[1] == 'I' && data[2] == 0x2A && data[3] == 0x00) {
			littleEndian = true;
		} else if (data[0] == 'M' && data[1] == 'M' && data[2] == 0x00 && data[3] == 0x2A) {
			littleEndian = false;
		} else {
			throw new IOException("Not a TIFF file");
		}
		int ifdOffset = BufferUtils.readInt(data, 4, littleEndian);
		if (ifdOffset < 8 || ifdOffset >= data.length) {
			throw new IOException("Invalid IFD offset");
		}
		int pos = ifdOffset;
		int numEntries = BufferUtils.readShort(data, pos, littleEndian);
		pos += 2;
		ObjectMap<Integer, TiffTag> tags = new ObjectMap<Integer, TiffTag>();
		for (int i = 0; i < numEntries; i++) {
			if (pos + 12 > data.length) {
				throw new IOException("Truncated IFD");
			}
			int tagId = BufferUtils.readShort(data, pos, littleEndian);
			int type = BufferUtils.readShort(data, pos + 2, littleEndian);
			int count = BufferUtils.readInt(data, pos + 4, littleEndian);
			int valueOffset = BufferUtils.readInt(data, pos + 8, littleEndian);
			tags.put(tagId, new TiffTag(type, count, valueOffset));
			pos += 12;
		}

		int imageWidth = (int) getTagAsLong(tags, data, 256, littleEndian, -1);
		int imageLength = (int) getTagAsLong(tags, data, 257, littleEndian, -1);
		if (imageWidth <= 0 || imageLength <= 0) {
			throw new IOException("Invalid TIFF dimensions");
		}
		int[] bitsPerSample = getTagAsIntArray(tags, data, 258, littleEndian);
		if (bitsPerSample == null) {
			bitsPerSample = new int[] { 8 };
		}
		int compression = (int) getTagAsLong(tags, data, 259, littleEndian, 1);
		if (compression != 1) {
			throw new IOException("Only uncompressed TIFF supported (Compression != 1)");
		}
		int photometric = (int) getTagAsLong(tags, data, 262, littleEndian, 2);
		int samplesPerPixel = (int) getTagAsLong(tags, data, 277, littleEndian, (photometric == 2 ? 3 : 1));
		int rowsPerStrip = (int) getTagAsLong(tags, data, 278, littleEndian, imageLength);
		long[] stripOffsets = getTagAsLongArray(tags, data, 273, littleEndian);
		long[] stripByteCounts = getTagAsLongArray(tags, data, 279, littleEndian);
		int planar = (int) getTagAsLong(tags, data, 284, littleEndian, 1);
		int[] colorMap = getTagAsIntArray(tags, data, 320, littleEndian);

		if (stripOffsets == null || stripByteCounts == null) {
			throw new IOException("TIFF missing strip offsets/byte counts");
		}
		if (planar != 1)
			throw new IOException("Only chunky PlanarConfiguration=1 supported");

		byte[] rgba = new byte[imageWidth * imageLength * 4];

		int strips = Math.min(stripOffsets.length, stripByteCounts.length);
		int row = 0;
		for (int s = 0; s < strips; s++) {
			long off = stripOffsets[s];
			long cnt = stripByteCounts[s];
			if (off < 0 || off + cnt > data.length) {
				throw new IOException("Strip out of bounds");
			}
			int stripRows = Math.min(rowsPerStrip, imageLength - row);
			int bps = bitsPerSample.length == 1 ? bitsPerSample[0] : bitsPerSample[0];
			int bytesPerSample = (bps + 7) / 8;
			int bytesPerPixel = samplesPerPixel * bytesPerSample;
			if (bps < 8) {
				int scanlineBytes = ((imageWidth * bps) + 7) / 8;
				int posOff = (int) off;
				for (int r = 0; r < stripRows; r++) {
					if (posOff + scanlineBytes > data.length) {
						throw new IOException("TIFF truncated");
					}
					byte[] scan = new byte[scanlineBytes];
					System.arraycopy(data, posOff, scan, 0, scanlineBytes);
					posOff += scanlineBytes;
					for (int x = 0; x < imageWidth; x++) {
						int sample = BufferUtils.getPackedSample(scan, x, bps);
						int outY = row + r;
						int base = (outY * imageWidth + x) * 4;
						if (photometric == 3 && colorMap != null) {
							int cmapSize = colorMap.length / 3;
							int idx = sample;
							if (idx >= cmapSize) {
								idx = cmapSize - 1;
							}
							int rcol = (colorMap[idx] >> 8) & 0xFF;
							int gcol = (colorMap[idx + cmapSize] >> 8) & 0xFF;
							int bcol = (colorMap[idx + 2 * cmapSize] >> 8) & 0xFF;
							rgba[base] = (byte) rcol;
							rgba[base + 1] = (byte) gcol;
							rgba[base + 2] = (byte) bcol;
							rgba[base + 3] = (byte) 0xFF;
						} else {
							int v = (bps == 1) ? (sample == 0 ? 0 : 255) : (int) ((sample * 255) / ((1 << bps) - 1));
							if (photometric == 0) {
								v = 255 - v;
							}
							rgba[base] = (byte) v;
							rgba[base + 1] = (byte) v;
							rgba[base + 2] = (byte) v;
							rgba[base + 3] = (byte) 0xFF;
						}
					}
				}
				row += stripRows;
				continue;
			}

			int posOff = (int) off;
			for (int r = 0; r < stripRows; r++) {
				for (int x = 0; x < imageWidth; x++) {
					if (posOff + bytesPerPixel > data.length) {
						throw new IOException("TIFF truncated");
					}
					int outY = row + r;
					int base = (outY * imageWidth + x) * 4;
					if (photometric == 2) {
						int rcol = BufferUtils.readSample(data, posOff, bytesPerSample, littleEndian);
						posOff += bytesPerSample;
						int gcol = BufferUtils.readSample(data, posOff, bytesPerSample, littleEndian);
						posOff += bytesPerSample;
						int bcol = BufferUtils.readSample(data, posOff, bytesPerSample, littleEndian);
						posOff += bytesPerSample;
						int a = 0xFF;
						if (samplesPerPixel == 4) {
							a = BufferUtils.readSample(data, posOff, bytesPerSample, littleEndian);
							posOff += bytesPerSample;
						}
						if (bytesPerSample == 2) {
							rcol = rcol >> 8;
							gcol = gcol >> 8;
							bcol = bcol >> 8;
							a = a >> 8;
						}
						rgba[base] = (byte) rcol;
						rgba[base + 1] = (byte) gcol;
						rgba[base + 2] = (byte) bcol;
						rgba[base + 3] = (byte) a;
					} else if (photometric == 3) {
						int idx = BufferUtils.readSample(data, posOff, bytesPerSample, littleEndian);
						posOff += bytesPerSample;
						if (colorMap != null) {
							int cmapSize = colorMap.length / 3;
							if (idx >= cmapSize) {
								idx = cmapSize - 1;
							}
							int rcol = (colorMap[idx] >> 8) & 0xFF;
							int gcol = (colorMap[idx + cmapSize] >> 8) & 0xFF;
							int bcol = (colorMap[idx + 2 * cmapSize] >> 8) & 0xFF;
							rgba[base] = (byte) rcol;
							rgba[base + 1] = (byte) gcol;
							rgba[base + 2] = (byte) bcol;
							rgba[base + 3] = (byte) 0xFF;
						} else {
							rgba[base] = (byte) idx;
							rgba[base + 1] = (byte) idx;
							rgba[base + 2] = (byte) idx;
							rgba[base + 3] = (byte) 0xFF;
						}
					} else {
						int v = BufferUtils.readSample(data, posOff, bytesPerSample, littleEndian);
						posOff += bytesPerSample;
						if (bytesPerSample == 2) {
							v = v >> 8;
						}
						if (photometric == 0) {
							v = 255 - v;
						}
						rgba[base] = (byte) v;
						rgba[base + 1] = (byte) v;
						rgba[base + 2] = (byte) v;
						rgba[base + 3] = (byte) 0xFF;
					}
				}
			}
			row += stripRows;
		}

		return new Pixmap(rgba, imageWidth, imageLength);
	}

	private static long getTagAsLong(ObjectMap<Integer, TiffTag> tags, byte[] data, int tagId, boolean le, long def) {
		TiffTag t = tags.get(tagId);
		if (t == null) {
			return def;
		}
		try {
			if (t.type == 3 && t.count == 1) {
				if (t.count * 2 <= 4) {
					if (le) {
						return (t.valueOffset & 0xFFFF);
					} else {
						return ((t.valueOffset >>> 16) & 0xFFFF);
					}
				} else {
					int pos = t.valueOffset;
					return BufferUtils.readShort(data, pos, le);
				}
			} else if (t.type == 4 && t.count == 1) {
				if (t.count * 4 <= 4) {
					return t.valueOffset;
				}
				int pos = t.valueOffset;
				return BufferUtils.readInt(data, pos, le);
			} else if (t.type == 1 && t.count == 1) {
				int pos = t.valueOffset;
				return data[pos] & 0xFF;
			}
		} catch (Exception e) {
			return def;
		}
		return def;
	}

	private static int[] getTagAsIntArray(ObjectMap<Integer, TiffTag> tags, byte[] data, int tagId, boolean le) {
		TiffTag t = tags.get(tagId);
		if (t == null) {
			return null;
		}
		try {
			if (t.type == 3) {
				int cnt = t.count;
				int[] arr = new int[cnt];
				if (cnt * 2 <= 4) {
					int v = t.valueOffset;
					for (int i = 0; i < cnt; i++) {
						if (le) {
							arr[i] = (v >> (i * 16)) & 0xFFFF;
						} else {
							arr[i] = (v >> ((1 - i) * 16)) & 0xFFFF;
						}
					}
				} else {
					int pos = t.valueOffset;
					for (int i = 0; i < cnt; i++) {
						arr[i] = BufferUtils.readShort(data, pos + i * 2, le) & 0xFFFF;
					}
				}
				return arr;
			} else if (t.type == 4) {
				int cnt = t.count;
				int[] arr = new int[cnt];
				int pos = t.valueOffset;
				for (int i = 0; i < cnt; i++) {
					arr[i] = BufferUtils.readInt(data, pos + i * 4, le);
				}
				return arr;
			} else if (t.type == 1) {
				int cnt = t.count;
				int[] arr = new int[cnt];
				int pos = t.valueOffset;
				for (int i = 0; i < cnt; i++) {
					arr[i] = data[pos + i] & 0xFF;
				}
				return arr;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

	private static long[] getTagAsLongArray(ObjectMap<Integer, TiffTag> tags, byte[] data, int tagId, boolean le) {
		TiffTag t = tags.get(tagId);
		if (t == null) {
			return null;
		}
		try {
			if (t.type == 3) {
				int cnt = t.count;
				long[] arr = new long[cnt];
				if (cnt * 2 <= 4) {
					int v = t.valueOffset;
					for (int i = 0; i < cnt; i++) {
						arr[i] = (v >> (i * 16)) & 0xFFFF;
					}
				} else {
					int pos = t.valueOffset;
					for (int i = 0; i < cnt; i++) {
						arr[i] = BufferUtils.readShort(data, pos + i * 2, le) & 0xFFFF;
					}
				}
				return arr;
			} else if (t.type == 4) {
				int cnt = t.count;
				long[] arr = new long[cnt];
				int pos = t.valueOffset;
				for (int i = 0; i < cnt; i++) {
					arr[i] = BufferUtils.readInt(data, pos + i * 4, le) & 0xFFFFFFFFL;
				}
				return arr;
			} else if (t.type == 1) {
				int cnt = t.count;
				long[] arr = new long[cnt];
				int pos = t.valueOffset;
				for (int i = 0; i < cnt; i++) {
					arr[i] = data[pos + i] & 0xFF;
				}
				return arr;
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}

}
