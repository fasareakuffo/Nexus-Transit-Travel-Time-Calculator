import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class RaptorRowCalculator implements Runnable {
    private final GTFSData gtfsData;
    private final GTFSStop originStop;
    private final String originStopId;
    private final int startTime;
    private final int endTime;
    private final int maxTrips;
    private final int maxTransferTime;
    private final RaptorResultMatrix resultMatrix;
    private Map<String, RaptorResult> row;
    
    public RaptorRowCalculator(GTFSData gtfsData, GTFSStop originStop, int startTime, int endTime, int maxTrips, int maxTransferTime, RaptorResultMatrix resultMatrix) {
        this.gtfsData = gtfsData;
        this.originStop = originStop;
        this.originStopId = originStop.getId();
        this.startTime = startTime;
        this.endTime = endTime;
        this.maxTrips = maxTrips;
        this.maxTransferTime = maxTransferTime;
        this.resultMatrix = resultMatrix;
        this.row = new HashMap<String, RaptorResult>();
    }
    
    public void run() {
        calculateResults();
        resultMatrix.putRow(originStopId, row);
    }
    
    private void processTripFromStop(GTFSTrip trip, GTFSStop currentStop, Set<GTFSStop> markedStops) {
		//System.out.println(">> Route " + trip.getRoute().getId());

		GTFSStop futureStop;
		String currentStopId = currentStop.getId();
		RaptorResult currentBest = getResult(currentStopId);
		RaptorResult futureBest;
		int futureArrivalTime;
		int futureActiveTime;
		int departureTime = trip.stopTimeAtStop(currentStop).getTime();
		
		for (GTFSStopTime futureStopTime : trip.stopTimesAfter(departureTime)) {
			futureStop = futureStopTime.getStop();
			futureBest = getResult(futureStop.getId());
			futureArrivalTime = futureStopTime.getTime();
			futureActiveTime = currentBest.activeTime + (futureArrivalTime - departureTime);
			
			if (futureActiveTime < futureBest.activeTime) {
				//System.out.println(">>> Stop " + futureStop.getId() + ": improved time from " + futureBest.activeTime + " to " + futureActiveTime + " by trip " + trip.getId() + " from stop " + currentStop.getId());
				futureBest.arrivalTime = futureArrivalTime;
				futureBest.activeTime = futureActiveTime;
				markedStops.add(futureStop);
			}
		}
	}
	
	private void processTransfersFromStop(GTFSStop currentStop, Set<GTFSStop> markedStops) {
		String currentStopId = currentStop.getId();
		RaptorResult currentBest = getResult(currentStopId);
		RaptorResult transferBest;
		int transferArrivalTime;
		int transferActiveTime;
		int transferAccessTime;
		
		for (GTFSStop transferStop : currentStop.getTransferStops()) {
			transferBest = getResult(transferStop.getId());
			transferAccessTime = currentStop.getAccessTimeForTransferStop(transferStop);
			transferArrivalTime = currentBest.arrivalTime + transferAccessTime;
			transferActiveTime = currentBest.activeTime + transferAccessTime;
			
			if (transferActiveTime < transferBest.activeTime) {
				//System.out.println(">>> Stop " + transferStop.getId() + ": improved time from " + transferBest.activeTime + " to " + transferActiveTime + " by walking from stop " + currentStop.getId());
				transferBest.arrivalTime = transferArrivalTime;
				transferBest.activeTime = transferActiveTime;
				
				markedStops.add(transferStop);
			}
		}
	}
	
	private void calculateResults() {
		System.out.println("Stop " + originStop.getId());
		HashSet<GTFSStop> nextRoundMarkedStops = new HashSet<GTFSStop>();
		nextRoundMarkedStops.add(originStop);
		HashSet<GTFSStop> thisRoundMarkedStops;
		updateResult(originStopId, startTime, 0);
		RaptorResult currentBest;
		int lastAllowedBoardingTime;
		
		// for the origin stop, there is no time limit on which trips can be taken
		for (int k=1; k<=maxTrips; k++) {
			//System.out.println("> Trip number " + k);
			thisRoundMarkedStops = new HashSet<GTFSStop>(nextRoundMarkedStops);
			nextRoundMarkedStops = new HashSet<GTFSStop>();
			
			for (GTFSStop currentStop : thisRoundMarkedStops) {
				
				currentBest = row.get(currentStop.getId());
				
				if (k == 1) {
					lastAllowedBoardingTime = endTime;
				} else {
					lastAllowedBoardingTime = currentBest.arrivalTime + maxTransferTime;
				}
				
				// process trips available from the current stop
				for (GTFSStopTime nextStopTime : currentStop.stopTimesBetween(currentBest.arrivalTime, lastAllowedBoardingTime)) {
					processTripFromStop(nextStopTime.getTrip(), currentStop, nextRoundMarkedStops);
				}	
				
				// process transfers available from the current stop
				HashSet<GTFSStop> possibleTransferStops = new HashSet<GTFSStop>(nextRoundMarkedStops);
				for (GTFSStop possibleTransferStop : possibleTransferStops) {
					processTransfersFromStop(possibleTransferStop, nextRoundMarkedStops);
				}
			}
		}
	}
	
	private RaptorResult getResult(String stopId) {
	    if (!row.containsKey(stopId)) {
	        row.put(stopId, new RaptorResult());    
	    }
	    return row.get(stopId);	    
	}
	
	private void updateResult(String stopId, int arrivalTime, int activeTime){
	    if (!row.containsKey(stopId)) {
	        row.put(stopId, new RaptorResult());
	    }
	    RaptorResult result = row.get(stopId);
	    result.arrivalTime = arrivalTime;
	    result.activeTime = activeTime;
	}
}