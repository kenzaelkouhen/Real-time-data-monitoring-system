import pickle
import pymongo
import pykafka
import json
import numpy as np
from pykafka.common import OffsetType
from collections import namedtuple

########################################################
# Carga de modelos y labelEncoder y conexión con Kafka #
########################################################

# Cargamos los modelos y el labelEncoder
with open('data/prediccionOnline/travelModel.pkl', 'rb') as f:
    modelo_tiempo_viaje = pickle.load(f)
with open('data/prediccionOnline/deliveryModel.pkl', 'rb') as f:
    modelo_tiempo_entrega = pickle.load(f)
with open('data/prediccionOnline/le.pkl', 'rb') as f:
    labelEncoder = pickle.load(f)

vectores = {}

# Conectamos con el servidor de Kafka
client = pykafka.KafkaClient(hosts="localhost:9093")
topic = client.topics['simulation']

###############################################################
# Métodos para obtener el plan de la base de datos y predecir #
###############################################################

# Obtener el plan de la base de datos mongodb
def obtenerPlan(evento):
    # conectar a mongo
    client = pymongo.MongoClient("mongodb://localhost:27017/")
    db = client["simulator"]
    col = db["plans"]
    # Obtener el plan de la simulación
    plan = col.find_one({"simulationId": evento["simulationId"]})
    # Buscamos en plan["trucks"] el camión con truckId = evento.truckId
    camion = list(filter(lambda truck: truck["truck_id"] == evento["truckId"], plan["trucks"]))[0]
    vector = {
        "tiemposEstimados": [ r["duration"] for r in camion["route"] ],
        "vector": np.array([])
    }
    # Añadir la información del camión al diccionario de vectores
    vectores[(evento["simulationId"],evento["truckId"])] = vector
    # Cerrar la conexión
    client.close()

def actualizarVectores(evento):
    # Si el evento es de comienzo de viaje, añadimos el tiempo estimado de viaje al vector
    if (evento["eventType"] in ["Truck departed", "Truck departed to depot"]):
        vectores[(evento["simulationId"],evento["truckId"])]["vector"] = np.array(vectores[(evento["simulationId"],evento["truckId"])]["tiemposEstimados"].pop(0))
    # Si el evento es de comienzo de entrega, añadimos el id del camión al vector
    elif (evento["eventType"] == "Truck started delivering"):
        vectores[(evento["simulationId"],evento["truckId"])]["vector"] = np.array(evento["truckId"])

def prediccionDeTiempoDeViaje(evento):
    vector = vectores[(evento["simulationId"],evento["truckId"])]["vector"].reshape(-1, 1) 
    prediccion = modelo_tiempo_viaje.predict(vector)[0]
    return (prediccion, "Prediccion de tiempo de viaje")

def prediccionDeTiempoDeEntrega(evento):
    vector = vectores[(evento["simulationId"],evento["truckId"])]["vector"].ravel()
    # Codificar el id del camión
    vector = labelEncoder.transform(vector)
    prediccion = modelo_tiempo_entrega.predict(vector.reshape(-1, 1) )[0]
    return (prediccion, "Prediccion de tiempo de entrega")

def escribirEnKafka(prediccion):
    # Conectamos con el topic de predicciones
    topic = client.topics['predictions']
    # Creamos el mensaje
    mensaje = str(prediccion[0]) + "," + str(prediccion[1])
    # Enviamos el mensaje
    with topic.get_sync_producer() as producer:
        producer.produce(mensaje.encode('utf-8'))

###########################################################
# Bucle principal: consumir mensajes y hacer predicciones #
###########################################################

# Consumimos los mensajes del topic
consumer = topic.get_simple_consumer( consumer_group='prediccionOnline', 
    reset_offset_on_start=True, 
    auto_offset_reset=OffsetType.LATEST, 
    auto_commit_enable=True, 
    auto_commit_interval_ms=1000)

# Procesamos los mensajes
for evento in consumer:

    # mensaje_evento, en formato json,  como un evento
    evento = json.loads(evento.value.decode('utf-8'))

    # Si no habíamos recibido ningún evento con este simulationId y truck_id, obtenemos su plan desde la base de datos
    if not (evento["simulationId"],evento["truckId"]) in vectores:
        obtenerPlan(evento)

    # Actualizamos el vector correspondiente según el vector de caracterísitcas de cada uno de los modelos
    actualizarVectores(evento)

    # Si el evento es de comienzo de viaje, hacemos una predicción
    if (evento["eventType"] in ["Truck departed", "Truck departed to depot"]):
        prediccion = prediccionDeTiempoDeViaje(evento)
        escribirEnKafka(prediccion)
    # Si el evento es de comienzo de entrega, hacemos una predicción
    elif (evento["eventType"] == "Truck started delivering"):
        prediccion = prediccionDeTiempoDeEntrega(evento)
        escribirEnKafka(prediccion)
    # Si el evento es final de ruta, borramos el vector para liberar memoria
    elif (evento["eventType"] == "Truck ended route"):
        del(vectores[(evento["simulationId"],evento["truckId"])])
