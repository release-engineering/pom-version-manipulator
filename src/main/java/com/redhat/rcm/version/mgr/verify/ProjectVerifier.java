package com.redhat.rcm.version.mgr.verify;

import com.redhat.rcm.version.mgr.VersionManagerSession;
import com.redhat.rcm.version.model.Project;

public interface ProjectVerifier
{

    void verify( Project project, VersionManagerSession session );

}
