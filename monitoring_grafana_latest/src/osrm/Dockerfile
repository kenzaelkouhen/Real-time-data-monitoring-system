FROM osrm/osrm-backend
RUN apt-get update && apt-get install -y wget

RUN wget http://download.geofabrik.de/europe/spain/madrid-latest.osm.pbf
RUN osrm-extract -p /opt/car.lua madrid-latest.osm.pbf
RUN osrm-partition madrid-latest.osrm
RUN osrm-customize madrid-latest.osrm
RUN rm madrid-latest.osm.pbf

EXPOSE 5000
CMD osrm-routed --algorithm mld madrid-latest.osrm
