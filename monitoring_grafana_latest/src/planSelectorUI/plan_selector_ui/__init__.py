from flask import Flask, render_template
from pymongo import MongoClient
from bson import json_util
from flask import jsonify
import requests
import os
from flask import request
#cors


def create_app():
    app = Flask(__name__)

    # Connect to a MongoDB database
    client = MongoClient("mongodb://mongo:27017/")
    db = client["simulator"]
    collection = db["locations"]
    # Get all the documents in the collection
    documents = collection.find()
    # Convert the documents to JSON without the _id field

    markers = json_util.dumps(documents, default=str)
    app = Flask("planSelectorUI", instance_relative_config=True)
    try:
        os.makedirs(app.instance_path)
    except OSError:
        pass
    
    @app.route("/")
    def index():
        return render_template("index.html", markers=markers)

    @app.route("/createPlan", methods=['POST'])
    def createPlan():
        #receive data from frontend
        data = request.json
        #send data to backend
        r = requests.post('http://simulator:7500/createPlan', json=data)
        #wait and return response from backend
        print(r)
        return r.json()
        
        
    return app







