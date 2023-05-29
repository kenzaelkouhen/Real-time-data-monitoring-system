var mymap = L.map('mapid').setView([40.4168, -3.7038], 13);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
}).addTo(mymap);


var mapMarkers = {};

var flightIcon = L.icon({
  iconUrl: 'static/truck.png',
  iconSize:     [30, 30], // size of the icon
  popupAnchor:  [-3, -76] // point from which the popup should open relative to the iconAnchor
});

var source = new EventSource('/topic/positions'); 
source.addEventListener('message', function(e){

  obj = JSON.parse(e.data);

  //get or else create path
  if (mapMarkers[obj.truckId] == undefined) {
    mapMarkers[obj.truckId] = [];
  }
  path = mapMarkers[obj.truckId]
  
  for (var i = 0; i < path.length; i++) {
    mymap.removeLayer(path[i]);
  }
  marker = L.marker([obj.coordinates.latitude, obj.coordinates.longitude], {icon: flightIcon}).addTo(mymap);
  if (path.length > 0) {
    line = L.polyline([path[path.length-1].getLatLng(),marker.getLatLng()], {color:'blue'}).addTo(mymap);

  }
  path.push(marker);
 
}, false);
