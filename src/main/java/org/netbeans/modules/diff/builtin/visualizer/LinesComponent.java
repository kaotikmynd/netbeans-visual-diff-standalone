/***************************************************
*
* cismet GmbH, Saarbruecken, Germany
*
*              ... and it just works.
*
****************************************************/
/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.netbeans.modules.diff.builtin.visualizer;

import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.FontMetricsCache;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;

import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.util.*;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;

import javax.swing.*;
import javax.swing.text.*;

/**
 * GlyphGutter is component for displaying line numbers and annotation glyph icons. Component also allow to "cycle"
 * through the annotations. It means that if there is more than one annotation on the line, only one of them might be
 * visible. And clicking the special cycling button in the gutter the user can cycle through the annotations.
 *
 * @author   David Konecny
 * @version  $Revision$, $Date$
 * @since    07/2001
 */

public class LinesComponent extends JComponent implements javax.accessibility.Accessible, PropertyChangeListener {

    //~ Static fields/initializers ---------------------------------------------

    /** The gutter height is enlarged by number of lines which specifies this constant. */
    private static final int ENLARGE_GUTTER_HEIGHT = 300;

    private static final long serialVersionUID = -4861542695772182147L;

    //~ Instance fields --------------------------------------------------------

    /** Document to which this gutter is attached. */
    private JEditorPane editorPane;

    private EditorUI editorUI;

    /** Backroung color of the gutter. */
    private Color backgroundColor;

    /** Foreground color of the gutter. Used for drawing line numbers. */
    private Color foreColor;

    /** Font used for drawing line numbers. */
    private Font font;

    /** Flag whther the gutter was initialized or not. The painting is disabled till the gutter is not initialized */
    private boolean init;

    /** Width of the column used for drawing line numbers. The value contains also line number margins. */
    private int numberWidth;

    /** Whether the line numbers are shown or not. */
    private boolean showLineNumbers = true;

    /** The hightest line number. This value is used for calculating width of the gutter */
    private int highestLineNumber = 0;

    /** Holds value of property lineNumberMargin. */
    private Insets lineNumberMargin;

    /** Holds value of property lineNumberDigitWidth. */
    private int lineNumberDigitWidth;

    private LinkedList<String> linesList;

    /** Holds value of property activeLine. */
    private int activeLine = -1;

    //~ Constructors -----------------------------------------------------------

    /**
     * Creates a new LinesComponent object.
     *
     * @param  pane  DOCUMENT ME!
     */
    public LinesComponent(final JEditorPane pane) {
        super();
        init = false;
        editorPane = pane;
        font = editorPane.getFont();
        foreColor = editorPane.getForeground();
        backgroundColor = editorPane.getBackground();
        setLineNumberDigitWidth(10);
        setLineNumberMargin(new Insets(2, 2, 2, 4));
        editorUI = org.netbeans.editor.Utilities.getEditorUI(editorPane);

        if (editorUI == null) {
            editorUI = new EditorUI();
        }
        editorUI.addPropertyChangeListener(WeakListeners.propertyChange(this, editorUI));
        init();
    }

    //~ Methods ----------------------------------------------------------------

