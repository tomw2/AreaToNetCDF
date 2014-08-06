@setlocal
@echo Make the AreaToNetCDF JAR file....
set classpath=\src\javacvs;.\;c:\repos\visad\visad.jar
javac *.java

pause ************  Now Ready to make the JAR file...
del AreaToNetCDF.jar
jar -cvfm AreaToNetCDF.jar aton.manifest *.class
