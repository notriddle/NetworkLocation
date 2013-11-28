package org.microg.networklocation.v2;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import org.microg.networklocation.source.CellLocationSource;
import org.microg.networklocation.source.LocationSource;
import org.microg.networklocation.source.WlanLocationSource;

import java.util.*;

public class LocationCalculator {
	private static final String TAG = "v2LocationCalculator";
	public static final int MAX_WIFI_RADIUS = 500;
	private List<CellLocationSource> cellLocationSources;
	private List<WlanLocationSource> wlanLocationSources;
	private LocationDatabase locationDatabase;

	private static <T extends PropSpec> Collection<Collection<LocationSpec<T>>> divideInClasses(
			Collection<LocationSpec<T>> locationSpecs, double accuracy) {
		Collection<Collection<LocationSpec<T>>> classes = new ArrayList<Collection<LocationSpec<T>>>();
		for (LocationSpec<T> locationSpec : locationSpecs) {
			boolean used = false;
			for (Collection<LocationSpec<T>> locClass : classes) {
				if (locationCompatibleWithClass(locationSpec, locClass, accuracy)) {
					locClass.add(locationSpec);
					used = true;
				}
			}
			if (!used) {
				Collection<LocationSpec<T>> locClass = new ArrayList<LocationSpec<T>>();
				locClass.add(locationSpec);
				classes.add(locClass);
			}
		}
		return classes;
	}

	private static <T extends PropSpec> boolean locationCompatibleWithClass(LocationSpec<T> locationSpec,
																			Collection<LocationSpec<T>> locClass,
																			double accuracy) {
		for (LocationSpec<T> spec : locClass) {
			if ((locationSpec.distanceBetween(spec) - locationSpec.getAccuracy() - spec.getAccuracy() -
				 accuracy) < 0) {
				return true;
			}
		}
		return false;
	}

	private static <T extends PropSpec> boolean locationCompatibleWithClass(Location location,
																			Collection<LocationSpec<T>> locClass) {
		for (LocationSpec<T> spec : locClass) {
			if ((spec.distanceBetween(location) - location.getAccuracy() - spec.getAccuracy()) < 0) {
				return true;
			}
		}
		return false;
	}

	private <T extends PropSpec> Location getAverageLocation(Collection<LocationSpec<T>> locationSpecs) {
		// TODO: This is a stupid way to do this, we could do better by using the signal strength and triangulation
		double latSum = 0, lonSum = 0, accSum = 0;
		for (LocationSpec<T> cellLocationSpec : locationSpecs) {
			latSum += cellLocationSpec.getLatitude();
			lonSum += cellLocationSpec.getLongitude();
			accSum += cellLocationSpec.getAccuracy();
		}

		Location location = new Location("network");
		location.setAccuracy((float) (accSum / locationSpecs.size()));
		location.setLatitude(latSum / locationSpecs.size());
		location.setLongitude(latSum / locationSpecs.size());
		return location;
	}

	public Location getCurrentCellLocation() {
		Collection<LocationSpec<CellSpec>> cellLocationSpecs = getLocation(getCurrentCells());

		if (cellLocationSpecs.isEmpty()) {
			return null;
		}
		Location location = getAverageLocation(cellLocationSpecs);


		Bundle b = new Bundle();
		b.putString("networkLocationType", "cell");
		location.setExtras(b);
		return location;
	}

	private Collection<CellSpec> getCurrentCells() {
		Log.d(TAG, "TODO: Implement: getCurrentCells()");
		return Collections.emptySet();
	}

