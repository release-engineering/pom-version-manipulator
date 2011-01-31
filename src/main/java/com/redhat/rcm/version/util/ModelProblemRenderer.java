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

package com.redhat.rcm.version.util;

import org.apache.maven.model.building.ModelProblem;
import org.apache.maven.model.building.ModelProblem.Severity;

import java.util.List;

public class ModelProblemRenderer
{

    private final List<ModelProblem> problems;

    private final Severity minSeverity;

    public ModelProblemRenderer( final List<ModelProblem> problems, final ModelProblem.Severity minSeverity )
    {
        this.problems = problems;
        this.minSeverity = minSeverity;
    }

    public boolean containsProblemAboveThreshold()
    {
        for ( final ModelProblem problem : problems )
        {
            if ( problem.getSeverity().ordinal() > minSeverity.ordinal() )
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        for ( final ModelProblem problem : problems )
        {
            if ( problem.getSeverity().ordinal() > minSeverity.ordinal() )
            {
                continue;
            }

            if ( sb.length() > 0 )
            {
                sb.append( "\n" );
            }

            sb.append( problem.getSeverity() )
              .append( ": " )
              .append( problem.getMessage() )
              .append( "\n\tSource: " )
              .append( problem.getSource() )
              .append( "@" )
              .append( problem.getLineNumber() )
              .append( ':' )
              .append( problem.getColumnNumber() );
        }

        return sb.toString();
    }

}
