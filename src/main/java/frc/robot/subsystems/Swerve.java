package frc.robot.subsystems;

import com.kauailabs.navx.frc.AHRS;
import com.ctre.phoenix6.hardware.CANcoder;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.util.PathPlannerLogging;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj2.command.*;
import frc.robot.SwerveModule;
import frc.robot.Constants;

import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModulePosition;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * This is the Swerve Drive Subsystem, based on team 364's code
 */
public class Swerve extends SubsystemBase {
    public SwerveDriveOdometry swerveOdometry;
    public SwerveModule[] mSwerveMods;
    private AHRS gyro;

    private Field2d field = new Field2d();

    public Swerve() {
        gyro = new AHRS(SPI.Port.kMXP);
        gyro.reset();

        mSwerveMods = new SwerveModule[] {
            new SwerveModule(0, Constants.Swerve.Mod0.constants),
            new SwerveModule(1, Constants.Swerve.Mod1.constants),
            new SwerveModule(2, Constants.Swerve.Mod2.constants),
            new SwerveModule(3, Constants.Swerve.Mod3.constants)
        };

        resetModulesToAbsolute();

        swerveOdometry = new SwerveDriveOdometry(Constants.Swerve.swerveKinematics, getGyroYaw(), getModulePositions());

        // Configure PathPlanner Auto Builder
        AutoBuilder.configureHolonomic(
                this::getPose,
                this::setPose,
                this::getRobotRelativeSpeeds,
                this::driveRobotRelative,
                Constants.PathPlannerConstants.pathFollowerConfig,
                () -> {
                    // Boolean supplier that controls when the path will be mirrored for the red alliance
                    // This will flip the path being followed to the red side of the field.
                    // THE ORIGIN WILL REMAIN ON THE BLUE SIDE

                    var alliance = DriverStation.getAlliance();
                    if (alliance.isPresent()) {
                        return alliance.get() == DriverStation.Alliance.Red;
                    }
                    return false;
                },
                this
        );

        PathPlannerLogging.setLogActivePathCallback((poses) -> field.getObject("path").setPoses(poses));

        // Logging callback for the active path, this is sent as a list of poses
        PathPlannerLogging.setLogActivePathCallback((poses) -> {
            // Do whatever you want with the poses here
            field.getObject("path").setPoses(poses);
        });

        SmartDashboard.putData("Field", field);
    }

    /**
     * Drives the {@link Swerve} Subsystem
     * @param translation A {@link Translation2d} representing the desired X and Y speed in meters per second
     * @param rotation The desired angular velocity in radians per second
     * @param fieldRelative Controls whether it should be driven in field or robot relative mode
     * @param isOpenLoop Controls whether it drives in open loop
     */
    public void drive(Translation2d translation, double rotation, boolean fieldRelative, boolean isOpenLoop) {
        SwerveModuleState[] swerveModuleStates =
            Constants.Swerve.swerveKinematics.toSwerveModuleStates(
                fieldRelative ? ChassisSpeeds.fromFieldRelativeSpeeds(
                                    translation.getX(), 
                                    translation.getY(), 
                                    rotation, 
                                    getHeading()
                                )
                                : new ChassisSpeeds(
                                    translation.getX(), 
                                    translation.getY(), 
                                    rotation)
                                );
        SwerveDriveKinematics.desaturateWheelSpeeds(swerveModuleStates, Constants.Swerve.maxSpeed);

        for(SwerveModule mod : mSwerveMods){
            mod.setDesiredState(swerveModuleStates[mod.moduleNumber], isOpenLoop);
        }
    }

    /**
     * Sets the states of each module
     * @param desiredStates An array of the {@link SwerveModuleState} for each {@link SwerveModule}
     */
    public void setModuleStates(SwerveModuleState[] desiredStates) {
        SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, Constants.Swerve.maxSpeed);
        
