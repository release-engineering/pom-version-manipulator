package com.redhat.rcm.version.stats;

public final class VersionInfo
{
    
    private VersionInfo(){}
    
    public static final String APP_NAME = "@project.name@";
    public static final String APP_DESCRIPTION = "@project.description@";
    public static final String APP_VERSION = "@project.version@";
    public static final String APP_BUILDER = "@user.name@";
    public static final String APP_COMMIT_ID = "@buildNumber@";
    public static final String APP_TIMESTAMP = "@timestamp@";
    
}
