package com.redhat.rcm.version.report;

import java.util.Map;

public abstract class AbstractReport
    implements Report
{

    @Override
    public Map<String, String> getPropertyDescriptions()
    {
        return null;
    }

}
