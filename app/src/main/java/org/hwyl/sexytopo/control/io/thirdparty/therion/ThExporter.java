package org.hwyl.sexytopo.control.io.thirdparty.therion;

import org.hwyl.sexytopo.control.io.thirdparty.survex.SurvexExporter;
import org.hwyl.sexytopo.control.util.TextTools;
import org.hwyl.sexytopo.model.graph.Direction;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;

import java.util.ArrayList;
import java.util.List;


public class ThExporter {

    public static String getContent(Survey survey, List<String> th2Files) {

        String text =
            TherionExporter.getEncodingText() + "\n" +
            getSurveyText(survey, th2Files) + "\n";

        return text;
    }

    public static String updateOriginalContent(
            Survey survey, String originalFileContent, List<String> th2Files) {
        String centrelineText = getCentrelineText(survey);
        String newContent = replaceCentreline(originalFileContent, centrelineText);

        String inputText = getInputText(th2Files);
        newContent = replaceInputsText(newContent, inputText);

        return newContent;
    }

    public static String replaceCentreline(String original, String replacementText) {
        String newContent = original.replaceFirst(
                "(?s)(\\s*?(centreline|centerline)(.*)(endcentreline|endcenterline)\\s*)",
                replacementText);
        return newContent;
    }

    public static String replaceInputsText(String original, String replacementText) {
        String newContent = original.replaceFirst("(?m)(^input .*\\n)+", replacementText);
        return newContent;
    }

    private static String getSurveyText(Survey survey, List<String> th2Files) {

        String surveyText =
            "survey " + survey.getName() + "\n\n" +
            getInputText(th2Files) + "\n\n" +
            indent(getCentrelineText(survey)) +
            "\n\nendsurvey";
        return surveyText;
    }

    private static String getInputText(List<String> th2Files) {
        List<String> lines = new ArrayList<>();
        for (String filename: th2Files) {
            lines.add("input \"" + filename + "\"");
        }
        return TextTools.join("\n", lines);
    }

    private static String getCentrelineText(Survey survey) {
        String centrelineText =
            "\ncentreline\n" +
            indent(getCentreline(survey)) + "\n\n" +
            indent(getExtendedElevationExtensions(survey)) + "\n\n" +
            "endcentreline\n";
        return centrelineText;
    }

    public static String indent(String text) {
        String indented = "";

        if (text.trim().equals("")) {
            return "";
        }

        String[] lines = text.split("\n");
        for (String line : lines) {
            indented += "\t" + line + "\n";
        }
        return indented;
    }

    private static String getCentreline(Survey survey) {
        return "data normal from to length compass clino\n\n" +
            new SurvexExporter().getContent(survey);
    }


    private static String getExtendedElevationExtensions(Survey survey) {
        StringBuilder builder = new StringBuilder();
        generateExtendCommandsFromStation(builder, survey.getOrigin(), null);
        return builder.toString();
    }

    private static void generateExtendCommandsFromStation(
            StringBuilder builder, Station station, Direction lastDirection) {

        Direction currentDirection = station.getExtendedElevationDirection();
        if (lastDirection == null) {
            builder.append(getExtendCommand(station, "start"));
        } else if (currentDirection != lastDirection) {
            builder.append(getExtendCommand(station, currentDirection.name().toLowerCase()));
        }

        for (Leg leg : station.getConnectedOnwardLegs()) {
            generateExtendCommandsFromStation(
                    builder, leg.getDestination(), station.getExtendedElevationDirection());
        }
    }

    private static String getExtendCommand(Station station, String direction) {
        return "extend " + direction + " " + station.getName() + "\n";
    }

}
