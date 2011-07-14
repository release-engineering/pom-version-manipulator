/*
 *  Copyright (C) 2011 John Casey.
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *  
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.redhat.rcm.version.mgr.load;

import com.redhat.rcm.version.VManException;
import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.mgr.model.Project;

import java.io.File;
import java.util.List;

public interface ModelLoader
{
    
    List<Project> buildModels( VersionManagerSession session, File...poms )
        throws VManException;

}
