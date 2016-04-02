export JAVA_HOME=/usr/java/default
export PATH=$JAVA_HOME/bin:$PATH
export CATALINA_HOME=/usr/share/tomcat7
JAVA_OPTS="$JAVA_OPTS -server"
JAVA_OPTS="$JAVA_OPTS -Xms10240m -Xmx10240m -Xmn2560m"
JAVA_OPTS="$JAVA_OPTS -XX:NewSize=512m -XX:MaxNewSize=512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC -XX:SurvivorRatio=15 -verbose:gc -XX:HeapDumpPath=/usr/share/tomcat7/logs/fedoar_heap.hprof -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -Xloggc:/var/log/tomcat6/gc.log"
JAVA_OPTS="$JAVA_OPTS -verbose:gc -XX:HeapDumpPath=/usr/share/tomcat7/logs/fedoar_heap.hprof -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime -Xloggc:/var/log/tomcat7/gc.log"
# Timezone and JVM file encoding
JAVA_OPTS="$JAVA_OPTS -Duser.timezone=UTC -Dfile.encoding=UTF8"

export CATALINA_OPTS="-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.authenticate=true \
-Dcom.sun.management.jmxremote.password.file=/usr/share/tomcat7/conf/jmxremote.password \
-Dcom.sun.management.jmxremote.access.file=/usr/share/tomcat7/conf/jmxremote.access"

JAVA_OPTS="$JAVA_OPTS $CATALINA_OPTS -XX:OnOutOfMemoryError='kill -9 %p; /sbin/service tomcat7 start'"

export JAVA_OPTS="$JAVA_OPTS -Dapp.name=Fedora4 -Djava.awt.headless=true -Dfile-encoding=UTF-8 -server -XX:+DisableExplicitGC"

# fedora 4 properties
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.home=/fedora_data2"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.modeshape.index.directory=/usr/share/fedora_data2/indexes"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.data.container=/prod"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.log.directory=/usr/share/tomcat7/logs"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.log.jcr=TRACE"


export CATALINA_PID=$CATALINA_HOME/logs/tomcat.pid