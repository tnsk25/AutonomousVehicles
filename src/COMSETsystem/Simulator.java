package COMSETsystem;

import MapCreation.*;

import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javafx.util.Pair;
import me.tongfei.progressbar.*;


import DataParsing.*;

import static COMSETsystem.ResourceEvent.EXPIRED;
import static COMSETsystem.Simulator.AlgorithmFactorEnum.PICKUP_TIME;
import static COMSETsystem.Simulator.AlgorithmFactorEnum.TOTAL_FARE;

/**
 * The Simulator class defines the major steps of the simulation. It is
 * responsible for loading the map, creating the necessary number of agents,
 * creating a respective AgentEvent for each of them such that they are added
 * to the events PriorityQueue. Furthermore it is also responsible for dealing 
 * with the arrival of resources, map matching them to the map, and assigning  
 * them to agents. This produces the score according to the scoring rules.
 * <p>
 * The time is simulated by the events having a variable time, this time
 * corresponds to when something will be empty and thus needs some
 * interaction (triggering). There's an event corresponding to every existent
 * Agent and for every resource that hasn't arrived yet. All of this events are
 * in a PriorityQueue called events which is ordered by their time in an
 * increasing way.
 */
public class Simulator {

	static enum AlgorithmFactorEnum {
		PICKUP_TIME("pickup_time"), TOTAL_FARE("total_fare");

		private String key;

		AlgorithmFactorEnum(String key) {
			this.key = key;
		}

		public String getKey(){
			return key;
		}

		private static final Map<String, AlgorithmFactorEnum> lookup = new HashMap<>();

		static {
			for(AlgorithmFactorEnum env : AlgorithmFactorEnum.values()) {
				lookup.put(env.getKey(), env);
			}
		}

		public static AlgorithmFactorEnum get(String url) {
			return lookup.get(url);
		}
	}
	// The map that everything will happen on.
	protected CityMap map;

	// A deep copy of map to be passed to agents. 
	// This is a way to make map unmodifiable.
	protected CityMap mapForAgents;

	// The event queue.
	protected PriorityQueue<Event> events = new PriorityQueue<>();

	// The set of empty agents.
	protected TreeSet<AgentEvent> emptyAgents = new TreeSet<>(new AgentEventComparator());

	// The set of resources that with no agent assigned to it yet.
	protected TreeSet<ResourceEvent> waitingResources = new TreeSet<>(new ResourceEventComparator());

	// The maximum life time of a resource in seconds. This is a parameter of the simulator. 
	public long ResourceMaximumLifeTime; 

	// Full path to an OSM JSON map file
	protected String mapJSONFile;


	// Full path to a TLC New York Yellow trip record file
	protected String resourceFile = null;

	// Full path to a KML defining the bounding polygon to crop the map
	protected String boundingPolygonKMLFile;

	// The simulation end time is the expiration time of the last resource.
	protected long simulationEndTime; 

	// Total trip time of all resources to which agents have been assigned.
	protected long totalResourceTripTime = 0;

	// Total wait time of all resources. The wait time of a resource is the amount of time
	// since the resource is introduced to the system until it is picked up by an agent.
	protected long totalResourceWaitTime = 0;

	// Total search time of all agents. The search time of an agent for a research is the amount of time 
	// since the agent is labeled as empty, i.e., added to emptyAgents, until it picks up a resource.  
	protected long totalAgentSearchTime = 0;

	// Total cruise time of all agents. The cruise time of an agent for a research is the amount of time 
	// since the agent is labeled as empty until it is assigned to a resource.
	protected long totalAgentCruiseTime = 0;

	// Total approach time of all agents. The approach time of an agent for a research is the amount of time
	// since the agent is assigned to a resource until agent reaches the resource.
	protected long totalAgentApproachTime = 0;

	// The number of expired resources.
	protected long expiredResources = 0;

	// The number of resources that have been introduced to the system.
	protected long totalResources = 0;

	// The number of agents that are deployed (at the beginning of the simulation). 
	protected long totalAgents;

	// The number of assignments that have been made.
	protected long totalAssignments = 0;

	protected long batchTimeFrame = 0;

	protected AlgorithmFactorEnum factorEnum;

	protected long poolCount = 0;

	protected double totalFare = 0.0;

	protected double totalBenefitFactor = 0.0;

	// A list of all the agents in the system. Not really used in COMSET, but maintained for
	// a user's debugging purposes.
	ArrayList<BaseAgent> agents;

