VMan (pom Version MANipulator) README
=====================================

This utility will read a series of Bill-of-Materials POM files (BOMs), and 
modify all POMs in the target path to make their dependencies conform to 
those BOMs. Optionally, it can also introduce references to the BOMs used, 
removing the dependency versions from the target POMs at the same time.

USAGE
------

java -jar pom-version-manipulator.jar [OPTIONS] <target-path> <BOM-path>

target-path            : POM file (or directory containing POM files) to modify.
BOM-path               : Bill-of-Materials POM file supplying versions.

-P (--preserve)        : Write changed POMs back to original input files
-b (--backups) FILE    : Backup original files here up before modifying.
-n (--normalize)       : Normalize the BOM usage (introduce the BOM if not 
                         already there, and defer all dependency versions to 
                         that).
-p VAL                 : POM path pattern (glob)
-r (--report-dir) FILE : Write reports here.
-h (--help)            : Print this message and quit


EXAMPLE
-------

If you have a series of POMs in a project directory structure, and want to 
align them to the JBoss AS parent POM (which can also act as a BOM), you would
first download the JBoss AS parent POM, then use a command such as the 
following:

    java -jar pom-version-manipulator-0.1-SNAPSHOT-bin.jar -n \
                -b ./backups \
                -r ./reports \
                ./my-project-directory \
                ./jboss-as-parent-7.0.0.Beta3.pom
            

