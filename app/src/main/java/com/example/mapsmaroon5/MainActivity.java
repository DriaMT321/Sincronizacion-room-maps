package com.example.mapsmaroon5;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import android.location.Location;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    private FusedLocationProviderClient fusedLocationClient;
    private GoogleMap mymap;
    private DatabaseReference databaseReference;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int PICK_IMAGE_REQUEST = 2;
    private LatLng selectedLocation;
    private String markerTitle;
    private AppDatabase database;
    private Polyline lastPolyline;

    private DirectionsApiService directionsApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupRetrofit();

        databaseReference = FirebaseDatabase.getInstance().getReference("markers");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        database = AppDatabase.getDatabase(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        if (isNetworkAvailable()) {
            loadMarkersFromFirebase();
        } else {
            loadMarkersFromRoom();
        }


    }
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void loadMarkersFromFirebase() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mymap.clear();
                for (DataSnapshot markerSnapshot : snapshot.getChildren()) {
                    MarkerData markerData = markerSnapshot.getValue(MarkerData.class);
                    LatLng location = new LatLng(markerData.latitude, markerData.longitude);

                    MarkerOptions markerOptions = new MarkerOptions().position(location).title(markerData.title);

                    if (markerData.imageBase64 != null && !markerData.imageBase64.isEmpty()) {
                        byte[] decodedString = Base64.decode(markerData.imageBase64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
                    }

                    mymap.addMarker(markerOptions);
                    CircleOptions circleOptions = new CircleOptions()
                            .center(location)
                            .radius(100) // Radio del círculo en metros (ajústalo según tus necesidades)
                            .strokeWidth(2) // Grosor del borde del círculo
                            .strokeColor(0x550000FF) // Color del borde (ARGB, con transparencia)
                            .fillColor(0x220000FF); // Color de relleno (ARGB, con mayor transparencia)

                    mymap.addCircle(circleOptions);
                    // Guardar en Room
                    new Thread(() -> {
                        MarkerData existingMarker = database.markerDao().getMarkerById(markerSnapshot.getKey());
                        if (existingMarker == null) {
                            database.markerDao().insert(markerData); // Inserta el marcador si no existe
                        }
                    }).start();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error al cargar marcadores", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void setupRetrofit() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        directionsApiService = retrofit.create(DirectionsApiService.class);
    }
    private void loadMarkersFromRoom() {
        new Thread(() -> {
            List<MarkerData> markerList = database.markerDao().getAllMarkers();
            runOnUiThread(() -> {
                mymap.clear();
                for (MarkerData markerData : markerList) {
                    LatLng location = new LatLng(markerData.latitude, markerData.longitude);
                    MarkerOptions markerOptions = new MarkerOptions().position(location).title(markerData.title);

                    if (markerData.imageBase64 != null && !markerData.imageBase64.isEmpty()) {
                        byte[] decodedString = Base64.decode(markerData.imageBase64, Base64.DEFAULT);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                        // Redondea los bordes del bitmap
                        Bitmap roundedBitmap = getRoundedCornerBitmap(bitmap);
                        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(roundedBitmap));
                    }


                    mymap.addMarker(markerOptions);
                    CircleOptions circleOptions = new CircleOptions()
                            .center(location)
                            .radius(100) // Radio del círculo en metros (ajústalo según tus necesidades)
                            .strokeWidth(2)
                            .strokeColor(0x550000FF)
                            .fillColor(0x220000FF);

                    mymap.addCircle(circleOptions);
                }
            });
        }).start();
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng((lat / 1E5), (lng / 1E5)));
        }
        return poly;
    }

    private void drawRouteToMarker(LatLng destination) {
        if (lastPolyline != null) {
            lastPolyline.remove();
        }
        // Asegúrate de que tienes la ubicación actual del usuario
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                LatLng origin = new LatLng(location.getLatitude(), location.getLongitude());
                String originStr = origin.latitude + "," + origin.longitude;
                String destinationStr = destination.latitude + "," + destination.longitude;

                directionsApiService.getDirections(originStr, destinationStr, getString(R.string.api_key))
                        .enqueue(new retrofit2.Callback<DirectionsResponse>() {
                            @Override
                            public void onResponse(Call<DirectionsResponse> call, retrofit2.Response<DirectionsResponse> response) {
                                if (response.isSuccessful() && response.body() != null) {
                                    List<DirectionsResponse.Route> routes = response.body().routes;
                                    if (!routes.isEmpty()) {
                                        List<DirectionsResponse.Leg> legs = routes.get(0).legs;
                                        List<LatLng> path = new ArrayList<>();
                                        for (DirectionsResponse.Leg leg : legs) {
                                            for (DirectionsResponse.Step step : leg.steps) {
                                                List<LatLng> points = decodePolyline(step.polyline.points);
                                                path.addAll(points);
                                            }
                                        }
                                        lastPolyline = mymap.addPolyline(new PolylineOptions().addAll(path).width(10).color(0x550000FF)); // Ajusta el color y ancho de la línea
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                                Toast.makeText(MainActivity.this, "Error al obtener la ruta", Toast.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toast.makeText(MainActivity.this, "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);
        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, width, height);
        final RectF rectF = new RectF(rect);
        final float roundPx = width / 2; // Radio para bordes redondeados

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    public void syncUnsyncedMarkers() {
        List<MarkerData> unsyncedMarkers = database.markerDao().getUnsyncedMarkers();
        for (MarkerData marker : unsyncedMarkers) {
            DatabaseReference markerRef = databaseReference.push();
            markerRef.setValue(marker).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    marker.isSynced = true;
                    database.markerDao().update(marker);
                }
            });
        }
    }

    private void showMarkerDialog(final LatLng latLng) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Registrar ubicación");

        final EditText input = new EditText(this);
        input.setHint("Nombre del lugar");
        builder.setView(input);

        builder.setPositiveButton("Seleccionar Foto", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                markerTitle = input.getText().toString();
                selectedLocation = latLng;
                if (!markerTitle.isEmpty()) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, PICK_IMAGE_REQUEST);
                } else {
                    Toast.makeText(MainActivity.this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void centerMapOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                            mymap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
                        } else {
                            Toast.makeText(MainActivity.this, "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mymap = googleMap;
        mymap.setOnMarkerClickListener(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mymap.setMyLocationEnabled(true);
            centerMapOnUserLocation();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }

        mymap.setOnMapLongClickListener(latLng -> showMarkerDialog(latLng));

        // Configurar un InfoWindowAdapter personalizado para mostrar el título
        mymap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return null; // Usa el contenido predeterminado de InfoWindow
            }

            @Override
            public View getInfoContents(Marker marker) {
                View infoWindow = LayoutInflater.from(MainActivity.this).inflate(R.layout.custom_info_window, null);

                TextView title = infoWindow.findViewById(R.id.info_window_title);
                title.setText(marker.getTitle());

                return infoWindow;
            }
        });

        mymap.setOnMapLongClickListener(latLng -> showMarkerDialog(latLng));
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mymap.setMyLocationEnabled(true);
                    centerMapOnUserLocation();
                }
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLocationUpdates() {
        // Código para iniciar actualizaciones de ubicación si es necesario
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                String imageBase64 = encodeImageToBase64(bitmap);
                saveMarkerWithImage(selectedLocation, markerTitle, imageBase64);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error al procesar imagen", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String encodeImageToBase64(Bitmap bitmap) {
        // Redimensionar la imagen a un tamaño fijo (por ejemplo, 100x100 píxeles)
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos); // Reducir calidad para tamaño manejable
        byte[] byteArray = baos.toByteArray();

        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }


    private void saveMarkerWithImage(LatLng location, String title, String imageBase64) {
        String markerId = databaseReference.push().getKey();
        MarkerData markerData = new MarkerData(location.latitude, location.longitude, title, imageBase64);
        databaseReference.child(markerId).setValue(markerData);
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        marker.showInfoWindow();
        drawRouteToMarker(marker.getPosition()); // Trazar la ruta al marcador
        return true;
    }
}
