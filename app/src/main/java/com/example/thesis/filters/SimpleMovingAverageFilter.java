package com.example.thesis.filters;

import java.util.ArrayList;

public class SimpleMovingAverageFilter {

    public SimpleMovingAverageFilter() {

    }

    public ArrayList<Double> filterData(double[] data) {
        ArrayList<Double> filteredData = new ArrayList<Double>();
        int n = 3;
        for(int i = 0; i < data.length; i++) {
            if(i+1 < n) {
                continue;
            }
            double newRSSI = ((double) 1 /n)*(data[i-2]+data[i-1]+data[i]);
            filteredData.add(newRSSI);
        }
        return filteredData;
    }
}
