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
package bluej.groupwork;

import java.util.ArrayList;
import java.util.List;

import org.netbeans.lib.cvsclient.command.FileInfoContainer;
import org.netbeans.lib.cvsclient.command.log.LogInformation;
import org.netbeans.lib.cvsclient.event.FileInfoEvent;

import bluej.groupwork.cvsnb.BasicServerResponse;

/**
 * A reponse handler for the "cvs log" command. Keeps a list of responses
 * generated by the server.
 * 
 * @author Davin McCall
 * @version $Id: LogServerResponse.java 6215 2009-03-30 13:28:25Z polle $
 */
public class LogServerResponse extends BasicServerResponse
{
    private List infoList;
    
    public LogServerResponse()
    {
        infoList = new ArrayList();
    }
    
    public void fileInfoGenerated(FileInfoEvent arg0)
    {
        FileInfoContainer fic = arg0.getInfoContainer();
        if (fic instanceof LogInformation) {
            infoList.add(fic);
        }
    }
    
    public List getInfoList()
    {
        return infoList;
    }
}