        for(SwerveModule mod : mSwerveMods){
            mod.setDesiredState(desiredStates[mod.moduleNumber], false);
        }
    }

    /**
     * Turns all the wheels to face inwards creating and "X" shape to lock the robot in place if the robot is not currently moving
     */
    public void xLockWheels() {
        if(getModuleStates()[0].speedMetersPerSecond < 0.1) {
            SwerveModuleState[] desiredStates = {
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(45)),
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(135)),
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(135)),
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(45))
            };
            for(SwerveModule mod : mSwerveMods){
                mod.setAngle(desiredStates[mod.moduleNumber]);
            }
        }
    }

    /**
     * @return An array containing the {@link SwerveModuleState} of each {@link SwerveModule}
     */
    public SwerveModuleState[] getModuleStates(){
        SwerveModuleState[] states = new SwerveModuleState[4];
        for(SwerveModule mod : mSwerveMods){
            states[mod.moduleNumber] = mod.getState();
        }
        return states;
    }

    /**
     * @return An array of the {@link SwerveModulePosition} of each {@link SwerveModule}
     */
    public SwerveModulePosition[] getModulePositions(){
        SwerveModulePosition[] positions = new SwerveModulePosition[4];
        for(SwerveModule mod : mSwerveMods){
            positions[mod.moduleNumber] = mod.getPosition();
        }
        return positions;
    }

    /**
     * @return The {@link Pose2d} of the robot according to the {@link SwerveDriveOdometry}
     */
    public Pose2d getPose() {
        return swerveOdometry.getPoseMeters();
    }

    /**
     * @param pose The {@link Pose2d} to set the {@link SwerveDriveOdometry} to
     */
    public void setPose(Pose2d pose) {
        swerveOdometry.resetPosition(getGyroYaw(), getModulePositions(), pose);
    }

    /**
     * @return The speeds of each {@link SwerveModule} as {@link ChassisSpeeds} Required by Path Planner
     */
    public ChassisSpeeds getRobotRelativeSpeeds() {
        return Constants.Swerve.swerveKinematics.toChassisSpeeds(getModuleStates());
    }

    /**
     * Required by Path Planner, drives the robot given {@link ChassisSpeeds}
     * @param speeds the ChassisSpeeds to drive the robot with
     */
    public void driveRobotRelative(ChassisSpeeds speeds) {
        SwerveModuleState[] states = Constants.Swerve.swerveKinematics.toSwerveModuleStates(speeds);
        SwerveDriveKinematics.desaturateWheelSpeeds(states, Constants.Swerve.maxSpeed);
        setModuleStates(states);
    }

    /**
     * @return The heading reported by the {@link SwerveDriveOdometry} as a {@link Rotation2d}
     */
    public Rotation2d getHeading(){
        return getPose().getRotation();
    }

    /**
     * Sets the {@link SwerveDriveOdometry} heading
     * @param heading The heading of the robot
     */
    public void setHeading(Rotation2d heading){
        swerveOdometry.resetPosition(getGyroYaw(), getModulePositions(), new Pose2d(getPose().getTranslation(), heading));
    }

    /**
     * Resets the {@link SwerveDriveOdometry} heading
     */
    public void zeroHeading(){
        swerveOdometry.resetPosition(getGyroYaw(), getModulePositions(), new Pose2d(getPose().getTranslation(), new Rotation2d()));
    }

    /**
     * @return The Robot yaw as reported by the NavX
     */
    public Rotation2d getGyroYaw() {
        return Constants.Swerve.invertGyro ? (Rotation2d.fromDegrees(360-gyro.getYaw())) : Rotation2d.fromDegrees(gyro.getYaw());
    }

    /**
     * @return The angular velocity of the robot as reported by the NavX
     */
    public float getGyroYawSpeed() {
        return Constants.Swerve.invertGyro ? -gyro.getRawGyroZ() : gyro.getRawGyroZ();
    }

    /**
     * Resets each {@link SwerveModule} based on the {@link CANcoder} and angle offset
     */
    public void resetModulesToAbsolute(){
        for(SwerveModule mod : mSwerveMods){
            mod.resetToAbsolute();
        }
    }

    /**
     * Used for driving the robot during teleop
     * @param translationSup {@link DoubleSupplier} for the forwards/backwards speed as a percentage
     * @param strafeSup {@link DoubleSupplier} for the left/right speed as a percentage
     * @param rotationSup {@link DoubleSupplier} for the angular velocity as a percentage
     * @param robotCentricSup {@link BooleanSupplier} for driving in robot or field centric mode
     */
    public void teleopDriveSwerve(DoubleSupplier translationSup, DoubleSupplier strafeSup,
                                  DoubleSupplier rotationSup, BooleanSupplier robotCentricSup) {
        /* Get Values, Deadband*/
        double translationVal = MathUtil.applyDeadband(translationSup.getAsDouble(), Constants.stickDeadband);
        double strafeVal = MathUtil.applyDeadband(strafeSup.getAsDouble(), Constants.stickDeadband);
        double rotationVal = MathUtil.applyDeadband(rotationSup.getAsDouble(), Constants.stickDeadband);

        drive(new Translation2d(translationVal, strafeVal).times(Constants.Swerve.maxSpeed),
                rotationVal * Constants.Swerve.maxAngularVelocity,
                !robotCentricSup.getAsBoolean(),
                true);
    }

    /**
     * Creates an angular velocity based on a target angle, and the direction the robot is currently facing
     * @param targetAngle The angle the robot should turn to
     * @return The angular velocity as a percentage
     */
    public double rotationPercentageFromTargetAngle(double targetAngle) {
        double headingError = (targetAngle - getGyroYaw().getDegrees() % 360);

        if(headingError > 180) {
            headingError = -180 + (headingError % 180);
        } else if(headingError < -180) {
            headingError = 180 + (headingError % 180);
        }

        return headingError/360;
    }

    // **** Commands ****
    /**
     * Used for driving the robot during teleop while rotating to the angle reported by the D-Pad of the controller
     * @param translationSup {@link DoubleSupplier} for the forwards/backwards speed as a percentage
     * @param strafeSup {@link DoubleSupplier} for the left/right speed as a percentage
     * @param rotationSup {@link IntSupplier} for the target angle
     * @param robotCentricSup {@link BooleanSupplier} for driving in robot or field centric mode
     */
    public Command teleopDriveSwerveAndRotateToDPadCommand(DoubleSupplier translationSup, DoubleSupplier strafeSup,
                                                           IntSupplier rotationSup, BooleanSupplier robotCentricSup) {

        return this.run(
                () -> teleopDriveSwerve(
                        translationSup,
                        strafeSup,
                        () -> rotationPercentageFromTargetAngle(rotationSup.getAsInt()),
                        robotCentricSup
                )
        );
    }

    /**
     * Cancels the command currently running on this subsystem unless it's the default command
     */
    public void cancelCurrentCommand() {
        if(this.getCurrentCommand() != this.getDefaultCommand()) {
            CommandScheduler.getInstance().cancel(this.getCurrentCommand());
        }
    }

    @Override
    public void periodic(){
        swerveOdometry.update(getGyroYaw(), getModulePositions());

        for(SwerveModule mod : mSwerveMods) {
            SmartDashboard.putNumber("Mod " + mod.moduleNumber + " Integrated", mod.getPosition().angle.getDegrees());
            SmartDashboard.putNumber("Mod " + mod.moduleNumber + " CANcoder", mod.getCANcoder().getDegrees());
            SmartDashboard.putNumber("Mod " + mod.moduleNumber + " Angle", mod.getPosition().angle.getDegrees());
            SmartDashboard.putNumber("Mod " + mod.moduleNumber + " Velocity", mod.getState().speedMetersPerSecond);    
        }
    }
}