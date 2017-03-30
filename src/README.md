Assignment 3
===========
Compilation
---------
Please run `make` to compile.

The version of compiler I use is `javac` 1.8.0_111. This compiler is available on `ubuntu1404-002.student.cs.uwaterloo.ca`. (A Java 7 compiler will not work)
The version of GNU Make 3.81.

Testing scripts
----
Two scripts are provided: `router` and `run5.sh`

router works same as assignment specification: `router <router_id> <nse_host> <nse_port> <router_port>`  
(Put port number as 0 to use an ephemeral port)

`run5.sh` starts exactly 5 routers on background, with IDs 1-5 respectively. 
The first argument is nse host address. The 2nd argument is the port of nse host. IDs and router ports are automatically assigned and cannot be specified.

Test environment
------
The program has been tested on `ubuntu1404-002.student.cs.uwaterloo.ca` to host nse, and 004, 008 to host routers.

A router process will terminate 10 seconds after sending the first "hello" packet.  