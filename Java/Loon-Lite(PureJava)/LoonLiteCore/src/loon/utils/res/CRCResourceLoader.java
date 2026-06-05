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
package loon.utils.res;

import java.io.IOException;

import loon.BaseIO;
import loon.LSysException;
import loon.LSystem;
import loon.canvas.Pixmap;
import loon.canvas.PixmapConverter;
import loon.utils.ARC4;
import loon.utils.ArrayByte;
import loon.utils.CRC32;
import loon.utils.MathUtils;
import loon.utils.ObjectMap;
import loon.utils.Random;
import loon.utils.TArray;

/**
 * 用来读取经过简单加密的loon自定义格式的资源文件包
 */
public class CRCResourceLoader {

	public interface ResourceConst {
		// 标识自定义资源包
		int MAGIC_NUMBER = 0x52455350; // "RESP"
		// 包版本
		int VERSION = 1;
		// ARC4加密密钥（密钥）
		String ENCRYPT_KEY = "loongameres";
		// 资源包后缀
		String PACKAGE_SUFFIX = ".res";
		// 文件头固定长度
		int HEADER_LENGTH = 4 + 4 + 1 + 4 + 4 + 4 + 4 + 16; // 41
		// simpleKDF轮数
		int KDF_ROUNDS = 16;
		// ARC4 key bytes
		int DERIVED_KEY_LEN = 16;
		// 64KB
		int DEFAULT_CHUNK_SIZE = 64 * 1024;
	}

	public enum ResourceType {
		/** 图片 */
		IMAGE,
		/** 音频 */
		AUDIO,
		/** 文本 */
		TEXT,
		/** 二进制/其他 */
		BINARY;

		public static ResourceType getTypeBySuffix(String suffix) {
			if (suffix == null) {
				return BINARY;
			}
			suffix = suffix.toLowerCase();
			if (LSystem.isImage(suffix)) {
				return IMAGE;
			} else if (LSystem.isAudio(suffix)) {
				return AUDIO;
			} else if (LSystem.isText(suffix)) {
				return TEXT;
			}
			return BINARY;
		}

		public static String detectTypeName(String suffix) {
			if (suffix == null) {
				return "BINARY";
			}
			suffix = suffix.toLowerCase();
			if (LSystem.isImage(suffix)) {
				return "IMAGE";
			} else if (LSystem.isAudio(suffix)) {
				return "AUDIO";
			} else if (LSystem.isText(suffix)) {
				return "TEXT";
			}
			return "BINARY";
		}
	}

	public static class ResourceEntry {

		public String filePath;
		public String fileName;
		public String suffix;
		public ResourceType fileType;
		public int dataOffset;
		public int dataLength;
		public int crc32;

		public int chunkSize;
		public int chunkCount;
		public byte[] fileSalt;
		public byte[] derivedKey;
		public int[] chunkPermutation;

		public ResourceEntry(String filePath, String fileName, String suffix, ResourceType fileType, int dataOffset,
				int dataLength, int crc32) {
			this.filePath = filePath;
			this.fileName = fileName;
			this.suffix = suffix;
			this.fileType = fileType;
			this.dataOffset = dataOffset;
			this.dataLength = dataLength;
			this.crc32 = crc32;
		}

		public String getFilePath() {
			return filePath;
		}

		public String getFileName() {
			return fileName;
		}

		public String getSuffix() {
			return suffix;
		}

		public ResourceType getFileType() {
			return fileType;
		}

		public int getDataOffset() {
			return dataOffset;
		}

		public int getDataLength() {
			return dataLength;
		}

		public int getCrc32() {
			return crc32;
		}

		@Override
		public String toString() {
			return "[" + fileType + "] " + filePath + " (" + dataLength + " Bit)";
		}
	}

	public static class ResourceTreeNode {

		private final String nodeName;

		private final boolean isDirectory;

		private final ResourceEntry entry;

		private final ResourceTreeNode parent;

		private final ObjectMap<String, ResourceTreeNode> children;

		public ResourceTreeNode(String nodeName) {
			this(nodeName, true, null, null);
		}

