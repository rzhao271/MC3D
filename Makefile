all: compile package

compile:
	javac *.java -Xlint:deprecation -Xlint:unchecked -d out

package:
	cd out; jar -cef MC3DApp mc3d.jar *.class
	mv out/mc3d.jar .

clean:
	rm -rf out *.jar
