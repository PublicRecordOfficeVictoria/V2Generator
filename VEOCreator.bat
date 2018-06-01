@echo off
set versclasspath="G:PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS"
java -classpath %versclasspath% VEOToolkitV2.VEOGenerator.VEOCreator -t %versclasspath%\VEOToolkitV2\VEOGenerator\encDirectory %*