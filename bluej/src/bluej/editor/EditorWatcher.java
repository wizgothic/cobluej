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
package bluej.editor;

/**
 * @version $Id: EditorWatcher.java 6619 2009-09-04 02:33:09Z davmac $
 * @author Michael Kolling
 * Interface between the editor and the rest of BlueJ
 * The editor uses this class
 */
public interface EditorWatcher
{
    /**
     * Called by Editor when a file is changed
     */
    void modificationEvent(Editor editor);

    /**
     * Called by Editor when a file is saved
     */
    void saveEvent(Editor editor);

    /**
     * Called by Editor when it is closed
     */
    void closeEvent(Editor editor);

    /**
     * Called by Editor to set/clear a breakpoint
     * @param lineNo		the line number of the breakpoint
     * @param set		whether the breakpoint is set (true) or cleared
     * @return             An error message or null if okay.
     */
    String breakpointToggleEvent(Editor editor, int lineNo, 
                                 boolean set);

    /**
     * Called by Editor when a file is to be compiled
     */
    void compile(Editor editor);
    
    /**
     * Called by Editor when documentation is to be compiled
     */
    void generateDoc();

} // end class EditorWatcher
