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
package bluej.groupwork.actions;

import java.util.List;

import bluej.Config;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;


/**
 * Action to show dialog for updating out-of-date files.
 * 
 * @author davmac
 * @version $Id: UpdateDialogAction.java 6215 2009-03-30 13:28:25Z polle $
 */
public class UpdateDialogAction extends TeamAction
{
    private Project project;
    private boolean includeLayout;
    
    /** A list of packages whose bluej.pkg file has been removed */
    private List removedPackages;
    
    public UpdateDialogAction()
    {
        super("team.update", true);
        putValue(SHORT_DESCRIPTION, Config.getString("tooltip.update"));
    }

    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(PkgMgrFrame pmf)
    {
        project = pmf.getProject();
        if (project != null) {
            project.saveAllEditors();
            project.getUpdateDialog().setVisible(true);
        }
    }
}
