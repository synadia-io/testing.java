// Copyright 2023 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.synadia.tools;

import javax.swing.*;
import java.awt.*;

public class WindowDisplay extends JPanel implements Displayable {

    static final int HEIGHT_REDUCTION = 45;

    static final Font DISPLAY_FONT;
    static final int SCREEN_AVAILABLE_WIDTH;
    static final int SCREEN_AVAILABLE_HEIGHT;
    static final int ROWS;

    static final String INDENT = "   ";

    final JTextArea area;
    final JFrame frame;
    final String name;

    static {
        // figure UI_FONT
        String fontName = Font.MONOSPACED;
        GraphicsEnvironment localEnv;
        localEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] allfonts = localEnv.getAvailableFontFamilyNames();
        for (String allfont : allfonts) {
            if (allfont.equals("JetBrains Mono")) {
                fontName = allfont;
                break;
            }
        }
        DISPLAY_FONT = new Font(fontName, Font.PLAIN, 14);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        SCREEN_AVAILABLE_WIDTH = (int)screenSize.getWidth();
        SCREEN_AVAILABLE_HEIGHT = (int)screenSize.getHeight() - HEIGHT_REDUCTION;

        ROWS = 30; // SCREEN_AVAILABLE_HEIGHT / 21;
    }

    public static WindowDisplay instance(String name, int xLoc, int yLoc, int width, int height) {
        //Create and set up the window.
        JFrame frame = new JFrame(name);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Add contents to the window.
        WindowDisplay display = new WindowDisplay(name, frame);
        frame.add(display);

        //WindowDisplay the window.
        frame.setLocation(xLoc, yLoc);
        frame.setPreferredSize(new Dimension(width, height));
        frame.pack();
        frame.setVisible(true);

        ImageIcon imgicon = new ImageIcon("icon.png");
        frame.setIconImage(imgicon.getImage());

        display.clear();
        return display;
    }

    private WindowDisplay(String name, JFrame frame) {
        super(new GridLayout(1, 1));
        this.name = name;
        this.frame = frame;
        area = new JTextArea(ROWS, 40);
        area.setBackground(Color.BLACK);
        area.setForeground(Color.WHITE);
        area.setEditable(false);
        area.setFont(DISPLAY_FONT);
        add(new JScrollPane(area));
    }

    private boolean showingWait = false;

    public void clear() {
        showingWait = false;
        area.setText(null);
        area.append("\n");
    }

    @Override
    public void showWait() {
        if (!showingWait) {
            showingWait = true;
            area.append("\n");
            area.append(INDENT);
        }
        area.append(".");
    }

    public void print(String s) {
        showingWait = false;
        area.append(s);
    }

    @Override
    public void printf(String format, Object... args) {
        showingWait = false;
        area.append(INDENT);
        area.append(String.format(format, args));
    }

    public void println() {
        showingWait = false;
        area.append("\n");
    }

    public void println(String s) {
        showingWait = false;
        area.append(INDENT);
        area.append(s);
        area.append("\n");
    }
}
