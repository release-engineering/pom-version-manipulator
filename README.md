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

**Note: The bom order is important - those appearing first take precedence.**

**2. Execute VMan**

Now, in your project directory, simply execute the command:

    ~/workspace/my-project$ java -jar vman.jar

This should modify the POMs in your project directory (traversing module references from ./pom.xml) to comply with the standard information you specified in your .vman.properties configuration file. Note that to traverse a directory structure containing multiple maven projects and no single parent it is possible to pass a directory e.g.

    ~/workspace/my-project$ java -jar vman.jar <my-maven-directory>

where _my-maven-directory_ might contain:

    module-core
    module-tools
    module-release

ADVANCED USAGE
--------------

You can override any of the options in your .vman.properties file on the command line. Additionally, you can specify what workspace should be used for temporary and backup files, where reports should be written, and how VMan will decide which POMs to process (from the target and -p options).

    java -jar vman.jar [OPTIONS] [<target-path>]

     target                                 : POM file (or directory tree containing POM files) to modify.
                                              Default: pom.xml

     -B (--bootstrap) FILE                  : Bootstrap properties to read for location of VMan configuration.


     -C (--config) FILE/URL                 : Load default configuration for BOMs, toolchain, removedPluginsList,
                                              etc. from this file/url.
                                              Default: $HOME/.vman.properties

     -W (--workspace) FILE                  : Backup original files here up before modifying.
                                              Default: vman-workspace

     -R (--report-dir) FILE                 : Write reports here.
                                              Default: <workspace>/reports

     -p VAL                                 : POM path pattern (glob)
                                              Default: **/*.pom,**/pom.xml

     -L (--local-repo,
         --local-repository) FILE           : Local repository directory.
                                              Default: <workspace>/local-repository
                                              Property file equivalent: local-repository

     -P (--preserve)                        : Write changed POMs back to original input files
                                              Default: false

     --strict                               : Change ONLY the dependencies, plugins, and parents that are listed in BOMs and
                                              toolchain POM
                                              Default: false
                                              Property file equivalent: strict

     -Z (--no-system-exit)                  : Don't call System.exit(..) with the return value (for embedding/testing).
                                              Default: false

     -t (--toolchain) VAL                   : Toolchain POM URL, containing standard plugin versions in the build /
                                              pluginManagement section, and plugin injections in the regular build /
                                              plugins section.

     -b (--boms) FILE                       : File containing a list of BOM URLs to use for standardizing dependencies
                                              Property file equivalent: boms

     -r (--rm-plugins,
         --removed-plugins) VAL             : List of plugins (format: <groupId:artifactId>[,<groupId:artifactId>]) to
                                              REMOVE if found
                                              Property file equivalent: removed-plugins

     --removed-tests                        : List of modules (format: <groupId:artifactId>[,<groupId:artifactId>]) for
                                              which tests should be disabled and test scoped dependencies removed.
                                              Property file equivalent: removed-tests

     --extensions-whitelist                 : List of extensions (format: <groupId:artifactId>[,<groupId:artifactId>]) to
                                              preserve. Only used with the ExtensionsRemovalModder.
                                              Property file equivalent: extensions-whitelist

     -O (--capture-output,
         --capture-pom) FILE                : Write captured (missing) definitions to this POM location.

     -s (--version-suffix) VAL              : A suffix to append to each POM's version

     --version-modifier VAL                 : Change each POM's version using pattern:replacement format.
                                              Property file equivalent: version-modifier

     -e VAL                                 : POM file path exclude pattern (glob)

     -E VAL                                 : POM module exclude list (format: <groupId:artifactId>[,<groupId:artifactId>])

     -M (--enable-modifications) VAL        : List of modifications to enable for this execution (see --help-modifications
                                              for more information).

     -m (--remote-repositories) VAL         : Maven remote repository from which load missing parent POMs. (format:
                                              <[id|]repository>[,<[id|]repository>].
                                              Property file equivalent: remote-repository.

     -S (--settings) FILE                   : Maven settings.xml file.
                                              Property file equivalent: settings

     --console                              : Log information to console instead of <workspace>/vman.log.

     -T (--test-config)                     : Test-load the configuration given, and print diagnostic information

     -H (--help-modifications)              : Print the list of available modifications and quit

     -h (--help)                            : Print this message and quit

     -v (--version)                         : Show version information and quit.


### MODIFICATIONS

These are the plugins that will alter the pom files. There is a standard set and extra optional ones. The standard ones are:

    bom-realignment          Forcibly realign dependencies to use those declared
                             in the supplied BOM file(s). Inject supplied BOM(s)
                             into the project root POM.

    repo-removal             Remove <repositories/> and <pluginRepostories/>
                             elements from the POM (this is a Maven best practice).

    toolchain-realignment    Forcibly realign POM build section to use plugins
                             declared in the supplied toolchain POM (if present).

    version-suffix           Modify the POM version to include the supplied
                             version suffix.

The optional ones are:

    minimize                 Remove all original site-production and
                             infrastructural elements of the POM (mainly useful
                             for re-release of a project by a third party). Note - this
                             also includes the repo, extensions and reporting removal modders.

    property                 Change property mappings to use those declared in the
                             supplied BOM file(s).

    extensions-removal       Remove extensions from the POM.

    version-modifier         Change the POM's versions using the format 'pattern:replacement'.

    reporting-removal        Remove reporting elements from the POM.

    testremoval              For each module, disable test compilation and execution and move
                             any test scoped dependencies into a separate profile. The modules
                             may be specified explicitly as groupId:artifactId or by using wildcards
                             using the Java regex syntax.

Details on the Java regex pattern syntax may be found [here](http://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html).

In order to add optional modifiers to the standard list use either

*  On the command line `--modifications=+<modifier-id>,+<modifier-id>` (prefixed with '+').
*  Or in the properties file `modifications = +property,+property`

Note that if the modifier is not prefixed by a '+' this will override the standard list.

#### Property Modification
The property modification allows an injected BOM to override version properties using
a mapping syntax of

            <mapping>
                version.productX=@version.org.productX@
                version.productY=1.0.11.Final
            </mapping>

In this example the two forms are:

*  Replace the value of version.productX property with the value of org.productX property
   from the injected BOM (or its parent).
*  Replace the value of version.productY with the literal 1.0.11.Final.

### VersionMapper
In order to work around the following issues

* When a dependencyManagement section in a pom has scopes and v-man removes the local dependency management to replace it with an external dependency management the scopes were lost.
* Extensions do not use dependencyManagement and therefore versions in whitelisted extensions need special handling.

mapping properties may be added to a toolchain pom. These properties will be used to either inject an explicit version for a given group/artifact (`org.apache.maven.archetype-archetype-packaging` below) or used to map to another property (`org.jboss.weld.se-weld-se-core` below).

    <properties>
       <versionmapper.org.apache.maven.archetype-archetype-packaging>
            2.0
        </versionmapper.org.apache.maven.archetype-archetype-packaging>
        <versionmapper.org.jboss.weld.se-weld-se-core>
            version.org.jboss.weld.weld
        </versionmapper.org.jboss.weld.se-weld-se-core>



SIMPLE EXAMPLE
--------------

Imagine you have a series of POMs in a project directory structure, and want to align them to your rebuild of your parent POM. You also want dependency versions to be aligned to those contained in a set of BOMs that were generated for your software product 'X'. You also want to use a version suffix to denote that you're rebuilding these projects. Finally, you want to disable the use of the maven-checkstyle-plugin.

Start by specifying the following $HOME/.vman.properties file:

    toolchain = http://www.host.com/path/to/toolchain.pom

    boms = http://www.host.com/path/to/bom-1.0.pom, \
           http://www.host.com/path/to/other-bom-1.0.pom, \
           /var/devel/path/to/local-devel-bom-1.0.pom

    remote-repository = http://www.host.com/path/to/maven/repo

    removed-plugins = org.apache.maven.plugins:maven-checkstyle-plugin

    version-suffix = -rebuild-1

    capture-pom = vman-workspace/capture.pom.xml

    modifications = +minimize

Now, cd to your project directory, and execute:

    java -jar vman.jar

Once you're finished, you should be able to use the diff command in your source-control system to view the patch generated by these modifications:

    git diff

If any dependency or plugin versions have NOT been captured in the BOMs or toolchain, you'll find them listed in the vman-workspace/capture.pom.xml file (assuming this has been configured using either the capture-pom in the properties file or using the command line flags. The dependencies and plugins' versions will still be removed from the project POM (unless you are using `--strict`). However, the listings in the capture.pom.xml now make it easy to add the missing information to the appropriate BOM or toolchain POM.

Additionally, the plugin listings in the capture-POM are directly consumable by the meadin-roller tool, which creates self-contained, monolithic versions of the plugins used in MEAD builds (to avoid dependency overlap with the actual projects being built).

KNOWN ISSUES
------------

* Currently, VMan cannot preserve CDATA sections in POM files.
* Additionally, some users have reported injected newlines being added using the Windows (\r\n) format, instead of the operating-system-dependent line ending.
* Dependency management blocks within plugins are not currently adjusted. The workaround is to use properties for the versions and use the property mapping plugin to adjust the versions.
