#!/bin/bash
version=`cat VERSION`
export VERSION=$version

mvn_cmd=mvn

version_file=modules/bayserver-core/src/main/java/yokohama/baykit/bayserver/Version.java
temp_version_file=/tmp/Version.java

sed "s/VERSION=.*/VERSION=\"${version}\";/" ${version_file} > ${temp_version_file}
mv ${temp_version_file} ${version_file}

class_dir=/tmp/out

stage_name=BayServer_Java-${version}
stage=/tmp/$stage_name
stage_bin=$stage/bin
stage_lib=$stage/lib
stage_tmp=$stage/tmp
stage_log=$stage/log

rm -r $stage
mkdir -p $stage
mkdir -p $stage_lib
mkdir -p $stage_bin
mkdir -p $stage_tmp
mkdir -p $stage_log

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
cp lib/servlet-api.jar $stage_lib
cp LICENSE.* README.md NEWS.md $stage

# PHPVerse path (= source checkout consumed by PhpVerseDocker's composer.json
# path repository). Override via env to point at a custom checkout; the
# default matches the BayServer-for-PHP convention of cloning PHPVerse next
# to the user's home.
PHPVERSE_PATH=${PHPVERSE_PATH:-$HOME/phpverse}
if [ ! -d "$PHPVERSE_PATH" ]; then
  echo "ERROR: PHPVERSE_PATH not found: $PHPVERSE_PATH" >&2
  echo "  Set PHPVERSE_PATH to a PHPVerse git checkout before running build.sh" >&2
  exit 1
fi

# Render PhpVerseDocker's composer.json from its template + run composer
# install BEFORE init.jar is built, so the materialized vendor/ tree gets
# bundled into init.jar and ends up at <bservHome>/phpverse/ when the
# operator runs `bayserver.sh -init`.
echo "[composer] Rendering PhpVerseDocker composer.json + installing PHPVerse..."
PHPVERSE_PATH="$PHPVERSE_PATH" envsubst \
    < modules/bayserver-docker-phpverse/composer.json.template \
    > modules/bayserver-docker-phpverse/composer.json
(cd modules/bayserver-docker-phpverse && composer install --no-dev --no-interaction --optimize-autoloader)

# Stage composer-installed PHPVerse into the init payload. The existing
# `jar cf init.jar *` step below picks up phpverse/ automatically.
rm -rf modules/bayserver/init/phpverse
mkdir -p modules/bayserver/init/phpverse
cp -r modules/bayserver-docker-phpverse/vendor modules/bayserver/init/phpverse/vendor
cp modules/bayserver-docker-phpverse/composer.json modules/bayserver/init/phpverse/composer.json
[ -f modules/bayserver-docker-phpverse/composer.lock ] \
    && cp modules/bayserver-docker-phpverse/composer.lock modules/bayserver/init/phpverse/composer.lock

pushd .
sapi=`pwd`/lib/servlet-api.jar
cp LICENSE.* README.md modules/bayserver/init
cd modules/bayserver/init/www/servlet-demo/WEB-INF/classes
javac -classpath ${sapi} --release 8 `find . -name "*.java"`
cd ../../../..
jar cf init.jar *
mv init.jar ../src/main/resources
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

cd ${stage}
bin/bayserver.sh -init
cd /tmp
jar cf BayServer_Java-${version}.jar ${stage_name}

