#!/bin/sh
bindir=`dirname $0`
args=$*
daemon=
for arg in $args; do
  if [ "$arg" = "-daemon" ]; then
    daemon=1
  fi
done

if [ "$daemon" = 1 ]; then
   java $JAVA_OPTS -classpath ${bindir}/bootstrap.jar BayServerBoot $* < /dev/null  > /dev/null 2>&1 &
else
   java $JAVA_OPTS -classpath ${bindir}/bootstrap.jar BayServerBoot $* 
fi