	public Location getCurrentWlanLocation(Location cellLocation) {
		Collection<LocationSpec<WlanSpec>> wlanLocationSpecs = getLocation(getCurrentWlans());

		if ((wlanLocationSpecs.size() < 2) || ((cellLocation == null) && (wlanLocationSpecs.size() < 3))) {
			return null;
		}

		Location location = null;
		if (cellLocation == null) {
			List<Collection<LocationSpec<WlanSpec>>> classes =
					new ArrayList<Collection<LocationSpec<WlanSpec>>>(divideInClasses(wlanLocationSpecs,
																					  MAX_WIFI_RADIUS));
			Collections.sort(classes, CollectionSizeComparator.INSTANCE);
			location = getAverageLocation(classes.get(0));
		} else {
			List<Collection<LocationSpec<WlanSpec>>> classes = new ArrayList<Collection<LocationSpec<WlanSpec>>>(
					divideInClasses(wlanLocationSpecs, cellLocation.getAccuracy()));
			Collections.sort(classes, CollectionSizeComparator.INSTANCE);
			for (Collection<LocationSpec<WlanSpec>> locClass : classes) {
				if (locationCompatibleWithClass(cellLocation, locClass)) {
					location = getAverageLocation(locClass);
					break;
				}
			}
		}
		if (location != null) {
			Bundle b = new Bundle();
			b.putString("networkLocationType", "wifi");
			location.setExtras(b);
		}
		return location;
	}

	public Location getCurrentLocation() {
		Location cellLocation = getCurrentCellLocation();
		Location wlanLocation = getCurrentWlanLocation(cellLocation);
		if (wlanLocation != null) {
			return wlanLocation;
		}
		return cellLocation;
	}

	private Collection<WlanSpec> getCurrentWlans() {
		Log.d(TAG, "TODO: Implement: getCurrentWlans()");
		return Collections.emptySet();
	}

	private <T extends PropSpec> Collection<LocationSpec<T>> getLocation(Collection<T> specs) {
		Collection<LocationSpec<T>> locationSpecs = new HashSet<LocationSpec<T>>();
		for (T spec : specs) {
			LocationSpec<T> locationSpec = locationDatabase.get(spec);
			if (locationSpec == null) {
				queueLocationRetrieval(spec);
			} else {
				locationSpecs.add(locationSpec);
			}
		}
		return locationSpecs;
	}

	private <T extends PropSpec> void queueLocationRetrieval(T spec) {
		Log.d(TAG, "TODO: Implement: queueLocationRetrieval(T)");
	}

	private void queueLocationRetrieval(CellSpec cellSpec) {
		Log.d(TAG, "TODO: Implement: queueLocationRetrieval(CellSpec)");
	}

	private void queueLocationRetrieval(WlanSpec wlanSpec) {
		Log.d(TAG, "TODO: Implement: queueLocationRetrieval(WlanSpec)");
	}

	private <T extends PropSpec> void retrieveLocation(Iterable<? extends LocationSource<T>> locationSources,
													   T... specs) {
		Collection<T> todo = new ArrayList<T>(Arrays.asList(specs));
		for (LocationSource<T> locationSource : locationSources) {
			for (LocationSpec<T> locationSpec : locationSource.retrieveLocation(todo)) {
				locationDatabase.put(locationSpec);
				todo.remove(locationSpec.getSource());
			}
			if (todo.isEmpty()) {
				break;
			}
		}
		for (T spec : todo) {
			locationDatabase.put(new LocationSpec<T>(spec));
		}
	}

	private void retrieveLocation(CellSpec... cellSpecs) {
		retrieveLocation(cellLocationSources, cellSpecs);
	}

	private void retrieveLocation(WlanSpec... wlanSpecs) {
		retrieveLocation(wlanLocationSources, wlanSpecs);
	}

	public static class CollectionSizeComparator implements Comparator<Collection<LocationSpec<WlanSpec>>> {
		public static CollectionSizeComparator INSTANCE = new CollectionSizeComparator();

		@Override
		public int compare(Collection<LocationSpec<WlanSpec>> left, Collection<LocationSpec<WlanSpec>> right) {
			return (left.size() < right.size()) ? -1 : ((left.size() > right.size()) ? 1 : 0);
		}
	}
}

