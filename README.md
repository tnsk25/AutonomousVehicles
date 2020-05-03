# AUTONOMOUS VEHICLES IMPACT ON RIDE‐HAILING

Ride hailing became one of the most used commercials after the introduction of Global Positioning System (GPS). To date, one of  the large scale bodies for ride hailing services are Uber and Lyft which provide a direct link between the drivers and passengers. Since the introduction of ride hailing services and car pooling services, its impact on reducing the number of vehicles, reducing traffic congestion, and reducing pollution has been phenomenal. 

Company ABC approached us to do a performance analysis on their fleet of cars used for ride hailing. They would like to know what would be a more profitable approach for the company as well as best for the customers by doing a comparison of a crowdsourced fleet of vehicles vs an autonomous one. We need to factor in a few parameters that would help us make a decision on the number of cabs to operate, the algorithm used to assign cabs to resources and so on. 

A few parameters that consider are agent search time, resource wait time and profitability for the company. We would look for the approach that would reduce the expiration percentage, agent search time and resource wait time, thus maximising the profit for the company.

Here, in this project we are consuming the yellow taxi records in order to build a model that gives out results efficiently with respect to the passenger and the driver. The system is built with two algorithms to make the passengers and drivers get their intended mode of benefits. These algorithms are developed, keeping in mind that both the passengers and the drivers are benefitted, and not just the company.


# COMSET

COMSET simulates crowdsourced taxicabs (called <i>agents</i>) searching for customers (called <i>resources</i>) to pick up in a city. We have used the COMSET solution that took resources as introduced in the system and matched them to nearest agent one by one. We modified this to enable a matching to happen for a pool of resources and implemented our Fair/Optimum algorithm on top of it.

# Prerequisites

COMSETsystem requires JAVA 8 or up.

When you download the project, Maven should be supported. In Intellij you can just follow the following link.

https://www.jetbrains.com/help/idea/maven-support.html#maven_import_project_start


