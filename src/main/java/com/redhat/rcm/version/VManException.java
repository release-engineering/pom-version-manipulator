/*
 * Copyright (c) 2011 Red Hat, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see 
 * <http://www.gnu.org/licenses>.
 */

package com.redhat.rcm.version;

import org.apache.maven.mae.project.ProjectToolsException;

public class VManException
    extends ProjectToolsException
{
    private static final long serialVersionUID = 1L;

    public VManException( final String message, final Throwable cause, final Object... params )
    {
        super( message, cause, params );
    }

    public VManException( final String message, final Object... params )
    {
        super( message, params );
    }

}
