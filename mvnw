#!/bin/sh
# Maven Wrapper Start
# ----------------------------------------------------------------------------

# Find project basedir
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
MAVEN_PROJECTBASEDIR="`pwd -P`"
cd "$SAVED" >/dev/null

export MAVEN_PROJECTBASEDIR

# Use Java from JAVA_HOME if set, otherwise from PATH
if [ -n "$JAVA_HOME" ] ; then
    JAVA_EXEC="$JAVA_HOME/bin/java"
else
    JAVA_EXEC="java"
fi

MAVEN_OPTS="${MAVEN_OPTS:-"-Xmx1024m"}"
export MAVEN_OPTS

exec "$JAVA_EXEC" \
    $MAVEN_OPTS \
    -Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR" \
    -classpath "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar" \
    org.apache.maven.wrapper.MavenWrapperMain \
    "$@"
# Maven Wrapper End
