export JAVA_HOME=/usr/java/default
export PATH=$JAVA_HOME/bin:$PATH
export CATALINA_HOME=/usr/share/tomcat7

# jvm options
JAVA_OPTS="$JAVA_OPTS -server"
JAVA_OPTS="$JAVA_OPTS -XX:NewSize=2560m -XX:MaxNewSize=5g -XX:InitialHeapSize=10g -XX:MaxHeapSize=10g"
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"
JAVA_OPTS="$JAVA_OPTS -XX:+UseParallelGC -XX:+DisableExplicitGC -XX:SurvivorRatio=15"
JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=/usr/share/tomcat7/logs/fedora_heap.hprof"
JAVA_OPTS="$JAVA_OPTS -XX:+PrintGCDetails -XX:+PrintGCDateStamps -XX:+PrintGCApplicationStoppedTime"
JAVA_OPTS="$JAVA_OPTS -Xloggc:/usr/share/tomcat7/logs/gc.log"

# Timezone and JVM file encoding
JAVA_OPTS="$JAVA_OPTS -Duser.timezone=UTC -Dfile.encoding=UTF8 -Djava.awt.headless=true"
export CATALINA_OPTS="-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.authenticate=true \
-Dcom.sun.management.jmxremote.password.file=/usr/share/tomcat7/conf/jmxremote.password \
-Dcom.sun.management.jmxremote.access.file=/usr/share/tomcat7/conf/jmxremote.access"
JAVA_OPTS="$JAVA_OPTS $CATALINA_OPTS -XX:OnOutOfMemoryError='kill -9 %p; /sbin/service tomcat7 start'"

# fedora 4 properties
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.home=/fedora_data2"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.modeshape.index.directory=/fedora_data2/fcrepo.index.directory"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.log.directory=/usr/share/tomcat7/logs"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.log.jcr=DEBUG"
export JAVA_OPTS="$JAVA_OPTS -Dfcrepo.log=DEBUG"


export CATALINA_PID=$CATALINA_HOME/logs/tomcat.pid