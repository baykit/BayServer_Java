#!/bin/sh
echo Content-Type: text/html
echo
echo "<HTML>"
echo "<BODY>"
echo PATH_INFO=$PATH_INFO "<BR>"
echo PATH_TRANSLATED=$PATH_TRANSLATED "<BR>" 
echo QUERY_STRING=$QUERY_STRING "<BR>"
echo SERVER_NAME=$SERVER_NAME "<BR>"
echo SCRIPT_NAME=$SCRIPT_NAME "<BR>"
echo "</BODY>"
echo "</HTML>"
