@echo off
rem set code="C:\Users\Andrew\Documents\Work\VERS-2015\VPA"
rem set bin="C:\Program Files\Java\jdk1.8.0_162\bin"
set code="J:\PROV\TECHNOLOGY MANAGEMENT\Application Development\VERS\VERS-1999\V2Generator"
set bin="C:\Program Files\Java\jdk1.8.0_144\bin"
set versclasspath=%code%/dist/*
%bin%\java -classpath %versclasspath% VEOGenerator.VEOGenerator %*
