/**
 * Copyright 2008 - 2015 The Loon Game Engine Authors
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
package loon.opengl;

import loon.LRelease;
import loon.LSysException;
import loon.LSystem;
import loon.canvas.LColor;
import loon.geom.Affine2f;
import loon.opengl.VertexAttributes.Usage;
import loon.utils.TArray;

public final class GLBatch implements LRelease {

	private static final class AttributeCache {

		final int vertexSize;
		final int normalOffset;
		final int colorOffset;
		final int texCoordOffset;

		AttributeCache(Mesh mesh) {
			this.vertexSize = mesh.getVertexAttributes().vertexSize / 4;
			this.normalOffset = mesh.getVertexAttribute(Usage.Normal) != null
					? mesh.getVertexAttribute(Usage.Normal).offset / 4
					: 0;
			this.colorOffset = mesh.getVertexAttribute(Usage.ColorPacked) != null
					? mesh.getVertexAttribute(Usage.ColorPacked).offset / 4
					: 0;
			this.texCoordOffset = mesh.getVertexAttribute(Usage.TextureCoordinates) != null
					? mesh.getVertexAttribute(Usage.TextureCoordinates).offset / 4
					: 0;
		}
	}

	private int primitiveType;
	private int vertexIdx;
	private int numSetTexCoords;
	private final int maxVertices;
	private int numVertices;

	private Mesh mesh;
	private ShaderProgram customShader;
	private boolean ownsShader, closed;
	private int numTexCoords;

	private final Affine2f projModelView = new Affine2f();
	private ExpandVertices expandVertices;
	private String[] shaderUniformNames;

	private boolean hasNormals, hasColors;

	private AttributeCache attributeCache;
	private int[] cachedUniformLocations;

	public GLBatch(boolean hasNormals, boolean hasColors, int numTexCoords) {
		this(8192, hasNormals, hasColors, numTexCoords, null);
		ownsShader = true;
	}

	public GLBatch(int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords) {
		this(maxVertices, hasNormals, hasColors, numTexCoords, null);
		ownsShader = true;
	}

	public GLBatch(int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords, ShaderProgram shader) {
		this.maxVertices = maxVertices;
		this.numTexCoords = numTexCoords;
		this.customShader = shader;
		this.hasNormals = hasNormals;
		this.hasColors = hasColors;
	}

	private VertexAttribute[] buildVertexAttributes(boolean hasNormals, boolean hasColor, int numTexCoords) {
		TArray<VertexAttribute> attribs = new TArray<VertexAttribute>(numTexCoords + 2);
		attribs.add(new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
		if (hasNormals) {
			attribs.add(new VertexAttribute(Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE));
		}
		if (hasColor) {
			attribs.add(new VertexAttribute(Usage.ColorPacked, 4, ShaderProgram.COLOR_ATTRIBUTE));
		}
		for (int i = 0; i < numTexCoords; i++) {
			attribs.add(new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + i));
		}
		final int size = attribs.size;
		final VertexAttribute[] array = new VertexAttribute[size];
		for (int i = 0; i < size; i++) {
			array[i] = attribs.get(i);
		}
		return array;
	}

	public void setShader(ShaderProgram shader) {
		if (shader == customShader) {
			return;
		}
		if (ownsShader) {
			this.customShader.close();
		}
		this.customShader = shader;
		ownsShader = false;
	}

	public void begin(Affine2f projModelView, int primitiveType) {
		begin(maxVertices, projModelView, primitiveType);
	}

	public void begin(int maxSize, Affine2f projModelView, int primitiveType) {
		if (mesh == null) {
			final VertexAttribute[] attribs = buildVertexAttributes(hasNormals, hasColors, numTexCoords);
			mesh = new Mesh(false, maxSize, 0, attribs);
			expandVertices = ExpandVertices.getVerticeCache(maxSize * (mesh.getVertexAttributes().vertexSize / 4));
			attributeCache = new AttributeCache(mesh);
			shaderUniformNames = new String[numTexCoords];
			cachedUniformLocations = new int[numTexCoords];
			for (int i = 0; i < numTexCoords; i++) {
				shaderUniformNames[i] = "u_sampler" + i;
				cachedUniformLocations[i] = -1;
			}
			if (customShader == null) {
				customShader = createDefaultShader(hasNormals, hasColors, numTexCoords);
			}
		}
		this.numSetTexCoords = 0;
		this.vertexIdx = 0;
		this.numVertices = 0;
		this.projModelView.set(projModelView);
		this.primitiveType = primitiveType;
	}

	public void reset(int verticesSize) {
		if (customShader != null) {
			customShader.close();
			customShader = null;
		}
		if (mesh != null) {
			mesh.close();
			mesh = null;
		}
		final VertexAttribute[] attribs = buildVertexAttributes(hasNormals, hasColors, numTexCoords);
		mesh = new Mesh(false, verticesSize, 0, attribs);
		attributeCache = new AttributeCache(mesh);
		shaderUniformNames = new String[numTexCoords];
		cachedUniformLocations = new int[numTexCoords];
		for (int i = 0; i < numTexCoords; i++) {
			shaderUniformNames[i] = "u_sampler" + i;
			cachedUniformLocations[i] = -1;
		}
		customShader = createDefaultShader(hasNormals, hasColors, numTexCoords);
		this.numSetTexCoords = 0;
		this.vertexIdx = 0;
		this.numVertices = 0;
	}

	public void color(float color) {
		expandVertices.setVertice(vertexIdx + attributeCache.colorOffset, color);
	}

	public void color(LColor color) {
		expandVertices.setVertice(vertexIdx + attributeCache.colorOffset, color.toFloatBits());
	}

	public void color(float r, float g, float b, float a) {
		expandVertices.setVertice(vertexIdx + attributeCache.colorOffset, LColor.toFloatBits(r, g, b, a));
	}

	public void texCoord(float u, float v) {
		final int idx = vertexIdx + attributeCache.texCoordOffset;
		expandVertices.setVertice(idx + numSetTexCoords, u);
		expandVertices.setVertice(idx + numSetTexCoords + 1, v);
		numSetTexCoords += 2;
	}

	public void normal(float x, float y, float z) {
		final int idx = vertexIdx + attributeCache.normalOffset;
		expandVertices.setVertice(idx, x);
		expandVertices.setVertice(idx + 1, y);
		expandVertices.setVertice(idx + 2, z);
	}

	public void vertex(float x, float y) {
		vertex(x, y, 0);
	}

	public void vertex(float x, float y, float z) {
		final int idx = vertexIdx;
		expandVertices.setVertice(idx, x);
		expandVertices.setVertice(idx + 1, y);
		expandVertices.setVertice(idx + 2, z);

		numSetTexCoords = 0;
		vertexIdx += attributeCache.vertexSize;
		numVertices++;
	}

	public void vertices2D(float[] buffer) {
		int count = buffer.length / 2;
		float[] data = new float[count * 3];
		for (int i = 0, j = 0; i < count; i++) {
			data[j++] = buffer[i * 2];
			data[j++] = buffer[i * 2 + 1];
			data[j++] = 0f;
		}
		expandVertices.setBatch(vertexIdx, data);
		vertexIdx += count * attributeCache.vertexSize;
		numVertices += count;
		numSetTexCoords = 0;
	}

	public void vertices3D(float[] xyzArray) {
		int count = xyzArray.length / 3;
		expandVertices.setBatch(vertexIdx, xyzArray);
		vertexIdx += count * attributeCache.vertexSize;
		numVertices += count;
		numSetTexCoords = 0;
	}

	public void vertices3D(float[] data, int offset, int length) {
		expandVertices.setBatch(vertexIdx, data, offset, length);
		vertexIdx += (length / 3) * attributeCache.vertexSize;
		numVertices += length / 3;
		numSetTexCoords = 0;
	}

	public void colors(float[] colors, int offset, int length) {
		expandVertices.setBatch(vertexIdx + attributeCache.colorOffset, colors, offset, length);
	}

	public void drawTexturedQuadBatch(float[] buffer, float[] uvArray) {
		int quadCount = buffer.length / 8;
		if (uvArray.length != quadCount * 8) {
			throw new LSysException("UV exception");
		}
		float[] data = new float[quadCount * 12];
		for (int q = 0, j = 0; q < quadCount; q++) {
			for (int v = 0; v < 4; v++) {
				data[j++] = buffer[q * 8 + v * 2];
				data[j++] = buffer[q * 8 + v * 2 + 1];
				data[j++] = 0f;
			}
		}
		expandVertices.setBatch(vertexIdx, data);
		expandVertices.setBatch(vertexIdx + attributeCache.texCoordOffset, uvArray);
		vertexIdx += quadCount * 4 * attributeCache.vertexSize;
		numVertices += quadCount * 4;
		numSetTexCoords = 0;
	}

	public void drawColoredTexturedQuadBatch(float[] buffer, float[] uvArray, float[] colorArray) {
		int quadCount = buffer.length / 8;
		if (uvArray.length != quadCount * 8 || colorArray.length != quadCount * 4) {
			throw new LSysException("UV exception");
		}
		float[] data = new float[quadCount * 12];
		for (int q = 0, j = 0; q < quadCount; q++) {
			for (int v = 0; v < 4; v++) {
				data[j++] = buffer[q * 8 + v * 2];
				data[j++] = buffer[q * 8 + v * 2 + 1];
				data[j++] = 0f;
			}
		}
		expandVertices.setBatch(vertexIdx, data);
		expandVertices.setBatch(vertexIdx + attributeCache.texCoordOffset, uvArray);
		expandVertices.setBatch(vertexIdx + attributeCache.colorOffset, colorArray);
		vertexIdx += quadCount * 4 * attributeCache.vertexSize;
		numVertices += quadCount * 4;
		numSetTexCoords = 0;
	}

	public void drawQuadBatch(float[] buffer) {
		int quadCount = buffer.length / 8;
		float[] data = new float[quadCount * 12];
		for (int q = 0, j = 0; q < quadCount; q++) {
			for (int v = 0; v < 4; v++) {
				data[j++] = buffer[q * 8 + v * 2];
				data[j++] = buffer[q * 8 + v * 2 + 1];
				data[j++] = 0f;
			}
		}
		expandVertices.setBatch(vertexIdx, data);
		vertexIdx += quadCount * 4 * attributeCache.vertexSize;
		numVertices += quadCount * 4;
		numSetTexCoords = 0;
	}

	public void drawGridBatch(int rows, int cols, float startX, float startY, float cellWidth, float cellHeight) {
		int vertexCount = rows * cols;
		float[] data = new float[vertexCount * 3];
		int idx = 0;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				data[idx++] = startX + c * cellWidth;
				data[idx++] = startY + r * cellHeight;
				data[idx++] = 0f;
			}
		}
		expandVertices.setBatch(vertexIdx, data);
		vertexIdx += vertexCount * attributeCache.vertexSize;
		numVertices += vertexCount;
		numSetTexCoords = 0;
	}

	public void colors(float[] colorArray) {
		expandVertices.setBatch(vertexIdx + attributeCache.colorOffset, colorArray);
	}

	public void texCoords(float[] uvArray) {
		expandVertices.setBatch(vertexIdx + attributeCache.texCoordOffset, uvArray);
		numSetTexCoords += uvArray.length;
	}

	public void flush() {
		if (numVertices == 0) {
			return;
		}
		try {
			customShader.begin();
			customShader.setUniformMatrix("u_projModelView", projModelView.toViewMatrix4());
			for (int i = 0; i < numTexCoords; i++) {
				if (cachedUniformLocations[i] == -1) {
					cachedUniformLocations[i] = customShader.getUniformLocation(shaderUniformNames[i]);
				}
				customShader.setUniformi(cachedUniformLocations[i], i);
			}
			mesh.setVertices(expandVertices.getVertices(), 0, vertexIdx);
			mesh.render(customShader, primitiveType);
		} catch (Throwable ex) {
			LSystem.error("Batch error flush()", ex);
		} finally {
			customShader.end();
		}
	}

	public void end() {
		flush();
	}

	public int getNumVertices() {
		return numVertices;
	}

	public int getMaxVertices() {
		return maxVertices;
	}

	public static String createVertexShader(boolean hasNormals, boolean hasColors, int numTexCoords) {
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, hasNormals);
		hashCode = LSystem.unite(hashCode, hasColors);
		hashCode = LSystem.unite(hashCode, numTexCoords);
		ShaderCmd cmd = ShaderCmd.getCmd("dvertexshader" + hashCode);
		if (cmd.isCache()) {
			return cmd.getShader();
		} else {
			cmd.putAttributeVec4(ShaderProgram.POSITION_ATTRIBUTE);
			if (hasNormals) {
				cmd.putAttributeVec3(ShaderProgram.NORMAL_ATTRIBUTE);
			}
			if (hasColors) {
				cmd.putAttributeVec4(ShaderProgram.COLOR_ATTRIBUTE);
			}
			for (int i = 0; i < numTexCoords; i++) {
				cmd.putAttributeVec2(ShaderProgram.TEXCOORD_ATTRIBUTE + i);
			}
			cmd.putUniformMat4("u_projModelView");
			if (hasColors) {
				cmd.putVaryingVec4("v_col");
			}
			for (int i = 0; i < numTexCoords; i++) {
				cmd.putVaryingVec2("v_tex" + i);
			}
			String mainCmd = "   gl_Position = u_projModelView * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
					+ (hasColors ? "   v_col = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n" : LSystem.EMPTY);
			for (int i = 0; i < numTexCoords; i++) {
				mainCmd += "   v_tex" + i + " = " + ShaderProgram.TEXCOORD_ATTRIBUTE + i + ";\n";
			}
			mainCmd += "   gl_PointSize = 1.0;";
			cmd.putMainCmd(mainCmd);
			return cmd.getShader();
		}
	}

	public static String createFragmentShader(boolean hasNormals, boolean hasColors, int numTexCoords) {
		int hashCode = 1;
		hashCode = LSystem.unite(hashCode, hasNormals);
		hashCode = LSystem.unite(hashCode, hasColors);
		hashCode = LSystem.unite(hashCode, numTexCoords);
		ShaderCmd cmd = ShaderCmd.getCmd("dfragmentshader" + hashCode);
		if (cmd.isCache()) {
			return cmd.getShader();
		} else {
			cmd.putDefine("#ifdef GL_ES\n" + "precision mediump float;\n" + "#endif\n");
			if (hasColors) {
				cmd.putVaryingVec4("v_col");
			}
			for (int i = 0; i < numTexCoords; i++) {
				cmd.putVaryingVec2("v_tex" + i);
				cmd.putUniform("sampler2D", "u_sampler" + i);
			}

			String mainCmd = "  gl_FragColor = " + (hasColors ? "v_col" : "vec4(1, 1, 1, 1)");
			if (numTexCoords > 0) {
				mainCmd += " * ";
			}
			for (int i = 0; i < numTexCoords; i++) {
				if (i == numTexCoords - 1) {
					mainCmd += " texture2D(u_sampler" + i + ",  v_tex" + i + ")";
				} else {
					mainCmd += " texture2D(u_sampler" + i + ",  v_tex" + i + ") *";
				}
			}
			mainCmd += ";";
			cmd.putMainCmd(mainCmd);
			return cmd.getShader();
		}
	}

	public static ShaderProgram createDefaultShader(boolean hasNormals, boolean hasColors, int numTexCoords) {
		String vertexShader = createVertexShader(hasNormals, hasColors, numTexCoords);
		String fragmentShader = createFragmentShader(hasNormals, hasColors, numTexCoords);
		ShaderProgram program = new ShaderProgram(vertexShader, fragmentShader);
		return program;
	}

	@Override
	public void close() {
		if (ownsShader && customShader != null) {
			customShader.close();
		}
		if (mesh != null) {
			mesh.close();
		}
		closed = true;
	}

	public boolean isClosed() {
		return closed;
	}
}
