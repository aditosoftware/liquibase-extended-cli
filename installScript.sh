#!/bin/sh

####################################################################################################################################################
####################################################################################################################################################
####                                                                                                                                            ####
####                This script runs the maven clean install and then moves the jar file and the folder with                                    ####
####                the dependencies to the location where the lib is located inside the extension dev folder.                                  ####
####                                                                                                                                            ####
####################################################################################################################################################
####################################################################################################################################################

# name of the jar file to be moved.
jarFile="liquibase-extended-cli.jar"
# path to the jar file above, from the location of this script.
jarPath="target/"
# the path to the folder in which the jar should be moved. Example of vscode-liquibase extension:
jarTargetPath="../vscode-liquibase/lib/"

if test -z "$jarFile" || test -z "$jarPath" || test -z "$jarTargetPath"
then
  echo "Variables for the file paths not set up, aborting the job. Please fill in the variables in the script"

else

  jarFilePath=$jarPath$jarFile
  jarFileTargetPath=$jarTargetPath$jarFile

  JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF8 JAVA_HOME="C:\Program Files\Java\jdk-13" mvn clean install -T 1C

  cp $jarFilePath $jarFileTargetPath

  echo "done copying to " $jarFileTargetPath

fi