		public ResourceTreeNode(String nodeName, boolean isDirectory, ResourceEntry entry, ResourceTreeNode parent) {
			this.nodeName = nodeName;
			this.isDirectory = isDirectory;
			this.entry = entry;
			this.parent = parent;
			this.children = new ObjectMap<String, ResourceTreeNode>();
		}

		public void addChild(ResourceTreeNode child) {
			children.put(child.nodeName, child);
		}

		public String getNodeName() {
			return nodeName;
		}

		public boolean isDirectory() {
			return isDirectory;
		}

		public ResourceEntry getEntry() {
			return entry;
		}

		public ResourceTreeNode getParent() {
			return parent;
		}

		public TArray<ResourceTreeNode> getChildren() {
			return new TArray<ResourceTreeNode>(children.values());
		}

		public void printTree(String indent) {
			System.out.println(indent + (isDirectory ? "+ " : "|- ") + nodeName);
			for (ResourceTreeNode child : getChildren()) {
				child.printTree(indent + "  ");
			}
		}
	}

	private final ObjectMap<String, ResourceEntry> resourceMap = new ObjectMap<String, ResourceEntry>();
	private ResourceTreeNode rootTree;
	private ArrayByte packageByte;
	private int dataOffset;
	private int indexLength;
	private long lastPathHash;

