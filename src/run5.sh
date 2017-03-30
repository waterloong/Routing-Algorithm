#!/bin/bash
#screen -dmS s1 echo "aa" && bash -c 'echo waiting 5 senconds...; sleep 5; exec bash'
#java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=20001,suspend=n Router 1 ubuntu1404-002.student.cs.uwaterloo.ca 19999 19999 &
#read dummy
machine=ubuntu1404-002.student.cs.uwaterloo.ca
port=19999
if [ "$#" -gt 0 ]; then
    machine=$1
fi;
if [ "$#" -gt 1 ]; then
    port=$2
fi;

java Router 1 $machine $port 0 &
java Router 2 $machine $port 0 &
java Router 3 $machine $port 0 &
java Router 4 $machine $port 0 &
java Router 5 $machine $port 0 &
