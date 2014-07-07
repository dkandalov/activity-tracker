What is this?
=============

This is a micro-plugin for IntelliJ that collects stats about which file is currently open
and which class/method cursor in on.
<br/>
(It runs inside [live-plugin](https://github.com/dkandalov/live-plugin).
Implemented for Java but should be easy to extend for other languages supported by IntelliJ.)


Why?
====
 - To get an idea about how much time you spend on different parts of code.
 You might already "know" it but having actual data should not hurt.
 - Measure how often source code is read vs how often it's modified.
 (This is not implemented. The idea is to get data from commits and subtract from real-time measurement.)