package edu.wpi.first.talontrack.spline.wpilib;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.DifferentialDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.spline.PoseWithCurvature;
import edu.wpi.first.math.spline.QuinticHermiteSpline;
import edu.wpi.first.math.spline.Spline;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrajectoryConfig;
import edu.wpi.first.math.trajectory.TrajectoryGenerator;
import edu.wpi.first.math.trajectory.TrajectoryUtil;
import edu.wpi.first.talontrack.FxUtils;
import edu.wpi.first.talontrack.PathUnits;
import edu.wpi.first.talontrack.ProjectPreferences;
import edu.wpi.first.talontrack.Waypoint;
import edu.wpi.first.talontrack.PratsTrajectoryStuff.PratsPose2d;
import edu.wpi.first.talontrack.PratsTrajectoryStuff.PratsTrajectory;
import edu.wpi.first.talontrack.PratsTrajectoryStuff.PratsTrajectoryConfig;
import edu.wpi.first.talontrack.PratsTrajectoryStuff.PratsTrajectoryGenerator;
import edu.wpi.first.talontrack.PratsTrajectoryStuff.PratsTrajectoryInstance;
import edu.wpi.first.talontrack.PratsTrajectoryStuff.PratsTrajectoryUtil;
import edu.wpi.first.talontrack.path.Path;
import edu.wpi.first.talontrack.spline.AbstractSpline;
import edu.wpi.first.talontrack.spline.SplineSegment;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Group;
import javafx.scene.Node;

import javax.measure.UnitConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A WpilibSpline interfaces with Wpilib to
 * calculate splines.
 */
public class WpilibSpline extends AbstractSpline {
    private static final Logger LOGGER = Logger.getLogger(WpilibSpline.class.getName());

    private final SimpleDoubleProperty strokeWidth = new SimpleDoubleProperty(1.0);
    private int subchildIdx = 0;

    private final Path path;

    @Override
    public void enableSubchildSelector(int i) {
        this.subchildIdx = i;
        for (Node node : group.getChildren()) {
            FxUtils.enableSubchildSelector(node, subchildIdx);
            node.applyCss();
        }
    }

    @Override
    public void removeFromGroup(Group splineGroup) {
        splineGroup.getChildren().remove(group);
    }

    public WpilibSpline(List<Waypoint> waypoints, Path path) {
        super(waypoints);
        this.path = path;
    }

    @Override
    public void update() {
        group.getChildren().clear();
        for (int i = 1; i < waypoints.size(); i++) {
            Waypoint segStart = waypoints.get(i - 1).copy();
            Waypoint segEnd = waypoints.get(i).copy();
            QuinticHermiteSpline quintic;

            if (segStart.isReversed()) {
                quintic = getQuinticSplinesFromWaypoints(new Waypoint[] { segEnd, segStart })[0];
            } else {
                quintic = getQuinticSplinesFromWaypoints(new Waypoint[] { segStart, segEnd })[0];
            }
            SplineSegment seg = new SplineSegment(waypoints.get(i - 1), waypoints.get(i), path);

            for (int sample = 0; sample <= 40; sample++) {
                PoseWithCurvature pose = quintic.getPoint(sample / 40.0);
                seg.getLine().getPoints().add(pose.poseMeters.getTranslation().getX());
                // Convert from WPILib to JavaFX coords
                seg.getLine().getPoints().add(-pose.poseMeters.getTranslation().getY());
            }

            if (segStart.isReversed()) {
                seg.getLine().getStrokeDashArray().addAll(0.1, 0.2);
            }

            seg.getLine().strokeWidthProperty().bind(strokeWidth);
            seg.getLine().getStyleClass().addAll("path");

            FxUtils.enableSubchildSelector(seg.getLine(), subchildIdx);
            seg.getLine().applyCss();

            group.getChildren().add(seg.getLine());
        }
    }

    @Override
    public void addToGroup(Group splineGroup, double scaleFactor) {
        strokeWidth.set(scaleFactor);
        splineGroup.getChildren().add(group);
        group.toBack();
    }