	// A class that extends BaseAgent and implements a search routing strategy
	protected final Class<? extends BaseAgent> agentClass;

	/**
	 * Constructor of the class Main. This is made such that the type of
	 * agent/resourceAnalyzer used is not hardcoded and the users can choose
	 * whichever they wants.
	 *
	 * @param agentClass the agent class that is going to be used in this
	 * simulation.
	 */
	public Simulator(Class<? extends BaseAgent> agentClass) {
		this.agentClass = agentClass;
	}

	/**
	 * Configure the simulation system including:
	 * 
	 * 1. Create a map from the map file and the bounding polygon KML file.
	 * 2. Load the resource data set and map match.
	 * 3. Create the event queue. 
	 *
	 * See Main.java for detailed description of the parameters.
	 * 
	 * @param mapJSONFile The map file 
	 * @param resourceFile The dataset file
	 * @param totalAgents The total number of agents to deploy
	 * @param boundingPolygonKMLFile The KML file defining a bounding polygon of the simulated area
	 * @param maximumLifeTime The maximum life time of a resource
	 * @param agentPlacementRandomSeed The see for the random number of generator when placing the agents
	 * @param speedReduction The speed reduction to accommodate traffic jams and turn delays
	 */
	public void configure(String mapJSONFile, String resourceFile, Long totalAgents, String boundingPolygonKMLFile, Long maximumLifeTime, long agentPlacementRandomSeed, double speedReduction, long batchTimeFrame, String algoFactor) {

		this.mapJSONFile = mapJSONFile;

		this.totalAgents = totalAgents;

		this.boundingPolygonKMLFile = boundingPolygonKMLFile;

		this.ResourceMaximumLifeTime = maximumLifeTime;

		this.resourceFile = resourceFile;

		this.batchTimeFrame = batchTimeFrame;

		this.factorEnum =  AlgorithmFactorEnum.get(algoFactor);

		MapCreator creator = new MapCreator(this.mapJSONFile, this.boundingPolygonKMLFile, speedReduction);
		System.out.println("Creating the map...");

		creator.createMap();

		// Output the map
		map = creator.outputCityMap();

		// Pre-compute shortest travel times between all pairs of intersections.
		System.out.println("Pre-computing all pair travel times...");
		map.calcTravelTimes();

		// Make a map copy for agents to use so that an agent cannot modify the map used by
		// the simulator
		mapForAgents = map.makeCopy();

		MapWithData mapWD = new MapWithData(map, this.resourceFile, agentPlacementRandomSeed);

		// map match resources
		System.out.println("Loading and map-matching resources...");
		long latestResourceTime = mapWD.createMapWithData(this);

		// The simulation end time is the expiration time of the last resource.
		this.simulationEndTime = latestResourceTime;

		// Deploy agents at random locations of the map.
		System.out.println("Randomly placing " + this.totalAgents + " agents on the map...");
		agents = mapWD.placeAgentsRandomly(this);

		// Initialize the event queue.
		events = mapWD.getEvents();
	}

