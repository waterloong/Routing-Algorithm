#screen -dmS s1 echo "aa" && bash -c 'echo waiting 5 senconds...; sleep 5; exec bash'
java -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=20001,suspend=n Router 1 ubuntu1404-002.student.cs.uwaterloo.ca 19999 19999 &
read dummy
java Router 2 ubuntu1404-002.student.cs.uwaterloo.ca 19999 19998 &
java Router 3 ubuntu1404-002.student.cs.uwaterloo.ca 19999 19997 &
java Router 4 ubuntu1404-002.student.cs.uwaterloo.ca 19999 19996 &
java Router 5 ubuntu1404-002.student.cs.uwaterloo.ca 19999 19995 &