    @Override
    public boolean writeToFile(java.nio.file.Path path) {
        final AtomicBoolean okay = new AtomicBoolean(true);
        TrajectoryGenerator.setErrorHandler((error, stacktrace) -> {
            LOGGER.log(Level.WARNING, "Could not write Spline to file: " + error, stacktrace);
            okay.set(false);
        });
        try {
            var values = ProjectPreferences.getInstance().getValues();
            var prefs = ProjectPreferences.getInstance();
            var lengthUnit = prefs.getField().getUnit();
            double height = prefs.getField().getRealLength().getValue().doubleValue();
            var maxVelocity = values.getMaxVelocity();
            var maxAcceleration = values.getMaxAcceleration();
            var trackWidth = values.getTrackWidth();
            var wheelBase = values.getWheelBase();
            SwerveDriveKinematics swerveKine = new SwerveDriveKinematics(
                    new Translation2d(wheelBase / 2, -trackWidth / 2),
                    new Translation2d(wheelBase / 2, trackWidth / 2),
                    new Translation2d(-wheelBase / 2, -trackWidth / 2),
                    new Translation2d(-wheelBase / 2, trackWidth / 2));

            // If the export type is different (i.e. meters), then we have to convert it.
            // Otherwise we are good.
            if (prefs.getValues().getExportUnit() == ProjectPreferences.ExportUnit.METER) {
                UnitConverter converter = lengthUnit.getConverterTo(PathUnits.METER);
                height = converter.convert(height);
                maxVelocity = converter.convert(maxVelocity);
                maxAcceleration = converter.convert(maxAcceleration);
                trackWidth = converter.convert(trackWidth);
                wheelBase = converter.convert(wheelBase);
            }

            PratsTrajectoryConfig config = new PratsTrajectoryConfig(maxVelocity, maxAcceleration)
                    .setKinematics(swerveKine)
                    .setReversed(waypoints.get(0).isReversed());
            PratsTrajectory traj = trajectoryFromWaypoints(waypoints, config);

            for (int i = 0; i < traj.getStates().size(); ++i) {
                var st = traj.getStates().get(i);
                traj.getStates().set(i, new PratsTrajectory.State(
                        st.timeSeconds, st.velocityMetersPerSecond, st.accelerationMetersPerSecondSq,
                        new PratsPose2d(st.poseMeters.getX(), st.poseMeters.getY() + height,
                                st.poseMeters.getRotation(), st.poseMeters.getTangent()),
                        st.curvatureRadPerMeter));
            }

            new PratsTrajectoryInstance(traj, (path.getFileName().toString() + ".wpilib.json"));
            PratsTrajectoryUtil.toPathweaverJson(traj, path.resolveSibling(path.getFileName() + ".wpilib.json"));

            return okay.get();
        } catch (IOException except) {
            LOGGER.log(Level.WARNING, "Could not write Spline to file", except);
            return false;
        }
    }

    private static QuinticHermiteSpline[] getQuinticSplinesFromWaypoints(Waypoint[] waypoints) {
        QuinticHermiteSpline[] splines = new QuinticHermiteSpline[waypoints.length - 1];
        for (int i = 0; i < waypoints.length - 1; i++) {
            var p0 = waypoints[i];
            var p1 = waypoints[i + 1];

            double[] xInitialVector = { p0.getX(), p0.getTangentX(), 0.0 };
            double[] xFinalVector = { p1.getX(), p1.getTangentX(), 0.0 };
            double[] yInitialVector = { p0.getY(), p0.getTangentY(), 0.0 };
            double[] yFinalVector = { p1.getY(), p1.getTangentY(), 0.0 };

            splines[i] = new QuinticHermiteSpline(xInitialVector, xFinalVector,
                    yInitialVector, yFinalVector);
        }

        return splines;
    }

    private static PratsTrajectory trajectoryFromWaypoints(Iterable<Waypoint> waypoints, PratsTrajectoryConfig config) {
        ProjectPreferences.Values prefs = ProjectPreferences.getInstance().getValues();

        List<PratsPose2d> poseList = new ArrayList<PratsPose2d>();

        for (Waypoint wp : waypoints) {
            if (prefs.getExportUnit() == ProjectPreferences.ExportUnit.METER) {
                UnitConverter converter = prefs.getLengthUnit().getConverterTo(PathUnits.METER);
                poseList.add(
                        new PratsPose2d(
                                converter.convert(wp.getX()),
                                converter.convert(wp.getY()),
                                new Rotation2d(
                                        converter.convert(wp.getHeadingX()),
                                        converter.convert(wp.getHeadingY())),
                                new Rotation2d(
                                        converter.convert(wp.getTangentX()),
                                        converter.convert(wp.getTangentY()))));
            } else {
                poseList.add(
                        new PratsPose2d(
                                wp.getX(),
                                wp.getY(),
                                new Rotation2d(
                                        wp.getHeadingX(),
                                        wp.getHeadingY()),
                                new Rotation2d(
                                        wp.getTangentX(),
                                        wp.getTangentY())));
            }
        }
        // return TrajectoryGenerator.generateTrajectory();
        return PratsTrajectoryGenerator.generateTrajectory(poseList, config);
    }
}
