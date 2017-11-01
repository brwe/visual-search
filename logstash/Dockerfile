FROM docker.elastic.co/logstash/logstash:5.6.2
RUN rm -f /usr/share/logstash/pipeline/logstash.conf


RUN /usr/share/logstash/bin/logstash-plugin install logstash-output-http

ADD logstash.conf /usr/share/logstash/pipeline/
