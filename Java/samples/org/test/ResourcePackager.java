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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import loon.utils.ARC4;
import loon.utils.ArrayByte;
import loon.utils.MathUtils;
import loon.utils.Random;
import loon.utils.res.CRCResourceLoader.ResourceConst;
import loon.utils.res.CRCResourceLoader.ResourceEntry;
import loon.utils.res.CRCResourceLoader.ResourceType;

/**
 * 简单的loon资源打包示例(这个格式做了个默认的CRCResourceLoader加载)
 */
public class ResourcePackager {

	private final List<ResourceEntry> entryList = new ArrayList<ResourceEntry>();

	private final Map<String, File> fileCache = new HashMap<String, File>();

	private final ArrayByte packageByte = new ArrayByte();

	public void pack(File sourceDir, String outputPath) throws IOException {
		if (!sourceDir.isDirectory()) {
			throw new IllegalArgumentException("源路径必须是文件夹");
		}

		collectFiles(sourceDir, sourceDir);
		preCalculateDataOffsets(ResourceConst.DEFAULT_CHUNK_SIZE);
		writeEmptyHeaderPlaceholder();
		int indexOffset = ResourceConst.HEADER_LENGTH;
		byte[] encryptedIndex = buildAndEncryptIndex();
		int indexLen = encryptedIndex.length;
		packageByte.write(encryptedIndex);
		int dataOffset = indexOffset + indexLen;
		writeResourceData(ResourceConst.DEFAULT_CHUNK_SIZE);
		fillHeader(entryList.size(), indexOffset, indexLen, dataOffset);
		saveToFile(outputPath);
		System.out.println("打包完成！资源数：" + entryList.size() + " → " + outputPath);
	}

	private void collectFiles(File rootDir, File current) {
		if (current.isFile()) {
			try {
				String relativePath = rootDir.toURI().relativize(current.toURI()).getPath();
				String fileName = current.getName();
				String suffix = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : null;
				byte[] data = Files.readAllBytes(current.toPath());
				int crc32 = Integer.parseUnsignedInt(ArrayByte.of(data).toCRC32Hex(), 16);
				ResourceType type = ResourceType.getTypeBySuffix(suffix);
				ResourceEntry entry = new ResourceEntry(relativePath, fileName, suffix, type, 0, data.length, crc32);
				entryList.add(entry);
				fileCache.put(relativePath, current);
			} catch (Exception e) {
				System.err.println("跳过文件：" + current.getAbsolutePath() + " 错误：" + e.getMessage());
			}
			return;
		}
		File[] files = current.listFiles();
		if (files != null) {
			for (File f : files)
				collectFiles(rootDir, f);
		}
	}

	private void preCalculateDataOffsets(int chunkSize) {
		int running = ResourceConst.HEADER_LENGTH;
		for (ResourceEntry e : entryList) {
			int chunkCount = MathUtils.max(1, (e.getDataLength() + chunkSize - 1) / chunkSize);
			e.chunkSize = chunkSize;
			e.chunkCount = chunkCount;
			e.dataOffset = running;
			int totalBytes = 0;
			for (int i = 0; i < chunkCount; i++) {
				int len = (i == chunkCount - 1) ? (e.dataLength - (chunkCount - 1) * chunkSize) : chunkSize;
				totalBytes += len;
			}
			running += totalBytes;
		}
	}

	private void writeEmptyHeaderPlaceholder() {
		packageByte.setOrder(ArrayByte.BIG_ENDIAN);
		packageByte.writeInt(ResourceConst.MAGIC_NUMBER);
		packageByte.writeInt(ResourceConst.VERSION);
		packageByte.writeBoolean(true);
		packageByte.writeInt(0);
		packageByte.writeInt(0);
		packageByte.writeInt(0);
		packageByte.writeInt(0);
		byte[] indexSalt = new byte[16];
		MathUtils.random.nextBytes(indexSalt);
		packageByte.write(indexSalt);
	}

