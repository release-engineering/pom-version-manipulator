/*
 * Copyright (c) 2013 Red Hat, Inc.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class VManFormatter extends Formatter
{
    public String format (LogRecord record)
    {
        String result;

        result = String.format
        (
            "%s %s%s",
            record.getLevel(),
            formatMessage(record),
            System.getProperty("line.separator")
        );

        Throwable t = record.getThrown();
        return t == null ? result : result + getStackTrace (t);        
    }

    private String getStackTrace (Throwable t)
    {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter (sw));
        return sw.toString();

    }
}
