#!/bin/sh
bindir=`dirname $0`
args=$*
daemon=
for arg in $args; do
  if [ "$arg" = "-daemon" ]; then
    daemon=1
  fi
done

libdir=${bindir}/../lib
jar=`ls ${libdir} | grep "bayserver-[0-9].*.jar"`
 

if [ "$daemon" = 1 ]; then
   java $JAVA_OPTS -classpath ${jar} yokohama.baykit.bayserver.boot.Boot $* < /dev/null  > /dev/null 2>&1 &
else
   java $JAVA_OPTS -classpath ${libdir}/${jar} yokohama.baykit.bayserver.boot.Boot $* 
fi
