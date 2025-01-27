package org.hwyl.sexytopo.control.graph;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.PopupWindow;

import org.hwyl.sexytopo.R;
import org.hwyl.sexytopo.control.SurveyManager;
import org.hwyl.sexytopo.control.activity.GraphActivity;
import org.hwyl.sexytopo.control.util.CrossSectioner;
import org.hwyl.sexytopo.control.util.PreferenceAccess;
import org.hwyl.sexytopo.control.util.Space2DUtils;
import org.hwyl.sexytopo.control.util.SpaceFlipper;
import org.hwyl.sexytopo.control.util.SurveyStats;
import org.hwyl.sexytopo.control.util.SurveyUpdater;
import org.hwyl.sexytopo.control.util.TextTools;
import org.hwyl.sexytopo.model.graph.Coord2D;
import org.hwyl.sexytopo.model.graph.Direction;
import org.hwyl.sexytopo.model.graph.Line;
import org.hwyl.sexytopo.model.graph.Projection2D;
import org.hwyl.sexytopo.model.graph.Space;
import org.hwyl.sexytopo.model.sketch.Colour;
import org.hwyl.sexytopo.model.sketch.CrossSection;
import org.hwyl.sexytopo.model.sketch.CrossSectionDetail;
import org.hwyl.sexytopo.model.sketch.PathDetail;
import org.hwyl.sexytopo.model.sketch.Sketch;
import org.hwyl.sexytopo.model.sketch.SketchDetail;
import org.hwyl.sexytopo.model.sketch.TextDetail;
import org.hwyl.sexytopo.model.survey.Leg;
import org.hwyl.sexytopo.model.survey.Station;
import org.hwyl.sexytopo.model.survey.Survey;
import org.hwyl.sexytopo.model.survey.SurveyConnection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class GraphView extends View {

    public static boolean DEBUG = false;

    private ScaleGestureDetector scaleGestureDetector;
    private GestureDetector longPressDetector;

    // The offset of the viewing window (what can be seen on the screen) from the whole survey
    private Coord2D viewpointOffset = Coord2D.ORIGIN;

    // These variables are used in handling the dragging of the viewing window
    private Coord2D actionDownPointOnView = Coord2D.ORIGIN;
    private Coord2D actionDownViewpointOffset = Coord2D.ORIGIN;

    // ratio of metres on the survey to pixels on the view
    // zooming in increases this, zooming out decreases it
    private double surveyToViewScale = 60.0;

    public static final double MIN_ZOOM = 0.1;
    public static final double MAX_ZOOM = 500.0;

    private GraphActivity activity;

    private Projection2D projectionType;
    private Survey survey;
    private Space<Coord2D> projection;
    private Sketch sketch;

    private Map<Survey, Space<Coord2D>> translatedConnectedSurveys = new HashMap<>();


    public static final Colour LEG_COLOUR = Colour.RED;
	public static final Colour SPLAY_COLOUR = Colour.PINK;
    public static final Colour LATEST_LEG_COLOUR = Colour.MAGENTA;
    public static final Colour HIGHLIGHT_COLOUR = Colour.GOLD;
    public static final Colour DEFAULT_SKETCH_COLOUR = Colour.BLACK;
    public static final Colour CROSS_SECTION_CONNECTION_COLOUR = Colour.SILVER;


    public static final int STATION_COLOUR = Colour.DARK_RED.intValue;
    public static final int STATION_DIAMETER = 8;
    public static final int CROSS_DIAMETER = 16;
    public static final int STATION_STROKE_WIDTH = 5;
    public static final int HIGHLIGHT_OUTLINE = 4;

    public static final int LEGEND_SIZE = 18;
    public static final Colour LEGEND_COLOUR = Colour.BLACK;
    public static final Colour GRID_COLOUR = Colour.LIGHT_GREY;

    public static final double DELETE_PATHS_WITHIN_N_PIXELS = 5.0;
    public static final double SELECTION_SENSITIVITY_IN_PIXELS = 25.0;
    public static final double SNAP_TO_LINE_SENSITIVITY_IN_PIXELS = 25.0;


    public static final int STATION_LABEL_OFFSET = 10;

    private Bitmap commentIcon, linkIcon;



    public enum BrushColour {

        BLACK(R.id.buttonBlack, Colour.BLACK),
        BROWN(R.id.buttonBrown, Colour.BROWN),
        ORANGE(R.id.buttonOrange, Colour.ORANGE),
        GREEN(R.id.buttonGreen, Colour.GREEN),
        BLUE(R.id.buttonBlue, Colour.BLUE),
        PURPLE(R.id.buttonPurple, Colour.PURPLE);

        private final int id;
        private final Colour colour;
        BrushColour(int id, Colour colour) {
            this.id = id;
            this.colour = colour;
        }

        public int getId() {
            return id;
        }
    }

    public enum SketchTool {
        MOVE(R.id.buttonMove),
        DRAW(R.id.buttonDraw, true),
        ERASE(R.id.buttonErase),
        TEXT(R.id.buttonText, true),
        SELECT(R.id.buttonSelect),
        POSITION_CROSS_SECTION(R.id.graph_station_new_cross_section),
        PINCH_TO_ZOOM(-1);

        private int id;
        private boolean usesColour = false;

        SketchTool(int id) {
            this.id = id;
        }

        SketchTool(int id, boolean usesColour) {
            this.id = id;
            this.usesColour = usesColour;
        }

        public int getId() {
            return id;
        }

        public boolean usesColour() {
            return usesColour;
        }
    }
    public SketchTool currentSketchTool = SketchTool.MOVE;
    // used to jump back to the previous tool when using one-use tools
    private SketchTool previousSketchTool = SketchTool.SELECT;

    private Paint stationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint legPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint gridPaint = new Paint();
    private Paint crossSectionConnectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);



    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        longPressDetector = new GestureDetector(context, new LongPressListener());
        initialisePaint();
    }


    public void initialisePaint() {

        stationPaint.setColor(STATION_COLOUR);
        stationPaint.setStrokeWidth(STATION_STROKE_WIDTH);
        int labelSize = PreferenceAccess.getInt(getContext(), "pref_station_label_font_size", 22);
        stationPaint.setTextSize(labelSize);

        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(HIGHLIGHT_OUTLINE);
        highlightPaint.setColor(HIGHLIGHT_COLOUR.intValue);

        legPaint.setARGB(127, 255, 0, 0);
        int legStrokeWidth = PreferenceAccess.getInt(getContext(), "pref_leg_width", 3);
        legPaint.setStrokeWidth(legStrokeWidth);
        legPaint.setColor(LEG_COLOUR.intValue);

        gridPaint.setColor(GRID_COLOUR.intValue);

        drawPaint.setColor(DEFAULT_SKETCH_COLOUR.intValue);
        drawPaint.setStrokeWidth(3);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        legendPaint.setColor(LEGEND_COLOUR.intValue);
        legendPaint.setTextSize(LEGEND_SIZE);

        labelPaint.setColor(STATION_COLOUR);
        int textSize = PreferenceAccess.getInt(getContext(), "pref_survey_text_font_size", 32);
        labelPaint.setTextSize(textSize);

        crossSectionConnectorPaint.setColor(CROSS_SECTION_CONNECTION_COLOUR.intValue);
        crossSectionConnectorPaint.setStrokeWidth(3);
        crossSectionConnectorPaint.setStyle(Paint.Style.STROKE);
        crossSectionConnectorPaint.setPathEffect(new DashPathEffect(new float[]{3, 2}, 0));

        commentIcon = BitmapFactory.decodeResource(getResources(), R.drawable.speech_bubble);
        linkIcon = BitmapFactory.decodeResource(getResources(), R.drawable.link);
    }


    public void setActivity(GraphActivity graphActivity) {
        this.activity = graphActivity;
    }


    public void setSurvey(Survey survey) {
        if (survey != this.survey) {
            this.survey = survey;
            centreViewOnActiveStation();

        }
    }

    public void setProjectionType(Projection2D projectionType) {
        this.projectionType = projectionType;
    }

    public void setProjection(Space<Coord2D> projection) {
        // We're going to flip the projection vertically because we want North at the top and
        // the survey is recorded assuming that that is the +ve axis. However, on the
        // screen this is reversed: DOWN is the +ve access. I think this makes sense...
        // we just have to remember to reverse the flip when exporting the sketch :)
        this.projection = SpaceFlipper.flipVertically(projection);
    }


    public void setSketch(Sketch sketch) {
        this.sketch = sketch;
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {

        scaleGestureDetector.onTouchEvent(event);
        longPressDetector.onTouchEvent(event);

        if (scaleGestureDetector.isInProgress()) {
            return true;
        } else {

        }

        if (currentSketchTool == SketchTool.PINCH_TO_ZOOM) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                setSketchTool(previousSketchTool);
            }
            return true;
        }

        switch (currentSketchTool) {
            case MOVE:
                return handleMove(event);
            case DRAW:
                return handleDraw(event);
            case ERASE:
                return handleErase(event);
            case TEXT:
                return handleText(event);
            case SELECT:
                return handleSelect(event);
            case POSITION_CROSS_SECTION:
                return handlePositionCrossSection(event);
        }
        return false;
    }


    private Coord2D viewCoordsToSurveyCoords(Coord2D coords) {
        // The more elegant way to do this is:
        // return coords.scale(1 / surveyToViewScale).plus(viewpointOffset);
        // ...but this method gets hit hard (profiled) so let's avoid creating intermediate objects:
        return new Coord2D(((coords.getX() * (1 / surveyToViewScale)) + viewpointOffset.getX()),
                ((coords.getY() * (1 / surveyToViewScale)) + viewpointOffset.getY()));
    }


    private Coord2D surveyCoordsToViewCoords(Coord2D coords) {
        // The more elegant way to do this is:
        // return coords.minus(viewpointOffset).scale(surveyToViewScale);
        // ...but this method gets hit hard (profiled) so let's avoid creating intermediate objects:
        return new Coord2D(((coords.getX() - viewpointOffset.getX()) * surveyToViewScale),
                ((coords.getY() - viewpointOffset.getY()) * surveyToViewScale));
    }


    private boolean handleDraw(MotionEvent event) {

        Coord2D touchPointOnView = new Coord2D(event.getX(), event.getY());
        Coord2D surveyCoords = viewCoordsToSurveyCoords(touchPointOnView);

        boolean snapToLines = getDisplayPreference(GraphActivity.DisplayPreference.SNAP_TO_LINES);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                actionDownPointOnView = touchPointOnView;
                Coord2D start = surveyCoords;
                if (snapToLines) {
                    Coord2D snappedStart = considerSnapToSketchLine(start);
                    if (snappedStart != null) {
                        start = snappedStart;
                    }
                }
                sketch.startNewPath(start);
                break;

            case MotionEvent.ACTION_MOVE:
                sketch.getActivePath().lineTo(surveyCoords);
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                if (touchPointOnView.equals(actionDownPointOnView)) { // handle dots
                    sketch.getActivePath().lineTo(surveyCoords);
                } else if (snapToLines) {
                    Coord2D snappedEnd = considerSnapToSketchLine(surveyCoords);
                    if (snappedEnd != null) {
                        sketch.getActivePath().lineTo(snappedEnd);
                        invalidate();
                    }
                }
                sketch.finishPath();
                break;

            default:
                return false;
        }

         return true;
    }


    private Coord2D considerSnapToSketchLine(Coord2D pointTouched) {

        double deltaInMetres = SNAP_TO_LINE_SENSITIVITY_IN_PIXELS / surveyToViewScale;

        Coord2D closestPathEnd = sketch.findEligibleSnapPointWithin(pointTouched, deltaInMetres);
        if (closestPathEnd != null) {
            return closestPathEnd;
        } else {
            return null;
        }
    }


    private boolean handleMove(MotionEvent event) {

        Coord2D touchPointOnView = new Coord2D(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                actionDownPointOnView = touchPointOnView;
                actionDownViewpointOffset = viewpointOffset;
                break;
            case MotionEvent.ACTION_MOVE:
                Coord2D surveyDelta =
                        touchPointOnView.minus(actionDownPointOnView).scale(1 / surveyToViewScale);
                viewpointOffset = actionDownViewpointOffset.minus(surveyDelta);
                invalidate();
                // fall through
            case MotionEvent.ACTION_UP:
                break;
            default:
                return false;
        }

        return true;
    }


    private boolean handleErase(MotionEvent event) {

        Coord2D touchPointOnView = new Coord2D(event.getX(), event.getY());
        Coord2D touchPointOnSurvey = viewCoordsToSurveyCoords(touchPointOnView);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                /*SketchDetail closestDetail =
                        sketch.findNearestPathWithin(sketch.getPathDetails(),
                                touchPointOnSurvey, DELETE_PATHS_WITHIN_N_PIXELS);*/
                SketchDetail closestDetail = sketch.findNearestDetailWithin(
                        touchPointOnSurvey, DELETE_PATHS_WITHIN_N_PIXELS);
                if (closestDetail != null) {
                    sketch.deleteDetail(closestDetail);
                    invalidate();
                }
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                break;
            default:
                return false;
        }

        return true;
    }


    private boolean handleText(MotionEvent event) {

        final Coord2D touchPointOnView = new Coord2D(event.getX(), event.getY());
        final Coord2D touchPointOnSurvey = viewCoordsToSurveyCoords(touchPointOnView);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                final EditText input = new EditText(getContext());
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                builder.setView(input)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                sketch.addTextDetail(touchPointOnSurvey, input.getText().toString());
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // do nothing
                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                dialog.show();
                return true;
            default:
                return false;
        }
    }



    private boolean handleSelect(MotionEvent event) {

        Coord2D touchPointOnView = new Coord2D(event.getX(), event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                Station newSelectedStation = checkForStation(touchPointOnView);

                if (newSelectedStation == null) {
                    return false;

                } else if (newSelectedStation != survey.getActiveStation()) {
                    survey.setActiveStation(newSelectedStation);
                    invalidate();
                    return true;

                } else { // double selection opens context menu
                    showContextMenu(event, newSelectedStation);
                }

            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                break;
            default:
                return false;
        }

        return true;
    }


    private Station checkForStation(Coord2D touchPointOnView) {

        double selectionTolerance =
                SELECTION_SENSITIVITY_IN_PIXELS / surveyToViewScale;

        Coord2D touchPointOnSurvey = viewCoordsToSurveyCoords(touchPointOnView);

        Station newSelectedStation = findNearestStationWithinDelta(projection,
                touchPointOnSurvey, selectionTolerance);

        return newSelectedStation; // this could be null if nothing is near
    }


    private boolean handlePositionCrossSection(MotionEvent event) {

        Coord2D touchPointOnView = new Coord2D(event.getX(), event.getY());
        Coord2D touchPointOnSurvey = viewCoordsToSurveyCoords(touchPointOnView);

        final Station station = survey.getActiveStation();
        CrossSection crossSection = CrossSectioner.section(survey, station);

        sketch.addCrossSection(crossSection, touchPointOnSurvey);

        setSketchTool(previousSketchTool);
        invalidate();

        return true;
    }


    private void showContextMenu(MotionEvent event, final Station station) {

        OnClickListener listener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                switch(view.getId()) {
                    case R.id.graph_station_select:
                        survey.setActiveStation(station);
                        invalidate();
                        break;
                    case R.id.graph_station_toggle_left_right:
                        Direction newDirection = station.getExtendedElevationDirection().opposite();
                        SurveyUpdater.setDirectionOfSubtree(survey, station,newDirection);
                        SurveyManager.getInstance(getContext()).broadcastSurveyUpdated();
                        invalidate();
                        break;
                    case R.id.graph_station_comment:
                        openCommentDialog(station);
                        break;
                    case R.id.graph_station_reverse:
                        SurveyUpdater.reverseLeg(survey, station);
                        SurveyManager.getInstance(getContext()).broadcastSurveyUpdated();
                        invalidate();
                        break;
                    case R.id.graph_station_delete:
                        askAboutDeletingStation(station);
                        invalidate();
                        break;
                    case R.id.graph_station_new_cross_section:
                        setSketchTool(SketchTool.POSITION_CROSS_SECTION);
                        activity.showSimpleToast(R.string.position_cross_section_instruction);
                        break;
                    case R.id.graph_station_start_new_survey:
                        if (!survey.isSaved()) {
                            activity.showSimpleToast(R.string.cannot_extend_unsaved_survey);
                        }
                        activity.continueSurvey(station);
                        break;
                    case R.id.graph_station_unlink_survey:
                        activity.unlinkSurvey(station);
                        break;
                }
            }
        };

        PopupWindow menu = activity.getContextMenu(station, listener);

        View unlinkSurveyButton = menu.getContentView().findViewById(R.id.graph_station_unlink_survey);
        unlinkSurveyButton.setEnabled(survey.hasLinkedSurveys(station));

        View commentButton = menu.getContentView().findViewById(R.id.graph_station_comment);
        commentButton.setEnabled(station != survey.getOrigin());

        menu.showAtLocation(this, Gravity.LEFT | Gravity.TOP,
                (int) (event.getX()), (int) (event.getY()));
    }


    private void openCommentDialog(final Station station) {
        final EditText input = new EditText(getContext());
        input.setLines(8);
        input.setGravity(Gravity.LEFT | Gravity.TOP);
        input.setText(station.getComment());
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(input)
                .setTitle(station.getName())
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        station.setComment(input.getText().toString());
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
    }


    private void askAboutDeletingStation(final Station station) {
        int legsToBeDeleted = SurveyStats.calcNumberSubLegs(station);
        int stationsToBeDeleted = SurveyStats.calcNumberSubStations(station);
        String message = "This will delete\n" +
                TextTools.pluralise(legsToBeDeleted, "leg") +
                " and " + TextTools.pluralise(stationsToBeDeleted, "station");
        new AlertDialog.Builder(getContext())
                .setMessage(message)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        survey.deleteStation(station);
                        SurveyManager.getInstance(getContext()).broadcastSurveyUpdated();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }


    private static Station findNearestStationWithinDelta(Space<Coord2D> space, Coord2D target, double delta) {
        double shortest = Double.MAX_VALUE;
        Station best = null;

        for (Station station : space.getStationMap().keySet()) {
            Coord2D point = space.getStationMap().get(station);
            double distance = Space2DUtils.getDistance(point, target);

            if (distance > delta) {
                continue;
            }

            if (best == null || (distance < shortest)) {
                best = station;
                shortest = distance;
            }
        }

        return best;

    }


    @Override
    protected void onDraw(Canvas canvas) {

        super.onDraw(canvas);

        if (getDisplayPreference(GraphActivity.DisplayPreference.SHOW_GRID)) {
            drawGrid(canvas);
        }

        if (getDisplayPreference(GraphActivity.DisplayPreference.SHOW_CONNECTIONS)) {
            drawConnectedSurveys(canvas, projection, 50);
        }

        drawSurvey(canvas, survey, projection, 255);

        drawLegend(canvas, survey);

        if (DEBUG) {
            drawDebuggingInfo(canvas);
        }
    }

    private void drawSurvey(Canvas canvas, Survey survey, Space<Coord2D> projection, int alpha) {

        drawSketch(canvas, activity.getSketch(survey), alpha);

        drawCrossSections(canvas, sketch.getCrossSectionDetails(), alpha);

        drawSurveyData(survey, canvas, projection, alpha);
    }


    private void drawConnectedSurveys(Canvas canvas, Space<Coord2D> projection, int alpha) {

        if (doTranslatedConnectedSurveysNeedUpdating()) {
            this.translatedConnectedSurveys =
                    ConnectedSurveys.getTranslatedConnectedSurveys(activity, survey, projection);
        }

        for (Survey translatedConnectedSurvey : translatedConnectedSurveys.keySet()) {
            Space<Coord2D> connectedProjection =
                        translatedConnectedSurveys.get(translatedConnectedSurvey);
            drawSurvey(canvas, translatedConnectedSurvey, connectedProjection, alpha);
        }
    }

    private boolean doTranslatedConnectedSurveysNeedUpdating() {
        Set<Survey> flatSetOfConnectedSurveys = getFlatSetOfConnectedSurveys();
        Set<Survey> flatSetOfTranslatedConnectedSurveys = translatedConnectedSurveys.keySet();
        return !flatSetOfConnectedSurveys.equals(flatSetOfTranslatedConnectedSurveys);
    }

    private Set<Survey> getFlatSetOfConnectedSurveys() {
        Set<Survey> flatSet = new HashSet<>();
        for (Set<SurveyConnection> connectionSet : survey.getConnectedSurveys().values()) {
            for (SurveyConnection connection : connectionSet) {
                flatSet.add(connection.otherSurvey);
            }
        }
        return flatSet;
    }


    private void drawGrid(Canvas canvas) {

        // FIXME need a better tick size function when we sort out zooming
        //scale           <-adjustZoomBy out 1  10   20    adjustZoomBy in->
        //inverted                   1  0.1  0.05
        // tick size in m <-adjustZoomBy out 10 1    1     adjustZoomBy in->

        int tickSizeInMetres = getMinorGridBoxSize();
        double boxSize = 10;

        int numberTicksJustBeforeViewpointOffsetX = (int)(viewpointOffset.getX() / tickSizeInMetres);

        for (int n = numberTicksJustBeforeViewpointOffsetX; true; n++) {
            double xSurvey = n * tickSizeInMetres;
            int xView = (int)((xSurvey - viewpointOffset.getX()) * surveyToViewScale);
            gridPaint.setStrokeWidth(n % boxSize == 0 ? 3 : 1);
            canvas.drawLine(xView, 0, xView, getHeight(), gridPaint);
            if (xView >= getWidth()) {
                break;
            }
        }

        int numberTicksJustBeforeViewpointOffsetY = (int)(viewpointOffset.getY() / tickSizeInMetres);

        for (int n = numberTicksJustBeforeViewpointOffsetY; true; n++) {
            double ySurvey = n * tickSizeInMetres;
            int yView = (int)((ySurvey - viewpointOffset.getY()) * surveyToViewScale);
            gridPaint.setStrokeWidth(n % boxSize == 0 ? 3 : 1);
            canvas.drawLine(0, yView, getWidth(), yView, gridPaint);
            if (yView >= getHeight()) {
                break;
            }
        }


    }

    public int getMinorGridBoxSize() {

        if (surveyToViewScale > 15) {
            return 1;
        } else if (surveyToViewScale > 2) {
            return 10;
        } else {
            return 100;
        }
    }


    private void drawSurveyData(Survey survey, Canvas canvas, Space<Coord2D> space, int alpha) {

        drawLegs(canvas, space, alpha);

        drawStations(survey, canvas, space, alpha);
    }


    private void drawCrossSections(Canvas canvas, Set<CrossSectionDetail> crossSectionDetails, int alpha) {
        for (CrossSectionDetail sectionDetail : crossSectionDetails) {
            Coord2D centreOnSurvey = sectionDetail.getPosition();
            Coord2D centreOnView = surveyCoordsToViewCoords(centreOnSurvey);
            drawStationCross(canvas, stationPaint,
                    (float) centreOnView.getX(), (float) centreOnView.getY(),
                    STATION_DIAMETER, alpha);

            String description =
                    sectionDetail.getCrossSection().getStation().getName() + " X";
            if (getDisplayPreference(GraphActivity.DisplayPreference.SHOW_STATION_LABELS)) {
                stationPaint.setAlpha(alpha);
                canvas.drawText(description,
                        (float) centreOnView.getX(),
                        (float) centreOnView.getY(),
                        stationPaint);
            }

            Space<Coord2D> projection = sectionDetail.getProjection();

            drawLegs(canvas, projection, alpha);

            Station station = sectionDetail.getCrossSection().getStation();
            Coord2D surveyStationLocation = this.projection.getStationMap().get(station);
            Coord2D viewStationLocation = surveyCoordsToViewCoords(surveyStationLocation);
            drawLineAsPath(
                    canvas, viewStationLocation, centreOnView, crossSectionConnectorPaint, alpha);
        }
    }


    private void drawLegs(Canvas canvas, Space<Coord2D> space, int alpha) {

        Map<Leg, Line<Coord2D>> legMap = space.getLegMap();

        for (Leg leg : legMap.keySet()) {

            if (!getDisplayPreference(GraphActivity.DisplayPreference.SHOW_SPLAYS) &&
                    !leg.hasDestination()) {
                continue;
            }
            Line<Coord2D> line = legMap.get(leg);

            Coord2D start = surveyCoordsToViewCoords(line.getStart());
            Coord2D end = surveyCoordsToViewCoords(line.getEnd());

            if (PreferenceAccess.getBoolean(getContext(), "pref_key_highlight_latest_leg", true)
                    && survey.getMostRecentLeg() == leg) {
                legPaint.setColor(LATEST_LEG_COLOUR.intValue);
            } else if (!leg.hasDestination()) {
				legPaint.setColor(SPLAY_COLOUR.intValue);
			} else {
				legPaint.setColor(LEG_COLOUR.intValue);
			}

			if (projectionType.isLegInPlane(leg)) {
                legPaint.setStyle(Paint.Style.STROKE);
                drawLine(canvas, start, end, legPaint, alpha);
			} else {
                legPaint.setPathEffect(new DashPathEffect(new float[]{3, 2}, 0));
                drawLineAsPath(canvas, start, end, legPaint, alpha);
			}

        }

    }


    private void drawStations(Survey survey, Canvas canvas, Space<Coord2D> space, int alpha) {

        stationPaint.setAlpha(alpha);

        int crossDiameter =
                PreferenceAccess.getInt(this.getContext(), "pref_station_diameter", CROSS_DIAMETER);

        for (Map.Entry<Station, Coord2D> entry : space.getStationMap().entrySet()) {
            Station station = entry.getKey();
            Coord2D translatedStation = surveyCoordsToViewCoords(entry.getValue());

            int x = (int)(translatedStation.getX());
            int y = (int)(translatedStation.getY());

            drawStationCross(canvas, stationPaint, x, y, crossDiameter, alpha);

            if (station == survey.getActiveStation()) {
                highlightActiveStation(canvas, x, y);
            }

            int spacing = crossDiameter / 2;
            int nextX = x + crossDiameter;

            if (getDisplayPreference(GraphActivity.DisplayPreference.SHOW_STATION_LABELS)) {
                String name = station.getName();
                if (station == survey.getOrigin()) {
                    name = name + " (" + survey.getName() + ")";
                }
                canvas.drawText(name,
                        nextX,
                        y + STATION_LABEL_OFFSET,
                        stationPaint);
                nextX += stationPaint.measureText(name) + spacing;
            }

            List<Bitmap> icons = new LinkedList<>();
            if (station.hasComment()) {
                icons.add(commentIcon);
            }
            if (survey.hasLinkedSurveys(station)) {
                icons.add(linkIcon);
            }

            for (Bitmap icon : icons) {int yTop = y - crossDiameter / 2;
                Rect rect = new Rect(nextX, yTop, nextX + crossDiameter, yTop + crossDiameter);
                canvas.drawBitmap(icon, null, rect, stationPaint);
                nextX += crossDiameter + spacing;
            }

            CrossSection crossSection = sketch.getCrossSection(station);
            if (crossSection != null) {
                /*
                canvas.drawText("a" + crossSection.getAngle(), x + 100, y, stationPaint);
                float angle = (float)(crossSection.getAngle());
                float width = 50;
                float startX = x - ((width / 2) * (float)Math.cos(angle));
                float startY = y - ((width / 2) * (float)Math.sin(angle));
                float endX = x + ((width / 2) * (float)Math.cos(angle));
                float endY = y + ((width / 2) * (float)Math.sin(angle));

                canvas.drawLine(startX, startY, endX, endY, stationPaint);


                float tickAngle = (float)Space2DUtils.adjustAngle(angle, 90);
                float endTickOneX = startX + (20 * (float)Math.cos(tickAngle));
                float endTickOneY = startY + (20 * (float)Math.sin(tickAngle));

                canvas.drawLine(startX, startY, endTickOneX, endTickOneY, stationPaint);
                */

            }
        }
    }


    private void highlightActiveStation(Canvas canvas, float x, float y) {

        int diameter = 22;
        int gap = 6;
        float topY = y - (diameter / 2);
        float bottomY = y + (diameter / 2);
        float leftX = x - (diameter / 2);
        float rightX = x + (diameter / 2);

        float innerLeft = leftX + ((diameter - gap) / 2);
        float innerRight = innerLeft + gap;
        float innerTop = topY + ((diameter - gap) / 2);
        float innerBottom = innerTop + gap;

        // top lines
        canvas.drawLine(leftX, topY, innerLeft, topY, highlightPaint);
        canvas.drawLine(innerRight, topY, rightX, topY, highlightPaint);
        // bottom lines
        canvas.drawLine(leftX, bottomY, innerLeft, bottomY, highlightPaint);
        canvas.drawLine(innerRight, bottomY, rightX, bottomY, highlightPaint);
        // left lines
        canvas.drawLine(leftX, topY, leftX, innerTop, highlightPaint);
        canvas.drawLine(leftX, innerBottom, leftX, bottomY, highlightPaint);
        // right lines
        canvas.drawLine(rightX, topY, rightX, innerTop, highlightPaint);
        canvas.drawLine(rightX, innerBottom, rightX, bottomY, highlightPaint);
    }


    private void drawStationCross(Canvas canvas, Paint paint, float x, float y, int crossDiameter, int alpha) {
        paint.setAlpha(alpha);
        canvas.drawLine(x , y - crossDiameter / 2, x, y + crossDiameter / 2, paint);
        canvas.drawLine(x - crossDiameter / 2, y, x + crossDiameter / 2, y, paint);
    }


    public boolean getDisplayPreference(GraphActivity.DisplayPreference preference) {
        SharedPreferences preferences =
            getContext().getSharedPreferences("display", Context.MODE_PRIVATE);
        boolean isSelected =
            preferences.getBoolean(preference.toString(), preference.getDefault());
        return isSelected;
    }


    private void drawSketch(Canvas canvas, Sketch sketch, int alpha) {

        if (!getDisplayPreference(GraphActivity.DisplayPreference.SHOW_SKETCH)) {
            return;
        }

        for (PathDetail pathDetail : sketch.getPathDetails()) {

            if (!couldBeOnScreen(pathDetail)) {
                continue;
            }

            List<Coord2D> path = pathDetail.getPath();
            Coord2D from = null;
            for (Coord2D point : path) {
                if (from == null) {
                    from = surveyCoordsToViewCoords(point);
                    continue;
                } else {
                    Coord2D to = surveyCoordsToViewCoords(point);
                    drawPaint.setColor(pathDetail.getColour().intValue);
                    drawPaint.setAlpha(alpha);

                    canvas.drawLine(
                        (float) from.getX(), (float) from.getY(),
                        (float) to.getX(), (float) to.getY(),
                        drawPaint);
                    from = to;
                }
            }
        }

        for (TextDetail textDetail : sketch.getTextDetails()) {
            Coord2D location = surveyCoordsToViewCoords(textDetail.getPosition());
            String text = textDetail.getText();
            labelPaint.setColor(textDetail.getColour().intValue);
            canvas.drawText(text, (float)location.getX(), (float)location.getY(), labelPaint);
        }
    }


    private void drawLegend(Canvas canvas, Survey survey) {

        String surveyLabel =
            survey.getName() +
            " L" + TextTools.formatTo0dpWithComma(SurveyStats.calcTotalLength(survey)) +
            " H" + TextTools.formatTo0dpWithComma(SurveyStats.calcHeightRange(survey));

        float offsetX = getWidth() * 0.03f;
        float offsetY = getHeight() - LEGEND_SIZE * 2;
        canvas.drawText(surveyLabel, offsetX, offsetY, legendPaint);

        int minorGridSize = getMinorGridBoxSize();
        float scaleWidth = (float)surveyToViewScale * minorGridSize;
        float scaleOffsetY = getHeight() - (LEGEND_SIZE * 4);
        canvas.drawLine(offsetX, scaleOffsetY, offsetX + scaleWidth, scaleOffsetY, legendPaint);
        final float TICK_SIZE = 5;
        canvas.drawLine(offsetX, scaleOffsetY, offsetX, scaleOffsetY - TICK_SIZE, legendPaint);
        canvas.drawLine(offsetX + scaleWidth, scaleOffsetY, offsetX + scaleWidth, scaleOffsetY - TICK_SIZE, legendPaint);
        String scaleLabel = minorGridSize + "m";
        canvas.drawText(scaleLabel, offsetX + scaleWidth + 5, scaleOffsetY, legendPaint);

    }

    private void drawDebuggingInfo(Canvas canvas) {
        float offsetX = getWidth() * 0.03f;
        float offsetY = LEGEND_SIZE * 2;
        String label = "x=" + offsetX + " y=" + offsetY +
                " s2v=" + TextTools.formatTo2dp(surveyToViewScale) +
                " 1/s2v=" + TextTools.formatTo2dp(1 / surveyToViewScale) +
                //" 1/log=" + TextTools.formatTo2dp(1 / Math.log(surveyToViewScale)) +
                //" 1/log10=" + TextTools.formatTo2dp(1 / Math.log10(surveyToViewScale)) +
                "\n log (1/s2v) =" + TextTools.formatTo2dp(Math.log(1 /surveyToViewScale)) +
                "\n log10 (1/s2v) =" + TextTools.formatTo2dp(Math.log10(1 /surveyToViewScale));

        canvas.drawText(label, offsetX, offsetY, legendPaint);
    }


    private boolean couldBeOnScreen(SketchDetail sketchDetail) {
        Coord2D topLeft = viewCoordsToSurveyCoords(Coord2D.ORIGIN);
        Coord2D bottomRight = viewCoordsToSurveyCoords(new Coord2D(getWidth(), getHeight()));
        return sketchDetail.intersectsRectangle(topLeft, bottomRight);
    }


    public void centreViewOnActiveStation() {

        Coord2D activeStationCoord =
                projection.getStationMap().get(survey.getActiveStation());

        // not sure how this could be null, but at least one null pointer has been reported
        if (activeStationCoord == null) {
            activeStationCoord = Coord2D.ORIGIN;
        }

        centreViewOnSurveyPoint(activeStationCoord);

    }


    public void centreViewOnSurveyPoint(Coord2D point) {

        double xDeltaInMetres = ((double)getWidth() / 2) / surveyToViewScale;
        double yDeltaInMetres = ((double)getHeight() / 2) / surveyToViewScale;

        double x = point.getX() - xDeltaInMetres;
        double y = point.getY() - yDeltaInMetres;

        viewpointOffset = new Coord2D(x, y);
    }


    private void drawLine(Canvas canvas, Coord2D start, Coord2D end, Paint paint, int alpha) {
        paint.setAlpha(alpha);
        canvas.drawLine(
                (float)(start.getX()), (float)(start.getY()),
                (float)(end.getX()), (float)(end.getY()),
                paint);
    }


    /**
     * Drawing as a path is useful in order to apply path effects (buggy when drawing lines)
     */
    private void drawLineAsPath(Canvas canvas, Coord2D start, Coord2D end, Paint paint, int alpha) {
        paint.setAlpha(alpha);
        Path path = new Path();
        path.moveTo((float)(start.getX()), (float)(start.getY()));
        path.lineTo((float)(end.getX()), (float)(end.getY()));
        canvas.drawPath(path, paint);
    }


    public void adjustZoomBy(double delta) {
        double newZoom = surveyToViewScale + delta;
        setZoom(newZoom);
    }

    public void setZoom(double newZoom) {
        Coord2D centre = new Coord2D((double) getWidth() / 2, (double) getHeight() / 2);
        setZoom(newZoom, centre);
    }

    public void setZoom(double newZoom, Coord2D focusOnScreen) {

        if (MIN_ZOOM >= newZoom || newZoom >= MAX_ZOOM) {
            return;
        }


        Coord2D focusInSurveyCoords = viewCoordsToSurveyCoords(focusOnScreen);

        Coord2D delta = focusInSurveyCoords.minus(viewpointOffset);

        Coord2D scaledDelta = delta.scale(surveyToViewScale / newZoom);
        viewpointOffset = focusInSurveyCoords.minus(scaledDelta);

        surveyToViewScale = newZoom;
    }


    public void undo() {
        sketch.undo();
        invalidate();
    }


    public void redo() {
        sketch.redo();
        invalidate();
    }


    public void setBrushColour(BrushColour brushColour) {
        sketch.setActiveColour(brushColour.colour);
    }


    public void setSketchTool(SketchTool sketchTool) {
        if (previousSketchTool != currentSketchTool) {
            previousSketchTool = currentSketchTool;
        }
        currentSketchTool = sketchTool;
    }


    public SketchTool getSketchTool() {
        return currentSketchTool;
    }


    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            if (currentSketchTool != SketchTool.PINCH_TO_ZOOM) {
                setSketchTool(SketchTool.PINCH_TO_ZOOM);
            }

            double x = detector.getFocusX();
            double y = detector.getFocusY();
            Coord2D focus = new Coord2D(x, y);

            double scaleFactor = detector.getScaleFactor();
            setZoom(surveyToViewScale * scaleFactor, focus);

            invalidate();
            return true;
        }
    }

    private class LongPressListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent motionEvent) {
            Coord2D touchPointOnView = new Coord2D(motionEvent.getX(), motionEvent.getY());
            Station newSelectedStation = checkForStation(touchPointOnView);
            if (newSelectedStation != null) {
                showContextMenu(motionEvent, newSelectedStation);
            }
        }
    }



}
