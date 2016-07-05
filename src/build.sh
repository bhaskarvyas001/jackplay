#!/usr/bin/env bash
javac -cp ../lib/javassist-3.20.0-GA.jar jackplay/*.java jackplay/web/*.java jackplay/play/*.java
jar -cmf manifest.txt ../lib/jackplay.jar jackplay/*.class jackplay/web/*.class jackplay/play/*.class jackplay/web/resources/*