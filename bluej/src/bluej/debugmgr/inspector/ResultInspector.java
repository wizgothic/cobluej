/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.debugmgr.inspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.debugger.DebuggerObject;
import bluej.debugger.gentype.GenTypeClass;
import bluej.debugger.gentype.GenTypeParameter;
import bluej.debugger.gentype.JavaType;
import bluej.debugmgr.ExpressionInformation;
import bluej.pkgmgr.Package;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.DialogManager;
import bluej.utility.JavaNames;
import bluej.utility.JavaUtils;
import bluej.utility.MultiLineLabel;
import bluej.views.Comment;
import bluej.views.LabelPrintWriter;
import bluej.views.MethodView;

/**
 * A window that displays a method return value.
 * 
 * @author Poul Henriksen
 * @version $Id: ResultInspector.java 6874 2009-11-30 05:46:18Z davmac $
 */
public class ResultInspector extends Inspector
{

    // === static variables ===

    protected final static String resultTitle = Config.getString("debugger.inspector.result.title");
    protected final static String returnedString = Config.getString("debugger.inspector.result.returned");

    // === instance variables ===

    protected DebuggerObject obj;
    protected String objName; // name on the object bench

    private ExpressionInformation expressionInformation;
    private JavaType resultType; // static result type

    

    /**
     * Note: 'pkg' may be null if 'ir' is null.
     * 
     * @param obj
     *            The object displayed by this viewer
     * @param name
     *            The name of this object or "null" if the name is unobtainable
     * @param pkg
     *            The package all this belongs to
     * @param ir
     *            the InvokerRecord explaining how we created this result/object
     *            if null, the "get" button is permanently disabled
     * @param info
     *            The expression used to create the object (ie. the method call
     *            information)
     * @param parent
     *            The parent frame of this frame
     */
    public ResultInspector(DebuggerObject obj, InspectorManager inspectorManager, String name, Package pkg, InvokerRecord ir, ExpressionInformation info,
            final JFrame parent)
    {
        super(inspectorManager, pkg, ir);

        expressionInformation = info;
        this.obj = obj;
        this.objName = name;

        calcResultType();

        makeFrame();
        pack();
        DialogManager.centreWindow(this, parent);
    }

    /**
     * Determine the expected static type of the result.
     */
    private void calcResultType()
    {
        GenTypeClass instanceType = expressionInformation.getInstanceType();
        // We know it's a MethodView, as we don't inspect the result of a
        // constructor!
        MethodView methodView = (MethodView) expressionInformation.getMethodView();
        Method m = methodView.getMethod();

        // Find the expected return type
        JavaType methodReturnType = JavaUtils.getJavaUtils().getReturnType(m);

        // TODO: infer type of generic parameters based on the actual
        // arguments passed to the method.
        // For now, use the base type of the any generic type parameters
        if (methodReturnType instanceof GenTypeParameter) {
            
            // The return type may contain type parameters. First, get the
            // type parameters of the object:
            Map tparmap;
            if (instanceType != null)
                tparmap = instanceType.mapToSuper(m.getDeclaringClass().getName()).getMap();
            else
                tparmap = new HashMap();
            
            // It's possible the mapping result is a raw type.
            if (tparmap == null) {
                resultType = JavaUtils.getJavaUtils().getRawReturnType(m);
                return;
            }
            
            // Then put in the type parameters from the method itself,
            // if there are any (ie. if the method is a generic method).
            // Tpars from the method override those from the instance.
            List tpars = JavaUtils.getJavaUtils().getTypeParams(m);
            if (tparmap != null)
                tparmap.putAll(JavaUtils.TParamsToMap(tpars));
            
            methodReturnType = ((JavaType) methodReturnType).mapTparsToTypes(tparmap);
        }

        resultType = methodReturnType;
    }

    /**
     * Returns a single string representing the return value.
     */
    protected Object[] getListData()
    {
        String fieldString;
        if (!resultType.isPrimitive()) {
            DebuggerObject resultObject = obj.getFieldObject(0, resultType);
            if (!resultObject.isNullObject())
                fieldString = resultObject.getGenType().toString(true);
            else
                fieldString = resultType.toString(true);
        }
        else
            fieldString = JavaNames.stripPrefix(obj.getFieldValueTypeString(0));
        fieldString += " = " + obj.getFieldValueString(0);

        return new Object[]{fieldString};
    }

