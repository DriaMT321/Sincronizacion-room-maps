package com.example.mapsmaroon5;
import java.util.List;

public class DirectionsResponse {
    public List<Route> routes;

    public static class Route {
        public List<Leg> legs;
    }

    public static class Leg {
        public List<Step> steps;
    }

    public static class Step {
        public Polyline polyline;
    }

    public static class Polyline {
        public String points;
    }
}
