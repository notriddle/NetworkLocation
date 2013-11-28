package org.microg.networklocation.data;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.telephony.CellLocation;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import org.microg.networklocation.MainService;
import org.microg.networklocation.database.CellMap;
import org.microg.networklocation.source.CellLocationSource;

import java.util.ArrayList;

public class CellLocationData extends DefaultLocationDataProvider {

	public static final String IDENTIFIER = "cell";
	private static final String TAG = "CellLocationData";
	private final Context context;
	private final CellMap gsmMap;
	private final LocationListener listener;
	private final CellLocationSource source;
	private TelephonyManager telephonyManager;

	public CellLocationData(final Context context, final CellMap gsmMap, final CellLocationSource source,
							final LocationListener listener) {
		this.context = context;
		this.gsmMap = gsmMap;
		this.listener = listener;
		this.source = source;
	}

	@Override
	public Location getCurrentLocation() {
		if (telephonyManager == null) {
			telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		}
		if (telephonyManager == null) {
			if (MainService.DEBUG)
				Log.d(TAG, "could not get location via gsm (could not initialize TelephonyManager)");
			return null;
		}
		final GsmCellLocation cell = getGsmCellLocation();
		final String operator = telephonyManager.getNetworkOperator();
		if (operator == null) {
			if (MainService.DEBUG)
				Log.d(TAG, "could not get location via gsm (operator is null)");
			return null;
		}
		final ArrayList<Location> locations = new ArrayList<Location>();

		if (cell != null) {
			final Location loc = getLocation(operator, cell.getLac(), cell.getCid());
			if (loc != null) {
				locations.add(loc);
			}
		}

		if (telephonyManager.getNeighboringCellInfo() != null) {
			for (final NeighboringCellInfo neighbor : telephonyManager.getNeighboringCellInfo()) {
				if (neighbor != null && neighbor.getCid() != -1 && neighbor.getLac() != -1) {
					final Location loc = getLocation(operator, neighbor.getLac(), neighbor.getCid());
					if (loc != null) {
						loc.setAccuracy(loc.getAccuracy() * 3);
						locations.add(loc);
					}
				}
			}
		}
		final Location location = calculateLocation(locations, 20000);
		if (location == null) {
			if (MainService.DEBUG)
				Log.d(TAG, "could not get location via gsm calculateLocation is null");
			return null;
		}
		listener.onLocationChanged(location);
		return location;
	}

	private GsmCellLocation getGsmCellLocation() {
		if (telephonyManager != null) {
			final CellLocation cellLocation = telephonyManager.getCellLocation();
			if (cellLocation instanceof GsmCellLocation) {
				return (GsmCellLocation) cellLocation;
			}
		}
		return null;
	}

	@Override
	public String getIdentifier() {
		return IDENTIFIER;
	}

	private Location getLocation(int mcc, int mnc, int lac, int cid) {
		if (mcc == 0 || mcc == -1 || mnc == 0 || mnc == -1 || cid == 0 || cid == -1) {
			if (MainService.DEBUG)
				Log.w(TAG, "gsm cell " + mcc + "/" + mnc + "/" + cid + " is invalid");
			return null;
		}
		final Location result = renameSource(gsmMap.get(mcc, mnc, cid));
		if (result == null) {
			if (MainService.DEBUG)
				Log.i(TAG, "gsm cell " + mcc + "/" + mnc + "/" + cid + " is not in database");
			return readCellLocationFromDatabaseFile(mcc, mnc, lac, cid);
		}
		return result;
	}

	private Location getLocation(String operator, int lac, int cid) {
		if (operator == null || operator.length() < 3) {
			if (MainService.DEBUG)
				Log.w(TAG, "Not connected to any gsm cell - won't track location...");
			return null;
		}
		return getLocation(operator.substring(0, 3), operator.substring(3), lac, cid);
	}

	private Location getLocation(String mcc, String mnc, int lac, int cid) {
		return getLocation(Integer.parseInt(mcc), Integer.parseInt(mnc), lac, cid);
	}

	private Location readCellLocationFromDatabaseFile(int mcc, int mnc, int lac, int cid) {
		if (!source.isSourceAvailable())
			return null;
		//source.requestCellLocation(mcc, mnc, cid, lac, gsmMap);
		return renameSource(gsmMap.get(mcc, mnc, cid));
	}

}
