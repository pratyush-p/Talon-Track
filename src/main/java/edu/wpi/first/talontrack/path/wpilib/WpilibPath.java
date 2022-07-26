package edu.wpi.first.talontrack.path.wpilib;

import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.image.WritableImage;
import javafx.scene.input.TransferMode;

import javax.measure.UnitConverter;

import edu.wpi.first.talontrack.*;
import edu.wpi.first.talontrack.global.CurrentSelections;
import edu.wpi.first.talontrack.path.Path;
import edu.wpi.first.talontrack.path.PathUtil;
import edu.wpi.first.talontrack.spline.wpilib.WpilibSpline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WpilibPath extends Path {
    private final Group iconGroup = new Group();
    private final Group robotGroup = new Group();
    private final Group tangentGroup = new Group();
    private final Group headingGroup = new Group();

    /**
     * Path constructor based on a known list of points.
     *
     * @param points The list of waypoints to add
     * @param name   The name of the path
     */

    public WpilibPath(List<Waypoint> points, String name) {
        super(WpilibSpline::new, name);
        this.waypoints.addListener((ListChangeListener<Waypoint>) c -> {
            Waypoint first = this.waypoints.get(0);
            while (c.next()) {
                for (Waypoint wp : c.getAddedSubList()) {
                    setupWaypoint(wp);
                    robotGroup.getChildren().add(wp.getRobot());
                    iconGroup.getChildren().add(wp.getIcon());
                    tangentGroup.getChildren().add(wp.getTangentLine());
                    headingGroup.getChildren().add(wp.getHeadingLine());
                    if (wp != first) {
                        wp.reversedProperty().bindBidirectional(first.reversedProperty());
                    }
                    wp.reversedProperty().addListener(l -> {
                        update();
                    });
                }

                for (Waypoint wp : c.getRemoved()) {
                    iconGroup.getChildren().remove(wp.getIcon());
                    robotGroup.getChildren().remove(wp.getRobot());
                    tangentGroup.getChildren().remove(wp.getTangentLine());
                    headingGroup.getChildren().remove(wp.getHeadingLine());
                }
            }
            update();
        });
        this.spline.addToGroup(this.mainGroup, DEFAULT_SPLINE_SCALE / field.getScale());
        this.mainGroup.getChildren().addAll(this.robotGroup, this.iconGroup, this.tangentGroup, this.headingGroup);
        this.waypoints.addAll(points);

        update();
        enableSubchildSelector(subchildIdx);
    }

    private void setupDrag(Waypoint waypoint) {
        waypoint.getRobot().setOnDragDetected(event -> {
            CurrentSelections.setCurWaypoint(waypoint);
            CurrentSelections.setCurPath(this);
            var db = waypoint.getRobot().startDragAndDrop(TransferMode.MOVE);
            db.setContent(Map.of(DataFormats.WAYPOINT, "point"));
            db.setDragView(new WritableImage(1, 1));
        });

        waypoint.getIcon().setOnDragDetected(event -> {
            CurrentSelections.setCurWaypoint(waypoint);
            CurrentSelections.setCurPath(this);
            var db = waypoint.getIcon().startDragAndDrop(TransferMode.MOVE);
            db.setContent(Map.of(DataFormats.WAYPOINT, "point"));
            db.setDragView(new WritableImage(1, 1));
        });

        waypoint.getTangentLine().setOnDragDetected(event -> {
            CurrentSelections.setCurWaypoint(waypoint);
            CurrentSelections.setCurPath(this);
            var db = waypoint.getTangentLine().startDragAndDrop(TransferMode.MOVE);
            db.setContent(Map.of(DataFormats.CONTROL_VECTOR, "vector"));
            db.setDragView(new WritableImage(1, 1));
        });

        waypoint.getHeadingLine().setOnDragDetected(event -> {
            CurrentSelections.setCurWaypoint(waypoint);
            CurrentSelections.setCurPath(this);
            var db = waypoint.getHeadingLine().startDragAndDrop(TransferMode.MOVE);
            db.setContent(Map.of(DataFormats.HEADING, "vector"));
            db.setDragView(new WritableImage(1, 1));
        });
    }

    private void setupClick(Waypoint waypoint) {
        waypoint.getRobot().setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                toggleWaypoint(waypoint);
            } else if (e.getClickCount() == 2) {
                waypoint.setLockTangent(false);
            }
            e.consume();
        });

        waypoint.getIcon().setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                toggleWaypoint(waypoint);
            } else if (e.getClickCount() == 2) {
                waypoint.setLockTangent(false);
            }
            e.consume();
        });

        waypoint.getTangentLine().setOnMouseClicked(e -> {
            toggleWaypoint(waypoint);
            if (e.getClickCount() == 2) {
                waypoint.setLockTangent(false);
                e.consume();
            }
        });

        waypoint.getHeadingLine().setOnMouseClicked(e -> {
            toggleWaypoint(waypoint);
            if (e.getClickCount() == 2) {
                e.consume();
            }
        });
    }

    /**
     * This implementation calls
     * {@link PathUtil#rawThetaOptimization(Point2D, Point2D, Point2D)} to update
     * the tangent line.
     * 
     * @param wp the waypoint to update the tangent line for.
     */
    @Override
    protected void updateTangent(Waypoint wp) {
        int curWpIndex = getWaypoints().indexOf(wp);
        if (curWpIndex - 1 < 0 || curWpIndex + 1 >= waypoints.size() || wp.isLockTangent()) {
            return;
        }

        Waypoint previous = getWaypoints().get(curWpIndex - 1);
        Waypoint next = getWaypoints().get(curWpIndex + 1);

        Point2D wpTangent = PathUtil.rawThetaOptimization(previous.getCoords(), wp.getCoords(), next.getCoords());
        if (wp.isReversed()) {
            wpTangent = wpTangent.multiply(-1);
        }
        wp.setTangent(wpTangent);
    }

    @Override
    protected void updateHeading(Waypoint wp) {
        int curWpIndex = getWaypoints().indexOf(wp);
        if (curWpIndex - 1 < 0 || curWpIndex + 1 >= waypoints.size() || wp.isLockHeading()) {
            return;
        }

        Waypoint previous = getWaypoints().get(curWpIndex - 1);
        Waypoint next = getWaypoints().get(curWpIndex + 1);

        Point2D wpTangent = PathUtil.rawThetaOptimization(previous.getCoords(), wp.getCoords(), next.getCoords());
        if (wp.isReversed()) {
            wpTangent = wpTangent.multiply(-1);
        }
        wp.setTangent(wpTangent);
    }

    private void setupWaypoint(Waypoint waypoint) {
        setupDrag(waypoint);
        setupClick(waypoint);

        waypoint.getIcon().setOnContextMenuRequested(e -> {
            ContextMenu menu = new ContextMenu();
            if (getWaypoints().size() > 2) {
                menu.getItems().add(FxUtils.menuItem("Delete", event -> removeWaypoint(waypoint)));
            }
            if (waypoint.getTangentLine().isVisible()) {
                menu.getItems().add(FxUtils.menuItem("Hide control vector",
                        event -> waypoint.getTangentLine().setVisible(false)));
            } else {
                menu.getItems().add(FxUtils.menuItem("Show control vector",
                        event -> waypoint.getTangentLine().setVisible(true)));
            }

            if (waypoint.getHeadingLine().isVisible()) {
                menu.getItems().add(FxUtils.menuItem("Hide heading",
                        event -> waypoint.getHeadingLine().setVisible(false)));
            } else {
                menu.getItems().add(FxUtils.menuItem("Show heading",
                        event -> waypoint.getHeadingLine().setVisible(true)));
            }

            menu.getItems().add(FxUtils.menuItem("Reverse Vector",
                    event -> waypoint.setReversed(!waypoint.isReversed())));
            menu.show(mainGroup.getScene().getWindow(), e.getScreenX(), e.getScreenY());
        });

        waypoint.getIcon().setScaleX(DEFAULT_CIRCLE_SCALE / field.getScale());
        waypoint.getIcon().setScaleY(DEFAULT_CIRCLE_SCALE / field.getScale());
        waypoint.getRobot().setScaleX(DEFAULT_CIRCLE_SCALE / field.getScale());
        waypoint.getRobot().setScaleY(DEFAULT_CIRCLE_SCALE / field.getScale());
        waypoint.getTangentLine().setStrokeWidth(DEFAULT_LINE_SCALE / field.getScale());
        waypoint.getHeadingLine().setStrokeWidth(DEFAULT_LINE_SCALE / field.getScale());
    }

    /**
     * Path constructor based on default start and end points.
     *
     * @param name The name of the path
     */
    public WpilibPath(String name) {
        this(new Point2D(0, 0),
                new Point2D(getDefaultLength(), getDefaultWidth()),
                new Point2D(getDefaultLength(), 0), new Point2D(0, getDefaultWidth()),
                new Point2D(1, -1), new Point2D(1, 1), name);
    }

    /**
     * Path constructor based on known start and end points.
     *
     * @param startPos     The starting waypoint of new path
     * @param endPos       The ending waypoint of new path
     * @param startTangent The starting tangent vector of new path
     * @param endTangent   The ending tangent vector of new path
     * @param name         The string name to assign path, also used for naming
     *                     exported files
     */
    private WpilibPath(Point2D startPos, Point2D endPos, Point2D startTangent, Point2D endTangent, Point2D startHeading,
            Point2D endHeading, String name) {
        this(List.of(new Waypoint(startPos, startTangent, startHeading, true, false),
                new Waypoint(endPos, endTangent, endHeading, true, false)),
                name);
    }

    @Override
    public String toString() {
        return getPathName();
    }

    /**
     * Returns all the tangent lines for the waypoints.
     *
     * @return Collection of Tangent Lines.
     */
    public Collection<Node> getTangentLines() {
        return getWaypoints().stream().map(Waypoint::getTangentLine).collect(Collectors.toList());
    }

    /**
     * Duplicates a path.
     *
     * @param newName filename of the new path.
     * @return the new path.
     */
    @Override
    public Path duplicate(String newName) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Waypoint wp : getWaypoints()) {
            waypoints.add(wp.copy());
        }
        return new WpilibPath(waypoints, newName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        WpilibPath path = (WpilibPath) o;
        if (!pathName.equals(path.pathName)) {
            return false;
        }

        return getWaypoints().equals(path.getWaypoints());
    }

    public static double getDefaultWidth() {
        UnitConverter toFeet = ProjectPreferences.getInstance().getField().getUnit().getConverterTo(PathUnits.FOOT);
        return -toFeet
                .convert(ProjectPreferences.getInstance().getField().getRealWidth().getValue().doubleValue() * 0.25);
    }

    public static double getDefaultLength() {
        UnitConverter toFeet = ProjectPreferences.getInstance().getField().getUnit().getConverterTo(PathUnits.FOOT);
        return toFeet
                .convert(ProjectPreferences.getInstance().getField().getRealLength().getValue().doubleValue() * 0.25);
    }
}
