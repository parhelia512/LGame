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
package org.packer;

import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class FatJarPackager extends JFrame {

	private static final long serialVersionUID = 1L;

	private final Color bg = new Color(18, 24, 31);
	private final Color panel = new Color(28, 36, 45);
	private final Color accent = new Color(0, 150, 136);
	private final Color text = new Color(230, 230, 230);
	private final Color muted = new Color(160, 170, 180);

	class CPClass {
		final int nameIndex;

		CPClass(int i) {
			nameIndex = i;
		}
	}

	class CPString {
		final int stringIndex;

		CPString(int i) {
			stringIndex = i;
		}
	}

	class CPRef {
		final int tag, classIndex, nameTypeIndex;

		CPRef(int t, int c, int n) {
			tag = t;
			classIndex = c;
			nameTypeIndex = n;
		}
	}

	class CPNameType {
		final int nameIndex, descIndex;

		CPNameType(int n, int d) {
			nameIndex = n;
			descIndex = d;
		}
	}

	private JTextField srcField, outField, mainField;
	private JCheckBox embedNativeBox, graalBox, autoReflectScanBox;
	private JButton packBtn, svcConfigBtn, graalBuildBtn;
	private JTextArea logArea;
	private JFileChooser dirChooser;

	private final Map<String, LinkedHashMap<String, List<String>>> serviceSources = new TreeMap<String, LinkedHashMap<String, List<String>>>();
	private final Map<String, ServiceChoice> serviceChoices = new HashMap<String, ServiceChoice>();

	private static class ServiceChoice {
		public enum Mode {
			CONCAT, FIRST, LAST, SELECT_BY_SOURCE, MANUAL
		}

		Mode mode = Mode.CONCAT;
		final Map<String, Set<String>> selectedBySource = new HashMap<String, Set<String>>();
		String manualContent = null;
	}

	private static final Charset UTF8 = Charset.forName("UTF-8");

	private class LogOutputStream extends OutputStream {
		private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		@Override
		public void write(int b) throws IOException {
			if (b == '\n') {
				publishStatic(buffer.toString("UTF-8"));
				buffer.reset();
			} else if (b != '\r') {
				buffer.write(b);
			}
		}

		public void flush() throws IOException {
			if (buffer.size() > 0) {
				publishStatic(buffer.toString("UTF-8"));
				buffer.reset();
			}
		}
	}

	private void publishStatic(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (logArea != null) {
					logArea.append(message + "\n");
					logArea.setCaretPosition(logArea.getDocument().getLength());
				}
			}
		});
	}

	public FatJarPackager() {
		setTitle("FatJar Packager | GraalVM Support");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(1000, 520);
		setLocationRelativeTo(null);
		setAlwaysOnTop(true);
		initUI();
	}

	private void initUI() {
		JPanel root = new JPanel(new BorderLayout(10, 10));
		root.setBorder(new EmptyBorder(10, 10, 10, 10));
		root.setBackground(bg);
		setContentPane(root);

		JPanel top = new JPanel(new GridBagLayout());
		top.setBackground(panel);
		top.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(40, 50, 60)),
				"Build Configuration", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
				top.getFont().deriveFont(Font.BOLD, 13f), text));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(6, 6, 6, 6);
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		top.add(createStyledLabel("Source Directory:"), c);
		srcField = createStyledTextField();
		c.gridx = 1;
		c.weightx = 1;
		top.add(srcField, c);
		JButton browseSrc = createGhostButton("Browse");
		c.gridx = 2;
		c.weightx = 0;
		top.add(browseSrc, c);

		c.gridx = 0;
		c.gridy = 1;
		top.add(createStyledLabel("Output Directory:"), c);
		outField = createStyledTextField();
		c.gridx = 1;
		c.weightx = 1;
		top.add(outField, c);
		JButton browseOut = createGhostButton("Browse");
		c.gridx = 2;
		top.add(browseOut, c);

		c.gridx = 0;
		c.gridy = 2;
		top.add(createStyledLabel("Main Class:"), c);
		mainField = createStyledTextField();
		c.gridx = 1;
		c.weightx = 1;
		top.add(mainField, c);
		JButton detectBtn = createGhostButton("Auto Detect");
		c.gridx = 2;
		top.add(detectBtn, c);

		c.gridx = 0;
		c.gridy = 3;
		top.add(createStyledLabel("Options:"), c);
		JPanel opts = new JPanel(new FlowLayout(FlowLayout.LEFT));
		opts.setBackground(panel);
		embedNativeBox = createStyledCheckBox("Embed & Auto-Extract Native Libraries", true);
		graalBox = createStyledCheckBox("Enable GraalVM Native Image Support", false);
		autoReflectScanBox = createStyledCheckBox("Auto Generate reflection.json", true);
		opts.add(embedNativeBox);
		opts.add(graalBox);
		opts.add(autoReflectScanBox);
		c.gridx = 1;
		c.gridwidth = 2;
		top.add(opts, c);
		c.gridwidth = 1;

		c.gridx = 0;
		c.gridy = 4;
		c.gridwidth = 3;
		JPanel extra = new JPanel(new FlowLayout(FlowLayout.LEFT));
		extra.setBackground(panel);
		svcConfigBtn = createGhostButton("Configure Service Merging");
		graalBuildBtn = createGhostButton("Build Native Image");
		graalBuildBtn.setEnabled(false);
		extra.add(svcConfigBtn);
		extra.add(graalBuildBtn);
		top.add(extra, c);
		c.gridwidth = 1;

		root.add(top, BorderLayout.NORTH);

		logArea = new JTextArea();
		logArea.setEditable(false);
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		logArea.setBackground(new Color(12, 16, 20));
		logArea.setForeground(text);
		logArea.setCaretColor(accent);
		JScrollPane sp = new JScrollPane(logArea);
		sp.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(40, 50, 60)),
				"Build Log", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
				sp.getFont().deriveFont(Font.BOLD, 13f), Color.BLACK));
		sp.getViewport().setBackground(bg);
		root.add(sp, BorderLayout.CENTER);

		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottom.setBackground(bg);
		packBtn = createAccentButton("Build Fat Jar");
		JButton exitBtn = createGhostButton("Close");
		bottom.add(packBtn);
		bottom.add(exitBtn);
		root.add(bottom, BorderLayout.SOUTH);

		dirChooser = new JFileChooser();
		dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		dirChooser.setBackground(bg);
		dirChooser.setForeground(text);

		browseSrc.addActionListener(e -> {
			if (dirChooser.showOpenDialog(FatJarPackager.this) == JFileChooser.APPROVE_OPTION)
				srcField.setText(dirChooser.getSelectedFile().getAbsolutePath());
		});
		browseOut.addActionListener(e -> {
			if (dirChooser.showOpenDialog(FatJarPackager.this) == JFileChooser.APPROVE_OPTION)
				outField.setText(dirChooser.getSelectedFile().getAbsolutePath());
		});
		exitBtn.addActionListener(e -> dispose());
		detectBtn.addActionListener(e -> autoDetectMainClass());
		packBtn.addActionListener(e -> startPackaging());
		svcConfigBtn.addActionListener(e -> openServiceConfigDialog());
		graalBuildBtn.addActionListener(e -> startGraalNativeBuild());

		new DropTarget(srcField, new DropTargetAdapter() {
			public void drop(DropTargetDropEvent dtde) {
				try {
					dtde.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> list = (List<?>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					if (!list.isEmpty())
						srcField.setText(((File) list.get(0)).getAbsolutePath());
				} catch (Exception ex) {
					publish("Drag & Drop failed: " + ex.getMessage());
				}
			}
		});

		// Graal开关联动
		graalBox.addActionListener(e -> {
			autoReflectScanBox.setEnabled(graalBox.isSelected());
		});
		detectGraalAvailabilityAsync();
	}

	private JLabel createStyledLabel(String txt) {
		JLabel label = new JLabel(txt);
		label.setForeground(muted);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		return label;
	}

	private JTextField createStyledTextField() {
		JTextField f = new JTextField();
		f.setBackground(new Color(22, 28, 34));
		f.setForeground(text);
		f.setCaretColor(accent);
		f.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(60, 70, 80), 1, true),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		f.setFont(f.getFont().deriveFont(Font.PLAIN, 13f));
		return f;
	}

	private JCheckBox createStyledCheckBox(String t, boolean s) {
		JCheckBox c = new JCheckBox(t, s);
		c.setBackground(panel);
		c.setForeground(Color.WHITE);
		c.setFocusPainted(false);
		c.setFont(c.getFont().deriveFont(Font.PLAIN, 13f));
		return c;
	}

	private JButton createAccentButton(String t) {
		final JButton b = new JButton(t);
		b.setFocusPainted(false);
		b.setForeground(Color.BLACK);
		b.setBackground(panel);
		b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(accent, 1, true),
				BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				b.setBackground(accent);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				b.setBackground(panel);
			}
		});
		return b;
	}

	private JButton createGhostButton(String t) {
		final JButton b = new JButton(t);
		b.setFocusPainted(false);
		b.setForeground(Color.BLACK);
		b.setBackground(panel);
		b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(60, 70, 80), 1, true),
				BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		b.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				b.setForeground(accent);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				b.setForeground(Color.BLACK);
			}
		});
		return b;
	}

	private void autoDetectMainClass() {
		final String src = srcField.getText().trim();
		if (src.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please select a source directory first!");
			return;
		}
		final Path rootPath = Paths.get(src);
		if (!Files.isDirectory(rootPath)) {
			JOptionPane.showMessageDialog(this, "Invalid source directory!");
			return;
		}

		SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() {
				publish("Scanning for classes with main() method...");
				try {
					Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
						@Override
						public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
							try {
								if (file.getFileName().toString().endsWith(".class")) {
									byte[] bytes = Files.readAllBytes(file);
									String content = new String(bytes, "ISO-8859-1");
									if (content.contains("main") && content.contains("([Ljava/lang/String;)V")) {
										String className = rootPath.relativize(file).toString()
												.replace(File.separatorChar, '.').replaceAll("\\.class$", "");
										publish("Found candidate main class: " + className);
										if (mainField.getText().trim().isEmpty()) {
											mainField.setText(className);
										}
									}
								}
							} catch (Exception ignored) {
							}
							return FileVisitResult.CONTINUE;
						}
					});
				} catch (IOException ex) {
					publish("Scan failed: " + ex.getMessage());
				}
				return null;
			}

			@Override
			protected void process(List<String> chunks) {
				for (String msg : chunks)
					publish(msg);
			}

			@Override
			protected void done() {
				publish("Auto-detection completed.");
			}
		};
		worker.execute();
	}

	private void startPackaging() {
		final String src = srcField.getText().trim();
		final String out = outField.getText().trim();
		final String main = mainField.getText().trim();
		final boolean embedNative = embedNativeBox.isSelected();
		final boolean graalMode = graalBox.isSelected();
		final boolean autoScan = autoReflectScanBox.isSelected();

		if (src.isEmpty() || out.isEmpty() || main.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Source, Output and Main Class are required!");
			return;
		}

		final Path srcDir = Paths.get(src);
		final Path outDir = Paths.get(out);
		if (!Files.isDirectory(srcDir)) {
			JOptionPane.showMessageDialog(this, "Source directory is invalid!");
			return;
		}

		try {
			if (!Files.exists(outDir))
				Files.createDirectories(outDir);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Failed to create output directory: " + ex.getMessage());
			return;
		}

		packBtn.setEnabled(false);
		logArea.setText("");
		publish("Starting fat jar build process...");

		SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() {
				try {
					executePackaging(srcDir, outDir, main, embedNative, graalMode, autoScan);
					publish("Build completed successfully!");
				} catch (Exception ex) {
					publish("=====================================");
					publish("BUILD FAILED: " + ex.getMessage());
					StringWriter sw = new StringWriter();
					ex.printStackTrace(new PrintWriter(sw));
					publish(sw.toString());
					publish("=====================================");
				}
				return null;
			}

			@Override
			protected void process(List<String> chunks) {
				for (String msg : chunks)
					publish(msg);
			}

			@Override
			protected void done() {
				packBtn.setEnabled(true);
			}
		};
		worker.execute();
	}

	private void executePackaging(Path srcDir, Path outDir, String mainClass, boolean embedNative, boolean graalMode,
			boolean autoScan) throws Exception {
		publish("Scanning source directory: " + srcDir);
		serviceSources.clear();

		final List<Path> jarFiles = new ArrayList<Path>();
		final List<Path> nativeFiles = new ArrayList<Path>();
		final List<Path> javaSourceFiles = new ArrayList<Path>();

		Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				String name = file.getFileName().toString().toLowerCase();
				if (name.endsWith(".jar"))
					jarFiles.add(file);
				else if (name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib"))
					nativeFiles.add(file);
				else if (name.endsWith(".java"))
					javaSourceFiles.add(file);
				return FileVisitResult.CONTINUE;
			}
		});

		publish("Found: " + jarFiles.size() + " JARs, " + nativeFiles.size() + " native libs, " + javaSourceFiles.size()
				+ " Java sources");

		for (Path jar : jarFiles) {
			try (JarInputStream jis = new JarInputStream(Files.newInputStream(jar))) {
				JarEntry entry;
				while ((entry = jis.getNextJarEntry()) != null) {
					String entryName = entry.getName();
					if (entryName.startsWith("META-INF/services/") && !entry.isDirectory()) {
						String serviceName = entryName.substring("META-INF/services/".length());
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						byte[] buffer = new byte[8192];
						int len;
						while ((len = jis.read(buffer)) != -1)
							baos.write(buffer, 0, len);
						List<String> implementations = parseServiceFile(new String(baos.toByteArray(), UTF8));

						LinkedHashMap<String, List<String>> serviceMap = serviceSources.get(serviceName);
						if (serviceMap == null) {
							serviceMap = new LinkedHashMap<String, List<String>>();
							serviceSources.put(serviceName, serviceMap);
						}
						serviceMap.put(jar.getFileName().toString(), implementations);
					}
				}
			} catch (Exception ex) {
				publish("Warning: Failed to scan JAR " + jar + ": " + ex.getMessage());
			}
		}

		for (String service : serviceSources.keySet()) {
			serviceChoices.put(service, new ServiceChoice());
		}

		Manifest manifest = new Manifest();
		Attributes attrs = manifest.getMainAttributes();
		attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attrs.put(new Attributes.Name("Created-By"), "LoonFatJarPackager");
		attrs.put(new Attributes.Name("Enable-Native-Access"), "ALL-UNNAMED");

		if (graalMode) {
			attrs.put(Attributes.Name.MAIN_CLASS, mainClass);
			publish("GraalVM mode: Using main class " + mainClass);
		} else {
			attrs.put(Attributes.Name.MAIN_CLASS, "packager.BootstrapLauncher");
			publish("Standard mode: Using bootstrap launcher");
		}

		Path outputJar = outDir.resolve("app.jar");
		final Set<String> addedEntries = new HashSet<String>();
		String compileClasspath = buildClassPath(jarFiles, srcDir);

		Path tempCompileDir = Files.createTempDirectory("fatjar_compile");
		try {
			if (!graalMode) {
				publish("Compiling bootstrap classes...");
				compileBootstrapClasses(tempCompileDir, compileClasspath);
			}

			if (!javaSourceFiles.isEmpty()) {
				publish("Compiling user Java source files...");
				compileUserJavaSources(srcDir, javaSourceFiles, jarFiles, tempCompileDir);
			}

			try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(outputJar), manifest)) {
				Files.walkFileTree(tempCompileDir, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						String entry = tempCompileDir.relativize(file).toString().replace(File.separatorChar, '/');
						if (!addedEntries.contains(entry)) {
							addFileToJar(jos, file, entry);
							addedEntries.add(entry);
						}
						return FileVisitResult.CONTINUE;
					}
				});

				String appProps = "app.main=" + mainClass + "\n";
				JarEntry propEntry = new JarEntry("app.properties");
				jos.putNextEntry(propEntry);
				jos.write(appProps.getBytes(UTF8));
				jos.closeEntry();
				addedEntries.add("app.properties");

				Files.walkFileTree(srcDir, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
						try {
							String fileName = file.getFileName().toString().toLowerCase();
							if (fileName.endsWith(".jar")) {
								publish("Unpacking and merging JAR: " + file.getFileName());
								unpackJarToRoot(file, jos, addedEntries);
							} else if (fileName.endsWith(".java")) {
								return FileVisitResult.CONTINUE;
							} else if (fileName.endsWith(".dll") || fileName.endsWith(".so")
									|| fileName.endsWith(".dylib")) {
								handleNativeLibrary(file, jos, outDir, embedNative, addedEntries);
							} else {
								String relPath = srcDir.relativize(file).toString().replace(File.separatorChar, '/');
								if (!addedEntries.contains(relPath)) {
									addFileToJar(jos, file, relPath);
									addedEntries.add(relPath);
								}
							}
						} catch (Exception ex) {
							publish("Warning: Failed to process file " + file + ": " + ex.getMessage());
						}
						return FileVisitResult.CONTINUE;
					}
				});

				for (Map.Entry<String, ServiceChoice> entry : serviceChoices.entrySet()) {
					String merged = buildMergedServiceFile(entry.getKey(), entry.getValue());
					if (merged != null && !merged.isEmpty()) {
						String serviceEntry = "META-INF/services/" + entry.getKey();
						if (!addedEntries.contains(serviceEntry)) {
							JarEntry je = new JarEntry(serviceEntry);
							jos.putNextEntry(je);
							jos.write(merged.getBytes(UTF8));
							jos.closeEntry();
							addedEntries.add(serviceEntry);
							publish("Written merged service: " + serviceEntry);
						}
					}
				}
			}
		} finally {
			deleteDirectoryRecursively(tempCompileDir);
		}

		publish("Fat Jar created: " + outputJar.toAbsolutePath());
		generateLaunchScripts(outDir, embedNative, graalMode);

		if (autoScan && graalMode) {
			publish("Scanning JAR for reflection configuration...");
			Set<String> reflectClasses = scanJarForReflection(outputJar);
			Path reflectFile = outDir.resolve("reflection.json");
			writeReflectionConfig(reflectFile, reflectClasses);
			publish("Generated reflection.json with " + reflectClasses.size() + " classes");
		}

		if (graalMode) {
			generateGraalConfigFiles(outDir, mainClass);
			publish("Generated complete GraalVM configuration files!");
		}
	}

	private void compileUserJavaSources(Path srcDir, List<Path> javaFiles, List<Path> jars, Path outputDir)
			throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IOException("Java Compiler not found! Please run this tool with JDK (not JRE)");
		}
		String classPath = buildClassPath(jars, srcDir);
		List<String> args = new ArrayList<String>();
		args.add("-d");
		args.add(outputDir.toString());
		args.add("-classpath");
		args.add(classPath);
		args.add("-encoding");
		args.add("UTF-8");

		for (Path javaFile : javaFiles) {
			args.add(javaFile.toAbsolutePath().toString());
			publish("Compiling: " + srcDir.relativize(javaFile));
		}

		LogOutputStream outStream = new LogOutputStream();
		LogOutputStream errStream = new LogOutputStream();

		int result = compiler.run(null, outStream, errStream, args.toArray(new String[args.size()]));

		outStream.flush();
		errStream.flush();

		if (result != 0) {
			throw new IOException("Java source compilation failed! Exit code: " + result);
		}
	}

	private String buildClassPath(List<Path> jars, Path srcDir) {
		String separator = System.getProperty("path.separator");
		StringBuilder sbr = new StringBuilder();
		for (Path jar : jars) {
			if (sbr.length() > 0) {
				sbr.append(separator);
			}
			sbr.append(jar.toAbsolutePath());
		}
		if (sbr.length() > 0) {
			sbr.append(separator);
		}
		sbr.append(srcDir.toAbsolutePath());
		return sbr.toString();
	}

	private List<String> parseServiceFile(String content) {
		List<String> result = new ArrayList<String>();
		if (content == null) {
			return result;
		}
		String[] lines = content.split("\\r?\\n");
		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			if (!result.contains(trimmed)) {
				result.add(trimmed);
			}
		}
		return result;
	}

	private void unpackJarToRoot(Path jarPath, JarOutputStream jos, Set<String> added) throws IOException {
		try (JarFile jarFile = new JarFile(jarPath.toFile())) {
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();

				if (entry.isDirectory()) {
					continue;
				}
				if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
					continue;
				}
				if (name.startsWith("META-INF/")
						&& (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) {
					continue;
				}
				if (added.contains(name)) {
					continue;
				}
				try (InputStream in = jarFile.getInputStream(entry)) {
					JarEntry newEntry = new JarEntry(name);
					jos.putNextEntry(newEntry);
					byte[] buffer = new byte[8192];
					int len;
					while ((len = in.read(buffer)) != -1) {
						jos.write(buffer, 0, len);
					}
					jos.closeEntry();
					added.add(name);
				}
			}
		}
	}

	private void handleNativeLibrary(Path file, JarOutputStream jos, Path outDir, boolean embed, Set<String> added)
			throws IOException {
		if (embed) {
			String entry = "META-INF/native/" + file.getFileName();
			if (!added.contains(entry)) {
				JarEntry je = new JarEntry(entry);
				jos.putNextEntry(je);
				Files.copy(file, jos);
				jos.closeEntry();
				added.add(entry);
				publish("Embedded native lib: " + entry);
			}
		} else {
			Path nativeDir = outDir.resolve("native");
			if (!Files.exists(nativeDir)) {
				Files.createDirectories(nativeDir);
			}
			Files.copy(file, nativeDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
			publish("Copied native lib to output/native");
		}
	}

	private void addFileToJar(JarOutputStream jos, Path file, String entryName) throws IOException {
		JarEntry entry = new JarEntry(entryName);
		entry.setTime(Files.getLastModifiedTime(file).toMillis());
		jos.putNextEntry(entry);
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[8192];
			int len;
			while ((len = in.read(buffer)) != -1) {
				jos.write(buffer, 0, len);
			}
		}
		jos.closeEntry();
	}

	private String buildMergedServiceFile(String service, ServiceChoice choice) {
		LinkedHashMap<String, List<String>> sources = serviceSources.get(service);
		if (sources == null) {
			return "";
		}
		if (choice.mode == ServiceChoice.Mode.MANUAL) {
			return choice.manualContent == null ? "" : choice.manualContent;
		}

		LinkedHashSet<String> resultSet = new LinkedHashSet<String>();
		if (choice.mode == ServiceChoice.Mode.CONCAT) {
			for (List<String> impls : sources.values()) {
				resultSet.addAll(impls);
			}
		} else if (choice.mode == ServiceChoice.Mode.FIRST) {
			for (List<String> impls : sources.values()) {
				if (!impls.isEmpty()) {
					resultSet.add(impls.get(0));
					break;
				}
			}
		} else if (choice.mode == ServiceChoice.Mode.LAST) {
			String last = "";
			for (List<String> impls : sources.values()) {
				for (String impl : impls)
					last = impl;
			}
			if (!last.isEmpty()) {
				resultSet.add(last);
			}
		} else if (choice.mode == ServiceChoice.Mode.SELECT_BY_SOURCE) {
			for (Map.Entry<String, List<String>> entry : sources.entrySet()) {
				Set<String> selected = choice.selectedBySource.get(entry.getKey());
				if (selected == null || selected.isEmpty()) {
					resultSet.addAll(entry.getValue());
				} else {
					for (String impl : entry.getValue()) {
						if (selected.contains(impl))
							resultSet.add(impl);
					}
				}
			}
		}

		StringBuilder sbr = new StringBuilder();
		for (String s : resultSet) {
			sbr.append(s).append("\n");
		}
		return sbr.toString();
	}

	private void compileBootstrapClasses(Path outDir, String classpath) throws IOException {
		Path srcRoot = Files.createDirectories(outDir.resolve("src"));

		Path extractorFile = writeJavaSource(srcRoot, "packager.NativeExtractor", generateNativeExtractorSource());
		Path bootstrapFile = writeJavaSource(srcRoot, "packager.BootstrapLauncher", generateBootstrapSource());
		Path directFile = writeJavaSource(srcRoot, "packager.DirectInvoker", generateDirectInvokerSource());

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		List<String> args = new ArrayList<String>();
		args.add("-d");
		args.add(outDir.toString());
		args.add("-classpath");
		args.add(classpath);
		args.add(extractorFile.toString());
		args.add(bootstrapFile.toString());
		args.add(directFile.toString());

		int rc = compiler.run(null, null, null, args.toArray(new String[args.size()]));
		if (rc != 0)
			throw new IOException("Bootstrap compilation failed! Code: " + rc);
	}

	private Path writeJavaSource(Path root, String fqcn, String source) throws IOException {
		String[] parts = fqcn.split("\\.");
		String clsName = parts[parts.length - 1];
		Path pkgDir = root;
		for (int i = 0; i < parts.length - 1; i++) {
			pkgDir = Files.createDirectories(pkgDir.resolve(parts[i]));
		}
		Path javaFile = pkgDir.resolve(clsName + ".java");
		writeStringToFile(javaFile, source, UTF8);
		return javaFile;
	}

	private String generateBootstrapSource() {
		return "package packager;\nimport java.io.InputStream;\nimport java.util.Properties;\nimport java.lang.reflect.Method;\npublic class BootstrapLauncher {\n  public static void main(String[] args) throws Exception {\n    NativeExtractor.extractAndLoad();\n    String mainClass = null;\n    try (InputStream in = BootstrapLauncher.class.getResourceAsStream(\"/app.properties\")) {\n      Properties p = new Properties();\n      p.load(in);\n      mainClass = p.getProperty(\"app.main\");\n    }\n    Class<?> mainClazz = Class.forName(mainClass);\n    Method mainMethod = mainClazz.getDeclaredMethod(\"main\", String[].class);\n    mainMethod.setAccessible(true);\n    mainMethod.invoke(null, new Object[]{args});\n  }\n}";
	}

	private String generateDirectInvokerSource() {
		return "package packager;\nimport java.io.InputStream;\nimport java.util.Properties;\nimport java.lang.reflect.Method;\npublic class DirectInvoker {\n  public static void invoke(String[] args) throws Throwable {\n    InputStream in = DirectInvoker.class.getResourceAsStream(\"/app.properties\");\n    Properties p = new Properties();\n    p.load(in);\n    String main = p.getProperty(\"app.main\");\n    Class<?> c = Class.forName(main);\n    Method m = c.getDeclaredMethod(\"main\", String[].class);\n    m.setAccessible(true);\n    m.invoke(null, new Object[]{args});\n  }\n}";
	}

	private String generateNativeExtractorSource() {
		return "package packager;\nimport java.io.*;\nimport java.net.URISyntaxException;\nimport java.net.URL;\nimport java.nio.file.*;\nimport java.util.Enumeration;\nimport java.util.jar.JarEntry;\nimport java.util.jar.JarFile;\npublic class NativeExtractor {\n  private static final String NATIVE_PREFIX = \"META-INF/native/\";\n  public static void extractAndLoad() {\n    try {\n      Path tempDir = Files.createTempDirectory(\"lwjgl-native\");\n      tempDir.toFile().deleteOnExit();\n      System.setProperty(\"org.lwjgl.librarypath\", tempDir.toAbsolutePath().toString());\n      URL codeSource = NativeExtractor.class.getProtectionDomain().getCodeSource().getLocation();\n      if (!codeSource.getProtocol().equals(\"jar\")) {\n         return;\n      }\n      String jarPath = codeSource.toURI().getSchemeSpecificPart().split(\"!\")[0];\n      try (JarFile jf = new JarFile(new File(jarPath))) {\n        Enumeration<JarEntry> entries = jf.entries();\n        while (entries.hasMoreElements()) {\n          JarEntry entry = entries.nextElement();\n          String name = entry.getName();\n          if (name.startsWith(NATIVE_PREFIX) && !entry.isDirectory()) {\n            String fileName = name.substring(NATIVE_PREFIX.length());\n            Path outFile = tempDir.resolve(fileName);\n            try (InputStream in = jf.getInputStream(entry)) {\n              Files.copy(in, outFile, StandardCopyOption.REPLACE_EXISTING);\n            }\n            outFile.toFile().deleteOnExit();\n          }\n        }\n      }\n    } catch (Exception e) {\n      System.err.println(\"Native library extraction failed: \" + e.getMessage());\n    }\n  }\n}";
	}

	private Set<String> scanJarForReflection(Path jarPath) {
		Set<String> classes = new TreeSet<String>();
		try (JarFile jf = new JarFile(jarPath.toFile())) {
			Enumeration<JarEntry> entries = jf.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.getName().endsWith(".class")) {
					try (InputStream in = jf.getInputStream(entry)) {
						classes.addAll(parseClassForReflection(in));
					} catch (Exception ignored) {
					}
				}
			}
		} catch (Exception ex) {
			publish("Reflection scan failed: " + ex.getMessage());
		}

		Set<String> filtered = new TreeSet<String>();
		for (String cls : classes) {
			if (isApplicationClass(cls))
				filtered.add(cls);
		}
		return filtered;
	}

	private boolean isApplicationClass(String className) {
		return !(className.startsWith("java.") || className.startsWith("javax.") || className.startsWith("sun.")
				|| className.startsWith("jdk."));
	}

	private Set<String> parseClassForReflection(InputStream in) throws IOException {
		DataInputStream din = new DataInputStream(in);
		Set<String> result = new HashSet<String>();
		if (din.readInt() != 0xCAFEBABE) {
			return result;
		}
		din.readUnsignedShort();
		din.readUnsignedShort();
		int cpCount = din.readUnsignedShort();
		Object[] cp = new Object[cpCount];

		for (int i = 1; i < cpCount; i++) {
			int tag = din.readUnsignedByte();
			switch (tag) {
			case 7:
				cp[i] = new CPClass(din.readUnsignedShort());
				break;
			case 9:
			case 10:
			case 11:
				cp[i] = new CPRef(tag, din.readUnsignedShort(), din.readUnsignedShort());
				break;
			case 8:
				cp[i] = new CPString(din.readUnsignedShort());
				break;
			case 3:
			case 4:
				din.readInt();
				break;
			case 5:
			case 6:
				din.readLong();
				i++;
				break;
			case 12:
				cp[i] = new CPNameType(din.readUnsignedShort(), din.readUnsignedShort());
				break;
			case 1:
				int len = din.readUnsignedShort();
				byte[] bytes = new byte[len];
				din.readFully(bytes);
				cp[i] = new String(bytes, "UTF-8");
				break;
			default:
				din.skipBytes(0);
			}
		}

		for (int i = 1; i < cpCount; i++) {
			if (cp[i] instanceof CPClass) {
				int idx = ((CPClass) cp[i]).nameIndex;
				if (cp[idx] instanceof String) {
					result.add(((String) cp[idx]).replace('/', '.'));
				}
			}
		}
		return result;
	}

	private void writeReflectionConfig(Path out, Set<String> classes) throws IOException {
		StringBuilder sbr = new StringBuilder("[\n");
		boolean first = true;
		for (String cls : classes) {
			if (!first)
				sbr.append(",\n");
			first = false;
			sbr.append("  {\"name\":\"").append(cls).append(
					"\",\"allDeclaredConstructors\":true,\"allDeclaredMethods\":true,\"allDeclaredFields\":true}");
		}
		sbr.append("\n]");
		writeStringToFile(out, sbr.toString(), UTF8);
	}

	private void generateGraalConfigFiles(Path outDir, String mainClass) throws IOException {
		// 反射配置
		Path reflect = outDir.resolve("reflection.json");
		writeStringToFile(reflect, "[{\"name\":\"" + mainClass
				+ "\",\"allDeclaredConstructors\":true,\"allDeclaredMethods\":true,\"allDeclaredFields\":true}]", UTF8);

		// JNI配置
		Path jni = outDir.resolve("jniconfig.json");
		writeStringToFile(jni, "[]", UTF8);

		// 资源配置
		Path resource = outDir.resolve("resource-config.json");
		writeStringToFile(resource, "{\"resources\":[{\"pattern\":\".*\"}],\"bundles\":[]}", UTF8);

		// 原生镜像配置文件
		Path nativeProps = outDir.resolve("native-image.properties");
		String props = "Args=--no-fallback \\\n" + "--enable-all-security-services \\\n"
				+ "--initialize-at-build-time=.* \\\n" + "-H:ReflectionConfigurationFiles=reflection.json \\\n"
				+ "-H:ResourceConfigurationFiles=resource-config.json \\\n"
				+ "-H:JNIConfigurationFiles=jniconfig.json \\\n" + "-jar app.jar " + mainClass;
		writeStringToFile(nativeProps, props, UTF8);
	}

	private void generateLaunchScripts(Path outDir, boolean embedNative, boolean graalMode) throws IOException {
		Path sh = outDir.resolve("run.sh");
		String shContent = "#!/usr/bin/env bash\ncd \"$(dirname \"$0\")\"\nexec java -jar app.jar \"$@\"";
		writeStringToFile(sh, shContent, UTF8);
		try {
			Files.setPosixFilePermissions(sh, PosixFilePermissions.fromString("rwxr-xr-x"));
		} catch (Exception ignored) {
		}

		Path bat = outDir.resolve("run.bat");
		String batContent = "@echo off\r\ncd /d %~dp0\r\njava -jar app.jar %*\r\npause";
		writeStringToFile(bat, batContent, UTF8);

		// Graal启动脚本
		if (graalMode) {
			Path graalSh = outDir.resolve("build_native.sh");
			writeStringToFile(graalSh,
					"#!/usr/bin/env bash\nnative-image --no-fallback -cp app.jar " + mainField.getText().trim(), UTF8);
			Files.setPosixFilePermissions(graalSh, PosixFilePermissions.fromString("rwxr-xr-x"));

			Path graalBat = outDir.resolve("build_native.bat");
			writeStringToFile(graalBat, "native-image --no-fallback -cp app.jar " + mainField.getText().trim(), UTF8);
		}
	}

	private void deleteDirectoryRecursively(Path path) throws IOException {
		if (!Files.exists(path))
			return;
		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.deleteIfExists(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * 只有安装了graalvm才能使用
	 */
	private void detectGraalAvailabilityAsync() {
		SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
			@Override
			protected Boolean doInBackground() {
				try {
					Process pb = new ProcessBuilder("native-image", "--version").redirectErrorStream(true).start();
					pb.waitFor();
					return pb.exitValue() == 0;
				} catch (Exception ex) {
					publish("GraalVM native-image not found in PATH!");
					return false;
				}
			}

			@Override
			protected void done() {
				try {
					boolean available = get();
					graalBuildBtn.setEnabled(available);
					graalBox.setEnabled(available);
				} catch (Exception ignored) {
				}
			}
		};
		worker.execute();
	}

	private void startGraalNativeBuild() {
		Path outDir = Paths.get(outField.getText().trim());
		Path jar = outDir.resolve("app.jar");
		if (!Files.exists(jar)) {
			JOptionPane.showMessageDialog(this, "app.jar not found! Build the fat jar first.");
			return;
		}
		String mainClass = mainField.getText().trim();
		if (mainClass.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please set main class first!");
			return;
		}

		SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() {
				publish("Starting GraalVM Native Image Build...");
				try {
					List<String> command = new ArrayList<String>();
					command.add("native-image");
					command.add("--no-fallback");
					command.add("--enable-all-security-services");

					// 自动加载配置文件
					Path reflect = outDir.resolve("reflection.json");
					Path resource = outDir.resolve("resource-config.json");
					if (Files.exists(reflect)) {
						command.add("-H:ReflectionConfigurationFiles=reflection.json");
					}
					if (Files.exists(resource)) {
						command.add("-H:ResourceConfigurationFiles=resource-config.json");
					}
					command.add("-jar");
					command.add("app.jar");
					command.add(mainClass);

					ProcessBuilder pb = new ProcessBuilder(command);
					pb.directory(outDir.toFile());
					pb.redirectErrorStream(true);

					Process process = pb.start();
					BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), UTF8));
					String line;
					while ((line = br.readLine()) != null) {
						publish(line);
					}
					int code = process.waitFor();
					if (code == 0) {
						publish("✅ Native Image Build Success!");
					} else {
						publish("❌ Build Failed! Exit Code: " + code);
					}
				} catch (Exception ex) {
					publish("Native build failed: " + ex.getMessage());
				}
				return null;
			}
		};
		worker.execute();
	}

	private void openServiceConfigDialog() {
		if (serviceSources.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No service files found!");
			return;
		}

		final JDialog dialog = new JDialog(this, "Service Merging Configuration", true);
		dialog.setSize(1000, 700);
		dialog.setLocationRelativeTo(this);
		dialog.setBackground(bg);
		dialog.getContentPane().setBackground(bg);

		JPanel root = new JPanel(new BorderLayout(8, 8));
		root.setBackground(bg);
		dialog.setContentPane(root);

		JPanel listPanel = new JPanel();
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(bg);
		final Map<String, Map<String, JCheckBox[]>> checkBoxMap = new HashMap<String, Map<String, JCheckBox[]>>();
		final Map<String, JComboBox<String>> modeMap = new HashMap<String, JComboBox<String>>();
		final Map<String, JTextArea> manualMap = new HashMap<String, JTextArea>();

		for (Map.Entry<String, LinkedHashMap<String, List<String>>> entry : serviceSources.entrySet()) {
			final String service = entry.getKey();
			LinkedHashMap<String, List<String>> sources = entry.getValue();

			JPanel panel = new JPanel(new BorderLayout(6, 6));
			panel.setBackground(Color.BLACK);
			panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(40, 50, 60)),
					service, TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
					panel.getFont().deriveFont(Font.BOLD), text));

			JComboBox<String> modeBox = new JComboBox<String>(
					new String[] { "Concatenate", "First", "Last", "Select By Source", "Manual" });
			modeBox.setBackground(Color.BLACK);
			modeBox.setForeground(text);
			ServiceChoice choice = serviceChoices.get(service);
			switch (choice.mode) {
			case CONCAT:
				modeBox.setSelectedIndex(0);
				break;
			case FIRST:
				modeBox.setSelectedIndex(1);
				break;
			case LAST:
				modeBox.setSelectedIndex(2);
				break;
			case SELECT_BY_SOURCE:
				modeBox.setSelectedIndex(3);
				break;
			case MANUAL:
				modeBox.setSelectedIndex(4);
				break;
			}

			JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
			topPanel.setBackground(Color.BLACK);
			JLabel modeLabel = new JLabel("Mode:");
			modeLabel.setForeground(muted);
			topPanel.add(modeLabel);
			topPanel.add(modeBox);
			panel.add(topPanel, BorderLayout.NORTH);

			JPanel sourcePanel = new JPanel();
			sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.Y_AXIS));
			sourcePanel.setBackground(Color.BLACK);
			Map<String, JCheckBox[]> boxMap = new LinkedHashMap<String, JCheckBox[]>();

			for (Map.Entry<String, List<String>> srcEntry : sources.entrySet()) {
				String jarName = srcEntry.getKey();
				List<String> impls = srcEntry.getValue();

				JPanel jarPanel = new JPanel(new BorderLayout());
				jarPanel.setBackground(Color.BLACK);
				jarPanel.setBorder(BorderFactory.createTitledBorder(
						BorderFactory.createLineBorder(new Color(40, 50, 60)), jarName,
						TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, jarPanel.getFont(), muted));
				JPanel implPanel = new JPanel();
				implPanel.setLayout(new BoxLayout(implPanel, BoxLayout.Y_AXIS));
				implPanel.setBackground(Color.BLACK);

				JCheckBox[] boxes = new JCheckBox[impls.size()];
				for (int i = 0; i < impls.size(); i++) {
					boxes[i] = createStyledCheckBox(impls.get(i), true);
					implPanel.add(boxes[i]);
				}
				boxMap.put(jarName, boxes);
				jarPanel.add(new JScrollPane(implPanel), BorderLayout.CENTER);
				sourcePanel.add(jarPanel);
			}

			checkBoxMap.put(service, boxMap);
			panel.add(new JScrollPane(sourcePanel), BorderLayout.CENTER);

			JTextArea manualArea = new JTextArea(4, 60);
			manualArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
			manualArea.setBackground(new Color(12, 16, 20));
			manualArea.setForeground(text);
			manualArea.setCaretColor(accent);
			if (choice.mode == ServiceChoice.Mode.MANUAL && choice.manualContent != null) {
				manualArea.setText(choice.manualContent);
			}
			manualMap.put(service, manualArea);
			panel.add(new JScrollPane(manualArea), BorderLayout.SOUTH);

			modeBox.addActionListener(e -> {
				int idx = modeBox.getSelectedIndex();
				manualArea.setEnabled(idx == 4);
				boolean enableCheck = idx == 3;
				for (JCheckBox[] arr : boxMap.values()) {
					for (JCheckBox cb : arr) {
						cb.setEnabled(enableCheck);
					}
				}
			});

			modeMap.put(service, modeBox);
			listPanel.add(panel);
		}

		root.add(new JScrollPane(listPanel), BorderLayout.CENTER);
		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		btnPanel.setBackground(bg);
		JButton okBtn = createAccentButton("OK");
		JButton cancelBtn = createGhostButton("Cancel");
		btnPanel.add(okBtn);
		btnPanel.add(cancelBtn);
		root.add(btnPanel, BorderLayout.SOUTH);

		okBtn.addActionListener(e -> {
			for (String service : serviceSources.keySet()) {
				ServiceChoice choice = new ServiceChoice();
				int idx = modeMap.get(service).getSelectedIndex();

				switch (idx) {
				case 1:
					choice.mode = ServiceChoice.Mode.FIRST;
					break;
				case 2:
					choice.mode = ServiceChoice.Mode.LAST;
					break;
				case 3:
					choice.mode = ServiceChoice.Mode.SELECT_BY_SOURCE;
					break;
				case 4:
					choice.mode = ServiceChoice.Mode.MANUAL;
					break;
				default:
					choice.mode = ServiceChoice.Mode.CONCAT;
				}

				if (choice.mode == ServiceChoice.Mode.SELECT_BY_SOURCE) {
					Map<String, JCheckBox[]> boxMap = checkBoxMap.get(service);
					for (Map.Entry<String, JCheckBox[]> entry : boxMap.entrySet()) {
						Set<String> selected = new LinkedHashSet<String>();
						for (JCheckBox cb : entry.getValue()) {
							if (cb.isSelected())
								selected.add(cb.getText());
						}
						choice.selectedBySource.put(entry.getKey(), selected);
					}
				} else if (choice.mode == ServiceChoice.Mode.MANUAL) {
					choice.manualContent = manualMap.get(service).getText();
				}

				serviceChoices.put(service, choice);
			}
			dialog.dispose();
		});

		cancelBtn.addActionListener(e -> dialog.dispose());
		dialog.setVisible(true);
	}

	private void writeStringToFile(Path path, String content, Charset charset) throws IOException {
		try (BufferedWriter bw = Files.newBufferedWriter(path, charset, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING)) {
			bw.write(content);
		}
	}

	private void publish(final String message) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				logArea.append(message + "\n");
				logArea.setCaretPosition(logArea.getDocument().getLength());
			}
		});
	}

}
