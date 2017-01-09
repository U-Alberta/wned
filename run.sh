#!/bin/bash

CP=target/wned-1.0-jar-with-dependencies.jar:lib/*:.

java -Xmx35G -XX:+UseG1GC -cp $CP ca.ualberta.entitylinking.SemanticSignatureEL el.config
