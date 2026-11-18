package com.novapetzlife.delivery;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class HaversineDistanceCalculator {

    // WGS-84 reference ellipsoid parameters
    private static final double WGS84_A = 6378137.0; // semi-major axis in meters
    private static final double WGS84_B = 6356752.314245; // semi-minor axis in meters
    private static final double WGS84_F = 1.0 / 298.257223563; // flattening
    private static final double EARTH_RADIUS_KM = 6371.0;

    /**
     * Calculates high-precision geodesic straight-line distance between two geographic coordinates
     * using the WGS-84 Vincenty inverse formula with fallback to Haversine.
     *
     * @param lat1 Latitude of point 1 in degrees
     * @param lon1 Longitude of point 1 in degrees
     * @param lat2 Latitude of point 2 in degrees
     * @param lon2 Longitude of point 2 in degrees
     * @return Straight-line geodesic distance in kilometers, rounded to 2 decimal places
     */
    public BigDecimal calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double distKm = calculateVincentyDistanceKm(lat1, lon1, lat2, lon2);
        return BigDecimal.valueOf(distKm).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * High-precision Vincenty inverse geodesic calculation on the WGS-84 reference ellipsoid.
     */
    public double calculateVincentyDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        if (Double.compare(lat1, lat2) == 0 && Double.compare(lon1, lon2) == 0) {
            return 0.0;
        }

        double L = Math.toRadians(lon2 - lon1);
        double U1 = Math.atan((1.0 - WGS84_F) * Math.tan(Math.toRadians(lat1)));
        double U2 = Math.atan((1.0 - WGS84_F) * Math.tan(Math.toRadians(lat2)));
        double sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
        double sinU2 = Math.sin(U2), cosU2 = Math.cos(U2);

        double lambda = L;
        double lambdaP;
        int iterLimit = 100;
        double cosSqAlpha = 0;
        double sinSigma = 0;
        double cos2SigmaM = 0;
        double cosSigma = 0;
        double sigma = 0;

        do {
            double sinLambda = Math.sin(lambda);
            double cosLambda = Math.cos(lambda);
            sinSigma = Math.sqrt((cosU2 * sinLambda) * (cosU2 * sinLambda)
                    + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda) * (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
            if (sinSigma == 0) {
                return 0.0;
            }
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
            cos2SigmaM = (cosSqAlpha != 0) ? (cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha) : 0;
            double C = WGS84_F / 16.0 * cosSqAlpha * (4.0 + WGS84_F * (4.0 - 3.0 * cosSqAlpha));
            lambdaP = lambda;
            lambda = L + (1.0 - C) * WGS84_F * sinAlpha *
                    (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)));
        } while (Math.abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0);

        if (iterLimit == 0) {
            return calculateHaversineFallback(lat1, lon1, lat2, lon2);
        }

        double uSq = cosSqAlpha * (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_B * WGS84_B);
        double A = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)));
        double B = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)));
        double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4.0 * (cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
                B / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)));

        double distanceMeters = WGS84_B * A * (sigma - deltaSigma);
        return distanceMeters / 1000.0;
    }

    /**
     * Estimates actual urban road network distance based on the Thiruvananthapuram road network circuity index.
     * Urban road networks require vehicles to follow arterial and collector roads, typically spanning 1.20x to 1.28x
     * of straight-line distance.
     *
     * @param straightLineKm Straight-line geodesic distance in km
     * @return Estimated road travel distance in km, rounded to 2 decimal places
     */
    public BigDecimal calculateEstimatedRoadDistanceKm(double straightLineKm) {
        if (straightLineKm <= 0.0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        double factor;
        if (straightLineKm <= 3.0) {
            factor = 1.20;
        } else if (straightLineKm <= 10.0) {
            factor = 1.24;
        } else {
            factor = 1.28;
        }
        double roadKm = straightLineKm * factor;
        return BigDecimal.valueOf(roadKm).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Estimates urban delivery transit duration in minutes based on real-world local dispatch speeds (24 km/h)
     * and a 5-minute handling/dispatch window.
     *
     * @param roadDistanceKm Road network distance in km
     * @return Estimated transit time in minutes
     */
    public int calculateEstimatedDurationMinutes(double roadDistanceKm) {
        if (roadDistanceKm <= 0.0) {
            return 5;
        }
        double travelMinutes = (roadDistanceKm / 24.0) * 60.0;
        return (int) Math.round(5.0 + travelMinutes);
    }

    private double calculateHaversineFallback(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
