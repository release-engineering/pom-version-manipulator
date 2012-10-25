package com.redhat.rcm.version.maven;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.mae.MAEException;
import org.apache.maven.mae.app.AbstractMAEApplication;
import org.apache.maven.mae.boot.embed.MAEEmbedderBuilder;
import org.apache.maven.mae.project.ModelLoader;
import org.apache.maven.mae.project.ProjectLoader;
import org.apache.maven.mae.project.session.SessionInitializer;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.settings.building.SettingsBuilder;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Singleton
public class MavenComponentProvider
{

    private static boolean useClasspathScanning;

    private MAEApp instance;

    @PostConstruct
    public synchronized void initialize()
        throws MAEException
    {
        if ( instance == null )
        {
            instance = new MAEApp();
            instance.load();
        }
    }

    public static void setClasspathScanning( final boolean scanning )
    {
        useClasspathScanning = scanning;
    }

    @Produces
    @Default
    public ModelLoader getModelLoader()
    {
        return instance.modelLoader;
    }

    @Produces
    @Default
    public ModelBuilder getModelBuilder()
    {
        return instance.modelBuilder;
    }

    @Produces
    @Default
    public ProjectLoader getProjectLoader()
    {
        return instance.projectLoader;
    }

    @Produces
    @Default
    public SettingsBuilder getSettingsBuilder()
    {
        return instance.settingsBuilder;
    }

    @Produces
    @Default
    public MavenExecutionRequestPopulator getExecutionRequestPopulator()
    {
        return instance.requestPopulator;
    }

    @Produces
    @Default
    public SessionInitializer getSessionInitializer()
    {
        return instance.sessionInitializer;
    }

    @Component( role = MAEApp.class )
    public class MAEApp
        extends AbstractMAEApplication
    {
        @Requirement
        private ModelBuilder modelBuilder;

        @Requirement
        private ModelLoader modelLoader;

        @Requirement
        private ProjectLoader projectLoader;

        @Requirement
        private SettingsBuilder settingsBuilder;

        @Requirement
        private MavenExecutionRequestPopulator requestPopulator;

        @Requirement
        private SessionInitializer sessionInitializer;

        @Override
        public String getId()
        {
            return "vman-maven-provider";
        }

        @Override
        public String getName()
        {
            return "VMan Maven Component Provider";
        }

        @Override
        protected void configureBuilder( final MAEEmbedderBuilder builder )
            throws MAEException
        {
            super.configureBuilder( builder );
            if ( useClasspathScanning )
            {
                builder.withClassScanningEnabled( true );
            }
        }

    }
}
