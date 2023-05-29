# Real-time-data-monitoring-system
Streaming data analytics visualization system

A correct visualisation of data allows us to eliminate noise from the data and highlight useful information, organising it 
in a way that is best suited to our needs. The logistics sector is characterised by a set of interlinked services that are generated in a chained manner,
with the correct performance of one of its services (such as parcel delivery) depending on the previous stages carried out and those that are being carried
out simultaneously. Therefore, it would be interesting to introduce a control panel to monitor the data in real time of its different stages in order to be
able to follow the correct functioning and development of these in a chained, simultaneous and orderly manner. 


This project is based on this use case to be able to apply a real-time data monitoring system for services and/or applications. 
To do so, this work involves several phases. 


A first phase of exploration of the different monitoring tools that are most interesting for the system to be designed, such as Kibana, Grafana, Prometheus 
(and more), focusing on those that allow real-time data visualisation. For this, the tools will be configured and installed in Docker and the rest of the 
code will be implemented in IntelliJ in Scala.  

The second phase after the exploration phase will be to apply to our use case a monitoring dashboard designed through one (or several) of the explored tools. 

The use case is a simulator of 9 logistics services, generating data in real time. In order to choose the right solution, a comparison is made between the
different implementation options offered by the tools, comparing what each one provides, their limitations, options, complexity and effectiveness. 

Finally, when deciding which of the tools to use, a real-time data control panel is created for certain metrics to be controlled. 
After obtaining this dashboard, we proceed to apply it to the use case of the given simulator. This control panel can be adapted and applied to any 
type of real-time services and/or applications.
