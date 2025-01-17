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
package bluej.prefmgr;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

import bluej.Config;
import bluej.BlueJTheme;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.utility.filefilter.DirectoryFilter;

/**
 * A PrefPanel subclass to allow the user to interactively edit
 * various miscellaneous settings
 *
 * @author  Andrew Patterson
 * @version $Id: MiscPrefPanel.java 6215 2009-03-30 13:28:25Z polle $
 */
public class MiscPrefPanel extends JPanel 
                           implements PrefPanelListener, ItemListener, ActionListener
{
    private static final String bluejJdkURL = "bluej.url.javaStdLib";
    private static final String greenfootJdkURL = "greenfoot.url.javaStdLib";
    private static final String toolkitDir = "bluej.javame.toolkit.dir";
   
    private JLabel toolkitDirLabel;
    private JButton toolkitBrowseButton;
    private JTextField jdkURLField, toolkitDirField;
    private JCheckBox linkToLibBox;
    private JCheckBox showUncheckedBox; // show "unchecked" compiler warning
    private JCheckBox showTestBox;
    private JCheckBox showTeamBox;
    private JCheckBox showJavaMEBox;
    private String jdkURLPropertyName;
    private JPanel toolkitPanel;
     
    /**
     * Setup the UI for the dialog and event handlers for the buttons.
     */
    public MiscPrefPanel()
    {
        if(Config.isGreenfoot()) {
            jdkURLPropertyName = greenfootJdkURL;
        }
        else {
            jdkURLPropertyName = bluejJdkURL;
        }
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        add(box);
        
        setBorder(BlueJTheme.generalBorder);

        box.add(Box.createVerticalGlue());

        JPanel docPanel = new JPanel();
        {
            docPanel.setLayout(new BoxLayout(docPanel, BoxLayout.Y_AXIS));
            String docTitle = Config.getString("prefmgr.misc.documentation.title");
            docPanel.setBorder(BorderFactory.createCompoundBorder(
                                        BorderFactory.createTitledBorder(docTitle),
                                        BlueJTheme.generalBorder));
            docPanel.setAlignmentX(LEFT_ALIGNMENT);

            JPanel urlPanel = new JPanel(new BorderLayout(5, 0));
            {
                urlPanel.add(new JLabel(Config.getString("prefmgr.misc.jdkurlpath")), 
                             BorderLayout.WEST);
                jdkURLField = new JTextField(32);
                urlPanel.add(jdkURLField, BorderLayout.CENTER);
            }
            urlPanel.setAlignmentX(LEFT_ALIGNMENT);
            docPanel.add(urlPanel);

            docPanel.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));

            linkToLibBox = new JCheckBox(Config.getString("prefmgr.misc.linkToLib"));
            linkToLibBox.setAlignmentX(LEFT_ALIGNMENT);
            docPanel.add(linkToLibBox);

            docPanel.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));

            JLabel linkToLibNoteLine1 = new JLabel(
                              Config.getString("prefmgr.misc.linkToLibNoteLine1"));
            Font smallFont = linkToLibNoteLine1.getFont().deriveFont(10);
            linkToLibNoteLine1.setFont(smallFont);
            linkToLibNoteLine1.setAlignmentX(LEFT_ALIGNMENT);
            docPanel.add(linkToLibNoteLine1);

            JLabel linkToLibNoteLine2 = new JLabel(
                              Config.getString("prefmgr.misc.linkToLibNoteLine2"));
            linkToLibNoteLine2.setFont(smallFont);
            linkToLibNoteLine2.setAlignmentX(LEFT_ALIGNMENT);
            docPanel.add(linkToLibNoteLine2);
        }
        box.add(docPanel);

        if(!Config.isGreenfoot()) {
            box.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));

            JPanel testPanel = new JPanel(new GridLayout(0,1,0,0));
            {
                testPanel.setBorder(BorderFactory.createCompoundBorder(
                                              BorderFactory.createTitledBorder(
                                                     Config.getString("prefmgr.misc.tools.title")),
                                              BlueJTheme.generalBorder));
                testPanel.setAlignmentX(LEFT_ALIGNMENT);

                showTestBox = new JCheckBox(Config.getString("prefmgr.misc.showTesting"));
                testPanel.add(showTestBox);

                showTeamBox = new JCheckBox(Config.getString("prefmgr.misc.showTeam"));
                testPanel.add(showTeamBox);
                
                showJavaMEBox = new JCheckBox(Config.getString("prefmgr.misc.showJavaME"));
                testPanel.add(showJavaMEBox);
                
                toolkitPanel = new JPanel( new BorderLayout( 5, 0 ) );
                {
                    toolkitDirLabel = new JLabel( Config.getString( "prefmgr.misc.wtk.dir.label" ) );
                    toolkitDirField = new JTextField( );
                    toolkitBrowseButton = new JButton( Config.getString( "prefmgr.misc.wtk.button" ) );
                    toolkitPanel.add( toolkitDirLabel,     BorderLayout.WEST   );
                    toolkitPanel.add( toolkitDirField,     BorderLayout.CENTER );
                    toolkitPanel.add( toolkitBrowseButton, BorderLayout.EAST   );                   
                }
                testPanel.add( toolkitPanel );
            }
            box.add(testPanel);

            box.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));

            JPanel vmPanel = new JPanel(new GridLayout(0,1,0,0));
            {
                vmPanel.setBorder(BorderFactory.createCompoundBorder(
                                              BorderFactory.createTitledBorder(
                                                     Config.getString("prefmgr.misc.vm.title")),
                                              BlueJTheme.generalBorder));
                vmPanel.setAlignmentX(LEFT_ALIGNMENT);

                showUncheckedBox = new JCheckBox(Config.getString("prefmgr.misc.showUnchecked"));
                if (Config.isJava15()) {
                    // "unchecked" warnings only occur in Java 5.
                    vmPanel.add(showUncheckedBox);
                }
            }
            box.add(vmPanel);
        }

        box.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));
    }

    public void beginEditing()
    {
        linkToLibBox.setSelected(PrefMgr.getFlag(PrefMgr.LINK_LIB));
        jdkURLField.setText(Config.getPropString(jdkURLPropertyName));
        if(!Config.isGreenfoot()) {
            showTestBox.setSelected(PrefMgr.getFlag(PrefMgr.SHOW_TEST_TOOLS));
            showTeamBox.setSelected(PrefMgr.getFlag(PrefMgr.SHOW_TEAM_TOOLS));
            showJavaMEBox.setSelected(PrefMgr.getFlag(PrefMgr.SHOW_JAVAME_TOOLS));
            showUncheckedBox.setSelected(PrefMgr.getFlag(PrefMgr.SHOW_UNCHECKED));
                        
            if ( showJavaMEBox.isSelected( ) ) {
                toolkitDirField.setText( Config.getPropString( toolkitDir, "" ) );
                enableToolkitPanel( true ); 
            } else {
                toolkitDirField.setText( "" );
                enableToolkitPanel( false );   
            }
            showJavaMEBox.addItemListener( this ); 
            toolkitBrowseButton.addActionListener( this );
        }
    }

    public void revertEditing()
    {
    }

    public void commitEditing()
    {
        PrefMgr.setFlag(PrefMgr.LINK_LIB, linkToLibBox.isSelected());
        if(!Config.isGreenfoot()) {
            PrefMgr.setFlag(PrefMgr.SHOW_TEST_TOOLS, showTestBox.isSelected());
            PrefMgr.setFlag(PrefMgr.SHOW_TEAM_TOOLS, showTeamBox.isSelected());
            PrefMgr.setFlag(PrefMgr.SHOW_JAVAME_TOOLS, showJavaMEBox.isSelected());            
            PrefMgr.setFlag(PrefMgr.SHOW_UNCHECKED, showUncheckedBox.isSelected());

            PkgMgrFrame.updateTestingStatus();
            PkgMgrFrame.updateTeamStatus();
            PkgMgrFrame.updateJavaMEstatus(); 
        }
        
        String jdkURL = jdkURLField.getText();
        Config.putPropString(jdkURLPropertyName, jdkURL);

        if(!Config.isGreenfoot()) {
            String tkDir = toolkitDirField.getText( ); 
            if ( ! tkDir.equals( "" ) )
                Config.putPropString( toolkitDir, tkDir );
        }
    }
    
    /**
     * Gray out or not the components in the toolkit panel depending on the argument passed.
     */    
    private void enableToolkitPanel( boolean b ) 
    {
        toolkitDirLabel.setEnabled( b );
        toolkitDirField.setEnabled( b );
        toolkitBrowseButton.setEnabled( b );
    }

    /**
     * Called when user ticks or unticks the 'Show Java ME controls' checkbox. 
     * When ticked, we automatically try to find the location of the Toolkit, 
     * and if we can't find it we pop up a file chooser. When unticked, we
     * gray out the Wireless Toolkit panel.
     */    
    public void itemStateChanged( ItemEvent event )
    {
        if ( event.getStateChange( ) == ItemEvent.SELECTED ) 
        {       
            enableToolkitPanel( true );
            String toolkitDirectory = tryToFindToolkit( );
            if ( toolkitDirectory.equals( "" ) ) 
                letUserChooseToolkitDir( );
            else //we found a toolkit
                toolkitDirField.setText( toolkitDirectory );
        } 
        else  //checkbox was deselected
        {
            enableToolkitPanel( false );
            toolkitDirField.setText( "" );  
        }
    }


    /**
     * Find the Wireless Toolkit. In Windows we search all the filesystem roots.
     * In other systems (Linux, that is) we search the directories in the
     * initializer list of array 'roots' listed below. Note that we search only 
     * one level down from each root. That is, we can find C:\WTK2.5.1 but not
     * C:\someDirectory\WTK2.5.1, or /usr/local/WTK2.5.1 but not
     * /usr/local/mydir/WTK2.5.1
     * 
     * @return String containing our first guess or "" if toolkit not found.
     */    
    private String tryToFindToolkit( )
    {  
        File[ ] roots = { new File( System.getProperty( "user.home" ) ),
                          new File( "/usr/local"                      ),
                          new File( "/usr/lib"                        ), 
                        };   
        if ( Config.isWinOS( ) )
            roots = File.listRoots( ); 

        File[ ] dirs;
        for ( int i = 0; i < roots.length ; i++ )
        {
            dirs = roots[ i ].listFiles( new DirectoryFilter( ) );
            if ( dirs != null)
                for ( int j = 0; j < dirs.length ; j++ ) 
                    if ( isToolkitDirectory( dirs[ j ] ) )
                        return dirs[ j ].toString( ); 
        }
        return "";   
    }

    /**
     * Check whether a directory fulfills the requirements of being a Wireless
     * Toolkit. The requirements are:
     *   1. That the bin, lib, and docs directories be all present.
     *   2. That there is an emulator file under the bin directory.
     * These were taken from the Unified Emulator Interface specification, 
     * version 1.0.2, dated Apr 2006, http://java.sun.com/j2me/docs/uei_specs.pdf
     * 
     * @param  dirToCheck   directory to check     * 
     * @return true if it fulfills requirements false otherwise
     */ 
    private boolean isToolkitDirectory( File dirToCheck )
    {  
         File file = new File( dirToCheck, "bin" );
         if ( file.isDirectory( ) )
         {   
             File emulatorInLinux   = new File( file, "emulator" );
             File emulatorInWindows = new File( file, "emulator.exe" );
             if ( ( ! emulatorInWindows.exists( ) )  &&  ( ! emulatorInLinux.exists( ) ) )
                 return false; 
         }
         else 
             return false;
             
         file = new File( dirToCheck, "lib" );
         File anotherFile = new File( dirToCheck, "docs" );
         if ( file.isDirectory( )  &&  anotherFile.isDirectory( ) )
             return true;
         else
             return false;
    }    
  
    /**
     * Called when the Browse button is pressed.
     */    
    public void actionPerformed( ActionEvent e ) { letUserChooseToolkitDir( ); }
    
    /**
     * Pop up a file chooser to let user specify Toolkit location. 
     */
    private void letUserChooseToolkitDir( )
    {
        String toolkitDirectory = "";  
        JFileChooser chooser = new JFileChooser( );                   
        chooser.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
        chooser.setDialogTitle( Config.getString( "prefmgr.misc.filechooser.title" ) );
                
        int returnVal = chooser.showOpenDialog( getParent( ) ); 
        if ( returnVal == JFileChooser.APPROVE_OPTION )
        {
            toolkitDirectory = chooser.getSelectedFile( ).toString();
            toolkitDirField.setText( toolkitDirectory );  
        }
        else if ( returnVal == JFileChooser.CANCEL_OPTION ) 
        {
            String s = toolkitDirField.getText( ).trim( );
            if ( s.equals( "" ) )
            {
                showJavaMEBox.setSelected( false );
                enableToolkitPanel( false );
            }
        }
    }
} 