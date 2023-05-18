FROM wurstmeister/kafka

ADD prom-jmx-agent-config.yml /usr/app/prom-jmx-agent-config.yml
ADD https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.15.0/jmx_prometheus_javaagent-0.15.0.jar /usr/app/jmx_prometheus_javaagent.jar
#ADD http://search.maven.org/remotecontent?filepath=org/jolokia/jolokia-jvm/1.6.1/jolokia-   #jvm-1.6.1-agent.jar /usr/app/jolokia-jvm.jar