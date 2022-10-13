@ECHO OFF

REM 
REM  Bootstrap script
REM 

java -classpath %~p0\bootstrap.jar BayServerBoot %*
pause
exit