package com.app.datadistribution.service.interfaces;

import java.util.List;

public interface ILocationService {
    List<String> getStates();
    List<String> getCitiesByState(String state);
    boolean isValidStateAndCity(String state, String city);
    boolean isValidState(String state);
}