	private byte[] buildAndEncryptIndex() throws IOException {
		ArrayByte idxPlain = new ArrayByte();
		idxPlain.setOrder(ArrayByte.BIG_ENDIAN);
		byte[] headerBuf = packageByte.getData();
		byte[] indexSalt = new byte[16];
		System.arraycopy(headerBuf, ResourceConst.HEADER_LENGTH - 16, indexSalt, 0, 16);

		for (ResourceEntry e : entryList) {
			byte[] fileSalt = new byte[16];
			MathUtils.random.nextBytes(fileSalt);
			e.fileSalt = fileSalt;

			byte[] derived = Random.KDF(ResourceConst.ENCRYPT_KEY.getBytes(StandardCharsets.UTF_8), fileSalt,
					ResourceConst.KDF_ROUNDS, ResourceConst.DERIVED_KEY_LEN);
			e.derivedKey = derived;

			e.chunkPermutation = Random.buildPermutationFromSeed(derived, e.chunkCount);

			idxPlain.writeLong((long) e.filePath.hashCode());
			idxPlain.writeUTF(e.filePath);
			idxPlain.writeUTF(e.fileName);
			idxPlain.writeUTF(e.suffix == null ? "" : e.suffix);
			idxPlain.writeUTF(ResourceType.detectTypeName(e.suffix));
			idxPlain.writeInt(e.dataLength);
			idxPlain.writeInt(e.crc32);
			idxPlain.writeInt(e.chunkSize);
			idxPlain.writeInt(e.chunkCount);
			idxPlain.write(fileSalt);
			for (int p : e.chunkPermutation)
				idxPlain.writeInt(p);
		}

		byte[] plainIndex = idxPlain.getBytes();

		byte[] indexKey = Random.KDF(ResourceConst.ENCRYPT_KEY.getBytes(StandardCharsets.UTF_8), indexSalt,
				ResourceConst.KDF_ROUNDS, ResourceConst.DERIVED_KEY_LEN);

		ARC4 rc4 = new ARC4(indexKey);
		byte[] encrypted = new byte[plainIndex.length];
		rc4.crypt(plainIndex, encrypted);

		return encrypted;
	}

	private void writeResourceData(int chunkSize) throws IOException {
		for (ResourceEntry e : entryList) {
			File f = fileCache.get(e.filePath);
			if (f == null)
				throw new IOException("找不到文件缓存: " + e.filePath);
			byte[] fileData = Files.readAllBytes(f.toPath());
			ARC4 rc4 = new ARC4(e.derivedKey);
			long seed = Random.bytesToLong(e.derivedKey);
			Random prng = new Random(seed);

			int baseChunk = e.chunkSize;
			int[] perm = e.chunkPermutation;
			for (int writeIndex = 0; writeIndex < e.chunkCount; writeIndex++) {
				int chunkIdx = perm[writeIndex];
				int offset = chunkIdx * baseChunk;
				int thisLen = MathUtils.min(baseChunk, e.dataLength - offset);
				byte[] chunk = new byte[thisLen];
				System.arraycopy(fileData, offset, chunk, 0, thisLen);
				byte[] enc = new byte[thisLen];
				rc4.crypt(chunk, enc);
				for (int pos = 0; pos < enc.length; pos += 256) {
					int blockRemain = MathUtils.min(256, enc.length - pos);
					int xorPos = pos + prng.nextInt(blockRemain);
					enc[xorPos] ^= (byte) prng.nextInt(256);
				}

				packageByte.write(enc);
			}
		}
	}

	private void fillHeader(int total, int indexOff, int indexLen, int dataOff) {
		packageByte.setPosition(0);
		packageByte.writeInt(ResourceConst.MAGIC_NUMBER);
		packageByte.writeInt(ResourceConst.VERSION);
		packageByte.writeBoolean(true);
		packageByte.writeInt(total);
		packageByte.writeInt(indexOff);
		packageByte.writeInt(indexLen);
		packageByte.writeInt(dataOff);
		packageByte.setPosition(packageByte.length());
	}

	private void saveToFile(String path) throws IOException {
		try (FileOutputStream fos = new FileOutputStream(path)) {
			fos.write(packageByte.getBytes());
		}
	}

	public static void main(String[] args) throws IOException {
		File sourceFolder = new File("D:/assets");
		String outputFile = "D:/game" + ResourceConst.PACKAGE_SUFFIX;
		new ResourcePackager().pack(sourceFolder, outputFile);
	}
}
