

from kafka import KafkaConsumer
import json
import os

## Connect to kafka as a consumer, output from 2 topics to their corresponding files
def kafka2file():
    # Connect to kafka as a consumer
    consumer = KafkaConsumer('positions', 'simulation', bootstrap_servers=['localhost:9093'], auto_offset_reset='earliest', enable_auto_commit=True, group_id='my-group', value_deserializer=lambda x: json.loads(x.decode('utf-8')))
    # Output from 2 topics to their corresponding files
    for message in consumer:
        if message.topic == 'positions':
            with open('positions.jsonlines', 'a') as f:
                f.write(json.dumps(message.value))
                f.write('\n')
        elif message.topic == 'simulation':
            with open('simulation.jsonlines', 'a') as f:
                f.write(json.dumps(message.value))
                f.write('\n')
        else:
            print('No topic found')

kafka2file()