    /**
     * Build the GUI
     * 
     * @param showAssert
     *            Indicates if assertions should be shown.
     */
    protected void makeFrame()
    {
        setTitle(resultTitle);
        setBorder(BlueJTheme.dialogBorder);

        // Create the header

        JComponent header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        Comment comment = expressionInformation.getComment();
        LabelPrintWriter commentLabelPrintWriter = new LabelPrintWriter();
        comment.print(commentLabelPrintWriter);
        MultiLineLabel commentLabel = commentLabelPrintWriter.getLabel();
        commentLabel.setForeground(Color.GRAY);
        header.add(commentLabel);
        JLabel sig = new JLabel(expressionInformation.getSignature());
        sig.setForeground(Color.GRAY);

        header.add(sig);
        header.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));
        header.add(new JSeparator());

        //Create the main part that shows the expression and the result

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setOpaque(false);

        Box result = Box.createVerticalBox();

        JLabel expression = new JLabel(expressionInformation.getExpression(), JLabel.LEFT);
        expression.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        result.add(expression);
        result.add(Box.createVerticalStrut(5));

        JLabel returnedLabel = new JLabel("  " + returnedString, JLabel.LEADING);
        returnedLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        result.add(returnedLabel);
        result.add(Box.createVerticalStrut(5));

        JScrollPane scrollPane = createFieldListScrollPane();
        scrollPane.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        result.add(scrollPane);
        result.add(Box.createVerticalStrut(5));

        mainPanel.add(result, BorderLayout.CENTER);

        JPanel inspectAndGetButtons = createInspectAndGetButtons();
        mainPanel.add(inspectAndGetButtons, BorderLayout.EAST);

        Insets insets = BlueJTheme.generalBorderWithStatusBar.getBorderInsets(mainPanel);
        mainPanel.setBorder(new EmptyBorder(insets));

        // create bottom button pane with "Close" button

        JPanel bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        if (inspectorManager != null && inspectorManager.inTestMode()) {
            assertPanel = new AssertPanel();
            {
                assertPanel.setAlignmentX(LEFT_ALIGNMENT);
                bottomPanel.add(assertPanel);
            }
        }
        
        JPanel buttonPanel;
        buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setOpaque(false);
        JButton button = createCloseButton();
        buttonPanel.add(button, BorderLayout.EAST);

        bottomPanel.add(buttonPanel);
        
        // add the components
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(header, BorderLayout.NORTH);
        contentPane.add(mainPanel, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(button);
    }

    /**
     * An element in the field list was selected.
     */
    protected void listElementSelected(int slot)
    {

        if (obj.instanceFieldIsObject(slot)) {
            String newInspectedName;

            if (objName != null) {
                newInspectedName = objName + "." + obj.getInstanceFieldName(slot);
            }
            else {
                newInspectedName = obj.getInstanceFieldName(slot);
            }

            // Don't use the name, since it is meaningless anyway (it is always "result")
            setCurrentObj(obj.getInstanceFieldObject(slot, resultType), null, resultType.toString(false));

            if (obj.instanceFieldIsPublic(slot)) {
                setButtonsEnabled(true, true);
            }
            else {
                setButtonsEnabled(true, false);
            }
        }
        else {
            setCurrentObj(null, null, null);
            setButtonsEnabled(false, false);
        }
    }

    /**
     * Show the inspector for the class of an object.
     */
    protected void showClass()
    {
        inspectorManager.getClassInspectorInstance(obj.getClassRef(), pkg, this);
    }

    /**
     * We are about to inspect an object - prepare.
     */
    protected void prepareInspection()
    {}

    /**
     * Remove this inspector.
     */
    protected void remove()
    {
        if(inspectorManager != null) {
            inspectorManager.removeInspector(obj);
        }
    }

    /**
     * return a String with the result.
     * 
     * @return The Result value
     */
    public String getResult()
    {
        return (String) obj.getInstanceFields(false).get(0);
    }

    protected int getPreferredRows()
    {
        return 2;
    }
    
    protected void doGet()
    {
        if (selectedField != null) {
            pkg.getEditor().raisePutOnBenchEvent(this, selectedField, resultType.asClass(), ir);
        }
    }
}
