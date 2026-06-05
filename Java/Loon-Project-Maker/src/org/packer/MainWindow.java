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

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainWindow extends JFrame {

	private static final long serialVersionUID = 1L;

	public class CustomProgressBar extends JComponent {

		private static final long serialVersionUID = 1L;
		private int min = 0;
		private int max = 100;
		private int value = 0;
		private int displayedValue = 0;
		private boolean indeterminate = false;

		private Color backgroundColor = new Color(28, 36, 45);
		private Color foregroundColor = new Color(0, 150, 136);
		private Color stripeColor = new Color(0, 120, 110);

		private final Timer animationTimer;

		public CustomProgressBar() {
			setPreferredSize(new Dimension(220, 22));
			setOpaque(false);
			animationTimer = new Timer(15, e -> {
				if (indeterminate) {
					repaint();
				} else {
					if (displayedValue != value) {
						int diff = value - displayedValue;
						int step = Math.max(1, Math.abs(diff) / 6);
						displayedValue += (diff > 0 ? step : -step);
						repaint();
					}
				}
			});
		}

		public void setMinimum(int min) {
			this.min = min;
			repaint();
		}

		public void setMaximum(int max) {
			this.max = max;
			repaint();
		}

		public void setValue(int value) {
			this.value = Math.max(min, Math.min(max, value));
			if (!indeterminate) {
				if (!animationTimer.isRunning()) {
					animationTimer.start();
				}
			}
		}

		public int getValue() {
			return value;
		}

		public void setIndeterminate(boolean indeterminate) {
			this.indeterminate = indeterminate;
			if (indeterminate) {
				animationTimer.start();
			} else {
				if (!animationTimer.isRunning())
					animationTimer.start();
			}
			repaint();
		}

		public boolean isIndeterminate() {
			return indeterminate;
		}

		public void setBackgroundColor(Color c) {
			this.backgroundColor = c;
			repaint();
		}

		public void setForegroundColor(Color c) {
			this.foregroundColor = c;
			repaint();
		}

		public void setStripeColor(Color c) {
			this.stripeColor = c;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			int w = getWidth();
			int h = getHeight();
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

				g2.setColor(backgroundColor);
				g2.fillRoundRect(0, 0, w, h, h, h);

				g2.setColor(new Color(0, 0, 0, 60));
				g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);

				g2.setClip(2, 2, w - 4, h - 4);

				if (indeterminate) {
					int bandW = Math.max(30, w / 5);
					long t = System.currentTimeMillis() % 1500L;
					float progress = (float) t / 1500.0f;
					int x = (int) ((w + bandW * 2) * progress) - bandW;

					GradientPaint gp = new GradientPaint(x, 0,
							new Color(foregroundColor.getRed(), foregroundColor.getGreen(), foregroundColor.getBlue(),
									220),
							x + bandW, 0,
							new Color(stripeColor.getRed(), stripeColor.getGreen(), stripeColor.getBlue(), 160), true);
					g2.setPaint(gp);
					g2.fillRoundRect(x, 2, bandW, h - 4, h - 6, h - 6);

					int x2 = x - bandW / 2;
					GradientPaint gp2 = new GradientPaint(x2, 0,
							new Color(foregroundColor.getRed(), foregroundColor.getGreen(), foregroundColor.getBlue(),
									120),
							x2 + bandW, 0,
							new Color(stripeColor.getRed(), stripeColor.getGreen(), stripeColor.getBlue(), 80), true);
					g2.setPaint(gp2);
					g2.fillRoundRect(x2, 2, bandW, h - 4, h - 6, h - 6);
				} else {
					double range = Math.max(1, max - min);
					double frac = (displayedValue - min) / range;
					int fillW = (int) Math.round((w - 4) * frac);
					if (fillW > 0) {
						GradientPaint gp = new GradientPaint(0, 0, foregroundColor.brighter(), w, 0,
								foregroundColor.darker());
						g2.setPaint(gp);
						g2.fillRoundRect(2, 2, fillW, h - 4, h - 6, h - 6);
					}
				}
			} finally {
				g2.dispose();
			}
		}
	}

	private FatJarPackager fatJarPackager;
	private final JTextField projectNameField = new JTextField("MyGame");
	private final JTextField packageField = new JTextField("com.mygame");
	private final JTextField mainClassField = new JTextField("Main");
	private final JTextField outPathField = new JTextField("");

	private JLabel androidSdkPathLabel;
	private JPanel sdkRow;
	private JLabel androidMinSdkLabel;
	private final JTextField androidMinSdkField = new JTextField("21");
	private JLabel androidMaxSdkLabel;
	private final JTextField androidMaxSdkField = new JTextField("35");

	private JLabel lwjglVersionLabel;
	private JLabel graalHelperVersionLabel;
	private JLabel robovmVersionLabel;

	private JLabel gwtVersionLabel;
	private JPanel gwtRow;
	private final JTextField gwtVersionField = new JTextField("2.9.0");

	private JLabel teavmVersionLabel;
	private JPanel teavmRow;
	private final JTextField teavmVersionField = new JTextField("0.14.0");

	private JLabel teavmCOutputLabel;
	private JPanel teavmCOutputRow;
	private final JTextField teavmCOutputField = new JTextField(
			System.getProperty("user.home") + File.separator + "cport");

	private JPanel lwjglRow;
	private JPanel graalRow;
	private JPanel robovmRow;

	private final JTextField widthField = new JTextField("480");
	private final JTextField heightField = new JTextField("320");
	private final JTextField sdkPathField = new JTextField("");
	private final JTextField lwjglVersionField = new JTextField("3.4.1");
	private final JTextField graalHelperVersionField = new JTextField("2.0.1");
	private final JCheckBox enableGraalNativeCheck = new JCheckBox("Enable Graal Native", false);
	private final JTextField robovmVersionField = new JTextField("2.3.24");
	private final JTextField loonVersionField = new JTextField("0.5");
	private final JTextField projectVersionField = new JTextField("1.0.0");
	private final JCheckBox autoRunGradleCheck = new JCheckBox("Auto Run Gradle Wrapper", true);

	private final DefaultTableModel jarsModel = new DefaultTableModel(new Object[] { "Local JAR" }, 0);
	private final JTable localJarsTable = new JTable(jarsModel);
	private final DefaultTableModel mavenModel = new DefaultTableModel(new Object[] { "Maven Dependency" }, 0);
	private final JTable mavenTable = new JTable(mavenModel);

	private final JCheckBox targetDesktop = new JCheckBox("Desktop", true);
	private final JCheckBox targetAndroid = new JCheckBox("Android");
	private final JCheckBox targetIos = new JCheckBox("iOS");
	private final JCheckBox targetTeavmC = new JCheckBox("TeaVM-C");
	private final JCheckBox targetTeavmJs = new JCheckBox("TeaVM-JS");
	private final JCheckBox targetTeavmWasm = new JCheckBox("TeaVM-WASM");
	private final JCheckBox targetOtherJvm = new JCheckBox("Other-JVM");

	private final JCheckBox targetGwt = new JCheckBox("GWT");

	private final JTextArea logArea = new JTextArea();
	private final CustomProgressBar progressBar = new CustomProgressBar();

	private final Color bg = new Color(18, 24, 31);
	private final Color panel = new Color(28, 36, 45);
	private final Color accent = new Color(0, 150, 136);
	private final Color softAccent = new Color(0, 120, 110);
	private final Color text = new Color(230, 230, 230);
	private final Color muted = new Color(160, 170, 180);

	private WatchService watchService;
	private Thread watchThread;
	private final AtomicBoolean watchRunning = new AtomicBoolean(false);

	public MainWindow() {
		super("Loon Project Maker");
		initUI();
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setExtendedState(JFrame.MAXIMIZED_BOTH);
		setAlwaysOnTop(true);
		maximizeToAvailableScreen();
		setVisible(true);
		setLocationRelativeTo(null);
		setAlwaysOnTop(true);
		requestFocus();
		startWatchService();
	}

	/**
	 * 将窗口扩展到主显示器的可用工作区
	 */
	private void maximizeToAvailableScreen() {
		try {
			GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
			GraphicsDevice gd = ge.getDefaultScreenDevice();
			GraphicsConfiguration gc = gd.getDefaultConfiguration();

			Rectangle screenBounds = gc.getBounds();

			Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);

			int x = screenBounds.x + screenInsets.left;
			int y = screenBounds.y + screenInsets.top;
			int width = screenBounds.width - screenInsets.left - screenInsets.right;
			int height = screenBounds.height - screenInsets.top - screenInsets.bottom;

			if (width <= 0 || height <= 0) {
				x = screenBounds.x;
				y = screenBounds.y;
				width = screenBounds.width;
				height = screenBounds.height;
			}

			setBounds(x, y, width, height);
		} catch (Exception ex) {
			setExtendedState(JFrame.MAXIMIZED_BOTH);
		}
	}

	private void updateDynamicComponents() {
		boolean android = targetAndroid.isSelected();
		boolean ios = targetIos.isSelected();
		boolean desktop = targetDesktop.isSelected() || targetOtherJvm.isSelected();
		boolean gwt = targetGwt.isSelected();
		boolean teavm = targetTeavmC.isSelected() || targetTeavmJs.isSelected() || targetTeavmWasm.isSelected();
		boolean teavmC = targetTeavmC.isSelected();

		animateVisibility(androidSdkPathLabel, android);
		animateVisibility(sdkRow, android);
		animateVisibility(androidMinSdkLabel, android);
		animateVisibility(androidMinSdkField, android);
		animateVisibility(androidMaxSdkLabel, android);
		animateVisibility(androidMaxSdkField, android);

		animateVisibility(lwjglVersionLabel, desktop);
		animateVisibility(lwjglRow, desktop);
		animateVisibility(graalHelperVersionLabel, desktop);
		animateVisibility(graalRow, desktop);
		animateVisibility(enableGraalNativeCheck, desktop);

		animateVisibility(robovmVersionLabel, ios);
		animateVisibility(robovmRow, ios);

		animateVisibility(gwtVersionLabel, gwt);
		animateVisibility(gwtRow, gwt);

		animateVisibility(teavmVersionLabel, teavm);
		animateVisibility(teavmRow, teavm);

		animateVisibility(teavmCOutputLabel, teavmC);
		animateVisibility(teavmCOutputRow, teavmC);

		((JComponent) getContentPane()).revalidate();
		((JComponent) getContentPane()).repaint();
	}

	private void initUI() {
		Container c = getContentPane();
		c.setLayout(new BorderLayout(12, 12));
		c.setBackground(bg);
		((JComponent) c).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		// header
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(bg);
		JLabel title = new JLabel("Loon Project Maker");
		title.setForeground(text);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
		header.add(title, BorderLayout.WEST);
		JLabel subtitle = new JLabel("Generate multi-target Gradle KTS projects");
		subtitle.setForeground(muted);
		subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
		header.add(subtitle, BorderLayout.SOUTH);
		c.add(header, BorderLayout.NORTH);

		// center
		JPanel center = new JPanel(new GridBagLayout());
		center.setBackground(bg);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(6, 6, 6, 6);
		gbc.fill = GridBagConstraints.BOTH;

		JPanel configCard = createCardPanel();
		configCard.setLayout(new GridBagLayout());
		GridBagConstraints cg = new GridBagConstraints();
		cg.insets = new Insets(6, 6, 6, 6);
		cg.fill = GridBagConstraints.HORIZONTAL;

		// basic fields
		cg.gridx = 0;
		cg.gridy = 0;
		cg.weightx = 0;
		configCard.add(createLabel("Project Name"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(projectNameField), cg);

		cg.gridx = 0;
		cg.gridy = 1;
		cg.weightx = 0;
		configCard.add(createLabel("Package"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(packageField), cg);

		cg.gridx = 0;
		cg.gridy = 2;
		cg.weightx = 0;
		configCard.add(createLabel("Main Class"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(mainClassField), cg);

		cg.gridx = 0;
		cg.gridy = 3;
		cg.weightx = 0;
		configCard.add(createLabel("Output Path"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		outPathField.setText(getDefaultOutputPath());
		JPanel outRow = new JPanel(new BorderLayout(6, 0));
		outRow.setBackground(panel);
		outRow.add(styledField(outPathField), BorderLayout.CENTER);
		JButton browse = createAccentButton("Browse");
		browse.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				outPathField.setText(fc.getSelectedFile().getAbsolutePath());
			}
		});
		outRow.add(browse, BorderLayout.EAST);
		configCard.add(outRow, cg);

		// window size
		cg.gridx = 0;
		cg.gridy = 4;
		cg.weightx = 0;
		configCard.add(createLabel("Window Width"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(widthField), cg);

		cg.gridx = 0;
		cg.gridy = 5;
		cg.weightx = 0;
		configCard.add(createLabel("Window Height"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(heightField), cg);

		cg.gridx = 0;
		cg.gridy = 6;
		cg.weightx = 0;
		androidSdkPathLabel = createLabel("Android SDK Path");
		configCard.add(androidSdkPathLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		sdkRow = new JPanel(new BorderLayout(6, 0));
		sdkRow.setBackground(panel);
		sdkRow.add(styledField(sdkPathField), BorderLayout.CENTER);
		JButton sdkBtn = createAccentButton("Browse");
		sdkBtn.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				sdkPathField.setText(fc.getSelectedFile().getAbsolutePath());
			}
		});
		sdkRow.add(sdkBtn, BorderLayout.EAST);
		configCard.add(sdkRow, cg);

		cg.gridx = 0;
		cg.gridy = 7;
		cg.weightx = 0;
		androidMinSdkLabel = createLabel("Android Min SDK");
		configCard.add(androidMinSdkLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(androidMinSdkField), cg);

		cg.gridx = 0;
		cg.gridy = 8;
		cg.weightx = 0;
		androidMaxSdkLabel = createLabel("Android Max SDK");
		configCard.add(androidMaxSdkLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(androidMaxSdkField), cg);

		// desktop
		cg.gridx = 0;
		cg.gridy = 9;
		cg.weightx = 0;
		lwjglVersionLabel = createLabel("LWJGL Version");
		configCard.add(lwjglVersionLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		lwjglRow = new JPanel(new BorderLayout());
		lwjglRow.setBackground(panel);
		lwjglRow.add(styledField(lwjglVersionField), BorderLayout.CENTER);
		configCard.add(lwjglRow, cg);
		lwjglRow.setVisible(false);

		cg.gridx = 0;
		cg.gridy = 10;
		cg.weightx = 0;
		graalHelperVersionLabel = createLabel("Graal Helper Version");
		configCard.add(graalHelperVersionLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		graalRow = new JPanel(new BorderLayout());
		graalRow.setBackground(panel);
		graalRow.add(styledField(graalHelperVersionField), BorderLayout.CENTER);
		configCard.add(graalRow, cg);
		graalRow.setVisible(false);

		cg.gridx = 0;
		cg.gridy = 11;
		cg.gridwidth = 2;
		enableGraalNativeCheck.setBackground(panel);
		enableGraalNativeCheck.setForeground(text);
		configCard.add(enableGraalNativeCheck, cg);
		enableGraalNativeCheck.setVisible(false);

		// iOS
		cg.gridx = 0;
		cg.gridy = 12;
		cg.gridwidth = 1;
		cg.weightx = 0;
		robovmVersionLabel = createLabel("RoboVM Version");
		configCard.add(robovmVersionLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		robovmRow = new JPanel(new BorderLayout());
		robovmRow.setBackground(panel);
		robovmRow.add(styledField(robovmVersionField), BorderLayout.CENTER);
		configCard.add(robovmRow, cg);
		robovmRow.setVisible(false);

		// GWT
		cg.gridx = 0;
		cg.gridy = 13;
		cg.weightx = 0;
		gwtVersionLabel = createLabel("GWT Version");
		configCard.add(gwtVersionLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		gwtRow = new JPanel(new BorderLayout());
		gwtRow.setBackground(panel);
		gwtRow.add(styledField(gwtVersionField), BorderLayout.CENTER);
		configCard.add(gwtRow, cg);
		gwtRow.setVisible(false);

		// TeaVM
		cg.gridx = 0;
		cg.gridy = 14;
		cg.weightx = 0;
		teavmVersionLabel = createLabel("TeaVM Version");
		configCard.add(teavmVersionLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		teavmRow = new JPanel(new BorderLayout());
		teavmRow.setBackground(panel);
		teavmRow.add(styledField(teavmVersionField), BorderLayout.CENTER);
		configCard.add(teavmRow, cg);
		teavmRow.setVisible(false);

		cg.gridx = 0;
		cg.gridy = 15;
		cg.weightx = 0;
		teavmCOutputLabel = createLabel("TeaVM-C Output");
		configCard.add(teavmCOutputLabel, cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		teavmCOutputRow = new JPanel(new BorderLayout(6, 0));
		teavmCOutputRow.setBackground(panel);
		teavmCOutputRow.add(styledField(teavmCOutputField), BorderLayout.CENTER);
		JButton teavmCBrowseBtn = createAccentButton("Browse");
		teavmCBrowseBtn.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				teavmCOutputField.setText(fc.getSelectedFile().getAbsolutePath());
			}
		});
		teavmCOutputRow.add(teavmCBrowseBtn, BorderLayout.EAST);
		configCard.add(teavmCOutputRow, cg);
		teavmCOutputRow.setVisible(false);

		// general
		cg.gridx = 0;
		cg.gridy = 16;
		cg.weightx = 0;
		configCard.add(createLabel("Loon Version"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(loonVersionField), cg);

		cg.gridx = 0;
		cg.gridy = 17;
		cg.weightx = 0;
		configCard.add(createLabel("Project Version"), cg);
		cg.gridx = 1;
		cg.weightx = 1.0;
		configCard.add(styledField(projectVersionField), cg);

		cg.gridx = 0;
		cg.gridy = 18;
		cg.gridwidth = 2;
		autoRunGradleCheck.setBackground(panel);
		autoRunGradleCheck.setForeground(text);
		configCard.add(autoRunGradleCheck, cg);

		cg.gridx = 0;
		cg.gridy = 19;
		cg.gridwidth = 2;
		configCard.add(createTargetsPanel(), cg);

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.6;
		gbc.weighty = 1.0;
		center.add(configCard, gbc);

		JPanel horizontalDepsPanel = new JPanel(new GridLayout(1, 2, 10, 10));
		horizontalDepsPanel.setBackground(bg);

		JPanel jarsCard = createCardPanel();
		jarsCard.setLayout(new BorderLayout(8, 8));
		jarsCard.setPreferredSize(new Dimension(250, 220));
		JLabel jarsTitle = new JLabel("Local JARs");
		jarsTitle.setForeground(text);
		jarsTitle.setFont(jarsTitle.getFont().deriveFont(Font.BOLD, 14f));
		jarsCard.add(jarsTitle, BorderLayout.NORTH);
		initTable(localJarsTable);
		jarsCard.add(new JScrollPane(localJarsTable), BorderLayout.CENTER);
		JPanel jarsBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		jarsBtns.setBackground(panel);
		JButton removeJarBtn = createGhostButton("Remove");
		removeJarBtn.addActionListener(e -> {
			int sel = localJarsTable.getSelectedRow();
			if (sel >= 0) {
				jarsModel.removeRow(sel);
			}
		});
		JButton addJarBtn = createAccentButton("Add JAR");
		addJarBtn.addActionListener(e -> {
			JFileChooser fc = new JFileChooser();
			fc.setMultiSelectionEnabled(true);
			if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				for (File f : fc.getSelectedFiles()) {
					if (!isJarInModel(f.getAbsolutePath())) {
						jarsModel.addRow(new Object[] { f.getAbsolutePath() });
					}
				}
			}
		});
		jarsBtns.add(removeJarBtn);
		jarsBtns.add(addJarBtn);
		jarsCard.add(jarsBtns, BorderLayout.SOUTH);

		JPanel mavenCard = createCardPanel();
		mavenCard.setLayout(new BorderLayout(8, 8));
		mavenCard.setPreferredSize(new Dimension(250, 220));
		JLabel mavenTitle = new JLabel("Maven");
		mavenTitle.setForeground(text);
		mavenTitle.setFont(mavenTitle.getFont().deriveFont(Font.BOLD, 14f));
		mavenCard.add(mavenTitle, BorderLayout.NORTH);
		initTable(mavenTable);
		mavenCard.add(new JScrollPane(mavenTable), BorderLayout.CENTER);

		enhanceTableWithBulkActions(localJarsTable, jarsModel, true);
		enhanceTableWithBulkActions(mavenTable, mavenModel, false);

		JPanel mavenBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		mavenBtns.setBackground(panel);
		JButton removeMavenBtn = createGhostButton("Remove");
		removeMavenBtn.addActionListener(e -> {
			int sel = mavenTable.getSelectedRow();
			if (sel >= 0)
				mavenModel.removeRow(sel);
		});
		JButton addMavenBtn = createAccentButton("Add Maven");
		addMavenBtn.addActionListener(e -> {
			String dep = JOptionPane.showInputDialog(this, "Format: group:name:version", "");
			if (dep != null && !dep.isEmpty())
				mavenModel.addRow(new Object[] { dep.trim() });
		});
		mavenBtns.add(removeMavenBtn);
		mavenBtns.add(addMavenBtn);
		mavenCard.add(mavenBtns, BorderLayout.SOUTH);

		horizontalDepsPanel.add(jarsCard);
		horizontalDepsPanel.add(mavenCard);
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 0.4;
		gbc.weighty = 0;
		center.add(horizontalDepsPanel, gbc);

		// bottom log
		JPanel bottom = new JPanel(new BorderLayout(8, 8));
		bottom.setBackground(bg);
		logArea.setEditable(false);
		logArea.setBackground(new Color(12, 16, 20));
		logArea.setForeground(text);
		logArea.setFont(new Font("MONOSPACED", Font.PLAIN, 12));
		JScrollPane logScroll = new JScrollPane(logArea);
		logScroll.setBorder(createCardBorder());
		logScroll.setPreferredSize(new Dimension(0, 200));
		bottom.add(logScroll, BorderLayout.CENTER);

		// controls
		// controls
		JPanel controls = new JPanel(new BorderLayout(8, 8));
		controls.setBackground(bg);
		// 左侧按钮组：Clear Log + Pack to Jar
		JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		leftBtns.setBackground(bg);
		JButton clearLog = createGhostButton("Clear Log");
		clearLog.addActionListener(e -> logArea.setText(""));
		// 新增 打包JAR 按钮
		JButton packJarBtn = createGhostButton("Pack to Jar");
		packJarBtn.addActionListener(e -> onPackJar(packJarBtn));

		leftBtns.add(clearLog);
		leftBtns.add(packJarBtn);
		controls.add(leftBtns, BorderLayout.WEST);

		JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		rightControls.setBackground(bg);
		progressBar.setPreferredSize(new Dimension(220, 20));
		progressBar.setForegroundColor(accent);
		progressBar.setBackgroundColor(panel);
		JButton generateBtn = createAccentButton("Generate Project");
		generateBtn.setPreferredSize(new Dimension(180, 30));
		generateBtn.addActionListener(e -> onGenerate(generateBtn));
		rightControls.add(progressBar);
		rightControls.add(generateBtn);
		controls.add(rightControls, BorderLayout.EAST);
		bottom.add(controls, BorderLayout.SOUTH);

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBackground(bg);
		mainPanel.add(center);
		mainPanel.add(Box.createVerticalStrut(10));
		mainPanel.add(bottom);

		JScrollPane mainScroll = new JScrollPane(mainPanel);
		mainScroll.setBorder(BorderFactory.createEmptyBorder());
		mainScroll.getVerticalScrollBar().setUnitIncrement(16);
		c.add(mainScroll, BorderLayout.CENTER);

		applyComponentStyles();
		updateDynamicComponents();

		// auto-scan jars on startup
		scanAndLoadJars();
		loadLists();
	}

	/**
	 * 打包可执行JAR
	 * 
	 * @param packJarBtn
	 */
	private void onPackJar(JButton packJarBtn) {
		if (fatJarPackager != null && fatJarPackager.isDisplayable()) {
			fatJarPackager.toFront();
			return;
		}
		SwingUtilities.invokeLater(() -> {
			fatJarPackager = new FatJarPackager();
			fatJarPackager.setAlwaysOnTop(true);
			fatJarPackager.setVisible(true);
		});
	}

	private File getRunDirectory() {
		try {
			return new File(".").getCanonicalFile();
		} catch (IOException e) {
			return new File(System.getProperty("user.dir"));
		}
	}

	private File getJarsStorageFile() {
		return new File(getRunDirectory(), "loon_jars.txt");
	}

	private File getMavenStorageFile() {
		return new File(getRunDirectory(), "loon_maven.txt");
	}

	private void loadLists() {
		File jarsFile = getJarsStorageFile();
		if (jarsFile.exists() && jarsFile.isFile()) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new java.io.InputStreamReader(new FileInputStream(jarsFile), "UTF-8"));
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.length() == 0) {
						continue;
					}
					if (!isJarInModel(line)) {
						jarsModel.addRow(new Object[] { line });
					}
				}
			} catch (IOException ex) {
				appendLog("Failed to load jars list: " + ex.getMessage());
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException ignored) {
					}
				}
			}
		}

		// Load maven
		File mavenFile = getMavenStorageFile();
		if (mavenFile.exists() && mavenFile.isFile()) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new java.io.InputStreamReader(new FileInputStream(mavenFile), "UTF-8"));
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.length() == 0) {
						continue;
					}
					boolean exists = false;
					for (int i = 0; i < mavenModel.getRowCount(); i++) {
						Object v = mavenModel.getValueAt(i, 0);
						if (v != null && line.equals(v.toString())) {
							exists = true;
							break;
						}
					}
					if (!exists) {
						mavenModel.addRow(new Object[] { line });
					}
				}
			} catch (IOException ex) {
				appendLog("Failed to load maven list: " + ex.getMessage());
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException ignored) {
					}
				}
			}
		}
	}

	private void saveLists() {
		File jarsFile = getJarsStorageFile();
		BufferedWriter bw = null;
		try {
			bw = new BufferedWriter(new java.io.OutputStreamWriter(new FileOutputStream(jarsFile), "UTF-8"));
			for (int i = 0; i < jarsModel.getRowCount(); i++) {
				Object v = jarsModel.getValueAt(i, 0);
				if (v != null) {
					bw.write(v.toString());
					bw.newLine();
				}
			}
			bw.flush();
		} catch (IOException ex) {
			appendLog("Failed to save jars list: " + ex.getMessage());
		} finally {
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException ignored) {
				}
			}
		}

		// Save maven
		File mavenFile = getMavenStorageFile();
		bw = null;
		try {
			bw = new BufferedWriter(new java.io.OutputStreamWriter(new FileOutputStream(mavenFile), "UTF-8"));
			for (int i = 0; i < mavenModel.getRowCount(); i++) {
				Object v = mavenModel.getValueAt(i, 0);
				if (v != null) {
					bw.write(v.toString());
					bw.newLine();
				}
			}
			bw.flush();
		} catch (IOException ex) {
			appendLog("Failed to save maven list: " + ex.getMessage());
		} finally {
			if (bw != null) {
				try {
					bw.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private void initTable(JTable table) {
		table.setFillsViewportHeight(true);
		table.setBackground(panel);
		table.setForeground(text);
		table.setSelectionBackground(softAccent);
		table.setSelectionForeground(Color.WHITE);
		JTableHeader th = table.getTableHeader();
		th.setBackground(bg);
		th.setForeground(muted);
		th.setReorderingAllowed(false);
	}

	public static String getDefaultOutputPath() {
		return System.getProperty("user.home") + File.separator + "MyGame-Project";
	}

	private JPanel createCardPanel() {
		JPanel p = new JPanel() {
			private static final long serialVersionUID = 1L;
			private float hoverOffset = 0f;
			private float targetOffset = 0f;
			private Timer anim;
			{
				setOpaque(false);
				setBorder(createCardBorder());
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						targetOffset = -4f;
						startAnim();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						targetOffset = 0f;
						startAnim();
					}
				});
			}

			private void startAnim() {
				if (anim != null && anim.isRunning()) {
					return;
				}
				anim = new Timer(15, ev -> {
					float t = 0.2f;
					hoverOffset += (targetOffset - hoverOffset) * t;
					if (Math.abs(targetOffset - hoverOffset) < 0.5f) {
						hoverOffset = targetOffset;
						anim.stop();
					}
					repaint();
				});
				anim.start();
			}

			@Override
			protected void paintComponent(Graphics g) {
				int w = getWidth();
				int h = getHeight();
				Graphics2D g2 = (Graphics2D) g.create();
				try {
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					// shadow
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
					g2.setColor(Color.BLACK);
					int arc = 12;
					g2.fillRoundRect(6, 8 + Math.round(hoverOffset), w - 12, h - 12, arc, arc);
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
					// card background
					g2.setColor(panel);
					g2.fillRoundRect(0, 0 + Math.round(hoverOffset), w, h, arc, arc);
				} finally {
					g2.dispose();
				}
				super.paintComponent(g);
			}
		};
		p.setBackground(panel);
		p.setLayout(new BorderLayout());
		p.setOpaque(false);
		return p;
	}

	private Border createCardBorder() {
		return BorderFactory.createCompoundBorder(new LineBorder(new Color(40, 50, 60), 1, true),
				BorderFactory.createEmptyBorder(10, 10, 10, 10));
	}

	private JLabel createLabel(String textStr) {
		JLabel l = new JLabel(textStr);
		l.setForeground(muted);
		l.setFont(l.getFont().deriveFont(Font.PLAIN, 13f));
		return l;
	}

	private JComponent styledField(JTextField field) {
		field.setBackground(new Color(22, 28, 34));
		field.setForeground(text);
		field.setCaretColor(accent);
		field.setBorder(BorderFactory.createCompoundBorder(new LineBorder(new Color(60, 70, 80), 1, true),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		field.setFont(field.getFont().deriveFont(Font.PLAIN, 13f));
		return field;
	}

	private void enhanceTableWithBulkActions(final JTable table, final DefaultTableModel model,
			final boolean isJarTable) {
		if (table == null || model == null) {
			return;
		}
		// 允许多选
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		KeyStroke delete = KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0);
		KeyStroke backspace = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0);

		Action deleteAction = new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				removeSelectedRowsFromModel(table, model);
			}
		};
		table.getInputMap(JComponent.WHEN_FOCUSED).put(delete, "deleteRows");
		table.getActionMap().put("deleteRows", deleteAction);
		table.getInputMap(JComponent.WHEN_FOCUSED).put(backspace, "deleteRows");

		// 右键菜单
		final JPopupMenu popup = new JPopupMenu();
		JMenuItem addItem = new JMenuItem("Add");
		JMenuItem addMultipleItem = new JMenuItem("Add Multiple");
		JMenuItem removeItem = new JMenuItem("Remove Selected");

		// Add单项
		addItem.addActionListener(e -> {
			if (isJarTable) {
				JFileChooser fc = new JFileChooser();
				fc.setMultiSelectionEnabled(false);
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				int res = fc.showOpenDialog(MainWindow.this);
				if (res == JFileChooser.APPROVE_OPTION) {
					File f = fc.getSelectedFile();
					if (f != null && f.getName().toLowerCase().endsWith(".jar")) {
						String path = f.getAbsolutePath();
						if (!isJarInModel(path)) {
							model.addRow(new Object[] { path });
						}
					} else {
						JOptionPane.showMessageDialog(MainWindow.this, "Please select a .jar file.", "Invalid file",
								JOptionPane.WARNING_MESSAGE);
					}
				}
			} else {
				String dep = JOptionPane.showInputDialog(MainWindow.this,
						"Enter Maven coordinate (group:name:version):", "");
				if (dep != null) {
					dep = dep.trim();
					if (!dep.isEmpty()) {
						model.addRow(new Object[] { dep });
					}
				}
			}
		});

		addMultipleItem.addActionListener(e -> {
			if (isJarTable) {
				JFileChooser fc = new JFileChooser();
				fc.setMultiSelectionEnabled(true);
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
				int res = fc.showOpenDialog(MainWindow.this);
				if (res == JFileChooser.APPROVE_OPTION) {
					File[] files = fc.getSelectedFiles();
					for (File f : files) {
						if (f != null && f.getName().toLowerCase().endsWith(".jar")) {
							String path = f.getAbsolutePath();
							if (!isJarInModel(path)) {
								model.addRow(new Object[] { path });
							}
						}
					}
				}
			} else {
				JTextArea ta = new JTextArea(8, 40);
				ta.setLineWrap(true);
				ta.setWrapStyleWord(true);
				int res = JOptionPane.showConfirmDialog(MainWindow.this, new JScrollPane(ta),
						"Paste Maven coordinates (one per line)", JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.PLAIN_MESSAGE);
				if (res == JFileChooser.APPROVE_OPTION) {
					String text = ta.getText();
					if (text != null && !text.trim().isEmpty()) {
						String[] lines = text.split("\\r?\\n");
						for (String line : lines) {
							String dep = line.trim();
							if (!dep.isEmpty()) {
								model.addRow(new Object[] { dep });
							}
						}
					}
				}
			}
		});

		removeItem.addActionListener(e -> removeSelectedRowsFromModel(table, model));

		popup.add(addItem);
		popup.add(addMultipleItem);
		popup.addSeparator();
		popup.add(removeItem);

		table.addMouseListener(new MouseAdapter() {
			private void showIfPopup(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int row = table.rowAtPoint(e.getPoint());
					if (row >= 0 && row < table.getRowCount()) {
						if (!table.isRowSelected(row)) {
							table.getSelectionModel().setSelectionInterval(row, row);
						}
					}
					popup.show(e.getComponent(), e.getX(), e.getY());
				}
			}

			@Override
			public void mousePressed(MouseEvent e) {
				showIfPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showIfPopup(e);
			}
		});

		table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK),
				"selectAll");
		table.getActionMap().put("selectAll", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				table.selectAll();
			}
		});
	}

	private void removeSelectedRowsFromModel(JTable table, DefaultTableModel model) {
		int[] sel = table.getSelectedRows();
		if (sel == null || sel.length == 0) {
			return;
		}
		Arrays.sort(sel);
		for (int i = sel.length - 1; i >= 0; i--) {
			int row = sel[i];
			if (row >= 0 && row < model.getRowCount()) {
				model.removeRow(row);
			}
		}
	}

	private JButton createAccentButton(String text) {
		final JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setForeground(Color.BLACK);
		b.setBackground(panel);
		b.setBorder(BorderFactory.createCompoundBorder(new LineBorder(accent, 1, true),
				BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		final Font originalFont = b.getFont();
		final float baseSize = originalFont.getSize2D();
		final float maxScale = 1.06f;

		final int delay = 15;
		final float step = 0.08f;
		final float[] progress = new float[] { 0f };

		final Timer[] hoverAnim = new Timer[1];

		b.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) {
				if (hoverAnim[0] != null && hoverAnim[0].isRunning()) {
					hoverAnim[0].stop();
				}
				hoverAnim[0] = new Timer(delay, ev -> {
					progress[0] = Math.min(1f, progress[0] + step);
					float eased = easeOutCubic(progress[0]);
					b.setBackground(interpolateColor(panel, accent, eased));
					float newSize = baseSize * (1f + (maxScale - 1f) * eased);
					b.setFont(originalFont.deriveFont(Font.PLAIN, newSize));
					b.repaint();
					if (progress[0] >= 1f) {
						((Timer) ev.getSource()).stop();
					}
				});
				hoverAnim[0].start();
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (hoverAnim[0] != null && hoverAnim[0].isRunning()) {
					hoverAnim[0].stop();
				}
				hoverAnim[0] = new Timer(delay, ev -> {
					progress[0] = Math.max(0f, progress[0] - step);
					float eased = easeOutCubic(progress[0]);
					b.setBackground(interpolateColor(panel, accent, eased));
					float newSize = baseSize * (1f + (maxScale - 1f) * eased);
					b.setFont(originalFont.deriveFont(Font.PLAIN, newSize));
					b.repaint();
					if (progress[0] <= 0f) {
						((Timer) ev.getSource()).stop();
					}
				});
				hoverAnim[0].start();
			}
		});

		return b;
	}

	private JButton createGhostButton(String text) {
		final JButton b = new JButton(text);
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

	private JPanel createTargetsPanel() {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		p.setBackground(panel);
		JCheckBox[] boxes = { targetDesktop, targetAndroid, targetIos, targetTeavmC, targetTeavmJs, targetTeavmWasm,
				targetOtherJvm, targetGwt };
		for (JCheckBox cb : boxes) {
			cb.setBackground(panel);
			cb.setForeground(text);
			cb.setFocusPainted(false);
			cb.addItemListener(e -> updateDynamicComponents());
			p.add(cb);
		}
		return p;
	}

	private void applyComponentStyles() {
		Font tableFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		localJarsTable.setRowHeight(26);
		localJarsTable.setFont(tableFont);
		mavenTable.setRowHeight(26);
		mavenTable.setFont(tableFont);
		Font cbFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		for (Component c : getTargets()) {
			c.setFont(cbFont);
		}
		enableGraalNativeCheck.setFont(cbFont);
		autoRunGradleCheck.setFont(cbFont);
		targetGwt.setFont(cbFont);
	}

	private JCheckBox[] getTargets() {
		return new JCheckBox[] { targetDesktop, targetAndroid, targetIos, targetTeavmC, targetTeavmJs, targetTeavmWasm,
				targetOtherJvm, targetGwt };
	}

	private void onGenerate(JButton generateBtn) {
		if (!validateBeforeGenerate()) {
			return;
		}
		ProjectConfig cfg = new ProjectConfig();
		cfg.projectName = projectNameField.getText().trim();
		cfg.projectPackage = packageField.getText().trim();
		cfg.mainClass = mainClassField.getText().trim();
		cfg.outDir = outPathField.getText().trim();

		try {
			cfg.width = Integer.parseInt(widthField.getText().trim());
			cfg.height = Integer.parseInt(heightField.getText().trim());
		} catch (Exception e) {
			appendLog("Warning: Invalid width/height, use default 480x320");
		}

		cfg.sdkPath = sdkPathField.getText().trim();
		cfg.androidMinSdk = androidMinSdkField.getText().trim();
		cfg.androidMaxSdk = androidMaxSdkField.getText().trim();

		cfg.lwjglVersion = lwjglVersionField.getText().trim();
		cfg.graalHelperVersion = graalHelperVersionField.getText().trim();
		cfg.enableGraalNative = enableGraalNativeCheck.isSelected();
		cfg.robovmVersion = robovmVersionField.getText().trim();
		cfg.loonVersion = loonVersionField.getText().trim();
		cfg.projectVersion = projectVersionField.getText().trim();
		cfg.autoRunGradleWrapper = autoRunGradleCheck.isSelected();

		cfg.gwtVersion = gwtVersionField.getText().trim();
		cfg.teavmVersion = teavmVersionField.getText().trim();
		cfg.teavmCOutput = teavmCOutputField.getText().trim();

		cfg.targets = new ArrayList<String>();
		if (targetDesktop.isSelected()) {
			cfg.targets.add("desktop");
		}
		if (targetAndroid.isSelected()) {
			cfg.targets.add("android");
		}
		if (targetIos.isSelected()) {
			cfg.targets.add("ios");
		}
		if (targetTeavmC.isSelected()) {
			cfg.targets.add("teavm-c");
		}
		if (targetTeavmJs.isSelected()) {
			cfg.targets.add("teavm-js");
		}
		if (targetTeavmWasm.isSelected()) {
			cfg.targets.add("teavm-wasm");
		}
		if (targetOtherJvm.isSelected()) {
			cfg.targets.add("other-jvm");
		}
		if (targetGwt.isSelected()) {
			cfg.targets.add("gwt");
		}
		if (cfg.targets.size() > 0) {
			cfg.targets.add("core");
		}

		cfg.localJars = new ArrayList<String>();
		for (int i = 0; i < jarsModel.getRowCount(); i++) {
			cfg.localJars.add((String) jarsModel.getValueAt(i, 0));
		}
		cfg.mavenDeps = new ArrayList<String>();
		for (int i = 0; i < mavenModel.getRowCount(); i++) {
			cfg.mavenDeps.add((String) mavenModel.getValueAt(i, 0));
		}
		cfg.includeDesktop = targetDesktop.isSelected();

		appendLog("=====================================");
		appendLog("Starting project generation...");
		progressBar.setIndeterminate(true);

		generateBtn.setEnabled(false);

		SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
			@Override
			protected Void doInBackground() throws Exception {
				ProjectGenerator gen = new ProjectGenerator();
				gen.generate(cfg, msg -> publish(msg));
				return null;
			}

			@Override
			protected void process(List<String> chunks) {
				for (String m : chunks) {
					appendLog(m);
				}
			}

			@Override
			protected void done() {
				try {
					get();
					appendLog("✅ Generation finished successfully!");
				} catch (Exception ex) {
					appendLog("❌ Error: " + ex.getMessage());
				} finally {
					progressBar.setIndeterminate(false);
					generateBtn.setEnabled(true);
				}
			}
		};
		worker.execute();
	}

	private void appendLog(String textLine) {
		logArea.append(textLine + "\n");
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	private boolean validateBeforeGenerate() {
		boolean anyTarget = Arrays.stream(getTargets()).anyMatch(cb -> ((JCheckBox) cb).isSelected());
		if (!anyTarget) {
			JOptionPane.showMessageDialog(this, "Please select at least one target platform before generating.",
					"No target selected", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		// output path
		String outPath = outPathField.getText().trim();
		if (outPath.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Output path cannot be empty.", "Invalid path",
					JOptionPane.ERROR_MESSAGE);
			outPathField.requestFocus();
			return false;
		}
		File out = new File(outPath);
		if (!out.exists()) {
			boolean ok = out.mkdirs();
			if (!ok) {
				JOptionPane.showMessageDialog(this, "Output path is invalid or not writable.", "Invalid path",
						JOptionPane.ERROR_MESSAGE);
				outPathField.requestFocus();
				return false;
			}
		} else if (!out.canWrite()) {
			JOptionPane.showMessageDialog(this, "Output path is not writable.", "Invalid path",
					JOptionPane.ERROR_MESSAGE);
			outPathField.requestFocus();
			return false;
		}
		if (!packageField.getText().trim().matches("([a-zA-Z_]\\w*)(\\.[a-zA-Z_]\\w*)*")) {
			JOptionPane.showMessageDialog(this, "Package name looks invalid.", "Invalid package",
					JOptionPane.ERROR_MESSAGE);
			packageField.requestFocus();
			return false;
		}
		if (!mainClassField.getText().trim().matches("[A-Z][A-Za-z0-9_]*")) {
			JOptionPane.showMessageDialog(this, "Main class name looks invalid. Start with uppercase letter.",
					"Invalid class", JOptionPane.ERROR_MESSAGE);
			mainClassField.requestFocus();
			return false;
		}
		return true;
	}

	private void scanAndLoadJars() {
		try {
			File runFile = null;
			try {
				runFile = new File(MainWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			} catch (URISyntaxException ignored) {
			}
			File dir;
			if (runFile != null && runFile.isFile()) {
				dir = runFile.getParentFile();
			} else {
				dir = new File(".").getCanonicalFile();
			}
			if (dir == null || !dir.exists()) {
				return;
			}
			File[] jars = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
			if (jars == null) {
				return;
			}
			for (File j : jars) {
				if (runFile != null) {
					try {
						if (runFile.getCanonicalPath().equals(j.getCanonicalPath()))
							continue;
					} catch (IOException ignored) {
					}
				}
				String path = j.getAbsolutePath();
				if (!isJarInModel(path)) {
					jarsModel.addRow(new Object[] { path });
				}
			}
		} catch (Exception ex) {
			appendLog("Auto-scan jars failed: " + ex.getMessage());
		}
	}

	private boolean isJarInModel(String path) {
		for (int i = 0; i < jarsModel.getRowCount(); i++) {
			if (path.equals(jarsModel.getValueAt(i, 0)))
				return true;
		}
		return false;
	}

	@SuppressWarnings({ "unchecked" })
	private void startWatchService() {
		try {
			final File runFileFinal;
			File tmp = null;
			try {
				tmp = new File(MainWindow.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			} catch (URISyntaxException ignored) {
			}
			runFileFinal = tmp;

			final File dir = (runFileFinal != null && runFileFinal.isFile()) ? runFileFinal.getParentFile()
					: new File(".").getCanonicalFile();
			if (dir == null || !dir.exists()) {
				return;
			}
			watchService = FileSystems.getDefault().newWatchService();
			final Path path = dir.toPath();
			path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

			watchRunning.set(true);
			watchThread = new Thread(() -> {
				while (watchRunning.get()) {
					try {
						WatchKey key = watchService.take();
						for (WatchEvent<?> ev : key.pollEvents()) {
							WatchEvent.Kind<?> kind = ev.kind();
							if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
								WatchEvent<Path> we = (WatchEvent<Path>) ev;
								Path created = we.context();
								if (created.toString().toLowerCase().endsWith(".jar")) {
									final File f = path.resolve(created).toFile();
									try {
										Thread.sleep(300);
									} catch (InterruptedException ie) {
										Thread.currentThread().interrupt();
									}
									SwingUtilities.invokeLater(() -> {
										try {
											if (runFileFinal != null
													&& runFileFinal.getCanonicalPath().equals(f.getCanonicalPath()))
												return;
										} catch (IOException ignored) {
										}
										String p = f.getAbsolutePath();
										if (!isJarInModel(p)) {
											jarsModel.addRow(new Object[] { p });
										}
									});
								}
							}
						}
						key.reset();
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					} catch (ClosedWatchServiceException cwse) {
						break;
					} catch (Exception ex) {
						appendLog("WatchService error: " + ex.getMessage());
					}
				}
			}, "JarWatchThread");
			watchThread.setDaemon(true);
			watchThread.start();
		} catch (Exception ex) {
			appendLog("Failed to start WatchService: " + ex.getMessage());
		}
	}

	private void stopWatchService() {
		watchRunning.set(false);
		try {
			if (watchService != null)
				watchService.close();
		} catch (IOException ignored) {
		}
	}

	private void animateVisibility(final Component comp, final boolean visible) {
		if (comp == null) {
			return;
		}
		if (visible) {
			comp.setVisible(true);
			comp.setEnabled(true);
			final Timer t = new Timer(15, null);
			final long start = System.currentTimeMillis();
			final int dur = 220;
			t.addActionListener(e -> {
				float p = Math.min(1f, (System.currentTimeMillis() - start) / (float) dur);
				if (comp instanceof JComponent) {
					((JComponent) comp).setOpaque(false);
				}
				Container parent = comp.getParent();
				if (parent != null) {
					parent.repaint();
				}
				if (p >= 1f) {
					((Timer) e.getSource()).stop();
				}
			});
			t.start();
		} else {
			final Timer t = new Timer(15, null);
			final long start = System.currentTimeMillis();
			final int dur = 180;
			t.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					float p = Math.min(1f, (System.currentTimeMillis() - start) / (float) dur);
					Container parent = comp.getParent();
					if (parent != null) {
						parent.repaint();
					}
					if (p >= 1f) {
						comp.setVisible(false);
						comp.setEnabled(false);
						((Timer) e.getSource()).stop();
					}
				}
			});
			t.start();
		}
	}

	private float easeOutCubic(float t) {
		return 1 - (float) Math.pow(1 - t, 3);
	}

	private Color interpolateColor(Color a, Color b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
		int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
		int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
		return new Color(r, g, bl);
	}

	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
		}
		SwingUtilities.invokeLater(() -> {
			MainWindow w = new MainWindow();
			w.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					w.stopWatchService();
					w.saveLists();
				}
			});
		});
	}
}
