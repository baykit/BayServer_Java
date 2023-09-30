#!/bin/bash
version=`cat VERSION`
go=$1

echo "Version=$version"
echo "go=$go"
echo "Enter key"
read

mvn_cmd=$HOME/git/maven/bin/mvn

deploy() {
  module=$1
  pushd .
  cd ${module}
  echo "Deploy ${module}"
  if [ "$go" == "1" ] ; then
    ${mvn_cmd} deploy
  fi
  popd 
}

cd modules

deploy bayserver-core


for dir in `ls -d */`; do
  if [ "$dir" == "bayserver-core/" -o "$dir" == "bayserver/" ]; then
    continue
  fi
  deploy ${dir}
done

deploy bayserver
