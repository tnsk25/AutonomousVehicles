package DataParsing;

/**
 *
 * @author TijanaKlimovic
 */
public class Resource extends TimestampAbstract {

	private double dropffLat; // drop-off latitude of resource
	private double dropffLon;
	private double fare;// drop-off longitude of resource

	public double getFare() {
		return fare;
	}

	//constructor initiating each field of the Resource record type
	public Resource(double pickupLat, double pickupLon, double dropffLat, double dropffLon, long time, double fare) {
		super(pickupLat, pickupLon, time);
		this.dropffLat = dropffLat;
		this.dropffLon = dropffLon;
		this.fare = fare;

	}

	/**
	 * returns the dropff latitude of the resource
	 * 
	 * @return {@code this.dropffLat}
	 */
	public double getDropoffLat() {
		return dropffLat;
	}

	/**
	 * returns the dropff longitude of the resource
	 * 
	 * @return {@code this.dropffon}
	 */
	public double getDropoffLon() {
		return dropffLon;
	}

}