    /* Read accessible context
     * @return - accessible context
     */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {

                    @Override
                    public AccessibleRole getAccessibleRole() {
                        return AccessibleRole.PANEL;
                    }
                };
        }
        return accessibleContext;
    }

    /**
     * Do initialization of the glyph gutter.
     */
    protected void init() {
        createLines();
        getAccessibleContext().setAccessibleName(NbBundle.getMessage(LinesComponent.class, "ACSN_Lines_Component")); // NOI18N
        getAccessibleContext().setAccessibleDescription(NbBundle.getMessage(
                LinesComponent.class,
                "ACSD_Lines_Component"));                                                                            // NOI18N
    }

    /**
     * DOCUMENT ME!
     */
    private void createLines() {
        linesList = new LinkedList<String>();
        final int lineCnt;
        final StyledDocument doc = (StyledDocument)editorPane.getDocument();
        final int lastOffset = doc.getEndPosition().getOffset();
        lineCnt = org.openide.text.NbDocument.findLineNumber(doc, lastOffset);
        for (int i = 0; i < lineCnt; i++) {
            linesList.add(Integer.toString(i + 1));
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @param  line   DOCUMENT ME!
     * @param  count  DOCUMENT ME!
     */
    public void addEmptyLines(final int line, final int count) {
        final boolean appending = line > linesList.size();
        for (int i = 0; i < count; i++) {
            if (appending) {
                linesList.add("");
            } else {
                linesList.add(line, "");
            }
        }
    }

    /**
     * Insert line numbers. If at the end, then line numbers are added to the end of the component. If in the middle,
     * subsequent lines are overwritten.
     *
     * @param  line      DOCUMENT ME!
     * @param  startNum  DOCUMENT ME!
     * @param  count     DOCUMENT ME!
     */
    public void insertNumbers(int line, int startNum, int count) {
        final boolean appending = line >= linesList.size();
        if (appending) {
            for (int i = 0; i < count; i++, startNum++) {
                linesList.add(Integer.toString(startNum));
            }
        } else {
            final int toAdd = Math.max(line + count - linesList.size(), 0);
            count -= toAdd;
            for (int i = 0; i < count; i++, startNum++, line++) {
                linesList.set(line, Integer.toString(startNum));
            }
            for (int i = 0; i < toAdd; i++, startNum++) {
                linesList.add(Integer.toString(startNum));
            }
        }
    }

    /*
     * Test method.
     *
     * private void dumpResultLineNumbers() { System.out.print("LinesComponent: linesList = "); boolean was = false; for
     * (int i = 0; i < linesList.size(); i++) {     System.out.print(linesList.get(i)+", "); } System.out.println(""); }
     */

    /**
     * Remove line numbers and leave the corresponding part of the lines component empty. If at the end, then an empty
     * space is added to the end of the component. If in the middle, subsequent lines are overwritten by an empty space.
     *
     * @param  line   DOCUMENT ME!
     * @param  count  DOCUMENT ME!
     */
    public void removeNumbers(int line, int count) {
        final boolean appending = line >= linesList.size();
        if (appending) {
            for (int i = 0; i < count; i++) {
                linesList.add("");
            }
        } else {
            final int toAdd = Math.max(line + count - linesList.size(), 0);
            count -= toAdd;
            for (int i = 0; i < count; i++, line++) {
                linesList.set(line, "");
            }
            for (int i = 0; i < toAdd; i++) {
                linesList.add("");
            }
        }
    }

    /**
     * Shrink the component, so that it will have <code>numLines</code> number of lines.
     *
     * @param  numLines  The new number of lines
     */
    public void shrink(final int numLines) {
        while (linesList.size() > numLines) {
            linesList.remove(numLines);
        }
    }

    /**
     * Update colors, fonts, sizes and invalidate itself. This method is called from EditorUI.update()
     *
     * @param  g  DOCUMENT ME!
     */
    private void updateState(final Graphics g) {
        // System.out.println("  => correction = "+lineHeightCorrection);
        final String mimeType = DocumentUtilities.getMimeType(editorPane);
        final FontColorSettings fcs = MimeLookup.getLookup(mimeType).lookup(FontColorSettings.class);
        final Coloring col = Coloring.fromAttributeSet(AttributesUtilities.createComposite(
                    fcs.getFontColors(FontColorNames.LINE_NUMBER_COLORING),
                    fcs.getFontColors(FontColorNames.DEFAULT_COLORING)));

        foreColor = col.getForeColor();
        backgroundColor = col.getBackColor();
        // System.out.println("  => foreground = "+foreColor+", background = "+backgroundColor);

        font = col.getFont();
        final FontMetrics fm = g.getFontMetrics(font);
        /*
         * int maxHeight = 1; int maxAscent = 0; if (fm != null) { maxHeight = Math.max(maxHeight, fm.getHeight());
         * maxAscent = Math.max(maxAscent, fm.getAscent()); }
         *
         * // Apply lineHeightCorrection lineHeight = (int)(maxHeight * lineHeightCorrection); lineAscent =
         * (int)(maxAscent * lineHeightCorrection);
         */
        // System.out.println("lineheight=" + lineHeight);//+", fm height = "+fm.getHeight());
        // System.out.println("lineascent=" + lineAscent);//+", fm ascent = "+fm.getAscent());
        showLineNumbers = true;

        /*
         * lineHeight = editorUI.getLineHeight(); lineAscent = editorUI.getLineAscent(); System.out.println("lineHeight
         * = "+lineHeight); System.out.println("lineascent=" + lineAscent);
         *
         * showLineNumbers = editorUI.isLineNumberEnabled();
         */

        init = true;

        // initialize the value with current number of lines
        if (highestLineNumber <= getLineCount()) {
            highestLineNumber = getLineCount();
        }
//        System.out.println("highestLineNumber=" + highestLineNumber);
        // width of a digit..
        int maxWidth = 1;
        final char[] digit = new char[1]; // will be used for '0' - '9'
        for (int i = 0; i <= 9; i++) {
            digit[0] = (char)('0' + i);
            maxWidth = Math.max(maxWidth, fm.charsWidth(digit, 0, 1));
        }
        setLineNumberDigitWidth(maxWidth);
//        System.out.println("maxwidth=" + maxWidth);
//        System.out.println("numner of lines=" + highestLineNumber);

        resize();
    }

    /**
     * DOCUMENT ME!
     */
    protected void resize() {
        final Dimension dim = new Dimension();
//        System.out.println("resizing...................");
        dim.width = getWidthDimension();
        dim.height = getHeightDimension();
        // enlarge the gutter so that inserting new lines into
        // document does not cause resizing too often
        dim.height += ENLARGE_GUTTER_HEIGHT * editorUI.getLineHeight();

        numberWidth = getLineNumberWidth();
        setPreferredSize(dim);

        revalidate();
    }

    /**
     * Return number of lines in the document.
     *
     * @return  DOCUMENT ME!
     */
    protected int getLineCount() {
        return linesList.size();
    }

    /**
     * Gets number of digits in the number.
     *
     * @param   number  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    protected int getDigitCount(final int number) {
        return Integer.toString(number).length();
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    protected int getLineNumberWidth() {
        int newWidth = 0;
        final Insets insets = getLineNumberMargin();
        if (insets != null) {
            newWidth += insets.left + insets.right;
        }
        newWidth += (getDigitCount(highestLineNumber) + 1) * getLineNumberDigitWidth();
//        System.out.println("new width=" + newWidth);
        return newWidth;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    protected int getWidthDimension() {
        int newWidth = 0;

        if (showLineNumbers) {
            newWidth += getLineNumberWidth();
        }

        return newWidth;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    protected int getHeightDimension() {
        /*TEMP+ (int)editorPane.getSize().getHeight() */
        final View rootView = org.netbeans.editor.Utilities.getDocumentView(editorPane);
        int height = highestLineNumber * editorUI.getLineHeight();
        if (rootView != null) {
            try {
                final int lineCount = rootView.getViewCount();
                if (lineCount > 0) {
                    final Rectangle rec = editorPane.modelToView(rootView.getView(lineCount - 1).getEndOffset() - 1);
                    height = rec.y + rec.height;
                }
            } catch (BadLocationException ex) {
                //
            }
        }
        return height;
    }

    /**
     * Paint the gutter itself.
     *
     * @param  g  DOCUMENT ME!
     */
    @Override
    public void paintComponent(final Graphics g) {
        super.paintComponent(g);
        if (!init) {
            updateState(g);
        }
        // return;

        final Rectangle drawHere = g.getClipBounds();

        // Fill clipping area with dirty brown/orange.
        g.setColor(backgroundColor);
        g.fillRect(drawHere.x, drawHere.y, drawHere.width, drawHere.height);

        g.setFont(font);
        g.setColor(foreColor);

        final FontMetrics fm = FontMetricsCache.getFontMetrics(font, this);
        int rightMargin = 0;
        final Insets margin = getLineNumberMargin();
        if (margin != null) {
            rightMargin = margin.right;
        }
        // calculate the first line which must be drawn
        final View rootView = org.netbeans.editor.Utilities.getDocumentView(editorPane);
        final int pos = editorPane.viewToModel(new Point(0, drawHere.y));
        int line = rootView.getViewIndex(pos, Position.Bias.Forward);
        if (line > 0) {
            --line;
        }
        try {
            // calculate the Y of the first line
            final Rectangle rec = editorPane.modelToView(rootView.getView(line).getStartOffset());
            if (rec == null) {
                return;
            }
            int y = rec.y;

            // draw liune numbers and annotations while we are in visible area
            final int lineHeight = editorUI.getLineHeight();
            final int lineAscent = editorUI.getLineAscent();
            final int lineCount = rootView.getViewCount();
            while ((line < lineCount) && ((y + (lineHeight / 2)) <= (drawHere.y + drawHere.height))) {
                final View view = rootView.getView(line);
                final Rectangle rec1 = editorPane.modelToView(view.getStartOffset());
                final Rectangle rec2 = editorPane.modelToView(view.getEndOffset() - 1);
                if ((rec1 == null) || (rec2 == null)) {
                    break;
                }
                y = (int)rec1.getY();
                // draw line numbers if they are turned on
                if (showLineNumbers) {
                    String lineStr = null;
                    if (line < linesList.size()) {
                        lineStr = linesList.get(line);
                    }
                    if (lineStr == null) {
                        lineStr = "";                // NOI18N
                    }
                    final String activeSymbol = "*"; // NOI18N
                    int lineNumberWidth = fm.stringWidth(lineStr);
                    if (line == (activeLine - 1)) {
                        lineStr = lineStr + activeSymbol;
                    }
                    final int activeSymbolWidth = fm.stringWidth(activeSymbol);
                    lineNumberWidth = lineNumberWidth + activeSymbolWidth;
                    g.drawString(lineStr, numberWidth - lineNumberWidth - rightMargin, y + lineAscent);
                }

                y += (int)(rec2.getY() + rec2.getHeight() - rec1.getY());
                line++;
            }
        } catch (BadLocationException ex) {
            return;
        }
    }

    /**
     * Repaint whole gutter.
     */
    public void changedAll() {
        if (!init) {
            return;
        }

/*        int lineCnt;
        try {
            lineCnt = Utilities.getLineOffset(doc, doc.getLength()) + 1;
        } catch (BadLocationException e) {
            lineCnt = 1;
        }
 */

        repaint();
        checkSize();
    }

    /**
     * DOCUMENT ME!
     */
    protected void checkSize() {
        final int count = getLineCount();
        if (count > highestLineNumber) {
            highestLineNumber = count;
        }
        final Dimension dim = getPreferredSize();
        if ((getWidthDimension() > dim.width)
                    || (getHeightDimension() > dim.height)) {
            resize();
        }
    }

    /**
     * Getter for property lineNumberMargin.
     *
     * @return  Value of property lineNumberMargin.
     */
    public Insets getLineNumberMargin() {
        return this.lineNumberMargin;
    }

    /**
     * Setter for property lineNumberMargin.
     *
     * @param  lineNumberMargin  New value of property lineNumberMargin.
     */
    public void setLineNumberMargin(final Insets lineNumberMargin) {
        this.lineNumberMargin = lineNumberMargin;
    }

    /**
     * Getter for property lineNumberDigitWidth.
     *
     * @return  Value of property lineNumberDigitWidth.
     */
    public int getLineNumberDigitWidth() {
        return this.lineNumberDigitWidth;
    }

    /**
     * Setter for property lineNumberDigitWidth.
     *
     * @param  lineNumberDigitWidth  New value of property lineNumberDigitWidth.
     */
    public void setLineNumberDigitWidth(final int lineNumberDigitWidth) {
        this.lineNumberDigitWidth = lineNumberDigitWidth;
    }

    /**
     * Getter for property activeLine.
     *
     * @return  Value of property activeLine.
     */
    public int getActiveLine() {
        return this.activeLine;
    }

    /**
     * Setter for property activeLine.
     *
     * @param  activeLine  New value of property activeLine.
     */
    public void setActiveLine(final int activeLine) {
        this.activeLine = activeLine;
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        init = false;
        repaint();
    }
}
