import os
from flask import Flask, request
from .route_optimizer import optimize_route


def create_app():
    app = Flask("RouteCalculator", instance_relative_config=True)
    try:
        os.makedirs(app.instance_path)
    except OSError:
        pass
    
    @app.route("/route_optimizer", methods=['POST'])
    def route_optimizer_call():
        return optimize_route(request.get_json())

    return app
