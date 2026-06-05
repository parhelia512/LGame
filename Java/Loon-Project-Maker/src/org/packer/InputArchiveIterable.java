package org.packer;

public abstract class InputArchiveIterable implements ArchiveIterable {
	public abstract InputArchiveIterator iterator();

	public abstract String getArchiveType();

	public boolean isInputArchive() {
		return true;
	}

	public boolean isOutputArchive() {
		return false;
	}
}
