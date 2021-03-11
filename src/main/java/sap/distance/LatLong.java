package sap.distance;

import java.util.Objects;

/**
 * Geographic location.
 */
public class LatLong {
    private final float latitude;
    private final float longitude;

    public LatLong(float latitude, float longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Auto-generated.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LatLong latLong = (LatLong) o;
        return Float.compare(latLong.latitude, latitude) == 0 && Float.compare(latLong.longitude, longitude) == 0;
    }

    /**
     * Auto-generated.
     */
    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }
}