package org.hwyl.sexytopo.model.survey;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by rls on 16/07/14.
 */
public class Station extends SurveyComponent {

    private final String name;
    private List<Leg> onwardLegs = new ArrayList<>();

    // special flag for switching drawing direction in extended elevation mode
    private boolean isSwitchback = false;

    public Station(String name) {
        this.name = name;
    }

    public Station(Station station, String name) {
        this.name = name;
        this.onwardLegs = station.onwardLegs;
    }

    public String getName() {
        return name;
    }

    public List<Leg> getOnwardLegs() {
        return onwardLegs;
    }

    public void addOnwardLeg(Leg leg) {
        onwardLegs.add(leg);
    }

    public List<Leg> getUnconnectedOnwardLegs() {
        List<Leg> unconnectedOnwardLegs = new ArrayList<>();
        for (Leg leg : onwardLegs) {
            if (!leg.hasDestination()) {
                unconnectedOnwardLegs.add(leg);
            }
        }
        return unconnectedOnwardLegs;
    }

    public List<Leg> getConnectedOnwardLegs() {
        List<Leg> connectedOnwardLegs = new ArrayList<>();
        for (Leg leg : onwardLegs) {
            if (leg.hasDestination()) {
                connectedOnwardLegs.add(leg);
            }
        }
        return connectedOnwardLegs;
    }

    public boolean isSwitchback() {
        return isSwitchback;
    }

    public void setSwitchback(boolean isSwitchback) {
        this.isSwitchback = isSwitchback;
    }

    public String toString() {
        return name;
    }
}