	public CRCResourceLoader(String packagePath) {
		try {
			loadPackage(packagePath);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadPackage(String packagePath) throws Exception {
		packageByte = BaseIO.loadArrayByte(packagePath);
		packageByte.setOrder(ArrayByte.BIG_ENDIAN);
		validateHeader();

		int total = packageByte.readInt();
		int indexOffset = packageByte.readInt();
		indexLength = packageByte.readInt();
		dataOffset = packageByte.readInt();

		decryptIndex(indexOffset, indexLength);

		parseIndexAndBuildTree(indexOffset, total);
	}

	private void validateHeader() throws Exception {
		int magic = packageByte.readInt();
		int version = packageByte.readInt();
		boolean encrypted = packageByte.readBoolean();
		if (magic != ResourceConst.MAGIC_NUMBER || version != ResourceConst.VERSION || !encrypted) {
			throw new Exception("❌ Illegal resource packs ！");
		}
	}

	private void decryptIndex(int indexOffset, int indexLen) throws Exception {
		byte[] buf = packageByte.getData();
		if (buf == null || buf.length < ResourceConst.HEADER_LENGTH) {
			throw new IOException("Invalid package or header too small");
		}
		byte[] indexSalt = new byte[16];
		System.arraycopy(buf, ResourceConst.HEADER_LENGTH - 16, indexSalt, 0, 16);

		byte[] indexKey = Random.KDF(ResourceConst.ENCRYPT_KEY.getBytes(LSystem.ENCODING), indexSalt,
				ResourceConst.KDF_ROUNDS, ResourceConst.DERIVED_KEY_LEN);

		if (indexOffset + indexLen > buf.length) {
			throw new IOException("Index range out of bounds");
		}
		byte[] encIndex = new byte[indexLen];
		System.arraycopy(buf, indexOffset, encIndex, 0, indexLen);

		ARC4 rc4 = new ARC4(indexKey);
		byte[] decIndex = new byte[indexLen];
		rc4.crypt(encIndex, decIndex);

		System.arraycopy(decIndex, 0, buf, indexOffset, indexLen);
	}

	private void parseIndexAndBuildTree(int indexOffset, int total) throws Exception {
		rootTree = new ResourceTreeNode("root");
		packageByte.setPosition(indexOffset);

		TArray<ResourceEntry> ordered = new TArray<ResourceEntry>(total);

		for (int i = 0; i < total; i++) {
			lastPathHash = packageByte.readLong();
			String path = packageByte.readUTF();
			String fileName = packageByte.readUTF();
			String suffix = packageByte.readUTF();
			String typeName = packageByte.readUTF();
			ResourceType type = ResourceType.valueOf(typeName);
			int dataLength = packageByte.readInt();
			int crc32 = packageByte.readInt();
			int chunkSize = packageByte.readInt();
			int chunkCount = packageByte.readInt();
			byte[] fileSalt = packageByte.readByteArray(16);

			int[] permutation = new int[chunkCount];
			for (int p = 0; p < chunkCount; p++) {
				permutation[p] = packageByte.readInt();
			}

			ResourceEntry entry = new ResourceEntry(path, fileName, suffix.isEmpty() ? null : suffix, type, 0,
					dataLength, crc32);
			entry.chunkSize = chunkSize;
			entry.chunkCount = chunkCount;
			entry.fileSalt = fileSalt;
			entry.chunkPermutation = permutation;

			ordered.add(entry);
		}

		int pos = dataOffset;
		for (ResourceEntry e : ordered) {
			e.dataOffset = pos;
			int totalBytes = 0;
			for (int i = 0; i < e.chunkCount; i++) {
				int len = (i == e.chunkCount - 1) ? (e.getDataLength() - (e.chunkCount - 1) * e.chunkSize)
						: e.chunkSize;
				totalBytes += len;
			}
			pos += totalBytes;
		}

		for (ResourceEntry e : ordered) {
			resourceMap.put(e.getFilePath(), e);
			addToTree(e.getFilePath(), e);
		}
	}

	private void addToTree(String path, ResourceEntry entry) {
		String[] paths = path.split("/");
		ResourceTreeNode current = rootTree;
		for (int i = 0; i < paths.length - 1; i++) {
			String folder = paths[i];
			ResourceTreeNode node = current.children.get(folder);
			if (node == null) {
				node = new ResourceTreeNode(folder, true, null, current);
				current.addChild(node);
			}
			current = node;
		}
		String fileName = paths[paths.length - 1];
		current.addChild(new ResourceTreeNode(fileName, false, entry, current));
	}

	public ResourceTreeNode getResourceTree() {
		return rootTree;
	}

	public Pixmap getPixmap(String relativePath) throws IOException {
		return PixmapConverter.parseToPixmap(getResource(relativePath));
	}

	public String getText(String relativePath) {
		return getText(relativePath, LSystem.ENCODING);
	}

	public String getText(String relativePath, String encoding) {
		try {
			String result = new String(getResource(relativePath), encoding);
			return result;
		} catch (Throwable e) {
			return null;
		}
	}

	public byte[] getResource(String relativePath) {
		ResourceEntry entry = resourceMap.get(relativePath);
		if (entry == null) {
			throw new IllegalArgumentException("The Resource does not exist: " + relativePath);
		}

		try {
			int totalLen = entry.getDataLength();
			byte[] out = new byte[totalLen];

			byte[] derived = Random.KDF(ResourceConst.ENCRYPT_KEY.getBytes(LSystem.ENCODING), entry.fileSalt,
					ResourceConst.KDF_ROUNDS, ResourceConst.DERIVED_KEY_LEN);

			ARC4 rc4 = new ARC4(derived);

			Random prng = new Random(Random.bytesToLong(derived));

			int baseChunk = entry.chunkSize;
			int chunkCount = entry.chunkCount;
			int readPos = entry.getDataOffset();

			for (int writeIndex = 0; writeIndex < chunkCount; writeIndex++) {
				int chunkIdx = entry.chunkPermutation[writeIndex];
				int offset = chunkIdx * baseChunk;
				int thisLen = MathUtils.min(baseChunk, entry.getDataLength() - offset);

				packageByte.setPosition(readPos);
				byte[] enc = packageByte.readByteArray(thisLen);
				readPos += thisLen;

				for (int pos = 0; pos < enc.length; pos += 256) {
					int blockRemain = MathUtils.min(256, enc.length - pos);
					int xorPos = pos + prng.nextInt(blockRemain);
					enc[xorPos] ^= (byte) prng.nextInt(256);
				}

				byte[] dec = new byte[thisLen];
				rc4.crypt(enc, dec);

				System.arraycopy(dec, 0, out, offset, thisLen);
			}

			CRC32 crc = new CRC32();
			crc.update(out);
			int calc = (int) crc.getValue();
			if (calc != entry.getCrc32()) {
				throw new LSysException(
						"CRC mismatch for " + relativePath + " expected=" + entry.getCrc32() + " got=" + calc);
			}

			return out;
		} catch (Exception e) {
			throw new LSysException("Read failed: " + relativePath, e);
		}
	}

	public int getIndexLength() {
		return indexLength;
	}

	public int getDataOffset() {
		return dataOffset;
	}

	public long getPathHash() {
		return lastPathHash;
	}

}
