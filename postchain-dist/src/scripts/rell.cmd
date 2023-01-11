@echo off
IF NOT DEFINED RELL_JAVA SET RELL_JAVA=java
%RELL_JAVA% -jar "%~dp0lib\rellr.jar" %*
