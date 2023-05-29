from flask import Flask, render_template, Response
from pykafka import KafkaClient
from pykafka.common import OffsetType

## based on https://github.com/code-and-dogs/python-dsd--12pythonFlightVisualization

def get_kafka_client():
    return KafkaClient(hosts='127.0.0.1:9093')

app = Flask(__name__)

@app.route('/')
def index():
    return(render_template('index.html'))

@app.route('/topic/<topicname>')
def get_messages(topicname):
    # conect on new messages
    client = get_kafka_client()
    def events():
        consumer = client.topics[topicname].get_simple_consumer( reset_offset_on_start=True, 
            auto_offset_reset=OffsetType.LATEST, 
            auto_commit_enable=True, 
            auto_commit_interval_ms=1000)
        for i in consumer:
            yield 'data:{0}\n\n'.format(i.value.decode())
    return Response(events(), mimetype="text/event-stream")

if __name__ == '__main__':
    app.run(debug=True, port=5005)