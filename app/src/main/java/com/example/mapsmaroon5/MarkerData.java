package com.example.mapsmaroon5;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "markers")
public class MarkerData {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public double latitude;
    public double longitude;
    public String title;
    public String imageBase64;
    public boolean isSynced;

    public MarkerData() {
    }

    public MarkerData(double latitude, double longitude, String title, String imageBase64) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.title = title;
        this.imageBase64 = imageBase64;
        this.isSynced = false;
    }
}
