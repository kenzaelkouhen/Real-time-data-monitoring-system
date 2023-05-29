from flask import Flask, render_template
from pymongo import MongoClient
from bson import json_util
from flask import jsonify
#cors
from flask_cors import CORS


app = Flask(__name__)
CORS(app)

# Connect to a MongoDB database
client = MongoClient("mongodb://mongo:27017/")
db = client["simulator"]
collection = db["locations"]
# Get all the documents in the collection
documents = collection.find()
# Convert the documents to JSON without the _id field

markers = json_util.dumps(documents, default=str)

@app.route("/")
def index():
    return render_template("index.html", markers=markers)

if __name__ == "__main__":

    app.run(port=5003)

