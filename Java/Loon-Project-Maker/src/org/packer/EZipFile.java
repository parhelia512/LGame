package org.packer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class EZipFile {

	EZipDirectory parent;

	public String name;

	public byte[] bytes;

	public EZipFile(String name, byte[] bytes) {
		this.name = name;
		this.bytes = bytes;
	}

	public EZipFile(String name, InputStream inputStream) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int n;
		while ((n = inputStream.read(buffer, 0, 1024)) != -1) {
			baos.write(buffer, 0, n);
		}
		this.bytes = baos.toByteArray();
		this.name = name;
	}

	public EZipFile(EZipDirectory parent, String name, InputStream inputStream)
			throws IOException {
		this(name, inputStream);
		this.parent = parent;
	}

	public EZipFile(EZipDirectory parent, String name, byte[] bytes) {
		this(name, bytes);
		this.parent = parent;
	}

	public String getPath() {
		String path = "";
		if (parent != null) {
			path = parent.getPath();
		}
		path = path + name;
		return path;
	}

	public OutputStream serialize(OutputStream out) throws IOException {
		if (bytes == null) {
			return out;
		}
		ZipUtils.writeByteArrayToOutput(out, bytes);
		return out;
	}

	public ZipOutputStream serialize(ZipOutputStream zos) throws IOException {
		if (bytes == null) {
			return zos;
		}
		ZipUtils.writeByteArrayToZipEntry(zos, new ZipEntry(getPath()), bytes);
		return zos;
	}
}
