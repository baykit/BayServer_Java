#!/bin/bash
version=`cat VERSION`

version_file=core/src/baykit/bayserver/Version.java
temp_version_file=/tmp/Version.java

sed "s/VERSION=.*/VERSION=\"${version}\";/" ${version_file} > ${temp_version_file}
mv ${temp_version_file} ${version_file}

class_dir=/tmp/out

stage_name=BayServer_Java-${version}
stage=/tmp/$stage_name
stage_bin=$stage/bin
stage_lib=$stage/lib
stage_cert=$stage/cert
stage_plan=$stage/plan
stage_tmp=$stage/tmp
stage_log=$stage/log
stage_www=$stage/www

rm -r $stage
mkdir -p $stage
mkdir -p $stage_lib
mkdir -p $stage_bin
mkdir -p $stage_cert
mkdir -p $stage_plan
mkdir -p $stage_tmp
mkdir -p $stage_log
mkdir -p $stage_www

compile() {
  src_dir=$1
  jar_name=$2
  pushd .
  cd $src_dir
  rm -r $class_dir
  mkdir $class_dir
  javac --release 8 -d $class_dir `find . -name "*.java"`


  for f in `find . -name "*.properties"`; do
     dir=`dirname $f`
     cp $f $class_dir/$dir
  done
  cd $class_dir
  pwd
  jar cf $jar_name *
  popd
}

cp -r test/simple/bin $stage
cp -r test/simple/lib/dtd $stage_lib
cp -r test/simple/lib/conf $stage_lib
cp -r lib/servlet-api.jar $stage_lib
cp -r test/simple/www/root $stage_www
cp -r test/simple/www/servlet-demo $stage_www
cp -r test/simple/www/cgi-demo $stage_www
cp -r legal $stage/legal
cp test/simple/cert/ore* $stage_cert
cp test/simple/plan/groups.plan $stage_plan
cp test/simple/plan/bayserver.plan $stage_plan
cp LICENSE.* README.md NEWS.md $stage

quiche_jar=${PWD}/../quiche4j/quiche4j-jni/target/quiche4j-jni-0.2.5-linux-x86_64.jar:${PWD}/../quiche4j/quiche4j-core/target/quiche4j-core-0.2.5.jar
 
export CLASSPATH=$stage_lib/bayserver.jar:${quiche_jar}

for f in lib/*; do
  f=${PWD}/$f
  export CLASSPATH=$f:$CLASSPATH
done
echo $CLASSPATH

pushd . 
cd $stage_www/servlet-demo/WEB-INF/classes
javac --release 8 `find . -name "*.java"`
popd 

compile core/src $stage_lib/bayserver.jar
compile docker/cgi/src $stage_lib/docker-cgi.jar
compile docker/http/src $stage_lib/docker-http.jar
compile docker/ajp/src $stage_lib/docker-ajp.jar
compile docker/fcgi/src $stage_lib/docker-fcgi.jar
compile docker/servlet/src $stage_lib/docker-servlet.jar
compile docker/wordpress/src $stage_lib/docker-wordpress.jar
compile docker/http3/src $stage_lib/docker-http3.jar
compile bootstrap/src $stage_bin/bootstrap.jar

cd /tmp
jar cf BayServer_Java-${version}.jar ${stage_name}

