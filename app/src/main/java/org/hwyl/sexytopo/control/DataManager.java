package org.hwyl.sexytopo.control;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import org.hwyl.sexytopo.SexyTopo;
import org.hwyl.sexytopo.control.io.basic.Saver;
import org.hwyl.sexytopo.control.util.PreferenceAccess;
import org.hwyl.sexytopo.control.util.SurveyUpdater;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.model.calibration.CalibrationReading;

import java.util.ArrayList;
import java.util.List;


public class DataManager {

    private static DataManager instance;
    public static synchronized DataManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataManager(context);
        }
        return instance;
    }

    private final Context context;

    private LocalBroadcastManager broadcastManager;

    // This should be created or loaded on startup
    private Survey currentSurvey = new Survey("Unnamed");

    private List<CalibrationReading> calibrationReadings =
            new ArrayList<CalibrationReading>();

    private DataManager(Context context) {
        this.context = context;
    }

    public void updateSurvey(Leg leg) {
        List<Leg> legList = new ArrayList<>();
        legList.add(leg);
        updateSurvey(legList);

    }

    public void updateSurvey(List<Leg> legs) {

        if (legs.size() > 0) {

            SharedPreferences preferences =
                    context.getSharedPreferences(SexyTopo.GENERAL_PREFS, 0);
            boolean reverseLegs =
                    preferences.getBoolean(SexyTopo.REVERSE_MEASUREMENTS_PREFERENCE, false);
            if (reverseLegs) {
                legs = reverseLegs(legs);
            }

            boolean automaticBacksightPromotion = PreferenceAccess.getBoolean(
                    context, "pref_backsight_promotion", false);

            boolean stationAdded =
                    SurveyUpdater.update(currentSurvey, legs, automaticBacksightPromotion);

            if (stationAdded) {
                broadcastNewStationCreated();
            }

            try {
                Saver.autosave(context, currentSurvey);
            } catch (Exception e) {
                Log.e(SexyTopo.TAG, "Error autosaving survey: " + e);
                Toast.makeText(context, "Error autosaving survey: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }

        broadcastSurveyUpdated();
    }

    private List<Leg> reverseLegs(List<Leg> legs) {
        List<Leg> reversed = new ArrayList<Leg>();
        for (Leg leg : legs) {
            reversed.add(leg.reverse());
        }
        return reversed;
    }

    public void broadcastSurveyUpdated() {
        broadcast(new Intent(SexyTopo.SURVEY_UPDATED_EVENT));
    }

    public void broadcastCalibrationUpdated() {
        broadcast(new Intent(SexyTopo.CALIBRATION_UPDATED_EVENT));
    }


    public void broadcastNewStationCreated() {
        broadcast(new Intent(SexyTopo.NEW_STATION_CREATED_EVENT));
    }

    private void broadcast(Intent intent) {
        if (broadcastManager == null) {
            broadcastManager = LocalBroadcastManager.getInstance(context);
        }
        broadcastManager.sendBroadcast(intent);
    }

    public Survey getCurrentSurvey() {
        return currentSurvey;
    }

    public void setCurrentSurvey(Survey currentSurvey) {
        this.currentSurvey = currentSurvey;
        broadcastSurveyUpdated();
    }

    public void addCalibrationReading(CalibrationReading calibrationReading) {
        this.calibrationReadings.add(calibrationReading);
        broadcastCalibrationUpdated();
    }

    public List<CalibrationReading> getCalibrationReadings() {
        return calibrationReadings;
    }

    public void clearCalibrationReadings() {
        this.calibrationReadings.clear();
    }
}
