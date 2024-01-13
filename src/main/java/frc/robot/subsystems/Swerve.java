package frc.robot.subsystems;

import com.kauailabs.navx.frc.AHRS;
import com.pathplanner.lib.auto.AutoBuilder;
import com.pathplanner.lib.util.PathPlannerLogging;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.SPI;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.FunctionalCommand;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
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
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

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

    /* Used by SwerveControllerCommand in Auto */
    public void setModuleStates(SwerveModuleState[] desiredStates) {
        SwerveDriveKinematics.desaturateWheelSpeeds(desiredStates, Constants.Swerve.maxSpeed);
        
        for(SwerveModule mod : mSwerveMods){
            mod.setDesiredState(desiredStates[mod.moduleNumber], false);
        }
    }

    public void xLockWheels() {
        if(getModuleStates()[0].speedMetersPerSecond < 0.1) {
            SwerveModuleState[] desiredStates = {
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(45)),
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(135)),
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(135)),
                    new SwerveModuleState(0.0, Rotation2d.fromDegrees(45))
            };
            for(SwerveModule mod : mSwerveMods){
                mod.forceSetAngle (desiredStates[mod.moduleNumber]);
            }
        }
    }

    public SwerveModuleState[] getModuleStates(){
        SwerveModuleState[] states = new SwerveModuleState[4];
        for(SwerveModule mod : mSwerveMods){
            states[mod.moduleNumber] = mod.getState();
        }
        return states;
    }

    public SwerveModulePosition[] getModulePositions(){
        SwerveModulePosition[] positions = new SwerveModulePosition[4];
        for(SwerveModule mod : mSwerveMods){
            positions[mod.moduleNumber] = mod.getPosition();
        }
        return positions;
    }

    public Pose2d getPose() {
        return swerveOdometry.getPoseMeters();
    }

    public void setPose(Pose2d pose) {
        swerveOdometry.resetPosition(getGyroYaw(), getModulePositions(), pose);
    }

    public ChassisSpeeds getRobotRelativeSpeeds() {
        return Constants.Swerve.swerveKinematics.toChassisSpeeds(getModuleStates());
    }

    public void driveRobotRelative(ChassisSpeeds speeds) {
        SwerveModuleState[] states = Constants.Swerve.swerveKinematics.toSwerveModuleStates(speeds);
        SwerveDriveKinematics.desaturateWheelSpeeds(states, Constants.Swerve.maxSpeed);
        setModuleStates(states);
    }

    public Rotation2d getHeading(){
        return getPose().getRotation();
    }

    public void setHeading(Rotation2d heading){
        swerveOdometry.resetPosition(getGyroYaw(), getModulePositions(), new Pose2d(getPose().getTranslation(), heading));
    }

    public void zeroHeading(){
        swerveOdometry.resetPosition(getGyroYaw(), getModulePositions(), new Pose2d(getPose().getTranslation(), new Rotation2d()));
    }

    public Rotation2d getGyroYaw() {
        return Constants.Swerve.invertGyro ? (Rotation2d.fromDegrees(360-gyro.getYaw())) : Rotation2d.fromDegrees(gyro.getYaw());
    }

    public float getGyroYawSpeed() {
        return gyro.getRawGyroZ();
    }

    public void resetModulesToAbsolute(){
        for(SwerveModule mod : mSwerveMods){
            mod.resetToAbsolute();
        }
    }

    // Drive command for teleop
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

    public Command teleopDriveSwerveAndRotateToDPadCommand(DoubleSupplier translationSup, DoubleSupplier strafeSup,
            DoubleSupplier rotationSup, BooleanSupplier robotCentricSup) {
        CommandXboxController driverController = new CommandXboxController(0);
        int targetAngle;
        boolean isFinished = false;

        if(driverController.povUp().getAsBoolean()) {
            targetAngle = 0;
        } else if(driverController.povRight().getAsBoolean()) {
            targetAngle = 90;
        } else if(driverController.povDown().getAsBoolean()) {
            targetAngle = 180;
        } else if(driverController.povLeft().getAsBoolean()) {
            targetAngle = -90;
        } else {
            targetAngle = 0;
            isFinished = true;
        }


        boolean finalIsFinished = isFinished;
        return new FunctionalCommand(
                null,
                () -> teleopDriveSwerve(translationSup, strafeSup, () -> rotationPercentageFromTargetAngle(targetAngle), robotCentricSup),
                null,
                () -> (finalIsFinished || rotationSup.getAsDouble() > 0.1),
                this
        );
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