	/**
	 * This method corresponds to running the simulation. An object of ScoreInfo
	 * is created in order to keep track of performance in the current
	 * simulation. Go through every event until the simulation is over.
	 *
	 * @throws Exception since triggering events may create an Exception
	 */
	public void run() throws Exception {
		System.out.println("Running the simulation...");

		ScoreInfo score = new ScoreInfo();
		if (map == null) {
			System.out.println("map is null at beginning of run");
		}
		try (ProgressBar pb = new ProgressBar("Progress:", 100, ProgressBarStyle.ASCII)) {
			long beginTime = events.peek().time;
			long batchStartTime = -1;
			List<Event> batchEvents = new ArrayList<>();
//			List<List<Pair<AgentEvent, Long>>> resourceAgentTimeInfo = new ArrayList<>();
			while (events.peek() != null && events.peek().time <= simulationEndTime) {

				Event toTrigger = events.poll();
				if(toTrigger.eventType == Event.EventType.Resource) {

					if(((ResourceEvent)toTrigger).eventCause == EXPIRED) {
						((ResourceEvent) toTrigger).expireHandler();
						continue;
					}
					++totalResources;
					if(batchStartTime == -1) {
						batchStartTime = toTrigger.time;
					}
					if( toTrigger.time <= batchStartTime + batchTimeFrame) {
						batchEvents.add(toTrigger);
					} else {

						poolCount++;

						// remove resources whose time has expired. They can no longer be alloted to an agent.
						Iterator<Event> eventIterator = batchEvents.iterator();
						while(eventIterator.hasNext()) {
							ResourceEvent event = (ResourceEvent) eventIterator.next();
							if (batchStartTime + batchTimeFrame >= event.expirationTime) {
								event.time += ResourceMaximumLifeTime;
								event.eventCause = EXPIRED;
								events.add(event);
								eventIterator.remove();
							}
						}
						List<List<Pair<AgentEvent, Long>>> resourceAgentTimeInfo = new ArrayList<>();

						// generate 2d table of resources and agents with their travel time to reach resources.
						for(Event event : batchEvents) {
							resourceAgentTimeInfo.add(((ResourceEvent)event).getAgentsAndArrivalTime());
						}

						// get the least value in the 2d table and assign agent to the resource.
						// remove agent and resource from 2d table so it is not being used again.

						if(factorEnum == TOTAL_FARE) {
							List<Long> agentList = resourceAgentTimeInfo.stream()
									.flatMap(List::stream)
									.map(p -> p.getKey().id)
									.distinct()
									.collect(Collectors.toList());

							double[][] agentArrivalTimeTable = new double[batchEvents.size()][agentList.size()];

							for (int i = 0; i < agentArrivalTimeTable.length; i++) {
								for (int j = 0; j < agentArrivalTimeTable[0].length; j++) {
									agentArrivalTimeTable[i][j] = 50000;
								}
							}
							for (int i = 0; i < resourceAgentTimeInfo.size(); i++) {
								for (int j = 0; j < resourceAgentTimeInfo.get(i).size(); j++) {
									Pair<AgentEvent, Long> agentArrivalTime = resourceAgentTimeInfo.get(i).get(j);

									double factorValue = agentArrivalTime.getValue()/((ResourceEvent)(batchEvents.get(i))).fare;
									agentArrivalTimeTable[i][agentList.indexOf(agentArrivalTime.getKey().id)] = factorValue;
								}
							}

							HungarianAlgorithm ha = new HungarianAlgorithm(agentArrivalTimeTable);
							int[] agentIndexMatchArray = ha.execute();

							List<Event> filteredEvents = new ArrayList<>();
							for (int i = 0; i < agentIndexMatchArray.length; i++) {
								if(agentIndexMatchArray[i] != -1) {
									ResourceEvent resourceEvent = (ResourceEvent) batchEvents.get(i);
									totalFare += resourceEvent.fare;
									Long agentId = agentList.get(agentIndexMatchArray[i]);
									for(Pair<AgentEvent, Long> pair : resourceAgentTimeInfo.get(i)) {
										if(pair.getKey().id == agentId) {

											totalBenefitFactor += agentArrivalTimeTable[i][agentIndexMatchArray[i]];
											// get total fare from agentArrivalTimeTable.
											// what is i  and what is j ?   i is resource and j is agent .

											events.add(resourceEvent.assignAgentToResource(pair.getKey(),pair.getValue()));
											break;
										}
									}
								}else {
									filteredEvents.add(batchEvents.get(i));

								}
							}
							batchEvents = filteredEvents;
								}
						else {
							List<Long> reservedAgentIdList = new ArrayList<>();
							while (true) {
								long bestMatchResource = -1;
								AgentEvent bestMatchAgent = null;
								long bestEarliestTime = Long.MAX_VALUE;
								for (int i = 0; i < batchEvents.size(); i++) {
									Iterator<Pair<AgentEvent, Long>> pairIterator = resourceAgentTimeInfo.get(i).iterator();
									while (pairIterator.hasNext()) {
										Pair<AgentEvent, Long> pair = pairIterator.next();
										if (reservedAgentIdList.contains(pair.getKey().id)) {
											pairIterator.remove();
											continue;
										}
										// compare it as pickTime / fare.
										if (pair.getValue() < bestEarliestTime) {
											bestEarliestTime = pair.getValue();
											bestMatchResource = i;
											bestMatchAgent = pair.getKey();
										}
									}
								}
								// if no agent can be matched to the resource in this batch,
								// then stop the batch and try in next batch.
								if (bestMatchResource == -1) {
									break;
								}
								reservedAgentIdList.add(bestMatchAgent.id);
								resourceAgentTimeInfo.remove((int) bestMatchResource);
								ResourceEvent resourceEvent = (ResourceEvent) batchEvents.remove((int) bestMatchResource);
								totalFare +=resourceEvent.fare;
								AgentEvent agentEvent = resourceEvent.assignAgentToResource(bestMatchAgent, bestEarliestTime);
								events.add(agentEvent);
							}
						}
						batchStartTime = toTrigger.time;
						batchEvents.add(toTrigger);
					}
				}else {
					pb.stepTo((long) (((float) (toTrigger.time - beginTime)) / (simulationEndTime - beginTime) * 100.0));

					Event e = toTrigger.trigger();
					if (e != null) {
						events.add(e);
					}
				}
			}
			expiredResources += batchEvents.size();
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.out.println("Simulation finished.");

		score.end();
	}

	/**
	 * This class is used to give a performance report and the score. It prints
	 * the total running time of the simulation, the used memory and the score.
	 * It uses Runtime which allows the application to interface with the
	 * environment in which the application is running. 
	 */
	class ScoreInfo {

		Runtime runtime = Runtime.getRuntime();
		NumberFormat format = NumberFormat.getInstance();
		StringBuilder sb = new StringBuilder();

		long startTime;
		long allocatedMemory;

		/**
		 * Constructor for ScoreInfo class. Runs beginning, this method
		 * initializes all the necessary things.
		 */
		ScoreInfo() {
			startTime = System.nanoTime();
			// Suppress memory allocation information display
			// beginning();
		}

		/**
		 * Initializes and gets the max memory, allocated memory and free
		 * memory. All of these are added to the Performance Report which is
		 * saved in the StringBuilder. Furthermore also takes the time, such
		 * that later on we can compare to the time when the simulation is over.
		 * The allocated memory is also used to compare to the allocated memory
		 * by the end of the simulation.
		 */
		void beginning() {
			// Getting the memory used
			long maxMemory = runtime.maxMemory();
			allocatedMemory = runtime.totalMemory();
			long freeMemory = runtime.freeMemory();

			// probably unnecessary
			sb.append("Performance Report: " + "\n");
			sb.append("free memory: " + format.format(freeMemory / 1024) + "\n");
			sb.append("allocated memory: " + format.format(allocatedMemory / 1024)
			+ "\n");
			sb.append("max memory: " + format.format(maxMemory / 1024) + "\n");

			// still looking into this one "freeMemory + (maxMemory -
			// allocatedMemory)"
			sb.append("total free memory: "
					+ format.format(
							(freeMemory + (maxMemory - allocatedMemory)) / 1024)
					+ "\n");

			System.out.print(sb.toString());
		}

		/**
		 * Calculate the time the simulation took by taking the time right now
		 * and comparing to the time when the simulation started. Add the total
		 * time to the report and the score as well. Furthermore, calculate the
		 * allocated memory by the participant's implementation by comparing the
		 * previous allocated memory with the current allocated memory. Print
		 * the Performance Report.
		 */
		void end() {
			// Empty the string builder
			sb.setLength(0);

			long endTime = System.nanoTime();
			long totalTime = (endTime - startTime) / 1000000000;

			System.out.println("\nrunning time: " + totalTime);

			System.out.println("\n***Simulation environment***");
			System.out.println("JSON map file: " + mapJSONFile);
			System.out.println("Resource dataset file: " + resourceFile);
			System.out.println("Bounding polygon KML file: " + boundingPolygonKMLFile);
			System.out.println("Number of agents: " + totalAgents);
			System.out.println("Number of resources: " + totalResources);
			System.out.println("Resource Maximum Life Time: " + ResourceMaximumLifeTime + " seconds");
			System.out.println("Agent class: " + agentClass.getName());

			System.out.println("\n***Statistics***");
		
			if (totalResources != 0) {
				// Collect the "search" time for the agents that are empty at the end of the simulation.
				// These agents are in search status and therefore the amount of time they spend on
				// searching until the end of the simulation should be counted toward the total search time.
				long totalRemainTime = 0;
				for (AgentEvent ae: emptyAgents) {
					totalRemainTime += (simulationEndTime - ae.startSearchTime); 
				}

//				sb.append("Algorithm: " + " Optimum " + " Date: " + " May 20, 2016 " + " \n");
				sb.append("Total Fare earned from allocation: " + totalFare + " \n");
				sb.append("Number of Pools processed: " + poolCount + " \n");
				sb.append("average agent search time: " + Math.floorDiv(totalAgentSearchTime + totalRemainTime, (totalAssignments + emptyAgents.size())) + " seconds \n");
				sb.append("average resource wait time: " + Math.floorDiv(totalResourceWaitTime, totalResources) + " seconds \n");
				sb.append("resource expiration percentage: " + (expiredResources * 100.0)/ totalResources + "%\n");
//				sb.append("resource expiration percentage: " + ((totalResources - totalAssignments)* 100.0)/ totalResources + "%\n");
				sb.append("average benefit factor: " + (totalBenefitFactor / totalAgents + "\n"));
				sb.append("\n");

				totalAssignments = totalResources - expiredResources;
//				sb.append("average agent cruise time: " + Math.floorDiv(totalAgentCruiseTime, totalAssignments) + " seconds \n");
//				sb.append("average agent approach time: " + Math.floorDiv(totalAgentApproachTime, totalAssignments) + " seconds \n");
//				sb.append("average resource trip time: " + Math.floorDiv(totalResourceTripTime, totalAssignments) + " seconds \n");
				sb.append("total number of assignments: " + totalAssignments + "\n");
			} else {
				sb.append("No resources.\n");
			}

			System.out.print(sb.toString());
		}
	}

	/**
	 * Compares agent events
	 */
	class AgentEventComparator implements Comparator<AgentEvent> {

		/**
		 * Checks if two agentEvents are the same by checking their ids.
		 * 
		 * @param a1 The first agent event
		 * @param a2 The second agent event
		 * @return returns 0 if the two agent events are the same, 1 if the id of
		 * the first agent event is bigger than the id of the second agent event,
		 * -1 otherwise
		 */
		public int compare(AgentEvent a1, AgentEvent a2) {
			if (a1.id == a2.id)
				return 0;
			else if (a1.id > a2.id)
				return 1;
			else
				return -1;
		}
	}

	/**
	 * Compares resource events
	 */
	class ResourceEventComparator implements Comparator<ResourceEvent> {
		/**
		 * Checks if two resourceEvents are the same by checking their ids.
		 * 
		 * @param a1 The first resource event
		 * @param a2 The second resource event
		 * @return returns 0 if the two resource events are the same, 1 if the id of
		 * the resource event is bigger than the id of the second resource event,
		 * -1 otherwise
		 */
		public int compare(ResourceEvent a1, ResourceEvent a2) {
			if (a1.id == a2.id)
				return 0;
			else if (a1.id > a2.id)
				return 1;
			else
				return -1;
		}
	}

	/**
	 * Retrieves the total number of agents
	 * 
	 * @return {@code totalAgents }
	 */
	public long totalAgents() {
		return totalAgents;
	}

	/**
	 * Retrieves the CityMap instance of this simulation
	 * 
	 * @return {@code map }
	 */
	public CityMap getMap() {
		return map;
	}

	/**
	 * Sets the events of the simulation.
	 * 
	 * @param events The PriorityQueue of events
	 */
	public void setEvents(PriorityQueue<Event> events) {
		this.events = events;
	}

	/**
	 * Retrieves the queue of events of the simulation.
	 * 
	 * @return {@code events }
	 */
	public PriorityQueue<Event> getEvents() {
		return events;
	}

	/**
	 * Gets the empty agents in the simulation
	 * 
	 * @return {@code emptyAgents }
	 */
	public TreeSet<AgentEvent> getEmptyAgents() {
		return emptyAgents;
	}

	/**
	 * Sets the empty agents in the simulation
	 * 
	 * @param emptyAgents The TreeSet of agent events to set.
	 */
	public void setEmptyAgents(TreeSet<AgentEvent> emptyAgents) {
		this.emptyAgents = emptyAgents;
	}

	/**
	 * Make an agent copy of locationOnRoad so that an agent cannot modify the attributes of the road.
	 * 
	 * @param locationOnRoad the location to make a copy for
	 * @return an agent copy of the location 
	 */
	public LocationOnRoad agentCopy(LocationOnRoad locationOnRoad) {
		Intersection from = mapForAgents.intersections().get(locationOnRoad.road.from.id);
		Intersection to = mapForAgents.intersections().get(locationOnRoad.road.to.id);
		Road roadAgentCopy = from.roadsMapFrom.get(to);
		LocationOnRoad locationOnRoadAgentCopy = new LocationOnRoad(roadAgentCopy, locationOnRoad.travelTimeFromStartIntersection);
		return locationOnRoadAgentCopy;
	}
}
