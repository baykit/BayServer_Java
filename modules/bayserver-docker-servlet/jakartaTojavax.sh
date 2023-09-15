BASE_PKG_DIR=src/baykit/bayserver/docker/servlet
JAKARTA_DIR=jakarta
JAVAX_DIR=javax

cd $BASE_PKG_DIR
rm ${JAVAX_DIR}/*
cp ${JAKARTA_DIR}/*.java  ${JAVAX_DIR}
cd ${JAVAX_DIR}

for f in *.java
do
	target=`echo $f | sed 's/Jakarta/Javax/'`
	sed -e 's/jakarta/javax/g'\
		-e 's/Jakarta/Javax/g' \
		-e 's/jakarta.servlet.ServletException/java/' $f > $target
	rm $f
	if [ $target = JavaxServletContext.java ]; then
		lineno=`sed -n '/addJspFile/=' $target`
		lineno=`echo $lineno - 1  | bc`
		if [ "$lineno" != "" ]; then
			sed "${lineno}d" $target > ${target}.bak
			mv ${target}.bak $target
		fi
	fi	
done
