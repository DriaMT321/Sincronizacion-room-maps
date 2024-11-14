package com.example.mapsmaroon5;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface MarkerDao {
    @Insert
    void insert(MarkerData marker);

    @Query("SELECT * FROM markers")
    List<MarkerData> getAllMarkers();

    @Query("SELECT * FROM markers WHERE id = :id LIMIT 1")
    MarkerData getMarkerById(String id);

    @Query("SELECT * FROM markers WHERE isSynced = 0")
    List<MarkerData> getUnsyncedMarkers();

    @Update
    void update(MarkerData marker);
}