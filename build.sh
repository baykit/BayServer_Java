#!/bin/bash
version=`cat VERSION`
export VERSION=$version

mvn_cmd=$HOME/git/maven/bin/mvn

version_file=modules/bayserver-core/src/main/java/yokohama/baykit/bayserver/Version.java
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
  jar_dir=$2
  pushd .
  cd $src_dir
  $mvn_cmd compile
  cp target/*.jar $jar_dir
  popd
}

cp -r test/simple/bin $stage
cp -r test/simple/lib/dtd $stage_lib
cp -r test/simple/lib/conf $stage_lib
cp -r lib/servlet-api.jar $stage_lib
cp -r test/simple/www/root $stage_www
cp -r test/simple/www/servlet-demo $stage_www
cp -r test/simple/www/cgi-demo $stage_www
cp test/simple/cert/ore* $stage_cert
cp test/simple/plan/groups.plan $stage_plan
cp test/simple/plan/bayserver.plan $stage_plan
cp LICENSE.* README.md NEWS.md $stage

pushd .
libdir=`pwd`/lib
cd $stage_www/servlet-demo/WEB-INF/classes
javac -classpath $libdir/servlet-api.jar --release 8 `find . -name "*.java"`
popd


for dir in `ls -d modules/*/`; do
  pushd .
  cd ${dir}
  sed "s/\\\${env.VERSION}/${version}/" pom.xml.template > pom.xml
  popd
done
sed "s/\\\${env.VERSION}/${version}/" pom.xml.template > pom.xml

rm -r ~/.m2/repository/yokohama/baykit/
$mvn_cmd clean
$mvn_cmd package
cp `find modules -name "*.jar"` $stage_lib

cd /tmp
jar cf BayServer_Java-${version}.jar ${stage_name}

