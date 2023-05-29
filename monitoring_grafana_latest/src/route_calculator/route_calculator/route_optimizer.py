from itertools import groupby
from collections import defaultdict, namedtuple
from ortools.constraint_solver import routing_enums_pb2, pywrapcp
from flask import jsonify

import requests
import json

osrm = "http://osrm:5000"

item = namedtuple("item", ["id", "length", "destinationId"])
truck = namedtuple("truck", ["id", "capacity"])
location = namedtuple("location", ["name", "longitude", "latitude"])


def optimize_route(data):
    try:
        items = [item(x["id"], 1, x["destinationId"]) for x in data["items"]]
        fleet = [truck(x["id"], x["capacity"]) for x in data["fleet"]]
        locations = [location(x["name"], x["coordinates"]["longitude"], x["coordinates"]["latitude"]) for x in data["locations"]]
    except KeyError as e:
        return {"error": "Invalid data format: " + str(e) + " is missing"}
    items_by_destination = defaultdict(list)
    
    # dict with all ids per destinationID
    for k, v in groupby(items, lambda x: x.destinationId):
        items_by_destination[k].extend(v)
    
    print(items_by_destination)
    matrix = generate_matrix(locations)
    solution = generate_solution(items_by_destination, fleet, matrix)
    if not solution:
        return jsonify({"error": "No valid routes found"})
    for route in solution:
        route["items"] = [x.id for x in route["items"]]
    return jsonify(solution)


def generate_matrix(destinations):
    res = json.loads(requests.get(osrm + "/table/v1/driving/" + ";".join(["{},{}".format(loc.longitude, loc.latitude) for loc in destinations])).content)
    return res["durations"]


def generate_solution(items_by_destination, fleet, matrix):
    manager = pywrapcp.RoutingIndexManager(len(matrix), len(fleet), 0)
    routing = pywrapcp.RoutingModel(manager)

    def duration_callback(from_index, to_index):
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return matrix[from_node][to_node]

    transit_callback_index = routing.RegisterTransitCallback(duration_callback)

    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    dimension_name = 'Time'
    routing.AddDimension(
        transit_callback_index,
        0,  # no slack
        3600 * 24,  # maximum 24 hours per vehicle
        True,
        dimension_name)
    distance_dimension = routing.GetDimensionOrDie(dimension_name)
    distance_dimension.SetGlobalSpanCostCoefficient(100)

    def demand_callback(from_index):
        from_node = manager.IndexToNode(from_index)
        return sum([x.length for x in items_by_destination[from_node]])

    demand_callback_index = routing.RegisterUnaryTransitCallback(demand_callback)
    routing.AddDimensionWithVehicleCapacity(
        demand_callback_index,
        0,  # null capacity slack
        [x.capacity for x in fleet],  # vehicle maximum capacities
        True,
        'Capacity')

    search_parameters = pywrapcp.DefaultRoutingSearchParameters()
    search_parameters.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PATH_CHEAPEST_ARC

    solution = routing.SolveWithParameters(search_parameters)
    if not solution:
        return None

    return get_routes(items_by_destination, fleet, manager, routing, solution, matrix)


def get_routes(items_by_destination, fleet, manager, routing, solution, matrix):
    routes = []
    for vehicle_id in range(len(fleet)):
        index = routing.Start(vehicle_id)
        route = []
        items = []
        while not routing.IsEnd(index):
            node_index = manager.IndexToNode(index)
            previous_index = index
            index = solution.Value(routing.NextVar(index))
            route.append( {"origin": manager.IndexToNode(previous_index),"destination": manager.IndexToNode(index), "duration": int(matrix[manager.IndexToNode(previous_index)][manager.IndexToNode(index)])})
            items.extend(items_by_destination[manager.IndexToNode(index)])
        if len(route):
            routes.append({"truck_id": fleet[vehicle_id].id, "route": route, "items": items})
    return routes
