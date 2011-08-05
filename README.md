VMan (pom Version MANipulator) README
=====================================

VMan is a utility to help manage dependencies and plugins, using information
specified in a series of BOM (Bill-of-Materials POM) files, a toolchain POM
file, and optionally, a list of banned plugin coordinates.

It manages dependency versions by removing dependency versions that are available
in one or more of the BOMs used, and then injecting references to the BOMs
using import scope.

It manages plugin information via the toolchain POM. Under ideal circumstances,
this toolchain POM should be an ancestor of the project being processed. If
not, and the project doesn't declare another parent, a parent reference to the
toolchain will be injected. If the project declares a different parent POM
outside the scope of the current operation, the plugin information from the
toolchain will be injected directly into the topmost project(s) of the current
processing run.

Using the toolchain POM, VMan will remove plugin versions from the project, and
rely on the versions declared in the pluginManagement section of the toolchain.
If the toolchain specifies non-pluginManagement plugin entries, those entries
serve the function of providing plugin executions that all projects should 
run at build time. Therefore, any references required to accomplish this will
be added to the project.

If the user wishes to ban certain plugins, VMan supports specifying
a list of plugin groupId:artifactId coordinates to be removed from the projects 
processed.

Finally, it can append a version suffix to every POM file it processes, and
intelligently modify versions of parent references, too.

EASY USAGE
----------

**1. Specify Defaults**

Specify default configuration in **$HOME/.vman.properties**:
 ---

    boms = http://www.host.com/path/to/bom-1.0.pom,\
           http://www.host.com/path/to/other-bom-1.0.pom
    
    toolchain = http://www.host.com/path/to/toolchain-1.0.pom
    
    removed-plugins = org.apache.maven.plugins:maven-checkstyle-plugin,\
                      org.apache.maven.plugins:maven-cobertura-plugin
    
    version-suffix = -rebuild-1

Once you've specified this file ($HOME/.vman.properties), VMan should pickup your
default configuration and execute the basic management process without requiring 
any additional command-line options.

**2. Execute VMan**

Now, in your project directory, simply execute the command:

    ~/workspace/my-project$ java -jar vman.jar

This should modify the POMs in your project directory (traversing module references from `./pom.xml`)
to comply with the standard information you specified in your `.vman.properties` configuration file.

ADVANCED USAGE
--------------

You can override any of the options in your `.vman.properties` file on the command line. Additionally,
you can specify what workspace should be used for temporary and backup files, where reports should be
written, and how VMan will decide which POMs to process (from the `target` and `-p` options).

    java -jar vman.jar [OPTIONS] [<target-path>]
    
     target                       : POM file (or directory containing POM files) to modify.
     
     -C (--config) FILE           : Load default configuration for BOMs, toolchain, removedPluginsList, etc. from this file.
     
     -P (--preserve)              : Write changed POMs back to original input files
     
     -R (--report-dir) FILE       : Write reports here.
     
     -W (--workspace) FILE        : Backup original files here up before modifying.
     
     -b (--boms) FILE             : File containing a list of BOM URLs to use for standardizing dependencies
     
     -h (--help)                  : Print this message and quit
     
     -p VAL                       : POM path pattern (glob)
     
     -r (--rm-plugins) VAL        : List of plugins (format: <groupId:artifactId>[,<groupId:artifactId>]) to REMOVE if found
     
      -s (--version-suffix) VAL   : A suffix to append to each POM's version
      
     -t (--toolchain) VAL         : Toolchain POM URL, containing standard plugin versions in the build/pluginManagement section, 
                                    and plugin injections in the regular build/plugins section.

SIMPLE EXAMPLE
--------------

Imagine you have a series of POMs in a project directory structure, and want to 
align them to Red Hat's rebuild of the `jboss-parent` POM. You also want dependency
versions to be aligned to those contained in a set of BOMs thatwere generated for 
Red Hat EAP 6. You also want to use a version suffix to denote that you're rebuilding 
these projects. Finally, since Checkstyle is non-free, you want to disable the use of
the `maven-checkstyle-plugin`.

Start by specifying the following `$HOME/.vman.properties` file:

    toolchain = http://download.devel.redhat.com/brewroot/repos/jb-eap-6-rhel-6-build/latest/maven/org/jboss/jboss-parent/6-redhat-1/jboss-parent-6-redhat-1.pom
    boms = http://download.devel.redhat.com/brewroot/repos/jb-eap-6-rhel-6-build/latest/maven/com/redhat/jboss/eap-bom/6.0.0/eap-bom-6.0.0.pom,\
           http://download.devel.redhat.com/brewroot/repos/jb-eap-6-rhel-6-build/latest/maven/com/redhat/jboss/thirdparty-bom/6.0.0/thirdparty-bom-6.0.0.pom
    
    removed-plugins = org.apache.maven.plugins:maven-checkstyle-plugin
    version-suffix = -redhat-1

Now, `cd` to your project directory, and execute:

    java -jar vman.jar

Once you're finished, you should be able to use the `diff` command in your source-control 
system to view the patch generated by these modifications:

    git diff

BUILDING
--------

The following project source Git repositories and tags will have to be built, in the specified order, in order to build VMan.
These projects are built using Maven 3.0.3+ and JDK 1.6+. The build order is:

1. git://git.engineering.redhat.com/users/jcasey/maven/sisu.git (tag: *2.2.1-selectable-1*)
2. git://git.engineering.redhat.com/users/jcasey/maven/maven-sandbox.git (tag: *mae-0.9.3-redhat-1*, sub-path: *mae/*)
3. git://git.engineering.redhat.com/users/jcasey/maven/emb.git (tag: *mae-components-0.5.9-redhat-3*)
4. git://git.engineering.redhat.com/users/jcasey/tools/pom-version-manipulator.git (tag: *0.9.5-redhat-1*)

The last of these will render the VMan binary under target/pom-version-manipulator-*-bin.jar.

KNOWN BUGS
----------

* Currently, VMan cannot preserve CDATA sections in POM files.
* Additionally, some users have reported injected newlines being added using the Windows (\r\n) format, instead of
the operating-system-dependent line ending.

